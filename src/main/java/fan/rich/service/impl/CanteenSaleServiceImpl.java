package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenSale;
import fan.rich.mapper.CanteenSaleMapper;
import fan.rich.service.CanteenSaleService;
import org.springframework.stereotype.Service;

/**
 * 就餐收入日报 业务实现。
 */
@Service
public class CanteenSaleServiceImpl extends ServiceImpl<CanteenSaleMapper, CanteenSale> implements CanteenSaleService {
}
