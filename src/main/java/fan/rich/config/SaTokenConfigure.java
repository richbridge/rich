package fan.rich.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层拦截链装配。
 *
 * <p>顺序（登记顺序即执行顺序）：
 * <ol>
 *   <li>{@code SaInterceptor}：登录态校验，未登录跳登录页 / 返回未登录；</li>
 *   <li>{@link RoutePermissionGuard}：RBAC 路径鉴权，已登录但无权访问时返回 403；</li>
 *   <li>缓存处理拦截器：所有 Thymeleaf 页面禁用浏览器缓存。</li>
 * </ol>
 *
 * <p>前两层共用同一份 {@link fan.rich.service.RbacService} 数据，
 * 注解式鉴权（{@code @SaCheckPermission}）也可直接使用。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    private final RoutePermissionGuard routePermissionGuard;
    private final OpenApiAuthInterceptor openApiAuthInterceptor;

    public SaTokenConfigure(RoutePermissionGuard routePermissionGuard,
                            OpenApiAuthInterceptor openApiAuthInterceptor) {
        this.routePermissionGuard = routePermissionGuard;
        this.openApiAuthInterceptor = openApiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 开放接口：先做 token 鉴权与限流；跨模块授权由各开放接口按 RBAC 自行裁减
        registry.addInterceptor(openApiAuthInterceptor).addPathPatterns("/openapi/**");
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**")
                    // 放行：登录页、登录接口、错误页、静态资源
                    .notMatch(
                            "/login",
                            "/doLogin",
                            "/error",           // 关键：放行错误处理路径，避免死循环
                            "/openapi/**",      // 开放接口自带 token 鉴权，返回 JSON 而非跳登录页
                            "/layui/**",
                            "/admin/**",
                            "/vendor/**",
                            "/css/**",
                            "/js/**",
                            "/images/**",
                            "/favicon.ico"
                    )
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");

        // RBAC 路径鉴权（在登录校验之后）
        registry.addInterceptor(routePermissionGuard).addPathPatterns("/**");

        // 页面（Thymeleaf 视图）禁用浏览器缓存：
        // 否则菜单调整后浏览器仍用旧缓存页面，出现「点菜单跳错页」的假象
        registry.addInterceptor(new HandlerInterceptor() {
            private boolean isStatic(String uri) {
                return uri.startsWith("/layui") || uri.startsWith("/admin")
                        || uri.startsWith("/vendor") || uri.startsWith("/css")
                        || uri.startsWith("/js") || uri.startsWith("/images")
                        || uri.equals("/favicon.ico");
            }

            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {
                String uri = request.getRequestURI();
                if (!isStatic(uri)) {
                    response.setHeader("Cache-Control", "no-store");
                }
                return true;
            }
        }).addPathPatterns("/**");
    }
}
