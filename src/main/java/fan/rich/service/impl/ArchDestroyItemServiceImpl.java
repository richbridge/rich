package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchDestroyItem;
import fan.rich.mapper.ArchDestroyItemMapper;
import fan.rich.service.ArchDestroyItemService;
import org.springframework.stereotype.Service;

/**
 * 销毁清册明细（待销毁案卷）。 业务实现。
 */
@Service
public class ArchDestroyItemServiceImpl extends ServiceImpl<ArchDestroyItemMapper, ArchDestroyItem> implements ArchDestroyItemService {
}
