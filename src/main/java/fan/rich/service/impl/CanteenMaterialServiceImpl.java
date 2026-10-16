package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenMaterial;
import fan.rich.mapper.CanteenMaterialMapper;
import fan.rich.service.CanteenMaterialService;
import org.springframework.stereotype.Service;

/**
 * 食材 / 物料档案 业务实现。
 */
@Service
public class CanteenMaterialServiceImpl extends ServiceImpl<CanteenMaterialMapper, CanteenMaterial> implements CanteenMaterialService {
}
