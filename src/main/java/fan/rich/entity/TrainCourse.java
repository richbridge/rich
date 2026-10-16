package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 培训课程（课件）。
 *
 * <p>对应企业培训系统里的「课程 / 课件」：一门课程有唯一的课程编号，可挂载课件地址，
 * 作为培训计划与考试的关联对象。
 */
@TableName("train_course")
public class TrainCourse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已发布。 */
    public static final String STATUS_PUBLISHED = "已发布";

    /** 状态：已下架。 */
    public static final String STATUS_OFFLINE = "已下架";

    /** 课程类型：点播。 */
    public static final String TYPE_VOD = "点播";

    /** 课程类型：直播。 */
    public static final String TYPE_LIVE = "直播";

    /** 课程类型：面授。 */
    public static final String TYPE_FACE = "面授";

    /** 课程类型：混合式。 */
    public static final String TYPE_MIX = "混合";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编号，如 KC2026-001。 */
    private String courseNo;

    /** 课程名称。 */
    private String name;

    /** 课程分类：安全环保 / 财务税务 / 生产技术 / 设备管理 / 职业素养 / 入职培训。 */
    private String category;

    /** 课程类型：点播 / 直播 / 面授 / 混合。 */
    private String courseType;

    /** 讲师。 */
    private String teacher;

    /** 讲师单位。 */
    private String teacherOrg;

    /** 课程时长（分钟）。 */
    private Integer duration;

    /** 学分。 */
    private BigDecimal credit;

    /** 难度：初级 / 中级 / 高级。 */
    private String level;

    /** 封面图地址。 */
    private String cover;

    /** 课件 / 播放地址。 */
    private String url;

    /** 课程简介。 */
    private String intro;

    /** 适用对象。 */
    private String target;

    /** 状态：草稿 / 已发布 / 已下架。 */
    private String status;

    /** 学习人次（由参训记录回填）。 */
    private Integer learnCount;

    /** 是否需要考试：1 是 0 否。 */
    private Integer examFlag;

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

    public String getCourseNo() {
        return courseNo;
    }

    public void setCourseNo(String courseNo) {
        this.courseNo = courseNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCourseType() {
        return courseType;
    }

    public void setCourseType(String courseType) {
        this.courseType = courseType;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getTeacherOrg() {
        return teacherOrg;
    }

    public void setTeacherOrg(String teacherOrg) {
        this.teacherOrg = teacherOrg;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLearnCount() {
        return learnCount;
    }

    public void setLearnCount(Integer learnCount) {
        this.learnCount = learnCount;
    }

    public Integer getExamFlag() {
        return examFlag;
    }

    public void setExamFlag(Integer examFlag) {
        this.examFlag = examFlag;
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
