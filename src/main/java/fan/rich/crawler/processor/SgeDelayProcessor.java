package fan.rich.crawler.processor;

import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.ErrorCollector;
import fan.rich.crawler.support.NumUtil;
import fan.rich.entity.MdSpotPrice;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上海黄金交易所<b>当日延时行情</b>采集器（WebMagic PageProcessor）。
 *
 * <p>页面地址：{@code https://www.sge.com.cn/sjzx/yshqbg}
 *
 * <p>该页是服务端渲染的 HTML 表格（区别于历史数据的 xlsx 下载接口），只有 5 列：
 * <pre>
 * 合约 | 最新价 | 最高价 | 最低价 | 今开盘
 * </pre>
 * 成交量为 0 的合约（如 Au99.95、Au(T+N1)）四档价格全是 {@code 0.0}，需要整行丢弃，
 * 否则会把 0 写进库、把看板曲线拉到零。
 *
 * <p>交易日取自页面标题「上海黄金交易所2026年10月14日延时行情」——盘前访问时页面
 * 展示的是上一交易日行情，用标题日期比用 {@code LocalDate.now()} 更准确。
 *
 * <p>该页没有加权平均价与涨跌，均价用 (最高+最低)/2 近似，保证看板曲线不断点。
 */
public class SgeDelayProcessor implements PageProcessor {

    /** 延时行情页面地址。 */
    public static final String TARGET_URL = "https://www.sge.com.cn/sjzx/yshqbg";

    /** SGE 贵金属报价统一单位。 */
    private static final String UNIT = "元/克";

    /** 标题里的日期：「上海黄金交易所2026年10月14日延时行情」。 */
    private static final Pattern TITLE_DATE =
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");

    private static final int TIME_OUT = 30_000;
    private static final int RETRY_TIMES = 3;
    private static final int SLEEP_TIME = 1_000;

    private final Site site;

    /** 采集错误（WebMagic 会静默吞掉 process() 的异常，这里暂存以便上报）。 */
    private final ErrorCollector errors = new ErrorCollector();

    public SgeDelayProcessor() {
        this.site = Site.me()
                .setTimeOut(TIME_OUT)
                .setRetryTimes(RETRY_TIMES)
                .setSleepTime(SLEEP_TIME)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .addHeader("Referer", "https://www.sge.com.cn/sjzx/quotation_daily_new")
                .setUseGzip(true);
    }

    @Override
    public void process(Page page) {
        Document doc = page.getHtml().getDocument();
        if (doc == null) {
            page.setSkip(true);
            return;
        }
        LocalDate tradeDate = resolveTradeDate(doc);
        List<MdSpotPrice> quotes = new ArrayList<>();
        try {
            for (Element row : doc.select("table tr")) {
                Elements cells = row.select("td");
                // 表头行是 th，取不到 5 个 td 的自然跳过
                if (cells.size() < 5) {
                    continue;
                }
                MdSpotPrice record = toRecord(cells, tradeDate);
                if (record != null) {
                    quotes.add(record);
                }
            }
        } catch (Exception e) {
            errors.record(e);
            page.setSkip(true);
            return;
        }
        if (quotes.isEmpty()) {
            page.setSkip(true);
            return;
        }
        page.putField(CrawlKeys.SPOT_QUOTES, quotes);
    }

    /** 单行 → 行情记录；无成交（四档价格全为 0）的行返回 null。 */
    private MdSpotPrice toRecord(Elements cells, LocalDate tradeDate) {
        // 最新价单元格里包了 <span class="colorGreen">897.6</span>，
        // 必须用 Element.text()（含子孙文本）而不是 Selectable 的 text 属性
        String contract = cells.get(0).text().trim();
        if (contract.isEmpty()) {
            return null;
        }
        BigDecimal latest = NumUtil.decimal(cells.get(1).text());
        BigDecimal high = NumUtil.decimal(cells.get(2).text());
        BigDecimal low = NumUtil.decimal(cells.get(3).text());
        BigDecimal open = NumUtil.decimal(cells.get(4).text());
        if (!hasPrice(latest, high, low, open)) {
            return null;
        }

        MdSpotPrice record = new MdSpotPrice();
        record.setSource(CrawlSource.SGE.getCode());
        record.setProductId(contract);
        record.setProductName(contract);
        record.setTradeDate(tradeDate);
        record.setUnit(UNIT);
        record.setPriceOpen(open);
        record.setPriceHigh(high);
        record.setPriceLow(low);
        // 延时行情没有收盘价概念，"最新价"即当前价，收盘后再采的 xlsx 会覆盖为正牌收盘价
        record.setPriceClose(latest);
        record.setPriceAvg(mid(high, low));
        return record;
    }

    /** 页面标题 → 交易日；解析不到时退回今天。 */
    private LocalDate resolveTradeDate(Document doc) {
        Element h1 = doc.selectFirst("h1");
        Matcher m = TITLE_DATE.matcher(h1 == null ? "" : h1.text());
        if (m.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (Exception ignored) {
                // 落到下面用今天
            }
        }
        return LocalDate.now();
    }

    /** (最高+最低)/2；任一为空或两者都是 0 时返回 null。 */
    private static BigDecimal mid(BigDecimal high, BigDecimal low) {
        if (high == null || low == null) {
            return null;
        }
        if (high.compareTo(BigDecimal.ZERO) == 0 && low.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return high.add(low).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /**
     * 是否至少有一个非零价格。
     *
     * <p>无成交的合约四档价格全为 {@code 0.0}（如 Au99.95、Au(T+N1)），解析失败的行则全为
     * null——这两种都要整行丢弃，否则会写入 0 价或空记录把看板曲线拉坏。
     */
    private static boolean hasPrice(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                return true;
            }
        }
        return false;
    }

    public ErrorCollector getErrors() {
        return errors;
    }

    @Override
    public Site getSite() {
        return site;
    }
}
