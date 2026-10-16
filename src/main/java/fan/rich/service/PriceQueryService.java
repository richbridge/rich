package fan.rich.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.entity.MdFuturesPrice;
import fan.rich.entity.MdSourceProduct;
import fan.rich.entity.MdSpotPrice;
import fan.rich.mapper.MdFuturesPriceMapper;
import fan.rich.mapper.MdSourceProductMapper;
import fan.rich.mapper.MdSpotPriceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 价格数据查询服务。
 *
 * <p>采集侧由 {@code CrawlerService} 负责写入，这里只做<b>读</b>：为列表页提供分页查询，
 * 为看板提供汇总指标与趋势序列。
 */
@Service
public class PriceQueryService {

    private final MdSpotPriceMapper spotMapper;
    private final MdFuturesPriceMapper futuresMapper;
    private final MdSourceProductMapper productMapper;

    /**
     * 多金属报价表的期货参考合约月份偏移：0=当月，1=次月，2=当月+2。
     *
     * <p>有色金属（铜铝铅锌锡）的活跃合约通常不是当月，报价习惯上取<b>当月+2</b>。
     */
    private static final int METAL_CONTRACT_MONTH_OFFSET = 2;

    /** 合约代码末尾 4 位的年月（cu2610 / pb2612）。 */
    private static final Pattern CONTRACT_MONTH = Pattern.compile("(\\d{4})$");

    public PriceQueryService(MdSpotPriceMapper spotMapper,
                             MdFuturesPriceMapper futuresMapper,
                             MdSourceProductMapper productMapper) {
        this.spotMapper = spotMapper;
        this.futuresMapper = futuresMapper;
        this.productMapper = productMapper;
    }

    // ------------------------------------------------------------------ 汇总

    /** 采集概况：各表数据量 + 各数据源最新交易日。 */
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spotCount", spotMapper.selectCount(null));
        result.put("futuresCount", futuresMapper.selectCount(null));
        result.put("productCount", productMapper.selectCount(null));

        Map<String, String> latest = new LinkedHashMap<>();
        for (Map<String, Object> row : spotMapper.selectLatestDateBySource()) {
            Object value = row.get("trade_date");
            latest.put(String.valueOf(row.get("source")), value == null ? "-" : value.toString());
        }
        // SHFE 走期货表，单独取最新日期补上
        LambdaQueryWrapper<MdFuturesPrice> w = new LambdaQueryWrapper<>();
        w.orderByDesc(MdFuturesPrice::getTradeDate).last("LIMIT 1");
        MdFuturesPrice newest = futuresMapper.selectOne(w);
        if (newest != null && newest.getTradeDate() != null) {
            latest.put("SHFE", newest.getTradeDate().toString());
        }
        result.put("latestDate", latest);
        return result;
    }

    // ------------------------------------------------------------------ 列表

    /** 现货行情分页列表。 */
    public IPage<MdSpotPrice> spotList(long page, long limit, String source, String keywords,
                                       LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MdSpotPrice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(source)) {
            wrapper.eq(MdSpotPrice::getSource, source);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(MdSpotPrice::getProductName, keywords)
                    .or().like(MdSpotPrice::getProductId, keywords));
        }
        if (startDate != null) {
            wrapper.ge(MdSpotPrice::getTradeDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(MdSpotPrice::getTradeDate, endDate);
        }
        wrapper.orderByDesc(MdSpotPrice::getTradeDate)
                .orderByAsc(MdSpotPrice::getSource)
                .orderByAsc(MdSpotPrice::getProductId);
        return spotMapper.selectPage(new Page<>(page, limit), wrapper);
    }

    /** 期货行情分页列表。 */
    public IPage<MdFuturesPrice> futuresList(long page, long limit, String instrumentId,
                                             String contractCode, LocalDate tradeDate) {
        LambdaQueryWrapper<MdFuturesPrice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(instrumentId)) {
            wrapper.eq(MdFuturesPrice::getInstrumentId, instrumentId);
        }
        if (StringUtils.hasText(contractCode)) {
            wrapper.like(MdFuturesPrice::getContractCode, contractCode);
        }
        if (tradeDate != null) {
            wrapper.eq(MdFuturesPrice::getTradeDate, tradeDate);
        }
        wrapper.orderByDesc(MdFuturesPrice::getTradeDate)
                .orderByAsc(MdFuturesPrice::getInstrumentId)
                .orderByAsc(MdFuturesPrice::getContractCode);
        return futuresMapper.selectPage(new Page<>(page, limit), wrapper);
    }

    /** 品种字典分页列表。 */
    public IPage<MdSourceProduct> productList(String source, String keywords, long page, long limit) {
        LambdaQueryWrapper<MdSourceProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(source)) {
            wrapper.eq(MdSourceProduct::getSource, source);
        }
        if (StringUtils.hasText(keywords)) {
            wrapper.and(w -> w.like(MdSourceProduct::getProductName, keywords)
                    .or().like(MdSourceProduct::getProductId, keywords));
        }
        wrapper.orderByAsc(MdSourceProduct::getSource).orderByAsc(MdSourceProduct::getId);
        return productMapper.selectPage(
                new Page<>(page <= 0 ? 1 : page, limit <= 0 ? 20 : limit), wrapper);
    }

    // ------------------------------------------------------------------ 看板

    /**
     * 某品种最近 N 个交易日的均价序列（升序）。
     *
     * @param source    数据源
     * @param productId 站点侧品种 ID
     * @param limit     取最近多少条
     * @return {dates:[], values:[]}
     */
    public Map<String, Object> trend(String source, String productId, int limit) {
        int size = limit <= 0 ? 30 : limit;
        LambdaQueryWrapper<MdSpotPrice> wrapper = new LambdaQueryWrapper<MdSpotPrice>()
                .eq(MdSpotPrice::getSource, source)
                .eq(MdSpotPrice::getProductId, productId)
                .orderByDesc(MdSpotPrice::getTradeDate)
                .last("LIMIT " + size);
        List<MdSpotPrice> rows = spotMapper.selectList(wrapper);
        Collections.reverse(rows);

        List<String> dates = new ArrayList<>(rows.size());
        List<BigDecimal> values = new ArrayList<>(rows.size());
        for (MdSpotPrice row : rows) {
            dates.add(row.getTradeDate() == null ? "" : row.getTradeDate().toString());
            // 优先用均价，缺失时退回收盘价/最高价，保证曲线连续
            values.add(firstNonNull(row.getPriceAvg(), row.getPriceClose(), row.getPriceHigh()));
        }
        return Map.of("dates", dates, "values", values);
    }

    /** 价格口径标签：行情日期为当日时是盘中最新价，历史日期则是收盘价。 */
    private static String priceType(LocalDate tradeDate) {
        return tradeDate != null && tradeDate.isEqual(LocalDate.now()) ? "最新价" : "收盘价";
    }

    /**
     * 多金属报价表：金/银取上金所 T+D 合约（昨结算用上一交易日加权平均价），
     * 铅/锌/铜/锡取上期所的主力合约（昨结算即前结算价）。
     *
     * <p><b>价格口径</b>：行情日期为<b>当日</b>时该列是盘中<b>最新价</b>（盘中延时行情，
     * 收盘后会被正式收盘价覆盖）；查询<b>历史日期</b>时该列是<b>收盘价</b>。
     * 每行用 {@code priceType} 字段标出，便于前端动态显示列名。
     *
     * @param date 指定交易日；为 null 时各源各自取最新可用交易日
     */
    public List<Map<String, Object>> multiMetalQuotes(LocalDate date) {
        List<Map<String, Object>> result = new ArrayList<>();
        addSgeRow(result, "金", "Au(T+D)", date);
        addSgeRow(result, "银", "Ag(T+D)", date);
        addFuturesRow(result, "铅", "pb", date);
        addFuturesRow(result, "锌", "zn", date);
        addFuturesRow(result, "铜", "cu", date);
        addFuturesRow(result, "锡", "sn", date);
        return result;
    }

    /**
     * 多金属报价表可选的交易日（倒序）：上金所与上期所已有行情日期的并集，
     * 便于按日期回溯查看历史报价。
     */
    public List<String> multiMetalDates() {
        Set<String> dates = new LinkedHashSet<>();
        List<LocalDate> sge = spotMapper.selectTradeDates("SGE");
        if (sge != null) {
            sge.forEach(d -> dates.add(d.toString()));
        }
        List<LocalDate> futures = futuresMapper.selectTradeDates();
        if (futures != null) {
            futures.forEach(d -> dates.add(d.toString()));
        }
        List<String> list = new ArrayList<>(dates);
        list.sort(Comparator.reverseOrder());
        return list;
    }

    /**
     * 上金所 T+D 行情行：最新价=收盘，昨结算=上一交易日的<b>加权平均价</b>。
     *
     * <p>上金所官方行情里没有「结算价」这一概念，其日行情的<b>加权平均价</b>是当日成交的
     * 成交额 ÷ 成交量，口径最接近结算价，因此这里用它代替收盘价作为昨日结算；
     * 该字段缺失（例如延时行情页不提供）时回退到上一交易日收盘价。
     */
    private void addSgeRow(List<Map<String, Object>> result, String name, String productId, LocalDate date) {
        LambdaQueryWrapper<MdSpotPrice> wrapper = new LambdaQueryWrapper<MdSpotPrice>()
                .eq(MdSpotPrice::getSource, "SGE")
                .eq(MdSpotPrice::getProductId, productId)
                .orderByDesc(MdSpotPrice::getTradeDate);
        if (date != null) {
            // 指定日期时：取该日期的行情 + 它之前最近的一个交易日算昨结算
            wrapper.le(MdSpotPrice::getTradeDate, date);
        }
        wrapper.last("LIMIT 2");
        List<MdSpotPrice> rows = spotMapper.selectList(wrapper);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("contract", productId);
        row.put("unit", "元/克");
        if (rows.isEmpty()) {
            // 该日无行情时保留占位行，避免表格行数忽多忽少
            row.put("latest", null);
            row.put("open", null);
            row.put("prevSettle", null);
            row.put("tradeDate", "");
            row.put("priceType", priceType(date));
            result.add(row);
            return;
        }
        // rows 已按交易日倒序：指定日期休市时自动落回该日之前最近的一个交易日
        MdSpotPrice latest = rows.get(0);
        // 昨日结算：优先加权平均价，缺失时回退收盘价，都没有则留空（不补 0）
        BigDecimal prevSettle = null;
        if (rows.size() > 1) {
            MdSpotPrice prev = rows.get(1);
            prevSettle = prev.getPriceAvg() != null ? prev.getPriceAvg() : prev.getPriceClose();
        }
        row.put("latest", firstNonNull(latest.getPriceClose(), latest.getPriceAvg()));
        row.put("open", latest.getPriceOpen());
        row.put("prevSettle", prevSettle);
        row.put("tradeDate", latest.getTradeDate() == null ? "" : latest.getTradeDate().toString());
        row.put("priceType", priceType(latest.getTradeDate()));
        row.put("unit", StringUtils.hasText(latest.getUnit()) ? latest.getUnit() : "元/克");
        result.add(row);
    }

    /**
     * 上期所行情行：参考合约取「当月 + {@link #METAL_CONTRACT_MONTH_OFFSET}」月的合约
     * （如 2026-10 取 2612），而不是成交量最大的主力合约。
     */
    private void addFuturesRow(List<Map<String, Object>> result, String name, String instrumentId, LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        MdFuturesPrice f = pickContract(futuresMapper.selectContractsOnDate(instrumentId, base),
                instrumentId, base);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("contract", f == null ? String.valueOf(instrumentId).toUpperCase() : f.getContractCode());
        row.put("latest", f == null ? null : f.getClosePrice());
        row.put("open", f == null ? null : f.getOpenPrice());
        row.put("prevSettle", f == null ? null : f.getPresettlementPrice());
        row.put("tradeDate", f == null || f.getTradeDate() == null ? "" : f.getTradeDate().toString());
        row.put("priceType", priceType(f == null ? null : f.getTradeDate()));
        row.put("unit", "元/吨");
        result.add(row);
    }

    /**
     * 按「当月 + N」挑选参考合约：优先精确命中 {@code 品种代码 + YYMM}，
     * 该月合约不存在时退回月份距离最近的合约（如贵金属只有双月合约）。
     */
    private MdFuturesPrice pickContract(List<MdFuturesPrice> contracts, String instrumentId, LocalDate base) {
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        YearMonth target = YearMonth.from(base).plusMonths(METAL_CONTRACT_MONTH_OFFSET);
        String want = instrumentId + yymm(target);
        for (MdFuturesPrice c : contracts) {
            if (want.equalsIgnoreCase(c.getContractCode())) {
                return c;
            }
        }
        MdFuturesPrice best = null;
        long bestDiff = Long.MAX_VALUE;
        for (MdFuturesPrice c : contracts) {
            YearMonth ym = parseContractMonth(c.getContractCode());
            if (ym == null) {
                continue;
            }
            long diff = Math.abs(ChronoUnit.MONTHS.between(target, ym));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = c;
            }
        }
        return best;
    }

    /** 合约代码末尾的年月（cu2610 → 2026-10）。 */
    private static YearMonth parseContractMonth(String contractCode) {
        if (contractCode == null) {
            return null;
        }
        Matcher m = CONTRACT_MONTH.matcher(contractCode);
        if (!m.find()) {
            return null;
        }
        String s = m.group(1);
        int month = Integer.parseInt(s.substring(2));
        if (month < 1 || month > 12) {
            return null;
        }
        return YearMonth.of(2000 + Integer.parseInt(s.substring(0, 2)), month);
    }

    /** YearMonth → YYMM（2026-12 → "2612"）。 */
    private static String yymm(YearMonth ym) {
        return String.format("%02d%02d", ym.getYear() % 100, ym.getMonthValue());
    }

    /** 期货品种的主力合约连续序列（升序）：每个交易日取成交量最大合约的收盘价。 */
    public Map<String, Object> futuresTrend(String instrumentId, int limit) {
        int size = limit <= 0 ? 30 : limit;
        List<Map<String, Object>> rows =
                new ArrayList<>(futuresMapper.selectMainContractTrend(instrumentId, size));
        Collections.reverse(rows);

        List<String> dates = new ArrayList<>(rows.size());
        List<BigDecimal> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object date = row.get("tradeDate");
            dates.add(date == null ? "" : date.toString());
            Object close = row.get("closePrice");
            values.add(close instanceof BigDecimal ? (BigDecimal) close
                    : new BigDecimal(String.valueOf(close == null ? "0" : close)));
        }
        return Map.of("dates", dates, "values", values);
    }

    /** 各数据源最新若干个报价，供总览卡片使用。 */
    public List<MdSpotPrice> latestSpot(String source, int limit) {        LambdaQueryWrapper<MdSpotPrice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(source)) {
            wrapper.eq(MdSpotPrice::getSource, source);
        }
        wrapper.orderByDesc(MdSpotPrice::getTradeDate).last("LIMIT " + limit);
        return spotMapper.selectList(wrapper);
    }

    /** 某品种按交易日正序的全部记录，用于多源对比。 */
    public List<MdSpotPrice> spotSeries(String source, String productId, int limit) {
        return trendList(source, productId, limit);
    }

    private List<MdSpotPrice> trendList(String source, String productId, int limit) {
        LambdaQueryWrapper<MdSpotPrice> wrapper = new LambdaQueryWrapper<MdSpotPrice>()
                .eq(MdSpotPrice::getSource, source)
                .eq(MdSpotPrice::getProductId, productId)
                .orderByDesc(MdSpotPrice::getTradeDate)
                .last("LIMIT " + limit);
        List<MdSpotPrice> rows = new ArrayList<>(spotMapper.selectList(wrapper));
        Collections.reverse(rows);
        return rows;
    }

    /** 期货某品种最新交易日的全部合约。 */
    public List<MdFuturesPrice> latestFutures(String instrumentId) {
        return futuresMapper.selectLatestContracts(instrumentId);
    }

    /** 已入库的现货品种清单，用于看板下拉框。 */
    public List<Map<String, Object>> spotSubjects() {
        return spotMapper.selectSubjects();
    }

    /** 期货已有数据的品种概览。 */
    public List<Map<String, Object>> instrumentSummary() {
        return futuresMapper.selectInstrumentSummary();
    }

    private static BigDecimal firstNonNull(BigDecimal... candidates) {
        for (BigDecimal value : candidates) {
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }
}
