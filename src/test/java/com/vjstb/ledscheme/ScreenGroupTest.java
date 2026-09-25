package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenDefaults;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.ScreenTagColor;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Группы экранов в дереве навигации «Сетапа» (запрос 2026-09-24): объединение
 * выделенных экранов в группу, цвет группы как метка экранов, дублирование и
 * вставка экранов со стандартными именами и параметрами по умолчанию сцены.
 * Главный инвариант, который здесь проверяется, — члены группы всегда идут в
 * {@code Scene#getScreens()} подряд, чтобы порядок строк дерева совпадал с
 * порядком экранов (сквозная нумерация портов).
 */
class ScreenGroupTest {

    private AppModel model;
    private Scene scene;
    private CabinetType type;
    private Screen a, b, c, d;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("Test P3 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setPowerConsumptionW(150);
        ct.setWeightKg(12);
        type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        scene = model.addScene("S");
        model.selectScene(scene);
        a = model.addScreen("Экран 1", type.getId(), 2, 3, 0, 0);
        b = model.addScreen("Экран 2", type.getId(), 2, 3, 2000, 0);
        c = model.addScreen("Экран 3", type.getId(), 2, 3, 4000, 0);
        d = model.addScreen("Экран 4", type.getId(), 2, 3, 6000, 0);
    }

    @Test
    void groupingNonAdjacentScreensGathersThemAtTheFirstMembersPosition() {
        ScreenGroup g = model.groupScreens(scene, List.of(a, c), "Левый борт");

        assertEquals(List.of(a, c, b, d), scene.getScreens());
        assertEquals(g.getId(), a.getGroupId());
        assertEquals(g.getId(), c.getGroupId());
        assertNull(b.getGroupId());
        assertEquals(List.of(g), scene.getScreenGroups());
    }

    @Test
    void ungroupingAMiddleMemberMovesItOutOfTheGroupsBlock() {
        ScreenGroup g = model.groupScreens(scene, List.of(a, b, c), "G");

        model.ungroupScreens(scene, List.of(b));

        assertEquals(List.of(a, c, b, d), scene.getScreens(), "группа остаётся сплошным блоком");
        assertNull(b.getGroupId());
        assertEquals(List.of(a, c), scene.screensOf(g));
    }

    @Test
    void groupThatLosesAllMembersDisappears() {
        model.groupScreens(scene, List.of(a, b), "G");

        model.ungroupScreens(scene, List.of(a, b));

        assertTrue(scene.getScreenGroups().isEmpty());
        assertNull(a.getGroupId());
    }

    @Test
    void deletingTheLastScreenOfAGroupRemovesTheGroup() {
        model.groupScreens(scene, List.of(a), "G");

        model.deleteScreens(List.of(a));

        assertTrue(scene.getScreenGroups().isEmpty());
        assertEquals(List.of(b, c, d), scene.getScreens());
    }

    @Test
    void groupColorTagsAllMembersAndNewMembersInheritIt() {
        ScreenGroup g = model.groupScreens(scene, List.of(a, b), "G");

        model.setGroupColor(scene, g, ScreenTagColor.BLUE);
        model.addScreensToGroup(scene, List.of(d), g);

        assertEquals(ScreenTagColor.BLUE, a.getTagColor());
        assertEquals(ScreenTagColor.BLUE, b.getTagColor());
        assertEquals(ScreenTagColor.BLUE, d.getTagColor(), "добавленный экран получает цвет группы");
        assertEquals(ScreenTagColor.NONE, c.getTagColor());
        assertEquals(List.of(a, b, d, c), scene.getScreens(), "добавленный экран встаёт в конец группы");
    }

    @Test
    void ungroupedScreensKeepTheirColorTag() {
        ScreenGroup g = model.groupScreens(scene, List.of(a, b), "G");
        model.setGroupColor(scene, g, ScreenTagColor.RED);

        model.dissolveGroups(scene, List.of(g));

        assertEquals(ScreenTagColor.RED, a.getTagColor());
        assertTrue(scene.getScreenGroups().isEmpty());
    }

    @Test
    void duplicateCreatesFreshScreensInTheSameGroupWithStandardNames() {
        model.groupScreens(scene, List.of(b, c), "G");
        ScreenGroup g = scene.groupOf(b);

        List<Screen> created = model.duplicateScreens(scene, List.of(b, c));

        assertEquals(2, created.size());
        assertEquals(List.of("Экран 5", "Экран 6"), created.stream().map(Screen::getName).toList());
        assertEquals(g.getId(), created.get(0).getGroupId());
        assertEquals(List.of(a, b, c, created.get(0), created.get(1), d), scene.getScreens());
        assertEquals(b.getCols(), created.get(0).getCols());
        assertEquals(b.getRows(), created.get(0).getRows());
    }

    @Test
    void duplicateOfUngroupedScreenStaysUngrouped() {
        Screen copy = model.duplicateScreens(scene, List.of(a)).get(0);

        assertNull(copy.getGroupId());
        assertEquals("Экран 5", copy.getName());
        assertEquals(copy, scene.getScreens().get(scene.getScreens().size() - 1));
    }

    @Test
    void pasteAppliesSceneDefaultsAndSkipsTakenNames() {
        ScreenDefaults defaults = new ScreenDefaults();
        defaults.setRefreshRateHz(120);
        defaults.setMountType(ScreenMountType.STRUCTURE);
        scene.setScreenDefaults(defaults);
        ScreenGroup g = model.groupScreens(scene, List.of(a, b), "G");
        // занимаем «Экран 5» — следующим стандартным должно стать «Экран 6»
        c.setName("Экран 5");

        List<Screen> created = model.pasteScreens(scene, List.of(AppModel.ScreenTemplate.of(a)), g);

        Screen s = created.get(0);
        assertEquals("Экран 6", s.getName());
        assertEquals(120, s.getRefreshRateHz(), "параметры по умолчанию сцены применяются");
        assertEquals(ScreenMountType.STRUCTURE, s.getMountType());
        assertEquals(g.getId(), s.getGroupId());
        assertEquals(List.of(a, b, s, c, d), scene.getScreens());
    }

    @Test
    void movingScreensBeforeAnotherOneAndIntoAGroupKeepsBlocksContiguous() {
        ScreenGroup g = model.groupScreens(scene, List.of(b, c), "G");

        model.moveScreens(scene, List.of(d), b, g, true);

        assertEquals(List.of(a, d, b, c), scene.getScreens());
        assertEquals(g.getId(), d.getGroupId());

        model.moveScreens(scene, List.of(d), null, null, true);

        assertEquals(List.of(a, b, c, d), scene.getScreens());
        assertNull(d.getGroupId());
    }

    @Test
    void draggingAWholeGroupKeepsItsMembership() {
        ScreenGroup g = model.groupScreens(scene, List.of(c, d), "G");

        model.moveScreens(scene, List.of(c, d), a, null, false);

        assertEquals(List.of(c, d, a, b), scene.getScreens());
        assertEquals(g.getId(), c.getGroupId());
        assertEquals(g.getId(), d.getGroupId());
    }

    @Test
    void undoRestoresDeletedScreensWithTheirGroupAndOrderEvenAfterSelectionChange() {
        ScreenGroup g = model.groupScreens(scene, List.of(b, c), "G");
        model.selectScreen(b);

        model.deleteScreens(List.of(b, c));
        assertEquals(List.of(a, d), scene.getScreens());
        assertTrue(scene.getScreenGroups().isEmpty());
        model.selectScreen(a); // смена выбора не должна сбрасывать отмену удаления

        model.undo();

        assertEquals(List.of(a, b, c, d), scene.getScreens());
        assertEquals(1, scene.getScreenGroups().size());
        assertEquals(g.getId(), scene.getScreenGroups().get(0).getId());
        assertEquals(g.getId(), b.getGroupId());
    }

    @Test
    void undoRemovesDuplicatedAndPastedScreens() {
        Screen copy = model.duplicateScreens(scene, List.of(a)).get(0);
        model.selectScreen(copy);
        assertEquals(5, scene.getScreens().size());

        model.undo();
        assertEquals(List.of(a, b, c, d), scene.getScreens());

        model.pasteScreens(scene, List.of(AppModel.ScreenTemplate.of(a)), null);
        assertEquals(5, scene.getScreens().size());
        model.undo();
        assertEquals(List.of(a, b, c, d), scene.getScreens());
    }

    @Test
    void undoOfGroupingRestoresOrderAndMembership() {
        model.groupScreens(scene, List.of(a, c), "G");
        assertEquals(List.of(a, c, b, d), scene.getScreens());

        model.undo();

        assertEquals(List.of(a, b, c, d), scene.getScreens());
        assertNull(a.getGroupId());
        assertTrue(scene.getScreenGroups().isEmpty());
    }
}
