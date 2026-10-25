package com.vjstb.ledscheme.service.graphimport;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphEdge;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.GraphNode;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph.Kind;
import com.vjstb.ledscheme.service.graphimport.NameMatcher.Candidate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Перенос схемы из внешнего файла на НОВЫЙ лист общей схемы: {@link #propose} предлагает, чем заменить каждый блок
 * (оборудование из библиотеки, экран проекта, заглушка, пропуск), пользователь правит выбор в диалоге, {@link #apply}
 * создаёт лист, узлы и связи одной записью отмены ({@link AppModel#runBatch}).
 *
 * <p><b>Связи и порты.</b> Во внешнем файле гнёзд нет, поэтому для каждой связи порт на каждом конце выбирается по
 * семейству разъёма ({@link EdgeHints}): общему для выходного гнезда источника и входного гнезда приёмника; если
 * подпись линии называет разъём («fiber 1», «CEE32A») — предпочитается он. Из подходящих берётся менее занятая
 * группа гнёзд. Нет общего разъёма — связь создаётся между блоками без гнёзд и попадает в отчёт. Связь, идущая
 * «против шерсти» (у источника нет подходящего выхода, а у приёмника есть), разворачивается. Всё сказанное в подписи
 * про сами порты («1-4 main / 5-8 backup») сохраняется текстом подписи, в номера портов не переводится.
 */
public final class SchemaGraphImport {

    /** Что сделать с блоком файла. */
    public enum Action {
        /** Узел из пресета библиотеки. */
        PRESET,
        /** Пустой блок с названием из файла (оборудования в библиотеке нет). */
        STUB,
        /** Узел-экран проекта. */
        SCREEN,
        /** Не переносить (связи этого блока тоже). */
        SKIP
    }

    /** План для одного блока файла; поля {@code action/preset/screen} правит диалог. */
    public static final class NodePlan {
        private final GraphNode node;
        private final List<Candidate> candidates;
        private Action action;
        private EquipmentPreset preset;
        private double presetScore;
        /** Пресет выбрал сам пользователь: подпись блока — название пресета, замечаний о приблизительности нет. */
        private boolean confirmed;
        private Screen screen;

        NodePlan(GraphNode node, List<Candidate> candidates) {
            this.node = node;
            this.candidates = candidates;
        }

        public GraphNode node() {
            return node;
        }

        /** Подходящие пресеты по убыванию оценки (пусто у картинок). */
        public List<Candidate> candidates() {
            return candidates;
        }

        public Action action() {
            return action;
        }

        public EquipmentPreset preset() {
            return preset;
        }

        public double presetScore() {
            return presetScore;
        }

        public Screen screen() {
            return screen;
        }

        public boolean confirmed() {
            return confirmed;
        }

        public void usePreset(EquipmentPreset p, double score) {
            usePreset(p, score, false);
        }

        public void usePreset(EquipmentPreset p, double score, boolean confirmed) {
            this.action = Action.PRESET;
            this.preset = p;
            this.presetScore = score;
            this.confirmed = confirmed;
            this.screen = null;
        }

        public void useStub() {
            this.action = Action.STUB;
            this.preset = null;
            this.screen = null;
        }

        public void useScreen(Screen s) {
            this.action = s == null ? Action.SKIP : Action.SCREEN;
            this.screen = s;
            this.preset = null;
        }

        public void skip() {
            this.action = Action.SKIP;
            this.preset = null;
            this.screen = null;
        }
    }

    /** Итог переноса для отчёта. */
    public record Result(String sheetName, int nodes, int screens, int stubs, int edges, int edgesWithPorts,
                         int edgesDropped, List<String> notes) {
    }

    /** Масштаб расстояний yEd → наши блоки (блок с гнёздами крупнее прямоугольника yEd). */
    private static final double SPACING = 1.5;
    private static final double ORIGIN = 60;

    private SchemaGraphImport() {
    }

    /** Предложение по каждому блоку: оборудование — лучший пресет библиотеки с оценкой от
     *  {@link NameMatcher#PRESELECT_SCORE}, иначе заглушка; картинки — пропуск (экран пользователь выбирает сам);
     *  группы-папки в план не попадают. */
    public static List<NodePlan> propose(ImportedGraph graph, SchemaMode mode, Collection<EquipmentPreset> library) {
        List<NodePlan> plans = new ArrayList<>();
        for (GraphNode n : graph.nodes()) {
            if (n.kind() == Kind.GROUP) {
                continue;
            }
            if (n.kind() == Kind.IMAGE) {
                NodePlan p = new NodePlan(n, List.of());
                p.skip();
                plans.add(p);
                continue;
            }
            List<Candidate> cands = NameMatcher.rank(n.label(), library, mode, 6);
            NodePlan p = new NodePlan(n, cands);
            if (!cands.isEmpty() && cands.get(0).score() >= NameMatcher.PRESELECT_SCORE) {
                p.usePreset(cands.get(0).preset(), cands.get(0).score());
            } else {
                p.useStub();
            }
            plans.add(p);
        }
        return plans;
    }

    /** Создаёт лист {@code sheetName} (при занятом названии добавляется номер), узлы и связи. Одна запись отмены. */
    public static Result apply(AppModel model, SchemaMode mode, String sheetName, ImportedGraph graph,
            List<NodePlan> plans) {
        return apply(model, mode, sheetName, graph, plans, null);
    }

    /** То же; {@code fitter} подгоняет размеры блоков под содержимое ТАК ЖЕ, как кнопка «Подогнать блоки» (по шрифтам
     *  и настройкам отображения групп гнёзд холста — знает только окно); {@code null} — подгонка модели по умолчанию,
     *  в ней гнёзда раскладываются по строке на каждое и блоки получаются заметно выше. */
    public static Result apply(AppModel model, SchemaMode mode, String sheetName, ImportedGraph graph,
            List<NodePlan> plans, java.util.function.Consumer<List<SchemaNode>> fitter) {
        return model.runBatch("Импорт схемы из файла",
                () -> doApply(model, mode, sheetName, graph, plans, fitter));
    }

    private static Result doApply(AppModel model, SchemaMode mode, String sheetName, ImportedGraph graph,
            List<NodePlan> plans, java.util.function.Consumer<List<SchemaNode>> fitter) {
        List<String> notes = new ArrayList<>();
        String name = uniqueSheetName(model, mode, sheetName);
        SchemaSheet sheet = model.addSchemaSheet(mode, name);

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (NodePlan p : plans) {
            if (p.action() != Action.SKIP) {
                minX = Math.min(minX, p.node().x());
                minY = Math.min(minY, p.node().y());
            }
        }
        Map<String, SchemaNode> created = new HashMap<>();
        int nodes = 0;
        int screens = 0;
        int stubs = 0;
        int imageStubs = 0;
        for (NodePlan p : plans) {
            if (p.action() == Action.SKIP) {
                continue;
            }
            GraphNode g = p.node();
            double x = ORIGIN + (g.x() - minX) * SPACING;
            double y = ORIGIN + (g.y() - minY) * SPACING;
            SchemaNode node;
            switch (p.action()) {
                case PRESET -> {
                    node = model.addSchemaNodeFromPreset(mode, p.preset(), x, y);
                    // «FOH UPS» вместо «UPS FOH»: при равнозначном совпадении остаётся название автора схемы;
                    // при приблизительном — название пресета, иначе подпись врала бы про состав разъёмов
                    if (p.confirmed()) {
                        // выбрано вручную — оборудование названо по пресету
                    } else if (!g.label().isBlank() && p.presetScore() >= NameMatcher.AUTO_SCORE) {
                        node.setLabel(g.label());
                    } else if (p.presetScore() < NameMatcher.AUTO_SCORE) {
                        notes.add("«" + g.label() + "» → «" + p.preset().getName() + "» (приблизительное совпадение)");
                    }
                }
                case SCREEN -> {
                    node = model.addSchemaNode(mode, SchemaNodeType.SCREEN, p.screen().getName(), x, y,
                            p.screen().getId());
                    screens++;
                }
                default -> {
                    node = model.addSchemaNode(mode, guessType(g.label()), g.label().isBlank() ? "Блок" : g.label(),
                            x, y, null);
                    // размер блока файла, но не больше разумного: картинка экрана в файле может быть в несколько раз больше блока
                    node.setWidth(Math.max(120, Math.min(260, g.width())));
                    node.setHeight(Math.max(44, Math.min(100, g.height())));
                    stubs++;
                    if (g.kind() == Kind.IMAGE) {
                        imageStubs++;
                    } else {
                        notes.add("«" + g.label() + "» — оборудования с таким названием нет в библиотеке, создан пустой блок");
                    }
                }
            }
            created.put(g.id(), node);
            nodes++;
        }

        Map<String, Integer> used = new HashMap<>();
        java.util.Set<String> wired = new java.util.HashSet<>(); // узлы, у которых есть связь с гнездом
        int edges = 0;
        int withPorts = 0;
        int dropped = 0;
        for (GraphEdge e : graph.edges()) {
            SchemaNode from = created.get(e.sourceId());
            SchemaNode to = created.get(e.targetId());
            if (from == null || to == null || from == to) {
                dropped++;
                continue;
            }
            String hint = EdgeHints.familyOfLabel(e.label());
            CardPort[] pair = pickPorts(from, to, hint, used);
            if (pair == null) {
                pair = pickPorts(to, from, hint, used);
                if (pair != null) {
                    SchemaNode t = from;
                    from = to;
                    to = t;
                }
            }
            String fromPort = pair != null ? pair[0].getId() : null;
            String toPort = pair != null ? pair[1].getId() : null;
            if (pair == null && (hasPorts(from) && hasPorts(to))) {
                notes.add("Связь «" + from.getLabel() + "» → «" + to.getLabel() + "»: нет общего разъёма, создана без гнёзд");
            }
            SchemaEdge edge = model.addSchemaEdge(mode, from.getId(), fromPort, null, to.getId(), toPort, null,
                    e.label(), EdgeRouteMode.AUTO);
            if (e.colorRgb() != null && e.colorRgb() != 0) {
                edge.setColor(e.colorRgb());
            }
            if (e.dashed()) {
                edge.setDashed(true);
            }
            edges++;
            if (pair != null) {
                withPorts++;
                used.merge(pair[0].getId(), 1, Integer::sum);
                used.merge(pair[1].getId(), 1, Integer::sum);
                wired.add(from.getId());
                wired.add(to.getId());
            }
        }
        // блок библиотеки со всеми гнёздами (H2 — две карты по 16 Ethernet) в несколько раз выше схемы в файле: на
        // перенесённой схеме показываем только подключённые гнёзда (остальные свёрнуты в строку «ещё N»), блок
        // подгоняется под это и затем блоки разводятся без наложений
        List<SchemaNode> toFit = new ArrayList<>();
        for (NodePlan p : plans) {
            SchemaNode n = created.get(p.node().id());
            // блок без единой связи с гнездом свёртывать нечего: все гнёзда стали бы «ещё N», а блок — крошечным
            if (p.action() == Action.PRESET && n != null && hasPorts(n) && wired.contains(n.getId())) {
                n.setOnlyUsedPorts(true);
                toFit.add(n);
            }
        }
        if (fitter != null) {
            fitter.accept(toFit);
        } else {
            toFit.forEach(model::autoFitNodeToPorts);
        }
        separateOverlapping(new ArrayList<>(created.values()));
        if (imageStubs > 0) {
            notes.add("Картинки экранов перенесены пустыми блоками (" + imageStubs + " шт.): экраны этого проекта на них не"
                    + " назначены — создайте экраны и выберите их при импорте, либо замените тип блока на «Экран»");
        }
        if (dropped > 0) {
            notes.add("Связей пропущено вместе с их блоками: " + dropped);
        }
        return new Result(sheet.getName(), nodes, screens, stubs, edges, withPorts, dropped, notes);
    }

    /** Зазор между блоками при разведении, px. */
    private static final double GAP = 50;

    /** Блоки библиотеки с гнёздами заметно крупнее прямоугольников yEd (и бывают в несколько раз выше), поэтому
     *  перенесённое по координатам файла налезало бы друг на друга. Блоки по порядку сверху вниз сдвигаются ВНИЗ ровно
     *  настолько, чтобы не перекрываться (с зазором {@link #GAP}); положение по горизонтали и порядок сверху вниз
     *  сохраняются, так что схема читается так же, как в файле. */
    static void separateOverlapping(List<SchemaNode> nodes) {
        List<SchemaNode> sorted = new ArrayList<>(nodes);
        sorted.sort(java.util.Comparator.comparingDouble(SchemaNode::getY).thenComparingDouble(SchemaNode::getX));
        List<SchemaNode> placed = new ArrayList<>();
        for (SchemaNode b : sorted) {
            boolean moved = true;
            int guard = 0;
            while (moved && guard++ < 200) {
                moved = false;
                for (SchemaNode a : placed) {
                    boolean overlapX = a.getX() < b.getX() + b.getWidth() + GAP && b.getX() < a.getX() + a.getWidth() + GAP;
                    boolean overlapY = a.getY() < b.getY() + b.getHeight() + GAP && b.getY() < a.getY() + a.getHeight() + GAP;
                    if (overlapX && overlapY) {
                        b.setY(a.getY() + a.getHeight() + GAP);
                        moved = true;
                    }
                }
            }
            placed.add(b);
        }
    }

    private static String uniqueSheetName(AppModel model, SchemaMode mode, String wanted) {
        String base = wanted == null || wanted.isBlank() ? "Импорт" : wanted.trim();
        if (model.schemaSheetNameProblem(mode, base, null) == null) {
            return base;
        }
        for (int i = 2; ; i++) {
            String c = base + " " + i;
            if (model.schemaSheetNameProblem(mode, c, null) == null) {
                return c;
            }
        }
    }

    /** Тип пустого блока по названию: монитор, сервер, электрощит («ELECTRIC SHIELD 125A+63A») и «щит-преобразователь»
     *  («Socapex->6x16A», «32A->6x16A») — иначе прочее оборудование. */
    private static SchemaNodeType guessType(String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (l.contains("monitor") || l.contains("монитор")) {
            return SchemaNodeType.MONITOR;
        }
        if (l.contains("server") || l.contains("сервер")) {
            return SchemaNodeType.SERVER;
        }
        if (l.contains("shield") || l.contains("щит")) {
            return SchemaNodeType.SOURCE;
        }
        if (l.contains("->") || l.contains("=>")) {
            return SchemaNodeType.DISTRO;
        }
        return SchemaNodeType.CUSTOM;
    }

    // ---- выбор гнёзд ----

    private static boolean hasPorts(SchemaNode n) {
        return !allPorts(n).isEmpty();
    }

    private static List<CardPort> allPorts(SchemaNode n) {
        List<CardPort> out = new ArrayList<>();
        for (SchemaCard c : n.getCards()) {
            out.addAll(c.getPorts());
        }
        out.addAll(n.getPowerConnectors());
        return out;
    }

    /** Пара {выход источника, вход приёмника} одного семейства разъёма или {@code null}. Узел-экран и блок без гнёзд
     *  пары не дают (связь пойдёт между блоками). Предпочтение: семейство из подписи, потом менее занятые гнёзда. */
    private static CardPort[] pickPorts(SchemaNode from, SchemaNode to, String hint, Map<String, Integer> used) {
        if (from.getType() == SchemaNodeType.SCREEN || to.getType() == SchemaNodeType.SCREEN) {
            return null;
        }
        List<CardPort> outs = new ArrayList<>();
        for (CardPort p : allPorts(from)) {
            if (p.getDirection() == PortDirection.OUT || p.getDirection() == PortDirection.IN_OUT) {
                outs.add(p);
            }
        }
        List<CardPort> ins = new ArrayList<>();
        for (CardPort p : allPorts(to)) {
            if (p.getDirection() == PortDirection.IN || p.getDirection() == PortDirection.IN_OUT) {
                ins.add(p);
            }
        }
        CardPort[] best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (CardPort o : outs) {
            String fam = EdgeHints.familyOfConnector(o.getConnectorType());
            if (fam.isEmpty()) {
                continue;
            }
            for (CardPort i : ins) {
                if (!fam.equals(EdgeHints.familyOfConnector(i.getConnectorType()))) {
                    continue;
                }
                double s = (hint != null && hint.equals(fam) ? 100 : 0) - load(o, used) - load(i, used);
                if (s > bestScore) {
                    bestScore = s;
                    best = new CardPort[]{o, i};
                }
            }
        }
        return best;
    }

    /** Занятость группы гнёзд: связей на гнездо (группа из N гнёзд вмещает N связей до «перегруза»). */
    private static double load(CardPort p, Map<String, Integer> used) {
        return (double) used.getOrDefault(p.getId(), 0) / Math.max(1, p.getCount());
    }
}
