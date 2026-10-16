package fan.rich.crawler.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部行情报文的脏值解析工具。
 *
 * <p>三个站点的数据都存在大量占位符与不规则格式：
 * <ul>
 *   <li>SGE：空值用 {@code "-"} 占位，涨跌幅带 {@code %} 后缀，日期可能是 Excel 序列号；</li>
 *   <li>SMM / CJ：数值字段偶尔为 {@code null}，同时提供 {@code *_show} 字符串版本；</li>
 *   <li>SHFE：数值全部以字符串下发，空串表示无值。</li>
 * </ul>
 * 统一在这里收敛，避免每个 Processor 各写一套。
 */
public final class NumUtil {

    /** 占位符：SGE 用 "-" 表示当日无成交。 */
    private static final String NULL_PLACEHOLDER = "-";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NumUtil() {
    }

    /** 判断字符串是否无有效内容。 */
    public static boolean blank(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || NULL_PLACEHOLDER.equals(trimmed)
                || "null".equalsIgnoreCase(trimmed) || "--".equals(trimmed);
    }

    /**
     * 解析十进制数，失败返回 null。
     *
     * <p>支持科学计数法（Excel 导出的大数值如 {@code 7.5174873672E9}）与千分位逗号。
     */
    public static BigDecimal decimal(String value) {
        if (blank(value)) {
            return null;
        }
        String cleaned = value.trim().replace(",", "").replace("％", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析长整数，失败返回 null。 */
    public static Long longValue(String value) {
        BigDecimal dec = decimal(value);
        return dec == null ? null : dec.longValue();
    }

    /** 解析整数，失败返回 null。 */
    public static Integer intValue(String value) {
        BigDecimal dec = decimal(value);
        return dec == null ? null : dec.intValue();
    }

    /**
     * 解析百分比并统一为"百分数数值"：{@code "1.55%"} → 1.55，{@code 0.0044} → 0.44。
     *
     * <p>SGE 下发带 % 的字符串，SMM 下发小数比例，二者通过本方法对齐到同一量纲。
     */
    public static BigDecimal percent(String value) {
        if (blank(value)) {
            return null;
        }
        String cleaned = value.trim().replace(",", "");
        boolean hasPercent = cleaned.endsWith("%") || cleaned.endsWith("％");
        if (hasPercent) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        BigDecimal dec = decimal(cleaned);
        if (dec == null) {
            return null;
        }
        BigDecimal result = hasPercent ? dec : dec.multiply(BigDecimal.valueOf(100));
        return result.setScale(4, RoundingMode.HALF_UP);
    }

    /** 解析日期，支持 yyyy-MM-dd 与 yyyy/MM/dd。 */
    public static LocalDate date(String value) {
        if (blank(value)) {
            return null;
        }
        String cleaned = value.trim().replace('/', '-');
        if (cleaned.length() > 10) {
            cleaned = cleaned.substring(0, 10);
        }
        try {
            return LocalDate.parse(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析日期时间，失败返回 null。 */
    public static LocalDateTime dateTime(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (Exception e) {
            LocalDate d = date(value);
            return d == null ? null : d.atStartOfDay();
        }
    }

    /** 四舍五入保留指定小数位，null 安全。 */
    public static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 按固定大小切分列表，避免单条 INSERT 语句过长。
     *
     * @param source 待切分列表，为 null 时返回空列表
     * @param size   每片大小，小于 1 时退化为整批
     * @param <T>    元素类型
     * @return 切分后的多个批次
     */
    public static <T> List<List<T>> partition(List<T> source, int size) {
        List<List<T>> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        int chunk = Math.max(1, size);
        for (int i = 0; i < source.size(); i += chunk) {
            result.add(new ArrayList<>(source.subList(i, Math.min(i + chunk, source.size()))));
        }
        return result;
    }
}
