package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenAccount;
import fan.rich.mapper.CanteenAccountMapper;
import fan.rich.service.CanteenAccountService;
import org.springframework.stereotype.Service;

/**
 * 食堂收支流水台账 业务实现。
 */
@Service
public class CanteenAccountServiceImpl extends ServiceImpl<CanteenAccountMapper, CanteenAccount> implements CanteenAccountService {
}
