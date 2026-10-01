package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.FloorPlanViewMode;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.settings.UserProfile;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Переключатель «2D схема» / «3D редактор» окна плана пола ({@link FloorPlanViewPanel}) —
 * запрос пользователя 2026-10-01. Проверяется без окон и без GL (обычные {@code JPanel}, 3D —
 * с {@code tryGl=false}, т.е. той же заглушкой, что на системе без JOGL): по умолчанию 2D,
 * выбор запоминается в профиле и переживает перезапуск, правка рамы в 3D сразу видна в 2D-плане
 * (оранжевый кабинет) и в строке итогов, а старый профиль без поля открывается в 2D.
 */
class FloorPlanViewPanelTest {

    private AppModel model;
    private Screen screen;

    private void floor(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType t = new CabinetType();
        t.setName("500");
        t.setWidthMm(500);
        t.setHeightMm(500);
        t.setResolutionWidth(128);
        t.setResolutionHeight(128);
        model.addCabinetType(t);
        screen = model.addScreen("Пол", t.getId(), 6, 14, 0, 0, ScreenMountType.FLOOR);
        model.selectScreen(screen);
    }

    private static SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    @Test
    void defaultsTo2dAndRemembersChoiceInProfileAcrossRestart(@TempDir Path dir) {
        floor(dir);
        SettingsManager settings = settings(dir);
        FloorPlanViewPanel view = new FloorPlanViewPanel(model, screen, settings, false);
        assertEquals(FloorPlanViewMode.PLAN_2D, view.getMode(), "по умолчанию — 2D, как было");
        assertEquals(FloorPlanViewPanel.CARD_2D, view.visibleCard());
        assertNull(view.view3d(), "в 2D-режиме 3D-вид (и GL) не создаётся вовсе");

        view.setMode(FloorPlanViewMode.EDITOR_3D, true);
        assertEquals(FloorPlanViewPanel.CARD_3D, view.visibleCard());
        assertNotNull(view.view3d());
        assertFalse(view.view3d().isGlAvailable(), "без GL — текстовая заглушка, не падение");
        assertEquals(FloorPlanViewMode.EDITOR_3D, settings.activeProfile().getFloorPlanViewMode());

        SettingsManager reloaded = settings(dir);
        assertEquals(FloorPlanViewMode.EDITOR_3D, reloaded.activeProfile().getFloorPlanViewMode(),
                "выбор записан на диск");
        FloorPlanViewPanel reopened = new FloorPlanViewPanel(model, screen, reloaded, false);
        assertEquals(FloorPlanViewMode.EDITOR_3D, reopened.getMode(), "окно открывается в последнем режиме");

        reopened.setMode(FloorPlanViewMode.PLAN_2D, true);
        assertEquals(FloorPlanViewMode.PLAN_2D, settings(dir).activeProfile().getFloorPlanViewMode());
    }

    @Test
    void frameEditIn3dShowsUpIn2dPlanAndTotals(@TempDir Path dir) {
        floor(dir);
        FloorPlanViewPanel view = new FloorPlanViewPanel(model, screen, null, false);
        assertTrue(view.totalsLabelText().contains("Рам: 42"), view.totalsLabelText());

        assertTrue(model.toggleFloorFrameCell(screen, 0, 2));
        view.refresh();

        assertEquals(41, view.plan2d().getResult().frameCount());
        assertTrue(view.totalsLabelText().contains("Рам: 41"), view.totalsLabelText());
        assertTrue(view.totalsLabelText().contains("Без опоры: 2"), view.totalsLabelText());

        int w = 900;
        int h = 520;
        BufferedImage img = view.plan2d().renderImage(w, h);
        Rectangle orphan = view.plan2d().cabinetRect(0, 2, w, h);
        assertEquals(FloorPlanPanel.UNSUPPORTED_FILL.getRGB() & 0xFFFFFF,
                img.getRGB((int) orphan.getCenterX(), (int) orphan.getCenterY()) & 0xFFFFFF,
                "кабинет убранной рамы на 2D-плане — оранжевый");
    }

    @Test
    void floor3dPanelWithoutGlIsAStubAndDoesNotThrow(@TempDir Path dir) {
        floor(dir);
        FloorPlan3DPanel panel = new FloorPlan3DPanel(model, screen, false);
        assertFalse(panel.isGlAvailable());
        panel.refresh();
        panel.setShowCabinets(false);
        panel.setViewAngle(0, 120);
        assertFalse(panel.isShowCabinets());
    }

    @Test
    void oldProfileWithoutFieldOpensIn2d() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        UserProfile old = mapper.readValue("{\"name\":\"Старый\"}", UserProfile.class);
        assertEquals(FloorPlanViewMode.PLAN_2D, old.getFloorPlanViewMode());
        old.setFloorPlanViewMode(FloorPlanViewMode.EDITOR_3D);
        assertEquals(FloorPlanViewMode.EDITOR_3D, old.copy().getFloorPlanViewMode(), "copy() переносит режим");
        UserProfile back = mapper.readValue(mapper.writeValueAsString(old), UserProfile.class);
        assertEquals(FloorPlanViewMode.EDITOR_3D, back.getFloorPlanViewMode());
    }
}
