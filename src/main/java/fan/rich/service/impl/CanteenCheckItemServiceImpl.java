package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenCheckItem;
import fan.rich.mapper.CanteenCheckItemMapper;
import fan.rich.service.CanteenCheckItemService;
import org.springframework.stereotype.Service;

/**
 * 库存盘点明细 业务实现。
 */
@Service
public class CanteenCheckItemServiceImpl extends ServiceImpl<CanteenCheckItemMapper, CanteenCheckItem> implements CanteenCheckItemService {
}
