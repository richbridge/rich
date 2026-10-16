package fan.rich.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import fan.rich.common.R;
import fan.rich.entity.SysUser;
import fan.rich.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录页（放行）
     * 返回视图名 login → Thymeleaf 渲染 templates/login.html
     */
    @SaIgnore
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 登录接口（放行）
     * 用 POST，密码不暴露在 URL
     * 返回 Token 字符串
     */
    @SaIgnore
    @PostMapping("/doLogin")
    @ResponseBody
    public R doLogin(@RequestParam String username,
                     @RequestParam String password,
                     HttpSession session) {
        SysUser user = userService.checkLogin(username, password);
        if (user == null) {
            return R.fail("用户名或密码错误");
        }
        StpUtil.login(user.getId());
        session.setAttribute("loginUser", user);
        return R.ok(StpUtil.getTokenValue());
    }

    /**
     * 注销（需要登录）
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        StpUtil.logout();
        session.invalidate();
        return "redirect:/login";
    }
}