package fan.rich.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.CanteenSupplier;
import fan.rich.mapper.CanteenSupplierMapper;
import fan.rich.service.CanteenSupplierService;
import org.springframework.stereotype.Service;

/**
 * 食堂供应商档案 业务实现。
 */
@Service
public class CanteenSupplierServiceImpl extends ServiceImpl<CanteenSupplierMapper, CanteenSupplier> implements CanteenSupplierService {
}
