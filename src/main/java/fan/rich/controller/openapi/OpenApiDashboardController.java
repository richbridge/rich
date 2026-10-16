package fan.rich.controller.openapi;

import fan.rich.common.R;
import fan.rich.service.DashboardService;
import fan.rich.service.RbacService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口 · 综合总览。
 *
 * <p>返回结构与 PC 端综合大屏完全一致（同一份 {@link DashboardService}），
 * 移动端按屏幕尺寸自行取舍展示。
 */
@RestController
@RequestMapping("/openapi/dashboard")
public class OpenApiDashboardController {

    private final DashboardService dashboardService;
    private final OpenApiSupport support;
    private final RbacService rbacService;

    public OpenApiDashboardController(DashboardService dashboardService,
                                      OpenApiSupport support,
                                      RbacService rbacService) {
        this.dashboardService = dashboardService;
        this.support = support;
        this.rbacService = rbacService;
    }

    /**
     * 综合经营总览。
     *
     * @param full 传 {@code true} 时一并返回图表所需明细，移动端首屏建议省略
     */
    @GetMapping("/overview")
    public R overview(@RequestParam(defaultValue = "false") boolean full) {
        R denied = support.require("overview");
        if (denied != null) {
            return denied;
        }
        Map<String, Object> data = dashboardService.overview();
        // kpiList：与 /modules 同一份带单位的 KPI 列表，前端不必到处硬写中文字段名
        data.put("kpiList", normalizedKpi(data.get("kpi")));
        if (!full) {
            Map<String, Object> slim = new LinkedHashMap<>(data);
            slim.remove("charts");
            return R.ok(slim);
        }
        return R.ok(data);
    }

    /**
     * 顶层 KPI 的展示元信息：顺序、中文名、单位与换算。
     *
     * <p>{@code DashboardService} 出于渲染性能返回的是扁平 Map（key 为英文字段），
     * 移动端没有这张字典表，这里统一翻成带单位的列表并顺手做单位换算
     * （金额类字段后台存的是元，展示口径是万元，与 PC 大屏 /10000 保持一致）。
     */
    private static final List<Object[]> KPI_META = List.of(
            new Object[]{"totalGold", "金含量", "g", 1d},
            new Object[]{"dryWeight", "精矿干重", "t", 1d},
            new Object[]{"settlementAmount", "提金结算", "万元", 1d / 10000},
            new Object[]{"oweGold", "欠金量", "g", 1d},
            new Object[]{"assetAmount", "资产原值", "万元", 1d / 10000},
            new Object[]{"salaryApproved", "工资核定", "万元", 1d / 10000},
            new Object[]{"canteenPeople", "就餐人次", "人次", 1d},
            new Object[]{"batchCount", "发货批次", "批", 1d}
    );

    /** 展示精度：不同量级的指标混在一起，统一位数会很别扭。 */
    private static final Map<String, Integer> KPI_SCALE = Map.of(
            "totalGold", 2, "dryWeight", 3, "settlementAmount", 2, "oweGold", 2,
            "assetAmount", 2, "salaryApproved", 2, "canteenPeople", 0, "batchCount", 0
    );

    /** 各模块关键指标卡片（不含图表明细，体积更小）。 */
    @GetMapping("/modules")
    public R modules() {
        R denied = support.require("overview");
        if (denied != null) {
            return denied;
        }
        Map<String, Object> data = dashboardService.overview();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpi", normalizedKpi(data.get("kpi")));
        result.put("kpiRaw", data.get("kpi"));
        result.put("modules", data.get("modules"));
        result.put("reminders", normalizedReminders(data.get("reminders")));
        return R.ok(result);
    }

    /** 把 KPI 字典翻成 [{key,label,value,unit}] 列表。 */
    private static List<Map<String, Object>> normalizedKpi(Object raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return list;
        }
        for (Object[] meta : KPI_META) {
            String key = (String) meta[0];
            if (!map.containsKey(key)) {
                continue;
            }
            double scale = (Double) meta[3];
            int digits = KPI_SCALE.getOrDefault(key, 2);
            Object converted = convert(map.get(key), scale, digits);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", meta[1]);
            item.put("value", converted);
            item.put("unit", meta[2]);
            list.add(item);
        }
        return list;
    }

    /** 数值换算：非数字原样回传，不做猜测。 */
    private static Object convert(Object value, double scale, int digits) {
        if (!(value instanceof Number num)) {
            return value;
        }
        double after = num.doubleValue() * scale;
        if (digits <= 0) {
            return (long) Math.round(after);
        }
        return java.math.BigDecimal.valueOf(after).setScale(digits, java.math.RoundingMode.HALF_UP);
    }

    /** 给待办补一条可直接展示的 title，移动端不必再拼字段名。 */
    private static List<Map<String, Object>> normalizedReminders(Object raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(raw instanceof List<?> rows)) {
            return list;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(castMap(item));
            Object module = copy.get("module");
            Object count = copy.get("count");
            Object text = copy.get("text");
            StringBuilder title = new StringBuilder();
            if (module != null) {
                title.append(module);
            }
            if (text != null) {
                title.append(' ').append(text);
            }
            copy.put("title", title.toString().trim());
            copy.put("desc", count == null ? null : (count + " 项待处理"));
            list.add(copy);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    /** 跨模块待办事项。 */
    @GetMapping("/reminders")
    public R reminders() {
        R denied = support.require("overview");
        if (denied != null) {
            return denied;
        }
        return R.ok(dashboardService.overview().get("reminders"));
    }

    /**
     * 当前用户「有权限使用」的模块清单，移动端据此决定首屏卡片。
     */
    @GetMapping("/permissions")
    public R permissions() {
        Long userId = support.currentUserId();
        if (userId == null) {
            return R.fail(401, "未登录或 token 已失效");
        }
        List<String> codes = new ArrayList<>(rbacService.permissionCodes(userId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("superAdmin", rbacService.isSuperAdmin(userId));
        data.put("roles", rbacService.roleCodes(userId));
        data.put("permissions", codes);
        return R.ok(data);
    }
}
