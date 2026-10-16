package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.InvTask;
import fan.rich.mapper.InvTaskMapper;
import fan.rich.service.InvTaskService;
import org.springframework.stereotype.Service;

/**
 * InvTask 业务实现。
 */
@Service
public class InvTaskServiceImpl extends ServiceImpl<InvTaskMapper, InvTask> implements InvTaskService {
}
