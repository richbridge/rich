package fan.rich.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 培训模块初始化器（课程课件 / 培训计划 / 参训记录 / 考试 / 成绩）。
 *
 * <p>应用启动后自动建表（{@code CREATE TABLE IF NOT EXISTS}），并在表为空时写入一批
 * 贴合金宝矿业实际科室与人员的演示数据，可在页面上自由增删改。
 * 已存在数据（以课程编号 {@code KC2026-001} 为标记）时不再重复写入。
 *
 * <p>{@code rich.train.enabled=false} 可整体关闭。
 */
@Order(4)
@Component
public class TrainDataInitializer implements ApplicationRunner {

    /** 演示数据的标记编号。 */
    private static final String MARK_COURSE = "KC2026-001";

    // 课程：编号 / 名称 / 分类 / 类型 / 讲师 / 讲师单位 / 时长(分) / 学分 / 难度 / 适用对象 / 状态 / 是否考试
    private static final int C_NO = 0;
    private static final int C_NAME = 1;
    private static final int C_CATE = 2;
    private static final int C_TYPE = 3;
    private static final int C_TEACHER = 4;
    private static final int C_ORG = 5;
    private static final int C_DUR = 6;
    private static final int C_CREDIT = 7;
    private static final int C_LEVEL = 8;
    private static final int C_TARGET = 9;
    private static final int C_STATUS = 10;
    private static final int C_EXAM = 11;

    private static final String[][] COURSES = {
            {"KC2026-001", "新员工入职培训（安全生产与企业文化）", "入职培训", "面授", "王殿华", "综合办公室",
                    "480", "4", "初级", "新入职员工", "已发布", "1"},
            {"KC2026-002", "井下作业安全操作规程", "安全环保", "面授", "刘文广", "采矿车间",
                    "240", "3", "中级", "采矿车间作业人员", "已发布", "1"},
            {"KC2026-003", "安全生产法律法规与典型事故警示", "安全环保", "点播", "黄艺文", "综合办公室",
                    "180", "2", "初级", "全体职工", "已发布", "0"},
            {"KC2026-004", "财务核算规范与税务风险防控", "财务税务", "面授", "柳鉴益", "财务科",
                    "300", "3", "中级", "财务、企管、采购人员", "已发布", "1"},
            {"KC2026-005", "金属价格分析与点价实务", "财务税务", "直播", "王龙梅", "企管科",
                    "120", "2", "高级", "销售、财务、企管人员", "已发布", "0"},
            {"KC2026-006", "选矿工艺流程与质量控制", "生产技术", "点播", "陈立国", "生产技术科",
                    "240", "3", "中级", "选矿、质检人员", "已发布", "1"},
            {"KC2026-007", "设备维护保养与故障排查", "设备管理", "面授", "郝勇凯", "物流设备科",
                    "180", "2", "中级", "设备维修人员", "已发布", "1"},
            {"KC2026-008", "环境保护与尾矿库安全管理", "安全环保", "混合", "周晓东", "生产技术科",
                    "150", "2", "中级", "生产、安全管理人员", "已发布", "0"},
    };

    // 考试：编号 / 名称 / 课程下标 / 类型 / 总分 / 及格分 / 时长(分) / 题数 / 考试日期 / 状态
    private static final int E_NO = 0;
    private static final int E_NAME = 1;
    private static final int E_COURSE = 2;
    private static final int E_TYPE = 3;
    private static final int E_TOTAL = 4;
    private static final int E_PASS = 5;
    private static final int E_DUR = 6;
    private static final int E_QCOUNT = 7;
    private static final int E_DATE = 8;
    private static final int E_STATUS = 9;

    private static final String[][] EXAMS = {
            {"KS2026-001", "新员工入职安全考试", "0", "线上考试", "100", "60", "60", "50",
                    "2026-03-20", "已结束"},
            {"KS2026-002", "井下作业安全规程考试", "1", "线下笔试", "100", "80", "90", "60",
                    "2026-05-15", "已结束"},
            {"KS2026-003", "财务核算规范测试", "3", "线上考试", "100", "60", "45", "40",
                    "2026-08-12", "已结束"},
            {"KS2026-004", "选矿工艺知识测评", "5", "线上考试", "100", "60", "40", "35",
                    "2026-09-10", "已结束"},
            {"KS2026-005", "设备维护技能实操考核", "6", "实操考核", "100", "70", "60", "20",
                    "2026-10-21", "报名中"},
    };

    // 培训计划：编号 / 名称 / 类型 / 课程下标 / 开始 / 结束 / 地点 / 讲师 / 主办部门 / 参训对象 / 计划人数 / 学时 / 学分 / 考试下标(-1 无) / 状态
    private static final int P_NO = 0;
    private static final int P_NAME = 1;
    private static final int P_TYPE = 2;
    private static final int P_COURSE = 3;
    private static final int P_START = 4;
    private static final int P_END = 5;
    private static final int P_ADDR = 6;
    private static final int P_TEACHER = 7;
    private static final int P_DEPT = 8;
    private static final int P_TARGET = 9;
    private static final int P_COUNT = 10;
    private static final int P_HOURS = 11;
    private static final int P_CREDIT = 12;
    private static final int P_EXAM = 13;
    private static final int P_STATUS = 14;

    private static final String[][] PLANS = {
            {"PX2026-001", "2026年新员工入职培训（第一期）", "入职培训", "0",
                    "2026-03-18", "2026-03-20", "办公楼三楼会议室", "王殿华", "综合办公室",
                    "2026年新入职员工", "12", "16", "4", "0", "已结束"},
            {"PX2026-002", "井下作业安全规程专题培训", "安全环保", "1",
                    "2026-05-13", "2026-05-15", "采矿车间会议室", "刘文广", "采矿车间",
                    "采矿车间全体作业人员", "45", "12", "3", "1", "已结束"},
            {"PX2026-003", "财务核算规范与税务风险培训", "专项培训", "3",
                    "2026-08-10", "2026-08-12", "财务科会议室", "柳鉴益", "财务科",
                    "财务科、企管科相关人员", "18", "10", "3", "2", "已结束"},
            {"PX2026-004", "金属价格分析与点价实务培训", "专项培训", "4",
                    "2026-09-05", "2026-09-05", "线上直播", "王龙梅", "企管科",
                    "企管科、财务科、销售人员", "25", "4", "2", "-1", "已结束"},
            {"PX2026-005", "选矿工艺流程与质量控制培训", "岗位技能", "5",
                    "2026-09-08", "2026-09-10", "生产技术科培训室", "陈立国", "生产技术科",
                    "选矿车间、质检人员", "30", "8", "3", "3", "已结束"},
            {"PX2026-006", "设备维护保养与故障排查培训", "岗位技能", "6",
                    "2026-10-19", "2026-10-21", "物流设备科培训室", "郝勇凯", "物流设备科",
                    "设备维修班组", "20", "8", "2", "4", "报名中"},
            {"PX2026-007", "环境保护与尾矿库安全管理培训", "安全环保", "7",
                    "2026-10-13", "2026-10-15", "办公楼三楼会议室", "周晓东", "生产技术科",
                    "生产及安全管理人员", "60", "6", "2", "-1", "进行中"},
    };

    /** 学员花名册：工号 / 姓名 / 部门 / 岗位。 */
    private static final String[][] STUDENTS = {
            {"JG0001", "柳鉴益", "财务科", "科长"},
            {"JG0002", "李雲钊", "财务科", "会计"},
            {"JG0003", "任宁", "财务科", "会计"},
            {"JG0004", "王晓欣", "财务科", "出纳"},
            {"JG0005", "王殿华", "财务科", "副科长"},
            {"JG0006", "郝勇凯", "物流设备科", "设备管理员"},
            {"JG0007", "张元征", "物流设备科", "采购专员"},
            {"JG0008", "刘文广", "采矿车间", "车间主任"},
            {"JG0009", "王旭波", "采矿车间", "班组长"},
            {"JG0010", "臧洪虎", "采矿车间", "采矿工"},
            {"JG0011", "黄艺文", "综合办公室", "副主任"},
            {"JG0012", "王文源", "综合办公室", "行政专员"},
            {"JG0013", "王龙梅", "企管科", "科长"},
            {"JG0014", "孙志强", "生产技术科", "技术员"},
            {"JG0015", "陈立国", "生产技术科", "选矿工程师"},
            {"JG0016", "赵秀兰", "食堂", "管理员"},
            {"JG0017", "周晓东", "生产技术科", "安全员"},
            {"JG0018", "郑海燕", "采矿车间", "安全监督员"},
            {"JG0019", "陈明", "采矿车间", "采矿工"},
            {"JG0020", "李佳", "财务科", "会计"},
            {"JG0021", "吴强", "物流设备科", "维修工"},
            {"JG0022", "孙丽", "综合办公室", "行政专员"},
    };

    /**
     * 参训记录：计划编号 / 工号 / 签到状态 / 成绩（空串表示未考试）/ 学习状态。
     */
    private static final String[][] ENROLLS = {
            // 第一期新员工入职培训（及格 60）
            {"PX2026-001", "JG0004", "已签到", "92", "已完成"},
            {"PX2026-001", "JG0012", "已签到", "85", "已完成"},
            {"PX2026-001", "JG0014", "已签到", "78", "已完成"},
            {"PX2026-001", "JG0010", "已签到", "66", "已完成"},
            {"PX2026-001", "JG0009", "已签到", "58", "不合格"},
            {"PX2026-001", "JG0016", "已签到", "88", "已完成"},
            {"PX2026-001", "JG0019", "已签到", "71", "已完成"},
            {"PX2026-001", "JG0020", "已签到", "90", "已完成"},
            {"PX2026-001", "JG0021", "已签到", "64", "已完成"},
            {"PX2026-001", "JG0022", "已签到", "83", "已完成"},
            {"PX2026-001", "JG0011", "已签到", "76", "已完成"},
            {"PX2026-001", "JG0007", "已签到", "95", "已完成"},
            // 井下作业安全规程专题培训（及格 80）
            {"PX2026-002", "JG0008", "已签到", "96", "已完成"},
            {"PX2026-002", "JG0018", "已签到", "93", "已完成"},
            {"PX2026-002", "JG0009", "已签到", "88", "已完成"},
            {"PX2026-002", "JG0010", "已签到", "82", "已完成"},
            {"PX2026-002", "JG0019", "已签到", "79", "不合格"},
            {"PX2026-002", "JG0017", "已签到", "91", "已完成"},
            {"PX2026-002", "JG0015", "已签到", "84", "已完成"},
            {"PX2026-002", "JG0014", "已签到", "86", "已完成"},
            {"PX2026-002", "JG0011", "已签到", "90", "已完成"},
            {"PX2026-002", "JG0012", "已签到", "81", "已完成"},
            {"PX2026-002", "JG0021", "已签到", "77", "不合格"},
            {"PX2026-002", "JG0006", "已签到", "89", "已完成"},
            {"PX2026-002", "JG0007", "已签到", "85", "已完成"},
            {"PX2026-002", "JG0022", "已签到", "83", "已完成"},
            {"PX2026-002", "JG0016", "已签到", "76", "不合格"},
            // 财务核算规范与税务风险培训（及格 60）
            {"PX2026-003", "JG0001", "已签到", "95", "已完成"},
            {"PX2026-003", "JG0002", "已签到", "88", "已完成"},
            {"PX2026-003", "JG0003", "已签到", "92", "已完成"},
            {"PX2026-003", "JG0004", "已签到", "84", "已完成"},
            {"PX2026-003", "JG0005", "已签到", "90", "已完成"},
            {"PX2026-003", "JG0020", "已签到", "73", "已完成"},
            {"PX2026-003", "JG0013", "已签到", "86", "已完成"},
            {"PX2026-003", "JG0011", "已签到", "79", "已完成"},
            {"PX2026-003", "JG0012", "已签到", "68", "已完成"},
            {"PX2026-003", "JG0016", "已签到", "71", "已完成"},
            // 金属价格分析与点价实务（不设考试）
            {"PX2026-004", "JG0001", "已签到", "", "已完成"},
            {"PX2026-004", "JG0002", "已签到", "", "已完成"},
            {"PX2026-004", "JG0005", "已签到", "", "已完成"},
            {"PX2026-004", "JG0013", "已签到", "", "已完成"},
            {"PX2026-004", "JG0004", "已签到", "", "已完成"},
            {"PX2026-004", "JG0003", "已签到", "", "已完成"},
            {"PX2026-004", "JG0020", "已签到", "", "已完成"},
            {"PX2026-004", "JG0011", "已签到", "", "已完成"},
            // 选矿工艺流程与质量控制（及格 60）
            {"PX2026-005", "JG0014", "已签到", "88", "已完成"},
            {"PX2026-005", "JG0015", "已签到", "94", "已完成"},
            {"PX2026-005", "JG0017", "已签到", "82", "已完成"},
            {"PX2026-005", "JG0018", "已签到", "76", "已完成"},
            {"PX2026-005", "JG0008", "已签到", "71", "已完成"},
            {"PX2026-005", "JG0009", "已签到", "68", "已完成"},
            {"PX2026-005", "JG0010", "已签到", "65", "已完成"},
            {"PX2026-005", "JG0019", "已签到", "59", "不合格"},
            {"PX2026-005", "JG0012", "已签到", "80", "已完成"},
            {"PX2026-005", "JG0022", "已签到", "73", "已完成"},
            {"PX2026-005", "JG0016", "已签到", "66", "已完成"},
            {"PX2026-005", "JG0021", "已签到", "70", "已完成"},
            // 设备维护保养（报名中，尚未开课）
            {"PX2026-006", "JG0006", "未签到", "", "未开始"},
            {"PX2026-006", "JG0007", "未签到", "", "未开始"},
            {"PX2026-006", "JG0021", "未签到", "", "未开始"},
            {"PX2026-006", "JG0018", "未签到", "", "未开始"},
            {"PX2026-006", "JG0009", "未签到", "", "未开始"},
            {"PX2026-006", "JG0017", "未签到", "", "未开始"},
            // 环境保护与尾矿库安全（进行中）
            {"PX2026-007", "JG0008", "已签到", "", "学习中"},
            {"PX2026-007", "JG0018", "已签到", "", "学习中"},
            {"PX2026-007", "JG0017", "已签到", "", "学习中"},
            {"PX2026-007", "JG0015", "已签到", "", "学习中"},
            {"PX2026-007", "JG0014", "已签到", "", "学习中"},
            {"PX2026-007", "JG0011", "已签到", "", "学习中"},
            {"PX2026-007", "JG0013", "已签到", "", "学习中"},
            {"PX2026-007", "JG0006", "已签到", "", "学习中"},
            {"PX2026-007", "JG0010", "已签到", "", "学习中"},
            {"PX2026-007", "JG0019", "请假", "", "未开始"},
    };

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean initDemo;
    private final boolean resetDemo;

    public TrainDataInitializer(JdbcTemplate jdbc,
                                @Value("${rich.train.enabled:true}") boolean enabled,
                                @Value("${rich.train.init-demo:true}") boolean initDemo,
                                @Value("${rich.train.reset-demo:true}") boolean resetDemo) {
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
            if (initDemo && countBy("SELECT COUNT(*) FROM train_course WHERE course_no = ?", MARK_COURSE) == 0) {
                if (resetDemo) {
                    clearAll();
                }
                insertCourses();
                insertExams();
                insertPlans();
                insertEnrolls();
                insertScores();
                refreshAll();
                System.out.println("[rich] 培训模块演示数据已写入（" + COURSES.length + " 门课程 / "
                        + PLANS.length + " 期培训 / " + ENROLLS.length + " 条参训记录）");
            }
            System.out.println("[rich] 培训模块表结构就绪");
        } catch (Exception e) {
            System.err.println("[rich] 培训模块初始化失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ DDL

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_course (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  course_no VARCHAR(32) DEFAULT NULL COMMENT '课程编号',
                  name VARCHAR(200) NOT NULL COMMENT '课程名称',
                  category VARCHAR(64) DEFAULT NULL COMMENT '课程分类',
                  course_type VARCHAR(32) DEFAULT NULL COMMENT '课程类型：点播/直播/面授/混合',
                  teacher VARCHAR(64) DEFAULT NULL COMMENT '讲师',
                  teacher_org VARCHAR(100) DEFAULT NULL COMMENT '讲师单位',
                  duration INT DEFAULT 0 COMMENT '课程时长(分钟)',
                  credit DECIMAL(6,1) DEFAULT 0 COMMENT '学分',
                  level VARCHAR(20) DEFAULT NULL COMMENT '难度：初级/中级/高级',
                  cover VARCHAR(255) DEFAULT NULL COMMENT '封面图地址',
                  url VARCHAR(255) DEFAULT NULL COMMENT '课件/播放地址',
                  intro VARCHAR(1000) DEFAULT NULL COMMENT '课程简介',
                  target VARCHAR(255) DEFAULT NULL COMMENT '适用对象',
                  status VARCHAR(20) DEFAULT '草稿' COMMENT '状态：草稿/已发布/已下架',
                  learn_count INT DEFAULT 0 COMMENT '学习人次',
                  exam_flag TINYINT DEFAULT 0 COMMENT '是否需要考试：1是 0否',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_train_course_no (course_no),
                  KEY idx_train_course_cate (category)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训课程(课件)'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_exam (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  exam_no VARCHAR(32) DEFAULT NULL COMMENT '试卷编号',
                  name VARCHAR(200) NOT NULL COMMENT '考试名称',
                  course_id BIGINT DEFAULT NULL COMMENT '关联课程ID',
                  course_name VARCHAR(200) DEFAULT NULL COMMENT '关联课程',
                  plan_id BIGINT DEFAULT NULL COMMENT '关联培训计划ID',
                  plan_name VARCHAR(200) DEFAULT NULL COMMENT '关联培训计划',
                  exam_type VARCHAR(32) DEFAULT NULL COMMENT '考试类型',
                  total_score INT DEFAULT 100 COMMENT '总分',
                  pass_score INT DEFAULT 60 COMMENT '及格分',
                  duration INT DEFAULT 60 COMMENT '考试时长(分钟)',
                  question_count INT DEFAULT 0 COMMENT '题目数',
                  exam_date DATE DEFAULT NULL COMMENT '考试日期',
                  status VARCHAR(20) DEFAULT '未发布' COMMENT '状态',
                  examinee_count INT DEFAULT 0 COMMENT '应考人数',
                  pass_count INT DEFAULT 0 COMMENT '合格人数',
                  avg_score DECIMAL(6,1) DEFAULT 0 COMMENT '平均分',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_train_exam_no (exam_no),
                  KEY idx_train_exam_course (course_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训考试(试卷)'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_plan (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  plan_no VARCHAR(32) DEFAULT NULL COMMENT '计划编号',
                  name VARCHAR(200) NOT NULL COMMENT '培训名称',
                  plan_year INT DEFAULT NULL COMMENT '培训年度',
                  plan_type VARCHAR(32) DEFAULT NULL COMMENT '培训类型',
                  course_id BIGINT DEFAULT NULL COMMENT '关联课程ID',
                  course_name VARCHAR(200) DEFAULT NULL COMMENT '关联课程',
                  start_date DATE DEFAULT NULL COMMENT '开始日期',
                  end_date DATE DEFAULT NULL COMMENT '结束日期',
                  address VARCHAR(200) DEFAULT NULL COMMENT '培训地点',
                  teacher VARCHAR(64) DEFAULT NULL COMMENT '讲师',
                  dept VARCHAR(100) DEFAULT NULL COMMENT '主办部门',
                  target_dept VARCHAR(200) DEFAULT NULL COMMENT '参训对象',
                  plan_count INT DEFAULT 0 COMMENT '计划参训人数',
                  enroll_count INT DEFAULT 0 COMMENT '参训人数',
                  pass_count INT DEFAULT 0 COMMENT '合格人数',
                  hours INT DEFAULT 0 COMMENT '学时',
                  credit DECIMAL(6,1) DEFAULT 0 COMMENT '学分',
                  exam_id BIGINT DEFAULT NULL COMMENT '关联考试ID',
                  exam_name VARCHAR(200) DEFAULT NULL COMMENT '关联考试',
                  status VARCHAR(20) DEFAULT '报名中' COMMENT '状态',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_train_plan_no (plan_no),
                  KEY idx_train_plan_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训计划(培训班)'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_enroll (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  plan_id BIGINT DEFAULT NULL COMMENT '培训计划ID',
                  plan_no VARCHAR(32) DEFAULT NULL COMMENT '计划编号',
                  plan_name VARCHAR(200) DEFAULT NULL COMMENT '培训名称',
                  student_no VARCHAR(32) DEFAULT NULL COMMENT '工号',
                  student_name VARCHAR(64) DEFAULT NULL COMMENT '学员姓名',
                  dept VARCHAR(100) DEFAULT NULL COMMENT '所在部门',
                  post VARCHAR(64) DEFAULT NULL COMMENT '岗位',
                  mobile VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
                  sign_status VARCHAR(20) DEFAULT '未签到' COMMENT '签到状态',
                  hours INT DEFAULT 0 COMMENT '获得学时',
                  credit DECIMAL(6,1) DEFAULT 0 COMMENT '获得学分',
                  score DECIMAL(6,1) DEFAULT NULL COMMENT '考试成绩',
                  pass TINYINT DEFAULT 0 COMMENT '是否合格：1是 0否',
                  cert_no VARCHAR(64) DEFAULT NULL COMMENT '证书编号',
                  finish_date DATE DEFAULT NULL COMMENT '完成日期',
                  status VARCHAR(20) DEFAULT '未开始' COMMENT '学习状态',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_train_enroll_plan (plan_id),
                  KEY idx_train_enroll_student (student_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训参训记录'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_score (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  exam_id BIGINT DEFAULT NULL COMMENT '考试ID',
                  exam_no VARCHAR(32) DEFAULT NULL COMMENT '试卷编号',
                  exam_name VARCHAR(200) DEFAULT NULL COMMENT '考试名称',
                  student_no VARCHAR(32) DEFAULT NULL COMMENT '工号',
                  student_name VARCHAR(64) DEFAULT NULL COMMENT '学员姓名',
                  dept VARCHAR(100) DEFAULT NULL COMMENT '所在部门',
                  score DECIMAL(6,1) DEFAULT NULL COMMENT '考试得分',
                  pass TINYINT DEFAULT 0 COMMENT '是否合格：1是 0否',
                  exam_date DATE DEFAULT NULL COMMENT '考试日期',
                  used_minutes INT DEFAULT 0 COMMENT '用时(分钟)',
                  times INT DEFAULT 1 COMMENT '第几次参加',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_train_score_exam (exam_id),
                  KEY idx_train_score_student (student_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试成绩明细'
                """);
    }

    /** 清空培训模块数据。 */
    private void clearAll() {
        jdbc.execute("DELETE FROM train_score");
        jdbc.execute("DELETE FROM train_enroll");
        jdbc.execute("DELETE FROM train_plan");
        jdbc.execute("DELETE FROM train_exam");
        jdbc.execute("DELETE FROM train_course");
    }

    // ------------------------------------------------------------------ 演示数据

    private void insertCourses() {
        for (String[] c : COURSES) {
            jdbc.update("INSERT INTO train_course (course_no, name, category, course_type, teacher,"
                            + " teacher_org, duration, credit, level, target, status, exam_flag, learn_count)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    c[C_NO], c[C_NAME], c[C_CATE], c[C_TYPE], c[C_TEACHER], c[C_ORG],
                    Integer.parseInt(c[C_DUR]), new BigDecimal(c[C_CREDIT]), c[C_LEVEL],
                    c[C_TARGET], c[C_STATUS], Integer.parseInt(c[C_EXAM]));
        }
    }

    private void insertExams() {
        for (String[] e : EXAMS) {
            String courseNo = COURSES[Integer.parseInt(e[E_COURSE])][C_NO];
            jdbc.update("INSERT INTO train_exam (exam_no, name, course_id, course_name, exam_type,"
                            + " total_score, pass_score, duration, question_count, exam_date, status)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    e[E_NO], e[E_NAME], queryCourseId(courseNo), courseName(courseNo), e[E_TYPE],
                    Integer.parseInt(e[E_TOTAL]), Integer.parseInt(e[E_PASS]),
                    Integer.parseInt(e[E_DUR]), Integer.parseInt(e[E_QCOUNT]),
                    LocalDate.parse(e[E_DATE]), e[E_STATUS]);
        }
    }

    private void insertPlans() {
        for (String[] p : PLANS) {
            String courseNo = COURSES[Integer.parseInt(p[P_COURSE])][C_NO];
            int examIdx = Integer.parseInt(p[P_EXAM]);
            String examNo = examIdx >= 0 ? EXAMS[examIdx][E_NO] : null;
            LocalDate start = LocalDate.parse(p[P_START]);
            jdbc.update("INSERT INTO train_plan (plan_no, name, plan_year, plan_type, course_id, course_name,"
                            + " start_date, end_date, address, teacher, dept, target_dept, plan_count,"
                            + " hours, credit, exam_id, exam_name, status)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    p[P_NO], p[P_NAME], start.getYear(), p[P_TYPE], queryCourseId(courseNo), courseName(courseNo),
                    start, LocalDate.parse(p[P_END]), p[P_ADDR], p[P_TEACHER], p[P_DEPT], p[P_TARGET],
                    Integer.parseInt(p[P_COUNT]), Integer.parseInt(p[P_HOURS]),
                    new BigDecimal(p[P_CREDIT]), examNo == null ? null : queryExamId(examNo),
                    examNo == null ? null : examName(examNo), p[P_STATUS]);
        }
    }

    private void insertEnrolls() {
        Map<String, String[]> students = new HashMap<>();
        for (String[] s : STUDENTS) {
            students.put(s[0], s);
        }
        int seq = 0;
        for (String[] r : ENROLLS) {
            String[] s = students.get(r[1]);
            if (s == null) {
                continue;
            }
            seq++;
            String planNo = r[0];
            Long planId = queryPlanId(planNo);
            String planName = queryPlanName(planNo);
            boolean finished = "已完成".equals(r[4]);
            // 学时学分只在完成培训后计入
            Integer hours = finished ? queryPlanHours(planId) : 0;
            BigDecimal credit = finished ? queryPlanCredit(planId) : BigDecimal.ZERO;
            BigDecimal score = r[3].isEmpty() ? null : new BigDecimal(r[3]);
            Integer passScore = queryExamPassScore(planId);
            int pass;
            if (score != null && passScore != null) {
                pass = score.compareTo(BigDecimal.valueOf(passScore)) >= 0 ? 1 : 0;
            } else {
                // 未设考试的培训，完成学习即视为合格
                pass = finished ? 1 : 0;
            }
            LocalDate finish = finished ? queryPlanEndDate(planId) : null;
            String certNo = pass == 1 ? "ZS" + planNo.substring(planNo.length() - 3) + "-"
                    + String.format("%03d", seq) : null;
            jdbc.update("INSERT INTO train_enroll (plan_id, plan_no, plan_name, student_no, student_name,"
                            + " dept, post, sign_status, hours, credit, score, pass, cert_no, finish_date, status)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    planId, planNo, planName, s[0], s[1], s[2], s[3], r[2],
                    hours, credit, score, pass, certNo, finish, r[4]);
        }
        // 关联考试回填到培训计划的 exam_name 上已在插入时完成，这里补计划名
        jdbc.update("UPDATE train_exam e JOIN train_plan p ON p.exam_id = e.id "
                + "SET e.plan_id = p.id, e.plan_name = p.name");
    }

    /** 由参训记录派生考试成绩，保证「培训—考试」两侧口径一致。 */
    private void insertScores() {
        Map<String, String[]> students = new HashMap<>();
        for (String[] s : STUDENTS) {
            students.put(s[0], s);
        }
        for (String[] r : ENROLLS) {
            if (r[3].isEmpty()) {
                continue;
            }
            String[] s = students.get(r[1]);
            Long planId = queryPlanId(r[0]);
            Long examId = queryExamIdOfPlan(planId);
            if (s == null || examId == null) {
                continue;
            }
            BigDecimal score = new BigDecimal(r[3]);
            Integer passScore = queryExamPassScore(planId);
            int pass = passScore != null && score.compareTo(BigDecimal.valueOf(passScore)) >= 0 ? 1 : 0;
            jdbc.update("INSERT INTO train_score (exam_id, exam_no, exam_name, student_no, student_name,"
                            + " dept, score, pass, exam_date, used_minutes, times)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,1)",
                    examId, queryExamNo(examId), queryExamName(examId), s[0], s[1], s[2],
                    score, pass, queryPlanEndDate(planId), queryExamDuration(examId) - 10);
        }
    }

    /** 回填课程学习人次、培训参训/合格人数、考试应考/合格/平均分。 */
    private void refreshAll() {
        jdbc.update("UPDATE train_plan p SET "
                + "enroll_count = (SELECT COUNT(*) FROM train_enroll e WHERE e.plan_id = p.id),"
                + "pass_count = (SELECT COUNT(*) FROM train_enroll e WHERE e.plan_id = p.id AND e.pass = 1)");
        jdbc.update("UPDATE train_course c SET "
                + "learn_count = (SELECT COUNT(*) FROM train_plan p "
                + "  JOIN train_enroll e ON e.plan_id = p.id WHERE p.course_id = c.id)");
        jdbc.update("UPDATE train_exam e SET "
                + "examinee_count = (SELECT COUNT(*) FROM train_score s WHERE s.exam_id = e.id),"
                + "pass_count = (SELECT COUNT(*) FROM train_score s WHERE s.exam_id = e.id AND s.pass = 1),"
                + "avg_score = IFNULL((SELECT ROUND(AVG(s.score), 1) FROM train_score s WHERE s.exam_id = e.id), 0)");
    }

    // ------------------------------------------------------------------ 查询小工具

    private long countBy(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private Long queryCourseId(String courseNo) {
        return queryLong("SELECT id FROM train_course WHERE course_no = ?", courseNo);
    }

    private String courseName(String courseNo) {
        return queryString("SELECT name FROM train_course WHERE course_no = ?", courseNo);
    }

    private Long queryExamId(String examNo) {
        return queryLong("SELECT id FROM train_exam WHERE exam_no = ?", examNo);
    }

    private Long queryExamIdOfPlan(Long planId) {
        return queryLong("SELECT exam_id FROM train_plan WHERE id = ?", planId);
    }

    private String queryExamNo(Long examId) {
        return queryString("SELECT exam_no FROM train_exam WHERE id = ?", examId);
    }

    private String queryExamName(Long examId) {
        return queryString("SELECT name FROM train_exam WHERE id = ?", examId);
    }

    private Integer queryExamDuration(Long examId) {
        Integer v = jdbc.queryForObject("SELECT IFNULL(duration, 60) FROM train_exam WHERE id = ?",
                Integer.class, examId);
        return v == null ? 60 : v;
    }

    private String examName(String examNo) {
        return queryString("SELECT name FROM train_exam WHERE exam_no = ?", examNo);
    }

    private Long queryPlanId(String planNo) {
        return queryLong("SELECT id FROM train_plan WHERE plan_no = ?", planNo);
    }

    private String queryPlanName(String planNo) {
        return queryString("SELECT name FROM train_plan WHERE plan_no = ?", planNo);
    }

    private Integer queryPlanHours(Long planId) {
        Integer v = jdbc.queryForObject("SELECT IFNULL(hours, 0) FROM train_plan WHERE id = ?",
                Integer.class, planId);
        return v == null ? 0 : v;
    }

    private BigDecimal queryPlanCredit(Long planId) {
        BigDecimal v = jdbc.queryForObject("SELECT IFNULL(credit, 0) FROM train_plan WHERE id = ?",
                BigDecimal.class, planId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private LocalDate queryPlanEndDate(Long planId) {
        try {
            return jdbc.queryForObject("SELECT end_date FROM train_plan WHERE id = ?", LocalDate.class, planId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 培训关联考试的及格分；未关联考试时返回 null（不判合格）。 */
    private Integer queryExamPassScore(Long planId) {
        try {
            return jdbc.queryForObject("SELECT e.pass_score FROM train_plan p "
                    + "JOIN train_exam e ON e.id = p.exam_id WHERE p.id = ?", Integer.class, planId);
        } catch (Exception e) {
            return null;
        }
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    private String queryString(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, String.class, args);
        } catch (Exception e) {
            return null;
        }
    }
}
