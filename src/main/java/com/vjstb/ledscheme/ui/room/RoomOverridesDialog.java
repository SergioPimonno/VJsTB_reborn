package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomOverrides;
import com.vjstb.ledscheme.service.room.RoomGeometry;
import com.vjstb.ledscheme.service.room.RoomItemSpec;
import com.vjstb.ledscheme.service.room.RoomLibraryEdit;
import com.vjstb.ledscheme.service.room.RoomUnits;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Параметры мебели, станка или монитора на плане (правый клик по объекту — запрос 2026-10-23). «Применить»
 * меняет только этот объект (переопределения {@link RoomOverrides}); «Сохранить как позицию» делает из
 * подогнанных параметров новую личную позицию библиотеки и ставит её на место объекта. Размеры — в
 * сантиметрах, как на плане.
 */
final class RoomOverridesDialog extends JDialog {

    private final PlannerSession session;
    private final RoomFurniture furniture;
    private final RoomItemSpec base;
    private final JTextField name = new JTextField(22);
    private final JTextField width = new JTextField(8);
    private final JTextField depth = new JTextField(8);
    private final JTextField height = new JTextField(8);
    private final JTextField weight = new JTextField(8);
    private final JTextField power = new JTextField(8);

    static void show(Window owner, PlannerSession session, RoomFurniture f) {
        new RoomOverridesDialog(owner, session, f).setVisible(true);
    }

    private RoomOverridesDialog(Window owner, PlannerSession session, RoomFurniture f) {
        super(owner, "Параметры объекта", ModalityType.APPLICATION_MODAL);
        this.session = session;
        this.furniture = f;
        this.base = RoomGeometry.effective(f, session.catalog());
        RoomItemSpec lib = session.catalog().resolve(f.getRef());

        name.setText(base.name());
        width.setText(RoomUnits.num(base.widthCm(), 1));
        depth.setText(RoomUnits.num(base.depthCm(), 1));
        height.setText(RoomUnits.num(base.heightCm(), 1));
        weight.setText(RoomUnits.num(base.weightKg(), 1));
        power.setText(RoomUnits.num(base.powerW(), 0));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.add(new JLabel("Название"));
        grid.add(name);
        grid.add(new JLabel("Ширина, см"));
        grid.add(width);
        grid.add(new JLabel("Глубина, см"));
        grid.add(depth);
        grid.add(new JLabel("Высота, см"));
        grid.add(height);
        grid.add(new JLabel("Вес, кг"));
        grid.add(weight);
        if (base.type() == RoomItemSpec.Type.MONITOR) {
            grid.add(new JLabel("Мощность, Вт"));
            grid.add(power);
        }

        JButton apply = new JButton("Применить");
        JButton preset = new JButton("Сохранить как позицию");
        JButton cancel = new JButton("Отмена");
        apply.addActionListener(e -> apply());
        preset.addActionListener(e -> saveAsPreset());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(preset);
        buttons.add(apply);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        JLabel hint = new JLabel("<html><div style='width:360px'>Основа в библиотеке: «" + escape(lib.name())
                + "». «Применить» меняет только этот объект на плане. «Сохранить как позицию» добавляет новую личную "
                + "позицию в библиотеку и ставит её на место этого объекта.</div></html>");
        content.add(hint, BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(apply);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private RoomOverrides read() {
        RoomOverrides o = new RoomOverrides();
        String n = name.getText().trim();
        o.setName(n.isEmpty() ? "Без названия" : n);
        o.setWidthCm(number(width, "Ширина", true));
        o.setDepthCm(number(depth, "Глубина", true));
        o.setHeightCm(number(height, "Высота", true));
        o.setWeightKg(number(weight, "Вес", false));
        o.setPowerW(base.type() == RoomItemSpec.Type.MONITOR ? number(power, "Мощность", false) : base.powerW());
        return o;
    }

    private static Double number(JTextField f, String what, boolean positive) {
        String t = f.getText().trim().replace(',', '.');
        if (t.isEmpty()) {
            return positive ? null : 0.0;
        }
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + ": «" + f.getText().trim() + "» — не число");
        }
        if (v < 0 || (positive && v == 0)) {
            throw new IllegalArgumentException(what + ": число должно быть " + (positive ? "больше нуля" : "неотрицательным"));
        }
        return v;
    }

    private void apply() {
        RoomOverrides o;
        try {
            o = read();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Параметры", JOptionPane.WARNING_MESSAGE);
            return;
        }
        session.edit(() -> furniture.setOverrides(o));
        dispose();
    }

    private void saveAsPreset() {
        RoomOverrides o;
        try {
            o = read();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Параметры", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            session.edit(() -> {
                furniture.setOverrides(o);
                RoomItemSpec eff = RoomGeometry.effective(furniture, session.catalog());
                RoomLibraryEdit.saveFurnitureAsPreset(session.model(), furniture, eff);
            });
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Не удалось сохранить", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
    }
}
