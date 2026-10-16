package fan.rich.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 开机补采钩子。
 *
 * <p>需求是"服务器开机时采集一次"，而 {@code @Scheduled} 只是"每隔一段时间检查一次"——
 * 如果服务器在 18:10 关机、23:00 开机，恰好错过了检查点还好（检查点每 20 分钟一次），
 * 但如果是 07:00 开机、08:00 之后窗口就关了，等到下一次检查点就可能来不及。
 * 更极端的情况是服务器整夜关机、第二天早上才开机——那时窗口早就过去了。
 *
 * <p>因此这里在应用启动完成后，<b>立刻</b>按当前时刻判定一次该跑哪个任务
 * （夜间补采窗口 → 补上一工作日终值；日间盘中时段 → 抓一次当天最新价），
 * 再交由原本的定时任务继续值守。
 *
 * <p>几个刻意的设计：
 * <ul>
 *   <li><b>异步执行</b>——采集要打外部站点、可能耗时几十秒到几分钟，
 *       绝不能拖慢应用启动（尤其 SMM / CJ 要按上百个品种逐个请求）；</li>
 *   <li><b>略作延迟</b>——等应用完全就绪、连接池热身完再动手，避免启动瞬间的资源争用；
 *       延迟秒数由 {@code rich.crawler.schedule.startup-delay-seconds} 控制；</li>
 *   <li><b>可关闭</b>——{@code rich.crawler.schedule.crawl-on-startup=false}
 *       可以在频繁重启调试时关掉它，免得每次重启都去打扰外部站点。</li>
 * </ul>
 *
 * <p>是否重复采集由任务本身保证：夜间补采在窗口内每个数据源只成功采一次，
 * 所以开机补跑与定时任务重叠也不会产生额外请求。
 */
@Component
public class CrawlStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlStartupRunner.class);

    private final CrawlScheduleTask scheduleTask;
    private final boolean enabled;
    private final int delaySeconds;

    public CrawlStartupRunner(CrawlScheduleTask scheduleTask,
                              @Value("${rich.crawler.schedule.crawl-on-startup:true}")
                              boolean enabled,
                              @Value("${rich.crawler.schedule.startup-delay-seconds:10}")
                              int delaySeconds) {
        this.scheduleTask = scheduleTask;
        this.enabled = enabled;
        this.delaySeconds = Math.max(0, delaySeconds);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[开机] 已关闭开机补采（rich.crawler.schedule.crawl-on-startup=false）");
            return;
        }
        Thread worker = new Thread(this::runSafely, "rich-crawl-startup");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSafely() {
        try {
            if (delaySeconds > 0) {
                Thread.sleep(delaySeconds * 1000L);
            }
            scheduleTask.runOnceForNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 补采失败不能影响应用本身，定时任务随后还会继续按窗口重试
            log.error("[开机] 启动补采执行失败，等待定时任务重试", e);
        }
    }
}
