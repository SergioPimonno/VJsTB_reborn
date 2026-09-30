package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-09-30 (скриншот: экран "Rauss Right", 2×29 кабинетов): рамка экрана в
 *  «Прериге сцены» (detailMode) была заметно уже реально нарисованной сетки кабинетов у
 *  широких экранов — крайние кабинеты оказывались правее рамки, и клик по ним не находил
 *  ни экрана, ни кабинета (см. {@code SceneCanvasPanel#screenAndCabinetAt} — грубая
 *  отбраковка "попал ли курсор в экран" шла именно по этой рамке).
 *
 * <p>Причина — разное округление ширины: сетка кладёт каждый кабинет на {@code col *
 *  round(мм * sc)} (округление НА кабинет, накапливается с числом колонок), а рамка (до
 *  фикса) округляла ширину ОДИН РАЗ для всего пролёта — {@code round(cols * мм * sc)}. При
 *  {@code sc = 0.25} (масштаб detailMode по умолчанию) и ширине кабинета 111 мм: {@code
 *  round(111×0.25) = 28} px/кабинет, то есть 29 кабинетов рисуются на {@code 29×28 = 812}
 *  px, а старая рамка считала {@code round(29×111×0.25) = round(804.75) = 805} px — на 7 px
 *  уже, чем реальная сетка. */
class SceneCanvasPanelWideScreenFrameTest {

    private static AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void frameAndHitTestCoverTheFullWidthOfAManyColumnScreen(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        // 111 мм подобраны так, чтобы округление НА кабинет (28 px) заметно разошлось с
        // округлением ОДНИМ РАЗОМ на весь пролёт (round(29×111×0.25) = 805, а не 812) —
        // без этого разрыв в 1 px было бы легко списать на погрешность округления.
        CabinetType t = new CabinetType();
        t.setName("T 111x200");
        t.setWidthMm(111);
        t.setHeightMm(200);
        model.addCabinetType(t);

        Screen screen = model.addScreen("Rauss Right", t.getId(), 1, 29, 0, 0);
        model.selectScreen(screen);

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);

        // Геометрия при sc=0.25 (detailMode по умолчанию, detailZoom=1): рамка начинается
        // в (40,40) — см. SceneCanvasPanelCabinetHitTestTest; cellW=round(111×0.25)=28,
        // последний кабинет (colIndex=28) реально нарисован на [40+28×28; 40+29×28] =
        // [824; 852] по X — этот диапазон и должен находиться хит-тестом.
        int gridX = 40, gridY = 40;
        int cellW = 28;
        int lastCabLeft = gridX + 28 * cellW;
        int lastCabRight = gridX + 29 * cellW;
        int y = gridY + 25;

        // Внутри реального контура последнего кабинета, но ПРАВЕЕ старой (заниженной)
        // рамки (gridX + 805 = 845) — до фикса здесь не находилось вообще ничего.
        int px = lastCabRight - 4;
        assertTrue(px > gridX + 805, "тестовая точка должна лежать за старой (багованной) рамкой");
        assertTrue(px < lastCabRight, "и при этом внутри реально нарисованного последнего кабинета");

        Object[] hit = canvas.screenAndCabinetAtForTest(px, y);
        assertNotNull(hit, "клик по крайнему кабинету широкого экрана должен попадать хотя бы на сам экран");
        assertEquals(screen, hit[0]);
        assertNotNull(hit[1], "и находить конкретный (последний) кабинет, а не промахиваться мимо сетки");
        assertEquals(screen.cabinetAt(0, 28).getId(), ((CabinetInstance) hit[1]).getId());

        // На всякий случай — и левый край последнего кабинета тоже находится (не только
        // «широкая» зона у самой рамки).
        assertNotNull(canvas.screenAndCabinetAtForTest(lastCabLeft + 2, y)[1]);
    }
}
