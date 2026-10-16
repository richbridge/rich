package fan.rich.service.impl;

import fan.rich.entity.SysUser;
import fan.rich.mapper.SysUserMapper;
import fan.rich.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现：基于 sys_user 表做账号密码校验。
 */
@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public SysUser checkLogin(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return null;
        }
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            return null;
        }
        // 禁用账号不允许登录
        if (user.getStatus() != null && user.getStatus() != 1) {
            return null;
        }
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        return user;
    }
}
