package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 案卷（卷盒）。
 */
@TableName("arch_volume")
public class ArchVolume implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 案卷号，如 2026-WS-0001。 */
    private String volumeNo;

    /** 案卷题名。 */
    private String title;

    /** 档案门类。 */
    private String category;

    /** 所属年度。 */
    private Integer year;

    /** 保管期限：永久 / 30年 / 10年 / 5年。 */
    private String retention;

    /** 密级：公开 / 内部 / 秘密 / 机密。 */
    private String security;

    /** 卷内文件起始日期。 */
    private LocalDate startDate;

    /** 卷内文件截止日期。 */
    private LocalDate endDate;

    /** 卷内文件件数，由明细汇总回填。 */
    private Integer fileCount;

    /** 总页数，由明细汇总回填。 */
    private Integer pageCount;

    /** 载体：纸质 / 电子 / 声像 / 实物。 */
    private String carrier;

    /** 归档部门。 */
    private String archiveDept;

    /** 立卷人 / 归档人。 */
    private String archivist;

    /** 归档日期。 */
    private LocalDate archiveDate;

    /** 存放位置编号，形如 A-01-1-03（办公室-柜号-层号-文件夹号）。 */
    private String locationCode;

    /** 存放位置完整路径（冗余文本，便于检索），如 财务科档案室 / 1号柜 / 第1层 / 第02夹。 */
    private String locationName;

    /** 存放办公室 / 档案室（冗余，便于按办公室筛选）。 */
    private String officeRoom;

    /** 存放柜号（冗余，便于按柜筛选）。 */
    private String cabinetNo;

    /** 存放层号（冗余）。 */
    private String layerNo;

    /** 存放文件夹 / 档案盒号（冗余，便于按文件夹检索）。 */
    private String folderNo;

    /** 盒号。 */
    private String boxNo;

    /** 二维码码值，供扫码定位案卷。 */
    private String qrCode;

    /** 累计借阅次数。 */
    private Integer borrowTimes;

    /** 状态：在库 / 借阅中 / 封存 / 已销毁。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVolumeNo() {
        return volumeNo;
    }

    public void setVolumeNo(String volumeNo) {
        this.volumeNo = volumeNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getArchiveDept() {
        return archiveDept;
    }

    public void setArchiveDept(String archiveDept) {
        this.archiveDept = archiveDept;
    }

    public String getArchivist() {
        return archivist;
    }

    public void setArchivist(String archivist) {
        this.archivist = archivist;
    }

    public LocalDate getArchiveDate() {
        return archiveDate;
    }

    public void setArchiveDate(LocalDate archiveDate) {
        this.archiveDate = archiveDate;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getOfficeRoom() {
        return officeRoom;
    }

    public void setOfficeRoom(String officeRoom) {
        this.officeRoom = officeRoom;
    }

    public String getCabinetNo() {
        return cabinetNo;
    }

    public void setCabinetNo(String cabinetNo) {
        this.cabinetNo = cabinetNo;
    }

    public String getLayerNo() {
        return layerNo;
    }

    public void setLayerNo(String layerNo) {
        this.layerNo = layerNo;
    }

    public String getFolderNo() {
        return folderNo;
    }

    public void setFolderNo(String folderNo) {
        this.folderNo = folderNo;
    }

    public String getBoxNo() {
        return boxNo;
    }

    public void setBoxNo(String boxNo) {
        this.boxNo = boxNo;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public Integer getBorrowTimes() {
        return borrowTimes;
    }

    public void setBorrowTimes(Integer borrowTimes) {
        this.borrowTimes = borrowTimes;
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
