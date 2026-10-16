package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenFee;
import fan.rich.mapper.CanteenFeeMapper;
import fan.rich.service.CanteenFeeService;
import org.springframework.stereotype.Service;

/**
 * 食堂费用支出单 业务实现。
 */
@Service
public class CanteenFeeServiceImpl extends ServiceImpl<CanteenFeeMapper, CanteenFee> implements CanteenFeeService {
}
