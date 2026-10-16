package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.Product;
import fan.rich.mapper.ProductMapper;
import fan.rich.service.ProductService;
import org.springframework.stereotype.Service;

/**
 * 产品服务实现。
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
}
