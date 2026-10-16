package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 培训计划（培训班）。
 *
 * <p>企业培训系统里的「培训」主体：一期培训绑定课程与考试，规定培训时间、地点、
 * 参训对象，参训情况由 {@link TrainEnroll} 明细回填报名人数与合格人数。
 */
@TableName("train_plan")
public class TrainPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：报名中。 */
    public static final String STATUS_ENROLL = "报名中";

    /** 状态：进行中。 */
    public static final String STATUS_RUNNING = "进行中";

    /** 状态：已结束。 */
    public static final String STATUS_DONE = "已结束";

    /** 状态：已取消。 */
    public static final String STATUS_CANCEL = "已取消";

    /** 培训类型：入职培训。 */
    public static final String TYPE_ENTRY = "入职培训";

    /** 培训类型：岗位技能。 */
    public static final String TYPE_POST = "岗位技能";

    /** 培训类型：安全环保。 */
    public static final String TYPE_SAFETY = "安全环保";

    /** 培训类型：专项培训。 */
    public static final String TYPE_SPECIAL = "专项培训";

    /** 培训类型：管理提升。 */
    public static final String TYPE_MANAGE = "管理提升";

    /** 培训类型：继续教育。 */
    public static final String TYPE_CONTINUE = "继续教育";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划编号，如 PX2026-001。 */
    private String planNo;

    /** 培训名称。 */
    private String name;

    /** 培训年度。 */
    private Integer planYear;

    /** 培训类型。 */
    private String planType;

    /** 关联课程ID。 */
    private Long courseId;

    /** 关联课程。 */
    private String courseName;

    /** 开始日期。 */
    private LocalDate startDate;

    /** 结束日期。 */
    private LocalDate endDate;

    /** 培训地点。 */
    private String address;

    /** 讲师。 */
    private String teacher;

    /** 主办部门。 */
    private String dept;

    /** 参训对象（部门 / 车间）。 */
    private String targetDept;

    /** 计划参训人数。 */
    private Integer planCount;

    /** 实际参训人数（回填）。 */
    private Integer enrollCount;

    /** 合格人数（回填）。 */
    private Integer passCount;

    /** 学时。 */
    private Integer hours;

    /** 学分。 */
    private BigDecimal credit;

    /** 关联考试ID。 */
    private Long examId;

    /** 关联考试。 */
    private String examName;

    /** 状态：草稿 / 报名中 / 进行中 / 已结束 / 已取消。 */
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

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPlanYear() {
        return planYear;
    }

    public void setPlanYear(Integer planYear) {
        this.planYear = planYear;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public String getTargetDept() {
        return targetDept;
    }

    public void setTargetDept(String targetDept) {
        this.targetDept = targetDept;
    }

    public Integer getPlanCount() {
        return planCount;
    }

    public void setPlanCount(Integer planCount) {
        this.planCount = planCount;
    }

    public Integer getEnrollCount() {
        return enrollCount;
    }

    public void setEnrollCount(Integer enrollCount) {
        this.enrollCount = enrollCount;
    }

    public Integer getPassCount() {
        return passCount;
    }

    public void setPassCount(Integer passCount) {
        this.passCount = passCount;
    }

    public Integer getHours() {
        return hours;
    }

    public void setHours(Integer hours) {
        this.hours = hours;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
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
