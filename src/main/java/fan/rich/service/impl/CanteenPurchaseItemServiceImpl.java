package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenPurchaseItem;
import fan.rich.mapper.CanteenPurchaseItemMapper;
import fan.rich.service.CanteenPurchaseItemService;
import org.springframework.stereotype.Service;

/**
 * 采购入库明细 业务实现。
 */
@Service
public class CanteenPurchaseItemServiceImpl extends ServiceImpl<CanteenPurchaseItemMapper, CanteenPurchaseItem> implements CanteenPurchaseItemService {
}
