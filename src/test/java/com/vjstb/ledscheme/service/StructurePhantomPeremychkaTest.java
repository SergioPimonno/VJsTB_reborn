package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Отзыв 2026-10-25 («239 рам, проверь по скрину»): в проекте «Точка А» у экрана Center сохранились перемычки на
 * уровнях 22 и 45 — номера перекладин 8-й и 16-й рамы при стопке из 4 (следствие двойной миграции старого формата
 * «уровней» в номера перекладин). В 3D их не было видно (они «висели» на 7 и 15 м над башней), а в спецификацию они
 * попадали: 16 лишних рам. Теперь перемычка без опоры (нет рамы с таким номером в обоих столбах зазора) не
 * считается и не рисуется.
 */
class StructurePhantomPeremychkaTest {

    private AppModel model;
    private Screen screen;

    private void setup(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        CabinetType type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        screen = model.addScreen("E", type.getId(), 9, 12, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(screen);
        screen.setStructureTowerHeightMm(4500);
        model.updateScreenStructure(screen, 4500, 7, 4, 2, 1, 500, 0.6, null, null, null, 0, null,
                ScreenCurveType.FLAT, 10_000, false, 600, 0, 1500);
    }

    private int perem() {
        return StructureCalc.compute(screen, model.typeOf(screen), model.getWorkspace()).peremychkaCount();
    }

    @Test
    void peremychkiOnRungsOfNonexistentFramesAreNotCounted(@TempDir Path dir) {
        setup(dir);
        int honest = perem();
        assertTrue(honest > 0);
        // как в файле: «уровни» 22 и 45 (8-я и 16-я рама) при четырёх рамах в стопке, плюс задний ряд
        for (int t = 0; t < 6; t++) {
            screen.getStructurePeremychkaCells().add(new StructurePeremychkaCell(t, 0, 22));
            screen.getStructurePeremychkaCells().add(new StructurePeremychkaCell(t, 0, 45));
            screen.getStructurePeremychkaCells().add(new StructurePeremychkaCell(t, 1, 22));
        }
        assertEquals(honest, perem(), "перемычки без опоры в закупку не идут");
    }

    @Test
    void supportRuleNeedsAFrameInBothPostsOfTheGap(@TempDir Path dir) {
        setup(dir);
        var supported = StructureCalc.peremychkaSupport(screen);
        assertTrue(supported.test(new StructurePeremychkaCell(0, 0, 4)), "рама 1 есть в обоих столбах");
        assertFalse(supported.test(new StructurePeremychkaCell(0, 0, 22)), "рамы 7 нет");
        assertFalse(supported.test(new StructurePeremychkaCell(0, 1, 9)), "заднего ряда выше второй рамы нет");
        assertFalse(supported.test(new StructurePeremychkaCell(6, 0, 4)), "справа от последнего столба зазора нет");
        // спрятали раму в соседнем столбе — перемычке не за что держаться
        screen.getStructureFrameCells().stream().filter(c -> c.matches(1, 0, 1)).forEach(c -> c.setHidden(true));
        assertFalse(StructureCalc.peremychkaSupport(screen).test(new StructurePeremychkaCell(0, 0, 4)));
    }

    @Test
    void reAddingAHiddenFrameTakesTheTypeChosenInTheSelector(@TempDir Path dir) {
        setup(dir);
        var cell = screen.getStructureFrameCells().stream().filter(c -> c.matches(2, 0, 1)).findFirst().get();
        assertEquals(null, cell.getFrameTypeId());

        model.toggleStructureFrameCell(screen, 2, 0, 1); // спрятали обычную
        assertTrue(cell.isHidden());
        model.toggleStructureFrameCell(screen, 2, 0, 1, "short-id"); // поставили короткую
        assertFalse(cell.isHidden());
        assertEquals("short-id", cell.getFrameTypeId(), "запись уже была — тип всё равно заменён на выбранный");

        model.toggleStructureFrameCell(screen, 2, 0, 1); // спрятали
        model.toggleStructureFrameCell(screen, 2, 0, 1, null); // «как обычно» тип не меняет
        assertEquals("short-id", cell.getFrameTypeId());
    }
}
