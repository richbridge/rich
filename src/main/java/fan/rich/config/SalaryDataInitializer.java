package fan.rich.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 工资模块初始化器（工资下拨 / 工资上报 / 工资明细 / 核算部门）。
 *
 * <p>应用启动后自动建表（{@code CREATE TABLE IF NOT EXISTS}）并幂等补列，随后在表为空时
 * 写入取自「D:\财务科共享盘\工资\工资」的真实数据。下拨与上报均按核算部门管理：
 * 一个月一个部门一张下拨单、一张上报单，二者一一对应。
 *
 * <p>2026 年 1—7 月按 8 月各部门占比还原到部门维度（已发放），8 月使用各部门工资表真实数据。
 * 已存在数据（以下拨单号 {@code XB2026-01-01} 为标记）时不再重复写入。
 *
 * <p>{@code rich.salary.enabled=false} 可整体关闭。
 */
@Order(5)
@Component
public class SalaryDataInitializer implements ApplicationRunner {

    /** 演示数据的标记编号。 */
    private static final String MARK_ALLOC = "XB2026-01-01";

    /** 接收单位（本公司）。 */
    private static final String OUR_UNIT = "招远市金宝黄金矿业有限公司";

    /** 拨付单位。 */
    private static final String SOURCE_UNIT = "招金有色金属集团有限公司";

    // 部门：编码 / 名称 / 类型 / 负责人 / 在册人数 / 月度工资定额
    private static final int D_CODE = 0;
    private static final int D_NAME = 1;
    private static final int D_TYPE = 2;
    private static final int D_LEADER = 3;
    private static final int D_COUNT = 4;
    private static final int D_QUOTA = 5;

    private static final String[][] DEPTS = {
            {"BM-00", "经营层", "经营层", "孙德华", "11", "135090.12"},
            {"BM-01", "生产技术科", "管理部门", "陈立国", "16", "152692.14"},
            {"BM-02", "安全生产科", "管理部门", "王朦", "7", "55477.29"},
            {"BM-03", "物流设备科", "管理部门", "郝勇凯", "13", "87351.37"},
            {"BM-04", "财务科", "管理部门", "柳鉴益", "3", "18136.74"},
            {"BM-05", "企管科", "管理部门", "王龙梅", "11", "84116.65"},
            {"BM-06", "综合办公室", "管理部门", "黄艺文", "21", "144898.72"},
            {"BM-07", "保卫科", "管理部门", "姜程杰", "17", "109463.49"},
            {"BM-08", "采矿车间", "生产车间", "刘文广", "171", "1649253.00"},
            {"BM-09", "选矿车间", "生产车间", "王旭波", "37", "249196.31"},
            {"BM-10", "外聘", "辅助部门", "李雲钊", "1", "24416.67"},
    };

    // 月度工资总额：月份 / 人数 / 应发 / 代扣 / 个税 / 实发 / 核定总额 / 下拨金额 / 下拨日期
    private static final int M_MONTH = 0;
    private static final int M_COUNT = 1;
    private static final int M_GROSS = 2;
    private static final int M_DEDUCT = 3;
    private static final int M_TAX = 4;
    private static final int M_NET = 5;
    private static final int M_APPROVED = 6;
    private static final int M_ALLOC = 7;
    private static final int M_DATE = 8;

    private static final String[][] MONTHS = {
            {"1", "266", "2176672.05", "515466.41", "89471.95", "1571733.69",
                    "2176672.05", "1571733.69", "2026-01-15"},
            {"2", "297", "2514414.65", "561350.51", "84510.19", "1868553.95",
                    "2514414.65", "1868553.95", "2026-02-15"},
            {"3", "303", "2247578.64", "538796.94", "39351.33", "1669430.37",
                    "2247578.64", "1669430.37", "2026-03-15"},
            {"4", "300", "3208046.05", "543399.29", "56174.17", "2608472.59",
                    "3208046.05", "2608472.59", "2026-04-15"},
            {"5", "304", "2831824.00", "547400.17", "47466.51", "2236957.32",
                    "2831824.00", "2236957.32", "2026-05-15"},
            {"6", "304", "2658280.65", "540906.30", "50153.60", "2067220.75",
                    "2658280.65", "2067220.75", "2026-06-15"},
            {"7", "315", "2488143.65", "542971.74", "22876.08", "1922295.83",
                    "2488143.65", "1922295.83", "2026-07-15"},
            {"8", "314", "2771468.58", "537386.48", "43372.75", "2190709.35",
                    "2771468.58", "2190709.35", "2026-08-15"},
    };

    // 不按部门分解的专项下拨 / 上报
    private static final String[][] SPECIALS = {
            {"XB2026-B01", "2026", "1", "奖金专项", "招金集团有限公司",
                    "186000.00", "186000.00", "2026-01-28", "招金集团人发〔2026〕12号", "李雲钊",
                    "SB2026-B01", "2026-01-28", "2025年度评先树优奖金"},
            {"XB2026-B02", "2026", "6", "专项补贴", "招远市公共就业和人才服务中心",
                    "96000.00", "96000.00", "2026-06-22", "招人社稳岗〔2026〕3号", "李雲钊",
                    "SB2026-B02", "2026-06-22", "2025年稳岗补贴奖励"},
    };

    // 2026年8月各部门上报单：部门 / 人数 / 应发 / 代扣 / 个税 / 实发 / 上报人 / 状态
    private static final int DR_DEPT = 0;
    private static final int DR_COUNT = 1;
    private static final int DR_GROSS = 2;
    private static final int DR_DEDUCT = 3;
    private static final int DR_TAX = 4;
    private static final int DR_NET = 5;
    private static final int DR_REPORTER = 6;
    private static final int DR_STATUS = 7;

    private static final String[][] DEPT_REPORTS = {
            {"经营层", "11", "135090.12", "43716.95", "2162.00", "89211.17", "孙德华", "已审核"},
            {"生产技术科", "16", "152692.14", "38010.62", "2443.71", "112237.81", "陈立国", "已审核"},
            {"安全生产科", "7", "55477.29", "18685.03", "887.87", "35904.39", "王朦", "已审核"},
            {"物流设备科", "13", "87351.37", "23110.90", "1397.99", "62842.48", "郝勇凯", "已审核"},
            {"财务科", "3", "18136.74", "4051.57", "290.26", "13794.91", "柳鉴益", "已审核"},
            {"企管科", "11", "84116.65", "19947.65", "1346.22", "62822.78", "王龙梅", "已审核"},
            {"综合办公室", "21", "144898.72", "36409.73", "2318.98", "106170.01", "黄艺文", "已审核"},
            {"保卫科", "17", "109463.49", "33322.51", "1751.87", "74389.11", "姜程杰", "已审核"},
            {"采矿车间", "171", "1649253.00", "248964.49", "26394.91", "1373893.60", "刘文广", "待审核"},
            {"选矿车间", "37", "249196.31", "65695.84", "3988.18", "179512.29", "王旭波", "待审核"},
            {"外聘", "1", "24416.67", "0", "390.77", "24025.90", "李雲钊", "草稿"},
    };

    /**
     * 2026年8月工资明细（取自各部门 Excel 工资明细表）。
     * 部门 / 姓名 / 出勤 / 岗位工资 / 夜班费 / 下井费 / 补助补贴 / 加班工资 / 绩效奖金 /
     * 安全奖 / 管理奖惩 / 工资总额 / 养老 / 失业 / 医保 / 公积金 / 年金 / 个税
     */
    private static final int P_DEPT = 0;
    private static final int P_NAME = 1;
    private static final int P_ATT = 2;
    private static final int P_BASE = 3;
    private static final int P_NIGHT = 4;
    private static final int P_DOWN = 5;
    private static final int P_SUB = 6;
    private static final int P_OT = 7;
    private static final int P_PERF = 8;
    private static final int P_SAFE = 9;
    private static final int P_MNG = 10;
    private static final int P_GROSS = 11;
    private static final int P_PEN = 12;
    private static final int P_UNE = 13;
    private static final int P_MED = 14;
    private static final int P_HOUS = 15;
    private static final int P_ANN = 16;
    private static final int P_TAX = 17;

    private static final String[][] DETAILS = {
            {"经营层", "孙德华", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "11459.77", "1960.0", "0.0", "22036.67", "1801.44", "67.55", "450.36", "3443.0", "450.36", "964.79"},
            {"经营层", "崔云龙", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "6459.77", "1529.6", "0.0", "17987.87", "1801.44", "67.55", "450.36", "3093.0", "450.36", "825.23"},
            {"经营层", "李涛", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "6459.77", "1699.2", "0.0", "18189.07", "1801.44", "67.55", "450.36", "3217.0", "450.36", "1140.47"},
            {"经营层", "侯志林", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "6244.72", "1505.6", "0.0", "17794.82", "1538.4", "57.69", "384.6", "2308.0", "384.6", "524.31"},
            {"经营层", "王立彬", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "6244.72", "1553.6", "0.0", "17350.82", "1302.64", "48.85", "325.66", "1954.0", "325.66", "1378.8"},
            {"经营层", "陈明杰", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "6459.77", "1200.0", "0.0", "13416.67", "1801.44", "67.55", "450.36", "3443.0", "450.36", "140.79"},
            {"经营层", "邹冬波", "31", "3000.0", "0.0", "0.0", "0.0", "2206.9", "4793.1", "1321.6", "0.0", "15045.2", "1801.44", "67.55", "450.36", "3443.0", "450.36", "0.0"},
            {"经营层", "刘怀江", "0", "3133.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "3133.0", "360.32", "13.51", "90.08", "385.0", "90.08", "0.0"},
            {"经营层", "王旭峰", "0", "3397.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "3397.0", "412.96", "15.49", "103.24", "619.0", "103.24", "0.0"},
            {"经营层", "王晓军", "0", "3109.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "3109.0", "360.32", "13.51", "90.08", "502.0", "90.08", "0.0"},
            {"经营层", "郭建英", "0", "3630.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "3630.0", "444.48", "16.67", "111.12", "667.0", "111.12", "0.0"},
            {"财务科", "王晓欣", "27", "2400.0", "0.0", "0.0", "450.0", "882.76", "1691.48", "700.0", "0.0", "6384.24", "555.2", "20.82", "138.8", "833.0", "138.8", "0.0"},
            {"财务科", "任宁", "23", "2400.0", "0.0", "0.0", "450.0", "0.0", "1083.5", "700.0", "0.0", "4923.5", "501.44", "18.8", "125.36", "752.0", "125.36", "0.0"},
            {"财务科", "单小良", "23", "2400.0", "0.0", "10.0", "2000.0", "0.0", "1379.0", "800.0", "-50.0", "6829.0", "360.32", "13.51", "90.08", "288.0", "90.08", "29.61"},
            {"企管科", "王龙梅", "29", "3200.0", "0.0", "0.0", "1150.0", "1765.52", "4528.25", "1400.0", "-200.0", "12133.77", "1259.84", "47.24", "314.96", "1890.0", "314.96", "0.0"},
            {"企管科", "李玉磊", "27", "2600.0", "120.0", "0.0", "500.0", "956.32", "3079.24", "1200.0", "0.0", "8745.56", "573.12", "21.49", "143.28", "860.0", "143.28", "0.0"},
            {"企管科", "徐丽君", "27", "2400.0", "0.0", "0.0", "450.0", "882.76", "1106.24", "700.0", "50.0", "5879.0", "504.32", "18.91", "126.08", "756.0", "126.08", "0.0"},
            {"企管科", "郭正仁", "27", "2400.0", "180.0", "0.0", "450.0", "882.76", "1403.24", "700.0", "50.0", "6326.0", "513.36", "19.25", "128.34", "770.0", "128.34", "0.0"},
            {"企管科", "刘娟", "27", "2400.0", "0.0", "0.0", "450.0", "882.76", "1601.25", "1000.0", "50.0", "6644.01", "554.72", "20.8", "138.68", "832.0", "138.68", "0.0"},
            {"企管科", "赵垠杰", "23", "2400.0", "0.0", "0.0", "450.0", "0.0", "1201.83", "777.42", "0.0", "5119.25", "473.92", "17.77", "118.48", "711.0", "118.48", "0.0"},
            {"企管科", "谭伟", "27.5", "2400.0", "180.0", "10.0", "450.0", "993.1", "2977.9", "800.0", "100.0", "8171.0", "810.88", "30.41", "202.72", "1216.0", "202.72", "21.24"},
            {"企管科", "范伟鸿", "23", "2400.0", "0.0", "0.0", "450.0", "0.0", "3725.28", "1000.0", "-20.0", "7845.28", "641.76", "24.07", "160.44", "963.0", "160.44", "0.0"},
            {"企管科", "曹进园", "23", "2400.0", "0.0", "0.0", "450.0", "0.0", "3713.74", "900.0", "-10.0", "7753.74", "606.08", "22.73", "151.52", "909.0", "151.52", "0.0"},
            {"企管科", "于娴", "23", "2400.0", "0.0", "0.0", "1400.0", "0.0", "3395.3", "900.0", "-10.0", "8375.3", "360.32", "13.51", "90.08", "515.0", "90.08", "69.19"},
            {"企管科", "刘晓庆", "23", "2400.0", "0.0", "0.0", "450.0", "0.0", "3093.74", "900.0", "-10.0", "7123.74", "360.32", "13.51", "90.08", "288.0", "90.08", "0.0"},
            {"安全生产科", "王朦", "29.5", "2600.0", "150.0", "30.0", "500.0", "1554.02", "3682.58", "1750.0", "-2100.0", "8456.6", "1231.92", "46.2", "307.98", "1848.0", "307.98", "0.0"},
            {"安全生产科", "杨卫鹏", "30", "2400.0", "180.0", "0.0", "450.0", "1544.83", "3786.81", "1460.0", "-1450.0", "8661.64", "992.08", "37.2", "248.02", "1488.0", "248.02", "0.0"},
            {"安全生产科", "杨鑫", "29", "2400.0", "60.0", "310.0", "800.0", "1324.14", "3800.05", "1260.0", "-450.0", "9794.19", "816.64", "30.62", "204.16", "1225.0", "204.16", "0.0"},
            {"安全生产科", "赵俊刚", "0", "1920.0", "0.0", "0.0", "50.0", "0.0", "0.0", "0.0", "0.0", "1960.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0"},
            {"安全生产科", "冯强", "29", "2400.0", "150.0", "0.0", "450.0", "1324.14", "3120.0", "1120.0", "-50.0", "8804.14", "866.08", "32.48", "216.52", "1299.0", "216.52", "39.71"},
            {"安全生产科", "李正", "29", "2400.0", "180.0", "60.0", "450.0", "1324.14", "2721.41", "1320.0", "-450.0", "8295.55", "1801.44", "67.55", "450.36", "2803.0", "450.36", "0.0"},
            {"安全生产科", "李泳省", "29", "2400.0", "210.0", "250.0", "750.0", "1324.14", "3521.03", "1260.0", "-500.0", "9505.17", "410.24", "15.38", "102.56", "615.0", "102.56", "97.78"},
    };

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean initDemo;
    private final boolean resetDemo;

    public SalaryDataInitializer(JdbcTemplate jdbc,
                                 @Value("${rich.salary.enabled:true}") boolean enabled,
                                 @Value("${rich.salary.init-demo:true}") boolean initDemo,
                                 @Value("${rich.salary.reset-demo:true}") boolean resetDemo) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.initDemo = initDemo;
        this.resetDemo = resetDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            createTables();
            addColumnIfMissing("salary_alloc", "dept_id", "BIGINT DEFAULT NULL COMMENT '下拨部门ID'");
            addColumnIfMissing("salary_alloc", "dept_name", "VARCHAR(100) DEFAULT NULL COMMENT '下拨部门'");
            addColumnIfMissing("salary_alloc", "headcount", "INT DEFAULT 0 COMMENT '部门在册人数'");
            if (initDemo && countBy("SELECT COUNT(*) FROM salary_alloc WHERE alloc_no = ?", MARK_ALLOC) == 0) {
                if (resetDemo) {
                    clearAll();
                }
                insertDepts();
                int allocs = insertDeptAllocs();
                int reports = insertDeptReports();
                insertSpecials();
                insertDetails();
                refreshAll();
                System.out.println("[rich] 工资模块演示数据已写入（" + allocs + " 张部门下拨单 / "
                        + reports + " 张部门上报单 / " + DETAILS.length + " 条工资明细）");
            }
            System.out.println("[rich] 工资模块表结构就绪");
        } catch (Exception e) {
            System.err.println("[rich] 工资模块初始化失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ DDL

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS salary_dept (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  dept_code VARCHAR(32) DEFAULT NULL COMMENT '部门编码',
                  dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
                  dept_type VARCHAR(32) DEFAULT NULL COMMENT '部门类型：管理部门/生产车间/辅助部门/经营层',
                  leader VARCHAR(64) DEFAULT NULL COMMENT '部门负责人',
                  headcount INT DEFAULT 0 COMMENT '在册人数',
                  month_quota DECIMAL(18,2) DEFAULT 0 COMMENT '月度工资定额(元)',
                  status VARCHAR(20) DEFAULT '启用' COMMENT '状态：启用/停用',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_salary_dept_code (dept_code),
                  KEY idx_salary_dept_name (dept_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资核算部门'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS salary_alloc (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  alloc_no VARCHAR(32) DEFAULT NULL COMMENT '下拨单号',
                  period_year INT DEFAULT NULL COMMENT '所属年度',
                  period_month INT DEFAULT NULL COMMENT '所属月份',
                  alloc_type VARCHAR(32) DEFAULT NULL COMMENT '下拨类型：月度工资总额/奖金专项/年终兑现/专项补贴/补拨',
                  dept_id BIGINT DEFAULT NULL COMMENT '下拨部门ID',
                  dept_name VARCHAR(100) DEFAULT NULL COMMENT '下拨部门',
                  headcount INT DEFAULT 0 COMMENT '部门在册人数',
                  source_unit VARCHAR(200) DEFAULT NULL COMMENT '拨付单位',
                  target_unit VARCHAR(200) DEFAULT NULL COMMENT '接收单位',
                  approved_amount DECIMAL(18,2) DEFAULT 0 COMMENT '核定工资总额(元)',
                  alloc_amount DECIMAL(18,2) DEFAULT 0 COMMENT '实际下拨金额(元)',
                  used_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已发放金额(元)',
                  balance_amount DECIMAL(18,2) DEFAULT 0 COMMENT '结余金额(元)',
                  alloc_date DATE DEFAULT NULL COMMENT '下拨日期',
                  voucher_no VARCHAR(100) DEFAULT NULL COMMENT '拨付文号/凭证号',
                  handler VARCHAR(64) DEFAULT NULL COMMENT '经办人',
                  status VARCHAR(20) DEFAULT '待下拨' COMMENT '状态：待下拨/已下拨/部分使用/已结算',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_salary_alloc_no (alloc_no),
                  KEY idx_salary_alloc_period (period_year, period_month),
                  KEY idx_salary_alloc_dept (dept_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资下拨单（按部门）'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS salary_report (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  report_no VARCHAR(32) DEFAULT NULL COMMENT '上报单号',
                  period_year INT DEFAULT NULL COMMENT '所属年度',
                  period_month INT DEFAULT NULL COMMENT '所属月份',
                  dept_id BIGINT DEFAULT NULL COMMENT '上报部门ID',
                  dept_name VARCHAR(100) DEFAULT NULL COMMENT '上报部门',
                  alloc_id BIGINT DEFAULT NULL COMMENT '关联下拨单ID',
                  alloc_no VARCHAR(32) DEFAULT NULL COMMENT '关联下拨单号',
                  report_type VARCHAR(32) DEFAULT NULL COMMENT '上报类型：部门工资表上报/专项上报',
                  headcount INT DEFAULT 0 COMMENT '上报人数',
                  gross_amount DECIMAL(18,2) DEFAULT 0 COMMENT '应发工资合计(元)',
                  deduct_amount DECIMAL(18,2) DEFAULT 0 COMMENT '代扣合计(元)',
                  tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '个税合计(元)',
                  net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '实发工资合计(元)',
                  report_date DATE DEFAULT NULL COMMENT '上报日期',
                  reporter VARCHAR(64) DEFAULT NULL COMMENT '上报人',
                  auditor VARCHAR(64) DEFAULT NULL COMMENT '审核人',
                  audit_date DATE DEFAULT NULL COMMENT '审核日期',
                  audit_opinion VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
                  status VARCHAR(20) DEFAULT '草稿' COMMENT '状态：草稿/待审核/已审核/已驳回/已发放',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_salary_report_no (report_no),
                  KEY idx_salary_report_period (period_year, period_month),
                  KEY idx_salary_report_alloc (alloc_id),
                  KEY idx_salary_report_dept (dept_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资上报单（按部门）'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS salary_detail (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  report_id BIGINT DEFAULT NULL COMMENT '关联上报单ID',
                  report_no VARCHAR(32) DEFAULT NULL COMMENT '关联上报单号',
                  period_year INT DEFAULT NULL COMMENT '所属年度',
                  period_month INT DEFAULT NULL COMMENT '所属月份',
                  dept_name VARCHAR(100) DEFAULT NULL COMMENT '所在部门',
                  emp_no VARCHAR(32) DEFAULT NULL COMMENT '工号',
                  emp_name VARCHAR(64) DEFAULT NULL COMMENT '姓名',
                  post VARCHAR(64) DEFAULT NULL COMMENT '岗位/工种',
                  emp_type VARCHAR(20) DEFAULT '在册' COMMENT '用工类型：在册/劳务派遣/外聘/实习',
                  attendance DECIMAL(6,1) DEFAULT 0 COMMENT '出勤天数',
                  base_wage DECIMAL(18,2) DEFAULT 0 COMMENT '岗位工资',
                  night_fee DECIMAL(18,2) DEFAULT 0 COMMENT '夜班费',
                  downhole_fee DECIMAL(18,2) DEFAULT 0 COMMENT '下井费',
                  subsidy DECIMAL(18,2) DEFAULT 0 COMMENT '补助补贴',
                  overtime_wage DECIMAL(18,2) DEFAULT 0 COMMENT '加班工资',
                  performance DECIMAL(18,2) DEFAULT 0 COMMENT '绩效奖金',
                  safety_bonus DECIMAL(18,2) DEFAULT 0 COMMENT '安全奖',
                  manage_award DECIMAL(18,2) DEFAULT 0 COMMENT '管理奖惩',
                  other_allow DECIMAL(18,2) DEFAULT 0 COMMENT '其他：高温补贴/床位费/乘车费等',
                  gross_amount DECIMAL(18,2) DEFAULT 0 COMMENT '工资总额(元)',
                  pension DECIMAL(18,2) DEFAULT 0 COMMENT '养老保险',
                  unemployment DECIMAL(18,2) DEFAULT 0 COMMENT '失业保险',
                  medical DECIMAL(18,2) DEFAULT 0 COMMENT '医疗保险',
                  housing_fund DECIMAL(18,2) DEFAULT 0 COMMENT '住房公积金',
                  annuity DECIMAL(18,2) DEFAULT 0 COMMENT '企业年金',
                  tax DECIMAL(18,2) DEFAULT 0 COMMENT '个人所得税',
                  net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '实发工资(元)',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_salary_detail_report (report_id),
                  KEY idx_salary_detail_emp (emp_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资明细'
                """);
    }

    /** MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断后补列。 */
    private void addColumnIfMissing(String table, String column, String definition) {
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        if (cnt == null || cnt == 0) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    /** 清空工资模块数据。 */
    private void clearAll() {
        jdbc.execute("DELETE FROM salary_detail");
        jdbc.execute("DELETE FROM salary_report");
        jdbc.execute("DELETE FROM salary_alloc");
        jdbc.execute("DELETE FROM salary_dept");
    }

    // ------------------------------------------------------------------ 演示数据

    private void insertDepts() {
        for (String[] d : DEPTS) {
            jdbc.update("INSERT INTO salary_dept (dept_code, dept_name, dept_type, leader,"
                            + " headcount, month_quota, status) VALUES (?,?,?,?,?,?,?)",
                    d[D_CODE], d[D_NAME], d[D_TYPE], d[D_LEADER],
                    Integer.parseInt(d[D_COUNT]), new BigDecimal(d[D_QUOTA]), "启用");
        }
    }

    /**
     * 按月按部门生成下拨单：核定总额按部门应发占比分解，下拨金额按部门实发占比分解。
     *
     * @return 生成的下拨单数量
     */
    private int insertDeptAllocs() {
        BigDecimal[] rGross = ratio(DR_GROSS);
        BigDecimal[] rNet = ratio(DR_NET);
        int count = 0;
        for (String[] m : MONTHS) {
            int month = Integer.parseInt(m[M_MONTH]);
            BigDecimal approvedTotal = new BigDecimal(m[M_APPROVED]);
            BigDecimal allocTotal = new BigDecimal(m[M_ALLOC]);
            for (int i = 0; i < DEPTS.length; i++) {
                boolean last = i == DEPTS.length - 1;
                BigDecimal approved = last ? approvedTotal.subtract(sumBefore(approvedTotal, rGross, i))
                        : approvedTotal.multiply(rGross[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal allocAmount = last ? allocTotal.subtract(sumBefore(allocTotal, rNet, i))
                        : allocTotal.multiply(rNet[i]).setScale(2, RoundingMode.HALF_UP);
                jdbc.update("INSERT INTO salary_alloc (alloc_no, period_year, period_month,"
                                + " alloc_type, dept_id, dept_name, headcount, source_unit,"
                                + " target_unit, approved_amount, alloc_amount, used_amount,"
                                + " balance_amount, alloc_date, voucher_no, handler, status)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?)",
                        allocNo(month, i + 1), 2026, month, "月度工资总额",
                        queryDeptId(DEPTS[i][D_NAME]), DEPTS[i][D_NAME],
                        Integer.parseInt(DEPTS[i][D_COUNT]), SOURCE_UNIT, OUR_UNIT,
                        approved, allocAmount, allocAmount,
                        LocalDate.parse(m[M_DATE]),
                        String.format("招金有色人发〔2026〕%d号", month), "柳鉴益", "待下拨");
                count++;
            }
        }
        return count;
    }

    /**
     * 按月按部门生成上报单：1—7 月按部门占比还原（已发放），8 月使用各部门工资表真实数据。
     *
     * @return 生成的上报单数量
     */
    private int insertDeptReports() {
        BigDecimal[] rGross = ratio(DR_GROSS);
        BigDecimal[] rDeduct = ratio(DR_DEDUCT);
        BigDecimal[] rTax = ratio(DR_TAX);
        BigDecimal[] rNet = ratio(DR_NET);
        int count = 0;
        for (String[] m : MONTHS) {
            int month = Integer.parseInt(m[M_MONTH]);
            boolean real = month == 8;
            BigDecimal grossTotal = new BigDecimal(m[M_GROSS]);
            BigDecimal deductTotal = new BigDecimal(m[M_DEDUCT]);
            BigDecimal taxTotal = new BigDecimal(m[M_TAX]);
            BigDecimal netTotal = new BigDecimal(m[M_NET]);
            for (int i = 0; i < DEPTS.length; i++) {
                boolean last = i == DEPTS.length - 1;
                BigDecimal gross;
                BigDecimal deduct;
                BigDecimal tax;
                BigDecimal net;
                int headcount;
                String status;
                if (real) {
                    String[] d = DEPT_REPORTS[i];
                    gross = new BigDecimal(d[DR_GROSS]);
                    deduct = new BigDecimal(d[DR_DEDUCT]);
                    tax = new BigDecimal(d[DR_TAX]);
                    net = new BigDecimal(d[DR_NET]);
                    headcount = Integer.parseInt(d[DR_COUNT]);
                    status = d[DR_STATUS];
                } else {
                    gross = last ? grossTotal.subtract(sumBefore(grossTotal, rGross, i))
                            : grossTotal.multiply(rGross[i]).setScale(2, RoundingMode.HALF_UP);
                    deduct = last ? deductTotal.subtract(sumBefore(deductTotal, rDeduct, i))
                            : deductTotal.multiply(rDeduct[i]).setScale(2, RoundingMode.HALF_UP);
                    tax = last ? taxTotal.subtract(sumBefore(taxTotal, rTax, i))
                            : taxTotal.multiply(rTax[i]).setScale(2, RoundingMode.HALF_UP);
                    net = last ? netTotal.subtract(sumBefore(netTotal, rNet, i))
                            : netTotal.multiply(rNet[i]).setScale(2, RoundingMode.HALF_UP);
                    headcount = Integer.parseInt(DEPTS[i][D_COUNT]);
                    status = "已发放";
                }
                boolean audited = "已审核".equals(status) || "已发放".equals(status);
                String allocNo = allocNo(month, i + 1);
                jdbc.update("INSERT INTO salary_report (report_no, period_year, period_month,"
                                + " dept_id, dept_name, alloc_id, alloc_no, report_type, headcount,"
                                + " gross_amount, deduct_amount, tax_amount, net_amount, report_date,"
                                + " reporter, auditor, audit_date, audit_opinion, status, remark)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        reportNo(month, i + 1), 2026, month,
                        queryDeptId(DEPTS[i][D_NAME]), DEPTS[i][D_NAME],
                        queryAllocId(allocNo), allocNo, "部门工资表上报", headcount,
                        gross, deduct, tax, net,
                        "草稿".equals(status) ? null : LocalDate.of(2026, month, real ? 10 : 18),
                        DEPT_REPORTS[i][DR_REPORTER],
                        audited ? (real ? "柳鉴益" : "王殿华") : null,
                        audited ? LocalDate.of(2026, month, real ? 12 : 19) : null,
                        audited ? "工资表与考勤、考核结果一致，准予发放" : null,
                        status, null);
                count++;
            }
        }
        return count;
    }

    /** 不按部门分解的专项下拨单与上报单。 */
    private void insertSpecials() {
        for (String[] s : SPECIALS) {
            BigDecimal amount = new BigDecimal(s[6]);
            jdbc.update("INSERT INTO salary_alloc (alloc_no, period_year, period_month, alloc_type,"
                            + " dept_name, source_unit, target_unit, approved_amount, alloc_amount,"
                            + " used_amount, balance_amount, alloc_date, voucher_no, handler, status)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,0,?,?,?,?,?)",
                    s[0], Integer.parseInt(s[1]), Integer.parseInt(s[2]), s[3], "全公司",
                    s[4], OUR_UNIT, new BigDecimal(s[5]), amount, amount,
                    LocalDate.parse(s[7]), s[8], s[9], "待下拨");
            Long allocId = queryAllocId(s[0]);
            jdbc.update("INSERT INTO salary_report (report_no, period_year, period_month, dept_name,"
                            + " alloc_id, alloc_no, report_type, headcount, gross_amount,"
                            + " deduct_amount, tax_amount, net_amount, report_date, reporter,"
                            + " auditor, audit_date, audit_opinion, status, remark)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    s[10], Integer.parseInt(s[1]), Integer.parseInt(s[2]), "全公司",
                    allocId, s[0], "专项上报", 0, new BigDecimal(s[5]),
                    BigDecimal.ZERO, BigDecimal.ZERO, amount,
                    LocalDate.parse(s[11]), s[9], "王殿华",
                    LocalDate.parse(s[11]).plusDays(1), "审核通过，准予发放", "已发放", s[12]);
        }
    }

    /** 工资明细：工资总额由分项汇总，差额计入「其他」，实发 = 总额 - 代扣 - 个税。 */
    private void insertDetails() {
        Map<String, Long> deptReport = new HashMap<>();
        for (int i = 0; i < DEPT_REPORTS.length; i++) {
            deptReport.put(DEPT_REPORTS[i][DR_DEPT], queryReportId(reportNo(8, i + 1)));
        }
        int seq = 0;
        for (String[] p : DETAILS) {
            seq++;
            BigDecimal base = dec(p[P_BASE]);
            BigDecimal night = dec(p[P_NIGHT]);
            BigDecimal down = dec(p[P_DOWN]);
            BigDecimal sub = dec(p[P_SUB]);
            BigDecimal ot = dec(p[P_OT]);
            BigDecimal perf = dec(p[P_PERF]);
            BigDecimal safe = dec(p[P_SAFE]);
            BigDecimal mng = dec(p[P_MNG]);
            BigDecimal gross = dec(p[P_GROSS]);
            // 未单独列出的补贴（高温补贴、床位费、乘车费等）并入「其他」，保证分项之和等于工资总额
            BigDecimal other = gross.subtract(base).subtract(night).subtract(down).subtract(sub)
                    .subtract(ot).subtract(perf).subtract(safe).subtract(mng)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal pen = dec(p[P_PEN]);
            BigDecimal une = dec(p[P_UNE]);
            BigDecimal med = dec(p[P_MED]);
            BigDecimal hous = dec(p[P_HOUS]);
            BigDecimal ann = dec(p[P_ANN]);
            BigDecimal tax = dec(p[P_TAX]);
            BigDecimal net = gross.subtract(pen).subtract(une).subtract(med).subtract(hous)
                    .subtract(ann).subtract(tax).setScale(2, RoundingMode.HALF_UP);
            String reportNo = reportNo(8, indexOfDept(p[P_DEPT]) + 1);
            jdbc.update("INSERT INTO salary_detail (report_id, report_no, period_year, period_month,"
                            + " dept_name, emp_no, emp_name, post, emp_type, attendance, base_wage,"
                            + " night_fee, downhole_fee, subsidy, overtime_wage, performance,"
                            + " safety_bonus, manage_award, other_allow, gross_amount, pension,"
                            + " unemployment, medical, housing_fund, annuity, tax, net_amount)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    deptReport.get(p[P_DEPT]), reportNo, 2026, 8, p[P_DEPT],
                    String.format("JG%04d", seq), p[P_NAME], null, "在册",
                    dec(p[P_ATT]), base, night, down, sub, ot, perf, safe, mng, other, gross,
                    pen, une, med, hous, ann, tax, net);
        }
    }

    /** 回填下拨单的已发放金额、结余与状态。 */
    private void refreshAll() {
        jdbc.update("UPDATE salary_alloc a SET used_amount = IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0),"
                        + " balance_amount = a.alloc_amount - IFNULL(("
                        + " SELECT SUM(r.net_amount) FROM salary_report r WHERE r.alloc_id = a.id"
                        + " AND r.status IN ('已审核','已发放')),0)");
        jdbc.update("UPDATE salary_alloc SET status = CASE"
                + " WHEN status = '已结算' THEN status"
                + " WHEN used_amount <= 0 THEN '已下拨'"
                + " WHEN balance_amount <= 0 THEN '已结算'"
                + " ELSE '部分使用' END");
    }

    // ------------------------------------------------------------------ 工具

    /** 部门下拨单号：XB2026-08-01。 */
    private String allocNo(int month, int seq) {
        return String.format("XB2026-%02d-%02d", month, seq);
    }

    /** 部门上报单号：SB2026-08-01。 */
    private String reportNo(int month, int seq) {
        return String.format("SB2026-%02d-%02d", month, seq);
    }

    /** 按 8 月各部门真实数据计算各金额项占比。 */
    private BigDecimal[] ratio(int column) {
        BigDecimal total = BigDecimal.ZERO;
        for (String[] d : DEPT_REPORTS) {
            total = total.add(new BigDecimal(d[column]));
        }
        BigDecimal[] ratios = new BigDecimal[DEPT_REPORTS.length];
        for (int i = 0; i < DEPT_REPORTS.length; i++) {
            ratios[i] = total.signum() == 0 ? BigDecimal.ZERO
                    : new BigDecimal(DEPT_REPORTS[i][column]).divide(total, 10, RoundingMode.HALF_UP);
        }
        return ratios;
    }

    /** 末位补齐差额时，前 n 项已分配金额之和。 */
    private BigDecimal sumBefore(BigDecimal total, BigDecimal[] ratios, int n) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            sum = sum.add(total.multiply(ratios[i]).setScale(2, RoundingMode.HALF_UP));
        }
        return sum;
    }

    private BigDecimal dec(String v) {
        return v == null || v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
    }

    private int indexOfDept(String deptName) {
        for (int i = 0; i < DEPT_REPORTS.length; i++) {
            if (DEPT_REPORTS[i][DR_DEPT].equals(deptName)) {
                return i;
            }
        }
        return 0;
    }

    private long countBy(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private Long queryAllocId(String allocNo) {
        return queryLong("SELECT id FROM salary_alloc WHERE alloc_no = ?", allocNo);
    }

    private Long queryDeptId(String deptName) {
        return queryLong("SELECT id FROM salary_dept WHERE dept_name = ?", deptName);
    }

    private Long queryReportId(String reportNo) {
        return queryLong("SELECT id FROM salary_report WHERE report_no = ?", reportNo);
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }
}
