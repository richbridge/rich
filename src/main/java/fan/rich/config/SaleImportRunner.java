package fan.rich.config;

import fan.rich.sale.SaleExcelImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 销售台账自动导入。
 *
 * <p>应用启动时：若 {@code rich.sale.import-path} 指向的《金宝销售台账》Excel 存在、
 * 且 {@code sale_shipment} 表为空，则自动导入一次，保证销售模块开箱即有真实数据。
 *
 * <p>后续换月台账可通过 {@code POST /api/sale/import} 手动触发重新导入。
 */
@Order(3)
@Component
public class SaleImportRunner implements ApplicationRunner {

    private final SaleExcelImporter importer;
    private final String importPath;
    private final boolean enabled;

    public SaleImportRunner(SaleExcelImporter importer,
                            @Value("${rich.sale.import-path:}") String importPath,
                            @Value("${rich.init.enabled:true}") boolean enabled) {
        this.importer = importer;
        this.importPath = importPath == null ? "" : importPath.trim();
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || importPath.isEmpty()) {
            return;
        }
        try {
            long exists = importer.countShipments();
            if (exists > 0) {
                System.out.println("[rich] 发货台账已有 " + exists + " 条记录，跳过自动导入");
                return;
            }
            SaleExcelImporter.ImportResult result = importer.importFile(Path.of(importPath));
            System.out.printf("[rich] 销售台账导入完成：精矿 %d 条，合质金 %d 条，点价 %d 条，"
                            + "提金结算 %d 条，点价情况 %d 条%n",
                    result.concentrate(), result.dore(), result.pricing(),
                    result.settlement(), result.fixAccount());
            for (String warning : result.warnings()) {
                System.out.println("[rich] 导入提示：" + warning);
            }
        } catch (Exception e) {
            // 导入失败不阻断启动，可通过接口重试
            System.err.println("[rich] 销售台账自动导入失败：" + e.getMessage());
        }
    }
}
