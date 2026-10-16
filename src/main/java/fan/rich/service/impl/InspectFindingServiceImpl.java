package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.InspectFinding;
import fan.rich.mapper.InspectFindingMapper;
import fan.rich.service.InspectFindingService;
import org.springframework.stereotype.Service;

/**
 * 检查发现问题服务实现。
 */
@Service
public class InspectFindingServiceImpl extends ServiceImpl<InspectFindingMapper, InspectFinding> implements InspectFindingService {
}
