package fan.rich.config;

import fan.rich.entity.CanteenOutbound;
import fan.rich.entity.CanteenPurchase;
import fan.rich.service.CanteenBizService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食堂模块初始化器：食材物料、供应商、饭卡、采购入库、领用出库、库存、盘点、就餐收入、费用与收支台账。
 *
 * <p>应用启动后按 {@link CanteenSchemaDdl} 建表（{@code CREATE TABLE IF NOT EXISTS}），
 * 表为空时写入 2026 年 1—8 月的演示数据，并把单据一次性过账到"已入库 / 已出库 / 已确认"状态，
 * 最后重算库存结存与流水余额，保证进销存与财务两侧口径自洽。
 *
 * <p>已存在数据（以入库单号 {@code CG2026-0001} 为标记）时不再重复写入。
 * 需要重写演示数据时清空 {@code canteen_*} 各表后重启即可；{@code rich.canteen.enabled=false}
 * 可整体关闭。
 */
@Order(6)
@Component
public class CanteenDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CanteenDataInitializer.class);

    /** 演示数据标记：首张采购入库单号。 */
    private static final String MARK_PURCHASE = "CG2026-0001";

    /** 演示数据的起始月份。 */
    private static final int START_MONTH = 1;

    /** 演示数据所属年度。 */
    private static final int YEAR = 2026;

    /**
     * 演示数据的结束月份：取启动时的当前月。
     *
     * <p>经营总览的当期 KPI 按系统当前年月取值，演示数据必须铺到当前月，
     * 否则当月没有业务数据、KPI 会全为 0。
     */
    private int endMonth() {
        return LocalDate.now().getMonthValue();
    }

    /** 指定月份的最后一天。 */
    private static LocalDate monthEnd(int month) {
        LocalDate first = LocalDate.of(YEAR, month, 1);
        return first.withDayOfMonth(first.lengthOfMonth());
    }

    private static final int S_CODE = 0;
    private static final int S_NAME = 1;
    private static final int S_CATEGORY = 2;
    private static final int S_CONTACT = 3;
    private static final int S_PHONE = 4;
    private static final int S_SETTLE = 5;

    /** 供应商：编码 / 名称 / 品类 / 联系人 / 电话 / 结算方式。 */
    private static final String[][] SUPPLIERS = {
            {"GYS-01", "招远市华兴粮油商贸有限公司", "粮油副食", "王建国", "1380546****", "月结"},
            {"GYS-02", "招远市金岭肉禽供应站", "肉禽水产", "刘振宇", "1390546****", "月结"},
            {"GYS-03", "招远市海纳水产有限公司", "肉禽水产", "孙明", "1360546****", "月结"},
            {"GYS-04", "招远市绿源蔬菜专业合作社", "蔬菜水果", "张翠华", "1370546****", "现结"},
            {"GYS-05", "烟台市盈丰调味品有限公司", "调料饮品", "李春梅", "1350546****", "月结"},
            {"GYS-06", "招远市鑫达燃气供应站", "燃料燃气", "曲立新", "1300546****", "月结"},
            {"GYS-07", "招远市利民劳保用品商店", "劳保用品", "赵金凤", "1880546****", "季结"},
            {"GYS-08", "招远市康洁保洁服务有限公司", "劳保用品", "孙丽", "1860546****", "月结"},
            {"GYS-09", "国网山东招远市供电公司", "燃料燃气", "营业厅", "95598", "月结"},
            {"GYS-10", "招远市自来水有限公司", "燃料燃气", "营业厅", "0546-*******", "月结"},
            {"GYS-11", "招远市恒兴厨具维修部", "设备维修", "陈大勇", "1500546****", "现结"},
            {"GYS-12", "烟台市质检技术服务有限公司", "劳保用品", "周敏", "1330546****", "季结"},
    };

    /** 物料：名称 / 分类 / 规格 / 单位 / 单价 / 安全库存 / 最高库存 / 保质天数 / 供应商序号。 */
    private static final int M_NAME = 0;
    private static final int M_CATEGORY = 1;
    private static final int M_SPEC = 2;
    private static final int M_UNIT = 3;
    private static final int M_PRICE = 4;
    private static final int M_MIN = 5;
    private static final int M_MAX = 6;
    private static final int M_SHELF = 7;
    private static final int M_SUPPLIER = 8;

    private static final String[][] MATERIALS = {
            {"大米", "粮油米面", "25kg/袋", "袋", "128.0000", "20", "120", "180", "1"},
            {"面粉", "粮油米面", "25kg/袋", "袋", "98.0000", "15", "100", "180", "1"},
            {"花生油", "粮油米面", "5L/桶", "桶", "89.5000", "10", "60", "540", "1"},
            {"调和油", "粮油米面", "20L/桶", "桶", "218.0000", "4", "24", "540", "1"},
            {"小米", "粮油米面", "10kg/袋", "袋", "76.0000", "5", "40", "365", "1"},
            {"挂面", "粮油米面", "5kg/箱", "箱", "42.0000", "6", "40", "365", "1"},
            {"猪肉", "肉禽蛋", "整片/斤", "斤", "14.8000", "60", "400", "3", "2"},
            {"牛腩", "肉禽蛋", "整块/斤", "斤", "32.5000", "20", "150", "3", "2"},
            {"鸡胸肉", "肉禽蛋", "箱装/斤", "斤", "9.6000", "40", "300", "7", "2"},
            {"鸡腿", "肉禽蛋", "箱装/斤", "斤", "11.2000", "30", "220", "7", "2"},
            {"鸡蛋", "肉禽蛋", "30斤/箱", "箱", "196.0000", "8", "50", "20", "2"},
            {"大骨", "肉禽蛋", "整片/斤", "斤", "12.4000", "10", "80", "3", "2"},
            {"带鱼", "水产海鲜", "箱装/斤", "斤", "18.6000", "15", "120", "90", "3"},
            {"海虾", "水产海鲜", "箱装/斤", "斤", "38.0000", "5", "60", "90", "3"},
            {"鲅鱼", "水产海鲜", "箱装/斤", "斤", "16.8000", "8", "80", "90", "3"},
            {"白菜", "蔬菜豆制品", "散装/斤", "斤", "1.2000", "80", "600", "5", "4"},
            {"土豆", "蔬菜豆制品", "散装/斤", "斤", "1.8000", "100", "700", "15", "4"},
            {"西红柿", "蔬菜豆制品", "散装/斤", "斤", "3.6000", "40", "300", "5", "4"},
            {"黄瓜", "蔬菜豆制品", "散装/斤", "斤", "2.8000", "40", "300", "5", "4"},
            {"茄子", "蔬菜豆制品", "散装/斤", "斤", "3.2000", "30", "240", "5", "4"},
            {"青椒", "蔬菜豆制品", "散装/斤", "斤", "4.2000", "25", "200", "5", "4"},
            {"洋葱", "蔬菜豆制品", "散装/斤", "斤", "1.9000", "40", "300", "20", "4"},
            {"萝卜", "蔬菜豆制品", "散装/斤", "斤", "1.5000", "40", "300", "15", "4"},
            {"豆腐", "蔬菜豆制品", "散装/斤", "斤", "2.6000", "20", "160", "2", "4"},
            {"粉条", "蔬菜豆制品", "10kg/箱", "箱", "68.0000", "5", "40", "365", "4"},
            {"生抽", "调料饮品", "5L/桶", "桶", "32.0000", "6", "40", "720", "5"},
            {"食盐", "调料饮品", "1kg/袋", "袋", "3.5000", "20", "150", "1095", "5"},
            {"白糖", "调料饮品", "1kg/袋", "袋", "7.8000", "10", "80", "540", "5"},
            {"醋", "调料饮品", "5L/桶", "桶", "18.0000", "4", "30", "720", "5"},
            {"料酒", "调料饮品", "5L/桶", "桶", "22.0000", "3", "24", "720", "5"},
            {"豆瓣酱", "调料饮品", "10kg/桶", "桶", "86.0000", "2", "16", "540", "5"},
            {"干粉条调料包", "调料饮品", "1kg/箱", "箱", "56.0000", "3", "24", "365", "5"},
            {"纯净水", "调料饮品", "24瓶/箱", "箱", "28.0000", "10", "80", "365", "5"},
            {"液化气", "燃料", "50kg/瓶", "瓶", "330.0000", "3", "20", "0", "6"},
            {"蜂窝煤", "燃料", "块", "块", "1.1500", "200", "1500", "0", "6"},
            {"餐巾纸", "低值易耗", "100包/箱", "箱", "118.0000", "4", "40", "0", "7"},
            {"保鲜膜", "低值易耗", "卷", "卷", "18.5000", "10", "80", "0", "7"},
            {"洗洁精", "低值易耗", "5L/桶", "桶", "36.0000", "5", "40", "0", "7"},
            {"一次性餐盒", "低值易耗", "500套/箱", "箱", "145.0000", "6", "50", "0", "7"},
            {"不锈钢餐盘", "低值易耗", "个", "个", "26.0000", "20", "200", "0", "11"},
    };

    /** 费用：类别 / 摘要 / 税率% / 供应商序号。 */
    private static final int F_TYPE = 0;
    private static final int F_SUMMARY = 1;
    private static final int F_TAX = 2;
    private static final int F_SUPPLIER = 3;

    private static final String[][] FEES = {
            {"水电燃气", "食堂月度电费", "13", "9"},
            {"水电燃气", "食堂月度水费", "3", "10"},
            {"设备维修", "蒸饭车维修更换配件", "13", "11"},
            {"保洁防疫", "餐厅消杀与保洁服务", "6", "8"},
            {"检测检验", "餐具与食用油抽检", "6", "12"},
            {"低值易耗", "劳保用品与清洁用品采购", "13", "7"},
    };

    private final JdbcTemplate jdbc;
    private final CanteenBizService bizService;
    private final boolean enabled;

    public CanteenDataInitializer(JdbcTemplate jdbc, CanteenBizService bizService,
                                  @Value("${rich.canteen.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.bizService = bizService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[rich] 食堂模块已关闭（rich.canteen.enabled=false）");
            return;
        }
        for (String sql : CanteenSchemaDdl.DDL) {
            jdbc.execute(sql);
        }
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM canteen_purchase WHERE purchase_no=?", Integer.class, MARK_PURCHASE);
        if (cnt != null && cnt > 0) {
            log.info("[rich] 食堂数据已存在（{} 张入库单），跳过初始化", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM canteen_purchase", Integer.class));
            return;
        }
        long start = System.currentTimeMillis();
        insertSuppliers();
        insertMaterials();
        insertCards();
        generatePurchases();
        bizService.recalcStock();
        generateOutbounds();
        bizService.recalcStock();
        generateChecks();
        generateSales();
        generateFees();
        writeAccounts();
        bizService.recalcBalance();
        log.info("[rich] 食堂模块初始化完成，用时 {} ms", System.currentTimeMillis() - start);
    }

    // ================================================================ 档案

    private void insertSuppliers() {
        for (String[] s : SUPPLIERS) {
            jdbc.update("INSERT INTO canteen_supplier (supplier_code, supplier_name, category, contact,"
                            + " phone, address, settle_type, bank_account, license_no, coop_amount, status, remark)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,0,'合作中',?)",
                    s[S_CODE], s[S_NAME], s[S_CATEGORY], s[S_CONTACT], s[S_PHONE],
                    "山东省烟台市招远市", s[S_SETTLE],
                    "中国农业银行招远支行 ****" + (1000 + Integer.parseInt(s[S_CODE].split("-")[1])),
                    "JY1****" + (200601 + Integer.parseInt(s[S_CODE].split("-")[1])),
                    s[S_CATEGORY] + "定点供应商");
        }
    }

    private void insertMaterials() {
        for (int i = 0; i < MATERIALS.length; i++) {
            String[] m = MATERIALS[i];
            jdbc.update("INSERT INTO canteen_material (material_code, material_name, category, spec, unit,"
                            + " unit_price, min_stock, max_stock, shelf_days, supplier_id, supplier_name,"
                            + " status, remark, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,'启用',?,?,?)",
                    String.format("SC-%02d", i + 1), m[M_NAME], m[M_CATEGORY], m[M_SPEC], m[M_UNIT],
                    new BigDecimal(m[M_PRICE]), new BigDecimal(m[M_MIN]), new BigDecimal(m[M_MAX]),
                    Integer.parseInt(m[M_SHELF]), (long) Integer.parseInt(m[M_SUPPLIER]),
                    SUPPLIERS[Integer.parseInt(m[M_SUPPLIER]) - 1][S_NAME],
                    "食堂日常用料", LocalDateTime.now(), LocalDateTime.now());
        }
    }

    /** 饭卡：覆盖各主要部门，便于演示充值与消费。 */
    private void insertCards() {
        String[][] depts = {
                {"经营层", "11"}, {"生产技术科", "16"}, {"安全生产科", "7"}, {"物流设备科", "13"},
                {"财务科", "3"}, {"企管科", "11"}, {"综合办公室", "21"}, {"保卫科", "17"},
                {"采矿车间", "171"}, {"选矿车间", "37"},
        };
        int seq = 1;
        for (String[] d : depts) {
            int n = Math.min(Integer.parseInt(d[1]), 6);
            for (int i = 0; i < n; i++) {
                BigDecimal balance = BigDecimal.valueOf(120 + ((seq * 53) % 380));
                jdbc.update("INSERT INTO canteen_card (card_no, holder, dept_name, card_type, balance,"
                                + " total_recharge, total_consume, status, remark, create_time, update_time)"
                                + " VALUES (?,?,?,?,?,?,0,'正常',?,?,?)",
                        String.format("FK-%03d", seq), d[0] + "职工" + (i + 1), d[0],
                        "职工卡", balance, balance, "演示数据",
                        LocalDateTime.now(), LocalDateTime.now());
                seq++;
            }
        }
    }

    // ================================================================ 采购入库

    /**
     * 生成采购入库单：每个月前 3 周每周一张日常采购，月末补一张燃料 / 低值易耗采购。
     *
     * <p>数量在参考值附近浮动，模拟真实的日采购不整齐；1—7 月均已付款结清，
     * 8 月留出部分未付款以便演示应付账款提醒。
     */
    private void generatePurchases() {
        int no = 0;
        for (int month = START_MONTH; month <= endMonth(); month++) {
            LocalDate[] dates = {
                    LocalDate.of(YEAR, month, 5),
                    LocalDate.of(YEAR, month, 12),
                    LocalDate.of(YEAR, month, 19),
                    LocalDate.of(YEAR, month, 26),
            };
            for (int w = 0; w < dates.length; w++) {
                if (dates[w].isAfter(LocalDate.now())) {
                    continue;
                }
                no++;
                boolean fuel = w == 3;
                String[] supplier = fuel ? SUPPLIERS[5] : SUPPLIERS[w % 4];
                LocalDate date = dates[w];
                String purchaseNo = String.format("CG%d-%04d", YEAR, no);
                jdbc.update("INSERT INTO canteen_purchase (purchase_no, supplier_id, supplier_name,"
                                + " purchase_date, purchase_type, total_amount, tax_amount, total_with_tax,"
                                + " invoice_no, pay_status, paid_amount, buyer, checker, handler, item_count,"
                                + " status, create_time, update_time)"
                                + " VALUES (?,?,?,?,?,0,0,0,?,?,0,?,?,?,0,?,?,?)",
                        purchaseNo, (long) index(supplier[S_CODE]), supplier[S_NAME], date,
                        fuel ? "定点采购" : "日常采购",
                        "00" + no + (1000 + no * 7), month < endMonth() ? "已付款" : "未付款",
                        "王玉芹", "刘德强", "李雲钊", CanteenPurchase.STATUS_STOCKED,
                        LocalDateTime.now(), LocalDateTime.now());
                Long purchaseId = jdbc.queryForObject("SELECT MAX(id) FROM canteen_purchase", Long.class);

                double amountSum = 0;
                double taxSum = 0;
                int itemCount = 0;
                for (int i = 0; i < MATERIALS.length; i++) {
                    // 每次采购覆盖不同物料：第几张单决定取哪些物料
                    if ((i + no * 3) % (fuel ? 6 : 4) != 0) {
                        continue;
                    }
                    if (fuel && !"燃料".equals(MATERIALS[i][M_CATEGORY])
                            && !"低值易耗".equals(MATERIALS[i][M_CATEGORY])) {
                        continue;
                    }
                    if (!fuel && ("燃料".equals(MATERIALS[i][M_CATEGORY])
                            || "低值易耗".equals(MATERIALS[i][M_CATEGORY]))) {
                        continue;
                    }
                    double unitPrice = Double.parseDouble(MATERIALS[i][M_PRICE])
                            * (1 + (((no + i) % 7) - 3) / 100.0);
                    // 单次采购量按安全库存的倍数铺开：一个月 3 次日常采购，使食材成本占收入约五成
                    double qty = Math.max(1, Math.round(Double.parseDouble(MATERIALS[i][M_MIN])
                            * (9 + ((no + i) % 5) * 2.5)));
                    double amount = round2(qty * unitPrice);
                    double rate = Double.parseDouble(MATERIALS[i][M_CATEGORY].equals("肉禽蛋") ? "13"
                            : ("蔬菜豆制品".equals(MATERIALS[i][M_CATEGORY]) ? "0" : "9"));
                    double tax = round2(amount * rate / 100);
                    jdbc.update("INSERT INTO canteen_purchase_item (purchase_id, purchase_no, material_id,"
                                    + " material_code, material_name, category, spec, unit, quantity, unit_price,"
                                    + " amount, tax_rate, tax_amount, total_amount, remark, create_time, update_time)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            purchaseId, purchaseNo, (long) (i + 1), String.format("SC-%02d", i + 1),
                            MATERIALS[i][M_NAME], MATERIALS[i][M_CATEGORY], MATERIALS[i][M_SPEC],
                            MATERIALS[i][M_UNIT], new BigDecimal(qty), new BigDecimal(unitPrice),
                            new BigDecimal(amount), new BigDecimal(rate), new BigDecimal(tax),
                            new BigDecimal(amount + tax), "演示采购明细",
                            LocalDateTime.now(), LocalDateTime.now());
                    amountSum += amount;
                    taxSum += tax;
                    itemCount++;
                }
                double total = round2(amountSum + taxSum);
                jdbc.update("UPDATE canteen_purchase SET total_amount=?, tax_amount=?, total_with_tax=?,"
                                + " item_count=?, status='已入库', paid_amount=?, pay_status=?"
                                + " WHERE id=?",
                        new BigDecimal(amountSum), new BigDecimal(taxSum), new BigDecimal(total),
                        itemCount,
                        new BigDecimal(month < endMonth() ? total : 0),
                        month < endMonth() ? "已付款" : "未付款", purchaseId);
                // 供应商累计采购额
                jdbc.update("UPDATE canteen_supplier SET coop_amount=coop_amount+? WHERE id=?",
                        new BigDecimal(total), (long) index(supplier[S_CODE]));
            }
        }
    }

    // ================================================================ 领用出库

    /**
     * 生成领用出库单：每月两次，明细单价取自库存结存的加权平均价。
     *
     * <p>数量控制在"截至该月已入库量 − 已领用量"的 45% 以内，
     * 保证先入库后领用、任何时点都不会出现负库存，且累计消耗约 85%。
     */
    private void generateOutbounds() {
        List<Map<String, Object>> stocks = jdbc.queryForList(
                "SELECT material_id, material_code, material_name, category, spec, unit, quantity, avg_price"
                        + " FROM canteen_stock WHERE quantity > 0 ORDER BY material_id");
        // materialId -> 截至各月末的累计入库量（下标 0 未用，1~12 对应月份）
        Map<Long, double[]> cumulative = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT pi.material_id AS mid, MONTH(p.purchase_date) AS m, SUM(pi.quantity) AS q"
                        + " FROM canteen_purchase_item pi JOIN canteen_purchase p ON p.id = pi.purchase_id"
                        + " GROUP BY pi.material_id, MONTH(p.purchase_date)")) {
            double[] arr = cumulative.computeIfAbsent(((Number) r.get("mid")).longValue(), k -> new double[13]);
            arr[((Number) r.get("m")).intValue()] += ((BigDecimal) r.get("q")).doubleValue();
        }
        for (double[] arr : cumulative.values()) {
            for (int m = 1; m <= 12; m++) {
                arr[m] += arr[m - 1];
            }
        }
        Map<Long, Double> consumed = new HashMap<>();
        int no = 0;
        for (int month = START_MONTH; month <= endMonth(); month++) {
            for (int w = 0; w < 2; w++) {
                LocalDate date = LocalDate.of(YEAR, month, w == 0 ? 16 : 27);
                if (date.isAfter(LocalDate.now())) {
                    continue;
                }
                no++;
                String outNo = String.format("LY%d-%04d", YEAR, no);
                String outType = w == 0 ? "职工餐" : (month % 3 == 0 ? "接待餐" : "职工餐");
                String dept = w == 0 ? "职工餐厅" : (month % 3 == 0 ? "综合办公室" : "职工餐厅");
                double total = 0;
                int itemCount = 0;
                jdbc.update("INSERT INTO canteen_outbound (out_no, out_date, out_type, window_name,"
                                + " dept_name, people_count, total_amount, receiver, handler, item_count,"
                                + " status, remark, create_time, update_time)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        outNo, date, outType, w == 0 ? "职工餐厅" : "副食间", dept,
                        0, BigDecimal.ZERO, w == 0 ? "杨师傅" : "刘德强", "刘德强", 0,
                        CanteenOutbound.STATUS_DONE, "演示出库明细",
                        LocalDateTime.now(), LocalDateTime.now());
                Long outId = jdbc.queryForObject("SELECT MAX(id) FROM canteen_outbound", Long.class);
                for (int i = 0; i < stocks.size(); i++) {
                    Map<String, Object> s = stocks.get(i);
                    if ((i + no * 2) % 3 != 0) {
                        continue;
                    }
                    Long materialId = ((Number) s.get("material_id")).longValue();
                    BigDecimal priceBd = (BigDecimal) s.get("avg_price");
                    if (priceBd == null || priceBd.doubleValue() <= 0) {
                        continue;
                    }
                    double[] arr = cumulative.get(materialId);
                    double available = (arr == null ? 0 : arr[month]) - consumed.getOrDefault(materialId, 0.0);
                    double qty = round(available * 0.45, 3);
                    if (qty <= 0) {
                        continue;
                    }
                    consumed.put(materialId, consumed.getOrDefault(materialId, 0.0) + qty);
                    double price = priceBd.doubleValue();
                    double amount = round2(qty * price);
                    jdbc.update("INSERT INTO canteen_outbound_item (out_id, out_no, material_id, material_code,"
                                    + " material_name, category, spec, unit, quantity, unit_price, amount,"
                                    + " remark, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            outId, outNo, s.get("material_id"), s.get("material_code"), s.get("material_name"),
                            s.get("category"), s.get("spec"), s.get("unit"), new BigDecimal(qty),
                            priceBd, new BigDecimal(amount), "演示领用",
                            LocalDateTime.now(), LocalDateTime.now());
                    total += amount;
                    itemCount++;
                }
                int people = 260 + (no * 17) % 90;
                jdbc.update("UPDATE canteen_outbound SET total_amount=?, item_count=?, people_count=?"
                                + " WHERE id=?",
                        new BigDecimal(total), itemCount, people, outId);
            }
        }
    }

    // ================================================================ 盘点

    /** 8 月末生成一张已盘点待处理的盘点单，明细含少量差异便于演示调整。 */
    private void generateChecks() {
        String checkNo = String.format("PD%d-0001", YEAR);
        // 盘点日取当月最后一天，但不能晚于今天
        LocalDate monthEnd = monthEnd(endMonth());
        LocalDate date = monthEnd.isAfter(LocalDate.now()) ? LocalDate.now() : monthEnd;
        jdbc.update("INSERT INTO canteen_check (check_no, check_date, check_type, book_amount, actual_amount,"
                        + " diff_amount, item_count, diff_count, checker, reviewer, status, remark,"
                        + " create_time, update_time) VALUES (?,?,'月末盘点',0,0,0,0,0,?,?,'已盘点',?,?,?)",
                checkNo, date, "刘德强", "王玉芹", "8月末实地盘点，待处理",
                LocalDateTime.now(), LocalDateTime.now());
        Long checkId = jdbc.queryForObject("SELECT MAX(id) FROM canteen_check", Long.class);
        List<Map<String, Object>> stocks = jdbc.queryForList(
                "SELECT material_id, material_code, material_name, category, spec, unit, quantity, avg_price"
                        + " FROM canteen_stock ORDER BY category, material_code");
        double book = 0;
        double actual = 0;
        int diffCount = 0;
        int itemCount = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Map<String, Object> s = stocks.get(i);
            double q = ((BigDecimal) s.get("quantity")).doubleValue();
            BigDecimal p = (BigDecimal) s.get("avg_price");
            double price = p == null ? 0 : p.doubleValue();
            double realQ = q;
            String reason = "";
            if (i % 7 == 0 && q > 0) {
                // 少量物料有损耗：实盘略低于账面
                realQ = round(q * 0.97, 3);
                reason = "正常损耗";
            } else if (i % 11 == 0 && q > 0) {
                realQ = round(q * 1.02, 3);
                reason = "计量误差";
            }
            double diff = round(realQ - q, 3);
            double diffAmount = round2(diff * price);
            jdbc.update("INSERT INTO canteen_check_item (check_id, check_no, material_id, material_code,"
                            + " material_name, category, spec, unit, book_qty, actual_qty, diff_qty, unit_price,"
                            + " diff_amount, reason, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    checkId, checkNo, s.get("material_id"), s.get("material_code"), s.get("material_name"),
                    s.get("category"), s.get("spec"), s.get("unit"), new BigDecimal(q),
                    new BigDecimal(realQ), new BigDecimal(diff), p == null ? BigDecimal.ZERO : p,
                    new BigDecimal(diffAmount), reason, "",
                    LocalDateTime.now(), LocalDateTime.now());
            book = round2(book + q * price);
            actual = round2(actual + realQ * price);
            if (Math.abs(diff) > 0.0001) {
                diffCount++;
            }
            itemCount++;
        }
        jdbc.update("UPDATE canteen_check SET book_amount=?, actual_amount=?, diff_amount=?, item_count=?,"
                        + " diff_count=? WHERE id=?",
                new BigDecimal(book), new BigDecimal(actual), new BigDecimal(round2(actual - book)),
                itemCount, diffCount, checkId);
    }

    // ================================================================ 就餐收入与费用

    private void generateSales() {
        int total = 0;
        for (int month = START_MONTH; month <= endMonth(); month++) {
            LocalDate from = LocalDate.of(YEAR, month, 1);
            LocalDate monthEnd = monthEnd(month);
            // 收入日报不生成未来日期
            LocalDate to = monthEnd.isAfter(LocalDate.now()) ? LocalDate.now() : monthEnd;
            if (from.isAfter(to)) {
                continue;
            }
            total += bizService.batchGenerateSale(from, to,
                    List.of("早餐", "午餐", "晚餐"), "职工餐",
                    month <= 3 ? new BigDecimal("5.50") : new BigDecimal("6.00"),
                    new BigDecimal("3.00"), 250 + month * 4, "饭卡", "职工餐厅", "李雲钊");
            total += bizService.batchGenerateSale(from, to,
                    List.of("午餐", "晚餐"), "客饭",
                    new BigDecimal("15.00"), new BigDecimal("15.00"), 12 + month,
                    "扫码支付", "接待餐厅", "王玉芹");
            total += bizService.batchGenerateSale(from, to,
                    List.of("夜宵"), "加班餐",
                    new BigDecimal("10.00"), new BigDecimal("0.00"), 18 + month,
                    "饭卡", "夜班窗口", "杨师傅");
        }
        // 收入全部确认为已确认状态（流水在 writeAccounts 中一次性补登）
        jdbc.update("UPDATE canteen_sale SET status='已确认' WHERE status='草稿'");
        log.info("[rich] 食堂就餐收入生成 {} 条", total);
    }

    private void generateFees() {
        int no = 0;
        for (int month = START_MONTH; month <= endMonth(); month++) {
            for (String[] f : FEES) {
                no++;
                LocalDate date = LocalDate.of(YEAR, month, 8 + (no % 18));
                double base = switch (f[F_TYPE]) {
                    case "水电燃气" -> 4200 + (no * 331) % 2600;
                    case "设备维修" -> 680 + (no * 197) % 1400;
                    case "保洁防疫" -> 1500 + (no * 113) % 900;
                    case "检测检验" -> 900 + (no * 71) % 600;
                    default -> 1200 + (no * 149) % 1100;
                };
                double amount = round2(base);
                double tax = round2(amount * Double.parseDouble(f[F_TAX]) / 100);
                jdbc.update("INSERT INTO canteen_fee (fee_no, fee_date, fee_type, amount, tax_amount,"
                                + " total_amount, supplier_id, supplier_name, invoice_no, pay_type, pay_status,"
                                + " pay_date, handler, approver, status, remark, create_time, update_time)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,'银行转账','已付款',?,?,?,'已付款',?,?,?)",
                        String.format("FY%d-%04d", YEAR, no), date, f[F_TYPE],
                        new BigDecimal(amount), new BigDecimal(tax), new BigDecimal(amount + tax),
                        (long) Integer.parseInt(f[F_SUPPLIER]),
                        SUPPLIERS[Integer.parseInt(f[F_SUPPLIER]) - 1][S_NAME],
                        "0" + (330021 + no), date.plusDays(2), "李雲钊", "柳鉴益",
                        f[F_SUMMARY], LocalDateTime.now(), LocalDateTime.now());
            }
        }
    }

    // ================================================================ 财务流水

    /**
     * 依据已生成的业务单据一次性补登流水。
     *
     * <p>逐笔调用业务的过账接口会在每一笔都重算余额（O(n²)），数据量上千时明显变慢，
     * 因此这里直接写流水，最后统一 {@link CanteenBizService#recalcBalance()}。
     * 流水按业务日期排序，余额由重算方法滚动生成。
     */
    private void writeAccounts() {
        int seq = 0;
        // 上级拨入：每季度一笔周转金
        for (int month : new int[]{1, 4, 7}) {
            seq++;
            insertAccount(LocalDate.of(YEAR, month, 3), seq, "收入", "上级拨入",
                    new BigDecimal("180000.00"), "银行转账", "招远市金宝黄金矿业有限公司",
                    "季度食堂周转金拨入", "手工记账", null, "手工", null, null, "柳鉴益");
        }
        // 就餐收入
        List<Map<String, Object>> sales = jdbc.queryForList(
                "SELECT id, sale_no, biz_date, receivable, customer_type, meal_type FROM canteen_sale"
                        + " ORDER BY biz_date, id");
        for (Map<String, Object> s : sales) {
            seq++;
            insertAccount(toDate(s.get("biz_date")), seq, "收入", "营业收入",
                    (BigDecimal) s.get("receivable"), "饭卡", "就餐职工",
                    s.get("meal_type") + " " + s.get("customer_type"), "就餐收入",
                    (Long) s.get("id"), String.valueOf(s.get("sale_no")), null, null, "系统记账");
        }
        // 采购付款（1—7 月已结清的单据）
        List<Map<String, Object>> purchases = jdbc.queryForList(
                "SELECT id, purchase_no, purchase_date, total_with_tax, supplier_name FROM canteen_purchase"
                        + " WHERE pay_status='已付款' ORDER BY purchase_date, id");
        for (Map<String, Object> p : purchases) {
            seq++;
            insertAccount(toDate(p.get("purchase_date")).plusDays(5), seq, "支出", "采购付款",
                    (BigDecimal) p.get("total_with_tax"), "银行转账",
                    String.valueOf(p.get("supplier_name")),
                    "支付 " + p.get("purchase_no") + " 采购款", "采购入库",
                    (Long) p.get("id"), String.valueOf(p.get("purchase_no")), null, null, "柳鉴益");
        }
        // 费用支出
        List<Map<String, Object>> fees = jdbc.queryForList(
                "SELECT id, fee_no, fee_date, total_amount, fee_type, supplier_name FROM canteen_fee"
                        + " ORDER BY fee_date, id");
        for (Map<String, Object> f : fees) {
            seq++;
            insertAccount(toDate(f.get("fee_date")), seq, "支出", "费用支出",
                    (BigDecimal) f.get("total_amount"), "银行转账",
                    String.valueOf(f.get("supplier_name")),
                    f.get("fee_type") + " " + f.get("fee_no"), "费用支出",
                    (Long) f.get("id"), String.valueOf(f.get("fee_no")), null, null, "李雲钊");
        }
        // 饭卡初始充值：直接写流水 + 累计值，避免逐笔调用充值接口导致每笔都重算余额
        List<Map<String, Object>> cards = jdbc.queryForList(
                "SELECT id, card_no, holder, balance FROM canteen_card ORDER BY id");
        LocalDate cardDay = LocalDate.of(YEAR, START_MONTH, 2);
        for (Map<String, Object> c : cards) {
            seq++;
            BigDecimal balance = (BigDecimal) c.get("balance");
            insertAccount(cardDay, seq, "收入", "餐卡充值", balance, "扫码支付",
                    String.valueOf(c.get("holder")), "饭卡充值 " + c.get("card_no"),
                    "餐卡充值", (Long) c.get("id"), String.valueOf(c.get("card_no")),
                    (Long) c.get("id"), String.valueOf(c.get("card_no")), "系统生成");
            jdbc.update("UPDATE canteen_card SET total_recharge=? WHERE id=?", balance, c.get("id"));
        }
        log.info("[rich] 食堂流水登记表 {} 笔", seq);
    }

    private void insertAccount(LocalDate bizDate, int seq, String direction, String accountType,
                               BigDecimal amount, String payType, String counterparty, String summary,
                               String sourceType, Long sourceId, String sourceNo,
                               Long cardId, String cardNo, String handler) {
        jdbc.update("INSERT INTO canteen_account (biz_date, voucher_no, direction, account_type, amount,"
                        + " balance, pay_type, counterparty, summary, source_type, source_id, source_no,"
                        + " card_id, card_no, handler, status, remark, create_time, update_time)"
                        + " VALUES (?,?,?,?,?,0,?,?,?,?,?,?,?,?,?,'已记账','初始演示数据',?,?)",
                bizDate, String.format("JZ%d%02d%02d%04d", bizDate.getYear(), bizDate.getMonthValue(),
                        bizDate.getDayOfMonth(), seq), direction, accountType, amount, payType,
                counterparty, summary, sourceType, sourceId, sourceNo, cardId, cardNo, handler,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ================================================================ 工具

    /** 供应商编码 → suppliers 数组下标（即数据库自增 ID）。 */
    private static int index(String supplierCode) {
        return Integer.parseInt(supplierCode.split("-")[1]);
    }

    /**
     * 把 JDBC 返回的日期列统一转换为 {@link LocalDate}。
     *
     * <p>{@code queryForList} 取回的 DATE 列可能是 {@code java.sql.Date}（取决于驱动配置），
     * 直接强转会抛 ClassCastException，这里做一次兼容转换。
     */
    private static LocalDate toDate(Object value) {
        if (value == null) {
            return LocalDate.now();
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
