package fan.rich.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import fan.rich.common.R;
import fan.rich.entity.MdFuturesPrice;
import fan.rich.entity.MdSourceProduct;
import fan.rich.entity.MdSpotPrice;
import fan.rich.service.PriceQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 价格数据查询接口。
 */
@RestController
@RequestMapping("/api/price")
public class PriceController {

    private final PriceQueryService queryService;

    public PriceController(PriceQueryService queryService) {
        this.queryService = queryService;
    }

    /** 采集概况（数据量 + 各源最新交易日）。 */
    @GetMapping("/summary")
    public R summary() {
        return R.ok(queryService.summary());
    }

    /** 现货行情分页列表。 */
    @GetMapping("/spot/list")
    public R spotList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "20") long limit,
                      @RequestParam(required = false) String source,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                      @RequestParam(required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        IPage<MdSpotPrice> result = queryService.spotList(page, limit, source, keywords, startDate, endDate);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 期货行情分页列表。 */
    @GetMapping("/futures/list")
    public R futuresList(@RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long limit,
                         @RequestParam(required = false) String instrumentId,
                         @RequestParam(required = false) String contractCode,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        IPage<MdFuturesPrice> result =
                queryService.futuresList(page, limit, instrumentId, contractCode, tradeDate);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 期货品种概览（每个品种的合约数与最新日期）。 */
    @GetMapping("/futures/instruments")
    public R instruments() {
        return R.ok(queryService.instrumentSummary());
    }

    /** 期货品种主力合约连续走势（每日成交量最大合约收盘价）。 */
    @GetMapping("/futures/trend")
    public R futuresTrend(@RequestParam String instrumentId,
                          @RequestParam(defaultValue = "120") int limit) {
        return R.ok(queryService.futuresTrend(instrumentId, limit));
    }

    /**
     * 多金属报价表（金/银=上金所 T+D，铅锌铜锡=上期所主力合约）。
     *
     * @param date 指定交易日（yyyy-MM-dd）；为空时各源取最新可用交易日
     */
    @GetMapping("/multi-metal")
    public R multiMetal(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(queryService.multiMetalQuotes(date));
    }

    /** 多金属报价表可选交易日（倒序），供日期下拉使用。 */
    @GetMapping("/multi-metal/dates")
    public R multiMetalDates() {
        return R.ok(queryService.multiMetalDates());
    }

    /** 某品种最新交易日的全部合约。 */
    @GetMapping("/futures/contracts")
    public R contracts(@RequestParam(required = false) String instrumentId) {
        return R.ok(queryService.latestFutures(instrumentId));
    }

    /** 品种字典分页列表（layui table 约定：code/count/data）。 */
    @GetMapping("/product/list")
    public R productList(@RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long limit,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false) String keywords) {
        IPage<MdSourceProduct> result = queryService.productList(source, keywords, page, limit);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 某品种均价走势。 */
    @GetMapping("/trend")
    public R trend(@RequestParam String source,
                   @RequestParam String productId,
                   @RequestParam(defaultValue = "30") int limit) {
        return R.ok(queryService.trend(source, productId, limit));
    }

    /** 指定数据源最新报价，用于总览表格。 */
    @GetMapping("/latest")
    public R latest(@RequestParam(required = false) String source,
                    @RequestParam(defaultValue = "20") int limit) {
        List<MdSpotPrice> rows = queryService.latestSpot(source, limit);
        return R.table(rows.size(), rows);
    }

    /** 已入库的现货品种清单，用于看板下拉框。 */
    @GetMapping("/subjects")
    public R subjects() {
        return R.ok(queryService.spotSubjects());
    }
}
