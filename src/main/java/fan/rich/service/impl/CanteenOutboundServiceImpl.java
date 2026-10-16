package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenOutbound;
import fan.rich.mapper.CanteenOutboundMapper;
import fan.rich.service.CanteenOutboundService;
import org.springframework.stereotype.Service;

/**
 * 领用出库单 业务实现。
 */
@Service
public class CanteenOutboundServiceImpl extends ServiceImpl<CanteenOutboundMapper, CanteenOutbound> implements CanteenOutboundService {
}
