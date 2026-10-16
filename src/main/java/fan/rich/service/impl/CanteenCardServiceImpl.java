package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenCard;
import fan.rich.mapper.CanteenCardMapper;
import fan.rich.service.CanteenCardService;
import org.springframework.stereotype.Service;

/**
 * 饭卡账户 业务实现。
 */
@Service
public class CanteenCardServiceImpl extends ServiceImpl<CanteenCardMapper, CanteenCard> implements CanteenCardService {
}
