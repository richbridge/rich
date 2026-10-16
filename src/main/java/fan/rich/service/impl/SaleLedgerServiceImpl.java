package fan.rich.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.dto.LedgerSummary;
import fan.rich.dto.NameValue;
import fan.rich.dto.ProductStatRow;
import fan.rich.dto.ShipmentQuery;
import fan.rich.dto.TrendPoint;
import fan.rich.entity.SaleFixAccount;
import fan.rich.entity.SalePricing;
import fan.rich.entity.SaleSettlement;
import fan.rich.entity.SaleShipment;
import fan.rich.mapper.SaleFixAccountMapper;
import fan.rich.mapper.SalePricingMapper;
import fan.rich.mapper.SaleSettlementMapper;
import fan.rich.mapper.SaleShipmentMapper;
import fan.rich.service.SaleLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售台账服务实现。
 *
 * <p>台账数据量在千行级别，统计口径又经常随业务调整，因此聚合统一在内存中完成，
 * 避免为每个指标写一条 SQL。
 */
@Service
public class SaleLedgerServiceImpl implements SaleLedgerService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SaleShipmentMapper shipmentMapper;
    private final SalePricingMapper pricingMapper;
    private final SaleSettlementMapper settlementMapper;
    private final SaleFixAccountMapper fixAccountMapper;

    public SaleLedgerServiceImpl(SaleShipmentMapper shipmentMapper,
                                 SalePricingMapper pricingMapper,
                                 SaleSettlementMapper settlementMapper,
                                 SaleFixAccountMapper fixAccountMapper) {
        this.shipmentMapper = shipmentMapper;
        this.pricingMapper = pricingMapper;
        this.settlementMapper = settlementMapper;
        this.fixAccountMapper = fixAccountMapper;
    }

    @Override
    public IPage<SaleShipment> pageShipments(ShipmentQuery query) {
        Page<SaleShipment> page = new Page<>(query.getPage(), query.getLimit());
        LambdaQueryWrapper<SaleShipment> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getProductType())) {
            wrapper.eq(SaleShipment::getProductType, query.getProductType());
        }
        if (StringUtils.hasText(query.getCustomerName())) {
            wrapper.eq(SaleShipment::getCustomerName, query.getCustomerName());
        }
        if (StringUtils.hasText(query.getKeywords())) {
            String kw = query.getKeywords().trim();
            wrapper.and(w -> w.like(SaleShipment::getShipNo, kw)
                    .or().like(SaleShipment::getCustomerName, kw)
                    .or().like(SaleShipment::getSampleNo, kw)
                    .or().like(SaleShipment::getRemark, kw));
        }
        if (query.getStartDate() != null) {
            wrapper.ge(SaleShipment::getShipDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(SaleShipment::getShipDate, query.getEndDate());
        }
        wrapper.orderByDesc(SaleShipment::getShipDate).orderByDesc(SaleShipment::getId);
        return shipmentMapper.selectPage(page, wrapper);
    }

    @Override
    public void saveShipment(SaleShipment shipment) {
        if (shipment.getId() == null) {
            shipment.setCreateTime(java.time.LocalDateTime.now());
            shipmentMapper.insert(shipment);
        } else {
            shipment.setUpdateTime(java.time.LocalDateTime.now());
            shipmentMapper.updateById(shipment);
        }
    }

    @Override
    public void removeShipment(Long id) {
        shipmentMapper.deleteById(id);
    }

    @Override
    public LedgerSummary summary() {
        List<SaleShipment> all = shipmentMapper.selectList(new LambdaQueryWrapper<>());
        List<SaleSettlement> settlements = settlementMapper.selectList(new LambdaQueryWrapper<>());
        List<SaleFixAccount> fixes = fixAccountMapper.selectList(new LambdaQueryWrapper<>());

        LedgerSummary s = new LedgerSummary();

        BigDecimal dryWeight = BigDecimal.ZERO;
        BigDecimal concentrateGold = BigDecimal.ZERO;
        BigDecimal doreWeight = BigDecimal.ZERO;
        BigDecimal doreGold = BigDecimal.ZERO;
        BigDecimal returnGold = BigDecimal.ZERO;
        BigDecimal preExtract = BigDecimal.ZERO;
        BigDecimal doreSettled = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        long concentrateBatches = 0;
        long doreBatches = 0;

        // 口径与《销售统计》一致：只统计当年实际发货，剔除「上年结余」结转行
        for (SaleShipment row : all) {
            if (SaleShipment.KIND_PREV_YEAR.equals(row.getRowKind())) {
                continue;
            }
            boolean concentrate = SaleShipment.TYPE_CONCENTRATE.equals(row.getProductType());
            if (concentrate) {
                concentrateBatches++;
                dryWeight = add(dryWeight, row.getDryWeight());
                fee = add(fee, row.getProcessFee());
                concentrateGold = concentrateGold.add(nz(row.getGoldContent()));
            } else {
                doreBatches++;
                doreWeight = add(doreWeight, doreWeightOf(row));
                doreGold = doreGold.add(nz(row.getGoldContent()));
                doreSettled = add(doreSettled, row.getSettledGold());
            }
            returnGold = add(returnGold, row.getReturnGold());
            preExtract = add(preExtract, row.getPreExtractGold());
        }

        BigDecimal extracted = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        BigDecimal owe = BigDecimal.ZERO;
        BigDecimal fixQty = BigDecimal.ZERO;
        for (SaleFixAccount f : fixes) {
            extracted = add(extracted, f.getExtractedQty());
            stock = add(stock, f.getStockQty());
            owe = add(owe, f.getOweQty());
            fixQty = add(fixQty, f.getFixQty());
        }
        // 已结算优先取点价情况的「已提金」合计，无点价台账时回落到合质金明细的「已结算」列
        BigDecimal settled = fixes.isEmpty() ? doreSettled : extracted;

        BigDecimal settleAmount = BigDecimal.ZERO;
        BigDecimal settleWeight = BigDecimal.ZERO;
        for (SaleSettlement st : settlements) {
            settleAmount = add(settleAmount, st.getSettleAmount());
            settleWeight = add(settleWeight, st.getGoldWeight());
        }

        s.setConcentrateBatches(concentrateBatches);
        s.setDoreBatches(doreBatches);
        s.setDryWeight(scale(dryWeight, 3));
        s.setAvgGrade(dryWeight.compareTo(BigDecimal.ZERO) > 0
                ? scale(concentrateGold.divide(dryWeight, 4, RoundingMode.HALF_UP), 2)
                : BigDecimal.ZERO);
        s.setConcentrateGold(scale(concentrateGold, 2));
        s.setDoreWeight(scale(doreWeight, 2));
        s.setDoreGold(scale(doreGold, 2));
        s.setTotalGold(scale(concentrateGold.add(doreGold), 2));
        s.setReturnGold(scale(returnGold, 2));
        s.setPreExtractGold(scale(preExtract, 2));
        s.setSettledGold(scale(settled, 2));
        s.setStockGold(scale(stock, 2));
        s.setOweGold(scale(owe, 2));
        s.setFixQty(scale(fixQty, 2));
        s.setSettlementAmount(scale(settleAmount, 2));
        s.setSettlementWeight(scale(settleWeight, 2));
        s.setProcessFee(scale(fee, 2));
        return s;
    }

    @Override
    public List<NameValue> statsByProduct() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("浮选精矿粉", BigDecimal.ZERO);
        map.put("合质金", BigDecimal.ZERO);
        for (SaleShipment row : shipmentMapper.selectList(new LambdaQueryWrapper<>())) {
            if (SaleShipment.KIND_PREV_YEAR.equals(row.getRowKind())) {
                continue;
            }
            String key = SaleShipment.TYPE_CONCENTRATE.equals(row.getProductType())
                    ? "浮选精矿粉" : "合质金";
            map.merge(key, nz(row.getGoldContent()), BigDecimal::add);
        }
        List<NameValue> list = new ArrayList<>();
        map.forEach((k, v) -> list.add(new NameValue(k, scale(v, 2))));
        return list;
    }

    @Override
    public List<NameValue> statsByBuyer() {
        return statsByBuyer(null, null);
    }

    @Override
    public List<NameValue> statsByBuyer(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (SaleShipment row : shipmentMapper.selectList(new LambdaQueryWrapper<>())) {
            if (SaleShipment.KIND_PREV_YEAR.equals(row.getRowKind())) {
                continue;
            }
            if (!inRange(row.getShipDate(), from, to)) {
                continue;
            }
            String key = StringUtils.hasText(row.getCustomerName())
                    ? row.getCustomerName() : "未指定";
            map.merge(key, nz(row.getGoldContent()), BigDecimal::add);
        }
        List<NameValue> list = new ArrayList<>();
        map.forEach((k, v) -> list.add(new NameValue(k, scale(v, 2))));
        list.sort(Comparator.comparing(NameValue::getValue, Comparator.reverseOrder()));
        return list;
    }

    @Override
    public List<ProductStatRow> statsTable() {
        return statsTable(null, null);
    }

    @Override
    public List<ProductStatRow> statsTable(LocalDate from, LocalDate to) {
        List<SaleShipment> all = shipmentMapper.selectList(new LambdaQueryWrapper<>());
        List<SaleFixAccount> fixes = fixAccountMapper.selectList(new LambdaQueryWrapper<>());

        BigDecimal cWeight = BigDecimal.ZERO;
        BigDecimal cGold = BigDecimal.ZERO;
        BigDecimal cReturn = BigDecimal.ZERO;
        BigDecimal dWeight = BigDecimal.ZERO;
        BigDecimal dGold = BigDecimal.ZERO;
        BigDecimal dReturn = BigDecimal.ZERO;

        for (SaleShipment row : all) {
            if (SaleShipment.KIND_PREV_YEAR.equals(row.getRowKind())) {
                continue;
            }
            if (!inRange(row.getShipDate(), from, to)) {
                continue;
            }
            if (SaleShipment.TYPE_CONCENTRATE.equals(row.getProductType())) {
                cWeight = add(cWeight, row.getDryWeight());
                cGold = cGold.add(nz(row.getGoldContent()));
                cReturn = add(cReturn, row.getReturnGold());
            } else {
                dWeight = add(dWeight, doreWeightOf(row));
                dGold = dGold.add(nz(row.getGoldContent()));
                dReturn = add(dReturn, row.getReturnGold());
            }
        }

        ProductStatRow concentrate = new ProductStatRow();
        concentrate.setName("浮选精矿粉");
        concentrate.setWeightLabel("干重(t)");
        concentrate.setWeight(scale(cWeight, 3));
        concentrate.setGoldContent(scale(cGold, 2));
        concentrate.setReturnGold(scale(cReturn, 2));
        fillFix(concentrate, fixes, "精矿");

        ProductStatRow dore = new ProductStatRow();
        dore.setName("合质金");
        dore.setWeightLabel("重量(g)");
        dore.setWeight(scale(dWeight, 2));
        dore.setGoldContent(scale(dGold, 2));
        dore.setReturnGold(scale(dReturn, 2));
        fillFix(dore, fixes, "合质金");

        List<ProductStatRow> rows = new ArrayList<>();
        rows.add(concentrate);
        rows.add(dore);

        ProductStatRow total = new ProductStatRow();
        total.setName("销售总合计");
        total.setWeightLabel("-");
        total.setWeight(null);
        total.setGoldContent(scale(cGold.add(dGold), 2));
        total.setReturnGold(scale(cReturn.add(dReturn), 2));
        total.setSettled(scale(add(concentrate.getSettled(), dore.getSettled()), 2));
        total.setStock(scale(add(nz(concentrate.getStock()), nz(dore.getStock())), 2));
        total.setOwe(scale(add(nz(concentrate.getOwe()), nz(dore.getOwe())), 2));
        rows.add(total);
        return rows;
    }

    /**
     * 合质金发货重量：口径与《销售统计》一致，取「留样后重量」，缺失时回落到溶火后重量。
     */
    private static BigDecimal doreWeightOf(SaleShipment row) {
        if (row.getRetainedWeight() != null && row.getRetainedWeight().compareTo(BigDecimal.ZERO) > 0) {
            return row.getRetainedWeight();
        }
        return nz(row.getPostMeltWeight());
    }

    /** 从点价情况台账回填已结算 / 库存 / 欠金量。 */
    private static void fillFix(ProductStatRow row, List<SaleFixAccount> fixes, String keyword) {
        BigDecimal settled = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        BigDecimal owe = BigDecimal.ZERO;
        for (SaleFixAccount f : fixes) {
            if (f.getVariety() == null || !f.getVariety().contains(keyword)) {
                continue;
            }
            settled = add(settled, f.getExtractedQty());
            stock = add(stock, f.getStockQty());
            owe = add(owe, f.getOweQty());
        }
        row.setSettled(scale(settled, 2));
        row.setStock(scale(stock, 2));
        row.setOwe(scale(owe, 2));
    }

    @Override
    public List<TrendPoint> monthlyTrend() {
        Map<String, BigDecimal> goldMap = new LinkedHashMap<>();
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (SaleShipment row : shipmentMapper.selectList(new LambdaQueryWrapper<>())) {
            LocalDate date = row.getShipDate();
            if (date == null || SaleShipment.KIND_PREV_YEAR.equals(row.getRowKind())) {
                continue;
            }
            String month = date.format(MONTH_FMT);
            goldMap.merge(month, nz(row.getGoldContent()), BigDecimal::add);
            countMap.merge(month, 1L, Long::sum);
        }
        List<TrendPoint> list = new ArrayList<>();
        new ArrayList<>(goldMap.keySet()).stream().sorted().forEach(month -> {
            TrendPoint p = new TrendPoint();
            p.setMonth(month);
            p.setAmount(scale(goldMap.get(month), 2));
            p.setOrderCount(countMap.getOrDefault(month, 0L));
            list.add(p);
        });
        return list;
    }

    @Override
    public List<SalePricing> pricingList() {
        return pricingMapper.selectList(new LambdaQueryWrapper<SalePricing>()
                .orderByAsc(SalePricing::getPriceDate)
                .orderByAsc(SalePricing::getId));
    }

    @Override
    public void savePricing(SalePricing pricing) {
        if (pricing.getId() == null) {
            pricing.setCreateTime(java.time.LocalDateTime.now());
            pricingMapper.insert(pricing);
        } else {
            pricingMapper.updateById(pricing);
        }
    }

    @Override
    public void removePricing(Long id) {
        pricingMapper.deleteById(id);
    }

    @Override
    public List<SaleSettlement> settlementList() {
        return settlementMapper.selectList(new LambdaQueryWrapper<SaleSettlement>()
                .orderByAsc(SaleSettlement::getGoldPickDate)
                .orderByAsc(SaleSettlement::getId));
    }

    @Override
    public void saveSettlement(SaleSettlement settlement) {
        if (settlement.getId() == null) {
            settlement.setCreateTime(java.time.LocalDateTime.now());
            settlementMapper.insert(settlement);
        } else {
            settlementMapper.updateById(settlement);
        }
    }

    @Override
    public void removeSettlement(Long id) {
        settlementMapper.deleteById(id);
    }

    @Override
    public List<SaleFixAccount> fixAccounts() {
        return fixAccountMapper.selectList(new LambdaQueryWrapper<SaleFixAccount>()
                .orderByAsc(SaleFixAccount::getId));
    }

    // ------------------------------------------------------------------ 工具方法

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 发货日期是否落在区间内（两端闭区间，null 表示不限）。 */
    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        return (from == null || !date.isBefore(from))
                && (to == null || !date.isAfter(to));
    }

    private static BigDecimal add(BigDecimal base, BigDecimal delta) {
        return base.add(nz(delta));
    }

    private static BigDecimal scale(BigDecimal v, int scale) {
        return nz(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
