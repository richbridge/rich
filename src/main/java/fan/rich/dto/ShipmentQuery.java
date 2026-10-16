package fan.rich.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 发货台账查询条件。
 */
public class ShipmentQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private long page = 1;

    private long limit = 20;

    /** 发货品种：CONCENTRATE / DORE。 */
    private String productType;

    /** 关键字：批次号 / 销售商 / 样品编号 / 备注。 */
    private String keywords;

    /** 销售商名称。 */
    private String customerName;

    private LocalDate startDate;

    private LocalDate endDate;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getLimit() {
        return limit;
    }

    public void setLimit(long limit) {
        this.limit = limit;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
