package com.vjstb.ledscheme.service.graphimport;

import java.util.List;

/**
 * Схема, прочитанная из внешнего файла (сейчас — yEd {@code .graphml}), в нейтральном виде: блоки с подписью и
 * геометрией и связи с подписью/цветом. Всё, что дальше (подбор оборудования, перенос на лист общей схемы), работает
 * только с этим видом и не знает про формат файла — поэтому позже тот же перенос можно подключить к другому читателю
 * (например, векторному PDF).
 *
 * <p>Гнёзд во внешних схемах обычно нет (в yEd связь крепится к блоку целиком): всё, что относится к портам, лежит в
 * подписи линии («fiber 1», «1-4 main / 5-8 backup», «CEE32A»), её разбирает {@link EdgeHints}.
 */
public record ImportedGraph(String name, List<GraphNode> nodes, List<GraphEdge> edges) {

    /** Что представляет блок в исходном файле. */
    public enum Kind {
        /** Блок с названием оборудования (прямоугольник и т.п.). */
        EQUIPMENT,
        /** Картинка (в схемах пользователя — рисунок экрана): сопоставляется с экраном проекта. */
        IMAGE,
        /** Папка/группа yEd: на схему не переносится, только объединяет вложенные блоки. */
        GROUP
    }

    /** Блок. {@code x, y, width, height} — абсолютные координаты в файле; {@code label} — подпись, строки через пробел. */
    public record GraphNode(String id, String parentId, Kind kind, String label, double x, double y, double width,
                            double height, Integer fillRgb) {
    }

    /** Связь от {@code sourceId} к {@code targetId}; {@code label} — строки подписи через « / »; {@code colorRgb} —
     *  цвет линии ({@code null} — не задан); {@code arrowAtTarget} — стрелка на конце у приёмника. */
    public record GraphEdge(String sourceId, String targetId, String label, Integer colorRgb, boolean dashed,
                            boolean arrowAtSource, boolean arrowAtTarget) {
    }

    public GraphNode nodeById(String id) {
        for (GraphNode n : nodes) {
            if (n.id().equals(id)) {
                return n;
            }
        }
        return null;
    }
}
