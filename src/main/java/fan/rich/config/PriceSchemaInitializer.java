package fan.rich.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 价格采集模块的建表初始化器。
 *
 * <p>与销售演示数据一样，采用应用启动时 {@code CREATE TABLE IF NOT EXISTS} 的方式建表，
 * 避免手工执行 SQL 脚本；重复启动不会破坏已有数据。
 *
 * <p>三张核心表都带唯一索引，配合 Mapper 上的 {@code INSERT ... ON DUPLICATE KEY UPDATE}
 * 实现<b>幂等采集</b>——同一交易日重复跑只会覆盖，不会堆叠重复行。
 *
 * <p>可通过 {@code rich.init.enabled=false} 关闭。
 */
@Order(1)
@Component
public class PriceSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public PriceSchemaInitializer(JdbcTemplate jdbc,
                                  @Value("${rich.init.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            createTables();
            ensureColumns();
            System.out.println("[rich] 价格采集模块表结构就绪");
        } catch (Exception e) {
            System.err.println("[rich] 价格采集模块建表失败：" + e.getMessage());
        }
    }

    private void createTables() {
        // 品种字典：SMM / CJ 靠页面发现写入；SHFE / SGE 可选登记
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS md_source_product (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  source VARCHAR(16) NOT NULL COMMENT '数据源：SHFE/SGE/SMM/CJ',
                  product_id VARCHAR(64) NOT NULL COMMENT '站点侧品种ID或合约代码',
                  product_name VARCHAR(128) DEFAULT NULL,
                  product_group VARCHAR(64) DEFAULT NULL COMMENT '所属专区或分组',
                  unit VARCHAR(32) DEFAULT NULL,
                  enabled TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_src_pid (source, product_id),
                  KEY idx_source (source, enabled)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集品种字典'
                """);

        // 现货行情：SGE / SMM / CJ
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS md_spot_price (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  source VARCHAR(16) NOT NULL,
                  product_id VARCHAR(64) NOT NULL,
                  product_name VARCHAR(128) DEFAULT NULL,
                  trade_date DATE NOT NULL,
                  unit VARCHAR(32) DEFAULT NULL,
                  price_open DECIMAL(18,4) DEFAULT NULL,
                  price_high DECIMAL(18,4) DEFAULT NULL,
                  price_low DECIMAL(18,4) DEFAULT NULL,
                  price_close DECIMAL(18,4) DEFAULT NULL,
                  price_avg DECIMAL(18,4) DEFAULT NULL,
                  change_value DECIMAL(18,4) DEFAULT NULL,
                  change_rate DECIMAL(10,4) DEFAULT NULL COMMENT '涨跌幅百分数，如 1.55 表示 1.55%',
                  volume DECIMAL(20,4) DEFAULT NULL,
                  amount DECIMAL(22,4) DEFAULT NULL,
                  open_interest DECIMAL(20,4) DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_spot (source, product_id, trade_date),
                  KEY idx_date (trade_date),
                  KEY idx_source_date (source, trade_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现货日行情'
                """);

        // 期货行情：SHFE
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS md_futures_price (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  exchange VARCHAR(16) NOT NULL DEFAULT 'SHFE',
                  instrument_id VARCHAR(16) NOT NULL COMMENT '品种代号：au/ag/cu/al/pb/zn/ni/sn',
                  instrument_name VARCHAR(64) DEFAULT NULL,
                  contract_code VARCHAR(32) NOT NULL COMMENT '合约代码：au2610',
                  trade_date DATE NOT NULL,
                  presettlement_price DECIMAL(18,4) DEFAULT NULL,
                  open_price DECIMAL(18,4) DEFAULT NULL,
                  high_price DECIMAL(18,4) DEFAULT NULL,
                  low_price DECIMAL(18,4) DEFAULT NULL,
                  close_price DECIMAL(18,4) DEFAULT NULL,
                  settlement_price DECIMAL(18,4) DEFAULT NULL
                    COMMENT '当日结算价，仅收盘后的正式日行情文件下发',
                  bid_price DECIMAL(18,4) DEFAULT NULL,
                  ask_price DECIMAL(18,4) DEFAULT NULL,
                  change_value DECIMAL(18,4) DEFAULT NULL,
                  volume DECIMAL(20,4) DEFAULT NULL,
                  turnover DECIMAL(22,4) DEFAULT NULL,
                  open_interest DECIMAL(20,4) DEFAULT NULL,
                  quote_time DATETIME DEFAULT NULL COMMENT '站点数据更新时间',
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_future (instrument_id, contract_code, trade_date),
                  KEY idx_instrument_date (instrument_id, trade_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期货日行情'
                """);

        // 采集日志
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS md_crawl_log (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  source VARCHAR(16) DEFAULT NULL,
                  task_type VARCHAR(16) DEFAULT NULL COMMENT 'DISCOVER/QUOTE',
                  start_time DATETIME DEFAULT NULL,
                  end_time DATETIME DEFAULT NULL,
                  cost_ms INT DEFAULT 0,
                  status TINYINT DEFAULT 1 COMMENT '1成功 0失败',
                  record_count INT DEFAULT 0,
                  request_count INT DEFAULT 0,
                  message VARCHAR(512) DEFAULT NULL,
                  PRIMARY KEY (id),
                  KEY idx_source_time (source, end_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务日志'
                """);
    }

    /**
     * 老库补列：MySQL 5.7 没有 {@code ADD COLUMN IF NOT EXISTS}，只能先查
     * {@code information_schema} 判断列是否存在，缺了才 ALTER。
     *
     * <p>建表语句里的新列只对"第一次部署"生效，已经跑过一段日子的库必须靠这里补齐，
     * 否则 MyBatis 的 INSERT 会因为未知列直接报错。
     */
    private void ensureColumns() {
        addColumnIfMissing("md_futures_price", "settlement_price",
                "DECIMAL(18,4) DEFAULT NULL COMMENT '当日结算价，仅收盘后的正式日行情文件下发'",
                "close_price");
    }

    /** 列不存在时追加到锚点列之后；失败只打日志，不影响启动。 */
    private void addColumnIfMissing(String table, String column, String definition, String after) {
        try {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (exists != null && exists > 0) {
                return;
            }
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition
                    + " AFTER " + after);
            System.out.println("[rich] " + table + " 已补列 " + column);
        } catch (Exception e) {
            System.err.println("[rich] " + table + " 补列 " + column + " 失败：" + e.getMessage());
        }
    }
}
