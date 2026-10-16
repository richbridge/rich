package fan.rich.config;

import java.util.List;

/**
 * 权限资源目录。
 *
 * <p>集中登记系统里所有「受控资源」，启动时同步进 {@code sys_permission} 表，
 * 作为角色授权的候选集。运行期的路径鉴权则从表里读取（见 {@link RoutePermissionGuard}），
 * 因此管理员在页面上调整资源路径能够立刻生效，不需要改代码。
 *
 * <p>四种资源类型：
 * <ul>
 *   <li>{@code MODULE}：一级模块，对应左侧菜单的分组</li>
 *   <li>{@code MENU}：二级页面菜单，携带具体 URL</li>
 *   <li>{@code API}：接口路径模式（支持 {@code /xx/**} 后缀通配），用于放行/拦截数据接口</li>
 *   <li>{@code ALIAS}：菜单的兼容别名路径，指向同一个权限码，不在菜单树里展示</li>
 * </ul>
 */
public final class PermissionCatalog {

    /** 资源类型：一级模块。 */
    public static final String TYPE_MODULE = "MODULE";
    /** 资源类型：二级页面菜单。 */
    public static final String TYPE_MENU = "MENU";
    /** 资源类型：接口路径模式。 */
    public static final String TYPE_API = "API";
    /** 资源类型：菜单别名路径（不在菜单树显示）。 */
    public static final String TYPE_ALIAS = "ALIAS";

    private PermissionCatalog() {
    }

    /** 一级模块定义：编码、名称、图标、排序。 */
    public record Module(String code, String name, String icon, int sort) {
    }

    /** 二级菜单定义：编码、名称、路径、图标、额外别名路径。 */
    public record Menu(String moduleCode, String code, String name, String path, String icon, List<String> alias) {
    }

    /** 接口模式定义：URL 模式、所需权限码、说明。 */
    public record ApiPattern(String pattern, String permCode, String remark) {
    }

    /** 受控模块（顺序即菜单顺序）。 */
    public static final List<Module> MODULES = List.of(
            new Module("overview", "可视化", "layui-icon-home", 1),
            new Module("sale", "销售管理", "layui-icon-cart", 2),
            new Module("price", "价格采集", "layui-icon-diamond", 3),
            new Module("audit", "审计检查", "layui-icon-auz", 4),
            new Module("train", "培训管理", "layui-icon-read", 5),
            new Module("salary", "工资管理", "layui-icon-rmb", 6),
            new Module("canteen", "食堂管理", "layui-icon-face-smile", 7),
            new Module("inventory", "盘点管理", "layui-icon-survey", 8),
            new Module("archive", "档案管理", "layui-icon-folder", 9),
            new Module("system", "系统管理", "layui-icon-set", 10)
    );

    /** 受控菜单（隶属 {@link #MODULES} 中的模块）。 */
    public static final List<Menu> MENUS = List.of(
            // 可视化
            new Menu("overview", "overview:dashboard", "数据大屏", "/home", "layui-icon-chart-screen",
                    List.of("/overview/dashboard")),
            // 销售管理
            new Menu("sale", "sale:dashboard", "销售看板", "/sale/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("sale", "sale:ledger", "发货台账", "/sale/ledger", "layui-icon-form",
                    List.of("/sale/order", "/sale/orders")),
            new Menu("sale", "sale:pricing", "点价管理", "/sale/pricing", "layui-icon-diamond", List.of()),
            new Menu("sale", "sale:settlement", "提金结算", "/sale/settlement", "layui-icon-rmb", List.of()),
            new Menu("sale", "sale:report", "销售统计", "/sale/report", "layui-icon-chart", List.of()),
            new Menu("sale", "sale:customer", "客户", "/sale/customer", "layui-icon-user", List.of()),
            new Menu("sale", "sale:product", "发货品种", "/sale/product", "layui-icon-component", List.of()),
            // 价格采集
            new Menu("price", "price:dashboard", "价格看板", "/price/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("price", "price:spot", "现货行情", "/price/spot", "layui-icon-list", List.of()),
            new Menu("price", "price:futures", "期货行情", "/price/futures", "layui-icon-template-1", List.of()),
            new Menu("price", "price:task", "采集任务", "/price/task", "layui-icon-set", List.of()),
            // 审计检查
            new Menu("audit", "audit:dashboard", "整改总览", "/audit/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("audit", "audit:project", "审计项目", "/audit/project", "layui-icon-template-1", List.of()),
            new Menu("audit", "audit:issue", "问题整改", "/audit/issue", "layui-icon-form", List.of()),
            new Menu("audit", "audit:inspect", "检查任务", "/audit/inspect", "layui-icon-list", List.of()),
            new Menu("audit", "audit:unit", "被审计单位", "/audit/unit", "layui-icon-user", List.of()),
            // 培训管理
            new Menu("train", "train:dashboard", "培训总览", "/train/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("train", "train:course", "课程管理", "/train/course", "layui-icon-template-1", List.of()),
            new Menu("train", "train:plan", "培训计划", "/train/plan", "layui-icon-list", List.of()),
            new Menu("train", "train:enroll", "参训记录", "/train/enroll", "layui-icon-group", List.of()),
            new Menu("train", "train:exam", "考试管理", "/train/exam", "layui-icon-edit", List.of()),
            // 工资管理
            new Menu("salary", "salary:dashboard", "工资总览", "/salary/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("salary", "salary:alloc", "工资下拨", "/salary/alloc", "layui-icon-down", List.of()),
            new Menu("salary", "salary:report", "工资上报", "/salary/report", "layui-icon-up", List.of()),
            new Menu("salary", "salary:detail", "工资明细", "/salary/detail", "layui-icon-form", List.of()),
            new Menu("salary", "salary:dept", "核算部门", "/salary/dept", "layui-icon-group", List.of()),
            // 食堂管理
            new Menu("canteen", "canteen:dashboard", "经营总览", "/canteen/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("canteen", "canteen:purchase", "采购入库", "/canteen/purchase", "layui-icon-cart", List.of()),
            new Menu("canteen", "canteen:outbound", "领用出库", "/canteen/outbound", "layui-icon-release", List.of()),
            new Menu("canteen", "canteen:stock", "库存盘点", "/canteen/stock", "layui-icon-table", List.of()),
            new Menu("canteen", "canteen:sale", "就餐收入", "/canteen/sale", "layui-icon-rmb", List.of()),
            new Menu("canteen", "canteen:finance", "食堂财务", "/canteen/finance", "layui-icon-form", List.of()),
            new Menu("canteen", "canteen:material", "食材物料", "/canteen/material", "layui-icon-component", List.of()),
            new Menu("canteen", "canteen:supplier", "供应商", "/canteen/supplier", "layui-icon-user", List.of()),
            // 盘点管理
            new Menu("inventory", "inventory:dashboard", "盘点总览", "/inventory/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("inventory", "inventory:scan", "扫码盘点", "/inventory/scan", "layui-icon-camera-fill", List.of()),
            new Menu("inventory", "inventory:task", "盘点任务", "/inventory/task", "layui-icon-form", List.of()),
            new Menu("inventory", "inventory:record", "盘点明细", "/inventory/record", "layui-icon-table", List.of()),
            new Menu("inventory", "inventory:asset", "固定资产", "/inventory/asset", "layui-icon-component", List.of()),
            new Menu("inventory", "inventory:goods", "库存物资", "/inventory/goods", "layui-icon-template-1", List.of()),
            new Menu("inventory", "inventory:label", "标签打印", "/inventory/label", "layui-icon-print", List.of()),
            // 档案管理
            new Menu("archive", "archive:dashboard", "档案总览", "/archive/dashboard", "layui-icon-chart-screen", List.of()),
            new Menu("archive", "archive:volume", "案卷管理", "/archive/volume", "layui-icon-template-1", List.of()),
            new Menu("archive", "archive:file", "卷内文件", "/archive/file", "layui-icon-file", List.of()),
            new Menu("archive", "archive:borrow", "借阅管理", "/archive/borrow", "layui-icon-export", List.of()),
            new Menu("archive", "archive:destroy", "销毁清册", "/archive/destroy", "layui-icon-fonts-del", List.of()),
            new Menu("archive", "archive:basic", "门类与位置", "/archive/basic", "layui-icon-tree", List.of()),
            // 系统管理
            new Menu("system", "system:user", "用户管理", "/system/user", "layui-icon-user", List.of()),
            new Menu("system", "system:role", "角色权限", "/system/role", "layui-icon-auz", List.of()),
            new Menu("system", "system:permission", "资源菜单", "/system/permission", "layui-icon-tree", List.of()),
            new Menu("system", "system:api", "接口文档", "/system/api", "layui-icon-console", List.of())
    );

    /**
     * 接口路径模式 → 所需权限码。
     *
     * <p>{@code **} 表示任意后续路径。匹配采取「精确优先、再按模式长度降序」的策略，
     * 保证更具体的规则覆盖更宽泛的规则。
     */
    public static final List<ApiPattern> API_PATTERNS = List.of(
            new ApiPattern("/api/sale/**", "sale", "销售台账接口"),
            new ApiPattern("/api/customer/**", "sale", "客户档案接口"),
            new ApiPattern("/api/product/**", "sale", "发货品种接口"),
            new ApiPattern("/api/price/**", "price", "价格查询接口"),
            new ApiPattern("/api/crawl/**", "price", "采集任务接口"),
            new ApiPattern("/api/audit/**", "audit", "审计项目与整改接口"),
            new ApiPattern("/api/inspect/**", "audit", "检查任务接口"),
            new ApiPattern("/api/train/**", "train", "培训接口"),
            new ApiPattern("/api/salary/**", "salary", "工资接口"),
            new ApiPattern("/api/canteen/**", "canteen", "食堂接口"),
            new ApiPattern("/api/inventory/**", "inventory", "盘点接口"),
            new ApiPattern("/api/archive/**", "archive", "档案接口"),
            new ApiPattern("/api/dashboard/**", "overview", "综合大屏接口"),
            new ApiPattern("/api/system/**", "system", "系统管理接口"),
            new ApiPattern("/openapi/system/**", "system", "开放接口的受控部分")
    );
}
