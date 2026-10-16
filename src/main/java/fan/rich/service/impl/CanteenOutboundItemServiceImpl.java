package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenOutboundItem;
import fan.rich.mapper.CanteenOutboundItemMapper;
import fan.rich.service.CanteenOutboundItemService;
import org.springframework.stereotype.Service;

/**
 * 领用出库明细 业务实现。
 */
@Service
public class CanteenOutboundItemServiceImpl extends ServiceImpl<CanteenOutboundItemMapper, CanteenOutboundItem> implements CanteenOutboundItemService {
}
