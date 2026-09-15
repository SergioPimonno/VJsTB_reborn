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
 * {@link Screen#getCols()} — см. javadoc {@link #suggestPointCount}. Это же
 * свойство (пересчёт стартует с нуля, ничего от предыдущего результата не
 * наследует) означает, что повторный расчёт всегда ищет ЗАНОВО минимально
 * достаточное число точек под текущий вес/лебёдку — если пользователь
 * заменил тяжёлые кабинеты на лёгкие или выбрал более мощную лебёдку, число
 * точек при повторном нажатии «Рассчитать» может и УМЕНЬШИТЬСЯ, не только
 * вырасти (см. также {@link #baseColumnPointCount} ниже, 2026-08-19 —
 * геометрический минимум больше не завышает предложение искусственно).
 *
 * <p><b>Геометрический минимум — по физической ширине, не по числу модулей
 * (2026-08-19, баг-репорт)</b>: до этой правки {@link #baseColumnPointCount}
 * считал {@code ceil(cols/2)} — минимум точек РОС вместе с числом занятых
 * колонок сетки, независимо от их физического размера. Для мелкомодульных
 * экранов (пример из баг-репорта — 17×15 кабинетов Dicolor 500×500мм, физическая
 * ширина 8.5м) это давало абсурдно завышенный результат: 9 точек подвеса, хотя
 * реальная нагрузка (255 кабинетов × 7.2кг × 1.2 наценки ≈ 2.2т) укладывается
 * всего в 4 лебёдки по 1т с большим запасом. Формула была не более чем эвристикой
 * "точка на каждые 2 модуля", случайно приемлемой для крупномодульных уличных
 * экранов (где 2 модуля — уже несколько метров), но абсурдной для мелкого шага.
 * Заменена на {@link #MAX_SPAN_MM} — минимум точек теперь считается от РЕАЛЬНОЙ
 * ширины экрана в мм, не от числа модулей, и итоговое число точек — это max
 * (геометрический минимум по пролёту, минимум по грузоподъёмности) — то есть
 * калькулятор теперь активно СТАРАЕТСЯ МИНИМИЗИРОВАТЬ число лебёдок, беря ровно
 * столько, сколько требуют оба ограничения, а не число модулей в сетке.
 *
 * <p><b>Точки расставляются от краёв ФЕРМЫ, не экрана (см. {@link TrussCalc})</b> —
 * ширина экрана {@code widthMm}, использовавшаяся выше и в {@link #EDGE_MARGIN_MM},
 * заменена на {@link TrussCalc#builtTrussLengthMm} — РЕАЛЬНУЮ физическую длину
 * фермы (набранный комплект сегментов, когда профиль выбран; иначе целевая длина,
 * по умолчанию равная ширине экрана — обратная совместимость сохраняется
 * КОНСТРУКТИВНО). Не {@code effectiveTrussLengthMm} (абстрактная цель) — баг-репорт
 * 2026-09-15: набранная из целых сегментов ферма почти всегда чуть длиннее цели, и
 * расстановка/нагрузка точек должны идти по тому, что реально смонтировано, иначе
 * визуально нарисованная ферма (см. {@code SceneCanvasPanel.drawRiggingTruss}, которая
 * рисует РЕАЛЬНОЙ длиной кусков) расходится с точками, расставленными по цели. Точки
 * сначала расставляются в координатах, локальных для ФЕРМЫ (0 — левый край фермы),
 * затем переводятся в координаты ЭКРАНА вычитанием {@link
 * TrussCalc#leftOffsetMm(Screen, CabinetType, Workspace)} — так распределение нагрузки
 * по ближайшей колонке ({@link #nearestPointIndex}, координаты колонок всегда
 * экранные) остаётся корректным независимо от того, нависает ли ферма за края экрана
 * или короче него. {@link PointLoad#xMm()} после этого — координата ЭКРАНА и может
 * стать отрицательной или больше ширины экрана при свесе фермы — это ожидаемо, не
 * ошибка.
 */
public final class RiggingCalc {

    private RiggingCalc() {
    }

    /** Наценка на крепёж/кабели/сами лебёдки сверх чистого веса кабинетов —
     *  типовая отраслевая практика 15–25%, берём среднее. */
    public static final double HARDWARE_ALLOWANCE = 0.20;

    /** Максимальный пролёт (мм) между соседними точками подвеса, взятый как
     *  геометрический ориентир для контроля прогиба стандартной прутковой
     *  фермы (типовая практика источников — единицы метров, не привязана к
     *  размеру/числу модулей экрана) — см. class-javadoc, секция про
     *  баг-репорт 2026-08-19. Как и {@link #HARDWARE_ALLOWANCE}, это
     *  недокументированная нормативно оценка для v1, не экспонируется в UI. */
    public static final double MAX_SPAN_MM = 3000.0;

    /** Стандартный отступ (мм) от самого края фермы, где точки подвеса не
     *  ставят (типовая практика — на самом краю фермы нет запаса на
     *  монтажную оснастку/угол стропа, да и сама фёрма обычно короче номинала
     *  экрана за счёт технологического свеса) — 2026-08-19, баг-репорт
     *  «расчёт отступа лебёдок в экспортируемой таблице всегда начинается с
     *  нуля, а это не так». Применяется СИММЕТРИЧНО с обеих сторон, только
     *  если после отступа остаётся хоть какая-то полезная ширина ({@link
     *  #usableWidthMm}) — иначе (экран у́же 2×отступа) отступ игнорируется
     *  целиком, а не наполовину, чтобы не сталкивать точки друг с другом или
     *  за пределы экрана. */
    public static final double EDGE_MARGIN_MM = 500.0;

    /** Допуск (мм) для распознавания ТОЧНОЙ ничьей между двумя точками при поиске
     *  ближайшей к колонке (см. {@link #nearestPointIndex}) — на несколько порядков
     *  меньше любой реальной разницы позиций (сами координаты — единицы-тысячи мм),
     *  нужен только чтобы не потерять законную ничью из-за шума double-арифметики
     *  деления. */
    private static final double TIE_EPSILON_MM = 1e-6;

    /** Ширина экрана за вычетом {@link #EDGE_MARGIN_MM} с каждой стороны —
     *  общий знаменатель для {@link #baseColumnPointCount} (геометрический
     *  минимум точек считается от НЕЁ, не от полной ширины — иначе отступ
     *  учитывался бы в расстановке точек, но не в решении, сколько их нужно)
     *  и {@link #compute} (сама расстановка). Откат на полную ширину для
     *  узких экранов — см. class-javadoc {@link #EDGE_MARGIN_MM}. */
    private static double usableWidthMm(double widthMm) {
        return widthMm > 2 * EDGE_MARGIN_MM ? widthMm - 2 * EDGE_MARGIN_MM : widthMm;
    }

    /** Фактический отступ, применяемый к КАЖДОЙ стороне — {@link #EDGE_MARGIN_MM}
     *  либо 0 для узких экранов (см. {@link #usableWidthMm}), никогда не «половина
     *  отступа» — сохраняет геометрический минимум ({@link #baseColumnPointCount})
     *  и реальную расстановку ({@link #compute}) согласованными между собой. */
    private static double edgeMarginMm(double widthMm) {
        return widthMm > 2 * EDGE_MARGIN_MM ? EDGE_MARGIN_MM : 0;
    }

    /** Ближайшая к колонке точка (или пара точек) подвеса — при ТОЧНОЙ ничьей
     *  (колонка ровно на середине между двумя соседними точками) выбирает ту, что
     *  БЛИЖЕ К ЦЕНТРУ фермы, а не "первую встреченную по возрастанию индекса"
     *  (2026-08-19, баг-репорт: «нагрузка на крайние точки неравномерна, похоже на
     *  то, что расчёт идёт слева направо» — так и есть: старый код при {@code d <
     *  best} оставлял для ничьей уже установленный МЕНЬШИЙ индекс, то есть точку
     *  левее, систематически утяжеляя левый край и облегчая правый). Это правило
     *  СИММЕТРИЧНО относительно зеркального отражения экрана (точка i ↔ точка
     *  n-1-i, x ↔ widthMm-x) — доказательство и разбор конкретного примера
     *  (17×15 Dicolor, 4 точки) см. RIGGING_CALC_NOTES.md: правило "первый слева"
     *  симметрии не сохраняет ни для одного варианта fixed-tie-break (ни "всегда
     *  меньший индекс", ни "всегда больший"), а "ближе к центру" сохраняет.
     *  Неизбежная (при остатке колонок, не делящемся ровно) асимметрия сдвигается
     *  К ЦЕНТРУ фермы, где она не так критична, как на краях.
     *
     * <p><b>Двойная ничья — колонка РОВНО в центре фермы при чётном числе точек
     *  (2026-09-15, баг-репорт)</b>: "ближе к центру" само может ничего не решить —
     *  для ДВУХ ЦЕНТРАЛЬНЫХ точек (n чётно, индексы n/2-1 и n/2) расстояние до
     *  центра у обеих ОДИНАКОВОЕ по построению (они зеркальны друг другу). Когда
     *  колонка при этом лежит ровно посередине между ними (типично при НЕЧЁТНОМ
     *  числе занятых колонок — тогда одна колонка приходится точно на геометрический
     *  центр экрана), условие {@code centerDist < bestCenterDist} для второй точки
     *  ложно (расстояния равны, не меньше), и код молча оставлял первую встреченную
     *  по возрастанию индекса — ЛЕВУЮ из пары — тот же перекос, который сама эта
     *  функция была написана устранить, просто на уровень глубже. Возвращает
     *  МАССИВ ИЗ ДВУХ индексов в этом случае — вызывающий код ({@link #compute})
     *  делит вес колонки ПОПОЛАM между ними, а не отдаёт целиком одной (единственный
     *  физически честный выход для нагрузки, приложенной ровно между двумя опорами
     *  — та же логика, что тривиальный расчёт балки на двух опорах даёт по 50%
     *  каждой опоре для груза точно в центре пролёта). Массив из ОДНОГО индекса —
     *  как раньше, когда двойной ничьи нет. */
    private static int[] nearestPointIndex(double columnX, double[] pointX, double centerMm) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        double bestCenterDist = Double.MAX_VALUE;
        int tieWith = -1;
        for (int i = 0; i < pointX.length; i++) {
            double d = Math.abs(columnX - pointX[i]);
            double centerDist = Math.abs(pointX[i] - centerMm);
            if (d < best - TIE_EPSILON_MM) {
                best = d;
                nearest = i;
                bestCenterDist = centerDist;
                tieWith = -1;
            } else if (Math.abs(d - best) <= TIE_EPSILON_MM) {
                if (centerDist < bestCenterDist - TIE_EPSILON_MM) {
                    best = d;
                    nearest = i;
                    bestCenterDist = centerDist;
                    tieWith = -1;
                } else if (Math.abs(centerDist - bestCenterDist) <= TIE_EPSILON_MM) {
                    tieWith = i;
                }
            }
        }
        return tieWith < 0 ? new int[]{nearest} : new int[]{nearest, tieWith};
    }

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

    /** Минимум точек подвеса по РЕАЛЬНОЙ длине ФЕРМЫ в мм (не по числу
     *  модулей экрана — см. class-javadoc, баг-репорт 2026-08-19; не по ширине
     *  экрана — см. class-javadoc, «Точки расставляются от краёв ФЕРМЫ»),
     *  исходя из {@link #MAX_SPAN_MM} — независимо от веса/грузоподъёмности,
     *  это ГЕОМЕТРИЧЕСКИЙ минимум (равномерность провеса фермы), а не то, что
     *  вообще ограничивает нагрузку на точку. {@code trussLengthMm <= 0}
     *  (ширина модуля неизвестна и длина фермы не переопределена) откатывается
     *  на абсолютный минимум 2 — без размера модуля посчитать физическую длину
     *  нечем. Использует {@link TrussCalc#builtTrussLengthMm} (РЕАЛЬНАЯ длина
     *  набранного комплекта, когда профиль выбран), а не {@code
     *  effectiveTrussLengthMm} — баг-репорт 2026-09-15: собранная из целых
     *  сегментов ферма почти всегда чуть длиннее цели, и минимум точек должен
     *  считаться от того, что реально смонтировано. */
    private static int baseColumnPointCount(Screen screen, CabinetType defaultType, Workspace workspace) {
        double trussLengthMm = TrussCalc.builtTrussLengthMm(screen, defaultType, workspace);
        if (trussLengthMm <= 0) {
            return 2;
        }
        int spans = (int) Math.ceil(usableWidthMm(trussLengthMm) / MAX_SPAN_MM);
        return Math.max(2, spans + 1);
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
        int base = baseColumnPointCount(screen, defaultType, workspace);
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
     *  равномерно вдоль РЕАЛЬНОЙ ДЛИНЫ ФЕРМЫ ({@link TrussCalc#builtTrussLengthMm} —
     *  не {@code effectiveTrussLengthMm}, см. её javadoc про баг-репорт 2026-09-15:
     *  собранная из целых сегментов ферма почти всегда длиннее абстрактной цели, и
     *  нагрузка должна раскладываться по тому, что реально смонтировано) — методом
     *  грузовых площадей (каждая колонка отдаёт вес ближайшей по X точке, см. {@link
     *  #nearestPointIndex} за симметричным правилом на случай точной ничьей).
     *  Крайние точки отступают от краёв ФЕРМЫ на {@link #EDGE_MARGIN_MM} (не 0 — см.
     *  её javadoc), между крайними точками остальные распределены равномерно; при
     *  {@code n == 1} единственная точка по-прежнему ставится строго в центр (отступ
     *  для одной точки не имеет смысла). Точки считаются сначала в координатах,
     *  локальных для фермы, затем сдвигаются на {@link TrussCalc#leftOffsetMm(Screen,
     *  CabinetType, Workspace)} в координаты экрана (см. class-javadoc). */
    public static Result compute(Screen screen, CabinetType defaultType, Workspace workspace, int pointCount) {
        List<ColumnWeight> columns = columnWeights(screen, defaultType, workspace);
        double totalCabinetWeight = 0;
        for (ColumnWeight c : columns) {
            totalCabinetWeight += c.weightKg();
        }
        double hardwareFactor = 1 + HARDWARE_ALLOWANCE;
        double totalWithHardware = totalCabinetWeight * hardwareFactor;

        double trussLengthMm = TrussCalc.builtTrussLengthMm(screen, defaultType, workspace);
        double leftOffsetMm = TrussCalc.leftOffsetMm(screen, defaultType, workspace);
        int n = Math.max(1, pointCount);
        double margin = edgeMarginMm(trussLengthMm);
        double usable = usableWidthMm(trussLengthMm);
        double[] pointX = new double[n];
        for (int i = 0; i < n; i++) {
            double trussLocalX = n == 1 ? trussLengthMm / 2.0 : margin + usable * i / (n - 1.0);
            pointX[i] = trussLocalX - leftOffsetMm;
        }
        double centerMm = (pointX[0] + pointX[n - 1]) / 2.0;
        double[] pointWeight = new double[n];
        for (ColumnWeight c : columns) {
            int[] nearest = nearestPointIndex(c.xMm(), pointX, centerMm);
            double w = c.weightKg() * hardwareFactor;
            if (nearest.length == 1) {
                pointWeight[nearest[0]] += w;
            } else {
                // Двойная ничья (колонка ровно в центре фермы, n чётно) — см. javadoc
                // {@link #nearestPointIndex}: единственный физически честный вариант —
                // поровну на обе центральные точки, не целиком одной.
                pointWeight[nearest[0]] += w / 2.0;
                pointWeight[nearest[1]] += w / 2.0;
            }
        }
        enforceMirrorSymmetry(columns, pointX, pointWeight, centerMm);

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

    /** Заключительный проход по УЖЕ посчитанным {@code pointWeight} — по прямому
     *  запросу пользователя (2026-09-15, баг-репорт «если количество точек чётное,
     *  то часто при одинаковых нагрузках на центральные точки расчёт показывает
     *  разную нагрузку, чего быть не может»). Корневая причина (двойная ничья на
     *  центральной паре точек при нечётном числе занятых колонок) уже устранена
     *  выше в {@link #nearestPointIndex} (делит вес такой колонки пополам, не
     *  отдаёт целиком одной стороне) — этот проход не столько НУЖЕН, сколько
     *  СТРАХУЕТ: если геометрия точек и веса колонок объективно зеркально
     *  симметричны (см. {@link #columnsAreMirrorSymmetric}), но пара точек i/n-1-i
     *  всё же разошлась (например, из-за иного, ещё не найденного источника
     *  асимметрии, а не только уже устранённой двойной ничьи) — усредняет её
     *  нагрузку между обеими точками пары, а не оставляет необъяснимый перекос
     *  там, где сама физическая расстановка симметрична. НЕ трогает пары, для
     *  которых зеркальная симметрия объективно НЕ выполняется (например, нечётное
     *  число колонок без центральной, или намеренно несимметричная развеска) —
     *  там разная нагрузка по краям физически ожидаема, не ошибка. */
    private static void enforceMirrorSymmetry(List<ColumnWeight> columns, double[] pointX, double[] pointWeight,
            double centerMm) {
        int n = pointWeight.length;
        if (n < 2 || !columnsAreMirrorSymmetric(columns, centerMm)) {
            return;
        }
        for (int i = 0; i < n / 2; i++) {
            int j = n - 1 - i;
            // pointX[i]/pointX[j] зеркальны по построению (см. compute) — сверяем
            // только на случай будущих изменений в расстановке точек, не считаем
            // геометрию сама собой разумеющейся.
            if (Math.abs((pointX[i] - centerMm) + (pointX[j] - centerMm)) > TIE_EPSILON_MM) {
                continue;
            }
            if (Math.abs(pointWeight[i] - pointWeight[j]) > 1e-6) {
                double avg = (pointWeight[i] + pointWeight[j]) / 2.0;
                pointWeight[i] = avg;
                pointWeight[j] = avg;
            }
        }
    }

    /** true, если у КАЖДОЙ занятой колонки есть зеркальная (по X относительно
     *  {@code centerMm}, с тем же весом) — то есть распределение веса по ширине
     *  экрана объективно симметрично, и потому парные точки подвеса ОБЯЗАНЫ нести
     *  одинаковую нагрузку (см. {@link #enforceMirrorSymmetry}). Колонка сама себе
     *  зеркало, если лежит ровно в {@code centerMm} (нечётное число колонок) — тоже
     *  проходит проверку. Квадратичная по числу колонок — их обычно десятки, не
     *  тысячи, отдельной оптимизации не требует. */
    private static boolean columnsAreMirrorSymmetric(List<ColumnWeight> columns, double centerMm) {
        for (ColumnWeight c : columns) {
            double mirrorX = 2 * centerMm - c.xMm();
            boolean hasMirror = columns.stream().anyMatch(o -> Math.abs(o.xMm() - mirrorX) <= TIE_EPSILON_MM
                    && Math.abs(o.weightKg() - c.weightKg()) <= 1e-9);
            if (!hasMirror) {
                return false;
            }
        }
        return true;
    }
}
