package fan.rich.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import fan.rich.entity.SysPermission;
import fan.rich.mapper.SysPermissionMapper;
import fan.rich.service.SysPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限资源业务实现。
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
        implements SysPermissionService {

    @Override
    public List<SysPermission> listOrdered() {
        return list(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));
    }

    @Override
    public List<Long> descendantIds(Long parentId) {
        List<Long> result = new ArrayList<>();
        collectChildren(parentId, result);
        return result;
    }

    /** 递归收集子树节点 ID。 */
    private void collectChildren(Long parentId, List<Long> bucket) {
        List<SysPermission> children = list(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, parentId));
        for (SysPermission child : children) {
            bucket.add(child.getId());
            collectChildren(child.getId(), bucket);
        }
    }

    @Override
    public boolean isCodeAvailable(String permCode, Long excludeId) {
        if (!StringUtils.hasText(permCode)) {
            return false;
        }
        LambdaQueryWrapper<SysPermission> q = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode);
        if (excludeId != null) {
            q.ne(SysPermission::getId, excludeId);
        }
        return count(q) == 0;
    }
}
