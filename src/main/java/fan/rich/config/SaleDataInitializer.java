package fan.rich.config;

import fan.rich.sale.SaleExcelImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售模块初始化器。
 *
 * <p>应用启动后自动建表（{@code CREATE TABLE IF NOT EXISTS}），并在字典表为空时写入
 * 客户与发货品种。真实业务数据（发货台账、点价、提金结算）来自《金宝销售台账》Excel，
 * 由 {@link SaleImportRunner} 在表为空时自动导入。
 *
 * <p>生产环境可通过 {@code rich.init.enabled=false} 关闭。
 */
@Order(2)
@Component
public class SaleDataInitializer implements ApplicationRunner {

    /** 演示账号默认密码。 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean resetAdminPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SaleDataInitializer(JdbcTemplate jdbc,
                               @Value("${rich.init.enabled:true}") boolean enabled,
                               @Value("${rich.init.reset-admin-password:true}") boolean resetAdminPassword) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.resetAdminPassword = resetAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            createTables();
            migrateColumns();
            if (count("sale_product") == 0) {
                insertProducts();
            }
            if (count("sale_customer") == 0) {
                insertCustomers();
            }
            normalizeCustomers();
            if (resetAdminPassword) {
                ensureAdminAccount();
            }
            System.out.println("[rich] 销售模块表结构就绪");
        } catch (Exception e) {
            // 初始化失败不应阻断应用启动（例如数据库不可达时仍可排查页面问题）
            System.err.println("[rich] 销售模块初始化失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ DDL

    /**
     * 存量库补列：{@code CREATE TABLE IF NOT EXISTS} 不会给已存在的表加新字段，
     * 这里显式补齐后加的列，保证老库升级后功能可用（幂等）。
     */
    private void migrateColumns() {
        addColumnIfMissing("sale_shipment", "arb_diff",
                "DECIMAL(18,4) DEFAULT NULL COMMENT '仲裁差(g)'");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        System.out.println("[rich] 已补列 " + table + "." + column);
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_customer (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  customer_no VARCHAR(32) NOT NULL,
                  name VARCHAR(128) NOT NULL,
                  contact VARCHAR(64) DEFAULT NULL,
                  phone VARCHAR(32) DEFAULT NULL,
                  level VARCHAR(32) DEFAULT NULL,
                  region VARCHAR(32) DEFAULT NULL,
                  industry VARCHAR(64) DEFAULT NULL,
                  status TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_customer_no (customer_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_product (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  product_no VARCHAR(32) NOT NULL,
                  name VARCHAR(128) NOT NULL,
                  category VARCHAR(64) DEFAULT NULL,
                  unit VARCHAR(16) DEFAULT NULL,
                  cost_price DECIMAL(18,2) DEFAULT 0,
                  sale_price DECIMAL(18,2) DEFAULT 0,
                  stock INT DEFAULT 0,
                  status TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_product_no (product_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货品种'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_shipment (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  ship_no VARCHAR(64) DEFAULT NULL COMMENT '批次号（回数）',
                  product_type VARCHAR(20) DEFAULT NULL COMMENT 'CONCENTRATE 浮选精矿 / DORE 合质金',
                  row_kind VARCHAR(20) DEFAULT NULL COMMENT 'NORMAL 正常 / PREV_YEAR 上年结余 / ARBITRATION 仲裁',
                  customer_id BIGINT DEFAULT NULL,
                  customer_name VARCHAR(64) DEFAULT NULL COMMENT '客户',
                  ship_date DATE DEFAULT NULL COMMENT '发货日期',
                  gross_weight DECIMAL(18,4) DEFAULT NULL COMMENT '毛重(T)',
                  tare_weight DECIMAL(18,4) DEFAULT NULL COMMENT '皮重(T)',
                  bag_deduct DECIMAL(18,4) DEFAULT NULL COMMENT '扣袋(T)',
                  net_weight DECIMAL(18,4) DEFAULT NULL COMMENT '净重(T)',
                  moisture DECIMAL(18,6) DEFAULT NULL COMMENT '水份，小数比例',
                  dry_weight DECIMAL(18,4) DEFAULT NULL COMMENT '干重(t)',
                  settle_grade DECIMAL(18,4) DEFAULT NULL COMMENT '结算品位(g/t)',
                  gold_content DECIMAL(18,4) DEFAULT NULL COMMENT '实际金含量(g)',
                  return_gold DECIMAL(18,4) DEFAULT NULL COMMENT '返金量(g)',
                  pre_extract_gold DECIMAL(18,4) DEFAULT NULL COMMENT '预提金量(g)',
                  grade_diff DECIMAL(18,4) DEFAULT NULL COMMENT '品位差',
                  process_fee DECIMAL(18,4) DEFAULT NULL COMMENT '加工费(元)',
                  picked_up VARCHAR(64) DEFAULT NULL COMMENT '是否提货',
                  arb_grade DECIMAL(18,4) DEFAULT NULL COMMENT '仲裁前品位(g/t)',
                  arb_diff DECIMAL(18,4) DEFAULT NULL COMMENT '仲裁差(g)',
                  sample_no VARCHAR(64) DEFAULT NULL COMMENT '样品编号（合质金）',
                  pre_melt_weight DECIMAL(18,4) DEFAULT NULL COMMENT '溶火前重量(g)',
                  impurity DECIMAL(18,4) DEFAULT NULL COMMENT '杂质(g)',
                  post_melt_weight DECIMAL(18,4) DEFAULT NULL COMMENT '溶火后重量(g)',
                  sample_weight DECIMAL(18,4) DEFAULT NULL COMMENT '样品重量(g)',
                  retained_weight DECIMAL(18,4) DEFAULT NULL COMMENT '留样后重量(g)',
                  gold_grade DECIMAL(18,6) DEFAULT NULL COMMENT '金结算品位(%)',
                  silver_grade DECIMAL(18,6) DEFAULT NULL COMMENT '银结算品位(%)',
                  silver_content DECIMAL(18,4) DEFAULT NULL COMMENT '银实际含量(g)',
                  return_silver DECIMAL(18,4) DEFAULT NULL COMMENT '返银量(g)',
                  settled_gold DECIMAL(18,4) DEFAULT NULL COMMENT '已结算(g)',
                  gold_pick_date DATE DEFAULT NULL COMMENT '提金日',
                  pre_steam_weight DECIMAL(18,4) DEFAULT NULL COMMENT '蒸前重量(g)',
                  external_check VARCHAR(128) DEFAULT NULL COMMENT '外检',
                  au_ag_ratio DECIMAL(18,6) DEFAULT NULL COMMENT '金银占比',
                  remark VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ship (product_type, ship_no),
                  KEY idx_ship_date (ship_date),
                  KEY idx_type_date (product_type, ship_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货台账'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_pricing (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  variety VARCHAR(32) DEFAULT NULL COMMENT '浮选金 / 合质金',
                  price_date DATE DEFAULT NULL COMMENT '点价日',
                  fix_price DECIMAL(18,4) DEFAULT NULL COMMENT '点价(元/克)',
                  settle_price DECIMAL(18,4) DEFAULT NULL COMMENT '结算价(元/克)',
                  quantity DECIMAL(18,4) DEFAULT NULL COMMENT '点价数量(g)',
                  avg_price DECIMAL(18,4) DEFAULT NULL COMMENT '平均点价',
                  amount DECIMAL(18,4) DEFAULT NULL COMMENT '点价成交额(万元)',
                  group_tag VARCHAR(64) DEFAULT NULL COMMENT '批次分组',
                  remark VARCHAR(255) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_variety_date (variety, price_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点价记录'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_settlement (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  category VARCHAR(32) DEFAULT NULL COMMENT '浮选金 / 合质金',
                  batch_no VARCHAR(64) DEFAULT NULL COMMENT '结算批次，如 浮精187-192回',
                  gold_pick_date DATE DEFAULT NULL COMMENT '提金日',
                  settle_price DECIMAL(18,4) DEFAULT NULL COMMENT '结算价格(元/克)',
                  gold_weight DECIMAL(18,4) DEFAULT NULL COMMENT '提金重量(g)',
                  settle_amount DECIMAL(20,2) DEFAULT NULL COMMENT '结算金额(元)',
                  invoice_status VARCHAR(64) DEFAULT NULL COMMENT '开票情况',
                  remark VARCHAR(255) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_batch (batch_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提金结算开票'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sale_fix_account (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  stat_year INT DEFAULT NULL COMMENT '统计年度',
                  variety VARCHAR(64) DEFAULT NULL COMMENT '点价种类',
                  prev_balance DECIMAL(18,4) DEFAULT NULL COMMENT '上年余量(g)',
                  fix_qty DECIMAL(18,4) DEFAULT NULL COMMENT '点价量(g)',
                  shipped_qty DECIMAL(18,4) DEFAULT NULL COMMENT '已发货量(g)',
                  extracted_qty DECIMAL(18,4) DEFAULT NULL COMMENT '已提金(g)',
                  stock_qty DECIMAL(18,4) DEFAULT NULL COMMENT '库存(g)',
                  owe_qty DECIMAL(18,4) DEFAULT NULL COMMENT '欠金量(g)',
                  remark VARCHAR(255) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_year (stat_year)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点价情况台账'
                """);
    }

    // ------------------------------------------------------------------ 种子数据

    private void insertProducts() {
        String sql = """
                INSERT INTO sale_product
                  (id, product_no, name, category, unit, cost_price, sale_price, stock, status, create_time)
                VALUES (?,?,?,?,?,?,?,?,1,?)
                """;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < PRODUCTS.size(); i++) {
            ProductSeed seed = PRODUCTS.get(i);
            jdbc.update(sql, (long) (i + 1), String.format("P%03d", i + 1),
                    seed.name(), seed.category(), seed.unit(),
                    bd(seed.cost()), bd(seed.sale()), 0, now);
        }
    }

    private void insertCustomers() {
        String sql = """
                INSERT INTO sale_customer
                  (id, customer_no, name, contact, phone, level, region, industry, status, create_time)
                VALUES (?,?,?,?,?,?,?,?,1,?)
                """;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < CUSTOMERS.size(); i++) {
            CustomerSeed seed = CUSTOMERS.get(i);
            jdbc.update(sql, (long) (i + 1), String.format("C%03d", i + 1),
                    seed.name(), seed.contact(), seed.phone(),
                    seed.level(), seed.region(), seed.industry(), now);
        }
    }

    /**
     * 客户档案口径校正（幂等，每次启动都跑）。
     *
     * <ol>
     *   <li>删除已下线的客户「金翅岭」，连带清理点价台账中它的点价情况行；</li>
     *   <li>客户「国大」统一为工商全称「烟台国大贸易有限公司」，
     *       历史发货明细里的客户简称同步改写，保证按客户汇总时不出现两套名字。</li>
     * </ol>
     */
    private void normalizeCustomers() {
        String guoda = SaleExcelImporter.BUYER_GUODA;
        int removedFix = jdbc.update("DELETE FROM sale_fix_account WHERE variety LIKE '%金翅岭%'");
        int removedCustomer = jdbc.update("DELETE FROM sale_customer WHERE name LIKE '%金翅岭%'");
        int renamedCustomer = jdbc.update(
                "UPDATE sale_customer SET name = ? WHERE name LIKE '%国大%' AND name <> ?", guoda, guoda);
        int renamedShipment = jdbc.update(
                "UPDATE sale_shipment SET customer_name = ? "
                        + "WHERE customer_name LIKE '%国大%' AND customer_name <> ?", guoda, guoda);
        if (removedFix + removedCustomer + renamedCustomer + renamedShipment > 0) {
            System.out.printf("[rich] 客户档案校正：清理金翅岭客户 %d 条 / 点价记录 %d 条，"
                            + "国大统一全称 %d 条客户、%d 条发货明细%n",
                    removedCustomer, removedFix, renamedCustomer, renamedShipment);
        }
    }

    private void ensureAdminAccount() {
        Long exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'admin'", Long.class);
        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
        if (exists == null || exists == 0) {
            jdbc.update("INSERT INTO sys_user (username, password, nickname, status, create_time) "
                    + "VALUES (?,?,?,1,?)", "admin", hash, "超级管理员", LocalDateTime.now());
        } else {
            jdbc.update("UPDATE sys_user SET password = ?, status = 1 WHERE username = 'admin'", hash);
        }
    }

    // ------------------------------------------------------------------ 工具方法

    private long count(String table) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return total == null ? 0 : total;
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** 产品种子数据：名称、品类、单位、成本价、销售价。 */
    private record ProductSeed(String name, String category, String unit, double cost, double sale) {
    }

    /** 客户（销售商）种子数据。 */
    private record CustomerSeed(String name, String region, String industry,
                                String level, String contact, String phone) {
    }

    /** 发货品种：精矿按吨发货按金量结算，合质金按克发货。 */
    private static final List<ProductSeed> PRODUCTS = List.of(
            new ProductSeed("浮选精矿粉", "金精矿", "吨", 0, 0),
            new ProductSeed("合质金", "合质金", "克", 0, 0)
    );

    /** 客户：目前仅保留国大一家冶炼方（客户「金翅岭」已下线）。 */
    private static final List<CustomerSeed> CUSTOMERS = List.of(
            new CustomerSeed(SaleExcelImporter.BUYER_GUODA, "华东", "贵金属冶炼", "战略客户", "", "")
    );
}
