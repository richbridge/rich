package fan.rich.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采集任务日志。
 *
 * <p>每次触发采集（无论手动还是定时）记录一条，便于在页面排查"为什么没数据"。
 */
@TableName("md_crawl_log")
public class MdCrawlLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功。 */
    public static final int STATUS_SUCCESS = 1;

    /** 失败。 */
    public static final int STATUS_FAILED = 0;

    /** 按口径跳过（目标日不是最新交易日、当日无行情文件等），非失败。 */
    public static final int STATUS_SKIPPED = -2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源编码：SHFE / SGE / SMM / CJ，为空表示全量采集任务。 */
    private String source;

    /**
     * 任务类型。
     *
     * <ul>
     *   <li>{@code DISCOVER} —— 品种发现（SMM / CJ）</li>
     *   <li>{@code QUOTE} —— 手动触发的完整采集（当日 + 历史回补）</li>
     *   <li>{@code INTRADAY} —— 日间每半小时一次的"当天最新价"轮询</li>
     *   <li>{@code SETTLE} —— 夜间窗口内的"上一工作日终值"补采</li>
     * </ul>
     */
    private String taskType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 耗时毫秒。 */
    private Integer costMs;

    /** 1 成功，0 失败，-2 按口径跳过。 */
    private Integer status;

    /** 写入/更新的记录数。 */
    private Integer recordCount;

    /** 请求数（行情采集时等于品种/分页数量）。 */
    private Integer requestCount;

    /** 成功时快照最新交易日，失败时记录异常信息。 */
    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getCostMs() {
        return costMs;
    }

    public void setCostMs(Integer costMs) {
        this.costMs = costMs;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Integer getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Integer requestCount) {
        this.requestCount = requestCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
