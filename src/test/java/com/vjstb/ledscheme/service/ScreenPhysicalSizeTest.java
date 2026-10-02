package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-02: размер экрана в таблице экспорта, подписях и отчёте — по ФАКТИЧЕСКИ
 *  стоящим (видимым) кабинетам, а не «колонки × кабинет по умолчанию» (Left 2: 7 м по
 *  настройкам, 6,5 м реально). Разрешение и маски остаются по номинальной сетке. */
class ScreenPhysicalSizeTest {

    private static CabinetType type(String name, double w, double h, int rw, int rh) {
        CabinetType ct = new CabinetType();
        ct.setName(name);
        ct.setWidthMm(w);
        ct.setHeightMm(h);
        ct.setResolutionWidth(rw);
        ct.setResolutionHeight(rh);
        ct.setWeightKg(10);
        return ct;
    }

    @Test
    void physicalSizeFollowsVisibleCabinetsAndKeepsResolutionNominal(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType base = model.addCabinetType(type("MG7s", 500, 500, 128, 128));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("Left 2", base.getId(), 3, 14, 0, 0);

        assertEquals(7000, ScreenLogic.physicalSizeMm(s, base, model.getWorkspace())[0], 1e-6);

        // убираем последнюю колонку целиком: видимые кабинеты занимают 6,5 м
        for (CabinetInstance c : s.getCabinets()) {
            if (c.getColIndex() == 13) {
                c.setHidden(true);
            }
        }
        double[] size = ScreenLogic.physicalSizeMm(s, base, model.getWorkspace());
        assertEquals(6500, size[0], 1e-6);
        assertEquals(1500, size[1], 1e-6);

        // разрешение (холст маски) — по номинальной сетке 14 × 128
        assertEquals(14 * 128, ScreenLogic.stats(s, base, model.getWorkspace()).resolutionWidthPx());

        // таблица экспорта показывает фактический размер
        var img = SchemeRenderer.renderScreensOverviewImage("Зал", model, List.of(s), 1.0);
        org.junit.jupiter.api.Assertions.assertTrue(img.getWidth() > 0);
    }

    @Test
    void noVisibleCabinetsFallsBackToTheNominalGrid(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType base = model.addCabinetType(type("MG7s", 500, 500, 128, 128));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", base.getId(), 2, 4, 0, 0);
        s.getCabinets().forEach(c -> c.setHidden(true));

        double[] size = ScreenLogic.physicalSizeMm(s, base, model.getWorkspace());
        assertEquals(2000, size[0], 1e-6);
        assertEquals(1000, size[1], 1e-6);
    }
}
