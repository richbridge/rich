package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.SalaryReport;
import fan.rich.mapper.SalaryReportMapper;
import fan.rich.service.SalaryReportService;
import org.springframework.stereotype.Service;

/**
 * SalaryReport 业务实现。
 */
@Service
public class SalaryReportServiceImpl extends ServiceImpl<SalaryReportMapper, SalaryReport> implements SalaryReportService {
}
