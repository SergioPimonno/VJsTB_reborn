package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.NodeSide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Куда попадёт группа гнёзд, если её сейчас отпустить — чистая функция без Swing
 * (docs/schema-ports-rework/PLAN.md, задача T3.3, "перетаскивание группы": "расчёт
 * стороны и позиции вставки по точке отпускания — чистая функция в schemalayout, с
 * тестом"). Результат передаётся как есть в {@code AppModel#setPortPlacement}.
 *
 * <p>Сторона определяется углом от центра узла к точке (тот же приём, что в yEd/
 * большинстве редакторов диаграмм для "куда бросили над блоком") — работает и для
 * точки ВНУТРИ узла, и слегка СНАРУЖИ (палец/курсор обычно проезжает мимо рамки),
 * не требует точного попадания в конкретный пиксель границы. Порядок — по проекции
 * точки на продольную ось выбранной стороны, среди уже стоящих там ДРУГИХ групп
 * (перетаскиваемая своя группа в этот список не входит, см. {@link #orderFor}).
 */
public final class GroupDropTarget {

    private GroupDropTarget() {
    }

    /** Сторона рамки — та из четырёх, к чьей воображаемой ПРОДОЛЖЕННОЙ линии
     *  направление "центр узла → точка" ближе всего (эквивалентно: узел разбит по
     *  двум диагоналям на 4 треугольника, точка определяет, в каком секторе она
     *  оказалась) — устойчиво для точки как внутри, так и в разумных пределах
     *  СНАРУЖИ прямоугольника {@code (nodeX,nodeY,nodeW,nodeH)}. */
    public static NodeSide sideFor(double nodeX, double nodeY, double nodeW, double nodeH,
                                    double dropX, double dropY) {
        double cx = nodeX + nodeW / 2.0;
        double cy = nodeY + nodeH / 2.0;
        double dx = dropX - cx;
        double dy = dropY - cy;
        if (nodeW <= 0 || nodeH <= 0) {
            return Math.abs(dx) >= Math.abs(dy) ? (dx < 0 ? NodeSide.LEFT : NodeSide.RIGHT)
                    : (dy < 0 ? NodeSide.TOP : NodeSide.BOTTOM);
        }
        boolean horizontal = Math.abs(dx) * nodeH > Math.abs(dy) * nodeW;
        return horizontal ? (dx < 0 ? NodeSide.LEFT : NodeSide.RIGHT)
                : (dy < 0 ? NodeSide.TOP : NodeSide.BOTTOM);
    }

    /** {@code order} для {@code AppModel#setPortPlacement} — вставляет перетаскиваемую
     *  группу МЕЖДУ соседями по продольной координате точки отпускания, не трогая
     *  порядок остальных групп стороны. {@code null}, если на стороне ещё нет ни
     *  одной ДРУГОЙ группы (естественный порядок и так поставит её первой — лишняя
     *  запись не нужна, см. {@link com.vjstb.ledscheme.model.PortPlacement#isEmpty()}).
     *
     * @param otherPins  пины ЦЕЛЕВОГО узла на стороне {@code side}, ИСКЛЮЧАЯ пины
     *                   самой перетаскиваемой группы (по {@link CardPort} identity) —
     *                   вызывающий код фильтрует {@link NodePortLayout.Result#pins()}
     *                   по стороне и группе один раз перед вызовом.
     * @param nodeX/Y    левый верхний угол целевого узла — точки в {@code otherPins}
     *                   заданы в его собственных координатах {@code [0,w]×[0,h]}. */
    public static Double orderFor(NodeSide side, double nodeX, double nodeY,
                                   List<NodePortLayout.Pin> otherPins, double dropX, double dropY) {
        boolean horizontal = side == NodeSide.TOP || side == NodeSide.BOTTOM;
        double along = horizontal ? dropX - nodeX : dropY - nodeY;

        // Одна представительная along-координата на группу (минимум среди её
        // слотов — она же координата первого пина группы, т.к. слоты идут подряд
        // по возрастанию along, см. NodePortLayout.layoutSide).
        Map<CardPort, Double> alongByGroup = new LinkedHashMap<>();
        for (NodePortLayout.Pin p : otherPins) {
            if (p.side() != side) {
                continue;
            }
            double a = horizontal ? p.x() : p.y();
            alongByGroup.merge(p.port(), a, Math::min);
        }
        if (alongByGroup.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(alongByGroup.values());
        sorted.sort(Double::compareTo);

        int insertAt = 0;
        while (insertAt < sorted.size() && sorted.get(insertAt) < along) {
            insertAt++;
        }
        double before = insertAt - 1 >= 0 ? insertAt - 1 : -1;
        double after = insertAt < sorted.size() ? insertAt : sorted.size();
        return (before + after) / 2.0;
    }
}
