package fan.rich.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fan.rich.entity.CanteenAccount;
import fan.rich.entity.CanteenCard;
import fan.rich.entity.CanteenCheck;
import fan.rich.entity.CanteenCheckItem;
import fan.rich.entity.CanteenMaterial;
import fan.rich.entity.CanteenOutbound;
import fan.rich.entity.CanteenOutboundItem;
import fan.rich.entity.CanteenPurchase;
import fan.rich.entity.CanteenPurchaseItem;
import fan.rich.entity.CanteenSale;
import fan.rich.entity.CanteenStock;
import fan.rich.entity.CanteenSupplier;
import fan.rich.entity.CanteenFee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 食堂业务核算：进销存（移动加权平均法）与财务流水。
 *
 * <p><b>进销存口径</b>
 * <ol>
 *     <li><b>入库</b>：以不含税金额计入库存成本。新单价 =（原结存金额 + 本次入库金额）/
 *         （原结存数量 + 本次入库数量），即移动加权平均法；</li>
 *     <li><b>出库</b>：按当前结存单价结转成本，只减数量不改单价，结存金额同步扣减；</li>
 *     <li><b>盘点</b>：差异 = 实盘 − 账面，按差异数量调整结存，金额按结存单价同向调整。</li>
 * </ol>
 * 单据状态即过账标记：采购单转为「已入库」、出库单转为「已出库」才影响库存，
 * 且同一单据不会重复过账。出现口径偏差时可调用 {@link #recalcStock()} 按全部单据全量重算。
 *
 * <p><b>财务口径</b>
 * 收入侧（就餐收入、餐卡充值、上级拨入、盘盈）与支出侧（采购付款、费用支出、盘亏）
 * 统一写入 {@code canteen_account} 流水，按业务日期滚动余额。
 * 流水以「来源单据类型 + 来源单号 + 科目」去重，重复确认同一张单据只会更新金额、不会重复记账。
 */
@Service
public class CanteenBizService {

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

    public CanteenBizService(CanteenSupplierService supplierService,
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
                             CanteenAccountService accountService) {
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
    }

    // ================================================================ 采购入库

    /** 保存采购入库单及其明细，返回主单。 */
    @Transactional
    public CanteenPurchase savePurchase(CanteenPurchase purchase, List<CanteenPurchaseItem> items) {
        fillPurchaseItems(purchase, items);
        summarizePurchase(purchase, items);
        boolean isNew = purchase.getId() == null;
        purchaseService.saveOrUpdate(purchase);
        // 明细采用「先清后写」，避免编辑时删掉的行残留
        purchaseItemService.remove(new LambdaQueryWrapper<CanteenPurchaseItem>()
                .eq(CanteenPurchaseItem::getPurchaseId, purchase.getId()));
        for (CanteenPurchaseItem item : items) {
            item.setPurchaseId(purchase.getId());
            item.setPurchaseNo(purchase.getPurchaseNo());
            purchaseItemService.save(item);
        }
        if (!isNew && CanteenPurchase.STATUS_STOCKED.equals(purchase.getStatus())) {
            // 编辑已入库单据后，库存口径可能变化，按全单重做一次
            reversePurchaseToStock(purchase.getId());
            applyPurchaseToStock(purchase.getId());
        }
        return purchase;
    }

    /** 删除采购入库单：已入库的先冲回库存。 */
    @Transactional
    public void deletePurchase(Long id) {
        reversePurchaseToStock(id);
        accountService.remove(new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getSourceType, CanteenAccount.SOURCE_PURCHASE)
                .eq(CanteenAccount::getSourceId, id));
        purchaseItemService.remove(new LambdaQueryWrapper<CanteenPurchaseItem>()
                .eq(CanteenPurchaseItem::getPurchaseId, id));
        purchaseService.removeById(id);
        recalcBalance();
    }

    /**
     * 采购单入库：扣减前一台账不可能重复，故以状态做幂等标记。
     *
     * @return 提示信息
     */
    @Transactional
    public String postPurchase(Long id) {
        CanteenPurchase purchase = purchaseService.getById(id);
        if (purchase == null) {
            throw new IllegalArgumentException("采购单不存在");
        }
        if (CanteenPurchase.STATUS_STOCKED.equals(purchase.getStatus())
                || CanteenPurchase.STATUS_SETTLED.equals(purchase.getStatus())) {
            return "该单据已入库，无需重复操作";
        }
        purchase.setStatus(CanteenPurchase.STATUS_STOCKED);
        purchaseService.updateById(purchase);
        applyPurchaseToStock(id);
        return "已入库，库存已更新";
    }

    /** 采购付款：登记付款金额并生成支出流水。 */
    @Transactional
    public String payPurchase(Long id, BigDecimal amount, String payType, LocalDate payDate, String handler) {
        CanteenPurchase purchase = purchaseService.getById(id);
        if (purchase == null) {
            throw new IllegalArgumentException("采购单不存在");
        }
        BigDecimal pay = nz(amount);
        if (pay.compareTo(BigDecimal.ZERO) <= 0) {
            pay = nz(purchase.getTotalWithTax()).subtract(nz(purchase.getPaidAmount()));
        }
        purchase.setPaidAmount(nz(purchase.getPaidAmount()).add(pay));
        BigDecimal rest = nz(purchase.getTotalWithTax()).subtract(purchase.getPaidAmount());
        purchase.setPayStatus(rest.compareTo(BigDecimal.ZERO) <= 0
                ? CanteenPurchase.PAY_PAID : CanteenPurchase.PAY_PART);
        purchaseService.updateById(purchase);
        CanteenSupplier supplier = supplierService.getById(purchase.getSupplierId());
        if (supplier != null) {
            supplier.setCoopAmount(nz(supplier.getCoopAmount()).add(pay));
            supplierService.updateById(supplier);
        }
        recordAccount(payDate == null ? LocalDate.now() : payDate,
                CanteenAccount.DIR_OUT, CanteenAccount.TYPE_PURCHASE_PAY, pay,
                defaultStr(payType, "银行转账"), purchase.getSupplierName(),
                "支付 " + purchase.getPurchaseNo() + " 采购款",
                CanteenAccount.SOURCE_PURCHASE, purchase.getId(), purchase.getPurchaseNo(),
                null, null, handler);
        return "已付款 " + pay.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元";
    }

    /** 明细行补齐物料信息并重算金额：金额 = 数量 × 单价，价税合计 = 金额 + 税额。 */
    private void fillPurchaseItems(CanteenPurchase purchase, List<CanteenPurchaseItem> items) {
        if (purchase.getSupplierId() != null && !StringUtils.hasText(purchase.getSupplierName())) {
            CanteenSupplier supplier = supplierService.getById(purchase.getSupplierId());
            if (supplier != null) {
                purchase.setSupplierName(supplier.getSupplierName());
            }
        }
        if (purchase.getPurchaseDate() == null) {
            purchase.setPurchaseDate(LocalDate.now());
        }
        for (CanteenPurchaseItem item : items) {
            if (item.getMaterialId() != null) {
                CanteenMaterial material = materialService.getById(item.getMaterialId());
                if (material != null) {
                    item.setMaterialCode(material.getMaterialCode());
                    item.setMaterialName(material.getMaterialName());
                    item.setCategory(material.getCategory());
                    item.setSpec(material.getSpec());
                    item.setUnit(material.getUnit());
                    if (item.getUnitPrice() == null) {
                        item.setUnitPrice(material.getUnitPrice());
                    }
                }
            }
            BigDecimal price = nz(item.getUnitPrice());
            BigDecimal amount = nz(item.getQuantity()).multiply(price).setScale(2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            BigDecimal tax = amount.multiply(nz(item.getTaxRate()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            item.setTaxAmount(tax);
            item.setTotalAmount(amount.add(tax));
        }
    }

    /** 主单金额由明细汇总，类别取金额占比最大的那一类。 */
    private void summarizePurchase(CanteenPurchase purchase, List<CanteenPurchaseItem> items) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (CanteenPurchaseItem item : items) {
            amount = amount.add(nz(item.getAmount()));
            tax = tax.add(nz(item.getTaxAmount()));
            byCategory.merge(defaultStr(item.getCategory(), "其他"),
                    nz(item.getAmount()), BigDecimal::add);
        }
        purchase.setTotalAmount(amount);
        purchase.setTaxAmount(tax);
        purchase.setTotalWithTax(amount.add(tax));
        purchase.setItemCount(items.size());
        purchase.setCategory(maxKey(byCategory));
        if (StringUtils.hasText(purchase.getStatus())) {
            BigDecimal paid = nz(purchase.getPaidAmount());
            purchase.setPayStatus(paid.compareTo(BigDecimal.ZERO) <= 0
                    ? CanteenPurchase.PAY_UNPAID
                    : (paid.compareTo(purchase.getTotalWithTax()) >= 0
                            ? CanteenPurchase.PAY_PAID : CanteenPurchase.PAY_PART));
        } else {
            purchase.setStatus(CanteenPurchase.STATUS_DRAFT);
            purchase.setPayStatus(CanteenPurchase.PAY_UNPAID);
        }
    }

    /** 按明细正向更新库存（移动加权平均）。 */
    private void applyPurchaseToStock(Long purchaseId) {
        CanteenPurchase purchase = purchaseService.getById(purchaseId);
        if (purchase == null) {
            return;
        }
        LocalDate date = purchase.getPurchaseDate() == null ? LocalDate.now() : purchase.getPurchaseDate();
        for (CanteenPurchaseItem item : purchaseItemService.list(
                new LambdaQueryWrapper<CanteenPurchaseItem>()
                        .eq(CanteenPurchaseItem::getPurchaseId, purchaseId))) {
            stockIn(item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(),
                    item.getCategory(), item.getSpec(), item.getUnit(),
                    nz(item.getQuantity()), nz(item.getAmount()), date);
        }
    }

    /** 按明细反向冲回库存，用于删除或重做已入库的单据。 */
    private void reversePurchaseToStock(Long purchaseId) {
        for (CanteenPurchaseItem item : purchaseItemService.list(
                new LambdaQueryWrapper<CanteenPurchaseItem>()
                        .eq(CanteenPurchaseItem::getPurchaseId, purchaseId))) {
            stockOut(item.getMaterialId(), nz(item.getQuantity()), nz(item.getAmount()));
        }
    }

    // ================================================================ 领用出库

    /** 保存出库单及其明细。 */
    @Transactional
    public CanteenOutbound saveOutbound(CanteenOutbound outbound, List<CanteenOutboundItem> items) {
        if (outbound.getOutDate() == null) {
            outbound.setOutDate(LocalDate.now());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CanteenOutboundItem item : items) {
            if (item.getMaterialId() != null) {
                CanteenMaterial material = materialService.getById(item.getMaterialId());
                if (material != null) {
                    item.setMaterialCode(material.getMaterialCode());
                    item.setMaterialName(material.getMaterialName());
                    item.setCategory(material.getCategory());
                    item.setSpec(material.getSpec());
                    item.setUnit(material.getUnit());
                }
            }
            // 出库单价取当前库存的加权平均价，用户一般不需要也不应该手改
            CanteenStock stock = loadStock(item.getMaterialId());
            BigDecimal price = stock == null ? nz(item.getUnitPrice()) : nz(stock.getAvgPrice());
            item.setUnitPrice(price);
            item.setAmount(nz(item.getQuantity()).multiply(price).setScale(2, RoundingMode.HALF_UP));
            total = total.add(nz(item.getAmount()));
        }
        outbound.setTotalAmount(total);
        outbound.setItemCount(items.size());
        if (!StringUtils.hasText(outbound.getStatus())) {
            outbound.setStatus(CanteenOutbound.STATUS_DRAFT);
        }
        boolean reCost = outbound.getId() != null;
        if (reCost && CanteenOutbound.STATUS_DONE.equals(outbound.getStatus())) {
            reverseOutboundToStock(outbound.getId());
        }
        outboundService.saveOrUpdate(outbound);
        outboundItemService.remove(new LambdaQueryWrapper<CanteenOutboundItem>()
                .eq(CanteenOutboundItem::getOutId, outbound.getId()));
        for (CanteenOutboundItem item : items) {
            item.setOutId(outbound.getId());
            item.setOutNo(outbound.getOutNo());
            outboundItemService.save(item);
        }
        if (CanteenOutbound.STATUS_DONE.equals(outbound.getStatus())) {
            applyOutboundToStock(outbound.getId());
        }
        return outbound;
    }

    /** 删除出库单：已出库的先冲回库存。 */
    @Transactional
    public void deleteOutbound(Long id) {
        reverseOutboundToStock(id);
        outboundItemService.remove(new LambdaQueryWrapper<CanteenOutboundItem>()
                .eq(CanteenOutboundItem::getOutId, id));
        outboundService.removeById(id);
    }

    /** 出库单过账：扣减库存。 */
    @Transactional
    public String postOutbound(Long id) {
        CanteenOutbound outbound = outboundService.getById(id);
        if (outbound == null) {
            throw new IllegalArgumentException("出库单不存在");
        }
        if (CanteenOutbound.STATUS_DONE.equals(outbound.getStatus())
                || CanteenOutbound.STATUS_POSTED.equals(outbound.getStatus())) {
            return "该单据已出库，无需重复操作";
        }
        outbound.setStatus(CanteenOutbound.STATUS_DONE);
        outboundService.updateById(outbound);
        applyOutboundToStock(id);
        return "已出库，库存已扣减";
    }

    /** 按明细正向扣减库存。 */
    private void applyOutboundToStock(Long outId) {
        CanteenOutbound outbound = outboundService.getById(outId);
        if (outbound == null) {
            return;
        }
        for (CanteenOutboundItem item : outboundItemService.list(
                new LambdaQueryWrapper<CanteenOutboundItem>()
                        .eq(CanteenOutboundItem::getOutId, outId))) {
            stockOut(item.getMaterialId(), nz(item.getQuantity()), nz(item.getAmount()));
        }
        LocalDate date = outbound.getOutDate();
        if (date != null) {
            for (CanteenOutboundItem item : outboundItemService.list(
                    new LambdaQueryWrapper<CanteenOutboundItem>()
                            .eq(CanteenOutboundItem::getOutId, outId))) {
                CanteenStock stock = loadStock(item.getMaterialId());
                if (stock != null) {
                    stock.setLastOutDate(date);
                    stockService.updateById(stock);
                }
            }
        }
    }

    /** 按明细反向恢复库存。 */
    private void reverseOutboundToStock(Long outId) {
        for (CanteenOutboundItem item : outboundItemService.list(
                new LambdaQueryWrapper<CanteenOutboundItem>()
                        .eq(CanteenOutboundItem::getOutId, outId))) {
            stockIn(item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(),
                    item.getCategory(), item.getSpec(), item.getUnit(),
                    nz(item.getQuantity()), nz(item.getAmount()),
                    LocalDate.now());
        }
    }

    // ================================================================ 库存

    /** 入库：按移动加权平均法更新结存数量、金额与单价。 */
    private void stockIn(Long materialId, String code, String name, String category,
                         String spec, String unit, BigDecimal qty, BigDecimal amount, LocalDate date) {
        CanteenStock stock = loadStock(materialId);
        if (stock == null) {
            stock = new CanteenStock();
            stock.setMaterialId(materialId);
            stock.setMaterialCode(code);
            stock.setMaterialName(name);
            stock.setCategory(category);
            stock.setSpec(spec);
            stock.setUnit(unit);
            stock.setQuantity(BigDecimal.ZERO);
            stock.setTotalAmount(BigDecimal.ZERO);
            stock.setAvgPrice(BigDecimal.ZERO);
            applyMaterialLimit(stock, materialId);
        }
        BigDecimal newQty = nz(stock.getQuantity()).add(qty);
        BigDecimal newAmount = nz(stock.getTotalAmount()).add(amount);
        stock.setQuantity(newQty);
        stock.setTotalAmount(newAmount);
        stock.setAvgPrice(newQty.compareTo(BigDecimal.ZERO) != 0
                ? newAmount.divide(newQty, 4, RoundingMode.HALF_UP)
                : nz(stock.getAvgPrice()));
        stock.setLastInDate(date);
        stockService.saveOrUpdate(stock);
    }

    /** 出库：按当前结存单价结转成本。 */
    private void stockOut(Long materialId, BigDecimal qty, BigDecimal amount) {
        CanteenStock stock = loadStock(materialId);
        if (stock == null) {
            CanteenMaterial material = materialService.getById(materialId);
            stock = new CanteenStock();
            stock.setMaterialId(materialId);
            if (material != null) {
                stock.setMaterialCode(material.getMaterialCode());
                stock.setMaterialName(material.getMaterialName());
                stock.setCategory(material.getCategory());
                stock.setSpec(material.getSpec());
                stock.setUnit(material.getUnit());
                stock.setAvgPrice(nz(material.getUnitPrice()));
                applyMaterialLimit(stock, materialId);
            }
            stock.setQuantity(BigDecimal.ZERO);
            stock.setTotalAmount(BigDecimal.ZERO);
        }
        stock.setQuantity(nz(stock.getQuantity()).subtract(qty));
        stock.setTotalAmount(nz(stock.getTotalAmount()).subtract(amount));
        if (stock.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            // 结存为零或负数时单价无意义，保留上一次的加权价便于后续回补
            stock.setTotalAmount(stock.getTotalAmount().max(BigDecimal.ZERO));
        }
        stockService.saveOrUpdate(stock);
    }

    private void applyMaterialLimit(CanteenStock stock, Long materialId) {
        CanteenMaterial material = materialService.getById(materialId);
        if (material != null) {
            stock.setMinStock(material.getMinStock());
            stock.setMaxStock(material.getMaxStock());
        }
    }

    private CanteenStock loadStock(Long materialId) {
        if (materialId == null) {
            return null;
        }
        return stockService.getOne(new LambdaQueryWrapper<CanteenStock>()
                .eq(CanteenStock::getMaterialId, materialId).last("LIMIT 1"));
    }

    /**
     * 按全部已过账单据全量重算库存，修复口径偏差。
     *
     * <p>顺序：清空结存 → 已入库采购单 → 已出库单 → 已处理盘点差异。
     */
    @Transactional
    public int recalcStock() {
        List<CanteenStock> all = stockService.list();
        for (CanteenStock stock : all) {
            stockService.removeById(stock.getId());
        }
        for (CanteenPurchase purchase : purchaseService.list(
                new LambdaQueryWrapper<CanteenPurchase>().in(CanteenPurchase::getStatus,
                        CanteenPurchase.STATUS_STOCKED, CanteenPurchase.STATUS_SETTLED))) {
            applyPurchaseToStock(purchase.getId());
        }
        for (CanteenOutbound outbound : outboundService.list(
                new LambdaQueryWrapper<CanteenOutbound>().in(CanteenOutbound::getStatus,
                        CanteenOutbound.STATUS_DONE, CanteenOutbound.STATUS_POSTED))) {
            applyOutboundToStock(outbound.getId());
        }
        for (CanteenCheck check : checkService.list(
                new LambdaQueryWrapper<CanteenCheck>().eq(CanteenCheck::getStatus, CanteenCheck.STATUS_DONE))) {
            adjustStockByCheck(check.getId());
        }
        return (int) stockService.count();
    }

    // ================================================================ 盘点

    /** 保存盘点单及其明细，并重算差异与主单金额。 */
    @Transactional
    public CanteenCheck saveCheck(CanteenCheck check, List<CanteenCheckItem> items) {
        if (check.getCheckDate() == null) {
            check.setCheckDate(LocalDate.now());
        }
        BigDecimal book = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        int diffCount = 0;
        for (CanteenCheckItem item : items) {
            if (item.getMaterialId() != null) {
                CanteenMaterial material = materialService.getById(item.getMaterialId());
                if (material != null) {
                    item.setMaterialCode(material.getMaterialCode());
                    item.setMaterialName(material.getMaterialName());
                    item.setCategory(material.getCategory());
                    item.setSpec(material.getSpec());
                    item.setUnit(material.getUnit());
                }
            }
            CanteenStock stock = loadStock(item.getMaterialId());
            BigDecimal price = stock == null ? nz(item.getUnitPrice()) : nz(stock.getAvgPrice());
            item.setUnitPrice(price);
            item.setDiffQty(nz(item.getActualQty()).subtract(nz(item.getBookQty())));
            item.setDiffAmount(item.getDiffQty().multiply(price).setScale(2, RoundingMode.HALF_UP));
            book = book.add(nz(item.getBookQty()).multiply(price).setScale(2, RoundingMode.HALF_UP));
            actual = actual.add(nz(item.getActualQty()).multiply(price).setScale(2, RoundingMode.HALF_UP));
            if (item.getDiffQty().compareTo(BigDecimal.ZERO) != 0) {
                diffCount++;
            }
        }
        check.setBookAmount(book);
        check.setActualAmount(actual);
        check.setDiffAmount(actual.subtract(book));
        check.setItemCount(items.size());
        check.setDiffCount(diffCount);
        if (!StringUtils.hasText(check.getStatus())) {
            check.setStatus(CanteenCheck.STATUS_DRAFT);
        }
        checkService.saveOrUpdate(check);
        checkItemService.remove(new LambdaQueryWrapper<CanteenCheckItem>()
                .eq(CanteenCheckItem::getCheckId, check.getId()));
        for (CanteenCheckItem item : items) {
            item.setCheckId(check.getId());
            item.setCheckNo(check.getCheckNo());
            checkItemService.save(item);
        }
        return check;
    }

    /** 删除盘点单。 */
    @Transactional
    public void deleteCheck(Long id) {
        accountService.remove(new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getSourceType, CanteenAccount.SOURCE_CHECK)
                .eq(CanteenAccount::getSourceId, id));
        checkItemService.remove(new LambdaQueryWrapper<CanteenCheckItem>()
                .eq(CanteenCheckItem::getCheckId, id));
        checkService.removeById(id);
        recalcBalance();
    }

    /** 盘点过账：调整库存结存并登记盘盈盘亏。 */
    @Transactional
    public String postCheck(Long id) {
        CanteenCheck check = checkService.getById(id);
        if (check == null) {
            throw new IllegalArgumentException("盘点单不存在");
        }
        if (CanteenCheck.STATUS_DONE.equals(check.getStatus())) {
            return "该盘点单已处理";
        }
        check.setStatus(CanteenCheck.STATUS_DONE);
        checkService.updateById(check);
        adjustStockByCheck(id);
        BigDecimal diff = nz(check.getDiffAmount());
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            recordAccount(check.getCheckDate() == null ? LocalDate.now() : check.getCheckDate(),
                    diff.compareTo(BigDecimal.ZERO) > 0 ? CanteenAccount.DIR_IN : CanteenAccount.DIR_OUT,
                    CanteenAccount.TYPE_INVENTORY, diff.abs(), "内部往来", "食堂仓库",
                    (diff.compareTo(BigDecimal.ZERO) > 0 ? "盘盈" : "盘亏") + " " + check.getCheckNo(),
                    CanteenAccount.SOURCE_CHECK, check.getId(), check.getCheckNo(),
                    null, null, check.getChecker());
        }
        return diff.compareTo(BigDecimal.ZERO) == 0
                ? "账实相符，无需调整" : "已处理，差异 " + diff.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元";
    }

    /** 按盘点差异调整结存数量与金额。 */
    private void adjustStockByCheck(Long checkId) {
        for (CanteenCheckItem item : checkItemService.list(
                new LambdaQueryWrapper<CanteenCheckItem>().eq(CanteenCheckItem::getCheckId, checkId))) {
            CanteenStock stock = loadStock(item.getMaterialId());
            if (stock == null) {
                continue;
            }
            stock.setQuantity(nz(stock.getQuantity()).add(nz(item.getDiffQty())));
            stock.setTotalAmount(nz(stock.getTotalAmount()).add(nz(item.getDiffAmount())));
            if (stock.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                stock.setAvgPrice(stock.getTotalAmount()
                        .divide(stock.getQuantity(), 4, RoundingMode.HALF_UP));
            }
            stockService.saveOrUpdate(stock);
        }
    }

    // ================================================================ 就餐收入

    /** 保存就餐收入，自动算应收 = 人次 × 标准，实收 + 补贴 = 应收。 */
    public CanteenSale saveSale(CanteenSale sale) {
        if (sale.getBizDate() == null) {
            sale.setBizDate(LocalDate.now());
        }
        sale.setReceivable(nz(sale.getPeopleCount() == null ? 0 : sale.getPeopleCount())
                .multiply(nz(sale.getUnitPrice())).setScale(2, RoundingMode.HALF_UP));
        if (sale.getSubsidyAmount() == null) {
            sale.setSubsidyAmount(sale.getReceivable().subtract(nz(sale.getActualAmount())));
            if (sale.getSubsidyAmount().compareTo(BigDecimal.ZERO) < 0) {
                sale.setSubsidyAmount(BigDecimal.ZERO);
            }
        }
        if (!StringUtils.hasText(sale.getStatus())) {
            sale.setStatus(CanteenSale.STATUS_DRAFT);
        }
        saleService.saveOrUpdate(sale);
        return sale;
    }

    /** 确认就餐收入：登记营业收入流水，饭卡支付方式同步扣减卡余额。 */
    @Transactional
    public String postSale(Long id) {
        CanteenSale sale = saleService.getById(id);
        if (sale == null) {
            throw new IllegalArgumentException("收入单不存在");
        }
        if (!CanteenSale.STATUS_DRAFT.equals(sale.getStatus())) {
            return "该收入单已确认";
        }
        sale.setStatus(CanteenSale.STATUS_CONFIRMED);
        saleService.updateById(sale);
        recordAccount(sale.getBizDate() == null ? LocalDate.now() : sale.getBizDate(),
                CanteenAccount.DIR_IN, CanteenAccount.TYPE_SALE_INCOME, nz(sale.getReceivable()),
                defaultStr(sale.getPayType(), "饭卡"), "就餐职工",
                sale.getMealType() + " " + sale.getCustomerType() + " " + sale.getPeopleCount() + " 人次",
                CanteenAccount.SOURCE_SALE, sale.getId(), sale.getSaleNo(),
                null, null, sale.getOperator());
        return "已确认并记账";
    }

    /**
     * 按日期范围批量生成就餐收入。
     *
     * <p>食堂收入日报每天都录成本低但操作繁琐，这里按「日期 × 餐次」一次性铺开：
     * 人次在传入均值上下浮动 ±8%，避免数据过于整齐；
     * 同一「日期 + 餐次 + 就餐类型」已有记录时跳过，重复点生成不会产生脏数据。
     *
     * @return 实际生成的条数
     */
    @Transactional
    public int batchGenerateSale(LocalDate start, LocalDate end, List<String> mealTypes,
                                 String customerType, BigDecimal unitPrice, BigDecimal actualAmount,
                                 int peopleCount, String payType, String windowName, String operator) {
        int created = 0;
        int seed = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            for (String meal : mealTypes) {
                LambdaQueryWrapper<CanteenSale> exist = new LambdaQueryWrapper<CanteenSale>()
                        .eq(CanteenSale::getBizDate, day)
                        .eq(CanteenSale::getMealType, meal)
                        .eq(CanteenSale::getCustomerType, customerType);
                if (saleService.count(exist) > 0) {
                    continue;
                }
                seed++;
                // 人次浮动：以均值为中心 ±8%，用序号做伪随机保证结果可复现
                double factor = 1 + (((seed * 37) % 17) - 8) / 100.0;
                int people = Math.max(1, (int) Math.round(peopleCount * factor));
                CanteenSale sale = new CanteenSale();
                sale.setSaleNo(nextSaleNo());
                sale.setBizDate(day);
                sale.setMealType(meal);
                sale.setCustomerType(customerType);
                sale.setWindowName(defaultStr(windowName, "职工餐厅"));
                sale.setPeopleCount(people);
                sale.setUnitPrice(unitPrice);
                sale.setActualAmount(nz(actualAmount).multiply(BigDecimal.valueOf(people))
                        .setScale(2, RoundingMode.HALF_UP));
                sale.setPayType(defaultStr(payType, "饭卡"));
                sale.setOperator(operator);
                saveSale(sale);
                created++;
            }
        }
        return created;
    }

    private String nextSaleNo() {
        return "SR" + LocalDate.now().getYear() + "-"
                + String.format("%04d", saleService.count() + 1);
    }

    @Transactional
    public void deleteSale(Long id) {
        accountService.remove(new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getSourceType, CanteenAccount.SOURCE_SALE)
                .eq(CanteenAccount::getSourceId, id));
        saleService.removeById(id);
        recalcBalance();
    }

    // ================================================================ 费用支出

    /** 保存费用单：价税合计 = 金额 + 税额。 */
    public CanteenFee saveFee(CanteenFee fee) {
        if (fee.getFeeDate() == null) {
            fee.setFeeDate(LocalDate.now());
        }
        fee.setTotalAmount(nz(fee.getAmount()).add(nz(fee.getTaxAmount())));
        if (!StringUtils.hasText(fee.getStatus())) {
            fee.setStatus(CanteenFee.STATUS_DRAFT);
        }
        if (!StringUtils.hasText(fee.getPayStatus())) {
            fee.setPayStatus(CanteenFee.PAY_UNPAID);
        }
        if (fee.getSupplierId() != null && !StringUtils.hasText(fee.getSupplierName())) {
            CanteenSupplier supplier = supplierService.getById(fee.getSupplierId());
            if (supplier != null) {
                fee.setSupplierName(supplier.getSupplierName());
            }
        }
        feeService.saveOrUpdate(fee);
        return fee;
    }

    /** 费用付款：登记支出流水。 */
    @Transactional
    public String payFee(Long id, String payType, LocalDate payDate, String handler) {
        CanteenFee fee = feeService.getById(id);
        if (fee == null) {
            throw new IllegalArgumentException("费用单不存在");
        }
        fee.setPayStatus(CanteenFee.PAY_PAID);
        fee.setStatus(CanteenFee.STATUS_PAID);
        fee.setPayDate(payDate == null ? LocalDate.now() : payDate);
        if (StringUtils.hasText(payType)) {
            fee.setPayType(payType);
        }
        feeService.updateById(fee);
        recordAccount(fee.getPayDate(), CanteenAccount.DIR_OUT, CanteenAccount.TYPE_FEE_EXPENSE,
                nz(fee.getTotalAmount()), defaultStr(fee.getPayType(), "银行转账"),
                defaultStr(fee.getSupplierName(), "—"),
                fee.getFeeType() + " " + fee.getFeeNo(),
                CanteenAccount.SOURCE_FEE, fee.getId(), fee.getFeeNo(),
                null, null, defaultStr(handler, fee.getHandler()));
        return "已付款并记账";
    }

    @Transactional
    public void deleteFee(Long id) {
        accountService.remove(new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getSourceType, CanteenAccount.SOURCE_FEE)
                .eq(CanteenAccount::getSourceId, id));
        feeService.removeById(id);
        recalcBalance();
    }

    // ================================================================ 饭卡

    /** 饭卡充值：增加卡余额并登记收入流水。 */
    @Transactional
    public String rechargeCard(Long cardId, BigDecimal amount, String payType, String handler) {
        CanteenCard card = cardService.getById(cardId);
        if (card == null) {
            throw new IllegalArgumentException("饭卡不存在");
        }
        BigDecimal money = nz(amount);
        if (money.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        card.setBalance(nz(card.getBalance()).add(money));
        card.setTotalRecharge(nz(card.getTotalRecharge()).add(money));
        cardService.updateById(card);
        LocalDate today = LocalDate.now();
        recordAccount(today, CanteenAccount.DIR_IN, CanteenAccount.TYPE_CARD_RECHARGE, money,
                defaultStr(payType, "扫码支付"), card.getHolder(),
                "饭卡充值 " + card.getCardNo(),
                CanteenAccount.SOURCE_RECHARGE, card.getId(), card.getCardNo(),
                card.getId(), card.getCardNo(), handler);
        syncCardStat(card.getId());
        return "已充值 " + money.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元";
    }

    /** 饭卡消费：扣减余额。 */
    @Transactional
    public String consumeCard(Long cardId, BigDecimal amount, String summary) {
        CanteenCard card = cardService.getById(cardId);
        if (card == null) {
            throw new IllegalArgumentException("饭卡不存在");
        }
        if (card.getBalance().compareTo(nz(amount)) < 0) {
            throw new IllegalArgumentException("饭卡余额不足，当前余额 " + card.getBalance());
        }
        card.setBalance(card.getBalance().subtract(nz(amount)));
        cardService.updateById(card);
        syncCardStat(card.getId());
        return "消费 " + nz(amount).setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元"
                + (StringUtils.hasText(summary) ? "（" + summary + "）" : "");
    }

    /** 根据流水重算饭卡的累计充值 / 累计消费。 */
    private void syncCardStat(Long cardId) {
        CanteenCard card = cardService.getById(cardId);
        if (card == null) {
            return;
        }
        List<CanteenAccount> flows = accountService.list(new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getCardId, cardId).eq(CanteenAccount::getStatus, CanteenAccount.STATUS_POSTED));
        BigDecimal recharge = BigDecimal.ZERO;
        BigDecimal consume = BigDecimal.ZERO;
        for (CanteenAccount flow : flows) {
            if (CanteenAccount.DIR_IN.equals(flow.getDirection())) {
                recharge = recharge.add(nz(flow.getAmount()));
            } else {
                consume = consume.add(nz(flow.getAmount()));
            }
        }
        card.setTotalRecharge(recharge);
        card.setTotalConsume(consume);
        cardService.updateById(card);
    }

    // ================================================================ 财务流水

    /**
     * 登记一笔流水。同一来源单据 + 科目已有记录时更新金额，避免重复记账；
     * 金额归零则删除该笔流水（例如单据被撤销）。
     */
    @Transactional
    public void recordAccount(LocalDate bizDate, String direction, String accountType, BigDecimal amount,
                              String payType, String counterparty, String summary,
                              String sourceType, Long sourceId, String sourceNo,
                              Long cardId, String cardNo, String handler) {
        BigDecimal money = nz(amount).abs().setScale(2, RoundingMode.HALF_UP);
        LambdaQueryWrapper<CanteenAccount> query = new LambdaQueryWrapper<CanteenAccount>()
                .eq(CanteenAccount::getSourceType, sourceType)
                .eq(CanteenAccount::getSourceId, sourceId)
                .eq(CanteenAccount::getAccountType, accountType);
        CanteenAccount exist = sourceId == null ? null : accountService.getOne(query.last("LIMIT 1"));
        if (money.compareTo(BigDecimal.ZERO) == 0) {
            if (exist != null) {
                accountService.removeById(exist.getId());
                recalcBalance();
            }
            return;
        }
        CanteenAccount account = exist == null ? new CanteenAccount() : exist;
        LocalDate day = bizDate == null ? LocalDate.now() : bizDate;
        account.setBizDate(day);
        account.setDirection(direction);
        account.setAccountType(accountType);
        account.setAmount(money);
        account.setPayType(payType);
        account.setCounterparty(counterparty);
        account.setSummary(summary);
        account.setSourceType(sourceType);
        account.setSourceId(sourceId);
        account.setSourceNo(sourceNo);
        account.setCardId(cardId);
        account.setCardNo(cardNo);
        account.setHandler(handler);
        account.setStatus(CanteenAccount.STATUS_POSTED);
        if (StringUtils.hasText(account.getVoucherNo())) {
            accountService.updateById(account);
        } else {
            account.setVoucherNo(nextVoucherNo(day));
            accountService.save(account);
        }
        recalcBalance();
        if (cardId != null) {
            syncCardStat(cardId);
        }
    }

    /** 生成记账流水号：JZ + yyyyMMdd + 当日三位序号。 */
    private String nextVoucherNo(LocalDate day) {
        String prefix = "JZ" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
        long seq = accountService.count(new LambdaQueryWrapper<CanteenAccount>()
                .likeRight(CanteenAccount::getVoucherNo, prefix)) + 1;
        return prefix + String.format("%03d", seq);
    }

    /** 按业务日期顺序滚动重算全部流水余额。 */
    @Transactional
    public int recalcBalance() {
        List<CanteenAccount> list = accountService.list(new LambdaQueryWrapper<CanteenAccount>()
                .orderByAsc(CanteenAccount::getBizDate).orderByAsc(CanteenAccount::getId));
        BigDecimal balance = BigDecimal.ZERO;
        for (CanteenAccount account : list) {
            balance = CanteenAccount.DIR_IN.equals(account.getDirection())
                    ? balance.add(nz(account.getAmount()))
                    : balance.subtract(nz(account.getAmount()));
            account.setBalance(balance);
            accountService.updateById(account);
        }
        return list.size();
    }

    /** 登记手工记账（上级拨入、内部转账、其他收入等无业务单据的收支）。 */
    @Transactional
    public String manualAccount(CanteenAccount account) {
        if (account.getBizDate() == null) {
            account.setBizDate(LocalDate.now());
        }
        if (!StringUtils.hasText(account.getStatus())) {
            account.setStatus(CanteenAccount.STATUS_POSTED);
        }
        account.setSourceType(CanteenAccount.SOURCE_MANUAL);
        account.setSourceId(account.getId() == null ? System.currentTimeMillis() : account.getId());
        account.setSourceNo(defaultStr(account.getSourceNo(), "手工"));
        account.setVoucherNo(StringUtils.hasText(account.getVoucherNo())
                ? account.getVoucherNo() : nextVoucherNo(account.getBizDate()));
        accountService.saveOrUpdate(account);
        recalcBalance();
        return "已记账";
    }

    @Transactional
    public void deleteAccount(Long id) {
        CanteenAccount account = accountService.getById(id);
        if (account == null) {
            return;
        }
        if (CanteenAccount.SOURCE_RECHARGE.equals(account.getSourceType()) && account.getCardId() != null) {
            CanteenCard card = cardService.getById(account.getCardId());
            if (card != null) {
                card.setBalance(nz(card.getBalance()).subtract(nz(account.getAmount())));
                cardService.updateById(card);
                syncCardStat(card.getId());
            }
        }
        accountService.removeById(id);
        recalcBalance();
    }

    // ================================================================ 工具

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal nz(int value) {
        return BigDecimal.valueOf(value);
    }

    private static String defaultStr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /** 取金额最大的分类。 */
    private static String maxKey(Map<String, BigDecimal> map) {
        String key = null;
        BigDecimal max = BigDecimal.valueOf(-1);
        for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
            if (entry.getValue().compareTo(max) > 0) {
                max = entry.getValue();
                key = entry.getKey();
            }
        }
        return key;
    }
}
