package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 就餐收入日报。
 *
 * <p>食堂的收入侧：按「营业日期 + 餐次 + 就餐类型」记录每一笔就餐收入。
 * 其中职工餐通常由公司按人次给予补贴（{@code subsidyAmount}），
 * 实际向就餐者收取的部分为 {@code actualAmount}，两者合计为应收。
 *
 * <p>确认后在财务台账记一笔「营业收入」流水。
 */
@TableName("canteen_sale")
public class CanteenSale implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿（未确认，不记账）。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已确认（已登记营业收入流水）。 */
    public static final String STATUS_CONFIRMED = "已确认";

    /** 状态：已记账（并入月度报表）。 */
    public static final String STATUS_POSTED = "已记账";

    /** 就餐类型：职工餐。 */
    public static final String CUST_STAFF = "职工餐";

    /** 就餐类型：加班餐。 */
    public static final String CUST_OVERTIME = "加班餐";

    /** 就餐类型：客饭。 */
    public static final String CUST_GUEST = "客饭";

    /** 就餐类型：接待餐。 */
    public static final String CUST_RECEPTION = "接待餐";

    /** 就餐类型：外来人员。 */
    public static final String CUST_OUTSIDER = "外来人员";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单据编号，如 SR2026-0001。 */
    private String saleNo;

    /** 营业日期。 */
    private LocalDate bizDate;

    /** 餐次：早餐 / 午餐 / 晚餐 / 夜宵。 */
    private String mealType;

    /** 就餐类型：职工餐 / 加班餐 / 客饭 / 接待餐 / 外来人员。 */
    private String customerType;

    /** 就餐人次。 */
    private Integer peopleCount;

    /** 每人次标准(元)。 */
    private BigDecimal unitPrice;

    /** 应收金额(元) = 人次 × 标准。 */
    private BigDecimal receivable;

    /** 实收金额(元)，个人支付部分。 */
    private BigDecimal actualAmount;

    /** 公司补贴金额(元)。 */
    private BigDecimal subsidyAmount;

    /** 收款方式：饭卡 / 扫码支付 / 现金 / 签单挂账。 */
    private String payType;

    /** 就餐窗口 / 餐厅。 */
    private String windowName;

    /** 收银 / 统计人。 */
    private String operator;

    /** 状态：草稿 / 已确认 / 已记账。 */
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

    public String getSaleNo() {
        return saleNo;
    }

    public void setSaleNo(String saleNo) {
        this.saleNo = saleNo;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public Integer getPeopleCount() {
        return peopleCount;
    }

    public void setPeopleCount(Integer peopleCount) {
        this.peopleCount = peopleCount;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getReceivable() {
        return receivable;
    }

    public void setReceivable(BigDecimal receivable) {
        this.receivable = receivable;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public BigDecimal getSubsidyAmount() {
        return subsidyAmount;
    }

    public void setSubsidyAmount(BigDecimal subsidyAmount) {
        this.subsidyAmount = subsidyAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getWindowName() {
        return windowName;
    }

    public void setWindowName(String windowName) {
        this.windowName = windowName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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
