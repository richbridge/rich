package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.Product;
import fan.rich.service.ProductService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 产品管理接口。
 */
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 分页列表。 */
    @GetMapping("/list")
    public R list(@RequestParam(defaultValue = "1") long page,
                  @RequestParam(defaultValue = "10") long limit,
                  @RequestParam(required = false) String keywords,
                  @RequestParam(required = false) String category,
                  @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(Product::getProductNo, keywords)
                    .or().like(Product::getName, keywords));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Product::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        wrapper.orderByAsc(Product::getId);

        IPage<Product> result = productService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部在售产品，用于订单明细下拉选择。 */
    @GetMapping("/all")
    public R all() {
        return R.ok(productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getId)));
    }

    /** 新增或修改。 */
    @PostMapping("/save")
    public R save(@RequestBody Product product) {
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        productService.saveOrUpdate(product);
        return R.ok();
    }

    /** 删除。 */
    @PostMapping("/delete")
    public R delete(@RequestParam Long id) {
        productService.removeById(id);
        return R.ok();
    }
}
