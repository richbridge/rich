package fan.rich.controller.openapi;

import fan.rich.common.R;
import fan.rich.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 开放接口的公共支撑：取当前调用方身份、按 RBAC 权限裁减返回内容。
 *
 * <p>移动端拿到的数据与 PC 后台同源同权：角色在后台没被授予某个模块，
 * 移动端同样查不到这个模块的数据。
 */
@Component
public class OpenApiSupport {

    private final RbacService rbacService;

    public OpenApiSupport(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /** 当前调用的登录用户 ID（由 {@code OpenApiAuthInterceptor} 解析后写入请求属性）。 */
    public Long currentUserId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest request = sra.getRequest();
        Object value = request.getAttribute("openApiUserId");
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /**
     * 校验调用方是否具备某个权限。
     *
     * @param permCode 权限码（模块级如 {@code sale}，菜单级如 {@code sale:ledger}）
     * @return 无权限时返回可直接抛给用户的结果；有权限返回 null
     */
    public R require(String permCode) {
        Long userId = currentUserId();
        if (userId == null) {
            return R.fail(401, "未登录或 token 已失效");
        }
        if (rbacService.isSuperAdmin(userId) || rbacService.hasPermission(userId, permCode)) {
            return null;
        }
        return R.fail(403, "当前账号没有访问该数据的权限（缺少权限：" + permCode + "）");
    }

    /** 是否具备某个权限。 */
    public boolean has(String permCode) {
        Long userId = currentUserId();
        return userId != null
                && (rbacService.isSuperAdmin(userId) || rbacService.hasPermission(userId, permCode));
    }
}
