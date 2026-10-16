package fan.rich.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计 / 检查模块初始化器（同一个模块：审计整改台账 + 检查发现问题）。
 *
 * <p>应用启动后自动建表（{@code CREATE TABLE IF NOT EXISTS}）、存量库补列，
 * 并把《招金有色二季度财务检查整改台账》的 12 条真实问题写入整改台账与检查发现问题。
 * 台账数据以 {@code rich.audit.project-no} 标识，已存在时不再重复写入；
 * 旧的演示数据由 {@code rich.audit.reset-demo} 控制在首次写入前清空。
 *
 * <p>{@code rich.audit.enabled=false} 可整体关闭。
 */
@Order(3)
@Component
public class AuditDataInitializer implements ApplicationRunner {

    /** 台账对应的项目编号，同时作为「是否已导入真实台账」的标记。 */
    private static final String PROJECT_NO = "JC2026-Q2";

    /** 台账对应的检查任务编号。 */
    private static final String TASK_NO = "JC2026-Q2-01";

    /** 台账发现期间。 */
    private static final String PERIOD = "2026年二季度";

    // 台账列下标：类型 / 单位 / 内容 / 建议 / 方案 / 时限 / 责任人 / 整改情况 / 金额 / 备注 / 严重程度
    private static final int C_TYPE = 0;
    private static final int C_UNIT = 1;
    private static final int C_DESC = 2;
    private static final int C_SUGGEST = 3;
    private static final int C_PLAN = 4;
    private static final int C_DEADLINE = 5;
    private static final int C_OWNER = 6;
    private static final int C_PROGRESS = 7;
    private static final int C_AMOUNT = 8;
    private static final int C_REMARK = 9;
    private static final int C_SEVERITY = 10;

    /**
     * 《招金有色二季度财务检查整改台账》原始台账。
     *
     * <p>整改时限为「持续整改」时表示长期整改，不设具体到期日；其余为具体日期。
     */
    private static final String[][] LEDGER = {
            {"税务、专利管理", "财务科",
                    "2026年6月4-122号凭证，交专利证书年费滞纳金120元，四个专利证书均未在规定期限内缴款。",
                    "压实岗位责任，严格按照税法规定进行税款申报和缴纳，按照知识产权管理要求按期完成费用缴纳，杜绝逾期滞纳金再次发生。同时，金宝矿业应重点对各税种纳税风险进行全面系统梳理，严格做到“应缴尽缴”，避免税收风险。",
                    "沟通业务部门做好专利台账管理，按时缴纳专利年费。",
                    "持续整改", "王殿华", "持续整改", "120", "", "一般"},
            {"税务、专利管理", "财务科",
                    "2026年6月4-136号凭证，补缴2025年一季度到2026年一季度城镇土地使用税滞纳金79790.29元。",
                    "", "加强税法知识学习，按规申报缴纳税款。",
                    "持续整改", "柳鉴益", "持续整改", "79790.29", "", "较重"},
            {"税务、专利管理", "财务科",
                    "2026年6月4-64号凭证，交2026年5月份税费耕地占用税滞纳金4361.90元，未及时足额缴纳城镇土地使用税耕地占用税。经了解，2026年4月30日取得建设项目临时占用林地批复，部门间未及时沟通，导致未及时办理税费申报缴纳。",
                    "", "加强业务部门间沟通，重点加强与生产技术科沟通。",
                    "持续整改", "柳鉴益、李雲钊", "持续整改", "4361.90", "", "较重"},
            {"财务管理", "财务科",
                    "计提安措附件只有按采矿量计提依据来源，缺少安措费补提金额相关支撑资料，无补提依据。",
                    "补充安措费补提对应的计算过程、调整原因等佐证资料，完善补提金额计提依据，做到数据来源清晰、逻辑可追溯。",
                    "补充专项储备明细账作为附件，完善补提依据。",
                    "2026-08-10", "柳鉴益、任宁", "已整改", "0", "", "一般"},
            {"财务管理", "财务科",
                    "2026年6月5-81号凭证，出售黄金16,608,240元，附件提金单、磅单和水分单，未附正式的预结算单。",
                    "依据提金单信息自行编制预结算单据，单据列明回数、数量、单价、金额等关键结算要素，完善附件资料，确保凭证附件与会计凭证具有直接的对应关系。",
                    "根据提金单相关数据，编制预结算单据，完善附件资料。",
                    "2026-08-10", "柳鉴益", "已整改", "0", "", "较重"},
            {"财务管理", "财务科",
                    "收据开具不规范，如：2026年6月3-2号凭证，收到款项考核款2000元，收款分别来自王旭波、臧洪虎各1000元，收据合并开具。",
                    "严格执行“一人一据”，对不同付款方的款项分别开具收据，确保每张收据的付款方名称、金额、事由等信息清晰独立。",
                    "收回原收据，重新开具收据，严格执行“一人一据”。",
                    "2026-08-10", "柳鉴益、王晓欣", "已整改", "2000", "", "一般"},
            {"财务管理", "财务科",
                    "会计科目运用不准确，如：经营层电话费未入福利费，但后附详细的个人报销明细。",
                    "附电话费明细的报销，应计入福利费。",
                    "对本年度前期业务进行更正，对后续业务加强科目运用规范。",
                    "2026-08-10", "柳鉴益、王晓欣", "已整改", "0", "", "一般"},
            {"财务管理", "财务科",
                    "差旅申请单、报销单事由模糊，存在虚报风险。如2026年6月4-65号凭证付郝勇凯报销差旅费1620元，附件出差申请单、费用报销单、凭证摘要均显示烟台出差，未写明具体出差事由。",
                    "出差申请单须列明具体事由，报销时须与实际行程、会议通知或工作记录相互印证。",
                    "已通知各部门，后续业务加以规范。",
                    "2026-08-10", "柳鉴益、王晓欣", "已整改", "1620", "", "较重"},
            {"财务管理", "财务科",
                    "差旅费、培训费报销发票备注信息不完整。如2026年5月4-15号凭证，付王文源报销差旅费80元，手撕发票未备注相应行程。2026年5月4-19号凭证张元征报销经伦学校应救援证复审费用，住宿费发票显示住宿费1间1300元，未详细注明住宿天数、人数等关键信息。",
                    "财务审核环节要严把票据关口，对备注内容缺失的发票及时退回补充完善，持续规范差旅费报销资料。",
                    "报销附件完善相关信息。",
                    "2026-08-10", "柳鉴益、王晓欣", "已整改", "1380", "", "一般"},
            {"采购管理", "物流设备科",
                    "质保金支付管控不严，多笔款项未按合同约定时限付款，存在履约违约隐患。如：2026年6月4-127号凭证，付淄博广梓机械有限公司矿车质保金，合同约定质保期满一年后30天内一次付清，经查，金宝矿业与该公司于2024年10月31日签订合同，2025年3月12日验收，2026年3月应支付质保金未支付，实际于2026年6月29日支付。虽不造成公司经济损失，但存在一定的违约风险，且可能对公司声誉造成不良影响，2026年6月4-129号凭证支付铲运机质保金存在同样的问题。",
                    "业务归口部门自行建立合同全周期台账，按合同条款标注质保到期、款项应付时间，严格依照合同约定节点办理质保金支付，防控合同违约风险，维护企业对外履约信誉。",
                    "检查问题移交物流设备科，沟通其梳理各自业务及欠款情况，建立采购业务台账，按合同约定及时付款。",
                    "2026-08-10", "王殿华", "已移交，整改中", "0", "", "较重"},
            {"食堂管理", "食堂",
                    "食堂未执行月度对账制度，账务核算差错未及时发现，2026年6月末食堂往来明细账与公司财务总账余额不一致。经查，食堂账2026年4月份8号凭证收矿部拨款27653.81元，凭证金额错误，实收应为10444.41元。",
                    "更正食堂会计凭证金额，做到账、单、款三者一致；固化月度对账机制，食堂每月定期与财务部核对往来收支，发现错误及时调整；强化制单人员审核责任，凭证录入后增加交叉复核，保障食堂账务核算真实准确。",
                    "检查问题移交综合办公室，更正前期账务，后续每月与其对账，加强对食堂账务的监管。",
                    "2026-08-10", "黄艺文", "已移交，整改中", "17209.40", "食堂财务软件暂时无法正常使用", "较重"},
            {"其他业务管理", "企管科",
                    "考核扣款操作不合规。2026年6月3-1、3-2号凭证收取考核款32,000元，系采矿车间员工栾志腾偷盗金矿石，公司对5名责任人处以考核扣款合计34,800元，截至目前仍有2,800元（采矿车间主任2000元、班组长800元）未收取。存在问题：①收缴不完整，跟踪落实不到位；②要求员工以现金缴纳考核款不合规；③即使确需考核，应依据《工资支付暂行规定》第十六条，从工资中依法扣除，不得要求员工另行缴纳。",
                    "①考核扣款方式事先征询法务相关部门意见，确保合法合规；②考核款在工资核算中依法扣除，严格遵守扣除比例及保底工资限制；③对尚未收取的2,800元，在相关人员工资中扣除；④修订完善内部奖惩制度，确保扣款有据、程序合规。",
                    "移交企管科，财务科监督催收。",
                    "2026-08-10", "王龙梅、柳鉴益", "已整改", "34800",
                    "2026-07-31采矿车间主任刘文广已交2000元，因栾志腾无班组长，相应的800元考核无相应人员则取消。", "严重"},
    };

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean initDemo;
    private final boolean resetDemo;

    public AuditDataInitializer(JdbcTemplate jdbc,
                                @Value("${rich.audit.enabled:true}") boolean enabled,
                                @Value("${rich.audit.init-demo:true}") boolean initDemo,
                                @Value("${rich.audit.reset-demo:true}") boolean resetDemo) {
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
            migrateColumns();
            if (initDemo && countBy("SELECT COUNT(*) FROM audit_project WHERE project_no = ?", PROJECT_NO) == 0) {
                if (resetDemo) {
                    clearAll();
                }
                insertUnits();
                insertProjects();
                insertIssues();
                insertInspectTasks();
                insertFindings();
                refreshProjectStats();
                refreshTaskStats();
                System.out.println("[rich] 审计 / 检查台账数据已写入（" + LEDGER.length + " 条问题）");
            }
            System.out.println("[rich] 审计 / 检查模块表结构就绪");
        } catch (Exception e) {
            System.err.println("[rich] 审计模块初始化失败：" + e.getMessage());
        }
    }

    private long countBy(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    // ------------------------------------------------------------------ DDL

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_unit (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  unit_no VARCHAR(32) DEFAULT NULL COMMENT '单位编号',
                  name VARCHAR(128) NOT NULL COMMENT '单位名称',
                  unit_type VARCHAR(32) DEFAULT NULL COMMENT '单位类型',
                  leader VARCHAR(64) DEFAULT NULL COMMENT '负责人',
                  contact VARCHAR(64) DEFAULT NULL COMMENT '联系人',
                  phone VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
                  status TINYINT DEFAULT 1 COMMENT '状态：1正常 0停用',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_audit_unit_no (unit_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='被审计（检查）单位'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_project (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  project_no VARCHAR(32) DEFAULT NULL COMMENT '项目编号',
                  name VARCHAR(200) NOT NULL COMMENT '项目名称',
                  audit_year INT DEFAULT NULL COMMENT '审计年度',
                  audit_type VARCHAR(64) DEFAULT NULL COMMENT '审计类型',
                  audit_date DATE DEFAULT NULL COMMENT '审计进点日期',
                  end_date DATE DEFAULT NULL COMMENT '审计结束日期',
                  leader VARCHAR(64) DEFAULT NULL COMMENT '审计组长',
                  workgroup VARCHAR(100) DEFAULT NULL COMMENT '审计工作组',
                  unit_id BIGINT DEFAULT NULL COMMENT '被审计单位ID',
                  unit_name VARCHAR(128) DEFAULT NULL COMMENT '被审计单位',
                  issue_count INT DEFAULT 0 COMMENT '问题数',
                  closed_count INT DEFAULT 0 COMMENT '已销号数',
                  overdue_count INT DEFAULT 0 COMMENT '逾期数',
                  amount DECIMAL(18,2) DEFAULT 0 COMMENT '问题金额(元)',
                  status VARCHAR(20) DEFAULT '整改中' COMMENT '状态',
                  archived TINYINT DEFAULT 0 COMMENT '是否归档',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_audit_project_no (project_no),
                  KEY idx_audit_project_year (audit_year)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计项目'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_issue (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  issue_no VARCHAR(32) DEFAULT NULL COMMENT '问题编号',
                  project_id BIGINT DEFAULT NULL COMMENT '审计项目ID',
                  project_name VARCHAR(200) DEFAULT NULL COMMENT '审计项目',
                  unit_id BIGINT DEFAULT NULL COMMENT '被审计单位ID',
                  unit_name VARCHAR(128) DEFAULT NULL COMMENT '被审计单位',
                  issue_type VARCHAR(64) DEFAULT NULL COMMENT '问题类型',
                  nature VARCHAR(64) DEFAULT NULL COMMENT '问题性质',
                  description VARCHAR(2000) DEFAULT NULL COMMENT '问题内容',
                  suggestion VARCHAR(2000) DEFAULT NULL COMMENT '相关建议',
                  rectify_plan VARCHAR(2000) DEFAULT NULL COMMENT '整改方案',
                  amount DECIMAL(18,2) DEFAULT 0 COMMENT '问题金额(元)',
                  rectify_type VARCHAR(32) DEFAULT NULL COMMENT '整改类型',
                  status VARCHAR(20) DEFAULT '未销号' COMMENT '状态：未销号/已销号/逾期',
                  due_date DATE DEFAULT NULL COMMENT '整改期限',
                  deadline_text VARCHAR(64) DEFAULT NULL COMMENT '整改时限原文',
                  progress VARCHAR(64) DEFAULT NULL COMMENT '整改情况',
                  check_period VARCHAR(64) DEFAULT NULL COMMENT '发现期间',
                  closed_date DATE DEFAULT NULL COMMENT '销号日期',
                  owner VARCHAR(64) DEFAULT NULL COMMENT '整改责任人',
                  measure VARCHAR(1000) DEFAULT NULL COMMENT '整改措施',
                  punish_count INT DEFAULT 0 COMMENT '问责人次',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_audit_issue_project (project_id),
                  KEY idx_audit_issue_status (status),
                  KEY idx_audit_issue_due (due_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计问题整改台账'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inspect_task (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  task_no VARCHAR(32) DEFAULT NULL COMMENT '任务编号',
                  name VARCHAR(200) NOT NULL COMMENT '检查名称',
                  inspect_type VARCHAR(32) DEFAULT NULL COMMENT '检查类型',
                  project_id BIGINT DEFAULT NULL COMMENT '关联审计项目ID',
                  project_name VARCHAR(200) DEFAULT NULL COMMENT '关联审计项目',
                  unit_name VARCHAR(128) DEFAULT NULL COMMENT '被检查单位',
                  dept VARCHAR(100) DEFAULT NULL COMMENT '检查部门',
                  inspector VARCHAR(64) DEFAULT NULL COMMENT '检查人',
                  plan_date DATE DEFAULT NULL COMMENT '计划检查日期',
                  actual_date DATE DEFAULT NULL COMMENT '实际检查日期',
                  status VARCHAR(20) DEFAULT '待检查' COMMENT '状态',
                  result VARCHAR(1000) DEFAULT NULL COMMENT '检查结果',
                  finding_count INT DEFAULT 0 COMMENT '发现问题数',
                  rectified_count INT DEFAULT 0 COMMENT '已整改数',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_inspect_task_no (task_no),
                  KEY idx_inspect_task_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查任务'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inspect_finding (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  task_id BIGINT DEFAULT NULL COMMENT '检查任务ID',
                  task_no VARCHAR(32) DEFAULT NULL COMMENT '检查任务编号',
                  project_id BIGINT DEFAULT NULL COMMENT '关联审计项目ID',
                  unit_name VARCHAR(128) DEFAULT NULL COMMENT '被检查单位',
                  category VARCHAR(64) DEFAULT NULL COMMENT '问题类别',
                  title VARCHAR(2000) DEFAULT NULL COMMENT '问题描述',
                  suggestion VARCHAR(2000) DEFAULT NULL COMMENT '相关建议',
                  severity VARCHAR(20) DEFAULT NULL COMMENT '严重程度',
                  amount DECIMAL(18,2) DEFAULT 0 COMMENT '涉及金额(元)',
                  requirement VARCHAR(2000) DEFAULT NULL COMMENT '整改要求',
                  status VARCHAR(20) DEFAULT '未整改' COMMENT '状态',
                  due_date DATE DEFAULT NULL COMMENT '整改期限',
                  owner VARCHAR(64) DEFAULT NULL COMMENT '整改责任人',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_inspect_finding_task (task_id),
                  KEY idx_inspect_finding_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查发现问题'
                """);
    }

    /**
     * 存量库补列。
     *
     * <p>MySQL 5.7 不支持 {@code ADD COLUMN IF NOT EXISTS}，这里先查 information_schema 再决定是否 ALTER。
     */
    private void migrateColumns() {
        addColumnIfMissing("audit_issue", "issue_type", "VARCHAR(64) DEFAULT NULL COMMENT '问题类型'");
        addColumnIfMissing("audit_issue", "suggestion", "VARCHAR(2000) DEFAULT NULL COMMENT '相关建议'");
        addColumnIfMissing("audit_issue", "rectify_plan", "VARCHAR(2000) DEFAULT NULL COMMENT '整改方案'");
        addColumnIfMissing("audit_issue", "deadline_text", "VARCHAR(64) DEFAULT NULL COMMENT '整改时限原文'");
        addColumnIfMissing("audit_issue", "progress", "VARCHAR(64) DEFAULT NULL COMMENT '整改情况'");
        addColumnIfMissing("audit_issue", "check_period", "VARCHAR(64) DEFAULT NULL COMMENT '发现期间'");
        addColumnIfMissing("inspect_finding", "suggestion", "VARCHAR(2000) DEFAULT NULL COMMENT '相关建议'");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /** 清空模块数据，便于用真实台账替换此前的演示数据。 */
    private void clearAll() {
        jdbc.execute("DELETE FROM audit_issue");
        jdbc.execute("DELETE FROM inspect_finding");
        jdbc.execute("DELETE FROM inspect_task");
        jdbc.execute("DELETE FROM audit_project");
        jdbc.execute("DELETE FROM audit_unit");
    }

    // ------------------------------------------------------------------ 台账数据

    private void insertUnits() {
        Object[][] rows = {
                {"U01", "金宝矿业", "被检查单位", null, null, null},
                {"U02", "财务科", "职能部门", null, null, null},
                {"U03", "生产技术科", "职能部门", null, null, null},
                {"U04", "物流设备科", "职能部门", null, null, null},
                {"U05", "综合办公室", "职能部门", null, null, null},
                {"U06", "企管科", "职能部门", null, null, null},
                {"U07", "采矿车间", "生产单位", null, null, null},
                {"U08", "食堂", "后勤单位", null, null, null},
        };
        for (Object[] r : rows) {
            jdbc.update("INSERT INTO audit_unit (unit_no, name, unit_type, leader, contact, phone, status)"
                    + " VALUES (?,?,?,?,?,?,1)", r);
        }
    }

    private void insertProjects() {
        jdbc.update("INSERT INTO audit_project (project_no, name, audit_year, audit_type, audit_date,"
                        + " end_date, leader, workgroup, unit_id, unit_name, status, archived, remark)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                PROJECT_NO, "2026年二季度招金有色财务检查", 2026, "财务检查",
                LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 31),
                "招金有色财务检查组", "招金有色财务检查组",
                queryUnitId("金宝矿业"), "金宝矿业", "整改中", 0, PERIOD + "财务检查整改台账");
    }

    /** 按原始台账生成审计问题（整改台账）。 */
    private void insertIssues() {
        Long projectId = queryProjectId(PROJECT_NO);
        String projectName = queryProjectName(PROJECT_NO);
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < LEDGER.length; i++) {
            String[] r = LEDGER[i];
            LocalDate due = parseDate(r[C_DEADLINE]);
            String progress = r[C_PROGRESS];
            // 台账已写明「已整改」的即视为已销号，销号日期取整改时限前两天
            boolean closed = "已整改".equals(progress);
            String status = closed ? "已销号" : "未销号";
            LocalDate closedDate = closed && due != null ? due.minusDays(2) : null;
            String rectifyType = due == null ? "持续整改" : "限期整改";
            batch.add(new Object[]{
                    String.format("%03d", i + 1), projectId, projectName,
                    queryUnitId(r[C_UNIT]), r[C_UNIT],
                    r[C_TYPE], r[C_TYPE], r[C_DESC], r[C_SUGGEST], r[C_PLAN],
                    new BigDecimal(r[C_AMOUNT]), rectifyType, status, due, r[C_DEADLINE],
                    progress, PERIOD, closedDate, r[C_OWNER], r[C_PLAN], 0,
                    blankToNull(r[C_REMARK])
            });
        }
        for (Object[] b : batch) {
            jdbc.update("INSERT INTO audit_issue (issue_no, project_id, project_name, unit_id, unit_name,"
                            + " issue_type, nature, description, suggestion, rectify_plan, amount, rectify_type,"
                            + " status, due_date, deadline_text, progress, check_period, closed_date, owner,"
                            + " measure, punish_count, remark)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", b);
        }
    }

    private void insertInspectTasks() {
        long closed = 0;
        for (String[] r : LEDGER) {
            if ("已整改".equals(r[C_PROGRESS])) {
                closed++;
            }
        }
        jdbc.update("INSERT INTO inspect_task (task_no, name, inspect_type, project_id, project_name,"
                        + " unit_name, dept, inspector, plan_date, actual_date, status, result)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                TASK_NO, "2026年二季度招金有色财务检查", "专项检查",
                queryProjectId(PROJECT_NO), queryProjectName(PROJECT_NO), "金宝矿业",
                "招金有色", "招金有色财务检查组",
                LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 29), "已完成",
                "共发现 " + LEDGER.length + " 项问题（税务及专利管理 3 项、财务管理 6 项、采购管理 1 项、"
                        + "食堂管理 1 项、其他业务管理 1 项），已整改 " + closed + " 项，其余持续整改或移交整改中");
    }

    /** 同一份台账同步写入检查发现问题，保证审计与检查两个视角口径一致。 */
    private void insertFindings() {
        Long taskId = queryTaskId(TASK_NO);
        Long projectId = queryProjectId(PROJECT_NO);
        for (String[] r : LEDGER) {
            LocalDate due = parseDate(r[C_DEADLINE]);
            String status = "已整改".equals(r[C_PROGRESS]) ? "已整改"
                    : ("持续整改".equals(r[C_PROGRESS]) ? "整改中" : "整改中");
            jdbc.update("INSERT INTO inspect_finding (task_id, task_no, project_id, unit_name, category,"
                            + " title, suggestion, severity, amount, requirement, status, due_date, owner, remark)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    taskId, TASK_NO, projectId, r[C_UNIT], r[C_TYPE], r[C_DESC], blankToNull(r[C_SUGGEST]),
                    r[C_SEVERITY], new BigDecimal(r[C_AMOUNT]), r[C_PLAN], status, due, r[C_OWNER],
                    blankToNull(r[C_REMARK]));
        }
    }

    // ------------------------------------------------------------------ 统计回填

    /** 用问题明细回填项目的问题数 / 已销号 / 逾期 / 金额。 */
    private void refreshProjectStats() {
        jdbc.update("UPDATE audit_project p SET "
                + "issue_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id),"
                + "closed_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id AND i.status = '已销号'),"
                + "overdue_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id AND i.status = '逾期'),"
                + "amount = IFNULL((SELECT SUM(i.amount) FROM audit_issue i WHERE i.project_id = p.id), 0)");
        jdbc.update("UPDATE audit_project SET status = '已完成' WHERE issue_count > 0 AND closed_count = issue_count");
        jdbc.update("UPDATE audit_project SET status = '整改中' WHERE issue_count > 0 AND closed_count < issue_count");
    }

    /** 用发现明细回填检查任务的发现问题数 / 已整改数。 */
    private void refreshTaskStats() {
        jdbc.update("UPDATE inspect_task t SET "
                + "finding_count = (SELECT COUNT(*) FROM inspect_finding f WHERE f.task_id = t.id),"
                + "rectified_count = (SELECT COUNT(*) FROM inspect_finding f WHERE f.task_id = t.id AND f.status = '已整改')");
    }

    // ------------------------------------------------------------------ 小工具

    /** 台账里的整改时限：具体日期或「持续整改」这类长期表述（后者返回 null）。 */
    private static LocalDate parseDate(String text) {
        if (text == null || text.length() != 10) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private Long queryUnitId(String name) {
        return queryLong("SELECT id FROM audit_unit WHERE name = ?", name);
    }

    private Long queryProjectId(String projectNo) {
        return queryLong("SELECT id FROM audit_project WHERE project_no = ?", projectNo);
    }

    private String queryProjectName(String projectNo) {
        try {
            return jdbc.queryForObject("SELECT name FROM audit_project WHERE project_no = ?", String.class, projectNo);
        } catch (Exception e) {
            return null;
        }
    }

    private Long queryTaskId(String taskNo) {
        return queryLong("SELECT id FROM inspect_task WHERE task_no = ?", taskNo);
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }
}
