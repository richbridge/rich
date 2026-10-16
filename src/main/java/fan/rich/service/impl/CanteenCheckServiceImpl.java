package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenCheck;
import fan.rich.mapper.CanteenCheckMapper;
import fan.rich.service.CanteenCheckService;
import org.springframework.stereotype.Service;

/**
 * 库存盘点单 业务实现。
 */
@Service
public class CanteenCheckServiceImpl extends ServiceImpl<CanteenCheckMapper, CanteenCheck> implements CanteenCheckService {
}
