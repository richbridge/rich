package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 档案销毁清册。
 */
@TableName("arch_destroy")
public class ArchDestroy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销毁单号，如 XH2026-0001。 */
    private String destroyNo;

    /** 清册题名。 */
    private String title;

    /** 档案门类。 */
    private String category;

    /** 销毁卷数，由明细汇总回填。 */
    private Integer volumeCount;

    /** 销毁页数，由明细汇总回填。 */
    private Integer pageCount;

    /** 销毁原因。 */
    private String reason;

    /** 申请部门。 */
    private String applyDept;

    /** 申请人。 */
    private String applicant;

    /** 申请日期。 */
    private LocalDate applyDate;

    /** 审批人。 */
    private String approver;

    /** 审批日期。 */
    private LocalDate approveDate;

    /** 监销人。 */
    private String witness;

    /** 销毁方式：粉碎 / 焚烧 / 电子数据删除。 */
    private String method;

    /** 销毁日期。 */
    private LocalDate destroyDate;

    /** 状态：草稿 / 待审批 / 已审批 / 已销毁。 */
    private String status;

    /** 备注。 */
    private String remark;

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

    public String getDestroyNo() {
        return destroyNo;
    }

    public void setDestroyNo(String destroyNo) {
        this.destroyNo = destroyNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getVolumeCount() {
        return volumeCount;
    }

    public void setVolumeCount(Integer volumeCount) {
        this.volumeCount = volumeCount;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApplyDept() {
        return applyDept;
    }

    public void setApplyDept(String applyDept) {
        this.applyDept = applyDept;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public LocalDate getApplyDate() {
        return applyDate;
    }

    public void setApplyDate(LocalDate applyDate) {
        this.applyDate = applyDate;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public LocalDate getApproveDate() {
        return approveDate;
    }

    public void setApproveDate(LocalDate approveDate) {
        this.approveDate = approveDate;
    }

    public String getWitness() {
        return witness;
    }

    public void setWitness(String witness) {
        this.witness = witness;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public LocalDate getDestroyDate() {
        return destroyDate;
    }

    public void setDestroyDate(LocalDate destroyDate) {
        this.destroyDate = destroyDate;
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
