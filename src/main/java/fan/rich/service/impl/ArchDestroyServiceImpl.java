package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchDestroy;
import fan.rich.mapper.ArchDestroyMapper;
import fan.rich.service.ArchDestroyService;
import org.springframework.stereotype.Service;

/**
 * 档案销毁清册。 业务实现。
 */
@Service
public class ArchDestroyServiceImpl extends ServiceImpl<ArchDestroyMapper, ArchDestroy> implements ArchDestroyService {
}
