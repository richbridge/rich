package fan.rich.service;

import fan.rich.dto.MenuNode;

import java.util.List;
import java.util.Set;

/**
 * 权限（RBAC）服务。
 *
 * <p>模型：用户 → 角色 → 权限资源。权限资源里既包含左侧菜单，也包含接口路径模式；
 * Sa-Token 通过 {@code StpInterface} 从这里取角色与权限集合做注解式鉴权，
 * 后台路由拦截器则从同一处取数据做路径级鉴权，两边口径一致。
 *
 * <p>结果按 userId 做进程内缓存，任何角色授权变更时调 {@link #invalidate} 统一失效。
 */
public interface RbacService {

    /** 超管角色编码：拥有该角色不做任何权限校验，直接放行。 */
    String SUPER_ROLE = "SUPER_ADMIN";

    /**
     * 取用户的全部角色编码。
     */
    Set<String> roleCodes(Long userId);

    /**
     * 取用户的角色 ID。
     */
    List<Long> roleIds(Long userId);

    /**
     * 取用户的全部权限码（含菜单码、按钮码、接口码）。
     */
    Set<String> permissionCodes(Long userId);

    /**
     * 是否超管。
     */
    boolean isSuperAdmin(Long userId);

    /**
     * 当前登录用户是否有某个权限。
     */
    boolean hasPermission(Long userId, String code);

    /**
     * 构造当前用户可见的左侧菜单树（仅含已启用且有权限的菜单）。
     */
    List<MenuNode> menuTree(Long userId);

    /**
     * 全量菜单树（不按权限裁剪，用于授权页面展示）。
     */
    List<MenuNode> fullMenuTree();

    /**
     * 设置某用户的角色（先清空后写入）。
     *
     * @param roleIds 角色 ID 列表，可为空表示清空
     */
    void setUserRoles(Long userId, List<Long> roleIds);

    /**
     * 设置某角色的权限（先清空后写入）。
     *
     * @param permIds 权限 ID 列表，可为空表示清空
     * @return 实际授权条数
     */
    int setRolePermissions(Long roleId, List<Long> permIds);

    /**
     * 失效用户的权限缓存。
     */
    void invalidate(Long userId);

    /**
     * 失效全部用户权限缓存（角色授权变更后调用）。
     */
    void invalidateAll();
}
