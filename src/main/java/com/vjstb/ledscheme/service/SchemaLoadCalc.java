package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import java.util.HashSet;
import java.util.Set;

/**
 * Сквозной расчёт нагрузки по силовой общей схеме (Task #87): для каждого силового
 * узла (щит/дистрибьютор) сравнивает суммарную нагрузку, уходящую через его
 * исходящие связи, с ёмкостью его входных разъёмов (см. PowerCalc, Task #80/#86).
 * Разбивка по фазам/цепочкам одного экрана уже проверяется отдельно и точнее на
 * уровне цепочки (см. Task #81) — здесь важна только суммарная мощность блока.
 */
public final class SchemaLoadCalc {

    /** capacityKnown=false — среди входных разъёмов узла нет ни одного распознанного
     *  типа (см. PowerCalc.connectorLabelAmps), контроль для узла не проводится. */
    public record NodeLoad(double loadWatts, double capacityWatts, boolean capacityKnown) {
        public boolean overloaded() {
            return capacityKnown && loadWatts > capacityWatts;
        }
    }

    private SchemaLoadCalc() {
    }

    public static NodeLoad evaluate(SchemaNode node, Scene scene, AppModel model) {
        double load = outputLoadWatts(node, scene, model, new HashSet<>());
        double capacity = 0;
        boolean any = false;
        for (CardPort p : node.getPowerConnectors()) {
            if (p.getDirection() != PortDirection.IN) {
                continue;
            }
            Double ratingA = PowerCalc.connectorLabelAmps(p.getConnectorType());
            if (ratingA == null) {
                continue;
            }
            double effectiveA = p.getBreakerAmps() != null ? Math.min(ratingA, p.getBreakerAmps()) : ratingA;
            double derating = node.getLoadDeratingPercent() != null
                    ? node.getLoadDeratingPercent() : PowerCalc.DEFAULT_DERATING_PERCENT;
            int phases = Math.max(1, p.getPhaseCount());
            capacity += p.getCount() * phases * PowerCalc.capacityWatts(effectiveA, derating);
            any = true;
        }
        return new NodeLoad(load, capacity, any);
    }

    /** Суммарная нагрузка (Вт), уходящая через все ИСХОДЯЩИЕ связи узла — рекурсивно
     *  для промежуточных силовых узлов. Экран может быть запитан сразу с НЕСКОЛЬКИХ
     *  разных проходных/щитов (разные линии/фазы с разных путей) — в этом случае
     *  каждая входящая в экран связь получает лишь СВОЮ долю полной нагрузки экрана,
     *  пропорционально числу линий связи (wireCount), а не всю нагрузку целиком (см.
     *  {@link #screenEdgeShareWatts}) — иначе на КАЖДОЙ проходной срабатывало бы
     *  ложное предупреждение о перегрузке, хотя по факту она несёт только часть.
     *  visited защищает от случайного цикла в пользовательской схеме. */
    private static double outputLoadWatts(SchemaNode node, Scene scene, AppModel model, Set<String> visited) {
        if (!visited.add(node.getId())) {
            return 0;
        }
        double total = 0;
        for (SchemaEdge e : scene.getSchemaEdges()) {
            if (e.getMode() != SchemaMode.POWER || !node.getId().equals(e.getFromNodeId())) {
                continue;
            }
            SchemaNode target = nodeById(scene, e.getToNodeId());
            if (target == null) {
                continue;
            }
            if (target.getType() == SchemaNodeType.SCREEN) {
                total += screenEdgeShareWatts(target, e, scene, model);
            } else {
                total += outputLoadWatts(target, scene, model, visited);
            }
        }
        return total;
    }

    /** Доля полной нагрузки экрана, приходящаяся на ОДНУ конкретную входящую связь —
     *  пропорционально числу линий этой связи (wireCount) от суммарного числа линий,
     *  заведённых на экран отовсюду (см. {@link #outputLoadWatts}). */
    private static double screenEdgeShareWatts(SchemaNode screenNode, SchemaEdge edge, Scene scene, AppModel model) {
        Screen scr = screenById(scene, screenNode.getScreenRefId());
        if (scr == null) {
            return 0;
        }
        CabinetType defaultType = model.getWorkspace().cabinetTypeById(scr.getCabinetTypeId());
        double totalWatts = ScreenLogic.stats(scr, defaultType, model.getWorkspace()).totalPowerW();
        int totalLines = 0;
        for (SchemaEdge e : scene.getSchemaEdges()) {
            if (e.getMode() == SchemaMode.POWER && screenNode.getId().equals(e.getToNodeId())) {
                totalLines += lineCount(e);
            }
        }
        if (totalLines <= 0) {
            return 0;
        }
        return totalWatts * lineCount(edge) / (double) totalLines;
    }

    private static int lineCount(SchemaEdge edge) {
        Integer n = edge.getWireCount();
        return n != null && n > 0 ? n : 1;
    }

    private static Screen screenById(Scene scene, String id) {
        if (id == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private static SchemaNode nodeById(Scene scene, String id) {
        if (id == null) {
            return null;
        }
        for (SchemaNode n : scene.getSchemaNodes()) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }
}
