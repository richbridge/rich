package fan.rich.controller.openapi;

import cn.dev33.satoken.stp.StpUtil;
import fan.rich.common.R;
import fan.rich.controller.openapi.OpenApiSupport;
import fan.rich.entity.SysUser;
import fan.rich.mapper.SysUserMapper;
import fan.rich.service.RbacService;
import fan.rich.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口 · 认证。
 *
 * <p>移动端（uni-app）与第三方系统从这里换取 token，之后在请求头携带：
 * <pre>
 * satoken: &lt;token&gt;
 * </pre>
 * 或者
 * <pre>
 * Authorization: Bearer &lt;token&gt;
 * </pre>
 */
@RestController
@RequestMapping("/openapi/auth")
public class OpenApiAuthController {

    private final UserService userService;
    private final SysUserMapper userMapper;
    private final OpenApiSupport support;
    private final RbacService rbacService;

    public OpenApiAuthController(UserService userService,
                                 SysUserMapper userMapper,
                                 OpenApiSupport support,
                                 RbacService rbacService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.support = support;
        this.rbacService = rbacService;
    }

    /**
     * 账号密码登录。
     *
     * @param body {@code {username, password}}
     * @return {@code {token, tokenName, timeout, user, roles, permissions}}
     */
    @PostMapping("/login")
    public R login(@RequestBody(required = false) Map<String, Object> body) {
        String username = body == null ? null : String.valueOf(body.get("username"));
        String password = body == null ? null : String.valueOf(body.get("password"));
        if (username == null || username.isBlank() || password == null) {
            return R.fail("用户名与密码不能为空");
        }
        SysUser user = userService.checkLogin(username.trim(), password);
        if (user == null) {
            return R.fail("用户名或密码错误，或账号已停用");
        }
        StpUtil.login(user.getId());
        return R.ok(buildPayload(user, StpUtil.getTokenValue()));
    }

    /** 注销当前 token。 */
    @PostMapping("/logout")
    public R logout() {
        Long userId = support.currentUserId();
        if (userId != null) {
            StpUtil.logout();
        }
        return R.ok(true);
    }

    /** 当前登录者信息。 */
    @GetMapping("/profile")
    public R profile() {
        Long userId = support.currentUserId();
        if (userId == null) {
            return R.fail(401, "未登录或 token 已失效");
        }
        SysUser user = userMapper.selectById(userId);
        return R.ok(buildPayload(user, null));
    }

    private Map<String, Object> buildPayload(SysUser user, String token) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (user != null) {
            info.put("id", user.getId());
            info.put("username", user.getUsername());
            info.put("nickname", user.getNickname());
            info.put("email", user.getEmail());
            info.put("phone", user.getPhone());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("tokenName", "satoken");
        data.put("timeout", StpUtil.getTokenTimeout());
        data.put("user", info);
        if (user != null) {
            data.put("roles", rbacService.roleCodes(user.getId()));
            List<String> permissions = new java.util.ArrayList<>(rbacService.permissionCodes(user.getId()));
            data.put("permissions", permissions);
            data.put("menu", rbacService.menuTree(user.getId()));
        }
        return data;
    }
}
