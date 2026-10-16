package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.ArchBorrow;
import fan.rich.mapper.ArchBorrowMapper;
import fan.rich.service.ArchBorrowService;
import org.springframework.stereotype.Service;

/**
 * 档案借阅登记。 业务实现。
 */
@Service
public class ArchBorrowServiceImpl extends ServiceImpl<ArchBorrowMapper, ArchBorrow> implements ArchBorrowService {
}
