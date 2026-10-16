package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.Customer;
import fan.rich.service.CustomerService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 客户管理接口。
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** 分页列表。 */
    @GetMapping("/list")
    public R list(@RequestParam(defaultValue = "1") long page,
                  @RequestParam(defaultValue = "10") long limit,
                  @RequestParam(required = false) String keywords,
                  @RequestParam(required = false) Integer status,
                  @RequestParam(required = false) String region) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(Customer::getCustomerNo, keywords)
                    .or().like(Customer::getName, keywords)
                    .or().like(Customer::getContact, keywords)
                    .or().like(Customer::getPhone, keywords));
        }
        if (status != null) {
            wrapper.eq(Customer::getStatus, status);
        }
        if (StringUtils.hasText(region)) {
            wrapper.eq(Customer::getRegion, region);
        }
        wrapper.orderByDesc(Customer::getId);

        IPage<Customer> result = customerService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部正常客户，用于订单表单下拉选择。 */
    @GetMapping("/all")
    public R all() {
        return R.ok(customerService.list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1)
                .orderByAsc(Customer::getId)));
    }

    /** 新增或修改。 */
    @PostMapping("/save")
    public R save(@RequestBody Customer customer) {
        if (customer.getStatus() == null) {
            customer.setStatus(1);
        }
        customerService.saveOrUpdate(customer);
        return R.ok();
    }

    /** 删除。 */
    @PostMapping("/delete")
    public R delete(@RequestParam Long id) {
        customerService.removeById(id);
        return R.ok();
    }
}
