package fan.rich.crawler.processor;

import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.ErrorCollector;
import fan.rich.crawler.support.NumUtil;
import fan.rich.entity.MdFuturesPrice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上期所「日行情」采集器（WebMagic PageProcessor）——用于<b>当日收盘后 + 历史</b>回补。
 *
 * <p>接口地址：{@code https://www.shfe.com.cn/data/tradedata/future/dailydata/kx{yyyyMMdd}.dat}
 *
 * <p>与 {@link ShfeProcessor}（延时行情）的区别：
 * <ul>
 *   <li>延时行情是<b>盘中快照</b>，一个 URL 拿到全部品种，收盘价字段是 lastprice；</li>
 *   <li>日行情是交易所<b>收盘后发布的正式行情</b>，按天一个文件，带结算价，
 *       且历史日期也能取到，是回补历史序列的唯一官方途径。</li>
 * </ul>
 *
 * <p>报文结构：
 * <pre>{@code
 * {"o_curinstrument":[{"PRODUCTGROUPID":"cu","PRODUCTID":"cu_f","PRODUCTNAME":"铜",
 *   "DELIVERYMONTH":"2610","PRESETTLEMENTPRICE":111670,"OPENPRICE":111960,
 *   "HIGHESTPRICE":112100,"LOWESTPRICE":111260,"CLOSEPRICE":111710,
 *   "SETTLEMENTPRICE":111560,"ZD1_CHG":40,"VOLUME":21665,
 *   "TURNOVER":1208501.2,"OPENINTEREST":16770}, ... ,{...,"DELIVERYMONTH":"小计"}]}
 * }</pre>
 *
 * <p><b>两个坑</b>：
 * <ol>
 *   <li>数组末尾混着每个品种的「小计」行（{@code DELIVERYMONTH="小计"}）和一个「总计」行，
 *       必须过滤，否则会往库里塞进脏合约号；</li>
 *   <li>非交易日（周末 / 节假日）该 URL 返回 404 的 HTML 页而不是 JSON，
 *       直接当解析失败会把整次任务判成失败，这里识别出来静默跳过。</li>
 * </ol>
 *
 * <p>落库主键与延时行情一致：{@code (instrument_id, contract_code, trade_date)}，
 * 因此收盘后会用正式行情覆盖盘中延时值。
 */
public class ShfeDailyProcessor implements PageProcessor {

    /** 日行情文件前缀，后接 yyyyMMdd。 */
    private static final String URL_PREFIX = "https://www.shfe.com.cn/data/tradedata/future/dailydata/kx";

    /** 日行情文件后缀。 */
    private static final String URL_SUFFIX = ".dat";

    /** Request extra：该请求对应的交易日（响应体里没有日期字段，只能由外部带进来）。 */
    public static final String EXTRA_TRADE_DATE = "tradeDate";

    /** 响应体的顶层数组字段名。 */
    private static final String ROOT_FIELD = "o_curinstrument";

    /** 交割月份必须是 4 位年月；「小计」等汇总行的该字段为空或非数字。 */
    private static final String MONTH_PATTERN = "\\d{4}";

    private static final int TIME_OUT = 30_000;
    private static final int RETRY_TIMES = 2;
    private static final int SLEEP_TIME = 500;

    /** 共享反序列化器，见 ShfeProcessor 同名字段的说明。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Site site;

    /** 解析错误（WebMagic 会静默吞掉 process() 的异常，这里暂存以便上报）。 */
    private final ErrorCollector errors = new ErrorCollector();

    /** 无日行情文件的日期数（非交易日），用于给用户一个明确解释。 */
    private final AtomicInteger skippedDays = new AtomicInteger();

    public ShfeDailyProcessor() {
        this.site = Site.me()
                .setCharset("UTF-8")
                .setTimeOut(TIME_OUT)
                .setRetryTimes(RETRY_TIMES)
                .setSleepTime(SLEEP_TIME)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Referer", "https://www.shfe.com.cn/reports/tradedata/dailyandweeklydata/")
                .setUseGzip(true);
    }

    /** 拼出某个交易日的日行情文件地址。 */
    public static String urlFor(LocalDate date) {
        return URL_PREFIX + DateTimeFormatter.BASIC_ISO_DATE.format(date) + URL_SUFFIX;
    }

    @Override
    public void process(Page page) {
        Request request = page.getRequest();
        String raw = page.getRawText();
        if (raw == null || raw.isBlank()) {
            skippedDays.incrementAndGet();
            page.setSkip(true);
            return;
        }
        // 非交易日：站点返回 404 的 HTML 页面，而不是 JSON
        String head = raw.length() > 64 ? raw.substring(0, 64) : raw;
        if (head.contains("<!DOCTYPE html") || head.contains("<html")) {
            skippedDays.incrementAndGet();
            page.setSkip(true);
            return;
        }

        Object extraDate = request.getExtra(EXTRA_TRADE_DATE);
        LocalDate tradeDate = extraDate == null ? null : NumUtil.date(String.valueOf(extraDate));
        if (tradeDate == null) {
            tradeDate = parseDateFromUrl(request.getUrl());
        }
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }

        List<MdFuturesPrice> quotes = new ArrayList<>();
        try {
            JsonNode rows = MAPPER.readTree(raw).path(ROOT_FIELD);
            if (!rows.isArray()) {
                page.setSkip(true);
                return;
            }
            for (JsonNode node : rows) {
                MdFuturesPrice quote = convert(node, tradeDate);
                if (quote != null) {
                    quotes.add(quote);
                }
            }
        } catch (Exception e) {
            errors.record(e);
            page.setSkip(true);
            return;
        }
        page.putField(CrawlKeys.FUTURES_QUOTES, quotes);
    }

    /** 单个合约报文 → 实体；汇总行与非期货合约直接丢弃。 */
    private MdFuturesPrice convert(JsonNode node, LocalDate tradeDate) {
        String month = text(node, "DELIVERYMONTH");
        if (!month.matches(MONTH_PATTERN)) {
            return null;
        }
        String groupId = text(node, "PRODUCTGROUPID");
        String productId = text(node, "PRODUCTID");
        String productName = text(node, "PRODUCTNAME");
        if (NumUtil.blank(groupId) || productName.contains("总计")) {
            return null;
        }
        // 只保留期货合约（cu_f）；原油 TAS（sc_tas）、期权（*_o）口径不同，不入库
        if (!productId.isBlank() && !productId.endsWith("_f")) {
            return null;
        }

        MdFuturesPrice quote = new MdFuturesPrice();
        quote.setExchange(CrawlSource.SHFE.getCode());
        quote.setInstrumentId(groupId.trim().toLowerCase());
        quote.setInstrumentName(productName.trim());
        quote.setContractCode(groupId.trim().toLowerCase() + month);
        quote.setTradeDate(tradeDate);
        quote.setPresettlementPrice(NumUtil.decimal(text(node, "PRESETTLEMENTPRICE")));
        quote.setOpenPrice(NumUtil.decimal(text(node, "OPENPRICE")));
        quote.setHighPrice(NumUtil.decimal(text(node, "HIGHESTPRICE")));
        quote.setLowPrice(NumUtil.decimal(text(node, "LOWESTPRICE")));
        quote.setClosePrice(NumUtil.decimal(text(node, "CLOSEPRICE")));
        // 结算价：按成交量加权的当日均价，是交易所计算保证金与盈亏的口径，
        // 与收盘价是两回事。延时行情接口不下发该字段，所以只有日行情才能补上。
        quote.setSettlementPrice(NumUtil.decimal(text(node, "SETTLEMENTPRICE")));
        quote.setChangeValue(NumUtil.decimal(text(node, "ZD1_CHG")));
        quote.setVolume(NumUtil.decimal(text(node, "VOLUME")));
        quote.setTurnover(NumUtil.decimal(text(node, "TURNOVER")));
        quote.setOpenInterest(NumUtil.decimal(text(node, "OPENINTEREST")));
        return quote;
    }

    /** 从 URL 末尾的 kx20261013.dat 里反解交易日，作为 extra 缺失时的兜底。 */
    private static LocalDate parseDateFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int start = url.lastIndexOf("/kx");
        if (start < 0) {
            return null;
        }
        int end = url.indexOf('.', start);
        if (end <= start) {
            return null;
        }
        String ymd = url.substring(start + 3, end);
        if (ymd.length() != 8) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(ymd.substring(0, 4)),
                    Integer.parseInt(ymd.substring(4, 6)),
                    Integer.parseInt(ymd.substring(6, 8)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    public ErrorCollector getErrors() {
        return errors;
    }

    /** 返回本次任务中「没有日行情文件」的日期数（多为非交易日）。 */
    public int getSkippedDays() {
        return skippedDays.get();
    }

    @Override
    public Site getSite() {
        return site;
    }
}
