package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 考试成绩明细。
 *
 * <p>一名学员一次考试的得分记录，支持补考（times 记录第几次参加）。
 * 写入后回填考试的应考人数 / 合格人数 / 平均分。
 */
@TableName("train_score")
public class TrainScore implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 考试ID。 */
    private Long examId;

    /** 试卷编号。 */
    private String examNo;

    /** 考试名称。 */
    private String examName;

    /** 工号。 */
    private String studentNo;

    /** 学员姓名。 */
    private String studentName;

    /** 所在部门 / 单位。 */
    private String dept;

    /** 考试得分。 */
    private BigDecimal score;

    /** 是否合格：1 是 0 否。 */
    private Integer pass;

    /** 考试日期。 */
    private LocalDate examDate;

    /** 用时（分钟）。 */
    private Integer usedMinutes;

    /** 第几次参加（补考次数 + 1）。 */
    private Integer times;

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

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public String getExamNo() {
        return examNo;
    }

    public void setExamNo(String examNo) {
        this.examNo = examNo;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
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

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public Integer getUsedMinutes() {
        return usedMinutes;
    }

    public void setUsedMinutes(Integer usedMinutes) {
        this.usedMinutes = usedMinutes;
    }

    public Integer getTimes() {
        return times;
    }

    public void setTimes(Integer times) {
        this.times = times;
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
