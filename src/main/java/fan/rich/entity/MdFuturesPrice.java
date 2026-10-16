package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 期货行情（上海期货交易所延时行情）。
 *
 * <p>唯一键：(instrument_id, contract_code, trade_date)，重复采集为幂等 upsert。
 */
@TableName("md_futures_price")
public class MdFuturesPrice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交易所，固定 SHFE。 */
    private String exchange;

    /** 品种代号：au / ag / cu / al / pb / zn / ni / sn 等。 */
    private String instrumentId;

    /** 品种中文名（由代号映射得到）。 */
    private String instrumentName;

    /** 合约代码：au2610、cu2611 等。 */
    private String contractCode;

    /** 行情日期（取自 updatetime 的日期部分）。 */
    private LocalDate tradeDate;

    /** 昨结算价。 */
    private BigDecimal presettlementPrice;

    private BigDecimal openPrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    /** 最新价（延时行情接口只给 lastprice）。 */
    private BigDecimal closePrice;

    /**
     * 当日结算价。
     *
     * <p>只有收盘后的正式日行情文件（{@code kx{yyyyMMdd}.dat}）会下发 {@code SETTLEMENTPRICE}，
     * 盘中的延时行情接口没有这个字段，因此取不到时保持 null 而不是补 0——
     * 结算价是加权均价，与收盘价是两个口径，混用会让资金测算失之毫厘。
     */
    private BigDecimal settlementPrice;

    /** 买价。 */
    private BigDecimal bidPrice;

    /** 卖价。 */
    private BigDecimal askPrice;

    /** 涨跌值（接口字段 upperdown）。 */
    private BigDecimal changeValue;

    /** 成交量（手）。 */
    private BigDecimal volume;

    /** 成交额（元）。 */
    private BigDecimal turnover;

    /** 持仓量（手）。 */
    private BigDecimal openInterest;

    /** 站点数据更新时间。 */
    private LocalDateTime quoteTime;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getContractCode() {
        return contractCode;
    }

    public void setContractCode(String contractCode) {
        this.contractCode = contractCode;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getPresettlementPrice() {
        return presettlementPrice;
    }

    public void setPresettlementPrice(BigDecimal presettlementPrice) {
        this.presettlementPrice = presettlementPrice;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getSettlementPrice() {
        return settlementPrice;
    }

    public void setSettlementPrice(BigDecimal settlementPrice) {
        this.settlementPrice = settlementPrice;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(BigDecimal bidPrice) {
        this.bidPrice = bidPrice;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(BigDecimal askPrice) {
        this.askPrice = askPrice;
    }

    public BigDecimal getChangeValue() {
        return changeValue;
    }

    public void setChangeValue(BigDecimal changeValue) {
        this.changeValue = changeValue;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public BigDecimal getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(BigDecimal openInterest) {
        this.openInterest = openInterest;
    }

    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
