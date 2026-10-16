package fan.rich.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import tools.jackson.databind.ObjectMapper;
import fan.rich.common.R;
import fan.rich.entity.SysPermission;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台路由权限拦截器。
 *
 * <p>把请求 URI 映射到 {@code sys_permission} 里登记的权限码，再交给 {@link RbacService}
 * 判断当前用户是否持有。相比逐个方法打注解，路径级鉴权不会因为新增接口漏写注解而失守，
 * 同时资源规则集中落在数据库里，管理员改菜单路径或接口路径即可生效。
 *
 * <p>两种特殊的放行：
 * <ul>
 *   <li>超管角色 {@link RbacService#SUPER_ROLE} 直接放行；</li>
 *   <li>URI 没有对应任何已登记资源时不拦截（认定为非受控资源），
 *       这样新增页面可以先行开发、后补权限，不会一上手就到处 403。</li>
 * </ul>
 */
@Component
public class RoutePermissionGuard implements HandlerInterceptor {

    /** 规则缓存有效期（毫秒）。资源表改动后最多等这么久生效，也可由管理端手动刷新。 */
    private static final long RULE_TTL = 30_000L;

    /**
     * 无需鉴权的路径：登录相关、静态资源，以及「取自身会话信息」这类基础设施接口。
     *
     * <p>{@code /api/system/{menu,profile}} 返回的就是已按权限裁剪过的结果，
     * 属于前端渲染的前置依赖，不该再反过来要求 system 权限。
     */
    private static final Set<String> WHITELIST = Set.of(
            "/login", "/doLogin", "/logout", "/403", "/error",
            "/api/system/menu", "/api/system/profile", "/favicon.ico"
    );

    /**
     * 不做路径鉴权的前缀。
     *
     * <p>{@code /openapi} 对外开放接口层自带 AppKey/Token 鉴权与限流，
     * 走独立的权限模型，不参与后台 RBAC 判定。
     */
    private static final Set<String> SKIP_PREFIX = Set.of(
            "/layui", "/admin", "/vendor", "/css", "/js", "/images", "/static",
            "/api/doc", "/openapi"
    );

    private final SysPermissionMapper permissionMapper;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 精确路径 → 权限码。 */
    private volatile Map<String, String> exactRules = Map.of();

    /** 前缀模式（去掉结尾的 /**）→ 权限码，按长度降序匹配。 */
    private volatile List<String[]> patternRules = List.of();

    private volatile long lastLoadAt;

    public RoutePermissionGuard(SysPermissionMapper permissionMapper, RbacService rbacService) {
        this.permissionMapper = permissionMapper;
        this.rbacService = rbacService;
    }

    /** 管理端改动资源后手动刷新规则缓存，避免最多等待一个 TTL。 */
    public void refresh() {
        lastLoadAt = 0L;
        loadRules();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI();
        if (isStatic(uri) || WHITELIST.contains(uri)) {
            return true;
        }
        Long userId = currentUserId();
        if (userId == null) {
            // 未登录：由 Sa-Token 那层统一处理，这里不重复拦截
            return true;
        }
        if (rbacService.isSuperAdmin(userId)) {
            return true;
        }
        String permCode = resolve(uri);
        if (permCode == null) {
            return true;
        }
        if (rbacService.hasPermission(userId, permCode)) {
            return true;
        }
        deny(request, response, permCode);
        return false;
    }

    // ------------------------------------------------------------------ 规则匹配

    private synchronized void loadRules() {
        long now = System.currentTimeMillis();
        if (now - lastLoadAt < RULE_TTL) {
            return;
        }
        List<SysPermission> all;
        try {
            all = permissionMapper.selectList(null);
        } catch (Exception e) {
            // 表还没建好时先放空规则（全部放行），避免启动初期自锁
            exactRules = Map.of();
            patternRules = List.of();
            lastLoadAt = now;
            return;
        }
        Map<String, String> exact = new LinkedHashMap<>();
        List<String[]> patterns = new ArrayList<>();
        for (SysPermission p : all) {
            if (p.getStatus() != null && p.getStatus() != 1) {
                continue;
            }
            if (!StringUtils.hasText(p.getPath()) || !StringUtils.hasText(p.getPermCode())) {
                continue;
            }
            if (PermissionCatalog.TYPE_MODULE.equals(p.getPermType())) {
                continue;
            }
            String path = p.getPath().trim();
            if (path.endsWith("/**")) {
                patterns.add(new String[]{path.substring(0, path.length() - 3), p.getPermCode()});
            } else {
                exact.put(path, p.getPermCode());
            }
        }
        patterns.sort((a, b) -> Integer.compare(b[0].length(), a[0].length()));
        exactRules = exact;
        patternRules = patterns;
        lastLoadAt = now;
    }

    /** 把 URI 解析成权限码：精确匹配优先，其次取最长的通配前缀。 */
    private String resolve(String uri) {
        loadRules();
        String hit = exactRules.get(uri);
        if (hit != null) {
            return hit;
        }
        for (String[] rule : patternRules) {
            if (uri.startsWith(rule[0])) {
                return rule[1];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 拦截响应

    private void deny(HttpServletRequest request, HttpServletResponse response, String permCode)
            throws IOException {
        String uri = request.getRequestURI();
        boolean json = uri.startsWith("/api/") || uri.startsWith("/openapi/")
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json");
        if (json) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    R.fail(403, "没有访问权限（缺少权限：" + permCode + "）")));
            return;
        }
        response.sendRedirect("/403");
    }

    private boolean isStatic(String uri) {
        for (String prefix : SKIP_PREFIX) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Long currentUserId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException | NotLoginException e) {
            return null;
        }
    }
}
