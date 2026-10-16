package fan.rich.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import fan.rich.common.R;
import fan.rich.entity.SalePricing;
import fan.rich.entity.SaleSettlement;
import fan.rich.entity.SaleShipment;
import fan.rich.sale.SaleExcelImporter;
import fan.rich.service.SaleLedgerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 销售台账接口：发货明细、点价、提金结算与统计。
 */
@RestController
@RequestMapping("/api/sale")
public class SaleLedgerController {

    private final SaleLedgerService ledgerService;
    private final SaleExcelImporter importer;
    private final String defaultImportPath;

    public SaleLedgerController(SaleLedgerService ledgerService,
                                SaleExcelImporter importer,
                                @Value("${rich.sale.import-path:}") String defaultImportPath) {
        this.ledgerService = ledgerService;
        this.importer = importer;
        this.defaultImportPath = defaultImportPath == null ? "" : defaultImportPath.trim();
    }

    // ------------------------------------------------------------------ 发货台账

    /** 发货台账分页列表。 */
    @GetMapping("/shipment/page")
    public R page(@RequestParam(defaultValue = "1") long page,
                  @RequestParam(defaultValue = "20") long limit,
                  @RequestParam(required = false) String productType,
                  @RequestParam(required = false) String keywords,
                  @RequestParam(required = false) String customerName,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        fan.rich.dto.ShipmentQuery query = new fan.rich.dto.ShipmentQuery();
        query.setPage(page);
        query.setLimit(limit);
        query.setProductType(productType);
        query.setKeywords(keywords);
        query.setCustomerName(customerName);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        IPage<SaleShipment> result = ledgerService.pageShipments(query);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 保存发货记录。 */
    @PostMapping("/shipment/save")
    public R save(@RequestBody SaleShipment shipment) {
        ledgerService.saveShipment(shipment);
        return R.ok();
    }

    /** 删除发货记录。 */
    @PostMapping("/shipment/delete")
    public R delete(@RequestParam Long id) {
        ledgerService.removeShipment(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 统计

    /** 核心指标汇总。 */
    @GetMapping("/summary")
    public R summary() {
        return R.ok(ledgerService.summary());
    }

    /** 按发货品种统计金含量。 */
    @GetMapping("/stats/product")
    public R statsByProduct() {
        return R.ok(ledgerService.statsByProduct());
    }

    /** 按销售商统计金含量；可用 startDate / endDate 限定发货区间。 */
    @GetMapping("/stats/buyer")
    public R statsByBuyer(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(ledgerService.statsByBuyer(startDate, endDate));
    }

    /** 按发货品种的汇总行；可用 startDate / endDate 限定发货区间。 */
    @GetMapping("/stats/table")
    public R statsTable(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(ledgerService.statsTable(startDate, endDate));
    }

    /** 月度发货趋势。 */
    @GetMapping("/stats/monthly")
    public R monthly() {
        return R.ok(ledgerService.monthlyTrend());
    }

    // ------------------------------------------------------------------ 点价

    /** 点价记录列表。 */
    @GetMapping("/pricing/list")
    public R pricingList() {
        return R.table(ledgerService.pricingList().size(), ledgerService.pricingList());
    }

    /** 保存点价记录。 */
    @PostMapping("/pricing/save")
    public R savePricing(@RequestBody SalePricing pricing) {
        ledgerService.savePricing(pricing);
        return R.ok();
    }

    /** 删除点价记录。 */
    @PostMapping("/pricing/delete")
    public R deletePricing(@RequestParam Long id) {
        ledgerService.removePricing(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 提金结算

    /** 提金结算列表。 */
    @GetMapping("/settlement/list")
    public R settlementList() {
        return R.table(ledgerService.settlementList().size(), ledgerService.settlementList());
    }

    /** 保存提金结算记录。 */
    @PostMapping("/settlement/save")
    public R saveSettlement(@RequestBody SaleSettlement settlement) {
        ledgerService.saveSettlement(settlement);
        return R.ok();
    }

    /** 删除提金结算记录。 */
    @PostMapping("/settlement/delete")
    public R deleteSettlement(@RequestParam Long id) {
        ledgerService.removeSettlement(id);
        return R.ok();
    }

    // ------------------------------------------------------------------ 点价情况

    /** 点价情况台账。 */
    @GetMapping("/fix/list")
    public R fixAccounts() {
        return R.table(ledgerService.fixAccounts().size(), ledgerService.fixAccounts());
    }

    // ------------------------------------------------------------------ 导入

    /**
     * 重新导入《金宝销售台账》Excel。
     *
     * @param path 台账绝对路径；不传则回落到 {@code rich.sale.import-path}
     */
    @PostMapping("/import")
    public R importLedger(@RequestParam(required = false) String path) {
        try {
            String target = (path == null || path.isBlank()) ? defaultImportPath : path.trim();
            if (target.isEmpty()) {
                return R.fail("未指定台账路径，请传入 path 参数或配置 rich.sale.import-path");
            }
            SaleExcelImporter.ImportResult result = importer.importFile(Path.of(target));
            Map<String, Object> data = new HashMap<>();
            data.put("concentrate", result.concentrate());
            data.put("dore", result.dore());
            data.put("pricing", result.pricing());
            data.put("settlement", result.settlement());
            data.put("fixAccount", result.fixAccount());
            data.put("warnings", result.warnings());
            return R.ok(data);
        } catch (Exception e) {
            return R.fail("导入失败：" + e.getMessage());
        }
    }
}
