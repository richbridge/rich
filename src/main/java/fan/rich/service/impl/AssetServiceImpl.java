package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.Asset;
import fan.rich.mapper.AssetMapper;
import fan.rich.service.AssetService;
import org.springframework.stereotype.Service;

/**
 * Asset 业务实现。
 */
@Service
public class AssetServiceImpl extends ServiceImpl<AssetMapper, Asset> implements AssetService {
}
