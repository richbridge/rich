package fan.rich.crawler.pipeline;

import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.NumUtil;
import fan.rich.entity.MdFuturesPrice;
import fan.rich.entity.MdSourceProduct;
import fan.rich.entity.MdSpotPrice;
import fan.rich.mapper.MdFuturesPriceMapper;
import fan.rich.mapper.MdSourceProductMapper;
import fan.rich.mapper.MdSpotPriceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebMagic 采集结果落库管道。
 *
 * <p>一个生命周期对应一次采集任务：由 CrawlerService 在每次任务开始时 new 一个实例，
 * 结束后读取 {@link #getWritten()} 写入采集日志。因此它不是 Spring Bean，避免并发任务间
 * 的计数器互相污染。
 *
 * <p>三种结果类型分别处理：
 * <ul>
 *   <li>{@link CrawlKeys#SOURCE_PRODUCTS} → md_source_product；</li>
 *   <li>{@link CrawlKeys#SPOT_QUOTES} → md_spot_price；</li>
 *   <li>{@link CrawlKeys#FUTURES_QUOTES} → md_futures_price。</li>
 * </ul>
 * 落库均走 Mapper 上的 MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE} 批量语句，
 * 保证重复采集幂等。
 */
public class PricePipeline implements Pipeline {

    /** 现货落库模式。 */
    public enum SpotMode {

        /** 已存在的行用新值覆盖（交易所口径，收盘后本就该用正式价刷新盘中延时值）。 */
        UPSERT,

        /**
         * 已存在的行直接跳过，只补新行（当前调度已不再使用，保留备用）。
         *
         * <p>原本用于 SMM / CJ："第一次采到什么就永久保留什么"。现改用覆盖写——
         * 日间轮询要求把当天那一行不断刷成最新值；"历史行不被改写"改由采集侧的
         * 新鲜度闸门保证（目标日不是站点最新交易日时整批跳过，根本走不到落库这一步）。
         */
        INSERT_ONLY
    }

    /** 单条 INSERT 的最大行数，避免 SQL 过长 pack 超限。 */
    private static final int BATCH_SIZE = 400;

    private static final Logger log = LoggerFactory.getLogger(PricePipeline.class);

    private final MdSourceProductMapper productMapper;
    private final MdSpotPriceMapper spotMapper;
    private final MdFuturesPriceMapper futuresMapper;

    /** 现货落库模式，默认覆盖写。 */
    private final SpotMode spotMode;

    /** 本次任务写库行数（含新增与更新）。 */
    private final AtomicInteger written = new AtomicInteger();

    /** 本次任务处理页数。 */
    private final AtomicInteger pages = new AtomicInteger();

    public PricePipeline(MdSourceProductMapper productMapper,
                         MdSpotPriceMapper spotMapper,
                         MdFuturesPriceMapper futuresMapper) {
        this(productMapper, spotMapper, futuresMapper, SpotMode.UPSERT);
    }

    public PricePipeline(MdSourceProductMapper productMapper,
                         MdSpotPriceMapper spotMapper,
                         MdFuturesPriceMapper futuresMapper,
                         SpotMode spotMode) {
        this.productMapper = productMapper;
        this.spotMapper = spotMapper;
        this.futuresMapper = futuresMapper;
        this.spotMode = spotMode == null ? SpotMode.UPSERT : spotMode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(ResultItems resultItems, Task task) {
        pages.incrementAndGet();
        handleProducts(resultItems.get(CrawlKeys.SOURCE_PRODUCTS));
        handleSpot((List<MdSpotPrice>) resultItems.get(CrawlKeys.SPOT_QUOTES));
        handleFutures((List<MdFuturesPrice>) resultItems.get(CrawlKeys.FUTURES_QUOTES));
    }

    @SuppressWarnings("unchecked")
    private void handleProducts(Object raw) {
        if (!(raw instanceof Collection<?> records) || records.isEmpty()) {
            return;
        }
        // CJ 首页会给同一品种同时列出"价格"与"升贴水"两个入口，productId 相同 → 去重保留首个
        Map<String, MdSourceProduct> unique = new LinkedHashMap<>();
        for (MdSourceProduct item : (List<MdSourceProduct>) raw) {
            if (item == null || NumUtil.blank(item.getProductId())) {
                continue;
            }
            unique.putIfAbsent(item.getProductId(), item);
        }
        List<List<MdSourceProduct>> batches = NumUtil.partition(new ArrayList<>(unique.values()), BATCH_SIZE);
        for (List<MdSourceProduct> batch : batches) {
            written.addAndGet(productMapper.bulkUpsert(batch));
        }
    }

    private void handleSpot(List<MdSpotPrice> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }
        for (List<MdSpotPrice> batch : NumUtil.partition(quotes, BATCH_SIZE)) {
            int rows = spotMode == SpotMode.INSERT_ONLY
                    ? spotMapper.bulkInsertIgnore(batch)
                    : spotMapper.bulkUpsert(batch);
            written.addAndGet(rows);
        }
    }

    private void handleFutures(List<MdFuturesPrice> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }
        for (List<MdFuturesPrice> batch : NumUtil.partition(quotes, BATCH_SIZE)) {
            written.addAndGet(futuresMapper.bulkUpsert(batch));
        }
    }

    /** 返回本次任务入库行数。 */
    public int getWritten() {
        return written.get();
    }

    /** 返回本次任务处理的页面数。 */
    public int getPages() {
        return pages.get();
    }

    /** 调试用：把空结果记录到日志，便于排查"サイト改版"导致的静默失败。 */
    public static void logEmpty(String source, int records) {
        if (records == 0) {
            log.warn("[采集] {} 本次未解析到任何记录，请检查页面结构是否已变更", source);
        }
    }
}
