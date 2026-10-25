package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomGeometry;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomRunMath;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.service.room.RoomTowerCalc;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Панель выбранного под рабочей зоной (порт {@code renderInspector()} прототипа): название и параметры
 * выбранного объекта и кнопки действий — убрать, повернуть, положение башни и станка, вверх/вниз по стопке,
 * режим мелочи, тип и название трассы. Перестраивается при каждом изменении выбора или раскладки.
 */
final class InspectorBar extends JPanel implements PlannerSession.Listener {

    private final PlannerSession session;
    private boolean building;

    InspectorBar(PlannerSession session) {
        super(new BorderLayout(10, 0));
        this.session = session;
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        session.addListener(this);
        rebuild();
    }

    @Override
    public void sessionChanged() {
        rebuild();
    }

    /**
     * Высота панели постоянна — по самому высокому содержимому (две строки текста или ряд кнопок), а не по тому, что
     * выбрано сейчас: иначе при каждом выборе объекта рабочая зона над панелью меняла высоту, и план «подпрыгивал».
     */
    @Override
    public java.awt.Dimension getPreferredSize() {
        java.awt.Dimension d = super.getPreferredSize();
        java.awt.Insets in = getInsets();
        int titleH = getFontMetrics(getFont().deriveFont(Font.BOLD, RoomTheme.fontSize(13f))).getHeight();
        int metaH = getFontMetrics(getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f))).getHeight();
        int buttonH = new JButton("Ag").getPreferredSize().height;
        d.height = Math.max(Math.max(buttonH, titleH + metaH), metaH * 2) + in.top + in.bottom;
        return d;
    }

    private static String cm(double v) {
        return RoomUnits.num(v, 1);
    }

    private JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, RoomTheme.fontSize(13f)));
        return l;
    }

    private JLabel meta(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, RoomTheme.fontSize(11f)));
        l.setForeground(RoomTheme.current().muted);
        return l;
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void rebuild() {
        building = true;
        try {
            removeAll();
            RoomSelection sel = session.selection();
            ControlRoomLayout l = session.layout();
            JPanel text = new JPanel();
            text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            if (l == null || session.calc() == null) {
                text.add(meta("Создайте раскладку кнопкой «Новая…» вверху."));
            } else if (sel == null) {
                text.add(RoomText.hint("Перетащите оборудование из библиотеки в кофр, в стопку без кофра или на план. Кнопка "
                        + "«Добавить» кладёт сразу. Выбранное можно убрать клавишей Delete или перетащив обратно в библиотеку."));
            } else {
                describe(sel, l, text, actions);
            }
            add(text, BorderLayout.CENTER);
            add(actions, BorderLayout.EAST);
            revalidate();
            repaint();
        } finally {
            building = false;
        }
    }

    private void describe(RoomSelection sel, ControlRoomLayout l, JPanel text, JPanel actions) {
        var cat = session.catalog();
        switch (sel.type()) {
            case RITEM -> {
                RoomEditor.Located f = RoomEditor.locateItem(l, sel.id());
                if (f == null) {
                    return;
                }
                RoomItemSpec s = cat.resolve(f.item().getRef());
                text.add(title(s.name()));
                text.add(meta("Башня " + f.tower().getTag() + " · U" + (f.item().getU0() + 1)
                        + (s.rackUnits() > 1 ? "–" + (f.item().getU0() + s.rackUnits()) : "") + " в кофре · " + s.rackUnits() + "U · гл. "
                        + cm(s.depthCm()) + " см · " + RoomUnits.num(s.weightKg(), 1) + " кг · " + RoomUnits.num(s.powerW(), 0) + " Вт"));
                actions.add(button("Убрать из стойки", this::removeSelection));
            }
            case SITEM -> {
                RoomEditor.Located f = RoomEditor.locateItem(l, sel.id());
                if (f == null) {
                    return;
                }
                RoomItemSpec s = cat.resolve(f.item().getRef());
                text.add(title(s.name()));
                text.add(meta("Башня " + f.tower().getTag() + " · без кофра, в стопке · " + cm(s.widthCm()) + "×" + cm(s.heightCm())
                        + "×" + cm(s.depthCm()) + " см · " + RoomUnits.num(s.weightKg(), 1) + " кг"));
                actions.add(button("Выше", () -> session.edit(() -> RoomEditor.moveStackItem(l, sel.id(), true))));
                actions.add(button("Ниже", () -> session.edit(() -> RoomEditor.moveStackItem(l, sel.id(), false))));
                actions.add(button("Убрать из стопки", this::removeSelection));
            }
            case TOWER -> {
                RoomTower t = RoomEditor.tower(l, sel.id());
                RoomTowerCalc c = t == null ? null : RoomTowerCalc.of(t, cat);
                if (t == null) {
                    return;
                }
                text.add(title(t.getName().isEmpty() ? "Башня " + t.getTag() : t.getName()));
                text.add(meta(c.caseCount() + " кофр." + (c.stackItems() > 0 ? " + " + c.stackItems() + " шт. без кофра" : "")
                        + " · " + c.unitsUsed() + "/" + c.unitsTotal() + "U · " + RoomUnits.num(c.heightCm(), 0) + " см высота · "
                        + RoomUnits.num(c.widthCm(), 0) + "×" + RoomUnits.num(c.depthCm(), 0) + " см на полу · "
                        + RoomUnits.num(c.weightKg(), 0) + " кг"));
                JComboBox<String> lay = new JComboBox<>(new String[] {"Положение: авто", "На столе или станке",
                        "Под столом или станком"});
                lay.setSelectedIndex(t.getLay() == null ? 0 : t.getLay() == RoomTower.Lay.ON ? 1 : 2);
                lay.addActionListener(e -> {
                    if (building) {
                        return;
                    }
                    int i = lay.getSelectedIndex();
                    session.edit(() -> t.setLay(i == 0 ? null : i == 1 ? RoomTower.Lay.ON : RoomTower.Lay.UNDER));
                });
                actions.add(lay);
                actions.add(button("Повернуть на 90°", () -> session.edit(() -> RoomEditor.rotate(l, sel))));
                actions.add(button("Убрать башню", this::removeSelection));
            }
            case FURN -> {
                RoomFurniture f = RoomEditor.furniture(l, sel.id());
                if (f == null) {
                    return;
                }
                RoomItemSpec s = RoomGeometry.effective(f, cat);
                text.add(title(s.name()));
                text.add(meta(cm(s.widthCm()) + "×" + cm(s.depthCm()) + " см" + (s.heightCm() > 0 ? " · выс. " + cm(s.heightCm()) + " см" : "")
                        + " · " + RoomUnits.num(s.weightKg(), 0) + " кг" + (s.powerW() > 0 ? " · " + RoomUnits.num(s.powerW(), 0) + " Вт" : "")
                        + " · поворот " + f.getRotation() + "°"));
                if (s.type() == RoomItemSpec.Type.DECK) {
                    JComboBox<String> pos = new JComboBox<>(new String[] {"Станок на полу", "Сверху на станках (как стол)"});
                    pos.setSelectedIndex(f.isOnTop() ? 1 : 0);
                    pos.addActionListener(e -> {
                        if (!building) {
                            boolean on = pos.getSelectedIndex() == 1;
                            session.edit(() -> f.setOnTop(on));
                        }
                    });
                    actions.add(pos);
                }
                Window w = SwingUtilities.getWindowAncestor(this);
                actions.add(button("Параметры…", () -> RoomOverridesDialog.show(w, session, f)));
                actions.add(button("Повернуть на 90°", () -> session.edit(() -> RoomEditor.rotate(l, sel))));
                actions.add(button("Убрать", this::removeSelection));
            }
            case LOOSE -> {
                RoomLooseItem li = RoomEditor.loose(l, sel.id());
                if (li == null) {
                    return;
                }
                RoomItemSpec s = cat.resolve(li.getRef());
                String where = switch (li.getMode()) {
                    case RUN -> {
                        RoomRun r = RoomEditor.run(l, li.getRunId());
                        yield "на трассе «" + (r != null ? r.getName() : "?") + "», "
                                + RoomUnits.num((li.getSCm() != null ? li.getSCm() : 0) / 100, 1) + " м от ввода";
                    }
                    case FREE -> "свободно на площадке";
                    case BOX -> "лежит в кофре мелочёвки";
                };
                text.add(title(s.name()));
                text.add(meta(cm(s.widthCm()) + "×" + cm(s.heightCm()) + "×" + cm(s.depthCm()) + " см · "
                        + RoomUnits.num(s.weightKg(), 2) + " кг · " + where));
                for (RoomLooseItem.Mode m : RoomLooseItem.Mode.values()) {
                    String label = switch (m) {
                        case FREE -> "Свободно";
                        case RUN -> "На трассе";
                        case BOX -> "В кофре";
                    };
                    JButton b = button(label, () -> session.edit(() ->
                            RoomEditor.setLooseMode(l, li, m, li.getRunId())));
                    b.setEnabled(li.getMode() != m && (m != RoomLooseItem.Mode.RUN || !l.getRuns().isEmpty()));
                    actions.add(b);
                }
                if (li.getMode() == RoomLooseItem.Mode.RUN && l.getRuns().size() > 1) {
                    JComboBox<RoomRun> runs = new JComboBox<>(l.getRuns().toArray(new RoomRun[0]));
                    runs.setRenderer(new javax.swing.DefaultListCellRenderer() {
                        @Override
                        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object v, int i,
                                                                               boolean sel2, boolean focus) {
                            return super.getListCellRendererComponent(list, v instanceof RoomRun r ? r.getName() : "", i, sel2, focus);
                        }
                    });
                    for (int i = 0; i < l.getRuns().size(); i++) {
                        if (l.getRuns().get(i).getId().equals(li.getRunId())) {
                            runs.setSelectedIndex(i);
                        }
                    }
                    runs.addActionListener(e -> {
                        RoomRun r = (RoomRun) runs.getSelectedItem();
                        if (!building && r != null && !r.getId().equals(li.getRunId())) {
                            session.edit(() -> RoomEditor.setLooseMode(l, li, RoomLooseItem.Mode.RUN, r.getId()));
                        }
                    });
                    actions.add(runs);
                }
                actions.add(button("Убрать", this::removeSelection));
            }
            case RUN -> {
                RoomRun r = RoomEditor.run(l, sel.id());
                if (r == null) {
                    return;
                }
                JTextField name = new JTextField(r.getName(), 18);
                name.setToolTipText("Название трассы — подписывается на плане");
                name.addActionListener(e -> rename(r, name.getText()));
                name.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        if (!building) {
                            rename(r, name.getText());
                        }
                    }
                });
                long on = l.getLoose().stream().filter(x -> x.getMode() == RoomLooseItem.Mode.RUN && r.getId().equals(x.getRunId()))
                        .count();
                text.add(name);
                text.add(meta(RoomUnits.num(RoomRunMath.length(r) / 100, 2) + " м · " + r.getPoints().size()
                        + " точек · мелочи на трассе: " + on));
                JComboBox<RoomRunKind> kinds = new JComboBox<>(RoomRunKind.values());
                kinds.setSelectedItem(RoomRunKind.of(r.getKind()));
                kinds.addActionListener(e -> {
                    if (!building) {
                        RoomRunKind k = (RoomRunKind) kinds.getSelectedItem();
                        session.edit(() -> r.setKind(k.key()));
                    }
                });
                actions.add(kinds);
                actions.add(button("Развернуть", () -> session.edit(() -> RoomRunMath.reverse(r, l.getLoose()))));
                actions.add(button("Удалить трассу", this::removeSelection));
            }
        }
    }

    private void rename(RoomRun r, String name) {
        String n = name == null ? "" : name.trim();
        if (!n.isEmpty() && !n.equals(r.getName())) {
            session.edit(() -> r.setName(n));
        }
    }

    private void removeSelection() {
        RoomSelection sel = session.selection();
        ControlRoomLayout l = session.layout();
        if (sel != null && l != null) {
            session.edit(() -> RoomEditor.removeSelection(l, sel));
        }
    }
}
