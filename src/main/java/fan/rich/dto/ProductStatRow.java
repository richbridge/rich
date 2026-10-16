package fan.rich.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 按发货品种的汇总行（对齐《销售统计》「按发货品种分类」表）。
 */
public class ProductStatRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 品种名称。 */
    private String name;

    /** 重量口径说明：干重(t) / 重量(g)。 */
    private String weightLabel;

    /** 重量。 */
    private BigDecimal weight;

    /** 实际金含量（g）。 */
    private BigDecimal goldContent;

    /** 返金量（g）。 */
    private BigDecimal returnGold;

    /** 已结算（g）。 */
    private BigDecimal settled;

    /** 库存（g）。 */
    private BigDecimal stock;

    /** 欠金量（g）。 */
    private BigDecimal owe;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWeightLabel() {
        return weightLabel;
    }

    public void setWeightLabel(String weightLabel) {
        this.weightLabel = weightLabel;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getGoldContent() {
        return goldContent;
    }

    public void setGoldContent(BigDecimal goldContent) {
        this.goldContent = goldContent;
    }

    public BigDecimal getReturnGold() {
        return returnGold;
    }

    public void setReturnGold(BigDecimal returnGold) {
        this.returnGold = returnGold;
    }

    public BigDecimal getSettled() {
        return settled;
    }

    public void setSettled(BigDecimal settled) {
        this.settled = settled;
    }

    public BigDecimal getStock() {
        return stock;
    }

    public void setStock(BigDecimal stock) {
        this.stock = stock;
    }

    public BigDecimal getOwe() {
        return owe;
    }

    public void setOwe(BigDecimal owe) {
        this.owe = owe;
    }
}
