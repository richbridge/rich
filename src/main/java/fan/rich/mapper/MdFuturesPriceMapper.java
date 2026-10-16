package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fan.rich.entity.MdFuturesPrice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 期货行情 Mapper。
 */
public interface MdFuturesPriceMapper extends BaseMapper<MdFuturesPrice> {

    /**
     * 批量写入期货行情，按 (instrument_id, contract_code, trade_date) 幂等。
     */
    @Insert("<script>"
            + "INSERT INTO md_futures_price "
            + "  (exchange, instrument_id, instrument_name, contract_code, trade_date, "
            + "   presettlement_price, open_price, high_price, low_price, close_price, "
            + "   settlement_price, "
            + "   bid_price, ask_price, change_value, volume, turnover, open_interest, "
            + "   quote_time, create_time) "
            + "VALUES "
            + "<foreach collection='list' item='r' separator=','>"
            + "  (#{r.exchange}, #{r.instrumentId}, #{r.instrumentName}, #{r.contractCode}, #{r.tradeDate}, "
            + "   #{r.presettlementPrice}, #{r.openPrice}, #{r.highPrice}, #{r.lowPrice}, #{r.closePrice}, "
            + "   #{r.settlementPrice}, "
            + "   #{r.bidPrice}, #{r.askPrice}, #{r.changeValue}, #{r.volume}, #{r.turnover}, #{r.openInterest}, "
            + "   #{r.quoteTime}, NOW())"
            + "</foreach> "
            + "ON DUPLICATE KEY UPDATE "
            + "  instrument_name = VALUES(instrument_name), "
            + "  presettlement_price = VALUES(presettlement_price), "
            + "  open_price = VALUES(open_price), "
            + "  high_price = VALUES(high_price), "
            + "  low_price = VALUES(low_price), "
            + "  close_price = VALUES(close_price), "
            // 结算价只在日行情文件里下发；盘中延时行情反复 UPSERT 时该列为 NULL，
            // 用 IFNULL 保留已采到的正式结算价，避免被白天的空值擦掉。
            + "  settlement_price = IFNULL(VALUES(settlement_price), settlement_price), "
            + "  bid_price = VALUES(bid_price), "
            + "  ask_price = VALUES(ask_price), "
            + "  change_value = VALUES(change_value), "
            + "  volume = VALUES(volume), "
            + "  turnover = VALUES(turnover), "
            + "  open_interest = VALUES(open_interest), "
            + "  quote_time = VALUES(quote_time)"
            + "</script>")
    int bulkUpsert(@Param("list") List<MdFuturesPrice> list);

    /** 各品种最新交易日的合约明细，用于主力·价格一览。 */
    @Select("<script>"
            + "SELECT f.* FROM md_futures_price f "
            + "INNER JOIN (SELECT instrument_id, MAX(trade_date) AS trade_date "
            + "            FROM md_futures_price GROUP BY instrument_id) t "
            + "  ON f.instrument_id = t.instrument_id AND f.trade_date = t.trade_date "
            + "<if test='instrumentId != null and instrumentId != &quot;&quot;'>"
            + "  WHERE f.instrument_id = #{instrumentId}"
            + "</if> "
            + "ORDER BY f.instrument_id, f.contract_code"
            + "</script>")
    List<MdFuturesPrice> selectLatestContracts(@Param("instrumentId") String instrumentId);

    /** 某品种在某个日期区间的全部合约。 */
    @Select("SELECT * FROM md_futures_price "
            + "WHERE instrument_id = #{instrumentId} "
            + "  AND trade_date BETWEEN #{start} AND #{end} "
            + "ORDER BY contract_code")
    List<MdFuturesPrice> selectRange(@Param("instrumentId") String instrumentId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    /** 已有数据的品种列表及其最新交易日。 */
    @Select("SELECT instrument_id, MAX(instrument_name) AS instrument_name, "
            + "COUNT(*) AS contract_count, MAX(trade_date) AS trade_date "
            + "FROM md_futures_price GROUP BY instrument_id ORDER BY instrument_id")
    List<Map<String, Object>> selectInstrumentSummary();

    /**
     * 某品种的主力合约连续序列：每个交易日取成交量最大的合约（即当日主力）的收盘价，
     * 用于价格看板的期货走势曲线。
     */
    @Select("SELECT f.trade_date AS tradeDate, ANY_VALUE(f.close_price) AS closePrice, "
            + "       MAX(f.instrument_name) AS instrumentName "
            + "FROM md_futures_price f "
            + "INNER JOIN (SELECT trade_date, MAX(volume) AS mv "
            + "            FROM md_futures_price WHERE instrument_id = #{instrumentId} "
            + "            GROUP BY trade_date) t "
            + "  ON f.trade_date = t.trade_date AND f.volume = t.mv "
            + "WHERE f.instrument_id = #{instrumentId} "
            + "GROUP BY f.trade_date "
            + "ORDER BY f.trade_date DESC LIMIT #{limit}")
    List<Map<String, Object>> selectMainContractTrend(@Param("instrumentId") String instrumentId,
                                                      @Param("limit") int limit);

    /**
     * 某品种主力合约明细（成交量最大），供多金属报价表使用。
     *
     * @param instrumentId 品种代码
     * @param tradeDate    指定交易日；为空时取该品种已有数据的最大交易日
     */
    @Select("<script>"
            + "SELECT f.* FROM md_futures_price f "
            + "JOIN (SELECT trade_date, MAX(volume) AS mv FROM md_futures_price "
            + "      WHERE instrument_id = #{instrumentId} "
            + "  <choose>"
            + "    <when test='tradeDate != null'>"
            + "        AND trade_date = #{tradeDate}"
            + "    </when>"
            + "    <otherwise>"
            + "        AND trade_date = (SELECT MAX(trade_date) FROM md_futures_price "
            + "                          WHERE instrument_id = #{instrumentId})"
            + "    </otherwise>"
            + "  </choose>"
            + "      GROUP BY trade_date) t "
            + "  ON f.trade_date = t.trade_date AND f.volume = t.mv "
            + "WHERE f.instrument_id = #{instrumentId} "
            + "  <if test='tradeDate != null'>AND f.trade_date = #{tradeDate}</if> "
            + "ORDER BY f.contract_code LIMIT 1"
            + "</script>")
    MdFuturesPrice selectMainContractLatest(@Param("instrumentId") String instrumentId,
                                            @Param("tradeDate") LocalDate tradeDate);

    /** 期货行情已有的全部交易日（倒序），供多金属报价表的日期下拉使用。 */
    @Select("SELECT DISTINCT trade_date FROM md_futures_price ORDER BY trade_date DESC")
    List<LocalDate> selectTradeDates();

    /**
     * 某品种在某个交易日（该日无数据时取之前最近的交易日）的全部合约明细，
     * 供按「当月+N」规则挑选参考合约。
     */
    @Select("<script>"
            + "SELECT f.* FROM md_futures_price f "
            + "JOIN (SELECT MAX(trade_date) AS td FROM md_futures_price "
            + "      WHERE instrument_id = #{instrumentId} "
            + "  <if test='tradeDate != null'>AND trade_date &lt;= #{tradeDate}</if> "
            + "     ) t ON f.trade_date = t.td "
            + "WHERE f.instrument_id = #{instrumentId} AND f.trade_date = t.td "
            + "ORDER BY f.contract_code"
            + "</script>")
    List<MdFuturesPrice> selectContractsOnDate(@Param("instrumentId") String instrumentId,
                                               @Param("tradeDate") LocalDate tradeDate);
}
