package fan.rich.service;

import com.baomidou.mybatisplus.spring.service.IService;
import fan.rich.entity.SysPermission;

import java.util.List;

/**
 * 权限资源（菜单 / 按钮 / 接口）业务接口。
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 查询全部权限并按 sort_no、id 排序。
     */
    List<SysPermission> listOrdered();

    /**
     * 查询某棵子树的全部后代（含自身之外的子节点，递归）。
     */
    List<Long> descendantIds(Long parentId);

    /**
     * 校验权限编码是否可用。
     */
    boolean isCodeAvailable(String permCode, Long excludeId);
}
