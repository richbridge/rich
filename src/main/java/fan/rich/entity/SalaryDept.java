package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serializable;

/**
 * 工资核算部门。
 *
 * <p>下拨与上报的核算主体：经营层、各科室、车间以及外聘人员。部门维护在册人数与
 * 月度工资定额，作为工资下拨时向各部门分解额度的依据。
 */
@TableName("salary_dept")
public class SalaryDept implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：启用。 */
    public static final String STATUS_ON = "启用";

    /** 状态：停用。 */
    public static final String STATUS_OFF = "停用";

    /** 部门类型：管理部门。 */
    public static final String TYPE_MANAGE = "管理部门";

    /** 部门类型：生产车间。 */
    public static final String TYPE_WORKSHOP = "生产车间";

    /** 部门类型：辅助部门。 */
    public static final String TYPE_SUPPORT = "辅助部门";

    /** 部门类型：经营层。 */
    public static final String TYPE_EXEC = "经营层";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门编码，如 BM-01。 */
    private String deptCode;

    /** 部门名称。 */
    private String deptName;

    /** 部门类型：管理部门 / 生产车间 / 辅助部门 / 经营层。 */
    private String deptType;

    /** 部门负责人。 */
    private String leader;

    /** 在册人数。 */
    private Integer headcount;

    /** 月度工资定额(元)。 */
    private BigDecimal monthQuota;

    /** 状态：启用 / 停用。 */
    private String status;

    /** 备注。 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptType() {
        return deptType;
    }

    public void setDeptType(String deptType) {
        this.deptType = deptType;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }

    public BigDecimal getMonthQuota() {
        return monthQuota;
    }

    public void setMonthQuota(BigDecimal monthQuota) {
        this.monthQuota = monthQuota;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
