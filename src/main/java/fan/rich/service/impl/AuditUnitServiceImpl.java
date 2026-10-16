package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.AuditUnit;
import fan.rich.mapper.AuditUnitMapper;
import fan.rich.service.AuditUnitService;
import org.springframework.stereotype.Service;

/**
 * 被审计单位服务实现。
 */
@Service
public class AuditUnitServiceImpl extends ServiceImpl<AuditUnitMapper, AuditUnit> implements AuditUnitService {
}
