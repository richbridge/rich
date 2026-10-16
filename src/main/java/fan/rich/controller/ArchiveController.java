package fan.rich.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.ArchBorrow;
import fan.rich.entity.ArchCategory;
import fan.rich.entity.ArchDestroy;
import fan.rich.entity.ArchDestroyItem;
import fan.rich.entity.ArchFile;
import fan.rich.entity.ArchLocation;
import fan.rich.entity.ArchVolume;
import fan.rich.service.ArchBorrowService;
import fan.rich.service.ArchCategoryService;
import fan.rich.service.ArchDestroyItemService;
import fan.rich.service.ArchDestroyService;
import fan.rich.service.ArchFileService;
import fan.rich.service.ArchLocationService;
import fan.rich.service.ArchiveStatsService;
import fan.rich.service.ArchVolumeService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 档案管理模块接口。
 *
 * <p>分五组：
 * <ul>
 *   <li><b>基础档案</b>：门类、存放位置的 CRUD</li>
 *   <li><b>案卷与文件</b>：案卷 CRUD、卷内文件 CRUD，案卷的件数/页数由文件明细汇总回填</li>
 *   <li><b>借阅</b>：登记（借出）、归还、续借，逾期状态按当天日期动态计算</li>
 *   <li><b>销毁</b>：清册 CRUD、加入待销毁案卷、审批、执行（执行后案卷置「已销毁」）</li>
 *   <li><b>统计</b>：总览 KPI、分布、趋势、提醒</li>
 * </ul>
 *
 * <p>业务约定：所有会改变案卷或借阅状态的操作之后，都调
 * {@link ArchiveStatsService} 的刷新方法重算派生数据，避免统计与明细对不上。
 */
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final ArchCategoryService categoryService;
    private final ArchLocationService locationService;
    private final ArchVolumeService volumeService;
    private final ArchFileService fileService;
    private final ArchBorrowService borrowService;
    private final ArchDestroyService destroyService;
    private final ArchDestroyItemService destroyItemService;
    private final ArchiveStatsService statsService;

    public ArchiveController(ArchCategoryService categoryService,
                             ArchLocationService locationService,
                             ArchVolumeService volumeService,
                             ArchFileService fileService,
                             ArchBorrowService borrowService,
                             ArchDestroyService destroyService,
                             ArchDestroyItemService destroyItemService,
                             ArchiveStatsService statsService) {
        this.categoryService = categoryService;
        this.locationService = locationService;
        this.volumeService = volumeService;
        this.fileService = fileService;
        this.borrowService = borrowService;
        this.destroyService = destroyService;
        this.destroyItemService = destroyItemService;
        this.statsService = statsService;
    }

    // ================================================================ 门类

    /** 门类列表（不分页，供下拉与维护页使用）。 */
    @GetMapping("/category/list")
    public R categoryList() {
        LambdaQueryWrapper<ArchCategory> w = new LambdaQueryWrapper<>();
        w.orderByAsc(ArchCategory::getSeq).orderByAsc(ArchCategory::getId);
        return R.ok(categoryService.list(w));
    }

    /** 新增或修改门类。 */
    @PostMapping("/category/save")
    public R categorySave(@RequestBody ArchCategory entity) {
        categoryService.saveOrUpdate(entity);
        return R.ok(entity.getId());
    }

    /** 删除门类。 */
    @PostMapping("/category/delete")
    public R categoryDelete(@RequestParam Long id) {
        categoryService.removeById(id);
        return R.ok();
    }

    // ================================================================ 存放位置

    /** 存放位置列表（可按办公室 / 柜号 / 文件夹 / 关键词过滤）。 */
    @GetMapping("/location/list")
    public R locationList(@RequestParam(required = false) String keywords,
                          @RequestParam(required = false) String officeRoom,
                          @RequestParam(required = false) String cabinetNo,
                          @RequestParam(required = false) String layerNo,
                          @RequestParam(required = false) String folderNo) {
        LambdaQueryWrapper<ArchLocation> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            w.and(q -> q.like(ArchLocation::getLocationCode, keywords)
                    .or().like(ArchLocation::getOfficeRoom, keywords)
                    .or().like(ArchLocation::getCabinetNo, keywords)
                    .or().like(ArchLocation::getFolderNo, keywords));
        }
        w.eq(StringUtils.hasText(officeRoom), ArchLocation::getOfficeRoom, officeRoom)
                .eq(StringUtils.hasText(cabinetNo), ArchLocation::getCabinetNo, cabinetNo)
                .eq(StringUtils.hasText(layerNo), ArchLocation::getLayerNo, layerNo)
                .eq(StringUtils.hasText(folderNo), ArchLocation::getFolderNo, folderNo)
                .orderByAsc(ArchLocation::getOfficeRoom)
                .orderByAsc(ArchLocation::getCabinetNo)
                .orderByAsc(ArchLocation::getLayerNo)
                .orderByAsc(ArchLocation::getFolderNo);
        List<ArchLocation> list = locationService.list(w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ArchLocation l : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("locationCode", l.getLocationCode());
            m.put("officeRoom", l.getOfficeRoom());
            m.put("cabinetNo", l.getCabinetNo());
            m.put("layerNo", l.getLayerNo());
            m.put("folderNo", l.getFolderNo());
            m.put("capacity", l.getCapacity());
            m.put("usedCount", l.getUsedCount());
            m.put("remark", l.getRemark());
            m.put("locationPath", locationName(l.getLocationCode()));
            rows.add(m);
        }
        return R.ok(rows);
    }

    /** 存放位置层级树：办公室 → 柜子 → 层 → 文件夹。 */
    @GetMapping("/location/tree")
    public R locationTree() {
        return R.ok(statsService.locationTree());
    }

    /** 按办公室汇总的位置容量概览。 */
    @GetMapping("/location/office-stats")
    public R officeStats() {
        return R.ok(statsService.officeStats());
    }

    /** 位置级联选项：办公室 / 柜号 / 层号 / 文件夹（含各自归属关系，供前端联动过滤）。 */
    @GetMapping("/location/options")
    public R locationOptions() {
        List<ArchLocation> all = locationService.list(new LambdaQueryWrapper<ArchLocation>()
                .orderByAsc(ArchLocation::getOfficeRoom)
                .orderByAsc(ArchLocation::getCabinetNo)
                .orderByAsc(ArchLocation::getLayerNo)
                .orderByAsc(ArchLocation::getFolderNo));
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> offices = new ArrayList<>();
        Set<String> seenOffice = new LinkedHashSet<>();
        for (ArchLocation l : all) {
            if (l.getOfficeRoom() == null || !seenOffice.add(l.getOfficeRoom())) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("officeRoom", l.getOfficeRoom());
            o.put("officeCode", officeCodeOf(l.getLocationCode()));
            o.put("code", l.getLocationCode());
            offices.add(o);
        }
        data.put("offices", offices);
        data.put("locations", all.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("locationCode", l.getLocationCode());
            m.put("officeRoom", l.getOfficeRoom());
            m.put("cabinetNo", l.getCabinetNo());
            m.put("layerNo", l.getLayerNo());
            m.put("folderNo", l.getFolderNo());
            m.put("locationPath", locationName(l.getLocationCode()));
            return m;
        }).toList());
        return R.ok(data);
    }

    /** 新增或修改存放位置；位置编号留空时按「办公室-柜-层-文件夹」自动生成。 */
    @PostMapping("/location/save")
    public R locationSave(@RequestBody ArchLocation entity) {
        if (!StringUtils.hasText(entity.getOfficeRoom())) {
            entity.setOfficeRoom(entity.getRoomName());
        }
        entity.setRoomName(entity.getOfficeRoom());
        if (!StringUtils.hasText(entity.getLocationCode())) {
            entity.setLocationCode(buildLocationCode(entity.getOfficeRoom(),
                    entity.getCabinetNo(), entity.getLayerNo(), entity.getFolderNo()));
        }
        locationService.saveOrUpdate(entity);
        statsService.refreshLocationUsed();
        statsService.refreshVolumeLocation();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("locationCode", entity.getLocationCode());
        return R.ok(result);
    }

    /** 删除存放位置。 */
    @PostMapping("/location/delete")
    public R locationDelete(@RequestParam Long id) {
        locationService.removeById(id);
        return R.ok();
    }

    // ================================================================ 案卷

    /** 案卷分页列表。 */
    @GetMapping("/volume/list")
    public R volumeList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "20") long limit,
                        @RequestParam(required = false) String keywords,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String retention,
                        @RequestParam(required = false) String locationCode,
                        @RequestParam(required = false) String officeRoom,
                        @RequestParam(required = false) String cabinetNo,
                        @RequestParam(required = false) String folderNo,
                        @RequestParam(required = false) Integer year) {
        LambdaQueryWrapper<ArchVolume> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            w.and(q -> q.like(ArchVolume::getTitle, keywords)
                    .or().like(ArchVolume::getVolumeNo, keywords)
                    .or().like(ArchVolume::getArchivist, keywords)
                    .or().like(ArchVolume::getQrCode, keywords)
                    .or().like(ArchVolume::getLocationName, keywords));
        }
        w.eq(StringUtils.hasText(category), ArchVolume::getCategory, category)
                .eq(StringUtils.hasText(status), ArchVolume::getStatus, status)
                .eq(StringUtils.hasText(retention), ArchVolume::getRetention, retention)
                .eq(StringUtils.hasText(locationCode), ArchVolume::getLocationCode, locationCode)
                .eq(StringUtils.hasText(officeRoom), ArchVolume::getOfficeRoom, officeRoom)
                .eq(StringUtils.hasText(cabinetNo), ArchVolume::getCabinetNo, cabinetNo)
                .eq(StringUtils.hasText(folderNo), ArchVolume::getFolderNo, folderNo)
                .eq(year != null, ArchVolume::getYear, year)
                .orderByDesc(ArchVolume::getYear)
                .orderByAsc(ArchVolume::getVolumeNo);
        IPage<ArchVolume> result = volumeService.page(new Page<>(page, limit), w);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改案卷；位置层级字段由存放位置自动补全。 */
    @PostMapping("/volume/save")
    public R volumeSave(@RequestBody ArchVolume entity) {
        boolean isNew = entity.getId() == null;
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("在库");
        }
        if (!StringUtils.hasText(entity.getQrCode())) {
            entity.setQrCode("VOL-" + System.currentTimeMillis());
        }
        applyVolumeLocation(entity);
        if (entity.getYear() == null && entity.getArchiveDate() != null) {
            entity.setYear(entity.getArchiveDate().getYear());
        }
        volumeService.saveOrUpdate(entity);
        statsService.refreshLocationUsed();
        if (isNew) {
            statsService.refreshVolumeStats(entity.getId());
        }
        return R.ok(entity.getId());
    }

    /** 删除案卷（连同卷内文件）。 */
    @PostMapping("/volume/delete")
    public R volumeDelete(@RequestParam Long id) {
        fileService.remove(new LambdaQueryWrapper<ArchFile>().eq(ArchFile::getVolumeId, id));
        volumeService.removeById(id);
        statsService.refreshLocationUsed();
        return R.ok();
    }

    /** 某案卷的卷内文件。 */
    @GetMapping("/volume/files")
    public R volumeFiles(@RequestParam Long volumeId) {
        LambdaQueryWrapper<ArchFile> w = new LambdaQueryWrapper<>();
        w.eq(ArchFile::getVolumeId, volumeId).orderByAsc(ArchFile::getItemNo);
        return R.ok(fileService.list(w));
    }

    /** 案卷筛选下拉：门类、状态、保管期限、位置（含层级）、年度。 */
    @GetMapping("/volume/options")
    public R volumeOptions() {
        Map<String, Object> data = new HashMap<>();
        data.put("categories", categoryService.list().stream().map(ArchCategory::getCategoryName).toList());
        data.put("statuses", List.of("在库", "借阅中", "封存", "已销毁"));
        data.put("retentions", List.of("永久", "30年", "10年", "5年"));
        List<ArchLocation> locations = locationService.list(new LambdaQueryWrapper<ArchLocation>()
                .orderByAsc(ArchLocation::getOfficeRoom)
                .orderByAsc(ArchLocation::getCabinetNo)
                .orderByAsc(ArchLocation::getLayerNo)
                .orderByAsc(ArchLocation::getFolderNo));
        data.put("locations", locations.stream().map(ArchLocation::getLocationCode).toList());
        data.put("locationOptions", locations.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("locationCode", l.getLocationCode());
            m.put("officeRoom", l.getOfficeRoom());
            m.put("cabinetNo", l.getCabinetNo());
            m.put("layerNo", l.getLayerNo());
            m.put("folderNo", l.getFolderNo());
            m.put("locationPath", locationName(l.getLocationCode()));
            return m;
        }).toList());
        data.put("offices", distinct(locations, ArchLocation::getOfficeRoom));
        data.put("cabinets", distinct(locations, ArchLocation::getCabinetNo));
        data.put("folders", distinct(locations, ArchLocation::getFolderNo));
        data.put("years", new LinkedHashSet<>(volumeService.list().stream()
                .map(ArchVolume::getYear).filter(y -> y != null).sorted().toList()));
        return R.ok(data);
    }

    // ================================================================ 卷内文件

    /** 卷内文件检索：可按关键词跨卷查，也可按案卷过滤。 */
    @GetMapping("/file/list")
    public R fileList(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "20") long limit,
                      @RequestParam(required = false) String keywords,
                      @RequestParam(required = false) Long volumeId,
                      @RequestParam(required = false) String category,
                      @RequestParam(required = false) String carrier) {
        LambdaQueryWrapper<ArchFile> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            w.like(ArchFile::getTitle, keywords)
                    .or().like(ArchFile::getDocNo, keywords)
                    .or().like(ArchFile::getVolumeNo, keywords)
                    .or().like(ArchFile::getResponsible, keywords);
        }
        w.eq(volumeId != null, ArchFile::getVolumeId, volumeId)
                .eq(StringUtils.hasText(carrier), ArchFile::getCarrier, carrier);
        if (StringUtils.hasText(category)) {
            // 按门类过滤：先取该门类的案卷号
            List<Long> ids = volumeService.list(new LambdaQueryWrapper<ArchVolume>()
                    .eq(ArchVolume::getCategory, category)).stream().map(ArchVolume::getId).toList();
            if (ids.isEmpty()) {
                return R.table(0, new ArrayList<>());
            }
            w.in(ArchFile::getVolumeId, ids);
        }
        w.orderByDesc(ArchFile::getDocDate).orderByAsc(ArchFile::getItemNo);
        IPage<ArchFile> result = fileService.page(new Page<>(page, limit), w);
        return R.table(result.getTotal(), result.getRecords());
    }

    /** 新增或修改卷内文件；保存后回写所属案卷的件数与页数。 */
    @PostMapping("/file/save")
    public R fileSave(@RequestBody ArchFile entity) {
        fileService.saveOrUpdate(entity);
        if (entity.getVolumeId() != null) {
            statsService.refreshVolumeStats(entity.getVolumeId());
        }
        return R.ok(entity.getId());
    }

    /** 删除卷内文件；删除后回写所属案卷的件数与页数。 */
    @PostMapping("/file/delete")
    public R fileDelete(@RequestParam Long id) {
        ArchFile file = fileService.getById(id);
        fileService.removeById(id);
        if (file != null && file.getVolumeId() != null) {
            statsService.refreshVolumeStats(file.getVolumeId());
        }
        return R.ok();
    }

    // ================================================================ 借阅

    /** 借阅分页列表。 */
    @GetMapping("/borrow/list")
    public R borrowList(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "20") long limit,
                        @RequestParam(required = false) String keywords,
                        @RequestParam(required = false) String status) {
        statsService.refreshBorrowStatus();
        LambdaQueryWrapper<ArchBorrow> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            w.like(ArchBorrow::getVolumeTitle, keywords)
                    .or().like(ArchBorrow::getVolumeNo, keywords)
                    .or().like(ArchBorrow::getBorrower, keywords)
                    .or().like(ArchBorrow::getBorrowNo, keywords);
        }
        w.eq(StringUtils.hasText(status), ArchBorrow::getStatus, status)
                .orderByDesc(ArchBorrow::getBorrowDate);
        IPage<ArchBorrow> result = borrowService.page(new Page<>(page, limit), w);
        return R.table(result.getTotal(), result.getRecords());
    }

    /**
     * 借阅登记（借出）。
     *
     * <p>校验：案卷必须存在且状态为「在库」——借阅中/封存/已销毁的案卷不允许再借出，
     * 避免出现同一卷同时被两个人借走的账面错误。
     */
    @PostMapping("/borrow/create")
    public R borrowCreate(@RequestBody ArchBorrow entity) {
        ArchVolume volume = volumeService.getById(entity.getVolumeId());
        if (volume == null) {
            return R.fail("案卷不存在");
        }
        if (!"在库".equals(volume.getStatus())) {
            return R.fail("案卷当前状态为「" + volume.getStatus() + "」，不可借出");
        }
        entity.setVolumeNo(volume.getVolumeNo());
        entity.setVolumeTitle(volume.getTitle());
        if (entity.getBorrowDate() == null) {
            entity.setBorrowDate(LocalDate.now());
        }
        if (entity.getDueDate() == null) {
            entity.setDueDate(entity.getBorrowDate().plusDays(15));
        }
        entity.setReturnDate(null);
        entity.setStatus(entity.getDueDate().isBefore(LocalDate.now()) ? "逾期" : "借阅中");
        if (!StringUtils.hasText(entity.getBorrowNo())) {
            entity.setBorrowNo("JY" + LocalDate.now().getYear()
                    + String.format("-%03d", borrowService.count() + 1));
        }
        borrowService.save(entity);
        statsService.refreshVolumeBorrow(volume.getId());
        statsService.refreshBorrowStatus();
        return R.ok(entity.getId());
    }

    /** 归还：写入归还日期，逾期天数按实际归还日计算。 */
    @PostMapping("/borrow/return")
    public R borrowReturn(@RequestParam Long id,
                          @RequestParam(required = false) String returnDate,
                          @RequestParam(required = false) String remark) {
        ArchBorrow entity = borrowService.getById(id);
        if (entity == null) {
            return R.fail("借阅记录不存在");
        }
        if (entity.getReturnDate() != null) {
            return R.fail("该借阅记录已归还");
        }
        entity.setReturnDate(StringUtils.hasText(returnDate)
                ? LocalDate.parse(returnDate) : LocalDate.now());
        entity.setStatus("已归还");
        if (StringUtils.hasText(remark)) {
            entity.setRemark(remark);
        }
        borrowService.updateById(entity);
        statsService.refreshBorrowStatus();
        if (entity.getVolumeId() != null) {
            statsService.refreshVolumeBorrow(entity.getVolumeId());
        }
        return R.ok();
    }

    /** 续借：应还日期顺延，天数由前端传入（默认 15 天）。 */
    @PostMapping("/borrow/renew")
    public R borrowRenew(@RequestParam Long id,
                         @RequestParam(required = false, defaultValue = "15") int days) {
        ArchBorrow entity = borrowService.getById(id);
        if (entity == null) {
            return R.fail("借阅记录不存在");
        }
        if (entity.getReturnDate() != null) {
            return R.fail("已归还的记录不能续借");
        }
        LocalDate base = entity.getDueDate() != null && entity.getDueDate().isAfter(LocalDate.now())
                ? entity.getDueDate() : LocalDate.now();
        entity.setDueDate(base.plusDays(days));
        entity.setStatus("借阅中");
        borrowService.updateById(entity);
        statsService.refreshBorrowStatus();
        return R.ok(entity.getDueDate());
    }

    /** 标记丢失（异常状态，不参与逾期判定）。 */
    @PostMapping("/borrow/lost")
    public R borrowLost(@RequestParam Long id, @RequestParam(required = false) String remark) {
        ArchBorrow entity = borrowService.getById(id);
        if (entity == null) {
            return R.fail("借阅记录不存在");
        }
        entity.setStatus("已丢失");
        entity.setRemark(StringUtils.hasText(remark) ? remark : "档案遗失，已登记");
        borrowService.updateById(entity);
        return R.ok();
    }

    /** 删除借阅记录。 */
    @PostMapping("/borrow/delete")
    public R borrowDelete(@RequestParam Long id) {
        ArchBorrow entity = borrowService.getById(id);
        borrowService.removeById(id);
        if (entity != null && entity.getVolumeId() != null) {
            statsService.refreshVolumeBorrow(entity.getVolumeId());
        }
        return R.ok();
    }

    // ================================================================ 销毁

    /** 销毁清册列表。 */
    @GetMapping("/destroy/list")
    public R destroyList(@RequestParam(required = false) String keywords,
                         @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ArchDestroy> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keywords)) {
            w.like(ArchDestroy::getTitle, keywords)
                    .or().like(ArchDestroy::getDestroyNo, keywords);
        }
        w.eq(StringUtils.hasText(status), ArchDestroy::getStatus, status)
                .orderByDesc(ArchDestroy::getApplyDate);
        return R.ok(destroyService.list(w));
    }

    /** 新增或修改销毁清册。 */
    @PostMapping("/destroy/save")
    public R destroySave(@RequestBody ArchDestroy entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("草稿");
        }
        destroyService.saveOrUpdate(entity);
        statsService.refreshDestroyStats(entity.getId());
        return R.ok(entity.getId());
    }

    /** 删除销毁清册（连同明细）。 */
    @PostMapping("/destroy/delete")
    public R destroyDelete(@RequestParam Long id) {
        destroyItemService.remove(new LambdaQueryWrapper<ArchDestroyItem>()
                .eq(ArchDestroyItem::getDestroyId, id));
        destroyService.removeById(id);
        return R.ok();
    }

    /** 销毁清册明细。 */
    @GetMapping("/destroy/items")
    public R destroyItems(@RequestParam Long destroyId) {
        LambdaQueryWrapper<ArchDestroyItem> w = new LambdaQueryWrapper<>();
        w.eq(ArchDestroyItem::getDestroyId, destroyId).orderByAsc(ArchDestroyItem::getId);
        return R.ok(destroyItemService.list(w));
    }

    /** 加入待销毁案卷：按案卷ID批量加入，重复加入自动跳过。 */
    @PostMapping("/destroy/add-items")
    public R destroyAddItems(@RequestParam Long destroyId, @RequestBody List<Long> volumeIds) {
        ArchDestroy destroy = destroyService.getById(destroyId);
        if (destroy == null) {
            return R.fail("销毁清册不存在");
        }
        if (!"草稿".equals(destroy.getStatus()) && !"待审批".equals(destroy.getStatus())) {
            return R.fail("当前状态不可调整明细");
        }
        int added = 0;
        for (Long volumeId : volumeIds) {
            Long exists = destroyItemService.count(new LambdaQueryWrapper<ArchDestroyItem>()
                    .eq(ArchDestroyItem::getDestroyId, destroyId)
                    .eq(ArchDestroyItem::getVolumeId, volumeId));
            if (exists > 0) {
                continue;
            }
            ArchVolume volume = volumeService.getById(volumeId);
            if (volume == null) {
                continue;
            }
            ArchDestroyItem item = new ArchDestroyItem();
            item.setDestroyId(destroyId);
            item.setVolumeId(volume.getId());
            item.setVolumeNo(volume.getVolumeNo());
            item.setVolumeTitle(volume.getTitle());
            item.setCategory(volume.getCategory());
            item.setRetention(volume.getRetention());
            item.setPageCount(volume.getPageCount());
            destroyItemService.save(item);
            added++;
        }
        statsService.refreshDestroyStats(destroyId);
        return R.ok("已加入 " + added + " 卷");
    }

    /** 移除销毁明细。 */
    @PostMapping("/destroy/remove-item")
    public R destroyRemoveItem(@RequestParam Long id) {
        ArchDestroyItem item = destroyItemService.getById(id);
        destroyItemService.removeById(id);
        if (item != null) {
            statsService.refreshDestroyStats(item.getDestroyId());
        }
        return R.ok();
    }

    /** 提交审批 / 审批通过。 */
    @PostMapping("/destroy/approve")
    public R destroyApprove(@RequestParam Long id,
                            @RequestParam(required = false) String approver) {
        ArchDestroy entity = destroyService.getById(id);
        if (entity == null) {
            return R.fail("销毁清册不存在");
        }
        if ("草稿".equals(entity.getStatus())) {
            entity.setStatus("待审批");
            if (entity.getApplyDate() == null) {
                entity.setApplyDate(LocalDate.now());
            }
        } else if ("待审批".equals(entity.getStatus())) {
            entity.setStatus("已审批");
            entity.setApprover(StringUtils.hasText(approver) ? approver : "李雲钊");
            entity.setApproveDate(LocalDate.now());
            // 审批通过后案卷先封存，等待实物销毁
            for (ArchDestroyItem item : destroyItemService.list(
                    new LambdaQueryWrapper<ArchDestroyItem>().eq(ArchDestroyItem::getDestroyId, id))) {
                ArchVolume v = volumeService.getById(item.getVolumeId());
                if (v != null && "在库".equals(v.getStatus())) {
                    v.setStatus("封存");
                    volumeService.updateById(v);
                }
            }
        } else {
            return R.fail("当前状态不可审批");
        }
        destroyService.updateById(entity);
        return R.ok(entity.getStatus());
    }

    /** 执行销毁：清册置「已销毁」，明细对应案卷置「已销毁」。 */
    @PostMapping("/destroy/execute")
    public R destroyExecute(@RequestParam Long id,
                            @RequestParam(required = false) String destroyDate,
                            @RequestParam(required = false) String witness,
                            @RequestParam(required = false) String method) {
        ArchDestroy entity = destroyService.getById(id);
        if (entity == null) {
            return R.fail("销毁清册不存在");
        }
        if (!"已审批".equals(entity.getStatus())) {
            return R.fail("清册需先审批通过才能销毁");
        }
        entity.setStatus("已销毁");
        entity.setDestroyDate(StringUtils.hasText(destroyDate)
                ? LocalDate.parse(destroyDate) : LocalDate.now());
        if (StringUtils.hasText(witness)) {
            entity.setWitness(witness);
        }
        if (StringUtils.hasText(method)) {
            entity.setMethod(method);
        }
        destroyService.updateById(entity);
        int n = 0;
        for (ArchDestroyItem item : destroyItemService.list(
                new LambdaQueryWrapper<ArchDestroyItem>().eq(ArchDestroyItem::getDestroyId, id))) {
            ArchVolume v = volumeService.getById(item.getVolumeId());
            if (v != null) {
                v.setStatus("已销毁");
                volumeService.updateById(v);
                n++;
            }
        }
        statsService.refreshDestroyStats(id);
        statsService.refreshLocationUsed();
        return R.ok("已销毁 " + n + " 卷");
    }

    // ================================================================ 统计

    /** 档案总览 KPI。 */
    @GetMapping("/overview")
    public R overview() {
        statsService.refreshBorrowStatus();
        return R.ok(statsService.overview());
    }

    /** 门类分布。 */
    @GetMapping("/stats/category")
    public R statsCategory() {
        return R.ok(statsService.categoryStats());
    }

    /** 年度分布。 */
    @GetMapping("/stats/year")
    public R statsYear() {
        return R.ok(statsService.yearStats());
    }

    /** 保管期限分布。 */
    @GetMapping("/stats/retention")
    public R statsRetention() {
        return R.ok(statsService.retentionStats());
    }

    /** 借阅趋势（近 6 个月借出 / 归还）。 */
    @GetMapping("/stats/borrow-trend")
    public R statsBorrowTrend() {
        return R.ok(statsService.borrowTrend());
    }

    /** 提醒：逾期未还、即将到期、待审批销毁、位置将满。 */
    @GetMapping("/reminders")
    public R reminders() {
        return R.ok(statsService.reminders());
    }

    /** 刷新全部派生数据（借阅逾期、案卷件数页数、位置占用、销毁统计）。 */
    @PostMapping("/refresh-status")
    public R refreshStatus() {
        statsService.refreshAll();
        return R.ok();
    }

    // ================================================================ 工具

    /** 存放位置完整路径：办公室 / 柜号 / 层号 / 文件夹号。 */
    private String locationName(String code) {
        ArchLocation loc = getLocation(code);
        if (loc == null) {
            return code;
        }
        return join(loc.getOfficeRoom(), loc.getCabinetNo(), loc.getLayerNo(), loc.getFolderNo());
    }

    private ArchLocation getLocation(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return locationService.getOne(new LambdaQueryWrapper<ArchLocation>()
                .eq(ArchLocation::getLocationCode, code).last("LIMIT 1"));
    }

    /**
     * 按位置档案补全案卷的层级冗余字段。
     *
     * <p>案卷只填「办公室 / 柜 / 层 / 文件夹」即可，位置编号由系统拼装；
     * 若前端直接传了位置编号，则反向用位置档案覆盖层级字段，保证两者一致。
     */
    private void applyVolumeLocation(ArchVolume entity) {
        ArchLocation loc = getLocation(entity.getLocationCode());
        if (loc == null && StringUtils.hasText(entity.getOfficeRoom())
                && StringUtils.hasText(entity.getCabinetNo())) {
            // 未指定具体文件夹：取该柜下第一个位置兜底
            loc = locationService.getOne(new LambdaQueryWrapper<ArchLocation>()
                    .eq(ArchLocation::getOfficeRoom, entity.getOfficeRoom())
                    .eq(ArchLocation::getCabinetNo, entity.getCabinetNo())
                    .orderByAsc(ArchLocation::getLayerNo)
                    .orderByAsc(ArchLocation::getFolderNo)
                    .last("LIMIT 1"));
        }
        if (loc == null) {
            // 位置留空时允许手工写地址，至少把文本存下来
            if (StringUtils.hasText(entity.getLocationCode())) {
                entity.setLocationName(entity.getLocationCode());
            }
            return;
        }
        entity.setLocationCode(loc.getLocationCode());
        entity.setOfficeRoom(loc.getOfficeRoom());
        entity.setCabinetNo(loc.getCabinetNo());
        entity.setLayerNo(loc.getLayerNo());
        entity.setFolderNo(loc.getFolderNo());
        entity.setLocationName(join(loc.getOfficeRoom(), loc.getCabinetNo(),
                loc.getLayerNo(), loc.getFolderNo()));
    }

    /** 位置编号：办公室代码-柜号-层号-文件夹号，如 A-01-1-02。 */
    private String buildLocationCode(String officeRoom, String cabinetNo,
                                     String layerNo, String folderNo) {
        return officeCode(officeRoom) + "-" + pad2(digits(cabinetNo)) + "-"
                + digits(layerNo) + "-" + pad2(digits(folderNo));
    }

    /** 办公室代码：已有位置沿用其前缀，新办公室按顺序顺延 A、B、C…… */
    private String officeCode(String officeRoom) {
        List<ArchLocation> all = locationService.list();
        Set<String> names = new LinkedHashSet<>();
        for (ArchLocation l : all) {
            if (StringUtils.hasText(l.getOfficeRoom())) {
                names.add(l.getOfficeRoom());
            }
            if (officeRoom != null && officeRoom.equals(l.getOfficeRoom())) {
                String code = officeCodeOf(l.getLocationCode());
                if (StringUtils.hasText(code)) {
                    return code;
                }
            }
        }
        return String.valueOf((char) ('A' + (names.size() % 26)));
    }

    private String officeCodeOf(String locationCode) {
        if (!StringUtils.hasText(locationCode) || !locationCode.contains("-")) {
            return "";
        }
        return locationCode.substring(0, locationCode.indexOf('-'));
    }

    private String digits(String text) {
        Matcher m = Pattern.compile("\\d+").matcher(text == null ? "" : text);
        return m.find() ? m.group() : "0";
    }

    private String pad2(String num) {
        return num.length() >= 2 ? num : "0" + num;
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!StringUtils.hasText(p)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private List<String> distinct(List<ArchLocation> list,
                                  java.util.function.Function<ArchLocation, String> getter) {
        Set<String> set = new LinkedHashSet<>();
        for (ArchLocation l : list) {
            String v = getter.apply(l);
            if (StringUtils.hasText(v)) {
                set.add(v);
            }
        }
        return new ArrayList<>(set);
    }
}
