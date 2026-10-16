package fan.rich.controller.openapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.common.R;
import fan.rich.entity.ArchFile;
import fan.rich.entity.ArchVolume;
import fan.rich.service.ArchFileService;
import fan.rich.service.ArchVolumeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放接口 · 档案检索。
 *
 * <p>移动端用于现场查档：按关键字查案卷 / 卷内文件，或扫码（案卷二维码）定位。
 */
@RestController
@RequestMapping("/openapi/archive")
public class OpenApiArchiveController {

    private final ArchVolumeService volumeService;
    private final ArchFileService fileService;
    private final OpenApiSupport support;

    public OpenApiArchiveController(ArchVolumeService volumeService,
                                    ArchFileService fileService,
                                    OpenApiSupport support) {
        this.volumeService = volumeService;
        this.fileService = fileService;
        this.support = support;
    }

    /**
     * 案卷分页检索。
     *
     * @param keyword      案卷号 / 题名模糊匹配
     * @param locationCode 存放位置编码精确匹配
     */
    @GetMapping("/volumes")
    public R volumes(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "20") long limit,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String locationCode) {
        R denied = support.require("archive");
        if (denied != null) {
            return denied;
        }
        LambdaQueryWrapper<ArchVolume> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ArchVolume::getVolumeNo, keyword)
                    .or().like(ArchVolume::getTitle, keyword)
                    .or().like(ArchVolume::getQrCode, keyword));
        }
        if (locationCode != null && !locationCode.isBlank()) {
            q.eq(ArchVolume::getLocationCode, locationCode);
        }
        q.orderByDesc(ArchVolume::getId);
        IPage<ArchVolume> records = volumeService.page(new Page<>(page, limit), q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getRecords());
        data.put("total", records.getTotal());
        return R.ok(data);
    }

    /** 按二维码 / 案卷号定位单个案卷。 */
    @GetMapping("/volume")
    public R volume(@RequestParam String code) {
        R denied = support.require("archive");
        if (denied != null) {
            return denied;
        }
        ArchVolume volume = volumeService.getOne(new LambdaQueryWrapper<ArchVolume>()
                .eq(ArchVolume::getQrCode, code).or().eq(ArchVolume::getVolumeNo, code).last("LIMIT 1"));
        if (volume == null) {
            return R.fail("未找到对应案卷：" + code);
        }
        return R.ok(volume);
    }

    /**
     * 卷内文件分页检索。
     *
     * @param volumeId 案卷 ID，可空
     * @param keyword  文号 / 题名 / 责任者模糊匹配
     */
    @GetMapping("/files")
    public R files(@RequestParam(defaultValue = "1") long page,
                   @RequestParam(defaultValue = "20") long limit,
                   @RequestParam(required = false) Long volumeId,
                   @RequestParam(required = false) String keyword) {
        R denied = support.require("archive");
        if (denied != null) {
            return denied;
        }
        LambdaQueryWrapper<ArchFile> q = new LambdaQueryWrapper<>();
        if (volumeId != null) {
            q.eq(ArchFile::getVolumeId, volumeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ArchFile::getDocNo, keyword)
                    .or().like(ArchFile::getTitle, keyword)
                    .or().like(ArchFile::getResponsible, keyword));
        }
        q.orderByDesc(ArchFile::getId);
        IPage<ArchFile> records = fileService.page(new Page<>(page, limit), q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getRecords());
        data.put("total", records.getTotal());
        return R.ok(data);
    }
}
