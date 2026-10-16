package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提金结算与开票记录。
 *
 * <p>对应台账 Sheet1「精矿结算单提金开票情况」：按结算批次（如 浮精187-192回）
 * 记录提金日、结算价格、提金重量、结算金额与开票情况。
 */
@TableName("sale_settlement")
public class SaleSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算品种：浮选金 / 合质金。 */
    private String category;

    /** 结算批次，如 浮精187-192回。 */
    private String batchNo;

    /** 提金日。 */
    private LocalDate goldPickDate;

    /** 结算价格（元/克）。 */
    private BigDecimal settlePrice;

    /** 提金重量（g）。 */
    private BigDecimal goldWeight;

    /** 结算金额（元）。 */
    private BigDecimal settleAmount;

    /** 开票情况，如 8月开票。 */
    private String invoiceStatus;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public LocalDate getGoldPickDate() {
        return goldPickDate;
    }

    public void setGoldPickDate(LocalDate goldPickDate) {
        this.goldPickDate = goldPickDate;
    }

    public BigDecimal getSettlePrice() {
        return settlePrice;
    }

    public void setSettlePrice(BigDecimal settlePrice) {
        this.settlePrice = settlePrice;
    }

    public BigDecimal getGoldWeight() {
        return goldWeight;
    }

    public void setGoldWeight(BigDecimal goldWeight) {
        this.goldWeight = goldWeight;
    }

    public BigDecimal getSettleAmount() {
        return settleAmount;
    }

    public void setSettleAmount(BigDecimal settleAmount) {
        this.settleAmount = settleAmount;
    }

    public String getInvoiceStatus() {
        return invoiceStatus;
    }

    public void setInvoiceStatus(String invoiceStatus) {
        this.invoiceStatus = invoiceStatus;
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
}
