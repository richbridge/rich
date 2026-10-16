package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 盘点任务。
 *
 * 一次盘点作业的主单，明确「盘什么范围、谁来盘、什么时候完成」。
 *
 * <p>任务创建（或点击生成清单）时会按范围把标的<b>冻结</b>成应盘明细
 * （{@link InvRecord}，{@code bookQty} 记下当时的账面数量，{@code result} 为「未盘」）。
 * 之后无论账面如何变动，本次盘点都以冻结值为基准，保证盘点结论可追溯。
 *
 * <p>状态流转：草稿 → 进行中 → 已完成 / 已取消。
 */
@TableName("inv_task")
public class InvTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务单号，如 PD2026-0001。 */
    private String taskNo;

    /** 任务名称。 */
    private String taskName;

    /** 盘点类型：固定资产/库存物资/食堂物资/综合盘点。 */
    private String taskType;

    /** 范围维度：全部/分类/部门/仓库。 */
    private String scopeType;

    /** 范围取值，多个用逗号分隔。 */
    private String scopeValue;

    /** 盘点方式：明盘/暗盘。 */
    private String checkMode;

    /** 计划盘点日期。 */
    private LocalDate planDate;

    /** 截止日期。 */
    private LocalDate deadline;

    /** 负责人。 */
    private String owner;

    /** 盘点人员，多人逗号分隔。 */
    private String checkers;

    /** 应盘标的数。 */
    private Integer subjectCount;

    /** 已盘标的数。 */
    private Integer checkedCount;

    /** 正常数。 */
    private Integer normalCount;

    /** 盘盈数。 */
    private Integer profitCount;

    /** 盘亏数。 */
    private Integer lossCount;

    /** 差异净额(元)，正为盘盈、负为盘亏。 */
    private BigDecimal diffAmount;

    /** 状态：草稿/进行中/已完成/已取消。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public void setScopeValue(String scopeValue) {
        this.scopeValue = scopeValue;
    }

    public String getCheckMode() {
        return checkMode;
    }

    public void setCheckMode(String checkMode) {
        this.checkMode = checkMode;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCheckers() {
        return checkers;
    }

    public void setCheckers(String checkers) {
        this.checkers = checkers;
    }

    public Integer getSubjectCount() {
        return subjectCount;
    }

    public void setSubjectCount(Integer subjectCount) {
        this.subjectCount = subjectCount;
    }

    public Integer getCheckedCount() {
        return checkedCount;
    }

    public void setCheckedCount(Integer checkedCount) {
        this.checkedCount = checkedCount;
    }

    public Integer getNormalCount() {
        return normalCount;
    }

    public void setNormalCount(Integer normalCount) {
        this.normalCount = normalCount;
    }

    public Integer getProfitCount() {
        return profitCount;
    }

    public void setProfitCount(Integer profitCount) {
        this.profitCount = profitCount;
    }

    public Integer getLossCount() {
        return lossCount;
    }

    public void setLossCount(Integer lossCount) {
        this.lossCount = lossCount;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
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
