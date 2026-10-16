package fan.rich.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对外开放接口鉴权与限流拦截器，作用于 {@code /openapi/**}。
 *
 * <p>与后台 RBAC 是两套模型：这边只校验「调用方是不是已登录的用户」，
 * 具体能看哪些模块仍由 {@link fan.rich.service.RbacService} 返回的权限集合决定，
 * 由各开放接口自行裁减返回内容（例如移动端菜单直接走 {@code menuTree}）。
 *
 * <p>Token 传递支持三种方式，移动端任选其一：
 * <ul>
 *   <li>请求头 {@code satoken: <token>}（Sa-Token 原生）</li>
 *   <li>请求头 {@code Authorization: Bearer <token>}（REST 惯例）</li>
 *   <li>Cookie {@code satoken}（H5 嵌真机 WebView 时自动携带）</li>
 * </ul>
 */
@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    /** 当前请求解析出的登录用户 ID。 */
    public static final String ATTR_USER_ID = "openApiUserId";

    /** 免鉴权路径。 */
    private static final java.util.Set<String> PUBLIC_PATHS = java.util.Set.of(
            "/openapi/auth/login", "/openapi/doc", "/openapi/ping"
    );

    private final OpenApiProperties properties;

    /** 限流计数：IP + 分钟桶。 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public OpenApiAuthInterceptor(OpenApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI();
        // 预检请求已在 CORS 过滤器直接放行，这里再兜一层
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!properties.isEnabled()) {
            writeJson(response, 503, "{\"code\":-1,\"msg\":\"开放接口已关闭\"}");
            return false;
        }
        if (!checkRate(request, response)) {
            return false;
        }
        if (PUBLIC_PATHS.contains(uri)) {
            return true;
        }
        Long userId = resolveUserId(request);
        if (userId == null) {
            writeJson(response, 401, "{\"code\":401,\"msg\":\"未登录或 token 已失效\"}");
            return false;
        }
        request.setAttribute(ATTR_USER_ID, userId);
        return true;
    }

    /** 解析调用方身份：优先走 Sa-Token 上下文，其次解析 Bearer 令牌。 */
    private Long resolveUserId(HttpServletRequest request) {
        try {
            if (StpUtil.isLogin()) {
                return Long.parseLong(String.valueOf(StpUtil.getLoginId()));
            }
        } catch (Exception ignored) {
            // 落到下面的 Bearer 分支
        }
        String bearer = bearer(request);
        if (bearer == null) {
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(bearer);
            return loginId == null ? null : Long.parseLong(String.valueOf(loginId));
        } catch (Exception e) {
            return null;
        }
    }

    private String bearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        String direct = request.getHeader("X-Access-Token");
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        return null;
    }

    /** 单 IP 每分钟限流。 */
    private boolean checkRate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int limit = properties.getRateLimit();
        if (limit <= 0) {
            return true;
        }
        String ip = clientIp(request);
        long minute = System.currentTimeMillis() / 60_000;
        Bucket bucket = buckets.computeIfAbsent(ip, k -> new Bucket());
        if (bucket.minute != minute) {
            bucket.minute = minute;
            bucket.counter.set(0);
        }
        if (bucket.counter.incrementAndGet() > limit) {
            writeJson(response, 429, "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }

    /** 计数桶。 */
    private static final class Bucket {
        private volatile long minute;
        private final AtomicInteger counter = new AtomicInteger();
    }
}
