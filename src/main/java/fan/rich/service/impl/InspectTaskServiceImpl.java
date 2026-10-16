package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.InspectTask;
import fan.rich.mapper.InspectTaskMapper;
import fan.rich.service.InspectTaskService;
import org.springframework.stereotype.Service;

/**
 * 检查任务服务实现。
 */
@Service
public class InspectTaskServiceImpl extends ServiceImpl<InspectTaskMapper, InspectTask> implements InspectTaskService {
}
