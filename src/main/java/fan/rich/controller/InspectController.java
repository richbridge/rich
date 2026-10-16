package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.AuditProject;
import fan.rich.entity.InspectFinding;
import fan.rich.entity.InspectTask;
import fan.rich.service.AuditProjectService;
import fan.rich.service.AuditStatsService;
import fan.rich.service.InspectFindingService;
import fan.rich.service.InspectTaskService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 检查模块接口：检查任务（跟踪检查 / 专项检查 / 日常检查 / 整改核查）与检查发现问题。
 */
@RestController
@RequestMapping("/api/inspect")
public class InspectController {

    private final InspectTaskService taskService;
    private final InspectFindingService findingService;
    private final AuditProjectService projectService;
    private final AuditStatsService statsService;

    public InspectController(InspectTaskService taskService,
                             InspectFindingService findingService,
                             AuditProjectService projectService,
                             AuditStatsService statsService) {
        this.taskService = taskService;
        this.findingService = findingService;
        this.projectService = projectService;
        this.statsService = statsService;
    }

    // ------------------------------------------------------------------ 统计

    /** 检查模块统计：按类型 / 严重程度 / 整改状态的分布。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.inspectOverview());
    }

    // ------------------------------------------------------------------ 检查任务

    /** 任务分页列表。 */
    @GetMapping("/task/list")
    public R taskList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) String inspectType,
                      @RequestParam(required = false) String status,
                      @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<InspectTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(InspectTask::getTaskNo, keywords)
                    .or().like(InspectTask::getName, keywords)
                    .or().like(InspectTask::getUnitName, keywords)
                    .or().like(InspectTask::getInspector, keywords));
        }
        if (StringUtils.hasText(inspectType)) {
            wrapper.eq(InspectTask::getInspectType, inspectType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(InspectTask::getStatus, status);
        }
        if (projectId != null) {
            wrapper.eq(InspectTask::getProjectId, projectId);
        }
        wrapper.orderByDesc(InspectTask::getPlanDate).orderByDesc(InspectTask::getId);

        IPage<InspectTask> result = taskService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部任务，用于下拉选择。 */
    @GetMapping("/task/all")
    public R taskAll() {
        List<InspectTask> list = taskService.list(new LambdaQueryWrapper<InspectTask>()
                .orderByDesc(InspectTask::getPlanDate));
        return R.ok(list);
    }

    /** 新增或修改任务。 */
    @PostMapping("/task/save")
    public R taskSave(@RequestBody InspectTask task) {
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus(InspectTask.STATUS_PENDING);
        }
        if (task.getProjectId() != null && !StringUtils.hasText(task.getProjectName())) {
            AuditProject project = projectService.getById(task.getProjectId());
            if (project != null) {
                task.setProjectName(project.getName());
            }
        }
        taskService.saveOrUpdate(task);
        return R.ok(task.getId());
    }

    /** 完成任务：状态置为已完成，未填实际日期时取今天。 */
    @PostMapping("/task/finish")
    public R taskFinish(@RequestParam Long id) {
        InspectTask task = taskService.getById(id);
        if (task == null) {
            return R.fail("任务不存在");
        }
        task.setStatus(InspectTask.STATUS_DONE);
        if (task.getActualDate() == null) {
            task.setActualDate(LocalDate.now());
        }
        taskService.updateById(task);
        statsService.refreshTask(id);
        return R.ok();
    }

    /** 删除任务（同时删除其下发现的问题）。 */
    @PostMapping("/task/delete")
    public R taskDelete(@RequestParam Long id) {
        findingService.remove(new LambdaQueryWrapper<InspectFinding>()
                .eq(InspectFinding::getTaskId, id));
        taskService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 检查发现问题

    /** 某个任务下的发现问题；不传 taskId 时返回全部。 */
    @GetMapping("/finding/list")
    public R findingList(@RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long limit,
                         @RequestParam(required = false) Long taskId,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String severity,
                         @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<InspectFinding> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(InspectFinding::getTaskId, taskId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(InspectFinding::getStatus, status);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(InspectFinding::getSeverity, severity);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(InspectFinding::getTitle, keywords)
                    .or().like(InspectFinding::getUnitName, keywords)
                    .or().like(InspectFinding::getOwner, keywords));
        }
        wrapper.orderByDesc(InspectFinding::getId);

        IPage<InspectFinding> result = findingService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改发现问题。 */
    @PostMapping("/finding/save")
    public R findingSave(@RequestBody InspectFinding finding) {
        if (!StringUtils.hasText(finding.getStatus())) {
            finding.setStatus(InspectFinding.STATUS_OPEN);
        }
        if (finding.getTaskId() != null) {
            InspectTask task = taskService.getById(finding.getTaskId());
            if (task != null) {
                finding.setTaskNo(task.getTaskNo());
                if (finding.getProjectId() == null) {
                    finding.setProjectId(task.getProjectId());
                }
                if (!StringUtils.hasText(finding.getUnitName())) {
                    finding.setUnitName(task.getUnitName());
                }
            }
        }
        findingService.saveOrUpdate(finding);
        statsService.refreshTask(finding.getTaskId());
        return R.ok(finding.getId());
    }

    /** 删除发现问题。 */
    @PostMapping("/finding/delete")
    public R findingDelete(@RequestParam Long id) {
        InspectFinding finding = findingService.getById(id);
        findingService.removeById(id);
        if (finding != null) {
            statsService.refreshTask(finding.getTaskId());
        }
        return R.ok();
    }
}
