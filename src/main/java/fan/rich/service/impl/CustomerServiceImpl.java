package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.Customer;
import fan.rich.mapper.CustomerMapper;
import fan.rich.service.CustomerService;
import org.springframework.stereotype.Service;

/**
 * 客户服务实现。
 */
@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {
}
