package com.vjstb.ledscheme.ui.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Дымовой тест полей изогнутого экрана в блоке «Наземный конструктив» (запрос пользователя
 * 2026-10-01): панель строится БЕЗ показа окна, поля изгиба неактивны у прямого экрана,
 * смена способа ввода пересчитывает радиус в угол между кабинетами, подсказка показывает
 * производные величины и предупреждение о коллизиях, а сохранённые в экране значения
 * подхватываются формой. Как и прочие GUI-тесты проекта, в headless-среде пропускается.
 */
class SetupStageCurveFieldsTest {

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        Field f = SetupStagePanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void curveFieldsFollowShapeConvertRadiusToAngleAndShowHints(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType ct = new CabinetType();
        ct.setName("T 500x500");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        model.addCabinetType(ct);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("Экран", ct.getId(), 3, 12, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SetupStagePanel[] ref = new SetupStagePanel[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = new SetupStagePanel(model, settings));
        flushEdt();
        SetupStagePanel panel = ref[0];
        JComboBox<ScreenCurveType> type = field(panel, "pStructureCurveType");
        JComboBox<String> mode = field(panel, "pStructureCurveMode");
        JSpinner value = field(panel, "pStructureCurveValue");
        JSpinner gap = field(panel, "pStructureTowerGap");
        JSpinner towers = field(panel, "pStructureSeparateTowers");

        String[] hint = new String[1];
        SwingUtilities.invokeAndWait(() -> hint[0] = panel.structureCurveHintText());
        assertEquals(ScreenCurveType.FLAT, type.getSelectedItem(), "старый/новый экран — прямой");
        assertFalse(value.isEnabled(), "у прямого экрана поля изгиба неактивны");
        assertFalse(towers.isEnabled(), "прямой без зазора — стена, число башен не задаётся");
        assertTrue(hint[0].contains("стеной"), hint[0]);

        SwingUtilities.invokeAndWait(() -> {
            type.setSelectedItem(ScreenCurveType.CONCAVE);
            value.setValue(10_000.0);
        });
        assertTrue(value.isEnabled());
        assertTrue(towers.isEnabled());
        SwingUtilities.invokeAndWait(() -> hint[0] = panel.structureCurveHintText());
        assertTrue(hint[0].contains("угол между кабинетами") && hint[0].contains("стрела прогиба")
                && hint[0].contains("Башен:"), hint[0]);

        SwingUtilities.invokeAndWait(() -> mode.setSelectedIndex(1));
        double angle = ((Number) value.getValue()).doubleValue();
        assertEquals(StructureCurveMath.cabinetAngleDeg(500, 10_000), angle, 0.01, "радиус 10 м → угол между кабинетами");

        // выпуклый, маленький радиус, глубокая база — подсказка предупреждает о пересечении
        SwingUtilities.invokeAndWait(() -> {
            mode.setSelectedIndex(0);
            type.setSelectedItem(ScreenCurveType.CONVEX);
            value.setValue(4000.0);
            gap.setValue(500.0);
            ((JSpinner) fieldUnchecked(panel, "pStructureBaseExtension")).setValue(2000.0);
            hint[0] = panel.structureCurveHintText();
        });
        assertTrue(hint[0].contains("пересекаются"), hint[0]);

        // сохранённое в экране подхватывается формой при ребилде
        SwingUtilities.invokeAndWait(() -> model.updateScreenStructure(s, 1500, 0, 3, 2, 1, 500, 0.6, null, null,
                null, 0, null, ScreenCurveType.CONVEX, StructureCurveMath.radiusFromCabinetAngle(500, 4), true,
                700, 3));
        flushEdt();
        assertEquals(ScreenCurveType.CONVEX, type.getSelectedItem());
        assertEquals(1, mode.getSelectedIndex(), "задано углом — форма показывает угол");
        assertEquals(4.0, ((Number) value.getValue()).doubleValue(), 0.01);
        assertEquals(700.0, ((Number) gap.getValue()).doubleValue(), 1e-9);
        assertEquals(3, ((Number) towers.getValue()).intValue());
        assertEquals(6, s.getStructureTowerCount(), "3 башни = 6 столбов");
    }

    private static Object fieldUnchecked(Object o, String name) {
        try {
            return field(o, name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
