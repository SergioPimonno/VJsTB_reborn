package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomGeometry;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomPlanCalc;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomSpecText;
import com.vjstb.ledscheme.service.room.RoomTowerCalc;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * Спецификация справа (порт {@code renderSide()} прототипа): башни с заполнением, мелочь по трассам, «К
 * перевозке» (кофры, станки, мониторы, кейс мелочёвки) и кнопка «Копировать спецификацию». Строки башен и мелочи
 * кликабельны — выбирают объект на плане.
 */
final class SpecPanel extends JPanel implements PlannerSession.Listener {

    private final PlannerSession session;
    private final Consumer<String> toast;
    private final JEditorPane html = new JEditorPane();
    private final JButton copyBtn = new JButton("Копировать спецификацию");
    private boolean stale;

    SpecPanel(PlannerSession session, Consumer<String> toast) {
        super(new BorderLayout(0, 6));
        this.session = session;
        this.toast = toast;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(310, 400));
        html.setEditable(false);
        html.setContentType("text/html");
        html.setEditorKit(new HTMLEditorKit());
        html.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                follow(e.getDescription());
            }
        });
        add(new JScrollPane(html), BorderLayout.CENTER);
        copyBtn.addActionListener(e -> copy());
        add(copyBtn, BorderLayout.SOUTH);
        session.addListener(this);
        render();
    }

    @Override
    public void sessionChanged() {
        // скрытая панель HTML не строит (это самая дорогая часть обновления); дособерёт при показе
        if (isVisible()) {
            render();
        } else {
            stale = true;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && stale) {
            stale = false;
            render();
        }
    }

    private void follow(String ref) {
        if (ref == null) {
            return;
        }
        int i = ref.indexOf(':');
        if (i < 0) {
            return;
        }
        String kind = ref.substring(0, i);
        String id = ref.substring(i + 1);
        switch (kind) {
            case "tower" -> session.select(RoomSelection.tower(id));
            case "loose" -> session.select(RoomSelection.loose(id));
            case "run" -> session.select(RoomSelection.run(id));
            default -> {
            }
        }
    }

    private void copy() {
        ControlRoomLayout l = session.layout();
        if (l == null || session.calc() == null) {
            return;
        }
        String text = RoomSpecText.of(l, session.catalog(), session.calc());
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            toast.accept("Спецификация скопирована");
        } catch (RuntimeException ex) {
            toast.accept("Не удалось скопировать");
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void render() {
        RoomTheme th = RoomTheme.current();
        HTMLEditorKit kit = (HTMLEditorKit) html.getEditorKit();
        StyleSheet css = new StyleSheet();
        css.addRule("body{font-family:sans-serif;font-size:11pt;color:" + hex(th.ink) + ";background:" + hex(th.panel)
                + ";margin:6px}h3{margin:10px 0 4px 0;font-size:12pt}a{color:" + hex(th.accent)
                + ";text-decoration:none}.m{color:" + hex(th.muted) + ";font-size:9pt}td{padding:1px 2px}");
        kit.setStyleSheet(css);
        ControlRoomLayout l = session.layout();
        RoomPlanCalc.Result calc = session.calc();
        if (l == null || calc == null) {
            html.setText("<html><body><p class='m'>Раскладки нет. Создайте её кнопкой «Новая…».</p></body></html>");
            return;
        }
        StringBuilder b = new StringBuilder("<html><body>");
        // башни
        b.append("<h3>Башни</h3>");
        if (calc.towers().isEmpty()) {
            b.append("<p class='m'>Башен нет. Перетащите рэк-кофр в рабочую зону.</p>");
        }
        for (RoomTowerCalc c : calc.towers()) {
            RoomTower t = c.tower();
            int pct = c.unitsTotal() > 0 ? (int) Math.round(100.0 * c.unitsUsed() / c.unitsTotal()) : 0;
            boolean sel = session.selection() != null && session.selection().touchesTower(t.getId());
            b.append("<p style='margin:3px 0'><a href='tower:").append(t.getId()).append("'>")
                    .append(sel ? "<b>" : "").append(esc(t.getName().isEmpty() ? "Башня " + t.getTag() : t.getName()))
                    .append(sel ? "</b>" : "").append("</a> &nbsp; ")
                    .append(c.unitsTotal() > 0 ? c.unitsUsed() + "/" + c.unitsTotal() + "U" : "без кофра").append("<br>");
            b.append("<table width='100%' cellspacing='0' cellpadding='0'><tr><td width='").append(Math.max(1, pct))
                    .append("%' bgcolor='").append(hex(pct > 90 ? th.warn : th.accent)).append("'> </td><td width='")
                    .append(Math.max(1, 100 - pct)).append("%' bgcolor='").append(hex(th.line)).append("'> </td></tr></table>");
            b.append("<span class='m'>").append(RoomUnits.num(c.widthCm(), 0)).append("×").append(RoomUnits.num(c.depthCm(), 0))
                    .append("×").append(RoomUnits.num(c.heightCm(), 0)).append(" см · ").append(RoomUnits.num(c.weightKg(), 0))
                    .append(" кг · ").append(RoomUnits.num(c.powerW(), 0)).append(" Вт</span></p>");
        }
        // мелочь
        b.append("<h3>Мелочь · ").append(l.getLoose().size()).append(" шт.</h3>");
        int n = 0;
        Map<String, Integer> numbers = new LinkedHashMap<>();
        for (RoomLooseItem li : l.getLoose()) {
            if (li.getMode() != RoomLooseItem.Mode.BOX) {
                numbers.put(li.getId(), ++n);
            }
        }
        boolean any = false;
        for (RoomRun r : l.getRuns()) {
            List<RoomLooseItem> on = l.getLoose().stream()
                    .filter(x -> x.getMode() == RoomLooseItem.Mode.RUN && r.getId().equals(x.getRunId()))
                    .sorted(Comparator.comparingDouble(x -> x.getSCm() != null ? x.getSCm() : 0)).toList();
            if (!on.isEmpty()) {
                any = true;
                b.append("<p class='m' style='margin:6px 0 1px 0'><b>").append(esc(r.getName())).append("</b></p>");
                for (RoomLooseItem li : on) {
                    looseRow(b, li, numbers, session.catalog().resolve(li.getRef()),
                            RoomUnits.num((li.getSCm() != null ? li.getSCm() : 0) / 100, 1) + " м");
                }
            }
        }
        List<RoomLooseItem> free = l.getLoose().stream().filter(x -> x.getMode() == RoomLooseItem.Mode.FREE).toList();
        if (!free.isEmpty()) {
            any = true;
            b.append("<p class='m' style='margin:6px 0 1px 0'><b>Свободно на площадке</b></p>");
            for (RoomLooseItem li : free) {
                looseRow(b, li, numbers, session.catalog().resolve(li.getRef()), "");
            }
        }
        List<RoomLooseItem> boxed = l.getLoose().stream().filter(x -> x.getMode() == RoomLooseItem.Mode.BOX).toList();
        if (!boxed.isEmpty()) {
            any = true;
            b.append("<p class='m' style='margin:6px 0 1px 0'><b>В кофре мелочёвки</b></p>");
            for (RoomLooseItem li : boxed) {
                looseRow(b, li, numbers, session.catalog().resolve(li.getRef()), "");
            }
        }
        if (!any) {
            b.append("<p class='m'>Конвертеры и прочая мелочь появятся здесь.</p>");
        }
        // к перевозке
        b.append("<h3>К перевозке</h3>");
        b.append(shipping(l, calc));
        b.append("</body></html>");
        html.setText(b.toString());
        html.setCaretPosition(0);
    }

    private void looseRow(StringBuilder b, RoomLooseItem li, Map<String, Integer> numbers, RoomItemSpec s, String where) {
        Integer num = numbers.get(li.getId());
        boolean sel = session.selection() != null && session.selection().type() == RoomSelection.Type.LOOSE
                && session.selection().id().equals(li.getId());
        b.append("<p style='margin:1px 0'>").append(num != null ? "<span class='m'>" + num + "</span> " : "")
                .append("<a href='loose:").append(li.getId()).append("'>").append(sel ? "<b>" : "").append(esc(s.name()))
                .append(sel ? "</b>" : "").append("</a>");
        if (!where.isEmpty()) {
            b.append(" <span class='m'>").append(where).append("</span>");
        }
        b.append("</p>");
    }

    private String shipping(ControlRoomLayout l, RoomPlanCalc.Result calc) {
        Map<String, int[]> cases = new LinkedHashMap<>();
        Map<String, String> caseDims = new LinkedHashMap<>();
        int stackItems = 0;
        for (RoomTower t : l.getTowers()) {
            for (RoomSegment seg : t.getSegments()) {
                if (seg.isStack()) {
                    stackItems += seg.getItems().size();
                    continue;
                }
                RoomItemSpec k = session.catalog().resolve(seg.getCaseRef());
                cases.computeIfAbsent(k.name(), x -> new int[1])[0]++;
                caseDims.put(k.name(), RoomUnits.num(k.widthCm(), 0) + "×" + RoomUnits.num(k.heightCm(), 0) + "×"
                        + RoomUnits.num(k.depthCm(), 0));
            }
        }
        Map<String, int[]> decks = new LinkedHashMap<>();
        Map<String, String> deckDims = new LinkedHashMap<>();
        int monitors = 0;
        Map<String, int[]> devices = new LinkedHashMap<>();
        Map<String, String> deviceDims = new LinkedHashMap<>();
        for (RoomFurniture f : l.getFurniture()) {
            RoomItemSpec s = RoomGeometry.effective(f, session.catalog());
            if (s.type() == RoomItemSpec.Type.DECK) {
                String key = s.name() + " · выс. " + RoomUnits.num(s.heightCm(), 0);
                decks.computeIfAbsent(key, x -> new int[1])[0]++;
                deckDims.put(key, RoomUnits.num(s.widthCm(), 0) + "×" + RoomUnits.num(s.depthCm(), 0));
            } else if (s.type() == RoomItemSpec.Type.MONITOR) {
                monitors++;
            } else if (s.type() == RoomItemSpec.Type.DEVICE) {
                devices.computeIfAbsent(s.name(), x -> new int[1])[0]++;
                deviceDims.put(s.name(), RoomUnits.num(s.widthCm(), 0) + "×" + RoomUnits.num(s.depthCm(), 0));
            }
        }
        StringBuilder rows = new StringBuilder();
        cases.forEach((k, v) -> rows.append("<tr><td>").append(v[0]).append(" × ").append(esc(k)).append("</td><td class='m'>")
                .append(caseDims.get(k)).append("</td></tr>"));
        decks.forEach((k, v) -> rows.append("<tr><td>").append(v[0]).append(" × ").append(esc(k)).append("</td><td class='m'>")
                .append(deckDims.get(k)).append("</td></tr>"));
        devices.forEach((k, v) -> rows.append("<tr><td>").append(v[0]).append(" × ").append(esc(k))
                .append(" (отдельно, едет в своём кейсе)</td><td class='m'>").append(deviceDims.get(k)).append("</td></tr>"));
        if (monitors > 0) {
            rows.append("<tr><td>").append(monitors).append(" × мониторы (едут в своих кейсах)</td><td>—</td></tr>");
        }
        if (stackItems > 0) {
            rows.append("<tr><td>").append(stackItems).append(" × оборудование без кофра (едет в своих кейсах)</td><td>—</td></tr>");
        }
        RoomPlanCalc.BoxCalc box = calc.box();
        if (box.count() > 0) {
            rows.append("<tr><td>1 × Кейс мелочёвки").append(box.pick() == null ? " (не помещается)" : "")
                    .append("</td><td class='m'>").append(box.pick() == null ? "—"
                            : (int) box.pick()[0] + "×" + (int) box.pick()[1] + "×" + (int) box.pick()[2]).append("</td></tr>");
        }
        if (rows.length() == 0) {
            return "<p class='m'>Пока ничего.</p>";
        }
        return "<table width='100%'>" + rows + "</table><p class='m'>Размеры кофров в см (ширина × высота × глубина), вес с "
                + "оборудованием: " + RoomUnits.num(calc.totals().kg(), 0) + " кг.</p>";
    }
}
