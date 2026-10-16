package fan.rich.service;

import fan.rich.entity.SysUser;

/**
 * 用户服务：登录校验。
 */
public interface UserService {

    /**
     * 校验账号密码。
     *
     * @return 校验通过返回用户信息，否则返回 null
     */
    SysUser checkLogin(String username, String password);
}
