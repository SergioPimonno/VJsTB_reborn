package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vjstb.ledscheme.model.PhysicalSpec;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** Поля габаритов позиции: пустое — «не указано» (а не 0), мусор не принимается, поля чужого вида
 *  набора не теряются при сохранении. */
class RoomDimensionsPanelTest {

    private static JTextField field(Container c, int index) {
        int seen = 0;
        for (Component k : c.getComponents()) {
            if (k instanceof JTextField t) {
                if (seen++ == index) {
                    return t;
                }
            }
        }
        throw new AssertionError("нет поля №" + index);
    }

    @Test
    void deviceRackBlockRoundTripsAndKeepsEmptyAsNull() {
        RoomDimensionsPanel panel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.DEVICE);
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.RACK);
        p.setRackUnits(2);
        p.setWidthTwelfths(6);
        p.setDepthMm(372.0);
        panel.load(p);
        PhysicalSpec back = panel.read();
        assertEquals(PhysicalSpec.FormFactor.RACK, back.getFormFactor());
        assertEquals(2, back.getRackUnits());
        assertEquals(6, back.getWidthTwelfths());
        assertEquals(372.0, back.getDepthMm());
        assertNull(back.getWeightKg(), "пусто — «не указано», а не 0");
        assertNull(back.getPowerW());
    }

    @Test
    void looseDeviceTakesThreeSizesAndAcceptsDecimalComma() {
        RoomDimensionsPanel panel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.DEVICE);
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        p.setWidthMm(200.0);
        panel.load(p);
        // поля по порядку: U, ширина, высота, глубина, вес, мощность
        field(panel, 2).setText("30,5");
        field(panel, 3).setText("120");
        PhysicalSpec back = panel.read();
        assertEquals(PhysicalSpec.FormFactor.LOOSE, back.getFormFactor());
        assertEquals(200.0, back.getWidthMm());
        assertEquals(30.5, back.getHeightMm());
        assertEquals(120.0, back.getDepthMm());
    }

    @Test
    void caseModeChangesOnlyUnitsAndInnerDepth() {
        RoomDimensionsPanel panel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.CASE);
        PhysicalSpec p = new PhysicalSpec();
        p.setWeightKg(18.0);
        panel.load(p);
        field(panel, 0).setText("12");
        field(panel, 1).setText("550");
        PhysicalSpec back = panel.read();
        assertEquals(12, back.getRackUnits());
        assertEquals(550.0, back.getDepthMm());
        assertEquals(18.0, back.getWeightKg(), "поля, которых нет в форме кофра, остаются как были");
    }

    @Test
    void garbageAndNegativeNumbersAreRefused() {
        RoomDimensionsPanel panel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.FURNITURE);
        panel.load(null);
        field(panel, 0).setText("широкий");
        assertThrows(IllegalArgumentException.class, panel::read);
        field(panel, 0).setText("-5");
        assertThrows(IllegalArgumentException.class, panel::read);
        field(panel, 0).setText("");
        assertNull(panel.read().getWidthMm());
    }
}
