package fan.rich.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售台账核心指标。
 *
 * <p>口径与《销售统计》汇总表保持一致：
 * 精矿按干重(t)与金含量(g)统计，合质金按重量(g)统计，点价敞口取点价情况台账。
 */
public class LedgerSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 精矿发货批次数量。 */
    private long concentrateBatches;

    /** 精矿干重合计（t）。 */
    private BigDecimal dryWeight;

    /** 精矿平均结算品位（g/t）。 */
    private BigDecimal avgGrade;

    /** 精矿实际金含量合计（g）。 */
    private BigDecimal concentrateGold;

    /** 合质金批次数量。 */
    private long doreBatches;

    /** 合质金发货重量合计（g）。 */
    private BigDecimal doreWeight;

    /** 合质金实际金含量合计（g）。 */
    private BigDecimal doreGold;

    /** 金含量总计（g）：精矿 + 合质金。 */
    private BigDecimal totalGold;

    /** 返金量合计（g）。 */
    private BigDecimal returnGold;

    /** 预提金量合计（g）。 */
    private BigDecimal preExtractGold;

    /** 已结算合计（g）。 */
    private BigDecimal settledGold;

    /** 库存（g），取点价情况台账合计。 */
    private BigDecimal stockGold;

    /** 欠金量（g），取点价情况台账合计。 */
    private BigDecimal oweGold;

    /** 点价量合计（g）。 */
    private BigDecimal fixQty;

    /** 提金结算金额合计（元）。 */
    private BigDecimal settlementAmount;

    /** 提金重量合计（g）。 */
    private BigDecimal settlementWeight;

    /** 加工费合计（元）。 */
    private BigDecimal processFee;

    public long getConcentrateBatches() {
        return concentrateBatches;
    }

    public void setConcentrateBatches(long concentrateBatches) {
        this.concentrateBatches = concentrateBatches;
    }

    public BigDecimal getDryWeight() {
        return dryWeight;
    }

    public void setDryWeight(BigDecimal dryWeight) {
        this.dryWeight = dryWeight;
    }

    public BigDecimal getAvgGrade() {
        return avgGrade;
    }

    public void setAvgGrade(BigDecimal avgGrade) {
        this.avgGrade = avgGrade;
    }

    public BigDecimal getConcentrateGold() {
        return concentrateGold;
    }

    public void setConcentrateGold(BigDecimal concentrateGold) {
        this.concentrateGold = concentrateGold;
    }

    public long getDoreBatches() {
        return doreBatches;
    }

    public void setDoreBatches(long doreBatches) {
        this.doreBatches = doreBatches;
    }

    public BigDecimal getDoreWeight() {
        return doreWeight;
    }

    public void setDoreWeight(BigDecimal doreWeight) {
        this.doreWeight = doreWeight;
    }

    public BigDecimal getDoreGold() {
        return doreGold;
    }

    public void setDoreGold(BigDecimal doreGold) {
        this.doreGold = doreGold;
    }

    public BigDecimal getTotalGold() {
        return totalGold;
    }

    public void setTotalGold(BigDecimal totalGold) {
        this.totalGold = totalGold;
    }

    public BigDecimal getReturnGold() {
        return returnGold;
    }

    public void setReturnGold(BigDecimal returnGold) {
        this.returnGold = returnGold;
    }

    public BigDecimal getPreExtractGold() {
        return preExtractGold;
    }

    public void setPreExtractGold(BigDecimal preExtractGold) {
        this.preExtractGold = preExtractGold;
    }

    public BigDecimal getSettledGold() {
        return settledGold;
    }

    public void setSettledGold(BigDecimal settledGold) {
        this.settledGold = settledGold;
    }

    public BigDecimal getStockGold() {
        return stockGold;
    }

    public void setStockGold(BigDecimal stockGold) {
        this.stockGold = stockGold;
    }

    public BigDecimal getOweGold() {
        return oweGold;
    }

    public void setOweGold(BigDecimal oweGold) {
        this.oweGold = oweGold;
    }

    public BigDecimal getFixQty() {
        return fixQty;
    }

    public void setFixQty(BigDecimal fixQty) {
        this.fixQty = fixQty;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(BigDecimal settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    public BigDecimal getSettlementWeight() {
        return settlementWeight;
    }

    public void setSettlementWeight(BigDecimal settlementWeight) {
        this.settlementWeight = settlementWeight;
    }

    public BigDecimal getProcessFee() {
        return processFee;
    }

    public void setProcessFee(BigDecimal processFee) {
        this.processFee = processFee;
    }
}
