package fan.rich.config;

import java.util.List;

/**
 * 对外开放 REST API 目录。
 *
 * <p>既作为 {@code /openapi/doc} 在线文档的来源，也作为移动端调用的口径约定：
 * 统一前缀 {@code /openapi}，统一响应体 {@code {code, msg, data, count}}，
 * {@code code=0} 表示成功，其余为失败。除登录与文档接口外，
 * 其余接口的 Header 需带 {@code satoken: <token>} 或 {@code Authorization: Bearer <token>}。
 */
public final class OpenApiCatalog {

    private OpenApiCatalog() {
    }

    /** 接口分组。 */
    public record Group(String name, String remark) {
    }

    /** 接口定义。 */
    public record Endpoint(String group, String method, String path, String desc,
                           String params, String permission) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("认证", "登录换取 token，token 有效期跟随后台 Sa-Token 会话设置"),
            new Group("总览", "跨模块核心指标，与 PC 端综合大屏同源"),
            new Group("价格", "上期所 / 上金所 / 上海有色 / 长江有色四源行情"),
            new Group("销售", "发货台账、点价与提金结算"),
            new Group("盘点", "资产台账与扫码盘点"),
            new Group("档案", "案卷与卷内文件检索"),
            new Group("食堂", "食材库存与低储预警"),
            new Group("系统", "移动端菜单与字典")
    );

    public static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("认证", "POST", "/openapi/auth/login",
                    "账号密码登录，返回 token 与用户信息",
                    "username 必填, password 必填", "-"),
            new Endpoint("认证", "POST", "/openapi/auth/logout", "注销当前 token", "-", "-"),
            new Endpoint("认证", "GET", "/openapi/auth/profile",
                    "当前用户、角色与权限集合", "-", "-"),

            new Endpoint("总览", "GET", "/openapi/dashboard/overview",
                    "综合经营指标（KPI + 各模块卡片 + 待办 + 图表）", "-", "overview"),
            new Endpoint("总览", "GET", "/openapi/dashboard/modules",
                    "各模块关键指标卡片", "-", "overview"),

            new Endpoint("价格", "GET", "/openapi/price/summary",
                    "四源价格概况与各源最新报价条数", "-", "price"),
            new Endpoint("价格", "GET", "/openapi/price/latest",
                    "最新挂牌价列表", "source=SGE|SMM|CJ|SHFE（可空）, limit 默认 20", "price"),
            new Endpoint("价格", "GET", "/openapi/price/futures",
                    "上期所主力合约最新行情", "instrumentId=au|ag|cu（可空）", "price"),
            new Endpoint("价格", "GET", "/openapi/price/trend",
                    "单品种价格走势", "source, productId, days 默认 30", "price"),

            new Endpoint("销售", "GET", "/openapi/sale/summary",
                    "金含量 / 干重 / 结算金额等核心指标", "-", "sale"),
            new Endpoint("销售", "GET", "/openapi/sale/ledger",
                    "发货台账分页", "page 默认 1, limit 默认 20, customer, from, to", "sale"),
            new Endpoint("销售", "GET", "/openapi/sale/monthly",
                    "月度发货趋势（金含量与批次）", "-", "sale"),

            new Endpoint("盘点", "GET", "/openapi/inventory/tasks",
                    "盘点任务列表（先选任务再扫码）", "page, limit, status=进行中", "inventory"),
            new Endpoint("盘点", "GET", "/openapi/inventory/assets",
                    "资产台账分页", "keyword, page, limit", "inventory"),
            new Endpoint("盘点", "GET", "/openapi/inventory/scan",
                    "扫码解析：按二维码内容取盘点对象", "code 必填", "inventory"),
            new Endpoint("盘点", "POST", "/openapi/inventory/check",
                    "提交盘点结果（实盘数量，自动算盈亏）",
                    "body: {taskId, checker, items:[{code, actualQty}]}", "inventory"),

            new Endpoint("档案", "GET", "/openapi/archive/volumes",
                    "案卷分页检索", "keyword, categoryId, locationCode, page, limit", "archive"),
            new Endpoint("档案", "GET", "/openapi/archive/files",
                    "卷内文件分页检索", "keyword, volumeId, page, limit", "archive"),

            new Endpoint("食堂", "GET", "/openapi/canteen/stock",
                    "库存结存与低储预警", "threshold 默认取物料安全库存", "canteen"),

            new Endpoint("系统", "GET", "/openapi/system/menu",
                    "移动端可见菜单（按权限裁剪）", "-", "-"),
            new Endpoint("系统", "GET", "/openapi/doc", "接口文档（本页数据来源）", "-", "-"),
            new Endpoint("系统", "GET", "/openapi/ping", "健康检查", "-", "-")
    );
}
