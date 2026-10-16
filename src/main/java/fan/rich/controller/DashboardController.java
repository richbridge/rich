package fan.rich.controller;

import fan.rich.common.R;
import fan.rich.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可视化大屏数据接口。
 *
 * <p>{@code /overview} 与 {@code /price-trend} 面向综合经营大屏，汇聚销售、价格、盘点、
 * 档案、培训、工资、食堂、审计八个模块；其余接口是销售维度的下钻数据，
 * 综合大屏与销售看板共用，避免两套口径。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 综合经营大屏总览：顶层 KPI + 模块卡片 + 待办 + 图表 + 采集健康度。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(dashboardService.overview());
    }

    /** 主力合约收盘价走势（多品种共用时间轴）。 */
    @GetMapping("/price-trend")
    public R priceTrend(@RequestParam(defaultValue = "24") int limit) {
        return R.ok(dashboardService.priceTrend(limit));
    }

    /** 核心指标汇总。 */
    @GetMapping("/summary")
    public R summary() {
        return R.ok(dashboardService.summary());
    }

    /** 月度发货趋势。 */
    @GetMapping("/trend")
    public R trend() {
        return R.ok(dashboardService.trend());
    }

    /** 按发货品种统计金含量。 */
    @GetMapping("/category")
    public R category() {
        return R.ok(dashboardService.category());
    }

    /** 按销售商统计金含量。 */
    @GetMapping("/buyer")
    public R buyer() {
        return R.ok(dashboardService.buyer());
    }

    /** 最近发货记录。 */
    @GetMapping("/recent")
    public R recent(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(dashboardService.recent(limit));
    }
}
