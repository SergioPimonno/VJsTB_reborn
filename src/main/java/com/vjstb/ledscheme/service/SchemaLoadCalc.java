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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        /** Доля загрузки (1.0 = 100% ёмкости; может быть больше 1 при перегрузке) — для
         *  кругового индикатора на блоке. 0, если ёмкость неизвестна или нулевая. */
        public double fraction() {
            return capacityKnown && capacityWatts > 0 ? loadWatts / capacityWatts : 0;
        }
    }

    /** Строка «входной разъём → его вклад в ёмкость» окна «Статистика блока». */
    public record InputRow(String connectorType, int count, int phases, double ratingAmps,
                           double effectiveAmps, double capacityWatts) {
    }

    /** Строка «исходящая связь → мощность, которую она уносит». {@code targetType} нужен окну
     *  статистики, чтобы отличить экран от промежуточного узла. */
    public record OutputRow(String targetLabel, SchemaNodeType targetType, int wireCount, double watts) {
    }

    /** Полная разбивка расчёта {@link #evaluate} для окна «Статистика блока».
     *  {@code deratingPercent} — применённый запас, {@code deratingManual} — задан ли он вручную
     *  на узле (иначе — умолчание по типу узла). Входные разъёмы с нераспознанным типом в
     *  {@code inputs} не попадают (как и в ёмкость), их число — в {@code unrecognizedInputs}. */
    public record Stats(NodeLoad load, double deratingPercent, boolean deratingManual,
                        List<InputRow> inputs, int unrecognizedInputs, List<OutputRow> outputs) {
    }

    private SchemaLoadCalc() {
    }

    public static NodeLoad evaluate(SchemaNode node, Scene scene, AppModel model) {
        return stats(node, scene, model).load();
    }

    /** Тот же расчёт, что {@link #evaluate}, но с разбивкой по входным разъёмам и
     *  исходящим связям — {@code evaluate} считается ЧЕРЕЗ него, чтобы сумма строк окна
     *  «Статистика блока» всегда совпадала с цифрами индикатора на блоке. */
    public static Stats stats(SchemaNode node, Scene scene, AppModel model) {
        List<OutputRow> outputs = outputRows(node, scene, model, new HashSet<>());
        double load = 0;
        for (OutputRow r : outputs) {
            load += r.watts();
        }
        // Ручное переопределение узла — в приоритете; иначе запас по умолчанию
        // зависит от типа узла: проходные блоки (DISTRO, «Распределение») грузят
        // на 100%, вводной источник/щит (SOURCE) и остальные — на ~92.6% (см.
        // PowerCalc.defaultDeratingPercentFor).
        boolean manual = node.getLoadDeratingPercent() != null;
        double derating = manual ? node.getLoadDeratingPercent() : PowerCalc.defaultDeratingPercentFor(node.getType());
        double capacity = 0;
        List<InputRow> inputs = new ArrayList<>();
        int unrecognized = 0;
        for (CardPort p : node.getPowerConnectors()) {
            if (p.getDirection() == PortDirection.OUT) {
                continue;
            }
            Double ratingA = PowerCalc.connectorLabelAmps(p.getConnectorType());
            if (ratingA == null) {
                unrecognized++;
                continue;
            }
            double effectiveA = p.getBreakerAmps() != null ? Math.min(ratingA, p.getBreakerAmps()) : ratingA;
            int phases = Math.max(1, p.getPhaseCount());
            double rowCapacity = p.getCount() * phases * PowerCalc.capacityWatts(effectiveA, derating);
            capacity += rowCapacity;
            inputs.add(new InputRow(p.getConnectorType(), p.getCount(), phases, ratingA, effectiveA, rowCapacity));
        }
        return new Stats(new NodeLoad(load, capacity, !inputs.isEmpty()), derating, manual, inputs, unrecognized,
                outputs);
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
        double total = 0;
        for (OutputRow r : outputRows(node, scene, model, visited)) {
            total += r.watts();
        }
        return total;
    }

    /** Исходящие силовые связи узла с мощностью каждой (см. {@link #outputLoadWatts} — это его
     *  сумма). Общий {@code visited} на все связи узла сохранён намеренно: узел, до которого
     *  дошли двумя путями, считается один раз — ровно как до разбивки на строки. */
    private static List<OutputRow> outputRows(SchemaNode node, Scene scene, AppModel model, Set<String> visited) {
        List<OutputRow> rows = new ArrayList<>();
        if (!visited.add(node.getId())) {
            return rows;
        }
        for (SchemaEdge e : scene.getSchemaEdges()) {
            if (e.getMode() != SchemaMode.POWER || !node.getId().equals(e.getFromNodeId())
                    || !sameSheet(e, node)) {
                continue;
            }
            SchemaNode target = nodeById(scene, e.getToNodeId());
            if (target == null) {
                continue;
            }
            double watts;
            String label;
            if (target.getType() == SchemaNodeType.SCREEN) {
                watts = screenEdgeShareWatts(target, e, scene, model);
                Screen scr = screenById(scene, target.getScreenRefId());
                label = scr != null ? scr.getName() : "(экран удалён)";
            } else {
                watts = outputLoadWatts(target, scene, model, visited);
                label = target.getLabel() != null && !target.getLabel().isEmpty()
                        ? target.getLabel() : model.categoryLabel(target.getType());
            }
            rows.add(new OutputRow(label, target.getType(), lineCount(e), watts));
        }
        return rows;
    }

    /** Доля полной нагрузки экрана, приходящаяся на ОДНУ конкретную входящую связь —
     *  пропорционально числу линий этой связи (wireCount) от ОБЩЕГО числа линий.
     *
     * <p>Баг-репорт: "6 вводных показывают общую нагрузку в 22,4кВт, в то время как
     * в окне расключения 6 вводных в сумме дают 20,16кВт (правильное значение)" —
     * знаменатель складывался ТОЛЬКО из wireCount всех связей, заведённых на экран
     * (см. {@link #outputLoadWatts}): если хотя бы ОДНА из ДРУГИХ связей того же
     * экрана (например, с СОСЕДНЕЙ проходной) имела wireCount МЕНЬШЕ реального числа
     * заведённых через неё цепочек (частый случай — пользователь консолидирует
     * несколько автосозданных связей в одну с укрупнённым "N×тип" и забывает
     * обновить при добавлении ещё одной цепочки через ту же проходную), знаменатель
     * занижался — и ВСЕ остальные связи того же экрана (включая эту, полностью
     * корректно промаркированную) получали завышенную долю. Теперь знаменатель —
     * МАКСИМУМ из старой суммы меток И реального числа силовых цепочек экрана
     * ({@code AppModel#powerChainsTouchingScreen}) — если метки где-то отстают от
     * факта, авторитетный источник (реальные цепочки) не даёт знаменателю занизиться
     * и "утечь" на другие связи; если цепочек питания вообще ещё нет (ранний этап,
     * общая схема строится ДО прописки экрана) — сумма меток остаётся единственным
     * источником, как и раньше (иначе экран без единой цепочки делил бы 0 Вт на 0). */
    private static double screenEdgeShareWatts(SchemaNode screenNode, SchemaEdge edge, Scene scene, AppModel model) {
        Screen scr = screenById(scene, screenNode.getScreenRefId());
        if (scr == null) {
            return 0;
        }
        CabinetType defaultType = model.getWorkspace().cabinetTypeById(scr.getCabinetTypeId());
        double totalWatts = ScreenLogic.stats(scr, defaultType, model.getWorkspace()).totalPowerW();
        int totalLines = 0;
        for (SchemaEdge e : scene.getSchemaEdges()) {
            if (e.getMode() == SchemaMode.POWER && screenNode.getId().equals(e.getToNodeId())
                    && sameSheet(e, screenNode)) {
                totalLines += lineCount(e);
            }
        }
        int totalChains = model.powerChainsTouchingScreen(scr).size();
        int denominator = Math.max(totalLines, totalChains);
        if (denominator <= 0) {
            return 0;
        }
        return totalWatts * lineCount(edge) / (double) denominator;
    }

    /** Связь на том же листе, что и узел (запрос 2026-09-30, несколько схем на сцену):
     *  нагрузка считается в пределах СХЕМЫ узла — каждая схема самостоятельна
     *  (решение D1), связь соседнего листа не должна ни добавлять нагрузку щиту, ни
     *  делить долю экрана. Узел/связь без листа (сцена собрана в обход модели) —
     *  без фильтра, как до листов. */
    private static boolean sameSheet(SchemaEdge e, SchemaNode node) {
        return node.getSheetId() == null || e.getSheetId() == null || node.getSheetId().equals(e.getSheetId());
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
