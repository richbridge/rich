package fan.rich.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 左侧菜单节点。
 *
 * <p>一级为模块（{@code children} 非空），二级为具体页面。
 * 由后端按当前登录用户的权限集合裁剪后下发，前端直接用 Thymeleaf 渲染成
 * layuiAdmin 的响应式菜单结构与完整一致。
 */
public class MenuNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单标识，对应 {@code sys_permission.perm_code}，同时用作 data-name。 */
    private String code;

    /** 菜单名称。 */
    private String name;

    /** layui 图标样式类，如 {@code layui-icon-cart}。 */
    private String icon;

    /** 页面路径（一级模块为空）。 */
    private String path;

    /** 子菜单。 */
    private List<MenuNode> children = new ArrayList<>();

    /** 排序号。 */
    private Integer sortNo;

    public MenuNode() {
    }

    public MenuNode(String code, String name, String icon, Integer sortNo) {
        this.code = code;
        this.name = name;
        this.icon = icon;
        this.sortNo = sortNo;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<MenuNode> getChildren() {
        return children;
    }

    public void setChildren(List<MenuNode> children) {
        this.children = children;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
