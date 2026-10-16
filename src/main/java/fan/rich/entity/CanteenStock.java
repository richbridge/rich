package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存结存。
 *
 * <p>一个物料一行，随采购入库、领用出库、盘点调整实时滚动。
 * 采用<b>移动加权平均法</b>计价：入库后新单价 = (原结存金额 + 本次入库金额) / (原结存数量 + 本次入库数量)；
 * 出库按最新结存单价结转，不改动单价。
 *
 * <p>与档案表 {@code canteen_material} 的安全库存、最高库存配合生成库存预警。
 */
@TableName("canteen_stock")
public class CanteenStock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料ID。 */
    private Long materialId;

    /** 物料编码。 */
    private String materialCode;

    /** 物料名称。 */
    private String materialName;

    /** 物料分类。 */
    private String category;

    /** 规格。 */
    private String spec;

    /** 计量单位。 */
    private String unit;

    /** 结存数量。 */
    private BigDecimal quantity;

    /** 加权平均单价(元)。 */
    private BigDecimal avgPrice;

    /** 结存金额(元)。 */
    private BigDecimal totalAmount;

    /** 安全库存（冗余档案，便于预警）。 */
    private BigDecimal minStock;

    /** 最高库存（冗余档案，便于预警）。 */
    private BigDecimal maxStock;

    /** 最近入库日期。 */
    private LocalDate lastInDate;

    /** 最近出库日期。 */
    private LocalDate lastOutDate;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
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

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public LocalDate getLastInDate() {
        return lastInDate;
    }

    public void setLastInDate(LocalDate lastInDate) {
        this.lastInDate = lastInDate;
    }

    public LocalDate getLastOutDate() {
        return lastOutDate;
    }

    public void setLastOutDate(LocalDate lastOutDate) {
        this.lastOutDate = lastOutDate;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
