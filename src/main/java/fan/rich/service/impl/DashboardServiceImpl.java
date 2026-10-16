package fan.rich.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.dto.LedgerSummary;
import fan.rich.dto.NameValue;
import fan.rich.dto.TrendPoint;
import fan.rich.entity.MdCrawlLog;
import fan.rich.entity.MdFuturesPrice;
import fan.rich.entity.SaleShipment;
import fan.rich.mapper.MdFuturesPriceMapper;
import fan.rich.mapper.SaleShipmentMapper;
import fan.rich.service.ArchiveStatsService;
import fan.rich.service.AuditStatsService;
import fan.rich.service.CanteenStatsService;
import fan.rich.service.DashboardService;
import fan.rich.service.InventoryStatsService;
import fan.rich.service.SalaryStatsService;
import fan.rich.service.SaleLedgerService;
import fan.rich.service.TrainStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * 可视化大屏服务实现。
 *
 * <p><b>口径来源</b>：顶层 KPI 与各模块指标全部复用各业务模块自己的统计服务
 * （{@link SaleLedgerService} / {@link InventoryStatsService} / {@link ArchiveStatsService} /
 * {@link TrainStatsService} / {@link SalaryStatsService} / {@link CanteenStatsService} /
 * {@link AuditStatsService}），保证大屏上的数字与各模块内页里的数字同源一致，
 * 不会出现"两个地方看两个数"的情况。
 *
 * <p><b>容错原则</b>：任何单个模块取数异常都被就地降级（该模块显示"暂不可用"），
 * 不影响整屏渲染。大屏的定位是"一眼看全公司"，某张业务表临时不可用不该让整页空白。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 大屏上展示走势的金属品种（上期所代号 → 中文名）。 */
    private static final Map<String, String> METAL_NAMES = Map.of(
            "au", "黄金", "ag", "白银", "cu", "铜");

    /**
     * 各品种的报价计量单位与归一系数（上期所官方口径）。
     *
     * <p>黄金按元/克、白银按元/千克、铜按元/吨，量级相差百倍。为了在大屏上可读，
     * 白银统一折算成元/克（×0.001），这样贵金属共享左轴，基本金属单独右轴；
     * 前端按 {@code unit} 字段分组挂轴即可。
     */
    private static final Map<String, String> METAL_UNITS = Map.of(
            "au", "元/克", "ag", "元/克", "cu", "元/吨");

    /** 各品种数值的换算系数：白银从元/千克折算成元/克。 */
    private static final Map<String, BigDecimal> METAL_SCALES = Map.of(
            "au", BigDecimal.ONE, "ag", new BigDecimal("0.001"), "cu", BigDecimal.ONE);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final SaleLedgerService ledgerService;
    private final SaleShipmentMapper shipmentMapper;
    private final InventoryStatsService inventoryStats;
    private final ArchiveStatsService archiveStats;
    private final AuditStatsService auditStats;
    private final TrainStatsService trainStats;
    private final SalaryStatsService salaryStats;
    private final CanteenStatsService canteenStats;
    private final MdFuturesPriceMapper futuresMapper;
    private final JdbcTemplate jdbc;

    public DashboardServiceImpl(SaleLedgerService ledgerService,
                                SaleShipmentMapper shipmentMapper,
                                InventoryStatsService inventoryStats,
                                ArchiveStatsService archiveStats,
                                AuditStatsService auditStats,
                                TrainStatsService trainStats,
                                SalaryStatsService salaryStats,
                                CanteenStatsService canteenStats,
                                MdFuturesPriceMapper futuresMapper,
                                JdbcTemplate jdbc) {
        this.ledgerService = ledgerService;
        this.shipmentMapper = shipmentMapper;
        this.inventoryStats = inventoryStats;
        this.archiveStats = archiveStats;
        this.auditStats = auditStats;
        this.trainStats = trainStats;
        this.salaryStats = salaryStats;
        this.canteenStats = canteenStats;
        this.futuresMapper = futuresMapper;
        this.jdbc = jdbc;
    }

    // ================================================================ 综合大屏

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        LedgerSummary sale = ledgerService.summary();
        Map<String, Object> inv = safe("盘点", inventoryStats::overview);
        Map<String, Object> arch = safe("档案", archiveStats::overview);
        Map<String, Object> audit = safe("审计", auditStats::overview);
        Map<String, Object> train = safe("培训", trainStats::overview);
        Map<String, Object> salary = safe("工资", salaryStats::overview);
        Map<String, Object> canteen = safe("食堂", canteenStats::overview);

        Map<String, Object> price = priceSummary();

        data.put("updateTime", LocalDateTime.now().format(TIME_FMT));
        data.put("kpi", topKpi(sale, inv, salary, canteen));
        data.put("modules", modules(sale, inv, arch, audit, train, salary, canteen, price));
        data.put("reminders", reminders(inv, arch, audit, train, salary, canteen));
        data.put("charts", charts(sale, inv, arch, salary, canteen));        data.put("crawl", crawlStatus());
        return data;
    }

    /**
     * 顶层 KPI：横跨经营、库存、人力、后勤四条线，
     * 让一屏能看到"卖了多少金、结了多少钱、资产多少、本月多少人吃饭"。
     */
    private Map<String, Object> topKpi(LedgerSummary sale,
                                       Map<String, Object> inv,
                                       Map<String, Object> salary,
                                       Map<String, Object> canteen) {
        Map<String, Object> salaryCurrent = sub(salary, "current");
        Map<String, Object> salaryKpi = sub(salary, "kpi");
        Map<String, Object> canteenKpi = sub(canteen, "kpi");

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalGold", nvl(sale.getTotalGold()));
        kpi.put("dryWeight", nvl(sale.getDryWeight()));
        kpi.put("settlementAmount", nvl(sale.getSettlementAmount()));
        kpi.put("oweGold", nvl(sale.getOweGold()));
        kpi.put("assetAmount", dec(inv.get("assetAmount")));
        kpi.put("canteenPeople", lng(canteenKpi.get("totalPeople")));
        kpi.put("salaryApproved", dec(first(salaryCurrent.get("approved"), salaryKpi.get("approvedAmount"))));
        kpi.put("batchCount", sale.getConcentrateBatches() + sale.getDoreBatches());
        return kpi;
    }

    /** 八个业务模块的概览卡片（含跳转路由与图标），前端渲染成可点击的九宫格。 */
    private List<Map<String, Object>> modules(LedgerSummary sale,
                                              Map<String, Object> inv,
                                              Map<String, Object> arch,
                                              Map<String, Object> audit,
                                              Map<String, Object> train,
                                              Map<String, Object> salary,
                                              Map<String, Object> canteen,
                                              Map<String, Object> price) {
        Map<String, Object> auditKpi = sub(audit, "kpi");
        Map<String, Object> trainKpi = sub(train, "kpi");
        Map<String, Object> salaryCurrent = sub(salary, "current");
        Map<String, Object> salaryKpi = sub(salary, "kpi");
        Map<String, Object> canteenKpi = sub(canteen, "kpi");

        List<Map<String, Object>> list = new ArrayList<>();

        list.add(module("sale", "销售管理", "/sale/dashboard", "layui-icon-cart", "#1E9FFF", !saleEmpty(sale),
                metric("金含量", sale.getTotalGold(), "g"),
                metric("精矿干重", sale.getDryWeight(), "t"),
                metric("结算金额", wan(sale.getSettlementAmount()), "万元")));

        list.add(module("price", "价格采集", "/price/dashboard", "layui-icon-diamond", "#F78400", !price.isEmpty(),
                metric("最新交易日", price.get("latestDate"), ""),
                metric("期货品种", price.get("instrumentCount"), "个"),
                metric("主力金价", price.get("goldClose"), "元/克")));

        list.add(module("inventory", "盘点管理", "/inventory/dashboard", "layui-icon-survey", "#16baaa", !inv.isEmpty(),
                metric("本年应盘", inv.get("subjectCount"), "件"),
                metric("盘点完成率", inv.get("checkedRate"), "%"),
                metric("资产原值", wan(dec(inv.get("assetAmount"))), "万元")));

        list.add(module("archive", "档案管理", "/archive/dashboard", "layui-icon-tabs", "#5FB878", !arch.isEmpty(),
                metric("在库案卷", arch.get("volumeAlive"), "卷"),
                metric("文件件数", arch.get("fileTotal"), "件"),
                metric("借阅在册", arch.get("borrowing"), "卷")));

        list.add(module("train", "培训管理", "/train/dashboard", "layui-icon-read", "#FFB800", !train.isEmpty(),
                metric("培训班", trainKpi.get("planCount"), "期"),
                metric("参训人次", trainKpi.get("enrollCount"), "人次"),
                metric("合格率", trainKpi.get("passRate"), "%")));

        list.add(module("salary", "工资管理", "/salary/dashboard", "layui-icon-rmb", "#AA3130", !salary.isEmpty(),
                metric("核定总额", wan(dec(first(salaryCurrent.get("approved"), salaryKpi.get("approvedAmount")))), "万元"),
                metric("已发放", wan(dec(first(salaryCurrent.get("used"), salaryKpi.get("usedAmount")))), "万元"),
                metric("账面结余", wan(dec(first(salaryCurrent.get("balance"), salaryKpi.get("balanceAmount")))), "万元")));

        list.add(module("canteen", "食堂管理", "/canteen/dashboard", "layui-icon-cart-simple", "#2D8CF0", !canteen.isEmpty(),
                metric("本月就餐", canteenKpi.get("peopleCount"), "人次"),
                metric("本月收入", wan(dec(canteenKpi.get("saleIncome"))), "万元"),
                metric("本月结余", wan(dec(canteenKpi.get("profit"))), "万元")));

        list.add(module("audit", "审计整改", "/audit/dashboard", "layui-icon-engine", "#9B59B6", !audit.isEmpty(),
                metric("审计项目", auditKpi.get("projectCount"), "个"),
                metric("问题总数", auditKpi.get("issueCount"), "条"),
                metric("整改率", auditKpi.get("rectifyRate"), "%")));

        return list;
    }

    /**
     * 跨模块待办：把各模块"需要人去处理"的事项汇总成一张清单。
     *
     * <p>大屏上安排这一块的用意是——指标是给管理者看趋势的，而待办是给经办人看的，
     * 两者缺一，大屏就退化成一张漂亮的壁纸。
     */
    private List<Map<String, Object>> reminders(Map<String, Object> inv,
                                                Map<String, Object> arch,
                                                Map<String, Object> audit,
                                                Map<String, Object> train,
                                                Map<String, Object> salary,
                                                Map<String, Object> canteen) {
        List<Map<String, Object>> list = new ArrayList<>();
        int fail = failToday();

        addTodo(list, "danger", "档案管理", lng(arch.get("overdue")),
                "笔借阅逾期未归还", "/archive/borrow");
        addTodo(list, "danger", "审计整改", lng(sub(audit, "reminders").get("overdue")),
                "条问题逾期未销号", "/audit/issue");
        addTodo(list, "warn", "盘点管理", lng(inv.get("pendingCount")),
                "条盘盈盘亏待处理", "/inventory/record");
        addTodo(list, "warn", "工资管理", lng(sub(salary, "reminders").get("pendingAudit")),
                "份工资表待审核", "/salary/report");
        addTodo(list, "warn", "食堂管理", lng(sub(canteen, "warn").get("lowStock")),
                "种食材低于安全库存", "/canteen/stock");
        addTodo(list, "info", "培训管理", lng(sub(train, "reminders").get("starting")),
                "期培训将在 7 天内开班", "/train/plan");
        addTodo(list, fail > 0 ? "danger" : "info", "价格采集", fail,
                "个数据源今日采集失败", "/price/task");

        // 无待办时的兜底文案；risk 级别排序：danger > warn > info
        list.sort((a, b) -> rank(a) - rank(b));
        return list;
    }

    /** 大屏中下部的三张分布 / 趋势图所需数据。 */
    private Map<String, Object> charts(LedgerSummary sale,
                                       Map<String, Object> inv,
                                       Map<String, Object> arch,
                                       Map<String, Object> salary,
                                       Map<String, Object> canteen) {
        Map<String, Object> charts = new LinkedHashMap<>();
        // 食堂月度收支（统计服务按 12 个月倒序给出，这里翻正）
        charts.put("canteenTrend", reverse(castList(canteen.get("trend"))));
        charts.put("salaryTrend", castList(salary.get("trend")));
        // 这两张分布图不在 overview() 里，单独取一次（同样做失败降级）
        charts.put("inventoryType", safeList("盘点分布", inventoryStats::typeStats));
        charts.put("archiveCategory", limit(safeList("档案门类", archiveStats::categoryStats), 8));
        charts.put("inventoryRate", dec(inv.get("checkedRate")));
        charts.put("saleGold", nvl(sale.getTotalGold()));
        return charts;
    }

    // ================================================================ 价格走势

    @Override
    public Map<String, Object> priceTrend(int limit) {
        int size = limit <= 0 ? 24 : limit;
        Map<String, Object> data = new LinkedHashMap<>();
        TreeSet<String> dates = new TreeSet<>();
        Map<String, Map<String, Object>> points = new LinkedHashMap<>();

        for (Map.Entry<String, String> metal : METAL_NAMES.entrySet()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            try {
                rows.addAll(futuresMapper.selectMainContractTrend(metal.getKey(), size));
            } catch (Exception e) {
                log.warn("[大屏] 读取 {} 主力合约走势失败：{}", metal.getKey(), e.getMessage());
            }
            // Mapper 按交易日倒序返回，翻正后再放进时间轴
            Collections.reverse(rows);
            Map<String, Object> point = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object day = row.get("tradeDate");
                if (day == null) {
                    continue;
                }
                String key = String.valueOf(day);
                point.put(key, row.get("closePrice"));
                dates.add(key);
            }
            points.put(metal.getKey(), point);
        }

        List<String> axis = new ArrayList<>(dates);
        data.put("dates", axis);
        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<String, String> metal : METAL_NAMES.entrySet()) {
            Map<String, Object> point = points.getOrDefault(metal.getKey(), Collections.emptyMap());
            BigDecimal scale = METAL_SCALES.getOrDefault(metal.getKey(), BigDecimal.ONE);
            List<Object> values = new ArrayList<>(axis.size());
            for (String day : axis) {
                Object v = point.get(day);
                // 白银等非标准单位在后台统一折算，前端不再关心单位换算
                values.add(v instanceof BigDecimal bd
                        ? bd.multiply(scale).setScale(2, RoundingMode.HALF_UP) : null);
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", metal.getValue());
            line.put("code", metal.getKey());
            line.put("unit", METAL_UNITS.getOrDefault(metal.getKey(), ""));
            line.put("data", values);
            series.add(line);
        }
        data.put("series", series);
        return data;
    }

    /** 价格模块概况：最新交易日、品种数、黄金主力收盘价。 */
    private Map<String, Object> priceSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            LocalDate latest = jdbc.queryForObject(
                    "SELECT MAX(trade_date) FROM md_futures_price", LocalDate.class);
            data.put("latestDate", latest == null ? "-" : latest.toString());
            data.put("instrumentCount", nvl(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT instrument_id) FROM md_futures_price", Long.class)));
            MdFuturesPrice gold = futuresMapper.selectMainContractLatest("au", null);
            data.put("goldClose", gold == null ? null : gold.getClosePrice());
            data.put("goldContract", gold == null ? "" : gold.getContractCode());
            data.put("spotCount", nvl(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM md_spot_price", Long.class)));
        } catch (Exception e) {
            log.warn("[大屏] 读取价格概况失败：{}", e.getMessage());
        }
        return data;
    }

    /** 采集健康度：各数据源最近一次结果 + 今日失败次数。 */
    private Map<String, Object> crawlStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> latest = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT source, task_type AS taskType, DATE_FORMAT(end_time,'%m-%d %H:%i') AS endTime,"
                            + " status, record_count AS records, LEFT(IFNULL(message,''),60) AS message "
                            + "FROM md_crawl_log ORDER BY end_time DESC LIMIT 60");
            List<String> seen = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String source = String.valueOf(row.get("source"));
                if (seen.contains(source)) {
                    continue;
                }
                seen.add(source);
                latest.add(row);
            }
        } catch (Exception e) {
            log.warn("[大屏] 读取采集日志失败：{}", e.getMessage());
        }
        data.put("latest", latest);
        data.put("failToday", failToday());
        data.put("status", MdCrawlLog.STATUS_FAILED);
        return data;
    }

    private int failToday() {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM md_crawl_log WHERE status = ? AND DATE(end_time) = CURDATE()",
                    Integer.class, MdCrawlLog.STATUS_FAILED);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    // ================================================================ 销售口径（沿用原实现）

    @Override
    public Map<String, Object> summary() {
        LedgerSummary s = ledgerService.summary();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalGold", s.getTotalGold());
        data.put("dryWeight", s.getDryWeight());
        data.put("doreWeight", s.getDoreWeight());
        data.put("avgGrade", s.getAvgGrade());
        data.put("returnGold", s.getReturnGold());
        data.put("settledGold", s.getSettledGold());
        data.put("stockGold", s.getStockGold());
        data.put("oweGold", s.getOweGold());
        data.put("fixQty", s.getFixQty());
        data.put("settlementAmount", s.getSettlementAmount());
        data.put("settlementWeight", s.getSettlementWeight());
        data.put("processFee", s.getProcessFee());
        data.put("concentrateBatches", s.getConcentrateBatches());
        data.put("doreBatches", s.getDoreBatches());
        return data;
    }

    @Override
    public List<TrendPoint> trend() {
        return ledgerService.monthlyTrend();
    }

    @Override
    public List<NameValue> category() {
        return ledgerService.statsByProduct();
    }

    @Override
    public List<NameValue> buyer() {
        return ledgerService.statsByBuyer();
    }

    @Override
    public List<SaleShipment> recent(int limit) {
        Page<SaleShipment> page = new Page<>(1, limit);
        return shipmentMapper.selectPage(page, new LambdaQueryWrapper<SaleShipment>()
                        .orderByDesc(SaleShipment::getShipDate)
                        .orderByDesc(SaleShipment::getId))
                .getRecords();
    }

    // ================================================================ 工具方法

    /** 单个模块取数：<b>失败降级为空 Map</b>，不让一颗老鼠屎坏了一锅粥。 */
    private Map<String, Object> safe(String module, Supplier<Map<String, Object>> task) {
        try {
            Map<String, Object> result = task.get();
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            log.warn("[大屏] {} 模块指标读取失败：{}", module, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 取分布类列表：<b>失败降级为空 List</b>。
     *
     * <p>{@code typeStats()} / {@code categoryStats()} 这类方法不在各自模块的 overview() 里，
     * 大屏要用只能单独调一次，同样不能让异常外溢。
     */
    private List<Map<String, Object>> safeList(String module,
                                               Supplier<List<Map<String, Object>>> task) {
        try {
            List<Map<String, Object>> result = task.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            log.warn("[大屏] {} 读取失败：{}", module, e.getMessage());
            return List.of();
        }
    }

    /** 取嵌套 Map，避免到处判空。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    /** 取嵌套 List。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    @SafeVarargs
    private static Map<String, Object> module(String code, String name, String route,
                                              String icon, String color, boolean ok,
                                              Map<String, Object>... metrics) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("code", code);
        card.put("name", name);
        card.put("route", route);
        card.put("icon", icon);
        card.put("color", color);
        card.put("ok", ok);
        card.put("metrics", List.of(metrics));
        return card;
    }

    private static Map<String, Object> metric(String label, Object value, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value instanceof BigDecimal bd ? bd.setScale(2, RoundingMode.HALF_UP) : value);
        m.put("unit", unit);
        return m;
    }

    private static void addTodo(List<Map<String, Object>> list, String level, String module,
                                long count, String text, String route) {
        if (count <= 0) {
            return;
        }
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("level", level);
        todo.put("module", module);
        todo.put("count", count);
        todo.put("text", text);
        todo.put("route", route);
        list.add(todo);
    }

    private static int rank(Map<String, Object> todo) {
        return switch (String.valueOf(todo.get("level"))) {
            case "danger" -> 0;
            case "warn" -> 1;
            default -> 2;
        };
    }

    /** 倒序列表翻正。 */
    private static List<Map<String, Object>> reverse(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>(source);
        Collections.reverse(copy);
        return copy;
    }

    private static List<Map<String, Object>> limit(List<Map<String, Object>> source, int max) {
        return source.size() <= max ? source : new ArrayList<>(source.subList(0, max));
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    /** 元 → 万元。 */
    private static BigDecimal wan(BigDecimal value) {
        return dec(value).divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static long lng(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static Object nvl(Object value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 销售台账一条数据都没有时，把该模块标灰而不是显示一排 0。 */
    private static boolean saleEmpty(LedgerSummary s) {
        return s.getConcentrateBatches() == 0 && s.getDoreBatches() == 0;
    }
}
