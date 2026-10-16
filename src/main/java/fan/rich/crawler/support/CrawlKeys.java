package fan.rich.crawler.support;

/**
 * 采集结果在 WebMagic {@code ResultItems} 中的键名。
 *
 * <p>Processor 与 Pipeline 之间通过这几个 key 解耦：Processor 只负责把外部格式解析成
 * 领域实体，Pipeline 只负责按 key 取数据落库。
 */
public final class CrawlKeys {

    /** 值为 {@code List<MdFuturesPrice>}。 */
    public static final String FUTURES_QUOTES = "futuresQuotes";

    /** 值为 {@code List<MdSpotPrice>}。 */
    public static final String SPOT_QUOTES = "spotQuotes";

    /** 值为 {@code List<MdSourceProduct>}。 */
    public static final String SOURCE_PRODUCTS = "sourceProducts";

    /** 某次请求对应的品种 ID，用于失败时定位。 */
    public static final String PRODUCT_ID = "productId";

    private CrawlKeys() {
    }
}
