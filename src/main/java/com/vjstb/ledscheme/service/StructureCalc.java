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
 * com.vjstb.ledscheme.model.ScreenMountType#STRUCTURE} — bill of materials (рамы/короткие
 * рамы/стаканы/болты/балласт), а НЕ распределение нагрузки по точкам, как у
 * {@link RiggingCalc} для RIGGED.
 *
 * <p><b>Источники данных — важное разграничение</b>: точное соотношение стаканов и болтов
 * на стык/раму задано пользователем НАПРЯМУЮ по его реальному оборудованию (2 стакана на
 * каждый стык рам, болты = число рам × 1.5) — это НЕ догадка.
 *
 * <p><b>Phase 2.1 — объёмная башня (2026-08-14)</b>: башня строится из ДВУХ рядов
 * вертикальных рам (передний — у экрана, задний — на глубину одной короткой рамы позади,
 * см. {@link StructureFrameCell#getRow()}), соединённых горизонтальными перемычками
 * (передний↔задний, ВНУТРИ одной башни — НЕ между соседними башнями, эта роль у
 * горизонтальных рам была в исходной версии Phase 2 и упразднена, см. историю
 * {@code StructureBraceCell} в git) каждые ~1.5м высоты (интервал — глобальная настройка
 * Персонализации), плюс базовая рама, которая может выносится назад в дополнительных
 * секциях (каждая — ширина короткой рамы) под балласт. Числа
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
 * <p><b>Балласт — правило "вес ≈ вес экрана", не ветровой расчёт</b>: прежняя версия считала
 * требуемый балласт от давления ветра (динамическое давление × Cd × площадь → опрокидывающий
 * момент → footprint) — по прямому указанию пользователя эта физика убрана целиком как
 * избыточная для этого инструмента. Новое правило — простое эмпирическое: суммарный вес
 * балласта должен примерно равняться весу отгружаемого экрана (простой противовес/рычаг,
 * без запаса — see {@link #compute}). Это ТОЖЕ грубая оценка, не заменяет инженерный расчёт
 * перед монтажом.
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

    /** Число башен по ширине экрана — расставлены "как заборные столбы" с шагом
     *  {@link Screen#getStructureTowerSpacingMm()}: N интервалов требуют N+1 башен,
     *  не меньше 2. */
    public static int suggestTowerCount(Screen screen, CabinetType type) {
        double widthMm = screen.getCols() * (type != null ? type.getWidthMm() : 0);
        double spacing = screen.getStructureTowerSpacingMm();
        if (widthMm <= 0 || spacing <= 0) {
            return 2;
        }
        return Math.max(2, (int) Math.ceil(widthMm / spacing) + 1);
    }

    /** Число сегментов рамы на ПЕРЕДНИЙ ряд башни (Round 5 — задний ряд короче, см.
     *  {@link #suggestBackRowSegments}, у него своя формула) — высота башни ÷ высота одной
     *  рамы, округление вверх. {@code null}/непригодная высота рамы в библиотеке — 0 (нечего
     *  предложить, пользователь вводит вручную). */
    public static int suggestVerticalFramesPerTower(Screen screen, StructureFrameType frameType) {
        if (frameType == null || frameType.getHeightMm() == null || frameType.getHeightMm() <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(screen.getStructureTowerHeightMm() / frameType.getHeightMm()));
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
     *  0 — ядра под объёмом башни) — грубая эвристика по прямому указанию пользователя: для
     *  экрана высотой примерно до 3м хватает одной секции выноса, для более высоких (от 3.5м)
     *  или очень широких экранов вынос делают больше (площадь опоры/рычаг нужны больше) — ЭТО
     *  ТОЛЬКО СТАРТОВАЯ ОЦЕНКА, требующая инженерного суждения на месте, не точная формула, как
     *  и остальные {@code suggestXxx} в этом классе. */
    public static int suggestExtendedBaseSections(double screenHeightMm, double screenWidthMm) {
        int sections = 1;
        if (screenHeightMm >= 4500) {
            sections = 3;
        } else if (screenHeightMm >= 3500) {
            sections = 2;
        }
        if (screenWidthMm >= 8000) {
            sections++;
        }
        return sections;
    }

    /** Итог расчёта количества железа. Все счётчики читают УЖЕ подтверждённые
     *  (возможно, вручную скорректированные пользователем) списки ячеек с {@link Screen} — см.
     *  class-javadoc. */
    public record Result(int verticalFrameCount, int peremychkaCount, int baseFrameCount,
                          int cupCount, int boltCount, double requiredBallastKg,
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
        // Перемычки/базовые секции/усилительные рамы выноса — та же связь "1 существующий
        // элемент = 1 стык" (усилительная рама не стоит в стеке, стыкуется только с базовой
        // секцией под ней).
        int peremychkaJoints = peremychkaCount;
        int baseJoints = baseFrameCount;
        int reinforcementCount = (int) screen.getStructureFrameCells().stream()
                .filter(c -> !c.isHidden() && c.getRow() == 2).count();
        int cupCount = (verticalJoints + peremychkaJoints + baseJoints + reinforcementCount) * CUPS_PER_JOINT;

        int totalFrameCount = verticalFrameCount + peremychkaCount + baseFrameCount;
        int boltCount = (int) Math.ceil(totalFrameCount * BOLTS_PER_FRAME);

        // Балласт "≈ вес экрана" (см. class-javadoc) — вместо прежнего ветрового расчёта.
        double requiredBallastKg = 0;
        int ballastContainerCount = 0;
        if (type != null) {
            requiredBallastKg = ScreenLogic.stats(screen, type, workspace).totalWeightKg();
            StructureFrameType ballastType = workspace.structureFrameTypeById(screen.getStructureBallastTypeId());
            if (ballastType != null && ballastType.getWeightKg() > 0 && requiredBallastKg > 0) {
                ballastContainerCount = (int) Math.ceil(requiredBallastKg / ballastType.getWeightKg());
            }
        }

        double towerHeightMm = screen.getStructureTowerHeightMm();
        double screenHeightMm = type != null ? screen.getRows() * type.getHeightMm() : 0;

        return new Result(verticalFrameCount, peremychkaCount, baseFrameCount, cupCount, boltCount,
                requiredBallastKg, ballastContainerCount, towerHeightMm,
                towerHeightMm > MAX_SAFE_TOWER_HEIGHT_MM,
                screenHeightMm > 0 && towerHeightMm >= screenHeightMm);
    }
}
