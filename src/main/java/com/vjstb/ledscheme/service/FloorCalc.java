package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.FloorFrameCell;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Расчёт каркаса НАПОЛЬНОГО экрана (светодиодный пол, по которому ходят) — {@link
 * com.vjstb.ledscheme.model.ScreenMountType#FLOOR}. Запрос пользователя 2026-10-01, см.
 * раздел «Напольный каркас» в STRUCTURE_CALC_NOTES.md. Чистый сервис без Swing/GL — по
 * образцу {@link StructureCalc}: ничего не кэширует, каждый вызов {@link #compute} считает
 * заново от текущей формы экрана и текущей библиотеки.
 *
 * <p><b>Физика каркаса (со слов пользователя, его реальное оборудование)</b>: каркас
 * собирается из ТЕХ ЖЕ рам, что и башни наземного конструктива ({@link
 * StructureFrameType.Kind#FRAME}, обычно 950×500×51 мм), но лежащих ГОРИЗОНТАЛЬНО — длинная
 * сторона рамы (её {@code heightMm} в библиотеке) идёт вдоль ширины экрана (ось X, колонки
 * кабинетов), короткая ({@code widthMm}) — вдоль глубины (ось Y, ряды кабинетов). Соседние
 * рамы ОДНОГО ряда стыкуются короткими сторонами через стаканы — стакан добавляет зазор
 * (~50 мм), поэтому шаг рам вдоль X = длина рамы + зазор стакана (950+50 = 1000 мм = 2
 * кабинета по 500). Соседние РЯДЫ рам стоят вплотную длинными сторонами и просто
 * скручиваются — шаг вдоль Y = ширина рамы (500 мм = 1 кабинет). Под каждой рамой — 4
 * ножки, кабинеты крепятся к раме «зубами».
 *
 * <p><b>Почему число кабинетов на раму СЧИТАЕТСЯ, а не зашито 2×1</b>: пользователь явно
 * попросил выводить его из размеров рамы/стакана/кабинета — у других кабинетов (например,
 * 500×1000 или 600×337.5) соотношение другое. Если шаг рамы не кратен кабинету (в допуске
 * {@link #PITCH_TOLERANCE_MM}) — округляем до ближайшего целого и пишем ПРЕДУПРЕЖДЕНИЕ в
 * {@link Result#warnings()}, а не отказываемся считать (тот же принцип «показать риск, не
 * мешать», что у {@link StructureCalc#MAX_SAFE_TOWER_HEIGHT_MM}).
 *
 * <p><b>Расстановка рам — по непрерывным цепочкам видимых кабинетов</b> (алгоритм задан
 * пользователем): ряды кабинетов группируются в полосы по {@code cabinetsPerFrameY} (при
 * кабинете 500×500 — по одному ряду); в каждой полосе идём слева направо по непрерывным
 * цепочкам колонок, в которых ВСЕ кабинеты полосы видимы (скрытые кабинеты формы экрана в
 * каркасе не участвуют — рвут цепочку), и группируем подряд идущие колонки по {@code
 * cabinetsPerFrameX}. Хвост цепочки, короче рамы, остаётся БЕЗ рамы — эти кабинеты
 * «неопёртые» ({@link Result#unsupportedCabinets()}, в 2D-плане подсвечиваются оранжевым).
 * Лишние рамы на хвост сознательно НЕ ставятся (прямое указание пользователя: «лишние рамы не
 * рисуются и не считаются») — инженер решает по месту сам. Неполная нижняя полоса (если рядов
 * не кратно {@code cabinetsPerFrameY}) тоже целиком неопёртая.
 *
 * <p><b>С 2026-10-01 (3D-редактор пола) автоматика — только ГЕНЕРАТОР начального списка</b>:
 * реально стоящие рамы — {@link Screen#getFloorFrameCells()} (клик по раме в 3D прячет её,
 * Ctrl+клик по «призраку» возвращает/добавляет — {@link #toggle}), а {@link #compute} считает
 * по действующему списку {@link #effectiveCells} = merge ручных правок с автоматикой
 * ({@link #mergeCells}). Без ручных правок результат в точности прежний.
 *
 * <p><b>Стыки</b>: две соседние рамы одной цепочки одного ряда, идущие встык, — стык по
 * КОРОТКОЙ стороне (2 стакана + 2 болта). Рамы соседних полос, у которых отрезки колонок
 * перекрываются на длину > 0, — стык по ДЛИННОЙ стороне (вплотную, без стаканов, 2 болта).
 * Ряды независимы: при сдвинутых цепочках одна рама может стыковаться длинной стороной сразу
 * с двумя рамами соседнего ряда — это две отдельные пары, по 2 болта каждая. Любой стык = 2
 * болта; «метизы» = только болты (пользователь: «ничего другого не считать»).
 *
 * <p><b>Нагрузка</b> — СРЕДНЕЕ значение кг/м² по экрану (не по ножкам): (вес видимых кабинетов
 * по {@link ScreenLogic#stats(Screen, CabinetType, Workspace)} + вес рам по библиотеке) /
 * площадь видимых кабинетов. Никаких пределов/предупреждений по нагрузке нет — только число
 * (прямое указание пользователя). Вес стаканов/болтов/ножек/зубов не учитывается — их веса
 * нет в библиотеке (ножки/зубы в библиотеку пока не заносятся вовсе, см. заготовки {@code
 * model.FloorLegType}/{@code model.FloorToothType}).
 *
 * <p><b>Высота ножки</b> в расчёте НЕ участвует (прямое указание пользователя) — ножек ровно 4
 * на раму, без экономии на общих углах соседних рам.
 */
public final class FloorCalc {

    private FloorCalc() {
    }

    /** Фолбэк-габариты рамы, если тип в библиотеке не выбран или габарит пустой — те же
     *  950×500, что и у наземного конструктива ({@code ui.Structure3DPanel}). */
    public static final double DEFAULT_FRAME_LONG_MM = 950;
    public static final double DEFAULT_FRAME_SHORT_MM = 500;
    /** Зазор, который стакан добавляет на стыке коротких сторон, если у выбранного стакана в
     *  библиотеке не задан {@code heightMm} (у CUP габариты необязательны — см. javadoc
     *  {@link StructureFrameType}). 50 мм — со слов пользователя. */
    public static final double DEFAULT_CUP_GAP_MM = 50;
    /** Допуск «шаг рамы кратен кабинету», мм — при большем расхождении расчёт всё равно
     *  идёт (округление до ближайшего целого числа кабинетов), но с предупреждением. */
    public static final double PITCH_TOLERANCE_MM = 10;

    /** Любое соединение двух рам (по любой стороне) = 2 болта — со слов пользователя. */
    public static final int BOLTS_PER_JOINT = 2;
    /** Стаканов на стык по короткой стороне; по длинной — 0 (рамы вплотную). */
    public static final int CUPS_PER_SHORT_SIDE_JOINT = 2;
    /** Ножек строго 4 на раму, без экономии на общих углах — прямое указание пользователя. */
    public static final int LEGS_PER_FRAME = 4;

    /** Зубов (фиксаторов кабинета к раме) на кабинет — настройка пользователя на экран. */
    public static final int MIN_TEETH_PER_CABINET = 2;
    public static final int MAX_TEETH_PER_CABINET = 4;
    public static final int DEFAULT_TEETH_PER_CABINET = 4;

    /** Зажим числа зубов в допустимый диапазон 2..4 — общая точка для модели и UI. */
    public static int clampTeeth(int teethPerCabinet) {
        return Math.max(MIN_TEETH_PER_CABINET, Math.min(MAX_TEETH_PER_CABINET, teethPerCabinet));
    }

    /** Одна рама каркаса — прямоугольник кабинетов: левый верхний кабинет ({@code row},
     *  {@code col}) и сколько рядов/колонок кабинетов она несёт. */
    public record FramePlacement(int row, int col, int rowCount, int colCount) {
        /** Колонка ПОСЛЕ правого края (полуоткрытый интервал {@code [col, colEnd)}). */
        public int colEnd() {
            return col + colCount;
        }
    }

    /** Позиция кабинета в сетке экрана (как {@code CabinetInstance} rowIndex/colIndex). */
    public record CabinetCell(int row, int col) {
    }

    /** Стык двух рам: {@code a} — левая (короткая сторона) или верхняя (длинная сторона). */
    public record Joint(FramePlacement a, FramePlacement b) {
    }

    /**
     * Итог расчёта. {@code frames}/{@code shortSideJoints}/{@code longSideJoints} — для 2D-плана
     * ({@code ui.FloorPlanPanel}) и тестов, счётчики — для сводки и спецификации.
     * {@code frameTypeName}/{@code cupTypeName} — названия из библиотеки (null — не выбрано
     * или запись удалена), нужны спецификации, чтобы не резолвить библиотеку второй раз.
     */
    public record Result(int cabinetsPerFrameX, int cabinetsPerFrameY,
                         double framePitchXMm, double framePitchYMm, double cupGapMm,
                         List<FramePlacement> frames,
                         List<Joint> shortSideJoints, List<Joint> longSideJoints,
                         int frameCount, int shortSideJointCount, int longSideJointCount,
                         int cupCount, int boltCount, int legCount,
                         int teethPerCabinet, int toothCount,
                         int visibleCabinetCount, int supportedCabinetCount,
                         List<CabinetCell> unsupportedCabinets,
                         double cabinetWeightKg, double frameWeightKg, double areaM2,
                         double averageLoadKgPerM2,
                         String frameTypeName, String cupTypeName,
                         List<String> warnings) {

        public int unsupportedCabinetCount() {
            return unsupportedCabinets.size();
        }

        public boolean isUnsupported(int row, int col) {
            return unsupportedCabinets.contains(new CabinetCell(row, col));
        }
    }

    /**
     * Геометрия расстановки рам для текущего экрана — общая для {@link #compute}, merge ячеек
     * ({@link #mergeCells}), правок 3D-редактора ({@link #toggle}) и самого 3D-вида ({@code
     * ui.FloorPlan3DPanel}): иначе редактор считал бы «допустимую позицию» не так, как расчёт.
     * {@code visible[row][col]} — видим ли кабинет формы экрана; {@code autoFrames} —
     * автоматическая расстановка по цепочкам (алгоритм пользователя, см. class-javadoc), которая
     * с 2026-10-01 — способ ГЕНЕРАЦИИ начального списка рам, а не единственный источник.
     * {@link #valid()} == false — не задан тип кабинета, рам нет вообще.
     */
    public record Layout(int cabinetsPerFrameX, int cabinetsPerFrameY, double framePitchXMm, double framePitchYMm,
                         double cupGapMm, double frameLongMm, double frameShortMm, double frameDepthMm,
                         double cabinetWidthMm, double cabinetHeightMm, int rows, int cols, boolean[][] visible,
                         List<FramePlacement> autoFrames, StructureFrameType frameType, StructureFrameType cupType,
                         List<String> warnings) {

        public boolean valid() {
            return cabinetsPerFrameX > 0 && cabinetsPerFrameY > 0;
        }

        public boolean isVisible(int row, int col) {
            return row >= 0 && row < rows && col >= 0 && col < cols && visible[row][col];
        }

        /** Рама с левым верхним кабинетом ({@code row}, {@code col}) текущего размера. */
        public FramePlacement placementAt(int row, int col) {
            return new FramePlacement(row, col, cabinetsPerFrameY, cabinetsPerFrameX);
        }

        /** Допустимая позиция рамы: ряд — начало полосы (кратен кабинетам на раму по глубине),
         *  рама целиком внутри сетки и ВСЕ её кабинеты видимы — то же правило, по которому
         *  автоматическая расстановка режет цепочки (через скрытый кабинет рама не кладётся). */
        public boolean isValidPosition(int row, int col) {
            if (!valid() || row < 0 || col < 0 || row % cabinetsPerFrameY != 0
                    || row + cabinetsPerFrameY > rows || col + cabinetsPerFrameX > cols) {
                return false;
            }
            for (int r = row; r < row + cabinetsPerFrameY; r++) {
                for (int c = col; c < col + cabinetsPerFrameX; c++) {
                    if (!visible[r][c]) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Высота ножки в 3D-виде пола, мм — УСЛОВНАЯ. В расчёте не участвует (прямое указание
     *  пользователя, 2026-10-01), но 3D-редактору ({@code ui.FloorPlan3DPanel}) нужна какая-то
     *  разумная высота, чтобы рамы не лежали на земле и ножки были видны. 100 мм — типичный
     *  низ регулируемой ножки LED-пола; когда ножки появятся в библиотеке ({@code
     *  model.FloorLegType#heightMm}), брать оттуда. */
    public static final double DISPLAY_LEG_HEIGHT_MM = 100;
    /** Толщина рамы, лежащей плашмя, если в библиотеке не задан {@code depthMm} — те же 51 мм,
     *  что фолбэк у {@code ui.Structure3DPanel}. Только для 3D-вида. */
    public static final double DEFAULT_FRAME_DEPTH_MM = 51;

    public static Layout layout(Screen screen, CabinetType type, Workspace workspace) {
        List<String> warnings = new ArrayList<>();
        StructureFrameType frameType = workspace != null
                ? workspace.structureFrameTypeById(screen.getStructureFrameTypeId()) : null;
        StructureFrameType cupType = workspace != null
                ? workspace.structureFrameTypeById(screen.getStructureCupTypeId()) : null;
        double longMm = frameType != null && frameType.getHeightMm() != null && frameType.getHeightMm() > 0
                ? frameType.getHeightMm() : DEFAULT_FRAME_LONG_MM;
        double shortMm = frameType != null && frameType.getWidthMm() != null && frameType.getWidthMm() > 0
                ? frameType.getWidthMm() : DEFAULT_FRAME_SHORT_MM;
        double depthMm = frameType != null && frameType.getDepthMm() != null && frameType.getDepthMm() > 0
                ? frameType.getDepthMm() : DEFAULT_FRAME_DEPTH_MM;
        // Зазор стакана — из библиотеки, если у записи CUP задан габарит (heightMm — та же ось,
        // по которой стакан наращивает башню наземного конструктива), иначе константа 50 мм.
        double cupGapMm = cupType != null && cupType.getHeightMm() != null && cupType.getHeightMm() > 0
                ? cupType.getHeightMm() : DEFAULT_CUP_GAP_MM;
        if (frameType == null) {
            warnings.add(String.format("Тип рамы не выбран — взяты размеры по умолчанию %.0f×%.0f мм, вес рам не"
                    + " учтён в нагрузке.", DEFAULT_FRAME_LONG_MM, DEFAULT_FRAME_SHORT_MM));
        }
        double pitchX = longMm + cupGapMm;
        double pitchY = shortMm;

        int rows = Math.max(0, screen.getRows());
        int cols = Math.max(0, screen.getCols());
        boolean[][] visible = new boolean[rows][cols];
        for (CabinetInstance c : screen.getCabinets()) {
            int r = c.getRowIndex();
            int col = c.getColIndex();
            if (r >= 0 && r < rows && col >= 0 && col < cols && !c.isHidden()) {
                visible[r][col] = true;
            }
        }

        double cabW = type != null ? type.getWidthMm() : 0;
        double cabH = type != null ? type.getHeightMm() : 0;
        if (cabW <= 0 || cabH <= 0) {
            warnings.add("Не задан тип кабинета (или его размеры) — каркас не рассчитан.");
            return new Layout(0, 0, pitchX, pitchY, cupGapMm, longMm, shortMm, depthMm, cabW, cabH, rows, cols,
                    visible, List.of(), frameType, cupType, List.copyOf(warnings));
        }

        int perX = cabinetsPerFrame(pitchX, cabW, "вдоль длинной стороны рамы (длина + зазор стакана)", warnings);
        int perY = cabinetsPerFrame(pitchY, cabH, "вдоль короткой стороны рамы", warnings);

        List<FramePlacement> auto = new ArrayList<>();
        int bands = rows / perY; // неполная нижняя полоса — без рам, кабинеты неопёртые
        for (int b = 0; b < bands; b++) {
            int r0 = b * perY;
            int c = 0;
            while (c < cols) {
                if (!bandColumnVisible(visible, r0, perY, c)) {
                    c++;
                    continue;
                }
                int start = c;
                while (c < cols && bandColumnVisible(visible, r0, perY, c)) {
                    c++;
                }
                int n = (c - start) / perX;
                for (int k = 0; k < n; k++) {
                    auto.add(new FramePlacement(r0, start + k * perX, perY, perX));
                }
            }
        }
        return new Layout(perX, perY, pitchX, pitchY, cupGapMm, longMm, shortMm, depthMm, cabW, cabH, rows, cols,
                visible, List.copyOf(auto), frameType, cupType, List.copyOf(warnings));
    }

    /**
     * Merge-not-overwrite (запрос 2026-10-01, по образцу {@code ScreenLogic
     * #regenerateStructureCells}): из сохранённого списка рам и текущей автоматической
     * расстановки собирает действующий список.
     * <ol>
     *   <li>РУЧНЫЕ записи ({@code manual} или {@code hidden}) сохраняются как есть, пока их
     *       позиция допустима ({@link Layout#isValidPosition}); недопустимые («вне новой
     *       сетки» — экран сжали, под рамой скрыли кабинет, сменили раму) отбрасываются.
     *       Видимая ручная рама, перекрывшаяся с уже принятой видимой, отбрасывается; спрятанная,
     *       перекрытая видимой, — тоже (прятать нечего).</li>
     *   <li>Каждая автоматическая позиция: если на ней уже есть запись — остаётся она (так
     *       спрятанная рама остаётся спрятанной); если позиция перекрывает ручную запись
     *       (видимую или спрятанную) — пропускается (этот участок пользователь расставил сам,
     *       убранная рама делает свои кабинеты неопёртыми — ожидаемое поведение); иначе —
     *       новая видимая НЕручная запись.</li>
     * </ol>
     * Неручные записи из сохранённого списка не переносятся — они каждый раз заново
     * генерируются автоматикой, поэтому правка формы экрана пересчитывает нетронутые участки так
     * же, как до 3D-редактора. Пустой сохранённый список (старый проект) = чистая
     * автоматическая расстановка, ровно как раньше. Операция идемпотентна. Результат
     * отсортирован по (ряд, колонка).
     */
    public static List<FloorFrameCell> mergeCells(List<FloorFrameCell> stored, Layout layout) {
        List<FloorFrameCell> result = new ArrayList<>();
        if (!layout.valid()) {
            return result;
        }
        boolean[][] occupied = new boolean[layout.rows()][layout.cols()];
        List<FloorFrameCell> manualHidden = new ArrayList<>();
        if (stored != null) {
            for (FloorFrameCell rec : stored) {
                if (rec == null || !(rec.isManual() || rec.isHidden())
                        || !layout.isValidPosition(rec.getRow(), rec.getCol())
                        || findAt(result, rec.getRow(), rec.getCol()) != null
                        || findAt(manualHidden, rec.getRow(), rec.getCol()) != null) {
                    continue;
                }
                if (rec.isHidden()) {
                    manualHidden.add(new FloorFrameCell(rec.getRow(), rec.getCol(), true, true));
                    continue;
                }
                if (overlapsOccupied(occupied, layout, rec.getRow(), rec.getCol())) {
                    continue;
                }
                mark(occupied, layout, rec.getRow(), rec.getCol());
                result.add(new FloorFrameCell(rec.getRow(), rec.getCol(), false, true));
            }
        }
        for (FloorFrameCell h : manualHidden) {
            if (!overlapsOccupied(occupied, layout, h.getRow(), h.getCol())) {
                result.add(h);
            }
        }
        for (FramePlacement p : layout.autoFrames()) {
            if (findAt(result, p.row(), p.col()) != null) {
                continue;
            }
            boolean blocked = false;
            for (FloorFrameCell rec : result) {
                if (overlaps(layout, rec.getRow(), rec.getCol(), p.row(), p.col())) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                result.add(new FloorFrameCell(p.row(), p.col(), false, false));
            }
        }
        result.sort(Comparator.comparingInt(FloorFrameCell::getRow).thenComparingInt(FloorFrameCell::getCol));
        return result;
    }

    /** Действующий список рам экрана: {@link #mergeCells} сохранённого списка с текущей
     *  автоматикой. Им пользуется и расчёт, и 3D-редактор. */
    public static List<FloorFrameCell> effectiveCells(Screen screen, Layout layout) {
        return mergeCells(screen.getFloorFrameCells(), layout);
    }

    /**
     * Позиции, куда сейчас можно поставить раму Ctrl+кликом в 3D («призраки»): любая допустимая
     * позиция ({@link Layout#isValidPosition}), не перекрывающая ВИДИМУЮ раму. Сюда входят и
     * спрятанные рамы (вернуть), и позиции со сдвигом на кабинет (переставить раму, если
     * автоматика не устроила). Позиции перекрываются между собой — какой из них отвечает клик,
     * решает {@code FloorPickMath#pickGhost} (ближайший центр к точке под курсором).
     */
    public static List<FramePlacement> addablePositions(Layout layout, List<FloorFrameCell> effective) {
        List<FramePlacement> out = new ArrayList<>();
        if (!layout.valid()) {
            return out;
        }
        boolean[][] occupied = new boolean[layout.rows()][layout.cols()];
        for (FloorFrameCell c : effective) {
            if (!c.isHidden()) {
                mark(occupied, layout, c.getRow(), c.getCol());
            }
        }
        int perY = layout.cabinetsPerFrameY();
        for (int r = 0; r + perY <= layout.rows(); r += perY) {
            for (int c = 0; c + layout.cabinetsPerFrameX() <= layout.cols(); c++) {
                if (layout.isValidPosition(r, c) && !overlapsOccupied(occupied, layout, r, c)) {
                    out.add(layout.placementAt(r, c));
                }
            }
        }
        return out;
    }

    /** Можно ли переключить раму в позиции ({@code row}, {@code col}): видимую — всегда
     *  (спрятать), иначе — только если позиция есть в {@link #addablePositions}. */
    public static boolean canToggle(Layout layout, List<FloorFrameCell> effective, int row, int col) {
        FloorFrameCell existing = findAt(effective, row, col);
        if (existing != null && !existing.isHidden()) {
            return true;
        }
        return addablePositions(layout, effective).contains(layout.placementAt(row, col));
    }

    /**
     * Правка 3D-редактора пола (запрос 2026-10-01): видимая рама в позиции ({@code row},
     * {@code col}) прячется (запись остаётся с {@code hidden=true, manual=true} — кабинеты под
     * ней становятся неопёртыми, оранжевыми); спрятанная — возвращается; пустая допустимая
     * позиция получает новую видимую раму (спрятанные записи, которые она перекрыла, удаляются
     * — прятать там больше нечего). Перед правкой список «материализуется» через {@link
     * #effectiveCells}, чтобы на экране сохранился ПОЛНЫЙ действующий список. Возвращённая/новая
     * рама помечается ручной, только если её позиции нет в автоматической расстановке — иначе
     * «вернуть как было» действительно возвращает экран к автоматике.
     *
     * @return {@code false}, если позиция недопустима и ничего не изменилось.
     */
    public static boolean toggle(Screen screen, CabinetType type, Workspace workspace, int row, int col) {
        Layout layout = layout(screen, type, workspace);
        List<FloorFrameCell> cells = new ArrayList<>(effectiveCells(screen, layout));
        if (!canToggle(layout, cells, row, col)) {
            return false;
        }
        boolean auto = layout.autoFrames().contains(layout.placementAt(row, col));
        FloorFrameCell existing = findAt(cells, row, col);
        if (existing != null && !existing.isHidden()) {
            existing.setHidden(true);
            existing.setManual(true);
        } else {
            if (existing != null) {
                existing.setHidden(false);
                existing.setManual(!auto);
            } else {
                cells.add(new FloorFrameCell(row, col, false, !auto));
            }
            cells.removeIf(c -> c.isHidden() && !(c.getRow() == row && c.getCol() == col)
                    && overlaps(layout, c.getRow(), c.getCol(), row, col));
        }
        cells.sort(Comparator.comparingInt(FloorFrameCell::getRow).thenComparingInt(FloorFrameCell::getCol));
        screen.setFloorFrameCells(cells);
        return true;
    }

    private static FloorFrameCell findAt(List<FloorFrameCell> cells, int row, int col) {
        for (FloorFrameCell c : cells) {
            if (c.matches(row, col)) {
                return c;
            }
        }
        return null;
    }

    private static boolean overlaps(Layout l, int r1, int c1, int r2, int c2) {
        return r1 < r2 + l.cabinetsPerFrameY() && r2 < r1 + l.cabinetsPerFrameY()
                && c1 < c2 + l.cabinetsPerFrameX() && c2 < c1 + l.cabinetsPerFrameX();
    }

    private static boolean overlapsOccupied(boolean[][] occupied, Layout l, int row, int col) {
        for (int r = row; r < row + l.cabinetsPerFrameY(); r++) {
            for (int c = col; c < col + l.cabinetsPerFrameX(); c++) {
                if (occupied[r][c]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mark(boolean[][] occupied, Layout l, int row, int col) {
        for (int r = row; r < row + l.cabinetsPerFrameY(); r++) {
            for (int c = col; c < col + l.cabinetsPerFrameX(); c++) {
                occupied[r][c] = true;
            }
        }
    }

    public static Result compute(Screen screen, CabinetType type, Workspace workspace) {
        int teeth = clampTeeth(screen.getFloorTeethPerCabinet());
        Layout layout = layout(screen, type, workspace);
        List<String> warnings = new ArrayList<>(layout.warnings());
        StructureFrameType frameType = layout.frameType();
        StructureFrameType cupType = layout.cupType();
        double pitchX = layout.framePitchXMm();
        double pitchY = layout.framePitchYMm();
        double cupGapMm = layout.cupGapMm();
        double cabW = layout.cabinetWidthMm();
        double cabH = layout.cabinetHeightMm();
        if (!layout.valid()) {
            return new Result(0, 0, pitchX, pitchY, cupGapMm, List.of(), List.of(), List.of(),
                    0, 0, 0, 0, 0, 0, teeth, 0, 0, 0, List.of(), 0, 0, 0, 0,
                    frameType != null ? frameType.getName() : null, cupType != null ? cupType.getName() : null,
                    List.copyOf(warnings));
        }
        int perX = layout.cabinetsPerFrameX();
        int perY = layout.cabinetsPerFrameY();
        int rows = layout.rows();
        int cols = layout.cols();
        boolean[][] visible = layout.visible();

        // С 2026-10-01 считаем по ДЕЙСТВУЮЩЕМУ списку рам (merge сохранённых ручных правок 3D-
        // редактора с автоматикой), а не по одной автоматике — без правок это ровно она же.
        boolean[][] supported = new boolean[rows][cols];
        List<FramePlacement> frames = new ArrayList<>();
        for (FloorFrameCell cell : effectiveCells(screen, layout)) {
            if (!cell.isHidden()) {
                FramePlacement f = layout.placementAt(cell.getRow(), cell.getCol());
                frames.add(f);
                for (int rr = f.row(); rr < f.row() + perY; rr++) {
                    for (int cc = f.col(); cc < f.colEnd(); cc++) {
                        supported[rr][cc] = true;
                    }
                }
            }
        }
        int bands = rows / perY;
        List<List<FramePlacement>> framesByBand = new ArrayList<>();
        for (int b = 0; b < bands; b++) {
            framesByBand.add(new ArrayList<>());
        }
        for (FramePlacement f : frames) {
            framesByBand.get(f.row() / perY).add(f);
        }
        // Стык по короткой стороне — две рамы одной полосы, стоящие ВСТЫК (правый край одной =
        // левый край другой). Для автоматики это ровно «соседние рамы одной цепочки»; рамы,
        // между которыми спрятанная/убранная рама или скрытый кабинет, не стыкуются.
        List<Joint> shortJoints = new ArrayList<>();
        for (List<FramePlacement> band : framesByBand) {
            for (int i = 0; i + 1 < band.size(); i++) {
                if (band.get(i).colEnd() == band.get(i + 1).col()) {
                    shortJoints.add(new Joint(band.get(i), band.get(i + 1)));
                }
            }
        }

        // Стыки по длинной стороне — между рамами СОСЕДНИХ полос с перекрытием отрезков колонок > 0.
        List<Joint> longJoints = new ArrayList<>();
        for (int b = 0; b + 1 < framesByBand.size(); b++) {
            for (FramePlacement upper : framesByBand.get(b)) {
                for (FramePlacement lower : framesByBand.get(b + 1)) {
                    int overlap = Math.min(upper.colEnd(), lower.colEnd()) - Math.max(upper.col(), lower.col());
                    if (overlap > 0) {
                        longJoints.add(new Joint(upper, lower));
                    }
                }
            }
        }

        int visibleCount = 0;
        int supportedCount = 0;
        List<CabinetCell> unsupported = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!visible[r][c]) {
                    continue;
                }
                visibleCount++;
                if (supported[r][c]) {
                    supportedCount++;
                } else {
                    unsupported.add(new CabinetCell(r, c));
                }
            }
        }
        if (!unsupported.isEmpty()) {
            warnings.add(String.format("Кабинетов без опоры (нет рамы под ними): %d — подсвечены оранжевым на плане.",
                    unsupported.size()));
        }

        int frameCount = frames.size();
        int shortCount = shortJoints.size();
        int longCount = longJoints.size();
        int cupCount = shortCount * CUPS_PER_SHORT_SIDE_JOINT;
        int boltCount = (shortCount + longCount) * BOLTS_PER_JOINT;
        int legCount = frameCount * LEGS_PER_FRAME;
        int toothCount = supportedCount * teeth;

        double cabinetWeightKg = ScreenLogic.stats(screen, type, workspace).totalWeightKg();
        double frameWeightKg = frameType != null ? frameType.getWeightKg() * frameCount : 0;
        double areaM2 = visibleCount * cabW * cabH / 1_000_000.0;
        double avgLoad = areaM2 > 0 ? (cabinetWeightKg + frameWeightKg) / areaM2 : 0;

        return new Result(perX, perY, pitchX, pitchY, cupGapMm, List.copyOf(frames), List.copyOf(shortJoints),
                List.copyOf(longJoints), frameCount, shortCount, longCount, cupCount, boltCount, legCount,
                teeth, toothCount, visibleCount, supportedCount, List.copyOf(unsupported),
                cabinetWeightKg, frameWeightKg, areaM2, avgLoad,
                frameType != null ? frameType.getName() : null, cupType != null ? cupType.getName() : null,
                List.copyOf(warnings));
    }

    /** Сколько кабинетов укладывается в шаг рамы по одной оси — округление до ближайшего
     *  целого, не меньше 1; расхождение больше {@link #PITCH_TOLERANCE_MM} — предупреждение. */
    private static int cabinetsPerFrame(double pitchMm, double cabMm, String axis, List<String> warnings) {
        int n = (int) Math.round(pitchMm / cabMm);
        if (n < 1) {
            n = 1;
        }
        double mismatch = Math.abs(pitchMm - n * cabMm);
        if (mismatch > PITCH_TOLERANCE_MM) {
            warnings.add(String.format("Шаг рамы %s — %.0f мм — не кратен кабинету %.0f мм (взято %d каб. на раму,"
                    + " расхождение %.0f мм).", axis, pitchMm, cabMm, n, mismatch));
        }
        return n;
    }

    private static boolean bandColumnVisible(boolean[][] visible, int r0, int perY, int col) {
        for (int r = r0; r < r0 + perY; r++) {
            if (!visible[r][col]) {
                return false;
            }
        }
        return true;
    }
}
