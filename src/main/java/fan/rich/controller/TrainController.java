package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.TrainCourse;
import fan.rich.entity.TrainEnroll;
import fan.rich.entity.TrainExam;
import fan.rich.entity.TrainPlan;
import fan.rich.entity.TrainScore;
import fan.rich.service.TrainCourseService;
import fan.rich.service.TrainEnrollService;
import fan.rich.service.TrainExamService;
import fan.rich.service.TrainPlanService;
import fan.rich.service.TrainScoreService;
import fan.rich.service.TrainStatsService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 培训模块接口：课程课件、培训计划、参训记录、考试与成绩，以及培训总览统计。
 */
@RestController
@RequestMapping("/api/train")
public class TrainController {

    private final TrainCourseService courseService;
    private final TrainPlanService planService;
    private final TrainEnrollService enrollService;
    private final TrainExamService examService;
    private final TrainScoreService scoreService;
    private final TrainStatsService statsService;

    public TrainController(TrainCourseService courseService,
                           TrainPlanService planService,
                           TrainEnrollService enrollService,
                           TrainExamService examService,
                           TrainScoreService scoreService,
                           TrainStatsService statsService) {
        this.courseService = courseService;
        this.planService = planService;
        this.enrollService = enrollService;
        this.examService = examService;
        this.scoreService = scoreService;
        this.statsService = statsService;
    }

    // ------------------------------------------------------------------ 总览

    /** 培训总览：KPI、分布、趋势、提醒、近期培训。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.overview());
    }

    /**
     * 刷新培训计划状态：到达开始日期转「进行中」，过了结束日期转「已结束」。
     */
    @PostMapping("/refresh-status")
    public R refreshStatus() {
        statsService.refreshPlanStatus();
        return R.ok();
    }

    // ------------------------------------------------------------------ 课程

    /** 课程分页列表。 */
    @GetMapping("/course/list")
    public R courseList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long limit,
                        @RequestParam(required = false) String keywords,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String courseType,
                        @RequestParam(required = false) String status) {
        LambdaQueryWrapper<TrainCourse> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(TrainCourse::getCourseNo, keywords)
                    .or().like(TrainCourse::getName, keywords)
                    .or().like(TrainCourse::getTeacher, keywords));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(TrainCourse::getCategory, category);
        }
        if (StringUtils.hasText(courseType)) {
            wrapper.eq(TrainCourse::getCourseType, courseType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainCourse::getStatus, status);
        }
        wrapper.orderByDesc(TrainCourse::getId);

        IPage<TrainCourse> result = courseService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部已发布课程，用于下拉选择。 */
    @GetMapping("/course/all")
    public R courseAll() {
        return R.ok(courseService.list(new LambdaQueryWrapper<TrainCourse>()
                .orderByDesc(TrainCourse::getId)));
    }

    /** 新增或修改课程。 */
    @PostMapping("/course/save")
    public R courseSave(@RequestBody TrainCourse course) {
        if (!StringUtils.hasText(course.getStatus())) {
            course.setStatus(TrainCourse.STATUS_DRAFT);
        }
        if (course.getLearnCount() == null) {
            course.setLearnCount(0);
        }
        if (course.getExamFlag() == null) {
            course.setExamFlag(0);
        }
        courseService.saveOrUpdate(course);
        return R.ok(course.getId());
    }

    /** 课程发布 / 下架。 */
    @PostMapping("/course/publish")
    public R coursePublish(@RequestParam Long id,
                           @RequestParam(defaultValue = "1") int published) {
        TrainCourse course = courseService.getById(id);
        if (course == null) {
            return R.fail("课程不存在");
        }
        course.setStatus(published == 1 ? TrainCourse.STATUS_PUBLISHED : TrainCourse.STATUS_OFFLINE);
        courseService.updateById(course);
        return R.ok();
    }

    /** 删除课程。 */
    @PostMapping("/course/delete")
    public R courseDelete(@RequestParam Long id) {
        courseService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 培训计划

    /** 培训计划分页列表。 */
    @GetMapping("/plan/list")
    public R planList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) Integer planYear,
                      @RequestParam(required = false) String planType,
                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<TrainPlan> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(TrainPlan::getPlanNo, keywords)
                    .or().like(TrainPlan::getName, keywords)
                    .or().like(TrainPlan::getTeacher, keywords)
                    .or().like(TrainPlan::getDept, keywords));
        }
        if (planYear != null) {
            wrapper.eq(TrainPlan::getPlanYear, planYear);
        }
        if (StringUtils.hasText(planType)) {
            wrapper.eq(TrainPlan::getPlanType, planType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainPlan::getStatus, status);
        }
        wrapper.orderByDesc(TrainPlan::getStartDate).orderByDesc(TrainPlan::getId);

        IPage<TrainPlan> result = planService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部计划，用于下拉选择。 */
    @GetMapping("/plan/all")
    public R planAll() {
        return R.ok(planService.list(new LambdaQueryWrapper<TrainPlan>()
                .orderByDesc(TrainPlan::getStartDate)));
    }

    /** 新增或修改培训计划。 */
    @PostMapping("/plan/save")
    public R planSave(@RequestBody TrainPlan plan) {
        normalizePlan(plan);
        planService.saveOrUpdate(plan);
        statsService.refreshPlan(plan.getId());
        return R.ok(plan.getId());
    }

    /** 结束培训：状态置已结束并回填统计。 */
    @PostMapping("/plan/finish")
    public R planFinish(@RequestParam Long id) {
        TrainPlan plan = planService.getById(id);
        if (plan == null) {
            return R.fail("培训计划不存在");
        }
        plan.setStatus(TrainPlan.STATUS_DONE);
        if (plan.getEndDate() == null) {
            plan.setEndDate(LocalDate.now());
        }
        planService.updateById(plan);
        statsService.refreshPlan(id);
        return R.ok();
    }

    /** 删除培训计划（同时删除其下参训记录）。 */
    @PostMapping("/plan/delete")
    public R planDelete(@RequestParam Long id) {
        enrollService.remove(new LambdaQueryWrapper<TrainEnroll>().eq(TrainEnroll::getPlanId, id));
        planService.removeById(id);
        return R.ok();
    }

    /**
     * 培训计划入库前统一口径：
     * <ul>
     *     <li>未填状态按日期推导：已过期→已结束、进行中→进行中、否则报名中；</li>
     *     <li>年度缺省取开始日期所在年；</li>
     *     <li>课程名 / 考试名按 ID 回填。</li>
     * </ul>
     */
    private void normalizePlan(TrainPlan plan) {
        if (plan.getPlanYear() == null && plan.getStartDate() != null) {
            plan.setPlanYear(plan.getStartDate().getYear());
        }
        if (!StringUtils.hasText(plan.getStatus())) {
            LocalDate today = LocalDate.now();
            LocalDate start = plan.getStartDate();
            LocalDate end = plan.getEndDate() == null ? start : plan.getEndDate();
            if (end != null && end.isBefore(today)) {
                plan.setStatus(TrainPlan.STATUS_DONE);
            } else if (start != null && !start.isAfter(today)) {
                plan.setStatus(TrainPlan.STATUS_RUNNING);
            } else {
                plan.setStatus(TrainPlan.STATUS_ENROLL);
            }
        }
        if (plan.getCourseId() != null && !StringUtils.hasText(plan.getCourseName())) {
            TrainCourse course = courseService.getById(plan.getCourseId());
            if (course != null) {
                plan.setCourseName(course.getName());
            }
        }
        if (plan.getExamId() != null && !StringUtils.hasText(plan.getExamName())) {
            TrainExam exam = examService.getById(plan.getExamId());
            if (exam != null) {
                plan.setExamName(exam.getName());
            }
        }
        if (plan.getEnrollCount() == null) {
            plan.setEnrollCount(0);
        }
        if (plan.getPassCount() == null) {
            plan.setPassCount(0);
        }
    }

    // ------------------------------------------------------------------ 参训记录

    /** 参训记录分页列表。 */
    @GetMapping("/enroll/list")
    public R enrollList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long limit,
                        @RequestParam(required = false) Long planId,
                        @RequestParam(required = false) String dept,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) Integer pass,
                        @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<TrainEnroll> wrapper = new LambdaQueryWrapper<>();
        if (planId != null) {
            wrapper.eq(TrainEnroll::getPlanId, planId);
        }
        if (StringUtils.hasText(dept)) {
            wrapper.eq(TrainEnroll::getDept, dept);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainEnroll::getStatus, status);
        }
        if (pass != null) {
            wrapper.eq(TrainEnroll::getPass, pass);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(TrainEnroll::getStudentNo, keywords)
                    .or().like(TrainEnroll::getStudentName, keywords)
                    .or().like(TrainEnroll::getPlanName, keywords)
                    .or().like(TrainEnroll::getCertNo, keywords));
        }
        wrapper.orderByDesc(TrainEnroll::getId);

        IPage<TrainEnroll> result = enrollService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 某个计划的参训名单。 */
    @GetMapping("/enroll/by-plan")
    public R enrollByPlan(@RequestParam Long planId) {
        return R.ok(enrollService.list(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getPlanId, planId)
                .orderByAsc(TrainEnroll::getId)));
    }

    /** 新增或修改参训记录。 */
    @PostMapping("/enroll/save")
    public R enrollSave(@RequestBody TrainEnroll enroll) {
        normalizeEnroll(enroll);
        enrollService.saveOrUpdate(enroll);
        statsService.refreshPlan(enroll.getPlanId());
        if (enroll.getPlanId() != null) {
            TrainPlan plan = planService.getById(enroll.getPlanId());
            if (plan != null) {
                statsService.refreshCourse(plan.getCourseId());
            }
        }
        return R.ok(enroll.getId());
    }

    /** 批量报名：一次把多名学员加入某期培训。 */
    @PostMapping("/enroll/batch")
    public R enrollBatch(@RequestParam Long planId, @RequestBody List<TrainEnroll> list) {
        TrainPlan plan = planService.getById(planId);
        if (plan == null) {
            return R.fail("培训计划不存在");
        }
        int saved = 0;
        for (TrainEnroll enroll : list) {
            if (enroll == null || !StringUtils.hasText(enroll.getStudentName())) {
                continue;
            }
            boolean exists = enrollService.count(new LambdaQueryWrapper<TrainEnroll>()
                    .eq(TrainEnroll::getPlanId, planId)
                    .eq(TrainEnroll::getStudentName, enroll.getStudentName())) > 0;
            if (exists) {
                continue;
            }
            enroll.setId(null);
            enroll.setPlanId(planId);
            enroll.setPlanNo(plan.getPlanNo());
            enroll.setPlanName(plan.getName());
            if (enroll.getHours() == null) {
                enroll.setHours(plan.getHours());
            }
            if (enroll.getCredit() == null) {
                enroll.setCredit(plan.getCredit());
            }
            normalizeEnroll(enroll);
            enrollService.save(enroll);
            saved++;
        }
        statsService.refreshPlan(planId);
        return R.ok(saved);
    }

    /** 签到 / 取消签到。 */
    @PostMapping("/enroll/sign")
    public R enrollSign(@RequestParam Long id, @RequestParam(defaultValue = "1") int signed) {
        TrainEnroll enroll = enrollService.getById(id);
        if (enroll == null) {
            return R.fail("参训记录不存在");
        }
        enroll.setSignStatus(signed == 1 ? "已签到" : "未签到");
        enrollService.updateById(enroll);
        return R.ok();
    }

    /** 删除参训记录。 */
    @PostMapping("/enroll/delete")
    public R enrollDelete(@RequestParam Long id) {
        TrainEnroll enroll = enrollService.getById(id);
        enrollService.removeById(id);
        if (enroll != null) {
            statsService.refreshPlan(enroll.getPlanId());
        }
        return R.ok();
    }

    /**
     * 参训记录入库前统一口径：
     * <ul>
     *     <li>计划名按 ID 回填；</li>
     *     <li>成绩与及格分比较后自动判定是否合格，并联动学习状态；</li>
     *     <li>合格且未填完成日期时取今天。</li>
     * </ul>
     */
    private void normalizeEnroll(TrainEnroll enroll) {
        if (enroll.getPlanId() != null && !StringUtils.hasText(enroll.getPlanName())) {
            TrainPlan plan = planService.getById(enroll.getPlanId());
            if (plan != null) {
                enroll.setPlanNo(plan.getPlanNo());
                enroll.setPlanName(plan.getName());
                if (enroll.getHours() == null) {
                    enroll.setHours(plan.getHours());
                }
                if (enroll.getCredit() == null) {
                    enroll.setCredit(plan.getCredit());
                }
            }
        }
        if (!StringUtils.hasText(enroll.getSignStatus())) {
            enroll.setSignStatus("未签到");
        }
        // 有成绩时按关联考试的及格线判定合格与否
        Integer passScore = resolvePassScore(enroll);
        if (enroll.getScore() != null && passScore != null) {
            enroll.setPass(enroll.getScore().compareTo(BigDecimal.valueOf(passScore)) >= 0 ? 1 : 0);
        }
        if (enroll.getPass() != null && enroll.getPass() == 1 && enroll.getFinishDate() == null) {
            enroll.setFinishDate(LocalDate.now());
        }
        if (!StringUtils.hasText(enroll.getStatus())) {
            enroll.setStatus(TrainEnroll.STATUS_NOT_START);
        }
        if (enroll.getScore() != null) {
            enroll.setStatus(enroll.getPass() != null && enroll.getPass() == 1
                    ? TrainEnroll.STATUS_FINISHED : TrainEnroll.STATUS_FAILED);
        } else if (TrainEnroll.STATUS_FINISHED.equals(enroll.getStatus()) && enroll.getFinishDate() == null) {
            enroll.setFinishDate(LocalDate.now());
        }
        if (enroll.getPass() == null) {
            enroll.setPass(0);
        }
    }

    /** 取参训记录所属培训的及格分，取不到时按 60 分兜底。 */
    private Integer resolvePassScore(TrainEnroll enroll) {
        if (enroll.getPlanId() != null) {
            TrainPlan plan = planService.getById(enroll.getPlanId());
            if (plan != null && plan.getExamId() != null) {
                TrainExam exam = examService.getById(plan.getExamId());
                if (exam != null && exam.getPassScore() != null) {
                    return exam.getPassScore();
                }
            }
        }
        return 60;
    }

    // ------------------------------------------------------------------ 考试

    /** 考试分页列表。 */
    @GetMapping("/exam/list")
    public R examList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) String examType,
                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<TrainExam> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(TrainExam::getExamNo, keywords)
                    .or().like(TrainExam::getName, keywords)
                    .or().like(TrainExam::getCourseName, keywords));
        }
        if (StringUtils.hasText(examType)) {
            wrapper.eq(TrainExam::getExamType, examType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainExam::getStatus, status);
        }
        wrapper.orderByDesc(TrainExam::getExamDate).orderByDesc(TrainExam::getId);

        IPage<TrainExam> result = examService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部考试，用于下拉选择。 */
    @GetMapping("/exam/all")
    public R examAll() {
        return R.ok(examService.list(new LambdaQueryWrapper<TrainExam>()
                .orderByDesc(TrainExam::getExamDate)));
    }

    /** 新增或修改考试。 */
    @PostMapping("/exam/save")
    public R examSave(@RequestBody TrainExam exam) {
        if (!StringUtils.hasText(exam.getStatus())) {
            exam.setStatus(TrainExam.STATUS_DRAFT);
        }
        if (exam.getTotalScore() == null) {
            exam.setTotalScore(100);
        }
        if (exam.getPassScore() == null) {
            exam.setPassScore(60);
        }
        if (exam.getCourseId() != null && !StringUtils.hasText(exam.getCourseName())) {
            TrainCourse course = courseService.getById(exam.getCourseId());
            if (course != null) {
                exam.setCourseName(course.getName());
            }
        }
        examService.saveOrUpdate(exam);
        statsService.refreshExam(exam.getId());
        return R.ok(exam.getId());
    }

    /** 删除考试（同时删除成绩）。 */
    @PostMapping("/exam/delete")
    public R examDelete(@RequestParam Long id) {
        scoreService.remove(new LambdaQueryWrapper<TrainScore>().eq(TrainScore::getExamId, id));
        examService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 成绩

    /** 成绩分页列表。 */
    @GetMapping("/score/list")
    public R scoreList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "10") long limit,
                       @RequestParam(required = false) Long examId,
                       @RequestParam(required = false) Integer pass,
                       @RequestParam(required = false) String dept,
                       @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<TrainScore> wrapper = new LambdaQueryWrapper<>();
        if (examId != null) {
            wrapper.eq(TrainScore::getExamId, examId);
        }
        if (pass != null) {
            wrapper.eq(TrainScore::getPass, pass);
        }
        if (StringUtils.hasText(dept)) {
            wrapper.eq(TrainScore::getDept, dept);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(TrainScore::getStudentNo, keywords)
                    .or().like(TrainScore::getStudentName, keywords)
                    .or().like(TrainScore::getExamName, keywords));
        }
        wrapper.orderByDesc(TrainScore::getExamDate).orderByDesc(TrainScore::getId);

        IPage<TrainScore> result = scoreService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 某场考试的成绩明细。 */
    @GetMapping("/score/by-exam")
    public R scoreByExam(@RequestParam Long examId) {
        return R.ok(scoreService.list(new LambdaQueryWrapper<TrainScore>()
                .eq(TrainScore::getExamId, examId)
                .orderByDesc(TrainScore::getScore)));
    }

    /** 新增或修改成绩。 */
    @PostMapping("/score/save")
    public R scoreSave(@RequestBody TrainScore score) {
        normalizeScore(score);
        scoreService.saveOrUpdate(score);
        statsService.refreshExam(score.getExamId());
        // 成绩同步回参训记录，保证「培训—考试」口径一致
        syncEnrollScore(score);
        return R.ok(score.getId());
    }

    /** 删除成绩。 */
    @PostMapping("/score/delete")
    public R scoreDelete(@RequestParam Long id) {
        TrainScore score = scoreService.getById(id);
        scoreService.removeById(id);
        if (score != null) {
            statsService.refreshExam(score.getExamId());
        }
        return R.ok();
    }

    /** 成绩入库前统一口径：考试名回填、按及格线判定、补考次数自动累计。 */
    private void normalizeScore(TrainScore score) {
        if (score.getExamId() != null && !StringUtils.hasText(score.getExamName())) {
            TrainExam exam = examService.getById(score.getExamId());
            if (exam != null) {
                score.setExamNo(exam.getExamNo());
                score.setExamName(exam.getName());
                if (score.getExamDate() == null) {
                    score.setExamDate(exam.getExamDate());
                }
            }
        }
        TrainExam exam = score.getExamId() == null ? null : examService.getById(score.getExamId());
        int passScore = exam != null && exam.getPassScore() != null ? exam.getPassScore() : 60;
        score.setPass(score.getScore() != null
                && score.getScore().compareTo(BigDecimal.valueOf(passScore)) >= 0 ? 1 : 0);
        if (score.getTimes() == null) {
            Long done = scoreService.count(new LambdaQueryWrapper<TrainScore>()
                    .eq(TrainScore::getExamId, score.getExamId())
                    .eq(TrainScore::getStudentName, score.getStudentName()));
            score.setTimes(done.intValue() + 1);
        }
    }

    /** 把考试成绩回写到对应培训计划的参训记录上。 */
    private void syncEnrollScore(TrainScore score) {
        if (score.getExamId() == null || score.getStudentName() == null) {
            return;
        }
        List<TrainPlan> plans = planService.list(new LambdaQueryWrapper<TrainPlan>()
                .eq(TrainPlan::getExamId, score.getExamId()));
        for (TrainPlan plan : plans) {
            TrainEnroll enroll = enrollService.getOne(new LambdaQueryWrapper<TrainEnroll>()
                    .eq(TrainEnroll::getPlanId, plan.getId())
                    .eq(TrainEnroll::getStudentName, score.getStudentName())
                    .last("LIMIT 1"));
            if (enroll == null) {
                continue;
            }
            enroll.setScore(score.getScore());
            enroll.setPass(score.getPass());
            enroll.setStatus(score.getPass() == 1 ? TrainEnroll.STATUS_FINISHED : TrainEnroll.STATUS_FAILED);
            if (score.getPass() == 1 && enroll.getFinishDate() == null) {
                enroll.setFinishDate(score.getExamDate() == null ? LocalDate.now() : score.getExamDate());
            }
            enrollService.updateById(enroll);
            statsService.refreshPlan(plan.getId());
        }
    }
}
