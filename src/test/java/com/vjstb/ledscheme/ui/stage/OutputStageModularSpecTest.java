package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ModularSpecCalc;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Спецификация сцены с МОДУЛЬНЫМ экраном (docs/receiver-cards/PLAN.md, раздел 7, трек E): приёмные карты и блоки
 * питания по моделям, сигнальные шлейфы по числу модулей (с длиной, если указана у типа карты) и кабели питания по
 * занятым выходам блоков (с отдельным выходом/шлейфом под карту). Немодульная сцена — лист «Оборудование» без
 * лишнего столбца (охраняют и старые тесты спецификации).
 */
class OutputStageModularSpecTest {

    private AppModel model;
    private Project project;
    private Scene scene;
    private ReceiverCardType cardType;
    private PowerSupplyType psuType;

    private void setUp(Path dir, boolean modular) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        project = model.addProject("П");
        model.selectProject(project);
        scene = model.addScene("С");
        model.selectScene(scene);
        CabinetType module = new CabinetType();
        module.setName("Модуль");
        module.setWidthMm(250);
        module.setHeightMm(250);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setReceiverCardSeparate(modular);
        model.addCabinetType(module);
        cardType = new ReceiverCardType();
        cardType.setName("Карта А");
        cardType.setModuleOutputs(16);
        cardType.setRibbonLengthMm(600.0);
        model.addReceiverCardType(cardType);
        psuType = new PowerSupplyType();
        psuType.setName("Блок Б");
        psuType.setOutputs(8);
        psuType.setCardOutput(true);
        psuType.setRatedPowerW(300.0);
        psuType.setCableLengthMm(700.0);
        model.addPowerSupplyType(psuType);
        Screen screen = model.addScreen("Э", module.getId(), 2, 4, 0, 0);
        model.selectScreen(screen);
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == CellType.NUMERIC ? String.valueOf((long) cell.getNumericCellValue())
                : cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
    }

    private static Row rowStarting(Sheet sheet, String category, String labelPart) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && category.equals(text(row.getCell(1))) && text(row.getCell(2)) != null
                    && text(row.getCell(2)).contains(labelPart)) {
                return row;
            }
        }
        return null;
    }

    @Test
    void rowsCountCardsSuppliesRibbonsAndCables(@TempDir Path dir) {
        setUp(dir, true);
        // Две карты по 4 модуля; один блок питает все 8 модулей и обе карты: одна карта на отдельном выходе «К»,
        // вторая забирает шлейф блока.
        model.assignReceiverCardsManual(new GridRect(0, 0, 1, 1), cardType.getId());
        model.assignReceiverCardsManual(new GridRect(0, 2, 1, 3), cardType.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), psuType.getId());

        List<ModularSpecCalc.Row> rows = ModularSpecCalc.rows(scene, model.getWorkspace());

        assertEquals(new ModularSpecCalc.Row("Сигнал", ModularSpecCalc.CARDS, "Карта А", 2), rows.get(0));
        assertEquals(new ModularSpecCalc.Row("Питание", ModularSpecCalc.SUPPLIES, "Блок Б", 1), rows.get(1));
        assertEquals(new ModularSpecCalc.Row("Сигнал", ModularSpecCalc.RIBBONS,
                "для карты «Карта А», 600 мм", 8), rows.get(2), "шлейф на каждый модуль, длина — в наименовании");
        assertEquals(new ModularSpecCalc.Row("Питание", ModularSpecCalc.CABLES, "от блока «Блок Б», 700 мм", 10),
                rows.get(3), "8 модулей + 2 карты: «К» и принудительный шлейф");
    }

    @Test
    void ribbonLabelHasNoLengthWhenTypeDoesNotSayIt(@TempDir Path dir) {
        setUp(dir, true);
        cardType.setRibbonLengthMm(null);
        psuType.setCableLengthMm(null);
        model.assignReceiverCardsManual(new GridRect(0, 0, 1, 3), cardType.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), psuType.getId());

        List<ModularSpecCalc.Row> rows = ModularSpecCalc.rows(scene, model.getWorkspace());

        assertEquals("для карты «Карта А»", rows.get(2).label());
        assertEquals("от блока «Блок Б»", rows.get(3).label());
    }

    @Test
    void nonModularSceneGivesNothing(@TempDir Path dir) {
        setUp(dir, false);
        assertTrue(ModularSpecCalc.rows(scene, model.getWorkspace()).isEmpty());
        assertFalse(ModularSpecCalc.hasModular(scene, model.getWorkspace()));
    }

    @Test
    void workbookEquipmentSheetGetsScreensColumnOnlyForModularScene(@TempDir Path dir) throws Exception {
        setUp(dir, true);
        model.assignReceiverCardsManual(new GridRect(0, 0, 1, 3), cardType.getId());
        model.assignPowerSuppliesManual(new GridRect(0, 0, 1, 3), psuType.getId());
        OutputStagePanel panel = new OutputStagePanel(model,
                new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json"))));

        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, scene)) {
            Sheet eq = wb.getSheet("Оборудование");
            int last = eq.getRow(0).getLastCellNum() - 1;
            assertEquals("Экраны сцены", eq.getRow(0).getCell(last).getStringCellValue());
            Row card = rowStarting(eq, ModularSpecCalc.CARDS, "Карта А");
            assertNotNull(card);
            assertEquals(1.0, card.getCell(last).getNumericCellValue());
            Row ribbons = rowStarting(eq, ModularSpecCalc.RIBBONS, "600 мм");
            assertEquals(8.0, ribbons.getCell(last).getNumericCellValue());
            Row cables = rowStarting(eq, ModularSpecCalc.CABLES, "700 мм");
            assertEquals(9.0, cables.getCell(last).getNumericCellValue(), "8 модулей + карта на выходе «К»");

            Sheet overall = wb.getSheet("Общий список");
            boolean found = false;
            for (int r = 1; r <= overall.getLastRowNum(); r++) {
                Row row = overall.getRow(r);
                if (row != null && ModularSpecCalc.SUPPLIES.equals(text(row.getCell(0)))
                        && "Блок Б".equals(text(row.getCell(1)))) {
                    assertEquals("1", text(row.getCell(2)));
                    assertEquals("шт", text(row.getCell(3)));
                    found = true;
                }
            }
            assertTrue(found, "блок питания в «Общем списке» с количеством");
        }
    }

    @Test
    void workbookOfNonModularSceneHasNoScreensColumn(@TempDir Path dir) throws Exception {
        setUp(dir, false);
        OutputStagePanel panel = new OutputStagePanel(model,
                new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json"))));

        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, scene)) {
            Row header = wb.getSheet("Оборудование").getRow(0);
            assertNotEquals("Экраны сцены", header.getCell(header.getLastCellNum() - 1).getStringCellValue(),
                    "столбец «Экраны сцены» появляется только у сцены с модульным экраном");
            assertEquals(3 + model.schemaSheets(scene, com.vjstb.ledscheme.model.SchemaMode.POWER).size()
                    + model.schemaSheets(scene, com.vjstb.ledscheme.model.SchemaMode.SIGNAL).size(),
                    header.getLastCellNum(), "Схема, Тип узла, Подпись + по столбцу на схему, как раньше");
            assertNull(rowStarting(wb.getSheet("Общий список"), ModularSpecCalc.CARDS, ""));
        }
    }
}
