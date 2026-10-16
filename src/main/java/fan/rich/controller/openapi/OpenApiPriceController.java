package fan.rich.controller.openapi;

import fan.rich.common.R;
import fan.rich.entity.MdFuturesPrice;
import fan.rich.entity.MdSpotPrice;
import fan.rich.service.PriceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开放接口 · 金属价格行情。
 *
 * <p>数据来源与 PC 后台一致：上期所（SHFE）、上金所（SGE）、上海有色（SMM）、长江有色（CJ）四源。
 */
@RestController
@RequestMapping("/openapi/price")
public class OpenApiPriceController {

    private final PriceQueryService priceQueryService;
    private final OpenApiSupport support;

    public OpenApiPriceController(PriceQueryService priceQueryService, OpenApiSupport support) {
        this.priceQueryService = priceQueryService;
        this.support = support;
    }

    /** 价格概况：各源品种数、最新交易日等。 */
    @GetMapping("/summary")
    public R summary() {
        R denied = support.require("price");
        if (denied != null) {
            return denied;
        }
        return R.ok(priceQueryService.summary());
    }

    /**
     * 现货最新挂牌价。
     *
     * @param source 数据源 SGE / SMM / CJ，空表示全部
     * @param limit  返回条数
     */
    @GetMapping("/latest")
    public R latest(@RequestParam(required = false) String source,
                    @RequestParam(defaultValue = "20") int limit) {
        R denied = support.require("price");
        if (denied != null) {
            return denied;
        }
        List<MdSpotPrice> rows = priceQueryService.latestSpot(source, limit);
        return R.ok(rows);
    }

    /** 上期所期货最新行情。 */
    @GetMapping("/futures")
    public R futures(@RequestParam(required = false) String instrumentId) {
        R denied = support.require("price");
        if (denied != null) {
            return denied;
        }
        List<MdFuturesPrice> rows = priceQueryService.latestFutures(instrumentId);
        return R.ok(rows);
    }

    /**
     * 单品种价格走势。
     *
     * @param source    数据源：SHFE 时 productId 传品种代号（au / ag / cu）
     * @param productId 品种编号
     * @param days      取最近多少个数据点
     */
    @GetMapping("/trend")
    public R trend(@RequestParam(required = false) String source,
                   @RequestParam(required = false) String productId,
                   @RequestParam(defaultValue = "30") int days) {
        R denied = support.require("price");
        if (denied != null) {
            return denied;
        }
        if ("SHFE".equalsIgnoreCase(source)) {
            return R.ok(priceQueryService.futuresTrend(productId, days));
        }
        Map<String, Object> trend = priceQueryService.trend(source, productId, days);
        return R.ok(trend);
    }

    /** 上金所等多源同日报价（App 报价墙用）。 */
    @GetMapping("/multi")
    public R multi() {
        R denied = support.require("price");
        if (denied != null) {
            return denied;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dates", priceQueryService.multiMetalDates());
        data.put("quotes", priceQueryService.multiMetalQuotes(null));
        return R.ok(data);
    }
}
