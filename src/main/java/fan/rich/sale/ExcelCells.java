package fan.rich.sale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Excel 单元格读取工具。
 *
 * <p>台账里同一列可能混着公式结果、真日期、以及"Excel 序列号日期"（如 45922 表示
 * 2025-09-22）三种形态，这里统一按目标类型归一：
 * <ul>
 *     <li>{@link #decimal}：数值，空单元格与非法文本统一返回 {@code null}</li>
 *     <li>{@link #date}：日期，数值按 Excel 序列号换算，真日期直接截取日期部分</li>
 *     <li>{@link #text}：文本，数值按原样输出（不做科学计数法展开）</li>
 * </ul>
 */
final class ExcelCells {

    /** Excel 的日期原点（1900 日期系统，含 Lotus 闰年 bug 的修正值）。 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private ExcelCells() {
    }

    /** 取单元格的原始值：公式取缓存结果。 */
    static Object raw(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        try {
            return switch (type) {
                case STRING -> {
                    String v = cell.getStringCellValue();
                    yield v == null ? null : v.trim();
                }
                case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue()
                        : cell.getNumericCellValue();
                case BOOLEAN -> cell.getBooleanCellValue();
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取数值，保留 4 位小数。 */
    static BigDecimal decimal(Sheet sheet, int rowIdx, int colIdx) {
        Object v = raw(sheet, rowIdx, colIdx);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        if (v instanceof LocalDateTime t) {
            return BigDecimal.valueOf(t.toLocalDate().toEpochDay());
        }
        String s = v.toString();
        if (s.isBlank() || "#".startsWith(s.substring(0, 1))) {
            return null;
        }
        try {
            return new BigDecimal(s).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 读取日期：数值按 Excel 序列号换算。 */
    static LocalDate date(Sheet sheet, int rowIdx, int colIdx) {
        Object v = raw(sheet, rowIdx, colIdx);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d < 20000 || d > 60000) {
                return null;
            }
            return EXCEL_EPOCH.plusDays((long) d);
        }
        String s = v.toString();
        if (s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取文本：数值去掉多余的 ".0"，避免出现「46287.0」这类展示。 */
    static String text(Sheet sheet, int rowIdx, int colIdx) {
        Object v = raw(sheet, rowIdx, colIdx);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).stripTrailingZeros().toPlainString();
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 按表头名称定位列号（0-based），找不到返回 -1。 */
    static int findColumn(Sheet sheet, int headerRow, String... candidates) {
        if (headerRow < 0) {
            return -1;
        }
        Row row = sheet.getRow(headerRow);
        if (row == null) {
            return -1;
        }
        int last = row.getLastCellNum();
        for (String candidate : candidates) {
            String target = normalize(candidate);
            for (int c = 0; c < last; c++) {
                String header = normalize(text(sheet, headerRow, c));
                if (header != null && header.equals(target)) {
                    return c;
                }
            }
        }
        // 退化为包含匹配，容忍表头带空格或全半角差异
        for (String candidate : candidates) {
            String target = normalize(candidate);
            for (int c = 0; c < last; c++) {
                String header = normalize(text(sheet, headerRow, c));
                if (header != null && !header.isEmpty()
                        && (header.contains(target) || target.contains(header))) {
                    return c;
                }
            }
        }
        return -1;
    }

    /** 归一化：去空白、去百分号内容、全角括号转半角。 */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\u00A0", "")
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("\\s+", "")
                .trim();
    }
}
