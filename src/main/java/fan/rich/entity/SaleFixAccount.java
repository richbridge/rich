package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点价情况台账。
 *
 * <p>对应《销售统计》中的「点价情况表」：按点价种类跟踪上年余量、点价量、已发货量、
 * 已提金、库存与欠金量，是销售模块最关键的资金/金属敞口指标。
 */
@TableName("sale_fix_account")
public class SaleFixAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计年度。 */
    private Integer statYear;

    /** 点价种类：国大精矿 / 合质金（国大） / 金翅岭精矿。 */
    private String variety;

    /** 上年余量（g）。 */
    private BigDecimal prevBalance;

    /** 点价量（g）。 */
    private BigDecimal fixQty;

    /** 已发货量（g）。 */
    private BigDecimal shippedQty;

    /** 已提金（g）。 */
    private BigDecimal extractedQty;

    /** 库存（g）。 */
    private BigDecimal stockQty;

    /** 欠金量（g）。 */
    private BigDecimal oweQty;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getStatYear() {
        return statYear;
    }

    public void setStatYear(Integer statYear) {
        this.statYear = statYear;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public BigDecimal getPrevBalance() {
        return prevBalance;
    }

    public void setPrevBalance(BigDecimal prevBalance) {
        this.prevBalance = prevBalance;
    }

    public BigDecimal getFixQty() {
        return fixQty;
    }

    public void setFixQty(BigDecimal fixQty) {
        this.fixQty = fixQty;
    }

    public BigDecimal getShippedQty() {
        return shippedQty;
    }

    public void setShippedQty(BigDecimal shippedQty) {
        this.shippedQty = shippedQty;
    }

    public BigDecimal getExtractedQty() {
        return extractedQty;
    }

    public void setExtractedQty(BigDecimal extractedQty) {
        this.extractedQty = extractedQty;
    }

    public BigDecimal getStockQty() {
        return stockQty;
    }

    public void setStockQty(BigDecimal stockQty) {
        this.stockQty = stockQty;
    }

    public BigDecimal getOweQty() {
        return oweQty;
    }

    public void setOweQty(BigDecimal oweQty) {
        this.oweQty = oweQty;
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
