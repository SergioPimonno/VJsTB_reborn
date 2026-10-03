package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Авторасстановка готовых блоков общей схемы колонками слева направо (запрос пользователя
 * 2026-10-02) — чистая геометрия без Swing и без модели целиком. Пустые колонки пропускаются.
 *
 * <p><b>Схема СИГНАЛА:</b> серверы ({@link SchemaNodeType#SERVER}) → «другое оборудование»
 * ({@code SOURCE}, {@code DISTRO}, {@code CUSTOM}, {@code MONITOR}; в одной колонке) →
 * контроллеры → конвертеры → экраны.
 *
 * <p><b>Схема ПИТАНИЯ</b> (уточнение пользователя): источники БЕЗ входных разъёмов → источники
 * С входными разъёмами (питаются от другого источника) → распределение ({@code DISTRO}) →
 * экраны и всё остальное (одна колонка: сверху экраны, ниже прочее оборудование).
 *
 * <p>Внутри колонки блоки идут сверху вниз в порядке их ТЕКУЩЕГО положения (по Y, затем по X) —
 * расстановка не перемешивает то, что пользователь уже упорядочил по вертикали. Колонки
 * центрируются по общей горизонтальной оси, левый верхний угол всей схемы остаётся там же, где
 * был (схема не «убегает» по холсту).
 */
public final class SchemaAutoArrange {

    /** Зазор между колонками по X — место под ортогональные линии, усы и подписи связей. */
    public static final double COLUMN_GAP = 140;
    /** Зазор между блоками в колонке по Y. */
    public static final double ROW_GAP = 50;

    private static final int COLUMNS = 7;

    private SchemaAutoArrange() {
    }

    /** Есть ли у блока питания входной (или проходной) разъём — то есть он сам от кого-то питается. */
    static boolean hasPowerInput(SchemaNode n) {
        for (CardPort p : n.getPowerConnectors()) {
            if (p.getDirection() == PortDirection.IN || p.getDirection() == PortDirection.IN_OUT) {
                return true;
            }
        }
        return false;
    }

    /** Индекс колонки блока в данном режиме схемы (0..{@value #COLUMNS}-1). */
    static int columnOf(SchemaNode n, SchemaMode mode) {
        SchemaNodeType type = n.getType();
        if (mode == SchemaMode.POWER) {
            return switch (type) {
                case SOURCE -> hasPowerInput(n) ? 1 : 0;
                case DISTRO -> 2;
                default -> 3;
            };
        }
        return switch (type) {
            case SERVER -> 0;
            case SOURCE, DISTRO, CUSTOM, MONITOR -> 1;
            case CONTROLLER -> 2;
            case CONVERTER -> 3;
            case SCREEN -> 4;
        };
    }

    /** Новые координаты левого верхнего угла каждого блока; пустой список — пустой результат. */
    public static Map<SchemaNode, double[]> arrange(List<SchemaNode> nodes, SchemaMode mode) {
        Map<SchemaNode, double[]> result = new IdentityHashMap<>();
        if (nodes.isEmpty()) {
            return result;
        }
        double originX = Double.POSITIVE_INFINITY;
        double originY = Double.POSITIVE_INFINITY;
        for (SchemaNode n : nodes) {
            originX = Math.min(originX, n.getX());
            originY = Math.min(originY, n.getY());
        }

        List<List<SchemaNode>> columns = new ArrayList<>();
        for (int i = 0; i < COLUMNS; i++) {
            columns.add(new ArrayList<>());
        }
        for (SchemaNode n : nodes) {
            columns.get(columnOf(n, mode)).add(n);
        }
        Comparator<SchemaNode> byPosition = Comparator.comparingDouble(SchemaNode::getY)
                .thenComparingDouble(SchemaNode::getX);
        // питание, последняя колонка: экраны выше прочего оборудования
        Comparator<SchemaNode> screensFirst = Comparator
                .<SchemaNode>comparingInt(n -> n.getType() == SchemaNodeType.SCREEN ? 0 : 1)
                .thenComparing(byPosition);
        // сигнал, колонка «другого оборудования»: монитор/TV ниже остального
        Comparator<SchemaNode> monitorsLast = Comparator
                .<SchemaNode>comparingInt(n -> n.getType() == SchemaNodeType.MONITOR ? 1 : 0)
                .thenComparing(byPosition);
        columns.removeIf(List::isEmpty);

        double tallest = 0;
        double[] heights = new double[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            List<SchemaNode> col = columns.get(c);
            int columnIndex = columnOf(col.get(0), mode);
            col.sort(mode == SchemaMode.POWER
                    ? (columnIndex == 3 ? screensFirst : byPosition)
                    : (columnIndex == 1 ? monitorsLast : byPosition));
            double h = ROW_GAP * (col.size() - 1);
            for (SchemaNode n : col) {
                h += n.getHeight();
            }
            heights[c] = h;
            tallest = Math.max(tallest, h);
        }

        double x = originX;
        for (int c = 0; c < columns.size(); c++) {
            double y = originY + (tallest - heights[c]) / 2.0;
            double widest = 0;
            for (SchemaNode n : columns.get(c)) {
                result.put(n, new double[]{x, y});
                y += n.getHeight() + ROW_GAP;
                widest = Math.max(widest, n.getWidth());
            }
            x += widest + COLUMN_GAP;
        }
        return result;
    }
}
