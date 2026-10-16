package fan.rich.service.impl;

import fan.rich.config.PermissionCatalog;
import fan.rich.dto.MenuNode;
import fan.rich.entity.SysPermission;
import fan.rich.entity.SysRole;
import fan.rich.entity.SysRolePermission;
import fan.rich.entity.SysUserRole;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.mapper.SysRoleMapper;
import fan.rich.mapper.SysRolePermissionMapper;
import fan.rich.mapper.SysUserRoleMapper;
import fan.rich.service.RbacService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 权限（RBAC）服务实现。
 *
 * <p>权限缓存为进程内缓存：任何角色 / 用户授权变更都会自增全局版本号，
 * 使所有用户的缓存条目自然失效，避免集群外状态不一致导致「改了权限不生效」。
 */
@Service
public class RbacServiceImpl implements RbacService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    /** 用户权限缓存：userId → 条目（含写入时的版本号）。 */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 全局版本号：授权变更时自增，用于一次性失效所有缓存。 */
    private final AtomicLong version = new AtomicLong();

    public RbacServiceImpl(SysUserRoleMapper userRoleMapper,
                           SysRolePermissionMapper rolePermMapper,
                           SysRoleMapper roleMapper,
                           SysPermissionMapper permissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.rolePermMapper = rolePermMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public Set<String> roleCodes(Long userId) {
        List<Long> roleIds = roleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (SysRole role : roleMapper.selectBatchIds(roleIds)) {
            if (StringUtils.hasText(role.getRoleCode())) {
                codes.add(role.getRoleCode());
            }
        }
        return codes;
    }

    @Override
    public List<Long> roleIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> ids = userRoleMapper.selectRoleIdsByUserId(userId);
        return ids == null ? List.of() : ids;
    }

    @Override
    public Set<String> permissionCodes(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        long current = version.get();
        CacheEntry hit = cache.get(userId);
        if (hit != null && hit.version == current) {
            return hit.codes;
        }
        Set<String> codes = loadPermissionCodes(userId);
        cache.put(userId, new CacheEntry(current, codes));
        return codes;
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        return roleCodes(userId).contains(SUPER_ROLE);
    }

    @Override
    public boolean hasPermission(Long userId, String code) {
        if (!StringUtils.hasText(code)) {
            return true;
        }
        Set<String> owned = permissionCodes(userId);
        if (owned.contains(code)) {
            return true;
        }
        // 模块级要求（如 /api/sale/** → "sale"）在持有该模块任意一个子权限时即视为放行：
        // 只看得到「销售看板」的人，同样需要能调用 /api/sale 下的数据接口
        if (code.indexOf(':') < 0) {
            String prefix = code + ":";
            for (String ownedCode : owned) {
                if (ownedCode.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<MenuNode> menuTree(Long userId) {
        Set<String> owned = permissionCodes(userId);
        boolean superAdmin = isSuperAdmin(userId);
        List<MenuNode> tree = fullMenuTree();
        for (MenuNode module : tree) {
            if (!superAdmin) {
                module.getChildren().removeIf(child -> !owned.contains(child.getCode()));
            }
        }
        // 一级节点至少保留一个可用子菜单，否则整个模块不展示
        tree.removeIf(module -> module.getChildren().isEmpty());
        return tree;
    }

    @Override
    public List<MenuNode> fullMenuTree() {
        // 只取菜单树可见的类型：接口模式与别名不参与菜单渲染
        List<SysPermission> menus = permissionMapper.selectList(null).stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == 1)
                .filter(p -> PermissionCatalog.TYPE_MODULE.equals(p.getPermType())
                        || PermissionCatalog.TYPE_MENU.equals(p.getPermType()))
                .toList();
        if (menus.isEmpty()) {
            return List.of();
        }
        // 只取启用状态节点
        List<SysPermission> enabled = menus.stream()
                .sorted(Comparator.comparing(SysPermission::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysPermission::getId))
                .toList();

        Map<Long, MenuNode> index = new LinkedHashMap<>();
        List<MenuNode> roots = new ArrayList<>();
        for (SysPermission p : enabled) {
            MenuNode node = new MenuNode(p.getPermCode(), p.getPermName(),
                    p.getIcon() == null ? "layui-icon-app" : p.getIcon(), p.getSortNo());
            node.setPath(p.getPath());
            index.put(p.getId(), node);
        }
        for (SysPermission p : enabled) {
            MenuNode self = index.get(p.getId());
            MenuNode parent = p.getParentId() != null ? index.get(p.getParentId()) : null;
            if (parent == null) {
                roots.add(self);
            } else {
                parent.getChildren().add(self);
            }
        }
        return roots;
    }

    // ------------------------------------------------------------------ 授权

    @Override
    public void setUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                SysUserRole rel = new SysUserRole();
                rel.setUserId(userId);
                rel.setRoleId(roleId);
                userRoleMapper.insert(rel);
            }
        }
        invalidate(userId);
    }

    @Override
    public int setRolePermissions(Long roleId, List<Long> permIds) {
        rolePermMapper.deleteByRoleId(roleId);
        Collection<Long> expanded = expandAncestors(permIds);
        for (Long permId : expanded) {
            SysRolePermission rel = new SysRolePermission();
            rel.setRoleId(roleId);
            rel.setPermId(permId);
            rolePermMapper.insert(rel);
        }
        invalidateAll();
        return expanded.size();
    }

    @Override
    public void invalidate(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    @Override
    public void invalidateAll() {
        version.incrementAndGet();
        cache.clear();
    }

    // ------------------------------------------------------------------ 内部实现

    /** 加载用户的权限码：汇集所有启用角色的权限，并补齐父级模块码。 */
    private Set<String> loadPermissionCodes(Long userId) {
        List<Long> roleIds = roleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> permIds = new LinkedHashSet<>();
        for (SysRole role : roleMapper.selectBatchIds(roleIds)) {
            if (role.getStatus() != null && role.getStatus() != 1) {
                continue;
            }
            List<Long> ids = rolePermMapper.selectPermIdsByRoleId(role.getId());
            if (ids != null) {
                permIds.addAll(ids);
            }
        }
        if (permIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new HashSet<>();
        for (SysPermission p : permissionMapper.selectBatchIds(permIds)) {
            if (p.getStatus() != null && p.getStatus() != 1) {
                continue;
            }
            if (StringUtils.hasText(p.getPermCode())) {
                codes.add(p.getPermCode());
            }
        }
        // 子菜单码隐含父模块码，便于接口层按模块前缀拦截
        for (String code : new ArrayList<>(codes)) {
            int sep = code.lastIndexOf(':');
            while (sep > 0) {
                codes.add(code.substring(0, sep));
                sep = code.lastIndexOf(':', sep - 1);
            }
        }
        return codes;
    }

    /**
     * 补齐所选权限的祖先链。
     *
     * <p>勾选「发货台账」但不勾「销售管理」时会出现看不到入口却仍能调接口的矛盾，
     * 这里统一向上补齐，保证父子权限同进同退。
     */
    private Collection<Long> expandAncestors(List<Long> permIds) {
        if (permIds == null || permIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> parentOf = new HashMap<>();
        for (SysPermission p : permissionMapper.selectList(null)) {
            parentOf.put(p.getId(), p.getParentId());
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long id : permIds) {
            if (id == null) {
                continue;
            }
            Long cursor = id;
            // 防止脏数据成环
            int guard = 0;
            while (cursor != null && result.add(cursor) && guard++ < 32) {
                cursor = parentOf.get(cursor);
            }
        }
        return result;
    }

    /** 缓存条目：记录写入时的全局版本号，版本变了即视为失效。 */
    private record CacheEntry(long version, Set<String> codes) {
    }
}
