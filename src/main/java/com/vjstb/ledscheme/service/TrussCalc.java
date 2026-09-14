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
 * <p><b>Геометрия влияет на расстановку точек подвеса</b> — {@link #effectiveTrussLengthMm}/
 * {@link #leftOffsetMm} используются также {@link RiggingCalc}, чтобы точки расставлялись
 * от краёв РЕАЛЬНОЙ фермы, а не от краёв экрана (см. class-javadoc {@code RiggingCalc}).
 * Эти геометрические методы сознательно НЕ требуют {@link Workspace} — они работают даже
 * если {@link Screen#getRiggingTrussProfileId()} ещё не выбран (свес/недостача фермы —
 * свойство длины/отступа, не библиотечного профиля). Только {@link #compute} (сам BOM)
 * требует {@link Workspace} для резолва профиля.
 */
public final class TrussCalc {

    /** Соединителей на КАЖДЫЙ стык между соседними сегментами фермы, уложенными в один
     *  прямой пролёт — тот же уровень строгости "недоказанная v1-оценка, не в UI", что у
     *  {@code StructureCalc.CUPS_PER_JOINT}/{@code RiggingCalc.HARDWARE_ALLOWANCE}. */
    public static final int CONNECTORS_PER_JOINT = 2;

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

    /** Свес/недостача фермы относительно ширины экрана, мм — положительно, если ферма
     *  длиннее экрана (нависает), отрицательно, если короче. */
    private static double overhangMm(Screen screen, CabinetType defaultType) {
        return effectiveTrussLengthMm(screen, defaultType) - screenWidthMm(screen, defaultType);
    }

    /** Отступ левого края фермы от левого края экрана, мм — положительно, если ферма
     *  начинается левее края экрана (нависает). {@link Screen#isRiggingTrussSymmetricOffset()}
     *  == true делит {@link #overhangMm} поровну между обеими сторонами; false берёт
     *  {@link Screen#getRiggingTrussManualLeftOffsetMm()}, если он задан, иначе (пока
     *  пользователь не ввёл число вручную) тот же симметричный откат. */
    public static double leftOffsetMm(Screen screen, CabinetType defaultType) {
        double overhang = overhangMm(screen, defaultType);
        if (screen.isRiggingTrussSymmetricOffset()) {
            return overhang / 2.0;
        }
        Double manual = screen.getRiggingTrussManualLeftOffsetMm();
        return manual != null ? manual : overhang / 2.0;
    }

    /** Отступ правого края фермы от правого края экрана, мм — остаток свеса после
     *  {@link #leftOffsetMm}, может быть отрицательным (несимметричный ручной ввод). */
    public static double rightOffsetMm(Screen screen, CabinetType defaultType) {
        return overhangMm(screen, defaultType) - leftOffsetMm(screen, defaultType);
    }

    /** true — ферма короче ширины экрана (физически не перекрывает его целиком) —
     *  показывается пользователю как предупреждение, не блокирует расчёт. */
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
     *  при {@code profileMissing || catalogEmpty}. {@code connectorCount} =
     *  {@code max(0, totalPieceCount-1) * CONNECTORS_PER_JOINT}. */
    public record Result(double targetLengthMm, double leftOffsetMm, double rightOffsetMm,
                          boolean shorterThanScreenWarning, List<CableSpecCalc.Piece> pieces,
                          int totalPieceCount, double totalKitLengthMm, int connectorCount,
                          boolean profileMissing, boolean catalogEmpty) {
    }

    public static Result compute(Screen screen, CabinetType defaultType, Workspace workspace) {
        double targetMm = effectiveTrussLengthMm(screen, defaultType);
        double left = leftOffsetMm(screen, defaultType);
        double right = rightOffsetMm(screen, defaultType);
        boolean shorter = isShorterThanScreen(screen, defaultType);

        String profileId = screen.getRiggingTrussProfileId();
        TrussProfile profile = profileId != null ? workspace.trussProfileById(profileId) : null;
        if (profile == null) {
            return new Result(targetMm, left, right, shorter, null, 0, 0, 0, true, false);
        }

        List<CableSpecCalc.Piece> pieces = minimalKit(targetMm / 1000.0, profile);
        if (pieces == null) {
            return new Result(targetMm, left, right, shorter, null, 0, 0, 0, false, true);
        }

        int totalPieces = pieces.stream().mapToInt(CableSpecCalc.Piece::count).sum();
        double totalKitLengthM = pieces.stream().mapToDouble(p -> p.lengthM() * p.count()).sum();
        int connectors = Math.max(0, totalPieces - 1) * CONNECTORS_PER_JOINT;
        return new Result(targetMm, left, right, shorter, pieces, totalPieces, totalKitLengthM * 1000.0,
                connectors, false, false);
    }
}
