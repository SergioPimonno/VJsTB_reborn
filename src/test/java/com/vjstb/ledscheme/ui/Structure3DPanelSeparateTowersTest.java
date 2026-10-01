package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Баг-репорт 2026-10-01 «у раздельных башен не рисуются перемычки» — со стороны 3D: кандидаты
 * отрисовки ({@code exists == true}) перемычек и оснований после «Предварительного расчёта»
 * вогнутого экрана пользователя (4×28 кабинетов, R = 10 м, зазор 500, башни авто), который до
 * этого был отредактированной кликами стеной. До правки у башен 0…6 кандидатов {@code exists}
 * не было вовсе (записи скрыты «по наследству» от стены) — рисовались перемычки и основание
 * только крайних башен. Панель строится без GL (headless), как в {@code
 * Structure3DPanelPickingTest}. Корень и исправление — см. {@code
 * service.StructureSeparateTowersCellsTest}.
 */
class Structure3DPanelSeparateTowersTest {

    @Test
    void everySeparateTowerHasDrawnPeremychkiAndBaseAfterSwitchingFromAnEditedWall(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("Dicolor");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setDepthMm(65.0);
        ct.setResolutionWidth(192);
        ct.setResolutionHeight(192);
        CabinetType type = model.addCabinetType(ct);
        StructureFrameType ft = new StructureFrameType();
        ft.setName("Рама стандартная");
        ft.setKind(StructureFrameType.Kind.FRAME);
        ft.setHeightMm(950.0);
        ft.setWidthMm(500.0);
        ft.setDepthMm(51.0);
        String frameId = model.addStructureFrameType(ft).getId();
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("Стена", type.getId(), 4, 28, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);

        model.updateScreenStructure(s, 2000, StructureCalc.suggestTowerCount(s, type), 2, 2, 1, 500, 0.6,
                frameId, null, null, 0, "", ScreenCurveType.FLAT, 10_000, false, 0, 0);
        for (StructurePeremychkaCell c : List.copyOf(s.getStructurePeremychkaCells())) {
            model.toggleStructurePeremychkaCell(s, c.getTowerIndex(), c.getRow(), c.getLevelIndex());
        }
        for (StructureBaseFrameCell c : List.copyOf(s.getStructureBaseFrameCells())) {
            model.toggleStructureBaseFrameSection(s, c.getTowerIndex(), c.getSectionIndex());
        }
        model.updateScreenStructure(s, 2000, StructureCalc.suggestTowerCount(s, type), 2, 2, 1, 500, 0.6,
                frameId, null, null, 0, "", ScreenCurveType.CONCAVE, 10_000, false, 500, 0);

        Structure3DPanel panel = new Structure3DPanel(model, false);
        List<String> drawn = panel.candidateKeys(true);
        Set<String> peremychki = drawn.stream().filter(k -> k.startsWith("peremychka:"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> bases = drawn.stream().filter(k -> k.startsWith("base:"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> expectedP = new TreeSet<>();
        Set<String> expectedB = new TreeSet<>();
        for (int k = 0; k < 9; k++) {
            for (int i = 0; i < 2; i++) {
                expectedP.add("peremychka:" + 2 * k + ":" + i + ":0");
                expectedB.add("base:" + 2 * k + ":" + i + ":0");
            }
        }
        assertEquals(expectedP, peremychki, "перемычки рисуются у всех 9 башен, в обоих рядах");
        assertEquals(expectedB, bases, "основание рисуется у всех 9 башен");
    }
}
