package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.TrainPlan;
import fan.rich.mapper.TrainPlanMapper;
import fan.rich.service.TrainPlanService;
import org.springframework.stereotype.Service;

/**
 * 培训计划（培训班）。 服务实现。
 */
@Service
public class TrainPlanServiceImpl extends ServiceImpl<TrainPlanMapper, TrainPlan> implements TrainPlanService {
}
