package fan.rich.crawler.processor;

import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.support.CrawlKeys;
import fan.rich.entity.MdSourceProduct;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMM / CJ 品种清单发现器（WebMagic PageProcessor）。
 *
 * <p>两个站的行情接口都要以 {@code productId} 发起请求，但官方没有开放品种清单 API，
 * 只能从页面 HTML 里提取。二者的组织形式不同：
 * <ul>
 *   <li><b>SMM</b>：按金属专区分站，每个页面的"现货价格"下拉里有
 *       {@code <option value="category_{id}">{名称}</option>}；</li>
 *   <li><b>CJ</b>：单页应用，首页即列出全部品种，形如
 *       {@code <a href="/category/{id}" title="{名称}">}。</li>
 * </ul>
 *
 * <p>这里用正则而非 XPath 提取：Xsoup 对 {@code starts-with()} 等谓词支持有限，
 * 且这类站点经常在标签里插入 {@code data-*} 属性，正则更稳定。
 */
public class SmmProductProcessor implements PageProcessor {

    /** Request extra：当前页面所属分组（中文）。 */
    public static final String EXTRA_GROUP = "group";

    /** SMM 已验证可用的金属专区及其分组名。 */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "copper", "铜专区", "aluminum", "铝专区", "lead", "铅专区",
            "zinc", "锌专区", "nickel", "镍专区", "tin", "锡专区"
    );

    /** SMM 下拉选项：category_品种ID + 名称。 */
    private static final Pattern SMM_OPTION =
            Pattern.compile("<option value=\"category_(\\d+)\">([^<]+)</option>");

    /** CJ 首页链接：/category/品种ID + title 名称。 */
    private static final Pattern CJ_LINK =
            Pattern.compile("href=\"/category/(\\d+)\"[^>]*title=\"([^\"]+)\"");

    static {
        for (String slug : CATEGORY_NAMES.keySet()) {
            CATEGORIES.put("https://hq.smm.cn/" + slug, CATEGORY_NAMES.get(slug));
        }
    }

    /*
     * 专区首页体积很大（铜 650KB、铝 820KB、铅 786KB），且站点响应不稳定，
     * 30s 超时会导致大页面被判定失败——实测铝、铅两专区因此漏抓。这里放宽到 60s。
     */
    private static final int TIME_OUT = 60_000;

    private final Site site;
    private final CrawlSource source;

    public SmmProductProcessor(CrawlSource source) {
        if (source != CrawlSource.SMM && source != CrawlSource.CJ) {
            throw new IllegalArgumentException("品种发现仅支持 SMM / CJ，当前：" + source);
        }
        this.source = source;
        this.site = Site.me()
                .setCharset("UTF-8")
                .setTimeOut(TIME_OUT)
                .setRetryTimes(2)
                .setSleepTime(500)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .setUseGzip(true);
    }

    /** 该数据源要抓取的起始页面。 */
    public List<String> startUrls() {
        // CJ 是单页应用，首页已含全部品种；SMM 按专区逐页抓取
        return source == CrawlSource.CJ
                ? List.of(source.getSiteUrl() + "/")
                : new ArrayList<>(CATEGORIES.keySet());
    }

    /** 起始页面 URL → 分组名，供 CrawlerService 构造 Request extras。 */
    public Map<String, String> urlGroups() {
        return source == CrawlSource.CJ
                ? Map.of(source.getSiteUrl() + "/", "长江有色")
                : CATEGORIES;
    }

    @Override
    public void process(Page page) {
        String raw = page.getRawText();
        if (raw == null || raw.isBlank()) {
            page.setSkip(true);
            return;
        }
        Object group = page.getRequest().getExtra(EXTRA_GROUP);
        String groupName = group == null ? source.getName() : String.valueOf(group);

        List<MdSourceProduct> products = source == CrawlSource.CJ
                ? match(CJ_LINK, raw, groupName)
                : match(SMM_OPTION, raw, groupName);
        page.putField(CrawlKeys.SOURCE_PRODUCTS, products);
    }

    private List<MdSourceProduct> match(Pattern pattern, String html, String groupName) {
        List<MdSourceProduct> products = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String productId = matcher.group(1);
            String productName = matcher.group(2).trim();
            if (productId.isEmpty() || productName.isEmpty()) {
                continue;
            }
            MdSourceProduct product = new MdSourceProduct();
            product.setSource(source.getCode());
            product.setProductId(productId);
            product.setProductName(productName);
            product.setProductGroup(groupName);
            product.setEnabled(1);
            products.add(product);
        }
        return products;
    }

    @Override
    public Site getSite() {
        return site;
    }
}
