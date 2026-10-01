package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Книга спецификации одной сцены (запрос пользователя 2026-09-30, решения D1/D6, трек
 * C3): {@link OutputStagePanel#buildEquipmentSpecWorkbook(Project, Scene)} считает все
 * листы только по этой сцене, в «Инфо» есть строка «Сцена», оборудование и коммутация
 * разнесены по колонкам-схемам БЕЗ колонки «Итого». Агрегация проверена отдельно в
 * {@code SceneSpecCalcTest}; здесь — раскладка по листам.
 */
class OutputStageSceneSpecWorkbookTest {

    private static List<String> headers(Sheet sheet) {
        List<String> out = new ArrayList<>();
        Row row = sheet.getRow(0);
        for (int i = 0; i < row.getLastCellNum(); i++) {
            out.add(row.getCell(i).getStringCellValue());
        }
        return out;
    }

    private static String cellText(Sheet sheet, int rowIndex, int col) {
        Row row = sheet.getRow(rowIndex);
        if (row == null || row.getCell(col) == null) {
            return null;
        }
        return switch (row.getCell(col).getCellType()) {
            case NUMERIC -> String.valueOf((long) row.getCell(col).getNumericCellValue());
            case BLANK -> null;
            default -> row.getCell(col).getStringCellValue();
        };
    }

    private static int rowOf(Sheet sheet, int col, String text) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            if (text.equals(cellText(sheet, r, col))) {
                return r;
            }
        }
        return -1;
    }

    @Test
    void sceneWorkbookHasSceneRowSchemeColumnsAndNoTotalAndIgnoresOtherScenes(@TempDir Path dir) throws Exception {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        Project project = model.addProject("Проект");
        model.selectProject(project);
        CabinetType ct = new CabinetType();
        ct.setName("P3");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        model.addCabinetType(ct);

        Scene hall = model.addScene("Зал");
        model.selectScene(hall);
        model.addScreen("Экран зала", ct.getId(), 2, 2, 0, 0);
        SchemaSheet signal1 = model.schemaSheets(SchemaMode.SIGNAL).get(0);
        SchemaSheet signal2 = model.addSchemaSheet(SchemaMode.SIGNAL, "Резерв");
        model.selectSchemaSheet(signal1);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL", 0, 0, null);
        model.selectSchemaSheet(signal2);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL", 0, 0, null);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Медиасервер резерва", 0, 0, null);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, "B", 0, 0, null);
        SchemaEdge e = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        model.updateSchemaEdgeWire(e, 2, "Cat6", 10.0);

        Scene foyer = model.addScene("Фойе");
        model.selectScene(foyer);
        model.addScreen("Экран фойе", ct.getId(), 1, 1, 0, 0);
        model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "Чужой контроллер", 0, 0, null);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        OutputStagePanel panel = new OutputStagePanel(model, settings);

        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, hall)) {
            Sheet info = wb.getSheet("Инфо");
            assertEquals("Сцена", info.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Зал", info.getRow(2).getCell(1).getStringCellValue());

            Sheet equipment = wb.getSheet("Оборудование");
            assertEquals(List.of("Схема", "Тип узла", "Подпись", "Схема питания", "Схема сигнала", "Резерв"),
                    headers(equipment), "колонка на каждую схему сцены: питание, затем сигнал; «Итого» нет");
            int mctrl = rowOf(equipment, 2, "MCTRL");
            assertTrue(mctrl > 0);
            assertNull(cellText(equipment, mctrl, 3));
            assertEquals("1", cellText(equipment, mctrl, 4), "MCTRL есть в первой схеме сигнала");
            assertEquals("1", cellText(equipment, mctrl, 5), "и во второй — в той же строке, в другой колонке");
            assertEquals(-1, rowOf(equipment, 2, "Чужой контроллер"), "узлы другой сцены не попадают");

            Sheet cabinets = wb.getSheet("Кабинеты");
            assertEquals("4", cellText(cabinets, 1, 1), "в зале 2x2 кабинета, экран фойе не примешан");

            Sheet purchase = wb.getSheet("Коммутация — сводная");
            assertEquals(List.of("Схема", "Тип провода", "Длина куска, м", "Схема питания", "Схема сигнала", "Резерв",
                    "Примечание"), headers(purchase));
            int cat6 = rowOf(purchase, 1, "Cat6");
            assertTrue(cat6 > 0);
            assertEquals("2", cellText(purchase, cat6, 5), "кабель второй схемы — только в её колонке");
            assertNull(cellText(purchase, cat6, 4));

            Sheet overall = wb.getSheet("Общий список");
            assertNotNull(overall);
            assertEquals(1, wb.getSheetIndex("Общий список"), "по-прежнему сразу после «Инфо»");
            assertEquals(List.of("Категория", "Наименование", "Кол-во", "Ед.", "Схема питания", "Схема сигнала",
                    "Резерв"), headers(overall));
            assertFalse(headers(overall).contains("Итого"));
        }

        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, foyer)) {
            Sheet equipment = wb.getSheet("Оборудование");
            assertEquals(List.of("Схема", "Тип узла", "Подпись", "Схема питания", "Схема сигнала"),
                    headers(equipment), "в фойе схем по одной на режим — колонок две");
            assertTrue(rowOf(equipment, 2, "Чужой контроллер") > 0);
            assertEquals(-1, rowOf(equipment, 2, "MCTRL"));
            assertEquals("1", cellText(wb.getSheet("Кабинеты"), 1, 1));
        }
    }

    @Test
    void sceneWithoutScreensAndSchemesStillGetsAWorkbookWithAllSheets(@TempDir Path dir) throws Exception {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene empty = model.addScene("Пустая");
        model.selectScene(empty);
        OutputStagePanel panel = new OutputStagePanel(model,
                new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json"))));

        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, empty)) {
            for (String name : List.of("Инфо", "Кабинеты", "Оборудование", "Конструктив", "Фермы", "Общий список",
                    "Коммутация — сводная", "Коммутация — сплайсовка")) {
                assertNotNull(wb.getSheet(name), "лист «" + name + "» есть, хоть и пустой");
            }
            assertEquals(0, wb.getSheet("Кабинеты").getLastRowNum());
        }
    }
}
