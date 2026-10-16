package fan.rich.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 名称-数值对，用于饼图 / 柱状图等分类统计。
 */
public class NameValue implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private BigDecimal value;

    public NameValue() {
    }

    public NameValue(String name, BigDecimal value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }
}
