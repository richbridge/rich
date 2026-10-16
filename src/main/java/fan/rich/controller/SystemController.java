package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import fan.rich.config.RoutePermissionGuard;
import fan.rich.common.R;
import fan.rich.dto.MenuNode;
import fan.rich.entity.SysPermission;
import fan.rich.entity.SysRole;
import fan.rich.entity.SysUser;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.mapper.SysRoleMapper;
import fan.rich.mapper.SysRolePermissionMapper;
import fan.rich.mapper.SysUserMapper;
import fan.rich.mapper.SysUserRoleMapper;
import fan.rich.service.RbacService;
import fan.rich.service.SysPermissionService;
import fan.rich.service.SysRoleService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统管理：用户、角色、权限资源与菜单下发。
 *
 * <p>所有写操作完成后都会调用 {@link RbacService#invalidate} 系列方法清缓存，
 * 保证「改完立刻生效」，不需要重启应用。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleService roleService;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysPermissionService permissionService;
    private final SysPermissionMapper permissionMapper;
    private final RbacService rbacService;
    private final RoutePermissionGuard guard;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SystemController(SysUserMapper userMapper,
                            SysUserRoleMapper userRoleMapper,
                            SysRoleService roleService,
                            SysRoleMapper roleMapper,
                            SysRolePermissionMapper rolePermMapper,
                            SysPermissionService permissionService,
                            SysPermissionMapper permissionMapper,
                            RbacService rbacService,
                            RoutePermissionGuard guard) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleService = roleService;
        this.roleMapper = roleMapper;
        this.rolePermMapper = rolePermMapper;
        this.permissionService = permissionService;
        this.permissionMapper = permissionMapper;
        this.rbacService = rbacService;
        this.guard = guard;
    }

    // ------------------------------------------------------------------ 当前登录者

    /** 当前登录用户的信息、角色与权限码，供前端做按钮级显隐。 */
    @GetMapping("/profile")
    public R profile() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        SysUser user = userMapper.selectById(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", userId);
        data.put("username", user == null ? null : user.getUsername());
        data.put("nickname", user == null ? null : user.getNickname());
        data.put("status", user == null ? null : user.getStatus());
        data.put("superAdmin", rbacService.isSuperAdmin(userId));
        data.put("roles", rbacService.roleCodes(userId));
        data.put("permissions", rbacService.permissionCodes(userId));
        return R.ok(data);
    }

    /** 当前用户可见的菜单树（index.html 用它渲染左侧菜单）。 */
    @GetMapping("/menu")
    public R menu() {
        Long userId = currentUserId();
        List<MenuNode> tree = userId == null ? List.of() : rbacService.menuTree(userId);
        return R.ok(tree);
    }

    // ------------------------------------------------------------------ 用户

    /** 用户列表（layui table 分页）。 */
    @GetMapping("/users")
    public R users(@RequestParam(defaultValue = "1") long page,
                   @RequestParam(defaultValue = "20") long limit,
                   @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword));
        }
        q.orderByAsc(SysUser::getId);
        IPage<SysUser> records = userMapper.selectPage(new Page<>(page, limit), q);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : records.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("nickname", u.getNickname());
            row.put("status", u.getStatus());
            row.put("roleIds", rbacService.roleIds(u.getId()));
            row.put("roleNames", rbacService.roleCodes(u.getId()));
            rows.add(row);
        }
        return R.table(records.getTotal(), rows);
    }

    /** 新增或编辑用户。密码仅在非空时更新。 */
    @PostMapping("/user/save")
    public R saveUser(@RequestBody Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String username = str(body.get("username"));
        if (!StringUtils.hasText(username)) {
            return R.fail("用户名不能为空");
        }
        SysUser user = id == null ? new SysUser() : userMapper.selectById(id);
        if (user == null) {
            return R.fail("用户不存在");
        }
        if (id == null) {
            if (userMapper.findByUsername(username) != null) {
                return R.fail("用户名已存在");
            }
            user.setCreateTime(LocalDateTime.now());
            user.setStatus(1);
        }
        user.setUsername(username);
        user.setNickname(str(body.get("nickname")));
        if (body.get("status") != null) {
            user.setStatus(toInt(body.get("status")));
        }
        String password = str(body.get("password"));
        if (StringUtils.hasText(password)) {
            if (password.length() < 6) {
                return R.fail("密码至少 6 位");
            }
            user.setPassword(passwordEncoder.encode(password));
        }
        if (id == null) {
            if (user.getPassword() == null) {
                user.setPassword(passwordEncoder.encode("123456"));
            }
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        List<Long> roleIds = toLongList(body.get("roleIds"));
        if (roleIds != null) {
            rbacService.setUserRoles(user.getId(), roleIds);
        }
        return R.ok(Map.of("id", user.getId()));
    }

    /** 删除用户（同时清掉角色关联与权限缓存）。 */
    @DeleteMapping("/user/delete")
    public R deleteUser(@RequestParam Long id) {
        if (id.equals(currentUserId())) {
            return R.fail("不能删除当前登录的用户");
        }
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        rbacService.invalidateAll();
        return R.ok(true);
    }

    /** 单独设置用户角色。 */
    @PostMapping("/user/roles")
    public R setUserRoles(@RequestParam Long userId, @RequestBody List<Long> roleIds) {
        if (userId == null) {
            return R.fail("缺少用户 ID");
        }
        rbacService.setUserRoles(userId, roleIds);
        return R.ok(true);
    }

    // ------------------------------------------------------------------ 角色

    /** 全部角色。 */
    @GetMapping("/roles")
    public R roles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortNo).orderByAsc(SysRole::getId));
        return R.ok(roles);
    }

    /** 新增或编辑角色。 */
    @PostMapping("/role/save")
    public R saveRole(@RequestBody SysRole role) {
        if (!StringUtils.hasText(role.getRoleCode()) || !StringUtils.hasText(role.getRoleName())) {
            return R.fail("角色编码与名称不能为空");
        }
        if (role.getId() == null) {
            if (!roleService.isCodeAvailable(role.getRoleCode(), null)) {
                return R.fail("角色编码已存在");
            }
            role.setCreateTime(LocalDateTime.now());
            if (role.getStatus() == null) {
                role.setStatus(1);
            }
            roleMapper.insert(role);
        } else {
            SysRole old = roleMapper.selectById(role.getId());
            if (old == null) {
                return R.fail("角色不存在");
            }
            if (!roleService.isCodeAvailable(role.getRoleCode(), role.getId())) {
                return R.fail("角色编码已存在");
            }
            roleMapper.updateById(role);
        }
        rbacService.invalidateAll();
        return R.ok(role.getId());
    }

    /** 删除角色（超管角色不允许删除）。 */
    @DeleteMapping("/role/delete")
    public R deleteRole(@RequestParam Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            return R.fail("角色不存在");
        }
        if (RbacService.SUPER_ROLE.equals(role.getRoleCode())) {
            return R.fail("超级管理员角色不可删除");
        }
        long affected = userRoleMapper.countByRoleId(id);
        if (affected > 0) {
            return R.fail("仍有 " + affected + " 个用户持有该角色，请先解除绑定");
        }
        roleMapper.deleteById(id);
        rbacService.invalidateAll();
        return R.ok(true);
    }

    /** 某角色已授权的资源 ID 列表。 */
    @GetMapping("/role/permissions")
    public R rolePermissions(@RequestParam Long roleId) {
        if (RbacService.SUPER_ROLE.equals(roleCode(roleId))) {
            // 超管默认全选，页面上把整棵树勾上便于理解
            List<Long> all = new ArrayList<>();
            for (SysPermission p : permissionMapper.selectList(null)) {
                all.add(p.getId());
            }
            return R.ok(all);
        }
        return R.ok(rolePermMapper.selectPermIdsByRoleId(roleId));
    }

    private String roleCode(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return role == null ? null : role.getRoleCode();
    }

    /** 给角色授权。 */
    @PostMapping("/role/grant")
    public R grant(@RequestParam Long roleId, @RequestBody List<Long> permIds) {
        if (roleId == null) {
            return R.fail("缺少角色 ID");
        }
        int rows = rbacService.setRolePermissions(roleId, permIds);
        return R.ok(Map.of("granted", rows));
    }

    // ------------------------------------------------------------------ 资源

    /** 资源树（含接口模式，供授权勾选）。 */
    @GetMapping("/permissions")
    public R permissions() {
        return R.ok(permissionService.listOrdered());
    }

    /** 新增或编辑资源。 */
    @PostMapping("/permission/save")
    public R savePermission(@RequestBody SysPermission permission) {
        if (!StringUtils.hasText(permission.getPermCode()) || !StringUtils.hasText(permission.getPermName())) {
            return R.fail("权限码与名称不能为空");
        }
        if (permission.getParentId() == null) {
            permission.setParentId(0L);
        }
        if (permission.getPermType() == null) {
            permission.setPermType("MENU");
        }
        if (permission.getId() == null) {
            if (!permissionService.isCodeAvailable(permission.getPermCode(), null)) {
                return R.fail("权限码已存在");
            }
            permission.setCreateTime(LocalDateTime.now());
            if (permission.getStatus() == null) {
                permission.setStatus(1);
            }
            permissionMapper.insert(permission);
        } else {
            if (!permissionService.isCodeAvailable(permission.getPermCode(), permission.getId())) {
                return R.fail("权限码已存在");
            }
            permissionMapper.updateById(permission);
        }
        rbacService.invalidateAll();
        return R.ok(permission.getId());
    }

    /** 删除资源（连带子资源与角色关联）。 */
    @DeleteMapping("/permission/delete")
    public R deletePermission(@RequestParam Long id) {
        List<Long> children = permissionService.descendantIds(id);
        List<Long> ids = new ArrayList<>(children);
        ids.add(id);
        permissionMapper.deleteBatchIds(ids);
        rbacService.invalidateAll();
        return R.ok(Map.of("removed", ids.size()));
    }

    /** 手动刷新路由鉴权规则缓存。 */
    @PostMapping("/refresh-rules")
    public R refreshRules() {
        guard.refresh();
        rbacService.invalidateAll();
        return R.ok(true);
    }

    // ------------------------------------------------------------------ 工具

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? null : Long.valueOf(String.valueOf(loginId));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object v) {
        Long l = toLong(v);
        return l == null ? 0 : l.intValue();
    }

    private List<Long> toLongList(Object v) {
        if (!(v instanceof List<?> list)) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : list) {
            Long id = toLong(o);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }
}
