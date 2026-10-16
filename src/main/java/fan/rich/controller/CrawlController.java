package fan.rich.controller;

import fan.rich.common.R;
import fan.rich.crawler.CrawlSource;
import fan.rich.crawler.CrawlerService;
import fan.rich.dto.CrawlResult;
import fan.rich.entity.MdCrawlLog;
import fan.rich.entity.MdSourceProduct;
import fan.rich.mapper.MdCrawlLogMapper;
import fan.rich.mapper.MdSourceProductMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采集任务触发接口。
 *
 * <p>所有触发动作都会同步执行（外部站点响应时间不可控，WebMagic 内部已按数据源限定了线程数），
 * 前端调用时应给出 loading 提示。
 */
@RestController
@RequestMapping("/api/crawl")
public class CrawlController {

    private final CrawlerService crawlerService;
    private final MdCrawlLogMapper logMapper;
    private final MdSourceProductMapper productMapper;

    public CrawlController(CrawlerService crawlerService,
                           MdCrawlLogMapper logMapper,
                           MdSourceProductMapper productMapper) {
        this.crawlerService = crawlerService;
        this.logMapper = logMapper;
        this.productMapper = productMapper;
    }

    /**
     * 采集指定数据源行情。
     *
     * @param source 数据源编码 SHFE / SGE / SMM / CJ
     * @param date   目标日期；SMM / CJ 会忽略该参数
     */
    @PostMapping("/run")
    public R run(@RequestParam String source,
                 @RequestParam(required = false)
                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CrawlSource target = CrawlSource.of(source);
        if (target == null) {
            return R.fail("未知数据源：" + source + "，可选值 SHFE / SGE / SMM / CJ");
        }
        return toR(crawlerService.crawlQuotes(target, date));
    }

    /**
     * 采集「当天最新价」（四源统一，日间轮询口径）。
     *
     * @param source SHFE / SGE / SMM / CJ
     */
    @PostMapping("/intraday")
    public R intraday(@RequestParam String source) {
        CrawlSource target = CrawlSource.of(source);
        if (target == null) {
            return R.fail("未知数据源：" + source + "，可选值 SHFE / SGE / SMM / CJ");
        }
        return toR(crawlerService.crawlIntraday(target));
    }

    /**
     * 采集「上一工作日终值」（四源统一，夜间补采口径）。
     *
     * <p>正常由定时任务在夜间窗口内自动执行；这里保留手动入口，
     * 便于服务器长时间关机后开机手动补一次。
     *
     * @param source SHFE / SGE / SMM / CJ
     * @param date   目标交易日；留空按"昨天"处理
     */
    @PostMapping("/settle")
    public R settle(@RequestParam String source,
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CrawlSource target = CrawlSource.of(source);
        if (target == null) {
            return R.fail("未知数据源：" + source + "，可选值 SHFE / SGE / SMM / CJ");
        }
        return toR(crawlerService.crawlNightSettle(target, date));
    }

    /**
     * 盘中延时行情刷新（SHFE / SGE 专用入口，等价于 {@link #intraday(String)}）。
     *
     * @param source SHFE 或 SGE
     */
    @PostMapping("/realtime")
    public R realtime(@RequestParam String source) {
        CrawlSource target = CrawlSource.of(source);
        if (target == null) {
            return R.fail("未知数据源：" + source + "，可选值 SHFE / SGE / SMM / CJ");
        }
        return toR(crawlerService.crawlIntraday(target));
    }

    /**
     * 多金属报价表专用：依次刷新上期所、上金所的<b>盘中延时行情</b>，抓完即可读最新价。
     *
     * <p>返回每个源的采集结果（含状态与条数），前端据此提示；单个源失败不影响另一个。
     */
    @PostMapping("/realtime/metals")
    public R realtimeMetals() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CrawlSource source : new CrawlSource[]{CrawlSource.SHFE, CrawlSource.SGE}) {
            CrawlResult r = crawlerService.crawlIntraday(source);
            results.add(Map.of(
                    "source", source.getCode(),
                    "status", r.getStatus(),
                    "records", r.getRecords(),
                    "message", r.getMessage() == null ? "" : r.getMessage()));
        }
        return R.ok(results);
    }

    /** 发现某数据源的品种清单（仅 SMM / CJ）。 */
    @PostMapping("/discover")
    public R discover(@RequestParam String source) {
        CrawlSource target = CrawlSource.of(source);
        if (target == null) {
            return R.fail("未知数据源：" + source);
        }
        return toR(crawlerService.discover(target));
    }

    /** 依次采集全部数据源。 */
    @PostMapping("/runAll")
    public R runAll(@RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(crawlerService.crawlAll(date));
    }

    /** 最近采集日志。 */
    @GetMapping("/logs")
    public R logs(@RequestParam(defaultValue = "60") int limit) {
        List<MdCrawlLog> rows = logMapper.selectRecent(limit);
        return R.table(rows.size(), rows);
    }

    /** 启停某个品种（停止后定时采集不再请求它）。 */
    @PostMapping("/product/toggle")
    public R toggle(@RequestParam Long id, @RequestParam int enabled) {
        MdSourceProduct exists = productMapper.selectById(id);
        if (exists == null) {
            return R.fail("品种不存在");
        }
        MdSourceProduct update = new MdSourceProduct();
        update.setId(id);
        update.setEnabled(enabled == 1 ? 1 : 0);
        productMapper.updateById(update);
        return R.ok();
    }

    /** 品种下拉数据源：当前已登记的全部品种。 */
    @GetMapping("/sources")
    public R sources() {
        List<Map<String, String>> list = new ArrayList<>();
        for (CrawlSource source : CrawlSource.values()) {
            list.add(Map.of("code", source.getCode(), "name", source.getName()));
        }
        return R.ok(list);
    }

    private static R toR(CrawlResult result) {
        return switch (result.getStatus()) {
            case CrawlResult.STATUS_SUCCESS -> R.ok(result);
            // 跳过（目标日尚未发布 / 非交易日）与"正在采集中"都用 -2 返回：
            // 前端一律弹出提示，文案里已经说清原因
            case CrawlResult.STATUS_SKIPPED, CrawlResult.STATUS_REJECTED ->
                    R.fail(-2, result.getMessage());
            default -> R.fail(result.getMessage());
        };
    }
}
