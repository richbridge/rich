package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.AuditProject;
import fan.rich.mapper.AuditProjectMapper;
import fan.rich.service.AuditProjectService;
import org.springframework.stereotype.Service;

/**
 * 审计项目服务实现。
 */
@Service
public class AuditProjectServiceImpl extends ServiceImpl<AuditProjectMapper, AuditProject> implements AuditProjectService {
}
