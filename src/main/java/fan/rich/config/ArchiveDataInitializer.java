package fan.rich.config;

import fan.rich.service.ArchiveStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 档案管理模块初始化器：门类、存放位置、案卷、卷内文件、借阅与销毁演示数据。
 *
 * <p>启动时按 {@link ArchiveSchemaDdl} 建表，演示数据刻意做成<b>全生命周期并存</b>：
 * <ul>
 *   <li>案卷三种状态：在库（多数）、借阅中（借出未还）、封存（待销毁）</li>
 *   <li>借阅四种状态：借阅中、已归还、逾期未还、个别已丢失——逾期由
 *       {@link ArchiveStatsService#refreshBorrowStatus()} 按当天日期动态判定，不是写死的</li>
 *   <li>销毁两条清册：一条已完成（对应案卷状态为「已销毁」），一条待审批</li>
 * </ul>
 *
 * <p>以门类代码 {@code WS} 且案卷号 {@code 2026-WS-0001} 为标记，存在则跳过；
 * 配置 {@code rich.archive.reset-demo=true} 时首次写入前清空旧演示数据；
 * {@code rich.archive.enabled=false} 可整体关闭。
 */
@Order(8)
@Component
public class ArchiveDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArchiveDataInitializer.class);

    /** 演示数据标记：首个案卷号（须与 {@code generateVolumes} 生成的格式一致）。 */
    private static final String MARK_VOLUME = "2026-WS-001";

    private final JdbcTemplate jdbc;
    private final ArchiveStatsService statsService;
    private final boolean enabled;
    private final boolean initDemo;
    private final boolean resetDemo;

    /** 位置编号 → {办公室, 柜号, 层号, 文件夹号}，生成案卷时用于回填冗余字段。 */
    private final Map<String, String[]> locByCode = new HashMap<>();

    /** 柜级编号（如 A-01）→ 该柜下的全部位置编号，案卷按序落到具体文件夹。 */
    private final Map<String, List<String>> foldersByCabinet = new HashMap<>();

    public ArchiveDataInitializer(JdbcTemplate jdbc,
                                  ArchiveStatsService statsService,
                                  @Value("${rich.archive.enabled:true}") boolean enabled,
                                  @Value("${rich.archive.init-demo:true}") boolean initDemo,
                                  @Value("${rich.archive.reset-demo:true}") boolean resetDemo) {
        this.jdbc = jdbc;
        this.statsService = statsService;
        this.enabled = enabled;
        this.initDemo = initDemo;
        this.resetDemo = resetDemo;
    }

    // ================================================================ 门类

    /** 门类：代码 / 名称 / 归口部门 / 默认保管期限 / 排序。 */
    private static final String[][] CATEGORIES = {
            {"WS", "文书档案", "综合办公室", "永久", "1"},
            {"KJ", "会计档案", "财务科", "30年", "2"},
            {"RS", "人事档案", "综合办公室", "永久", "3"},
            {"HT", "合同档案", "企管科", "30年", "4"},
            {"ZZ", "证照档案", "综合办公室", "永久", "5"},
            {"SJ", "审计档案", "财务科", "30年", "6"},
            {"YX", "影像档案", "综合办公室", "10年", "7"},
            {"SW", "实物档案", "综合办公室", "永久", "8"},
    };

    // ================================================================ 存放位置

    /** 位置：编号 / 库房 / 柜架号 / 层号 / 容量(卷)。 */
    /**
     * 存放位置：办公室 / 柜号 / 层号 / 文件夹 / 容量。
     *
     * <p>位置编号由「办公室代码-柜号-层号-文件夹号」自动拼成，如 {@code A-01-1-02}。
     */
    private static final String[][] LOCATIONS = {
            // 综合办公室：文书现行、文书历史、人事
            {"A-01", "综合办公室", "1号档案柜（文书-现行）", "第1层", "第01夹 2026年发文", "8"},
            {"A-01", "综合办公室", "1号档案柜（文书-现行）", "第1层", "第02夹 2026年收文", "8"},
            {"A-01", "综合办公室", "1号档案柜（文书-现行）", "第2层", "第01夹 2025年发文", "8"},
            {"A-01", "综合办公室", "1号档案柜（文书-现行）", "第2层", "第02夹 2025年收文", "8"},
            {"A-02", "综合办公室", "2号档案柜（文书-历史）", "第1层", "第01夹 2024年文书", "10"},
            {"A-02", "综合办公室", "2号档案柜（文书-历史）", "第1层", "第02夹 2023年文书", "10"},
            {"A-02", "综合办公室", "2号档案柜（文书-历史）", "第2层", "第01夹 2022年及以前", "10"},
            {"A-02", "综合办公室", "2号档案柜（文书-历史）", "第2层", "第02夹 制度与总结", "10"},
            {"A-03", "综合办公室", "3号档案柜（人事）", "第1层", "第01夹 在职人员", "12"},
            {"A-03", "综合办公室", "3号档案柜（人事）", "第1层", "第02夹 劳动合同", "12"},
            {"A-03", "综合办公室", "3号档案柜（人事）", "第2层", "第01夹 退休及离岗", "10"},
            // 财务科档案室：凭证、账簿报告
            {"B-01", "财务科档案室", "1号凭证柜", "第1层", "第01夹 2026年凭证", "10"},
            {"B-01", "财务科档案室", "1号凭证柜", "第1层", "第02夹 2025年凭证", "10"},
            {"B-01", "财务科档案室", "1号凭证柜", "第2层", "第01夹 2024年凭证", "10"},
            {"B-01", "财务科档案室", "1号凭证柜", "第2层", "第02夹 2015年凭证", "10"},
            {"B-02", "财务科档案室", "2号账簿柜", "第1层", "第01夹 账簿总账", "8"},
            {"B-02", "财务科档案室", "2号账簿柜", "第1层", "第02夹 财务报告", "8"},
            {"B-02", "财务科档案室", "2号账簿柜", "第2层", "第01夹 纳税与银行", "10"},
            {"B-02", "财务科档案室", "2号账簿柜", "第2层", "第02夹 工资清册", "8"},
            // 企管科资料室：合同、证照、审计
            {"C-01", "企管科资料室", "1号合同柜", "第1层", "第01夹 采购施工合同", "10"},
            {"C-01", "企管科资料室", "1号合同柜", "第1层", "第02夹 销售合同", "8"},
            {"C-01", "企管科资料室", "1号合同柜", "第2层", "第01夹 合同汇总", "10"},
            {"C-01", "企管科资料室", "1号合同柜", "第2层", "第02夹 审计台账", "6"},
            {"C-02", "企管科资料室", "2号证照柜", "第1层", "第01夹 证照正本", "6"},
            {"C-02", "企管科资料室", "2号证照柜", "第1层", "第02夹 许可与资质", "6"},
            {"C-02", "企管科资料室", "2号证照柜", "第2层", "第01夹 审计报告", "6"},
            {"C-02", "企管科资料室", "2号证照柜", "第2层", "第02夹 内控与整改", "6"},
            // 档案中心一号库：影像实物、待销毁暂存
            {"D-01", "档案中心一号库", "1号声像柜", "第1层", "第01夹 影像资料", "6"},
            {"D-01", "档案中心一号库", "1号声像柜", "第1层", "第02夹 实物档案", "6"},
            {"D-01", "档案中心一号库", "1号声像柜", "第2层", "第01夹 荣誉与印鉴", "6"},
            {"D-02", "档案中心一号库", "2号待销毁柜", "第1层", "第01夹 2016年文书", "8"},
            {"D-02", "档案中心一号库", "2号待销毁柜", "第1层", "第02夹 2016年文书（二）", "8"},
    };

    // ================================================================ 案卷

    // 案卷字段下标
    private static final int V_TITLE = 0;
    private static final int V_CATEGORY = 1;
    private static final int V_YEAR = 2;
    private static final int V_RETENTION = 3;
    private static final int V_SECURITY = 4;
    private static final int V_DEPT = 5;
    private static final int V_ARCHIVIST = 6;
    private static final int V_CARRIER = 7;
    private static final int V_LOCATION = 8;
    private static final int V_STATUS = 9;

    /** 案卷：题名 / 门类 / 年度 / 保管期限 / 密级 / 归档部门 / 立卷人 / 载体 / 位置 / 状态。 */
    private static final String[][] VOLUMES = {
            {"2026年公司发文（第一批）", "WS", "2026", "永久", "内部", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2026年公司发文（第二批）", "WS", "2026", "永久", "内部", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2026年公司收文（第一批）", "WS", "2026", "30年", "内部", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2026年党委会议纪要", "WS", "2026", "永久", "秘密", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2026年总经理办公会纪要", "WS", "2026", "永久", "秘密", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2025年公司发文（全年）", "WS", "2025", "永久", "内部", "综合办公室", "王玉芹", "纸质", "A-01", "在库"},
            {"2025年公司收文（全年）", "WS", "2025", "30年", "内部", "综合办公室", "王玉芹", "纸质", "A-02", "在库"},
            {"2025年规章制度汇编", "WS", "2025", "永久", "内部", "企管科", "张科长", "纸质", "A-02", "借阅中"},
            {"2024年公司发文（全年）", "WS", "2024", "永久", "内部", "综合办公室", "王玉芹", "纸质", "A-02", "在库"},
            {"2024年收发文登记簿", "WS", "2024", "30年", "内部", "综合办公室", "王玉芹", "纸质", "A-02", "在库"},
            {"2023年公司发文（全年）", "WS", "2023", "永久", "内部", "综合办公室", "王玉芹", "纸质", "A-02", "在库"},
            {"2022-2023年工作总结与计划", "WS", "2023", "30年", "内部", "综合办公室", "王玉芹", "纸质", "A-02", "在库"},

            {"2026年会计凭证（1-6月）", "KJ", "2026", "30年", "秘密", "财务科", "李春梅", "纸质", "B-01", "在库"},
            {"2026年会计账簿（总账明细账）", "KJ", "2026", "30年", "秘密", "财务科", "李春梅", "纸质", "B-01", "借阅中"},
            {"2026年第一季度财务报告", "KJ", "2026", "永久", "秘密", "财务科", "李春梅", "纸质", "B-01", "在库"},
            {"2025年会计凭证（全年）", "KJ", "2025", "30年", "秘密", "财务科", "李春梅", "纸质", "B-01", "在库"},
            {"2025年会计账簿（全年）", "KJ", "2025", "30年", "秘密", "财务科", "李春梅", "纸质", "B-01", "在库"},
            {"2025年度财务决算报告", "KJ", "2025", "永久", "秘密", "财务科", "李春梅", "纸质", "B-02", "在库"},
            {"2025年纳税申报资料", "KJ", "2025", "10年", "内部", "财务科", "李春梅", "纸质", "B-02", "借阅中"},
            {"2025年银行对账单", "KJ", "2025", "10年", "内部", "财务科", "李春梅", "纸质", "B-02", "在库"},
            {"2024年会计凭证（全年）", "KJ", "2024", "30年", "秘密", "财务科", "李春梅", "纸质", "B-02", "在库"},
            {"2024年工资发放清册", "KJ", "2024", "30年", "秘密", "财务科", "李春梅", "纸质", "B-02", "在库"},

            {"在职职工人事档案（第一批）", "RS", "2026", "永久", "秘密", "综合办公室", "王玉芹", "纸质", "A-03", "在库"},
            {"在职职工人事档案（第二批）", "RS", "2026", "永久", "秘密", "综合办公室", "王玉芹", "纸质", "A-03", "在库"},
            {"2026年劳动合同台账", "RS", "2026", "30年", "内部", "综合办公室", "王玉芹", "纸质", "A-03", "借阅中"},
            {"退休及离岗人员档案", "RS", "2024", "永久", "秘密", "综合办公室", "王玉芹", "纸质", "A-03", "在库"},

            {"2026年物资采购合同", "HT", "2026", "30年", "内部", "企管科", "张科长", "纸质", "C-01", "在库"},
            {"2026年工程施工合同", "HT", "2026", "30年", "内部", "企管科", "张科长", "纸质", "C-01", "在库"},
            {"2026年精矿销售合同", "HT", "2026", "永久", "秘密", "企管科", "张科长", "纸质", "C-01", "借阅中"},
            {"2025年采购与施工合同", "HT", "2025", "30年", "内部", "企管科", "张科长", "纸质", "C-01", "在库"},
            {"2025年销售合同（国大）", "HT", "2025", "永久", "秘密", "企管科", "张科长", "纸质", "C-01", "在库"},
            {"2024年各类合同汇总", "HT", "2024", "30年", "内部", "企管科", "张科长", "纸质", "C-01", "在库"},

            {"公司证照正本", "ZZ", "2026", "永久", "内部", "综合办公室", "王玉芹", "纸质", "C-02", "在库"},
            {"采矿与安全生产许可", "ZZ", "2026", "永久", "内部", "综合办公室", "王玉芹", "纸质", "C-02", "借阅中"},
            {"历年资质与荣誉证书", "ZZ", "2023", "永久", "内部", "综合办公室", "王玉芹", "纸质", "C-02", "在库"},

            {"2026年二季度财务检查台账", "SJ", "2026", "30年", "秘密", "财务科", "李春梅", "纸质", "C-01", "借阅中"},
            {"2025年度财务报表审计报告", "SJ", "2025", "永久", "秘密", "财务科", "李春梅", "纸质", "C-02", "在库"},
            {"2025年度内控评价报告", "SJ", "2025", "30年", "秘密", "财务科", "李春梅", "纸质", "C-02", "在库"},
            {"2024年专项审计工作底稿", "SJ", "2024", "30年", "秘密", "财务科", "李春梅", "纸质", "C-02", "在库"},

            {"2025年工作会议与安全月影像", "YX", "2025", "10年", "内部", "综合办公室", "王玉芹", "声像", "D-01", "在库"},
            {"2024年公司宣传与培训影像", "YX", "2024", "10年", "内部", "综合办公室", "王玉芹", "声像", "D-01", "在库"},

            {"荣誉奖牌与印鉴实物", "SW", "2024", "永久", "内部", "综合办公室", "王玉芹", "实物", "D-01", "在库"},

            // 已销毁：2015 年会计凭证，见销毁清册 XH2025-0001
            {"2015年会计凭证（1-3月）", "KJ", "2015", "10年", "秘密", "财务科", "李春梅", "纸质", "B-02", "已销毁"},
            {"2015年会计凭证（4-6月）", "KJ", "2015", "10年", "秘密", "财务科", "李春梅", "纸质", "B-02", "已销毁"},
            {"2015年会计凭证（7-9月）", "KJ", "2015", "10年", "秘密", "财务科", "李春梅", "纸质", "B-02", "已销毁"},
            {"2015年会计凭证（10-12月）", "KJ", "2015", "10年", "秘密", "财务科", "李春梅", "纸质", "B-02", "已销毁"},
            {"2015年银行回单与对账单", "KJ", "2015", "10年", "内部", "财务科", "李春梅", "纸质", "B-02", "已销毁"},

            // 待销毁：2016 年文书档案，见销毁清册 XH2026-0001（待审批，案卷先封存）
            {"2016年公司发文（上半年）", "WS", "2016", "10年", "内部", "综合办公室", "王玉芹", "纸质", "D-02", "封存"},
            {"2016年公司发文（下半年）", "WS", "2016", "10年", "内部", "综合办公室", "王玉芹", "纸质", "D-02", "封存"},
            {"2016年一般事务性收文", "WS", "2016", "10年", "内部", "综合办公室", "王玉芹", "纸质", "D-02", "封存"},
    };

    // ================================================================ 卷内文件模板

    /** 按门类的文件题名模板。 */
    private static final Map<String, String[]> FILE_TITLES = new HashMap<>();

    static {
        FILE_TITLES.put("WS", new String[]{
                "关于做好年度工作的通知", "年度安全生产工作总结", "第季度生产经营分析会纪要",
                "公司规章制度修订说明", "收发文登记与督办情况", "关于人事任免的通知"});
        FILE_TITLES.put("KJ", new String[]{
                "月份记账凭证及原始单据", "月份银行余额调节表", "月份工资发放汇总表",
                "季度纳税申报表及完税凭证", "年度财务决算说明", "月份成本费用分析表"});
        FILE_TITLES.put("RS", new String[]{
                "员工入职登记表与资格审查", "年度绩效考核结果汇总", "劳动合同续签与变更记录",
                "社会保险与公积金缴纳明细", "岗位培训与持证情况登记"});
        FILE_TITLES.put("HT", new String[]{
                "物资采购合同及补充协议", "工程施工合同及工程量清单", "精矿销售合同与结算条款",
                "设备维修劳务合同", "技术服务与咨询合同"});
        FILE_TITLES.put("ZZ", new String[]{
                "营业执照正副本复印件", "采矿许可证正本", "安全生产许可证",
                "排污许可证及检测报告", "商标注册证与年检材料"});
        FILE_TITLES.put("SJ", new String[]{
                "年度财务报表审计报告", "内部控制评价报告", "季度财务检查工作底稿",
                "审计整改台账与佐证材料", "专项审计取证单"});
        FILE_TITLES.put("YX", new String[]{
                "年度工作会议影像资料", "安全生产月活动照片", "公司宣传片素材与脚本"});
        FILE_TITLES.put("SW", new String[]{
                "年度先进单位奖牌", "财务专用章与法人章印模", "客户赠送锦旗"});
    }

    // ================================================================ 借阅

    // 借阅字段下标
    private static final int B_VOLUME = 0;
    private static final int B_BORROWER = 1;
    private static final int B_DEPT = 2;
    private static final int B_PURPOSE = 3;
    private static final int B_START = 4;
    private static final int B_DAYS = 5;
    private static final int B_RETURN = 6;

    /**
     * 借阅：案卷题名关键字 / 借阅人 / 部门 / 用途 / 借出距今天数 / 借阅期限(天) / 归还偏移。
     *
     * <p>归还偏移 {@code -1} 表示尚未归还；期限 + 借出日早于今天即形成逾期。
     */
    private static final Object[][] BORROWS = {
            {"2025年规章制度汇编", "张科长", "企管科", "制度修订查阅", 12, 15, -1},
            {"2026年会计账簿（总账明细账）", "李春梅", "财务科", "月末结账核对", 5, 30, -1},
            {"2025年纳税申报资料", "周会计", "财务科", "税务自查", 40, 15, -1},
            {"2026年精矿销售合同", "张科长", "企管科", "合同条款核对", 20, 15, -1},
            {"采矿与安全生产许可", "赵金凤", "安全生产科", "资质年审", 3, 15, -1},
            {"2026年劳动合同台账", "王玉芹", "综合办公室", "用工核查", 9, 15, -1},
            {"2026年二季度财务检查台账", "李春梅", "财务科", "整改佐证调阅", 2, 15, -1},
            {"2024年会计凭证（全年）", "周会计", "财务科", "审计取证", 55, 15, 10},
            {"2025年度财务决算报告", "李雲钊", "生产技术科", "经营分析参考", 70, 15, 12},
            {"2024年工资发放清册", "王玉芹", "综合办公室", "工龄核定", 100, 15, 13},
            {"2025年销售合同（国大）", "张科长", "企管科", "结算争议核对", 85, 15, 9},
            {"2025年度内控评价报告", "李春梅", "财务科", "制度完善参考", 130, 15, 14},
            {"2024年专项审计工作底稿", "周会计", "财务科", "复查取证", 160, 15, 20},
            {"2023年公司发文（全年）", "王玉芹", "综合办公室", "历史沿革查询", 200, 15, 15},
            {"2022-2023年工作总结与计划", "李雲钊", "生产技术科", "年度计划编制", 260, 15, 11},
            {"2026年公司收文（第一批）", "王玉芹", "综合办公室", "文件督办", 300, 15, 18},
    };

    // ================================================================ 销毁清册

    /** 销毁清册：单号 / 题名 / 门类 / 原因 / 申请部门 / 申请人 / 申请日期偏移 / 审批人 / 销毁方式 / 状态。 */
    private static final Object[][] DESTROYS = {
            {"XH2025-0001", "2015年度会计凭证到期销毁清册", "KJ", "保管期限届满（10年）", "财务科", "李春梅", -330,
                    "李雲钊", "粉碎", "已销毁"},
            {"XH2026-0001", "2016年度一般性文书档案销毁清册", "WS", "保管期限届满（10年），无留存价值", "综合办公室", "王玉芹", -20,
                    "", "粉碎", "待审批"},
    };

    // ================================================================ 执行

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (String ddl : ArchiveSchemaDdl.DDL) {
            jdbc.execute(ddl);
        }
        ensureColumns();
        log.info("[rich] 档案管理模块建表完成");
        if (!initDemo) {
            return;
        }
        if (count("SELECT COUNT(*) FROM arch_volume WHERE volume_no = ?", MARK_VOLUME) > 0) {
            log.info("[rich] 档案演示数据已存在，跳过");
            statsService.refreshAll();
            return;
        }
        if (resetDemo) {
            jdbc.execute("DELETE FROM arch_destroy_item");
            jdbc.execute("DELETE FROM arch_destroy");
            jdbc.execute("DELETE FROM arch_borrow");
            jdbc.execute("DELETE FROM arch_file");
            jdbc.execute("DELETE FROM arch_volume");
            jdbc.execute("DELETE FROM arch_location");
            jdbc.execute("DELETE FROM arch_category");
        }
        generateCategories();
        generateLocations();
        Map<String, Long> volumeIds = generateVolumes();
        generateFiles(volumeIds);
        generateBorrows(volumeIds);
        generateDestroys(volumeIds);
        statsService.refreshAll();
        log.info("[rich] 档案演示数据生成完成：门类 {} / 位置 {} / 案卷 {} / 文件 {} / 借阅 {} / 销毁 {}",
                CATEGORIES.length, LOCATIONS.length, VOLUMES.length,
                count("SELECT COUNT(*) FROM arch_file"),
                count("SELECT COUNT(*) FROM arch_borrow"),
                count("SELECT COUNT(*) FROM arch_destroy"));
    }

    /** 按需补列：旧库缺少新增列时自动 ALTER，保证升级不丢数据。 */
    private void ensureColumns() {
        for (String[] c : ArchiveSchemaDdl.ENSURE_COLUMNS) {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS"
                            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, c[0], c[1]);
            if (exists != null && exists > 0) {
                continue;
            }
            jdbc.execute("ALTER TABLE " + c[0] + " ADD COLUMN " + c[1] + " " + c[2]);
            log.info("[rich] 档案模块补列：{}.{}", c[0], c[1]);
        }
    }

    private void generateCategories() {
        LocalDateTime now = LocalDateTime.now();
        for (String[] c : CATEGORIES) {
            jdbc.update("INSERT INTO arch_category (category_code, category_name, keeper_dept,"
                            + " retention, seq, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?)",
                    c[0], c[1], c[2], c[3], Integer.parseInt(c[4]), c[1] + "由" + c[2] + "归口管理", now, now);
        }
    }

    private void generateLocations() {
        LocalDateTime now = LocalDateTime.now();
        locByCode.clear();
        foldersByCabinet.clear();
        for (String[] l : LOCATIONS) {
            String cabinetCode = l[0];
            String officeRoom = l[1];
            String cabinetNo = l[2];
            String layerNo = l[3];
            String folderNo = l[4];
            String code = cabinetCode + "-" + digits(layerNo) + "-" + pad2(digits(folderNo));
            jdbc.update("INSERT INTO arch_location (location_code, office_room, room_name, cabinet_no,"
                            + " layer_no, folder_no, capacity, used_count, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,0,?,?,?)",
                    code, officeRoom, officeRoom, cabinetNo, layerNo, folderNo,
                    Integer.parseInt(l[5]), officeRoom + " " + cabinetNo + " " + layerNo + " " + folderNo,
                    now, now);
            locByCode.put(code, new String[]{officeRoom, cabinetNo, layerNo, folderNo});
            foldersByCabinet.computeIfAbsent(cabinetCode, k -> new ArrayList<>()).add(code);
        }
    }

    /** 从「第1层 / 第01夹」里取出数字。 */
    private String digits(String text) {
        Matcher m = Pattern.compile("\\d+").matcher(text == null ? "" : text);
        return m.find() ? m.group() : "0";
    }

    private String pad2(String num) {
        return num.length() >= 2 ? num : "0" + num;
    }

    /** 生成案卷，返回 案卷号 → 主键 的映射。 */
    private Map<String, Long> generateVolumes() {
        Map<String, Long> ids = new HashMap<>();
        Map<String, Integer> seq = new HashMap<>();
        Map<String, Integer> cabinetSeq = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (String[] v : VOLUMES) {
            int year = Integer.parseInt(v[V_YEAR]);
            String category = v[V_CATEGORY];
            int next = seq.getOrDefault(category + year, 0) + 1;
            seq.put(category + year, next);
            String volumeNo = String.format("%d-%s-%03d", year, category, next);

            // 柜级编号（如 A-01）→ 具体文件夹位置，同柜案卷按顺序落到各文件夹
            String cabinet = v[V_LOCATION];
            List<String> folders = foldersByCabinet.getOrDefault(cabinet, List.of());
            int inCabinet = cabinetSeq.merge(cabinet, 1, Integer::sum) - 1;
            String locationCode = folders.isEmpty()
                    ? cabinet : folders.get(inCabinet % folders.size());
            String[] loc = locByCode.getOrDefault(locationCode, new String[]{"", "", "", ""});
            String boxNo = loc[3] == null || loc[3].isEmpty() ? "" : "H-" + digits(loc[3]);

            LocalDate archiveDate = LocalDate.of(year, Math.min(12, Math.max(1, 3 + next)), 15);
            LocalDate startDate = LocalDate.of(year, 1, 5);
            LocalDate endDate = LocalDate.of(year, Math.min(12, Math.max(1, 3 + next)), 10);
            if ("已销毁".equals(v[V_STATUS])) {
                archiveDate = LocalDate.of(year, 12, 20);
            }
            long id = insert("INSERT INTO arch_volume (volume_no, title, category, year, retention,"
                            + " security, start_date, end_date, file_count, page_count, carrier,"
                            + " archive_dept, archivist, archive_date, location_code, location_name,"
                            + " office_room, cabinet_no, layer_no, folder_no,"
                            + " box_no, qr_code, borrow_times, status, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?,0,0,?,?,?,?,?,?,?,?,?,?,"
                            + "?,?,0,?,?,?,?)",
                    volumeNo, v[V_TITLE], category, year, v[V_RETENTION], v[V_SECURITY],
                    startDate, endDate, v[V_CARRIER], v[V_DEPT], v[V_ARCHIVIST], archiveDate,
                    locationCode, locationName(locationCode),
                    loc[0], loc[1], loc[2], loc[3],
                    boxNo, "VOL-" + volumeNo, v[V_STATUS], "", now, now);
            ids.put(v[V_TITLE], id);
        }
        return ids;
    }

    /** 逐卷生成卷内文件：每卷 3~7 件，日期在案卷年度内递增。 */
    private void generateFiles(Map<String, Long> volumeIds) {
        int idx = 0;
        for (String[] v : VOLUMES) {
            Long volumeId = volumeIds.get(v[V_TITLE]);
            String category = v[V_CATEGORY];
            int year = Integer.parseInt(v[V_YEAR]);
            String[] templates = FILE_TITLES.getOrDefault(category, new String[]{"文件材料"});
            int items = 3 + (idx * 3) % 5;
            int pageStart = 1;
            for (int i = 0; i < items; i++) {
                String title = templates[(idx + i) % templates.length];
                int pages = 3 + (idx * 7 + i * 11) % 28;
                LocalDate docDate = LocalDate.of(year, 1, 1).plusDays((idx * 13 + i * 27) % 330);
                jdbc.update("INSERT INTO arch_file (volume_id, volume_no, item_no, doc_no, title,"
                                + " responsible, doc_date, page_count, page_no, security, carrier,"
                                + " file_path, remark, create_time, update_time)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                        volumeId, volumeNo(volumeId), i + 1,
                        String.format("%s〔%d〕%d号", category, year, i + 1),
                        title, v[V_DEPT], docDate, pages,
                        pageStart + "-" + (pageStart + pages - 1), v[V_SECURITY], v[V_CARRIER],
                        "电子".equals(v[V_CARRIER]) ? "/archive/" + year + "/" + category + "/" + (i + 1) + ".pdf" : "",
                        "");
                pageStart += pages;
            }
            idx++;
        }
    }

    private void generateBorrows(Map<String, Long> volumeIds) {
        LocalDate today = LocalDate.now();
        int i = 0;
        for (Object[] b : BORROWS) {
            Long volumeId = volumeIds.get(String.valueOf(b[B_VOLUME]));
            if (volumeId == null) {
                continue;
            }
            LocalDate borrowDate = today.plusDays(-((Integer) b[B_START]));
            int days = (Integer) b[B_DAYS];
            LocalDate dueDate = borrowDate.plusDays(days);
            int ret = (Integer) b[B_RETURN];
            LocalDate returnDate = ret >= 0 ? borrowDate.plusDays(ret) : null;
            String status = returnDate != null ? "已归还" : (dueDate.isBefore(today) ? "逾期" : "借阅中");
            if (i == 13) {
                // 一条"已丢失"，用于演示异常状态的处理
                status = "已丢失";
                returnDate = null;
            }
            insert("INSERT INTO arch_borrow (borrow_no, volume_id, volume_no, volume_title, borrower,"
                            + " borrower_dept, purpose, borrow_date, due_date, return_date, handler,"
                            + " approver, overdue_days, status, remark, create_time, update_time)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                    String.format("JY%d-%03d", borrowDate.getYear(), i + 1),
                    volumeId, volumeNo(volumeId), String.valueOf(b[B_VOLUME]),
                    b[B_BORROWER], b[B_DEPT], b[B_PURPOSE], borrowDate, dueDate, returnDate,
                    "王玉芹", "李雲钊", 0, status, "");
            i++;
        }
    }

    private void generateDestroys(Map<String, Long> volumeIds) {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < DESTROYS.length; i++) {
            Object[] d = DESTROYS[i];
            LocalDate applyDate = today.plusDays((Integer) d[6]);
            String status = String.valueOf(d[9]);
            LocalDate approveDate = "已销毁".equals(status) ? applyDate.plusDays(10) : null;
            LocalDate destroyDate = "已销毁".equals(status) ? applyDate.plusDays(25) : null;
            long destroyId = insert("INSERT INTO arch_destroy (destroy_no, title, category, volume_count,"
                            + " page_count, reason, apply_dept, applicant, apply_date, approver,"
                            + " approve_date, witness, method, destroy_date, status, remark,"
                            + " create_time, update_time)"
                            + " VALUES (?,?,?,0,0,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                    d[0], d[1], d[2], d[3], d[4], d[5], applyDate,
                    "已销毁".equals(status) ? d[7] : "", approveDate,
                    "已销毁".equals(status) ? "李雲钊,王玉芹" : "", d[8], destroyDate, status, "");

            // 明细：已销毁清册取已销毁的会计卷，待审批清册取封存的文书卷
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, volume_no, title, category, retention, page_count FROM arch_volume"
                            + " WHERE status = ? AND category = ? ORDER BY volume_no",
                    i == 0 ? "已销毁" : "封存", String.valueOf(d[2]));
            for (Map<String, Object> r : rows) {
                jdbc.update("INSERT INTO arch_destroy_item (destroy_id, volume_id, volume_no,"
                                + " volume_title, category, retention, page_count, remark,"
                                + " create_time, update_time) VALUES (?,?,?,?,?,?,?,?,NOW(),NOW())",
                        destroyId, ((Number) r.get("id")).longValue(), r.get("volume_no"),
                        r.get("title"), r.get("category"), r.get("retention"),
                        r.get("page_count"), "");
            }
        }
    }

    // ================================================================ 工具

    private String locationName(String code) {
        String[] l = locByCode.get(code);
        if (l == null) {
            return code;
        }
        return l[0] + " / " + l[1] + " / " + l[2] + " / " + l[3];
    }

    private String volumeNo(Long volumeId) {
        return jdbc.queryForObject("SELECT volume_no FROM arch_volume WHERE id = ?", String.class, volumeId);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    /** 插入并返回自增主键。 */
    private long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        final Object[] params = args;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                Object v = params[i];
                if (v instanceof LocalDate d) {
                    ps.setDate(i + 1, Date.valueOf(d));
                } else {
                    ps.setObject(i + 1, v);
                }
            }
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? 0L : key.longValue();
    }
}
