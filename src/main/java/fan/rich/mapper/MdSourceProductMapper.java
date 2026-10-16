package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.MdSourceProduct;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采集品种字典 Mapper。
 */
public interface MdSourceProductMapper extends BaseMapper<MdSourceProduct> {

    /** 指定数据源下参与采集的品种。 */
    @Select("SELECT * FROM md_source_product WHERE source = #{source} AND enabled = 1 ORDER BY id")
    List<MdSourceProduct> selectEnabled(@Param("source") String source);

    /**
     * 批量写入品种字典，按 (source, product_id) 幂等。
     *
     * <p>已存在则更新名称与分组（站点可能调整命名），不存在则插入；已存在的品种保持原有
     * enabled 状态，避免把用户手动关闭的品种重新打开。
     */
    @Insert("<script>"
            + "INSERT INTO md_source_product "
            + "  (source, product_id, product_name, product_group, unit, enabled, create_time, update_time) "
            + "VALUES "
            + "<foreach collection='list' item='r' separator=','>"
            + "  (#{r.source}, #{r.productId}, #{r.productName}, #{r.productGroup}, #{r.unit}, "
            + "   IFNULL(#{r.enabled},1), NOW(), NOW())"
            + "</foreach> "
            + "ON DUPLICATE KEY UPDATE "
            + "  product_name = VALUES(product_name), "
            + "  product_group = VALUES(product_group), "
            + "  unit = IFNULL(VALUES(unit), unit), "
            + "  update_time = NOW()"
            + "</script>")
    int bulkUpsert(@Param("list") List<MdSourceProduct> list);
}
