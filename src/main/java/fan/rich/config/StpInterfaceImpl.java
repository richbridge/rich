package fan.rich.config;

import fan.rich.entity.SysPermission;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.service.RbacService;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把 RBAC 数据接入 Sa-Token。
 *
 * <p>注册该类后，{@code @SaCheckPermission} / {@code @SaCheckRole} /
 * {@code StpUtil.hasPermission()} 等注解式能力即可直接使用，
 * 与后台路由拦截器共用同一份 {@link RbacService} 数据。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final RbacService rbacService;
    private final SysPermissionMapper permissionMapper;

    public StpInterfaceImpl(RbacService rbacService, SysPermissionMapper permissionMapper) {
        this.rbacService = rbacService;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseLoginId(loginId);
        if (userId == null) {
            return List.of();
        }
        // 超管直接持有全部权限码：注解式鉴权不必再单独写旁路逻辑
        if (rbacService.isSuperAdmin(userId)) {
            return permissionMapper.selectList(null).stream()
                    .filter(p -> p.getStatus() == null || p.getStatus() == 1)
                    .filter(p -> StringUtils.hasText(p.getPermCode()))
                    .map(SysPermission::getPermCode)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>(rbacService.permissionCodes(userId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseLoginId(loginId);
        return userId == null ? List.of() : new ArrayList<>(rbacService.roleCodes(userId));
    }

    private Long parseLoginId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
