package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Раскладка гнёзд узла общей схемы на РАМКЕ блока (docs/schema-ports-rework/
 * PLAN.md, задача T2.1/§2.4) — без Swing, без {@code AppModel}: чистая функция от
 * уже готовых данных (карты/разъёмы узла, связи сцены, ручные переопределения,
 * библиотека). Общая геометрия для отрисовки, хит-теста клика, привязки конца
 * линии связи и автоподгона размера — раньше это было четыре независимые копии
 * логики в {@code SchemaCanvasPanel}/{@code AppModel}, тихо разошедшиеся местами
 * (см. DIALOG.md, реплика 1, п.5).
 *
 * <p>Иерархия результата: сторона рамки → отсек карты (может быть без шапки) →
 * группа разъёмов ({@link CardPort}) → одно или несколько гнёзд-пинов. Роль и
 * транзит каждой группы разрешаются через {@link PortRoleResolver}/{@link
 * ThruResolver}, сторона — через {@link SideRules}; здесь только раскладка вдоль
 * найденной стороны и расчёт минимального размера блока.
 *
 * <p>{@link #minimumSize} и {@link #layout} делят один и тот же внутренний проход
 * ({@link #resolve}/{@link #layoutSide}) — минимальный размер это просто раскладка
 * с БЕСКОНЕЧНО доступной длиной каждой стороны (ничего не обрезается в overflow),
 * а обычная раскладка — то же самое с реальной длиной стороны текущего блока.
 */
public final class NodePortLayout {

    private NodePortLayout() {
    }

    /** Одна "карта" узла для целей раскладки: для СИГНАЛА — одна {@code
     *  SchemaCard} ({@code cardId}/{@code cardName} реальные); для ПИТАНИЯ — ОДНА
     *  синтетическая запись на весь узел ({@code cardId == null}, {@code
     *  ports == node.getPowerConnectors()}) — у разъёмов питания нет карт. */
    public record CardGroup(String cardId, String cardName, List<CardPort> ports) {
    }

    /** Один гнездо-пин на рамке. {@code slotIndex}/{@code slotCount} — 0 и 1 для
     *  свёрнутой группы; при развёрнутой — порядковый номер (для подписи "#N") и
     *  общее число слотов группы. Координаты — на границе прямоугольника
     *  {@code (0,0)-(w,h)} блока. */
    public record Pin(CardPort port, String cardId, int slotIndex, int slotCount, PortDirection direction,
                       InterfaceRole role, boolean thru, NodeSide side, double x, double y) {
    }

    /** Один отсек — подряд идущие на ОДНОЙ стороне гнёзда ОДНОЙ карты. {@code
     *  label} — {@code null}, если шапка не рисуется (единственная карта узла) или
     *  у карты нет имени; {@code mergedCount > 1} — это слитые незадействованные
     *  одинаковые карты ("Имя ×N"), а НЕ N реальных карт с гнёздами каждой. */
    public record Bay(NodeSide side, String cardId, String label, int mergedCount,
                       double alongStart, double alongEnd) {
    }

    /** Итоговая строка "ещё ..." на сторону при включённом {@code onlyUsedPorts},
     *  либо "+N ещё…" — та же группа не поместилась целиком в заданный размер
     *  блока (символично прежнему поведению до переработки). */
    public record Overflow(NodeSide side, String text) {
    }

    public record Size(double width, double height) {
    }

    public record Result(List<Pin> pins, List<Bay> bays, List<Overflow> overflow, Size size) {
    }

    /** @param defaultCollapsed глобальное умолчание для НЕпереопределённых вручную
     *                          групп (см. {@code PortPlacement#getCollapsed()},
     *                          старшинство у него) — {@code TRUE} держит группу
     *                          свёрнутой всегда ({@code GroupDisplayMode
     *                          #ALWAYS_COLLAPSED}), {@code FALSE} — всегда
     *                          развёрнутой ({@code #ALWAYS_EXPANDED}), {@code null}
     *                          — авто: свёрнута, только пока не задействована
     *                          ({@code #AUTO}). Вызывающий UI-код конвертирует enum
     *                          в это значение — {@code service.schemalayout} не
     *                          зависит от пакета {@code settings}. */
    public record Input(SchemaMode mode, SchemaNodeType nodeType, NodeOrientation orientation,
                         List<CardGroup> cardGroups, List<SchemaEdge> modeEdges, List<PortPlacement> placements,
                         boolean onlyUsedPorts, Boolean defaultCollapsed, List<InterfaceType> library,
                         TextMeasure textMeasure) {
    }

    /** Минимальный размер, вмещающий ВСЕ видимые гнёзда без наложений — не зависит
     *  от текущего размера узла (только растит его, см. {@code
     *  AppModel#autoFitNodeToPorts}, T2.2). */
    public static Size minimumSize(Input in) {
        Resolved r = resolve(in);
        double topDepth = reservedDepth(r, NodeSide.TOP);
        double bottomDepth = reservedDepth(r, NodeSide.BOTTOM);
        Map<NodeSide, SideOutcome> outcomes = new EnumMap<>(NodeSide.class);
        for (NodeSide side : NodeSide.values()) {
            outcomes.put(side, layoutSide(side, r, in, Double.POSITIVE_INFINITY, 0, 0, false, topDepth));
        }
        return sizeFromOutcomes(outcomes, bottomDepth);
    }

    /** Полная раскладка на ЗАДАННЫЙ размер блока — если он меньше {@link
     *  #minimumSize}, часть гнёзд не поместится и попадёт в overflow (как раньше
     *  "+N ещё…"), поведение симметрично для всех четырёх сторон. */
    public static Result layout(Input in, double width, double height) {
        Resolved r = resolve(in);
        double topDepth = reservedDepth(r, NodeSide.TOP);
        double bottomDepth = reservedDepth(r, NodeSide.BOTTOM);
        List<Pin> pins = new ArrayList<>();
        List<Bay> bays = new ArrayList<>();
        List<Overflow> overflow = new ArrayList<>();
        for (NodeSide side : NodeSide.values()) {
            // LEFT/RIGHT сравнивают along (который у них СТАРТУЕТ с TITLE_BAND +
            // topDepth, см. layoutSide) с высотой блока ЗА ВЫЧЕТОМ bottomDepth —
            // иначе их гнёзда/шапки карт наезжали бы на полосу гнёзд BOTTOM снизу
            // (см. DIALOG.md/PLAN.md, задача T3.2, найдено пиксельным просмотром
            // рендера — genlock IN сверху и header карты вставали друг на друга).
            double avail = side == NodeSide.TOP || side == NodeSide.BOTTOM ? width : height - bottomDepth;
            SideOutcome o = layoutSide(side, r, in, avail, width, height, true, topDepth);
            pins.addAll(o.pins);
            bays.addAll(o.bays);
            overflow.addAll(o.overflow);
        }
        return new Result(pins, bays, overflow, new Size(width, height));
    }

    /** {@code HORIZONTAL_SIDE_DEPTH}, если у этой стороны вообще есть видимое
     *  содержимое (пины или сводка "ещё…" при {@code onlyUsedPorts}), иначе 0 —
     *  используется ДО вызова {@link #layoutSide} для этой же стороны (не из уже
     *  посчитанного {@link SideOutcome}), чтобы LEFT/RIGHT знали о зарезервированной
     *  под TOP/BOTTOM полосе независимо от порядка обхода {@link NodeSide#values()}. */
    private static double reservedDepth(Resolved r, NodeSide side) {
        boolean hasContent = !r.bySide.getOrDefault(side, List.of()).isEmpty()
                || !r.overflowBySide.getOrDefault(side, List.of()).isEmpty();
        return hasContent ? SchemaLayoutMetrics.HORIZONTAL_SIDE_DEPTH : 0;
    }

    private static Size sizeFromOutcomes(Map<NodeSide, SideOutcome> outcomes, double bottomDepth) {
        double leftDepth = Math.max(SchemaLayoutMetrics.VERTICAL_SIDE_DEPTH_MIN,
                outcomes.get(NodeSide.LEFT).maxLabelWidth + SchemaLayoutMetrics.LABEL_PAD * 2);
        double rightDepth = Math.max(SchemaLayoutMetrics.VERTICAL_SIDE_DEPTH_MIN,
                outcomes.get(NodeSide.RIGHT).maxLabelWidth + SchemaLayoutMetrics.LABEL_PAD * 2);

        double minWidth = Math.max(SchemaLayoutMetrics.MIN_SIZE, leftDepth + rightDepth
                + Math.max(outcomes.get(NodeSide.TOP).alongUsed, outcomes.get(NodeSide.BOTTOM).alongUsed));
        // LEFT/RIGHT alongUsed уже НАЧИНАЕТСЯ с TITLE_BAND + topDepth (см.
        // layoutSide) — не прибавляем topDepth здесь ещё раз, только bottomDepth
        // (он не входит в along, добавлен отдельно через уменьшенный avail в layout()).
        double minHeight = Math.max(SchemaLayoutMetrics.MIN_SIZE, bottomDepth
                + Math.max(outcomes.get(NodeSide.LEFT).alongUsed, outcomes.get(NodeSide.RIGHT).alongUsed));
        return new Size(minWidth, minHeight);
    }

    // ---- разрешение роли/стороны/транзита/занятости + "только задействованные" ----

    private record ResolvedGroup(VisibleCard card, CardPort port, PortPlacement placement, InterfaceRole role,
                                  boolean thru, NodeSide side, boolean used) {
    }

    private record Resolved(boolean suppressHeaders, Map<NodeSide, List<ResolvedGroup>> bySide,
                             Map<NodeSide, List<Overflow>> overflowBySide) {
    }

    private static Resolved resolve(Input in) {
        Map<String, PortPlacement> placementByPort = new LinkedHashMap<>();
        for (PortPlacement p : in.placements()) {
            placementByPort.put(p.getPortId(), p);
        }
        List<VisibleCard> visibleCards = mergeUnusedIdenticalCards(in.cardGroups(), in.modeEdges());
        boolean suppressHeaders = in.cardGroups().size() <= 1;

        List<ResolvedGroup> all = new ArrayList<>();
        for (VisibleCard vc : visibleCards) {
            for (CardPort port : vc.ports()) {
                PortPlacement placement = placementByPort.get(port.getId());
                InterfaceRole role = PortRoleResolver.resolve(in.mode(), in.nodeType(), port, placement, in.library());
                boolean thru = ThruResolver.isThru(port, vc.ports(), placement);
                NodeSide side = SideRules.sideFor(role, port.getDirection(), thru, in.orientation(), placement);
                boolean used = SchemaUsage.isPortUsed(in.modeEdges(), port.getId());
                all.add(new ResolvedGroup(vc, port, placement, role, thru, side, used));
            }
        }

        Map<NodeSide, List<Overflow>> overflowBySide = new EnumMap<>(NodeSide.class);
        List<ResolvedGroup> visible;
        if (in.onlyUsedPorts()) {
            visible = new ArrayList<>();
            Map<VisibleCard, Boolean> cardHasAnyUsed = new LinkedHashMap<>();
            for (ResolvedGroup g : all) {
                cardHasAnyUsed.merge(g.card, g.used, Boolean::logicalOr);
            }
            Map<NodeSide, Integer> hiddenWholeCards = new EnumMap<>(NodeSide.class);
            Map<VisibleCard, Boolean> countedWholeCard = new LinkedHashMap<>();
            Map<NodeSide, List<String>> hiddenPartialGroups = new EnumMap<>(NodeSide.class);
            for (ResolvedGroup g : all) {
                if (g.used) {
                    visible.add(g);
                    continue;
                }
                boolean wholeCardHidden = !cardHasAnyUsed.getOrDefault(g.card, false);
                if (wholeCardHidden) {
                    if (!countedWholeCard.getOrDefault(g.card, false)) {
                        countedWholeCard.put(g.card, true);
                        hiddenWholeCards.merge(g.side, g.card.mergedCount(), Integer::sum);
                    }
                } else {
                    hiddenPartialGroups.computeIfAbsent(g.side, s -> new ArrayList<>())
                            .add(g.port.getCount() + "×" + g.port.getConnectorType());
                }
            }
            for (Map.Entry<NodeSide, Integer> e : hiddenWholeCards.entrySet()) {
                overflowBySide.computeIfAbsent(e.getKey(), s -> new ArrayList<>())
                        .add(new Overflow(e.getKey(), "ещё " + e.getValue() + (e.getValue() == 1 ? " карта" : " карт")));
            }
            for (Map.Entry<NodeSide, List<String>> e : hiddenPartialGroups.entrySet()) {
                overflowBySide.computeIfAbsent(e.getKey(), s -> new ArrayList<>())
                        .add(new Overflow(e.getKey(), "ещё: " + String.join(", ", e.getValue())));
            }
        } else {
            visible = all;
        }

        Map<NodeSide, List<ResolvedGroup>> bySide = new EnumMap<>(NodeSide.class);
        for (ResolvedGroup g : visible) {
            bySide.computeIfAbsent(g.side, s -> new ArrayList<>()).add(g);
        }
        for (List<ResolvedGroup> list : bySide.values()) {
            sortWithinSide(list);
        }
        return new Resolved(suppressHeaders, bySide, overflowBySide);
    }

    /** Порядок групп на одной стороне: естественный порядок обхода (карты узла по
     *  порядку, группы внутри карты по порядку) — переопределяется {@link
     *  PortPlacement#getOrder()}, если задан (сортировка по нему среди остальных,
     *  естественный индекс — запасной ключ для группы без явного {@code order},
     *  поэтому список стабилен, когда override не задан ни у кого). */
    private static void sortWithinSide(List<ResolvedGroup> list) {
        List<Double> keys = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ResolvedGroup g = list.get(i);
            keys.add(g.placement != null && g.placement.getOrder() != null ? g.placement.getOrder() : (double) i);
        }
        Integer[] idx = new Integer[list.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(keys.get(a), keys.get(b)));
        List<ResolvedGroup> sorted = new ArrayList<>(list.size());
        for (int i : idx) {
            sorted.add(list.get(i));
        }
        list.clear();
        list.addAll(sorted);
    }

    // ---- раскладка одной стороны ----

    private record SideOutcome(double alongUsed, double maxLabelWidth, List<Pin> pins, List<Bay> bays,
                                List<Overflow> overflow) {
    }

    private static SideOutcome layoutSide(NodeSide side, Resolved r, Input in, double avail,
                                           double w, double h, boolean emitCoords, double topDepth) {
        List<ResolvedGroup> onSide = r.bySide.getOrDefault(side, List.of());
        List<Overflow> preOverflow = r.overflowBySide.getOrDefault(side, List.of());
        boolean horizontal = side == NodeSide.TOP || side == NodeSide.BOTTOM;
        double step = horizontal ? colStepFor(onSide, in.textMeasure()) : SchemaLayoutMetrics.ROW_STEP;

        List<Pin> pins = new ArrayList<>();
        List<Bay> bays = new ArrayList<>();
        List<Overflow> overflow = new ArrayList<>();
        double maxLabelWidth = 0;
        // LEFT/RIGHT начинают СРАЗУ после полосы под название узла (см.
        // SchemaLayoutMetrics.TITLE_BAND) И, если у узла есть гнёзда сверху, ещё и
        // после их полосы (topDepth) — иначе шапка первой карты/пин LEFT или RIGHT
        // рисуется поверх пина TOP (см. DIALOG.md/PLAN.md, задача T3.2, найдено
        // пиксельным просмотром рендера). Важно завести это здесь, а не добавлять
        // отдельно только в realPin: иначе along-координаты у Bay (которые строятся
        // из ЭТОГО along, не проходя через realPin) не совпали бы с along-координатами
        // у Pin того же отсека, и рамка отсека уехала бы относительно своих гнёзд.
        double along = horizontal ? 0 : SchemaLayoutMetrics.TITLE_BAND + topDepth;
        String prevCardKey = null;
        double bayStart = 0;
        String bayCardId = null;
        String bayLabel = null;
        int bayMergedCount = 1;
        boolean truncated = false;

        for (ResolvedGroup g : onSide) {
            if (truncated) {
                break;
            }
            String cardKey = g.card.cardId();
            boolean newBay = prevCardKey == null || !Objects.equals(prevCardKey, cardKey);
            if (newBay) {
                if (prevCardKey != null) {
                    bays.add(new Bay(side, bayCardId, bayLabel, bayMergedCount, bayStart, along));
                    along += SchemaLayoutMetrics.BAY_GAP;
                }
                boolean showHeader = !r.suppressHeaders && cardKey != null
                        && g.card.cardName() != null && !g.card.cardName().isBlank();
                if (showHeader) {
                    along += SchemaLayoutMetrics.BAY_HEADER_STEP;
                }
                bayStart = along - (showHeader ? SchemaLayoutMetrics.BAY_HEADER_STEP : 0);
                bayCardId = cardKey;
                bayLabel = showHeader ? g.card.displayName() : null;
                bayMergedCount = g.card.mergedCount();
                prevCardKey = cardKey;
                if (bayLabel != null && !horizontal) {
                    // Название карты ("HDMI+DP+SDI Input Card 1") часто ДЛИННЕЕ подписи
                    // любого отдельного гнезда — без этого узкий блок обрезал бы шапку
                    // отсека вплотную к первой строке гнёзд (см. DIALOG.md/PLAN.md,
                    // задача T3.2, найдено пиксельным просмотром рендера). Для TOP/BOTTOM
                    // не нужно — там шапка идёт ВДОЛЬ границы (её ширина влияет на along,
                    // не на depth) и уже клипуется по ширине СВОЕГО отсека при отрисовке.
                    maxLabelWidth = Math.max(maxLabelWidth,
                            in.textMeasure().width(bayLabel, SchemaLayoutMetrics.LABEL_FONT_SIZE, false));
                }
            }
            boolean collapsed = isCollapsed(g, in.defaultCollapsed());
            int slots = collapsed ? 1 : Math.max(1, g.port.getCount());
            String label = collapsed ? g.port.getCount() + "×" + g.port.getConnectorType() : g.port.getConnectorType();
            double labelWidth = in.textMeasure().width(label, SchemaLayoutMetrics.LABEL_FONT_SIZE, false);
            maxLabelWidth = Math.max(maxLabelWidth, labelWidth);
            // Общий "step" — ширина под ОДНУ цифру слота, рассчитан на плотно стоящие
            // колонки развёрнутой группы ("1","2","3"…). Свёрнутая группа на TOP/BOTTOM
            // рисует НЕ цифру, а весь свой ярлык ("16×BNC") в ОДНОМ слоте — если не
            // раздвинуть этот слот под ширину ярлыка, соседняя свёрнутая группа той же
            // стороны стартует через несколько пикселей и наезжает на предыдущую (см.
            // DIALOG.md/PLAN.md, задача T3.2, найдено пиксельным просмотром рендера
            // ориентации DOWN: "2×XLR" и "16×BNC" на TOP слились в "2×XIERBNC").
            double groupStep = horizontal && collapsed
                    ? Math.max(step, labelWidth + SchemaLayoutMetrics.LABEL_PAD)
                    : step;

            for (int slot = 0; slot < slots; slot++) {
                if (along + groupStep > avail) {
                    overflow.add(new Overflow(side, "+" + (slots - slot) + " ещё…"));
                    truncated = true;
                    break;
                }
                if (emitCoords) {
                    pins.add(realPin(g, slot, slots, side, along + groupStep / 2.0, w, h));
                }
                along += groupStep;
            }
        }
        if (prevCardKey != null) {
            bays.add(new Bay(side, bayCardId, bayLabel, bayMergedCount, bayStart, along));
        }
        if (!truncated) {
            for (Overflow o : preOverflow) {
                maxLabelWidth = Math.max(maxLabelWidth, in.textMeasure().width(o.text(), SchemaLayoutMetrics.LABEL_FONT_SIZE, false));
                if (along + step <= avail) {
                    overflow.add(o);
                    along += step;
                } else {
                    overflow.add(new Overflow(side, "ещё…"));
                    break;
                }
            }
        }
        return new SideOutcome(along, maxLabelWidth, pins, bays, overflow);
    }

    /** {@code pos} — along-координата, УЖЕ включающая смещение под полосу названия
     *  и (если есть) под полосу гнёзд TOP для LEFT/RIGHT (см. начальное значение
     *  {@code along} в {@link #layoutSide}) — здесь её только проецируют на нужную
     *  ось, второй раз не прибавляют. */
    private static Pin realPin(ResolvedGroup g, int slot, int slots, NodeSide side, double pos, double w, double h) {
        double x;
        double y;
        switch (side) {
            case LEFT -> {
                x = 0;
                y = pos;
            }
            case RIGHT -> {
                x = w;
                y = pos;
            }
            case TOP -> {
                x = pos;
                y = 0;
            }
            default -> { // BOTTOM
                x = pos;
                y = h;
            }
        }
        return new Pin(g.port, g.card.cardId(), slot, slots, g.port.getDirection(), g.role, g.thru, side, x, y);
    }

    private static boolean isCollapsed(ResolvedGroup g, Boolean defaultCollapsed) {
        if (g.placement != null && g.placement.getCollapsed() != null) {
            return g.placement.getCollapsed();
        }
        if (defaultCollapsed != null) {
            return defaultCollapsed;
        }
        return !g.used;
    }

    private static double colStepFor(List<ResolvedGroup> onSide, TextMeasure textMeasure) {
        int maxSlot = 1;
        for (ResolvedGroup g : onSide) {
            maxSlot = Math.max(maxSlot, g.port.getCount());
        }
        double digitsWidth = textMeasure.width(String.valueOf(maxSlot), SchemaLayoutMetrics.LABEL_FONT_SIZE, false);
        return Math.max(SchemaLayoutMetrics.COL_STEP_MIN, digitsWidth + 6);
    }

    // ---- слияние подряд идущих незадействованных одинаковых карт ("Имя ×N") ----

    /** Карта после слияния подряд идущих НЕзадействованных карт с одинаковым
     *  именем и составом групп в одну "Имя ×N" (см. §2.4 PLAN.md) — {@code ports}
     *  несёт РЕАЛЬНЫЕ id гнёзд ПЕРВОЙ карты слитого пробега (клик по слитому
     *  отсеку соединяется именно с ней; как только любое из её гнёзд получает
     *  связь, при следующем пересчёте карта перестаёт быть "незадействованной"
     *  целиком и выходит из слияния — см. {@link #mergeUnusedIdenticalCards}). */
    private record VisibleCard(String cardId, String cardName, List<CardPort> ports, int mergedCount) {
        String displayName() {
            return mergedCount > 1 ? cardName + " ×" + mergedCount : cardName;
        }
    }

    private static List<VisibleCard> mergeUnusedIdenticalCards(List<CardGroup> cardGroups, List<SchemaEdge> modeEdges) {
        List<VisibleCard> result = new ArrayList<>();
        int i = 0;
        while (i < cardGroups.size()) {
            CardGroup first = cardGroups.get(i);
            boolean firstUnused = first.cardId() != null && isCardFullyUnused(first, modeEdges);
            int runEnd = i + 1;
            if (firstUnused) {
                while (runEnd < cardGroups.size()) {
                    CardGroup next = cardGroups.get(runEnd);
                    if (next.cardId() == null || !isCardFullyUnused(next, modeEdges)
                            || !Objects.equals(first.cardName(), next.cardName())
                            || !sameComposition(first.ports(), next.ports())) {
                        break;
                    }
                    runEnd++;
                }
            }
            int mergedCount = runEnd - i;
            result.add(new VisibleCard(first.cardId(), first.cardName(), first.ports(), mergedCount));
            i = runEnd;
        }
        return result;
    }

    private static boolean isCardFullyUnused(CardGroup card, List<SchemaEdge> modeEdges) {
        for (CardPort p : card.ports()) {
            if (SchemaUsage.isPortUsed(modeEdges, p.getId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameComposition(List<CardPort> a, List<CardPort> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            CardPort pa = a.get(i);
            CardPort pb = b.get(i);
            if (!Objects.equals(pa.getConnectorType(), pb.getConnectorType())
                    || pa.getDirection() != pb.getDirection() || pa.getCount() != pb.getCount()) {
                return false;
            }
        }
        return true;
    }
}
