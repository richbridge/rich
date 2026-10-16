package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 食堂费用支出单。
 *
 * <p>除食材采购之外的食堂运行性支出：燃料动力、水电燃气、设备维修、低值易耗、
 * 保洁防疫、劳务用工等。付款后在财务台账记一笔「费用支出」流水。
 */
@TableName("canteen_fee")
public class CanteenFee implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已审批。 */
    public static final String STATUS_APPROVED = "已审批";

    /** 状态：已付款。 */
    public static final String STATUS_PAID = "已付款";

    /** 付款状态：未付款。 */
    public static final String PAY_UNPAID = "未付款";

    /** 付款状态：已付款。 */
    public static final String PAY_PAID = "已付款";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单据编号，如 FY2026-0001。 */
    private String feeNo;

    /** 费用发生日期。 */
    private LocalDate feeDate;

    /** 费用类别：燃料动力 / 水电燃气 / 设备维修 / 低值易耗 / 劳保用品 / 保洁防疫 / 劳务用工 / 检测检验 / 其他支出。 */
    private String feeType;

    /** 不含税金额(元)。 */
    private BigDecimal amount;

    /** 税额(元)。 */
    private BigDecimal taxAmount;

    /** 价税合计(元)。 */
    private BigDecimal totalAmount;

    /** 收款单位ID。 */
    private Long supplierId;

    /** 收款单位名称。 */
    private String supplierName;

    /** 票据号码。 */
    private String invoiceNo;

    /** 付款方式：银行转账 / 现金 / 公务卡 / 签单挂账。 */
    private String payType;

    /** 付款状态：未付款 / 已付款。 */
    private String payStatus;

    /** 付款日期。 */
    private LocalDate payDate;

    /** 经办人。 */
    private String handler;

    /** 审批人。 */
    private String approver;

    /** 状态：草稿 / 已审批 / 已付款。 */
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

    public String getFeeNo() {
        return feeNo;
    }

    public void setFeeNo(String feeNo) {
        this.feeNo = feeNo;
    }

    public LocalDate getFeeDate() {
        return feeDate;
    }

    public void setFeeDate(LocalDate feeDate) {
        this.feeDate = feeDate;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
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
