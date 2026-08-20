package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.HoistType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты распределения веса подвешенного экрана по точкам подвеса (метод грузовых
 *  площадей, см. class-javadoc {@link RiggingCalc}). */
class RiggingCalcTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type(double weightKg) {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weightKg);
        return ct;
    }

    @Test
    void uniformScreen_distributesWeightEvenlyAcrossPoints(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(10));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 4 колонки x 2 строки по 10кг/кабинет = 80 кг + 20% крепёж = 96 кг. 4 колонки/
        // 2 точки делятся БЕЗ ничейного исхода привязки к ближайшей точке (граница между
        // левой и правой парой колонок не совпадает ни с одной из точек) — чистый тест
        // симметричного распределения без завязки на конкретное правило тай-брейка
        // (см. compute_edgeTieBreaksTowardCenterNotLeft за прицельным тестом на ничьи).
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0);

        int suggested = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertEquals(2, suggested); // 4 * 500мм = 2000мм <= MAX_SPAN_MM -> абсолютный минимум 2, грузоподъёмность не задана

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), suggested);
        assertEquals(80.0, result.totalCabinetWeightKg(), 1e-6);
        assertEquals(96.0, result.totalWeightWithHardwareKg(), 1e-6);

        double sum = result.points().stream().mapToDouble(RiggingCalc.PointLoad::loadKg).sum();
        assertEquals(96.0, sum, 1e-6);
        // Равномерная нагрузка по ширине -> равное распределение по 2 симметричным точкам.
        for (RiggingCalc.PointLoad p : result.points()) {
            assertEquals(48.0, p.loadKg(), 1e-6);
        }
    }

    @Test
    void hiddenCabinets_dontContributeWeightOrShiftDistribution(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(10));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 6x1 экран, правая половина (арка/вырез) скрыта -> вес сосредоточен слева.
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);
        for (int col = 3; col < 6; col++) {
            screen.cabinetAt(0, col).setHidden(true);
        }

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 3);
        assertEquals(30.0, result.totalCabinetWeightKg(), 1e-6); // только 3 видимых кабинета
        double sum = result.points().stream().mapToDouble(RiggingCalc.PointLoad::loadKg).sum();
        assertEquals(36.0, sum, 1e-6); // 30 * 1.2 наценки, весь вес где-то среди точек
        // Крайняя правая точка (x=3000, глубоко в вырезанной/скрытой зоне cols 3..5,
        // x от 1500 до 3000) не должна получить ни грамма веса — все видимые кабинеты
        // (cols 0..2, x от 0 до 1500) физически ближе к левым точкам.
        RiggingCalc.PointLoad rightmost = result.points().get(result.points().size() - 1);
        assertEquals(0.0, rightmost.loadKg(), 1e-6);
    }

    @Test
    void overCapacity_flaggedWhenHoistWllExceeded(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0); // 2 кабинета x 50кг = 100кг, +20% = 120кг
        screen.setRiggingHoistCapacityKg(50.0); // одна точка при 2 точках несёт 60кг -> превышение

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity));
    }

    @Test
    void noCapacitySet_neverFlaggedOverCapacity(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(1000)); // намеренно огромный вес
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0);
        assertNull(screen.getRiggingHoistCapacityKg());

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().noneMatch(RiggingCalc.PointLoad::overCapacity));
    }

    @Test
    void libraryHoistType_takesPrecedenceOverManualCapacity(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0); // 2 x 50кг = 100кг, +20% = 120кг

        HoistType weak = new HoistType();
        weak.setName("Слабая лебёдка");
        weak.setWllKg(50); // одна точка при 2 точках несёт 60кг -> должно флагануть превышение
        model.addHoistType(weak);
        screen.setRiggingHoistTypeId(weak.getId());
        // Ручное число НАМЕРЕННО противоречит библиотечной записи -- FK обязан победить.
        screen.setRiggingHoistCapacityKg(1_000_000.0);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity),
                "Библиотечный WLL (50кг) обязан победить ручное число (1_000_000), иначе превышение не найдётся");
    }

    @Test
    void danglingHoistTypeId_fallsBackToManualCapacity(@TempDir Path dir) {
        // FK ссылается на запись, которой больше нет в библиотеке (удалена на сервере) --
        // не должно падать, обязано тихо откатиться на ручное число, а не считать
        // "грузоподъёмность не указана" (иначе реальный fallback-сценарий из
        // RIGGING_CALC_NOTES.md был бы бесполезен).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0);
        screen.setRiggingHoistTypeId("does-not-exist");
        screen.setRiggingHoistCapacityKg(50.0);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity),
                "Несуществующий FK обязан откатиться на riggingHoistCapacityKg (50), не игнорировать проверку вовсе");
    }

    @Test
    void suggestPointCount_geometricMinimumFallsBackTo2WithoutKnownModuleWidth() {
        // Без известной ширины модуля (defaultType == null) физическую ширину экрана
        // посчитать нечем -- откат на абсолютный минимум 2, а не на число модулей
        // (старая формула ceil(cols/2), см. следующий тест за причиной отказа от неё).
        com.vjstb.ledscheme.model.Workspace ws = new com.vjstb.ledscheme.model.Workspace();
        Screen wide = new Screen();
        wide.setCols(11);
        wide.setRows(1);
        assertEquals(2, RiggingCalc.suggestPointCount(wide, null, ws));

        Screen narrow = new Screen();
        narrow.setCols(1);
        narrow.setRows(1);
        assertEquals(2, RiggingCalc.suggestPointCount(narrow, null, ws));
    }

    @Test
    void suggestPointCount_usesPhysicalSpanNotModuleCount() {
        // Прямой тест на баг-репорт пользователя: экран 17х15 кабинетов Dicolor
        // (500х500мм, физическая ширина 8.5м) реально укладывается в 4 лебёдки, но
        // старая формула (ceil(модули/2)) требовала 9 -- считала минимум по числу
        // занятых колонок сетки, а не по физическому пролёту между точками. Новая
        // формула (MAX_SPAN_MM=3000мм, за вычетом EDGE_MARGIN_MM=500мм с каждого
        // края) считает минимум от реальной ПОЛЕЗНОЙ ширины в мм.
        com.vjstb.ledscheme.model.Workspace ws = new com.vjstb.ledscheme.model.Workspace();
        CabinetType t500 = type(7.2);

        // 17*500=8500мм, полезная ширина 8500-2*500=7500мм -> ceil(7500/3000)=3 пролёта -> 4 точки
        Screen wide17 = new Screen();
        wide17.setCols(17);
        wide17.setRows(15);
        assertEquals(4, RiggingCalc.suggestPointCount(wide17, t500, ws),
                "8.5м экран из мелких модулей обязан предлагать 4 точки, а не 9 (баг-репорт)");

        // 8*500=4000мм, полезная ширина 4000-1000=3000мм -> ceil(3000/3000)=1 пролёт -> 2 точки
        Screen medium = new Screen();
        medium.setCols(8);
        medium.setRows(1);
        assertEquals(2, RiggingCalc.suggestPointCount(medium, t500, ws));

        Screen narrow = new Screen(); // 1 * 500мм = 500мм -> 1 пролёт -> минимум 2
        narrow.setCols(1);
        narrow.setRows(1);
        assertEquals(2, RiggingCalc.suggestPointCount(narrow, t500, ws));
    }

    @Test
    void compute_insetsPointsFromScreenEdgeByStandardMargin() {
        // Прямой тест на баг-репорт пользователя: "за самые края фермы лебёдки не
        // вешают, стандартный отступ полметра, а расчёт в экспортируемой табличке
        // всегда начинается с нуля". Крайние точки обязаны отступать от 0 и от
        // widthMm на RiggingCalc.EDGE_MARGIN_MM (500мм), а не садиться прямо на края.
        com.vjstb.ledscheme.model.Workspace ws = new com.vjstb.ledscheme.model.Workspace();
        CabinetType t500 = type(10);

        Screen screen = new Screen(); // 6 * 500мм = 3000мм
        screen.setCols(6);
        screen.setRows(1);
        RiggingCalc.Result result = RiggingCalc.compute(screen, t500, ws, 3);
        assertEquals(500.0, result.points().get(0).xMm(), 1e-6,
                "первая точка обязана отступать на EDGE_MARGIN_MM от левого края, не садиться на x=0");
        assertEquals(2500.0, result.points().get(2).xMm(), 1e-6,
                "последняя точка обязана отступать на EDGE_MARGIN_MM от правого края (3000мм), не садиться на него");
        assertEquals(1500.0, result.points().get(1).xMm(), 1e-6, "средняя точка -- ровно посередине");
    }

    @Test
    void compute_narrowScreenSkipsMarginRatherThanCollidingPoints() {
        // Экран у́же 2*EDGE_MARGIN_MM (1000мм) -- отступ целиком игнорируется (не
        // наполовину), иначе точки сталкивались бы друг с другом или вылезали за
        // пределы экрана. См. RiggingCalc.usableWidthMm/edgeMarginMm javadoc.
        com.vjstb.ledscheme.model.Workspace ws = new com.vjstb.ledscheme.model.Workspace();
        CabinetType t500 = type(10);

        Screen screen = new Screen(); // 1 * 500мм = 500мм <= 2*500мм
        screen.setCols(1);
        screen.setRows(1);
        RiggingCalc.Result result = RiggingCalc.compute(screen, t500, ws, 2);
        assertEquals(0.0, result.points().get(0).xMm(), 1e-6);
        assertEquals(500.0, result.points().get(1).xMm(), 1e-6);
    }

    @Test
    void compute_edgeTieBreaksTowardCenterNotLeft(@TempDir Path dir) {
        // Прямой тест на баг-репорт пользователя: "нагрузка на крайние точки идёт
        // неравномерно, похоже расчёт слева направо". Воспроизводит РЕАЛЬНЫЙ проект
        // из предыдущего баг-репорта (17х15 Dicolor 500х500мм -> 4 точки): при точках
        // на x=500,3000,5500,8000 колонки k=3 (x=1750) и k=13 (x=6750) лежат РОВНО на
        // середине между соседними точками -- законная ничья. Старый код (первый
        // встреченный по возрастанию индекса побеждает при "d < best") отдавал ОБЕ
        // ничьи более ЛЕВОЙ точке пары, из-за чего крайняя левая точка (3 колонки)
        // получала на целую колонку больше крайней правой (2 колонки) -- ровно та
        // асимметрия КРАЙНИХ точек, о которой сообщил пользователь. Новое правило
        // (тянуть к центру) отдаёт k=3 точке 1 (не 0) и k=13 -- по-прежнему точке 2
        // (уже была ближе к центру), выравнивая крайние точки между собой.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(7.2)); // вес и ширина модуля Dicolor из баг-репорта
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 17, 0, 0); // 1 строка -- вес по колонкам не задваивается

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 4);
        RiggingCalc.PointLoad leftEdge = result.points().get(0);
        RiggingCalc.PointLoad rightEdge = result.points().get(3);
        assertEquals(rightEdge.loadKg(), leftEdge.loadKg(), 1e-6,
                "крайние точки симметричного экрана обязаны нести ОДИНАКОВУЮ нагрузку, "
                        + "а не быть перекошены в пользу левого края (баг-репорт)");
        // 3 колонки на каждый край (k=0..2 и k=14..16) * 7.2кг * 1.2 наценки = 25.92кг --
        // числовое подтверждение, что асимметричные ничьи ушли именно в центр (точки 1/2),
        // а не растворились где попало.
        assertEquals(25.92, leftEdge.loadKg(), 1e-6);
        assertEquals(25.92, rightEdge.loadKg(), 1e-6);
    }

    @Test
    void suggestPointCount_increasesWhenLoadExceedsHoistCapacity(@TempDir Path dir) {
        // Прямой тест на баг-репорт «при перерасчёте количество лебёдок не меняется»:
        // формула раньше зависела ТОЛЬКО от ширины экрана в колонках, теперь обязана
        // расти, если реальная нагрузка на точку (метод compute) превышает WLL.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(100)); // тяжёлые кабинеты, 100кг каждый
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 8 колонок x 1 строка, кабинет 500мм -> ширина 4000мм, полезная ширина за
        // вычетом EDGE_MARGIN_MM с каждого края 4000-1000=3000мм -> ceil(3000/3000)=1
        // пролёт -> база 2 точки (см. MAX_SPAN_MM). Суммарный вес 800кг +20% = 960кг.
        Screen screen = model.addScreen("E", t.getId(), 1, 8, 0, 0);

        int withoutCapacity = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertEquals(2, withoutCapacity, "без указанной грузоподъёмности -- геометрический минимум по пролёту");

        screen.setRiggingHoistCapacityKg(150.0); // ниже расчётной нагрузки на точку при базовых 2 точках
        int withCapacity = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertTrue(withCapacity > 2,
                "заданная грузоподъёмность (150кг) ниже расчётной нагрузки на точку -- количество точек обязано вырасти");

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), withCapacity);
        assertTrue(result.points().stream().noneMatch(RiggingCalc.PointLoad::overCapacity),
                "предложенное количество точек обязано реально устранять превышение, а не просто вырасти на 1");
    }
}
