package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
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
 * Напольный каркас в XLSX-спецификации сцены (запрос 2026-10-01): экран {@code FLOOR} даёт
 * строку на отдельном листе «Напольный каркас» и позиции в «Общем списке» — рамы (по
 * названию из библиотеки), стаканы, болты, ножки, зубы. Проверяется и то, что пол не
 * попадает в башенный «Конструктив» (не дублируется), и правка пользователя того же дня:
 * число СТЫКОВ — вспомогательная информация калькулятора, в спецификации его нет ни на одном
 * листе.
 */
class OutputStageFloorSpecTest {

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == CellType.NUMERIC ? String.valueOf((long) cell.getNumericCellValue())
                : cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
    }

    private static int rowOf(Sheet sheet, int col, String value) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && value.equals(text(row.getCell(col)))) {
                return r;
            }
        }
        return -1;
    }

    @Test
    void floorScreenGetsOwnSheetAndOverallRowsWithoutJointsAndWithoutTouchingTowers(@TempDir Path dir)
            throws Exception {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        Project project = model.addProject("Проект");
        model.selectProject(project);
        Scene scene = model.addScene("Зал");
        model.selectScene(scene);
        CabinetType ct = new CabinetType();
        ct.setName("P3.9 пол");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        model.addCabinetType(ct);
        StructureFrameType frame = new StructureFrameType();
        frame.setName("Рама 950");
        frame.setKind(StructureFrameType.Kind.FRAME);
        frame.setHeightMm(950.0);
        frame.setWidthMm(500.0);
        model.addStructureFrameType(frame);
        StructureFrameType cup = new StructureFrameType();
        cup.setName("Стакан 50");
        cup.setKind(StructureFrameType.Kind.CUP);
        model.addStructureFrameType(cup);

        Screen floor = model.addScreen("Пол", ct.getId(), 6, 14, 0, 0, ScreenMountType.FLOOR);
        model.selectScreen(floor);
        model.updateScreenFloor(floor, frame.getId(), cup.getId(), 4);
        model.addScreen("Башня", ct.getId(), 2, 2, 8000, 0, ScreenMountType.STRUCTURE);

        OutputStagePanel panel = new OutputStagePanel(model,
                new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json"))));
        try (Workbook wb = panel.buildEquipmentSpecWorkbook(project, scene)) {
            Sheet floorSheet = wb.getSheet("Напольный каркас");
            assertNotNull(floorSheet);
            int row = rowOf(floorSheet, 1, "Пол");
            assertTrue(row > 0);
            assertEquals("Рама 950", text(floorSheet.getRow(row).getCell(2)));
            assertEquals("42", text(floorSheet.getRow(row).getCell(3)));
            assertEquals("Стакан 50", text(floorSheet.getRow(row).getCell(4)));
            assertEquals("72", text(floorSheet.getRow(row).getCell(5)));
            assertEquals("142", text(floorSheet.getRow(row).getCell(6)));
            assertEquals("168", text(floorSheet.getRow(row).getCell(7)));
            assertEquals("336", text(floorSheet.getRow(row).getCell(8)));
            assertEquals(-1, rowOf(floorSheet, 1, "Башня"), "башенный экран не попадает на лист пола");

            assertEquals(-1, rowOf(wb.getSheet("Конструктив"), 1, "Пол"), "пол не дублируется в «Конструктиве»");

            Sheet overall = wb.getSheet("Общий список");
            for (String[] expected : List.of(
                    new String[]{"Рамы: Рама 950", "42"},
                    new String[]{"Стаканы: Стакан 50", "72"},
                    new String[]{"Болты", "142"},
                    new String[]{"Ножки", "168"},
                    new String[]{"Зубы", "336"})) {
                int r = rowOf(overall, 1, expected[0]);
                assertTrue(r > 0, "в «Общем списке» есть «" + expected[0] + "»");
                assertEquals("Напольный каркас", text(overall.getRow(r).getCell(0)));
                assertEquals(expected[1], text(overall.getRow(r).getCell(2)));
            }

            // Правка пользователя 2026-10-01: стыки каркаса в спецификацию не выводятся нигде.
            // Лист «Фермы» исключён: его столбец «Стыков, шт» — стыки сегментов фермы подвеса
            // (RIGGED), существующее поведение, к полу не относится и в этой правке не менялось.
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if ("Фермы".equals(sheet.getSheetName())) {
                    continue;
                }
                for (Row r : sheet) {
                    for (Cell c : r) {
                        String v = text(c);
                        assertFalse(v != null && v.toLowerCase().contains("стык"),
                                "лист «" + sheet.getSheetName() + "»: «" + v + "»");
                    }
                }
            }
        }
    }
}
