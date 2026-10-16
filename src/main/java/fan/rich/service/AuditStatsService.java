package fan.rich.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计 / 检查统计服务。
 *
 * <p>看板的 KPI、分布图与提醒都在这里一次性算好返回，避免前端多次请求。
 * 统计口径与参考的审计整改系统保持一致：问题数、已销号、逾期、整改率、整改类型分布、
 * 问题性质分布、年度整改趋势。
 */
@Service
public class AuditStatsService {

    private final JdbcTemplate jdbc;

    public AuditStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 总览：KPI + 分布 + 趋势 + 提醒。 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();

        // ---------------------------------------------------------- KPI
        long projectCount = count("SELECT COUNT(*) FROM audit_project");
        long issueCount = count("SELECT COUNT(*) FROM audit_issue");
        long closedCount = count("SELECT COUNT(*) FROM audit_issue WHERE status = '已销号'");
        long overdueCount = count("SELECT COUNT(*) FROM audit_issue WHERE status <> '已销号' AND due_date < CURDATE()");
        long punishCount = count("SELECT IFNULL(SUM(punish_count), 0) FROM audit_issue");
        BigDecimal amount = jdbc.queryForObject("SELECT IFNULL(SUM(amount), 0) FROM audit_issue", BigDecimal.class);

        Map<String, Object> kpi = new HashMap<>();
        kpi.put("projectCount", projectCount);
        kpi.put("issueCount", issueCount);
        kpi.put("closedCount", closedCount);
        kpi.put("openCount", issueCount - closedCount);
        kpi.put("overdueCount", overdueCount);
        kpi.put("punishCount", punishCount);
        kpi.put("amount", amount == null ? BigDecimal.ZERO : amount);
        kpi.put("rectifyRate", issueCount == 0 ? 0 : round(closedCount * 100.0 / issueCount));
        data.put("kpi", kpi);

        // ---------------------------------------------------------- 检查 KPI
        long taskCount = count("SELECT COUNT(*) FROM inspect_task");
        long taskDone = count("SELECT COUNT(*) FROM inspect_task WHERE status = '已完成'");
        long findingCount = count("SELECT COUNT(*) FROM inspect_finding");
        long findingOpen = count("SELECT COUNT(*) FROM inspect_finding WHERE status <> '已整改'");
        Map<String, Object> inspectKpi = new HashMap<>();
        inspectKpi.put("taskCount", taskCount);
        inspectKpi.put("taskDone", taskDone);
        inspectKpi.put("findingCount", findingCount);
        inspectKpi.put("findingOpen", findingOpen);
        data.put("inspectKpi", inspectKpi);

        // ---------------------------------------------------------- 分布
        data.put("issueTypes", withRatio(jdbc.queryForList(
                "SELECT IFNULL(issue_type, '其他') AS name, COUNT(*) AS value "
                        + "FROM audit_issue GROUP BY issue_type ORDER BY value DESC")));
        data.put("progresses", withRatio(jdbc.queryForList(
                "SELECT IFNULL(progress, '未填写') AS name, COUNT(*) AS value "
                        + "FROM audit_issue GROUP BY progress ORDER BY value DESC")));
        data.put("rectifyTypes", withRatio(jdbc.queryForList(
                "SELECT IFNULL(rectify_type, '未分类') AS name, COUNT(*) AS value "
                        + "FROM audit_issue GROUP BY rectify_type ORDER BY value DESC")));
        data.put("unitStats", jdbc.queryForList(
                "SELECT IFNULL(unit_name, '未指定') AS name, COUNT(*) AS total, "
                        + "SUM(status = '已销号') AS closed, "
                        + "SUM(status <> '已销号' AND due_date < CURDATE()) AS overdue "
                        + "FROM audit_issue GROUP BY unit_name ORDER BY total DESC LIMIT 8"));

        // ---------------------------------------------------------- 年度整改趋势
        data.put("trend", jdbc.queryForList(
                "SELECT p.audit_year AS label, COUNT(i.id) AS total, "
                        + "IFNULL(SUM(i.status = '已销号'), 0) AS closed, "
                        + "IFNULL(SUM(i.status <> '已销号' AND i.due_date < CURDATE()), 0) AS overdue "
                        + "FROM audit_project p LEFT JOIN audit_issue i ON i.project_id = p.id "
                        + "GROUP BY p.audit_year ORDER BY p.audit_year"));

        // ---------------------------------------------------------- 提醒
        long keepGoing = count("SELECT COUNT(*) FROM audit_issue WHERE status <> '已销号' "
                + "AND (progress = '持续整改' OR due_date IS NULL)");
        Map<String, Object> reminders = new HashMap<>();
        reminders.put("keepGoing", keepGoing);
        reminders.put("traceInspect", count("SELECT COUNT(*) FROM inspect_task WHERE status IN ('待检查','检查中') "
                + "AND plan_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)"));
        reminders.put("dueSoon", count("SELECT COUNT(*) FROM audit_issue WHERE status <> '已销号' "
                + "AND due_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)"));
        reminders.put("overdue", overdueCount);
        data.put("reminders", reminders);

        return data;
    }

    /** 按单位维度的检查发现分布（检查模块看板用）。 */
    public Map<String, Object> inspectOverview() {
        Map<String, Object> data = new HashMap<>();
        data.put("typeStats", jdbc.queryForList(
                "SELECT IFNULL(inspect_type, '其他') AS name, COUNT(*) AS value "
                        + "FROM inspect_task GROUP BY inspect_type ORDER BY value DESC"));
        data.put("severityStats", jdbc.queryForList(
                "SELECT IFNULL(severity, '一般') AS name, COUNT(*) AS value "
                        + "FROM inspect_finding GROUP BY severity ORDER BY value DESC"));
        data.put("statusStats", jdbc.queryForList(
                "SELECT IFNULL(status, '未整改') AS name, COUNT(*) AS value "
                        + "FROM inspect_finding GROUP BY status ORDER BY value DESC"));
        return data;
    }

    /** 用问题明细回填某个项目的统计值。 */
    public void refreshProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        jdbc.update("UPDATE audit_project p SET "
                + "issue_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id),"
                + "closed_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id AND i.status = '已销号'),"
                + "overdue_count = (SELECT COUNT(*) FROM audit_issue i WHERE i.project_id = p.id "
                + "  AND i.status <> '已销号' AND i.due_date < CURDATE()),"
                + "amount = IFNULL((SELECT SUM(i.amount) FROM audit_issue i WHERE i.project_id = p.id), 0) "
                + "WHERE p.id = ?", projectId);
        jdbc.update("UPDATE audit_project SET status = CASE "
                + "WHEN issue_count = 0 THEN status "
                + "WHEN closed_count >= issue_count THEN '已完成' ELSE '整改中' END "
                + "WHERE id = ?", projectId);
    }

    /** 用发现明细回填某个检查任务的统计值。 */
    public void refreshTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        jdbc.update("UPDATE inspect_task t SET "
                + "finding_count = (SELECT COUNT(*) FROM inspect_finding f WHERE f.task_id = t.id),"
                + "rectified_count = (SELECT COUNT(*) FROM inspect_finding f WHERE f.task_id = t.id AND f.status = '已整改') "
                + "WHERE t.id = ?", taskId);
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
