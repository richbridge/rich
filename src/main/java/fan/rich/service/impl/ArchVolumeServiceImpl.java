package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchVolume;
import fan.rich.mapper.ArchVolumeMapper;
import fan.rich.service.ArchVolumeService;
import org.springframework.stereotype.Service;

/**
 * 案卷（卷盒）。 业务实现。
 */
@Service
public class ArchVolumeServiceImpl extends ServiceImpl<ArchVolumeMapper, ArchVolume> implements ArchVolumeService {
}
