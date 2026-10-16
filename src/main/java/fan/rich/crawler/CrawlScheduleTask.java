package fan.rich.crawler;

import fan.rich.crawler.support.WorkdayCalendar;
import fan.rich.dto.CrawlResult;
import fan.rich.entity.MdCrawlLog;
import fan.rich.mapper.MdCrawlLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情采集定时任务。
 *
 * <p>采集节奏按"日终价什么时候发布"来划分，四个数据源（上期所 SHFE、上金所 SGE、
 * 上海有色 SMM、长江有色 CJ）走同一套时间表：
 *
 * <table border="1">
 *   <caption>采集节奏</caption>
 *   <tr><th>任务</th><th>时段</th><th>内容</th><th>频次</th></tr>
 *   <tr>
 *     <td>夜间补采 {@code SETTLE}</td>
 *     <td>上一工作日 18:00 ~ 当天 08:00</td>
 *     <td>采集<b>上一工作日</b>的终值（交易所=正式收盘价/结算价，现货=当日结算价）</td>
 *     <td>每个源在该窗口内只成功采一次；未成功则每 30 分钟重试，最多 6 次</td>
 *   </tr>
 *   <tr>
 *     <td>日间轮询 {@code INTRADAY}</td>
 *     <td>工作日 08:00 ~ 18:00</td>
 *     <td>采集<b>当天最新价</b>（交易所=延时行情，现货=当日最新报价）</td>
 *     <td>每 30 分钟一次</td>
 *   </tr>
 *   <tr>
 *     <td>历史回补 {@code QUOTE}</td>
 *     <td>每日 16:40</td>
 *     <td>交易所当日 + 最近 30 天历史序列补齐</td>
 *     <td>每天一次</td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么"上一工作日"要单独成一个窗口</b>：
 * <ol>
 *   <li>日终价在收盘后（约 18:00）才发布，白天去取拿到的只是盘中值；</li>
 *   <li>而下一工作日开盘（08:00）之后，站点就进入新一天的节奏，昨天的值不再是"最新"。</li>
 * </ol>
 * 两个口径正好首尾相接：白天每半小时把当天价格刷成最新（{@code INTRADAY}），
 * 夜里再把这一天定稿成终值（{@code SETTLE}）。
 *
 * <p><b>为什么上海有色 / 长江有色的"当天"也只有一次机会</b>：这两个站点只对
 * <b>最新交易日</b>返回未经处理的原始值（表现为整数，反复请求恒定不变）；一旦有更新的
 * 交易日发布，此前所有行都会被平台做一次"加密变更"（叠加 {@code 真值 × n × 万分之1}
 * 的随机偏移，n 每次请求重新抽签，不可还原）。因此采集端有一道"新鲜度闸门"：
 * 目标日一旦不是当次响应里的最新交易日，整批跳过不入库——见
 * {@link fan.rich.crawler.processor.SmmQuoteProcessor}。
 *
 * <p><b>开机即采</b>：定时任务是"每隔一段时间检查一次"，服务器半夜关机再开机可能正好
 * 错过检查点。因此 {@link CrawlStartupRunner} 在应用启动后会立即按当前时刻补跑一次
 * （落在哪个时段就跑哪个任务），保证"服务器开机时采集一次"。
 *
 * <p>窗口的"工作日"口径由 {@link fan.rich.crawler.support.WorkdayCalendar} 提供：
 * 周五 18:00 开的窗一直延续到下周一 08:00，法定节假日同理。
 *
 * <p>所有 cron 都可通过 {@code rich.crawler.schedule.*} 覆盖；把 {@code enabled} 设为
 * false 可整体关闭（例如本地调试时不希望它偷偷打外部站点）。
 */
@Component
public class CrawlScheduleTask {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduleTask.class);

    /**
     * 触发"下一轮冷却"的最小请求数。
     *
     * <p>交易所的盘中采集只有 1 个请求，偶发一次网络抖动也会 0 页；若不加门槛，
     * 一次抖动就会白跳过半小时。现货源要按上百个品种逐个请求，整批 0 页必然是站点拦截。
     */
    private static final int MIN_REQUESTS_FOR_BACKOFF = 5;

    /** 上一轮整批未拿到响应的数据源，本轮跳过（见 {@link #MIN_REQUESTS_FOR_BACKOFF}）。 */
    private final Set<String> intradayBackoff = ConcurrentHashMap.newKeySet();

    private final CrawlerService crawlerService;
    private final MdCrawlLogMapper logMapper;
    private final boolean enabled;
    private final int nightWindowStartHour;
    private final int nightWindowEndHour;
    private final int intradayStartHour;
    private final int intradayEndHour;
    private final int nightRetryMinutes;
    private final int nightMaxAttempts;
    private final boolean intradaySpot;
    private final WorkdayCalendar calendar;

    public CrawlScheduleTask(CrawlerService crawlerService,
                             MdCrawlLogMapper logMapper,
                             @Value("${rich.crawler.schedule.enabled:true}") boolean enabled,
                             @Value("${rich.crawler.schedule.night-window-start-hour:"
                                     + "${rich.crawler.schedule.spot-window-start-hour:18}}")
                             int nightWindowStartHour,
                             @Value("${rich.crawler.schedule.night-window-end-hour:"
                                     + "${rich.crawler.schedule.spot-window-end-hour:8}}")
                             int nightWindowEndHour,
                             @Value("${rich.crawler.schedule.intraday-start-hour:8}")
                             int intradayStartHour,
                             @Value("${rich.crawler.schedule.intraday-end-hour:18}")
                             int intradayEndHour,
                             @Value("${rich.crawler.schedule.night-retry-minutes:"
                                     + "${rich.crawler.schedule.spot-retry-minutes:30}}")
                             int nightRetryMinutes,
                             @Value("${rich.crawler.schedule.night-max-attempts:6}")
                             int nightMaxAttempts,
                             @Value("${rich.crawler.schedule.intraday-spot:true}")
                             boolean intradaySpot,
                             @Value("${rich.crawler.schedule.holidays:}") String holidays,
                             @Value("${rich.crawler.schedule.workdays:}") String extraWorkdays) {
        this.crawlerService = crawlerService;
        this.logMapper = logMapper;
        this.enabled = enabled;
        this.nightWindowStartHour = nightWindowStartHour;
        this.nightWindowEndHour = nightWindowEndHour;
        this.intradayStartHour = intradayStartHour;
        this.intradayEndHour = intradayEndHour;
        this.nightRetryMinutes = nightRetryMinutes;
        this.nightMaxAttempts = Math.max(1, nightMaxAttempts);
        this.intradaySpot = intradaySpot;
        this.calendar = new WorkdayCalendar(
                WorkdayCalendar.parseDates(holidays),
                WorkdayCalendar.parseDates(extraWorkdays));
    }

    /**
     * 夜间补采：采集「上一工作日」的终值。
     *
     * <p>窗口 = 上一工作日 18:00 ~ 当天 08:00（周五开的窗延续到下周一 08:00）。
     * 窗口内每个数据源只成功采一次，多开机一次不会产生额外请求；失败或数据尚未发布时
     * 每 {@code night-retry-minutes} 分钟重试一次，最多 {@code night-max-attempts} 次，
     * 避免站点没数据时整夜猛打。
     */
    @Scheduled(cron = "${rich.crawler.schedule.night-cron:"
            + "${rich.crawler.schedule.spot-cron:0 0/20 * * * ?}}")
    public void nightSettle() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkdayCalendar.Window window = calendar.resolveNightWindow(
                now, nightWindowStartHour, nightWindowEndHour);
        if (!window.active()) {
            return;
        }
        LocalDate target = window.targetDay();
        for (CrawlSource source : CrawlSource.values()) {
            if (!source.isEnabled() || !shouldRunNightSettle(source, now, window.start())) {
                continue;
            }
            log.info("[定时] 夜间补采 {} 的终值数据（窗口 {} ~ {}）",
                    target, window.start(), window.end());
            try {
                crawlerService.crawlNightSettle(source, target);
            } catch (Exception e) {
                log.error("[定时] {} 夜间补采异常", source.getCode(), e);
            }
        }
    }

    /**
     * 日间轮询：采集「当天最新价」。
     *
     * <p>工作日 08:00~18:00 每 30 分钟一次；非交易日（周末、法定节假日）直接跳过，
     * 调休上班日按工作日处理。18:00 之后交给 {@link #nightSettle()}，
     * 由它把这天的价格定稿成终值。
     *
     * <p>上海有色 / 长江有色需要按品种逐个请求（上百个），如果站点限流导致频繁被拦，
     * 可把 {@code rich.crawler.schedule.intraday-spot} 设为 false，
     * 只保留交易所的盘中轮询与夜间的现货终值补采。
     */
    @Scheduled(cron = "${rich.crawler.schedule.intraday-cron:"
            + "${rich.crawler.schedule.realtime-cron:0 0/30 * * * ?}}")
    public void intradayPoll() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!calendar.isIntraday(now, intradayStartHour, intradayEndHour)) {
            return;
        }
        for (CrawlSource source : CrawlSource.values()) {
            if (!source.isEnabled()) {
                continue;
            }
            if (!intradaySpot
                    && (source == CrawlSource.SMM || source == CrawlSource.CJ)) {
                continue;
            }
            if (intradayBackoff.remove(source.getCode())) {
                log.warn("[定时] {} 上一轮盘中采集整批未拿到响应（疑似被限流），本轮跳过让站点冷却",
                        source.getCode());
                continue;
            }
            try {
                CrawlResult result = crawlerService.crawlIntraday(source);
                // 整批 0 页 = 站点把我们拦了。继续按半小时猛打只会越拦越死，
                // 所以跳过下一轮（半小时）再试；单个品种超时之类的零星失败不在此列。
                if (result.getStatus() == CrawlResult.STATUS_FAILED && result.getPages() == 0
                        && result.getRequests() >= MIN_REQUESTS_FOR_BACKOFF) {
                    intradayBackoff.add(source.getCode());
                }
            } catch (Exception e) {
                log.error("[定时] {} 当天最新价采集异常", source.getCode(), e);
            }
        }
    }

    /**
     * 交易所历史回补：当日 + 最近 {@code shfe/sge-backfill-days} 天。
     *
     * <p>每天一次（默认 16:40，日盘收盘后交易所已发布当日正式行情文件）。
     * 这是唯一会重下几十天历史的任务，把它单独隔出来，是为了让高频的盘中轮询保持轻量。
     */
    @Scheduled(cron = "${rich.crawler.schedule.daily-cron:0 40 16 * * ?}")
    public void historyBackfill() {
        if (!enabled) {
            return;
        }
        log.info("[定时] 开始回补交易所当日 + 历史行情");
        for (CrawlSource source : new CrawlSource[]{CrawlSource.SHFE, CrawlSource.SGE}) {
            try {
                crawlerService.crawlQuotes(source, LocalDate.now());
            } catch (Exception e) {
                log.error("[定时] {} 历史回补异常", source.getCode(), e);
            }
        }
    }

    /**
     * 按当前时刻立即补跑一次，供开机钩子调用。
     *
     * <p>服务器夜间关机、错过检查点时，靠这个方法在开机瞬间完成补采：
     * 落在夜间窗口就补上一工作日终值，落在日间盘中时段就抓一次当天最新价。
     */
    public void runOnceForNow() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkdayCalendar.Window window = calendar.resolveNightWindow(
                now, nightWindowStartHour, nightWindowEndHour);
        if (window.active()) {
            log.info("[开机] 处于夜间补采窗口（目标交易日 {}），立即补采一次", window.targetDay());
            nightSettle();
            return;
        }
        if (calendar.isIntraday(now, intradayStartHour, intradayEndHour)) {
            log.info("[开机] 处于日间盘中时段，立即采集一次当天最新价");
            intradayPoll();
            return;
        }
        log.info("[开机] 当前既不在夜间补采窗口、也不在日间盘中时段，无需补采");
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 判断某个数据源本轮是否需要跑夜间补采。
     *
     * <p>三条规则，依次判定：
     * <ol>
     *   <li><b>本窗口内已成功采过一次</b> → 不再采。日终值一天只有一份，
     *       重复采集只会白打站点；对 SMM / CJ 更关键的是，窗口一旦滑过去，
     *       它就从"最新交易日"变成被加密变更的历史行，再采就是脏数据；</li>
     *   <li><b>本窗口内尝试次数已达上限</b> → 放弃。目标日不是交易日（如调休之外的
     *       空档）时，站点永远不会有那天数据，不设上限会整夜重试；</li>
     *   <li><b>距上次尝试不足冷却时间</b> → 再等等。日终价常常发布得比 18:00 晚，
     *       失败后隔几分钟再试是有效的。</li>
     * </ol>
     */
    private boolean shouldRunNightSettle(CrawlSource source, LocalDateTime now,
                                         LocalDateTime windowStart) {
        List<MdCrawlLog> attempts = logMapper.selectSince(
                source.getCode(), CrawlerService.TASK_SETTLE, windowStart);
        if (attempts.isEmpty()) {
            return true;
        }
        for (MdCrawlLog attempt : attempts) {
            if (attempt.getStatus() != null
                    && attempt.getStatus() == MdCrawlLog.STATUS_SUCCESS) {
                return false;
            }
        }
        if (attempts.size() >= nightMaxAttempts) {
            log.info("[定时] {} 本轮窗口已尝试 {} 次仍未采到，本窗口不再重试",
                    source.getCode(), attempts.size());
            return false;
        }
        LocalDateTime lastEnd = attempts.get(0).getEndTime();
        if (lastEnd == null) {
            return true;
        }
        return !lastEnd.plusMinutes(Math.max(nightRetryMinutes, 5)).isAfter(now);
    }
}
