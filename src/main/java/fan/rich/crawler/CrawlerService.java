package fan.rich.crawler;

import fan.rich.dto.CrawlResult;
import fan.rich.entity.MdCrawlLog;
import fan.rich.entity.MdSourceProduct;
import fan.rich.mapper.MdCrawlLogMapper;
import fan.rich.mapper.MdFuturesPriceMapper;
import fan.rich.mapper.MdSourceProductMapper;
import fan.rich.mapper.MdSpotPriceMapper;
import fan.rich.crawler.pipeline.PricePipeline;
import fan.rich.crawler.support.ErrorCollector;
import fan.rich.crawler.processor.SgeDelayProcessor;
import fan.rich.crawler.processor.SgeProcessor;
import fan.rich.crawler.processor.ShfeDailyProcessor;
import fan.rich.crawler.processor.ShfeProcessor;
import fan.rich.crawler.processor.SmmProductProcessor;
import fan.rich.crawler.processor.SmmQuoteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Spider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 采集任务编排服务。
 *
 * <p>对外动作分两类，均可被手动触发或定时触发：
 * <ol>
 *   <li><b>按节奏采集</b>（定时任务用）：
 *     <ul>
 *       <li>{@link #crawlIntraday(CrawlSource)} —— 当天最新价，工作日 08:00~18:00 每半小时一次；</li>
 *       <li>{@link #crawlNightSettle(CrawlSource, LocalDate)} —— 上一工作日终值，
 *           在「上一工作日 18:00 ~ 当天 08:00」窗口内每源补采一次。</li>
 *     </ul>
 *   </li>
 *   <li><b>手动采集</b>（页面上点按钮用）：
 *     <ul>
 *       <li>{@link #discover(CrawlSource)} —— 品种发现，仅 SMM / CJ 需要
 *           （SHFE / SGE 的合约号是确定的）；</li>
 *       <li>{@link #crawlQuotes(CrawlSource, LocalDate)} —— 单个数据源的完整采集；</li>
 *       <li>{@link #crawlAll(LocalDate)} —— 依次跑通四个数据源。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>并发保护：以 {@code source:taskType} 为 key 加一把内存锁，避免用户在页面上连点造成
 * 重复请求外部站点。
 *
 * <p>每次执行都会写一条 {@code md_crawl_log}，页面可通过日志直观看到"哪一次失败、为什么失败"。
 */
@Service
public class CrawlerService {

    /** 任务类型：品种发现。 */
    public static final String TASK_DISCOVER = "DISCOVER";

    /** 任务类型：行情采集（手动触发或完整采集）。 */
    public static final String TASK_QUOTE = "QUOTE";

    /**
     * 任务类型：日间「当天最新价」轮询（工作日 08:00~18:00 每半小时一次）。
     *
     * <p>四个数据源统一走这个口径：交易所取延时行情/最新价，现货取当日最新报价。
     */
    public static final String TASK_INTRADAY = "INTRADAY";

    /**
     * 任务类型：夜间「上一工作日终值」补采（上一工作日 18:00 ~ 当天 08:00）。
     *
     * <p>四个数据源统一走这个口径，只取目标那一天：交易所取正式日行情（收盘价/结算价），
     * 现货取当日结算数据。窗口内每个数据源只成功采一次，多开机一次不会有额外请求。
     */
    public static final String TASK_SETTLE = "SETTLE";

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final MdSourceProductMapper productMapper;
    private final MdSpotPriceMapper spotMapper;
    private final MdFuturesPriceMapper futuresMapper;
    private final MdCrawlLogMapper logMapper;
    private final int threadNum;
    private final int sgeBackfillDays;
    private final int shfeBackfillDays;

    /**
     * SMM / CJ 是否只接受"最新交易日"的数据。
     *
     * <p>这两个站点只对最新交易日返回未经加密变更的真值，历史行被施加了
     * {@code 真值 × n × 万分之1} 的随机偏移，属于不可还原的脏数据。
     * 开启后，目标日一旦不是站点口径下的最新交易日，整批跳过不入库。
     * 详见 SmmQuoteProcessor 类注释。
     */
    private final boolean spotRequireFresh;

    /** 正在执行中的任务 key（source:taskType），用于幂等拒绝。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public CrawlerService(MdSourceProductMapper productMapper,
                          MdSpotPriceMapper spotMapper,
                          MdFuturesPriceMapper futuresMapper,
                          MdCrawlLogMapper logMapper,
                          @Value("${rich.crawler.thread:4}") int threadNum,
                          @Value("${rich.crawler.sge-backfill-days:30}") int sgeBackfillDays,
                          @Value("${rich.crawler.shfe-backfill-days:30}") int shfeBackfillDays,
                          @Value("${rich.crawler.spot.require-fresh:true}") boolean spotRequireFresh) {
        this.productMapper = productMapper;
        this.spotMapper = spotMapper;
        this.futuresMapper = futuresMapper;
        this.logMapper = logMapper;
        this.threadNum = threadNum;
        this.sgeBackfillDays = sgeBackfillDays;
        this.shfeBackfillDays = shfeBackfillDays;
        this.spotRequireFresh = spotRequireFresh;
    }

    // ------------------------------------------------------------------ 对外动作

    /**
     * 采集某个数据源的行情（当日 + 历史）。
     *
     * @param source 数据源，必填
     * @param date   目标日期；为 null 时取今天。SMM / CJ 只采该日的结算数据（详见
     *               {@link fan.rich.crawler.processor.SmmQuoteProcessor}），其余源以它为锚点回补历史
     * @return 执行结果
     */
    public CrawlResult crawlQuotes(CrawlSource source, LocalDate date) {
        if (source == null) {
            return CrawlResult.failure("-", TASK_QUOTE, 0, "未指定数据源");
        }
        String key = source.getCode() + ":" + TASK_QUOTE;
        return runGuarded(source, TASK_QUOTE, key, () -> switch (source) {
            case SHFE -> crawlShfe(date);
            case SGE -> crawlSge(date);
            case SMM, CJ -> crawlSpotDay(source, date == null ? LocalDate.now() : date, TASK_QUOTE);
        });
    }

    /**
     * 采集<b>当天最新价</b>（日间轮询口径，四个数据源统一）。
     *
     * <p>供定时任务每半小时调用一次：
     * <ul>
     *   <li>上期所 / 上金所 —— 延时行情快照，一个请求即可，成本低；</li>
     *   <li>上海有色 / 长江有色 —— 取当天那一行报价。站点只对"最新交易日"返回未经加密变更的
     *       真值，因此仍受新鲜度闸门保护：当天数据尚未发布时整批跳过（状态 -2），
     *       而不是往库里写脏数据。</li>
     * </ul>
     * 落库走覆盖写（UPSERT）：盘中价格本就会变，当天那一行需要不断刷新成最新值。
     *
     * @param source 任意数据源
     */
    public CrawlResult crawlIntraday(CrawlSource source) {
        if (source == null) {
            return CrawlResult.failure("-", TASK_INTRADAY, 0, "未指定数据源");
        }
        String key = source.getCode() + ":" + TASK_INTRADAY;
        return runGuarded(source, TASK_INTRADAY, key, () -> switch (source) {
            case SHFE -> crawlShfeDelay(TASK_INTRADAY);
            case SGE -> crawlSgeDelay(TASK_INTRADAY);
            case SMM, CJ -> crawlSpotDay(source, LocalDate.now(), TASK_INTRADAY);
        });
    }

    /**
     * 采集<b>上一工作日的终值</b>（夜间窗口口径，四个数据源统一）。
     *
     * <p>日终价在收盘后（约 18:00）才发布，次日开盘后站点就进入新一天的节奏，
     * 所以只在「上一工作日 18:00 ~ 当天 08:00」这个窗口内调用本方法，且只取目标那一天：
     * <ul>
     *   <li>上期所 / 上金所 —— 只下载目标日的日行情文件（{@code kx{yyyyMMdd}.dat} /
     *       当日 xlsx），拿的是正式收盘价与结算价，<b>不做 30 天历史回补</b>；</li>
     *   <li>上海有色 / 长江有色 —— 取目标日的结算数据，同受新鲜度闸门保护。</li>
     * </ul>
     * 落库同样是覆盖写：白天采到的是盘中值，这里要用终值把它刷成当天的最终价格。
     *
     * @param source 任意数据源
     * @param date   目标交易日（上一工作日）；为 null 时取昨天
     */
    public CrawlResult crawlNightSettle(CrawlSource source, LocalDate date) {
        if (source == null) {
            return CrawlResult.failure("-", TASK_SETTLE, 0, "未指定数据源");
        }
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        String key = source.getCode() + ":" + TASK_SETTLE;
        return runGuarded(source, TASK_SETTLE, key, () -> switch (source) {
            case SHFE, SGE -> crawlExchangeDay(source, day);
            case SMM, CJ -> crawlSpotDay(source, day, TASK_SETTLE);
        });
    }

    /** 发现某个数据源的品种清单（仅 SMM / CJ 支持）。 */
    public CrawlResult discover(CrawlSource source) {
        if (source == null) {
            return CrawlResult.failure("-", TASK_DISCOVER, 0, "未指定数据源");
        }
        if (source != CrawlSource.SMM && source != CrawlSource.CJ) {
            return CrawlResult.failure(source.getCode(), TASK_DISCOVER, 0,
                    "该数据源为交易所口径，无需发现品种");
        }
        String key = source.getCode() + ":" + TASK_DISCOVER;
        return runGuarded(source, TASK_DISCOVER, key, () -> doDiscover(source));
    }

    /** 依次采集全部数据源；某个源失败不影响后续。 */
    public List<CrawlResult> crawlAll(LocalDate date) {
        List<CrawlResult> results = new ArrayList<>();
        for (CrawlSource source : CrawlSource.values()) {
            try {
                results.add(crawlQuotes(source, date));
            } catch (Exception e) {
                log.error("[采集] {} 执行异常", source.getCode(), e);
                results.add(CrawlResult.failure(source.getCode(), TASK_QUOTE, 0, e.getMessage()));
            }
        }
        return results;
    }

    // ------------------------------------------------------------------ 各源实现

    /** SHFE 当日延时行情：单 URL，一次请求即拿到全部品种合约。 */
    private CrawlResult crawlShfeDelay(String taskType) {
        PricePipeline pipeline = newPipeline();
        long start = System.currentTimeMillis();
        runSpider(new ShfeProcessor(), List.of(new Request(ShfeProcessor.TARGET_URL)), pipeline, 1);
        return finish(CrawlSource.SHFE, taskType, pipeline, 1, start);
    }

    /**
     * 交易所「指定交易日」的正式日行情：只下目标那一天，五个请求内解决。
     *
     * <p>夜间补采专用。与 {@link #crawlShfe(LocalDate)} / {@link #crawlSge(LocalDate)} 的
     * 完整采集不同，这里刻意<b>不做历史回补</b>——历史序列由每天的
     * {@code QUOTE} 任务补齐，而夜间只需要把"上一工作日"那一天从盘中值刷成正式终值。
     *
     * <p>目标日没有行情文件（非交易日、或交易所尚未发布）时返回"跳过"而不是失败，
     * 让定时任务在窗口内再试几次。
     */
    private CrawlResult crawlExchangeDay(CrawlSource source, LocalDate day) {
        PricePipeline pipeline = newPipeline();
        long start = System.currentTimeMillis();
        CrawlResult result;
        ErrorCollector errors;
        if (source == CrawlSource.SHFE) {
            Request request = new Request(ShfeDailyProcessor.urlFor(day))
                    .putExtra(ShfeDailyProcessor.EXTRA_TRADE_DATE, day.toString());
            ShfeDailyProcessor processor = new ShfeDailyProcessor();
            runSpider(processor, List.of(request), pipeline, 1);
            result = finish(source, TASK_SETTLE, pipeline, 1, start);
            result = withErrors(result, processor.getErrors(), 1, "解析");
            errors = processor.getErrors();
        } else {
            Request request = new Request(SgeProcessor.urlFor(day)).setBinaryContent(true);
            SgeProcessor processor = new SgeProcessor(day);
            runSpider(processor, List.of(request), pipeline, 1);
            result = finish(source, TASK_SETTLE, pipeline, 1, start);
            result = withErrors(result, processor.getErrors(), 1, "解析");
            errors = processor.getErrors();
        }
        // 一条都没写进去、也没有解析错误 = 交易所没有这一天的行情文件。
        // 非交易日（周末 / 节假日）站点给的是 404 的 HTML 页，WebMagic 拿不到页面也就不会调用
        // Processor，所以这里不能只看"处理页数"，而是看"到底有没有落库"。
        // 归为"跳过"而不是失败：定时任务在窗口内会继续重试，日志也不会一片红。
        if (pipeline.getWritten() == 0 && !errors.hasError()) {
            return CrawlResult.skipped(source.getCode(), TASK_SETTLE, result.getCostMs(),
                    day + " 未取到日行情文件（非交易日、交易所尚未发布，或站点临时不可达）");
        }
        return result;
    }

    /**
     * SHFE 行情采集：当日 + 历史，分两步。
     *
     * <ol>
     *   <li><b>当日</b>——延时行情 {@code delaymarket_all.dat}，盘中可反复刷新，收盘价是最新价；</li>
     *   <li><b>当日 + 历史</b>——按天下载日行情文件 {@code kx{yyyyMMdd}.dat}
     *       （{@link ShfeDailyProcessor}），回溯最近 {@code shfeBackfillDays} 个自然日。
     *       收盘后交易所发布该文件，会用正式收盘价覆盖盘中延时值，历史序列也由此补齐。</li>
     * </ol>
     */
    private CrawlResult crawlShfe(LocalDate date) {
        PricePipeline pipeline = newPipeline();
        LocalDate anchor = date == null ? LocalDate.now() : date;
        long start = System.currentTimeMillis();

        // 1) 当日延时行情
        runSpider(new ShfeProcessor(), List.of(new Request(ShfeProcessor.TARGET_URL)), pipeline, 1);

        // 2) 当日 + 历史日行情：响应体里没有日期，用 extra 把交易日带给 Processor
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < shfeBackfillDays; i++) {
            LocalDate day = anchor.minusDays(i);
            requests.add(new Request(ShfeDailyProcessor.urlFor(day))
                    .putExtra(ShfeDailyProcessor.EXTRA_TRADE_DATE, day.toString()));
        }
        ShfeDailyProcessor daily = new ShfeDailyProcessor();
        // 交易所站点对并发比较敏感，历史回补刻意用 2 线程慢速下载
        runSpider(daily, requests, pipeline, Math.min(threadNum, 2));

        int requestCount = requests.size() + 1;
        CrawlResult result = finish(CrawlSource.SHFE, TASK_QUOTE, pipeline, requestCount, start);
        result = withErrors(result, daily.getErrors(), requests.size(), "解析");
        // 非交易日站点直接返回 404，WebMagic 拿不到页面也就不会调用 Processor。
        // 不解释一句的话，这十几个"未成功"的请求会被当成站点限流而误判成任务失败。
        int missingDays = Math.max(0, requests.size() - Math.max(0, pipeline.getPages() - 1));
        if (missingDays > 0) {
            result.setMessage(result.getMessage()
                    + "（其中 " + missingDays + " 个日期没有日行情文件，通常为周末 / 节假日）");
        }
        return result;
    }

    /** SGE 当日延时行情页（HTML 表格），盘中可反复刷新。 */
    private CrawlResult crawlSgeDelay(String taskType) {
        PricePipeline pipeline = newPipeline();
        long start = System.currentTimeMillis();
        SgeDelayProcessor delay = new SgeDelayProcessor();
        runSpider(delay, List.of(new Request(SgeDelayProcessor.TARGET_URL)), pipeline, 1);
        CrawlResult result = finish(CrawlSource.SGE, taskType, pipeline, 1, start);
        return withErrors(result, delay.getErrors(), 1, "解析");
    }

    /**
     * SGE 行情采集：当日 + 历史，分两步。
     *
     * <ol>
     *   <li><b>当日</b>——抓延时行情页 {@code /sjzx/yshqbg}（HTML 表格），盘中即可拿到
     *       最新价/最高/最低/今开盘，交易日内可反复执行覆盖更新；</li>
     *   <li><b>历史</b>——按天逐个下载 xlsx（{@code downloadExcelForQuoteDailyNew}），
     *       以天为单位请求最近 {@code sgeBackfillDays} 天补齐序列。
     *       这一步也包含当日，收盘后它会用正牌收盘价覆盖延时行情的最新价。</li>
     * </ol>
     */
    private CrawlResult crawlSge(LocalDate date) {
        PricePipeline pipeline = newPipeline();
        LocalDate anchor = date == null ? LocalDate.now() : date;
        long start = System.currentTimeMillis();

        // 1) 当日延时行情（HTML）
        SgeDelayProcessor delay = new SgeDelayProcessor();
        runSpider(delay, List.of(new Request(SgeDelayProcessor.TARGET_URL)), pipeline, 1);

        // 2) 历史日行情（xlsx，含当日收盘价）
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < sgeBackfillDays; i++) {
            LocalDate day = anchor.minusDays(i);
            requests.add(new Request(SgeProcessor.urlFor(day))
                    // 告诉 WebMagic 这是二进制响应，跳过字符串解码
                    .setBinaryContent(true));
        }
        // Processor 本身与日期无关（只解析拿到的二进制内容），一个实例即可服务全部请求
        SgeProcessor processor = new SgeProcessor(anchor);
        runSpider(processor, requests, pipeline);

        int requestCount = requests.size() + 1;
        CrawlResult result = finish(CrawlSource.SGE, TASK_QUOTE, pipeline, requestCount, start);
        result = withErrors(result, processor.getErrors(), requests.size(), "解析");
        return withErrors(result, delay.getErrors(), 1, "解析");
    }

    /**
     * SMM / CJ 现货行情：只采<b>目标日</b>那一天，先取出已发现的品种，再按品种逐个请求。
     *
     * <p>站点接口会忽略 URL 里的日期参数、固定回传最近 30 个交易日，
     * 由 {@link fan.rich.crawler.processor.SmmQuoteProcessor} 按目标日过滤，历史行直接丢弃。
     *
     * <p><b>目标日的两种用法</b>：日间轮询传今天（拿盘中最新报价），夜间补采传上一工作日
     * （拿当日终值）。两者都走覆盖写——当天那一行本就该随行情推进不断刷新，
     * 而历史的旧行由新鲜度闸门挡住，不会被碰到。
     *
     * <p><b>新鲜度闸门</b>：这两个站点只对"最新交易日"返回原始值；一旦该日让位给更新的交易日，
     * 就被施加了 {@code 真值 × n × 万分之1} 的随机偏移（n 每次请求重新抽签，不可还原）。
     * 目标日不等于站点最新交易日时整批跳过并记为"跳过"（不是失败）——日间轮询在当天数据
     * 尚未发布时必然遇到这种情况，属于正常现象。
     *
     * <p>品种字典为空时会先自动跑一次发现，保证"首次使用->点采集"一步到位。
     */
    private CrawlResult crawlSpotDay(CrawlSource source, LocalDate day, String taskType) {
        List<MdSourceProduct> products = productMapper.selectEnabled(source.getCode());
        if (products.isEmpty()) {
            CrawlResult discovered = doDiscover(source);
            if (discovered.getStatus() != CrawlResult.STATUS_SUCCESS) {
                return CrawlResult.failure(source.getCode(), taskType,
                        discovered.getCostMs(), "自动品种发现失败：" + discovered.getMessage());
            }
            products = productMapper.selectEnabled(source.getCode());
        }
        if (products.isEmpty()) {
            return CrawlResult.failure(source.getCode(), taskType, 0,
                    "品种字典为空且页面结构可能已变更，未提取到任何 productId");
        }

        // 覆盖写：当天那一行需要随行情推进不断刷新成最新值
        PricePipeline pipeline = newPipeline(PricePipeline.SpotMode.UPSERT);
        LocalDate target = day == null ? LocalDate.now() : day;
        SmmQuoteProcessor processor = new SmmQuoteProcessor(
                source.getCode(), source.getEndpoint(), source.getSiteUrl() + "/",
                target, true, spotRequireFresh);

        List<Request> requests = new ArrayList<>(products.size());
        for (MdSourceProduct product : products) {
            // 接口不返回品种名，借助 Request extras 把名称带给 Processor
            requests.add(new Request(processor.buildUrl(product.getProductId()))
                    .putExtra(SmmQuoteProcessor.EXTRA_PRODUCT_ID, product.getProductId())
                    .putExtra(SmmQuoteProcessor.EXTRA_PRODUCT_NAME, product.getProductName()));
        }
        long start = System.currentTimeMillis();
        // SMM/CJ 有 IP 级反爬：上百个品种并发猛打会直接触发 JS 挑战页，
        // 这里刻意用 2 线程慢速抓取，宁可慢也不要被封。
        runSpider(processor, requests, pipeline, Math.min(threadNum, 2));
        CrawlResult result = finish(source, taskType, pipeline, requests.size(), start);
        result = withErrors(result, processor.getErrors(), requests.size(), "请求");

        // 整批都被闸门拦下 = 目标日的行情还没发布（日间轮询早晨常见）。
        // 归为"跳过"而不是失败：既不该在日志里刷红，也不该让定时任务反复重试。
        if (processor.getGateBlocked() >= requests.size() && result.getStatus() != CrawlResult.STATUS_FAILED) {
            return CrawlResult.skipped(source.getCode(), taskType, result.getCostMs(),
                    "目标日 " + target + " 尚不是站点最新交易日（站点最新 "
                            + processor.getLatestSeen() + "），按新鲜度口径跳过");
        }
        result.setMessage(result.getMessage()
                + "（口径：" + target + " 当日数据，历史行不入库）");
        return result;
    }

    private CrawlResult doDiscover(CrawlSource source) {
        PricePipeline pipeline = newPipeline();
        SmmProductProcessor processor = new SmmProductProcessor(source);
        Map<String, String> urlGroups = processor.urlGroups();
        List<Request> requests = new ArrayList<>(urlGroups.size());
        for (Map.Entry<String, String> entry : urlGroups.entrySet()) {
            requests.add(new Request(entry.getKey())
                    .putExtra(SmmProductProcessor.EXTRA_GROUP, entry.getValue()));
        }
        long start = System.currentTimeMillis();
        runSpider(processor, requests, pipeline);
        return finish(source, TASK_DISCOVER, pipeline, requests.size(), start);
    }

    // ------------------------------------------------------------------ 骨架方法

    private PricePipeline newPipeline() {
        return newPipeline(PricePipeline.SpotMode.UPSERT);
    }

    private PricePipeline newPipeline(PricePipeline.SpotMode spotMode) {
        return new PricePipeline(productMapper, spotMapper, futuresMapper, spotMode);
    }

    private void runSpider(us.codecraft.webmagic.processor.PageProcessor processor,
                           List<Request> requests, PricePipeline pipeline) {
        runSpider(processor, requests, pipeline, threadNum);
    }

    private void runSpider(us.codecraft.webmagic.processor.PageProcessor processor,
                           List<Request> requests, PricePipeline pipeline, int threads) {
        Spider spider = Spider.create(processor)
                .addRequest(requests.toArray(new Request[0]))
                .addPipeline(pipeline)
                .thread(Math.max(1, Math.min(threads, requests.size())))
                // 队列取空后立即结束，否则 run() 会一直空转等待新 URL
                .setExitWhenComplete(true);
        try {
            spider.run();
        } finally {
            spider.close();
        }
    }

    private CrawlResult finish(CrawlSource source, String taskType, PricePipeline pipeline,
                               int requestCount, long start) {
        int costMs = (int) (System.currentTimeMillis() - start);
        int records = pipeline.getWritten();
        int pages = pipeline.getPages();
        String message = String.format("请求 %d 次，处理 %d 页，写入/更新 %d 条，耗时 %.1fs",
                requestCount, pages, records, costMs / 1000.0);
        if (records == 0) {
            message = "未写入任何数据：" + message + "，请检查目标站点是否改版或当日非交易日";
        }
        // 有请求没拿到页面＝超时/被限流/非交易日，不说出来就会被当成"站点没数据"
        if (pages < requestCount) {
            message += "（" + (requestCount - pages) + " 个请求未成功，多为超时或站点限流）";
        }
        CrawlResult result = CrawlResult.success(source.getCode(), taskType, records,
                requestCount, pages, costMs, message);
        // 一个页面都没拿到＝站点不可达或已被限流，这不是"没数据"而是"没采到"，必须判失败，
        // 否则盘中轮询会安安静静地空跑一整天，页面上却全是绿色的成功。
        if (pages == 0 && requestCount > 0) {
            result.setStatus(CrawlResult.STATUS_FAILED);
            result.setMessage(requestCount + " 个请求全部未拿到响应（站点不可达或已限流），本次采集作废");
        }
        log.info("[采集] {} {} -> {}", source.getCode(), taskType, message);
        return result;
    }

    /**
     * 把 Processor 收集到的错误回填到结果里。
     *
     * <p>WebMagic 会静默丢弃 process() 抛出的异常，若不回填，用户只会看到"0 条数据"而
     * 无从判断是站点没数据还是代码炸了。全部请求都失败时直接标记任务失败。
     */
    private CrawlResult withErrors(CrawlResult result, ErrorCollector errors,
                                   int requestCount, String verb) {
        if (!errors.hasError()) {
            return result;
        }
        String reason = verb + "失败 " + errors.getCount() + "/" + requestCount
                + " 个请求：" + errors.getFirst();
        log.error("[采集] {} {} {}", result.getSource(), result.getTaskType(), reason);
        if (result.getRecords() == 0) {
            return CrawlResult.failure(result.getSource(), result.getTaskType(),
                    result.getCostMs(), reason);
        }
        result.setMessage(result.getMessage() + "（另有 " + reason + "）");
        return result;
    }

    private CrawlResult runGuarded(CrawlSource source, String taskType, String key,
                                   Supplier<CrawlResult> task) {
        if (!running.add(key)) {
            return CrawlResult.rejected(source.getCode(), taskType,
                    "该数据源正在采集中，请稍候再试");
        }
        long start = System.currentTimeMillis();
        try {
            CrawlResult result = task.get();
            saveLog(source.getCode(), taskType, result);
            return result;
        } catch (Exception e) {
            log.error("[采集] {} {} 失败", source.getCode(), taskType, e);
            int costMs = (int) (System.currentTimeMillis() - start);
            CrawlResult result = CrawlResult.failure(source.getCode(), taskType, costMs, e.getMessage());
            saveLog(source.getCode(), taskType, result);
            return result;
        } finally {
            running.remove(key);
        }
    }

    private void saveLog(String source, String taskType, CrawlResult result) {
        try {
            MdCrawlLog entity = new MdCrawlLog();
            entity.setSource(source);
            entity.setTaskType(taskType);
            entity.setEndTime(LocalDateTime.now());
            entity.setStartTime(entity.getEndTime().minusNanos(result.getCostMs() * 1_000_000L));
            entity.setCostMs(Math.max(result.getCostMs(), 0));
            entity.setStatus(switch (result.getStatus()) {
                case CrawlResult.STATUS_SUCCESS -> MdCrawlLog.STATUS_SUCCESS;
                case CrawlResult.STATUS_SKIPPED -> MdCrawlLog.STATUS_SKIPPED;
                default -> MdCrawlLog.STATUS_FAILED;
            });
            entity.setRecordCount(result.getRecords());
            entity.setRequestCount(result.getRequests());
            entity.setMessage(cut(result.getMessage(), 500));
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[采集] 写入采集日志失败：{}", e.getMessage());
        }
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
