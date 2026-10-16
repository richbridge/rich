package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.MdSpotPrice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 现货行情 Mapper。
 */
public interface MdSpotPriceMapper extends BaseMapper<MdSpotPrice> {

    /**
     * 批量写入现货行情，按 (source, product_id, trade_date) 幂等。
     *
     * <p>依赖唯一索引 {@code uk_spot}，重复采集时走 UPDATE 分支，不会产生重复行。
     */
    @Insert("<script>"
            + "INSERT INTO md_spot_price "
            + "  (source, product_id, product_name, trade_date, unit, "
            + "   price_open, price_high, price_low, price_close, price_avg, "
            + "   change_value, change_rate, volume, amount, open_interest, create_time) "
            + "VALUES "
            + "<foreach collection='list' item='r' separator=','>"
            + "  (#{r.source}, #{r.productId}, #{r.productName}, #{r.tradeDate}, #{r.unit}, "
            + "   #{r.priceOpen}, #{r.priceHigh}, #{r.priceLow}, #{r.priceClose}, #{r.priceAvg}, "
            + "   #{r.changeValue}, #{r.changeRate}, #{r.volume}, #{r.amount}, #{r.openInterest}, NOW())"
            + "</foreach> "
            + "ON DUPLICATE KEY UPDATE "
            + "  product_name = VALUES(product_name), "
            + "  unit = VALUES(unit), "
            + "  price_open = VALUES(price_open), "
            + "  price_high = VALUES(price_high), "
            + "  price_low = VALUES(price_low), "
            + "  price_close = VALUES(price_close), "
            + "  price_avg = VALUES(price_avg), "
            + "  change_value = VALUES(change_value), "
            + "  change_rate = VALUES(change_rate), "
            + "  volume = VALUES(volume), "
            + "  amount = VALUES(amount), "
            + "  open_interest = VALUES(open_interest)"
            + "</script>")
    int bulkUpsert(@Param("list") List<MdSpotPrice> list);

    /**
     * 批量写入现货行情，已存在的行<b>直接跳过、不更新</b>（当前调度已不再使用，保留备用）。
     *
     * <p>与 {@link #bulkUpsert(List)} 的区别就在这一句 {@code IGNORE}：适合"第一次采到什么
     * 就永久保留什么"的口径。SMM / CJ 现已改用覆盖写——它们的日间轮询要求把当天那一行
     * 不断刷成最新值，而"历史行不被改写"这件事由采集侧的新鲜度闸门保证
     * （目标日不是站点最新交易日时整批跳过，根本走不到这里），不再依赖只插入。
     *
     * <p>依赖唯一索引 {@code uk_spot(source, product_id, trade_date)}；
     * 返回值为真正插入的行数（被忽略的行不计入），因此重跑时返回 0 属正常。
     */
    @Insert("<script>"
            + "INSERT IGNORE INTO md_spot_price "
            + "  (source, product_id, product_name, trade_date, unit, "
            + "   price_open, price_high, price_low, price_close, price_avg, "
            + "   change_value, change_rate, volume, amount, open_interest, create_time) "
            + "VALUES "
            + "<foreach collection='list' item='r' separator=','>"
            + "  (#{r.source}, #{r.productId}, #{r.productName}, #{r.tradeDate}, #{r.unit}, "
            + "   #{r.priceOpen}, #{r.priceHigh}, #{r.priceLow}, #{r.priceClose}, #{r.priceAvg}, "
            + "   #{r.changeValue}, #{r.changeRate}, #{r.volume}, #{r.amount}, #{r.openInterest}, NOW())"
            + "</foreach>"
            + "</script>")
    int bulkInsertIgnore(@Param("list") List<MdSpotPrice> list);

    /** 每个数据源的最新交易日。 */
    @Select("SELECT source, MAX(trade_date) AS trade_date FROM md_spot_price GROUP BY source")
    List<Map<String, Object>> selectLatestDateBySource();

    /**
     * 已入库的现货品种清单（来源 + 品种ID + 最新名称 + 记录数 + 最新日期），
     * 供看板的品种下拉框使用；数据服务缺失时用 MAX(product_name) 兜底去重。
     */
    @Select("SELECT source, product_id AS productId, MAX(product_name) AS productName, "
            + "       COUNT(*) AS recordCount, MAX(trade_date) AS latestDate "
            + "FROM md_spot_price "
            + "GROUP BY source, product_id "
            + "ORDER BY source, product_id")
    List<Map<String, Object>> selectSubjects();

    /** 某品种在某数据源下已有的交易日，用于增量判断。 */
    @Select("SELECT trade_date FROM md_spot_price "
            + "WHERE source = #{source} AND product_id = #{productId} "
            + "ORDER BY trade_date DESC LIMIT #{limit}")
    List<String> selectRecentDates(@Param("source") String source,
                                   @Param("productId") String productId,
                                   @Param("limit") int limit);

    /** 某数据源已有行情的全部交易日（倒序），供多金属报价表的日期下拉使用。 */
    @Select("SELECT DISTINCT trade_date FROM md_spot_price "
            + "WHERE source = #{source} "
            + "ORDER BY trade_date DESC")
    List<LocalDate> selectTradeDates(@Param("source") String source);
}
