package fan.rich.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import fan.rich.dto.LedgerSummary;
import fan.rich.dto.NameValue;
import fan.rich.dto.ProductStatRow;
import fan.rich.dto.ShipmentQuery;
import fan.rich.dto.TrendPoint;
import fan.rich.entity.SaleFixAccount;
import fan.rich.entity.SalePricing;
import fan.rich.entity.SaleSettlement;
import fan.rich.entity.SaleShipment;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售台账服务：发货明细、点价、提金结算与各类统计。
 */
public interface SaleLedgerService {

    /** 发货台账分页查询。 */
    IPage<SaleShipment> pageShipments(ShipmentQuery query);

    /** 保存发货记录（新增或更新）。 */
    void saveShipment(SaleShipment shipment);

    /** 删除发货记录。 */
    void removeShipment(Long id);

    /** 核心指标汇总。 */
    LedgerSummary summary();

    /** 按发货品种统计金含量（g）。 */
    List<NameValue> statsByProduct();

    /** 按销售商统计金含量（g）。 */
    List<NameValue> statsByBuyer();

    /** 按销售商统计金含量（g），可限定发货日期区间（两端可空）。 */
    List<NameValue> statsByBuyer(LocalDate from, LocalDate to);

    /** 按发货品种的汇总行（含重量、金含量、返金量、已结算、库存、欠金量）。 */
    List<ProductStatRow> statsTable();

    /** 按发货品种的汇总行，可限定发货日期区间（两端可空）。 */
    List<ProductStatRow> statsTable(LocalDate from, LocalDate to);

    /** 月度发货趋势：金含量（g）与批次数量。 */
    List<TrendPoint> monthlyTrend();

    /** 点价记录列表。 */
    List<SalePricing> pricingList();

    /** 保存点价记录。 */
    void savePricing(SalePricing pricing);

    /** 删除点价记录。 */
    void removePricing(Long id);

    /** 提金结算列表。 */
    List<SaleSettlement> settlementList();

    /** 保存提金结算记录。 */
    void saveSettlement(SaleSettlement settlement);

    /** 删除提金结算记录。 */
    void removeSettlement(Long id);

    /** 点价情况台账。 */
    List<SaleFixAccount> fixAccounts();
}
