package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchLocation;
import fan.rich.mapper.ArchLocationMapper;
import fan.rich.service.ArchLocationService;
import org.springframework.stereotype.Service;

/**
 * 档案存放位置（库房 - 密集架 - 层）。 业务实现。
 */
@Service
public class ArchLocationServiceImpl extends ServiceImpl<ArchLocationMapper, ArchLocation> implements ArchLocationService {
}
