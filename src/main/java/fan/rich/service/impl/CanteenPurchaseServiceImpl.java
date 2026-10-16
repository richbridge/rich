package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenPurchase;
import fan.rich.mapper.CanteenPurchaseMapper;
import fan.rich.service.CanteenPurchaseService;
import org.springframework.stereotype.Service;

/**
 * 采购入库单 业务实现。
 */
@Service
public class CanteenPurchaseServiceImpl extends ServiceImpl<CanteenPurchaseMapper, CanteenPurchase> implements CanteenPurchaseService {
}
