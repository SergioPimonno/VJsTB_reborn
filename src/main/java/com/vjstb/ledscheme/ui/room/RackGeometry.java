package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomCatalog;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomTowerCalc;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Раскладка прямоугольников вкладки «Стойки и башни» (лицевые панели): башни рядом друг с другом, у каждой
 * слои сверху вниз — кофры с рядами по юнитам и стопки без кофра. Чистая арифметика без Swing-компонентов, поэтому
 * поиск цели под указателем (какой слот, какая стопка, между какими слоями) проверяется обычными тестами, а
 * {@link RacksPanel} только рисует и вызывает {@link RoomDnd}. Размеры — пиксели; один юнит — {@link #UH}.
 */
final class RackGeometry {

    static final int UH = RoomTheme.scale(26);
    static final int RACK_W = RoomTheme.scale(300);
    static final int RULER = RoomTheme.scale(36);
    static final int PAD = RoomTheme.scale(12);
    static final int CARD_W = RULER + RACK_W + RULER + PAD * 2;
    static final int GAP = RoomTheme.scale(18);
    static final int HEADER_H = RoomTheme.scale(34);
    static final int BRACKET_H = RoomTheme.scale(20);
    static final int ADD_H = RoomTheme.scale(26);
    static final int SEG_HEAD_H = RoomTheme.scale(24);
    static final int SEG_GAP = RoomTheme.scale(8);
    static final int FOOTER_H = RoomTheme.scale(24);
    static final int ZONE_W = RoomTheme.scale(240);
    static final int MARGIN = RoomTheme.scale(14);
    /** Пикселей на сантиметр высоты у предметов в стопке: один юнит (4,445 см) = {@link #UH} пикселей. */
    static final double PX_PER_CM = UH / RoomUnits.U_CM;

    /** Блок в слоте кофра. */
    record SlotItem(RoomRackItem item, RoomItemSpec spec, Rectangle rect, boolean tooDeep) {
    }

    /** Предмет в стопке без кофра. */
    record StackItem(RoomRackItem item, RoomItemSpec spec, Rectangle rect) {
    }

    /** Слой башни: кофр или стопка. */
    static final class SegBox {
        final RoomSegment seg;
        final RoomItemSpec caseSpec;
        Rectangle head;
        Rectangle body;
        Rectangle slots;
        Rectangle close;
        final List<SlotItem> slotItems = new ArrayList<>();
        final List<StackItem> stackItems = new ArrayList<>();

        SegBox(RoomSegment seg, RoomItemSpec caseSpec) {
            this.seg = seg;
            this.caseSpec = caseSpec;
        }
    }

    /** Карточка башни. */
    static final class TowerBox {
        final RoomTower tower;
        final RoomTowerCalc calc;
        Rectangle card;
        Rectangle header;
        Rectangle close;
        Rectangle addTop;
        Rectangle addBottom;
        Rectangle footer;
        final List<SegBox> segs = new ArrayList<>(); // сверху вниз

        TowerBox(RoomTower tower, RoomTowerCalc calc) {
            this.tower = tower;
            this.calc = calc;
        }

        RoomTower tower() {
            return tower;
        }
    }

    final List<TowerBox> towers = new ArrayList<>();
    Rectangle newTowerZone;
    Rectangle boxCard;
    Dimension size = new Dimension(400, 300);

    static RackGeometry build(List<RoomTowerCalc> calcs, RoomCatalog cat, int viewportHeight) {
        RackGeometry g = new RackGeometry();
        int x = MARGIN;
        int maxBottom = 0;
        for (RoomTowerCalc c : calcs) {
            TowerBox tb = new TowerBox(c.tower(), c);
            int y = MARGIN;
            tb.header = new Rectangle(x, y, CARD_W, HEADER_H);
            tb.close = new Rectangle(x + CARD_W - 28, y + 5, 24, 24);
            y += HEADER_H + 4;
            y += BRACKET_H;
            tb.addTop = new Rectangle(x + PAD, y, CARD_W - PAD * 2, ADD_H);
            y += ADD_H + 6;
            List<RoomSegment> top = new ArrayList<>(c.tower().getSegments());
            Collections.reverse(top);
            for (RoomSegment seg : top) {
                RoomItemSpec k = seg.isStack() ? null : cat.resolve(seg.getCaseRef());
                SegBox sb = new SegBox(seg, k);
                sb.head = new Rectangle(x + PAD, y, CARD_W - PAD * 2, SEG_HEAD_H);
                sb.close = new Rectangle(sb.head.x + sb.head.width - 22, sb.head.y + 2, 20, 20);
                int by = y + SEG_HEAD_H;
                if (seg.isStack()) {
                    layoutStack(sb, seg, cat, x + PAD + RULER, by);
                } else {
                    layoutCase(sb, seg, k, cat, x + PAD + RULER, by);
                }
                tb.segs.add(sb);
                y = sb.body.y + sb.body.height + SEG_GAP;
            }
            tb.addBottom = new Rectangle(x + PAD, y, CARD_W - PAD * 2, ADD_H);
            y += ADD_H + 6;
            tb.footer = new Rectangle(x + PAD, y, CARD_W - PAD * 2, FOOTER_H);
            y += FOOTER_H + PAD;
            tb.card = new Rectangle(x, MARGIN, CARD_W, y - MARGIN);
            maxBottom = Math.max(maxBottom, y);
            g.towers.add(tb);
            x += CARD_W + GAP;
        }
        int zoneH = 120;
        g.newTowerZone = new Rectangle(x, MARGIN, ZONE_W, zoneH);
        g.boxCard = new Rectangle(x, MARGIN + zoneH + GAP, ZONE_W, 210);
        maxBottom = Math.max(maxBottom, g.boxCard.y + g.boxCard.height);
        g.size = new Dimension(x + ZONE_W + MARGIN, Math.max(viewportHeight, maxBottom + MARGIN));
        return g;
    }

    private static void layoutCase(SegBox sb, RoomSegment seg, RoomItemSpec k, RoomCatalog cat, int slotsX, int by) {
        int u = Math.max(1, k.rackUnits());
        sb.slots = new Rectangle(slotsX, by, RACK_W, u * UH);
        sb.body = new Rectangle(sb.head.x, by, sb.head.width, u * UH + 4);
        for (RoomRackItem it : seg.getItems()) {
            RoomItemSpec s = cat.resolve(it.getRef());
            int ix = sb.slots.x + (int) Math.round(it.getC0() / 12.0 * RACK_W);
            int iw = (int) Math.round(s.widthTwelfths() / 12.0 * RACK_W);
            Rectangle r = new Rectangle(ix + 1, sb.slots.y + it.getU0() * UH + 1, iw - 2, s.rackUnits() * UH - 2);
            sb.slotItems.add(new SlotItem(it, s, r, s.depthCm() > k.innerDepthCm() && k.innerDepthCm() > 0));
        }
    }

    private static void layoutStack(SegBox sb, RoomSegment seg, RoomCatalog cat, int slotsX, int by) {
        List<RoomRackItem> topFirst = new ArrayList<>(seg.getItems());
        Collections.reverse(topFirst);
        double maxW = 1;
        for (RoomRackItem it : topFirst) {
            maxW = Math.max(maxW, cat.resolve(it.getRef()).widthCm());
        }
        int y = by;
        for (RoomRackItem it : topFirst) {
            RoomItemSpec s = cat.resolve(it.getRef());
            int h = Math.max(22, (int) Math.round(s.heightCm() * PX_PER_CM));
            int wPct = Math.max(32, (int) Math.round(s.widthCm() / maxW * 100));
            int w = (int) Math.round(RACK_W * wPct / 100.0);
            int ix = slotsX + (RACK_W - w) / 2;
            sb.stackItems.add(new StackItem(it, s, new Rectangle(ix, y, w, h)));
            y += h + 2;
        }
        int bodyH = topFirst.isEmpty() ? 34 : y - by + 2;
        sb.slots = new Rectangle(slotsX, by, RACK_W, bodyH);
        sb.body = new Rectangle(sb.head.x, by, sb.head.width, bodyH);
    }

    // ---------------------------------------------------------------- поиск цели

    /** Башня, чья карточка содержит точку. */
    TowerBox towerAt(Point p) {
        for (TowerBox t : towers) {
            if (t.card.contains(p)) {
                return t;
            }
        }
        return null;
    }

    /** Слой башни, в слотах или теле которого лежит точка. */
    SegBox segAt(TowerBox t, Point p) {
        for (SegBox s : t.segs) {
            if (s.body.contains(p) || s.head.contains(p)) {
                return s;
            }
        }
        return null;
    }

    /** Позиция вставки слоя среди слоёв башни сверху вниз: число слоёв, чей центр выше указателя. */
    int segInsertPosition(TowerBox t, Point p) {
        int pos = 0;
        for (SegBox s : t.segs) {
            int mid = s.head.y + (s.body.y + s.body.height - s.head.y) / 2;
            if (p.y > mid) {
                pos++;
            }
        }
        return pos;
    }

    /** Y линии вставки слоя на позиции {@code pos}. */
    int segInsertY(TowerBox t, int pos) {
        if (t.segs.isEmpty()) {
            return t.addTop.y + t.addTop.height + 2;
        }
        if (pos < t.segs.size()) {
            return t.segs.get(pos).head.y - SEG_GAP / 2;
        }
        SegBox last = t.segs.get(t.segs.size() - 1);
        return last.body.y + last.body.height + SEG_GAP / 2;
    }

    /** Позиция вставки в стопку сверху: число предметов, чей центр выше указателя. */
    int stackInsertPosition(SegBox s, Point p) {
        int pos = 0;
        for (StackItem it : s.stackItems) {
            if (p.y > it.rect().y + it.rect().height / 2) {
                pos++;
            }
        }
        return pos;
    }

    int stackInsertY(SegBox s, int pos) {
        if (s.stackItems.isEmpty()) {
            return s.slots.y + 2;
        }
        if (pos < s.stackItems.size()) {
            return s.stackItems.get(pos).rect().y - 1;
        }
        StackItem last = s.stackItems.get(s.stackItems.size() - 1);
        return last.rect().y + last.rect().height + 1;
    }
}
