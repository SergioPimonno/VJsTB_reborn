package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Импорт библиотеки из Excel и шаблон для него — см. {@link LibraryXlsx}. */
class LibraryXlsxTest {

    @TempDir
    Path tmp;

    private static LibraryXlsx.Libraries emptyLibraries() {
        return new LibraryXlsx.Libraries(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private File write(Workbook wb) throws IOException {
        File f = tmp.resolve("lib-" + System.nanoTime() + ".xlsx").toFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            wb.write(out);
        }
        return f;
    }

    private static void row(Sheet sheet, Object... cells) {
        Row r = sheet.createRow(sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] instanceof Number n) {
                r.createCell(i).setCellValue(n.doubleValue());
            } else if (cells[i] != null) {
                r.createCell(i).setCellValue(String.valueOf(cells[i]));
            }
        }
    }

    @Test
    void untouchedTemplateImportsNothingAndWarnsAboutNothing() throws IOException {
        File template = tmp.resolve("template.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);

        LibraryXlsx.Parsed parsed = LibraryXlsx.parse(template);

        assertTrue(parsed.cabinets().isEmpty());
        assertTrue(parsed.equipment().isEmpty());
        assertTrue(parsed.cables().isEmpty());
        assertTrue(parsed.warnings().isEmpty(), parsed.warnings().toString());
    }

    @Test
    void templateHasInstructionAndThreeDataSheetsWithDropdowns() throws Exception {
        File template = tmp.resolve("template.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);

        try (Workbook wb = new XSSFWorkbook(template)) {
            assertEquals(LibraryXlsx.SHEET_HELP, wb.getSheetAt(0).getSheetName());
            assertNotNull(wb.getSheet(LibraryXlsx.SHEET_CABINETS));
            assertNotNull(wb.getSheet(LibraryXlsx.SHEET_EQUIPMENT));
            assertNotNull(wb.getSheet(LibraryXlsx.SHEET_CABLES));
            assertFalse(wb.getSheet(LibraryXlsx.SHEET_EQUIPMENT).getDataValidations().isEmpty());
        }
    }

    @Test
    void filledTemplateRoundTripsIntoLibrary() throws Exception {
        File template = tmp.resolve("template.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        File filled;
        try (Workbook wb = new XSSFWorkbook(template)) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "ROE BP2", "Rental", 500, 500.5, 90, 192, 192, 175, "9,5",
                    "TRUEcon", 1, 2);
            row(wb.getSheet(LibraryXlsx.SHEET_EQUIPMENT), "Контроллер X", "сигнал", "контроллер", "Vend", "Co",
                    "описание", 2, 16, 1000, "да", "в стойку", 1, null, null, 350, 5, 40);
            row(wb.getSheet(LibraryXlsx.SHEET_CABLES), "Питание", "CEE16 → PowerCon", "3,5");
            filled = write(wb);
        }

        LibraryXlsx.Parsed parsed = LibraryXlsx.parse(filled);
        assertTrue(parsed.warnings().isEmpty(), parsed.warnings().toString());
        LibraryXlsx.Libraries lib = emptyLibraries();
        LibraryXlsx.Report report = LibraryXlsx.merge(parsed, lib);

        assertEquals(1, report.cabinetsAdded());
        assertEquals(1, report.equipmentAdded());
        assertEquals(1, report.cablesAdded());
        CabinetType cab = lib.cabinets().get(0);
        assertEquals("ROE BP2", cab.getName());
        assertEquals("Rental", cab.getCompany());
        assertEquals(500.5, cab.getHeightMm(), 1e-9);
        assertEquals(90.0, cab.getDepthMm(), 1e-9);
        assertEquals(192, cab.getResolutionWidth());
        assertEquals(9.5, cab.getWeightKg(), 1e-9);
        assertEquals(PowerConnectorType.TRUECON, cab.getPowerConnectorType());
        assertEquals(2, cab.getSignalConnectorsNeeded());

        EquipmentPreset eq = lib.equipment().get(0);
        assertEquals(SchemaMode.SIGNAL, eq.getMode());
        assertEquals(SchemaNodeType.CONTROLLER, eq.getCategory());
        assertEquals("Vend", eq.getVendor());
        assertEquals(16, eq.getPortCount());
        assertEquals(2, eq.getInputPortCount());
        assertTrue(eq.isLoopPort());
        PhysicalSpec phys = eq.getPhysical();
        assertEquals(PhysicalSpec.FormFactor.RACK, phys.getFormFactor());
        assertEquals(1, phys.getRackUnits());
        assertEquals(350.0, phys.getDepthMm(), 1e-9);
        assertTrue(phys.missingFields().isEmpty());

        CableType cable = lib.cables().get(0);
        assertEquals(SchemaMode.POWER, cable.getMode());
        assertEquals(3.5, cable.getFixedLengthM(), 1e-9);
    }

    @Test
    void columnsAreFoundByHeaderNotPosition() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("кабинеты");
            row(s, "Разрешение по вертикали", "Своя колонка", "ВЫСОТА, ММ", "Разрешение по горизонтали", "Название",
                    "Ширина");
            row(s, 64, "x", 250, 128, "Узкий", 1000);
            LibraryXlsx.Parsed parsed = LibraryXlsx.parse(write(wb));

            assertEquals(1, parsed.cabinets().size());
            LibraryXlsx.CabinetRow c = parsed.cabinets().get(0);
            assertEquals("Узкий", c.name());
            assertEquals(1000.0, c.widthMm());
            assertEquals(250.0, c.heightMm());
            assertEquals(128, c.resolutionWidth());
            assertEquals(64, c.resolutionHeight());
        }
    }

    @Test
    void badRowsAreSkippedWithWarningsAndGoodRowsStillImport() throws IOException {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            Sheet cab = wb.getSheet(LibraryXlsx.SHEET_CABINETS);
            row(cab, "Хороший", null, 500, 500, null, 128, 128);
            row(cab, null, null, 500, 500, null, 128, 128);                  // нет названия
            row(cab, "Без размера", null, null, 500, null, 128, 128);         // нет ширины
            row(cab, "Буквы", null, "широкий", 500, null, 128, 128);          // не число
            row(cab, "Дробное разрешение", null, 500, 500, null, 12.5, 128);  // не целое
            row(cab, "Ноль", null, 0, 500, null, 128, 128);                   // нулевой размер
            Sheet cables = wb.getSheet(LibraryXlsx.SHEET_CABLES);
            row(cables, "что-то", "Кабель без режима");

            LibraryXlsx.Parsed parsed = LibraryXlsx.parse(write(wb));

            assertEquals(1, parsed.cabinets().size());
            assertEquals("Хороший", parsed.cabinets().get(0).name());
            assertTrue(parsed.cables().isEmpty());
            assertEquals(6, parsed.warnings().size(), parsed.warnings().toString());
            assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("строка 6") && w.contains("«широкий»")),
                    parsed.warnings().toString());
        }
    }

    @Test
    void optionalGarbageIsIgnoredWithWarningNotRowSkipped() throws IOException {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "Кабинет", null, 500, 500, "глубокий", 128, 128, "много");
            LibraryXlsx.Parsed parsed = LibraryXlsx.parse(write(wb));

            assertEquals(1, parsed.cabinets().size());
            assertNull(parsed.cabinets().get(0).depthMm());
            assertNull(parsed.cabinets().get(0).powerW());
            assertEquals(2, parsed.warnings().size());
        }
    }

    @Test
    void unknownCategoryBecomesCustomSubcategoryAndModeDefaultsToSignal() throws IOException {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            row(wb.getSheet(LibraryXlsx.SHEET_EQUIPMENT), "Свитч", null, "Сеть");
            LibraryXlsx.Parsed parsed = LibraryXlsx.parse(write(wb));
            LibraryXlsx.Libraries lib = emptyLibraries();
            LibraryXlsx.merge(parsed, lib);

            EquipmentPreset p = lib.equipment().get(0);
            assertEquals(SchemaMode.SIGNAL, p.getMode());
            assertEquals(SchemaNodeType.CUSTOM, p.getCategory());
            assertEquals("Сеть", p.getCustomCategoryLabel());
            assertEquals(1, parsed.warnings().size());
        }
    }

    @Test
    void mergeUpdatesOnlyFilledCellsAndKeepsRestOfExistingRecord() throws IOException {
        CabinetType existing = new CabinetType();
        existing.setName("ROE");
        existing.setCompany("Старая компания");
        existing.setWeightKg(7);
        existing.setRotationDeg(90);
        LibraryXlsx.Libraries lib = emptyLibraries();
        lib.cabinets().add(existing);
        EquipmentPreset preset = new EquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Сервер", "старое");
        preset.setCards(new ArrayList<>(List.of(new com.vjstb.ledscheme.model.SchemaCard())));
        lib.equipment().add(preset);

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "roe", null, 600, 600, null, 160, 160);
            row(wb.getSheet(LibraryXlsx.SHEET_EQUIPMENT), "СЕРВЕР", "Сигнал", "Медиасервер", null, null, null, null,
                    null, null, null, "Отдельно", null, 400, 300, 200);
            LibraryXlsx.Report report = LibraryXlsx.merge(LibraryXlsx.parse(write(wb)), lib);

            assertEquals(1, report.cabinetsUpdated());
            assertEquals(0, report.cabinetsAdded());
            assertEquals(1, lib.cabinets().size());
            assertSame(existing, lib.cabinets().get(0));
            assertEquals(600.0, existing.getWidthMm(), 1e-9);
            assertEquals("Старая компания", existing.getCompany());
            assertEquals(7.0, existing.getWeightKg(), 1e-9);
            assertEquals(90.0, existing.getRotationDeg(), 1e-9);

            assertEquals(1, report.equipmentUpdated());
            assertSame(preset, lib.equipment().get(0));
            assertEquals("старое", preset.getDescription());
            assertEquals(1, preset.getCards().size());
            assertEquals(PhysicalSpec.FormFactor.STANDALONE, preset.getPhysical().getFormFactor());
            assertEquals(400.0, preset.getPhysical().getWidthMm(), 1e-9);
        }
    }

    @Test
    void sharedLibraryRecordsAreNotTouched() throws IOException {
        CabinetType shared = new CabinetType();
        shared.setName("Общий");
        shared.setWidthMm(111);
        LibraryXlsx.Libraries lib = new LibraryXlsx.Libraries(new ArrayList<>(List.of(shared)), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "общий", null, 500, 500, null, 128, 128);
            LibraryXlsx.Report report = LibraryXlsx.merge(LibraryXlsx.parse(write(wb)), lib);

            assertEquals(0, report.total());
            assertTrue(lib.cabinets().isEmpty());
            assertEquals(111.0, shared.getWidthMm(), 1e-9);
            assertEquals(1, report.warnings().size());
        }
    }

    @Test
    void sameNameTwiceInOneFileUpdatesInsteadOfDuplicating() throws IOException {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes()))) {
            Sheet cab = wb.getSheet(LibraryXlsx.SHEET_CABINETS);
            row(cab, "Дубль", null, 500, 500, null, 128, 128);
            row(cab, "дубль", null, 250, 500, null, 128, 128);
            LibraryXlsx.Libraries lib = emptyLibraries();
            LibraryXlsx.Report report = LibraryXlsx.merge(LibraryXlsx.parse(write(wb)), lib);

            assertEquals(1, lib.cabinets().size());
            assertEquals(1, report.cabinetsAdded());
            assertEquals(1, report.cabinetsUpdated());
            assertEquals(250.0, lib.cabinets().get(0).getWidthMm(), 1e-9);
        }
    }

    @Test
    void sheetWithoutRequiredColumnIsSkippedWithWarning() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(LibraryXlsx.SHEET_CABINETS);
            row(s, "Название", "Ширина, мм");
            row(s, "A", 500);
            LibraryXlsx.Parsed parsed = LibraryXlsx.parse(write(wb));

            assertTrue(parsed.cabinets().isEmpty());
            assertEquals(1, parsed.warnings().size());
            assertTrue(parsed.warnings().get(0).contains("Высота"));
        }
    }

    /** Модульные экраны (docs/receiver-cards, раздел 8): шаблон содержит листы карт и блоков питания и
     *  столбец флага модуля, заголовки — ровно те, что описаны в плане и в админке (формат файла один). */
    @Test
    void templateHasReceiverCardAndPowerSupplySheetsAndTheModuleColumn() throws Exception {
        File template = tmp.resolve("template5.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        try (Workbook wb = new XSSFWorkbook(template)) {
            assertEquals(6, wb.getNumberOfSheets(), "инструкция + пять листов данных");
            assertEquals("Приёмная карта отдельно (Да/Нет)",
                    wb.getSheet(LibraryXlsx.SHEET_CABINETS).getRow(0).getCell(12).getStringCellValue());
            Sheet rc = wb.getSheet(LibraryXlsx.SHEET_RECEIVER_CARDS);
            assertEquals("Приёмные карты", rc.getSheetName());
            assertEquals("Выходов на модули (шлейфов)", rc.getRow(0).getCell(3).getStringCellValue());
            Sheet ps = wb.getSheet(LibraryXlsx.SHEET_POWER_SUPPLIES);
            assertEquals("Блоки питания", ps.getSheetName());
            assertEquals("Отдельный выход на карту (Да/Нет)", ps.getRow(0).getCell(6).getStringCellValue());
            assertFalse(ps.getDataValidations().isEmpty());
        }
        LibraryXlsx.Parsed parsed = LibraryXlsx.parse(template);
        assertTrue(parsed.receiverCards().isEmpty() && parsed.powerSupplies().isEmpty());
        assertTrue(parsed.warnings().isEmpty(), parsed.warnings().toString());
    }

    @Test
    void receiverCardsPowerSuppliesAndModuleFlagRoundTripIntoLibrary() throws Exception {
        File template = tmp.resolve("t5.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        File filled;
        try (Workbook wb = new XSSFWorkbook(template)) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "Модуль P2.5", null, 320, 160, null, 128, 64, null, null,
                    null, null, null, "Да");
            row(wb.getSheet(LibraryXlsx.SHEET_RECEIVER_CARDS), "A8s", "NovaStar", "Моя", 8, "600,5", 512, 384, 131072,
                    5, 0.1, 80, 20, 100, "заметка");
            row(wb.getSheet(LibraryXlsx.SHEET_RECEIVER_CARDS), "Пустая карта");
            row(wb.getSheet(LibraryXlsx.SHEET_POWER_SUPPLIES), "БП 8", "Mean Well", null, 8, 300, 700, "да",
                    "110–240 В", 1.2);
            filled = write(wb);
        }

        LibraryXlsx.Parsed parsed = LibraryXlsx.parse(filled);
        assertTrue(parsed.warnings().isEmpty(), parsed.warnings().toString());
        LibraryXlsx.Libraries lib = emptyLibraries();
        LibraryXlsx.Report report = LibraryXlsx.merge(parsed, lib);

        assertEquals(1, report.cabinetsAdded());
        assertEquals(2, report.receiverCardsAdded());
        assertEquals(1, report.powerSuppliesAdded());
        assertEquals(4, report.total());
        assertTrue(report.summary().contains("Приёмные карты: добавлено 2"));
        assertTrue(report.summary().contains("Блоки питания: добавлено 1"));
        assertTrue(lib.cabinets().get(0).isReceiverCardSeparate());

        com.vjstb.ledscheme.model.ReceiverCardType card = lib.receiverCards().get(0);
        assertEquals("A8s", card.getName());
        assertEquals("NovaStar", card.getVendor());
        assertEquals("Моя", card.getCompany());
        assertEquals(8, card.getModuleOutputs());
        assertEquals(600.5, card.getRibbonLengthMm(), 1e-9);
        assertEquals(512, card.getMaxWidthPx());
        assertEquals(384, card.getMaxHeightPx());
        assertEquals(131072, card.getMaxPixels());
        assertEquals(5.0, card.getPowerW(), 1e-9);
        assertEquals(0.1, card.getWeightKg(), 1e-9);
        assertEquals("заметка", card.getNotes());
        assertEquals(80.0, card.getPhysical().getWidthMm(), 1e-9);
        assertEquals(PhysicalSpec.FormFactor.LOOSE, card.getPhysical().getFormFactor());

        com.vjstb.ledscheme.model.ReceiverCardType bare = lib.receiverCards().get(1);
        assertNull(bare.getModuleOutputs(), "пустая ячейка — «не указано», а не 0");
        assertNull(bare.getRibbonLengthMm());
        assertNull(bare.getMaxPixels());
        assertNull(bare.getPhysical());

        com.vjstb.ledscheme.model.PowerSupplyType psu = lib.powerSupplies().get(0);
        assertEquals(8, psu.getOutputs());
        assertEquals(300.0, psu.getRatedPowerW(), 1e-9);
        assertEquals(700.0, psu.getCableLengthMm(), 1e-9);
        assertTrue(psu.isCardOutput());
        assertEquals("110–240 В", psu.getInputVoltage());
        assertEquals(1.2, psu.getWeightKg(), 1e-9);
    }

    @Test
    void cardMergeUpdatesOnlyFilledCellsAndSkipsSharedOnes() throws Exception {
        com.vjstb.ledscheme.model.ReceiverCardType mine = new com.vjstb.ledscheme.model.ReceiverCardType();
        mine.setName("A8s");
        mine.setModuleOutputs(8);
        mine.setMaxPixels(131072);
        mine.setNotes("моя заметка");
        com.vjstb.ledscheme.model.PowerSupplyType sharedPsu = new com.vjstb.ledscheme.model.PowerSupplyType();
        sharedPsu.setName("Общий БП");
        sharedPsu.setOutputs(4);
        LibraryXlsx.Libraries lib = new LibraryXlsx.Libraries(new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(List.of(mine)),
                new ArrayList<>(List.of(sharedPsu)), new ArrayList<>());
        File template = tmp.resolve("t6.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        File filled;
        try (Workbook wb = new XSSFWorkbook(template)) {
            row(wb.getSheet(LibraryXlsx.SHEET_RECEIVER_CARDS), "a8S", null, null, null, 450);
            row(wb.getSheet(LibraryXlsx.SHEET_POWER_SUPPLIES), "общий бп", null, null, 16);
            filled = write(wb);
        }

        LibraryXlsx.Report report = LibraryXlsx.merge(LibraryXlsx.parse(filled), lib);

        assertEquals(1, report.receiverCardsUpdated());
        assertEquals(0, report.receiverCardsAdded());
        assertEquals(0, report.powerSuppliesAdded() + report.powerSuppliesUpdated());
        assertEquals(1, lib.receiverCards().size());
        assertEquals(450.0, mine.getRibbonLengthMm(), 1e-9);
        assertEquals(8, mine.getModuleOutputs(), "пустая ячейка не затирает число выходов");
        assertEquals(131072, mine.getMaxPixels());
        assertEquals("моя заметка", mine.getNotes());
        assertTrue(lib.powerSupplies().isEmpty());
        assertEquals(4, sharedPsu.getOutputs(), "общая запись не меняется");
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("общей библиотеке")));
    }

    @Test
    void badFlagCellsAreReportedAndNotApplied() throws Exception {
        File template = tmp.resolve("t7.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        File filled;
        try (Workbook wb = new XSSFWorkbook(template)) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "Куб", null, 500, 500, null, 128, 128, null, null, null,
                    null, null, "возможно");
            row(wb.getSheet(LibraryXlsx.SHEET_POWER_SUPPLIES), "БП", null, null, 4, null, null, "не знаю");
            filled = write(wb);
        }
        LibraryXlsx.Parsed parsed = LibraryXlsx.parse(filled);
        assertNull(parsed.cabinets().get(0).receiverCardSeparate());
        assertNull(parsed.powerSupplies().get(0).cardOutput());
        assertEquals(2, parsed.warnings().size(), parsed.warnings().toString());
    }

    @Test
    void importThroughAppModelFillsPersonalLibrary(@TempDir Path dir) throws Exception {
        File template = tmp.resolve("t8.xlsx").toFile();
        LibraryXlsx.writeTemplate(template);
        File filled;
        try (Workbook wb = new XSSFWorkbook(template)) {
            row(wb.getSheet(LibraryXlsx.SHEET_CABINETS), "Модуль", null, 320, 160, null, 128, 64, null, null, null,
                    null, null, "Да");
            row(wb.getSheet(LibraryXlsx.SHEET_RECEIVER_CARDS), "A8s", null, null, 8);
            row(wb.getSheet(LibraryXlsx.SHEET_POWER_SUPPLIES), "БП", null, null, 8, 300);
            filled = write(wb);
        }
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(new File(dir.toFile(), "w.json")));

        LibraryXlsx.Report report = model.importLibraryXlsx(filled);

        assertEquals(3, report.total());
        assertEquals(1, model.getReceiverCardTypes().size());
        assertEquals(8, model.getReceiverCardTypes().get(0).getModuleOutputs());
        assertEquals(300.0, model.getPowerSupplyTypes().get(0).getRatedPowerW(), 1e-9);
        assertTrue(model.getCabinetTypes().stream().anyMatch(c -> c.getName().equals("Модуль")
                && c.isReceiverCardSeparate()));
    }

    @Test
    void fileWithoutKnownSheetsOrNotExcelGivesReadableError() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Лист1");
            File other = write(wb);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> LibraryXlsx.parse(other));
            assertTrue(ex.getMessage().contains("Кабинеты"));
        }
        File notExcel = tmp.resolve("x.xlsx").toFile();
        java.nio.file.Files.writeString(notExcel.toPath(), "просто текст");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> LibraryXlsx.parse(notExcel));
        assertTrue(ex.getMessage().startsWith("Не удалось прочитать"));
    }

    @Test
    void headerKeyIgnoresCaseStarUnitsAndYo() {
        assertEquals("ширина", LibraryXlsx.headerKey("  Ширина, мм *"));
        assertEquals("режим", LibraryXlsx.headerKey("Режим (Сигнал/Питание) *"));
        assertEquals("разъем питания", LibraryXlsx.headerKey("Разъём питания"));
        assertEquals("loop-порт", LibraryXlsx.headerKey("Loop-порт (Да/Нет)"));
    }

    @Test
    void numbersAcceptCommaSpacesAndReject() {
        assertEquals(1200.5, LibraryXlsx.parseNumber("1 200,5"));
        assertEquals(3.0, LibraryXlsx.parseNumber("3"));
        assertNull(LibraryXlsx.parseNumber("три"));
        assertNull(LibraryXlsx.parseNumber("NaN"));
    }

    private byte[] templateBytes() throws IOException {
        File t = tmp.resolve("t-" + System.nanoTime() + ".xlsx").toFile();
        LibraryXlsx.writeTemplate(t);
        return java.nio.file.Files.readAllBytes(t.toPath());
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }
}
