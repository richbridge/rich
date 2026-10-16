package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采集数据源品种字典。
 *
 * <p>SHFE / SGE 为合约维度（uqKey 存合约代码），SMM / CJ 为品种维度（uqKey 存 productId）。
 * 由"品种发现"任务写入，"行情采集"任务读取后逐个请求历史接口。
 */
@TableName("md_source_product")
public class MdSourceProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源编码：SHFE / SGE / SMM / CJ。 */
    private String source;

    /** 站点侧唯一标识（productId 或 contract code）。 */
    private String productId;

    /** 品种 / 合约名称。 */
    private String productName;

    /** 所属分组（SMM：铜专区/铝专区…；SHFE：品种代号）。 */
    private String productGroup;

    /** 计量单位，可为空。 */
    private String unit;

    /** 是否参与定时采集：1 是，0 否。 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductGroup() {
        return productGroup;
    }

    public void setProductGroup(String productGroup) {
        this.productGroup = productGroup;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
