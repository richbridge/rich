package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.CanteenAccount;
import fan.rich.entity.CanteenCard;
import fan.rich.entity.CanteenCheck;
import fan.rich.entity.CanteenCheckItem;
import fan.rich.entity.CanteenFee;
import fan.rich.entity.CanteenMaterial;
import fan.rich.entity.CanteenOutbound;
import fan.rich.entity.CanteenOutboundItem;
import fan.rich.entity.CanteenPurchase;
import fan.rich.entity.CanteenPurchaseItem;
import fan.rich.entity.CanteenSale;
import fan.rich.entity.CanteenStock;
import fan.rich.entity.CanteenSupplier;
import fan.rich.service.CanteenAccountService;
import fan.rich.service.CanteenBizService;
import fan.rich.service.CanteenCardService;
import fan.rich.service.CanteenCheckItemService;
import fan.rich.service.CanteenCheckService;
import fan.rich.service.CanteenFeeService;
import fan.rich.service.CanteenMaterialService;
import fan.rich.service.CanteenOutboundItemService;
import fan.rich.service.CanteenOutboundService;
import fan.rich.service.CanteenPurchaseItemService;
import fan.rich.service.CanteenPurchaseService;
import fan.rich.service.CanteenSaleService;
import fan.rich.service.CanteenStatsService;
import fan.rich.service.CanteenStockService;
import fan.rich.service.CanteenSupplierService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食堂管理接口：进销存（采购入库 / 领用出库 / 库存盘点）与财务（就餐收入 / 费用支出 / 收支台账）。
 *
 * <p>业务流程：供应商供货后登记<b>采购入库</b>（按移动加权平均法更新库存结存单价）→
 * 后厨按餐次<b>领用出库</b>（按结存单价结转食材成本）→ 月末<b>盘点</b>处理账实差异 →
 * 就餐<b>收入</b>与各项<b>费用支出</b>一并进入<b>收支流水台账</b>，滚动结出账面余额。
 */
@RestController
@RequestMapping("/api/canteen")
public class CanteenController {

    private final CanteenSupplierService supplierService;
    private final CanteenMaterialService materialService;
    private final CanteenCardService cardService;
    private final CanteenPurchaseService purchaseService;
    private final CanteenPurchaseItemService purchaseItemService;
    private final CanteenOutboundService outboundService;
    private final CanteenOutboundItemService outboundItemService;
    private final CanteenStockService stockService;
    private final CanteenCheckService checkService;
    private final CanteenCheckItemService checkItemService;
    private final CanteenSaleService saleService;
    private final CanteenFeeService feeService;
    private final CanteenAccountService accountService;
    private final CanteenBizService bizService;
    private final CanteenStatsService statsService;

    public CanteenController(CanteenSupplierService supplierService,
                             CanteenMaterialService materialService,
                             CanteenCardService cardService,
                             CanteenPurchaseService purchaseService,
                             CanteenPurchaseItemService purchaseItemService,
                             CanteenOutboundService outboundService,
                             CanteenOutboundItemService outboundItemService,
                             CanteenStockService stockService,
                             CanteenCheckService checkService,
                             CanteenCheckItemService checkItemService,
                             CanteenSaleService saleService,
                             CanteenFeeService feeService,
                             CanteenAccountService accountService,
                             CanteenBizService bizService,
                             CanteenStatsService statsService) {
        this.supplierService = supplierService;
        this.materialService = materialService;
        this.cardService = cardService;
        this.purchaseService = purchaseService;
        this.purchaseItemService = purchaseItemService;
        this.outboundService = outboundService;
        this.outboundItemService = outboundItemService;
        this.stockService = stockService;
        this.checkService = checkService;
        this.checkItemService = checkItemService;
        this.saleService = saleService;
        this.feeService = feeService;
        this.accountService = accountService;
        this.bizService = bizService;
        this.statsService = statsService;
    }

    // ================================================================ 供应商

    /** 供应商分页列表。 */
    @GetMapping("/supplier/list")
    public R supplierList(@RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long limit,
                          @RequestParam(required = false) String keywords,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CanteenSupplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(CanteenSupplier::getSupplierName, keywords)
                    .or().like(CanteenSupplier::getSupplierCode, keywords)
                    .or().like(CanteenSupplier::getContact, keywords);
        }
        wrapper.eq(StringUtils.hasText(category), CanteenSupplier::getCategory, category)
                .eq(StringUtils.hasText(status), CanteenSupplier::getStatus, status)
                .orderByAsc(CanteenSupplier::getSupplierCode);
        IPage<CanteenSupplier> result = supplierService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部供应商，用于下拉选择。 */
    @GetMapping("/supplier/all")
    public R supplierAll() {
        return R.ok(supplierService.list(new LambdaQueryWrapper<CanteenSupplier>()
                .eq(CanteenSupplier::getStatus, "合作中").orderByAsc(CanteenSupplier::getSupplierCode)));
    }

    /** 新增或修改供应商。 */
    @PostMapping("/supplier/save")
    public R supplierSave(@RequestBody CanteenSupplier supplier) {
        if (!StringUtils.hasText(supplier.getStatus())) {
            supplier.setStatus("合作中");
        }
        supplierService.saveOrUpdate(supplier);
        return R.ok(supplier.getId());
    }

    /** 删除供应商。 */
    @PostMapping("/supplier/delete")
    public R supplierDelete(@RequestParam Long id) {
        long used = purchaseService.count(new LambdaQueryWrapper<CanteenPurchase>()
                .eq(CanteenPurchase::getSupplierId, id))
                + feeService.count(new LambdaQueryWrapper<CanteenFee>()
                .eq(CanteenFee::getSupplierId, id));
        if (used > 0) {
            return R.fail("该供应商已有 " + used + " 张业务单据，不能删除");
        }
        supplierService.removeById(id);
        return R.ok();
    }

    // ================================================================ 食材物料

    /** 物料分页列表。 */
    @GetMapping("/material/list")
    public R materialList(@RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long limit,
                          @RequestParam(required = false) String keywords,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CanteenMaterial> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(CanteenMaterial::getMaterialName, keywords)
                    .or().like(CanteenMaterial::getMaterialCode, keywords)
                    .or().like(CanteenMaterial::getSpec, keywords);
        }
        wrapper.eq(StringUtils.hasText(category), CanteenMaterial::getCategory, category)
                .eq(StringUtils.hasText(status), CanteenMaterial::getStatus, status)
                .orderByAsc(CanteenMaterial::getMaterialCode);
        IPage<CanteenMaterial> result = materialService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部物料，用于下拉选择。 */
    @GetMapping("/material/all")
    public R materialAll() {
        return R.ok(materialService.list(new LambdaQueryWrapper<CanteenMaterial>()
                .eq(CanteenMaterial::getStatus, "启用").orderByAsc(CanteenMaterial::getMaterialCode)));
    }

    /** 新增或修改物料，同步刷新库存档案冗余字段。 */
    @PostMapping("/material/save")
    public R materialSave(@RequestBody CanteenMaterial material) {
        if (!StringUtils.hasText(material.getStatus())) {
            material.setStatus("启用");
        }
        if (material.getSupplierId() != null && !StringUtils.hasText(material.getSupplierName())) {
            CanteenSupplier supplier = supplierService.getById(material.getSupplierId());
            if (supplier != null) {
                material.setSupplierName(supplier.getSupplierName());
            }
        }
        materialService.saveOrUpdate(material);
        CanteenStock stock = stockService.getOne(new LambdaQueryWrapper<CanteenStock>()
                .eq(CanteenStock::getMaterialId, material.getId()).last("LIMIT 1"));
        if (stock != null) {
            stock.setMaterialName(material.getMaterialName());
            stock.setCategory(material.getCategory());
            stock.setSpec(material.getSpec());
            stock.setUnit(material.getUnit());
            stock.setMinStock(material.getMinStock());
            stock.setMaxStock(material.getMaxStock());
            stockService.updateById(stock);
        }
        return R.ok(material.getId());
    }

    /** 删除物料。 */
    @PostMapping("/material/delete")
    public R materialDelete(@RequestParam Long id) {
        long used = purchaseItemService.count(new LambdaQueryWrapper<CanteenPurchaseItem>()
                .eq(CanteenPurchaseItem::getMaterialId, id))
                + outboundItemService.count(new LambdaQueryWrapper<CanteenOutboundItem>()
                .eq(CanteenOutboundItem::getMaterialId, id));
        if (used > 0) {
            return R.fail("该物料已在 " + used + " 条单据明细中使用，建议改为停用");
        }
        stockService.remove(new LambdaQueryWrapper<CanteenStock>().eq(CanteenStock::getMaterialId, id));
        materialService.removeById(id);
        return R.ok();
    }

    /** 物料收发存卡片（结存与流水）。 */
    @GetMapping("/material/card")
    public R materialCard(@RequestParam Long materialId) {
        return R.ok(statsService.materialCard(materialId));
    }

    // ================================================================ 采购入库

    /** 采购入库单分页列表。 */
    @GetMapping("/purchase/list")
    public R purchaseList(@RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long limit,
                          @RequestParam(required = false) String supplierName,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String payStatus,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month,
                          @RequestParam(required = false) String keywords) {
        LambdaQueryWrapper<CanteenPurchase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(supplierName), CanteenPurchase::getSupplierName, supplierName)
                .eq(StringUtils.hasText(status), CanteenPurchase::getStatus, status)
                .eq(StringUtils.hasText(payStatus), CanteenPurchase::getPayStatus, payStatus)
                .eq(year != null, CanteenPurchase::getPurchaseDate, year == null ? null : year)
                .like(StringUtils.hasText(keywords), CanteenPurchase::getPurchaseNo, keywords)
                .orderByDesc(CanteenPurchase::getPurchaseDate).orderByDesc(CanteenPurchase::getId);
        IPage<CanteenPurchase> result = purchaseService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 采购单及其明细。 */
    @GetMapping("/purchase/detail")
    public R purchaseDetail(@RequestParam Long id) {
        CanteenPurchase purchase = purchaseService.getById(id);
        if (purchase == null) {
            return R.fail("单据不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("bill", purchase);
        data.put("items", purchaseItemService.list(new LambdaQueryWrapper<CanteenPurchaseItem>()
                .eq(CanteenPurchaseItem::getPurchaseId, id).orderByAsc(CanteenPurchaseItem::getId)));
        return R.ok(data);
    }

    /** 保存采购单（主表 + 明细）。 */
    @PostMapping("/purchase/save")
    public R purchaseSave(@RequestBody PurchaseBill request) {
        CanteenPurchase purchase = request.getBill();
        List<CanteenPurchaseItem> items = nullToEmpty(request.getItems());
        if (!StringUtils.hasText(purchase.getPurchaseNo())) {
            purchase.setPurchaseNo(nextNo("CG", purchaseService.count()));
        }
        return R.ok(bizService.savePurchase(purchase, items).getId());
    }

    /** 删除采购单。 */
    @PostMapping("/purchase/delete")
    public R purchaseDelete(@RequestParam Long id) {
        bizService.deletePurchase(id);
        return R.ok();
    }

    /** 采购单入库过账。 */
    @PostMapping("/purchase/post")
    public R purchasePost(@RequestParam Long id) {
        try {
            return R.ok(bizService.postPurchase(id));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 采购付款。 */
    @PostMapping("/purchase/pay")
    public R purchasePay(@RequestParam Long id,
                         @RequestParam(required = false) BigDecimal amount,
                         @RequestParam(required = false) String payType,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate payDate,
                         @RequestParam(required = false) String handler) {
        try {
            return R.ok(bizService.payPurchase(id, amount, payType, payDate, handler));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 下一个入库单号。 */
    @GetMapping("/purchase/next-no")
    public R purchaseNextNo() {
        return R.ok(nextNo("CG", purchaseService.count()));
    }

    // ================================================================ 领用出库

    /** 出库单分页列表。 */
    @GetMapping("/outbound/list")
    public R outboundList(@RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long limit,
                          @RequestParam(required = false) String outType,
                          @RequestParam(required = false) String deptName,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month) {
        LambdaQueryWrapper<CanteenOutbound> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(outType), CanteenOutbound::getOutType, outType)
                .eq(StringUtils.hasText(deptName), CanteenOutbound::getDeptName, deptName)
                .eq(StringUtils.hasText(status), CanteenOutbound::getStatus, status)
                .eq(year != null, CanteenOutbound::getOutDate, year == null ? null : year)
                .orderByDesc(CanteenOutbound::getOutDate).orderByDesc(CanteenOutbound::getId);
        IPage<CanteenOutbound> result = outboundService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 出库单及其明细。 */
    @GetMapping("/outbound/detail")
    public R outboundDetail(@RequestParam Long id) {
        CanteenOutbound outbound = outboundService.getById(id);
        if (outbound == null) {
            return R.fail("单据不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("bill", outbound);
        data.put("items", outboundItemService.list(new LambdaQueryWrapper<CanteenOutboundItem>()
                .eq(CanteenOutboundItem::getOutId, id).orderByAsc(CanteenOutboundItem::getId)));
        return R.ok(data);
    }

    /** 保存出库单。 */
    @PostMapping("/outbound/save")
    public R outboundSave(@RequestBody OutboundBill request) {
        CanteenOutbound outbound = request.getBill();
        List<CanteenOutboundItem> items = nullToEmpty(request.getItems());
        if (!StringUtils.hasText(outbound.getOutNo())) {
            outbound.setOutNo(nextNo("LY", outboundService.count()));
        }
        return R.ok(bizService.saveOutbound(outbound, items).getId());
    }

    @PostMapping("/outbound/delete")
    public R outboundDelete(@RequestParam Long id) {
        bizService.deleteOutbound(id);
        return R.ok();
    }

    /** 出库过账。 */
    @PostMapping("/outbound/post")
    public R outboundPost(@RequestParam Long id) {
        try {
            return R.ok(bizService.postOutbound(id));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/outbound/next-no")
    public R outboundNextNo() {
        return R.ok(nextNo("LY", outboundService.count()));
    }

    // ================================================================ 库存

    /** 库存结存列表。 */
    @GetMapping("/stock/list")
    public R stockList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "20") long limit,
                       @RequestParam(required = false) String keywords,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String warnLevel) {
        LambdaQueryWrapper<CanteenStock> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(CanteenStock::getMaterialName, keywords)
                    .or().like(CanteenStock::getMaterialCode, keywords);
        }
        wrapper.eq(StringUtils.hasText(category), CanteenStock::getCategory, category);
        if ("低于安全库存".equals(warnLevel)) {
            wrapper.apply("min_stock > 0 AND quantity < min_stock");
        } else if ("超出最高库存".equals(warnLevel)) {
            wrapper.apply("max_stock > 0 AND quantity > max_stock");
        } else if ("负库存".equals(warnLevel)) {
            wrapper.lt(CanteenStock::getQuantity, BigDecimal.ZERO);
        }
        wrapper.orderByDesc(CanteenStock::getTotalAmount);
        IPage<CanteenStock> result = stockService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 库存预警列表。 */
    @GetMapping("/stock/warnings")
    public R stockWarnings() {
        return R.ok(statsService.stockWarnings());
    }

    /** 按全部单据重算库存。 */
    @PostMapping("/stock/recalc")
    public R stockRecalc() {
        return R.ok(bizService.recalcStock());
    }

    // ================================================================ 盘点

    /** 盘点单列表。 */
    @GetMapping("/check/list")
    public R checkList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "20") long limit,
                       @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CanteenCheck> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), CanteenCheck::getStatus, status)
                .orderByDesc(CanteenCheck::getCheckDate).orderByDesc(CanteenCheck::getId);
        IPage<CanteenCheck> result = checkService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 盘点单及其明细。 */
    @GetMapping("/check/detail")
    public R checkDetail(@RequestParam Long id) {
        CanteenCheck check = checkService.getById(id);
        if (check == null) {
            return R.fail("单据不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("bill", check);
        data.put("items", checkItemService.list(new LambdaQueryWrapper<CanteenCheckItem>()
                .eq(CanteenCheckItem::getCheckId, id).orderByAsc(CanteenCheckItem::getId)));
        return R.ok(data);
    }

    /** 按当前库存生成盘点明细草稿（账面数量取自库存结存）。 */
    @GetMapping("/check/init-items")
    public R checkInitItems() {
        List<CanteenCheckItem> items = new ArrayList<>();
        for (CanteenStock stock : stockService.list(new LambdaQueryWrapper<CanteenStock>()
                .orderByAsc(CanteenStock::getCategory).orderByAsc(CanteenStock::getMaterialCode))) {
            if (stock.getMaterialId() == null) {
                continue;
            }
            CanteenCheckItem item = new CanteenCheckItem();
            item.setMaterialId(stock.getMaterialId());
            item.setMaterialCode(stock.getMaterialCode());
            item.setMaterialName(stock.getMaterialName());
            item.setCategory(stock.getCategory());
            item.setSpec(stock.getSpec());
            item.setUnit(stock.getUnit());
            item.setBookQty(nvl(stock.getQuantity()));
            item.setActualQty(nvl(stock.getQuantity()));
            item.setUnitPrice(nvl(stock.getAvgPrice()));
            item.setDiffQty(BigDecimal.ZERO);
            item.setDiffAmount(BigDecimal.ZERO);
            items.add(item);
        }
        return R.ok(items);
    }

    /** 保存盘点单。 */
    @PostMapping("/check/save")
    public R checkSave(@RequestBody CheckBill request) {
        CanteenCheck check = request.getBill();
        List<CanteenCheckItem> items = nullToEmpty(request.getItems());
        if (!StringUtils.hasText(check.getCheckNo())) {
            check.setCheckNo(nextNo("PD", checkService.count()));
        }
        return R.ok(bizService.saveCheck(check, items).getId());
    }

    @PostMapping("/check/delete")
    public R checkDelete(@RequestParam Long id) {
        bizService.deleteCheck(id);
        return R.ok();
    }

    /** 盘点过账：调整库存并登记盘盈盘亏。 */
    @PostMapping("/check/post")
    public R checkPost(@RequestParam Long id) {
        try {
            return R.ok(bizService.postCheck(id));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/check/next-no")
    public R checkNextNo() {
        return R.ok(nextNo("PD", checkService.count()));
    }

    // ================================================================ 就餐收入

    /** 就餐收入列表。 */
    @GetMapping("/sale/list")
    public R saleList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "20") long limit,
                      @RequestParam(required = false) String mealType,
                      @RequestParam(required = false) String customerType,
                      @RequestParam(required = false) String status,
                      @RequestParam(required = false) Integer year,
                      @RequestParam(required = false) Integer month) {
        LambdaQueryWrapper<CanteenSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(mealType), CanteenSale::getMealType, mealType)
                .eq(StringUtils.hasText(customerType), CanteenSale::getCustomerType, customerType)
                .eq(StringUtils.hasText(status), CanteenSale::getStatus, status)
                .eq(year != null, CanteenSale::getBizDate, year == null ? null : year)
                .orderByDesc(CanteenSale::getBizDate).orderByDesc(CanteenSale::getId);
        IPage<CanteenSale> result = saleService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 保存就餐收入。 */
    @PostMapping("/sale/save")
    public R saleSave(@RequestBody CanteenSale sale) {
        if (!StringUtils.hasText(sale.getSaleNo())) {
            sale.setSaleNo(nextNo("SR", saleService.count()));
        }
        return R.ok(bizService.saveSale(sale).getId());
    }

    @PostMapping("/sale/delete")
    public R saleDelete(@RequestParam Long id) {
        bizService.deleteSale(id);
        return R.ok();
    }

    /** 确认收入并记账。 */
    @PostMapping("/sale/post")
    public R salePost(@RequestParam Long id) {
        try {
            return R.ok(bizService.postSale(id));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 按日期范围批量生成就餐收入日报。
     *
     * <p>同一「日期 + 餐次 + 就餐类型」已有记录时跳过，重复提交安全。
     */
    @PostMapping("/sale/batch")
    public R saleBatch(@RequestBody Map<String, Object> body) {
        LocalDate start = dateOf(body.get("startDate"));
        LocalDate end = dateOf(body.get("endDate"));
        if (start == null || end == null) {
            return R.fail("请选择日期范围");
        }
        List<String> mealTypes = new ArrayList<>();
        Object rawTypes = body.get("mealTypes");
        if (rawTypes instanceof List<?> list) {
            for (Object item : list) {
                mealTypes.add(String.valueOf(item));
            }
        }
        if (mealTypes.isEmpty()) {
            return R.fail("请至少选择一个餐次");
        }
        if (end.isBefore(start)) {
            return R.fail("结束日期不能早于开始日期");
        }
        if (start.plusDays(93).isBefore(end)) {
            return R.fail("一次最多生成 3 个月的数据，请缩小日期范围");
        }
        int count = bizService.batchGenerateSale(
                start, end, mealTypes,
                str(body.get("customerType"), "职工餐"),
                decimal(body.get("unitPrice")), decimal(body.get("actualAmount")),
                body.get("peopleCount") == null ? 0 : ((Number) body.get("peopleCount")).intValue(),
                str(body.get("payType"), "饭卡"),
                str(body.get("windowName"), "职工餐厅"),
                str(body.get("operator"), "系统生成"));
        return count == 0 ? R.fail("所选范围内记录已存在，未新增") : R.ok(count);
    }

    @GetMapping("/sale/next-no")
    public R saleNextNo() {
        return R.ok(nextNo("SR", saleService.count()));
    }

    // ================================================================ 费用支出

    /** 费用单列表。 */
    @GetMapping("/fee/list")
    public R feeList(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "20") long limit,
                     @RequestParam(required = false) String feeType,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String payStatus,
                     @RequestParam(required = false) Integer year) {
        LambdaQueryWrapper<CanteenFee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(feeType), CanteenFee::getFeeType, feeType)
                .eq(StringUtils.hasText(status), CanteenFee::getStatus, status)
                .eq(StringUtils.hasText(payStatus), CanteenFee::getPayStatus, payStatus)
                .eq(year != null, CanteenFee::getFeeDate, year == null ? null : year)
                .orderByDesc(CanteenFee::getFeeDate).orderByDesc(CanteenFee::getId);
        IPage<CanteenFee> result = feeService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    @PostMapping("/fee/save")
    public R feeSave(@RequestBody CanteenFee fee) {
        if (!StringUtils.hasText(fee.getFeeNo())) {
            fee.setFeeNo(nextNo("FY", feeService.count()));
        }
        return R.ok(bizService.saveFee(fee).getId());
    }

    @PostMapping("/fee/delete")
    public R feeDelete(@RequestParam Long id) {
        bizService.deleteFee(id);
        return R.ok();
    }

    /** 费用付款并记账。 */
    @PostMapping("/fee/pay")
    public R feePay(@RequestParam Long id,
                    @RequestParam(required = false) String payType,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                            LocalDate payDate,
                    @RequestParam(required = false) String handler) {
        try {
            return R.ok(bizService.payFee(id, payType, payDate, handler));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/fee/next-no")
    public R feeNextNo() {
        return R.ok(nextNo("FY", feeService.count()));
    }

    // ================================================================ 饭卡

    /** 饭卡列表。 */
    @GetMapping("/card/list")
    public R cardList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "20") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) String deptName,
                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CanteenCard> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(CanteenCard::getCardNo, keywords)
                    .or().like(CanteenCard::getHolder, keywords);
        }
        wrapper.eq(StringUtils.hasText(deptName), CanteenCard::getDeptName, deptName)
                .eq(StringUtils.hasText(status), CanteenCard::getStatus, status)
                .orderByAsc(CanteenCard::getCardNo);
        IPage<CanteenCard> result = cardService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    @PostMapping("/card/save")
    public R cardSave(@RequestBody CanteenCard card) {
        if (!StringUtils.hasText(card.getStatus())) {
            card.setStatus("正常");
        }
        if (card.getBalance() == null) {
            card.setBalance(BigDecimal.ZERO);
        }
        cardService.saveOrUpdate(card);
        return R.ok(card.getId());
    }

    /** 饭卡充值。 */
    @PostMapping("/card/recharge")
    public R cardRecharge(@RequestParam Long id,
                          @RequestParam BigDecimal amount,
                          @RequestParam(required = false) String payType,
                          @RequestParam(required = false) String handler) {
        try {
            return R.ok(bizService.rechargeCard(id, amount, payType, handler));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 饭卡消费。 */
    @PostMapping("/card/consume")
    public R cardConsume(@RequestParam Long id,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String summary) {
        try {
            return R.ok(bizService.consumeCard(id, amount, summary));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/card/delete")
    public R cardDelete(@RequestParam Long id) {
        CanteenCard card = cardService.getById(id);
        if (card != null && card.getBalance() != null && card.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            return R.fail("饭卡余额不为零，请先办理退款后再注销");
        }
        cardService.removeById(id);
        return R.ok();
    }

    // ================================================================ 收支台账

    /** 收支流水台账。 */
    @GetMapping("/account/list")
    public R accountList(@RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long limit,
                         @RequestParam(required = false) String direction,
                         @RequestParam(required = false) String accountType,
                         @RequestParam(required = false) String keywords,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate startDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate endDate) {
        LambdaQueryWrapper<CanteenAccount> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(CanteenAccount::getSummary, keywords)
                    .or().like(CanteenAccount::getCounterparty, keywords)
                    .or().like(CanteenAccount::getVoucherNo, keywords)
                    .or().like(CanteenAccount::getSourceNo, keywords);
        }
        wrapper.eq(StringUtils.hasText(direction), CanteenAccount::getDirection, direction)
                .eq(StringUtils.hasText(accountType), CanteenAccount::getAccountType, accountType)
                .ge(startDate != null, CanteenAccount::getBizDate, startDate)
                .le(endDate != null, CanteenAccount::getBizDate, endDate)
                .orderByDesc(CanteenAccount::getBizDate).orderByDesc(CanteenAccount::getId);
        IPage<CanteenAccount> result = accountService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 手工记账。 */
    @PostMapping("/account/save")
    public R accountSave(@RequestBody CanteenAccount account) {
        return R.ok(bizService.manualAccount(account));
    }

    @PostMapping("/account/delete")
    public R accountDelete(@RequestParam Long id) {
        bizService.deleteAccount(id);
        return R.ok();
    }

    /** 重算流水余额。 */
    @PostMapping("/account/recalc")
    public R accountRecalc() {
        return R.ok(bizService.recalcBalance());
    }

    // ================================================================ 统计

    /** 经营总览。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.overview());
    }

    /** 月度经营情况表。 */
    @GetMapping("/monthly-report")
    public R monthlyReport(@RequestParam(required = false) Integer year) {
        return R.ok(statsService.monthlyReport(year == null ? LocalDate.now().getYear() : year));
    }

    /** 全部收支科目的金额分布，供财务页展示。 */
    @GetMapping("/account/summary")
    public R accountSummary(@RequestParam(required = false) Integer year) {
        return R.ok(statsService.accountSummary(year == null ? LocalDate.now().getYear() : year));
    }

    // ================================================================ 工具

    /** 生成单据编号：前缀 + 年份 + 四位流水，如 CG2026-0001。 */
    private static String nextNo(String prefix, long count) {
        return prefix + LocalDate.now().getYear() + "-" + String.format("%04d", count + 1);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static LocalDate dateOf(Object value) {
        return StringUtils.hasText(String.valueOf(value)) && !"null".equals(String.valueOf(value))
                ? LocalDate.parse(String.valueOf(value).trim())
                : null;
    }

    private static String str(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? fallback : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue())
                : new BigDecimal(String.valueOf(value));
    }

    // ================================================================ 工具

    /**
     * 采购单提交体：主表 + 明细一起提交，避免先保存主表再逐条提交明细带来的中间态。
     */
    public static class PurchaseBill {

        private CanteenPurchase bill;
        private List<CanteenPurchaseItem> items;

        public CanteenPurchase getBill() {
            return bill;
        }

        public void setBill(CanteenPurchase bill) {
            this.bill = bill;
        }

        public List<CanteenPurchaseItem> getItems() {
            return items;
        }

        public void setItems(List<CanteenPurchaseItem> items) {
            this.items = items;
        }
    }

    /** 出库单提交体：主表 + 明细。 */
    public static class OutboundBill {

        private CanteenOutbound bill;
        private List<CanteenOutboundItem> items;

        public CanteenOutbound getBill() {
            return bill;
        }

        public void setBill(CanteenOutbound bill) {
            this.bill = bill;
        }

        public List<CanteenOutboundItem> getItems() {
            return items;
        }

        public void setItems(List<CanteenOutboundItem> items) {
            this.items = items;
        }
    }

    /** 盘点单提交体：主表 + 明细。 */
    public static class CheckBill {

        private CanteenCheck bill;
        private List<CanteenCheckItem> items;

        public CanteenCheck getBill() {
            return bill;
        }

        public void setBill(CanteenCheck bill) {
            this.bill = bill;
        }

        public List<CanteenCheckItem> getItems() {
            return items;
        }

        public void setItems(List<CanteenCheckItem> items) {
            this.items = items;
        }
    }
}
