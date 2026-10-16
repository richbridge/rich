package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchFile;
import fan.rich.mapper.ArchFileMapper;
import fan.rich.service.ArchFileService;
import org.springframework.stereotype.Service;

/**
 * 卷内文件（件）。 业务实现。
 */
@Service
public class ArchFileServiceImpl extends ServiceImpl<ArchFileMapper, ArchFile> implements ArchFileService {
}
