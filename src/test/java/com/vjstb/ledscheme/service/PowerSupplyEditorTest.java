package com.vjstb.ledscheme.service;

import static com.vjstb.ledscheme.service.ModularFixture.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Назначение блоков питания (D17–D23). Сердце — автоподбор размера блока: прямоугольник не больше числа шлейфов,
 * все модули в пределах кабеля от места блока, карте в области хватает выхода (отдельного «К» или принудительного
 * шлейфа); из прошедших — меньше всего блоков, при равенстве крупнее и ближе к квадрату; не прошёл никто — 1×1.
 * Ожидаемые размеры в тестах выведены вручную из этого правила, а не подогнаны под результат (расчёт — в javadoc
 * каждого теста). Модуль 250 мм: соседи — 250, диагональ — 353,6.
 */
class PowerSupplyEditorTest {

    private static List<Integer> sizes(ModularFixture f) {
        List<Integer> out = new ArrayList<>();
        for (PowerSupplyInstance s : f.screen.getPowerSupplies()) {
            out.add(s.getModuleIds().size());
        }
        out.sort(null);
        return out;
    }

    /** 6×8 модулей, 8 шлейфов, кабель не указан: кандидаты площади 8 — 8×1 и 4×2 дают по 6 блоков, 2×4 — 8;
     *  из 6 блоков выбираем ближе к квадрату — 4×2. */
    @Test
    void autoPicksTheFewestBlocksThenLargerThenCloserToSquare() {
        ModularFixture f = new ModularFixture(6, 8);
        String t = f.psuType("БП8", 8, null, false).getId();

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 5, 7), t);

        assertEquals(4, r.autoWidth());
        assertEquals(2, r.autoHeight());
        assertTrue(r.autoOk());
        assertEquals(6, f.screen.getPowerSupplies().size());
        assertEquals(List.of(8, 8, 8, 8, 8, 8), sizes(f));
        assertEquals("Подобрано: 4×2 мод. на блок, блоков: 6", r.autoInfo());
        assertEquals(1, f.screen.getPowerAutoAreas().size());
        assertEquals(rect(0, 0, 5, 7), f.screen.getPowerAutoAreas().get(0).getRect());
    }

    /** 3 строки × 5 столбцов: 2×3 даёт 3 блока (6+6+3), 5×1 — тоже 3, но меньше площадью → 2×3.
     *  Неполный блок у правого края допустим. */
    @Test
    void incompleteBlocksAtTheEdgeAreAllowed() {
        ModularFixture f = new ModularFixture(3, 5);
        String t = f.psuType("БП8", 8, null, false).getId();

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 2, 4), t);

        assertEquals(2, r.autoWidth());
        assertEquals(3, r.autoHeight());
        assertEquals(List.of(3, 6, 6), sizes(f));
    }

    /** 4×4 модуля, кабель 400 мм (1,6 шага): 4×2 не проходит (дальний модуль — 559 мм), 3×2 и 2×3 проходят (353 мм).
     *  У обоих 4 блока; при равенстве побеждает первый перебранный — 2×3. Без ограничения кабеля блоков было бы 2. */
    @Test
    void cableLengthLimitsHowLargeABlockCanBe() {
        ModularFixture f = new ModularFixture(4, 4);
        PowerSupplyType t = f.psuType("БП8", 8, 400.0, false);

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 3), t.getId());

        assertEquals(2, r.autoWidth());
        assertEquals(3, r.autoHeight());
        assertEquals(4, f.screen.getPowerSupplies().size());
        assertTrue(PowerSupplyChecks.check(f.screen, f.ws, ReceiverCardNumbering.DEFAULT).ok());

        // без ограничения — два блока по 8 модулей; 2×4 и 4×2 равны по числу блоков, площади и квадратности,
        // поэтому остаётся первый перебранный — более узкий 2×4
        t.setCableLengthMm(null);
        EditReport free = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 3), t.getId());
        assertEquals(2, free.autoWidth());
        assertEquals(4, free.autoHeight());
        assertEquals(2, f.screen.getPowerSupplies().size());
        assertEquals(List.of(8, 8), sizes(f));
    }

    /** Ограничение на число шлейфов: 5 шлейфов — блок не больше 5 модулей; 2×2 (4 модуля) и 5×1 допустимы, 3×2 нет. */
    @Test
    void blockNeverHasMoreModulesThanOutputs() {
        ModularFixture f = new ModularFixture(4, 6);
        String t = f.psuType("БП5", 5, null, false).getId();

        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 5), t);

        for (PowerSupplyInstance s : f.screen.getPowerSupplies()) {
            assertTrue(s.getModuleIds().size() <= 5, "блок на " + s.getModuleIds().size() + " модулей");
        }
        assertTrue(PowerSupplyChecks.check(f.screen, f.ws, ReceiverCardNumbering.DEFAULT).problems().isEmpty());
    }

    /** Карта 2×4 в области 2 строки × 4 столбца. Без «К» карта забирает шлейф: блок 4×2 дал бы 9 из 8 → проходит
     *  3×2 (карта + 6 = 7): 2 блока (6+2). С «К» карта не занимает шлейф — единственный блок 4×2. */
    @Test
    void dedicatedCardOutputLetsTheWholeAreaBeOneBlock() {
        ModularFixture f = new ModularFixture(2, 4);
        String card = f.cardType("К", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 3), card);
        PowerSupplyType t = f.psuType("БП8", 8, null, false);

        EditReport forced = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());
        assertEquals(3, forced.autoWidth());
        assertEquals(2, forced.autoHeight());
        assertEquals(List.of(2, 6), sizes(f));
        PowerSupplyLayout.Supply withCard = PowerSupplyLayout.compute(f.screen, f.ws, ReceiverCardNumbering.DEFAULT)
                .ofCard(f.cardAt(0, 0).getId());
        assertNotNull(withCard, "карта питается от блока, в области которого её место");
        assertFalse(withCard.cards().get(0).dedicated(), "без «К» — принудительный шлейф №1");
        assertEquals(1, withCard.cards().get(0).output());

        t.setCardOutput(true);
        EditReport ded = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());
        assertEquals(4, ded.autoWidth());
        assertEquals(2, ded.autoHeight());
        assertEquals(1, f.screen.getPowerSupplies().size());
        PowerSupplyLayout.Supply only = PowerSupplyLayout.compute(f.screen, f.ws, ReceiverCardNumbering.DEFAULT)
                .supplies().get(0);
        assertTrue(only.hasDedicatedCard());
        assertEquals("К", only.cards().get(0).label());
        assertEquals(8, only.used());
        assertEquals(0, only.over());
    }

    /** Если ни одно разбиение не проходит проверки, берётся 1×1 и отчёт говорит об этом: 1 шлейф, а в области карта,
     *  которой нужен ещё один. */
    @Test
    void whenNothingPassesTheFallbackIsOneByOneAndTheReportSaysSo() {
        ModularFixture f = new ModularFixture(1, 3);
        String card = f.cardType("К", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 2), card);
        String t = f.psuType("БП1", 1, null, false).getId();

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 0, 2), t);

        assertEquals(1, r.autoWidth());
        assertEquals(1, r.autoHeight());
        assertFalse(r.autoOk());
        assertEquals(3, f.screen.getPowerSupplies().size());
        assertTrue(r.autoInfo().endsWith("— проверки не прошли даже для 1×1"));
    }

    @Test
    void autoNeedsAKnownType() {
        ModularFixture f = new ModularFixture(1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 0, 0), "нет"));
    }

    @Test
    void autoWithUnknownOutputsIsRefusedInsteadOfMergingTheWholeAreaIntoOneBlock() {
        // баг-репорт: у блоков базовой библиотеки число шлейфов не указано, «не указано» читалось как «без лимита», и экран
        // из 3072 модулей слился в один блок
        ModularFixture f = new ModularFixture(2, 4);
        PowerSupplyType t = f.psuType("БП", null, null, false);

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());

        assertTrue(r.noop());
        assertEquals(0, f.screen.getPowerSupplies().size(), "блоков нет — подбирать размер не по чему");
        assertTrue(r.message().contains("не указано число шлейфов"), r.message());

        PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());
        assertEquals(1, f.screen.getPowerSupplies().size(), "вручную блок на выделение поставить можно");
    }

    @Test
    void engineerLimitsCapTheAutoBlockSizeAndEmptyLimitsKeepItFullyAutomatic() {
        // запрос пользователя: как «Г × В» у карт — инженер может ограничить размер блока, пусто — расчёт автоматический
        ModularFixture f = new ModularFixture(4, 8);
        String t = f.psuType("БП8", 8, null, false).getId();

        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t);
        int automatic = f.screen.getPowerSupplies().size();
        assertEquals(4, automatic, "без ограничений — блоки по 8 модулей (2×4 / 4×2)");

        EditReport r = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t, 2, null);
        assertTrue(r.autoWidth() <= 2, "ширина блока не больше заданной: " + r.autoWidth());
        assertEquals(2, r.autoWidth());
        assertEquals(4, r.autoHeight());

        EditReport narrow = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t, 1, 3);
        assertEquals(1, narrow.autoWidth());
        assertEquals(3, narrow.autoHeight(), "потолок по высоте 3 — блок 1×3, а не 1×4");
        for (var supply : f.screen.getPowerSupplies()) {
            assertTrue(supply.getModuleIds().size() <= 3);
        }
    }

    @Test
    void autoAreaRemembersItsLimitsAndTheSameDragWithOtherLimitsIsNotANoop() {
        ModularFixture f = new ModularFixture(4, 8);
        String t = f.psuType("БП8", 8, null, false).getId();

        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t, 2, 2);
        var area = f.screen.getPowerAutoAreas().get(0);
        assertEquals(2, area.getMaxWidth());
        assertEquals(2, area.getMaxHeight());

        assertTrue(PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t, 2, 2).noop(),
                "те же область, тип и ограничения — ничего не меняется");
        assertFalse(PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 3, 7), t, null, null).noop(),
                "ограничения сняли — область перебирается заново");
        assertNull(f.screen.getPowerAutoAreas().get(0).getMaxWidth());
    }

    @Test
    void manualBlockCoversTheSelectionAndRemovesAnAutoAreaItLandsOn() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.psuType("БП8", 8, null, false).getId();
        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t);
        assertEquals(1, f.screen.getPowerAutoAreas().size());

        EditReport r = PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 1), t);

        assertEquals(1, r.replaced());
        assertEquals(6, r.orphaned(), "авто-блок 4×2 заменён целиком, вне выделения осталось 6 модулей");
        assertEquals("Заменено блоков: 1, модулей без питания: 6", r.message());
        assertTrue(f.screen.getPowerAutoAreas().isEmpty(), "ручной блок поверх области удаляет её запись (D19)");
        assertEquals(List.of(2), sizes(f));
        assertTrue(f.supplyAt(0, 0) != null && f.supplyAt(1, 3) == null);
    }

    @Test
    void draggingAgainOverTheSameManualBlockDoesNothing() {
        ModularFixture f = new ModularFixture(2, 2);
        String t = f.psuType("БП8", 8, null, false).getId();
        PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t);
        String id = f.supplyAt(0, 0).getId();

        assertTrue(PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 1), t).noop());
        assertEquals(id, f.supplyAt(0, 0).getId());
    }

    @Test
    void repeatedAutoDragGivingTheSameBlocksKeepsThemAndTheirPhases() {
        ModularFixture f = new ModularFixture(2, 4);
        String t = f.psuType("БП4", 4, null, false).getId();
        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t);
        List<String> ids = new ArrayList<>();
        f.screen.getPowerSupplies().forEach(s -> ids.add(s.getId()));

        EditReport again = PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t);

        assertTrue(again.noop());
        List<String> after = new ArrayList<>();
        f.screen.getPowerSupplies().forEach(s -> after.add(s.getId()));
        assertEquals(ids, after);
    }

    @Test
    void replacingABlockLeavesItsOtherModulesWithoutPower() {
        ModularFixture f = new ModularFixture(1, 4);
        String t = f.psuType("БП8", 8, null, false).getId();
        PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 3), t);

        EditReport r = PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 2, 0, 3), t);

        assertEquals(1, r.replaced());
        assertEquals(2, r.orphaned());
        assertEquals(List.of(f.id(0, 0), f.id(0, 1)),
                PowerSupplyChecks.check(f.screen, f.ws, ReceiverCardNumbering.DEFAULT).modulesWithoutSupply());
    }

    @Test
    void removingABlockAlsoForgetsItsAutoAreaSoARecalcDoesNotResurrectIt() {
        ModularFixture f = new ModularFixture(1, 4);
        PowerSupplyType t = f.psuType("БП8", 8, null, false);
        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 0, 3), t.getId());

        EditReport r = PowerSupplyEditor.remove(f.screen, f.screen.getPowerSupplies().get(0).getId());

        assertEquals("Блок снят, модулей без питания: 4", r.message());
        assertTrue(f.screen.getPowerAutoAreas().isEmpty());
        assertTrue(PowerSupplyEditor.onTypeChanged(f.screen, f.ws, t.getId()).noop());
        assertTrue(f.screen.getPowerSupplies().isEmpty());
    }

    /** Область 2×4 с картой: сначала без «К» — два блока (6+2); включили «К» → пересчёт даёт один блок 4×2. Блок,
     *  поставленный вручную в другой части экрана, не тронут (его id тот же). Старые блоки области уходят целиком. */
    @Test
    void turningOnTheDedicatedCardOutputRecalculatesOnlyAutoAreasOfThatType() {
        ModularFixture f = new ModularFixture(4, 4);
        String card = f.cardType("К", 8, null).getId();
        ReceiverCardEditor.assignManual(f.screen, f.ws, rect(0, 0, 1, 3), card);
        PowerSupplyType t = f.psuType("БП8", 8, null, false);
        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());
        PowerSupplyEditor.assignManual(f.screen, f.ws, rect(2, 0, 3, 1), t.getId());
        String manualId = f.supplyAt(2, 0).getId();
        List<String> oldAuto = new ArrayList<>();
        f.screen.getPowerSupplies().stream().filter(s -> !s.getId().equals(manualId))
                .forEach(s -> oldAuto.add(s.getId()));
        assertEquals(2, oldAuto.size());

        t.setCardOutput(true);
        EditReport r = PowerSupplyEditor.onTypeChanged(f.screen, f.ws, t.getId());

        assertFalse(r.noop());
        assertTrue(r.removedIds().containsAll(oldAuto), "старые блоки области пересозданы — их звенья выпадают из фаз");
        assertEquals(1, r.createdIds().size());
        assertEquals(manualId, f.supplyAt(2, 0).getId(), "ручной блок не тронут");
        assertEquals(2, f.screen.getPowerSupplies().size());
        assertEquals(1, f.screen.getPowerAutoAreas().size());
        PowerSupplyLayout.Supply recalced = PowerSupplyLayout.compute(f.screen, f.ws, ReceiverCardNumbering.DEFAULT)
                .ofModule(f.id(0, 0));
        assertTrue(recalced.hasDedicatedCard());
    }

    @Test
    void recalcLeavesBlocksAloneWhenTheSplitDoesNotChange() {
        ModularFixture f = new ModularFixture(2, 4);
        PowerSupplyType t = f.psuType("БП8", 8, null, false);
        PowerSupplyEditor.assignAuto(f.screen, f.ws, rect(0, 0, 1, 3), t.getId());
        String id = f.screen.getPowerSupplies().get(0).getId();

        t.setCardOutput(true); // карт в области нет — разбиение то же

        assertTrue(PowerSupplyEditor.onTypeChanged(f.screen, f.ws, t.getId()).noop());
        assertEquals(id, f.screen.getPowerSupplies().get(0).getId());
    }

    @Test
    void placeDefaultsToTheCenterAndMustBeInsideTheBlock() {
        ModularFixture f = new ModularFixture(1, 5);
        String t = f.psuType("БП8", 8, null, false).getId();
        PowerSupplyEditor.assignManual(f.screen, f.ws, rect(0, 0, 0, 4), t);
        PowerSupplyInstance s = f.supplyAt(0, 0);

        assertEquals(f.id(0, 2), UnitNames.placeOf(f.screen, s));
        PowerSupplyEditor.setHost(f.screen, s.getId(), f.id(0, 4));
        assertEquals(f.id(0, 4), UnitNames.placeOf(f.screen, s));
        PowerSupplyEditor.resetHost(f.screen, s.getId());
        assertNull(s.getHostModuleId());
        assertThrows(IllegalArgumentException.class, () -> PowerSupplyEditor.setHost(f.screen, s.getId(), "чужой"));
    }
}
