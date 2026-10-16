package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 工资上报单（部门 → 财务科，或公司 → 上级单位）。
 *
 * <p>各部门按月造表上报工资，财务科审核后汇总上报上级；审核通过后发放，
 * 实发合计回填到关联下拨单的已发放金额上。
 */
@TableName("salary_report")
public class SalaryReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：待审核。 */
    public static final String STATUS_WAIT = "待审核";

    /** 状态：已审核。 */
    public static final String STATUS_AUDITED = "已审核";

    /** 状态：已驳回。 */
    public static final String STATUS_REJECT = "已驳回";

    /** 状态：已发放。 */
    public static final String STATUS_PAID = "已发放";

    /** 上报类型：部门工资表上报（一个部门一个期间一张）。 */
    public static final String TYPE_DEPT = "部门工资表上报";

    /** 上报类型：专项上报（奖金 / 补贴等不按部门分解的上报）。 */
    public static final String TYPE_SPECIAL = "专项上报";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上报单号，如 SB2026-08-01。 */
    private String reportNo;

    /** 所属年度。 */
    private Integer periodYear;

    /** 所属月份。 */
    private Integer periodMonth;

    /** 上报部门ID。 */
    private Long deptId;

    /** 上报部门。 */
    private String deptName;

    /** 关联下拨单ID。 */
    private Long allocId;

    /** 关联下拨单号。 */
    private String allocNo;

    /** 上报类型：部门工资表上报 / 公司汇总上报。 */
    private String reportType;

    /** 上报人数。 */
    private Integer headcount;

    /** 应发工资合计 / 工资总额(元)。 */
    private BigDecimal grossAmount;

    /** 代扣合计(元)：养老+失业+医保+公积金+年金。 */
    private BigDecimal deductAmount;

    /** 个人所得税合计(元)。 */
    private BigDecimal taxAmount;

    /** 实发工资合计(元)。 */
    private BigDecimal netAmount;

    /** 上报日期。 */
    private LocalDate reportDate;

    /** 上报人。 */
    private String reporter;

    /** 审核人。 */
    private String auditor;

    /** 审核日期。 */
    private LocalDate auditDate;

    /** 审核意见。 */
    private String auditOpinion;

    /** 状态：草稿 / 待审核 / 已审核 / 已驳回 / 已发放。 */
    private String status;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public Integer getPeriodYear() {
        return periodYear;
    }

    public void setPeriodYear(Integer periodYear) {
        this.periodYear = periodYear;
    }

    public Integer getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(Integer periodMonth) {
        this.periodMonth = periodMonth;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getAllocId() {
        return allocId;
    }

    public void setAllocId(Long allocId) {
        this.allocId = allocId;
    }

    public String getAllocNo() {
        return allocNo;
    }

    public void setAllocNo(String allocNo) {
        this.allocNo = allocNo;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getDeductAmount() {
        return deductAmount;
    }

    public void setDeductAmount(BigDecimal deductAmount) {
        this.deductAmount = deductAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getAuditor() {
        return auditor;
    }

    public void setAuditor(String auditor) {
        this.auditor = auditor;
    }

    public LocalDate getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(LocalDate auditDate) {
        this.auditDate = auditDate;
    }

    public String getAuditOpinion() {
        return auditOpinion;
    }

    public void setAuditOpinion(String auditOpinion) {
        this.auditOpinion = auditOpinion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
