package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.TrainExam;
import fan.rich.mapper.TrainExamMapper;
import fan.rich.service.TrainExamService;
import org.springframework.stereotype.Service;

/**
 * 培训考试（试卷）。 服务实现。
 */
@Service
public class TrainExamServiceImpl extends ServiceImpl<TrainExamMapper, TrainExam> implements TrainExamService {
}
