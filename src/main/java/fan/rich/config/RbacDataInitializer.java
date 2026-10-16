package fan.rich.config;

import fan.rich.entity.SysPermission;
import fan.rich.entity.SysRole;
import fan.rich.entity.SysRolePermission;
import fan.rich.entity.SysUser;
import fan.rich.entity.SysUserRole;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.mapper.SysRoleMapper;
import fan.rich.mapper.SysRolePermissionMapper;
import fan.rich.mapper.SysUserMapper;
import fan.rich.mapper.SysUserRoleMapper;
import fan.rich.service.RbacService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限模块初始化器。
 *
 * <p>启动顺序设为 0，保证用到权限数据的其它初始化器之前先把四张权限表建好。
 *
 * <p>幂等策略：权限资源与角色按 {@code code} 查重后补齐（已存在的不改名，避免覆盖管理员在页面上做的重命名）；
 * 预置角色仅在「尚无任何授权」时才写入默认权限，之后的一切调整都由页面负责、重启不回滚。
 * 唯一例外是超管角色：它会始终补齐新出现的所有资源，避免新增菜单后超管反而打不开。
 */
@Order(0)
@Component
public class RbacDataInitializer implements ApplicationRunner {

    /** 演示账号默认密码。 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final RbacService rbacService;
    private final boolean enabled;
    private final boolean initDemo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 预置角色定义：编码、名称、说明、默认拥有的资源（权限码前缀或具体码）。 */
    private record RoleDef(String code, String name, String remark, List<String> perms) {
    }

    /** 预置角色清单；{@code system} 全量由超管持有，其余按职能切分。 */
    private static final List<RoleDef> ROLE_DEFS = List.of(
            new RoleDef(RbacService.SUPER_ROLE, "超级管理员", "拥有全部权限，不做任何鉴权拦截", List.of("*")),
            new RoleDef("VIEWER", "访客", "只能查看各模块总览页",
                    List.of("overview", "sale:dashboard", "price:dashboard", "audit:dashboard",
                            "train:dashboard", "salary:dashboard", "canteen:dashboard",
                            "inventory:dashboard", "archive:dashboard")),
            new RoleDef("SALE_CLERK", "销售专员", "销售台账、客户与发货品种",
                    List.of("overview", "sale", "price:dashboard")),
            new RoleDef("FINANCE", "财务专员", "工资、食堂财务与结算",
                    List.of("overview", "sale:dashboard", "sale:settlement", "salary", "canteen")),
            new RoleDef("ARCHIVIST", "档案管理员", "档案全流程管理",
                    List.of("overview", "archive", "inventory:dashboard")),
            new RoleDef("AUDITOR", "审计专员", "审计整改与检查任务",
                    List.of("overview", "audit", "train:dashboard"))
    );

    /** 演示账号：用户名、昵称、角色编码。 */
    private static final List<String[]> DEMO_USERS = List.of(
            new String[]{"viewer", "访客账号", "VIEWER"},
            new String[]{"finance", "财务小张", "FINANCE"},
            new String[]{"archivist", "档案专员", "ARCHIVIST"}
    );

    public RbacDataInitializer(SysRoleMapper roleMapper,
                               SysPermissionMapper permissionMapper,
                               SysUserRoleMapper userRoleMapper,
                               SysRolePermissionMapper rolePermMapper,
                               SysUserMapper userMapper,
                               JdbcTemplate jdbc,
                               RbacService rbacService,
                               @Value("${rich.init.enabled:true}") boolean enabled,
                               @Value("${rich.rbac.init-demo:true}") boolean initDemo) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermMapper = rolePermMapper;
        this.userMapper = userMapper;
        this.jdbc = jdbc;
        this.rbacService = rbacService;
        this.enabled = enabled;
        this.initDemo = initDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            createTables();
            syncPermissions();
            syncRoles();
            grantSuperAdmin();
            grantDefaultPermissions();
            bindAdmin();
            if (initDemo) {
                createDemoUsers();
            }
            System.out.println("[rich] 权限模块就绪：角色 " + roleMapper.selectCount(null)
                    + " 个 / 资源 " + permissionMapper.selectCount(null) + " 项");
        } catch (Exception e) {
            // 拉取源数据要连数据源与站点，鉴权模块失败不应拖垮整个应用
            System.out.println("[rich] 权限模块初始化异常：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 建表

    private void createTables() {
        exec("CREATE TABLE IF NOT EXISTS sys_auth_role (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',\n"
                + "  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',\n"
                + "  remark VARCHAR(255) DEFAULT NULL COMMENT '说明',\n"
                + "  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',\n"
                + "  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',\n"
                + "  create_time DATETIME DEFAULT NULL COMMENT '创建时间',\n"
                + "  UNIQUE KEY uk_role_code (role_code)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色'");

        exec("CREATE TABLE IF NOT EXISTS sys_auth_permission (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父节点，0 为根节点',\n"
                + "  perm_name VARCHAR(64) NOT NULL COMMENT '资源名称',\n"
                + "  perm_type VARCHAR(16) NOT NULL DEFAULT 'MENU' COMMENT 'MODULE/MENU/API/ALIAS',\n"
                + "  perm_code VARCHAR(100) NOT NULL COMMENT '权限码',\n"
                + "  path VARCHAR(200) DEFAULT NULL COMMENT '页面 URL 或接口路径模式',\n"
                + "  icon VARCHAR(64) DEFAULT NULL COMMENT 'layui 图标样式类',\n"
                + "  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',\n"
                + "  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',\n"
                + "  remark VARCHAR(255) DEFAULT NULL COMMENT '说明',\n"
                + "  create_time DATETIME DEFAULT NULL COMMENT '创建时间',\n"
                + "  UNIQUE KEY uk_perm_code (perm_code),\n"
                + "  KEY idx_parent (parent_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限资源'");

        exec("CREATE TABLE IF NOT EXISTS sys_auth_user_role (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  user_id BIGINT NOT NULL COMMENT '用户 ID',\n"
                + "  role_id BIGINT NOT NULL COMMENT '角色 ID',\n"
                + "  UNIQUE KEY uk_user_role (user_id, role_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联'");

        exec("CREATE TABLE IF NOT EXISTS sys_auth_role_perm (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  role_id BIGINT NOT NULL COMMENT '角色 ID',\n"
                + "  perm_id BIGINT NOT NULL COMMENT '资源 ID',\n"
                + "  UNIQUE KEY uk_role_perm (role_id, perm_id)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联'");
    }

    private boolean exec(String sql) {
        try {
            jdbc.execute(sql);
            return true;
        } catch (Exception e) {
            System.out.println("[rich] 权限建表失败：" + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ 资源

    /** 同步 {@link PermissionCatalog} 里登记的全部资源，已存在的不改名称。 */
    private void syncPermissions() {
        Map<String, SysPermission> existing = new HashMap<>();
        for (SysPermission p : permissionMapper.selectList(null)) {
            existing.put(p.getPermCode(), p);
        }
        int created = 0;

        // 一级模块
        for (PermissionCatalog.Module m : PermissionCatalog.MODULES) {
            if (existing.containsKey(m.code())) {
                continue;
            }
            SysPermission p = new SysPermission();
            p.setParentId(0L);
            p.setPermName(m.name());
            p.setPermType(PermissionCatalog.TYPE_MODULE);
            p.setPermCode(m.code());
            p.setIcon(m.icon());
            p.setSortNo(m.sort());
            p.setStatus(1);
            p.setCreateTime(LocalDateTime.now());
            permissionMapper.insert(p);
            created++;
        }
        // 二级菜单
        int menuSort = 1;
        for (PermissionCatalog.Menu m : PermissionCatalog.MENUS) {
            SysPermission parent = permissionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                            .eq(SysPermission::getPermCode, m.moduleCode()));
            if (parent == null) {
                continue;
            }
            created += ensure(m.code(), m.name(), PermissionCatalog.TYPE_MENU, m.path(), m.icon(), parent.getId(), menuSort++);
            // 兼容别名
            int aliasSort = menuSort * 100;
            for (String alias : m.alias()) {
                created += ensure(m.code() + "#" + alias, m.name() + "（别名）",
                        PermissionCatalog.TYPE_ALIAS, alias, null, parent.getId(), aliasSort++);
            }
        }
        // 接口模式
        int apiSort = 900;
        for (PermissionCatalog.ApiPattern api : PermissionCatalog.API_PATTERNS) {
            SysPermission parent = permissionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                            .eq(SysPermission::getPermCode, api.permCode()));
            if (parent == null) {
                continue;
            }
            created += ensure("api:" + api.pattern(), api.remark(), PermissionCatalog.TYPE_API,
                    api.pattern(), null, parent.getId(), apiSort++);
        }
        if (created > 0) {
            System.out.println("[rich] 权限资源同步：新增 " + created + " 项");
        }
    }

    private int ensure(String code, String name, String type, String path, String icon,
                       Long parentId, int sort) {
        SysPermission old = permissionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getPermCode, code));
        if (old != null) {
            return 0;
        }
        SysPermission p = new SysPermission();
        p.setParentId(parentId);
        p.setPermName(name);
        p.setPermType(type);
        p.setPermCode(code);
        p.setPath(path);
        p.setIcon(icon);
        p.setSortNo(sort);
        p.setStatus(1);
        p.setCreateTime(LocalDateTime.now());
        permissionMapper.insert(p);
        return 1;
    }

    // ------------------------------------------------------------------ 角色

    private void syncRoles() {
        int sort = 1;
        for (RoleDef def : ROLE_DEFS) {
            SysRole old = roleMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleCode, def.code()));
            if (old == null) {
                SysRole role = new SysRole();
                role.setRoleCode(def.code());
                role.setRoleName(def.name());
                role.setRemark(def.remark());
                role.setStatus(1);
                role.setSortNo(sort++);
                role.setCreateTime(LocalDateTime.now());
                roleMapper.insert(role);
            }
        }
    }

    /** 超管始终持有全部资源（只增不减），保证新菜单上线后超管可用。 */
    private void grantSuperAdmin() {
        SysRole superRole = roleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, RbacService.SUPER_ROLE));
        if (superRole == null) {
            return;
        }
        Set<Long> owned = new LinkedHashSet<>(rolePermMapper.selectPermIdsByRoleId(superRole.getId()));
        for (SysPermission p : permissionMapper.selectList(null)) {
            if (owned.contains(p.getId())) {
                continue;
            }
            SysRolePermission rel = new SysRolePermission();
            rel.setRoleId(superRole.getId());
            rel.setPermId(p.getId());
            rolePermMapper.insert(rel);
        }
    }

    /** 预置职能角色在首次创建时授予默认权限，之后以页面调整为准。 */
    private void grantDefaultPermissions() {
        for (RoleDef def : ROLE_DEFS) {
            if ("*".equals(def.perms().get(0))) {
                continue;
            }
            Long roleId = findRoleId(def.code());
            if (roleId == null || !rolePermMapper.selectPermIdsByRoleId(roleId).isEmpty()) {
                continue;
            }
            Set<Long> permIds = new LinkedHashSet<>();
            for (String code : def.perms()) {
                permIds.addAll(collectByCodePrefix(code));
            }
            rbacService.setRolePermissions(roleId, new ArrayList<>(permIds));
        }
    }

    /**
     * 按权限码收集资源 ID。
     *
     * <p>传入 {@code "sale"} 命中整个销售模块（含其接口模式），传入 {@code "sale:ledger"}
     * 只命中该菜单及其别名。之后再做一轮向上/向下扩散，保证父子资源同进同退。
     */
    private List<Long> collectByCodePrefix(String code) {
        List<SysPermission> all = permissionMapper.selectList(null);
        Set<Long> hit = new LinkedHashSet<>();
        for (SysPermission p : all) {
            String pc = p.getPermCode();
            if (pc == null) {
                continue;
            }
            if (pc.equals(code) || pc.startsWith(code + ":")) {
                hit.add(p.getId());
            }
        }
        // 向下补齐后代：勾一棵子树里的某个点时，整条链都应可用
        boolean grew = true;
        while (grew) {
            grew = false;
            for (SysPermission p : all) {
                if (hit.contains(p.getId())) {
                    continue;
                }
                if (p.getParentId() != null && hit.contains(p.getParentId())) {
                    hit.add(p.getId());
                    grew = true;
                }
            }
        }
        return new ArrayList<>(hit);
    }

    private Long findRoleId(String roleCode) {
        SysRole role = roleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, roleCode));
        return role == null ? null : role.getId();
    }

    // ------------------------------------------------------------------ 账号

    /** admin 账号无条件绑定超管角色。 */
    private void bindAdmin() {
        SysUser admin = userMapper.findByUsername("admin");
        if (admin == null) {
            return;
        }
        Long superId = findRoleId(RbacService.SUPER_ROLE);
        if (superId == null) {
            return;
        }
        List<Long> owned = userRoleMapper.selectRoleIdsByUserId(admin.getId());
        if (owned != null && owned.contains(superId)) {
            return;
        }
        SysUserRole rel = new SysUserRole();
        rel.setUserId(admin.getId());
        rel.setRoleId(superId);
        userRoleMapper.insert(rel);
        rbacService.invalidate(admin.getId());
    }

    private void createDemoUsers() {
        for (String[] def : DEMO_USERS) {
            SysUser user = userMapper.findByUsername(def[0]);
            if (user == null) {
                user = new SysUser();
                user.setUsername(def[0]);
                user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                user.setNickname(def[1]);
                user.setStatus(1);
                user.setCreateTime(LocalDateTime.now());
                userMapper.insert(user);
            }
            Long roleId = findRoleId(def[2]);
            if (roleId == null) {
                continue;
            }
            List<Long> owned = userRoleMapper.selectRoleIdsByUserId(user.getId());
            if (owned != null && owned.contains(roleId)) {
                continue;
            }
            SysUserRole rel = new SysUserRole();
            rel.setUserId(user.getId());
            rel.setRoleId(roleId);
            userRoleMapper.insert(rel);
        }
    }
}
