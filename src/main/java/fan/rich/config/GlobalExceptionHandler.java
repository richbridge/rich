package fan.rich.config;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 未登录异常处理：跳转登录页
 * 注意用 @ControllerAdvice，不是 @RestControllerAdvice
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public RedirectView handleNotLogin(NotLoginException e) {
        // 未登录 → 302 重定向到 /login
        return new RedirectView("/login");
    }
}