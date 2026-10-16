package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 参训记录（报名 / 学习明细）。
 *
 * <p>一条记录 = 一名学员参加一期培训的情况：签到、获得学时学分、考试成绩、
 * 是否合格发证。培训计划的参训人数与合格人数由这里汇总回填。
 */
@TableName("train_enroll")
public class TrainEnroll implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学习状态：未开始。 */
    public static final String STATUS_NOT_START = "未开始";

    /** 学习状态：学习中。 */
    public static final String STATUS_LEARNING = "学习中";

    /** 学习状态：已完成。 */
    public static final String STATUS_FINISHED = "已完成";

    /** 学习状态：不合格。 */
    public static final String STATUS_FAILED = "不合格";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 培训计划ID。 */
    private Long planId;

    /** 计划编号。 */
    private String planNo;

    /** 培训名称。 */
    private String planName;

    /** 工号。 */
    private String studentNo;

    /** 学员姓名。 */
    private String studentName;

    /** 所在部门 / 单位。 */
    private String dept;

    /** 岗位。 */
    private String post;

    /** 联系电话。 */
    private String mobile;

    /** 签到状态：未签到 / 已签到 / 缺勤 / 请假。 */
    private String signStatus;

    /** 获得学时。 */
    private Integer hours;

    /** 获得学分。 */
    private BigDecimal credit;

    /** 考试成绩。 */
    private BigDecimal score;

    /** 是否合格：1 是 0 否。 */
    private Integer pass;

    /** 证书编号。 */
    private String certNo;

    /** 完成日期。 */
    private LocalDate finishDate;

    /** 学习状态：未开始 / 学习中 / 已完成 / 不合格。 */
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

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public String getPost() {
        return post;
    }

    public void setPost(String post) {
        this.post = post;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getSignStatus() {
        return signStatus;
    }

    public void setSignStatus(String signStatus) {
        this.signStatus = signStatus;
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

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getPass() {
        return pass;
    }

    public void setPass(Integer pass) {
        this.pass = pass;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public LocalDate getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(LocalDate finishDate) {
        this.finishDate = finishDate;
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
