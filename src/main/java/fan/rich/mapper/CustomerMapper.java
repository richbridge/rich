package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.Customer;
import org.apache.ibatis.annotations.Select;

/**
 * 客户 Mapper。
 */
public interface CustomerMapper extends BaseMapper<Customer> {

    /** 正常状态客户数量。 */
    @Select("SELECT COUNT(*) FROM sale_customer WHERE status = 1")
    Long countActive();
}
