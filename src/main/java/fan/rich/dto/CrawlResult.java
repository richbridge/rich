package fan.rich.dto;

/**
 * 一次采集任务的执行结果，用于接口返回与落 {@code md_crawl_log}。
 */
public class CrawlResult {

    /** 任务已被拒（同一数据源正在采集）。 */
    public static final int STATUS_REJECTED = -1;

    /**
     * 按口径主动跳过：目标日尚不是（或已不是）站点最新交易日、当日无行情文件等。
     *
     * <p>与"失败"区分开是有意义的：定时任务靠状态判断是否重试，
     * 而"还没到发布时间"属于正常现象，不该在日志里刷成一片红。
     */
    public static final int STATUS_SKIPPED = -2;

    /** 失败。 */
    public static final int STATUS_FAILED = 0;

    /** 成功。 */
    public static final int STATUS_SUCCESS = 1;

    private String source;
    private String taskType;
    private int status = STATUS_SUCCESS;
    private int records;
    private int requests;
    private int pages;
    private int costMs;
    private String message;

    public static CrawlResult success(String source, String taskType, int records,
                                      int requests, int pages, int costMs, String message) {
        CrawlResult result = new CrawlResult();
        result.source = source;
        result.taskType = taskType;
        result.status = STATUS_SUCCESS;
        result.records = records;
        result.requests = requests;
        result.pages = pages;
        result.costMs = costMs;
        result.message = message;
        return result;
    }

    public static CrawlResult failure(String source, String taskType, int costMs, String message) {
        CrawlResult result = new CrawlResult();
        result.source = source;
        result.taskType = taskType;
        result.status = STATUS_FAILED;
        result.costMs = costMs;
        result.message = message;
        return result;
    }

    public static CrawlResult rejected(String source, String taskType, String message) {
        CrawlResult result = new CrawlResult();
        result.source = source;
        result.taskType = taskType;
        result.status = STATUS_REJECTED;
        result.message = message;
        return result;
    }

    /**
     * 按口径跳过：目标日不是站点最新交易日、当日没有行情文件等。
     *
     * <p>不计入失败，但会写进采集日志（状态 -2，页面显示"跳过"），
     * 定时任务据此继续在窗口内重试。
     */
    public static CrawlResult skipped(String source, String taskType, int costMs, String message) {
        CrawlResult result = new CrawlResult();
        result.source = source;
        result.taskType = taskType;
        result.status = STATUS_SKIPPED;
        result.costMs = costMs;
        result.message = message;
        return result;
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getRecords() {
        return records;
    }

    public void setRecords(int records) {
        this.records = records;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getCostMs() {
        return costMs;
    }

    public void setCostMs(int costMs) {
        this.costMs = costMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
