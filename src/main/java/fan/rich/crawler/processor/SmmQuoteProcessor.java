package fan.rich.crawler.processor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.ErrorCollector;
import fan.rich.crawler.support.NumUtil;
import fan.rich.entity.MdSpotPrice;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上海有色网(SMM) / 长江有色网(CJ) 现货历史行情采集器（WebMagic PageProcessor）。
 *
 * <p>两个站点共用同一套接口结构与返回格式，仅域名不同：
 * <pre>{@code
 * https://hq.smm.cn/ajax/spot/history/{productId}/{date}/{date}
 * https://cj.smm.cn/ajax/spot/history/{productId}/{date}/{date}
 * }</pre>
 *
 * <p>返回：
 * <pre>{@code
 * {"code":0,"data":[{"product_id":"201102250376","highs":108639.135,"low":108219.177,
 *   "average":108429.156,"vchange":474.95,"vchange_rate":0.0044,"renew_date":"2026-08-25",
 *   "high_show":"108639.14","low_show":"108219.18","average_show":"108429.16",
 *   "change_value_show":"474.95","change_rate_show":"0.44%","point_precision":2}]}
 * }</pre>
 *
 * <p><b>实测坑位</b>：URL 中的日期区间参数会被服务端忽略，无论传 2025-11-19 还是 2026-09-01，
 * 返回的都是该品种<b>最近 30 个交易日</b>（实测固定 {@code count=30}）的数据。
 *
 * <p><b>业务口径</b>：上海有色 / 长江有色只取<b>目标日结算数据</b>（结算价在 18:00 之后由站点发布），
 * 历史数据不采集。因此本类默认开启 {@code strictDate}：接口虽然回传 30 天，只有交易日
 * 等于目标日的那一行会落库，其余直接丢弃——既符合口径，也避免上百个品种的历史数据反复
 * 写库触发站点限流。
 *
 * <h3>关于"加密变更"——实测结论</h3>
 *
 * <p>对 SMM 1#电解铜连续采样 8 轮（每轮间隔 20 秒），取同一历史交易日 2026-08-26 对比：
 * <pre>
 *   最新交易日 2026-10-14 ：111710.000 —— 8 轮恒定，极差 0
 *   历史行     2026-08-26 ：109929.006 / 109950.994 / 109961.988 —— 随机跳变，极差 32.98
 * </pre>
 * 三个取值相对最小值的偏移分别是 0、2、3 倍于 {@code 真值 × 万分之1}（≈10.99）。
 * 由此得出平台的加密变更模型：
 * <pre>
 *   观测值 = 真值 × (1 + n × 0.0001)，n 为每次请求随机取的整数（量级约 ±1 ~ ±10）
 * </pre>
 * 即：<b>只有"最新交易日"那一行是真值（表现为整数且恒定不变），其余历史行都是
 * 被施加了随机偏移的脏数据</b>，偏移量在一个请求内对所有行一致，但请求之间会重新抽签。
 *
 * <p><b>这对采集策略的含义</b>：
 * <ol>
 *   <li>历史数据<b>无法还原</b>——偏移可达真值的万分之十（11 万的铜价就是 ±110 元），
 *       想靠四舍五入之类的运算把它修回去是不可能的；
 *   <li>因此<b>唯一可靠的做法就是只采最新交易日</b>，并且采到之后不再改写；
 *   <li>于是有了 {@link #requireFresh} 开关：只有当目标日恰好等于本次响应中的
 *       最新交易日时才落库，否则整批跳过——这比"按日期过滤"更本质，
 *       因为判断依据直接来自站点当次返回，不依赖本地时钟与节假日配置。</li>
 * </ol>
 */
public class SmmQuoteProcessor implements PageProcessor {

    /** Request extra：站点侧品种 ID。 */
    public static final String EXTRA_PRODUCT_ID = "productId";

    /** Request extra：品种名称（接口不返回名称，需外部带过来）。 */
    public static final String EXTRA_PRODUCT_NAME = "productName";

    private static final int TIME_OUT = 20_000;
    private static final int RETRY_TIMES = 2;
    private static final int SLEEP_TIME = 300;

    /** 共享反序列化器，见 ShfeProcessor 同名字段的说明。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Site site;
    private final String sourceCode;
    private final String endpoint;
    private final LocalDate date;

    /** 是否只保留目标日的行情（true=不采历史，只留目标日结算数据）。 */
    private final boolean strictDate;

    /**
     * 是否要求"目标日必须是本次响应中的最新交易日"。
     *
     * <p>开启后，只有未被平台加密变更的数据才会落库，详见类注释。
     */
    private final boolean requireFresh;

    /** 采集错误（WebMagic 会静默吞掉 process() 的异常，这里暂存以便上报）。 */
    private final ErrorCollector errors = new ErrorCollector();

    /**
     * 被新鲜度闸门拦下的请求数。
     *
     * <p>它<b>不算错误</b>——"今天的数据还没发布"在日间轮询里每天都必然发生几次，
     * 记成错误会让日志一片红、也会让定时任务误判成需要重试的失败。
     * 调用方据 {@code gateBlocked == 请求数} 判定"整批按口径跳过"。
     */
    private final AtomicInteger gateBlocked = new AtomicInteger();

    /** 本次响应中见到的最新交易日（取最大值），用于给用户一个明确的说明。 */
    private volatile LocalDate latestSeen;

    /**
     * @param sourceCode 数据源编码，SMM 或 CJ
     * @param endpoint   接口前缀，如 {@code https://hq.smm.cn/ajax/spot/history}
     * @param referer    站点首页，用于绕过简单的防盗链校验
     * @param date       目标交易日；服务端会忽略它，但本地按它过滤（见类注释）
     */
    public SmmQuoteProcessor(String sourceCode, String endpoint, String referer, LocalDate date) {
        this(sourceCode, endpoint, referer, date, true);
    }

    /**
     * @param strictDate true 时只保留 {@code date} 当天的行情，false 时保留接口回传的全部 30 天
     */
    public SmmQuoteProcessor(String sourceCode, String endpoint, String referer, LocalDate date,
                             boolean strictDate) {
        this(sourceCode, endpoint, referer, date, strictDate, true);
    }

    /**
     * @param requireFresh true 时只接受"最新交易日"的数据，被加密变更过的历史行整批跳过
     */
    public SmmQuoteProcessor(String sourceCode, String endpoint, String referer, LocalDate date,
                             boolean strictDate, boolean requireFresh) {
        this.sourceCode = sourceCode;
        this.endpoint = endpoint;
        this.date = date == null ? LocalDate.now() : date;
        this.strictDate = strictDate;
        this.requireFresh = requireFresh;
        this.site = Site.me()
                .setCharset("UTF-8")
                .setTimeOut(TIME_OUT)
                .setRetryTimes(RETRY_TIMES)
                .setSleepTime(SLEEP_TIME)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Referer", referer)
                .setUseGzip(true);
    }

    /** 拼出某个品种的历史行情地址。 */
    public String buildUrl(String productId) {
        String day = date.toString();
        return endpoint + "/" + productId + "/" + day + "/" + day;
    }

    @Override
    public void process(Page page) {
        Request request = page.getRequest();
        // 注意：WebMagic 的 getExtra 是泛型方法 {@code <T> T getExtra(String)}，
        // 直接写 String.valueOf(getExtra(k)) 会被推断成 char[] 重载并插入 checkcast 到 [C，
        // 运行期抛 ClassCastException。必须先落到 Object 再转换。
        Object extraId = request.getExtra(EXTRA_PRODUCT_ID);
        Object extraName = request.getExtra(EXTRA_PRODUCT_NAME);
        String productId = extraId == null ? "" : String.valueOf(extraId);
        String productName = extraName == null ? "" : String.valueOf(extraName);
        page.putField(CrawlKeys.PRODUCT_ID, productId);

        String raw = page.getRawText();
        if (raw == null || raw.isBlank()) {
            page.setSkip(true);
            return;
        }
        // 站点在持续请求后会返回一段带 rsab.js 的 JS 挑战页（IP 级反爬），
        // 此时响应是 HTML 而非 JSON。不识别的话，表现就是"请求了 N 次、0 条数据"，
        // 很容易被误判成"今日无行情"。
        if (looksLikeChallenge(raw)) {
            errors.record(new IllegalStateException("返回反爬挑战页（疑似 IP 被限流）"));
            page.setSkip(true);
            return;
        }
        List<MdSpotPrice> quotes = new ArrayList<>();
        try {
            JsonNode data = MAPPER.readTree(raw).path("data");
            if (!data.isArray() || data.isEmpty()) {
                page.setSkip(true);
                return;
            }
            // 新鲜度闸门：站点只对"最新交易日"返回未经加密变更的真值。
            // 这里直接从本次响应里取出最大交易日作为判据，比本地时钟可靠——
            // 即使窗口算错、或手动指定了一个历史日期，也不会把脏数据写进库。
            LocalDate latest = latestTradeDate(data);
            if (latest != null) {
                LocalDate seen = latestSeen;
                if (seen == null || latest.isAfter(seen)) {
                    latestSeen = latest;
                }
            }
            if (requireFresh && latest != null && !latest.equals(date)) {
                // 计数而不是记错误：日间轮询在"当日尚未发布"时每次都会走到这里
                gateBlocked.incrementAndGet();
                page.setSkip(true);
                return;
            }
            for (JsonNode node : data) {
                MdSpotPrice quote = convert(node, sourceCode, productId, productName);
                if (quote != null) {
                    quotes.add(quote);
                }
            }
        } catch (Exception e) {
            errors.record(e);
            page.setSkip(true);
            return;
        }
        page.putField(CrawlKeys.SPOT_QUOTES, quotes);
    }

    /** 取本次响应中最大的交易日，即站点口径下的"最新交易日"。 */
    private static LocalDate latestTradeDate(JsonNode data) {
        LocalDate latest = null;
        for (JsonNode node : data) {
            LocalDate day = NumUtil.date(node.path("renew_date").asText(""));
            if (day != null && (latest == null || day.isAfter(latest))) {
                latest = day;
            }
        }
        return latest;
    }

    public ErrorCollector getErrors() {
        return errors;
    }

    /** 被新鲜度闸门拦下的请求数（目标日不是站点最新交易日）。 */
    public int getGateBlocked() {
        return gateBlocked.get();
    }

    /** 本次采集在站点侧见到的最新交易日；一个请求都没成功时为 null。 */
    public LocalDate getLatestSeen() {
        return latestSeen;
    }

    /** 接口正常时返回 JSON；被拦截时返回一段 HTML 挑战页。 */
    private static boolean looksLikeChallenge(String raw) {
        String head = raw.length() > 64 ? raw.substring(0, 64) : raw;
        return head.contains("<!DOCTYPE html") || head.contains("<html")
                || raw.contains("rsab.js");
    }

    /**
     * 单条行情 → 实体。
     *
     * <p>SMM 不提供开盘价；收盘价字段用当日"SMM 均价"(average) 填充，这也是其对外报价的口径。
     * 涨跌优先取 {@code *_show} 字符串版本——它已是站点算好的展示值，能避开浮点比例与
     * 百分数两种量纲混用的问题。
     */
    private MdSpotPrice convert(JsonNode node, String source, String fallbackId, String name) {
        LocalDate tradeDate = NumUtil.date(node.path("renew_date").asText(""));
        if (tradeDate == null) {
            return null;
        }
        // 只采目标日结算数据：接口回传的其余历史行直接丢弃
        if (strictDate && !tradeDate.equals(date)) {
            return null;
        }
        String productId = node.path("product_id").asText(fallbackId);
        BigDecimalHolder holder = new BigDecimalHolder(node);

        MdSpotPrice quote = new MdSpotPrice();
        quote.setSource(source);
        quote.setProductId(productId);
        quote.setProductName(NumUtil.blank(name) ? productId : name);
        quote.setTradeDate(tradeDate);

        // 原样入库：最新交易日本来就是整数真值，不需要也不应该做任何加工。
        // 试图用取整等运算"还原"被加密变更的历史行是行不通的——偏移可达真值的万分之十。
        quote.setPriceHigh(holder.decimal("highs", "high_show"));
        quote.setPriceLow(holder.decimal("low", "low_show"));
        quote.setPriceAvg(holder.decimal("average", "average_show"));
        // 无收盘价口径，用均价作为当日代表价
        quote.setPriceClose(quote.getPriceAvg());
        quote.setChangeValue(holder.decimal("vchange", "change_value_show"));
        quote.setChangeRate(holder.percent("change_rate_show", "vchange_rate"));
        return quote;
    }

    /** 优先取展示字符串、其次取原始小数的取值辅助。 */
    private static final class BigDecimalHolder {
        private final JsonNode node;

        private BigDecimalHolder(JsonNode node) {
            this.node = node;
        }

        private BigDecimal decimal(String shown, String raw) {
            BigDecimal value = NumUtil.decimal(node.path(shown).asText(""));
            if (value == null) {
                value = NumUtil.decimal(node.path(raw).asText(""));
            }
            return value;
        }

        private BigDecimal percent(String shown, String raw) {
            BigDecimal value = NumUtil.percent(node.path(shown).asText(""));
            if (value == null) {
                value = NumUtil.percent(node.path(raw).asText(""));
            }
            return value;
        }
    }

    @Override
    public Site getSite() {
        return site;
    }
}
