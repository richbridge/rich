package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenStock;
import fan.rich.mapper.CanteenStockMapper;
import fan.rich.service.CanteenStockService;
import org.springframework.stereotype.Service;

/**
 * 库存结存 业务实现。
 */
@Service
public class CanteenStockServiceImpl extends ServiceImpl<CanteenStockMapper, CanteenStock> implements CanteenStockService {
}
