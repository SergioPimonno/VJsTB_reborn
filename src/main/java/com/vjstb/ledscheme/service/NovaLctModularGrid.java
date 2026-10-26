package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.NovaLctScrWriter.CellKey;
import com.vjstb.ledscheme.service.NovaLctScrWriter.Rec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Модульный экран глазами NovaLCT (docs/receiver-cards/PLAN.md, раздел 7, трек E): «кабинет» NovaLCT — это ПРИЁМНАЯ
 * КАРТА, а не модуль, и по размеру он равен прямоугольнику её модулей в пикселях; обычный кабинет смешанного экрана —
 * отдельный «кабинет» как раньше. Порядок на порту — порядок звеньев цепочки (как у обычных кабинетов), а порядок
 * модулей ВНУТРИ карты (.rcfgx) файл не несёт — его техник выставляет в самой NovaLCT.
 *
 * <p>Зачем отдельный класс, а не правка {@code ScreenLogic}: сетка Standard-экрана NovaLCT адресует «кабинеты» парой
 * (столбец, ряд), а у модульного экрана сетка МОДУЛЕЙ (десятки тысяч ячеек) и сетка КАРТ — разные вещи. Здесь из
 * прямоугольников карт строится «сетка карт»: столбцы — различные X начала, ряды — различные Y. Standard годится,
 * только если эта укладка в точности воспроизводится тем, как сама NovaLCT складывает размеры (x — сумма ширин левее
 * в ряду, y — сумма высот выше в столбце, см. {@code NovaLctScrWriter.writeCabinetRecords}) и на одном порту все
 * карты одного размера. Укладку проверяем строго — «выровненная сетка»: каждая карта занимает ровно свою клетку
 * (до начала следующего столбца и ряда), углы экрана в (0, 0). Карты, у которых линии стыков в соседних рядах или
 * столбцах не совпадают (3+1 модуль сверху, 2+2 снизу), и укладки с разной высотой клеток внутри одного ряда
 * (в образце NovaLCT это допустимо, но не проверено для карт) — Complex с явными пиксельными прямоугольниками.
 * Для экранов без карт и блоков
 * класс не создаётся ({@link #of} → {@code null}) — прежний путь не затронут ни одним байтом.
 *
 * <p>Не проверено загрузкой в NovaLCT: карты РАЗНЫХ размеров на разных портах одного Standard-экрана (по аналогии
 * с подтверждённым образцом «порт 1 — 128×128, порт 2 — 128×64» для кабинетов) и Complex-экран с картами
 * (формат записи подтверждён, но не для карт) — {@code docs/receiver-cards/samples/}.
 */
final class NovaLctModularGrid {

    /** Прямоугольник звена в пикселях экрана: карта (по её модулям) или обычный кабинет. */
    record UnitRect(String id, boolean card, int anchorRow, int anchorCol, int x, int y, int w, int h, int modules,
                    boolean rectangular) {
        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    /** Расключённое звено: прямоугольник + карта/порт/номер в цепочке (0-based, как {@link Rec}). */
    record Wired(UnitRect unit, int card, int port, int seq) {
    }

    private final List<UnitRect> units = new ArrayList<>();
    private final Map<Long, UnitRect> byAnchor = new HashMap<>();
    private final Map<String, UnitRect> byId = new HashMap<>();
    /** Прямоугольники видимых модулей, у которых нет карты: место «не расключено», а не «кабинета тут нет». */
    private final List<int[]> cardlessRects = new ArrayList<>();
    private final List<String> geometryProblems = new ArrayList<>();

    // Укладка Standard (считается один раз): столбцы/ряды «сетки карт».
    private boolean regular;
    private String irregularReason;
    private int cols;
    private int rows;
    private int cabW = 128;
    private int cabH = 128;
    private final Map<String, CellKey> cellOf = new HashMap<>();
    private final Map<CellKey, int[]> cellSizes = new HashMap<>();
    private final Set<CellKey> blankCells = new LinkedHashSet<>();

    private NovaLctModularGrid(Screen screen, Workspace ws, ScreenModules sm) {
        CabinetType def = ws.cabinetTypeById(screen.getCabinetTypeId());
        int nativeW = def != null ? def.getResolutionWidth() : 128;
        int nativeH = def != null ? def.getResolutionHeight() : 128;
        Set<String> inCard = new HashSet<>();
        for (ReceiverCardInstance card : screen.getReceiverCards()) {
            List<CabinetInstance> mods = new ArrayList<>();
            for (CabinetInstance c : ChainLinks.cabinetsOf(screen, card.getId())) {
                if (!c.isHidden()) {
                    mods.add(c);
                }
            }
            if (mods.isEmpty()) {
                continue;
            }
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int y1 = Integer.MIN_VALUE;
            int minRow = Integer.MAX_VALUE;
            int maxRow = Integer.MIN_VALUE;
            int minCol = Integer.MAX_VALUE;
            int maxCol = Integer.MIN_VALUE;
            for (CabinetInstance c : mods) {
                int[] r = rectOf(c, def, ws, nativeW, nativeH);
                x0 = Math.min(x0, r[0]);
                y0 = Math.min(y0, r[1]);
                x1 = Math.max(x1, r[0] + r[2]);
                y1 = Math.max(y1, r[1] + r[3]);
                minRow = Math.min(minRow, c.getRowIndex());
                maxRow = Math.max(maxRow, c.getRowIndex());
                minCol = Math.min(minCol, c.getColIndex());
                maxCol = Math.max(maxCol, c.getColIndex());
                inCard.add(c.getId());
            }
            boolean rect = (maxRow - minRow + 1) * (maxCol - minCol + 1) == mods.size();
            CabinetInstance anchor = mods.get(0);
            add(new UnitRect(card.getId(), true, anchor.getRowIndex(), anchor.getColIndex(), x0, y0, x1 - x0,
                    y1 - y0, mods.size(), rect));
        }
        for (CabinetInstance c : sm.ordinaryCabinets()) {
            int[] r = rectOf(c, def, ws, nativeW, nativeH);
            add(new UnitRect(c.getId(), false, c.getRowIndex(), c.getColIndex(), r[0], r[1], r[2], r[3], 1, true));
        }
        for (ScreenModules.Module m : sm.modules()) {
            if (!inCard.contains(m.id())) {
                cardlessRects.add(rectOf(m.cabinet(), def, ws, nativeW, nativeH));
            }
        }
        checkGeometry();
        buildStandardLayout();
    }

    private void add(UnitRect u) {
        units.add(u);
        byAnchor.put(anchorKey(u.anchorRow(), u.anchorCol()), u);
        byId.put(u.id(), u);
    }

    private static long anchorKey(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    /** {x, y, w, h} ячейки в пикселях — тот же расчёт, что у Complex Screen обычных экранов. */
    private static int[] rectOf(CabinetInstance c, CabinetType def, Workspace ws, int nativeW, int nativeH) {
        CabinetType eff = ScreenLogic.effectiveType(c, def, ws);
        double ox = def != null ? ScreenLogic.offsetPx(c.getOffsetXMm(), nativeW, def.getWidthMm()) : 0;
        double oy = def != null ? ScreenLogic.offsetPx(c.getOffsetYMm(), nativeH, def.getHeightMm()) : 0;
        int x = (int) Math.round(c.getColIndex() * nativeW + ox);
        int y = (int) Math.round(c.getRowIndex() * nativeH + oy);
        int w = eff != null ? eff.getResolutionWidth() : nativeW;
        int h = eff != null ? eff.getResolutionHeight() : nativeH;
        return new int[]{x, y, w, h};
    }

    /** Модульный экран NovaLCT-экспорту или {@code null} (нет модулей — прежний путь). */
    static NovaLctModularGrid of(Screen screen, Workspace ws) {
        if (screen == null || ws == null) {
            return null;
        }
        ScreenModules sm = ScreenModules.of(screen, ws);
        return sm.isModular() ? new NovaLctModularGrid(screen, ws, sm) : null;
    }

    // ------------------------------------------------------------------ звенья и их сопоставление с записями

    List<UnitRect> units() {
        return units;
    }

    /** Звено, чей якорь (первый модуль карты / кабинет) стоит в ячейке {@code (row, col)}; {@code null} — такого нет. */
    UnitRect unitAtAnchor(int row, int col) {
        return byAnchor.get(anchorKey(row, col));
    }

    UnitRect unit(String id) {
        return byId.get(id);
    }

    /** Расключённые звенья из записей резолвера контроллера (записи других экранов и неизвестные ячейки пропускаются). */
    List<Wired> wire(Screen screen, List<NovaLctControllerResolver.CabinetRec> recs) {
        List<Wired> out = new ArrayList<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            if (r.sourceScreen() != screen) {
                continue;
            }
            UnitRect u = unitAtAnchor(r.row(), r.col());
            if (u != null) {
                out.add(new Wired(u, r.cardIndex(), r.portInPool(), r.seq()));
            }
        }
        return out;
    }

    /** Расключённые звенья из записей экрано-центричного разбора цепочек ({@code id якоря → Rec}); порядок — по
     *  порту и номеру в цепочке, чтобы Complex-записи шли как в обычном экспорте. */
    List<Wired> wire(Map<String, Rec> byAnchorCabinetId, Screen screen) {
        List<Wired> out = new ArrayList<>();
        for (Rec r : byAnchorCabinetId.values()) {
            UnitRect u = unitAtAnchor(r.row(), r.col());
            if (u != null) {
                out.add(new Wired(u, r.card(), r.port(), r.seq()));
            }
        }
        out.sort(java.util.Comparator.comparingInt(Wired::card).thenComparingInt(Wired::port)
                .thenComparingInt(Wired::seq));
        return out;
    }

    // ------------------------------------------------------------------ причины Complex

    /**
     * Почему экран нельзя записать как обычный (Standard) — для диалога «как прописывать экран»; пустой список —
     * можно. {@code wired == null} — порты неизвестны (проверка «одного размера на порт» заменяется проверкой
     * «одинаковый размер у всех карт»).
     */
    List<String> complexReasons(List<Wired> wired) {
        List<String> reasons = new ArrayList<>(geometryProblems);
        Map<String, LinkedHashSet<String>> sizesByPort = new LinkedHashMap<>();
        Set<String> allSizes = new LinkedHashSet<>();
        for (UnitRect u : units) {
            allSizes.add(u.w() + "×" + u.h());
        }
        if (wired == null) {
            if (allSizes.size() > 1) {
                reasons.add("карты разных размеров (" + String.join(", ", allSizes)
                        + "), а в этом режиме Standard хранит один размер на весь экран");
            }
        } else {
            for (Wired w : wired) {
                sizesByPort.computeIfAbsent(w.card() + ":" + w.port(), k -> new LinkedHashSet<>())
                        .add(w.unit().w() + "×" + w.unit().h());
            }
            for (Map.Entry<String, LinkedHashSet<String>> e : sizesByPort.entrySet()) {
                if (e.getValue().size() > 1) {
                    String[] cp = e.getKey().split(":");
                    reasons.add("на одном порту (Sending Card " + (Integer.parseInt(cp[0]) + 1) + ", порт "
                            + (Integer.parseInt(cp[1]) + 1) + ") карты разных размеров: "
                            + String.join(", ", e.getValue()) + " — Standard хранит один размер на порт");
                }
            }
        }
        if (!regular && irregularReason != null) {
            reasons.add(irregularReason);
        }
        return reasons;
    }

    private void checkGeometry() {
        for (UnitRect u : units) {
            if (!u.rectangular()) {
                geometryProblems.add("карта в ряду " + (u.anchorRow() + 1) + ", столбце " + (u.anchorCol() + 1)
                        + " не прямоугольная: NovaLCT получит охватывающий прямоугольник, он наедет на соседние модули");
            }
        }
        for (int i = 0; i < units.size(); i++) {
            UnitRect a = units.get(i);
            for (int j = i + 1; j < units.size(); j++) {
                UnitRect b = units.get(j);
                if (a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom()) {
                    geometryProblems.add("карты пересекаются в пикселях: ряд " + (a.anchorRow() + 1) + ", столбец "
                            + (a.anchorCol() + 1) + " и ряд " + (b.anchorRow() + 1) + ", столбец "
                            + (b.anchorCol() + 1));
                    return; // одной строки достаточно: пересечения плодят друг друга
                }
            }
        }
    }

    // ------------------------------------------------------------------ укладка Standard

    private void buildStandardLayout() {
        if (units.isEmpty()) {
            regular = false;
            irregularReason = "нет ни одной карты или обычного кабинета";
            return;
        }
        TreeSet<Integer> xs = new TreeSet<>();
        TreeSet<Integer> ys = new TreeSet<>();
        for (UnitRect u : units) {
            xs.add(u.x());
            ys.add(u.y());
        }
        List<Integer> xl = new ArrayList<>(xs);
        List<Integer> yl = new ArrayList<>(ys);
        cols = xl.size();
        rows = yl.size();
        Map<Integer, Integer> xi = new HashMap<>();
        Map<Integer, Integer> yi = new HashMap<>();
        for (int i = 0; i < xl.size(); i++) {
            xi.put(xl.get(i), i);
        }
        for (int i = 0; i < yl.size(); i++) {
            yi.put(yl.get(i), i);
        }
        Map<CellKey, UnitRect> unitAt = new HashMap<>();
        for (UnitRect u : units) {
            CellKey k = new CellKey(xi.get(u.x()), yi.get(u.y()));
            cellOf.put(u.id(), k);
            unitAt.put(k, u);
        }
        // Размер пустой клетки сетки — ширина её столбца и высота её ряда (до следующего начала либо самый
        // большой размер звена в столбце/ряду для последнего).
        int[] colW = new int[cols];
        int[] rowH = new int[rows];
        for (UnitRect u : units) {
            CellKey k = cellOf.get(u.id());
            colW[k.col()] = Math.max(colW[k.col()], u.w());
            rowH[k.row()] = Math.max(rowH[k.row()], u.h());
        }
        for (int c = 0; c + 1 < cols; c++) {
            colW[c] = xl.get(c + 1) - xl.get(c);
        }
        for (int r = 0; r + 1 < rows; r++) {
            rowH[r] = yl.get(r + 1) - yl.get(r);
        }
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                CellKey k = new CellKey(c, r);
                UnitRect u = unitAt.get(k);
                if (u != null) {
                    cellSizes.put(k, new int[]{u.w(), u.h()});
                } else {
                    cellSizes.put(k, new int[]{colW[c], rowH[r]});
                    int[] cell = {xl.get(c), yl.get(r), colW[c], rowH[r]};
                    if (!coveredByCardless(cell)) {
                        blankCells.add(k);
                    }
                }
            }
        }
        UnitRect first = unitAt.get(new CellKey(0, 0));
        if (first != null) {
            cabW = first.w();
            cabH = first.h();
        } else {
            int[] s = cellSizes.get(new CellKey(0, 0));
            cabW = s[0];
            cabH = s[1];
        }
        // Standard складывает размеры сам: x = сумма ширин клеток левее в ряду, y = сумма высот выше в столбце.
        if (xl.get(0) != 0 || yl.get(0) != 0) {
            regular = false;
            irregularReason = "первый ряд или первый столбец экрана пуст (у модулей нет карт) — Standard не хранит"
                    + " смещение экрана по Y и не умеет пустой начальный столбец";
            return;
        }
        // Выровненная сетка: звено занимает ровно свою клетку (до начала следующего столбца и ряда). Карта, которая
        // длиннее или короче шага столбца/ряда, в Standard не выражается: клетка «над» ней или «под» ней была бы
        // видимой фантомной картой, а положение соседей перестало бы быть суммой размеров.
        for (UnitRect u : units) {
            CellKey k = cellOf.get(u.id());
            if ((k.col() + 1 < cols && u.w() != colW[k.col()]) || (k.row() + 1 < rows && u.h() != rowH[k.row()])) {
                regular = false;
                irregularReason = "карты не образуют выровненную сетку: размер карты в ряду " + (u.anchorRow() + 1)
                        + ", столбце " + (u.anchorCol() + 1) + " не совпадает с шагом сетки (" + colW[k.col()] + "×"
                        + rowH[k.row()] + " px) — карты в соседних рядах/столбцах начинаются на других линиях,"
                        + " а Standard складывает размеры подряд";
                return;
            }
        }
        for (UnitRect u : units) {
            CellKey k = cellOf.get(u.id());
            int accX = 0;
            for (int c = 0; c < k.col(); c++) {
                accX += cellSizes.get(new CellKey(c, k.row()))[0];
            }
            int accY = 0;
            for (int r = 0; r < k.row(); r++) {
                accY += cellSizes.get(new CellKey(k.col(), r))[1];
            }
            if (accX != u.x() || accY != u.y()) {
                regular = false;
                irregularReason = "карты не образуют сетку: положение карты в ряду " + (u.anchorRow() + 1)
                        + ", столбце " + (u.anchorCol() + 1) + " не получается как сумма размеров соседей —"
                        + " Standard складывает размеры подряд";
                return;
            }
        }
        regular = true;
    }

    private boolean coveredByCardless(int[] cell) {
        for (int[] m : cardlessRects) {
            if (cell[0] < m[0] + m[2] && m[0] < cell[0] + cell[2] && cell[1] < m[1] + m[3]
                    && m[1] < cell[1] + cell[3]) {
                return true;
            }
        }
        return false;
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    int cabW() {
        return cabW;
    }

    int cabH() {
        return cabH;
    }

    /** Размеры ВСЕХ клеток сетки карт (пустые — размером их столбца и ряда): Standard складывает их при подсчёте
     *  положения, поэтому нужны и клетки без записи. */
    Map<CellKey, int[]> cellSizes() {
        return cellSizes;
    }

    /** Записи Standard: расключённые звенья + blank-сентинелы ({@code card=0xFF}) в клетках, где ни карты, ни
     *  модуля без карты нет (форма экрана с вырезом — как скрытые ячейки обычного экрана). */
    Map<CellKey, Rec> cells(List<Wired> wired) {
        Map<CellKey, Rec> out = new HashMap<>();
        for (Wired w : wired) {
            CellKey k = cellOf.get(w.unit().id());
            out.put(k, new Rec(k.row(), k.col(), w.card(), w.port(), w.seq()));
        }
        for (CellKey k : blankCells) {
            out.putIfAbsent(k, new Rec(k.row(), k.col(), 255, 0, 0));
        }
        return out;
    }

    /** Левый верхний угол всех расключённых звеньев, px — для положения куска экрана на канвасе NovaLCT. */
    static int[] originOf(List<Wired> wired) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        for (Wired w : wired) {
            x = Math.min(x, w.unit().x());
            y = Math.min(y, w.unit().y());
        }
        return wired.isEmpty() ? new int[]{0, 0} : new int[]{x, y};
    }
}
