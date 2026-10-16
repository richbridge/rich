package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 领用出库单（主表）。
 *
 * <p>食材物资出库的凭证：职工餐消耗、加班餐、接待餐、福利发放、报损等。
 * 出库按当前库存的<b>移动加权平均单价</b>结转成本，扣减 {@code canteen_stock} 的结存数量。
 *
 * <p>其中「职工餐 / 加班餐」类出库金额计入食堂主营成本，
 * 「接待餐」通常由接待部门承担，出库时在备注挂对口部门。
 */
@TableName("canteen_outbound")
public class CanteenOutbound implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿（不影响库存）。 */
    public static final String STATUS_DRAFT = "草稿";

    /** 状态：已出库（库存已扣减，视为已过账）。 */
    public static final String STATUS_DONE = "已出库";

    /** 状态：已记账（成本已结转进财务账）。 */
    public static final String STATUS_POSTED = "已记账";

    /** 出库类型：职工餐。 */
    public static final String TYPE_STAFF = "职工餐";

    /** 出库类型：加班餐。 */
    public static final String TYPE_OVERTIME = "加班餐";

    /** 出库类型：接待餐。 */
    public static final String TYPE_RECEPTION = "接待餐";

    /** 出库类型：客饭。 */
    public static final String TYPE_GUEST = "客饭";

    /** 出库类型：福利发放。 */
    public static final String TYPE_WELFARE = "福利发放";

    /** 出库类型：报损报废。 */
    public static final String TYPE_SCRAP = "报损报废";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出库单号，如 LY2026-0001。 */
    private String outNo;

    /** 出库日期。 */
    private LocalDate outDate;

    /** 出库类型：职工餐 / 加班餐 / 接待餐 / 客饭 / 福利发放 / 报损报废。 */
    private String outType;

    /** 领用窗口 / 班组：主食间 / 副食间 / 面点间 / 凉菜间 / 库房。 */
    private String windowName;

    /** 领用部门（接待餐填接待对口部门）。 */
    private String deptName;

    /** 涉及就餐人次。 */
    private Integer peopleCount;

    /** 出库金额合计(元)。 */
    private BigDecimal totalAmount;

    /** 领用人。 */
    private String receiver;

    /** 保管 / 经办人。 */
    private String handler;

    /** 明细条数。 */
    private Integer itemCount;

    /** 状态：草稿 / 已出库 / 已记账。 */
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

    public String getOutNo() {
        return outNo;
    }

    public void setOutNo(String outNo) {
        this.outNo = outNo;
    }

    public LocalDate getOutDate() {
        return outDate;
    }

    public void setOutDate(LocalDate outDate) {
        this.outDate = outDate;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getWindowName() {
        return windowName;
    }

    public void setWindowName(String windowName) {
        this.windowName = windowName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Integer getPeopleCount() {
        return peopleCount;
    }

    public void setPeopleCount(Integer peopleCount) {
        this.peopleCount = peopleCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
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
