package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点价记录。
 *
 * <p>对应《销售统计》中的「2026年点价统计表」：按品种记录点价日、点价、结算价、
 * 点价数量与点价成交额。
 */
@TableName("sale_pricing")
public class SalePricing implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 点价品种：浮选金。 */
    public static final String VARIETY_FLOAT = "浮选金";

    /** 点价品种：合质金。 */
    public static final String VARIETY_DORE = "合质金";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点价品种：浮选金 / 合质金。 */
    private String variety;

    /** 点价日。 */
    private LocalDate priceDate;

    /** 点价（元/克）。 */
    private BigDecimal fixPrice;

    /** 结算价（元/克）。 */
    private BigDecimal settlePrice;

    /** 点价数量（g）。 */
    private BigDecimal quantity;

    /** 平均点价（元/克），汇总行使用。 */
    private BigDecimal avgPrice;

    /** 点价成交额（万元）。 */
    private BigDecimal amount;

    /** 分组标签：如 26年点价。 */
    private String groupTag;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public BigDecimal getFixPrice() {
        return fixPrice;
    }

    public void setFixPrice(BigDecimal fixPrice) {
        this.fixPrice = fixPrice;
    }

    public BigDecimal getSettlePrice() {
        return settlePrice;
    }

    public void setSettlePrice(BigDecimal settlePrice) {
        this.settlePrice = settlePrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getGroupTag() {
        return groupTag;
    }

    public void setGroupTag(String groupTag) {
        this.groupTag = groupTag;
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
