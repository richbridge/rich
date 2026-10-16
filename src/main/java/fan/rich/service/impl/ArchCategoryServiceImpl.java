package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchCategory;
import fan.rich.mapper.ArchCategoryMapper;
import fan.rich.service.ArchCategoryService;
import org.springframework.stereotype.Service;

/**
 * 档案门类。 业务实现。
 */
@Service
public class ArchCategoryServiceImpl extends ServiceImpl<ArchCategoryMapper, ArchCategory> implements ArchCategoryService {
}
