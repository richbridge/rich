package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.SalaryAlloc;
import fan.rich.entity.SalaryDept;
import fan.rich.entity.SalaryDetail;
import fan.rich.entity.SalaryReport;
import fan.rich.service.SalaryAllocService;
import fan.rich.service.SalaryDeptService;
import fan.rich.service.SalaryDetailService;
import fan.rich.service.SalaryReportService;
import fan.rich.service.SalaryStatsService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工资模块接口：工资下拨、工资上报、工资明细与部门维护，以及工资总览统计。
 *
 * <p>业务流程：上级单位下拨工资总额（{@code salary_alloc}）→ 各部门造表上报
 * （{@code salary_report}）→ 财务科审核、发放 → 实发金额回填下拨单结余。
 */
@RestController
@RequestMapping("/api/salary")
public class SalaryController {

    /** 本公司（下拨接收单位）。 */
    private static final String OUR_UNIT = "招远市金宝黄金矿业有限公司";

    private final SalaryDeptService deptService;
    private final SalaryAllocService allocService;
    private final SalaryReportService reportService;
    private final SalaryDetailService detailService;
    private final SalaryStatsService statsService;

    public SalaryController(SalaryDeptService deptService,
                            SalaryAllocService allocService,
                            SalaryReportService reportService,
                            SalaryDetailService detailService,
                            SalaryStatsService statsService) {
        this.deptService = deptService;
        this.allocService = allocService;
        this.reportService = reportService;
        this.detailService = detailService;
        this.statsService = statsService;
    }

    // ------------------------------------------------------------------ 总览

    /** 工资总览：KPI、当前期间、月度趋势、分布、提醒。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.overview());
    }

    /** 重算所有下拨单的已发放金额、结余与状态。 */
    @PostMapping("/refresh-status")
    public R refreshStatus() {
        statsService.refreshAll();
        return R.ok();
    }

    // ------------------------------------------------------------------ 部门

    /** 部门分页列表。 */
    @GetMapping("/dept/list")
    public R deptList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long limit,
                      @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<SalaryDept> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(SalaryDept::getDeptName, keywords)
                    .or().like(SalaryDept::getDeptCode, keywords)
                    .or().like(SalaryDept::getLeader, keywords);
        }
        wrapper.orderByAsc(SalaryDept::getDeptCode);
        IPage<SalaryDept> result = deptService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部部门，用于下拉选择。 */
    @GetMapping("/dept/all")
    public R deptAll() {
        return R.ok(deptService.list(new LambdaQueryWrapper<SalaryDept>()
                .orderByAsc(SalaryDept::getDeptCode)));
    }

    /** 新增或修改部门。 */
    @PostMapping("/dept/save")
    public R deptSave(@RequestBody SalaryDept dept) {
        if (!StringUtils.hasText(dept.getStatus())) {
            dept.setStatus(SalaryDept.STATUS_ON);
        }
        if (dept.getHeadcount() == null) {
            dept.setHeadcount(0);
        }
        deptService.saveOrUpdate(dept);
        return R.ok(dept.getId());
    }

    /** 删除部门。 */
    @PostMapping("/dept/delete")
    public R deptDelete(@RequestParam Long id) {
        deptService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 工资下拨

    /** 下拨单分页列表（按部门）。 */
    @GetMapping("/alloc/list")
    public R allocList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "10") long limit,
                       @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       @RequestParam(required = false) String deptName,
                       @RequestParam(required = false) String allocType,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<SalaryAlloc> wrapper = new LambdaQueryWrapper<>();
        if (year != null) {
            wrapper.eq(SalaryAlloc::getPeriodYear, year);
        }
        if (month != null) {
            wrapper.eq(SalaryAlloc::getPeriodMonth, month);
        }
        if (StringUtils.hasText(deptName)) {
            wrapper.eq(SalaryAlloc::getDeptName, deptName);
        }
        if (StringUtils.hasText(allocType)) {
            wrapper.eq(SalaryAlloc::getAllocType, allocType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalaryAlloc::getStatus, status);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(SalaryAlloc::getAllocNo, keywords)
                    .or().like(SalaryAlloc::getVoucherNo, keywords)
                    .or().like(SalaryAlloc::getSourceUnit, keywords)
                    .or().like(SalaryAlloc::getDeptName, keywords)
                    .or().like(SalaryAlloc::getHandler, keywords));
        }
        wrapper.orderByDesc(SalaryAlloc::getPeriodYear)
                .orderByDesc(SalaryAlloc::getPeriodMonth)
                .orderByAsc(SalaryAlloc::getAllocNo);
        IPage<SalaryAlloc> result = allocService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /**
     * 按部门批量生成下拨单：把期间核定总额与下拨金额分解到各核算部门。
     *
     * @param year       所属年度
     * @param month      所属月份
     * @param approved   核定工资总额
     * @param amount     实际下拨金额
     * @param mode       分摊方式：quota 按部门工资定额 / headcount 按在册人数 / avg 平均分摊
     */
    @PostMapping("/alloc/generate-by-dept")
    public R allocGenerateByDept(@RequestParam int year,
                                 @RequestParam int month,
                                 @RequestParam BigDecimal approved,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(required = false, defaultValue = "月度工资总额") String allocType,
                                 @RequestParam(required = false) String sourceUnit,
                                 @RequestParam(required = false) String voucherNo,
                                 @RequestParam(required = false) String handler,
                                 @RequestParam(required = false) String allocDate,
                                 @RequestParam(required = false, defaultValue = "quota") String mode) {
        if (approved == null || approved.signum() <= 0) {
            return R.fail("请填写核定工资总额");
        }
        LocalDate date = StringUtils.hasText(allocDate) ? LocalDate.parse(allocDate) : LocalDate.now();
        int count = statsService.generateDeptAllocs(year, month, allocType,
                StringUtils.hasText(sourceUnit) ? sourceUnit : "招金有色金属集团有限公司",
                OUR_UNIT, approved, amount == null ? approved : amount,
                date, voucherNo, handler, mode);
        return R.ok(count);
    }

    /** 全部下拨单，用于上报单关联选择。 */
    @GetMapping("/alloc/all")
    public R allocAll() {
        return R.ok(allocService.list(new LambdaQueryWrapper<SalaryAlloc>()
                .orderByDesc(SalaryAlloc::getPeriodYear)
                .orderByDesc(SalaryAlloc::getPeriodMonth)));
    }

    /** 新增或修改下拨单。 */
    @PostMapping("/alloc/save")
    public R allocSave(@RequestBody SalaryAlloc alloc) {
        normalizeAlloc(alloc);
        allocService.saveOrUpdate(alloc);
        statsService.refreshAlloc(alloc.getId());
        return R.ok(alloc.getId());
    }

    /**
     * 确认下拨：状态由「待下拨」转为「已下拨」，未填下拨日期时取当天。
     */
    @PostMapping("/alloc/post")
    public R allocPost(@RequestParam Long id) {
        SalaryAlloc alloc = allocService.getById(id);
        if (alloc == null) {
            return R.fail("下拨单不存在");
        }
        alloc.setStatus(SalaryAlloc.STATUS_DONE);
        if (alloc.getAllocDate() == null) {
            alloc.setAllocDate(LocalDate.now());
        }
        allocService.updateById(alloc);
        statsService.refreshAlloc(id);
        return R.ok();
    }

    /** 结清下拨单。 */
    @PostMapping("/alloc/settle")
    public R allocSettle(@RequestParam Long id) {
        SalaryAlloc alloc = allocService.getById(id);
        if (alloc == null) {
            return R.fail("下拨单不存在");
        }
        alloc.setStatus(SalaryAlloc.STATUS_SETTLED);
        allocService.updateById(alloc);
        return R.ok();
    }

    /** 删除下拨单。 */
    @PostMapping("/alloc/delete")
    public R allocDelete(@RequestParam Long id) {
        allocService.removeById(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 工资上报

    /** 上报单分页列表。 */
    @GetMapping("/report/list")
    public R reportList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long limit,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) String deptName,
                        @RequestParam(required = false) String reportType,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) Long allocId,
                        @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<SalaryReport> wrapper = new LambdaQueryWrapper<>();
        if (year != null) {
            wrapper.eq(SalaryReport::getPeriodYear, year);
        }
        if (month != null) {
            wrapper.eq(SalaryReport::getPeriodMonth, month);
        }
        if (allocId != null) {
            wrapper.eq(SalaryReport::getAllocId, allocId);
        }
        if (StringUtils.hasText(deptName)) {
            wrapper.eq(SalaryReport::getDeptName, deptName);
        }
        if (StringUtils.hasText(reportType)) {
            wrapper.eq(SalaryReport::getReportType, reportType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalaryReport::getStatus, status);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(SalaryReport::getReportNo, keywords)
                    .or().like(SalaryReport::getDeptName, keywords)
                    .or().like(SalaryReport::getReporter, keywords)
                    .or().like(SalaryReport::getAllocNo, keywords));
        }
        wrapper.orderByDesc(SalaryReport::getPeriodYear)
                .orderByDesc(SalaryReport::getPeriodMonth)
                .orderByAsc(SalaryReport::getDeptName)
                .orderByDesc(SalaryReport::getId);
        IPage<SalaryReport> result = reportService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改上报单。 */
    @PostMapping("/report/save")
    public R reportSave(@RequestBody SalaryReport report) {
        normalizeReport(report);
        reportService.saveOrUpdate(report);
        return R.ok(report.getId());
    }

    /**
     * 提交上报：草稿 → 待审核。已发放的单据不允许再提交。
     */
    @PostMapping("/report/submit")
    public R reportSubmit(@RequestParam Long id) {
        SalaryReport report = reportService.getById(id);
        if (report == null) {
            return R.fail("上报单不存在");
        }
        if (SalaryReport.STATUS_PAID.equals(report.getStatus())) {
            return R.fail("该上报单已发放，不能重复提交");
        }
        report.setStatus(SalaryReport.STATUS_WAIT);
        if (report.getReportDate() == null) {
            report.setReportDate(LocalDate.now());
        }
        reportService.updateById(report);
        return R.ok();
    }

    /**
     * 审核上报单。
     *
     * @param id      上报单ID
     * @param passed  1 通过（→ 已审核），0 驳回（→ 已驳回）
     * @param auditor 审核人
     * @param opinion 审核意见
     */
    @PostMapping("/report/audit")
    public R reportAudit(@RequestParam Long id,
                         @RequestParam(defaultValue = "1") int passed,
                         @RequestParam(required = false) String auditor,
                         @RequestParam(required = false) String opinion) {
        SalaryReport report = reportService.getById(id);
        if (report == null) {
            return R.fail("上报单不存在");
        }
        report.setStatus(passed == 1 ? SalaryReport.STATUS_AUDITED : SalaryReport.STATUS_REJECT);
        report.setAuditor(auditor);
        report.setAuditOpinion(opinion);
        report.setAuditDate(LocalDate.now());
        reportService.updateById(report);
        statsService.refreshAlloc(report.getAllocId());
        return R.ok();
    }

    /**
     * 发放：已审核 → 已发放，并回填关联下拨单的已发放金额与结余。
     */
    @PostMapping("/report/pay")
    public R reportPay(@RequestParam Long id) {
        SalaryReport report = reportService.getById(id);
        if (report == null) {
            return R.fail("上报单不存在");
        }
        report.setStatus(SalaryReport.STATUS_PAID);
        reportService.updateById(report);
        statsService.refreshAlloc(report.getAllocId());
        return R.ok();
    }

    /** 由工资明细汇总回填上报单的人数与金额。 */
    @PostMapping("/report/summary")
    public R reportSummary(@RequestParam Long id) {
        int headcount = statsService.refreshReportFromDetail(id);
        return R.ok(headcount);
    }

    /** 删除上报单（同时删除其工资明细）。 */
    @PostMapping("/report/delete")
    public R reportDelete(@RequestParam Long id) {
        Long allocId = null;
        SalaryReport report = reportService.getById(id);
        if (report != null) {
            allocId = report.getAllocId();
        }
        detailService.remove(new LambdaQueryWrapper<SalaryDetail>()
                .eq(SalaryDetail::getReportId, id));
        reportService.removeById(id);
        statsService.refreshAlloc(allocId);
        return R.ok();
    }

    // ------------------------------------------------------------------ 工资明细

    /** 工资明细分页列表。 */
    @GetMapping("/detail/list")
    public R detailList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long limit,
                        @RequestParam(required = false) Long reportId,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) String deptName,
                        @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<SalaryDetail> wrapper = new LambdaQueryWrapper<>();
        if (reportId != null) {
            wrapper.eq(SalaryDetail::getReportId, reportId);
        }
        if (year != null) {
            wrapper.eq(SalaryDetail::getPeriodYear, year);
        }
        if (month != null) {
            wrapper.eq(SalaryDetail::getPeriodMonth, month);
        }
        if (StringUtils.hasText(deptName)) {
            wrapper.eq(SalaryDetail::getDeptName, deptName);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(SalaryDetail::getEmpName, keywords)
                    .or().like(SalaryDetail::getEmpNo, keywords)
                    .or().like(SalaryDetail::getPost, keywords));
        }
        wrapper.orderByAsc(SalaryDetail::getDeptName).orderByAsc(SalaryDetail::getId);
        IPage<SalaryDetail> result = detailService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改工资明细；工资总额与实发工资由分项自动计算。 */
    @PostMapping("/detail/save")
    public R detailSave(@RequestBody SalaryDetail detail) {
        detail.setGrossAmount(statsService.calcGross(detail.getBaseWage(), detail.getNightFee(),
                detail.getDownholeFee(), detail.getSubsidy(), detail.getOvertimeWage(),
                detail.getPerformance(), detail.getSafetyBonus(), detail.getManageAward(),
                detail.getOtherAllow()));
        detail.setNetAmount(statsService.calcNet(detail.getGrossAmount(), detail.getPension(),
                detail.getUnemployment(), detail.getMedical(), detail.getHousingFund(),
                detail.getAnnuity(), detail.getTax()));
        if (!StringUtils.hasText(detail.getEmpType())) {
            detail.setEmpType(SalaryDetail.TYPE_REG);
        }
        detailService.saveOrUpdate(detail);
        if (detail.getReportId() != null) {
            statsService.refreshReportFromDetail(detail.getReportId());
        }
        return R.ok(detail.getId());
    }

    /** 删除工资明细。 */
    @PostMapping("/detail/delete")
    public R detailDelete(@RequestParam Long id) {
        SalaryDetail detail = detailService.getById(id);
        detailService.removeById(id);
        if (detail != null && detail.getReportId() != null) {
            statsService.refreshReportFromDetail(detail.getReportId());
        }
        return R.ok();
    }

    // ------------------------------------------------------------------ 内部方法

    /** 下拨单字段兜底：金额为空置 0，结余按下拨金额计算。 */
    private void normalizeAlloc(SalaryAlloc alloc) {
        if (alloc.getApprovedAmount() == null) {
            alloc.setApprovedAmount(BigDecimal.ZERO);
        }
        if (alloc.getAllocAmount() == null) {
            alloc.setAllocAmount(alloc.getApprovedAmount());
        }
        if (alloc.getUsedAmount() == null) {
            alloc.setUsedAmount(BigDecimal.ZERO);
        }
        if (alloc.getBalanceAmount() == null) {
            alloc.setBalanceAmount(alloc.getAllocAmount().subtract(alloc.getUsedAmount()));
        }
        if (!StringUtils.hasText(alloc.getStatus())) {
            alloc.setStatus(SalaryAlloc.STATUS_WAIT);
        }
        if (!StringUtils.hasText(alloc.getAllocType())) {
            alloc.setAllocType(SalaryAlloc.TYPE_MONTH);
        }
        if (!StringUtils.hasText(alloc.getTargetUnit())) {
            alloc.setTargetUnit(OUR_UNIT);
        }
        if (!StringUtils.hasText(alloc.getDeptName())) {
            alloc.setDeptName(SalaryAlloc.DEPT_ALL);
        }
        if (alloc.getDeptId() == null && StringUtils.hasText(alloc.getDeptName())) {
            SalaryDept dept = deptService.getOne(new LambdaQueryWrapper<SalaryDept>()
                    .eq(SalaryDept::getDeptName, alloc.getDeptName()).last("LIMIT 1"));
            if (dept != null) {
                alloc.setDeptId(dept.getId());
                if (alloc.getHeadcount() == null) {
                    alloc.setHeadcount(dept.getHeadcount());
                }
            }
        }
        if (alloc.getHeadcount() == null) {
            alloc.setHeadcount(0);
        }
    }

    /** 上报单字段兜底：金额为空置 0，人数为空置 0，部门与下拨单互相同步。 */
    private void normalizeReport(SalaryReport report) {
        // 关联下拨单 → 回填下拨单号与部门
        if (report.getAllocId() != null) {
            SalaryAlloc alloc = allocService.getById(report.getAllocId());
            if (alloc != null) {
                report.setAllocNo(alloc.getAllocNo());
                if (!StringUtils.hasText(report.getDeptName())) {
                    report.setDeptName(alloc.getDeptName());
                }
            }
        } else if (StringUtils.hasText(report.getDeptName())) {
            // 未指定下拨单时，按「部门 + 期间」自动关联本部门下拨单
            report.setAllocId(statsService.findAllocId(report.getPeriodYear(),
                    report.getPeriodMonth(), report.getDeptName()));
            if (report.getAllocId() != null) {
                SalaryAlloc alloc = allocService.getById(report.getAllocId());
                if (alloc != null) {
                    report.setAllocNo(alloc.getAllocNo());
                }
            }
        }
        if (StringUtils.hasText(report.getDeptName()) && report.getDeptId() == null) {
            SalaryDept dept = deptService.getOne(new LambdaQueryWrapper<SalaryDept>()
                    .eq(SalaryDept::getDeptName, report.getDeptName()).last("LIMIT 1"));
            if (dept != null) {
                report.setDeptId(dept.getId());
            }
        }
        if (report.getGrossAmount() == null) {
            report.setGrossAmount(BigDecimal.ZERO);
        }
        if (report.getNetAmount() == null) {
            report.setNetAmount(BigDecimal.ZERO);
        }
        if (report.getDeductAmount() == null) {
            report.setDeductAmount(BigDecimal.ZERO);
        }
        if (report.getTaxAmount() == null) {
            report.setTaxAmount(BigDecimal.ZERO);
        }
        if (report.getHeadcount() == null) {
            report.setHeadcount(0);
        }
        if (!StringUtils.hasText(report.getStatus())) {
            report.setStatus(SalaryReport.STATUS_DRAFT);
        }
        if (!StringUtils.hasText(report.getReportType())) {
            report.setReportType(SalaryReport.TYPE_DEPT);
        }
    }

    /** 按部门 + 期间查询该部门的工资上报单（含历史），用于部门台账视角。 */
    @GetMapping("/report/by-dept")
    public R reportByDept(@RequestParam Integer year,
                          @RequestParam Integer month,
                          @RequestParam(required = false) String deptName) {
        LambdaQueryWrapper<SalaryReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalaryReport::getPeriodYear, year)
                .eq(SalaryReport::getPeriodMonth, month)
                .eq(SalaryReport::getReportType, SalaryReport.TYPE_DEPT);
        if (StringUtils.hasText(deptName)) {
            wrapper.eq(SalaryReport::getDeptName, deptName);
        }
        wrapper.orderByAsc(SalaryReport::getDeptName);
        return R.ok(reportService.list(wrapper));
    }

    /** 供页面下拉使用的上报单简要列表。 */
    @GetMapping("/report/all")
    public R reportAll() {
        List<SalaryReport> list = reportService.list(new LambdaQueryWrapper<SalaryReport>()
                .orderByDesc(SalaryReport::getPeriodYear)
                .orderByDesc(SalaryReport::getPeriodMonth)
                .orderByAsc(SalaryReport::getDeptName));
        return R.ok(list);
    }
}
