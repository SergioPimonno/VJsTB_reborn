package com.vjstb.ledscheme.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Небольшой помощник для сборки табличной спецификации (.xlsx) — используется
 * вместо plain-text отчёта, чтобы спецификацию можно было открыть/фильтровать/
 * досчитать прямо в Excel (см. ui.stage.OutputStagePanel.buildEquipmentSpecWorkbook).
 * Никакой доменной логики здесь нет, только механика листов/строк/стилей POI.
 */
public final class SpecXlsxWriter {

    private SpecXlsxWriter() {
    }

    public static Workbook newWorkbook() {
        return new XSSFWorkbook();
    }

    /** Новый лист с жирной строкой заголовков в первой строке (и закреплённой,
     *  чтобы не терялась при прокрутке длинных таблиц). */
    public static Sheet addSheet(Workbook wb, String name, String... headers) {
        Sheet sheet = wb.createSheet(name);
        CellStyle headerStyle = headerStyle(wb);
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    /** Добавляет строку в конец листа. Числа (Integer/Double/...) записываются как
     *  числовые ячейки (можно суммировать в Excel), всё остальное — как текст;
     *  {@code null} — пустая ячейка. */
    public static void addRow(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            Object v = values[i];
            if (v == null) {
                continue;
            }
            if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else {
                c.setCellValue(String.valueOf(v));
            }
        }
    }

    /** Подгоняет ширину первых {@code columnCount} колонок под содержимое —
     *  вызывать после того, как все строки листа уже добавлены. */
    public static void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    public static void write(Workbook wb, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        } finally {
            wb.close();
        }
    }
}
