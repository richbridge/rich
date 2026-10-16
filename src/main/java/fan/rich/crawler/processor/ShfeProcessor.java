package fan.rich.crawler.processor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.NumUtil;
import fan.rich.entity.MdFuturesPrice;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上海期货交易所延时行情采集器（WebMagic PageProcessor）。
 *
 * <p>接口地址：{@code https://www.shfe.com.cn/data/tradedata/future/delaymarket/delaymarket_all.dat}
 *
 * <p>返回结构较为特殊：顶层对象只有一个 {@code delaymarket} 字段，其值是一个"<b>数组的数组</b>"——
 * 外层每个元素代表一个品种，内层每个元素是该品种的一个交割合约，因此必须两层遍历后拍平。
 * 字段全部为小写字符串，且未取值时下发空串而非 null，统一经 {@link NumUtil} 收敛。
 *
 * <p>示例（截断）：
 * <pre>{@code
 * {"delaymarket":[[{"turnover":"11849550","volume":"51","presettlementprice":"23400",
 *   "contractname":"ad2610","instrumentid":"ad","lastprice":"23200",
 *   "updatetime":"2026-10-13 15:00:00", ...}], [{...cu...}]]}
 * }</pre>
 */
public class ShfeProcessor implements PageProcessor {

    /** 延时行情接口地址。 */
    public static final String TARGET_URL =
            "https://www.shfe.com.cn/data/tradedata/future/delaymarket/delaymarket_all.dat";

    private static final int TIME_OUT = 30_000;
    private static final int RETRY_TIMES = 3;
    private static final int SLEEP_TIME = 500;

    /** 上期所品种代号 → 中文名；未列出的原样展示代号。 */
    private static final Map<String, String> INSTRUMENT_NAMES = Map.ofEntries(
            Map.entry("au", "黄金"),
            Map.entry("ag", "白银"),
            Map.entry("cu", "铜"),
            Map.entry("al", "铝"),
            Map.entry("pb", "铅"),
            Map.entry("zn", "锌"),
            Map.entry("ni", "镍"),
            Map.entry("sn", "锡"),
            Map.entry("ad", "氧化铝"),
            Map.entry("ao", "氧化铝"),
            Map.entry("bc", "国际铜"),
            Map.entry("rb", "螺纹钢"),
            Map.entry("hc", "热轧卷板"),
            Map.entry("wr", "线材"),
            Map.entry("ss", "不锈钢"),
            Map.entry("sc", "原油"),
            Map.entry("fu", "燃料油"),
            Map.entry("lu", "低硫燃料油"),
            Map.entry("bu", "石油沥青"),
            Map.entry("br", "合成橡胶"),
            Map.entry("ru", "天然橡胶"),
            Map.entry("nr", "20号胶"),
            Map.entry("sp", "漂白硫酸盐针叶木浆"),
            Map.entry("ec", "集运指数（欧线）"),
            Map.entry("imci", "上期有色金属指数")
    );

    /**
     * 共享的反序列化器。
     *
     * <p>这里只做读取，不依赖任何 Spring 定制配置，因此不注入容器中的 ObjectMapper
     * （避免与其它模块的序列化偏好互相干扰）。ObjectMapper 的读操作线程安全，
     * WebMagic 多线程抓取时可放心共用。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Site site;

    public ShfeProcessor() {
        this.site = Site.me()
                .setCharset("UTF-8")
                .setTimeOut(TIME_OUT)
                .setRetryTimes(RETRY_TIMES)
                .setSleepTime(SLEEP_TIME)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Referer", "https://www.shfe.com.cn/marketdata/delaymarket")
                .setUseGzip(true);
    }

    @Override
    public void process(Page page) {
        String raw = page.getRawText();
        if (raw == null || raw.isBlank()) {
            page.setSkip(true);
            return;
        }
        List<MdFuturesPrice> quotes = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode groups = root.path("delaymarket");
            if (!groups.isArray()) {
                page.setSkip(true);
                return;
            }
            // 外层：品种分组；内层：该品种下的各交割合约
            for (JsonNode group : groups) {
                if (!group.isArray()) {
                    continue;
                }
                for (JsonNode node : group) {
                    MdFuturesPrice quote = convert(node);
                    if (quote != null) {
                        quotes.add(quote);
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败时把异常塞进 ResultItems，由 CrawlerService 记录到采集日志
            page.putField(CrawlKeys.PRODUCT_ID, "SHFE");
            page.getResultItems().setSkip(true);
            throw new IllegalStateException("解析 SHFE 延时行情失败：" + e.getMessage(), e);
        }
        page.putField(CrawlKeys.FUTURES_QUOTES, quotes);
    }

    /** 单个合约报文 → 实体；合约代码为空则丢弃。 */
    private MdFuturesPrice convert(JsonNode node) {
        String contract = text(node, "contractname");
        if (NumUtil.blank(contract)) {
            return null;
        }
        String instrumentId = text(node, "instrumentid");
        LocalDate tradeDate = NumUtil.date(text(node, "updatetime"));
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }

        MdFuturesPrice quote = new MdFuturesPrice();
        quote.setExchange(CrawlSource.SHFE.getCode());
        quote.setInstrumentId(instrumentId);
        quote.setInstrumentName(resolveName(instrumentId));
        quote.setContractCode(contract);
        quote.setTradeDate(tradeDate);
        quote.setPresettlementPrice(NumUtil.decimal(text(node, "presettlementprice")));
        quote.setOpenPrice(NumUtil.decimal(text(node, "openprice")));
        quote.setHighPrice(NumUtil.decimal(text(node, "highprice")));
        quote.setLowPrice(NumUtil.decimal(text(node, "lowerprice")));
        quote.setClosePrice(NumUtil.decimal(text(node, "lastprice")));
        quote.setBidPrice(NumUtil.decimal(text(node, "bidprice")));
        quote.setAskPrice(NumUtil.decimal(text(node, "askprice")));
        quote.setChangeValue(NumUtil.decimal(text(node, "upperdown")));
        quote.setVolume(NumUtil.decimal(text(node, "volume")));
        quote.setTurnover(NumUtil.decimal(text(node, "turnover")));
        quote.setOpenInterest(NumUtil.decimal(text(node, "openinterest")));
        quote.setQuoteTime(NumUtil.dateTime(text(node, "updatetime")));
        return quote;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String resolveName(String instrumentId) {
        if (NumUtil.blank(instrumentId)) {
            return "";
        }
        return INSTRUMENT_NAMES.getOrDefault(instrumentId.trim().toLowerCase(), instrumentId);
    }

    @Override
    public Site getSite() {
        return site;
    }
}
