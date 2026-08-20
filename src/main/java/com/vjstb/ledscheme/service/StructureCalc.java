package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.Workspace;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Расчёт количества элементов наземного конструктива (объёмные башни из рам, см.
 * STRUCTURE_CALC_NOTES.md в корне репозитория) для монтажа {@link
 * com.vjstb.ledscheme.model.ScreenMountType#STRUCTURE} — bill of materials (рамы/стаканы/
 * болты/балласт), а НЕ распределение нагрузки по точкам, как у {@link RiggingCalc} для
 * RIGGED.
 *
 * <p><b>Источники данных — важное разграничение</b>: точное соотношение стаканов и болтов
 * на стык/раму задано пользователем НАПРЯМУЮ по его реальному оборудованию (2 стакана на
 * каждый стык рам, болты = число рам × 1.5) — это НЕ догадка.
 *
 * <p><b>Phase 2.1 — объёмная башня (2026-08-14)</b>: башня строится из ДВУХ рядов
 * вертикальных рам (передний — у экрана, задний — на глубину одной рамы позади,
 * см. {@link StructureFrameCell#getRow()}), соединённых горизонтальными перемычками
 * (передний↔задний, ВНУТРИ одной башни — НЕ между соседними башнями, эта роль у
 * горизонтальных рам была в исходной версии Phase 2 и упразднена, см. историю
 * {@code StructureBraceCell} в git) каждые ~1.5м высоты (интервал — глобальная настройка
 * Персонализации), плюс базовая рама, которая может выносится назад в дополнительных
 * секциях (каждая — ширина рамы) под балласт. Round 16 — отдельный "короткий" тип рамы
 * упразднён целиком (по прямому указанию пользователя: "чаще всего перемычка делается из
 * обычной рамы... уберём короткие рамы, переделываем всё под обычные"), перемычка/база/
 * усилительные рамы теперь берут габариты из ТОГО ЖЕ типа, что и вертикальные рамы башни
 * (см. {@code ui.Structure3DPanel#computeGeometry}) — если инженеру на месте нужна именно
 * короткая рама где-то конкретно, это по-прежнему доступно per-cell (см.
 * {@link StructureFrameCell#getFrameTypeId()}), просто больше не встроено в номинальный
 * расчёт всей сетки. Числа
 * {@code structureTowerCount}/{@code structureVerticalFramesPerTower}/
 * {@code structurePeremychkaLevels}/{@code structureExtendedBaseSections} на {@link Screen}
 * остаются только НОМИНАЛЬНЫМИ границами сетки (заполняются в
 * {@code service.ScreenLogic#regenerateStructureCells} при нажатии «Рассчитать
 * конструктив») — калькулятор только предлагает стартовое значение (см. {@code suggestXxx}
 * ниже), а РЕАЛЬНО существующее железо — это {@link Screen#getStructureFrameCells()}/
 * {@code getStructurePeremychkaCells()}/{@code getStructureBaseFrameCells()}, редактируемые
 * поячеечно кликом в 3D-превью ({@code ui.Structure3DPanel}). {@link #compute} читает
 * именно эти списки — убранный из середины башни сегмент не попадёт ни в счётчик рам, ни в
 * стыки/стаканы на него.
 *
 * <p><b>Балласт — правило "вес ≈ вес экрана × коэффициент", не ветровой расчёт</b>: прежняя
 * версия считала требуемый балласт от давления ветра (динамическое давление × Cd × площадь →
 * опрокидывающий момент → footprint) — по прямому указанию пользователя эта физика убрана
 * целиком как избыточная для этого инструмента. Новое правило — простое эмпирическое:
 * суммарный вес балласта = вес отгружаемого экрана × {@link Screen#getStructureBallastRatio()}
 * (простой противовес/рычаг, без отдельного запаса — см. {@link #compute}). Коэффициент
 * (2026-08-19) — РЕДАКТИРУЕМЫЙ, не жёстко зашитая константа: изначально был жёстко 1:1, но
 * по прямому указанию пользователя ("в реальности хорошо если 6:10") дефолт теперь 0.6. Это
 * ТОЖЕ грубая оценка, не заменяет инженерный расчёт перед монтажом.
 *
 * <p><b>«Вынос» базы — ручной ввод в мм, не автоформула (2026-08-19)</b>: число дополнительных
 * секций выноса базовой рамы под балласт ({@link Screen#getStructureExtendedBaseSections()})
 * раньше предлагалось эвристикой по высоте/ширине экрана ({@code suggestExtendedBaseSections},
 * УДАЛЕНА) — по прямому указанию пользователя эта эвристика признана ненадёжной ("аппаратно не
 * получается его просчитывать эффективно"), заменена прямым ручным вводом расстояния в мм
 * ({@link Screen#getStructureBaseExtensionMm()}, поле «Вынос базы под балласт, мм» в форме —
 * занимает место прежнего параметра «Шаг башен», см. {@link #DEFAULT_TOWER_SPACING_MM} за тем,
 * почему тот был убран). {@link #extendedBaseSectionsFromOverhang} — единственная оставшаяся
 * формула, чисто перевод мм в целое число каталожных модулей, не инженерная оценка.
 *
 * <p><b>НЕ добавляй авто-формулу для стартового значения выноса без нового явного запроса
 * (2026-08-20, откачено В ТОТ ЖЕ ДЕНЬ)</b>: между этой правкой и предыдущей на короткое время
 * существовали {@code BASE_TO_HEIGHT_RATIO}/{@code suggestBaseExtensionMm} — стартовая
 * подсказка выноса как {@code towerHeightMm × ctg(60°)} (после исправления опечатки "tg60"
 * → "ctg60"). Пользователь явно попросил откатить: "забываем про котангенсы, раньше
 * рассчитывалось лучше" — т.е. простое персистированное значение (без авто-формулы вообще,
 * тот же принцип, что и у {@link #extendedBaseSectionsFromOverhang} — вынос целиком ручной
 * ввод) оказалось предпочтительнее любой автоматической подсказки. Не изобретай новую формулу
 * для этого поля впредь без прямого запроса пользователя.
 */
public final class StructureCalc {

    private StructureCalc() {
    }

    /** Болты = число рам × этот коэффициент — точная цифра от пользователя (его реальное
     *  оборудование), не оценка. */
    private static final double BOLTS_PER_FRAME = 1.5;
    /** Стаканов на один стык рам — точная цифра от пользователя для стыков ВЕРТИКАЛЬНЫХ
     *  рам; применяется здесь одинаково и к стыкам перемычек/базовых секций — см.
     *  class-javadoc {@link #compute} про это допущение. */
    private static final int CUPS_PER_JOINT = 2;
    /** Выше этой высоты конструктив не строим без отдельного инженерного расчёта — не
     *  жёсткий блок (см. {@link Result#exceedsSafeHeightWarning()}), предупреждение. */
    public static final double MAX_SAFE_TOWER_HEIGHT_MM = 5000;

    /** Интервал перемычек = высота рамы × этот коэффициент — по прямому указанию пользователя
     *  (Phase 2.2): "если интервал в 1.5м не сходится, убирай его чёткое значение, оставляй
     *  только расчёт перемычек с интервалом в 1.5 рамы" — раньше 1.5м было отдельной константой
     *  (мм, глобальная настройка Персонализации), теперь ЭТО коэффициент от РЕАЛЬНОЙ высоты
     *  выбранной рамы — у рам есть перегородка ровно посередине для крепления перемычки, "1.5
     *  рамы" — это стык/середина через раму на раму. */
    private static final double PEREMYCHKA_INTERVAL_FRAME_MULTIPLE = 1.5;

    /** Высота усилительной рамы в зоне выноса базы под балласт (row == 2, см.
     *  {@code StructureFrameCell} class-javadoc) по умолчанию — "1 метровая рама на секцию" по
     *  прямому указанию пользователя; переопределяется per-cell выбором конкретного типа рамы
     *  в библиотеке (см. {@code StructureFrameCell#getFrameTypeId()}), это только фолбэк. */
    public static final double DEFAULT_REINFORCEMENT_FRAME_HEIGHT_MM = 1000;

    /** Фолбэк-размеры рамы/короткой рамы, если тип не выбран в библиотеке вообще — те же
     *  числа, что использует {@code ui.Structure3DPanel} для рендера (см. её собственные
     *  {@code DEFAULT_FRAME_WIDTH_MM}/{@code DEFAULT_SECTION_DEPTH_MM}, независимая копия по
     *  обычной конвенции проекта), нужны здесь для {@link #extendedBaseSectionsFromOverhang} —
     *  единственное место в этом классе, где расчёт зависит от физических габаритов рамы, а не
     *  только от номинальных счётчиков экрана. */
    public static final double DEFAULT_FRAME_WIDTH_MM = 500;
    public static final double DEFAULT_SECTION_DEPTH_MM = 500;

    /** Шаг расстановки башен по ширине экрана (2026-08-19) — раньше был отдельным
     *  редактируемым полем {@code Screen.structureTowerSpacingMm}, убран целиком по прямому
     *  указанию пользователя: "убирай параметр шаг башен, он ни на что не влияет" — верно
     *  в том смысле, что {@link #compute} (итоговая ведомость материалов) от него не зависит
     *  вовсе, он влиял только на {@link #suggestTowerCount} (стартовое число башен) и на X-
     *  позиции в 3D-рендере/picking'е ({@code ui.Structure3DPanel}), а реальная расстановка
     *  всё равно правится поячеечно кликом. Теперь это внутренняя константа (тот же прежний
     *  дефолт — 1000мм), не выставляется пользователем; освободившееся место в форме занял
     *  параметр «Вынос базы под балласт» ({@link Screen#getStructureBaseExtensionMm()}). */
    public static final double DEFAULT_TOWER_SPACING_MM = 1000.0;

    /** Обязательная ("мандатная") глубина базы — РОВНО 1 каталожный модуль рамы, не зависит
     *  от того, сколько рядов вертикальных рам физически стоит на башне (2026-08-19, по
     *  прямому указанию пользователя: "обязательная часть базы конструктива — строго 500мм
     *  [= 1 модуль при их реальной библиотеке], всё остальное считается выносом"). ДО этой
     *  правки (Round 10) ядро покрывало ОБА ряда сразу ({@code ceil(2×frameW/sectionDepthMm)},
     *  обычно 2 модуля) — второй ряд получал опору "бесплатно", в обязательном минимуме, не
     *  за счёт выноса. Теперь это неверно: под вторым рядом опора должна ЯВНО входить в вынос,
     *  который инженер вводит сам (см. {@link #extendedBaseSectionsFromOverhang} — вычитает
     *  этот же 1 модуль из введённого пользователем значения, т.к. то значение включает в
     *  себя обязательную часть, а не идёт сверх нее). Раньше был методом от {@code frameW}/
     *  {@code sectionDepthMm} — теперь просто константа, физические габариты рамы больше не
     *  влияют на размер обязательной части (она всегда 1 модуль, какой бы широкой рама ни
     *  была). */
    public static final int CORE_BASE_SECTION_COUNT = 1;

    /** Число башен по ширине экрана — расставлены "как заборные столбы" с шагом
     *  {@link #DEFAULT_TOWER_SPACING_MM}: N интервалов требуют N+1 башен, не меньше 2.
     *  Round 14 — откат Round 13 ("мало башен, по краям"): пользователь явно попросил
     *  вернуть сплошную стену конструктива по умолчанию ("пусть для экрана генерится стена
     *  конструктива, при необходимости инженер сам удалит ненужные рамы") — проще убрать
     *  лишнее кликом в 3D, чем достраивать недостающее. */
    public static int suggestTowerCount(Screen screen, CabinetType type) {
        double widthMm = screen.getCols() * (type != null ? type.getWidthMm() : 0);
        if (widthMm <= 0) {
            return 2;
        }
        return Math.max(2, (int) Math.ceil(widthMm / DEFAULT_TOWER_SPACING_MM) + 1);
    }

    /** Стартовое предложение высоты башни (мм) — по прямому указанию пользователя (2026-08-19)
     *  высота башни теперь АВТОМАТИЧЕСКИ приравнивается к собственной физической высоте
     *  экрана (строк × высоту кабинета), а не оставляется на фиксированном дефолте — башня
     *  строится вплотную под весь экран. Пользователь по-прежнему может переопределить
     *  значение в форме вручную (см. {@link Result#exceedsScreenHeightWarning()} за тем, что
     *  происходит, если он поднимет её ВЫШЕ экрана). {@code type == null} — 0 (нечего
     *  предложить). */
    public static double suggestTowerHeightMm(Screen screen, CabinetType type) {
        return type != null ? screen.getRows() * type.getHeightMm() : 0;
    }

    /** Число сегментов рамы на ПЕРЕДНИЙ ряд башни (Round 5 — задний ряд короче, см.
     *  {@link #suggestBackRowSegments}, у него своя формула) — высота башни ÷ высота одной
     *  рамы, округление вверх. {@code null}/непригодная высота рамы в библиотеке — 0 (нечего
     *  предложить, пользователь вводит вручную). */
    public static int suggestVerticalFramesPerTower(Screen screen, StructureFrameType frameType) {
        if (frameType == null || frameType.getHeightMm() == null || frameType.getHeightMm() <= 0) {
            return 0;
        }
        // Баг-репорт: "при выставленной высоте 2м строится башня 3м высотой... высота башни
        // считается от пола до верхней грани верхней рамы" -- было Math.ceil, что округляет
        // ВВЕРХ и всегда даёт башню ВЫШЕ запрошенной (2000мм при 950мм раме -> 3 сегмента =
        // 2850мм, заметно выше 2м). Собранная высота (N * frameHeightMm) не должна ПРЕВЫШАТЬ
        // запрошенную -- Math.floor вместо ceil, не меньше 1 сегмента даже если рама сама выше
        // запрошенной высоты (тогда башня неизбежно выше, но это уже не ошибка округления).
        return Math.max(1, (int) Math.floor(screen.getStructureTowerHeightMm() / frameType.getHeightMm()));
    }

    /** Число сегментов ЗАДНЕГО ряда башни (row=1) — Round 5, баг-репорт по фото реальной башни:
     *  с фронта видна ОДНА полноразмерная лестничная рама, задний ряд — короткий (~1-2м), опора
     *  для перемычек и выноса, не вторая полноразмерная башня. Формула — сколько сегментов
     *  рамы укладывается в 1.5м (середина диапазона "1-2м"), не меньше 1. */
    public static int suggestBackRowSegments(double frameHeightMm) {
        if (frameHeightMm <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(1500.0 / frameHeightMm));
    }

    /** Интервал перемычек в мм для рамы данной высоты — см. {@link #PEREMYCHKA_INTERVAL_FRAME_MULTIPLE}. */
    public static double peremychkaIntervalMm(double frameHeightMm) {
        return frameHeightMm > 0 ? frameHeightMm * PEREMYCHKA_INTERVAL_FRAME_MULTIPLE : 0;
    }

    /** Число уровней перемычек по высоте башни — один уровень на каждые 1.5 высоты рамы (см.
     *  {@link #peremychkaIntervalMm}), пользователь свободно меняет количество, а конкретную
     *  расстановку — поячеечно в 3D. */
    public static int suggestPeremychkaLevels(double towerHeightMm, double frameHeightMm) {
        double intervalMm = peremychkaIntervalMm(frameHeightMm);
        if (intervalMm <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(towerHeightMm / intervalMm));
    }

    /** Число ДОПОЛНИТЕЛЬНЫХ секций выноса базовой рамы под балласт (сверх обязательной секции
     *  0 — ядра под объёмом башни, см. {@link #CORE_BASE_SECTION_COUNT}), выведенное из
     *  ручного «Выноса» ({@link Screen#getStructureBaseExtensionMm()}). Раньше здесь была
     *  эвристика по высоте/ширине экрана ({@code suggestExtendedBaseSections}, УДАЛЕНА
     *  2026-08-19) — по прямому указанию пользователя: "аппаратно не получается его
     *  просчитывать эффективно, будем указывать руками", инженер вводит вынос в мм напрямую.
     *
     * <p><b>{@code baseExtensionMm} — это ПОЛНАЯ глубина базы, ВКЛЮЧАЯ обязательную часть, не
     * сверх неё (2026-08-19, уточнение пользователя: "значение выноса, которое вбивает
     * пользователь, включает в себя значение основания")</b> — то есть инженер вводит, на
     * сколько мм база должна выступать ОТ ПЕРЕДНЕЙ ГРАНИ башни целиком, а не "сколько ДОПОЛНИТЕЛЬНО
     * добавить к обязательному модулю". Поэтому здесь сначала вычитается ровно 1 обязательный
     * модуль ({@code sectionDepthMm}), и только оставшаяся часть переводится в целое число
     * ДОПОЛНИТЕЛЬНЫХ каталожных секций (округление вверх — на неполный модуль всё равно нужен
     * целый). Если введённого выноса не хватает даже на обязательную часть (после вычитания
     * получился 0 или отрицательное число) — 0 дополнительных секций, обязательная часть всё
     * равно строится сама по себе (см. {@link #CORE_BASE_SECTION_COUNT}), просто вынос сверх
     * неё не запрошен. {@code sectionDepthMm <= 0} — защитный фолбэк 0 (нечего делить). */
    public static int extendedBaseSectionsFromOverhang(double baseExtensionMm, double sectionDepthMm) {
        if (sectionDepthMm <= 0) {
            return 0;
        }
        double beyondMandatoryMm = baseExtensionMm - CORE_BASE_SECTION_COUNT * sectionDepthMm;
        if (beyondMandatoryMm <= 0) {
            return 0;
        }
        return (int) Math.ceil(beyondMandatoryMm / sectionDepthMm);
    }

    /** Итог расчёта количества железа. Все счётчики читают УЖЕ подтверждённые
     *  (возможно, вручную скорректированные пользователем) списки ячеек с {@link Screen} — см.
     *  class-javadoc.
     *
     * <p><b>{@code totalFrameCount} (2026-08-20)</b> — по прямому указанию пользователя: в
     * спецификации нужно выдавать ОДНО общее число рам (вертикальные + перемычки + секции
     * базы), а не три отдельные строки — физически это ОДИН и тот же каталожный тип рамы
     * (Round 16: перемычка/база/усилительные рамы берут габариты из ТОГО ЖЕ {@code
     * structureFrameTypeId}, что и вертикальные), поэтому и заказывать/закупать их нужно
     * ОДНИМ числом. {@code verticalFrameCount}/{@code peremychkaCount}/{@code baseFrameCount}
     * остаются в записи (внутренние тесты и 3D-picking по-прежнему опираются на разбивку по
     * ролям) — просто UI-слой (спецификация в диалоге/XLSX) теперь показывает сумму, а не три
     * строки. */
    public record Result(int verticalFrameCount, int peremychkaCount, int baseFrameCount,
                          int totalFrameCount, int cupCount, int boltCount, double requiredBallastKg,
                          int ballastContainerCount, double totalTowerHeightMm,
                          boolean exceedsSafeHeightWarning, boolean exceedsScreenHeightWarning) {
    }

    private record TowerRow(int tower, int row) {
    }

    public static Result compute(Screen screen, CabinetType type, Workspace workspace) {
        // Счётчики железа — из РЕАЛЬНО существующих (не hidden, см. class-javadoc про Phase 2)
        // ячеек, а не из номинальных чисел сетки: вручную убранный пользователем сегмент/
        // перемычка/секция базы не должен попадать в закупку.
        int verticalFrameCount = (int) screen.getStructureFrameCells().stream().filter(c -> !c.isHidden()).count();
        int peremychkaCount = (int) screen.getStructurePeremychkaCells().stream().filter(c -> !c.isHidden()).count();
        // Round 4: опорная рама больше не вкл/выкл (прямое указание пользователя — "опорные
        // рамы должны быть всегда"), считаем безусловно.
        int baseFrameCount = (int) screen.getStructureBaseFrameCells().stream().filter(c -> !c.isHidden()).count();

        // Стыки вертикальных рам — только РЕАЛЬНО смежные пары (segmentIndex, segmentIndex+1)
        // ОБА присутствующие (не hidden) в ОДНОМ И ТОМ ЖЕ (башня, ряд) — передний и задний ряд
        // считаются независимо, убранный из середины сегмент разрывает стык только в своём
        // ряду, значит и в счётчике стаканов/болтов на него быть не должно. row == 2
        // (усилительные рамы в зоне выноса, см. StructureFrameCell class-javadoc) намеренно
        // ИСКЛЮЧЕНЫ отсюда — там segmentIndex означает НОМЕР СЕКЦИИ ВЫНОСА, а не позицию в
        // стеке, соседние по номеру секции усилительные рамы НЕ стыкуются друг с другом.
        Map<TowerRow, Set<Integer>> segmentsByTowerRow = new HashMap<>();
        for (StructureFrameCell c : screen.getStructureFrameCells()) {
            if (!c.isHidden() && c.getRow() < 2) {
                segmentsByTowerRow.computeIfAbsent(new TowerRow(c.getTowerIndex(), c.getRow()), k -> new HashSet<>())
                        .add(c.getSegmentIndex());
            }
        }
        int verticalJoints = 0;
        for (Set<Integer> segments : segmentsByTowerRow.values()) {
            for (int seg : segments) {
                if (segments.contains(seg + 1)) {
                    verticalJoints++;
                }
            }
        }
        // Round 10 (баг-репорт: "стаканы ставятся сверху рам, в случае если над ней ставится
        // еще одна рама и только тогда") — пользователь сузил правило до единственного случая:
        // стакан существует ТОЛЬКО там, где рама физически стоит СВЕРХУ другой рамы ТОГО ЖЕ
        // типа (вертикальный стек, row 0/1 — verticalJoints выше). Перемычка (Round 8), база и
        // усилительные рамы примыкают сбоку/снизу, а не стоят друг на друге — ни одна из них
        // больше не участвует в cupCount.
        int cupCount = verticalJoints * CUPS_PER_JOINT;

        int totalFrameCount = verticalFrameCount + peremychkaCount + baseFrameCount;
        int boltCount = (int) Math.ceil(totalFrameCount * BOLTS_PER_FRAME);

        // Балласт "≈ вес экрана × коэффициент" (см. class-javadoc) — вместо прежнего ветрового
        // расчёта. Коэффициент теперь редактируемый (Screen.structureBallastRatio, дефолт 0.6),
        // не жёстко зашитое 1:1 — по прямому указанию пользователя ("сейчас он 1:1, в
        // реальности хорошо если 6:10").
        double requiredBallastKg = 0;
        int ballastContainerCount = 0;
        if (type != null) {
            double screenWeightKg = ScreenLogic.stats(screen, type, workspace).totalWeightKg();
            requiredBallastKg = screenWeightKg * screen.getStructureBallastRatio();
            StructureFrameType ballastType = workspace.structureFrameTypeById(screen.getStructureBallastTypeId());
            if (ballastType != null && ballastType.getWeightKg() > 0 && requiredBallastKg > 0) {
                ballastContainerCount = (int) Math.ceil(requiredBallastKg / ballastType.getWeightKg());
            }
        }

        double towerHeightMm = screen.getStructureTowerHeightMm();
        double screenHeightMm = type != null ? screen.getRows() * type.getHeightMm() : 0;

        // Высота башни автоматически предлагается РАВНОЙ высоте экрана (см.
        // suggestTowerHeightMm) — это НОРМАЛЬНЫЙ, ожидаемый случай, не повод для
        // предупреждения (по прямому указанию пользователя, 2026-08-19: "если высота башни ==
        // экрану, то расчёт производим, если выше — выдаём предупреждение"). Раньше граница
        // была {@code >=} (равенство уже считалось нарушением) — теперь строго {@code >}.
        return new Result(verticalFrameCount, peremychkaCount, baseFrameCount, totalFrameCount, cupCount,
                boltCount, requiredBallastKg, ballastContainerCount, towerHeightMm,
                towerHeightMm > MAX_SAFE_TOWER_HEIGHT_MM,
                screenHeightMm > 0 && towerHeightMm > screenHeightMm);
    }
}
