package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysUserRole Mapper（由 mapper 包统一扫描注册）。
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    /** 查询某用户的全部角色 ID。 */
    @Select("SELECT role_id FROM sys_auth_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 删除某用户的全部角色。 */
    @Delete("DELETE FROM sys_auth_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 统计持有某角色的用户数，用于删除角色前的占用校验。 */
    @Select("SELECT COUNT(*) FROM sys_auth_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);
}
