package fan.rich.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 对外开放 API 的配置：跨域与鉴权放行策略。
 *
 * <p>移动端 H5 在浏览器里调试时，页面端口（8080 / 5173）与后台端口不同，
 * 需要在这里放开通配域名；真机 App 走原生网络请求，跨域限制对其无效。
 */
@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfiguration {

    /** 预检请求与常见请求头。 */
    private static final List<String> ALLOWED_HEADERS = Arrays.asList(
            "satoken", "Authorization", "X-Access-Token", "Content-Type", "Accept", "Origin",
            "X-Requested-With");

    @Bean
    public FilterRegistrationBean<Filter> openApiCorsFilter(OpenApiProperties properties) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest request = (HttpServletRequest) req;
                HttpServletResponse response = (HttpServletResponse) res;
                String origin = request.getHeader("Origin");
                String allow = resolveAllowOrigin(origin, properties.getAllowedOrigins());
                if (allow != null) {
                    response.setHeader("Access-Control-Allow-Origin", allow);
                    response.setHeader("Vary", "Origin");
                }
                response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", String.join(",", ALLOWED_HEADERS));
                response.setHeader("Access-Control-Max-Age", "3600");
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
                chain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/openapi/*");
        registration.setOrder(-100);
        return registration;
    }

    /** 受 concerns：* 时回显调用方 Origin，保证带凭证请求也能通过。 */
    private static String resolveAllowOrigin(String origin, String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return null;
        }
        if ("*".equals(allowedOrigins.trim()) && origin != null) {
            return origin;
        }
        List<String> items = Arrays.asList(allowedOrigins.split(","));
        if (origin != null && items.contains(origin)) {
            return origin;
        }
        return items.isEmpty() ? null : items.get(0).trim();
    }
}
