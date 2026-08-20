package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.VehicleType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Тесты автоматической раскладки "Разместить всё" — см. class-javadoc
 *  {@link VehicleLoadCanvasPanel#autoPlaceAll} и VEHICLE_CALC_NOTES.md. */
class VehicleLoadCanvasPanelTest {

    private CaseType caseType(double lengthMm, double widthMm, double heightMm, int maxStack, double clearanceMm) {
        CaseType c = new CaseType();
        c.setName("Кофр");
        c.setLengthMm(lengthMm);
        c.setWidthMm(widthMm);
        c.setHeightMm(heightMm);
        c.setMaxStackCount(maxStack);
        c.setClearanceMm(clearanceMm);
        return c;
    }

    private VehicleType vehicleType(double cargoLengthMm, double cargoWidthMm, double cargoHeightMm) {
        VehicleType v = new VehicleType();
        v.setName("Машина");
        v.setCargoLengthMm(cargoLengthMm);
        v.setCargoWidthMm(cargoWidthMm);
        v.setCargoHeightMm(cargoHeightMm);
        v.setPayloadKg(10_000);
        return v;
    }

    private int sumStack(VehicleLoadCanvasPanel canvas) {
        return canvas.getPlacements().stream().mapToInt(p -> p.stackCount).sum();
    }

    @Test
    void autoPlaceAll_fillsEverythingWhenSpaceSufficient() {
        CaseType c = caseType(1000, 1000, 1000, 1, 0);
        VehicleType v = vehicleType(4000, 2000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(c, 5));

        assertTrue(leftover.isEmpty());
        assertEquals(5, sumStack(canvas));
        assertEquals(5, canvas.getPlacements().size()); // maxStack=1 -> одна позиция на кофр
    }

    @Test
    void autoPlaceAll_appliesStackingBeforePacking() {
        // maxStack=3, кузов достаточно высокий для 3 уровней -> 9 кофров = 3 "стопки"
        // (ceil(9/3)=3) => ровно 3 позиции на полу, не 9.
        CaseType c = caseType(1000, 1000, 1000, 3, 0);
        VehicleType v = vehicleType(5000, 2000, 3200);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(c, 9));

        assertTrue(leftover.isEmpty());
        assertEquals(9, sumStack(canvas));
        assertEquals(3, canvas.getPlacements().size());
        for (VehicleLoadCanvasPanel.Placement p : canvas.getPlacements()) {
            assertEquals(3, p.stackCount);
        }
    }

    @Test
    void autoPlaceAll_reportsLeftoverWhenFloorSpaceInsufficient() {
        // Пол вмещает только 2 кофра 1x1м (2x1м доступно), просят 5, без штабелирования.
        CaseType c = caseType(1000, 1000, 1000, 1, 0);
        VehicleType v = vehicleType(2000, 1000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(c, 5));

        assertEquals(2, sumStack(canvas));
        assertEquals(3, leftover.getOrDefault(c, 0));
    }

    @Test
    void autoPlaceAll_reportsFullLeftoverWhenTallerThanCargo() {
        CaseType tall = caseType(1000, 1000, 2000, 1, 0); // кофр 2м высотой
        VehicleType v = vehicleType(4000, 2000, 1500); // кузов 1.5м высотой
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(tall, 3));

        assertEquals(0, sumStack(canvas));
        assertTrue(canvas.getPlacements().isEmpty());
        assertEquals(3, leftover.get(tall));
    }

    @Test
    void autoPlaceAll_rotatesWhenOnlySidewaysOrientationFits() {
        // Длина 3000мм не влезает в кузов длиной 2000мм, но влезает по ширине кузова (2000),
        // а ширина кофра (500) укладывается в длину кузова -> должен развернуться на 90°.
        CaseType c = caseType(3000, 500, 1000, 1, 0);
        VehicleType v = vehicleType(2000, 3000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(c, 1));

        assertTrue(leftover.isEmpty());
        assertEquals(1, canvas.getPlacements().size());
        assertTrue(canvas.getPlacements().get(0).rotated);
    }

    @Test
    void autoPlaceAll_replacesExistingPlacementsRatherThanAppending() {
        CaseType original = caseType(1000, 1000, 1000, 1, 0);
        VehicleType v = vehicleType(4000, 2000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);
        canvas.addPlacementAt(original, 0, 0);
        assertEquals(1, canvas.getPlacements().size());

        CaseType replacement = caseType(500, 500, 500, 1, 0);
        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(replacement, 2));

        assertTrue(leftover.isEmpty());
        assertEquals(2, canvas.getPlacements().size());
        assertTrue(canvas.getPlacements().stream().allMatch(p -> p.type == replacement));
    }

    @Test
    void autoPlaceAll_placementsStayWithinCargoBounds() {
        CaseType c = caseType(700, 400, 300, 2, 50);
        VehicleType v = vehicleType(3000, 1800, 1000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        canvas.autoPlaceAll(Map.of(c, 20));

        for (VehicleLoadCanvasPanel.Placement p : canvas.getPlacements()) {
            double w = p.rotated ? c.getWidthMm() : c.getLengthMm();
            double h = p.rotated ? c.getLengthMm() : c.getWidthMm();
            assertTrue(p.xMm >= 0 && p.xMm + w <= v.getCargoLengthMm() + 1e-6);
            assertTrue(p.yMm >= 0 && p.yMm + h <= v.getCargoWidthMm() + 1e-6);
        }
    }

    @Test
    void autoPlaceAll_emptyVehicleReturnsEverythingAsLeftover() {
        CaseType c = caseType(1000, 1000, 1000, 1, 0);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel(); // vehicle никогда не выбран

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(new LinkedHashMap<>(Map.of(c, 4)));

        assertEquals(4, leftover.get(c));
        assertTrue(canvas.getPlacements().isEmpty());
    }
}
