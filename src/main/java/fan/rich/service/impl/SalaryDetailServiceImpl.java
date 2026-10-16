package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.SalaryDetail;
import fan.rich.mapper.SalaryDetailMapper;
import fan.rich.service.SalaryDetailService;
import org.springframework.stereotype.Service;

/**
 * SalaryDetail 业务实现。
 */
@Service
public class SalaryDetailServiceImpl extends ServiceImpl<SalaryDetailMapper, SalaryDetail> implements SalaryDetailService {
}
