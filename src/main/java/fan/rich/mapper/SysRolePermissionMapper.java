package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysRolePermission Mapper（由 mapper 包统一扫描注册）。
 */
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
    /** 查询某角色的全部权限 ID。 */
    @Select("SELECT perm_id FROM sys_auth_role_perm WHERE role_id = #{roleId}")
    List<Long> selectPermIdsByRoleId(@Param("roleId") Long roleId);

    /** 删除某角色的全部权限。 */
    @Delete("DELETE FROM sys_auth_role_perm WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
