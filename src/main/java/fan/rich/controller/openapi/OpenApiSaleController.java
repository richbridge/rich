package fan.rich.controller.openapi;

import com.baomidou.mybatisplus.core.metadata.IPage;
import fan.rich.common.R;
import fan.rich.dto.ShipmentQuery;
import fan.rich.entity.SaleShipment;
import fan.rich.service.SaleLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放接口 · 销售台账。
 */
@RestController
@RequestMapping("/openapi/sale")
public class OpenApiSaleController {

    private final SaleLedgerService saleLedgerService;
    private final OpenApiSupport support;

    public OpenApiSaleController(SaleLedgerService saleLedgerService, OpenApiSupport support) {
        this.saleLedgerService = saleLedgerService;
        this.support = support;
    }

    /** 核心指标：金含量、干重、结算金额、欠金量等。 */
    @GetMapping("/summary")
    public R summary() {
        R denied = support.require("sale");
        if (denied != null) {
            return denied;
        }
        return R.ok(saleLedgerService.summary());
    }

    /**
     * 发货台账分页。
     *
     * @param page   页码
     * @param limit  每页条数
     * @param keyword 关键字（批次号 / 销售商 / 样品编号）
     * @param from   起始发货日期，可空
     * @param to     截止发货日期，可空
     */
    @GetMapping("/ledger")
    public R ledger(@RequestParam(defaultValue = "1") long page,
                    @RequestParam(defaultValue = "20") long limit,
                    @RequestParam(required = false) String keyword,
                    @RequestParam(required = false) String customer,
                    @RequestParam(required = false) String productType,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        R denied = support.require("sale");
        if (denied != null) {
            return denied;
        }
        ShipmentQuery query = new ShipmentQuery();
        query.setPage(page);
        query.setLimit(limit);
        query.setKeywords(keyword);
        query.setCustomerName(customer);
        query.setProductType(productType);
        query.setStartDate(from);
        query.setEndDate(to);
        IPage<SaleShipment> records = saleLedgerService.pageShipments(query);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getRecords());
        data.put("total", records.getTotal());
        data.put("page", records.getCurrent());
        data.put("limit", records.getSize());
        return R.ok(data);
    }

    /** 月度发货趋势。 */
    @GetMapping("/monthly")
    public R monthly() {
        R denied = support.require("sale");
        if (denied != null) {
            return denied;
        }
        return R.ok(saleLedgerService.monthlyTrend());
    }

    /** 按发货品种汇总（含库存与欠金量）。 */
    @GetMapping("/product-stats")
    public R productStats() {
        R denied = support.require("sale");
        if (denied != null) {
            return denied;
        }
        return R.ok(saleLedgerService.statsTable());
    }
}
