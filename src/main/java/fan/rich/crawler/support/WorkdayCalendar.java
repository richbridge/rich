package fan.rich.crawler.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作日日历：为行情采集窗口提供「工作日」口径。
 *
 * <p>四个数据源的采集窗口都按「<b>上一工作日 18:00 ~ 当天 08:00</b>」定义——这段时间内
 * 日终价已经发布，而下一交易日的行情还没出来。而在中国，上一工作日并不等于昨天：
 * <ul>
 *   <li>周五 18:00 开窗，要一直开到<b>下周一</b> 08:00，中间整个周末都在窗口内；</li>
 *   <li>法定节假日同理（国庆前的最后一个工作日，窗口一直延续到节后首个工作日 08:00）；</li>
 *   <li>周末调休上班的日子（如某个周日）要按工作日算。</li>
 * </ul>
 * 用简单的 {@code plusDays(1)} 会在周六早上就把窗口关掉，导致周五的数据漏采，
 * 因此这里单独抽一个日历出来。
 *
 * <p>节假日与调休上班日通过配置项 {@code rich.crawler.schedule.holidays} /
 * {@code rich.crawler.schedule.workdays} 注入，形如 {@code 2026-10-01,2026-10-02}。
 *
 * <p>本类为不可变对象，构造后即可安全地在定时任务中复用。
 */
public final class WorkdayCalendar {

    /** 默认向前 / 向后查找的天数上限，防止配置错误时死循环。 */
    private static final int MAX_SCAN_DAYS = 60;

    private final Set<LocalDate> holidays;

    private final Set<LocalDate> extraWorkdays;

    /**
     * @param holidays      法定节假日（含周末调休放假），会覆盖"周一至周五即工作日"的默认判断
     * @param extraWorkdays 周末调休上班日，优先级高于"周六周日即休息"的默认判断
     */
    public WorkdayCalendar(Set<LocalDate> holidays, Set<LocalDate> extraWorkdays) {
        this.holidays = holidays == null ? Collections.emptySet() : new LinkedHashSet<>(holidays);
        this.extraWorkdays = extraWorkdays == null
                ? Collections.emptySet() : new LinkedHashSet<>(extraWorkdays);
    }

    /** 是否工作日：周末为休息日，法定节假日为休息日，调休上班日为例外。 */
    public boolean isWorkday(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (extraWorkdays.contains(date)) {
            return true;
        }
        if (holidays.contains(date)) {
            return false;
        }
        int dow = date.getDayOfWeek().getValue();
        return dow >= 1 && dow <= 5;
    }

    /** 返回严格晚于 {@code date} 的下一个工作日。 */
    public LocalDate nextWorkday(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            cursor = cursor.plusDays(1);
            if (isWorkday(cursor)) {
                return cursor;
            }
        }
        return cursor;
    }

    /**
     * 返回不晚于 {@code date} 的最近一个工作日；{@code date} 本身是工作日时直接返回它。
     */
    public LocalDate currentOrPreviousWorkday(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            if (isWorkday(cursor)) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    /**
     * 解析当前时刻所属的<b>夜间补采窗口</b>，并给出该窗口对应的目标交易日。
     *
     * <p>窗口口径由业务定义：<b>「上一工作日 18:00 ~ 当天 08:00」</b>——四个数据源
     * （上期所 / 上金所 / 上海有色 / 长江有色）都在这段时间内产出最终的日终价，
     * 白天拿到的只是盘中值。
     *
     * <p>判定顺序：
     * <ol>
     *   <li>凌晨（{@code now < endHour}）属于<b>昨晚</b>的窗口，基准日取昨天，否则取今天；</li>
     *   <li>基准日向前回退到最近的工作日，即为目标交易日 D（也就是"上一工作日"）；</li>
     *   <li>窗口 = {@code [D 18:00, nextWorkday(D) 08:00)}。</li>
     * </ol>
     *
     * <p>注意第 3 步的右端点是 {@code nextWorkday(D)} 而不是 {@code D+1}：周五 18:00 开的窗
     * 一直延续到下周一 08:00，整个周末都落在窗口内——服务器周末才开机时仍有机会补采周五的数据。
     * 白天其余时段（08:00~18:00）不落在任何窗口内，{@link Window#active} 为 false。
     *
     * @param now       当前时刻
     * @param startHour 开窗小时（18）
     * @param endHour   关窗小时（8）
     */
    public Window resolveNightWindow(LocalDateTime now, int startHour, int endHour) {
        LocalDate base = now.toLocalTime().isBefore(LocalTime.of(endHour, 0))
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        LocalDate targetDay = currentOrPreviousWorkday(base);
        LocalDateTime start = LocalDateTime.of(targetDay, LocalTime.of(startHour, 0));
        LocalDateTime end = LocalDateTime.of(nextWorkday(targetDay), LocalTime.of(endHour, 0));
        boolean active = !now.isBefore(start) && now.isBefore(end);
        return new Window(targetDay, start, end, active);
    }

    /**
     * 当前时刻是否落在<b>日间盘中时段</b>：工作日 的 {@code [startHour, endHour)}。
     *
     * <p>这段时间用于"每半小时采一次当天最新价"；非交易日跳过（交易所不开市、
     * 现货站点也不发布新报价），周末调休上班日按 {@link #isWorkday} 的口径算作工作日。
     *
     * @param now       当前时刻
     * @param startHour 起始小时（8）
     * @param endHour   结束小时（18）
     */
    public boolean isIntraday(LocalDateTime now, int startHour, int endHour) {
        if (!isWorkday(now.toLocalDate())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(startHour, 0)) && time.isBefore(LocalTime.of(endHour, 0));
    }

    /**
     * 一个采集窗口的描述。
     *
     * @param targetDay 该窗口应当采集的目标交易日，即"上一工作日"
     * @param start     窗口起点（目标日 18:00）
     * @param end       窗口终点（目标日的下一工作日 08:00）
     * @param active    当前时刻是否落在窗口内
     */
    public record Window(LocalDate targetDay, LocalDateTime start, LocalDateTime end,
                         boolean active) {
    }

    // ------------------------------------------------------------------ 配置解析

    /**
     * 解析 {@code 2026-10-01,2026-10-02} 形式的日期列表，容忍空格、空串与换行。
     *
     * @param raw 原始配置串，可为 null
     */
    public static Set<LocalDate> parseDates(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s;]+")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                dates.add(LocalDate.parse(text));
            } catch (Exception e) {
                // 配置写错不应导致应用起不来，跳过这一条即可
                System.err.println("[rich] 忽略无法解析的日期配置：" + text);
            }
        }
        return dates;
    }

    /** 便于测试的构造：直接给日期字符串数组。 */
    public static WorkdayCalendar of(String... holidayTexts) {
        List<LocalDate> list = new ArrayList<>();
        if (holidayTexts != null) {
            Arrays.stream(holidayTexts).forEach(t -> list.add(LocalDate.parse(t)));
        }
        return new WorkdayCalendar(new LinkedHashSet<>(list), Collections.emptySet());
    }
}
