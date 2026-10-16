package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.Product;
import org.apache.ibatis.annotations.Select;

/**
 * 产品 Mapper。
 */
public interface ProductMapper extends BaseMapper<Product> {

    /** 在售产品数量。 */
    @Select("SELECT COUNT(*) FROM sale_product WHERE status = 1")
    Long countActive();
}
