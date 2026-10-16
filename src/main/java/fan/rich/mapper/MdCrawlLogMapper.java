package fan.rich.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fan.rich.entity.MdCrawlLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集日志 Mapper。
 */
public interface MdCrawlLogMapper extends BaseMapper<MdCrawlLog> {

    /** 最近 N 条日志。 */
    @Select("SELECT * FROM md_crawl_log ORDER BY id DESC LIMIT #{limit}")
    List<MdCrawlLog> selectRecent(@Param("limit") int limit);

    /** 指定数据源/任务类型最近一条记录（不论成功失败），用于定时任务的冷却判断。 */
    @Select("SELECT * FROM md_crawl_log "
            + "WHERE source = #{source} AND task_type = #{taskType} "
            + "ORDER BY id DESC LIMIT 1")
    MdCrawlLog selectLast(@Param("source") String source,
                          @Param("taskType") String taskType);

    /** 指定数据源/任务类型最近一条成功记录。 */
    @Select("SELECT * FROM md_crawl_log "
            + "WHERE source = #{source} AND task_type = #{taskType} AND status = 1 "
            + "ORDER BY id DESC LIMIT 1")
    MdCrawlLog selectLastSuccess(@Param("source") String source,
                                 @Param("taskType") String taskType);

    /**
     * 指定时间点之后、指定数据源/任务类型的日志，按时间倒序。
     *
     * <p>夜间补采窗口可能横跨多天（周五 18:00 → 下周一 08:00），需要按"窗口起点"而不是
     * "今天"来判断本窗口内是否已经采过、一共试了几次，否则周一凌晨会被误判成新窗口而重复采集。
     */
    @Select("SELECT * FROM md_crawl_log "
            + "WHERE source = #{source} AND task_type = #{taskType} AND start_time >= #{since} "
            + "ORDER BY id DESC")
    List<MdCrawlLog> selectSince(@Param("source") String source,
                                 @Param("taskType") String taskType,
                                 @Param("since") LocalDateTime since);
}
