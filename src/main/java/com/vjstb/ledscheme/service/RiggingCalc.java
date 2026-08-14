package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.HoistType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Расчёт распределения веса подвешенного экрана по точкам подвеса — метод
 * грузовых площадей (tributary area): каждая занятая колонка кабинетов отдаёт
 * свой вес БЛИЖАЙШЕЙ по X точке подвеса (точки распределены равномерно вдоль
 * верхней кромки экрана). Консервативная инженерная оценка для v1 — не полный
 * расчёт неразрезной балки (метод трёх моментов, theorem of three moments):
 * при РАВНОМЕРНОЙ нагрузке по ширине даёт тот же результат, при неравномерной
 * (см. пример "ДКФ-уличная сцена" — экран с арками, часть ячеек сетки физически
 * пустая) — консервативно приписывает вес каждой ячейки только ОДНОЙ ближайшей
 * точке, не размазывая его по соседним, как это делает метод трёх моментов.
 *
 * <p><b>Требует независимой инженерной перепроверки перед боевым монтажом</b> —
 * как и остальные калькуляторы проекта (см. class-javadoc VideoTimingCalc), это
 * отправная точка по открытым источникам (PLASA/ESTA-практика 5:1, tributary-
 * area метод распределения), не сертифицированная методика. См. {@code
 * RIGGING_CALC_NOTES.md} в корне репозитория — источники.
 *
 * <p><b>Библиотека подъёмного оборудования</b> (2026-08-11, ранее сознательно
 * отложенная часть плана) — {@link Screen#getRiggingHoistTypeId()} ссылается на
 * общую (синхронизируемую с сервером, вид {@code LibraryItemKind.HOIST})
 * запись {@code HoistType} с паспортной грузоподъёмностью, по образцу
 * {@link Screen#getCabinetTypeId()}. {@link Screen#getRiggingHoistCapacityKg()}
 * остаётся FALLBACK-полем для проектов, сохранённых до появления каталога —
 * см. {@link #effectiveHoistCapacityKg}, которое разрешает эффективное
 * значение (FK побеждает, если задан).
 *
 * <p><b>Коэффициент запаса прочности vs WLL — важное разграничение</b>:
 * {@link Screen#getRiggingSafetyFactorMin()} — это ТРЕБОВАНИЕ к сертификации
 * оборудования (breaking strength ÷ WLL ≥ этот коэффициент), не множитель,
 * применяемый к вычисленной нагрузке. WLL (грузоподъёмность), указанный
 * производителем на сертифицированном подъёмном оборудовании, УЖЕ учитывает
 * этот запас — поэтому проверка превышения здесь сравнивает нагрузку точки
 * НАПРЯМУЮ с {@link Screen#getRiggingHoistCapacityKg()}, без домножения на
 * коэффициент. Спутать эти два понятия — типичная ошибка (либо требовать в
 * 5 раз больше оборудования, чем нужно, либо не спутать вовсе и не проверить
 * ничего) — оставлено явно задокументированным.
 *
 * <p><b>Реактивный пересчёт</b> (правка пользователя к плану, п.7): ничего из
 * расчёта здесь НЕ кэшируется — {@link #compute} читает {@code CabinetType}
 * заново при каждом вызове, поэтому правка веса типа кабинета (или удаление
 * типа) автоматически отражается на следующий показ панели прерига, без
 * отдельного механизма "пометить устаревшим". Персистентно на {@link Screen}
 * хранится только количество точек ({@link Screen#getRiggingPointsCount()}),
 * которое (2026-08-11, баг-репорт «при перерасчёте количество лебёдок не
 * меняется») {@link #suggestPointCount} тоже пересчитывает заново при каждом
 * вызове «Рассчитать точки подвеса», а не только при изменении
 * {@link Screen#getCols()} — см. javadoc {@link #suggestPointCount}.
 */
public final class RiggingCalc {

    private RiggingCalc() {
    }

    /** Наценка на крепёж/кабели/сами лебёдки сверх чистого веса кабинетов —
     *  типовая отраслевая практика 15–25%, берём среднее. */
    public static final double HARDWARE_ALLOWANCE = 0.20;

    /** Вес одной занятой колонки сетки экрана (суммарно по всем строкам) и её
     *  X-координата центра (мм от левого края номинальной сетки экрана). */
    public record ColumnWeight(double xMm, double weightKg) {
    }

    /** Нагрузка одной точки подвеса: {@code index} — слева направо, {@code xMm} —
     *  позиция вдоль ширины экрана, {@code loadKg} — вес (с наценкой на крепёж),
     *  который эта точка несёт, {@code overCapacity} — true, если задана
     *  {@link Screen#getRiggingHoistCapacityKg()} и {@code loadKg} её превышает. */
    public record PointLoad(int index, double xMm, double loadKg, boolean overCapacity) {
    }

    /** Итог расчёта по экрану целиком. */
    public record Result(double totalCabinetWeightKg, double totalWeightWithHardwareKg,
                          double requiredWllPerPointKg, List<PointLoad> points) {
    }

    /** Минимум точек подвеса по ширине экрана (модули/2, историческая формула,
     *  см. {@link #suggestPointCount}) — независимо от веса/грузоподъёмности,
     *  это ГЕОМЕТРИЧЕСКИЙ минимум (равномерность провеса фермы), а не то, что
     *  вообще ограничивает нагрузку на точку. */
    private static int baseColumnPointCount(Screen screen) {
        return Math.max(2, (int) Math.ceil(screen.getCols() / 2.0));
    }

    /** Число точек подвеса — минимум по ширине экрана ({@link #baseColumnPointCount}),
     *  УВЕЛИЧЕННЫЙ (если задана {@link #effectiveHoistCapacityKg}), пока нагрузка ни
     *  одной точки не превышает грузоподъёмность выбранного оборудования — то есть
     *  перерасчёт реально реагирует на изменение веса/WLL, а не только на ширину
     *  экрана (баг-репорт: "при перерасчёте количество лебёдок не меняется", хотя
     *  меняется вес/лебёдка). НЕ делит суммарный вес поровну на число точек — метод
     *  трибьютарных площадей ({@link #compute}) даёт НЕравномерное распределение
     *  (тяжёлая колонка целиком уходит одной точке, см. class-javadoc), поэтому на
     *  каждой пробной точке количество проверяется РЕАЛЬНЫМ распределением через
     *  {@link #compute}, а не наивным {@code totalWeight/n} (см. источники:
     *  "Multi-Point Rigging — It's not so simple", RIGGING_CALC_NOTES.md).
     *  Перебор ограничен числом занятых колонок экрана — в этой модели вес колонки
     *  никогда не делится между несколькими точками, поэтому больше точек, чем
     *  колонок, никак не может дополнительно снизить максимальную нагрузку точки;
     *  если даже при таком пределе есть превышение — оборудование физически не
     *  подходит для этого экрана НИ ПРИ КАКОМ числе точек, дальше наращивать
     *  бессмысленно (возвращается предел, а превышение по-прежнему видно в {@link
     *  PointLoad#overCapacity} для каждой точки на экране прерига). */
    public static int suggestPointCount(Screen screen, CabinetType defaultType, Workspace workspace) {
        int base = baseColumnPointCount(screen);
        Double capacity = effectiveHoistCapacityKg(screen, workspace);
        if (capacity == null || capacity <= 0) {
            return base;
        }
        int maxPoints = Math.max(base, screen.getCols());
        for (int n = base; n <= maxPoints; n++) {
            Result r = compute(screen, defaultType, workspace, n);
            boolean anyOver = r.points().stream().anyMatch(PointLoad::overCapacity);
            if (!anyOver) {
                return n;
            }
        }
        return maxPoints;
    }

    /** Эффективная грузоподъёмность (WLL, кг) для проверки превышения —
     *  {@link Screen#getRiggingHoistTypeId()} побеждает, если задан и запись ещё
     *  существует в библиотеке (общей или личной); иначе {@link
     *  Screen#getRiggingHoistCapacityKg()} (ручной ввод/fallback для старых
     *  проектов); {@code null}, если ни то ни другое не задано. */
    public static Double effectiveHoistCapacityKg(Screen screen, Workspace workspace) {
        String hoistTypeId = screen.getRiggingHoistTypeId();
        if (hoistTypeId != null) {
            HoistType hoist = workspace.hoistTypeById(hoistTypeId);
            if (hoist != null) {
                return hoist.getWllKg();
            }
        }
        return screen.getRiggingHoistCapacityKg();
    }

    /** Вес каждой занятой колонки экрана — учитывает ТОЛЬКО видимые
     *  ({@code !isHidden()}) кабинеты, поэтому вырезанные ячейки (экран с
     *  арками и подобные неровные формы) физически не добавляют вес и не
     *  смещают распределение к пустому месту. */
    public static List<ColumnWeight> columnWeights(Screen screen, CabinetType defaultType, Workspace workspace) {
        double cellW = defaultType != null ? defaultType.getWidthMm() : 0;
        TreeMap<Integer, Double> byCol = new TreeMap<>();
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.isHidden()) {
                continue;
            }
            CabinetType eff = ScreenLogic.effectiveType(c, defaultType, workspace);
            byCol.merge(c.getColIndex(), eff != null ? eff.getWeightKg() : 0, Double::sum);
        }
        List<ColumnWeight> result = new ArrayList<>();
        for (var e : byCol.entrySet()) {
            double xCenter = e.getKey() * cellW + cellW / 2.0;
            result.add(new ColumnWeight(xCenter, e.getValue()));
        }
        return result;
    }

    /** Распределяет суммарный вес занятых колонок (+ наценка на крепёж, см.
     *  {@link #HARDWARE_ALLOWANCE}) по {@code pointCount} точкам, расставленным
     *  равномерно вдоль номинальной ширины экрана — методом грузовых площадей
     *  (каждая колонка отдаёт вес ближайшей по X точке). */
    public static Result compute(Screen screen, CabinetType defaultType, Workspace workspace, int pointCount) {
        List<ColumnWeight> columns = columnWeights(screen, defaultType, workspace);
        double totalCabinetWeight = 0;
        for (ColumnWeight c : columns) {
            totalCabinetWeight += c.weightKg();
        }
        double hardwareFactor = 1 + HARDWARE_ALLOWANCE;
        double totalWithHardware = totalCabinetWeight * hardwareFactor;

        double cellW = defaultType != null ? defaultType.getWidthMm() : 0;
        double widthMm = screen.getCols() * cellW;
        int n = Math.max(1, pointCount);
        double[] pointX = new double[n];
        for (int i = 0; i < n; i++) {
            pointX[i] = n == 1 ? widthMm / 2.0 : widthMm * i / (n - 1.0);
        }
        double[] pointWeight = new double[n];
        for (ColumnWeight c : columns) {
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double d = Math.abs(c.xMm() - pointX[i]);
                if (d < best) {
                    best = d;
                    nearest = i;
                }
            }
            pointWeight[nearest] += c.weightKg() * hardwareFactor;
        }

        Double capacity = effectiveHoistCapacityKg(screen, workspace);
        List<PointLoad> points = new ArrayList<>();
        double maxLoad = 0;
        for (int i = 0; i < n; i++) {
            boolean over = capacity != null && pointWeight[i] > capacity;
            points.add(new PointLoad(i, pointX[i], pointWeight[i], over));
            maxLoad = Math.max(maxLoad, pointWeight[i]);
        }
        return new Result(totalCabinetWeight, totalWithHardware, maxLoad, points);
    }
}
