package fan.rich.crawler;

/**
 * 价格采集数据源。
 *
 * <p>每个枚举值对应一个外部行情站点，并描述其接入方式，便于前端下拉、日志与
 * 调度器统一处理。
 */
public enum CrawlSource {

    /** 上海期货交易所（延时行情，JSON，合约维度）。 */
    SHFE("SHFE", "上海期货交易所", "https://www.shfe.com.cn", true, false,
            "https://www.shfe.com.cn/data/tradedata/future/delaymarket/delaymarket_all.dat"),

    /**
     * 上海黄金交易所（合约维度）。
     *
     * <p>当日走延时行情页 {@code https://www.sge.com.cn/sjzx/yshqbg}（HTML 表格，
     * 盘中可反复刷新）；历史走 xlsx 下载接口 {@code downloadExcelForQuoteDailyNew}
     * （含收盘价，收盘后覆盖当日延时行情的最新价）。
     */
    SGE("SGE", "上海黄金交易所", "https://www.sge.com.cn", true, false,
            "https://www.sge.com.cn/portal/marketAutomation/downloadExcelForQuoteDailyNew"),

    /** 上海有色网 SMM（现货历史价格，JSON，品种维度）。 */
    SMM("SMM", "上海有色网", "https://hq.smm.cn", true, true,
            "https://hq.smm.cn/ajax/spot/history"),

    /** 长江有色网 CJ（现货价格，JSON，品种维度；接口结构与 SMM 一致）。 */
    CJ("CJ", "长江有色网", "https://cj.smm.cn", true, true,
            "https://cj.smm.cn/ajax/spot/history");

    private final String code;
    private final String name;
    private final String siteUrl;
    private final boolean enabled;
    private final boolean needDiscovery;
    private final String endpoint;

    CrawlSource(String code, String name, String siteUrl, boolean enabled,
                boolean needDiscovery, String endpoint) {
        this.code = code;
        this.name = name;
        this.siteUrl = siteUrl;
        this.enabled = enabled;
        this.needDiscovery = needDiscovery;
        this.endpoint = endpoint;
    }

    /** 按 code 查找，未命中返回 null。 */
    public static CrawlSource of(String code) {
        if (code == null) {
            return null;
        }
        for (CrawlSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) {
                return source;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否需要先爬取品种清单（SMM / CJ 按 productId 逐个请求）。 */
    public boolean isNeedDiscovery() {
        return needDiscovery;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
