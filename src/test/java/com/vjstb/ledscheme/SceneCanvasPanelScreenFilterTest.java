package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-05: в файле «_Все экраны сцены» (экспорт питания/сигнала) экраны без цепочек не
 *  рисуются. Панель получает набор id экранов ({@code setScreenFilter}): габариты сцены (а значит и
 *  размер картинки) считаются только по ним, чтобы на месте убранных экранов не оставалось пустоты. */
class SceneCanvasPanelScreenFilterTest {

    @Test
    void filteredOutScreensDoNotTakeSpaceInTheOverview(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType t = new CabinetType();
        t.setName("T 500x500");
        t.setWidthMm(500);
        t.setHeightMm(500);
        model.addCabinetType(t);
        Screen near = model.addScreen("Рядом", t.getId(), 2, 2, 0, 0);
        Screen far = model.addScreen("Далеко", t.getId(), 2, 2, 20000, 0);
        model.selectScreen(near);

        SceneCanvasPanel all = new SceneCanvasPanel(model, settings);
        all.setDetailMode(true, true);
        Dimension full = all.getPreferredSize();

        SceneCanvasPanel filtered = new SceneCanvasPanel(model, settings);
        filtered.setScreenFilter(Set.of(near.getId()));
        filtered.setDetailMode(true, true);
        Dimension only = filtered.getPreferredSize();

        assertTrue(only.width < full.width, "экран без цепочек не должен растягивать картинку: "
                + only.width + " vs " + full.width);
        // оба экрана одинакового размера, так что вдвоём они заметно шире одного
        assertTrue(full.width > only.width * 3);

        filtered.setScreenFilter(Set.of(near.getId(), far.getId()));
        assertEquals(full, filtered.getPreferredSize(), "полный набор экранов = как без фильтра");
    }
}
