package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.TrainEnroll;
import fan.rich.mapper.TrainEnrollMapper;
import fan.rich.service.TrainEnrollService;
import org.springframework.stereotype.Service;

/**
 * 参训记录（报名 / 学习明细）。 服务实现。
 */
@Service
public class TrainEnrollServiceImpl extends ServiceImpl<TrainEnrollMapper, TrainEnroll> implements TrainEnrollService {
}
