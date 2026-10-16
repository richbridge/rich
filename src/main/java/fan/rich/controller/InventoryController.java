package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.Asset;
import fan.rich.entity.AstGoods;
import fan.rich.entity.InvRecord;
import fan.rich.entity.InvTask;
import fan.rich.service.AssetService;
import fan.rich.service.AstGoodsService;
import fan.rich.service.InventoryService;
import fan.rich.service.InventoryStatsService;
import fan.rich.service.InvRecordService;
import fan.rich.service.InvTaskService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点模块接口。
 *
 * <p>分四组：
 * <ul>
 *   <li><b>台账</b>：固定资产、库存物资的档案 CRUD 与二维码赋码</li>
 *   <li><b>任务</b>：盘点任务的创建、生成清单、完成、进度</li>
 *   <li><b>明细</b>：盘点结果查询与差异处理</li>
 *   <li><b>扫码</b>：{@code /scan/submit}、{@code /scan/batch}、{@code /task/subjects}
 *       ——这三条是留给手机 APP 的，PC 扫码页也走同一套，
 *       因此现在 PC 端验证通过，APP 接入时无需再动后端</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final AssetService assetService;
    private final AstGoodsService goodsService;
    private final InvTaskService taskService;
    private final InvRecordService recordService;
    private final InventoryService inventoryService;
    private final InventoryStatsService statsService;

    public InventoryController(AssetService assetService,
                               AstGoodsService goodsService,
                               InvTaskService taskService,
                               InvRecordService recordService,
                               InventoryService inventoryService,
                               InventoryStatsService statsService) {
        this.assetService = assetService;
        this.goodsService = goodsService;
        this.taskService = taskService;
        this.recordService = recordService;
        this.inventoryService = inventoryService;
        this.statsService = statsService;
    }

    // ================================================================ 固定资产

    /** 固定资产分页列表。 */
    @GetMapping("/asset/list")
    public R assetList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "20") long limit,
                       @RequestParam(required = false) String keywords,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String useDept,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String location) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(Asset::getAssetName, keywords)
                    .or().like(Asset::getAssetCode, keywords)
                    .or().like(Asset::getSpec, keywords)
                    .or().like(Asset::getQrCode, keywords)
                    .or().like(Asset::getCustodian, keywords);
        }
        wrapper.eq(StringUtils.hasText(category), Asset::getCategory, category)
                .eq(StringUtils.hasText(useDept), Asset::getUseDept, useDept)
                .eq(StringUtils.hasText(status), Asset::getStatus, status)
                .eq(StringUtils.hasText(location), Asset::getLocation, location)
                .orderByAsc(Asset::getAssetCode);
        IPage<Asset> result = assetService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改固定资产。 */
    @PostMapping("/asset/save")
    public R assetSave(@RequestBody Asset asset) {
        if (!StringUtils.hasText(asset.getQrCode())) {
            asset.setQrCode("AST-" + System.currentTimeMillis());
        }
        if (!StringUtils.hasText(asset.getStatus())) {
            asset.setStatus("在用");
        }
        assetService.saveOrUpdate(asset);
        return R.ok(asset.getId());
    }

    /** 删除固定资产。 */
    @PostMapping("/asset/delete")
    public R assetDelete(@RequestParam Long id) {
        assetService.removeById(id);
        return R.ok();
    }

    /** 固定资产分类、部门、地点下拉数据。 */
    @GetMapping("/asset/options")
    public R assetOptions() {
        Map<String, Object> data = new HashMap<>();
        data.put("categories", distinct("ast_asset", "category"));
        data.put("depts", distinct("ast_asset", "use_dept"));
        data.put("locations", distinct("ast_asset", "location"));
        return R.ok(data);
    }

    // ================================================================ 库存物资

    /** 库存物资分页列表。 */
    @GetMapping("/goods/list")
    public R goodsList(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "20") long limit,
                       @RequestParam(required = false) String keywords,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String warehouse,
                       @RequestParam(required = false) String status) {
        LambdaQueryWrapper<AstGoods> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(AstGoods::getGoodsName, keywords)
                    .or().like(AstGoods::getGoodsCode, keywords)
                    .or().like(AstGoods::getSpec, keywords)
                    .or().like(AstGoods::getQrCode, keywords);
        }
        wrapper.eq(StringUtils.hasText(category), AstGoods::getCategory, category)
                .eq(StringUtils.hasText(warehouse), AstGoods::getWarehouse, warehouse)
                .eq(StringUtils.hasText(status), AstGoods::getStatus, status)
                .orderByAsc(AstGoods::getGoodsCode);
        IPage<AstGoods> result = goodsService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改库存物资。 */
    @PostMapping("/goods/save")
    public R goodsSave(@RequestBody AstGoods goods) {
        if (!StringUtils.hasText(goods.getQrCode())) {
            goods.setQrCode("GDS-" + System.currentTimeMillis());
        }
        if (!StringUtils.hasText(goods.getStatus())) {
            goods.setStatus("正常");
        }
        goodsService.saveOrUpdate(goods);
        return R.ok(goods.getId());
    }

    /** 删除库存物资。 */
    @PostMapping("/goods/delete")
    public R goodsDelete(@RequestParam Long id) {
        goodsService.removeById(id);
        return R.ok();
    }

    /** 库存物资分类与仓库下拉数据。 */
    @GetMapping("/goods/options")
    public R goodsOptions() {
        Map<String, Object> data = new HashMap<>();
        data.put("categories", distinct("ast_goods", "category"));
        data.put("warehouses", distinct("ast_goods", "warehouse"));
        return R.ok(data);
    }

    /** 食堂物料分类下拉（食堂物资作为盘点标的时的筛选维度）。 */
    @GetMapping("/canteen/options")
    public R canteenOptions() {
        Map<String, Object> data = new HashMap<>();
        data.put("categories", distinct("canteen_material", "category"));
        return R.ok(data);
    }

    // ================================================================ 二维码

    /**
     * 批量生成二维码码值。
     *
     * @param type 标的类型，见 {@link InventoryService#TYPE_ASSET} 等；为空表示全部
     */
    @PostMapping("/qrcode/generate")
    public R generateQrCode(@RequestParam(required = false) String type) {
        int n = inventoryService.generateQrCodes(type);
        return R.ok(n);
    }

    /** 标签打印数据。 */
    @GetMapping("/labels")
    public R labels(@RequestParam(defaultValue = "ASSET") String type,
                    @RequestParam(required = false) String category) {
        return R.ok(inventoryService.labelData(type, category));
    }

    // ================================================================ 盘点任务

    /** 盘点任务分页列表。 */
    @GetMapping("/task/list")
    public R taskList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "20") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) String taskType,
                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InvTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(InvTask::getTaskName, keywords)
                    .or().like(InvTask::getTaskNo, keywords)
                    .or().like(InvTask::getOwner, keywords);
        }
        wrapper.eq(StringUtils.hasText(taskType), InvTask::getTaskType, taskType)
                .eq(StringUtils.hasText(status), InvTask::getStatus, status)
                .orderByDesc(InvTask::getId);
        IPage<InvTask> result = taskService.page(new Page<>(page, limit), wrapper);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 全部任务，供下拉与 APP 选择。 */
    @GetMapping("/task/all")
    public R taskAll() {
        return R.ok(taskService.list(new LambdaQueryWrapper<InvTask>()
                .ne(InvTask::getStatus, InventoryService.TASK_CANCEL)
                .orderByDesc(InvTask::getId)));
    }

    /** 新增或修改任务。 */
    @PostMapping("/task/save")
    public R taskSave(@RequestBody InvTask task) {
        return R.ok(inventoryService.createTask(task).getId());
    }

    /** 更新任务（范围、人员等，不影响已生成的清单）。 */
    @PostMapping("/task/update")
    public R taskUpdate(@RequestBody InvTask task) {
        return R.ok(inventoryService.updateTask(task).getId());
    }

    /** 生成应盘清单。 */
    @PostMapping("/task/generate")
    public R taskGenerate(@RequestParam Long id) {
        int n = inventoryService.generateSubjects(id);
        return R.ok(n);
    }

    /** 完成任务。 */
    @PostMapping("/task/finish")
    public R taskFinish(@RequestParam Long id) {
        InvTask task = inventoryService.finishTask(id);
        return task == null ? R.fail("任务不存在") : R.ok(task.getStatus());
    }

    /** 删除任务及其明细。 */
    @PostMapping("/task/delete")
    public R taskDelete(@RequestParam Long id) {
        inventoryService.deleteTask(id);
        return R.ok();
    }

    /** 任务进度。 */
    @GetMapping("/task/stat")
    public R taskStat(@RequestParam Long id) {
        InvTask task = taskService.getById(id);
        if (task == null) {
            return R.fail("任务不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("subjectCount", task.getSubjectCount());
        data.put("checkedCount", task.getCheckedCount());
        data.put("normalCount", task.getNormalCount());
        data.put("profitCount", task.getProfitCount());
        data.put("lossCount", task.getLossCount());
        data.put("diffAmount", task.getDiffAmount());
        data.put("status", task.getStatus());
        return R.ok(data);
    }

    /**
     * 任务清单（APP 离线下载用）。
     *
     * <p>APP 开工前先把清单拉到手机里，现场没信号也能照单盘点，回传时按标的逐条上报。
     */
    @GetMapping("/task/subjects")
    public R taskSubjects(@RequestParam Long id) {
        List<InvRecord> list = recordService.list(new LambdaQueryWrapper<InvRecord>()
                .eq(InvRecord::getTaskId, id).orderByAsc(InvRecord::getId));
        return R.ok(list);
    }

    // ================================================================ 盘点明细

    /** 盘点明细分页列表。 */
    @GetMapping("/record/list")
    public R recordList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "20") long limit,
                        @RequestParam(required = false) Long taskId,
                        @RequestParam(required = false) String keywords,
                        @RequestParam(required = false) String subjectType,
                        @RequestParam(required = false) String result,
                        @RequestParam(required = false) String handleStatus) {
        LambdaQueryWrapper<InvRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            wrapper.like(InvRecord::getSubjectName, keywords)
                    .or().like(InvRecord::getSubjectCode, keywords)
                    .or().like(InvRecord::getQrCode, keywords)
                    .or().like(InvRecord::getChecker, keywords);
        }
        wrapper.eq(taskId != null, InvRecord::getTaskId, taskId)
                .eq(StringUtils.hasText(subjectType), InvRecord::getSubjectType, subjectType)
                .eq(StringUtils.hasText(result), InvRecord::getResult, result)
                .eq(StringUtils.hasText(handleStatus), InvRecord::getHandleStatus, handleStatus)
                .orderByDesc(InvRecord::getId);
        IPage<InvRecord> result2 = recordService.page(new Page<>(page, limit), wrapper);
        return R.table(result2.getTotal(), result2.getRecords());
    }

    /** 新增或修改明细（手工录入 / 修正实盘数）。 */
    @PostMapping("/record/save")
    public R recordSave(@RequestBody InvRecord record) {
        return R.ok(inventoryService.saveRecord(record).getId());
    }

    /** 删除明细。 */
    @PostMapping("/record/delete")
    public R recordDelete(@RequestParam Long id) {
        inventoryService.deleteRecord(id);
        return R.ok();
    }

    /** 单条差异处理。 */
    @PostMapping("/record/handle")
    public R recordHandle(@RequestParam Long id,
                          @RequestParam String handleStatus,
                          @RequestParam(required = false) String opinion,
                          @RequestParam(required = false) String handler) {
        InvRecord r = inventoryService.handle(id, handleStatus, opinion, handler);
        return r == null ? R.fail("明细不存在") : R.ok(r.getHandleStatus());
    }

    /** 批量处理本任务的全部待处理差异。 */
    @PostMapping("/record/handle-all")
    public R recordHandleAll(@RequestParam Long taskId,
                             @RequestParam String handleStatus,
                             @RequestParam(required = false) String opinion,
                             @RequestParam(required = false) String handler) {
        return R.ok(inventoryService.handleAll(taskId, handleStatus, opinion, handler));
    }

    // ================================================================ 扫码

    /**
     * 扫码解析：只查询不登记，用于扫码页先展示标的再确认数量。
     *
     * @param code   二维码 / 条码 / 业务编码
     * @param taskId 当前任务，用于在返回里带上本任务下已登记的结果
     */
    @GetMapping("/scan")
    public R scan(@RequestParam String code, @RequestParam(required = false) Long taskId) {
        InventoryService.Subject s = inventoryService.resolve(code);
        if (s == null) {
            return R.fail(404, "未找到码值对应的资产或物资：" + code);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("subjectType", s.type);
        data.put("subjectTypeLabel", InventoryStatsService.label(s.type));
        data.put("subjectId", s.id);
        data.put("code", s.code);
        data.put("qrCode", s.qrCode);
        data.put("name", s.name);
        data.put("category", s.category);
        data.put("spec", s.spec);
        data.put("unit", s.unit);
        data.put("location", s.location);
        data.put("deptName", s.deptName);
        data.put("custodian", s.custodian);
        data.put("bookQty", s.bookQty);
        data.put("unitPrice", s.unitPrice);
        if (taskId != null) {
            InvRecord r = recordService.getOne(new LambdaQueryWrapper<InvRecord>()
                    .eq(InvRecord::getTaskId, taskId)
                    .eq(InvRecord::getSubjectType, s.type)
                    .eq(InvRecord::getSubjectId, s.id)
                    .last("LIMIT 1"));
            if (r != null) {
                data.put("recordId", r.getId());
                data.put("actualQty", r.getActualQty());
                data.put("result", r.getResult());
                data.put("checked", r.getActualQty() != null);
            } else {
                data.put("checked", false);
                data.put("inList", false);
            }
        }
        return R.ok(data);
    }

    /**
     * 提交一条盘点结果。
     *
     * <p>PC 扫码枪与 APP 共用。扫码枪会在码值后自动补回车，
     * 因此这里对码值做 trim，并容忍结尾的换行符。
     */
    @PostMapping("/scan/submit")
    public R scanSubmit(@RequestBody Map<String, Object> body) {
        Long taskId = toLong(body.get("taskId"));
        String code = body.get("code") == null ? null : String.valueOf(body.get("code")).trim();
        BigDecimal qty = toDecimal(body.get("actualQty"));
        if (taskId == null || !StringUtils.hasText(code)) {
            return R.fail("任务与码值不能为空");
        }
        String checker = body.get("checker") == null ? null : String.valueOf(body.get("checker"));
        String source = body.get("source") == null ? null : String.valueOf(body.get("source"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        InvRecord r = inventoryService.submit(taskId, code, qty, checker, source, remark);
        return r == null ? R.fail("未找到码值对应的资产或物资：" + code) : R.ok(r.getId());
    }

    /**
     * APP 批量上传盘点结果。
     *
     * <p>离线盘点攒下的一批记录一次性回传；同一标的重复上传只覆盖，不会重复计数。
     */
    @PostMapping("/scan/batch")
    public R scanBatch(@RequestBody Map<String, Object> body) {
        Long taskId = toLong(body.get("taskId"));
        String checker = body.get("checker") == null ? null : String.valueOf(body.get("checker"));
        Object raw = body.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    items.add(m);
                }
            }
        }
        if (taskId == null) {
            return R.fail("任务不能为空");
        }
        int[] res = inventoryService.submitBatch(taskId, items, checker);
        Map<String, Object> data = new HashMap<>();
        data.put("success", res[0]);
        data.put("fail", res[1]);
        return R.ok(data);
    }

    // ================================================================ 统计

    /** 盘点总览。 */
    @GetMapping("/overview")
    public R overview() {
        return R.ok(statsService.overview());
    }

    /** 按标的类型统计差异。 */
    @GetMapping("/type-stats")
    public R typeStats() {
        return R.ok(statsService.typeStats());
    }

    /** 按分类统计差异。 */
    @GetMapping("/category-stats")
    public R categoryStats() {
        return R.ok(statsService.categoryStats());
    }

    /** 任务进度。 */
    @GetMapping("/task-progress")
    public R taskProgress() {
        return R.ok(statsService.taskProgress());
    }

    /** 月度趋势。 */
    @GetMapping("/monthly-trend")
    public R monthlyTrend() {
        return R.ok(statsService.monthlyTrend());
    }

    /** 盘点人员工作量。 */
    @GetMapping("/checker-stats")
    public R checkerStats() {
        return R.ok(statsService.checkerStats());
    }

    /** 账外标的（账面 0、实盘有数）。 */
    @GetMapping("/extra-subjects")
    public R extraSubjects() {
        return R.ok(statsService.extraSubjects());
    }

    // ================================================================ 工具

    /** 取某列的去重非空值，用于下拉筛选。 */
    private List<String> distinct(String table, String column) {
        return statsService.distinctValues(table, column);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
