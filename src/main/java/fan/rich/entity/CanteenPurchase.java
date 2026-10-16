package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购入库单（主表）。
 *
 * <p>食堂向供应商采购食材物资的入库凭证。金额明细挂在 {@code canteen_purchase_item} 上，
 * 主表金额由明细汇总。单据过账后按<b>移动加权平均法</b>更新 {@code canteen_stock} 的结存单价与金额。
 *
 * <p>业务口径：不含税金额合计 + 税额合计 = 价税合计；已付金额与价税合计之差即应付账款。
 * 付款时在财务台账记一笔「采购付款」支出流水。
 */
@TableName("canteen_purchase")
public class CanteenPurchase implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿（未收货，不影响库存）。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已收货（验收通过，待入库）。 */
    public static final String STATUS_RECEIVED = "已收货";

    /** 状态：已入库（库存已更新，视为已过账）。 */
    public static final String STATUS_STOCKED = "已入库";

    /** 状态：已结算（款项结清）。 */
    public static final String STATUS_SETTLED = "已结算";

    /** 付款状态：未付款。 */
    public static final String PAY_UNPAID = "未付款";

    /** 付款状态：部分付款。 */
    public static final String PAY_PART = "部分付款";

    /** 付款状态：已付款。 */
    public static final String PAY_PAID = "已付款";

    /** 采购类型：日常采购。 */
    public static final String TYPE_DAILY = "日常采购";

    /** 采购类型：定点采购。 */
    public static final String TYPE_FIXED = "定点采购";

    /** 采购类型：紧急采购。 */
    public static final String TYPE_URGENT = "紧急采购";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号，如 CG2026-0001。 */
    private String purchaseNo;

    /** 供应商ID。 */
    private Long supplierId;

    /** 供应商名称。 */
    private String supplierName;

    /** 入库日期。 */
    private LocalDate purchaseDate;

    /** 采购类型：日常采购 / 定点采购 / 紧急采购。 */
    private String purchaseType;

    /** 采购品类，取明细中金额占比最大的分类。 */
    private String category;

    /** 不含税金额合计(元)。 */
    private BigDecimal totalAmount;

    /** 税额合计(元)。 */
    private BigDecimal taxAmount;

    /** 价税合计(元)。 */
    private BigDecimal totalWithTax;

    /** 发票号码。 */
    private String invoiceNo;

    /** 付款状态：未付款 / 部分付款 / 已付款。 */
    private String payStatus;

    /** 已付金额(元)。 */
    private BigDecimal paidAmount;

    /** 采购人。 */
    private String buyer;

    /** 验收人。 */
    private String checker;

    /** 经办人。 */
    private String handler;

    /** 明细条数。 */
    private Integer itemCount;

    /** 状态：草稿 / 已收货 / 已入库 / 已结算。 */
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

    public String getPurchaseNo() {
        return purchaseNo;
    }

    public void setPurchaseNo(String purchaseNo) {
        this.purchaseNo = purchaseNo;
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

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getPurchaseType() {
        return purchaseType;
    }

    public void setPurchaseType(String purchaseType) {
        this.purchaseType = purchaseType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalWithTax() {
        return totalWithTax;
    }

    public void setTotalWithTax(BigDecimal totalWithTax) {
        this.totalWithTax = totalWithTax;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getBuyer() {
        return buyer;
    }

    public void setBuyer(String buyer) {
        this.buyer = buyer;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
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
