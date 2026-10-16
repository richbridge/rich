package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 食堂收支流水台账（财务核心）。
 *
 * <p>食堂资金的进出流水，一本账管到底：营业收入、餐卡充值、采购付款、费用报销、
 * 盘盈盘亏、上级拨入等全部在这里留痕，并按发生顺序滚动结出账面余额。
 *
 * <p>{@code sourceType / sourceId / sourceNo} 记录流水来源单据，便于从业务单据反查流水、
 * 也避免同一笔业务重复记账（付款 / 确认时按来源单号去重）。
 */
@TableName("canteen_account")
public class CanteenAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收支方向：收入。 */
    public static final String DIR_IN = "收入";

    /** 收支方向：支出。 */
    public static final String DIR_OUT = "支出";

    /** 科目：营业收入。 */
    public static final String TYPE_SALE_INCOME = "营业收入";

    /** 科目：餐卡充值。 */
    public static final String TYPE_CARD_RECHARGE = "餐卡充值";

    /** 科目：采购付款。 */
    public static final String TYPE_PURCHASE_PAY = "采购付款";

    /** 科目：费用支出。 */
    public static final String TYPE_FEE_EXPENSE = "费用支出";

    /** 科目：盘盈盘亏。 */
    public static final String TYPE_INVENTORY = "盘盈盘亏";

    /** 科目：上级拨入。 */
    public static final String TYPE_ALLOC_IN = "上级拨入";

    /** 科目：内部转账。 */
    public static final String TYPE_TRANSFER = "内部转账";

    /** 科目：其他收入。 */
    public static final String TYPE_OTHER = "其他收入";

    /** 来源单据：采购入库。 */
    public static final String SOURCE_PURCHASE = "采购入库";

    /** 来源单据：领用出库。 */
    public static final String SOURCE_OUTBOUND = "领用出库";

    /** 来源单据：就餐收入。 */
    public static final String SOURCE_SALE = "就餐收入";

    /** 来源单据：费用支出。 */
    public static final String SOURCE_FEE = "费用支出";

    /** 来源单据：盘点单。 */
    public static final String SOURCE_CHECK = "盘点单";

    /** 来源单据：餐卡充值。 */
    public static final String SOURCE_RECHARGE = "餐卡充值";

    /** 来源单据：手工记账。 */
    public static final String SOURCE_MANUAL = "手工记账";

    /** 状态：已记账。 */
    public static final String STATUS_POSTED = "已记账";

    /** 状态：已冲销。 */
    public static final String STATUS_REVERSED = "已冲销";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务日期。 */
    private LocalDate bizDate;

    /** 记账流水号，如 JZ20260001。 */
    private String voucherNo;

    /** 收支方向：收入 / 支出。 */
    private String direction;

    /** 收支科目：营业收入 / 餐卡充值 / 采购付款 / 费用支出 / 盘盈盘亏 / 上级拨入 / 内部转账 / 其他收入。 */
    private String accountType;

    /** 发生金额(元)，恒为正数。 */
    private BigDecimal amount;

    /** 结存余额(元) = 上笔余额 ± 本笔金额。 */
    private BigDecimal balance;

    /** 结算方式：银行转账 / 现金 / 饭卡 / 扫码支付 / 内部往来。 */
    private String payType;

    /** 对方单位 / 个人。 */
    private String counterparty;

    /** 摘要。 */
    private String summary;

    /** 来源单据类型：采购入库 / 领用出库 / 就餐收入 / 费用支出 / 盘点单 / 餐卡充值 / 手工记账。 */
    private String sourceType;

    /** 来源单据ID。 */
    private Long sourceId;

    /** 来源单号。 */
    private String sourceNo;

    /** 关联饭卡ID（充值 / 消费时）。 */
    private Long cardId;

    /** 关联饭卡号。 */
    private String cardNo;

    /** 记账人 / 经办人。 */
    private String handler;

    /** 状态：已记账 / 已冲销。 */
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

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
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
