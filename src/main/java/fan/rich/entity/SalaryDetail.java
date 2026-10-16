package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

/**
 * 工资明细（个人）。
 *
 * <p>对应各部门 Excel 工资明细表的一行：出勤、工资部分、考核部分、其他、
 * 工资总额、代扣项目、个税、实发工资。
 */
@TableName("salary_detail")
public class SalaryDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用工类型：在册。 */
    public static final String TYPE_REG = "在册";

    /** 用工类型：劳务派遣。 */
    public static final String TYPE_DISPATCH = "劳务派遣";

    /** 用工类型：外聘。 */
    public static final String TYPE_OUT = "外聘";

    /** 用工类型：实习。 */
    public static final String TYPE_INTERN = "实习";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联上报单ID。 */
    private Long reportId;

    /** 关联上报单号。 */
    private String reportNo;

    /** 所属年度。 */
    private Integer periodYear;

    /** 所属月份。 */
    private Integer periodMonth;

    /** 所在部门。 */
    private String deptName;

    /** 工号。 */
    private String empNo;

    /** 姓名。 */
    private String empName;

    /** 岗位 / 工种。 */
    private String post;

    /** 用工类型：在册 / 劳务派遣 / 外聘 / 实习。 */
    private String empType;

    /** 出勤天数。 */
    private BigDecimal attendance;

    /** 岗位工资。 */
    private BigDecimal baseWage;

    /** 夜班费。 */
    private BigDecimal nightFee;

    /** 下井费。 */
    private BigDecimal downholeFee;

    /** 补助补贴。 */
    private BigDecimal subsidy;

    /** 加班工资。 */
    private BigDecimal overtimeWage;

    /** 绩效奖金。 */
    private BigDecimal performance;

    /** 安全奖。 */
    private BigDecimal safetyBonus;

    /** 管理奖惩。 */
    private BigDecimal manageAward;

    /** 其他（高温补贴 / 床位费 / 乘车费等）。 */
    private BigDecimal otherAllow;

    /** 工资总额(元)。 */
    private BigDecimal grossAmount;

    /** 养老保险。 */
    private BigDecimal pension;

    /** 失业保险。 */
    private BigDecimal unemployment;

    /** 医疗保险。 */
    private BigDecimal medical;

    /** 住房公积金。 */
    private BigDecimal housingFund;

    /** 企业年金。 */
    private BigDecimal annuity;

    /** 个人所得税。 */
    private BigDecimal tax;

    /** 实发工资(元)。 */
    private BigDecimal netAmount;

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

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
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

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getPost() {
        return post;
    }

    public void setPost(String post) {
        this.post = post;
    }

    public String getEmpType() {
        return empType;
    }

    public void setEmpType(String empType) {
        this.empType = empType;
    }

    public BigDecimal getAttendance() {
        return attendance;
    }

    public void setAttendance(BigDecimal attendance) {
        this.attendance = attendance;
    }

    public BigDecimal getBaseWage() {
        return baseWage;
    }

    public void setBaseWage(BigDecimal baseWage) {
        this.baseWage = baseWage;
    }

    public BigDecimal getNightFee() {
        return nightFee;
    }

    public void setNightFee(BigDecimal nightFee) {
        this.nightFee = nightFee;
    }

    public BigDecimal getDownholeFee() {
        return downholeFee;
    }

    public void setDownholeFee(BigDecimal downholeFee) {
        this.downholeFee = downholeFee;
    }

    public BigDecimal getSubsidy() {
        return subsidy;
    }

    public void setSubsidy(BigDecimal subsidy) {
        this.subsidy = subsidy;
    }

    public BigDecimal getOvertimeWage() {
        return overtimeWage;
    }

    public void setOvertimeWage(BigDecimal overtimeWage) {
        this.overtimeWage = overtimeWage;
    }

    public BigDecimal getPerformance() {
        return performance;
    }

    public void setPerformance(BigDecimal performance) {
        this.performance = performance;
    }

    public BigDecimal getSafetyBonus() {
        return safetyBonus;
    }

    public void setSafetyBonus(BigDecimal safetyBonus) {
        this.safetyBonus = safetyBonus;
    }

    public BigDecimal getManageAward() {
        return manageAward;
    }

    public void setManageAward(BigDecimal manageAward) {
        this.manageAward = manageAward;
    }

    public BigDecimal getOtherAllow() {
        return otherAllow;
    }

    public void setOtherAllow(BigDecimal otherAllow) {
        this.otherAllow = otherAllow;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getPension() {
        return pension;
    }

    public void setPension(BigDecimal pension) {
        this.pension = pension;
    }

    public BigDecimal getUnemployment() {
        return unemployment;
    }

    public void setUnemployment(BigDecimal unemployment) {
        this.unemployment = unemployment;
    }

    public BigDecimal getMedical() {
        return medical;
    }

    public void setMedical(BigDecimal medical) {
        this.medical = medical;
    }

    public BigDecimal getHousingFund() {
        return housingFund;
    }

    public void setHousingFund(BigDecimal housingFund) {
        this.housingFund = housingFund;
    }

    public BigDecimal getAnnuity() {
        return annuity;
    }

    public void setAnnuity(BigDecimal annuity) {
        this.annuity = annuity;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
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
