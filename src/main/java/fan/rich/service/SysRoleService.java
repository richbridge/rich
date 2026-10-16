package fan.rich.service;

import com.baomidou.mybatisplus.spring.service.IService;
import fan.rich.entity.SysRole;

/**
 * 系统角色业务接口。
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 校验角色编码是否可用。
     *
     * @param roleCode 角色编码
     * @param excludeId 排除的角色 ID（编辑自身时传自身 ID），新增传 null
     * @return 未被占用返回 true
     */
    boolean isCodeAvailable(String roleCode, Long excludeId);
}
