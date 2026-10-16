package fan.rich.crawler.processor;

import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.crawler.support.ErrorCollector;
import fan.rich.crawler.support.SgeQuoteParser;
import fan.rich.entity.MdSpotPrice;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.time.LocalDate;
import java.util.List;

/**
 * 上海黄金交易所日行情采集器（WebMagic PageProcessor）。
 *
 * <p>接口地址：
 * <pre>{@code
 * https://www.sge.com.cn/portal/marketAutomation/downloadExcelForQuoteDailyNew
 *        ?start_date=2025-11-10&end_date=2025-11-10
 * }</pre>
 *
 * <p>与其他三个源不同，该接口返回的是 <b>xlsx 二进制流</b>（Content-Type 为 application/octet-stream），
 * 因此构造 Request 时调用 {@code setBinaryContent(true)}，WebMagic 会跳过字符串解码，
 * 原始字节保留在 {@link Page#getBytes()} 中，再由 POI 解析。
 */
public class SgeProcessor implements PageProcessor {

    /** 接口基础地址。 */
    public static final String BASE_URL =
            "https://www.sge.com.cn/portal/marketAutomation/downloadExcelForQuoteDailyNew";

    /** SGE 贵金属报价统一单位。 */
    private static final String UNIT = "元/克";

    private static final int TIME_OUT = 30_000;
    private static final int RETRY_TIMES = 3;
    private static final int SLEEP_TIME = 1_000;

    private final Site site;
    private final LocalDate tradeDate;

    /** 采集错误（WebMagic 会静默吞掉 process() 的异常，这里暂存以便上报）。 */
    private final ErrorCollector errors = new ErrorCollector();

    /**
     * @param tradeDate 要采集的交易日；为 null 时使用当天（当天可能是非交易日，接口会返回空表）
     */
    public SgeProcessor(LocalDate tradeDate) {
        this.tradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        this.site = Site.me()
                .setTimeOut(TIME_OUT)
                .setRetryTimes(RETRY_TIMES)
                .setSleepTime(SLEEP_TIME)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .addHeader("Referer", "https://www.sge.com.cn/marketdata/dailyquotation")
                .setUseGzip(true);
    }

    /** 拼接带日期区间的完整地址。 */
    public String targetUrl() {
        return urlFor(tradeDate);
    }

    /** 指定交易日的下载地址。 */
    public static String urlFor(LocalDate tradeDate) {
        String date = tradeDate.toString();
        return BASE_URL + "?start_date=" + date + "&end_date=" + date;
    }

    @Override
    public void process(Page page) {
        byte[] bytes = page.getBytes();
        if (bytes == null || bytes.length == 0) {
            page.setSkip(true);
            return;
        }
        List<MdSpotPrice> quotes;
        try {
            quotes = SgeQuoteParser.parse(bytes, UNIT);
        } catch (Exception e) {
            errors.record(e);
            page.setSkip(true);
            return;
        }
        page.putField(CrawlKeys.SPOT_QUOTES, quotes);
    }

    public ErrorCollector getErrors() {
        return errors;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public String getSourceCode() {
        return CrawlSource.SGE.getCode();
    }

    @Override
    public Site getSite() {
        return site;
    }
}
