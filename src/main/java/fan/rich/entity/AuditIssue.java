package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 审计问题（整改台账）。
 *
 * <p>一条问题对应一项需要整改的事项，全流程跟踪：录入 → 整改中 → 销号。
 * 状态与整改期限决定「逾期 / 时限临近 / 已销号」，是整改看板的统计口径来源。
 */
@TableName("audit_issue")
public class AuditIssue implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：未销号。 */
    public static final String STATUS_OPEN = "未销号";

    /** 状态：已销号。 */
    public static final String STATUS_CLOSED = "已销号";

    /** 状态：逾期（整改期限已过且未销号）。 */
    public static final String STATUS_OVERDUE = "逾期";

    /** 整改类型：立行立改。 */
    public static final String RECTIFY_NOW = "立行立改";

    /** 整改类型：持续整改。 */
    public static final String RECTIFY_KEEP = "持续整改";

    /** 整改类型：分阶段整改。 */
    public static final String RECTIFY_STAGE = "分阶段整改";

    /** 整改类型：限期整改。 */
    public static final String RECTIFY_LIMIT = "限期整改";

    /** 整改情况：已整改。 */
    public static final String PROGRESS_DONE = "已整改";

    /** 整改情况：持续整改。 */
    public static final String PROGRESS_KEEP = "持续整改";

    /** 整改情况：已移交，整改中。 */
    public static final String PROGRESS_DOING = "已移交，整改中";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问题编号（项目内序号，如 001）。 */
    private String issueNo;

    /** 所属审计项目 ID。 */
    private Long projectId;

    /** 所属项目名称（冗余，便于列表与导出）。 */
    private String projectName;

    /** 被审计单位 ID。 */
    private Long unitId;

    /** 被审计单位名称。 */
    private String unitName;

    /** 问题类型：税务、专利管理 / 财务管理 / 采购管理 / 食堂管理 / 其他业务管理（来自检查台账）。 */
    private String issueType;

    /** 问题性质：违规资金使用 / 固定资产管理 / 财务凭证管理 / 内部控制缺陷 / 其他。 */
    private String nature;

    /** 问题内容。 */
    private String description;

    /** 相关建议（检查方给出的整改建议）。 */
    private String suggestion;

    /** 整改方案（被检查单位的整改措施）。 */
    private String rectifyPlan;

    /** 整改时限原文，可能是具体日期，也可能是「持续整改」这类长期表述。 */
    private String deadlineText;

    /** 整改情况：已整改 / 持续整改 / 整改中 / 已移交，整改中。 */
    private String progress;

    /** 发现期间，如「2026年二季度」。 */
    private String checkPeriod;

    /** 问题金额（元）。 */
    private BigDecimal amount;

    /** 整改类型：立行立改 / 持续整改 / 分阶段整改。 */
    private String rectifyType;

    /** 状态：未销号 / 已销号 / 逾期。 */
    private String status;

    /** 整改期限。 */
    private LocalDate dueDate;

    /** 销号日期。 */
    private LocalDate closedDate;

    /** 整改责任人。 */
    private String owner;

    /** 整改措施。 */
    private String measure;

    /** 问责人次。 */
    private Integer punishCount;

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

    public String getIssueNo() {
        return issueNo;
    }

    public void setIssueNo(String issueNo) {
        this.issueNo = issueNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getRectifyPlan() {
        return rectifyPlan;
    }

    public void setRectifyPlan(String rectifyPlan) {
        this.rectifyPlan = rectifyPlan;
    }

    public String getDeadlineText() {
        return deadlineText;
    }

    public void setDeadlineText(String deadlineText) {
        this.deadlineText = deadlineText;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public String getCheckPeriod() {
        return checkPeriod;
    }

    public void setCheckPeriod(String checkPeriod) {
        this.checkPeriod = checkPeriod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRectifyType() {
        return rectifyType;
    }

    public void setRectifyType(String rectifyType) {
        this.rectifyType = rectifyType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getMeasure() {
        return measure;
    }

    public void setMeasure(String measure) {
        this.measure = measure;
    }

    public Integer getPunishCount() {
        return punishCount;
    }

    public void setPunishCount(Integer punishCount) {
        this.punishCount = punishCount;
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
