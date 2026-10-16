package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.InvRecord;
import fan.rich.mapper.InvRecordMapper;
import fan.rich.service.InvRecordService;
import org.springframework.stereotype.Service;

/**
 * InvRecord 业务实现。
 */
@Service
public class InvRecordServiceImpl extends ServiceImpl<InvRecordMapper, InvRecord> implements InvRecordService {
}
