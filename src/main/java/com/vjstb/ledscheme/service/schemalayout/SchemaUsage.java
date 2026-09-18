package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.SchemaEdge;
import java.util.List;

/**
 * Занятость гнёзд/узлов общей схемы связями — без Swing и без {@code AppModel},
 * чистые функции от уже готового списка связей (docs/schema-ports-rework/PLAN.md,
 * задача T2.3). Перенесено из {@code SchemaCanvasPanel} (методы {@code usedCount}/
 * {@code screenUsedCount}/{@code edgeOrdinalForPort}) БЕЗ изменения поведения — только
 * вынесено в отдельный класс, чтобы им мог пользоваться не только холст (отрисовка,
 * хит-тест, проверка ёмкости при соединении), но и {@link NodePortLayout}, который
 * ничего не знает про Swing.
 *
 * <p>Везде передаётся уже отфильтрованный по {@code SchemaMode} список связей (как
 * {@code AppModel.schemaEdgesForCurrentScene(mode)}) — эти функции сами по режиму
 * не фильтруют.
 */
public final class SchemaUsage {

    private SchemaUsage() {
    }

    /** Сколько ЛИНИЙ (с учётом {@link SchemaEdge#getWireCount()}, 1 — если не задан)
     *  уже подведено к гнезду {@code portId} связями списка, кроме {@code exclude}
     *  (переподключаемая связь сама на себя не должна давить лимит). */
    public static int usedCount(List<SchemaEdge> edges, String portId, SchemaEdge exclude) {
        int used = 0;
        for (SchemaEdge e : edges) {
            if (e == exclude) {
                continue;
            }
            if (portId.equals(e.getFromPortId()) || portId.equals(e.getToPortId())) {
                used += e.getWireCount() != null ? e.getWireCount() : 1;
            }
        }
        return used;
    }

    /** То же самое, но по УЗЛУ целиком (для связей без привязки к конкретному гнезду —
     *  например, лимит числа вводных линий узла-экрана). */
    public static int nodeUsedCount(List<SchemaEdge> edges, String nodeId, SchemaEdge exclude) {
        int used = 0;
        for (SchemaEdge e : edges) {
            if (e == exclude) {
                continue;
            }
            if (nodeId.equals(e.getFromNodeId()) || nodeId.equals(e.getToNodeId())) {
                used += e.getWireCount() != null ? e.getWireCount() : 1;
            }
        }
        return used;
    }

    /** Порядковый номер (0-based) связи {@code forEdge} среди связей списка,
     *  ссылающихся на гнездо {@code portId}, в порядке списка — нужен, чтобы в
     *  режиме "каждый разъём отдельно" несколько параллельных связей одной группы
     *  сходились на РАЗНЫЕ развёрнутые гнёзда, а не все в одну точку. Если {@code
     *  forEdge} не найдена в списке (превью ещё не созданной связи) — возвращает
     *  общее число уже существующих совпадений: такая связь встанет следующей. */
    public static int edgeOrdinalForPort(List<SchemaEdge> edges, SchemaEdge forEdge, String portId) {
        int idx = 0;
        for (SchemaEdge e : edges) {
            if (!portId.equals(e.getFromPortId()) && !portId.equals(e.getToPortId())) {
                continue;
            }
            if (e == forEdge) {
                return idx;
            }
            idx++;
        }
        return idx;
    }

    /** true, если у гнезда {@code portId} есть хотя бы одна связь — критерий "группа
     *  задействована" для авто-свёртки (PLAN.md §2.4, D5) и для "только
     *  задействованные" (PLAN.md §2.4). */
    public static boolean isPortUsed(List<SchemaEdge> edges, String portId) {
        for (SchemaEdge e : edges) {
            if (portId.equals(e.getFromPortId()) || portId.equals(e.getToPortId())) {
                return true;
            }
        }
        return false;
    }
}
