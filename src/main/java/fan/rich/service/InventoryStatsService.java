package fan.rich.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点统计：总览 KPI、按标的类型 / 分类的差异分布、任务进度、月度趋势与人员工作量。
 *
 * <p><b>口径说明</b>：
 * <ul>
 *   <li>「应盘」= 任务清单里的明细条数，「已盘」= 已录入实盘数的条数；
 *       只有生成过清单的标的才计入，因此账外临时补录的也会计入应盘。</li>
 *   <li>盘盈按 {@code diff_amount} 正值汇总，盘亏取其绝对值，两者分开列示，
 *       不做净额抵消——净额会掩盖"两头都漏"的管理问题。</li>
 *   <li>月度趋势按任务的<b>计划盘点日</b>归属月份，与财务期间口径一致。</li>
 * </ul>
 *
 * <p>所有 SQL 面向 MySQL 5.7 编写，不使用窗口函数。
 */
@Service
public class InventoryStatsService {

    private final JdbcTemplate jdbc;

    public InventoryStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 盘点总览。 */
    public Map<String, Object> overview() {
        int year = LocalDate.now().getYear();
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> task = jdbc.queryForMap(
                "SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN status=? THEN 1 ELSE 0 END) AS running,"
                        + " SUM(CASE WHEN status=? THEN 1 ELSE 0 END) AS done"
                        + " FROM inv_task WHERE YEAR(plan_date)=?",
                InventoryService.TASK_RUNNING, InventoryService.TASK_DONE, year);

        Map<String, Object> rec = jdbc.queryForMap(
                "SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN actual_qty IS NOT NULL THEN 1 ELSE 0 END) AS checked,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS normal,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS profit,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS loss,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN r.diff_amount ELSE 0 END),0) AS profit_amt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN -r.diff_amount ELSE 0 END),0) AS loss_amt,"
                        + " SUM(CASE WHEN r.handle_status=? THEN 1 ELSE 0 END) AS pending"
                        + " FROM inv_record r JOIN inv_task t ON t.id=r.task_id"
                        + " WHERE YEAR(t.plan_date)=?",
                InventoryService.RESULT_NORMAL, InventoryService.RESULT_PROFIT,
                InventoryService.RESULT_LOSS, InventoryService.RESULT_PROFIT,
                InventoryService.RESULT_LOSS, InventoryService.HANDLE_TODO, year);

        BigDecimal subject = dec(rec.get("total"));
        BigDecimal checked = dec(rec.get("checked"));

        data.put("taskCount", task.get("total"));
        data.put("runningCount", nvl(task.get("running")));
        data.put("doneCount", nvl(task.get("done")));
        data.put("subjectCount", subject);
        data.put("checkedCount", checked);
        data.put("uncheckedCount", subject.subtract(checked));
        data.put("checkedRate", rate(checked, subject));
        data.put("normalCount", nvl(rec.get("normal")));
        data.put("profitCount", nvl(rec.get("profit")));
        data.put("lossCount", nvl(rec.get("loss")));
        data.put("profitAmount", dec(rec.get("profit_amt")));
        data.put("lossAmount", dec(rec.get("loss_amt")));
        data.put("pendingCount", nvl(rec.get("pending")));

        // 各类型台账规模（供看板显示"有多少东西可盘"）
        data.put("assetCount", count("ast_asset", "status <> '报废'"));
        data.put("goodsCount", count("ast_goods", "status <> '停用'"));
        data.put("canteenCount", count("canteen_material", "1=1"));
        data.put("assetAmount", sum("ast_asset", "original_value", "status <> '报废'"));
        return data;
    }

    /** 按标的类型统计盘盈盘亏分布。 */
    public List<Map<String, Object>> typeStats() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.subject_type AS name, COUNT(*) AS total,"
                        + " SUM(CASE WHEN r.actual_qty IS NOT NULL THEN 1 ELSE 0 END) AS checked,"
                        + " SUM(CASE WHEN r.result=? THEN 1 ELSE 0 END) AS profit_cnt,"
                        + " SUM(CASE WHEN r.result=? THEN 1 ELSE 0 END) AS loss_cnt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN r.diff_amount ELSE 0 END),0) AS profit_amt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN -r.diff_amount ELSE 0 END),0) AS loss_amt"
                        + " FROM inv_record r GROUP BY r.subject_type",
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS,
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS);
        for (Map<String, Object> row : rows) {
            row.put("label", label(String.valueOf(row.get("name"))));
        }
        return rows;
    }

    /** 按分类统计差异金额 TOP10。 */
    public List<Map<String, Object>> categoryStats() {
        return jdbc.queryForList(
                "SELECT IFNULL(r.category,'未分类') AS name, COUNT(*) AS total,"
                        + " SUM(CASE WHEN r.result=? THEN 1 ELSE 0 END) AS profit_cnt,"
                        + " SUM(CASE WHEN r.result=? THEN 1 ELSE 0 END) AS loss_cnt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN r.diff_amount ELSE 0 END),0) AS profit_amt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN -r.diff_amount ELSE 0 END),0) AS loss_amt"
                        + " FROM inv_record r WHERE r.actual_qty IS NOT NULL"
                        + " GROUP BY IFNULL(r.category,'未分类')"
                        + " ORDER BY SUM(IFNULL(ABS(r.diff_amount),0)) DESC LIMIT 10",
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS,
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS);
    }

    /** 各任务进度。 */
    public List<Map<String, Object>> taskProgress() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, task_no, task_name, task_type, status, owner, deadline,"
                        + " subject_count, checked_count, normal_count, profit_count, loss_count, diff_amount"
                        + " FROM inv_task ORDER BY "
                        + " CASE status WHEN ? THEN 0 WHEN ? THEN 1 ELSE 2 END, id DESC LIMIT 12",
                InventoryService.TASK_RUNNING, InventoryService.TASK_DRAFT);
        for (Map<String, Object> row : rows) {
            BigDecimal total = dec(row.get("subject_count"));
            BigDecimal done = dec(row.get("checked_count"));
            row.put("progress", rate(done, total));
        }
        return rows;
    }

    /** 近 12 个月盘点趋势：任务数、已盘数、盘盈盘亏金额。 */
    public List<Map<String, Object>> monthlyTrend() {
        return jdbc.queryForList(
                "SELECT DATE_FORMAT(t.plan_date,'%Y-%m') AS ym, COUNT(DISTINCT t.id) AS task_cnt,"
                        + " SUM(CASE WHEN r.actual_qty IS NOT NULL THEN 1 ELSE 0 END) AS checked_cnt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN r.diff_amount ELSE 0 END),0) AS profit_amt,"
                        + " IFNULL(SUM(CASE WHEN r.result=? THEN -r.diff_amount ELSE 0 END),0) AS loss_amt"
                        + " FROM inv_task t LEFT JOIN inv_record r ON r.task_id=t.id"
                        + " GROUP BY DATE_FORMAT(t.plan_date,'%Y-%m') ORDER BY ym DESC LIMIT 12",
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS);
    }

    /** 盘点人员工作量。 */
    public List<Map<String, Object>> checkerStats() {
        return jdbc.queryForList(
                "SELECT checker AS name, COUNT(*) AS cnt,"
                        + " SUM(CASE WHEN result IN (?,?) THEN 1 ELSE 0 END) AS diff_cnt,"
                        + " MAX(check_time) AS last_time"
                        + " FROM inv_record WHERE checker IS NOT NULL AND checker<>''"
                        + " GROUP BY checker ORDER BY cnt DESC LIMIT 10",
                InventoryService.RESULT_PROFIT, InventoryService.RESULT_LOSS);
    }

    /** 账外（清单外补录）标的：账面为 0 却有实盘数。 */
    public List<Map<String, Object>> extraSubjects() {
        return jdbc.queryForList(
                "SELECT task_no, subject_name, subject_code, qr_code, actual_qty, unit, checker, check_time"
                        + " FROM inv_record WHERE book_qty = 0 AND IFNULL(actual_qty,0) > 0"
                        + " ORDER BY id DESC LIMIT 20");
    }

    /** 取某列去重非空值，供页面下拉筛选；未知表或列时返回空列表。 */
    public List<String> distinctValues(String table, String column) {
        try {
            return jdbc.queryForList(
                    "SELECT DISTINCT " + column + " FROM " + table
                            + " WHERE " + column + " IS NOT NULL AND " + column + " <> ''"
                            + " ORDER BY " + column, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 标的类型 → 中文名。 */
    public static String label(String type) {
        if (type == null) {
            return "其他";
        }
        switch (type) {
            case InventoryService.TYPE_ASSET:
                return "固定资产";
            case InventoryService.TYPE_GOODS:
                return "库存物资";
            case InventoryService.TYPE_CANTEEN:
                return "食堂物资";
            default:
                return type;
        }
    }

    private long count(String table, String where) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return n == null ? 0 : n;
    }

    private BigDecimal sum(String table, String column, String where) {
        BigDecimal v = jdbc.queryForObject(
                "SELECT IFNULL(SUM(" + column + "),0) FROM " + table + " WHERE " + where, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 百分比；分母为 0 时返回 0。 */
    private static BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    private static Object nvl(Object value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    /** 空列表兜底，避免页面拿到 null。 */
    public static List<Map<String, Object>> emptyRows() {
        return new ArrayList<>();
    }
}
