package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.FloorCalc;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 2D-план напольного каркаса ({@link FloorPlanPanel}, запрос 2026-10-01) должен рисоваться в
 * картинку без показа окон — значит, работать и в headless-прогоне {@code mvn test}. Главное,
 * что проверяется по пикселям: кабинет БЕЗ опоры закрашен оранжевым (прямое требование
 * пользователя), опёртый — нет; плюс текст сводки «Рассчитать пол» содержит итоговые числа.
 */
class FloorPlanPanelTest {

    private static Screen screen13x2(Path dir, AppModel[] out) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType t = new CabinetType();
        t.setName("500");
        t.setWidthMm(500);
        t.setHeightMm(500);
        t.setResolutionWidth(128);
        t.setResolutionHeight(128);
        model.addCabinetType(t);
        out[0] = model;
        return model.addScreen("Пол", t.getId(), 2, 13, 0, 0, ScreenMountType.FLOOR);
    }

    @Test
    void rendersHeadlessAndPaintsUnsupportedCabinetOrange(@TempDir Path dir) {
        AppModel[] holder = new AppModel[1];
        Screen s = screen13x2(dir, holder);
        AppModel model = holder[0];
        s.cabinetAt(0, 5).setHidden(true);
        CabinetType type = model.typeOf(s);
        FloorCalc.Result r = FloorCalc.compute(s, type, model.getWorkspace());
        assertTrue(r.isUnsupported(1, 12));

        FloorPlanPanel panel = new FloorPlanPanel(s, type, r);
        int w = 900;
        int h = 500;
        BufferedImage img = panel.renderImage(w, h);
        assertEquals(w, img.getWidth());

        Rectangle unsupported = panel.cabinetRect(1, 12, w, h);
        int orange = FloorPlanPanel.UNSUPPORTED_FILL.getRGB() & 0xFFFFFF;
        assertEquals(orange, img.getRGB((int) unsupported.getCenterX(), (int) unsupported.getCenterY()) & 0xFFFFFF,
                "кабинет без опоры — оранжевый");

        Rectangle supported = panel.cabinetRect(1, 0, w, h);
        assertNotEquals(orange, img.getRGB((int) supported.getCenterX(), (int) supported.getCenterY()) & 0xFFFFFF);

        // Скрытый кабинет — не кабинет: ни оранжевым, ни серым не заливается.
        Rectangle hidden = panel.cabinetRect(0, 5, w, h);
        assertEquals(0xFFFFFF, img.getRGB((int) hidden.getCenterX(), (int) hidden.getCenterY()) & 0xFFFFFF);
    }

    @Test
    void summaryTextListsTotalsJointsAndLoad(@TempDir Path dir) {
        AppModel[] holder = new AppModel[1];
        Screen s = screen13x2(dir, holder);
        AppModel model = holder[0];
        FloorCalc.Result r = FloorCalc.compute(s, model.typeOf(s), model.getWorkspace());

        String text = FloorPlanDialog.summaryText(s.getName(), r);

        assertTrue(text.contains("Рам: 12"), text);
        assertTrue(text.contains("Стыков по короткой стороне (со стаканами): 10"), text);
        assertTrue(text.contains("Стыков по длинной стороне (вплотную): 6"), text);
        assertTrue(text.contains("Ножек: 48"), text);
        assertTrue(text.contains("Кабинетов без опоры: 2"), text);
        assertTrue(text.contains("кг/м²"), text);
    }
}
