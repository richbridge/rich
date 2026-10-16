package fan.rich.controller.openapi;

import fan.rich.common.R;
import fan.rich.service.CanteenStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放接口 · 食堂经营。
 */
@RestController
@RequestMapping("/openapi/canteen")
public class OpenApiCanteenController {

    private final CanteenStatsService canteenStatsService;
    private final OpenApiSupport support;

    public OpenApiCanteenController(CanteenStatsService canteenStatsService, OpenApiSupport support) {
        this.canteenStatsService = canteenStatsService;
        this.support = support;
    }

    /** 食堂经营总览：收支、就餐人次、结余。 */
    @GetMapping("/overview")
    public R overview() {
        R denied = support.require("canteen");
        if (denied != null) {
            return denied;
        }
        return R.ok(canteenStatsService.overview());
    }

    /** 低储预警清单。 */
    @GetMapping("/stock")
    public R stock() {
        R denied = support.require("canteen");
        if (denied != null) {
            return denied;
        }
        return R.ok(canteenStatsService.stockWarnings());
    }

    /**
     * 年度月度经营表。
     *
     * @param year 年份，默认当前年
     */
    @GetMapping("/monthly")
    public R monthly(@RequestParam(required = false) Integer year) {
        R denied = support.require("canteen");
        if (denied != null) {
            return denied;
        }
        int target = year == null ? java.time.Year.now().getValue() : year;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", target);
        data.put("rows", canteenStatsService.monthlyReport(target));
        return R.ok(data);
    }
}
