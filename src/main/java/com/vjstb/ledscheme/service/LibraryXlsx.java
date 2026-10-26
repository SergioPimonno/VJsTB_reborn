package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Импорт библиотеки оборудования из Excel-таблицы (.xlsx) и генерация пустого шаблона
 * для неё. Задача — новый пользователь (новая команда) заполняет готовый шаблон списка
 * оборудования, загружает его в приложение и сразу получает рабочую библиотеку, не
 * создавая записи руками по одной.
 *
 * <p>Пять листов с данными — «Кабинеты», «Оборудование», «Кабели», «Приёмные карты» и «Блоки
 * питания» (последние два — для модульных экранов, docs/receiver-cards/PLAN.md в VJsTB_reborn; на
 * листе «Кабинеты» ещё столбец «Приёмная карта отдельно», он делает тип модулем). Лист «Инструкция» при
 * разборе игнорируется. Столбцы ищутся по тексту заголовка (регистр, звёздочка
 * обязательности и единицы после запятой/скобки не важны — см. {@link #headerKey}), а не
 * по номеру, поэтому пользователь может переставлять и добавлять свои столбцы; лишние
 * столбцы и листы молча игнорируются. Строки, название которых начинается с «Пример:»,
 * пропускаются — в шаблоне лежит по одной такой строке-образцу, чтобы нетронутый
 * шаблон не засорил библиотеку.
 *
 * <p>Плохая строка не обрывает импорт: она пропускается с понятным предупреждением
 * ({@link Parsed#warnings()}); необязательное число, которое не удалось прочесть,
 * просто не применяется. Сопоставление с уже существующими записями — тот же ключ,
 * что у JSON-импорта (кабинет — имя; оборудование — режим+категория+имя; кабель —
 * режим+подпись), но запись обновляется ТОЛЬКО заполненными ячейками: в отличие от
 * JSON-импорта, который подменяет запись целиком, здесь из таблицы приходит лишь часть
 * полей (нет карт, разъёмов питания, форм), и затирать их пустотой нельзя. Записи общей
 * (серверной) библиотеки не трогаются — совпадение с ними даёт предупреждение.
 */
public final class LibraryXlsx {

    public static final String SHEET_HELP = "Инструкция";
    public static final String SHEET_CABINETS = "Кабинеты";
    public static final String SHEET_EQUIPMENT = "Оборудование";
    public static final String SHEET_CABLES = "Кабели";
    /** Листы модульных экранов (docs/receiver-cards/PLAN.md в VJsTB_reborn, раздел 8): приёмные карты и блоки питания. */
    public static final String SHEET_RECEIVER_CARDS = "Приёмные карты";
    public static final String SHEET_POWER_SUPPLIES = "Блоки питания";

    /** Строка, название которой начинается с этого префикса, — образец из шаблона, не данные. */
    public static final String EXAMPLE_PREFIX = "Пример:";

    private LibraryXlsx() {
    }

    // ------------------------------------------------------------------ результат разбора

    /** Строка листа «Кабинеты»; {@code null}-поля — ячейка пуста (при обновлении не меняется). */
    public record CabinetRow(int rowNumber, String name, String company, Double widthMm, Double heightMm,
                              Double depthMm, Integer resolutionWidth, Integer resolutionHeight,
                              Double powerW, Double weightKg, PowerConnectorType connector,
                              Integer powerLines, Integer signalLines, Boolean receiverCardSeparate) {
    }

    /** Строка листа «Оборудование». */
    public record EquipmentRow(int rowNumber, String name, SchemaMode mode, SchemaNodeType category,
                                String customCategoryLabel, String vendor, String company, String description,
                                Integer inputPorts, Integer outputPorts, Double portBandwidthMbps,
                                Boolean loopPort, PhysicalSpec.FormFactor formFactor, Integer rackUnits,
                                Double widthMm, Double heightMm, Double depthMm, Double weightKg, Double powerW) {
    }

    /** Строка листа «Кабели». */
    public record CableRow(int rowNumber, SchemaMode mode, String label, Double fixedLengthM) {
    }

    /** Строка листа «Приёмные карты»; {@code null}-поля — ячейка пуста (при обновлении не меняется). */
    public record ReceiverCardRow(int rowNumber, String name, String vendor, String company, Integer moduleOutputs,
                                   Double ribbonLengthMm, Integer maxWidthPx, Integer maxHeightPx, Integer maxPixels,
                                   Double powerW, Double weightKg, Double widthMm, Double heightMm, Double depthMm,
                                   String notes) {
    }

    /** Строка листа «Блоки питания»; {@code null}-поля — ячейка пуста (при обновлении не меняется). */
    public record PowerSupplyRow(int rowNumber, String name, String vendor, String company, Integer outputs,
                                  Double ratedPowerW, Double cableLengthMm, Boolean cardOutput, String inputVoltage,
                                  Double weightKg, Double widthMm, Double heightMm, Double depthMm, String notes) {
    }

    /** Разобранное содержимое файла; {@code warnings} — всё, что пропущено или применено не так, как написано. */
    public record Parsed(List<CabinetRow> cabinets, List<EquipmentRow> equipment, List<CableRow> cables,
                          List<ReceiverCardRow> receiverCards, List<PowerSupplyRow> powerSupplies,
                          List<String> warnings) {
    }

    /** Итог слияния с библиотекой пользователя. */
    public record Report(int cabinetsAdded, int cabinetsUpdated, int equipmentAdded, int equipmentUpdated,
                          int cablesAdded, int cablesUpdated, int receiverCardsAdded, int receiverCardsUpdated,
                          int powerSuppliesAdded, int powerSuppliesUpdated, List<String> warnings) {
        public int total() {
            return cabinetsAdded + cabinetsUpdated + equipmentAdded + equipmentUpdated + cablesAdded
                    + cablesUpdated + receiverCardsAdded + receiverCardsUpdated + powerSuppliesAdded
                    + powerSuppliesUpdated;
        }

        /** Строки про карты и блоки питания добавляются только если по ним что-то было: большинству таблиц эти листы не нужны. */
        public String summary() {
            String s = "Кабинеты: добавлено " + cabinetsAdded + ", обновлено " + cabinetsUpdated
                    + "\nОборудование: добавлено " + equipmentAdded + ", обновлено " + equipmentUpdated
                    + "\nКабели: добавлено " + cablesAdded + ", обновлено " + cablesUpdated;
            if (receiverCardsAdded + receiverCardsUpdated > 0) {
                s += "\nПриёмные карты: добавлено " + receiverCardsAdded + ", обновлено " + receiverCardsUpdated;
            }
            if (powerSuppliesAdded + powerSuppliesUpdated > 0) {
                s += "\nБлоки питания: добавлено " + powerSuppliesAdded + ", обновлено " + powerSuppliesUpdated;
            }
            return s;
        }
    }

    /** Списки библиотеки, в которые вливается таблица: {@code shared*} только читаются (проверка совпадений). */
    public record Libraries(List<CabinetType> sharedCabinets, List<CabinetType> cabinets,
                             List<EquipmentPreset> sharedEquipment, List<EquipmentPreset> equipment,
                             List<CableType> sharedCables, List<CableType> cables,
                             List<ReceiverCardType> sharedReceiverCards, List<ReceiverCardType> receiverCards,
                             List<PowerSupplyType> sharedPowerSupplies, List<PowerSupplyType> powerSupplies) {
        /** Без карт и блоков питания — для вызовов, которым нужны только три исходных вида. */
        public Libraries(List<CabinetType> sharedCabinets, List<CabinetType> cabinets,
                          List<EquipmentPreset> sharedEquipment, List<EquipmentPreset> equipment,
                          List<CableType> sharedCables, List<CableType> cables) {
            this(sharedCabinets, cabinets, sharedEquipment, equipment, sharedCables, cables,
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    // ------------------------------------------------------------------ столбцы

    /** Столбец листа: {@code id} — внутренний ключ, {@code aliases} — дополнительные допустимые заголовки. */
    private record Col(String id, String header, boolean required, int width, String... aliases) {
    }

    private static final Col C_NAME = new Col("name", "Название *", true, 28, "name", "модель", "наименование");
    private static final Col C_COMPANY = new Col("company", "Компания", false, 18, "company", "владелец");

    private static final List<Col> CABINET_COLS = List.of(
            C_NAME,
            C_COMPANY,
            new Col("width", "Ширина, мм *", true, 14, "width"),
            new Col("height", "Высота, мм *", true, 14, "height"),
            new Col("depth", "Глубина, мм", false, 14, "depth"),
            new Col("resX", "Разрешение по горизонтали, px *", true, 20, "разрешение x", "px по горизонтали"),
            new Col("resY", "Разрешение по вертикали, px *", true, 20, "разрешение y", "px по вертикали"),
            new Col("power", "Мощность, Вт", false, 14, "потребление"),
            new Col("weight", "Вес, кг", false, 12, "weight"),
            new Col("connector", "Разъём питания", false, 18, "тип разъёма питания"),
            new Col("powerLines", "Линий питания на кабинет", false, 16, "линий питания"),
            new Col("signalLines", "Линий сигнала на кабинет", false, 16, "линий сигнала"),
            new Col("receiverCardSeparate", "Приёмная карта отдельно (Да/Нет)", false, 18, "модуль"));

    private static final List<Col> EQUIPMENT_COLS = List.of(
            C_NAME,
            new Col("mode", "Режим (Сигнал/Питание)", false, 16, "mode"),
            new Col("category", "Категория", false, 22, "category", "тип"),
            new Col("vendor", "Производитель", false, 18, "vendor"),
            C_COMPANY,
            new Col("description", "Описание", false, 30, "description", "комментарий"),
            new Col("inputs", "Входных портов (контроллер)", false, 16, "входов"),
            new Col("outputs", "Выходных портов (контроллер)", false, 16, "выходов"),
            new Col("bandwidth", "Пропускная способность порта, Мбит/с", false, 18, "bandwidth"),
            new Col("loop", "Loop-порт (Да/Нет)", false, 14, "loop"),
            new Col("form", "Способ установки", false, 16, "установка"),
            new Col("rackU", "Высота в стойке, U", false, 14, "юниты", "rack units"),
            new Col("width", "Ширина, мм", false, 12, "width"),
            new Col("height", "Высота, мм", false, 12, "height"),
            new Col("depth", "Глубина, мм", false, 12, "depth"),
            new Col("weight", "Вес, кг", false, 10, "weight"),
            new Col("power", "Мощность, Вт", false, 12, "потребление"));

    private static final List<Col> CABLE_COLS = List.of(
            new Col("mode", "Режим (Сигнал/Питание) *", true, 20, "mode"),
            new Col("label", "Название *", true, 36, "подпись", "label", "наименование"),
            new Col("length", "Длина, м", false, 12, "длина", "length"));

    private static final List<Col> RECEIVER_CARD_COLS = List.of(
            C_NAME,
            new Col("vendor", "Производитель", false, 18, "vendor"),
            C_COMPANY,
            new Col("outputs", "Выходов на модули (шлейфов)", false, 18, "выходов", "выходы на модули"),
            new Col("ribbon", "Длина шлейфа, мм", false, 14, "длина шлейфа до модуля"),
            new Col("maxWidth", "Макс. ширина области, px", false, 16, "макс. ширина"),
            new Col("maxHeight", "Макс. высота области, px", false, 16, "макс. высота"),
            new Col("maxPixels", "Макс. пикселей карты", false, 16, "макс. пикселей"),
            new Col("power", "Мощность, Вт", false, 12, "потребление"),
            new Col("weight", "Вес, кг", false, 10, "weight"),
            new Col("width", "Ширина, мм", false, 12, "width"),
            new Col("height", "Высота, мм", false, 12, "height"),
            new Col("depth", "Глубина, мм", false, 12, "depth"),
            new Col("notes", "Заметки", false, 30, "notes", "комментарий", "описание"));

    private static final List<Col> POWER_SUPPLY_COLS = List.of(
            C_NAME,
            new Col("vendor", "Производитель", false, 18, "vendor"),
            C_COMPANY,
            new Col("outputs", "Шлейфов питания модулей", false, 16, "шлейфов", "выходов"),
            new Col("ratedPower", "Номинальная мощность, Вт", false, 16, "мощность"),
            new Col("cableLength", "Длина кабеля до модуля, мм", false, 16, "длина кабеля"),
            new Col("cardOutput", "Отдельный выход на карту (Да/Нет)", false, 18, "выход на карту"),
            new Col("inputVoltage", "Входное напряжение", false, 16, "напряжение"),
            new Col("weight", "Вес, кг", false, 10, "weight"),
            new Col("width", "Ширина, мм", false, 12, "width"),
            new Col("height", "Высота, мм", false, 12, "height"),
            new Col("depth", "Глубина, мм", false, 12, "depth"),
            new Col("notes", "Заметки", false, 30, "notes", "комментарий", "описание"));

    private static final String[] MODE_LIST = {"Сигнал", "Питание"};
    private static final String[] BOOL_LIST = {"Да", "Нет"};
    private static final String[] FORM_LIST = {"В стойку", "Россыпью", "Отдельно"};
    private static final String[] CONNECTOR_LIST = {"PowerCon", "TRUEcon", "Другой/не указан"};

    private static String[] categoryList() {
        SchemaNodeType[] all = SchemaNodeType.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = all[i].getLabel();
        }
        return labels;
    }

    /** Ключ заголовка для сопоставления: нижний регистр, «ё»→«е», без «*», без хвоста после первой
     *  запятой или скобки (там единицы и подсказки), пробелы схлопнуты. Публичный для тестов. */
    public static String headerKey(String header) {
        if (header == null) {
            return "";
        }
        String s = header.toLowerCase(Locale.ROOT).replace('ё', 'е').replace("*", "");
        int cut = s.length();
        for (char stop : new char[] {',', '('}) {
            int i = s.indexOf(stop);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return s.substring(0, cut).replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ шаблон

    /** Записывает пустой шаблон (по одной строке-образцу на листе) в {@code file}. */
    public static void writeTemplate(File file) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Styles st = new Styles(wb);
            writeHelp(wb, st);

            Sheet cab = dataSheet(wb, st, SHEET_CABINETS, CABINET_COLS);
            example(cab, st, CABINET_COLS, Map.ofEntries(
                    Map.entry("name", EXAMPLE_PREFIX + " Кабинет P2.6 500x500"),
                    Map.entry("company", "Моя компания"), Map.entry("width", 500), Map.entry("height", 500),
                    Map.entry("depth", 80), Map.entry("resX", 192), Map.entry("resY", 192),
                    Map.entry("power", 150), Map.entry("weight", 8.5), Map.entry("connector", "PowerCon"),
                    Map.entry("powerLines", 1), Map.entry("signalLines", 1),
                    Map.entry("receiverCardSeparate", "Нет")));
            list(cab, CABINET_COLS, "connector", CONNECTOR_LIST);
            list(cab, CABINET_COLS, "receiverCardSeparate", BOOL_LIST);

            Sheet eq = dataSheet(wb, st, SHEET_EQUIPMENT, EQUIPMENT_COLS);
            example(eq, st, EQUIPMENT_COLS, Map.ofEntries(
                    Map.entry("name", EXAMPLE_PREFIX + " Контроллер 16 портов"), Map.entry("mode", "Сигнал"),
                    Map.entry("category", SchemaNodeType.CONTROLLER.getLabel()),
                    Map.entry("vendor", "Производитель"), Map.entry("description", "Любой комментарий"),
                    Map.entry("inputs", 4), Map.entry("outputs", 16), Map.entry("bandwidth", 1000),
                    Map.entry("loop", "Да"), Map.entry("form", "В стойку"), Map.entry("rackU", 2),
                    Map.entry("depth", 300), Map.entry("weight", 4.5), Map.entry("power", 60)));
            list(eq, EQUIPMENT_COLS, "mode", MODE_LIST);
            list(eq, EQUIPMENT_COLS, "category", categoryList());
            list(eq, EQUIPMENT_COLS, "loop", BOOL_LIST);
            list(eq, EQUIPMENT_COLS, "form", FORM_LIST);

            Sheet cb = dataSheet(wb, st, SHEET_CABLES, CABLE_COLS);
            example(cb, st, CABLE_COLS, Map.of("mode", "Питание", "label", EXAMPLE_PREFIX + " CEE 16A → PowerCon",
                    "length", 5));
            list(cb, CABLE_COLS, "mode", MODE_LIST);

            Sheet rc = dataSheet(wb, st, SHEET_RECEIVER_CARDS, RECEIVER_CARD_COLS);
            example(rc, st, RECEIVER_CARD_COLS, Map.ofEntries(
                    Map.entry("name", EXAMPLE_PREFIX + " Приёмная карта 8 выходов"),
                    Map.entry("vendor", "Производитель"), Map.entry("outputs", 8), Map.entry("ribbon", 600),
                    Map.entry("maxWidth", 512), Map.entry("maxHeight", 384), Map.entry("maxPixels", 131072),
                    Map.entry("power", 5), Map.entry("weight", 0.1)));

            Sheet ps = dataSheet(wb, st, SHEET_POWER_SUPPLIES, POWER_SUPPLY_COLS);
            example(ps, st, POWER_SUPPLY_COLS, Map.ofEntries(
                    Map.entry("name", EXAMPLE_PREFIX + " Блок питания 5 В, 8 шлейфов"),
                    Map.entry("vendor", "Производитель"), Map.entry("outputs", 8), Map.entry("ratedPower", 300),
                    Map.entry("cableLength", 700), Map.entry("cardOutput", "Да"),
                    Map.entry("inputVoltage", "110–240 В AC"), Map.entry("weight", 1.2)));
            list(ps, POWER_SUPPLY_COLS, "cardOutput", BOOL_LIST);

            wb.setActiveSheet(1);
            wb.write(out);
        }
    }

    private static final int LAST_VALIDATED_ROW = 1000;

    private static final class Styles {
        final CellStyle header;
        final CellStyle headerRequired;
        final CellStyle example;
        final CellStyle title;
        final CellStyle wrap;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setWrapText(true);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerRequired = wb.createCellStyle();
            headerRequired.cloneStyleFrom(header);
            headerRequired.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            Font italic = wb.createFont();
            italic.setItalic(true);
            italic.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            example = wb.createCellStyle();
            example.setFont(italic);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);
            wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);
        }
    }

    private static Sheet dataSheet(Workbook wb, Styles st, String name, List<Col> cols) {
        Sheet sheet = wb.createSheet(name);
        Row head = sheet.createRow(0);
        head.setHeightInPoints(34);
        for (int i = 0; i < cols.size(); i++) {
            Cell c = head.createCell(i);
            c.setCellValue(cols.get(i).header());
            c.setCellStyle(cols.get(i).required() ? st.headerRequired : st.header);
            sheet.setColumnWidth(i, cols.get(i).width() * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static void example(Sheet sheet, Styles st, List<Col> cols, Map<String, Object> values) {
        Row row = sheet.createRow(1);
        for (int i = 0; i < cols.size(); i++) {
            Object v = values.get(cols.get(i).id());
            if (v == null) {
                continue;
            }
            Cell c = row.createCell(i);
            if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else {
                c.setCellValue(String.valueOf(v));
            }
            c.setCellStyle(st.example);
        }
    }

    private static void list(Sheet sheet, List<Col> cols, String colId, String[] values) {
        int idx = -1;
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).id().equals(colId)) {
                idx = i;
            }
        }
        DataValidationHelper h = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = h.createExplicitListConstraint(values);
        DataValidation v = h.createValidation(constraint, new CellRangeAddressList(1, LAST_VALIDATED_ROW, idx, idx));
        v.setShowErrorBox(false); // свободный текст не запрещаем — парсер сам скажет, что не понял
        sheet.addValidationData(v);
    }

    private static void writeHelp(Workbook wb, Styles st) {
        Sheet help = wb.createSheet(SHEET_HELP);
        help.setColumnWidth(0, 110 * 256);
        String[] lines = {
                "#Шаблон библиотеки оборудования",
                "Заполните листы «Кабинеты», «Оборудование», «Кабели», «Приёмные карты», «Блоки питания» (нужны не все — пустые листы пропускаются) "
                        + "и загрузите файл в приложении: Библиотеки → «Импорт из Excel…».",
                "",
                "#Общие правила",
                "• Первая строка каждого листа — заголовки. Столбцы можно переставлять, а свои — добавлять "
                        + "(неизвестные столбцы игнорируются). Столбцы со звёздочкой (*) обязательны, их заголовки "
                        + "подсвечены жёлтым.",
                "• Серая курсивная строка «Пример: …» — образец. Её можно удалить; если оставить, она всё равно "
                        + "не попадёт в библиотеку (строки с названием на «Пример:» пропускаются).",
                "• Числа — обычные числа Excel (дробная часть через запятую или точку). Единицы измерения указаны "
                        + "в заголовках, писать их в ячейки не нужно.",
                "• Если запись с таким названием уже есть в вашей библиотеке, она обновится ТОЛЬКО заполненными "
                        + "ячейками, остальное (карты, разъёмы, формы кабинета) останется как было. Записи общей "
                        + "серверной библиотеки не изменяются — совпадение с ними пропускается с предупреждением.",
                "• Строка с ошибкой пропускается, остальные импортируются; после импорта приложение покажет, "
                        + "что именно не принято и почему.",
                "",
                "#Лист «Кабинеты»",
                "Обязательно: название, ширина и высота (мм), разрешение по горизонтали и вертикали (px). "
                        + "Мощность по умолчанию 150 Вт, вес 12 кг, если не указаны. Разъём питания — из списка "
                        + "(PowerCon / TRUEcon / Другой). «Линий питания/сигнала» — сколько линий нужно развести "
                        + "на кабинет (0 — коммутация сквозная). «Приёмная карта отдельно» (Да/Нет): «Да» делает "
                        + "тип модулем — у него нет своей приёмной карты, он подключается шлейфом к общей карте "
                        + "и питается от блока питания.",
                "",
                "#Лист «Оборудование»",
                "Обязательно только название. Режим: Сигнал (по умолчанию) или Питание. Категория — из списка "
                        + "(Источник/щит, Распределение, Конвертер, Медиасервер, Контроллер, Экран, Монитор/TV, "
                        + "Прочее оборудование); неизвестный текст попадёт в «Прочее оборудование» как "
                        + "подкатегория с этим названием. Столбцы «Входных/Выходных портов», «Пропускная "
                        + "способность» и «Loop-порт» значимы только для категории «Контроллер».",
                "Габариты нужны Планеру пультовой. Способ установки: «В стойку» (нужны высота в U и глубина), "
                        + "«Россыпью» или «Отдельно» (нужны ширина, высота и глубина). Если способ не указан, "
                        + "он определяется сам: есть высота в U — в стойку, есть размеры — россыпью.",
                "Карты ввода/вывода и разъёмы питания в таблице не задаются — добавьте их в редакторе пресета "
                        + "после импорта.",
                "",
                "#Лист «Кабели»",
                "Это переходники (разные разъёмы на концах). Обязательно: режим (Сигнал/Питание) и название. "
                        + "Длина, м — если переходник фиксированной длины.",
                "",
                "#Листы «Приёмные карты» и «Блоки питания»",
                "Для модульных экранов. Обязательно только название. «Выходов на модули» — сколько модулей можно "
                        + "включить в карту; «Длина шлейфа» (карта) и «Длина кабеля» (блок питания) — предельное "
                        + "расстояние до модуля по прямой, мм. Пустая ячейка означает «не указано, не проверять» "
                        + "(это не ноль). «Отдельный выход на карту» (Да/Нет) — у блока питания есть ещё один выход "
                        + "сверх шлейфов, предназначенный только для приёмной карты. Ширина, высота и глубина — "
                        + "габариты россыпью, мм.",
        };
        for (int i = 0; i < lines.length; i++) {
            Row r = help.createRow(i);
            Cell c = r.createCell(0);
            String line = lines[i];
            if (line.startsWith("#")) {
                c.setCellValue(line.substring(1));
                c.setCellStyle(i == 0 ? st.title : st.header);
            } else {
                c.setCellValue(line);
                c.setCellStyle(st.wrap);
                if (line.length() > 105) {
                    r.setHeightInPoints(15f * (1 + line.length() / 105));
                }
            }
        }
    }

    // ------------------------------------------------------------------ разбор

    /** Читает файл; {@link IllegalArgumentException} с текстом для пользователя, если файл не открывается
     *  или в нём нет ни одного листа с данными. */
    public static Parsed parse(File file) {
        List<String> warnings = new ArrayList<>();
        List<CabinetRow> cabinets = new ArrayList<>();
        List<EquipmentRow> equipment = new ArrayList<>();
        List<CableRow> cables = new ArrayList<>();
        List<ReceiverCardRow> receiverCards = new ArrayList<>();
        List<PowerSupplyRow> powerSupplies = new ArrayList<>();
        boolean any = false;
        try (Workbook wb = WorkbookFactory.create(file, null, true)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sheetName = sheet.getSheetName().trim();
                if (sheetName.equalsIgnoreCase(SHEET_CABINETS)) {
                    any = true;
                    parseCabinets(sheet, cabinets, warnings);
                } else if (sheetName.equalsIgnoreCase(SHEET_EQUIPMENT)) {
                    any = true;
                    parseEquipment(sheet, equipment, warnings);
                } else if (sheetName.equalsIgnoreCase(SHEET_CABLES)) {
                    any = true;
                    parseCables(sheet, cables, warnings);
                } else if (sheetName.equalsIgnoreCase(SHEET_RECEIVER_CARDS)) {
                    any = true;
                    parseReceiverCards(sheet, receiverCards, warnings);
                } else if (sheetName.equalsIgnoreCase(SHEET_POWER_SUPPLIES)) {
                    any = true;
                    parsePowerSupplies(sheet, powerSupplies, warnings);
                }
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Не удалось прочитать Excel-файл: " + ex.getMessage(), ex);
        }
        if (!any) {
            throw new IllegalArgumentException("В файле нет листов «" + SHEET_CABINETS + "», «"
                    + SHEET_EQUIPMENT + "», «" + SHEET_CABLES + "», «" + SHEET_RECEIVER_CARDS + "» или «"
                    + SHEET_POWER_SUPPLIES + "» — это не шаблон библиотеки. "
                    + "Скачайте шаблон кнопкой «Шаблон Excel…».");
        }
        return new Parsed(cabinets, equipment, cables, receiverCards, powerSupplies, warnings);
    }

    /** Индексы столбцов по id; {@code null}, если на листе нет какого-то обязательного столбца (предупреждение уже добавлено). */
    private static Map<String, Integer> locateColumns(Sheet sheet, List<Col> cols, List<String> warnings) {
        Row head = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> byKey = new HashMap<>();
        if (head != null) {
            for (Cell c : head) {
                String key = headerKey(text(c));
                if (!key.isEmpty()) {
                    byKey.putIfAbsent(key, c.getColumnIndex());
                }
            }
        }
        Map<String, Integer> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (Col col : cols) {
            Integer idx = byKey.get(headerKey(col.header()));
            for (int a = 0; idx == null && a < col.aliases().length; a++) {
                idx = byKey.get(headerKey(col.aliases()[a]));
            }
            if (idx != null) {
                result.put(col.id(), idx);
            } else if (col.required()) {
                missing.add(col.header().replace("*", "").trim());
            }
        }
        if (!missing.isEmpty()) {
            warnings.add("Лист «" + sheet.getSheetName() + "» пропущен: нет обязательных столбцов — "
                    + String.join(", ", missing) + ".");
            return null;
        }
        return result;
    }

    private static boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell c : row) {
            if (!text(c).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExample(String name) {
        return name.regionMatches(true, 0, EXAMPLE_PREFIX, 0, EXAMPLE_PREFIX.length());
    }

    /** Читатель ячеек одной строки — копит предупреждения «строка N, столбец …». */
    private static final class RowReader {
        private final Sheet sheet;
        private final Row row;
        private final Map<String, Integer> cols;
        private final Map<String, String> headers = new HashMap<>();
        private final List<String> warnings;
        boolean broken;

        RowReader(Sheet sheet, Row row, Map<String, Integer> cols, List<Col> defs, List<String> warnings) {
            this.sheet = sheet;
            this.row = row;
            this.cols = cols;
            this.warnings = warnings;
            for (Col d : defs) {
                headers.put(d.id(), d.header().replace("*", "").trim());
            }
        }

        int number() {
            return row.getRowNum() + 1;
        }

        String where(String id) {
            return "Лист «" + sheet.getSheetName() + "», строка " + number() + ", «" + headers.get(id) + "»: ";
        }

        String str(String id) {
            Integer idx = cols.get(id);
            return idx == null ? "" : text(row.getCell(idx));
        }

        /** Неотрицательное число или {@code null} (пусто / не число — во втором случае с предупреждением). */
        Double dbl(String id) {
            String s = str(id);
            if (s.isEmpty()) {
                return null;
            }
            Double v = parseNumber(s);
            if (v == null || v < 0) {
                warnings.add(where(id) + "«" + s + "» — не неотрицательное число"
                        + (isRequired(id) ? ", строка пропущена." : ", значение не применено."));
                broken |= isRequired(id);
                return null;
            }
            return v;
        }

        Integer integer(String id) {
            Double v = dbl(id);
            if (v == null) {
                return null;
            }
            if (v != Math.rint(v)) {
                warnings.add(where(id) + "«" + str(id) + "» — нужно целое число"
                        + (isRequired(id) ? ", строка пропущена." : ", значение не применено."));
                broken |= isRequired(id);
                return null;
            }
            return (int) Math.round(v);
        }

        private boolean isRequired(String id) {
            return required.contains(id);
        }

        private java.util.Set<String> required = java.util.Set.of();

        RowReader requiring(String... ids) {
            this.required = java.util.Set.of(ids);
            return this;
        }
    }

    private static void parseCabinets(Sheet sheet, List<CabinetRow> out, List<String> warnings) {
        Map<String, Integer> cols = locateColumns(sheet, CABINET_COLS, warnings);
        if (cols == null) {
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row)) {
                continue;
            }
            RowReader rd = new RowReader(sheet, row, cols, CABINET_COLS, warnings)
                    .requiring("width", "height", "resX", "resY");
            String name = rd.str("name");
            if (isExample(name)) {
                continue;
            }
            if (name.isEmpty()) {
                warnings.add(rd.where("name") + "не заполнено, строка пропущена.");
                continue;
            }
            Double w = rd.dbl("width");
            Double h = rd.dbl("height");
            Integer rx = rd.integer("resX");
            Integer ry = rd.integer("resY");
            if (w == null || h == null || rx == null || ry == null) {
                if (!rd.broken) {
                    warnings.add(rd.where("name") + "«" + name + "»: не указаны ширина, высота или разрешение, "
                            + "строка пропущена.");
                }
                continue;
            }
            if (w <= 0 || h <= 0 || rx <= 0 || ry <= 0) {
                warnings.add(rd.where("name") + "«" + name + "»: размеры и разрешение должны быть больше нуля, "
                        + "строка пропущена.");
                continue;
            }
            rd.requiring();
            Double depth = rd.dbl("depth");
            Double power = rd.dbl("power");
            Double weight = rd.dbl("weight");
            Integer powerLines = rd.integer("powerLines");
            Integer signalLines = rd.integer("signalLines");
            PowerConnectorType connector = null;
            String connectorText = rd.str("connector");
            if (!connectorText.isEmpty()) {
                connector = parseConnector(connectorText);
                if (connector == null) {
                    warnings.add(rd.where("connector") + "«" + connectorText
                            + "» не из списка (PowerCon / TRUEcon / Другой), значение не применено.");
                }
            }
            Boolean module = null;
            String moduleText = rd.str("receiverCardSeparate");
            if (!moduleText.isEmpty()) {
                module = parseBool(moduleText);
                if (module == null) {
                    warnings.add(rd.where("receiverCardSeparate") + "«" + moduleText
                            + "» — ожидалось «Да» или «Нет», значение не применено.");
                }
            }
            out.add(new CabinetRow(r + 1, name, blankToNull(rd.str("company")), w, h, depth, rx, ry, power, weight,
                    connector, powerLines, signalLines, module));
        }
    }

    private static void parseEquipment(Sheet sheet, List<EquipmentRow> out, List<String> warnings) {
        Map<String, Integer> cols = locateColumns(sheet, EQUIPMENT_COLS, warnings);
        if (cols == null) {
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row)) {
                continue;
            }
            RowReader rd = new RowReader(sheet, row, cols, EQUIPMENT_COLS, warnings);
            String name = rd.str("name");
            if (isExample(name)) {
                continue;
            }
            if (name.isEmpty()) {
                warnings.add(rd.where("name") + "не заполнено, строка пропущена.");
                continue;
            }
            SchemaMode mode = SchemaMode.SIGNAL;
            String modeText = rd.str("mode");
            if (!modeText.isEmpty()) {
                SchemaMode parsed = parseMode(modeText);
                if (parsed == null) {
                    warnings.add(rd.where("mode") + "«" + modeText + "» — ожидалось «Сигнал» или «Питание», "
                            + "принято «Сигнал».");
                } else {
                    mode = parsed;
                }
            }
            SchemaNodeType category = SchemaNodeType.CUSTOM;
            String customLabel = "";
            String categoryText = rd.str("category");
            if (!categoryText.isEmpty()) {
                SchemaNodeType parsed = parseCategory(categoryText);
                if (parsed != null) {
                    category = parsed;
                } else {
                    customLabel = categoryText;
                    warnings.add(rd.where("category") + "«" + categoryText + "» не из списка — записано как "
                            + "«Прочее оборудование / " + categoryText + "».");
                }
            }
            Boolean loop = null;
            String loopText = rd.str("loop");
            if (!loopText.isEmpty()) {
                loop = parseBool(loopText);
                if (loop == null) {
                    warnings.add(rd.where("loop") + "«" + loopText + "» — ожидалось «Да» или «Нет», значение не применено.");
                }
            }
            PhysicalSpec.FormFactor form = null;
            String formText = rd.str("form");
            if (!formText.isEmpty()) {
                form = parseForm(formText);
                if (form == null) {
                    warnings.add(rd.where("form") + "«" + formText + "» — ожидалось «В стойку», «Россыпью» или "
                            + "«Отдельно», способ определён по размерам.");
                }
            }
            out.add(new EquipmentRow(r + 1, name, mode, category, customLabel, blankToNull(rd.str("vendor")),
                    blankToNull(rd.str("company")), blankToNull(rd.str("description")), rd.integer("inputs"),
                    rd.integer("outputs"), rd.dbl("bandwidth"), loop, form, rd.integer("rackU"), rd.dbl("width"),
                    rd.dbl("height"), rd.dbl("depth"), rd.dbl("weight"), rd.dbl("power")));
        }
    }

    private static void parseCables(Sheet sheet, List<CableRow> out, List<String> warnings) {
        Map<String, Integer> cols = locateColumns(sheet, CABLE_COLS, warnings);
        if (cols == null) {
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row)) {
                continue;
            }
            RowReader rd = new RowReader(sheet, row, cols, CABLE_COLS, warnings);
            String label = rd.str("label");
            if (isExample(label)) {
                continue;
            }
            if (label.isEmpty()) {
                warnings.add(rd.where("label") + "не заполнено, строка пропущена.");
                continue;
            }
            SchemaMode mode = parseMode(rd.str("mode"));
            if (mode == null) {
                warnings.add(rd.where("mode") + "«" + rd.str("mode") + "» — ожидалось «Сигнал» или «Питание», "
                        + "строка пропущена.");
                continue;
            }
            out.add(new CableRow(r + 1, mode, label, rd.dbl("length")));
        }
    }

    private static void parseReceiverCards(Sheet sheet, List<ReceiverCardRow> out, List<String> warnings) {
        Map<String, Integer> cols = locateColumns(sheet, RECEIVER_CARD_COLS, warnings);
        if (cols == null) {
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row)) {
                continue;
            }
            RowReader rd = new RowReader(sheet, row, cols, RECEIVER_CARD_COLS, warnings);
            String name = rd.str("name");
            if (isExample(name)) {
                continue;
            }
            if (name.isEmpty()) {
                warnings.add(rd.where("name") + "не заполнено, строка пропущена.");
                continue;
            }
            out.add(new ReceiverCardRow(r + 1, name, blankToNull(rd.str("vendor")), blankToNull(rd.str("company")),
                    rd.integer("outputs"), rd.dbl("ribbon"), rd.integer("maxWidth"), rd.integer("maxHeight"),
                    rd.integer("maxPixels"), rd.dbl("power"), rd.dbl("weight"), rd.dbl("width"), rd.dbl("height"),
                    rd.dbl("depth"), blankToNull(rd.str("notes"))));
        }
    }

    private static void parsePowerSupplies(Sheet sheet, List<PowerSupplyRow> out, List<String> warnings) {
        Map<String, Integer> cols = locateColumns(sheet, POWER_SUPPLY_COLS, warnings);
        if (cols == null) {
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row)) {
                continue;
            }
            RowReader rd = new RowReader(sheet, row, cols, POWER_SUPPLY_COLS, warnings);
            String name = rd.str("name");
            if (isExample(name)) {
                continue;
            }
            if (name.isEmpty()) {
                warnings.add(rd.where("name") + "не заполнено, строка пропущена.");
                continue;
            }
            Boolean cardOutput = null;
            String cardOutputText = rd.str("cardOutput");
            if (!cardOutputText.isEmpty()) {
                cardOutput = parseBool(cardOutputText);
                if (cardOutput == null) {
                    warnings.add(rd.where("cardOutput") + "«" + cardOutputText
                            + "» — ожидалось «Да» или «Нет», значение не применено.");
                }
            }
            out.add(new PowerSupplyRow(r + 1, name, blankToNull(rd.str("vendor")), blankToNull(rd.str("company")),
                    rd.integer("outputs"), rd.dbl("ratedPower"), rd.dbl("cableLength"), cardOutput,
                    blankToNull(rd.str("inputVoltage")), rd.dbl("weight"), rd.dbl("width"), rd.dbl("height"),
                    rd.dbl("depth"), blankToNull(rd.str("notes"))));
        }
    }

    // ------------------------------------------------------------------ значения ячеек

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Текст ячейки как её видит пользователь: числа без «.0», формулы — по кэшированному результату. */
    static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
            }
            case STRING -> cell.getStringCellValue().replace(' ', ' ').trim();
            case BOOLEAN -> cell.getBooleanCellValue() ? "Да" : "Нет";
            default -> "";
        };
    }

    /** Число из текста с запятой или точкой, пробелами-разделителями тысяч; {@code null}, если не число. */
    static Double parseNumber(String s) {
        String t = s.replace(' ', ' ').replace(" ", "").replace(',', '.');
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static SchemaMode parseMode(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("сигнал") || t.equals("signal")) {
            return SchemaMode.SIGNAL;
        }
        if (t.startsWith("питан") || t.equals("power")) {
            return SchemaMode.POWER;
        }
        return null;
    }

    static SchemaNodeType parseCategory(String s) {
        String t = s.trim();
        for (SchemaNodeType c : SchemaNodeType.values()) {
            if (c.getLabel().equalsIgnoreCase(t) || c.name().equalsIgnoreCase(t)) {
                return c;
            }
        }
        return null;
    }

    static PowerConnectorType parseConnector(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (PowerConnectorType c : PowerConnectorType.values()) {
            if (c.getLabel().toLowerCase(Locale.ROOT).equals(t) || c.name().toLowerCase(Locale.ROOT).equals(t)) {
                return c;
            }
        }
        if (t.startsWith("другой") || t.equals("other")) {
            return PowerConnectorType.OTHER;
        }
        return null;
    }

    static Boolean parseBool(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("да") || t.equals("yes") || t.equals("true") || t.equals("1") || t.equals("+")) {
            return Boolean.TRUE;
        }
        if (t.equals("нет") || t.equals("no") || t.equals("false") || t.equals("0") || t.equals("-")) {
            return Boolean.FALSE;
        }
        return null;
    }

    static PhysicalSpec.FormFactor parseForm(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.contains("стойк") || t.equals("rack")) {
            return PhysicalSpec.FormFactor.RACK;
        }
        if (t.contains("россып") || t.equals("loose")) {
            return PhysicalSpec.FormFactor.LOOSE;
        }
        if (t.contains("отдельн") || t.equals("standalone")) {
            return PhysicalSpec.FormFactor.STANDALONE;
        }
        return null;
    }

    // ------------------------------------------------------------------ слияние

    /** Вливает разобранные строки в библиотеку пользователя (см. class-javadoc про ключи и «только заполненные ячейки»). */
    public static Report merge(Parsed parsed, Libraries lib) {
        List<String> warnings = new ArrayList<>(parsed.warnings());
        int cabAdded = 0;
        int cabUpdated = 0;
        for (CabinetRow row : parsed.cabinets()) {
            if (findCabinet(lib.sharedCabinets(), row.name()) != null) {
                warnings.add("Кабинет «" + row.name() + "» (строка " + row.rowNumber()
                        + ") уже есть в общей библиотеке — пропущен.");
                continue;
            }
            CabinetType match = findCabinet(lib.cabinets(), row.name());
            CabinetType target = match != null ? match : new CabinetType();
            applyCabinet(target, row);
            if (match != null) {
                cabUpdated++;
            } else {
                lib.cabinets().add(target);
                cabAdded++;
            }
        }
        int eqAdded = 0;
        int eqUpdated = 0;
        for (EquipmentRow row : parsed.equipment()) {
            if (findEquipment(lib.sharedEquipment(), row) != null) {
                warnings.add("Оборудование «" + row.name() + "» (строка " + row.rowNumber()
                        + ") уже есть в общей библиотеке — пропущено.");
                continue;
            }
            EquipmentPreset match = findEquipment(lib.equipment(), row);
            EquipmentPreset target = match != null ? match : new EquipmentPreset();
            applyEquipment(target, row);
            if (match != null) {
                eqUpdated++;
            } else {
                lib.equipment().add(target);
                eqAdded++;
            }
        }
        int cbAdded = 0;
        int cbUpdated = 0;
        for (CableRow row : parsed.cables()) {
            if (findCable(lib.sharedCables(), row) != null) {
                warnings.add("Кабель «" + row.label() + "» (строка " + row.rowNumber()
                        + ") уже есть в общей библиотеке — пропущен.");
                continue;
            }
            CableType match = findCable(lib.cables(), row);
            CableType target = match != null ? match : new CableType();
            target.setMode(row.mode());
            target.setLabel(row.label());
            if (row.fixedLengthM() != null) {
                target.setFixedLengthM(row.fixedLengthM());
            }
            if (match != null) {
                cbUpdated++;
            } else {
                lib.cables().add(target);
                cbAdded++;
            }
        }
        int rcAdded = 0;
        int rcUpdated = 0;
        for (ReceiverCardRow row : parsed.receiverCards()) {
            if (findReceiverCard(lib.sharedReceiverCards(), row.name()) != null) {
                warnings.add("Приёмная карта «" + row.name() + "» (строка " + row.rowNumber()
                        + ") уже есть в общей библиотеке — пропущена.");
                continue;
            }
            ReceiverCardType match = findReceiverCard(lib.receiverCards(), row.name());
            ReceiverCardType target = match != null ? match : new ReceiverCardType();
            applyReceiverCard(target, row);
            if (match != null) {
                rcUpdated++;
            } else {
                lib.receiverCards().add(target);
                rcAdded++;
            }
        }
        int psAdded = 0;
        int psUpdated = 0;
        for (PowerSupplyRow row : parsed.powerSupplies()) {
            if (findPowerSupply(lib.sharedPowerSupplies(), row.name()) != null) {
                warnings.add("Блок питания «" + row.name() + "» (строка " + row.rowNumber()
                        + ") уже есть в общей библиотеке — пропущен.");
                continue;
            }
            PowerSupplyType match = findPowerSupply(lib.powerSupplies(), row.name());
            PowerSupplyType target = match != null ? match : new PowerSupplyType();
            applyPowerSupply(target, row);
            if (match != null) {
                psUpdated++;
            } else {
                lib.powerSupplies().add(target);
                psAdded++;
            }
        }
        return new Report(cabAdded, cabUpdated, eqAdded, eqUpdated, cbAdded, cbUpdated, rcAdded, rcUpdated,
                psAdded, psUpdated, warnings);
    }

    private static ReceiverCardType findReceiverCard(List<ReceiverCardType> list, String name) {
        for (ReceiverCardType t : list) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    private static PowerSupplyType findPowerSupply(List<PowerSupplyType> list, String name) {
        for (PowerSupplyType t : list) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    /** Габариты россыпью из трёх столбцов; пустые ячейки существующий физический блок не затирают. */
    private static PhysicalSpec withDimensions(PhysicalSpec current, Double w, Double h, Double d) {
        if (w == null && h == null && d == null) {
            return current;
        }
        PhysicalSpec spec = current != null ? current : new PhysicalSpec();
        if (w != null) {
            spec.setWidthMm(w);
        }
        if (h != null) {
            spec.setHeightMm(h);
        }
        if (d != null) {
            spec.setDepthMm(d);
        }
        if (spec.getFormFactor() == null) {
            spec.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        }
        return spec;
    }

    private static void applyReceiverCard(ReceiverCardType t, ReceiverCardRow r) {
        t.setName(r.name());
        if (r.vendor() != null) {
            t.setVendor(r.vendor());
        }
        if (r.company() != null) {
            t.setCompany(r.company());
        }
        if (r.moduleOutputs() != null) {
            t.setModuleOutputs(r.moduleOutputs());
        }
        if (r.ribbonLengthMm() != null) {
            t.setRibbonLengthMm(r.ribbonLengthMm());
        }
        if (r.maxWidthPx() != null) {
            t.setMaxWidthPx(r.maxWidthPx());
        }
        if (r.maxHeightPx() != null) {
            t.setMaxHeightPx(r.maxHeightPx());
        }
        if (r.maxPixels() != null) {
            t.setMaxPixels(r.maxPixels());
        }
        if (r.powerW() != null) {
            t.setPowerW(r.powerW());
        }
        if (r.weightKg() != null) {
            t.setWeightKg(r.weightKg());
        }
        if (r.notes() != null) {
            t.setNotes(r.notes());
        }
        t.setPhysical(withDimensions(t.getPhysical(), r.widthMm(), r.heightMm(), r.depthMm()));
    }

    private static void applyPowerSupply(PowerSupplyType t, PowerSupplyRow r) {
        t.setName(r.name());
        if (r.vendor() != null) {
            t.setVendor(r.vendor());
        }
        if (r.company() != null) {
            t.setCompany(r.company());
        }
        if (r.outputs() != null) {
            t.setOutputs(r.outputs());
        }
        if (r.ratedPowerW() != null) {
            t.setRatedPowerW(r.ratedPowerW());
        }
        if (r.cableLengthMm() != null) {
            t.setCableLengthMm(r.cableLengthMm());
        }
        if (r.cardOutput() != null) {
            t.setCardOutput(r.cardOutput());
        }
        if (r.inputVoltage() != null) {
            t.setInputVoltage(r.inputVoltage());
        }
        if (r.weightKg() != null) {
            t.setWeightKg(r.weightKg());
        }
        if (r.notes() != null) {
            t.setNotes(r.notes());
        }
        t.setPhysical(withDimensions(t.getPhysical(), r.widthMm(), r.heightMm(), r.depthMm()));
    }

    private static CabinetType findCabinet(List<CabinetType> list, String name) {
        for (CabinetType c : list) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    private static EquipmentPreset findEquipment(List<EquipmentPreset> list, EquipmentRow row) {
        for (EquipmentPreset p : list) {
            if (p.getMode() == row.mode() && p.getCategory() == row.category()
                    && p.getName().equalsIgnoreCase(row.name())) {
                return p;
            }
        }
        return null;
    }

    private static CableType findCable(List<CableType> list, CableRow row) {
        for (CableType c : list) {
            if (c.getMode() == row.mode() && c.getLabel().equalsIgnoreCase(row.label())) {
                return c;
            }
        }
        return null;
    }

    private static void applyCabinet(CabinetType t, CabinetRow r) {
        t.setName(r.name());
        if (r.company() != null) {
            t.setCompany(r.company());
        }
        t.setWidthMm(r.widthMm());
        t.setHeightMm(r.heightMm());
        if (r.depthMm() != null) {
            t.setDepthMm(r.depthMm());
        }
        t.setResolutionWidth(r.resolutionWidth());
        t.setResolutionHeight(r.resolutionHeight());
        if (r.powerW() != null) {
            t.setPowerConsumptionW(r.powerW());
        }
        if (r.weightKg() != null) {
            t.setWeightKg(r.weightKg());
        }
        if (r.connector() != null) {
            t.setPowerConnectorType(r.connector());
        }
        if (r.powerLines() != null) {
            t.setPowerConnectorsNeeded(r.powerLines());
        }
        if (r.signalLines() != null) {
            t.setSignalConnectorsNeeded(r.signalLines());
        }
        if (r.receiverCardSeparate() != null) {
            t.setReceiverCardSeparate(r.receiverCardSeparate());
        }
    }

    private static void applyEquipment(EquipmentPreset p, EquipmentRow r) {
        p.setName(r.name());
        p.setMode(r.mode());
        p.setCategory(r.category());
        if (r.category() == SchemaNodeType.CUSTOM && !r.customCategoryLabel().isEmpty()) {
            p.setCustomCategoryLabel(r.customCategoryLabel());
        }
        if (r.vendor() != null) {
            p.setVendor(r.vendor());
        }
        if (r.company() != null) {
            p.setCompany(r.company());
        }
        if (r.description() != null) {
            p.setDescription(r.description());
        }
        if (r.category() == SchemaNodeType.CONTROLLER) {
            if (r.inputPorts() != null) {
                p.setInputPortCount(r.inputPorts());
            }
            if (r.outputPorts() != null) {
                p.setPortCount(r.outputPorts());
            }
            if (r.portBandwidthMbps() != null) {
                p.setPortBandwidthMbps(r.portBandwidthMbps());
            }
            if (r.loopPort() != null) {
                p.setLoopPort(r.loopPort());
            }
        }
        boolean hasPhysical = r.formFactor() != null || r.rackUnits() != null || r.widthMm() != null
                || r.heightMm() != null || r.depthMm() != null || r.weightKg() != null || r.powerW() != null;
        if (hasPhysical) {
            PhysicalSpec spec = p.getPhysical() != null ? p.getPhysical() : new PhysicalSpec();
            if (r.rackUnits() != null) {
                spec.setRackUnits(r.rackUnits());
            }
            if (r.widthMm() != null) {
                spec.setWidthMm(r.widthMm());
            }
            if (r.heightMm() != null) {
                spec.setHeightMm(r.heightMm());
            }
            if (r.depthMm() != null) {
                spec.setDepthMm(r.depthMm());
            }
            if (r.weightKg() != null) {
                spec.setWeightKg(r.weightKg());
            }
            if (r.powerW() != null) {
                spec.setPowerW(r.powerW());
            }
            if (r.formFactor() != null) {
                spec.setFormFactor(r.formFactor());
            } else if (spec.getFormFactor() == null) {
                spec.setFormFactor(spec.getRackUnits() != null ? PhysicalSpec.FormFactor.RACK
                        : PhysicalSpec.FormFactor.LOOSE);
            }
            p.setPhysical(spec);
        }
    }
}
