package fan.rich.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 培训模块统计服务。
 *
 * <p>培训总览的 KPI、分布图、趋势与提醒在这里一次性算好返回，避免前端多次请求。
 * 口径参照企业培训系统：课程数、培训期数、参训人次、合格人次、合格率、总学时，
 * 以及按培训类型 / 课程分类 / 部门维度的分布和按月培训趋势。
 */
@Service
public class TrainStatsService {

    private final JdbcTemplate jdbc;

    public TrainStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 培训总览：KPI + 分布 + 趋势 + 提醒。 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();

        // ---------------------------------------------------------- KPI
        long courseCount = count("SELECT COUNT(*) FROM train_course");
        long coursePublished = count("SELECT COUNT(*) FROM train_course WHERE status = '已发布'");
        long planCount = count("SELECT COUNT(*) FROM train_plan WHERE status <> '已取消'");
        long planRunning = count("SELECT COUNT(*) FROM train_plan WHERE status IN ('报名中','进行中')");
        long planDone = count("SELECT COUNT(*) FROM train_plan WHERE status = '已结束'");
        long enrollCount = count("SELECT COUNT(*) FROM train_enroll");
        long passCount = count("SELECT COUNT(*) FROM train_enroll WHERE pass = 1");
        long signedCount = count("SELECT COUNT(*) FROM train_enroll WHERE sign_status = '已签到'");
        long hours = count("SELECT IFNULL(SUM(hours), 0) FROM train_enroll");
        BigDecimal credit = jdbc.queryForObject("SELECT IFNULL(SUM(credit), 0) FROM train_enroll", BigDecimal.class);

        Map<String, Object> kpi = new HashMap<>();
        kpi.put("courseCount", courseCount);
        kpi.put("coursePublished", coursePublished);
        kpi.put("planCount", planCount);
        kpi.put("planRunning", planRunning);
        kpi.put("planDone", planDone);
        kpi.put("enrollCount", enrollCount);
        kpi.put("passCount", passCount);
        kpi.put("signedCount", signedCount);
        kpi.put("hours", hours);
        kpi.put("credit", credit == null ? BigDecimal.ZERO : credit.setScale(1, java.math.RoundingMode.HALF_UP));
        kpi.put("passRate", enrollCount == 0 ? 0 : round(passCount * 100.0 / enrollCount));
        kpi.put("signRate", enrollCount == 0 ? 0 : round(signedCount * 100.0 / enrollCount));
        data.put("kpi", kpi);

        // ---------------------------------------------------------- 考试 KPI
        long examCount = count("SELECT COUNT(*) FROM train_exam");
        long examDone = count("SELECT COUNT(*) FROM train_exam WHERE status = '已结束'");
        long scoreCount = count("SELECT COUNT(*) FROM train_score");
        long scorePass = count("SELECT COUNT(*) FROM train_score WHERE pass = 1");
        BigDecimal avgScore = jdbc.queryForObject("SELECT IFNULL(ROUND(AVG(score), 1), 0) FROM train_score",
                BigDecimal.class);
        Map<String, Object> examKpi = new HashMap<>();
        examKpi.put("examCount", examCount);
        examKpi.put("examDone", examDone);
        examKpi.put("scoreCount", scoreCount);
        examKpi.put("scorePass", scorePass);
        examKpi.put("avgScore", avgScore == null ? BigDecimal.ZERO : avgScore);
        examKpi.put("passRate", scoreCount == 0 ? 0 : round(scorePass * 100.0 / scoreCount));
        data.put("examKpi", examKpi);

        // ---------------------------------------------------------- 分布
        data.put("planTypes", withRatio(jdbc.queryForList(
                "SELECT IFNULL(plan_type, '其他') AS name, COUNT(*) AS value "
                        + "FROM train_plan WHERE status <> '已取消' GROUP BY plan_type ORDER BY value DESC")));
        data.put("categories", withRatio(jdbc.queryForList(
                "SELECT IFNULL(category, '其他') AS name, COUNT(*) AS value "
                        + "FROM train_course GROUP BY category ORDER BY value DESC")));
        data.put("enrollStatus", withRatio(jdbc.queryForList(
                "SELECT IFNULL(status, '未开始') AS name, COUNT(*) AS value "
                        + "FROM train_enroll GROUP BY status ORDER BY value DESC")));
        data.put("deptStats", jdbc.queryForList(
                "SELECT IFNULL(dept, '未指定') AS name, COUNT(*) AS total, "
                        + "SUM(pass = 1) AS passed, IFNULL(SUM(hours), 0) AS hours "
                        + "FROM train_enroll GROUP BY dept ORDER BY total DESC LIMIT 8"));

        // ---------------------------------------------------------- 月度培训趋势
        data.put("trend", jdbc.queryForList(
                "SELECT DATE_FORMAT(start_date, '%Y-%m') AS label, COUNT(*) AS total, "
                        + "IFNULL(SUM(status = '已结束'), 0) AS done, "
                        + "IFNULL(SUM(plan_count), 0) AS planned "
                        + "FROM train_plan WHERE start_date IS NOT NULL AND status <> '已取消' "
                        + "GROUP BY DATE_FORMAT(start_date, '%Y-%m') ORDER BY label"));

        // ---------------------------------------------------------- 提醒
        Map<String, Object> reminders = new HashMap<>();
        reminders.put("enrolling", count("SELECT COUNT(*) FROM train_plan WHERE status = '报名中'"));
        reminders.put("starting", count("SELECT COUNT(*) FROM train_plan WHERE status <> '已取消' "
                + "AND start_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)"));
        reminders.put("running", count("SELECT COUNT(*) FROM train_plan WHERE status = '进行中'"));
        reminders.put("failed", count("SELECT COUNT(*) FROM train_enroll WHERE status = '不合格'"));
        reminders.put("unsigned", count("SELECT COUNT(*) FROM train_enroll "
                + "WHERE sign_status = '未签到' AND status <> '已完成'"));
        data.put("reminders", reminders);

        // ---------------------------------------------------------- 近期培训计划
        data.put("recentPlans", jdbc.queryForList(
                "SELECT id, plan_no AS planNo, name, plan_type AS planType, start_date AS startDate, "
                        + "end_date AS endDate, dept, teacher, plan_count AS planCount, "
                        + "enroll_count AS enrollCount, pass_count AS passCount, status "
                        + "FROM train_plan WHERE status <> '已取消' "
                        + "ORDER BY start_date DESC LIMIT 8"));

        return data;
    }

    /** 用参训明细回填某期培训计划的参训人数与合格人数。 */
    public void refreshPlan(Long planId) {
        if (planId == null) {
            return;
        }
        jdbc.update("UPDATE train_plan p SET "
                + "enroll_count = (SELECT COUNT(*) FROM train_enroll e WHERE e.plan_id = p.id),"
                + "pass_count = (SELECT COUNT(*) FROM train_enroll e WHERE e.plan_id = p.id AND e.pass = 1) "
                + "WHERE p.id = ?", planId);
    }

    /** 用成绩明细回填某场考试的应考人数、合格人数与平均分。 */
    public void refreshExam(Long examId) {
        if (examId == null) {
            return;
        }
        jdbc.update("UPDATE train_exam e SET "
                + "examinee_count = (SELECT COUNT(*) FROM train_score s WHERE s.exam_id = e.id),"
                + "pass_count = (SELECT COUNT(*) FROM train_score s WHERE s.exam_id = e.id AND s.pass = 1),"
                + "avg_score = IFNULL((SELECT ROUND(AVG(s.score), 1) FROM train_score s WHERE s.exam_id = e.id), 0) "
                + "WHERE e.id = ?", examId);
    }

    /** 用参训记录回填课程的学习人次。 */
    public void refreshCourse(Long courseId) {
        if (courseId == null) {
            return;
        }
        jdbc.update("UPDATE train_course c SET "
                + "learn_count = (SELECT COUNT(*) FROM train_plan p "
                + "  JOIN train_enroll e ON e.plan_id = p.id WHERE p.course_id = c.id) "
                + "WHERE c.id = ?", courseId);
    }

    /**
     * 培训计划状态自动流转。
     *
     * <p>培训是随时间推进的：到了开始日期由「报名中」转为「进行中」，过了结束日期转为「已结束」，
     * 不需要人工逐个改状态，看板加载前调一次即可。
     */
    public int refreshPlanStatus() {
        int n = jdbc.update("UPDATE train_plan SET status = '进行中' "
                + "WHERE status IN ('草稿','报名中') AND start_date <= CURDATE() AND end_date >= CURDATE()");
        n += jdbc.update("UPDATE train_plan SET status = '已结束' "
                + "WHERE status IN ('报名中','进行中') AND end_date < CURDATE()");
        return n;
    }

    // ------------------------------------------------------------------ 工具

    private long count(String sql) {
        Long total = jdbc.queryForObject(sql, Long.class);
        return total == null ? 0 : total;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** 给分布数据补上占比（%）。 */
    private List<Map<String, Object>> withRatio(List<Map<String, Object>> rows) {
        long total = 0;
        for (Map<String, Object> row : rows) {
            Object v = row.get("value");
            total += v instanceof Number n ? n.longValue() : 0;
        }
        for (Map<String, Object> row : rows) {
            Object v = row.get("value");
            long value = v instanceof Number n ? n.longValue() : 0;
            row.put("ratio", total == 0 ? 0 : round(value * 100.0 / total));
        }
        return rows;
    }
}
