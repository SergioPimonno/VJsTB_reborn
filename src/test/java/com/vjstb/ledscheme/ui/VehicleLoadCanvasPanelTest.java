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

    @Test
    void addPlacement_stacksOntoSelectedStackOfSameTypeNotTheOneAtOrigin() {
        // Баг-репорт: "если выбран какой-то стек кофров, и нажимается кнопка
        // добавить сюда, то новый кофр добавляется к стопке в верхнем левом углу
        // машины, а не выбранному" -- раньше addPlacement всегда звало
        // addPlacementAt(type, 0, 0), которое штабелировало в ПЕРВЫЙ совпадающий
        // по позиции кофр -- т.е. в стек в углу, даже если выделен был другой.
        CaseType c = caseType(1000, 1000, 1000, 5, 0);
        VehicleType v = vehicleType(5000, 3000, 3000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        // Стек "в углу" -- как если бы менеджер когда-то давно поставил его туда.
        canvas.restorePlacement(c, 0, 0, false, false, 1, "");
        // Другой стек того же типа в другом месте -- тот, что менеджер сейчас
        // реально выделил на канвасе.
        canvas.restorePlacement(c, 2000, 1000, false, false, 1, "");
        VehicleLoadCanvasPanel.Placement corner = canvas.getPlacements().get(0);
        VehicleLoadCanvasPanel.Placement selectedStack = canvas.getPlacements().get(1);
        canvas.select(selectedStack);

        canvas.addPlacement(c);

        assertEquals(1, corner.stackCount, "стек в углу не должен был вырасти");
        assertEquals(2, selectedStack.stackCount, "должен вырасти именно выделенный стек");
        assertEquals(2, canvas.getPlacements().size(), "не должно появиться новое отдельное размещение");
    }

    @Test
    void addPlacement_fallsBackToOriginWhenNoMatchingSelection() {
        // Ничего не выделено (или выделен другой тип) -- прежнее поведение без
        // изменений, addPlacementAt(0,0).
        CaseType c = caseType(1000, 1000, 1000, 5, 0);
        VehicleType v = vehicleType(5000, 3000, 3000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        canvas.addPlacement(c);

        assertEquals(1, canvas.getPlacements().size());
        assertEquals(0.0, canvas.getPlacements().get(0).xMm);
        assertEquals(0.0, canvas.getPlacements().get(0).yMm);
    }

    @Test
    void addPlacement_fallsBackToOriginWhenSelectedStackIsAtMaxCapacity() {
        CaseType c = caseType(1000, 1000, 1000, 1, 0); // maxStackCount=1
        VehicleType v = vehicleType(5000, 3000, 3000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);
        canvas.restorePlacement(c, 2000, 1000, false, false, 1, "");
        VehicleLoadCanvasPanel.Placement full = canvas.getPlacements().get(0);
        canvas.select(full);

        canvas.addPlacement(c);

        assertEquals(1, full.stackCount, "полный стек не может расти дальше maxStackCount");
        assertEquals(2, canvas.getPlacements().size(), "новый кофр должен встать отдельно");
    }

    @Test
    void clearAll_removesEveryPlacementAndSelection() {
        CaseType c = caseType(1000, 1000, 1000, 5, 0);
        VehicleType v = vehicleType(5000, 3000, 3000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);
        canvas.restorePlacement(c, 0, 0, false, false, 2, "");
        canvas.restorePlacement(c, 2000, 1000, false, false, 1, "");
        canvas.select(canvas.getPlacements().get(0));

        canvas.clearAll();

        assertTrue(canvas.getPlacements().isEmpty());
        // Машина остаётся выбранной -- clearAll не должен требовать заново
        // выбирать машину в комбобоксе.
        assertEquals(v, canvas.getVehicle());
    }

    @Test
    void autoPlaceAll_verticalStandsCaseOnEndWithoutStacking() {
        // Запрос: "опция для автозаполнения... для заполнения машины кофрами в
        // вертикальной ориентации" -- длинный (1800мм) невысокий (400мм) кофр
        // стоя занимает footprint 500x400 (ширина×высота), а не 1800x500
        // (длина×ширина) лёжа -- намного меньше площади пола. Длина (1800мм)
        // помещается под потолок кузова (2000мм), поэтому все 4 штуки должны
        // встать "на попа", БЕЗ штабелирования (maxStack=3, но vertical его
        // игнорирует -- по одной штуке на клетку).
        CaseType c = caseType(1800, 500, 400, 3, 0);
        VehicleType v = vehicleType(5000, 3000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(c, 4), true);

        assertTrue(leftover.isEmpty());
        assertEquals(4, canvas.getPlacements().size(), "вертикально -- без штабелирования, по клетке на штуку");
        for (VehicleLoadCanvasPanel.Placement p : canvas.getPlacements()) {
            assertTrue(p.vertical, "должен быть отмечен как стоящий вертикально");
            assertEquals(1, p.stackCount);
            assertEquals(500, p.footprintWMm(), 0.001);
            assertEquals(400, p.footprintHMm(), 0.001);
        }
    }

    @Test
    void autoPlaceAll_verticalFallsBackToLyingWhenTooTallToStand() {
        // Тип, чья длина НЕ помещается под потолок кузова стоя -- вертикальный
        // режим для НЕГО тихо откатывается на обычное лежачее положение (со
        // штабелированием), а не проваливается в leftover только из-за того, что
        // "вертикально" не подошло.
        CaseType tooTallStanding = caseType(2500, 500, 400, 2, 0); // длина 2500 > высота кузова 2000
        VehicleType v = vehicleType(5000, 3000, 2000);
        VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        canvas.setVehicle(v);

        Map<CaseType, Integer> leftover = canvas.autoPlaceAll(Map.of(tooTallStanding, 2), true);

        assertTrue(leftover.isEmpty());
        assertEquals(1, canvas.getPlacements().size(), "не помещается стоя -> лёжа со штабелированием -> 1 стопка");
        VehicleLoadCanvasPanel.Placement p = canvas.getPlacements().get(0);
        assertTrue(!p.vertical, "не должен быть отмечен вертикальным -- физически стоя не влезает");
        assertEquals(2, p.stackCount);
    }

    @Test
    void autoPlaceAll_verticalFitsMoreThanLyingOnLimitedFloorArea() {
        // Практическая проверка выгоды опции: на узком по площади, но высоком
        // кузове вертикальная раскладка вмещает БОЛЬШЕ штук, чем лежачая (с
        // тем же ограниченным бюджетом пола).
        CaseType c = caseType(1800, 500, 400, 1, 0); // maxStack=1 -- штабелирование не помогает лежачему варианту
        VehicleType v = vehicleType(2000, 500, 2000); // пол ровно под ОДНУ лежачую штуку (1800x500 + 0 зазора)
        int need = 3;

        VehicleLoadCanvasPanel lying = new VehicleLoadCanvasPanel();
        lying.setVehicle(v);
        Map<CaseType, Integer> leftoverLying = lying.autoPlaceAll(Map.of(c, need), false);

        VehicleLoadCanvasPanel vertical = new VehicleLoadCanvasPanel();
        vertical.setVehicle(v);
        Map<CaseType, Integer> leftoverVertical = vertical.autoPlaceAll(Map.of(c, need), true);

        int placedLying = need - leftoverLying.getOrDefault(c, 0);
        int placedVertical = need - leftoverVertical.getOrDefault(c, 0);
        assertTrue(placedVertical > placedLying,
                "вертикально (footprint 500x400) должно поместиться больше штук на том же полу, чем лёжа (1800x500): "
                        + placedVertical + " vs " + placedLying);
    }
}
