package fan.rich.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售趋势坐标点。
 */
public class TrendPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 月份，格式 yyyy-MM。 */
    private String month;

    /** 当月销售额。 */
    private BigDecimal amount;

    /** 当月订单数。 */
    private Long orderCount;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }
}
