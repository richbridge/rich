package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 工资下拨单（上级单位 → 本公司，按核算部门下拨）。
 *
 * <p>招金有色 / 招金集团核定当月工资总额后向金宝矿业下拨工资资金，财务科按各核算部门
 * 分解下拨额度：一个部门一个期间一张下拨单。部门当月上报单审核 / 发放后，其实发合计
 * 回填到本部门下拨单的已发放金额上，下拨金额 - 已发放金额 = 部门结余。
 *
 * <p>不按部门分解的奖金、补贴等专项下拨单，部门记为「全公司」。
 */
@TableName("salary_alloc")
public class SalaryAlloc implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待下拨。 */
    public static final String STATUS_WAIT = "待下拨";

    /** 状态：已下拨。 */
    public static final String STATUS_DONE = "已下拨";

    /** 状态：部分使用。 */
    public static final String STATUS_PART = "部分使用";

    /** 状态：已结算。 */
    public static final String STATUS_SETTLED = "已结算";

    /** 下拨类型：月度工资总额。 */
    public static final String TYPE_MONTH = "月度工资总额";

    /** 下拨类型：奖金专项。 */
    public static final String TYPE_BONUS = "奖金专项";

    /** 下拨类型：年终兑现。 */
    public static final String TYPE_YEAR = "年终兑现";

    /** 下拨类型：专项补贴。 */
    public static final String TYPE_SUBSIDY = "专项补贴";

    /** 下拨类型：补拨。 */
    public static final String TYPE_ADD = "补拨";

    /** 不按部门分解时的部门名称。 */
    public static final String DEPT_ALL = "全公司";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 下拨单号，如 XB2026-08-01。 */
    private String allocNo;

    /** 所属年度。 */
    private Integer periodYear;

    /** 所属月份。 */
    private Integer periodMonth;

    /** 下拨类型：月度工资总额 / 奖金专项 / 年终兑现 / 专项补贴 / 补拨。 */
    private String allocType;

    /** 下拨部门ID。 */
    private Long deptId;

    /** 下拨部门名称，如 采矿车间。 */
    private String deptName;

    /** 部门在册人数。 */
    private Integer headcount;

    /** 拨付单位。 */
    private String sourceUnit;

    /** 接收单位。 */
    private String targetUnit;

    /** 核定工资总额(元)。 */
    private BigDecimal approvedAmount;

    /** 实际下拨金额(元)。 */
    private BigDecimal allocAmount;

    /** 已发放金额(元)，由上报单回填。 */
    private BigDecimal usedAmount;

    /** 结余金额(元) = 下拨 - 已发放。 */
    private BigDecimal balanceAmount;

    /** 下拨日期。 */
    private LocalDate allocDate;

    /** 拨付文号 / 凭证号。 */
    private String voucherNo;

    /** 经办人。 */
    private String handler;

    /** 状态：待下拨 / 已下拨 / 部分使用 / 已结算。 */
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

    public String getAllocNo() {
        return allocNo;
    }

    public void setAllocNo(String allocNo) {
        this.allocNo = allocNo;
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

    public String getAllocType() {
        return allocType;
    }

    public void setAllocType(String allocType) {
        this.allocType = allocType;
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

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }

    public String getSourceUnit() {
        return sourceUnit;
    }

    public void setSourceUnit(String sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    public String getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(String targetUnit) {
        this.targetUnit = targetUnit;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public BigDecimal getAllocAmount() {
        return allocAmount;
    }

    public void setAllocAmount(BigDecimal allocAmount) {
        this.allocAmount = allocAmount;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public void setUsedAmount(BigDecimal usedAmount) {
        this.usedAmount = usedAmount;
    }

    public BigDecimal getBalanceAmount() {
        return balanceAmount;
    }

    public void setBalanceAmount(BigDecimal balanceAmount) {
        this.balanceAmount = balanceAmount;
    }

    public LocalDate getAllocDate() {
        return allocDate;
    }

    public void setAllocDate(LocalDate allocDate) {
        this.allocDate = allocDate;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
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
