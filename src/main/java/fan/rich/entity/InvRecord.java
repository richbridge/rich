package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点明细。
 *
 * 一条记录 = 一个标的在某次盘点任务中的实盘结果。
 *
 * <p>标的通过 {@link #subjectType} + {@link #subjectId} 关联到固定资产 / 库存物资 / 食堂物料，
 * 同时冗余名称、规格、存放地等信息——因为盘点任务执行期间源档案可能被改名或删除，
 * 冗余能保证历史盘点单始终可读。
 *
 * <p>{@code actualQty} 为空表示「未盘」；录入后自动算出 {@code diffQty} 与 {@code result}。
 * 差异经 {@code handleStatus} 走「待处理 → 已确认 → 已调账」流程，调账后会回写源台账。
 */
@TableName("inv_record")
public class InvRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务ID。 */
    private Long taskId;

    /** 任务单号。 */
    private String taskNo;

    /** 标的类型：ASSET 固定资产 / GOODS 库存物资 / CANTEEN 食堂物资。 */
    private String subjectType;

    /** 标的ID。 */
    private Long subjectId;

    /** 标的编码。 */
    private String subjectCode;

    /** 二维码码值。 */
    private String qrCode;

    /** 标的名称。 */
    private String subjectName;

    /** 分类。 */
    private String category;

    /** 规格型号。 */
    private String spec;

    /** 计量单位。 */
    private String unit;

    /** 存放地点 / 仓库。 */
    private String location;

    /** 归属部门。 */
    private String deptName;

    /** 保管人。 */
    private String custodian;

    /** 账面数量（任务创建时冻结）。 */
    private BigDecimal bookQty;

    /** 实盘数量。 */
    private BigDecimal actualQty;

    /** 差异数量 = 实盘 - 账面。 */
    private BigDecimal diffQty;

    /** 单价(元)。 */
    private BigDecimal unitPrice;

    /** 差异金额(元)。 */
    private BigDecimal diffAmount;

    /** 盘点结果：未盘/正常/盘盈/盘亏。 */
    private String result;

    /** 数据来源：APP扫码/PC扫码/手工录入。 */
    private String checkSource;

    /** 盘点人。 */
    private String checker;

    /** 盘点时间。 */
    private LocalDateTime checkTime;

    /** 处理状态：待处理/已确认/已调账/无需处理。 */
    private String handleStatus;

    /** 处理意见。 */
    private String handleOpinion;

    /** 处理人。 */
    private String handler;

    /** 处理时间。 */
    private LocalDateTime handleTime;

    /** 盘点备注。 */
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

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getCustodian() {
        return custodian;
    }

    public void setCustodian(String custodian) {
        this.custodian = custodian;
    }

    public BigDecimal getBookQty() {
        return bookQty;
    }

    public void setBookQty(BigDecimal bookQty) {
        this.bookQty = bookQty;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public BigDecimal getDiffQty() {
        return diffQty;
    }

    public void setDiffQty(BigDecimal diffQty) {
        this.diffQty = diffQty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getCheckSource() {
        return checkSource;
    }

    public void setCheckSource(String checkSource) {
        this.checkSource = checkSource;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
    }

    public String getHandleStatus() {
        return handleStatus;
    }

    public void setHandleStatus(String handleStatus) {
        this.handleStatus = handleStatus;
    }

    public String getHandleOpinion() {
        return handleOpinion;
    }

    public void setHandleOpinion(String handleOpinion) {
        this.handleOpinion = handleOpinion;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
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
