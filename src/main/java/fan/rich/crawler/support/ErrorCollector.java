package fan.rich.crawler.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 采集过程中的错误收集器。
 *
 * <p>WebMagic 的下载线程不会把 {@code PageProcessor#process} 抛出的异常回传给 Spider，
 * 而是直接交给线程的 {@code UncaughtExceptionHandler}——表现就是"请求了 N 次、写入 0 条、
 * 任务状态却是成功"。各 Processor 把首个错误与错误计数记录在这里，
 * 由 {@link fan.rich.crawler.CrawlerService} 在采集结束后读取并如实上报。
 */
public final class ErrorCollector {

    private final AtomicInteger count = new AtomicInteger();
    private final AtomicReference<String> first = new AtomicReference<>();

    /** 记录一次失败，只保留第一个错误详情。 */
    public void record(Throwable error) {
        count.incrementAndGet();
        String detail = error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
        first.compareAndSet(null, detail);
    }

    /** 是否有失败。 */
    public boolean hasError() {
        return count.get() > 0;
    }

    public int getCount() {
        return count.get();
    }

    /** 首个错误详情；无错误时为 {@code null}。 */
    public String getFirst() {
        return first.get();
    }
}
