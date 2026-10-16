package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.TrainCourse;
import fan.rich.mapper.TrainCourseMapper;
import fan.rich.service.TrainCourseService;
import org.springframework.stereotype.Service;

/**
 * 培训课程（课件）。 服务实现。
 */
@Service
public class TrainCourseServiceImpl extends ServiceImpl<TrainCourseMapper, TrainCourse> implements TrainCourseService {
}
