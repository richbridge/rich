package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食堂食材 / 物料档案。
 *
 * <p>食堂进销存的最小核算单位。除食材外，燃料、低值易耗品也纳入同一本台账，
 * 通过 {@link #category} 区分。安全库存与最高库存用于库存预警。
 */
@TableName("canteen_material")
public class CanteenMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料编码，如 SC-01。 */
    private String materialCode;

    /** 物料名称。 */
    private String materialName;

    /** 分类：粮油米面 / 蔬菜豆制品 / 肉禽蛋 / 水产海鲜 / 调料饮品 / 燃料 / 低值易耗。 */
    private String category;

    /** 规格型号，如 25kg/袋。 */
    private String spec;

    /** 计量单位：斤 / kg / 桶 / 袋 / 箱 / 个。 */
    private String unit;

    /** 二维码码值，供扫码盘点定位（食堂物料作为盘点标的之一）。 */
    private String qrCode;

    /** 参考单价(元)。 */
    private BigDecimal unitPrice;

    /** 安全库存（下限），低于此值预警。 */
    private BigDecimal minStock;

    /** 最高库存（上限），高于此值预警。 */
    private BigDecimal maxStock;

    /** 保质天数。 */
    private Integer shelfDays;

    /** 默认供应商ID。 */
    private Long supplierId;

    /** 默认供应商名称。 */
    private String supplierName;

    /** 状态：启用 / 停用。 */
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

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(BigDecimal maxStock) {
        this.maxStock = maxStock;
    }

    public Integer getShelfDays() {
        return shelfDays;
    }

    public void setShelfDays(Integer shelfDays) {
        this.shelfDays = shelfDays;
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
