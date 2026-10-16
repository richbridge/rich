package fan.rich.service;

import fan.rich.entity.SalaryAlloc;
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
 * 工资模块统计：下拨 / 上报 / 发放的 KPI、分布、月度趋势与提醒。
 *
 * <p>下拨与上报均按核算部门管理：一个部门一个期间一张下拨单、一张上报单，
 * 部门上报单审核 / 发放后实发合计回填到本部门下拨单上。
 *
 * <p>所有金额口径：
 * <ul>
 *     <li>核定工资总额：上级批复的当月工资总额（含个人代扣部分），按部门分解</li>
 *     <li>下拨金额：上级实际拨付的资金（等于当月实发需求），按部门分解</li>
 *     <li>已发放金额：部门上报单实发合计，回填到 {@code salary_alloc.used_amount}</li>
 *     <li>结余金额：下拨金额 - 已发放金额</li>
 * </ul>
 */
@Service
public class SalaryStatsService {

    private final JdbcTemplate jdbc;

    public SalaryStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 工资总览。 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();

        // ------------------------------------------------------------------ KPI
        Map<String, Object> kpi = new HashMap<>();
        Map<String, Object> alloc = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt,"
                        + " IFNULL(SUM(approved_amount),0) AS approved,"
                        + " IFNULL(SUM(alloc_amount),0) AS alloced,"
                        + " IFNULL(SUM(used_amount),0) AS used,"
                        + " IFNULL(SUM(balance_amount),0) AS balance FROM salary_alloc");
        kpi.put("allocCount", alloc.get("cnt"));
        kpi.put("approvedAmount", alloc.get("approved"));
        kpi.put("allocAmount", alloc.get("alloced"));
        kpi.put("usedAmount", alloc.get("used"));
        kpi.put("balanceAmount", alloc.get("balance"));

        Map<String, Object> report = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt,"
                        + " IFNULL(SUM(CASE WHEN status IN ('已审核','已发放') THEN 1 ELSE 0 END),0) AS audited,"
                        + " IFNULL(SUM(CASE WHEN status = '待审核' THEN 1 ELSE 0 END),0) AS pending,"
                        + " IFNULL(SUM(CASE WHEN status = '草稿' THEN 1 ELSE 0 END),0) AS draft,"
                        + " IFNULL(SUM(CASE WHEN status = '已驳回' THEN 1 ELSE 0 END),0) AS rejected,"
                        + " IFNULL(SUM(CASE WHEN status = '已发放' THEN 1 ELSE 0 END),0) AS paid,"
                        + " IFNULL(SUM(CASE WHEN report_type = '部门工资表上报' THEN gross_amount ELSE 0 END),0) AS gross,"
                        + " IFNULL(SUM(CASE WHEN report_type = '部门工资表上报' THEN net_amount ELSE 0 END),0) AS net,"
                        + " IFNULL(SUM(CASE WHEN report_type = '部门工资表上报' THEN tax_amount ELSE 0 END),0) AS tax,"
                        + " IFNULL(SUM(CASE WHEN report_type = '部门工资表上报' THEN deduct_amount ELSE 0 END),0) AS deduct,"
                        + " IFNULL(SUM(CASE WHEN report_type = '部门工资表上报' THEN headcount ELSE 0 END),0) AS headcount"
                        + " FROM salary_report");
        kpi.putAll(report);
        // 部门维度：已下拨 / 已结清的部门单数
        kpi.put("deptAllocCount", countBy("SELECT COUNT(DISTINCT dept_name) FROM salary_alloc"
                + " WHERE dept_name IS NOT NULL AND dept_name <> ''"));
        kpi.put("deptSettled", countBy("SELECT COUNT(*) FROM salary_alloc WHERE status = '已结算'"));
        data.put("kpi", kpi);

        // ------------------------------------------------------------------ 当前期间
        Integer period = jdbc.queryForObject(
                "SELECT MAX(period_year * 100 + period_month) FROM salary_alloc", Integer.class);
        String periodLabel = "";
        Map<String, Object> current = new HashMap<>();
        if (period != null) {
            int year = period / 100;
            int month = period % 100;
            periodLabel = year + "-" + (month < 10 ? "0" + month : String.valueOf(month));
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT IFNULL(SUM(approved_amount),0) AS approved,"
                            + " IFNULL(SUM(alloc_amount),0) AS alloced,"
                            + " IFNULL(SUM(used_amount),0) AS used,"
                            + " IFNULL(SUM(balance_amount),0) AS balance,"
                            + " COUNT(*) AS cnt FROM salary_alloc WHERE period_year = ? AND period_month = ?",
                    year, month);
            current.putAll(row);
            Map<String, Object> r = jdbc.queryForMap(
                    "SELECT IFNULL(SUM(gross_amount),0) AS gross, IFNULL(SUM(net_amount),0) AS net,"
                            + " IFNULL(SUM(tax_amount),0) AS tax, IFNULL(SUM(headcount),0) AS headcount,"
                            + " COUNT(*) AS cnt, COUNT(DISTINCT dept_name) AS deptCount"
                            + " FROM salary_report r"
                            + " WHERE period_year = ? AND period_month = ? AND report_type = '部门工资表上报'",
                    year, month);
            current.put("reportGross", r.get("gross"));
            current.put("reportNet", r.get("net"));
            current.put("reportHeadcount", r.get("headcount"));
            current.put("reportCount", r.get("cnt"));
            current.put("deptCount", r.get("deptCount"));
        }
        current.put("period", periodLabel);
        data.put("current", current);

        // ------------------------------------------------------------------ 月度趋势
        data.put("trend", jdbc.queryForList(
                "SELECT CONCAT(a.period_year, '-', LPAD(a.period_month, 2, '0')) AS label,"
                        + " IFNULL(SUM(a.approved_amount),0) AS approved,"
                        + " IFNULL(SUM(a.alloc_amount),0) AS alloced,"
                        + " IFNULL(SUM(a.used_amount),0) AS used,"
                        + " IFNULL(SUM(a.balance_amount),0) AS balance"
                        + " FROM salary_alloc a GROUP BY a.period_year, a.period_month"
                        + " ORDER BY a.period_year, a.period_month"));

        // ------------------------------------------------------------------ 分布
        data.put("allocTypes", jdbc.queryForList(
                "SELECT IFNULL(alloc_type,'未分类') AS name, COUNT(*) AS value,"
                        + " IFNULL(SUM(alloc_amount),0) AS amount FROM salary_alloc"
                        + " GROUP BY alloc_type ORDER BY value DESC"));
        data.put("reportStatus", jdbc.queryForList(
                "SELECT IFNULL(status,'未分类') AS name, COUNT(*) AS value FROM salary_report"
                        + " GROUP BY status ORDER BY value DESC"));

        // ------------------------------------------------------ 部门分布（最新期间：下拨 vs 上报）
        List<Map<String, Object>> deptStats = new ArrayList<>();
        if (period != null) {
            deptStats = jdbc.queryForList(
                    "SELECT IFNULL(a.dept_name, r.dept_name) AS name,"
                            + " IFNULL(a.alloced,0) AS alloced, IFNULL(a.used,0) AS used,"
                            + " IFNULL(a.balance,0) AS balance, IFNULL(a.headcount,0) AS allocHeadcount,"
                            + " IFNULL(r.headcount,0) AS headcount, IFNULL(r.gross,0) AS gross,"
                            + " IFNULL(r.net,0) AS net, IFNULL(r.cnt,0) AS reportCount"
                            + " FROM (SELECT dept_name, SUM(alloc_amount) AS alloced,"
                            + " SUM(used_amount) AS used, SUM(balance_amount) AS balance,"
                            + " SUM(IFNULL(headcount,0)) AS headcount"
                            + " FROM salary_alloc WHERE period_year = ? AND period_month = ?"
                            + " GROUP BY dept_name) a"
                            + " LEFT JOIN (SELECT dept_name, SUM(headcount) AS headcount,"
                            + " SUM(gross_amount) AS gross, SUM(net_amount) AS net, COUNT(*) AS cnt"
                            + " FROM salary_report WHERE period_year = ? AND period_month = ?"
                            + " AND report_type = '部门工资表上报' GROUP BY dept_name) r"
                            + " ON a.dept_name = r.dept_name"
                            + " ORDER BY alloced DESC",
                    period / 100, period % 100, period / 100, period % 100);
        }
        data.put("deptStats", deptStats);

        // ------------------------------------------------------------------ 提醒
        Map<String, Object> reminders = new HashMap<>();
        reminders.put("pendingAudit", countBy("SELECT COUNT(*) FROM salary_report WHERE status = '待审核'"));
        reminders.put("rejected", countBy("SELECT COUNT(*) FROM salary_report WHERE status = '已驳回'"));
        reminders.put("draft", countBy("SELECT COUNT(*) FROM salary_report WHERE status = '草稿'"));
        reminders.put("waitAlloc", countBy("SELECT COUNT(*) FROM salary_alloc WHERE status = '待下拨'"));
        reminders.put("lowBalance", countBy("SELECT COUNT(*) FROM salary_alloc"
                + " WHERE balance_amount < 0"));
        data.put("reminders", reminders);

        // ------------------------------------------------------------------ 近期下拨
        data.put("recentAlloc", jdbc.queryForList(
                "SELECT alloc_no, period_year, period_month, alloc_type, dept_name, source_unit,"
                        + " approved_amount, alloc_amount, used_amount, balance_amount, alloc_date, status"
                        + " FROM salary_alloc ORDER BY period_year DESC, period_month DESC, id DESC LIMIT 12"));

        return data;
    }

    /** 重算某张下拨单的已发放金额与结余，并同步状态。 */
    public void refreshAlloc(Long allocId) {
        if (allocId == null) {
            return;
        }
        jdbc.update("UPDATE salary_alloc a SET used_amount = IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0),"
                        + " balance_amount = a.alloc_amount - IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0)"
                        + " WHERE a.id = ?",
                allocId);
        jdbc.update("UPDATE salary_alloc SET status = CASE"
                + " WHEN status IN ('待下拨','已结算') THEN status"
                + " WHEN used_amount <= 0 THEN '已下拨'"
                + " WHEN balance_amount <= 0 THEN '已结算'"
                + " ELSE '部分使用' END WHERE id = ?", allocId);
    }

    /** 重算全部下拨单。 */
    public void refreshAll() {
        jdbc.update("UPDATE salary_alloc a SET used_amount = IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0),"
                        + " balance_amount = a.alloc_amount - IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0)");
        jdbc.update("UPDATE salary_alloc SET status = CASE"
                + " WHEN status IN ('待下拨','已结算') THEN status"
                + " WHEN used_amount <= 0 THEN '已下拨'"
                + " WHEN balance_amount <= 0 THEN '已结算'"
                + " ELSE '部分使用' END");
    }

    /**
     * 按核算部门批量生成（或更新）下拨单：把期间核定总额与下拨金额按权重分解到各部门。
     *
     * @param year         所属年度
     * @param month        所属月份
     * @param allocType    下拨类型
     * @param sourceUnit   拨付单位
     * @param targetUnit   接收单位
     * @param approvedTotal 核定工资总额
     * @param allocTotal   实际下拨金额
     * @param allocDate    下拨日期
     * @param voucherNo    拨付文号
     * @param handler      经办人
     * @param mode         分摊方式：{@code quota} 按部门月度工资定额 / {@code headcount} 按在册人数 /
     *                     {@code avg} 平均分摊
     * @return 生成 / 更新的下拨单数量
     */
    public int generateDeptAllocs(int year, int month, String allocType, String sourceUnit,
                                  String targetUnit, BigDecimal approvedTotal, BigDecimal allocTotal,
                                  LocalDate allocDate, String voucherNo, String handler, String mode) {
        List<Map<String, Object>> depts = jdbc.queryForList(
                "SELECT id, dept_code, dept_name, IFNULL(headcount,0) AS headcount,"
                        + " IFNULL(month_quota,0) AS quota FROM salary_dept"
                        + " WHERE IFNULL(status,'启用') = '启用' ORDER BY dept_code");
        if (depts.isEmpty()) {
            return 0;
        }
        // ---------------------------------------------------------------- 权重
        double[] weights = new double[depts.size()];
        double sum = 0;
        for (int i = 0; i < depts.size(); i++) {
            Map<String, Object> d = depts.get(i);
            double w;
            if ("headcount".equals(mode)) {
                w = ((Number) d.get("headcount")).doubleValue();
            } else if ("avg".equals(mode)) {
                w = 1;
            } else {
                w = ((Number) d.get("quota")).doubleValue();
            }
            if (w <= 0) {
                w = 0;
            }
            weights[i] = w;
            sum += w;
        }
        if (sum <= 0) {
            // 定额与人数都为空时退化为平均分摊
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1;
            }
            sum = weights.length;
        }
        // ---------------------------------------------------------------- 分摊（末位补齐差额）
        BigDecimal approvedSum = BigDecimal.ZERO;
        BigDecimal allocSum = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>();
        String seqFormat = "%02d";
        for (int i = 0; i < depts.size(); i++) {
            Map<String, Object> d = depts.get(i);
            boolean last = i == depts.size() - 1;
            BigDecimal approved;
            BigDecimal allocAmount;
            if (last) {
                approved = approvedTotal.subtract(approvedSum);
                allocAmount = allocTotal.subtract(allocSum);
            } else {
                approved = approvedTotal.multiply(BigDecimal.valueOf(weights[i] / sum))
                        .setScale(2, RoundingMode.HALF_UP);
                allocAmount = allocTotal.multiply(BigDecimal.valueOf(weights[i] / sum))
                        .setScale(2, RoundingMode.HALF_UP);
                approvedSum = approvedSum.add(approved);
                allocSum = allocSum.add(allocAmount);
            }
            String allocNo = String.format("XB%d-%s-" + seqFormat, year,
                    month < 10 ? "0" + month : String.valueOf(month), i + 1);
            jdbc.update("INSERT INTO salary_alloc (alloc_no, period_year, period_month, alloc_type,"
                            + " dept_id, dept_name, headcount, source_unit, target_unit,"
                            + " approved_amount, alloc_amount, used_amount, balance_amount,"
                            + " alloc_date, voucher_no, handler, status)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?)"
                            + " ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id),"
                            + " dept_name = VALUES(dept_name), headcount = VALUES(headcount),"
                            + " source_unit = VALUES(source_unit), target_unit = VALUES(target_unit),"
                            + " approved_amount = VALUES(approved_amount),"
                            + " alloc_amount = VALUES(alloc_amount), alloc_date = VALUES(alloc_date),"
                            + " voucher_no = VALUES(voucher_no), handler = VALUES(handler)",
                    allocNo, year, month, allocType,
                    ((Number) d.get("id")).longValue(), d.get("dept_name"),
                    ((Number) d.get("headcount")).intValue(),
                    sourceUnit, targetUnit, approved, allocAmount, allocAmount,
                    allocDate, voucherNo, handler, SalaryAlloc.STATUS_WAIT);
            Long id = queryLong("SELECT id FROM salary_alloc WHERE alloc_no = ?", allocNo);
            if (id != null) {
                ids.add(id);
            }
        }
        for (Long id : ids) {
            refreshAlloc(id);
        }
        return ids.size();
    }

    /** 查询某部门某期间的部门下拨单ID（用于上报单自动关联）。 */
    public Long findAllocId(Integer year, Integer month, String deptName) {
        if (year == null || month == null || deptName == null || deptName.isEmpty()) {
            return null;
        }
        return queryLong("SELECT id FROM salary_alloc WHERE period_year = ? AND period_month = ?"
                + " AND dept_name = ? ORDER BY id LIMIT 1", year, month, deptName);
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按工资明细汇总回填上报单的人数与金额。
     *
     * @param reportId 上报单ID
     * @return 汇总后的人数
     */
    public int refreshReportFromDetail(Long reportId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS headcount,"
                        + " IFNULL(SUM(gross_amount),0) AS gross,"
                        + " IFNULL(SUM(pension + unemployment + medical + housing_fund + annuity),0) AS deduct,"
                        + " IFNULL(SUM(tax),0) AS tax,"
                        + " IFNULL(SUM(net_amount),0) AS net"
                        + " FROM salary_detail WHERE report_id = ?", reportId);
        jdbc.update("UPDATE salary_report SET headcount = ?, gross_amount = ?, deduct_amount = ?,"
                        + " tax_amount = ?, net_amount = ? WHERE id = ?",
                row.get("headcount"), row.get("gross"), row.get("deduct"),
                row.get("tax"), row.get("net"), reportId);
        return ((Number) row.get("headcount")).intValue();
    }

    /** 明细行：工资总额 = 各工资项之和。 */
    public BigDecimal calcGross(BigDecimal base, BigDecimal night, BigDecimal down,
                                BigDecimal subsidy, BigDecimal overtime, BigDecimal perf,
                                BigDecimal safety, BigDecimal manage, BigDecimal other) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : new BigDecimal[]{base, night, down, subsidy, overtime, perf, safety, manage, other}) {
            sum = sum.add(v == null ? BigDecimal.ZERO : v);
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** 明细行：实发工资 = 工资总额 - 代扣 - 个税。 */
    public BigDecimal calcNet(BigDecimal gross, BigDecimal pension, BigDecimal unemployment,
                              BigDecimal medical, BigDecimal housingFund, BigDecimal annuity, BigDecimal tax) {
        BigDecimal deduct = BigDecimal.ZERO;
        for (BigDecimal v : new BigDecimal[]{pension, unemployment, medical, housingFund, annuity, tax}) {
            deduct = deduct.add(v == null ? BigDecimal.ZERO : v);
        }
        return (gross == null ? BigDecimal.ZERO : gross).subtract(deduct).setScale(2, RoundingMode.HALF_UP);
    }

    private long countBy(String sql) {
        Long total = jdbc.queryForObject(sql, Long.class);
        return total == null ? 0 : total;
    }
}
