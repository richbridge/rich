package fan.rich.sale;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fan.rich.entity.SaleFixAccount;
import fan.rich.entity.SalePricing;
import fan.rich.entity.SaleSettlement;
import fan.rich.entity.SaleShipment;
import fan.rich.mapper.SaleFixAccountMapper;
import fan.rich.mapper.SalePricingMapper;
import fan.rich.mapper.SaleSettlementMapper;
import fan.rich.mapper.SaleShipmentMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 《金宝销售台账》Excel 导入器。
 *
 * <p>台账是典型的"多区块报表"：一个工作簿里既有发货明细，又有按品种/按销售商的汇总，
 * 还有点价情况与提金开票。这里按<b>区块</b>分别解析并落库：
 * <ol>
 *     <li>{@code 浮精汇总} → 浮选精矿发货明细（sale_shipment，product_type=CONCENTRATE）</li>
 *     <li>{@code 合质金销售台账} → 合质金发货明细（sale_shipment，product_type=DORE）</li>
 *     <li>{@code 销售统计} 点价情况表 → sale_fix_account</li>
 *     <li>{@code 销售统计} 点价统计表 → sale_pricing</li>
 *     <li>{@code Sheet1} 提金开票情况 → sale_settlement</li>
 * </ol>
 *
 * <p>发货明细按 {@code (product_type, ship_no)} 幂等 upsert，重复导入只覆盖不堆叠；
 * 点价 / 结算 / 点价情况属于统计性数据，采用整表重刷。
 */
@Component
public class SaleExcelImporter {

    /** 台账文件名中的年度，如「2026年金宝矿业销售统计汇总表」。 */
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})\\s*年");

    /**
     * 销售商「国大」的工商全称。
     *
     * <p>台账单元格里写的都是简称（「国大」「国大上年结余」「国大合质金」），落库统一
     * 换成全称，保证与 sale_customer 档案、发货明细、销售统计口径一致。
     */
    public static final String BUYER_GUODA = "烟台国大贸易有限公司";

    private final SaleShipmentMapper shipmentMapper;
    private final SalePricingMapper pricingMapper;
    private final SaleSettlementMapper settlementMapper;
    private final SaleFixAccountMapper fixAccountMapper;

    public SaleExcelImporter(SaleShipmentMapper shipmentMapper,
                             SalePricingMapper pricingMapper,
                             SaleSettlementMapper settlementMapper,
                             SaleFixAccountMapper fixAccountMapper) {
        this.shipmentMapper = shipmentMapper;
        this.pricingMapper = pricingMapper;
        this.settlementMapper = settlementMapper;
        this.fixAccountMapper = fixAccountMapper;
    }

    /** 导入结果。 */
    public record ImportResult(int concentrate, int dore, int pricing, int settlement,
                               int fixAccount, List<String> warnings) {
    }

    /** 当前台账已导入的发货记录条数。 */
    public long countShipments() {
        Long total = shipmentMapper.selectCount(new LambdaQueryWrapper<SaleShipment>());
        return total == null ? 0L : total;
    }

    /**
     * 导入指定路径的销售台账。
     *
     * @param path xlsx 文件绝对路径
     * @return 各区块写入条数与警告信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("台账文件不存在：" + path);
        }
        List<String> warnings = new ArrayList<>();
        int concentrate;
        int dore;
        int pricing;
        int settlement;
        int fixAccount;

        try (InputStream in = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet concentrateSheet = wb.getSheet("浮精汇总");
            if (concentrateSheet == null) {
                warnings.add("未找到工作表「浮精汇总」，已跳过精矿明细");
                concentrate = 0;
            } else {
                concentrate = importConcentrate(concentrateSheet);
            }

            Sheet doreSheet = wb.getSheet("合质金销售台账");
            if (doreSheet == null) {
                warnings.add("未找到工作表「合质金销售台账」，已跳过合质金明细");
                dore = 0;
            } else {
                dore = importDore(doreSheet);
            }

            Sheet statSheet = wb.getSheet("销售统计");
            if (statSheet == null) {
                warnings.add("未找到工作表「销售统计」，已跳过点价数据");
                fixAccount = 0;
                pricing = 0;
            } else {
                fixAccount = importFixAccount(statSheet);
                pricing = importPricing(statSheet);
            }

            Sheet settleSheet = wb.getSheet("Sheet1");
            if (settleSheet == null) {
                warnings.add("未找到工作表「Sheet1」，已跳过提金开票数据");
                settlement = 0;
            } else {
                settlement = importSettlement(settleSheet);
            }
        }
        return new ImportResult(concentrate, dore, pricing, settlement, fixAccount, warnings);
    }

    // ------------------------------------------------------------------ 浮选精矿

    private int importConcentrate(Sheet ws) {
        int h = 1; // 表头在第 2 行（0-based = 1）
        int cBuyer = ExcelCells.findColumn(ws, h, "销售商");
        int cBatch = ExcelCells.findColumn(ws, h, "回数");
        int cDate = ExcelCells.findColumn(ws, h, "发货日期");
        int cGross = ExcelCells.findColumn(ws, h, "毛重(T)", "毛重(t)");
        int cTare = ExcelCells.findColumn(ws, h, "皮重(T)", "皮重(t)");
        int cBag = ExcelCells.findColumn(ws, h, "扣袋(T)", "扣袋(t)");
        int cNet = ExcelCells.findColumn(ws, h, "净重(T)", "净重(t)");
        int cMoisture = ExcelCells.findColumn(ws, h, "水份(%)");
        int cDry = ExcelCells.findColumn(ws, h, "干重(t)", "干重（t)");
        int cGrade = ExcelCells.findColumn(ws, h, "结算品位(g/t)", "结算品位（g/t)");
        int cGold = ExcelCells.findColumn(ws, h, "实际金含量(g)", "实际金含量（g)");
        int cReturn = ExcelCells.findColumn(ws, h, "返金量(g)", "返金量（g)");
        int cPre = ExcelCells.findColumn(ws, h, "预提金量(g)", "预提金量（g）");
        int cDiff = ExcelCells.findColumn(ws, h, "品位差");
        int cFee = ExcelCells.findColumn(ws, h, "加工费");
        int cPick = ExcelCells.findColumn(ws, h, "是否提货");
        int cArb = ExcelCells.findColumn(ws, h, "仲裁前品位");
        int cRemark = ExcelCells.findColumn(ws, h, "备注");

        int count = 0;
        int last = Math.min(ws.getLastRowNum(), 246);
        for (int r = 2; r <= last; r++) {
            String batch = ExcelCells.text(ws, r, cBatch);
            LocalDate shipDate = ExcelCells.date(ws, r, cDate);
            String buyer = ExcelCells.text(ws, r, cBuyer);
            if (batch == null && shipDate == null && buyer == null) {
                continue;
            }
            if (batch != null && (batch.contains("合计") || batch.contains("平均"))) {
                continue;
            }
            // 客户「金翅岭」已删除，台账里残留的相关行不再入库
            if (buyer != null && buyer.contains("金翅岭")) {
                continue;
            }

            String kind = SaleShipment.KIND_NORMAL;
            if (batch != null && batch.contains("上年结余")) {
                kind = SaleShipment.KIND_PREV_YEAR;
            } else if (batch == null && shipDate == null) {
                kind = SaleShipment.KIND_ARBITRATION;
                batch = "仲裁补差";
            }

            SaleShipment row = new SaleShipment();
            row.setProductType(SaleShipment.TYPE_CONCENTRATE);
            row.setRowKind(kind);
            row.setShipNo(batch);
            row.setCustomerName(normalizeBuyer(buyer));
            row.setShipDate(shipDate);
            row.setGrossWeight(ExcelCells.decimal(ws, r, cGross));
            row.setTareWeight(ExcelCells.decimal(ws, r, cTare));
            row.setBagDeduct(ExcelCells.decimal(ws, r, cBag));
            row.setNetWeight(ExcelCells.decimal(ws, r, cNet));
            row.setMoisture(ExcelCells.decimal(ws, r, cMoisture));
            row.setDryWeight(ExcelCells.decimal(ws, r, cDry));
            row.setSettleGrade(ExcelCells.decimal(ws, r, cGrade));
            row.setGoldContent(ExcelCells.decimal(ws, r, cGold));
            row.setReturnGold(ExcelCells.decimal(ws, r, cReturn));
            row.setPreExtractGold(ExcelCells.decimal(ws, r, cPre));
            row.setGradeDiff(ExcelCells.decimal(ws, r, cDiff));
            row.setProcessFee(ExcelCells.decimal(ws, r, cFee));
            row.setPickedUp(ExcelCells.text(ws, r, cPick));
            row.setArbGrade(ExcelCells.decimal(ws, r, cArb));
            // 台账「备注」列混着两类内容：数值=仲裁差(g)，文字=说明（如「361-383仲裁」）
            BigDecimal arbDiff = cRemark >= 0 ? ExcelCells.decimal(ws, r, cRemark) : null;
            if (arbDiff != null) {
                row.setArbDiff(arbDiff);
            } else if (cRemark >= 0) {
                row.setRemark(ExcelCells.text(ws, r, cRemark));
            }
            // 备注右侧还有一列无表头的文字说明（如「…回付加工费…元」），并入备注
            if (cRemark >= 0) {
                String extra = ExcelCells.text(ws, r, cRemark + 1);
                if (extra != null && !extra.isBlank()) {
                    String base = row.getRemark();
                    row.setRemark(base == null || base.isBlank() ? extra : base + "；" + extra);
                }
            }

            if (upsertShipment(row)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 合质金

    private int importDore(Sheet ws) {
        int h = 1;
        int cBuyer = ExcelCells.findColumn(ws, h, "金宝发货统计");
        int cDate = ExcelCells.findColumn(ws, h, "发货日期");
        int cSample = ExcelCells.findColumn(ws, h, "样品编号");
        int cPreMelt = ExcelCells.findColumn(ws, h, "溶火前国大重量(g)", "溶火前国大重量（克）");
        int cImpurity = ExcelCells.findColumn(ws, h, "杂质(g)", "杂质（克）");
        int cPostMelt = ExcelCells.findColumn(ws, h, "溶火后重量(g)", "溶火后重量（克）");
        int cSampleW = ExcelCells.findColumn(ws, h, "样品重量(g)", "样品重量（克）");
        int cRetained = ExcelCells.findColumn(ws, h, "留样后重量(g)", "留样后重量（克）");
        int cGoldGrade = ExcelCells.findColumn(ws, h, "金结算品位(%)", "金结算品位（%)");
        int cGold = ExcelCells.findColumn(ws, h, "金实际金含量(g)", "金实际金含量（g)");
        int cReturn = ExcelCells.findColumn(ws, h, "返金量(g)", "返金量（g)");
        int cSilverGrade = ExcelCells.findColumn(ws, h, "银结算品位(%)", "银结算品位（%）");
        int cSilver = ExcelCells.findColumn(ws, h, "银实际金含量(g)", "银实际金含量（g)");
        int cReturnSilver = ExcelCells.findColumn(ws, h, "返银量(g)", "返银量（g)");
        int cSettled = ExcelCells.findColumn(ws, h, "已结算");
        int cPickDate = ExcelCells.findColumn(ws, h, "提金日");
        int cPreSteam = ExcelCells.findColumn(ws, h, "蒸前重量");
        int cExternal = ExcelCells.findColumn(ws, h, "外检");
        int cRatio = ExcelCells.findColumn(ws, h, "金银占比");
        int cRemark = ExcelCells.findColumn(ws, h, "备注");

        int count = 0;
        int last = Math.min(ws.getLastRowNum(), 24);
        for (int r = 2; r <= last; r++) {
            String sampleNo = ExcelCells.text(ws, r, cSample);
            if (sampleNo == null || sampleNo.contains("合计")) {
                continue;
            }
            String doreBuyer = ExcelCells.text(ws, r, cBuyer);
            // 客户「金翅岭」已删除，台账里残留的相关行不再入库
            if (doreBuyer != null && doreBuyer.contains("金翅岭")) {
                continue;
            }

            SaleShipment row = new SaleShipment();
            row.setProductType(SaleShipment.TYPE_DORE);
            row.setRowKind(SaleShipment.KIND_NORMAL);
            row.setShipNo(sampleNo);
            row.setSampleNo(sampleNo);
            row.setCustomerName(normalizeBuyer(doreBuyer));
            row.setShipDate(ExcelCells.date(ws, r, cDate));
            row.setPreMeltWeight(ExcelCells.decimal(ws, r, cPreMelt));
            row.setImpurity(ExcelCells.decimal(ws, r, cImpurity));
            row.setPostMeltWeight(ExcelCells.decimal(ws, r, cPostMelt));
            row.setSampleWeight(ExcelCells.decimal(ws, r, cSampleW));
            row.setRetainedWeight(ExcelCells.decimal(ws, r, cRetained));
            row.setGoldGrade(ExcelCells.decimal(ws, r, cGoldGrade));
            row.setGoldContent(ExcelCells.decimal(ws, r, cGold));
            row.setReturnGold(ExcelCells.decimal(ws, r, cReturn));
            row.setSilverGrade(ExcelCells.decimal(ws, r, cSilverGrade));
            row.setSilverContent(ExcelCells.decimal(ws, r, cSilver));
            row.setReturnSilver(ExcelCells.decimal(ws, r, cReturnSilver));
            row.setSettledGold(ExcelCells.decimal(ws, r, cSettled));
            row.setGoldPickDate(ExcelCells.date(ws, r, cPickDate));
            row.setPreSteamWeight(ExcelCells.decimal(ws, r, cPreSteam));
            row.setExternalCheck(ExcelCells.text(ws, r, cExternal));
            row.setAuAgRatio(ExcelCells.decimal(ws, r, cRatio));
            row.setRemark(ExcelCells.text(ws, r, cRemark));

            if (upsertShipment(row)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 点价情况表

    private int importFixAccount(Sheet ws) {
        int h = 14; // 表头在第 15 行
        int cVariety = ExcelCells.findColumn(ws, h, "点价种类");
        int cPrev = ExcelCells.findColumn(ws, h, "上年余量(g)", "上年余量（g)");
        int cFix = ExcelCells.findColumn(ws, h, "点价量(g)", "点价量（g)");
        int cShipped = ExcelCells.findColumn(ws, h, "已发货量(g)", "已发货量（g)");
        int cExtract = ExcelCells.findColumn(ws, h, "已提金");
        int cStock = ExcelCells.findColumn(ws, h, "库存");
        int cOwe = ExcelCells.findColumn(ws, h, "欠金量");
        int cRemark = ExcelCells.findColumn(ws, h, "备注");
        if (cVariety < 0) {
            return 0;
        }

        // 统计性数据整表重刷，避免重复导入堆叠
        fixAccountMapper.delete(new LambdaQueryWrapper<SaleFixAccount>().isNotNull(SaleFixAccount::getId));

        int year = detectYear(ws);
        int count = 0;
        int last = Math.min(ws.getLastRowNum(), 20);
        for (int r = 15; r <= last; r++) {
            String variety = ExcelCells.text(ws, r, cVariety);
            if (variety == null || variety.contains("合计")) {
                continue;
            }
            // 客户「金翅岭」已删除，其点价情况不再统计
            if (variety.contains("金翅岭")) {
                continue;
            }
            SaleFixAccount row = new SaleFixAccount();
            row.setStatYear(year);
            row.setVariety(variety);
            row.setPrevBalance(ExcelCells.decimal(ws, r, cPrev));
            row.setFixQty(ExcelCells.decimal(ws, r, cFix));
            row.setShippedQty(ExcelCells.decimal(ws, r, cShipped));
            row.setExtractedQty(ExcelCells.decimal(ws, r, cExtract));
            row.setStockQty(ExcelCells.decimal(ws, r, cStock));
            row.setOweQty(ExcelCells.decimal(ws, r, cOwe));
            row.setRemark(ExcelCells.text(ws, r, cRemark));
            row.setCreateTime(LocalDateTime.now());
            fixAccountMapper.insert(row);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ 点价统计表

    private int importPricing(Sheet ws) {
        int h = 22; // 表头在第 23 行
        int cVariety = ExcelCells.findColumn(ws, h, "26年点价情况", "点价情况");
        int cDate = ExcelCells.findColumn(ws, h, "点价日");
        int cFix = ExcelCells.findColumn(ws, h, "点价");
        int cSettle = ExcelCells.findColumn(ws, h, "结算价");
        int cQty = ExcelCells.findColumn(ws, h, "点价数量");
        int cAvg = ExcelCells.findColumn(ws, h, "平均点价");
        int cAmount = ExcelCells.findColumn(ws, h, "点价成交额(万元)", "点价成交额（万元）");
        int cRemark = ExcelCells.findColumn(ws, h, "备注");
        if (cVariety < 0) {
            return 0;
        }

        pricingMapper.delete(new LambdaQueryWrapper<SalePricing>().isNotNull(SalePricing::getId));

        int count = 0;
        int last = Math.min(ws.getLastRowNum(), 40);
        for (int r = 23; r <= last; r++) {
            String variety = ExcelCells.text(ws, r, cVariety);
            if (variety == null || !(variety.equals(SalePricing.VARIETY_FLOAT)
                    || variety.equals(SalePricing.VARIETY_DORE))) {
                continue;
            }
            SalePricing row = new SalePricing();
            row.setVariety(variety);
            row.setPriceDate(ExcelCells.date(ws, r, cDate));
            row.setFixPrice(ExcelCells.decimal(ws, r, cFix));
            row.setSettlePrice(ExcelCells.decimal(ws, r, cSettle));
            row.setQuantity(ExcelCells.decimal(ws, r, cQty));
            row.setAvgPrice(ExcelCells.decimal(ws, r, cAvg));
            row.setAmount(ExcelCells.decimal(ws, r, cAmount));
            row.setRemark(ExcelCells.text(ws, r, cRemark));
            // 台账里点价统计分成两段：23~32 行为主表，34 行起为补充点价
            row.setGroupTag(r < 33 ? "26年点价" : "补充点价");
            row.setCreateTime(LocalDateTime.now());
            pricingMapper.insert(row);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ 提金开票

    private int importSettlement(Sheet ws) {
        int h = 1; // 表头在第 2 行
        int cBatch = ExcelCells.findColumn(ws, h, "浮选金");
        int cDate = ExcelCells.findColumn(ws, h, "提金日");
        int cPrice = ExcelCells.findColumn(ws, h, "结算价格");
        int cWeight = ExcelCells.findColumn(ws, h, "提金重量(g)", "提金重量（g)");
        int cAmount = ExcelCells.findColumn(ws, h, "结算金额");
        int cInvoice = ExcelCells.findColumn(ws, h, "开票情况");
        int cRemark = ExcelCells.findColumn(ws, h, "备注");
        if (cBatch < 0) {
            cBatch = 0;
        }

        settlementMapper.delete(new LambdaQueryWrapper<SaleSettlement>()
                .isNotNull(SaleSettlement::getId));

        int count = 0;
        int last = Math.min(ws.getLastRowNum(), 40);
        for (int r = 2; r <= last; r++) {
            String batch = ExcelCells.text(ws, r, cBatch);
            if (batch == null || batch.contains("合计")) {
                continue;
            }
            SaleSettlement row = new SaleSettlement();
            row.setCategory(SalePricing.VARIETY_FLOAT);
            row.setBatchNo(batch);
            row.setGoldPickDate(ExcelCells.date(ws, r, cDate));
            row.setSettlePrice(ExcelCells.decimal(ws, r, cPrice));
            row.setGoldWeight(ExcelCells.decimal(ws, r, cWeight));
            row.setSettleAmount(ExcelCells.decimal(ws, r, cAmount));
            row.setInvoiceStatus(ExcelCells.text(ws, r, cInvoice));
            row.setRemark(ExcelCells.text(ws, r, cRemark));
            row.setCreateTime(LocalDateTime.now());
            settlementMapper.insert(row);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ 工具方法

    /** 按 (品种, 批次号) 幂等写入：已存在则更新，不存在则插入。 */
    private boolean upsertShipment(SaleShipment row) {
        if (row.getShipNo() == null) {
            return false;
        }
        SaleShipment exists = shipmentMapper.selectOne(new LambdaQueryWrapper<SaleShipment>()
                .eq(SaleShipment::getProductType, row.getProductType())
                .eq(SaleShipment::getShipNo, row.getShipNo()));
        if (exists != null) {
            row.setId(exists.getId());
            row.setCreateTime(exists.getCreateTime());
            row.setUpdateTime(LocalDateTime.now());
            shipmentMapper.updateById(row);
            return true;
        }
        row.setCreateTime(LocalDateTime.now());
        shipmentMapper.insert(row);
        return true;
    }

    /**
     * 销售商名称归一：「国大上年结余」「国大合质金」等写法统一为客户全称
     * {@value #BUYER_GUODA}；客户「金翅岭」已下线，返回 null 表示整行不入库。
     */
    private static String normalizeBuyer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.contains("国大")) {
            return BUYER_GUODA;
        }
        if (raw.contains("金翅岭")) {
            return null;
        }
        return raw;
    }

    /** 从销售统计表标题中解析统计年度。 */
    private static int detectYear(Sheet ws) {
        String title = ExcelCells.text(ws, 0, 0);
        if (title != null) {
            Matcher m = YEAR_PATTERN.matcher(title);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return LocalDate.now().getYear();
    }

    /** 数值安全相加。 */
    static BigDecimal add(BigDecimal base, BigDecimal delta) {
        if (delta == null) {
            return base;
        }
        return base == null ? delta : base.add(delta);
    }
}
