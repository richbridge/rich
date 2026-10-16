package fan.rich.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fan.rich.entity.Asset;
import fan.rich.entity.AstGoods;
import fan.rich.entity.CanteenMaterial;
import fan.rich.entity.CanteenStock;
import fan.rich.entity.InvRecord;
import fan.rich.entity.InvTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 盘点业务：任务 → 应盘清单 → 扫码登记 → 差异处理 → 调账回写。
 *
 * <h3>三种标的如何统一</h3>
 * 扫码盘点要同时覆盖固定资产、库存物资、食堂物资，这三类台账结构完全不同。
 * 做法是：把「一次盘点中的一个标的」抽象成 {@link Subject} 快照，
 * 通过 {@code subjectType + subjectId} 指回各自的源表，
 * 并把名称、规格、单位、存放地、账面数量<b>冗余</b>进 {@link InvRecord}。
 *
 * <p>冗余是刻意的：盘点从发起到出报告往往跨天数，期间源档案完全可能被改名、
 * 调拨甚至删除。只有冻结快照才能保证「当时盘的是什么」始终可查。
 *
 * <h3>为什么先冻结应盘清单</h3>
 * 任务生成清单时记录 {@code bookQty}（账面数量）并把结果置为「未盘」。
 * 之后即便台账发生出入库，本次盘点仍以冻结值为基准，
 * 这样「已盘 / 未盘」可统计、 APP 可离线下载清单、盘点结论也不会被后续业务改动偷换。
 *
 * <h3>扫码链路（PC 与 APP 共用）</h3>
 * <pre>
 * 二维码/条码 → resolve(code) 定位标的 → submit(taskId, code, 实盘数)
 *             → 清单里没有就补一条（多为账外资产，账面数记 0 → 判为盘盈）
 *             → 算差异、回写任务统计
 * </pre>
 * 因此 {@link #submit} 同时服务 PC 扫码枪录入与将来 APP 的批量上传，接口语义一致。
 *
 * <h3>差异处理</h3>
 * 差异不自动改账。需人工确认后走 {@link #handle}：
 * 处理结论为「已调账」时才回写源台账（资产数量 / 物资结存 / 食堂库存），
 * 保证盘点与账面调整之间有可审计的责任节点。
 */
@Service
public class InventoryService {

    // ------------------------------------------------------------------ 标的类型
    /** 标的类型：固定资产。 */
    public static final String TYPE_ASSET = "ASSET";
    /** 标的类型：库存物资。 */
    public static final String TYPE_GOODS = "GOODS";
    /** 标的类型：食堂物资。 */
    public static final String TYPE_CANTEEN = "CANTEEN";

    // ------------------------------------------------------------------ 盘点结果
    /** 尚未盘点。 */
    public static final String RESULT_UNCHECKED = "未盘";
    /** 账实相符。 */
    public static final String RESULT_NORMAL = "正常";
    /** 实盘大于账面。 */
    public static final String RESULT_PROFIT = "盘盈";
    /** 实盘小于账面。 */
    public static final String RESULT_LOSS = "盘亏";

    // ------------------------------------------------------------------ 处理状态
    /** 待处理。 */
    public static final String HANDLE_TODO = "待处理";
    /** 已确认（差异认可，未调账）。 */
    public static final String HANDLE_CONFIRMED = "已确认";
    /** 已调账（已回写源台账）。 */
    public static final String HANDLE_ADJUSTED = "已调账";
    /** 无需处理。 */
    public static final String HANDLE_NONE = "无需处理";

    // ------------------------------------------------------------------ 任务状态
    /** 任务状态：草稿。 */
    public static final String TASK_DRAFT = "草稿";
    /** 任务状态：进行中。 */
    public static final String TASK_RUNNING = "进行中";
    /** 任务状态：已完成。 */
    public static final String TASK_DONE = "已完成";
    /** 任务状态：已取消。 */
    public static final String TASK_CANCEL = "已取消";

    /** 数据来源：APP 扫码。 */
    public static final String SRC_APP = "APP扫码";
    /** 数据来源：PC 扫码（扫码枪）。 */
    public static final String SRC_PC = "PC扫码";
    /** 数据来源：手工录入。 */
    public static final String SRC_MANUAL = "手工录入";

    private final AssetService assetService;
    private final AstGoodsService goodsService;
    private final CanteenMaterialService materialService;
    private final CanteenStockService stockService;
    private final InvTaskService taskService;
    private final InvRecordService recordService;
    private final JdbcTemplate jdbc;

    public InventoryService(AssetService assetService,
                            AstGoodsService goodsService,
                            CanteenMaterialService materialService,
                            CanteenStockService stockService,
                            InvTaskService taskService,
                            InvRecordService recordService,
                            JdbcTemplate jdbc) {
        this.assetService = assetService;
        this.goodsService = goodsService;
        this.materialService = materialService;
        this.stockService = stockService;
        this.taskService = taskService;
        this.recordService = recordService;
        this.jdbc = jdbc;
    }

    // ================================================================ 标的快照

    /** 一个盘点标的的快照，用于在三类台账之间做统一视图。 */
    public static class Subject {

        /** 标的类型，见 {@link #TYPE_ASSET} 等。 */
        public String type;
        /** 源表主键。 */
        public Long id;
        /** 业务编码。 */
        public String code;
        /** 二维码码值。 */
        public String qrCode;
        /** 名称。 */
        public String name;
        /** 分类。 */
        public String category;
        /** 规格型号。 */
        public String spec;
        /** 计量单位。 */
        public String unit;
        /** 存放地点 / 仓库。 */
        public String location;
        /** 归属部门。 */
        public String deptName;
        /** 保管人。 */
        public String custodian;
        /** 账面数量。 */
        public BigDecimal bookQty = BigDecimal.ZERO;
        /** 单价（资产取原值，物资取加权均价）。 */
        public BigDecimal unitPrice = BigDecimal.ZERO;
    }

    // ================================================================ 任务

    /**
     * 新建盘点任务。
     *
     * <p>单号形如 {@code PD2026-0001}；状态置为「草稿」，等生成清单后才转到「进行中」。
     */
    @Transactional
    public InvTask createTask(InvTask task) {
        if (!StringUtils.hasText(task.getTaskNo())) {
            task.setTaskNo(nextTaskNo());
        }
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus(TASK_DRAFT);
        }
        if (task.getPlanDate() == null) {
            task.setPlanDate(LocalDate.now());
        }
        task.setSubjectCount(0);
        task.setCheckedCount(0);
        task.setNormalCount(0);
        task.setProfitCount(0);
        task.setLossCount(0);
        task.setDiffAmount(BigDecimal.ZERO);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskService.save(task);
        return task;
    }

    /** 更新任务（不重算统计）。 */
    @Transactional
    public InvTask updateTask(InvTask task) {
        task.setUpdateTime(LocalDateTime.now());
        taskService.updateById(task);
        return task;
    }

    /**
     * 按任务范围生成应盘清单。
     *
     * <p>已存在于本任务中的标的会跳过，因此可以反复点「生成清单」来补齐新增资产，
     * 而不会把已盘的实盘数冲掉。
     *
     * @return 本次新增的明细条数
     */
    @Transactional
    public int generateSubjects(Long taskId) {
        InvTask task = taskService.getById(taskId);
        if (task == null || TASK_CANCEL.equals(task.getStatus())) {
            return 0;
        }
        Set<String> exist = new HashSet<>();
        for (InvRecord r : recordService.list(new LambdaQueryWrapper<InvRecord>()
                .eq(InvRecord::getTaskId, taskId))) {
            exist.add(key(r.getSubjectType(), r.getSubjectId()));
        }

        int added = 0;
        for (Subject s : collectSubjects(task)) {
            if (exist.contains(key(s.type, s.id))) {
                continue;
            }
            InvRecord r = new InvRecord();
            r.setTaskId(taskId);
            r.setTaskNo(task.getTaskNo());
            r.setSubjectType(s.type);
            r.setSubjectId(s.id);
            r.setSubjectCode(s.code);
            r.setQrCode(s.qrCode);
            r.setSubjectName(s.name);
            r.setCategory(s.category);
            r.setSpec(s.spec);
            r.setUnit(s.unit);
            r.setLocation(s.location);
            r.setDeptName(s.deptName);
            r.setCustodian(s.custodian);
            r.setBookQty(s.bookQty);
            r.setUnitPrice(s.unitPrice);
            r.setResult(RESULT_UNCHECKED);
            r.setHandleStatus(HANDLE_TODO);
            r.setCreateTime(LocalDateTime.now());
            recordService.save(r);
            added++;
        }
        if (TASK_DRAFT.equals(task.getStatus())) {
            // 必须先落库再刷新统计：refreshTaskStats 内部会重新读取并回写整行，
            // 若这里不先 updateById，状态会被重新读到的旧值覆盖回"草稿"
            task.setStatus(TASK_RUNNING);
            taskService.updateById(task);
        }
        refreshTaskStats(taskId);
        return added;
    }

    /** 按任务类型与范围维度收集标的。 */
    private List<Subject> collectSubjects(InvTask task) {
        List<Subject> list = new ArrayList<>();
        String type = task.getTaskType() == null ? "" : task.getTaskType();
        Set<String> scope = splitScope(task.getScopeValue());
        String dim = task.getScopeType();

        if (type.contains("固定资产") || type.contains("综合")) {
            LambdaQueryWrapper<Asset> w = new LambdaQueryWrapper<>();
            w.ne(Asset::getStatus, "报废");
            if ("分类".equals(dim)) {
                w.in(Asset::getCategory, scope);
            } else if ("部门".equals(dim)) {
                w.in(Asset::getUseDept, scope);
            } else if ("仓库".equals(dim)) {
                w.in(Asset::getLocation, scope);
            }
            for (Asset a : assetService.list(w)) {
                Subject s = new Subject();
                s.type = TYPE_ASSET;
                s.id = a.getId();
                s.code = a.getAssetCode();
                s.qrCode = a.getQrCode();
                s.name = a.getAssetName();
                s.category = a.getCategory();
                s.spec = a.getSpec();
                s.unit = a.getUnit();
                s.location = a.getLocation();
                s.deptName = a.getUseDept();
                s.custodian = a.getCustodian();
                s.bookQty = nvl(a.getQuantity());
                s.unitPrice = nvl(a.getOriginalValue());
                list.add(s);
            }
        }
        if (type.contains("库存物资") || type.contains("综合")) {
            LambdaQueryWrapper<AstGoods> w = new LambdaQueryWrapper<>();
            w.ne(AstGoods::getStatus, "停用");
            if ("分类".equals(dim)) {
                w.in(AstGoods::getCategory, scope);
            } else if ("仓库".equals(dim)) {
                w.in(AstGoods::getWarehouse, scope);
            }
            for (AstGoods g : goodsService.list(w)) {
                Subject s = new Subject();
                s.type = TYPE_GOODS;
                s.id = g.getId();
                s.code = g.getGoodsCode();
                s.qrCode = g.getQrCode();
                s.name = g.getGoodsName();
                s.category = g.getCategory();
                s.spec = g.getSpec();
                s.unit = g.getUnit();
                s.location = g.getWarehouse();
                s.deptName = "物资库";
                s.bookQty = nvl(g.getStockQty());
                s.unitPrice = nvl(g.getAvgPrice());
                list.add(s);
            }
        }
        if (type.contains("食堂") || type.contains("综合")) {
            Map<Long, CanteenStock> stockMap = new HashMap<>();
            for (CanteenStock st : stockService.list()) {
                stockMap.put(st.getMaterialId(), st);
            }
            LambdaQueryWrapper<CanteenMaterial> w = new LambdaQueryWrapper<>();
            if ("分类".equals(dim)) {
                w.in(CanteenMaterial::getCategory, scope);
            }
            for (CanteenMaterial m : materialService.list(w)) {
                CanteenStock st = stockMap.get(m.getId());
                Subject s = new Subject();
                s.type = TYPE_CANTEEN;
                s.id = m.getId();
                s.code = m.getMaterialCode();
                s.qrCode = m.getQrCode();
                s.name = m.getMaterialName();
                s.category = "食堂·" + m.getCategory();
                s.spec = m.getSpec();
                s.unit = m.getUnit();
                s.location = "食堂库房";
                s.deptName = "食堂";
                s.custodian = "刘德强";
                s.bookQty = st == null ? BigDecimal.ZERO : nvl(st.getQuantity());
                s.unitPrice = st == null ? nvl(m.getUnitPrice()) : nvl(st.getAvgPrice());
                list.add(s);
            }
        }
        return list;
    }

    /** 删除任务及其明细。 */
    @Transactional
    public void deleteTask(Long id) {
        recordService.remove(new LambdaQueryWrapper<InvRecord>().eq(InvRecord::getTaskId, id));
        taskService.removeById(id);
    }

    // ================================================================ 扫码

    /**
     * 解析二维码 / 条码内容，定位标的。
     *
     * <p>依次在固定资产、库存物资、食堂物料中按 {@code qrCode} 匹配；
     * 若都未命中，再按业务编码（资产编号 / 物资编码 / 物料编码）兜底匹配一次——
     * 早期没贴码的资产也能靠手输编号参与盘点。
     *
     * @return 标的快照；未找到返回 null
     */
    public Subject resolve(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String c = code.trim();

        Asset a = assetService.getOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getQrCode, c).last("LIMIT 1"));
        if (a == null) {
            a = assetService.getOne(new LambdaQueryWrapper<Asset>()
                    .eq(Asset::getAssetCode, c).last("LIMIT 1"));
        }
        if (a != null) {
            Subject s = new Subject();
            s.type = TYPE_ASSET;
            s.id = a.getId();
            s.code = a.getAssetCode();
            s.qrCode = a.getQrCode();
            s.name = a.getAssetName();
            s.category = a.getCategory();
            s.spec = a.getSpec();
            s.unit = a.getUnit();
            s.location = a.getLocation();
            s.deptName = a.getUseDept();
            s.custodian = a.getCustodian();
            s.bookQty = nvl(a.getQuantity());
            s.unitPrice = nvl(a.getOriginalValue());
            return s;
        }

        AstGoods g = goodsService.getOne(new LambdaQueryWrapper<AstGoods>()
                .eq(AstGoods::getQrCode, c).last("LIMIT 1"));
        if (g == null) {
            g = goodsService.getOne(new LambdaQueryWrapper<AstGoods>()
                    .eq(AstGoods::getGoodsCode, c).last("LIMIT 1"));
        }
        if (g != null) {
            Subject s = new Subject();
            s.type = TYPE_GOODS;
            s.id = g.getId();
            s.code = g.getGoodsCode();
            s.qrCode = g.getQrCode();
            s.name = g.getGoodsName();
            s.category = g.getCategory();
            s.spec = g.getSpec();
            s.unit = g.getUnit();
            s.location = g.getWarehouse();
            s.deptName = "物资库";
            s.bookQty = nvl(g.getStockQty());
            s.unitPrice = nvl(g.getAvgPrice());
            return s;
        }

        CanteenMaterial m = materialService.getOne(new LambdaQueryWrapper<CanteenMaterial>()
                .eq(CanteenMaterial::getQrCode, c).last("LIMIT 1"));
        if (m == null) {
            m = materialService.getOne(new LambdaQueryWrapper<CanteenMaterial>()
                    .eq(CanteenMaterial::getMaterialCode, c).last("LIMIT 1"));
        }
        if (m != null) {
            CanteenStock st = stockService.getOne(new LambdaQueryWrapper<CanteenStock>()
                    .eq(CanteenStock::getMaterialId, m.getId()).last("LIMIT 1"));
            Subject s = new Subject();
            s.type = TYPE_CANTEEN;
            s.id = m.getId();
            s.code = m.getMaterialCode();
            s.qrCode = m.getQrCode();
            s.name = m.getMaterialName();
            s.category = "食堂·" + m.getCategory();
            s.spec = m.getSpec();
            s.unit = m.getUnit();
            s.location = "食堂库房";
            s.deptName = "食堂";
            s.bookQty = st == null ? BigDecimal.ZERO : nvl(st.getQuantity());
            s.unitPrice = st == null ? nvl(m.getUnitPrice()) : nvl(st.getAvgPrice());
            return s;
        }
        return null;
    }

    /**
     * 提交一条盘点结果（扫码 / 手输共用）。
     *
     * <p>标的若不在本任务清单中，会<b>补一条</b>记录再登记：
     * 现实中扫到的东西常常不在清单里（新增资产、账外物资），
     * 此时账面数记 0、结果判为「盘盈」，交由人工确认，而不是直接拒绝——
     * 否则现场盘点会因为"扫不出来"而中断。
     *
     * @param taskId    任务ID
     * @param code      二维码 / 业务编码
     * @param actualQty 实盘数量；null 表示仅查询不登记
     * @param checker   盘点人
     * @param source    数据来源，见 {@link #SRC_APP} 等
     * @return 登记后的明细；码值无法解析时返回 null
     */
    @Transactional
    public InvRecord submit(Long taskId, String code, BigDecimal actualQty,
                            String checker, String source, String remark) {
        InvTask task = taskService.getById(taskId);
        if (task == null) {
            return null;
        }
        Subject s = resolve(code);
        if (s == null) {
            return null;
        }
        InvRecord r = recordService.getOne(new LambdaQueryWrapper<InvRecord>()
                .eq(InvRecord::getTaskId, taskId)
                .eq(InvRecord::getSubjectType, s.type)
                .eq(InvRecord::getSubjectId, s.id)
                .last("LIMIT 1"));
        if (r == null) {
            r = new InvRecord();
            r.setTaskId(taskId);
            r.setTaskNo(task.getTaskNo());
            r.setSubjectType(s.type);
            r.setSubjectId(s.id);
            r.setSubjectCode(s.code);
            r.setQrCode(s.qrCode);
            r.setSubjectName(s.name);
            r.setCategory(s.category);
            r.setSpec(s.spec);
            r.setUnit(s.unit);
            r.setLocation(s.location);
            r.setDeptName(s.deptName);
            r.setCustodian(s.custodian);
            r.setBookQty(s.bookQty);
            r.setUnitPrice(s.unitPrice);
            r.setHandleStatus(HANDLE_TODO);
            r.setCreateTime(LocalDateTime.now());
            recordService.save(r);
        }
        if (actualQty == null) {
            return r;
        }
        r.setActualQty(actualQty);
        fillResult(r);
        r.setCheckSource(StringUtils.hasText(source) ? source : SRC_MANUAL);
        r.setChecker(checker);
        r.setCheckTime(LocalDateTime.now());
        if (StringUtils.hasText(remark)) {
            r.setRemark(remark);
        }
        recordService.updateById(r);
        if (TASK_DRAFT.equals(task.getStatus())) {
            task.setStatus(TASK_RUNNING);
            taskService.updateById(task);
        }
        refreshTaskStats(taskId);
        return r;
    }

    /**
     * APP 批量上传盘点结果。
     *
     * <p>APP 常年在弱网 / 离线下工作，因此这里按「标的」逐条 upsert，
     * 重复上传同一批数据只会覆盖实盘结果，不会翻倍记账。
     *
     * @param items 每项含 code、actualQty、remark（可选）
     * @return [成功条数, 失败条数]
     */
    @Transactional
    public int[] submitBatch(Long taskId, List<Map<String, Object>> items, String checker) {
        int ok = 0;
        int fail = 0;
        if (items == null) {
            return new int[]{0, 0};
        }
        for (Map<String, Object> item : items) {
            Object code = item.get("code");
            BigDecimal qty = toDecimal(item.get("actualQty"));
            if (code == null || qty == null) {
                fail++;
                continue;
            }
            String remark = item.get("remark") == null ? null : String.valueOf(item.get("remark"));
            try {
                if (submit(taskId, String.valueOf(code), qty, checker, SRC_APP, remark) == null) {
                    fail++;
                } else {
                    ok++;
                }
            } catch (Exception e) {
                fail++;
            }
        }
        return new int[]{ok, fail};
    }

    /**
     * 按明细ID登记实盘数（标的已在清单中时的登记入口）。
     *
     * <p>与 {@link #submit} 的区别：那一个是「按码值」定位标的（可能落在清单外），
     * 这一个直接落到指定明细行，用于批量导入与演示数据生成。
     */
    @Transactional
    public InvRecord submitRecord(Long taskId, Long recordId, BigDecimal actualQty, String checker) {
        InvRecord input = new InvRecord();
        input.setId(recordId);
        input.setTaskId(taskId);
        input.setActualQty(actualQty);
        input.setChecker(checker);
        input.setCheckSource(SRC_APP);
        return saveRecord(input);
    }

    /**
     * 保存 / 修正单条明细（PC 手工录入与编辑）。
     *
     * <p>传入的对象通常只带 {@code id} 与实盘数几个字段（页面只改这几项），
     * 因此这里先取回库里的完整记录再<b>局部覆盖</b>——
     * 否则账面数量、单价这些不传的字段会被 null 冲掉，差异就全算错了。
     */
    @Transactional
    public InvRecord saveRecord(InvRecord input) {
        InvRecord r = input;
        if (input.getId() != null) {
            InvRecord exist = recordService.getById(input.getId());
            if (exist != null) {
                r = exist;
                if (input.getActualQty() != null) {
                    r.setActualQty(input.getActualQty());
                }
                if (StringUtils.hasText(input.getChecker())) {
                    r.setChecker(input.getChecker());
                }
                if (StringUtils.hasText(input.getCheckSource())) {
                    r.setCheckSource(input.getCheckSource());
                }
                if (input.getRemark() != null) {
                    r.setRemark(input.getRemark());
                }
                if (StringUtils.hasText(input.getHandleStatus())) {
                    r.setHandleStatus(input.getHandleStatus());
                }
            }
        }
        if (r.getActualQty() != null) {
            fillResult(r);
            if (r.getCheckTime() == null) {
                r.setCheckTime(LocalDateTime.now());
            }
        } else {
            r.setResult(RESULT_UNCHECKED);
            r.setDiffQty(BigDecimal.ZERO);
            r.setDiffAmount(BigDecimal.ZERO);
        }
        if (r.getCreateTime() == null) {
            r.setCreateTime(LocalDateTime.now());
        }
        recordService.saveOrUpdate(r);
        if (r.getTaskId() != null) {
            refreshTaskStats(r.getTaskId());
        }
        return r;
    }

    /** 删除明细后重算任务统计。 */
    @Transactional
    public void deleteRecord(Long id) {
        InvRecord r = recordService.getById(id);
        if (r == null) {
            return;
        }
        Long taskId = r.getTaskId();
        recordService.removeById(id);
        if (taskId != null) {
            refreshTaskStats(taskId);
        }
    }

    // ================================================================ 差异处理

    /**
     * 差异处理。
     *
     * <p>结论为 {@link #HANDLE_ADJUSTED}（已调账）时才回写源台账；
     * 其余结论只记录意见，不动账面——盘点差异往往牵涉责任认定，
     * 账面调整必须是显式的、可追溯的一步。
     */
    @Transactional
    public InvRecord handle(Long recordId, String handleStatus, String opinion, String handler) {
        InvRecord r = recordService.getById(recordId);
        if (r == null) {
            return null;
        }
        r.setHandleStatus(handleStatus);
        r.setHandleOpinion(opinion);
        r.setHandler(handler);
        r.setHandleTime(LocalDateTime.now());
        if (HANDLE_ADJUSTED.equals(handleStatus) && r.getActualQty() != null) {
            applyDiff(r);
        }
        recordService.updateById(r);
        if (r.getTaskId() != null) {
            refreshTaskStats(r.getTaskId());
        }
        return r;
    }

    /** 批量处理同一任务的所有未处理差异。 */
    @Transactional
    public int handleAll(Long taskId, String handleStatus, String opinion, String handler) {
        List<InvRecord> list = recordService.list(new LambdaQueryWrapper<InvRecord>()
                .eq(InvRecord::getTaskId, taskId)
                .eq(InvRecord::getHandleStatus, HANDLE_TODO)
                .ne(InvRecord::getResult, RESULT_UNCHECKED)
                .ne(InvRecord::getResult, RESULT_NORMAL));
        int n = 0;
        for (InvRecord r : list) {
            handle(r.getId(), handleStatus, opinion, handler);
            n++;
        }
        return n;
    }

    /**
     * 把实盘数回写到源台账。
     *
     * <ul>
     *   <li>固定资产：更新账面数量</li>
     *   <li>库存物资：更新结存数量与金额（金额 = 实盘数 × 加权均价）</li>
     *   <li>食堂物资：更新 {@code canteen_stock} 的结存数量与金额</li>
     * </ul>
     */
    private void applyDiff(InvRecord r) {
        BigDecimal actual = nvl(r.getActualQty());
        if (TYPE_ASSET.equals(r.getSubjectType())) {
            Asset a = assetService.getById(r.getSubjectId());
            if (a != null) {
                a.setQuantity(actual);
                assetService.updateById(a);
            }
        } else if (TYPE_GOODS.equals(r.getSubjectType())) {
            AstGoods g = goodsService.getById(r.getSubjectId());
            if (g != null) {
                BigDecimal price = nvl(g.getAvgPrice());
                g.setStockQty(actual);
                g.setTotalAmount(actual.multiply(price).setScale(2, RoundingMode.HALF_UP));
                goodsService.updateById(g);
            }
        } else if (TYPE_CANTEEN.equals(r.getSubjectType())) {
            CanteenStock st = stockService.getOne(new LambdaQueryWrapper<CanteenStock>()
                    .eq(CanteenStock::getMaterialId, r.getSubjectId()).last("LIMIT 1"));
            if (st != null) {
                BigDecimal price = nvl(st.getAvgPrice());
                st.setQuantity(actual);
                st.setTotalAmount(actual.multiply(price).setScale(2, RoundingMode.HALF_UP));
                st.setUpdateTime(LocalDateTime.now());
                stockService.updateById(st);
            }
        }
    }

    // ================================================================ 二维码码值

    /**
     * 为尚未赋码的标的批量生成二维码码值。
     *
     * <p>码值规则：{@code AST-资产编号} / {@code GDS-物资编码} / {@code CTN-物料编码}。
     * 用业务编号派生而不是随机串，是为了让现场人员<b>看码就能认出是什么</b>，
     * 二维码扫不出来时还能照着码面上的编号手工输入。
     *
     * <p>已有码值的行不会重算——贴出去的牌子不会因为一次误点就作废。
     *
     * @param type 标的类型；为空表示三类全生成
     * @return 本次生成的条数
     */
    @Transactional
    public int generateQrCodes(String type) {
        int n = 0;
        if (type == null || type.isEmpty() || TYPE_ASSET.equals(type)) {
            for (Asset a : assetService.list()) {
                if (!StringUtils.hasText(a.getQrCode())) {
                    a.setQrCode("AST-" + defaultStr(a.getAssetCode(), a.getId()));
                    assetService.updateById(a);
                    n++;
                }
            }
        }
        if (type == null || type.isEmpty() || TYPE_GOODS.equals(type)) {
            for (AstGoods g : goodsService.list()) {
                if (!StringUtils.hasText(g.getQrCode())) {
                    g.setQrCode("GDS-" + defaultStr(g.getGoodsCode(), g.getId()));
                    goodsService.updateById(g);
                    n++;
                }
            }
        }
        if (type == null || type.isEmpty() || TYPE_CANTEEN.equals(type)) {
            for (CanteenMaterial m : materialService.list()) {
                if (!StringUtils.hasText(m.getQrCode())) {
                    m.setQrCode("CTN-" + defaultStr(m.getMaterialCode(), m.getId()));
                    materialService.updateById(m);
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * 标签打印数据：某类标的的码值、名称、规格、位置。
     *
     * @param type     标的类型
     * @param category 分类过滤，可为空
     */
    public List<Map<String, Object>> labelData(String type, String category) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (TYPE_ASSET.equals(type)) {
            LambdaQueryWrapper<Asset> w = new LambdaQueryWrapper<Asset>()
                    .eq(StringUtils.hasText(category), Asset::getCategory, category)
                    .ne(Asset::getStatus, "报废")
                    .orderByAsc(Asset::getAssetCode);
            for (Asset a : assetService.list(w)) {
                Map<String, Object> row = new HashMap<>();
                row.put("qrCode", a.getQrCode());
                row.put("code", a.getAssetCode());
                row.put("name", a.getAssetName());
                row.put("spec", a.getSpec());
                row.put("location", a.getLocation());
                row.put("dept", a.getUseDept());
                row.put("custodian", a.getCustodian());
                rows.add(row);
            }
        } else if (TYPE_GOODS.equals(type)) {
            LambdaQueryWrapper<AstGoods> w = new LambdaQueryWrapper<AstGoods>()
                    .eq(StringUtils.hasText(category), AstGoods::getCategory, category)
                    .ne(AstGoods::getStatus, "停用")
                    .orderByAsc(AstGoods::getGoodsCode);
            for (AstGoods g : goodsService.list(w)) {
                Map<String, Object> row = new HashMap<>();
                row.put("qrCode", g.getQrCode());
                row.put("code", g.getGoodsCode());
                row.put("name", g.getGoodsName());
                row.put("spec", g.getSpec());
                row.put("location", g.getWarehouse());
                row.put("dept", "物资库");
                row.put("custodian", "");
                rows.add(row);
            }
        } else if (TYPE_CANTEEN.equals(type)) {
            LambdaQueryWrapper<CanteenMaterial> w = new LambdaQueryWrapper<CanteenMaterial>()
                    .eq(StringUtils.hasText(category), CanteenMaterial::getCategory, category)
                    .orderByAsc(CanteenMaterial::getMaterialCode);
            for (CanteenMaterial m : materialService.list(w)) {
                Map<String, Object> row = new HashMap<>();
                row.put("qrCode", m.getQrCode());
                row.put("code", m.getMaterialCode());
                row.put("name", m.getMaterialName());
                row.put("spec", m.getSpec());
                row.put("location", "食堂库房");
                row.put("dept", "食堂");
                row.put("custodian", "");
                rows.add(row);
            }
        }
        return rows;
    }

    private static String defaultStr(String value, Long id) {
        return StringUtils.hasText(value) ? value : String.valueOf(id);
    }

    // ================================================================ 任务统计

    /**
     * 重算任务的应盘 / 已盘 / 盈亏计数与差异净额。
     *
     * <p>任何影响明细的写操作后都要调一次，保证列表页与看板口径一致。
     */
    @Transactional
    public void refreshTaskStats(Long taskId) {
        InvTask task = taskService.getById(taskId);
        if (task == null) {
            return;
        }
        Map<String, Object> stat = jdbc.queryForMap(
                "SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN actual_qty IS NOT NULL THEN 1 ELSE 0 END) AS checked,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS normal,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS profit,"
                        + " SUM(CASE WHEN result=? THEN 1 ELSE 0 END) AS loss,"
                        + " IFNULL(SUM(diff_amount),0) AS diff"
                        + " FROM inv_record WHERE task_id=?",
                RESULT_NORMAL, RESULT_PROFIT, RESULT_LOSS, taskId);
        task.setSubjectCount(intVal(stat.get("total")));
        task.setCheckedCount(intVal(stat.get("checked")));
        task.setNormalCount(intVal(stat.get("normal")));
        task.setProfitCount(intVal(stat.get("profit")));
        task.setLossCount(intVal(stat.get("loss")));
        task.setDiffAmount(decVal(stat.get("diff")));
        task.setUpdateTime(LocalDateTime.now());
        taskService.updateById(task);
    }

    /** 任务完成：已盘标的全部处理完毕后才允许完成。 */
    @Transactional
    public InvTask finishTask(Long taskId) {
        InvTask task = taskService.getById(taskId);
        if (task == null) {
            return null;
        }
        refreshTaskStats(taskId);
        InvTask fresh = taskService.getById(taskId);
        fresh.setStatus(TASK_DONE);
        fresh.setUpdateTime(LocalDateTime.now());
        taskService.updateById(fresh);
        return fresh;
    }

    // ================================================================ 工具

    /** 由实盘数推导差异与结果。 */
    private static void fillResult(InvRecord r) {
        BigDecimal book = nvl(r.getBookQty());
        BigDecimal actual = nvl(r.getActualQty());
        BigDecimal diff = actual.subtract(book);
        r.setDiffQty(diff);
        r.setDiffAmount(diff.multiply(nvl(r.getUnitPrice())).setScale(2, RoundingMode.HALF_UP));
        int cmp = diff.compareTo(BigDecimal.ZERO);
        r.setResult(cmp == 0 ? RESULT_NORMAL : (cmp > 0 ? RESULT_PROFIT : RESULT_LOSS));
    }

    /** 生成任务单号：PD + 年份 + 四位流水。 */
    private String nextTaskNo() {
        long n = taskService.count();
        String prefix = "PD" + LocalDate.now().getYear() + "-";
        long seq = n + 1;
        String no;
        do {
            no = prefix + String.format("%04d", seq);
            seq++;
        } while (taskService.count(new LambdaQueryWrapper<InvTask>().eq(InvTask::getTaskNo, no)) > 0);
        return no;
    }

    private static String key(String type, Long id) {
        return type + "#" + id;
    }

    /** 范围值按逗号 / 顿号拆分。 */
    private static Set<String> splitScope(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (!StringUtils.hasText(value)) {
            return set;
        }
        for (String part : value.split("[,，、;；\\s]+")) {
            if (StringUtils.hasText(part)) {
                set.add(part.trim());
            }
        }
        return set;
    }

    /** 空值安全：null → 0。 */
    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal decVal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static int intVal(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
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
