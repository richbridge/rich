package fan.rich.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食堂统计：经营情况的 KPI、月度收支趋势、分类分布与预警提醒。
 *
 * <p><b>经营口径</b>：食堂的"利润"是内部核算口径，不是会计利润——
 * <pre>
 * 收入     = 就餐收入（个人实付 + 公司餐补）
 * 食材成本 = 当期领用出库金额（职工餐 / 加班餐等消耗）
 * 费用     = 除食材采购外的运行性支出（燃料动力、水电燃气、维修、劳保等）
 * 结余     = 收入 − 食材成本 − 费用
 * </pre>
 * 采购入库只增加库存资产，不直接进入成本；成本在领用出库时按加权平均价结转，
 * 这与企业会计准则的存货核算一致，也避免了"采购即费用"导致成本忽高忽低。
 *
 * <p>所有查询均以 MySQL 5.7 为目标编写，不使用 8.0 才有的窗口函数。
 */
@Service
public class CanteenStatsService {

    private final JdbcTemplate jdbc;

    public CanteenStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 食堂经营总览。 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        // ------------------------------------------------------------------ KPI
        Map<String, Object> kpi = new HashMap<>();
        // 就餐收入（应收 = 实付 + 补贴）
        Map<String, Object> sale = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(people_count),0) AS people,"
                        + " IFNULL(SUM(receivable),0) AS income, IFNULL(SUM(subsidy_amount),0) AS subsidy"
                        + " FROM canteen_sale WHERE YEAR(biz_date)=? AND MONTH(biz_date)=?"
                        + " AND status <> '草稿'", year, month);
        // 食材成本：当期领用出库（不含报损与福利发放，这两项不属于主营成本）
        Map<String, Object> cost = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(total_amount),0) AS amount,"
                        + " IFNULL(SUM(people_count),0) AS people"
                        + " FROM canteen_outbound WHERE YEAR(out_date)=? AND MONTH(out_date)=?"
                        + " AND status <> '草稿'", year, month);
        // 运行费用
        Map<String, Object> fee = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(total_amount),0) AS amount"
                        + " FROM canteen_fee WHERE YEAR(fee_date)=? AND MONTH(fee_date)=?"
                        + " AND status <> '草稿'", year, month);
        // 采购入库（当期入库额，属资产增加）
        Map<String, Object> purchase = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(total_with_tax),0) AS amount,"
                        + " IFNULL(SUM(total_with_tax - paid_amount),0) AS unpaid"
                        + " FROM canteen_purchase WHERE YEAR(purchase_date)=? AND MONTH(purchase_date)=?",
                year, month);

        BigDecimal income = dec(sale.get("income"));
        BigDecimal subsidy = dec(sale.get("subsidy"));
        BigDecimal materialCost = dec(cost.get("amount"));
        BigDecimal feeAmount = dec(fee.get("amount"));
        BigDecimal profit = income.subtract(materialCost).subtract(feeAmount);

        kpi.put("period", year + "年" + month + "月");
        kpi.put("saleCount", sale.get("cnt"));
        kpi.put("peopleCount", sale.get("people"));
        kpi.put("peopleCost", safeDivide(materialCost, dec(sale.get("people"))));
        kpi.put("saleIncome", income);
        kpi.put("subsidyAmount", subsidy);
        kpi.put("materialCost", materialCost);
        kpi.put("outCount", cost.get("cnt"));
        kpi.put("feeAmount", feeAmount);
        kpi.put("feeCount", fee.get("cnt"));
        kpi.put("profit", profit);
        kpi.put("profitRate", safeDivide(profit.multiply(BigDecimal.valueOf(100)), income));

        Map<String, Object> stock = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(total_amount),0) AS amount"
                        + " FROM canteen_stock WHERE quantity > 0");
        kpi.put("stockCount", stock.get("cnt"));
        kpi.put("stockAmount", stock.get("amount"));

        Map<String, Object> card = jdbc.queryForMap(
                "SELECT IFNULL(COUNT(*),0) AS cnt, IFNULL(SUM(balance),0) AS balance FROM canteen_card"
                        + " WHERE status = '正常'");
        kpi.put("cardCount", card.get("cnt"));
        kpi.put("cardBalance", card.get("balance"));

        Map<String, Object> acc = jdbc.queryForMap(
                "SELECT IFNULL(SUM(CASE WHEN direction='收入' THEN amount ELSE -amount END),0) AS balance,"
                        + " IFNULL(COUNT(*),0) AS cnt FROM canteen_account WHERE status='已记账'");
        kpi.put("accountBalance", acc.get("balance"));
        kpi.put("accountCount", acc.get("cnt"));

        Map<String, Object> unpaid = jdbc.queryForMap(
                "SELECT IFNULL(SUM(total_with_tax - paid_amount),0) AS amount,"
                        + " IFNULL(SUM(CASE WHEN total_with_tax - paid_amount > 0 THEN 1 ELSE 0 END),0) AS cnt"
                        + " FROM canteen_purchase WHERE status <> '草稿'");
        kpi.put("unpaidAmount", unpaid.get("amount"));
        kpi.put("unpaidCount", unpaid.get("cnt"));
        kpi.put("purchaseAmount", purchase.get("amount"));
        kpi.put("purchaseCount", purchase.get("cnt"));

        // 累计
        Map<String, Object> total = jdbc.queryForMap(
                "SELECT IFNULL(SUM(receivable),0) AS income, IFNULL(SUM(people_count),0) AS people"
                        + " FROM canteen_sale WHERE status <> '草稿'");
        Map<String, Object> totalCost = jdbc.queryForMap(
                "SELECT IFNULL(SUM(total_amount),0) AS cost FROM canteen_outbound WHERE status <> '草稿'");
        Map<String, Object> totalFee = jdbc.queryForMap(
                "SELECT IFNULL(SUM(total_amount),0) AS fee FROM canteen_fee WHERE status <> '草稿'");
        kpi.put("totalIncome", total.get("income"));
        kpi.put("totalPeople", total.get("people"));
        kpi.put("totalCost", totalCost.get("cost"));
        kpi.put("totalFee", totalFee.get("fee"));
        kpi.put("totalProfit", dec(total.get("income"))
                .subtract(dec(totalCost.get("cost"))).subtract(dec(totalFee.get("fee"))));
        data.put("kpi", kpi);

        // ------------------------------------------------------------------ 月度收支趋势
        data.put("trend", jdbc.queryForList(
                "SELECT DATE_FORMAT(d.ym_date, '%Y-%m') AS ym,"
                        + " IFNULL((SELECT SUM(receivable) FROM canteen_sale s"
                        + "   WHERE DATE_FORMAT(s.biz_date,'%Y-%m-01')=d.ym_date AND s.status<>'草稿'),0) AS income,"
                        + " IFNULL((SELECT SUM(total_amount) FROM canteen_outbound o"
                        + "   WHERE DATE_FORMAT(o.out_date,'%Y-%m-01')=d.ym_date AND o.status<>'草稿'),0) AS cost,"
                        + " IFNULL((SELECT SUM(total_amount) FROM canteen_fee f"
                        + "   WHERE DATE_FORMAT(f.fee_date,'%Y-%m-01')=d.ym_date AND f.status<>'草稿'),0) AS fee"
                        + " FROM (SELECT DISTINCT DATE_FORMAT(biz_date,'%Y-%m-01') AS ym_date FROM canteen_sale"
                        + "       UNION SELECT DISTINCT DATE_FORMAT(out_date,'%Y-%m-01') FROM canteen_outbound"
                        + "       UNION SELECT DISTINCT DATE_FORMAT(fee_date,'%Y-%m-01') FROM canteen_fee"
                        + "      ) d"
                        + " ORDER BY d.ym_date DESC LIMIT 12"));

        // ------------------------------------------------------------------ 分布
        data.put("mealDist", jdbc.queryForList(
                "SELECT meal_type AS name, SUM(receivable) AS value FROM canteen_sale"
                        + " WHERE status<>'草稿' GROUP BY meal_type ORDER BY value DESC"));

        data.put("custDist", jdbc.queryForList(
                "SELECT customer_type AS name, SUM(receivable) AS value FROM canteen_sale"
                        + " WHERE status<>'草稿' GROUP BY customer_type ORDER BY value DESC"));

        data.put("stockDist", jdbc.queryForList(
                "SELECT IFNULL(category,'未分类') AS name, SUM(total_amount) AS value"
                        + " FROM canteen_stock WHERE quantity > 0 GROUP BY category ORDER BY value DESC"));

        data.put("supplierTop", jdbc.queryForList(
                "SELECT supplier_name AS name, SUM(total_with_tax) AS value FROM canteen_purchase"
                        + " WHERE status<>'草稿' GROUP BY supplier_name ORDER BY value DESC LIMIT 8"));

        data.put("materialCostTop", jdbc.queryForList(
                "SELECT material_name AS name, SUM(amount) AS value FROM canteen_outbound_item"
                        + " WHERE category IS NOT NULL GROUP BY material_name ORDER BY value DESC LIMIT 10"));

        data.put("feeDist", jdbc.queryForList(
                "SELECT fee_type AS name, SUM(total_amount) AS value FROM canteen_fee"
                        + " WHERE status<>'草稿' GROUP BY fee_type ORDER BY value DESC"));

        // ------------------------------------------------------------------ 预警
        Map<String, Object> warn = new HashMap<>();
        warn.put("lowStock", jdbc.queryForObject(
                "SELECT COUNT(*) FROM canteen_stock s WHERE s.min_stock IS NOT NULL"
                        + " AND s.min_stock > 0 AND s.quantity < s.min_stock", Integer.class));
        warn.put("overStock", jdbc.queryForObject(
                "SELECT COUNT(*) FROM canteen_stock s WHERE s.max_stock IS NOT NULL"
                        + " AND s.max_stock > 0 AND s.quantity > s.max_stock", Integer.class));
        warn.put("unpaidPurchase", jdbc.queryForList(
                "SELECT purchase_no, supplier_name, total_with_tax - paid_amount AS amount, purchase_date"
                        + " FROM canteen_purchase WHERE status<>'草稿' AND total_with_tax - paid_amount > 0"
                        + " ORDER BY amount DESC LIMIT 8"));
        warn.put("lowStockList", jdbc.queryForList(
                "SELECT material_name, unit, quantity, min_stock FROM canteen_stock"
                        + " WHERE min_stock IS NOT NULL AND min_stock > 0 AND quantity < min_stock"
                        + " ORDER BY quantity ASC LIMIT 10"));
        warn.put("unpaidFee", jdbc.queryForObject(
                "SELECT IFNULL(SUM(total_amount),0) FROM canteen_fee"
                        + " WHERE status<>'草稿' AND pay_status='未付款'", BigDecimal.class));
        warn.put("draftBill", jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM canteen_purchase WHERE status='草稿')"
                        + " + (SELECT COUNT(*) FROM canteen_outbound WHERE status='草稿')"
                        + " + (SELECT COUNT(*) FROM canteen_sale WHERE status='草稿')"
                        + " + (SELECT COUNT(*) FROM canteen_fee WHERE status='草稿')", Integer.class));
        warn.put("negativeStock", jdbc.queryForObject(
                "SELECT COUNT(*) FROM canteen_stock WHERE quantity < 0", Integer.class));
        data.put("warn", warn);

        // ------------------------------------------------------------------ 近期单据
        data.put("recentPurchase", jdbc.queryForList(
                "SELECT purchase_no, supplier_name, purchase_date, total_with_tax, pay_status, status"
                        + " FROM canteen_purchase ORDER BY purchase_date DESC, id DESC LIMIT 10"));
        data.put("recentOutbound", jdbc.queryForList(
                "SELECT out_no, out_type, window_name, out_date, total_amount, people_count, status"
                        + " FROM canteen_outbound ORDER BY out_date DESC, id DESC LIMIT 10"));
        data.put("recentSale", jdbc.queryForList(
                "SELECT sale_no, biz_date, meal_type, customer_type, people_count, receivable, status"
                        + " FROM canteen_sale ORDER BY biz_date DESC, id DESC LIMIT 10"));
        data.put("recentAccount", jdbc.queryForList(
                "SELECT biz_date, voucher_no, direction, account_type, amount, balance, counterparty, summary"
                        + " FROM canteen_account WHERE status='已记账'"
                        + " ORDER BY biz_date DESC, id DESC LIMIT 12"));

        return data;
    }

    /** 库存预警列表（低于安全库存 / 超出最高库存）。 */
    public List<Map<String, Object>> stockWarnings() {
        return jdbc.queryForList(
                "SELECT s.material_name, s.category, s.unit, s.quantity, s.avg_price, s.total_amount,"
                        + " s.min_stock, s.max_stock,"
                        + " CASE WHEN s.quantity < 0 THEN '负库存'"
                        + "      WHEN s.min_stock > 0 AND s.quantity < s.min_stock THEN '低于安全库存'"
                        + "      WHEN s.max_stock > 0 AND s.quantity > s.max_stock THEN '超出最高库存'"
                        + "      ELSE '正常' END AS warn_level"
                        + " FROM canteen_stock s"
                        + " WHERE s.quantity < 0"
                        + "    OR (s.min_stock > 0 AND s.quantity < s.min_stock)"
                        + "    OR (s.max_stock > 0 AND s.quantity > s.max_stock)"
                        + " ORDER BY FIELD(warn_level,'负库存','低于安全库存','超出最高库存'),"
                        + "          s.total_amount ASC");
    }

    /** 单个物料的收发存明细（存货卡片）。 */
    public Map<String, Object> materialCard(Long materialId) {
        Map<String, Object> data = new HashMap<>();
        data.put("stock", jdbc.queryForList(
                "SELECT material_name, unit, quantity, avg_price, total_amount,"
                        + " last_in_date, last_out_date FROM canteen_stock WHERE material_id=?",
                materialId));
        List<Map<String, Object>> flows = new ArrayList<>();
        flows.addAll(jdbc.queryForList(
                "SELECT p.purchase_date AS biz_date, '采购入库' AS flow_type,"
                        + " pi.purchase_no AS biz_no, pi.quantity, pi.unit_price, pi.amount,"
                        + " p.supplier_name AS counterparty"
                        + " FROM canteen_purchase_item pi JOIN canteen_purchase p ON p.id=pi.purchase_id"
                        + " WHERE pi.material_id=? AND p.status IN ('已入库','已结算')"
                        + " ORDER BY p.purchase_date DESC, pi.id DESC LIMIT 50", materialId));
        flows.addAll(jdbc.queryForList(
                "SELECT o.out_date AS biz_date, o.out_type AS flow_type,"
                        + " oi.out_no AS biz_no, oi.quantity, oi.unit_price, oi.amount,"
                        + " o.dept_name AS counterparty"
                        + " FROM canteen_outbound_item oi JOIN canteen_outbound o ON o.id=oi.out_id"
                        + " WHERE oi.material_id=? AND o.status IN ('已出库','已记账')"
                        + " ORDER BY o.out_date DESC, oi.id DESC LIMIT 50", materialId));
        flows.sort((a, b) -> String.valueOf(b.get("biz_date"))
                .compareTo(String.valueOf(a.get("biz_date"))));
        data.put("flows", flows);
        return data;
    }

    /** 月度经营情况表（收入 / 成本 / 费用 / 结余 / 人次）。 */
    public List<Map<String, Object>> monthlyReport(int year) {
        return jdbc.queryForList(
                "SELECT d.ym, IFNULL(s.income,0) AS income, IFNULL(s.people,0) AS people,"
                        + " IFNULL(o.cost,0) AS cost, IFNULL(f.fee,0) AS fee,"
                        + " IFNULL(s.income,0) - IFNULL(o.cost,0) - IFNULL(f.fee,0) AS profit"
                        + " FROM (SELECT DATE_FORMAT(DATE_ADD(?, INTERVAL m.n MONTH),'%Y-%m') AS ym"
                        + "       FROM (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3"
                        + "             UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7"
                        + "             UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11) m) d"
                        + " LEFT JOIN (SELECT DATE_FORMAT(biz_date,'%Y-%m') ym, SUM(receivable) income,"
                        + "      SUM(people_count) people FROM canteen_sale WHERE status<>'草稿'"
                        + "      GROUP BY ym) s ON s.ym=d.ym"
                        + " LEFT JOIN (SELECT DATE_FORMAT(out_date,'%Y-%m') ym, SUM(total_amount) cost"
                        + "      FROM canteen_outbound WHERE status<>'草稿' GROUP BY ym) o ON o.ym=d.ym"
                        + " LEFT JOIN (SELECT DATE_FORMAT(fee_date,'%Y-%m') ym, SUM(total_amount) fee"
                        + "      FROM canteen_fee WHERE status<>'草稿' GROUP BY ym) f ON f.ym=d.ym"
                        + " ORDER BY d.ym", year + "-01-01");
    }

    /**
     * 按科目汇总收支发生额。
     *
     * @return 收入在前、支出在后，各自按金额降序，用于财务页的科目构成表
     */
    public List<Map<String, Object>> accountSummary(int year) {
        return jdbc.queryForList(
                "SELECT direction, account_type, COUNT(*) AS cnt, SUM(amount) AS amount"
                        + " FROM canteen_account WHERE YEAR(biz_date)=? AND status='已记账'"
                        + " GROUP BY direction, account_type"
                        + " ORDER BY direction DESC, amount DESC", year);
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal safeDivide(BigDecimal a, BigDecimal b) {
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, 2, java.math.RoundingMode.HALF_UP);
    }
}
