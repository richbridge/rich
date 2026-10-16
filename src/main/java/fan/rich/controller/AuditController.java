package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.AuditIssue;
import fan.rich.entity.AuditProject;
import fan.rich.entity.AuditUnit;
import fan.rich.service.AuditIssueService;
import fan.rich.service.AuditProjectService;
import fan.rich.service.AuditStatsService;
import fan.rich.service.AuditUnitService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 审计模块接口：审计项目、审计问题（整改台账）、被审计单位，以及整改看板统计。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditProjectService projectService;
    private final AuditIssueService issueService;
    private final AuditUnitService unitService;
    private final AuditStatsService statsService;

    public AuditController(AuditProjectService projectService,
                           AuditIssueService issueService,
                           AuditUnitService unitService,
                           AuditStatsService statsService) {
        this.projectService = projectService;
        this.issueService = issueService;
        this.unitService = unitService;
        this.statsService = statsService;
    }

    // ------------------------------------------------------------------ 看板统计

    /** 审计整改总览：KPI、分布、趋势、提醒。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.overview());
    }

    /**
     * 批量刷新逾期状态：到期仍未销号的问题标记为「逾期」，并回填项目统计。
     *
     * <p>整改期限是随时间推进自然变成逾期的，不需要人工改状态，看板加载前调一次即可。
     */
    @PostMapping("/refresh-status")
    public R refreshStatus() {
        issueService.lambdaUpdate()
                .set(AuditIssue::getStatus, AuditIssue.STATUS_OVERDUE)
                .eq(AuditIssue::getStatus, AuditIssue.STATUS_OPEN)
                .lt(AuditIssue::getDueDate, LocalDate.now())
                .update();
        for (AuditProject project : projectService.list()) {
            statsService.refreshProject(project.getId());
        }
        return R.ok();
    }

    // ------------------------------------------------------------------ 审计项目

    /** 项目分页列表。 */
    @GetMapping("/project/list")
    public R projectList(@RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "10") long limit,
                         @RequestParam(required = false) String keywords,
                         @RequestParam(required = false) Integer auditYear,
                         @RequestParam(required = false) String auditType,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer archived) {
        LambdaQueryWrapper<AuditProject> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(AuditProject::getProjectNo, keywords)
                    .or().like(AuditProject::getName, keywords)
                    .or().like(AuditProject::getLeader, keywords)
                    .or().like(AuditProject::getUnitName, keywords));
        }
        if (auditYear != null) {
            wrapper.eq(AuditProject::getAuditYear, auditYear);
        }
        if (StringUtils.hasText(auditType)) {
            wrapper.eq(AuditProject::getAuditType, auditType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AuditProject::getStatus, status);
        }
        if (archived != null) {
            wrapper.eq(AuditProject::getArchived, archived);
        }
        wrapper.orderByDesc(AuditProject::getAuditDate).orderByDesc(AuditProject::getId);

        IPage<AuditProject> result = projectService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部项目，用于下拉选择。 */
    @GetMapping("/project/all")
    public R projectAll() {
        List<AuditProject> list = projectService.list(new LambdaQueryWrapper<AuditProject>()
                .orderByDesc(AuditProject::getAuditDate));
        return R.ok(list);
    }

    /** 新增或修改项目。 */
    @PostMapping("/project/save")
    public R projectSave(@RequestBody AuditProject project) {
        if (project.getStatus() == null || !StringUtils.hasText(project.getStatus())) {
            project.setStatus(AuditProject.STATUS_RECTIFYING);
        }
        if (project.getArchived() == null) {
            project.setArchived(0);
        }
        if (project.getUnitId() != null && !StringUtils.hasText(project.getUnitName())) {
            AuditUnit unit = unitService.getById(project.getUnitId());
            if (unit != null) {
                project.setUnitName(unit.getName());
            }
        }
        projectService.saveOrUpdate(project);
        statsService.refreshProject(project.getId());
        return R.ok(project.getId());
    }

    /** 项目归档 / 取消归档。 */
    @PostMapping("/project/archive")
    public R projectArchive(@RequestParam Long id, @RequestParam(defaultValue = "1") int archived) {
        AuditProject project = projectService.getById(id);
        if (project == null) {
            return R.fail("项目不存在");
        }
        project.setArchived(archived);
        projectService.updateById(project);
        return R.ok();
    }

    /** 删除项目（同时删除其下问题）。 */
    @PostMapping("/project/delete")
    public R projectDelete(@RequestParam Long id) {
        issueService.remove(new LambdaQueryWrapper<AuditIssue>().eq(AuditIssue::getProjectId, id));
        projectService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 审计问题

    /** 问题分页列表。 */
    @GetMapping("/issue/list")
    public R issueList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "10") long limit,
                       @RequestParam(required = false) Long projectId,
                       @RequestParam(required = false) Long unitId,
                       @RequestParam(required = false) String issueType,
                       @RequestParam(required = false) String nature,
                       @RequestParam(required = false) String rectifyType,
                       @RequestParam(required = false) String progress,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<AuditIssue> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(AuditIssue::getProjectId, projectId);
        }
        if (unitId != null) {
            wrapper.eq(AuditIssue::getUnitId, unitId);
        }
        if (StringUtils.hasText(issueType)) {
            wrapper.eq(AuditIssue::getIssueType, issueType);
        }
        if (StringUtils.hasText(nature)) {
            wrapper.eq(AuditIssue::getNature, nature);
        }
        if (StringUtils.hasText(rectifyType)) {
            wrapper.eq(AuditIssue::getRectifyType, rectifyType);
        }
        if (StringUtils.hasText(progress)) {
            wrapper.eq(AuditIssue::getProgress, progress);
        }
        if (StringUtils.hasText(status)) {
            if (AuditIssue.STATUS_OVERDUE.equals(status)) {
                // 逾期是动态状态：未销号且已过整改期限
                wrapper.ne(AuditIssue::getStatus, AuditIssue.STATUS_CLOSED)
                        .lt(AuditIssue::getDueDate, LocalDate.now());
            } else {
                wrapper.eq(AuditIssue::getStatus, status);
            }
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(AuditIssue::getIssueNo, keywords)
                    .or().like(AuditIssue::getDescription, keywords)
                    .or().like(AuditIssue::getSuggestion, keywords)
                    .or().like(AuditIssue::getRectifyPlan, keywords)
                    .or().like(AuditIssue::getProjectName, keywords)
                    .or().like(AuditIssue::getOwner, keywords));
        }
        wrapper.orderByAsc(AuditIssue::getProjectId).orderByAsc(AuditIssue::getIssueNo)
                .orderByAsc(AuditIssue::getId);

        IPage<AuditIssue> result = issueService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改问题。 */
    @PostMapping("/issue/save")
    public R issueSave(@RequestBody AuditIssue issue) {
        normalizeIssue(issue);
        issueService.saveOrUpdate(issue);
        statsService.refreshProject(issue.getProjectId());
        return R.ok(issue.getId());
    }

    /** 问题销号 / 取消销号。 */
    @PostMapping("/issue/close")
    public R issueClose(@RequestParam Long id, @RequestParam(defaultValue = "1") int closed) {
        AuditIssue issue = issueService.getById(id);
        if (issue == null) {
            return R.fail("问题不存在");
        }
        if (closed == 1) {
            issue.setStatus(AuditIssue.STATUS_CLOSED);
            issue.setProgress(AuditIssue.PROGRESS_DONE);
            if (issue.getClosedDate() == null) {
                issue.setClosedDate(LocalDate.now());
            }
        } else {
            issue.setClosedDate(null);
            if (!StringUtils.hasText(issue.getProgress())
                    || AuditIssue.PROGRESS_DONE.equals(issue.getProgress())) {
                issue.setProgress(AuditIssue.PROGRESS_DOING);
            }
            normalizeIssue(issue);
        }
        issueService.updateById(issue);
        statsService.refreshProject(issue.getProjectId());
        return R.ok();
    }

    /** 删除问题。 */
    @PostMapping("/issue/delete")
    public R issueDelete(@RequestParam Long id) {
        AuditIssue issue = issueService.getById(id);
        issueService.removeById(id);
        if (issue != null) {
            statsService.refreshProject(issue.getProjectId());
        }
        return R.ok();
    }

    /**
     * 问题入库前统一口径：
     * <ul>
     *     <li>未销号且已过整改期限 → 逾期；</li>
     *     <li>已销号且未填销号日期 → 取今天；</li>
     *     <li>项目名 / 单位名按 ID 回填，保证列表与统计口径一致。</li>
     * </ul>
     */
    private void normalizeIssue(AuditIssue issue) {
        if (!StringUtils.hasText(issue.getStatus())) {
            issue.setStatus(AuditIssue.STATUS_OPEN);
        }
        if (!AuditIssue.STATUS_CLOSED.equals(issue.getStatus())
                && issue.getDueDate() != null
                && issue.getDueDate().isBefore(LocalDate.now())) {
            issue.setStatus(AuditIssue.STATUS_OVERDUE);
        }
        if (AuditIssue.STATUS_CLOSED.equals(issue.getStatus()) && issue.getClosedDate() == null) {
            issue.setClosedDate(LocalDate.now());
        }
        if (issue.getProjectId() != null && !StringUtils.hasText(issue.getProjectName())) {
            AuditProject project = projectService.getById(issue.getProjectId());
            if (project != null) {
                issue.setProjectName(project.getName());
            }
        }
        if (issue.getUnitId() != null && !StringUtils.hasText(issue.getUnitName())) {
            AuditUnit unit = unitService.getById(issue.getUnitId());
            if (unit != null) {
                issue.setUnitName(unit.getName());
            }
        }
        if (issue.getPunishCount() == null) {
            issue.setPunishCount(0);
        }
        // 整改情况未填时按状态兜底，保证台账列不空
        if (!StringUtils.hasText(issue.getProgress())) {
            issue.setProgress(AuditIssue.STATUS_CLOSED.equals(issue.getStatus())
                    ? AuditIssue.PROGRESS_DONE : AuditIssue.PROGRESS_DOING);
        }
        if (!StringUtils.hasText(issue.getDeadlineText()) && issue.getDueDate() != null) {
            issue.setDeadlineText(issue.getDueDate().toString());
        }
    }

    // ------------------------------------------------------------------ 被审计单位

    /** 单位分页列表。 */
    @GetMapping("/unit/list")
    public R unitList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) String unitType,
                      @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<AuditUnit> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(AuditUnit::getUnitNo, keywords)
                    .or().like(AuditUnit::getName, keywords)
                    .or().like(AuditUnit::getLeader, keywords));
        }
        if (StringUtils.hasText(unitType)) {
            wrapper.eq(AuditUnit::getUnitType, unitType);
        }
        if (status != null) {
            wrapper.eq(AuditUnit::getStatus, status);
        }
        wrapper.orderByAsc(AuditUnit::getId);

        IPage<AuditUnit> result = unitService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部正常单位，用于下拉选择。 */
    @GetMapping("/unit/all")
    public R unitAll() {
        return R.ok(unitService.list(new LambdaQueryWrapper<AuditUnit>()
                .eq(AuditUnit::getStatus, 1)
                .orderByAsc(AuditUnit::getId)));
    }

    /** 新增或修改单位。 */
    @PostMapping("/unit/save")
    public R unitSave(@RequestBody AuditUnit unit) {
        if (unit.getStatus() == null) {
            unit.setStatus(1);
        }
        unitService.saveOrUpdate(unit);
        return R.ok(unit.getId());
    }

    /** 删除单位。 */
    @PostMapping("/unit/delete")
    public R unitDelete(@RequestParam Long id) {
        unitService.removeById(id);
        return R.ok();
    }
}
