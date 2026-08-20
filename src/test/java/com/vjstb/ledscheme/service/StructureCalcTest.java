package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты {@link StructureCalc} — количество железа объёмной башни наземного конструктива
 *  (Phase 2.1), по формулам, заданным пользователем НАПРЯМУЮ (2 стакана на стык рам, болты =
 *  число рам × 1.5, требуемый балласт ≈ вес экрана — см. STRUCTURE_CALC_NOTES.md). */
class StructureCalcTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type(double widthMm, double heightMm) {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(widthMm);
        ct.setHeightMm(heightMm);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    @Test
    void countsFollowUserSuppliedRatios_withBaseFrame(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);
        // compute() читает РЕАЛЬНЫЕ ячейки (Phase 2), не просто числа выше -- обычно их
        // заполняет AppModel.updateScreenStructure, здесь эмулируем то же самое напрямую.
        // 3 башни, 3 сегмента на башню (В КАЖДОМ из 2 рядов), 3 уровня перемычек, ядро без
        // выноса (extendedBaseSections=0 -> 1 секция базы на зазор). Round 14 (откат Round
        // 13): перемычка/база между соседними башнями снова сеются автоматически -- 3 башни =
        // 2 зазора x 2 ряда x 3 уровня перемычек, 2 зазора x 1 секция (ядро) базы.
        ScreenLogic.regenerateStructureCells(screen, t, 3, 3, 3, 3, 0, 1);

        StructureCalc.Result result = StructureCalc.compute(screen, t, model.getWorkspace());

        assertEquals(18, result.verticalFrameCount(), "3 башни x 2 ряда x 3 сегмента");
        assertEquals(12, result.peremychkaCount(), "2 зазора x 2 ряда x 3 уровня");
        // Round 7: базовые секции тоже строятся между СОСЕДНИМИ башнями (тот же зазор, что и
        // перемычки), а не по одной на башню -- 2 зазора x 1 секция (ядро) = 2.
        assertEquals(2, result.baseFrameCount(), "2 зазора x 1 секция (ядро)");
        // 2026-08-20, по прямому указанию пользователя: спецификация выдаёт ОДНО общее число
        // рам (физически один и тот же каталожный тип, см. Result#totalFrameCount javadoc) --
        // 18(вертикальных)+12(перемычек)+2(базы)=32.
        assertEquals(32, result.totalFrameCount());
        // Стыков: ТОЛЬКО вертикальные (рама стоит физически сверху другой рамы того же типа)
        // -- (3-1)*2ряда*3башни=12 -> 12*2=24. Round 10: перемычка (Round 8) и база больше не
        // считаются -- ни одна не стоит "сверху" другой рамы (см. StructureCalc.compute javadoc).
        assertEquals(24, result.cupCount());
        // Всего рам 18+12+2=32, болты = ceil(32*1.5) = 48.
        assertEquals(48, result.boltCount());
    }

    @Test
    void removingMiddleSegmentBreaksItsRowJointsButNotTheOtherRow(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);
        // 1 башня, 3 сегмента (в ОБОИХ рядах — передний/задний ряд генерируются всегда),
        // без перемычек -- изолируем именно подсчёт вертикальных стыков по рядам. Round 7:
        // базовые секции теперь строятся между СОСЕДНИМИ башнями (как перемычки) -- при
        // towerCount=1 зазоров нет, значит база = 0 (не пытаемся её здесь считать).
        ScreenLogic.regenerateStructureCells(screen, t, 1, 3, 3, 0, 0, 1);

        StructureCalc.Result full = StructureCalc.compute(screen, t, model.getWorkspace());
        assertEquals(6, full.verticalFrameCount(), "1 башня x 2 ряда x 3 сегмента");
        assertEquals(0, full.baseFrameCount(), "1 башня -- нет зазора, база не строится");
        // Стыков: 2 на ряд (0-1, 1-2) x 2 ряда = 4 вертикальных, базовых нет -> 4*2 = 8 стаканов.
        assertEquals(8, full.cupCount());

        // Убираем СРЕДНИЙ сегмент ПЕРЕДНЕГО ряда (row=0, segment=1) -- баг-репорт "не
        // поддерживает вырезание из середины башни": оба стыка ПЕРЕДНЕГО ряда должны
        // пропасть, а ЗАДНИЙ ряд (row=1) остаётся нетронутым (ряды независимы).
        screen.getStructureFrameCells().removeIf(c -> c.matches(0, 0, 1));
        StructureCalc.Result gapped = StructureCalc.compute(screen, t, model.getWorkspace());
        assertEquals(5, gapped.verticalFrameCount(), "5 из 6 сегментов остались");
        // Передний ряд потерял оба стыка (0), задний ряд не пострадал (2 стыка), базы нет -> 2*2=4.
        assertEquals(4, gapped.cupCount(), "стыки переднего ряда разорваны, заднего -- нет, базы нет");
        // Всего рам 5(вертикальных)+0(перемычек)+0(база)=5, болты = ceil(5*1.5) = 8 (7.5 округляется вверх).
        assertEquals(8, gapped.boltCount());
    }

    @Test
    void requiredBallastApproximatelyEqualsScreenWeightAtRatioOne(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        t.setWeightKg(10);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 2 строки x 4 колонки = 8 кабинетов * 10 кг = 80 кг суммарного веса экрана.
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0);
        // Явно 1:1 -- проверяем исходную "вес ≈ вес экрана" идею при коэффициенте 1.0
        // (дефолт теперь 0.6, см. ballastRatioScalesRequiredWeight за проверкой ЭТОГО дефолта).
        screen.setStructureBallastRatio(1.0);

        StructureCalc.Result result = StructureCalc.compute(screen, t, model.getWorkspace());

        assertEquals(80.0, result.requiredBallastKg(), 0.01, "балласт = вес экрана при коэффициенте 1.0");
        assertEquals(0, result.ballastContainerCount(), "без выбранного типа контейнера в библиотеке -- 0");
    }

    @Test
    void ballastRatioScalesRequiredWeight(@TempDir Path dir) {
        // Баг-репорт/уточнение пользователя: "сейчас он 1:1, в реальности хорошо если 6:10" --
        // коэффициент теперь редактируемый (Screen.structureBallastRatio), дефолт 0.6, а не
        // жёстко зашитая 1.0.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        t.setWeightKg(10);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0); // 80 кг веса экрана

        assertEquals(0.6, screen.getStructureBallastRatio(), 0.001, "дефолт -- 0.6 (6:10), не 1.0");
        StructureCalc.Result defaultResult = StructureCalc.compute(screen, t, model.getWorkspace());
        assertEquals(48.0, defaultResult.requiredBallastKg(), 0.01, "80кг x 0.6 = 48кг балласта");

        screen.setStructureBallastRatio(0.3);
        StructureCalc.Result customResult = StructureCalc.compute(screen, t, model.getWorkspace());
        assertEquals(24.0, customResult.requiredBallastKg(), 0.01, "80кг x 0.3 = 24кг -- коэффициент реально применяется");
    }

    @Test
    void ballastContainerCountDividesByLibraryContainerWeight(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        t.setWeightKg(10);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0); // 80 кг веса экрана
        screen.setStructureBallastRatio(1.0); // изолируем проверку деления от дефолтного коэффициента

        StructureFrameType ballast = new StructureFrameType();
        ballast.setName("Мешок с песком 25 кг");
        ballast.setKind(StructureFrameType.Kind.BALLAST_CONTAINER);
        ballast.setWeightKg(25);
        model.addStructureFrameType(ballast);
        screen.setStructureBallastTypeId(ballast.getId());

        StructureCalc.Result result = StructureCalc.compute(screen, t, model.getWorkspace());

        assertEquals(80.0, result.requiredBallastKg(), 0.01);
        assertEquals(4, result.ballastContainerCount(), "ceil(80 / 25) = 4");
    }

    @Test
    void exceedsSafeHeightWarningAboveFiveMeters(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);

        screen.setStructureTowerHeightMm(4999);
        assertFalse(StructureCalc.compute(screen, t, model.getWorkspace()).exceedsSafeHeightWarning());

        screen.setStructureTowerHeightMm(5001);
        assertTrue(StructureCalc.compute(screen, t, model.getWorkspace()).exceedsSafeHeightWarning());
    }

    @Test
    void exceedsScreenHeightWarningOnlyWhenTowerIsStrictlyTallerThanScreen(@TempDir Path dir) {
        // 2026-08-19, по прямому указанию пользователя: высота башни автоматически
        // приравнивается к высоте экрана -- РАВЕНСТВО теперь НОРМАЛЬНЫЙ случай (расчёт
        // производится без предупреждения), предупреждение -- только если башня ВЫШЕ экрана
        // (граница была {@code >=}, стала строго {@code >}).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500)); // 2 строки x 500мм = 1000мм высоты экрана
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);

        screen.setStructureTowerHeightMm(999);
        assertFalse(StructureCalc.compute(screen, t, model.getWorkspace()).exceedsScreenHeightWarning());

        screen.setStructureTowerHeightMm(1000);
        assertFalse(StructureCalc.compute(screen, t, model.getWorkspace()).exceedsScreenHeightWarning(),
                "равно высоте экрана -- это теперь НОРМАЛЬНЫЙ (авто-приравненный) случай, не нарушение");

        screen.setStructureTowerHeightMm(1001);
        assertTrue(StructureCalc.compute(screen, t, model.getWorkspace()).exceedsScreenHeightWarning(),
                "строго выше экрана -- предупреждение");
    }

    @Test
    void suggestTowerHeightMmEqualsScreenPhysicalHeight() {
        // 2026-08-19: высота башни автоматически приравнивается к собственной высоте экрана
        // (строк x высота кабинета) -- по прямому указанию пользователя.
        CabinetType t = type(500, 500);
        Screen screen = new Screen();
        screen.setRows(4);
        assertEquals(2000.0, StructureCalc.suggestTowerHeightMm(screen, t), 0.001);
        assertEquals(0.0, StructureCalc.suggestTowerHeightMm(screen, null), "без типа кабинета -- нечего предложить");
    }

    // suggestBaseExtensionMm/BASE_TO_HEIGHT_RATIO (auto-подсказка выноса по tg/ctg 60°) были
    // добавлены и в тот же день откачены по прямому указанию пользователя ("забываем про
    // котангенсы, раньше рассчитывалось лучше", 2026-08-20) -- см. class-javadoc StructureCalc
    // за полной историей. Вынос снова полностью ручной ввод, без авто-формулы.

    @Test
    void suggestTowerCountUsesFixedDefaultSpacingAsFenceposts(@TempDir Path dir) {
        // Шаг башен убран как редактируемое поле (2026-08-19, "ни на что не влияет") --
        // suggestTowerCount теперь всегда использует внутреннюю константу
        // StructureCalc.DEFAULT_TOWER_SPACING_MM (тот же прежний дефолт, 1000мм).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500)); // 500мм x 8 колонок = 4000мм ширины
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 8, 0, 0);

        // ceil(4000/1000) + 1 = 5 башен ("заборными столбами" с шагом 1м).
        assertEquals(5, StructureCalc.suggestTowerCount(screen, t));
    }

    @Test
    void suggestVerticalFramesPerTowerReadsLibraryFrameHeight() {
        Screen screen = new Screen();
        screen.setStructureTowerHeightMm(3000);

        StructureFrameType frame = new StructureFrameType();
        frame.setKind(StructureFrameType.Kind.FRAME);
        frame.setHeightMm(950.0);

        // floor(3000/950) = 3 сегмента -- собранная высота (2850мм) не должна ПРЕВЫШАТЬ
        // запрошенную (баг-репорт: "при высоте 2м строится башня 3м", было Math.ceil).
        assertEquals(3, StructureCalc.suggestVerticalFramesPerTower(screen, frame));
        assertEquals(0, StructureCalc.suggestVerticalFramesPerTower(screen, null),
                "без типа рамы в библиотеке -- нечего предложить, не гадать");
    }

    @Test
    void peremychkaIntervalIsOneAndHalfFrameHeights() {
        // Phase 2.2: интервал = 1.5 x высота рамы (не фиксированный мм), по прямому указанию
        // пользователя -- "если интервал в 1.5м не сходится, убирай его чёткое значение,
        // оставляй только расчёт перемычек с интервалом в 1.5 рамы".
        assertEquals(1425.0, StructureCalc.peremychkaIntervalMm(950), 0.01);
        assertEquals(0.0, StructureCalc.peremychkaIntervalMm(0), "нет рамы -- нет интервала");
    }

    @Test
    void suggestPeremychkaLevelsFitsIntervalIntoTowerHeight() {
        // Рама 1000мм -> интервал 1500мм. floor(3000/1500) = 2 уровня (на 1.5м и 3.0м).
        assertEquals(2, StructureCalc.suggestPeremychkaLevels(3000, 1000));
        // floor(2999/1500) = 1 уровень -- неполный последний интервал не считается.
        assertEquals(1, StructureCalc.suggestPeremychkaLevels(2999, 1000));
        assertEquals(0, StructureCalc.suggestPeremychkaLevels(1000, 1000), "башня ниже одного интервала -- 0");
        assertEquals(0, StructureCalc.suggestPeremychkaLevels(3000, 0), "нулевая рама -- не делить на 0");
    }

    @Test
    void extendedBaseSectionsFromOverhangSubtractsMandatoryModuleFirst() {
        // Round 19 (уточнение того же дня, баг-репорт на реальном проекте ДКФ): введённое
        // значение выноса -- это ПОЛНАЯ глубина базы, ВКЛЮЧАЯ обязательный модуль
        // (StructureCalc.CORE_BASE_SECTION_COUNT = 1), не сверх него -- формула сначала
        // вычитает ровно 1 модуль, и только остаток переводит в целое число ДОПОЛНИТЕЛЬНЫХ
        // каталожных секций.
        assertEquals(0, StructureCalc.extendedBaseSectionsFromOverhang(0, 500), "меньше обязательной части -- 0");
        assertEquals(0, StructureCalc.extendedBaseSectionsFromOverhang(500, 500),
                "ровно 1 обязательный модуль, ничего сверх -- 0 доп. секций");
        assertEquals(1, StructureCalc.extendedBaseSectionsFromOverhang(600, 500),
                "600-500=100 сверх обязательного -> округление вверх до 1 доп. секции");
        assertEquals(1, StructureCalc.extendedBaseSectionsFromOverhang(1000, 500),
                "1000-500=500 сверх обязательного -> 500/500=1 ровно доп. секция");
        // Реальный сценарий из баг-репорта (проект ДКФ): пользователь ввёл 1500мм, ожидая
        // ИТОГО 1500мм базы (не 1500 сверх ядра) -- 1500-500=1000 сверх обязательного ->
        // 1000/500=2 доп. секции -> 1(обязательная)+2(доп.)=3 секции x 500мм = 1500мм ровно.
        assertEquals(2, StructureCalc.extendedBaseSectionsFromOverhang(1500, 500),
                "баг-репорт ДКФ: 1500мм ИТОГО -> 2 доп. секции (1 обязательная уже включена в итог)");
        assertEquals(0, StructureCalc.extendedBaseSectionsFromOverhang(500, 0), "sectionDepthMm<=0 -- защитный 0");
    }

    @Test
    void reinforcementFramesInExtensionSectionsCountTowardVerticalFramesAndJoints(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0);
        // 2 башни, 1 сегмент рамы на башню (в каждом ряду), без перемычек, 1 доп. секция
        // выноса -- по 1 усилительной раме (row=2) на башню в секции 1 (Phase 2.2).
        ScreenLogic.regenerateStructureCells(screen, t, 2, 1, 1, 0, 1, 1);

        long reinforcementCount = screen.getStructureFrameCells().stream().filter(c -> c.getRow() == 2).count();
        assertEquals(2, reinforcementCount, "по 1 усилительной раме на башню в единственной секции выноса");

        StructureCalc.Result result = StructureCalc.compute(screen, t, model.getWorkspace());
        // Вертикальных: 2 башни x 2 ряда x 1 сегмент = 4, + 2 усилительные = 6.
        assertEquals(6, result.verticalFrameCount());
        assertEquals(2, result.baseFrameCount(), "1 зазор x 2 секции (ядро+вынос)");
        // Стыков: 0 -- вертикальных нет (1 сегмент на ряд, нет смежных пар), а база и
        // усилительные рамы (Round 10) больше не считаются: ни одна не стоит физически
        // СВЕРХУ другой рамы того же типа, только вертикальный стек даёт стыки.
        assertEquals(0, result.cupCount());
    }

    @Test
    void coreBaseSectionsAreIndependentlyToggleableAndExtensionStartsAfterThem(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);
        // 2 башни (1 зазор), ядро из 2 модулей -- ScreenLogic.regenerateStructureCells
        // принимает coreBaseSectionCount как обычный параметр (не привязана к тому, что
        // в проде AppModel теперь всегда передаёт StructureCalc.CORE_BASE_SECTION_COUNT=1,
        // см. Round 19) -- здесь намеренно 2, чтобы проверить поведение функции при ядре из
        // НЕСКОЛЬКИХ модулей, плюс 1 доп. секция выноса под балласт -- сеется автоматически.
        ScreenLogic.regenerateStructureCells(screen, t, 2, 1, 1, 0, 1, 2);

        var baseCells = screen.getStructureBaseFrameCells();
        assertEquals(3, baseCells.size(), "1 зазор x (2 секции ядра + 1 секция выноса) = 3");
        assertTrue(baseCells.stream().anyMatch(c -> c.matches(0, 0)), "первая секция ядра существует");
        assertTrue(baseCells.stream().anyMatch(c -> c.matches(0, 1)), "вторая секция ядра существует");
        assertTrue(baseCells.stream().anyMatch(c -> c.matches(0, 2)), "секция выноса существует отдельно");

        // Обе секции ядра -- РЕАЛЬНО независимые записи: спрятать одну не трогает другую (это и
        // есть "являться 2 отдельными рамами", а не 2 куска одной плиты).
        baseCells.stream().filter(c -> c.matches(0, 0)).findFirst().orElseThrow().setHidden(true);
        assertTrue(baseCells.stream().anyMatch(c -> c.matches(0, 0) && c.isHidden()));
        assertTrue(baseCells.stream().anyMatch(c -> c.matches(0, 1) && !c.isHidden()),
                "вторая секция ядра не пострадала от скрытия первой");

        // Усилительная рама (row == 2) выноса должна начинаться СРАЗУ ПОСЛЕ ядра (индекс 2 --
        // coreBaseSectionCount), не жёстко с 1.
        boolean reinforcementAtCoreEnd = screen.getStructureFrameCells().stream()
                .anyMatch(c -> c.getRow() == 2 && c.matches(0, 2, 2) && !c.isHidden());
        assertTrue(reinforcementAtCoreEnd, "усилительная рама первой (и единственной) секции выноса -- индекс 2");
        boolean reinforcementInsideCore = screen.getStructureFrameCells().stream()
                .anyMatch(c -> c.getRow() == 2 && (c.matches(0, 2, 0) || c.matches(0, 2, 1)));
        assertFalse(reinforcementInsideCore, "усилительных рам внутри ядра быть не должно");
    }

    @Test
    void towersNotGeneratedWhereScreenHasNoCabinetsUnderThem(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 4, 0, 0); // 4 колонки x 500мм = 2000мм
        // Прячем кабинеты в правой половине экрана (колонки 2,3, т.е. 1000..2000мм) -- имитирует
        // вырезанную форму экрана (Phase 2.2: "если в экране есть отсутствующие кабинеты, то
        // для них башни не строятся").
        for (var cab : screen.getCabinets()) {
            if (cab.getColIndex() >= 2) {
                cab.setHidden(true);
            }
        }
        // Номинально 3 башни (0, 1000, 2000мм) при шаге 1000мм и ширине 2000мм.
        ScreenLogic.regenerateStructureCells(screen, t, 3, 1, 1, 0, 0, 1);

        var towers = screen.getStructureFrameCells().stream()
                .map(com.vjstb.ledscheme.model.StructureFrameCell::getTowerIndex).distinct().sorted().toList();
        assertEquals(java.util.List.of(0, 1), towers, "башня 2 (зона 1500..2500мм, всё скрыто) не строится");
    }

    @Test
    void towerNotBuiltWhenOnlyGroundRowIsMissingEvenIfUpperRowsRemain(@TempDir Path dir) {
        // Баг-репорт (реальный проект пользователя): проём-арка вырезан снизу экрана, верхние
        // ряды того же столбца остались видимы. Башня растёт ОТ ЗЕМЛИ вверх -- ей физически
        // нечего поддерживать под проёмом, даже если где-то выше в том же столбце снова есть
        // видимые кабинеты. Старая версия проверки смотрела "виден ли хоть один кабинет в
        // столбце на любом ряду" и ошибочно считала такую башню нужной.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 4, 4, 0, 0); // 4 ряда x 4 колонки, 2000x2000мм
        // Башня 2 стоит на X=2000мм, её зона ответственности [1500..2500)мм -- это СТОЛБЕЦ 3
        // (X=[1500..2000)) при cellW=500мм, не столбец 2. Нижний ряд (rowIndex=3, "земля")
        // столбца 3 прячем, верхние 3 ряда остаются видимы -- проём-арка снизу.
        for (var cab : screen.getCabinets()) {
            if (cab.getColIndex() == 3 && cab.getRowIndex() == 3) {
                cab.setHidden(true);
            }
        }
        ScreenLogic.regenerateStructureCells(screen, t, 3, 1, 1, 0, 0, 1);

        var towers = screen.getStructureFrameCells().stream()
                .map(com.vjstb.ledscheme.model.StructureFrameCell::getTowerIndex).distinct().sorted().toList();
        assertEquals(java.util.List.of(0, 1), towers,
                "башня 2 (X=2000мм) не строится -- под ней пусто у земли, хотя выше есть кабинеты");
    }

    @Test
    void towerStillBuiltWhenGroundRowVisibleEvenIfUpperRowsAreMissing(@TempDir Path dir) {
        // Обратный случай -- вырезаны верхние ряды (например, экран ниже стандартного в этом
        // месте), нижний (опорный) ряд остался виден -- башня по-прежнему нужна.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 4, 4, 0, 0);
        // Башня 2 -> столбец 3 (см. предыдущий тест) -- верхние 3 ряда прячем, нижний (опорный)
        // оставляем видимым.
        for (var cab : screen.getCabinets()) {
            if (cab.getColIndex() == 3 && cab.getRowIndex() < 3) {
                cab.setHidden(true);
            }
        }
        ScreenLogic.regenerateStructureCells(screen, t, 3, 1, 1, 0, 0, 1);

        var towers = screen.getStructureFrameCells().stream()
                .map(com.vjstb.ledscheme.model.StructureFrameCell::getTowerIndex).distinct().sorted().toList();
        assertEquals(java.util.List.of(0, 1, 2), towers, "нижний ряд виден -- башня 2 всё ещё строится");
    }

    @Test
    void backRowIsIndependentAndShorterThanFrontRow(@TempDir Path dir) {
        // Round 5, баг-репорт по фото реальной башни: с фронта видна ОДНА полноразмерная
        // лестничная рама (передний ряд), задний ряд -- короткая опора (~1-2м), а не вторая
        // полноразмерная башня. regenerateStructureCells должен уважать РАЗНЫЕ границы для
        // row=0 (передний, verticalFramesPerTower) и row=1 (задний, backRowSegments).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 2, 6, 0, 0);
        // 1 башня, передний ряд -- 5 сегментов (высокая башня), задний -- всего 2 (короткая опора).
        ScreenLogic.regenerateStructureCells(screen, t, 1, 5, 2, 0, 0, 1);

        long frontSegments = screen.getStructureFrameCells().stream().filter(c -> c.getRow() == 0).count();
        long backSegments = screen.getStructureFrameCells().stream().filter(c -> c.getRow() == 1).count();
        assertEquals(5, frontSegments, "передний ряд -- полная высота (5 сегментов)");
        assertEquals(2, backSegments, "задний ряд -- короткая опора (2 сегмента), независимо от переднего");

        // Уменьшили передний ряд до 3 -- задний остаётся 2, независимо (не связаны формулой).
        ScreenLogic.regenerateStructureCells(screen, t, 1, 3, 2, 0, 0, 1);
        frontSegments = screen.getStructureFrameCells().stream().filter(c -> c.getRow() == 0 && !c.isHidden()).count();
        backSegments = screen.getStructureFrameCells().stream().filter(c -> c.getRow() == 1 && !c.isHidden()).count();
        assertEquals(3, frontSegments);
        assertEquals(2, backSegments, "задний ряд не зависит от изменения переднего");
    }

    @Test
    void suggestBackRowSegmentsTargetsAboutOneAndHalfMeters() {
        // floor/round(1500/950) = 2 сегмента (~1.9м, ближе к верхней границе "1-2м").
        assertEquals(2, StructureCalc.suggestBackRowSegments(950));
        // Рама 1500мм -> ровно 1 сегмент.
        assertEquals(1, StructureCalc.suggestBackRowSegments(1500));
        assertEquals(1, StructureCalc.suggestBackRowSegments(0), "нет рамы -- хотя бы 1 сегмент, не делить на 0");
    }

    @Test
    void peremychkaConnectsAdjacentTowersOnlyWhenBothAreValid(@TempDir Path dir) {
        // Round 5, баг-репорт с обведённым фото реальной башни: перемычка соединяет СОСЕДНИЕ
        // башни В ПРЕДЕЛАХ ОДНОГО ряда, не передний/задний ряд одной башни -- и, как следствие,
        // не может появиться там, где одной из двух соседних башен физически нет.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0); // 6 колонок x 500мм = 3000мм
        // Прячем столбцы 3-5 (X=1500..3000мм) -- вся зона ответственности башни 2
        // (X=2000мм, ±500мм = [1500..2500)) остаётся без видимых кабинетов.
        for (var cab : screen.getCabinets()) {
            if (cab.getColIndex() >= 3) {
                cab.setHidden(true);
            }
        }
        // 3 номинальные башни (0,1000,2000мм), 1 уровень перемычек.
        ScreenLogic.regenerateStructureCells(screen, t, 3, 1, 1, 1, 0, 1);

        var peremychkaTowers = screen.getStructurePeremychkaCells().stream()
                .map(StructurePeremychkaCell::getTowerIndex).distinct().sorted().toList();
        // Башня 2 не строится (нет кабинетов) -> зазор 0-1 (между валидными башнями 0 и 1)
        // остаётся, зазор 1-2 не создаётся (башня 2 отсутствует).
        assertEquals(List.of(0), peremychkaTowers, "только зазор между валидными башнями 0 и 1");

        // Каждая перемычка -- и передний, и задний ряд независимо (row=0 и row=1).
        var rows = screen.getStructurePeremychkaCells().stream()
                .map(StructurePeremychkaCell::getRow).distinct().sorted().toList();
        assertEquals(List.of(0, 1), rows);
    }

    @Test
    void baseFrameConnectsAdjacentTowersOnlyWhenBothAreValid(@TempDir Path dir) {
        // Round 7, баг-репорт: "рамы основания должны ставиться точно так же как перемычки" --
        // тот же зазорный принцип (и то же правило валидности), просто на уровне пола.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(500, 500));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);
        for (var cab : screen.getCabinets()) {
            if (cab.getColIndex() >= 3) {
                cab.setHidden(true);
            }
        }
        ScreenLogic.regenerateStructureCells(screen, t, 3, 1, 1, 0, 0, 1);

        var baseTowers = screen.getStructureBaseFrameCells().stream()
                .map(StructureBaseFrameCell::getTowerIndex).distinct().sorted().toList();
        assertEquals(List.of(0), baseTowers, "только зазор между валидными башнями 0 и 1");
    }
}
