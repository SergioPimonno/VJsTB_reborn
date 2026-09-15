package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.TrussProfile;
import com.vjstb.ledscheme.model.Workspace;
import java.util.List;

/**
 * Расчёт фермы подвеса — по аналогии с {@code service.StructureCalc} (наземный
 * конструктив): целевая длина фермы по умолчанию равна физической ширине экрана, но
 * может быть переопределена пользователем ({@link Screen#getRiggingTrussLengthMm()}),
 * а BOM (комплект сегментов) подбирается минимальным по количеству кусков набором длин
 * из библиотечного {@link TrussProfile} (см. {@link MinimalKitCalc} — тот же алгоритм,
 * что сплайсовка кабеля в {@link CableSpecCalc}, без запаса на округление).
 *
 * <p><b>Геометрия влияет на расстановку точек подвеса</b> — {@link #leftOffsetMm(Screen,
 * CabinetType, Workspace)}/{@link #rightOffsetMm(Screen, CabinetType, Workspace)} используются
 * также {@link RiggingCalc}, чтобы точки расставлялись от краёв РЕАЛЬНОЙ фермы, а не от краёв
 * экрана (см. class-javadoc {@code RiggingCalc}). Два набора методов: {@link
 * #effectiveTrussLengthMm}/{@link #leftOffsetMm(Screen, CabinetType)}/{@link
 * #rightOffsetMm(Screen, CabinetType)} — по ЦЕЛЕВОЙ длине, сознательно НЕ требуют {@link
 * Workspace} (работают даже без выбранного {@link Screen#getRiggingTrussProfileId()}); {@link
 * #builtTrussLengthMm}/{@link #leftOffsetMm(Screen, CabinetType, Workspace)}/{@link
 * #rightOffsetMm(Screen, CabinetType, Workspace)} — по РЕАЛЬНОЙ длине набранного комплекта
 * сегментов (см. {@link #builtTrussLengthMm} — баг-репорт 2026-09-15, зачем это два разных
 * набора и почему совпадение целевой и реальной длины не гарантировано). {@link #compute}
 * использует второй набор внутри себя, когда комплект удаётся собрать.
 */
public final class TrussCalc {

    /** Крепёж на КАЖДЫЙ стык между соседними сегментами фермы, уложенными в один прямой
     *  пролёт — коробчатая ферма стыкуется по 4 углам, на каждый угол приходится одна
     *  бобышка + один палец + одна шпилька, итого по 4 штуки каждого вида на стык. Тот
     *  же уровень строгости "недоказанная v1-оценка, не в UI", что у {@code
     *  StructureCalc.CUPS_PER_JOINT}/{@code RiggingCalc.HARDWARE_ALLOWANCE}.
     *
     * <p>Бобышки (конусные полумуфты) в стык устанавливаются заводом сразу в торец
     *  сегмента при производстве — приезжают уже накрученными НА самой ферме, отдельной
     *  строкой закупки не являются (см. {@link #SPIGOTS_PER_JOINT} — считается только
     *  для информации в спецификации, "чем стык уже укомплектован", а не для докупки).
     *  Пальцы (конические соединительные пальцы, вставляются в стык бобышек) и шпильки
     *  (пружинные R-клипсы, фиксируют палец от выпадения) — расходники, на монтаже
     *  теряются/остаются на площадке и добираются отдельно на каждый выезд ({@link
     *  #PINS_PER_JOINT}/{@link #CLIPS_PER_JOINT}) — баг-репорт 2026-09-15: спецификация
     *  считала один неразличимый "соединитель" на стык, из-за чего в закупку не попадали
     *  ни пальцы, ни шпильки в нужном количестве. */
    public static final int SPIGOTS_PER_JOINT = 4;
    public static final int PINS_PER_JOINT = 4;
    public static final int CLIPS_PER_JOINT = 4;

    private TrussCalc() {
    }

    /** Физическая ширина экрана, мм — {@code cols * defaultType.getWidthMm()};
     *  {@code defaultType == null} (тип кабинета неизвестен) -> 0. */
    public static double screenWidthMm(Screen screen, CabinetType defaultType) {
        double cellW = defaultType != null ? defaultType.getWidthMm() : 0;
        return screen.getCols() * cellW;
    }

    /** Целевая длина фермы по умолчанию — РЕАЛЬНАЯ физическая ширина экрана: ферма
     *  должна физически перекрывать весь экран. */
    public static double suggestTrussLengthMm(Screen screen, CabinetType defaultType) {
        return screenWidthMm(screen, defaultType);
    }

    /** {@link Screen#getRiggingTrussLengthMm()} (если задан и положителен) побеждает,
     *  иначе {@link #suggestTrussLengthMm}. */
    public static double effectiveTrussLengthMm(Screen screen, CabinetType defaultType) {
        Double override = screen.getRiggingTrussLengthMm();
        if (override != null && override > 0) {
            return override;
        }
        return suggestTrussLengthMm(screen, defaultType);
    }

    /** Свес/недостача фермы относительно ширины экрана, мм, для ЯВНО заданной физической
     *  длины фермы {@code actualLengthMm} — положительно, если ферма длиннее экрана
     *  (нависает), отрицательно, если короче. Приватный общий хелпер для {@link
     *  #leftOffsetMm(Screen, CabinetType)}/{@link #leftOffsetMm(Screen, CabinetType, Workspace)}
     *  и их пар — см. class-javadoc про {@link #builtTrussLengthMm}, зачем вообще два набора. */
    private static double overhangFor(Screen screen, CabinetType defaultType, double actualLengthMm) {
        return actualLengthMm - screenWidthMm(screen, defaultType);
    }

    private static double leftOffsetFor(Screen screen, CabinetType defaultType, double actualLengthMm) {
        double overhang = overhangFor(screen, defaultType, actualLengthMm);
        if (screen.isRiggingTrussSymmetricOffset()) {
            return overhang / 2.0;
        }
        Double manual = screen.getRiggingTrussManualLeftOffsetMm();
        return manual != null ? manual : overhang / 2.0;
    }

    private static double rightOffsetFor(Screen screen, CabinetType defaultType, double actualLengthMm) {
        return overhangFor(screen, defaultType, actualLengthMm) - leftOffsetFor(screen, defaultType, actualLengthMm);
    }

    /** Отступ левого края фермы от левого края экрана, мм — по ЦЕЛЕВОЙ длине ({@link
     *  #effectiveTrussLengthMm}), без учёта реального комплекта сегментов (без {@link
     *  Workspace} резолвить профиль/BOM нечем) — положительно, если ферма начинается
     *  левее края экрана. {@link Screen#isRiggingTrussSymmetricOffset()} == true делит
     *  свес поровну между обеими сторонами; false берёт {@link
     *  Screen#getRiggingTrussManualLeftOffsetMm()}, если он задан, иначе (пока пользователь
     *  не ввёл число вручную) тот же симметричный откат. Если профиль выбран и BOM
     *  считается — используйте {@link #leftOffsetMm(Screen, CabinetType, Workspace)} вместо
     *  этого метода (см. её javadoc, баг-репорт 2026-09-15). */
    public static double leftOffsetMm(Screen screen, CabinetType defaultType) {
        return leftOffsetFor(screen, defaultType, effectiveTrussLengthMm(screen, defaultType));
    }

    /** Отступ правого края фермы от правого края экрана, мм — остаток свеса после
     *  {@link #leftOffsetMm(Screen, CabinetType)}, может быть отрицательным (несимметричный
     *  ручной ввод). См. {@link #rightOffsetMm(Screen, CabinetType, Workspace)} за версией
     *  по реальной длине комплекта. */
    public static double rightOffsetMm(Screen screen, CabinetType defaultType) {
        return rightOffsetFor(screen, defaultType, effectiveTrussLengthMm(screen, defaultType));
    }

    /** Отступы по РЕАЛЬНОЙ физической длине фермы ({@link #builtTrussLengthMm}) — версии
     *  {@link #leftOffsetMm(Screen, CabinetType)}/{@link #rightOffsetMm(Screen, CabinetType)},
     *  требующие {@link Workspace} для резолва профиля и подбора комплекта. Используются
     *  {@link #compute} (чтобы нарисованная ферма и её геометрия совпадали) и {@link
     *  RiggingCalc} (чтобы точки подвеса расставлялись по РЕАЛЬНОЙ длине, не абстрактной
     *  цели) — баг-репорт 2026-09-15: см. class-javadoc {@link #builtTrussLengthMm}. */
    public static double leftOffsetMm(Screen screen, CabinetType defaultType, Workspace workspace) {
        return leftOffsetFor(screen, defaultType, builtTrussLengthMm(screen, defaultType, workspace));
    }

    public static double rightOffsetMm(Screen screen, CabinetType defaultType, Workspace workspace) {
        return rightOffsetFor(screen, defaultType, builtTrussLengthMm(screen, defaultType, workspace));
    }

    /** true — ферма короче ширины экрана (физически не перекрывает его целиком) —
     *  показывается пользователю как предупреждение, не блокирует расчёт. Сравнивает
     *  ЦЕЛЕВУЮ длину ({@link #effectiveTrussLengthMm}) — это предупреждение осмысленно и
     *  ДО выбора профиля/BOM (пользователь явно ввёл длину короче экрана). */
    public static boolean isShorterThanScreen(Screen screen, CabinetType defaultType) {
        return effectiveTrussLengthMm(screen, defaultType) < screenWidthMm(screen, defaultType);
    }

    /** Минимальный по СУММАРНОЙ ДЛИНЕ (не по числу кусков — см. {@link
     *  MinimalKitCalc#solveMinimizingOverage} class-javadoc про баг-репорт 2026-09-14)
     *  комплект сегментов из {@code profile} на целевую длину (метры), без запаса на
     *  округление (в отличие от {@link CableSpecCalc#minimalKit}, для фермы целевая длина
     *  уже точна) — ферма должна быть длиной "точно такая же или чуть больше", не первый
     *  попавшийся одиночный кусок с большим излишком. */
    public static List<CableSpecCalc.Piece> minimalKit(double targetLengthM, TrussProfile profile) {
        return MinimalKitCalc.solveMinimizingOverage(targetLengthM, profile.getAvailableLengthsM());
    }

    /** Реальная физическая длина фермы, мм: если профиль выбран и по нему удаётся подобрать
     *  комплект (см. {@link #compute}) — это ФАКТИЧЕСКАЯ суммарная длина набранных сегментов,
     *  которая почти всегда чуть БОЛЬШЕ целевой ({@link #effectiveTrussLengthMm}) — доступные
     *  в библиотеке длины редко делят целевую без остатка (см. {@link MinimalKitCalc}). Без
     *  профиля/пустого каталога — сама целевая длина (прежнее поведение, было единственным
     *  источником геометрии до этой правки).
     *
     * <p>Баг-репорт 2026-09-15: «сборная длина фермы превышает длину экрана, а выступ при
     *  включённой галочке равномерного отступа асимметричен» + «расчёт подвеса и нагрузки на
     *  точки должен идти по длине фермы, не экрана». Причина — {@link #leftOffsetMm(Screen,
     *  CabinetType)}/{@link RiggingCalc#compute} до этой правки делили СВЕС от ЦЕЛЕВОЙ длины
     *  (обычно ровно равной ширине экрана, свес=0), а {@code SceneCanvasPanel.drawRiggingTruss}
     *  рисует сегменты РЕАЛЬНОЙ длины подряд от {@code cursorMm=0} БЕЗ обрезки по цели (по
     *  прямому запросу пользователя из более раннего баг-репорта, см. её javadoc) — весь
     *  излишек между целевой и реально набранной длиной визуально уезжал целиком вправо,
     *  свес читался нулевым слева и полным справа даже при включённом симметричном отступе.
     *  Теперь свес/отступы и расстановка точек подвеса считаются от ЭТОЙ (реальной) длины —
     *  см. {@link #leftOffsetMm(Screen, CabinetType, Workspace)}. */
    public static double builtTrussLengthMm(Screen screen, CabinetType defaultType, Workspace workspace) {
        double targetMm = effectiveTrussLengthMm(screen, defaultType);
        String profileId = screen.getRiggingTrussProfileId();
        TrussProfile profile = profileId != null ? workspace.trussProfileById(profileId) : null;
        if (profile == null) {
            return targetMm;
        }
        List<CableSpecCalc.Piece> pieces = minimalKit(targetMm / 1000.0, profile);
        if (pieces == null) {
            return targetMm;
        }
        double totalM = pieces.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        return totalM * 1000.0;
    }

    /** Итог расчёта фермы по экрану — геометрические поля ({@code targetLengthMm}/
     *  {@code leftOffsetMm}/{@code rightOffsetMm}/{@code shorterThanScreenWarning})
     *  заполнены ВСЕГДА (нужны {@link RiggingCalc} независимо от того, выбран ли
     *  профиль). {@code profileMissing} — {@link Screen#getRiggingTrussProfileId()}
     *  {@code == null} либо ссылается на удалённую запись (висячий FK тихо трактуется
     *  как "не выбрано", тот же принцип, что у {@code RiggingCalc#effectiveHoistCapacityKg}
     *  про {@code riggingHoistTypeId}, но здесь нет числового fallback — просто нет BOM).
     *  {@code catalogEmpty} — профиль есть, но {@code availableLengthsM} пуст/без
     *  положительных длин ({@link MinimalKitCalc#solve} вернул {@code null}).
     *  {@code pieces}/{@code totalPieceCount}/{@code totalKitLengthMm} — {@code null}/0/0
     *  при {@code profileMissing || catalogEmpty}. {@code jointCount} =
     *  {@code max(0, totalPieceCount-1)}; {@code spigotCount}/{@code pinCount}/
     *  {@code clipCount} = {@code jointCount * SPIGOTS_PER_JOINT}/{@code PINS_PER_JOINT}/
     *  {@code CLIPS_PER_JOINT} — см. их javadoc про то, что бобышки уже в комплекте
     *  фермы, а пальцы/шпильки — отдельная закупка.
     *
     * <p>{@code leftOffsetMm}/{@code rightOffsetMm} считаются от {@code totalKitLengthMm}
     *  (РЕАЛЬНОЙ длины комплекта), когда он есть, а не от {@code targetLengthMm} — см.
     *  javadoc {@link #builtTrussLengthMm} про баг-репорт 2026-09-15. При
     *  {@code profileMissing || catalogEmpty} комплекта нет, поэтому это по-прежнему
     *  отступы от целевой длины (совпадает с {@link #leftOffsetMm(Screen, CabinetType)}). */
    public record Result(double targetLengthMm, double leftOffsetMm, double rightOffsetMm,
                          boolean shorterThanScreenWarning, List<CableSpecCalc.Piece> pieces,
                          int totalPieceCount, double totalKitLengthMm, int jointCount,
                          int spigotCount, int pinCount, int clipCount,
                          boolean profileMissing, boolean catalogEmpty) {
    }

    public static Result compute(Screen screen, CabinetType defaultType, Workspace workspace) {
        double targetMm = effectiveTrussLengthMm(screen, defaultType);
        boolean shorter = isShorterThanScreen(screen, defaultType);

        String profileId = screen.getRiggingTrussProfileId();
        TrussProfile profile = profileId != null ? workspace.trussProfileById(profileId) : null;
        if (profile == null) {
            double left = leftOffsetFor(screen, defaultType, targetMm);
            double right = rightOffsetFor(screen, defaultType, targetMm);
            return new Result(targetMm, left, right, shorter, null, 0, 0, 0, 0, 0, 0, true, false);
        }

        List<CableSpecCalc.Piece> pieces = minimalKit(targetMm / 1000.0, profile);
        if (pieces == null) {
            double left = leftOffsetFor(screen, defaultType, targetMm);
            double right = rightOffsetFor(screen, defaultType, targetMm);
            return new Result(targetMm, left, right, shorter, null, 0, 0, 0, 0, 0, 0, false, true);
        }

        int totalPieces = pieces.stream().mapToInt(CableSpecCalc.Piece::count).sum();
        double totalKitLengthM = pieces.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        double builtLengthMm = totalKitLengthM * 1000.0;
        double left = leftOffsetFor(screen, defaultType, builtLengthMm);
        double right = rightOffsetFor(screen, defaultType, builtLengthMm);
        int joints = Math.max(0, totalPieces - 1);
        int spigots = joints * SPIGOTS_PER_JOINT;
        int pins = joints * PINS_PER_JOINT;
        int clips = joints * CLIPS_PER_JOINT;
        return new Result(targetMm, left, right, shorter, pieces, totalPieces, builtLengthMm,
                joints, spigots, pins, clips, false, false);
    }
}
