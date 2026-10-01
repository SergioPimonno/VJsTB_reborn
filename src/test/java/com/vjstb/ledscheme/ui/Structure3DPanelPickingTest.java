package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCalc;
import com.vjstb.ledscheme.service.StructurePickMath.Ray;
import com.vjstb.ledscheme.service.StructurePickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Picking 3D-превью конструктива по ПОВЁРНУТЫМ башням изогнутого экрана (запрос пользователя
 * 2026-10-01): клик по раме раздельной башни должен попадать в неё там, где она реально
 * нарисована (луч переводится в локальную систему башни, OBB), а прямой экран с зазором 0
 * выбирается ровно как раньше. Панель строится БЕЗ GL-канваса (пакетный конструктор) — тест
 * идёт и в headless-среде, JOGL здесь не нужен: вся геометрия кандидатов и picking — CPU.
 */
class Structure3DPanelPickingTest {

    private AppModel model;
    private CabinetType type;

    private Screen screen(Path dir, int cols, ScreenCurveType curve, double radius, double gap) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        type = model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", type.getId(), 3, cols, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 2, 1, 1, 500, 0.6,
                null, null, null, 0, null, curve, radius, false, gap, 0);
        return s;
    }

    /** Луч снизу вверх через мировой центр pick-коробки — снизу ничего другого pick'абельного
     *  нет (основание лежит между столбами, не под ними). */
    private static Ray upThrough(double[] center) {
        return new Ray(new Vec3(center[0], -5000, center[2]), new Vec3(0, 1, 0));
    }

    @Test
    void flatWallIsPickedExactlyAsBefore(@TempDir Path dir) {
        screen(dir, 6, ScreenCurveType.FLAT, 10_000, 0);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        double[] c = panel.pickBoxWorldCenter("frame:1:0:0");
        assertNotNull(c);
        // координаты стены: столб 1 на x = 1000, передний ряд от z = −400 до −900, рельс 60 мм
        assertEquals(1000, c[0], 1e-9);
        assertEquals(-870, c[2], 1e-9);
        assertEquals("frame:1:0:0", panel.pickKeyAlongRay(upThrough(c), true));
        List<String> ghosts = panel.candidateKeys(false);
        assertTrue(ghosts.contains("frame:-1:0:0"), "у стены есть призрак лишнего столба сбоку");
        assertTrue(panel.candidateKeys(true).contains("peremychka:1:0:0"), "перемычка в каждом промежутке");
    }

    @Test
    void rotatedFramesOfSeparateTowersArePickedWhereTheyAreDrawn(@TempDir Path dir) {
        Screen s = screen(dir, 12, ScreenCurveType.CONCAVE, 4000, 500);
        int posts = s.getStructureTowerCount();
        assertTrue(posts >= 4 && posts % 2 == 0, "раздельные башни — пары столбов: " + posts);
        Structure3DPanel panel = new Structure3DPanel(model, false);
        for (int post = 0; post < posts; post++) {
            for (int row = 0; row < 2; row++) {
                String key = "frame:" + post + ":" + row + ":0";
                double[] c = panel.pickBoxWorldCenter(key);
                assertNotNull(c, key);
                assertEquals(key, panel.pickKeyAlongRay(upThrough(c), true), "клик по повёрнутой раме " + key);
            }
        }
        // крайняя башня повёрнута: там, где её столб стоял бы у стены, его нет
        double[] edge = panel.pickBoxWorldCenter("frame:0:0:0");
        assertNotEquals(-870, edge[2], 1.0, "крайняя башня вынесена вперёд по дуге вогнутого экрана");
        String atWallSpot = panel.pickKeyAlongRay(upThrough(new double[]{0, 0, -870}), true);
        assertNotEquals("frame:0:0:0", atWallSpot);
    }

    @Test
    void noConnectionsOrSideGhostsBetweenSeparateTowers(@TempDir Path dir) {
        Screen s = screen(dir, 12, ScreenCurveType.CONVEX, 8000, 600);
        int posts = s.getStructureTowerCount();
        Structure3DPanel panel = new Structure3DPanel(model, false);
        for (boolean existing : new boolean[]{true, false}) {
            for (String key : panel.candidateKeys(existing)) {
                String[] p = key.split(":");
                int index = Integer.parseInt(p[1]);
                if (p[0].equals("peremychka") || p[0].equals("base")) {
                    assertEquals(0, Math.floorMod(index, 2), "соединение только внутри башни: " + key);
                }
                if (p[0].equals("frame") || p[0].equals("reinforcement")) {
                    assertTrue(index >= 0 && index < posts, "нет призрака лишнего столба: " + key);
                }
            }
        }
        assertFalse(panel.candidateKeys(true).isEmpty());
    }
}
