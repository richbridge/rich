package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.AuditIssue;
import fan.rich.mapper.AuditIssueMapper;
import fan.rich.service.AuditIssueService;
import org.springframework.stereotype.Service;

/**
 * 审计问题服务实现。
 */
@Service
public class AuditIssueServiceImpl extends ServiceImpl<AuditIssueMapper, AuditIssue> implements AuditIssueService {
}
