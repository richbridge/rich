package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发货台账。
 *
 * <p>对应《金宝销售台账》中的「浮精汇总」与「合质金销售台账」两张明细表。两类业务的
 * 计量口径差异很大（精矿按吨、按品位折算金量；合质金按克、按成色折算），因此用一张
 * 宽表承载，通过 {@link #productType} 区分，未涉及的列留空。
 */
@TableName("sale_shipment")
public class SaleShipment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发货品种：浮选精矿粉。 */
    public static final String TYPE_CONCENTRATE = "CONCENTRATE";

    /** 发货品种：合质金。 */
    public static final String TYPE_DORE = "DORE";

    /** 台账行类别：正常发货批次。 */
    public static final String KIND_NORMAL = "NORMAL";

    /** 台账行类别：上年结余。 */
    public static final String KIND_PREV_YEAR = "PREV_YEAR";

    /** 台账行类别：仲裁补差。 */
    public static final String KIND_ARBITRATION = "ARBITRATION";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号（回数），如 26-001回。 */
    private String shipNo;

    /** 发货品种：CONCENTRATE 浮选精矿粉 / DORE 合质金。 */
    private String productType;

    /** 行类别：NORMAL 正常 / PREV_YEAR 上年结余 / ARBITRATION 仲裁。 */
    private String rowKind;

    /** 销售商 ID。 */
    private Long customerId;

    /** 销售商名称（冗余，便于列表展示与统计）。 */
    private String customerName;

    /** 发货日期。 */
    private LocalDate shipDate;

    // -------------------------------------------------- 浮选精矿专用字段

    /** 毛重（T）。 */
    private BigDecimal grossWeight;

    /** 皮重（T）。 */
    private BigDecimal tareWeight;

    /** 扣袋（T）。 */
    private BigDecimal bagDeduct;

    /** 净重（T）。 */
    private BigDecimal netWeight;

    /** 水份（%）。 */
    private BigDecimal moisture;

    /** 干重（t）。 */
    private BigDecimal dryWeight;

    /** 结算品位（g/t）。 */
    private BigDecimal settleGrade;

    /** 实际金含量（g）。 */
    private BigDecimal goldContent;

    /** 返金量（g）。 */
    private BigDecimal returnGold;

    /** 预提金量（g）。 */
    private BigDecimal preExtractGold;

    /** 品位差。 */
    private BigDecimal gradeDiff;

    /** 加工费（元）。 */
    private BigDecimal processFee;

    /** 是否提货。 */
    private String pickedUp;

    /** 仲裁前品位（g/t）。 */
    private BigDecimal arbGrade;

    /**
     * 仲裁差（g）：仲裁后与原结算之间的金量差异。
     *
     * <p>台账 Excel「备注」列里混着两类内容——数值即仲裁差（如 371），文字说明才是备注
     * （如「361-383仲裁」）。导入时按类型分流到本字段与 {@code remark}。
     * 台账展示的「返金量 + 仲裁差」= {@code returnGold + arbDiff}。
     */
    private BigDecimal arbDiff;

    // -------------------------------------------------- 合质金专用字段

    /** 样品编号。 */
    private String sampleNo;

    /** 溶火前国大重量（克）。 */
    private BigDecimal preMeltWeight;

    /** 杂质（克）。 */
    private BigDecimal impurity;

    /** 溶火后重量（克）。 */
    private BigDecimal postMeltWeight;

    /** 样品重量（克）。 */
    private BigDecimal sampleWeight;

    /** 留样后重量（克）。 */
    private BigDecimal retainedWeight;

    /** 金结算品位（%）。 */
    private BigDecimal goldGrade;

    /** 银结算品位（%）。 */
    private BigDecimal silverGrade;

    /** 银实际含量（g）。 */
    private BigDecimal silverContent;

    /** 返银量（g）。 */
    private BigDecimal returnSilver;

    /** 已结算（g）。 */
    private BigDecimal settledGold;

    /** 提金日。 */
    private LocalDate goldPickDate;

    /** 蒸前重量（克）。 */
    private BigDecimal preSteamWeight;

    /** 外检。 */
    private String externalCheck;

    /** 金银占比。 */
    private BigDecimal auAgRatio;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShipNo() {
        return shipNo;
    }

    public void setShipNo(String shipNo) {
        this.shipNo = shipNo;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getRowKind() {
        return rowKind;
    }

    public void setRowKind(String rowKind) {
        this.rowKind = rowKind;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getShipDate() {
        return shipDate;
    }

    public void setShipDate(LocalDate shipDate) {
        this.shipDate = shipDate;
    }

    public BigDecimal getGrossWeight() {
        return grossWeight;
    }

    public void setGrossWeight(BigDecimal grossWeight) {
        this.grossWeight = grossWeight;
    }

    public BigDecimal getTareWeight() {
        return tareWeight;
    }

    public void setTareWeight(BigDecimal tareWeight) {
        this.tareWeight = tareWeight;
    }

    public BigDecimal getBagDeduct() {
        return bagDeduct;
    }

    public void setBagDeduct(BigDecimal bagDeduct) {
        this.bagDeduct = bagDeduct;
    }

    public BigDecimal getNetWeight() {
        return netWeight;
    }

    public void setNetWeight(BigDecimal netWeight) {
        this.netWeight = netWeight;
    }

    public BigDecimal getMoisture() {
        return moisture;
    }

    public void setMoisture(BigDecimal moisture) {
        this.moisture = moisture;
    }

    public BigDecimal getDryWeight() {
        return dryWeight;
    }

    public void setDryWeight(BigDecimal dryWeight) {
        this.dryWeight = dryWeight;
    }

    public BigDecimal getSettleGrade() {
        return settleGrade;
    }

    public void setSettleGrade(BigDecimal settleGrade) {
        this.settleGrade = settleGrade;
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

    public BigDecimal getPreExtractGold() {
        return preExtractGold;
    }

    public void setPreExtractGold(BigDecimal preExtractGold) {
        this.preExtractGold = preExtractGold;
    }

    public BigDecimal getGradeDiff() {
        return gradeDiff;
    }

    public void setGradeDiff(BigDecimal gradeDiff) {
        this.gradeDiff = gradeDiff;
    }

    public BigDecimal getProcessFee() {
        return processFee;
    }

    public void setProcessFee(BigDecimal processFee) {
        this.processFee = processFee;
    }

    public String getPickedUp() {
        return pickedUp;
    }

    public void setPickedUp(String pickedUp) {
        this.pickedUp = pickedUp;
    }

    public BigDecimal getArbGrade() {
        return arbGrade;
    }

    public void setArbGrade(BigDecimal arbGrade) {
        this.arbGrade = arbGrade;
    }

    public BigDecimal getArbDiff() {
        return arbDiff;
    }

    public void setArbDiff(BigDecimal arbDiff) {
        this.arbDiff = arbDiff;
    }

    public String getSampleNo() {
        return sampleNo;
    }

    public void setSampleNo(String sampleNo) {
        this.sampleNo = sampleNo;
    }

    public BigDecimal getPreMeltWeight() {
        return preMeltWeight;
    }

    public void setPreMeltWeight(BigDecimal preMeltWeight) {
        this.preMeltWeight = preMeltWeight;
    }

    public BigDecimal getImpurity() {
        return impurity;
    }

    public void setImpurity(BigDecimal impurity) {
        this.impurity = impurity;
    }

    public BigDecimal getPostMeltWeight() {
        return postMeltWeight;
    }

    public void setPostMeltWeight(BigDecimal postMeltWeight) {
        this.postMeltWeight = postMeltWeight;
    }

    public BigDecimal getSampleWeight() {
        return sampleWeight;
    }

    public void setSampleWeight(BigDecimal sampleWeight) {
        this.sampleWeight = sampleWeight;
    }

    public BigDecimal getRetainedWeight() {
        return retainedWeight;
    }

    public void setRetainedWeight(BigDecimal retainedWeight) {
        this.retainedWeight = retainedWeight;
    }

    public BigDecimal getGoldGrade() {
        return goldGrade;
    }

    public void setGoldGrade(BigDecimal goldGrade) {
        this.goldGrade = goldGrade;
    }

    public BigDecimal getSilverGrade() {
        return silverGrade;
    }

    public void setSilverGrade(BigDecimal silverGrade) {
        this.silverGrade = silverGrade;
    }

    public BigDecimal getSilverContent() {
        return silverContent;
    }

    public void setSilverContent(BigDecimal silverContent) {
        this.silverContent = silverContent;
    }

    public BigDecimal getReturnSilver() {
        return returnSilver;
    }

    public void setReturnSilver(BigDecimal returnSilver) {
        this.returnSilver = returnSilver;
    }

    public BigDecimal getSettledGold() {
        return settledGold;
    }

    public void setSettledGold(BigDecimal settledGold) {
        this.settledGold = settledGold;
    }

    public LocalDate getGoldPickDate() {
        return goldPickDate;
    }

    public void setGoldPickDate(LocalDate goldPickDate) {
        this.goldPickDate = goldPickDate;
    }

    public BigDecimal getPreSteamWeight() {
        return preSteamWeight;
    }

    public void setPreSteamWeight(BigDecimal preSteamWeight) {
        this.preSteamWeight = preSteamWeight;
    }

    public String getExternalCheck() {
        return externalCheck;
    }

    public void setExternalCheck(String externalCheck) {
        this.externalCheck = externalCheck;
    }

    public BigDecimal getAuAgRatio() {
        return auAgRatio;
    }

    public void setAuAgRatio(BigDecimal auAgRatio) {
        this.auAgRatio = auAgRatio;
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
