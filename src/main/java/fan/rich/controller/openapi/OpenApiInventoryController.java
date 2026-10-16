package fan.rich.controller.openapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.Asset;
import fan.rich.entity.InvTask;
import fan.rich.service.AssetService;
import fan.rich.service.InventoryService;
import fan.rich.service.InventoryStatsService;
import fan.rich.service.InvTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口 · 资产盘点。
 *
 * <p>移动端主打「扫码盘点」：扫码得到盘点对象 → 现场录入实盘数量 → 批量提交，
 * 后台自动计算盈亏（与 PC 端同一套 {@link InventoryService}，盘点结果同源）。
 */
@RestController
@RequestMapping("/openapi/inventory")
public class OpenApiInventoryController {

    private final InventoryService inventoryService;
    private final InventoryStatsService inventoryStatsService;
    private final AssetService assetService;
    private final InvTaskService taskService;
    private final OpenApiSupport support;

    public OpenApiInventoryController(InventoryService inventoryService,
                                      InventoryStatsService inventoryStatsService,
                                      AssetService assetService,
                                      InvTaskService taskService,
                                      OpenApiSupport support) {
        this.inventoryService = inventoryService;
        this.inventoryStatsService = inventoryStatsService;
        this.assetService = assetService;
        this.taskService = taskService;
        this.support = support;
    }

    /** 盘点总览：账实差异、各类别完成情况。 */
    @GetMapping("/overview")
    public R overview() {
        R denied = support.require("inventory");
        if (denied != null) {
            return denied;
        }
        return R.ok(inventoryStatsService.overview());
    }

    /**
     * 资产台账分页。
     *
     * @param keyword 资产编号 / 名称 / 保管人模糊匹配
     */
    @GetMapping("/assets")
    public R assets(@RequestParam(defaultValue = "1") long page,
                    @RequestParam(defaultValue = "20") long limit,
                    @RequestParam(required = false) String keyword) {
        R denied = support.require("inventory");
        if (denied != null) {
            return denied;
        }
        LambdaQueryWrapper<Asset> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(Asset::getAssetCode, keyword)
                    .or().like(Asset::getAssetName, keyword)
                    .or().like(Asset::getCustodian, keyword)
                    .or().like(Asset::getQrCode, keyword));
        }
        q.orderByAsc(Asset::getId);
        IPage<Asset> records = assetService.page(new Page<>(page, limit), q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getRecords());
        data.put("total", records.getTotal());
        return R.ok(data);
    }

    /**
     * 盘点任务列表。
     *
     * <p>移动端扫码登记时必须挂载到某个盘点任务上（与 PC 端同一约束），
     * 所以先取任务清单，再开始扫^{*}码。
     */
    @GetMapping("/tasks")
    public R tasks(@RequestParam(defaultValue = "1") long page,
                   @RequestParam(defaultValue = "20") long limit,
                   @RequestParam(required = false) String status) {
        R denied = support.require("inventory");
        if (denied != null) {
            return denied;
        }
        LambdaQueryWrapper<InvTask> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(InvTask::getStatus, status);
        }
        q.orderByDesc(InvTask::getPlanDate).orderByDesc(InvTask::getId);
        IPage<InvTask> records = taskService.page(new Page<>(page, limit), q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getRecords());
        data.put("total", records.getTotal());
        return R.ok(data);
    }

    /**
     * 扫码解析：按二维码内容取盘点对象（台账数量、规格、存放地、保管人）。
     *
     * @param code 二维码内容（资产号 / 二维码号均可）
     */
    @GetMapping("/scan")
    public R scan(@RequestParam String code) {
        R denied = support.require("inventory");
        if (denied != null) {
            return denied;
        }
        InventoryService.Subject subject = inventoryService.resolve(code.trim());
        if (subject == null) {
            return R.fail("未找到对应的盘点对象：" + code);
        }
        return R.ok(subject);
    }

    /**
     * 提交盘点结果。
     *
     * <p>请求体示例：
     * <pre>
     * {"taskId": 1, "checker": "张三", "items": [{"code": "GD-001", "actualQty": 3}]}
     * </pre>
     * taskId 为空时按盘点单据逐个录入（同样走 submit 逻辑）。
     */
    @PostMapping("/check")
    public R check(@RequestBody Map<String, Object> body) {
        R denied = support.require("inventory");
        if (denied != null) {
            return denied;
        }
        Long taskId = toLong(body.get("taskId"));
        String checker = body.get("checker") == null ? null : String.valueOf(body.get("checker"));
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> items) {
            List<Map<String, Object>> payload = new java.util.ArrayList<>();
            for (Object o : items) {
                Map<String, Object> item = new LinkedHashMap<>();
                if (o instanceof Map<?, ?> m) {
                    item.put("code", String.valueOf(m.get("code")));
                    item.put("actualQty", new BigDecimal(String.valueOf(m.get("actualQty"))));
                    if (m.get("remark") != null) {
                        item.put("remark", String.valueOf(m.get("remark")));
                    }
                }
                payload.add(item);
            }
            if (!payload.isEmpty()) {
                int[] result = inventoryService.submitBatch(taskId, payload, checker);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("inserted", result[0]);
                data.put("updated", result[1]);
                data.put("count", payload.size());
                return R.ok(data);
            }
        }
        String code = body.get("code") == null ? null : String.valueOf(body.get("code"));
        BigDecimal qty = new BigDecimal(String.valueOf(body.get("actualQty")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        // source 标记为 APP 扫码，便于后台区分录入渠道
        Object saved = inventoryService.submit(taskId, code, qty, checker,
                InventoryService.SRC_APP, remark);
        if (saved == null) {
            return R.fail("盘点失败：任务不存在或二维码无法解析");
        }
        return R.ok(saved);
    }

    private Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
