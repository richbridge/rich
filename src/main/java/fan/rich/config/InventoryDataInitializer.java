package fan.rich.config;

import fan.rich.entity.InvRecord;
import fan.rich.entity.InvTask;
import fan.rich.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 盘点模块初始化器：固定资产、库存物资与盘点任务的演示数据。
 *
 * <p>启动时按 {@link InventorySchemaDdl} 建表，并为 {@code canteen_material} 补上
 * {@code qr_code} 列——食堂物料是盘点的三类标的之一，必须可扫码定位。
 *
 * <p>演示数据刻意做成<b>三种状态并存</b>，便于直接看到模块全貌：
 * <ul>
 *   <li>一个<b>已完成</b>的二季度固定资产盘点：多数相符，少量盘盈盘亏，差异已处理完毕</li>
 *   <li>一个<b>进行中</b>的当期物资盘点：只盘了一部分，剩余仍是「未盘」，差异待处理</li>
 *   <li>盘点来源标注为「APP扫码」，对应将来手机端回传的数据</li>
 * </ul>
 *
 * <p>以任务单号 {@code PD2026-0001} 为标记，已存在则跳过；清空 {@code ast_*}、
 * {@code inv_*} 各表后重启可重新生成；{@code rich.inventory.enabled=false} 可整体关闭。
 */
@Order(7)
@Component
public class InventoryDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryDataInitializer.class);

    /** 演示数据标记：首个盘点任务单号。 */
    private static final String MARK_TASK = "PD2026-0001";

    private final JdbcTemplate jdbc;
    private final InventoryService inventoryService;
    private final boolean enabled;

    public InventoryDataInitializer(JdbcTemplate jdbc,
                                    InventoryService inventoryService,
                                    @Value("${rich.inventory.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.inventoryService = inventoryService;
        this.enabled = enabled;
    }

    // 资产字段下标
    private static final int A_NAME = 0;
    private static final int A_CATEGORY = 1;
    private static final int A_SPEC = 2;
    private static final int A_UNIT = 3;
    private static final int A_QTY = 4;
    private static final int A_VALUE = 5;
    private static final int A_LIFE = 6;
    private static final int A_DEPT = 7;
    private static final int A_LOC = 8;
    private static final int A_KEEPER = 9;

    /** 固定资产：名称 / 分类 / 规格 / 单位 / 数量 / 原值 / 年限 / 部门 / 地点 / 保管人。 */
    private static final String[][] ASSETS = {
            {"办公楼", "房屋建筑物", "砖混 4 层 2400㎡", "平方米", "2400", "3860000", "30", "综合办公室", "矿区办公楼", "王玉芹"},
            {"职工宿舍楼", "房屋建筑物", "砖混 5 层 3200㎡", "平方米", "3200", "4520000", "30", "综合办公室", "生活区", "王玉芹"},
            {"职工食堂", "房屋建筑物", "框架 2 层 860㎡", "平方米", "860", "1560000", "30", "食堂", "生活区", "刘德强"},
            {"机修车间厂房", "房屋建筑物", "钢结构 1200㎡", "平方米", "1200", "1980000", "25", "物流设备科", "工业广场", "杨师傅"},
            {"物资仓库", "房屋建筑物", "钢结构 900㎡", "平方米", "900", "1120000", "25", "物流设备科", "工业广场", "刘德强"},
            {"选矿厂主厂房", "房屋建筑物", "框架 3600㎡", "平方米", "3600", "6800000", "30", "选矿车间", "选矿工业场", "陈大勇"},
            {"球磨机", "机器设备", "MQG2740", "台", "2", "1860000", "15", "选矿车间", "选矿厂主厂房", "陈大勇"},
            {"圆锥破碎机", "机器设备", "PYB1750", "台", "2", "1240000", "12", "选矿车间", "破碎车间", "陈大勇"},
            {"颚式破碎机", "机器设备", "PE900×1200", "台", "1", "860000", "12", "选矿车间", "破碎车间", "陈大勇"},
            {"浮选机", "机器设备", "SF-8", "台", "12", "384000", "10", "选矿车间", "浮选车间", "陈大勇"},
            {"浓缩机", "机器设备", "NZ-24", "台", "2", "960000", "15", "选矿车间", "浓缩池", "陈大勇"},
            {"真空过滤机", "机器设备", "GP-40", "台", "2", "620000", "12", "选矿车间", "过滤车间", "陈大勇"},
            {"螺旋分级机", "机器设备", "FG-2000", "台", "2", "430000", "12", "选矿车间", "磨矿车间", "陈大勇"},
            {"提升机", "机器设备", "2JK-3.0", "台", "2", "2160000", "15", "采矿车间", "主井井口", "孙立新"},
            {"空压机", "机器设备", "LGFD-40/8", "台", "3", "468000", "10", "采矿车间", "压风机房", "孙立新"},
            {"主通风机", "机器设备", "FKZ-No20", "台", "2", "740000", "12", "采矿车间", "回风井", "孙立新"},
            {"凿岩台车", "机器设备", "CYTJ45", "台", "3", "1350000", "8", "采矿车间", "井下中段", "孙立新"},
            {"铲运机", "机器设备", "CY-2C", "台", "4", "1680000", "8", "采矿车间", "井下中段", "孙立新"},
            {"装载机", "机器设备", "ZL50G", "台", "2", "720000", "10", "物流设备科", "工业广场", "杨师傅"},
            {"变压器", "机器设备", "S11-1250/10", "台", "3", "540000", "20", "生产技术科", "变电所", "李雲钊"},
            {"高压开关柜", "机器设备", "KYN28-12", "台", "8", "384000", "20", "生产技术科", "变电所", "李雲钊"},
            {"尾矿输送泵", "机器设备", "150ZJ-I-A60", "台", "4", "268000", "8", "选矿车间", "尾矿库", "陈大勇"},
            {"地磅", "机器设备", "SCS-100t", "台", "1", "186000", "12", "物流设备科", "磅房", "杨师傅"},
            {"皮带输送机", "机器设备", "B1000×120m", "条", "6", "580000", "10", "选矿车间", "输送廊道", "陈大勇"},
            {"给料机", "机器设备", "GZG-1003", "台", "4", "96000", "8", "选矿车间", "破碎车间", "陈大勇"},
            {"皮卡车", "运输设备", "长城炮 4驱", "辆", "2", "286000", "8", "综合办公室", "办公楼前", "王玉芹"},
            {"通勤客车", "运输设备", "金龙 XMQ6119", "辆", "2", "1120000", "10", "综合办公室", "办公楼前", "王玉芹"},
            {"叉车", "运输设备", "合力 CPCD50", "辆", "2", "248000", "10", "物流设备科", "物资仓库", "刘德强"},
            {"自卸车", "运输设备", "陕汽德龙 20t", "辆", "3", "1350000", "8", "物流设备科", "工业广场", "杨师傅"},
            {"洒水车", "运输设备", "东风 10t", "辆", "1", "320000", "8", "安全生产科", "工业广场", "赵金凤"},
            {"台式电脑", "电子设备", "联想 M460 i5", "台", "28", "168000", "5", "综合办公室", "办公楼", "王玉芹"},
            {"笔记本电脑", "电子设备", "ThinkPad T14", "台", "12", "108000", "5", "生产技术科", "办公楼", "李雲钊"},
            {"打印机", "电子设备", "惠普 M405d", "台", "10", "32000", "5", "综合办公室", "办公楼", "王玉芹"},
            {"服务器", "电子设备", "戴尔 R750", "台", "2", "186000", "6", "生产技术科", "中心机房", "李雲钊"},
            {"交换机", "电子设备", "华为 S5731", "台", "6", "48000", "6", "生产技术科", "中心机房", "李雲钊"},
            {"视频监控系统", "电子设备", "海康 32 路 NVR", "套", "2", "248000", "8", "保卫科", "监控中心", "赵金凤"},
            {"人员定位系统", "电子设备", "KJ128A", "套", "1", "560000", "8", "安全生产科", "调度室", "孙立新"},
            {"投影仪", "电子设备", "爱普生 CB-2155W", "台", "2", "26000", "5", "综合办公室", "会议室", "王玉芹"},
            {"会议音响系统", "电子设备", "JBL 套装", "套", "1", "58000", "6", "综合办公室", "会议室", "王玉芹"},
            {"办公桌", "办公家具", "1.6m 实木", "张", "36", "72000", "10", "综合办公室", "办公楼", "王玉芹"},
            {"办公椅", "办公家具", "网布转椅", "把", "42", "33600", "8", "综合办公室", "办公楼", "王玉芹"},
            {"会议桌", "办公家具", "4.2m 实木", "张", "2", "18000", "10", "综合办公室", "会议室", "王玉芹"},
            {"文件柜", "办公家具", "铁皮 四门", "个", "24", "21600", "10", "财务科", "办公楼", "李春梅"},
            {"沙发", "办公家具", "三人位皮质", "套", "6", "28800", "8", "综合办公室", "办公楼", "王玉芹"},
            {"餐桌", "办公家具", "十人位不锈钢", "张", "30", "45000", "10", "食堂", "职工食堂", "刘德强"},
    };

    // 物资字段下标
    private static final int G_NAME = 0;
    private static final int G_CATEGORY = 1;
    private static final int G_SPEC = 2;
    private static final int G_UNIT = 3;
    private static final int G_QTY = 4;
    private static final int G_PRICE = 5;
    private static final int G_SAFE = 6;
    private static final int G_WAREHOUSE = 7;

    /** 库存物资：名称 / 分类 / 规格 / 单位 / 库存 / 单价 / 安全库存 / 仓库。 */
    private static final String[][] GOODS = {
            {"六角螺栓", "五金材料", "M20×80", "套", "860", "3.20", "200", "一号物资库"},
            {"膨胀螺栓", "五金材料", "M12×100", "套", "540", "1.80", "150", "一号物资库"},
            {"焊条", "五金材料", "J422 3.2mm", "kg", "320", "8.60", "100", "一号物资库"},
            {"角磨机切片", "五金材料", "Φ100", "片", "180", "4.50", "60", "一号物资库"},
            {"钢丝刷", "五金材料", "6 行", "把", "96", "6.00", "30", "一号物资库"},
            {"生料带", "五金材料", "20m/卷", "卷", "240", "2.40", "80", "一号物资库"},
            {"轴承", "备品备件", "22320CA", "套", "18", "680.00", "6", "二号物资库"},
            {"三角带", "备品备件", "C-3150", "条", "64", "42.00", "20", "二号物资库"},
            {"衬板", "备品备件", "球磨机用 高锰钢", "块", "36", "1850.00", "12", "二号物资库"},
            {"输送带", "备品备件", "B1000 EP200", "米", "180", "168.00", "60", "二号物资库"},
            {"液压油", "备品备件", "46# 200L/桶", "桶", "12", "2380.00", "4", "二号物资库"},
            {"润滑油", "备品备件", "220# 20L/桶", "桶", "28", "360.00", "10", "二号物资库"},
            {"空滤滤芯", "备品备件", "铲运机配套", "个", "46", "128.00", "16", "二号物资库"},
            {"电缆", "五金材料", "YJV 3×95+1", "米", "420", "86.00", "150", "一号物资库"},
            {"LED 投光灯", "低值易耗", "200W", "盏", "52", "186.00", "20", "一号物资库"},
            {"安全帽", "劳保用品", "ABS 矿用", "顶", "260", "46.00", "100", "劳保库"},
            {"防尘口罩", "劳保用品", "KN95", "只", "1200", "3.60", "400", "劳保库"},
            {"劳保手套", "劳保用品", "加厚帆布", "副", "680", "5.20", "200", "劳保库"},
            {"绝缘手套", "劳保用品", "35kV", "副", "48", "128.00", "20", "劳保库"},
            {"工作服", "劳保用品", "防静电 春秋款", "套", "320", "118.00", "120", "劳保库"},
            {"矿用胶靴", "劳保用品", "42 码", "双", "180", "86.00", "60", "劳保库"},
            {"安全带", "劳保用品", "全身式", "条", "56", "168.00", "20", "劳保库"},
            {"护目镜", "劳保用品", "防冲击", "副", "140", "28.00", "50", "劳保库"},
            {"A4 复印纸", "办公用品", "70g 500张/包", "包", "180", "26.00", "60", "办公库"},
            {"中性笔", "办公用品", "0.5mm 黑色", "支", "420", "1.60", "150", "办公库"},
            {"文件夹", "办公用品", "A4 加厚", "个", "160", "8.80", "50", "办公库"},
            {"订书机", "办公用品", "12#", "个", "24", "26.00", "10", "办公库"},
            {"硒鼓", "办公用品", "惠普 58A", "个", "16", "286.00", "6", "办公库"},
            {"记号笔", "办公用品", "油性 双头", "支", "96", "4.20", "30", "办公库"},
            {"垃圾桶", "低值易耗", "60L 脚踏", "个", "42", "58.00", "15", "一号物资库"},
            {"拖把", "低值易耗", "棉线 加长杆", "把", "68", "18.00", "25", "一号物资库"},
            {"警示带", "低值易耗", "100m/卷", "卷", "36", "32.00", "12", "一号物资库"},
    };

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[rich] 盘点模块已关闭（rich.inventory.enabled=false）");
            return;
        }
        for (String sql : InventorySchemaDdl.DDL) {
            jdbc.execute(sql);
        }
        // 食堂物料作为盘点标的之一，需要二维码列
        addColumnIfMissing("canteen_material", "qr_code", "VARCHAR(64) DEFAULT NULL COMMENT '二维码码值'");

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_task WHERE task_no=?", Integer.class, MARK_TASK);
        if (cnt != null && cnt > 0) {
            log.info("[rich] 盘点数据已存在，跳过初始化");
            return;
        }
        long start = System.currentTimeMillis();
        insertAssets();
        insertGoods();
        markCanteenQrCode();
        inventoryService.generateQrCodes(null);
        generateTasks();
        log.info("[rich] 盘点模块初始化完成，用时 {} ms", System.currentTimeMillis() - start);
    }

    // ================================================================ 档案

    private void insertAssets() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < ASSETS.length; i++) {
            String[] a = ASSETS[i];
            String code = String.format("GD-2026-%04d", i + 1);
            BigDecimal value = new BigDecimal(a[A_VALUE]);
            // 净值按已用年限线性折旧估算，最少保留 5% 残值
            int used = Math.min(Integer.parseInt(a[A_LIFE]), Math.max(1, today.getYear() - 2023));
            double remain = 1.0 - used * 1.0 / Integer.parseInt(a[A_LIFE]);
            BigDecimal net = value.multiply(BigDecimal.valueOf(Math.max(0.05, remain)))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            jdbc.update("INSERT INTO ast_asset (asset_code, asset_name, category, spec, unit, quantity,"
                            + " original_value, net_value, buy_date, useful_life, use_dept, location,"
                            + " custodian, supplier_name, status, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    code, a[A_NAME], a[A_CATEGORY], a[A_SPEC], a[A_UNIT], new BigDecimal(a[A_QTY]),
                    value, net, LocalDate.of(2023 + (i % 3), 1 + (i % 12), 1 + (i % 27)),
                    Integer.parseInt(a[A_LIFE]), a[A_DEPT], a[A_LOC], a[A_KEEPER],
                    "招远市机电设备有限公司", i % 41 == 0 ? "维修" : "在用",
                    a[A_CATEGORY] + "台账", LocalDateTime.now(), LocalDateTime.now());
        }
        log.info("[rich] 固定资产 {} 条", ASSETS.length);
    }

    private void insertGoods() {
        for (int i = 0; i < GOODS.length; i++) {
            String[] g = GOODS[i];
            String code = String.format("WZ-%02d", i + 1);
            BigDecimal qty = new BigDecimal(g[G_QTY]);
            BigDecimal price = new BigDecimal(g[G_PRICE]);
            jdbc.update("INSERT INTO ast_goods (goods_code, goods_name, category, spec, unit, warehouse,"
                            + " stock_qty, avg_price, total_amount, safe_stock, status, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,'正常',?,?,?)",
                    code, g[G_NAME], g[G_CATEGORY], g[G_SPEC], g[G_UNIT], g[G_WAREHOUSE],
                    qty, price, qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP),
                    new BigDecimal(g[G_SAFE]), g[G_CATEGORY] + "库存",
                    LocalDateTime.now(), LocalDateTime.now());
        }
        log.info("[rich] 库存物资 {} 条", GOODS.length);
    }

    /** 为食堂物料补二维码码值。 */
    private void markCanteenQrCode() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, material_code FROM canteen_material WHERE qr_code IS NULL OR qr_code=''");
        for (Map<String, Object> r : rows) {
            jdbc.update("UPDATE canteen_material SET qr_code=? WHERE id=?",
                    "CTN-" + r.get("material_code"), r.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("[rich] 食堂物料赋码 {} 条", rows.size());
        }
    }

    // ================================================================ 盘点任务

    private void generateTasks() {
        LocalDate today = LocalDate.now();

        // ---- 任务一：二季度固定资产全面盘点（已完成）
        InvTask t1 = new InvTask();
        t1.setTaskNo(MARK_TASK);
        t1.setTaskName("2026年二季度固定资产全面盘点");
        t1.setTaskType("固定资产");
        t1.setScopeType("");
        t1.setCheckMode("明盘");
        t1.setPlanDate(LocalDate.of(2026, 6, 30));
        t1.setDeadline(LocalDate.of(2026, 7, 10));
        t1.setOwner("李雲钊");
        t1.setCheckers("王玉芹,刘德强,赵金凤");
        t1.setRemark("二季度末例行全面盘点，含井下设备与地表建筑");
        t1.setCreateBy("系统生成");
        inventoryService.createTask(t1);
        int n1 = inventoryService.generateSubjects(t1.getId());
        checkAll(t1.getId(), "王玉芹", true);
        inventoryService.finishTask(t1.getId());
        log.info("[rich] 盘点任务 {}：{} 条应盘明细，已完成", t1.getTaskNo(), n1);

        // ---- 任务二：当期物资盘点（进行中，只盘了一部分）
        InvTask t2 = new InvTask();
        t2.setTaskNo("PD2026-0002");
        t2.setTaskName(today.getMonthValue() + "月库存物资与食堂物资盘点");
        t2.setTaskType("综合盘点");
        t2.setScopeType("");
        t2.setCheckMode("明盘");
        t2.setPlanDate(today);
        t2.setDeadline(today.plusDays(7));
        t2.setOwner("刘德强");
        t2.setCheckers("刘德强,杨师傅");
        t2.setRemark("月末存货盘点，APP 扫码与 PC 扫码并行");
        t2.setCreateBy("系统生成");
        inventoryService.createTask(t2);
        int n2 = inventoryService.generateSubjects(t2.getId());
        checkPart(t2.getId(), "刘德强", 45);
        log.info("[rich] 盘点任务 {}：{} 条应盘明细，进行中", t2.getTaskNo(), n2);
    }

    /** 全部盘点：绝大多数相符，少量盘盈盘亏；完成后逐条处理差异。 */
    private void checkAll(Long taskId, String checker, boolean handle) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, book_qty FROM inv_record WHERE task_id=? ORDER BY id", taskId);
        int i = 0;
        for (Map<String, Object> r : rows) {
            BigDecimal book = (BigDecimal) r.get("book_qty");
            BigDecimal actual = book;
            if (i % 17 == 3) {
                // 盘亏：少一件或整件丢失
                actual = book.compareTo(BigDecimal.ONE) > 0
                        ? book.subtract(BigDecimal.ONE) : BigDecimal.ZERO;
            } else if (i % 23 == 5) {
                // 盘盈：账外多出一件
                actual = book.add(BigDecimal.ONE);
            }
            inventoryService.submitRecord(taskId, ((Number) r.get("id")).longValue(), actual, checker);
            if (handle) {
                Object diff = jdbc.queryForObject(
                        "SELECT result FROM inv_record WHERE id=?", Object.class, r.get("id"));
                if (diff != null && !"正常".equals(String.valueOf(diff))) {
                    inventoryService.handle(((Number) r.get("id")).longValue(),
                            i % 3 == 0 ? InventoryService.HANDLE_ADJUSTED : InventoryService.HANDLE_CONFIRMED,
                            i % 3 == 0 ? "已按实盘数调整台账" : "差异已确认，列支下期处理", "李雲钊");
                }
            }
            i++;
        }
    }

    /** 部分盘点：只盘前 rate% 的标的，模拟盘点进行中。 */
    private void checkPart(Long taskId, String checker, int rate) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, book_qty FROM inv_record WHERE task_id=? ORDER BY id", taskId);
        int limit = rows.size() * rate / 100;
        for (int i = 0; i < limit; i++) {
            Map<String, Object> r = rows.get(i);
            BigDecimal book = (BigDecimal) r.get("book_qty");
            BigDecimal actual = book;
            if (i % 11 == 2) {
                actual = book.subtract(BigDecimal.valueOf(2));
            } else if (i % 13 == 4) {
                actual = book.add(BigDecimal.valueOf(1));
            }
            inventoryService.submitRecord(taskId, ((Number) r.get("id")).longValue(), actual, checker);
        }
    }

    // ================================================================ 工具

    /** 若列不存在则追加（MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS）。 */
    private void addColumnIfMissing(String table, String column, String definition) {
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
}
