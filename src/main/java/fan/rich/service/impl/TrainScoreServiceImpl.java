package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.TrainScore;
import fan.rich.mapper.TrainScoreMapper;
import fan.rich.service.TrainScoreService;
import org.springframework.stereotype.Service;

/**
 * 考试成绩明细。 服务实现。
 */
@Service
public class TrainScoreServiceImpl extends ServiceImpl<TrainScoreMapper, TrainScore> implements TrainScoreService {
}
