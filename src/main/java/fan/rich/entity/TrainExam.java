package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 培训考试（试卷）。
 *
 * <p>一次考试对应一门课程或一期培训，规定总分、及格分、考试时长。
 * 应考人数 / 合格人数 / 平均分由 {@link TrainScore} 明细回填。
 */
@TableName("train_exam")
public class TrainExam implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：未发布。 */
    public static final String STATUS_DRAFT = "未发布";

    /** 状态：报名中。 */
    public static final String STATUS_OPEN = "报名中";

    /** 状态：进行中。 */
    public static final String STATUS_RUNNING = "进行中";

    /** 状态：已结束。 */
    public static final String STATUS_DONE = "已结束";

    /** 考试类型：线上考试。 */
    public static final String TYPE_ONLINE = "线上考试";

    /** 考试类型：线下笔试。 */
    public static final String TYPE_OFFLINE = "线下笔试";

    /** 考试类型：实操考核。 */
    public static final String TYPE_PRACTICE = "实操考核";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷编号，如 KS2026-001。 */
    private String examNo;

    /** 考试名称。 */
    private String name;

    /** 关联课程ID。 */
    private Long courseId;

    /** 关联课程。 */
    private String courseName;

    /** 关联培训计划ID。 */
    private Long planId;

    /** 关联培训计划。 */
    private String planName;

    /** 考试类型：线上考试 / 线下笔试 / 实操考核。 */
    private String examType;

    /** 总分。 */
    private Integer totalScore;

    /** 及格分。 */
    private Integer passScore;

    /** 考试时长（分钟）。 */
    private Integer duration;

    /** 题目数。 */
    private Integer questionCount;

    /** 考试日期。 */
    private LocalDate examDate;

    /** 状态：未发布 / 报名中 / 进行中 / 已结束。 */
    private String status;

    /** 应考人数（回填）。 */
    private Integer examineeCount;

    /** 合格人数（回填）。 */
    private Integer passCount;

    /** 平均分（回填）。 */
    private BigDecimal avgScore;

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

    public String getExamNo() {
        return examNo;
    }

    public void setExamNo(String examNo) {
        this.examNo = examNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getPassScore() {
        return passScore;
    }

    public void setPassScore(Integer passScore) {
        this.passScore = passScore;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getExamineeCount() {
        return examineeCount;
    }

    public void setExamineeCount(Integer examineeCount) {
        this.examineeCount = examineeCount;
    }

    public Integer getPassCount() {
        return passCount;
    }

    public void setPassCount(Integer passCount) {
        this.passCount = passCount;
    }

    public BigDecimal getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(BigDecimal avgScore) {
        this.avgScore = avgScore;
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
