package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 固定资产卡片。
 *
 * 每个实物资产一张卡片，是扫码盘点的一类标的。
 *
 * <p>{@link #qrCode} 是贴牌二维码的内容，全局唯一，APP / 扫码枪扫到后即由它定位到本行。
 * 码值形如 {@code AST-GD20260001}，与业务编号 {@link #assetCode} 一一对应但相互独立，
 * 这样即便资产编号规则调整，已贴的二维码也不用重打。
 */
@TableName("ast_asset")
public class Asset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产编号，如 GD-2026-0001。 */
    private String assetCode;

    /** 二维码码值，全局唯一。 */
    private String qrCode;

    /** 资产名称。 */
    private String assetName;

    /** 资产分类：房屋建筑物/机器设备/运输设备/电子设备/办公家具/其他。 */
    private String category;

    /** 规格型号。 */
    private String spec;

    /** 计量单位：台/套/件/辆/平方米。 */
    private String unit;

    /** 账面数量。 */
    private BigDecimal quantity;

    /** 资产原值(元)。 */
    private BigDecimal originalValue;

    /** 资产净值(元)。 */
    private BigDecimal netValue;

    /** 购置日期。 */
    private LocalDate buyDate;

    /** 预计使用年限(年)。 */
    private Integer usefulLife;

    /** 使用部门。 */
    private String useDept;

    /** 存放地点。 */
    private String location;

    /** 保管人。 */
    private String custodian;

    /** 供应商。 */
    private String supplierName;

    /** 状态：在用/闲置/维修/借出/报废。 */
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

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(BigDecimal originalValue) {
        this.originalValue = originalValue;
    }

    public BigDecimal getNetValue() {
        return netValue;
    }

    public void setNetValue(BigDecimal netValue) {
        this.netValue = netValue;
    }

    public LocalDate getBuyDate() {
        return buyDate;
    }

    public void setBuyDate(LocalDate buyDate) {
        this.buyDate = buyDate;
    }

    public Integer getUsefulLife() {
        return usefulLife;
    }

    public void setUsefulLife(Integer usefulLife) {
        this.usefulLife = usefulLife;
    }

    public String getUseDept() {
        return useDept;
    }

    public void setUseDept(String useDept) {
        this.useDept = useDept;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCustodian() {
        return custodian;
    }

    public void setCustodian(String custodian) {
        this.custodian = custodian;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
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
