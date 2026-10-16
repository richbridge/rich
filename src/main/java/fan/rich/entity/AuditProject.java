package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 审计项目。
 *
 * <p>一次审计工作（如「2025 年度财务收支审计」）的主记录，问题明细挂在
 * {@link AuditIssue} 上，项目统计值（问题数 / 已销号 / 逾期 / 金额）在问题变动时回填，
 * 避免列表页每次现算。
 */
@TableName("audit_project")
public class AuditProject implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：未开始。 */
    public static final String STATUS_NOT_STARTED = "未开始";

    /** 状态：整改中。 */
    public static final String STATUS_RECTIFYING = "整改中";

    /** 状态：已完成。 */
    public static final String STATUS_DONE = "已完成";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号，如 SJ2025-001。 */
    private String projectNo;

    /** 项目名称。 */
    private String name;

    /** 审计年度。 */
    private Integer auditYear;

    /** 审计类型：财务审计 / 固定资产审计 / 内部控制审计 / 预算执行审计 / 专项审计。 */
    private String auditType;

    /** 审计（进点）日期。 */
    private LocalDate auditDate;

    /** 审计结束日期。 */
    private LocalDate endDate;

    /** 审计组长。 */
    private String leader;

    /** 审计工作组。 */
    private String workgroup;

    /** 被审计单位 ID。 */
    private Long unitId;

    /** 被审计单位名称（冗余，便于列表与统计）。 */
    private String unitName;

    /** 问题数。 */
    private Integer issueCount;

    /** 已销号数。 */
    private Integer closedCount;

    /** 逾期未整改数。 */
    private Integer overdueCount;

    /** 问题金额（元）。 */
    private BigDecimal amount;

    /** 状态：未开始 / 整改中 / 已完成。 */
    private String status;

    /** 是否归档：0 否 1 是。 */
    private Integer archived;

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

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAuditYear() {
        return auditYear;
    }

    public void setAuditYear(Integer auditYear) {
        this.auditYear = auditYear;
    }

    public String getAuditType() {
        return auditType;
    }

    public void setAuditType(String auditType) {
        this.auditType = auditType;
    }

    public LocalDate getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(LocalDate auditDate) {
        this.auditDate = auditDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public String getWorkgroup() {
        return workgroup;
    }

    public void setWorkgroup(String workgroup) {
        this.workgroup = workgroup;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public Integer getClosedCount() {
        return closedCount;
    }

    public void setClosedCount(Integer closedCount) {
        this.closedCount = closedCount;
    }

    public Integer getOverdueCount() {
        return overdueCount;
    }

    public void setOverdueCount(Integer overdueCount) {
        this.overdueCount = overdueCount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
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
