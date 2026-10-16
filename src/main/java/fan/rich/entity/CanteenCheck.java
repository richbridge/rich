package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存盘点单（主表）。
 *
 * <p>月度 / 季度实地盘点。账面数量取自 {@code canteen_stock}，实盘数量人工录入，
 * 差异 = 实盘 - 账面。盘点单处理后按差异反向调整库存结存，并把盘盈盘亏计入财务台账。
 */
@TableName("canteen_check")
public class CanteenCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿（未盘点，不影响库存）。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已盘点（实盘数量已录入，待处理）。 */
    public static final String STATUS_CHECKED = "已盘点";

    /** 状态：已处理（库存已按差异调整）。 */
    public static final String STATUS_DONE = "已处理";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号，如 PD2026-0001。 */
    private String checkNo;

    /** 盘点日期。 */
    private LocalDate checkDate;

    /** 盘点方式：月末盘点 / 季度盘点 / 临时抽盘 / 年度清查。 */
    private String checkType;

    /** 账面金额(元)。 */
    private BigDecimal bookAmount;

    /** 实盘金额(元)。 */
    private BigDecimal actualAmount;

    /** 差异金额(元) = 实盘 - 账面。 */
    private BigDecimal diffAmount;

    /** 明细条数。 */
    private Integer itemCount;

    /** 有差异的明细条数。 */
    private Integer diffCount;

    /** 盘点人。 */
    private String checker;

    /** 复核人。 */
    private String reviewer;

    /** 状态：草稿 / 已盘点 / 已处理。 */
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

    public String getCheckNo() {
        return checkNo;
    }

    public void setCheckNo(String checkNo) {
        this.checkNo = checkNo;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(LocalDate checkDate) {
        this.checkDate = checkDate;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public BigDecimal getBookAmount() {
        return bookAmount;
    }

    public void setBookAmount(BigDecimal bookAmount) {
        this.bookAmount = bookAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Integer getDiffCount() {
        return diffCount;
    }

    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
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
