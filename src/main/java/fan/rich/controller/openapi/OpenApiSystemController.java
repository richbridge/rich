package fan.rich.controller.openapi;

import fan.rich.common.R;
import fan.rich.dto.MenuNode;
import fan.rich.service.RbacService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口 · 系统侧：移动端菜单、健康检查、接口文档。
 */
@RestController
public class OpenApiSystemController {

    private final OpenApiSupport support;
    private final RbacService rbacService;

    public OpenApiSystemController(OpenApiSupport support, RbacService rbacService) {
        this.support = support;
        this.rbacService = rbacService;
    }

    /** 健康检查，无需鉴权。 */
    @GetMapping("/openapi/ping")
    public R ping() {
        return R.ok(Map.of("service", "rich-openapi", "timestamp", System.currentTimeMillis()));
    }

    /** 移动端可见菜单：与 PC 后台共用同一棵按权限裁剪的菜单树。 */
    @GetMapping("/openapi/system/menu")
    public R menu() {
        Long userId = support.currentUserId();
        if (userId == null) {
            return R.fail(401, "未登录或 token 已失效");
        }
        List<MenuNode> tree = rbacService.menuTree(userId);
        // 打平成「模块 + 页面」两级数组，移动端直接渲染宫格
        List<Map<String, Object>> groups = new java.util.ArrayList<>();
        for (MenuNode node : tree) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("code", node.getCode());
            group.put("name", node.getName());
            group.put("icon", node.getIcon());
            List<Map<String, String>> children = new java.util.ArrayList<>();
            for (MenuNode child : node.getChildren()) {
                children.add(Map.of("code", String.valueOf(child.getCode()),
                        "name", String.valueOf(child.getName()),
                        "icon", String.valueOf(child.getIcon()),
                        "path", String.valueOf(child.getPath())));
            }
            group.put("pages", children);
            groups.add(group);
        }
        return R.ok(groups);
    }

    /** 接口文档清单，供 {@code /system/api} 页面渲染。 */
    @GetMapping("/openapi/doc")
    public R doc() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", fan.rich.config.OpenApiCatalog.GROUPS);
        data.put("endpoints", fan.rich.config.OpenApiCatalog.ENDPOINTS);
        data.put("convention", Map.of(
                "basePath", "/openapi",
                "auth", "satoken 请求头 或 Authorization: Bearer <token>",
                "response", "{code:0 成功非 0 失败, msg, data, count(分页总数)}",
                "rateLimit", "单 IP 每分钟限制，可在 rich.openapi.rate-limit 调整"
        ));
        return R.ok(data);
    }
}
