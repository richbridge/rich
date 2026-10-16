package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 现货行情（SMM 上海有色网 / CJ 长江有色网 / SGE 上海黄金交易所）。
 *
 * <p>三者的差异由 source 字段区分：
 * <ul>
 *   <li>SMM / CJ：仅有最高、最低、均价、涨跌，无开盘价；</li>
 *   <li>SGE：xlsx 中提供开高低收、加权均价、成交量与成交额。</li>
 * </ul>
 * 唯一键：(source, product_id, trade_date)，重复采集为幂等 upsert。
 */
@TableName("md_spot_price")
public class MdSpotPrice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源编码。 */
    private String source;

    /** 站点侧品种 ID（SGE 为合约代码）。 */
    private String productId;

    /** 品种名称冗余存储，避免列表页频繁 join。 */
    private String productName;

    /** 交易日期。 */
    private LocalDate tradeDate;

    /** 计价单位。 */
    private String unit;

    private BigDecimal priceOpen;

    private BigDecimal priceHigh;

    private BigDecimal priceLow;

    /** 收盘价；SMM / CJ 场景下用当日均价填充以保证列不为空。 */
    private BigDecimal priceClose;

    /** 加权平均价。 */
    private BigDecimal priceAvg;

    /** 涨跌值。 */
    private BigDecimal changeValue;

    /** 涨跌幅（%）。 */
    private BigDecimal changeRate;

    /** 成交量。 */
    private BigDecimal volume;

    /** 成交金额。 */
    private BigDecimal amount;

    /** 持仓量（SGE 才有）。 */
    private BigDecimal openInterest;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPriceOpen() {
        return priceOpen;
    }

    public void setPriceOpen(BigDecimal priceOpen) {
        this.priceOpen = priceOpen;
    }

    public BigDecimal getPriceHigh() {
        return priceHigh;
    }

    public void setPriceHigh(BigDecimal priceHigh) {
        this.priceHigh = priceHigh;
    }

    public BigDecimal getPriceLow() {
        return priceLow;
    }

    public void setPriceLow(BigDecimal priceLow) {
        this.priceLow = priceLow;
    }

    public BigDecimal getPriceClose() {
        return priceClose;
    }

    public void setPriceClose(BigDecimal priceClose) {
        this.priceClose = priceClose;
    }

    public BigDecimal getPriceAvg() {
        return priceAvg;
    }

    public void setPriceAvg(BigDecimal priceAvg) {
        this.priceAvg = priceAvg;
    }

    public BigDecimal getChangeValue() {
        return changeValue;
    }

    public void setChangeValue(BigDecimal changeValue) {
        this.changeValue = changeValue;
    }

    public BigDecimal getChangeRate() {
        return changeRate;
    }

    public void setChangeRate(BigDecimal changeRate) {
        this.changeRate = changeRate;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(BigDecimal openInterest) {
        this.openInterest = openInterest;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
