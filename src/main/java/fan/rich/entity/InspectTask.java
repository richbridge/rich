package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检查任务。
 *
 * <p>与审计项目并列的另一条主线：日常检查 / 专项检查 / 跟踪检查（针对已发现问题的
 * 整改回头看）/ 整改核查。检查过程中发现的问题记录在 {@link InspectFinding} 上，
 * 任务统计值（发现问题数 / 已整改数）由发现记录变动时回填。
 */
@TableName("inspect_task")
public class InspectTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待检查。 */
    public static final String STATUS_PENDING = "待检查";

    /** 状态：检查中。 */
    public static final String STATUS_RUNNING = "检查中";

    /** 状态：已完成。 */
    public static final String STATUS_DONE = "已完成";

    /** 检查类型：跟踪检查。 */
    public static final String TYPE_TRACE = "跟踪检查";

    /** 检查类型：专项检查。 */
    public static final String TYPE_SPECIAL = "专项检查";

    /** 检查类型：日常检查。 */
    public static final String TYPE_DAILY = "日常检查";

    /** 检查类型：整改核查。 */
    public static final String TYPE_VERIFY = "整改核查";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号，如 JC2025-001。 */
    private String taskNo;

    /** 检查名称。 */
    private String name;

    /** 检查类型：跟踪检查 / 专项检查 / 日常检查 / 整改核查。 */
    private String inspectType;

    /** 关联审计项目 ID（跟踪检查 / 整改核查时使用）。 */
    private Long projectId;

    /** 关联项目名称。 */
    private String projectName;

    /** 被检查单位。 */
    private String unitName;

    /** 检查部门。 */
    private String dept;

    /** 检查人。 */
    private String inspector;

    /** 计划检查日期。 */
    private LocalDate planDate;

    /** 实际检查日期。 */
    private LocalDate actualDate;

    /** 状态：待检查 / 检查中 / 已完成。 */
    private String status;

    /** 检查结果（结论性描述）。 */
    private String result;

    /** 发现问题数。 */
    private Integer findingCount;

    /** 已整改数。 */
    private Integer rectifiedCount;

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

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInspectType() {
        return inspectType;
    }

    public void setInspectType(String inspectType) {
        this.inspectType = inspectType;
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

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public String getInspector() {
        return inspector;
    }

    public void setInspector(String inspector) {
        this.inspector = inspector;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }

    public void setActualDate(LocalDate actualDate) {
        this.actualDate = actualDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(Integer findingCount) {
        this.findingCount = findingCount;
    }

    public Integer getRectifiedCount() {
        return rectifiedCount;
    }

    public void setRectifiedCount(Integer rectifiedCount) {
        this.rectifiedCount = rectifiedCount;
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
