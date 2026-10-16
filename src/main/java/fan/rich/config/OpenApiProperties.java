package fan.rich.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对外开放 REST API（{@code /openapi/**}）配置。
 *
 * <pre>
 * rich:
 *   openapi:
 *     enabled: true
 *     rate-limit: 600      # 单 IP 每分钟请求上限，0 为不限
 *     allowed-origins: "*"
 * </pre>
 */
@ConfigurationProperties(prefix = "rich.openapi")
public class OpenApiProperties {

    /** 总开关。 */
    private boolean enabled = true;

    /** 同一 IP 每分钟请求上限，0 表示不限流。 */
    private int rateLimit = 600;

    /** 允许跨域的源，逗号分隔或 *。 */
    private String allowedOrigins = "*";

    /** Token 有效期（秒），与 Sa-Token 会话时长一致时可设 -1 表示跟随全局。 */
    private long timeout = -1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
