package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.ModuleNumberingPattern;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Screen;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Номера модулей в приёмной карте = номера её выходов (docs/receiver-cards/PLAN.md, D6–D8, D14). Порт функций
 * {@code seq} / {@code ports} прототипа {@code prototype-flow.html}: обход прямоугольника, описанного вокруг модулей
 * карты, по шаблону экрана ({@link ModuleNumberingPattern}); клетки, которых в карте нет (чужие карты, вырезы,
 * скрытые ячейки), пропускаются, а номера продолжаются без дыр. Либо — ручной порядок карты
 * ({@link ReceiverCardInstance#getManualPortOrder()}), тогда модули, которых в нём нет, остаются без номера.
 *
 * <p>Функции чистые (без Swing и без состояния): рисует их результат вид «Приёмные карты» и тот же рендер
 * экспорта «Карта подключения».
 */
public final class ModuleNumbering {

    private ModuleNumbering() {
    }

    /**
     * Порядок обхода прямоугольника {@code width × height} по шаблону: пары {@code {строка, столбец}} (с 0)
     * в порядке номеров 1, 2, 3… Столбцы/строки «змейки» чередуют направление.
     */
    public static List<int[]> sequence(int width, int height, ModuleNumberingPattern p) {
        boolean top = p.top();
        boolean left = p.left();
        boolean byRows = p.getAxis() == ModuleNumberingPattern.Axis.ROWS;
        int outer = byRows ? height : width;
        int inner = byRows ? width : height;
        List<int[]> out = new ArrayList<>(width * height);
        for (int g = 0; g < outer; g++) {
            boolean forward = byRows ? left : top;
            if (p.isSnake() && g % 2 == 1) {
                forward = !forward;
            }
            for (int i = 0; i < inner; i++) {
                int k = forward ? i : inner - 1 - i;
                int o = byRows ? (top ? g : height - 1 - g) : (left ? g : width - 1 - g);
                out.add(byRows ? new int[]{o, k} : new int[]{k, o});
            }
        }
        return out;
    }

    /**
     * Модули карты в порядке номеров (первый — выход 1). Шаблонный порядок — по шаблону экрана; ручной —
     * {@code manualPortOrder} без повторов и без чужих/пропавших модулей, а остальные модули карты в список не
     * попадают (у них нет номера, см. {@link #unnumbered}).
     */
    public static List<String> order(ReceiverCardInstance card, Screen screen) {
        return order(card, screen, ScreenModules.index(screen));
    }

    static List<String> order(ReceiverCardInstance card, Screen screen, Map<String, CabinetInstance> index) {
        Set<String> members = new LinkedHashSet<>();
        for (String id : card.getModuleIds()) {
            CabinetInstance c = index.get(id);
            if (c != null && !c.isHidden()) {
                members.add(id);
            }
        }
        if (card.getManualPortOrder() != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (String id : card.getManualPortOrder()) {
                if (members.contains(id)) {
                    seen.add(id);
                }
            }
            return new ArrayList<>(seen);
        }
        if (members.isEmpty()) {
            return new ArrayList<>();
        }
        int r0 = Integer.MAX_VALUE;
        int c0 = Integer.MAX_VALUE;
        int r1 = Integer.MIN_VALUE;
        int c1 = Integer.MIN_VALUE;
        Map<Long, String> byCell = new java.util.HashMap<>();
        for (String id : members) {
            CabinetInstance c = index.get(id);
            r0 = Math.min(r0, c.getRowIndex());
            r1 = Math.max(r1, c.getRowIndex());
            c0 = Math.min(c0, c.getColIndex());
            c1 = Math.max(c1, c.getColIndex());
            byCell.put(((long) c.getRowIndex() << 32) | (c.getColIndex() & 0xFFFFFFFFL), id);
        }
        List<String> out = new ArrayList<>(members.size());
        for (int[] rc : sequence(c1 - c0 + 1, r1 - r0 + 1, screen.getModuleNumbering())) {
            long key = ((long) (r0 + rc[0]) << 32) | ((c0 + rc[1]) & 0xFFFFFFFFL);
            String id = byCell.get(key);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    /** Номер модуля в карте (с 1) или 0, если у модуля нет номера (ручной порядок его не включает / не член карты). */
    public static int numberOf(ReceiverCardInstance card, Screen screen, String moduleId) {
        return order(card, screen).indexOf(moduleId) + 1;
    }

    /** Модули карты без номера — при ручном порядке те, кого пользователь ещё не кликнул («?» в виде). */
    public static List<String> unnumbered(ReceiverCardInstance card, Screen screen) {
        Set<String> numbered = new HashSet<>(order(card, screen));
        List<String> out = new ArrayList<>();
        Map<String, CabinetInstance> index = ScreenModules.index(screen);
        for (String id : card.getModuleIds()) {
            CabinetInstance c = index.get(id);
            if (c != null && !c.isHidden() && !numbered.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Подпись номера модуля по шаблону Предпочтений (D14): {@code {n}} — номер выхода, {@code {card}} — имя карты
     * («К3»). Пустой шаблон — просто число; неизвестный текст остаётся как есть («J{n}» → «J1»).
     *
     * @param number номер выхода (с 1); 0 — «без номера», подпись «?»
     */
    public static String label(String format, String cardName, int number) {
        if (number <= 0) {
            return "?";
        }
        String f = format == null || format.isBlank() ? "{n}" : format;
        return f.replace("{n}", Integer.toString(number)).replace("{card}", cardName != null ? cardName : "");
    }
}
