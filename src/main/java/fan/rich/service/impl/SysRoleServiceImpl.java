package fan.rich.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.SysRole;
import fan.rich.mapper.SysRoleMapper;
import fan.rich.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统角色业务实现。
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    @Override
    public boolean isCodeAvailable(String roleCode, Long excludeId) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode);
        if (excludeId != null) {
            q.ne(SysRole::getId, excludeId);
        }
        return count(q) == 0;
    }
}
