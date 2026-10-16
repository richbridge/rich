package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食堂供应商档案。
 *
 * <p>食堂物资采购的来源单位：粮油副食、肉禽水产、蔬菜、燃气等定点供货商。
 * 记录主营品类、联系人与结算方式，采购入库单从这里选择供应商。
 */
@TableName("canteen_supplier")
public class CanteenSupplier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商编码，如 GYS-01。 */
    private String supplierCode;

    /** 供应商名称。 */
    private String supplierName;

    /** 主营品类：粮油副食 / 蔬菜水果 / 肉禽水产 / 调料饮品 / 燃料燃气 / 劳保用品 / 设备维修。 */
    private String category;

    /** 联系人。 */
    private String contact;

    /** 联系电话。 */
    private String phone;

    /** 地址。 */
    private String address;

    /** 结算方式：现结 / 月结 / 季结。 */
    private String settleType;

    /** 开户行及账号。 */
    private String bankAccount;

    /** 营业执照 / 食品经营许可证号。 */
    private String licenseNo;

    /** 累计采购金额(元)。 */
    private BigDecimal coopAmount;

    /** 状态：合作中 / 暂停 / 终止。 */
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

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSettleType() {
        return settleType;
    }

    public void setSettleType(String settleType) {
        this.settleType = settleType;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public BigDecimal getCoopAmount() {
        return coopAmount;
    }

    public void setCoopAmount(BigDecimal coopAmount) {
        this.coopAmount = coopAmount;
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
