package fan.rich.controller;

import fan.rich.dto.MenuNode;
import fan.rich.entity.SysUser;
import fan.rich.service.RbacService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 页面视图路由：主框架与各个业务页面（均由 layuiAdmin iframe 方式加载）。
 */
@Controller
public class IndexController {

    private final RbacService rbacService;

    public IndexController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 主框架（左侧菜单 + 标签页 + iframe 容器）。
     *
     * <p>左侧菜单不再写死在模板里，而是由后端按当前用户的权限集合裁剪后下发，
     * 没有权限的模块既看不到入口、也调不了对应的接口。
     */
    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        Object attribute = session.getAttribute("loginUser");
        Long userId = null;
        if (attribute instanceof SysUser user) {
            model.addAttribute("nickname",
                    StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            userId = user.getId();
        } else {
            model.addAttribute("nickname", "未登录");
        }
        List<MenuNode> menus = userId == null ? List.of() : rbacService.menuTree(userId);
        model.addAttribute("menus", menus);
        model.addAttribute("superAdmin", userId != null && rbacService.isSuperAdmin(userId));
        // 首屏标签：取第一个可见菜单，避免授权后落到无权限页面
        model.addAttribute("homePath", menus.isEmpty() ? "/403" : menus.get(0).getChildren().get(0).getPath());
        return "index";
    }

    /** 无权限提示页。 */
    @GetMapping("/403")
    public String forbidden(Model model) {
        model.addAttribute("nickname", "无权限访问");
        return "system/403";
    }

    /**
     * 综合经营数据大屏（首屏）。
     *
     * <p>汇聚销售、价格、盘点、档案、培训、工资、食堂、审计八个模块的核心指标。
     * 只看单一业务的画面请走各模块自己的看板（如 {@code /sale/dashboard}）。
     */
    @GetMapping("/home")
    public String home() {
        return "overview/dashboard";
    }

    /** 综合经营大屏的显式入口（与 {@code /home} 同一张页面）。 */
    @GetMapping("/overview/dashboard")
    public String overviewDashboard() {
        return "overview/dashboard";
    }

    /** 销售看板（按发货品种 / 销售商的经营概览）。 */
    @GetMapping("/sale/dashboard")
    public String saleDashboard() {
        return "sale/dashboard";
    }

    /** 发货台账：浮选精矿与合质金发货明细。 */
    @GetMapping("/sale/ledger")
    public String ledger() {
        return "sale/ledger";
    }

    /**
     * 销售订单（旧入口，已由「发货台账」承接）。
     *
     * <p>台账化改造后销售订单并入发货台账。这里直接渲染发货台账视图（不走 302），
     * 兼容浏览器缓存/本地记录里残留的旧标签，不会 404 也不会跳错页。
     */
    @GetMapping({"/sale/order", "/sale/orders"})
    public String orderAlias() {
        return "sale/ledger";
    }

    /** 点价管理。 */
    @GetMapping("/sale/pricing")
    public String pricing() {
        return "sale/pricing";
    }

    /** 提金结算与开票。 */
    @GetMapping("/sale/settlement")
    public String settlement() {
        return "sale/settlement";
    }

    /** 客户管理（销售商）。 */
    @GetMapping("/sale/customer")
    public String customer() {
        return "sale/customer";
    }

    /** 产品管理（发货品种）。 */
    @GetMapping("/sale/product")
    public String product() {
        return "sale/product";
    }

    /** 销售统计报表。 */
    @GetMapping("/sale/report")
    public String report() {
        return "sale/report";
    }

    // ------------------------------------------------------------------ 价格采集

    /** 价格总览（多源价格曲线与最新报价）。 */
    @GetMapping("/price/dashboard")
    public String priceDashboard() {
        return "price/dashboard";
    }

    /** 现货行情列表（SGE / SMM / CJ）。 */
    @GetMapping("/price/spot")
    public String priceSpot() {
        return "price/spot";
    }

    /** 期货行情列表（SHFE）。 */
    @GetMapping("/price/futures")
    public String priceFutures() {
        return "price/futures";
    }

    /** 品种字典与采集任务（手动触发采集、查看日志）。 */
    @GetMapping("/price/task")
    public String priceTask() {
        return "price/task";
    }

    // ------------------------------------------------------------------ 审计与检查

    /** 审计整改总览（KPI、分布、趋势、提醒）。 */
    @GetMapping("/audit/dashboard")
    public String auditDashboard() {
        return "audit/dashboard";
    }

    /** 审计项目管理。 */
    @GetMapping("/audit/project")
    public String auditProject() {
        return "audit/project";
    }

    /** 审计问题整改台账。 */
    @GetMapping("/audit/issue")
    public String auditIssue() {
        return "audit/issue";
    }

    /** 被审计单位。 */
    @GetMapping("/audit/unit")
    public String auditUnit() {
        return "audit/unit";
    }

    /** 检查任务（跟踪检查 / 专项检查 / 日常检查 / 整改核查）。 */
    @GetMapping("/audit/inspect")
    public String auditInspect() {
        return "audit/inspect";
    }

    // ------------------------------------------------------------------ 培训

    /** 培训总览（KPI、分布、趋势、提醒）。 */
    @GetMapping("/train/dashboard")
    public String trainDashboard() {
        return "train/dashboard";
    }

    /** 课程管理（课件）。 */
    @GetMapping("/train/course")
    public String trainCourse() {
        return "train/course";
    }

    /** 培训计划（培训班）。 */
    @GetMapping("/train/plan")
    public String trainPlan() {
        return "train/plan";
    }

    /** 参训记录（报名 / 签到 / 成绩）。 */
    @GetMapping("/train/enroll")
    public String trainEnroll() {
        return "train/enroll";
    }

    /** 考试管理（试卷与成绩）。 */
    @GetMapping("/train/exam")
    public String trainExam() {
        return "train/exam";
    }

    // ------------------------------------------------------------------ 工资

    /** 工资总览（下拨 / 上报 / 发放 KPI、趋势、部门分布）。 */
    @GetMapping("/salary/dashboard")
    public String salaryDashboard() {
        return "salary/dashboard";
    }

    /** 工资下拨（上级单位 → 本公司）。 */
    @GetMapping("/salary/alloc")
    public String salaryAlloc() {
        return "salary/alloc";
    }

    /** 工资上报（部门 → 财务科，公司 → 上级）。 */
    @GetMapping("/salary/report")
    public String salaryReport() {
        return "salary/report";
    }

    /** 工资明细（个人工资表）。 */
    @GetMapping("/salary/detail")
    public String salaryDetail() {
        return "salary/detail";
    }

    /** 工资核算部门。 */
    @GetMapping("/salary/dept")
    public String salaryDept() {
        return "salary/dept";
    }

    // ------------------------------------------------------------------ 食堂

    /** 食堂经营总览（KPI、收支趋势、库存预警）。 */
    @GetMapping("/canteen/dashboard")
    public String canteenDashboard() {
        return "canteen/dashboard";
    }

    /** 食材物料档案。 */
    @GetMapping("/canteen/material")
    public String canteenMaterial() {
        return "canteen/material";
    }

    /** 供应商档案。 */
    @GetMapping("/canteen/supplier")
    public String canteenSupplier() {
        return "canteen/supplier";
    }

    /** 采购入库（含明细）。 */
    @GetMapping("/canteen/purchase")
    public String canteenPurchase() {
        return "canteen/purchase";
    }

    /** 领用出库（含明细）。 */
    @GetMapping("/canteen/outbound")
    public String canteenOutbound() {
        return "canteen/outbound";
    }

    /** 库存结存与盘点。 */
    @GetMapping("/canteen/stock")
    public String canteenStock() {
        return "canteen/stock";
    }

    /** 就餐收入日报。 */
    @GetMapping("/canteen/sale")
    public String canteenSale() {
        return "canteen/sale";
    }

    /** 食堂财务（收支台账 / 费用支出 / 饭卡 / 月度经营表）。 */
    @GetMapping("/canteen/finance")
    public String canteenFinance() {
        return "canteen/finance";
    }

    // ================================================================ 盘点管理

    /** 盘点总览。 */
    @GetMapping("/inventory/dashboard")
    public String inventoryDashboard() {
        return "inventory/dashboard";
    }

    /** 固定资产台账。 */
    @GetMapping("/inventory/asset")
    public String inventoryAsset() {
        return "inventory/asset";
    }

    /** 库存物资。 */
    @GetMapping("/inventory/goods")
    public String inventoryGoods() {
        return "inventory/goods";
    }

    /** 盘点任务。 */
    @GetMapping("/inventory/task")
    public String inventoryTask() {
        return "inventory/task";
    }

    /** 盘点明细。 */
    @GetMapping("/inventory/record")
    public String inventoryRecord() {
        return "inventory/record";
    }

    /** 扫码盘点。 */
    @GetMapping("/inventory/scan")
    public String inventoryScan() {
        return "inventory/scan";
    }

    /** 二维码标签打印。 */
    @GetMapping("/inventory/label")
    public String inventoryLabel() {
        return "inventory/label";
    }

    // ================================================================ 档案管理

    /** 档案总览（KPI、门类与年度分布、借阅趋势、到期提醒）。 */
    @GetMapping("/archive/dashboard")
    public String archiveDashboard() {
        return "archive/dashboard";
    }

    /** 案卷管理（含卷内文件）。 */
    @GetMapping("/archive/volume")
    public String archiveVolume() {
        return "archive/volume";
    }

    /** 卷内文件检索。 */
    @GetMapping("/archive/file")
    public String archiveFile() {
        return "archive/file";
    }

    /** 门类与存放位置维护（档案模块的字典表）。 */
    @GetMapping("/archive/basic")
    public String archiveBasic() {
        return "archive/basic";
    }

    /** 借阅管理（登记 / 归还 / 续借 / 报失）。 */
    @GetMapping("/archive/borrow")
    public String archiveBorrow() {
        return "archive/borrow";
    }

    /** 销毁清册（申请 → 审批 → 执行）。 */
    @GetMapping("/archive/destroy")
    public String archiveDestroy() {
        return "archive/destroy";
    }

    // ================================================================ 系统管理

    /** 用户管理（账号、角色分配、启停）。 */
    @GetMapping("/system/user")
    public String systemUser() {
        return "system/user";
    }

    /** 角色权限（角色 CRUD + 资源勾选授权）。 */
    @GetMapping("/system/role")
    public String systemRole() {
        return "system/role";
    }

    /** 权限资源（菜单与接口路径模式）。 */
    @GetMapping("/system/permission")
    public String systemPermission() {
        return "system/permission";
    }

    /** 对外开放接口文档。 */
    @GetMapping("/system/api")
    public String systemApi() {
        return "system/api";
    }
}
