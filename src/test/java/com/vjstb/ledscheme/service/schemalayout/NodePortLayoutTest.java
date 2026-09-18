package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.NodeSide;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Bay;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.CardGroup;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Input;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Overflow;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Pin;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Result;
import com.vjstb.ledscheme.service.schemalayout.NodePortLayout.Size;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T2.1 — {@link NodePortLayout} против
 *  всех перечисленных в плане критериев приёмки. */
class NodePortLayoutTest {

    private static SchemaEdge edgeTo(String portId) {
        SchemaEdge e = new SchemaEdge(SchemaMode.SIGNAL, "other", "target", null);
        e.setToPortId(portId);
        return e;
    }

    private static Input input(SchemaMode mode, SchemaNodeType type, NodeOrientation orientation,
                                List<CardGroup> cards, List<SchemaEdge> edges, boolean onlyUsed, boolean autoCollapse) {
        // autoCollapse=true -> AUTO (null, свернуть только незанятые), false -> ALWAYS_EXPANDED (Boolean.FALSE) —
        // те же два случая, что раньше покрывал boolean defaultCollapseUnusedGroups.
        Boolean defaultCollapsed = autoCollapse ? null : Boolean.FALSE;
        return new Input(mode, type, orientation, cards, edges, List.of(), onlyUsed, defaultCollapsed, List.of(), TextMeasure.awt());
    }

    // ---- пины на рамке ----

    @Test
    void pinsLieExactlyOnNodeBorderInAllFourOrientations() {
        CardPort in = new CardPort("HDMI", PortDirection.IN, 1);
        CardPort out = new CardPort("HDMI", PortDirection.OUT, 1);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Basic", List.of(in, out)));
        List<SchemaEdge> edges = List.of(edgeTo(in.getId()), edgeTo(out.getId()));

        for (NodeOrientation o : NodeOrientation.values()) {
            Input in2 = input(SchemaMode.SIGNAL, SchemaNodeType.SERVER, o, cards, edges, false, true);
            Size size = NodePortLayout.minimumSize(in2);
            Result r = NodePortLayout.layout(in2, size.width(), size.height());
            for (Pin p : r.pins()) {
                boolean onBorder = p.x() == 0 || p.x() == size.width() || p.y() == 0 || p.y() == size.height();
                assertTrue(onBorder, "пин не на рамке при ориентации " + o + ": " + p);
            }
        }
    }

    // ---- минимальный размер вмещает всё, без наложений подписей ----

    @Test
    void minimumSizeFitsAllVisiblePinsWithoutLabelOverlap() {
        CardPort a = new CardPort("Ethernet Cat6", PortDirection.OUT, 3);
        CardPort b = new CardPort("DisplayPort 1.2", PortDirection.OUT, 1);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Basic Set", List.of(a, b)));
        List<SchemaEdge> edges = List.of(edgeTo(a.getId()), edgeTo(b.getId())); // всё задействовано -> развёрнуто

        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.SERVER, NodeOrientation.RIGHT, cards, edges, false, true);
        Size size = NodePortLayout.minimumSize(in);
        Result r = NodePortLayout.layout(in, size.width(), size.height());

        assertTrue(r.overflow().isEmpty(), "при минимальном размере ничего не должно обрезаться");
        // 3 разъёма Ethernet (развёрнуты, т.к. заняты) + 1 DisplayPort = 4 пина
        assertEquals(4, r.pins().size());
        assertNoOverlaps(r);
    }

    @Test
    void allFourOrientationsFitWithoutOverlap() {
        CardPort syncIn = new CardPort("Genlock", PortDirection.IN, 1);
        CardPort syncOut = new CardPort("Genlock", PortDirection.OUT, 1);
        CardPort videoIn = new CardPort("HDMI", PortDirection.IN, 2);
        CardPort videoOut = new CardPort("HDMI", PortDirection.OUT, 2);
        CardPort net = new CardPort("Ethernet", PortDirection.IN_OUT, 1);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Basic", List.of(syncIn, syncOut, videoIn, videoOut, net)));
        List<SchemaEdge> edges = List.of(edgeTo(syncIn.getId()), edgeTo(videoIn.getId()), edgeTo(net.getId()));

        for (NodeOrientation o : NodeOrientation.values()) {
            Input in = input(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, o, cards, edges, false, true);
            Size size = NodePortLayout.minimumSize(in);
            Result r = NodePortLayout.layout(in, size.width(), size.height());
            assertTrue(r.overflow().isEmpty(), "ориентация " + o + " не должна давать overflow при минимальном размере");
            assertNoOverlaps(r);
        }
    }

    private static void assertNoOverlaps(Result r) {
        // Подписи гнёзд одной стороны не пересекаются друг с другом — приближение:
        // никакие два пина ОДНОЙ стороны не стоят в одной и той же точке.
        for (int i = 0; i < r.pins().size(); i++) {
            for (int j = i + 1; j < r.pins().size(); j++) {
                Pin a = r.pins().get(i);
                Pin b = r.pins().get(j);
                if (a.side() == b.side()) {
                    boolean samePoint = Double.compare(a.x(), b.x()) == 0 && Double.compare(a.y(), b.y()) == 0;
                    assertFalse(samePoint, "два пина одной стороны в одной точке: " + a + " / " + b);
                }
            }
        }
    }

    // ---- Q8-подобный узел: "только задействованные" ----

    @Test
    void onlyUsedPortsCollapsesFullyUnusedCardsIntoOverflowLine() {
        List<CardGroup> cards = new ArrayList<>();
        List<SchemaEdge> edges = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CardPort hdmi = new CardPort("HDMI 2.0", PortDirection.IN, 4);
            CardPort dp = new CardPort("DisplayPort 1.2", PortDirection.IN, 4);
            CardPort sdi = new CardPort("SDI", PortDirection.IN, 4);
            cards.add(new CardGroup("in-" + i, "HDMI+DP+SDI Input Card", List.of(hdmi, dp, sdi)));
            if (i == 0) {
                edges.add(edgeTo(hdmi.getId())); // только ПЕРВАЯ карта задействована
            }
        }
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards, edges, true, true);
        Size size = NodePortLayout.minimumSize(in);
        Result r = NodePortLayout.layout(in, size.width(), size.height());

        // "Группы без связей не рисуются" — БЕЗУСЛОВНО (PLAN.md §2.4), в т.ч. на карте
        // 0: незадействованные DP/SDI этой карты тоже скрываются (не просто сворачиваются),
        // видны только 4 пина занятой группы HDMI; DP/SDI карты 0 уходят в "ещё: ...",
        // 5 остальных полностью незадействованных карт — в "ещё 5 карт".
        assertEquals(4, r.pins().size(), "видна только задействованная группа HDMI карты 0");
        boolean hasWholeCardsLine = r.overflow().stream().anyMatch(o -> o.text().contains("ещё 5 карт"));
        assertTrue(hasWholeCardsLine, "5 полностью незадействованных карт должны схлопнуться в одну строку: " + r.overflow());
        boolean hasPartialLine = r.overflow().stream()
                .anyMatch(o -> o.text().contains("4×DisplayPort 1.2") && o.text().contains("4×SDI"));
        assertTrue(hasPartialLine, "незадействованные группы карты 0 (частично занятой) перечисляются: " + r.overflow());
    }

    @Test
    void onlyUsedPortsListsPartiallyHiddenGroupsByContent() {
        CardPort used = new CardPort("HDMI 2.0", PortDirection.OUT, 4);
        CardPort unusedDp = new CardPort("DisplayPort 1.2", PortDirection.OUT, 4);
        CardPort unusedSdi = new CardPort("SDI", PortDirection.OUT, 4);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Output Card", List.of(used, unusedDp, unusedSdi)));
        List<SchemaEdge> edges = List.of(edgeTo(used.getId()));

        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards, edges, true, true);
        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());

        boolean hasPartialLine = r.overflow().stream()
                .anyMatch(o -> o.text().contains("4×DisplayPort 1.2") && o.text().contains("4×SDI"));
        assertTrue(hasPartialLine, "карта частично задействована — скрытые группы должны быть перечислены: " + r.overflow());
    }

    // ---- авто-свёртка / IN_OUT одним пином / ручная сторона и порядок ----

    @Test
    void autoCollapseHidesUnusedGroupIntoOnePinAndExpandsOnUse() {
        CardPort unused = new CardPort("SDI", PortDirection.OUT, 4);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Card", List.of(unused)));

        Input notUsed = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards, List.of(), false, true);
        Result r1 = NodePortLayout.layout(notUsed, 300, 300);
        assertEquals(1, r1.pins().size(), "не задействована — одно свёрнутое гнездо");

        Input used = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards,
                List.of(edgeTo(unused.getId())), false, true);
        Result r2 = NodePortLayout.layout(used, 300, 300);
        assertEquals(4, r2.pins().size(), "как только подключена связь — группа разворачивается");
    }

    @Test
    void alwaysExpandedIgnoresUsageAndKeepsAllSlotsVisible() {
        CardPort unused = new CardPort("SDI", PortDirection.OUT, 4);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Card", List.of(unused)));
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards, List.of(), false, false);
        Result r = NodePortLayout.layout(in, 300, 300);
        assertEquals(4, r.pins().size());
    }

    @Test
    void inOutDirectionProducesOnePinPerSlotNotTwo() {
        CardPort loop = new CardPort("SDI Loop", PortDirection.IN_OUT, 1);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Card", List.of(loop)));
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, NodeOrientation.RIGHT, cards,
                List.of(edgeTo(loop.getId())), false, true);
        Result r = NodePortLayout.layout(in, 300, 300);
        assertEquals(1, r.pins().size(), "IN_OUT — одно гнездо, не два, как раньше");
    }

    // ---- "VFC ×4": слияние незадействованных одинаковых карт ----

    @Test
    void mergesConsecutiveUnusedIdenticalCardsIntoOneNamedGroup() {
        List<CardGroup> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cards.add(new CardGroup("vfc-" + i, "VFC HDMI2.0", List.of(new CardPort("HDMI 2.0", PortDirection.OUT, 1))));
        }
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.SERVER, NodeOrientation.RIGHT, cards, List.of(), false, true);
        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());

        assertEquals(1, r.pins().size(), "4 одинаковые незадействованные карты — визуально одна свёрнутая группа");
        Bay bay = r.bays().stream().filter(b -> b.label() != null).findFirst().orElseThrow();
        assertEquals("VFC HDMI2.0 ×4", bay.label());
        assertEquals(4, bay.mergedCount());
    }

    @Test
    void usedCardBreaksMergeRunIntoTwoSeparateContiguousGroups() {
        // "Подряд идущие" — строго СМЕЖНЫЕ карты; занятая карта посередине не даёт
        // слить карты ДО нее и ПОСЛЕ неё в одну общую группу — получаются ДВЕ
        // отдельные группы по 2 карты, а не одна на все 4 незадействованные.
        List<CardGroup> cards = new ArrayList<>();
        CardPort usedPort = null;
        for (int i = 0; i < 5; i++) {
            CardPort p = new CardPort("HDMI 2.0", PortDirection.OUT, 1);
            cards.add(new CardGroup("vfc-" + i, "VFC HDMI2.0", List.of(p)));
            if (i == 2) {
                usedPort = p;
            }
        }
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.SERVER, NodeOrientation.RIGHT, cards,
                List.of(edgeTo(usedPort.getId())), false, true);
        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());

        List<String> labels = r.bays().stream().map(Bay::label).filter(java.util.Objects::nonNull).toList();
        assertEquals(3, labels.size(), "два слитых по 2 + одна занятая карта посередине: " + labels);
        assertEquals(2, labels.stream().filter("VFC HDMI2.0 ×2"::equals).count(),
                "ДВЕ отдельные группы ×2 по разные стороны от занятой карты, не одна ×4: " + labels);
        assertTrue(labels.contains("VFC HDMI2.0"), "занятая карта — своя, неслитая, без суффикса ×N: " + labels);
    }

    @Test
    void manualSideAndOrderOverridesAreRespected() {
        CardPort videoOut = new CardPort("HDMI", PortDirection.OUT, 1); // обычно RIGHT
        CardPort syncIn = new CardPort("Genlock", PortDirection.IN, 1); // обычно TOP
        List<CardGroup> cards = List.of(new CardGroup("c1", "Card", List.of(videoOut, syncIn)));
        List<SchemaEdge> edges = List.of(edgeTo(videoOut.getId()), edgeTo(syncIn.getId()));

        com.vjstb.ledscheme.model.PortPlacement pp = new com.vjstb.ledscheme.model.PortPlacement(syncIn.getId());
        pp.setSide(NodeSide.RIGHT); // "синхро сбоку" — реплика пользователя 2026-09-16
        Input in = new Input(SchemaMode.SIGNAL, SchemaNodeType.CONVERTER, NodeOrientation.RIGHT, cards, edges,
                List.of(pp), false, null, List.of(), TextMeasure.awt());

        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());
        boolean syncOnRight = r.pins().stream()
                .anyMatch(p -> p.port() == syncIn && p.side() == NodeSide.RIGHT);
        assertTrue(syncOnRight, "ручная сторона должна быть применена, а не автоматическая (TOP)");
    }

    // ---- отсек карты соответствует координатам своих же пинов ----

    @Test
    void bayBoundsAlignWithItsOwnPinsOnLeftAndRightSides() {
        // Регрессия: alongStart/alongEnd отсека и along-координата его пинов должны
        // быть в ОДНОЙ системе координат (полоса под название узла — TITLE_BAND —
        // раньше прибавлялась только к пину, но не к отсеку, и рамка карты уезжала
        // относительно своих же гнёзд на LEFT/RIGHT, см. docs/schema-ports-rework/
        // PLAN.md, задача T3.2).
        CardPort a = new CardPort("HDMI", PortDirection.IN, 1);
        CardPort b = new CardPort("DisplayPort", PortDirection.IN, 1);
        List<CardGroup> cards = List.of(new CardGroup("c1", "Basic Set", List.of(a, b)));
        List<SchemaEdge> edges = List.of(edgeTo(a.getId()), edgeTo(b.getId()));
        Input in = input(SchemaMode.SIGNAL, SchemaNodeType.SERVER, NodeOrientation.RIGHT, cards, edges, false, true);
        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());

        Bay bay = r.bays().stream().filter(bb -> bb.side() == NodeSide.LEFT).findFirst().orElseThrow();
        for (Pin p : r.pins()) {
            if (p.side() != NodeSide.LEFT) {
                continue;
            }
            assertTrue(p.y() >= bay.alongStart() && p.y() <= bay.alongEnd(),
                    "пин " + p + " должен лежать внутри границ своего отсека " + bay);
        }
    }

    // ---- отсек питания без шапки ----

    @Test
    void powerConnectorsFormOneUnheadedBay() {
        CardPort in32 = new CardPort("CEE 32A", PortDirection.IN, 1);
        CardPort out32 = new CardPort("CEE 32A", PortDirection.OUT, 1);
        CardPort out16 = new CardPort("CEE 16A", PortDirection.OUT, 6);
        List<CardGroup> cards = List.of(new CardGroup(null, null, List.of(in32, out32, out16)));
        List<SchemaEdge> edges = List.of(edgeTo(in32.getId()), edgeTo(out32.getId()), edgeTo(out16.getId()));

        Input in = input(SchemaMode.POWER, SchemaNodeType.DISTRO, NodeOrientation.RIGHT, cards, edges, false, true);
        Result r = NodePortLayout.layout(in, NodePortLayout.minimumSize(in).width(), NodePortLayout.minimumSize(in).height());

        assertTrue(r.bays().stream().noneMatch(b -> b.label() != null), "у разъёмов питания нет карт — шапки быть не должно");
        // CEE 32A IN -> LEFT; CEE 32A OUT транзитный -> BOTTOM; CEE 16A OUT -> RIGHT (развёрнуты, задействованы)
        assertEquals(8, r.pins().size(), "1(IN) + 1(транзит OUT) + 6(16A развёрнут)");
        assertTrue(r.pins().stream().anyMatch(p -> p.port() == out32 && p.thru() && p.side() == NodeSide.BOTTOM));
    }
}
