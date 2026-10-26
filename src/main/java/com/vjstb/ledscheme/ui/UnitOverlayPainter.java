package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.ChainLinks;
import com.vjstb.ledscheme.service.ChainUnits;
import com.vjstb.ledscheme.service.ScreenModules;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Подложка «Расключения экрана» для модульного экрана (docs/receiver-cards/PLAN.md, D24/D25; прототип
 * {@code prototype-flow.html}, вид {@code wire}). Рисуется ПОД линиями цепочек ({@link SchemeRenderer#paintSchemeChains}):
 * <ul>
 *   <li>модули — серым (звеном цепочки модуль не бывает, D26);</li>
 *   <li>сигнал: приёмные карты поверх модулей — заливка цвета «Приёмные карты» ({@link Palette#RECEIVER_CARD}),
 *   рамка цвета цепочки, если карта уже на порту; плашка с именем (К3) на месте карты;</li>
 *   <li>питание: карты НЕ рисуются, вместо них — области блоков питания: контур по границе их модулей (область
 *   может быть непрямоугольной), заливка цветом фазы, плашка «БПn» на месте блока.</li>
 * </ul>
 * На экране без модулей не рисует ничего — вывод немодульных проектов остаётся прежним. Имена К1…КN / БП1…БПn
 * зависят от параметра Предпочтений «Нумерация приёмных карт»; статический рендер не знает настроек, поэтому
 * холст перед отрисовкой сообщает его через {@link #setNumbering}.
 */
final class UnitOverlayPainter {

    private static final Color MODULE_GRAY = new Color(0x6E7681);
    private static final Color CARD_EDGE = new Color(0x3B6D11);
    private static final Color CARD_PLATE = new Color(0x27500A);
    /** Блок, ещё не стоящий на фазе (в питании) — тёмно-оранжевый, как в прототипе. */
    private static final Color SUPPLY_NO_PHASE = new Color(0xB5651D);

    private static volatile ReceiverCardNumbering numbering = ReceiverCardNumbering.DEFAULT;

    private UnitOverlayPainter() {
    }

    /** Порядок нумерации К1…КN / БП1…БПn из Предпочтений (холст зовёт перед отрисовкой). */
    static void setNumbering(ReceiverCardNumbering n) {
        numbering = n != null ? n : ReceiverCardNumbering.DEFAULT;
    }

    static void paint(Graphics2D g2, Screen scr, boolean power, int cellW, int cellH, int offX, int offY,
                      CabinetType type, Workspace workspace, List<PowerChain> powerChains,
                      List<SignalChain> signalChains) {
        if (workspace == null) {
            return;
        }
        ScreenModules sm = ScreenModules.of(scr, workspace);
        if (!sm.isModular()) {
            return;
        }
        java.awt.Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        java.awt.Font prevFont = g2.getFont();

        for (ScreenModules.Module m : sm.modules()) {
            Rectangle r = SchemeRenderer.cabinetScreenRect(m.cabinet(), type, cellW, cellH, offX, offY, workspace);
            g2.setColor(MODULE_GRAY);
            g2.fillRect(r.x + 1, r.y + 1, Math.max(1, r.width - 2), Math.max(1, r.height - 2));
            // серая заливка закрыла подпись «строка,столбец» из сетки — возвращаем её
            SchemeRenderer.drawCabinetIndexLabel(g2, m.cabinet(), r.x, r.y, r.width, r.height);
        }

        ChainUnits units = ChainUnits.of(scr, workspace, numbering);
        if (power) {
            Map<String, Color> colorOf = new HashMap<>();
            for (PowerChain chain : powerChains) {
                Color c = SchemeRenderer.chainColor(chain);
                for (String id : chain.getCabinetInstanceIds()) {
                    colorOf.put(id, c);
                }
            }
            for (ChainUnits.Unit u : units.units(ChainUnits.Mode.POWER)) {
                if (u.kind() != ChainUnits.Kind.POWER_SUPPLY) {
                    continue;
                }
                Color chainColor = colorOf.get(u.id());
                Color base = chainColor != null ? chainColor : SUPPLY_NO_PHASE;
                paintArea(g2, u.cabinets(), type, cellW, cellH, offX, offY, workspace,
                        withAlpha(base, chainColor != null ? 110 : 60), base, 2.5f);
            }
            for (ChainUnits.Unit u : units.units(ChainUnits.Mode.POWER)) {
                if (u.kind() == ChainUnits.Kind.POWER_SUPPLY) {
                    Color chainColor = colorOf.get(u.id());
                    paintPlate(g2, scr, u, u.name(), chainColor != null ? chainColor : SUPPLY_NO_PHASE, type, cellW,
                            cellH, offX, offY, workspace);
                }
            }
        } else {
            Map<String, Color> colorOf = new HashMap<>();
            for (int i = 0; i < signalChains.size(); i++) {
                SignalChain chain = signalChains.get(i);
                Color c = SchemeRenderer.chainColor(chain, i);
                for (String id : chain.getCabinetInstanceIds()) {
                    colorOf.put(id, c);
                }
            }
            for (ChainUnits.Unit u : units.units(ChainUnits.Mode.SIGNAL)) {
                if (u.kind() != ChainUnits.Kind.RECEIVER_CARD) {
                    continue;
                }
                Color chainColor = colorOf.get(u.id());
                paintArea(g2, u.cabinets(), type, cellW, cellH, offX, offY, workspace,
                        withAlpha(Palette.RECEIVER_CARD, chainColor != null ? 128 : 64),
                        chainColor != null ? chainColor : CARD_EDGE, chainColor != null ? 2.5f : 1.5f);
            }
            for (ChainUnits.Unit u : units.units(ChainUnits.Mode.SIGNAL)) {
                if (u.kind() == ChainUnits.Kind.RECEIVER_CARD) {
                    paintPlate(g2, scr, u, u.name(), CARD_PLATE, type, cellW, cellH, offX, offY, workspace);
                }
            }
        }
        g2.setStroke(prevStroke);
        g2.setColor(prevColor);
        g2.setFont(prevFont);
    }

    /** Заливка ячеек области и контур по её внешней границе (стороны ячеек, чей сосед не из этой области). */
    private static void paintArea(Graphics2D g2, List<CabinetInstance> cells, CabinetType type, int cellW, int cellH,
                                  int offX, int offY, Workspace workspace, Color fill, Color edge, float edgeWidth) {
        Set<Long> keys = new HashSet<>();
        for (CabinetInstance c : cells) {
            keys.add(key(c.getRowIndex(), c.getColIndex()));
        }
        g2.setColor(fill);
        for (CabinetInstance c : cells) {
            Rectangle r = SchemeRenderer.cabinetScreenRect(c, type, cellW, cellH, offX, offY, workspace);
            g2.fillRect(r.x + 1, r.y + 1, Math.max(1, r.width - 2), Math.max(1, r.height - 2));
        }
        g2.setColor(edge);
        g2.setStroke(new BasicStroke(edgeWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        int in = Math.max(1, (int) Math.ceil(edgeWidth / 2));
        for (CabinetInstance c : cells) {
            Rectangle r = SchemeRenderer.cabinetScreenRect(c, type, cellW, cellH, offX, offY, workspace);
            int x0 = r.x + in;
            int y0 = r.y + in;
            int x1 = r.x + r.width - in;
            int y1 = r.y + r.height - in;
            if (!keys.contains(key(c.getRowIndex() - 1, c.getColIndex()))) {
                g2.drawLine(x0, y0, x1, y0);
            }
            if (!keys.contains(key(c.getRowIndex() + 1, c.getColIndex()))) {
                g2.drawLine(x0, y1, x1, y1);
            }
            if (!keys.contains(key(c.getRowIndex(), c.getColIndex() - 1))) {
                g2.drawLine(x0, y0, x0, y1);
            }
            if (!keys.contains(key(c.getRowIndex(), c.getColIndex() + 1))) {
                g2.drawLine(x1, y0, x1, y1);
            }
        }
    }

    /** Плашка с именем звена (К3 / БП2) на месте карты/блока. На мелком масштабе имя не помещается — не рисуется. */
    private static void paintPlate(Graphics2D g2, Screen scr, ChainUnits.Unit u, String name, Color bg,
                                   CabinetType type, int cellW, int cellH, int offX, int offY, Workspace workspace) {
        if (name == null || name.isEmpty() || Math.min(cellW, cellH) < 16) {
            return;
        }
        CabinetInstance place = ChainLinks.place(scr, u.id());
        if (place == null) {
            return;
        }
        Rectangle r = SchemeRenderer.cabinetScreenRect(place, type, cellW, cellH, offX, offY, workspace);
        float size = Math.max(8f, Math.min(13f, Math.min(r.width, r.height) * 0.3f));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, size));
        FontMetrics fm = g2.getFontMetrics();
        int w = Math.min(r.width - 2, fm.stringWidth(name) + 8);
        int h = fm.getHeight();
        int x = r.x + (r.width - w) / 2;
        int y = r.y + (r.height - h) / 2;
        g2.setColor(bg);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(luminance(bg) > 150 ? Color.BLACK : Color.WHITE);
        g2.drawString(name, x + Math.max(1, (w - fm.stringWidth(name)) / 2), y + fm.getAscent());
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }
}
