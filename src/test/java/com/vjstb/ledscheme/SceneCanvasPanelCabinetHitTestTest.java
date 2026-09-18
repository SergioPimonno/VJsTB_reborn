package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

/** Баг-репорт: "привязки для уменьшенных кабинетов сломались" — после починки
 *  ВИЗУАЛЬНОГО размера переопределённого типа (см. {@code
 *  SceneCanvasPanelOverrideMarksRenderTest}) хит-тест клика ({@link
 *  SceneCanvasPanel#screenAndCabinetAt}) по-прежнему считал ячейку номинального
 *  (старого) размера — клик в опустевшую полосу РЯДОМ с уже уменьшенным кабинетом
 *  продолжал попадать в него, хотя визуально там пусто. */
class SceneCanvasPanelCabinetHitTestTest {

    private AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void hitTestUsesTheEffectiveShrunkSizeNotTheNominalGridStep(@TempDir Path dir) {
        AppModel model = model(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        CabinetType wide = new CabinetType();
        wide.setName("Wide 500x500");
        wide.setWidthMm(500);
        wide.setHeightMm(500);
        model.addCabinetType(wide);

        CabinetType narrow = new CabinetType();
        narrow.setName("MG14 250x500");
        narrow.setWidthMm(250);
        narrow.setHeightMm(500);
        model.addCabinetType(narrow);

        // 1 строка x 2 столбца — col 0 остаётся номинальным типом, col 1
        // переопределён на вдвое ýжий (тот же случай, что в баг-репорте MG14/MG15).
        Screen screen = model.addScreen("E", wide.getId(), 1, 2, 0, 0);
        model.selectScreen(screen);
        CabinetInstance col1 = screen.cabinetAt(0, 1);
        model.setCabinetTypeOverride(col1.getId(), narrow.getId());

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings(dir));
        canvas.setDetailMode(true, false);

        // Та же геометрия, что в SceneCanvasPanelOverrideMarksRenderTest: сетка
        // начинается в (40,40), номинальный шаг колонки — 125px (500мм×0.25),
        // эффективная ширина col 1 (переопределён на 250мм) — round(125×250/500)=63px.
        int gridX = 40, gridY = 40;
        int nominalStep = 125;
        int effectiveW = 63;
        int y = gridY + 60;

        // Внутри РЕАЛЬНОГО (уменьшенного) контура col 1 — должен найтись именно он.
        Object[] insideHit = canvas.screenAndCabinetAtForTest(gridX + nominalStep + effectiveW / 2, y);
        assertEquals(col1.getId(), ((CabinetInstance) insideHit[1]).getId(),
                "клик внутри реального контура уменьшенного кабинета должен попадать в него");

        // За пределами реального контура col 1, но внутри его СТАРОЙ номинальной
        // ширины (до конца шага сетки) — до фикса здесь всё ещё "хватался" col 1,
        // хотя визуально там уже пусто.
        Object[] outsideHit = canvas.screenAndCabinetAtForTest(gridX + nominalStep + effectiveW + 20, y);
        assertNull(outsideHit[1],
                "клик в опустевшую полосу рядом с уменьшенным кабинетом не должен попадать в него");
    }
}
