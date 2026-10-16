package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检查发现问题。
 *
 * <p>检查任务现场发现的具体问题，是检查模块的明细台账，可独立跟踪整改状态。
 */
@TableName("inspect_finding")
public class InspectFinding implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：未整改。 */
    public static final String STATUS_OPEN = "未整改";

    /** 状态：整改中。 */
    public static final String STATUS_DOING = "整改中";

    /** 状态：已整改。 */
    public static final String STATUS_DONE = "已整改";

    /** 严重程度：一般。 */
    public static final String LEVEL_NORMAL = "一般";

    /** 严重程度：较重。 */
    public static final String LEVEL_MAJOR = "较重";

    /** 严重程度：严重。 */
    public static final String LEVEL_SEVERE = "严重";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属检查任务 ID。 */
    private Long taskId;

    /** 所属检查任务编号。 */
    private String taskNo;

    /** 关联审计项目 ID（可为空）。 */
    private Long projectId;

    /** 被检查单位。 */
    private String unitName;

    /** 问题类别：资金管理 / 物资采购 / 资产管理 / 安全环保 / 合同管理 / 其他。 */
    private String category;

    /** 问题描述。 */
    private String title;

    /** 相关建议（检查方给出的整改建议）。 */
    private String suggestion;

    /** 严重程度：一般 / 较重 / 严重。 */
    private String severity;

    /** 涉及金额（元）。 */
    private BigDecimal amount;

    /** 整改要求。 */
    private String requirement;

    /** 状态：未整改 / 整改中 / 已整改。 */
    private String status;

    /** 整改期限。 */
    private LocalDate dueDate;

    /** 整改责任人。 */
    private String owner;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
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
