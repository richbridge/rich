package fan.rich.crawler.support;

import fan.rich.entity.MdSpotPrice;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上海黄金交易所日行情 xlsx 解析器。
 *
 * <p>SGE 的下载接口返回 {@code xlsx} 二进制流，表头为中文：
 * <pre>
 * 序号 | 日期 | 合约 | 开盘价 | 最高价 | 最低价 | 收盘价 | 涨跌（元） | 涨跌幅 |
 * 加权平均价 | 成交量（kg） | 成交金额（元） | 市场持仓（手） | 交收方向 | 交收量（手）
 * </pre>
 * 列顺序由后台模板决定，因此这里按表头文字做列自增映射，而不是写死下标；无成交的单元格
 * 用 {@code "-"} 占位，需转成 null。
 */
public final class SgeQuoteParser {

    /** 列含义枚举。 */
    private enum Column {
        DATE, CONTRACT, OPEN, HIGH, LOW, CLOSE, CHANGE, CHANGE_RATE, AVG, VOLUME, AMOUNT, OPEN_INTEREST
    }

    private SgeQuoteParser() {
    }

    /**
     * 解析 xlsx 字节流。
     *
     * @param bytes 文件内容
     * @param unit  计价单位，写入每条记录（SGE 贵金属现货统一为元/克）
     * @return 解析出的行情列表
     * @throws IOException POI 读取失败时抛出
     */
    public static List<MdSpotPrice> parse(byte[] bytes, String unit) throws IOException {
        List<MdSpotPrice> result = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<Integer, Column> columnMap = new HashMap<>();
            DataFormatter formatter = new DataFormatter();
            // Excel 纵向合并单元格时只有首行有值，日期列需要向下补齐
            LocalDate carriedDate = null;

            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cells = readRow(row, formatter);
                if (isEmpty(cells)) {
                    continue;
                }
                if (columnMap.isEmpty()) {
                    columnMap = resolveColumns(cells);
                    // 未识别到表头（如"日期"缺失）说明不是预期模板，直接结束
                    if (!columnMap.containsValue(Column.DATE)) {
                        return result;
                    }
                    continue;
                }

                LocalDate tradeDate = cellDate(cells, columnMap, Column.DATE);
                if (tradeDate != null) {
                    carriedDate = tradeDate;
                }
                if (carriedDate == null) {
                    continue;
                }
                String contract = cellText(cells, columnMap, Column.CONTRACT);
                if (NumUtil.blank(contract)) {
                    continue;
                }

                MdSpotPrice record = new MdSpotPrice();
                record.setSource("SGE");
                record.setProductId(contract);
                record.setProductName(contract);
                record.setTradeDate(carriedDate);
                record.setUnit(unit);
                record.setPriceOpen(cellDecimal(cells, columnMap, Column.OPEN));
                record.setPriceHigh(cellDecimal(cells, columnMap, Column.HIGH));
                record.setPriceLow(cellDecimal(cells, columnMap, Column.LOW));
                record.setPriceClose(cellDecimal(cells, columnMap, Column.CLOSE));
                record.setPriceAvg(cellDecimal(cells, columnMap, Column.AVG));
                record.setChangeValue(cellDecimal(cells, columnMap, Column.CHANGE));
                record.setChangeRate(cellPercent(cells, columnMap, Column.CHANGE_RATE));
                record.setVolume(cellDecimal(cells, columnMap, Column.VOLUME));
                record.setAmount(cellDecimal(cells, columnMap, Column.AMOUNT));
                record.setOpenInterest(cellDecimal(cells, columnMap, Column.OPEN_INTEREST));

                // 涨停/无成交行所有价格为空，只有全部价格为空才丢弃，避免丢掉仅缺开高低的行
                if (record.getPriceClose() == null && record.getPriceAvg() == null
                        && record.getPriceHigh() == null && record.getPriceLow() == null) {
                    continue;
                }
                result.add(record);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 单元格读取

    /** 读取整行，数值与日期单元格统一转成字符串后再按需解析。 */
    private static List<String> readRow(Row row, DataFormatter formatter) {
        List<String> cells = new ArrayList<>();
        short lastCell = row.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                cells.add("");
                continue;
            }
            String text;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                text = DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate().toString();
            } else {
                text = formatter.formatCellValue(cell, null);
            }
            cells.add(text == null ? "" : text.trim());
        }
        return cells;
    }

    /**
     * 依据表头文字建立列映射。
     *
     * <p>注意判断顺序：{@code 涨跌幅} 必须优先于 {@code 涨跌}，否则会被后者先匹配走。
     */
    private static Map<Integer, Column> resolveColumns(List<String> header) {
        Map<Integer, Column> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String text = header.get(i);
            Column column = matchColumn(text);
            if (column != null) {
                map.put(i, column);
            }
        }
        return map;
    }

    private static Column matchColumn(String text) {
        if (text.contains("日期")) {
            return Column.DATE;
        }
        if (text.contains("合约")) {
            return Column.CONTRACT;
        }
        if (text.contains("开盘")) {
            return Column.OPEN;
        }
        if (text.contains("最高")) {
            return Column.HIGH;
        }
        if (text.contains("最低")) {
            return Column.LOW;
        }
        if (text.contains("收盘")) {
            return Column.CLOSE;
        }
        if (text.contains("涨跌幅")) {
            return Column.CHANGE_RATE;
        }
        if (text.contains("涨跌")) {
            return Column.CHANGE;
        }
        if (text.contains("加权平均")) {
            return Column.AVG;
        }
        if (text.contains("成交金额")) {
            return Column.AMOUNT;
        }
        if (text.contains("成交量")) {
            return Column.VOLUME;
        }
        if (text.contains("持仓")) {
            return Column.OPEN_INTEREST;
        }
        return null;
    }

    private static String cellText(List<String> cells, Map<Integer, Column> map, Column target) {
        Integer index = indexOf(map, target);
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }

    private static LocalDate cellDate(List<String> cells, Map<Integer, Column> map, Column target) {
        return NumUtil.date(cellText(cells, map, target));
    }

    private static BigDecimal cellDecimal(List<String> cells, Map<Integer, Column> map, Column target) {
        return NumUtil.decimal(cellText(cells, map, target));
    }

    private static BigDecimal cellPercent(List<String> cells, Map<Integer, Column> map, Column target) {
        return NumUtil.percent(cellText(cells, map, target));
    }

    private static Integer indexOf(Map<Integer, Column> map, Column target) {
        for (Map.Entry<Integer, Column> entry : map.entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean isEmpty(List<String> cells) {
        for (String cell : cells) {
            if (!NumUtil.blank(cell)) {
                return false;
            }
        }
        return true;
    }
}
