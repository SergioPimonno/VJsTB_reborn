package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PhysicalSpec;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Поля физического блока ({@link PhysicalSpec}) позиции библиотеки для Планнера пультовой: размеры в
 * миллиметрах (как в библиотеке), вес — кг, мощность — Вт. Три вида набора полей по тому, что значимо
 * для позиции: прибор ({@link Mode#DEVICE}: в стойку или россыпью), кофр ({@link Mode#CASE}: только
 * юниты и внутренняя глубина — наружные размеры у кофра свои) и мебель/станок/монитор
 * ({@link Mode#FURNITURE}: ширина, глубина, высота, вес, мощность).
 *
 * <p>Пустое поле — «не указано» ({@code null}), а не ноль: для Планнера это разные вещи (ноль весит
 * ноль, «не указано» помечается цветом). Поля, которых нет в выбранном виде набора, сохраняются из
 * исходного блока как есть.
 */
final class RoomDimensionsPanel extends JPanel {

    enum Mode { DEVICE, CASE, FURNITURE }

    private static final String[] FORM_FACTORS = {"В стойку 19″", "Россыпью"};
    private static final String[] FRACTIONS = {"целиком (12/12)", "1/2 (6/12)", "1/3 (4/12)", "1/4 (3/12)"};

    private final Mode mode;
    private final JComboBox<String> formFactorBox = new JComboBox<>(FORM_FACTORS);
    private final JTextField unitsField = new JTextField(8);
    private final JComboBox<String> fractionBox = new JComboBox<>(FRACTIONS);
    private final JTextField widthField = new JTextField(8);
    private final JTextField heightField = new JTextField(8);
    private final JTextField depthField = new JTextField(8);
    private final JTextField weightField = new JTextField(8);
    private final JTextField powerField = new JTextField(8);
    private PhysicalSpec base = new PhysicalSpec();

    RoomDimensionsPanel(Mode mode) {
        super(new GridLayout(0, 2, 8, 6));
        this.mode = mode;
        switch (mode) {
            case DEVICE -> {
                row("Установка", formFactorBox);
                row("Высота в стойке, U", unitsField);
                row("Доля ширины стойки", fractionBox);
                row("Ширина, мм", widthField);
                row("Высота, мм", heightField);
                row("Глубина, мм", depthField);
                row("Вес, кг", weightField);
                row("Мощность, Вт", powerField);
                formFactorBox.addActionListener(e -> syncEnabled());
            }
            case CASE -> {
                row("Высота рэка, U", unitsField);
                row("Внутренняя глубина, мм", depthField);
            }
            case FURNITURE -> {
                row("Ширина, мм", widthField);
                row("Глубина, мм", depthField);
                row("Высота, мм", heightField);
                row("Вес, кг", weightField);
                row("Мощность, Вт", powerField);
            }
        }
        syncEnabled();
    }

    private void row(String label, java.awt.Component field) {
        add(new JLabel(label));
        add(field);
    }

    private void syncEnabled() {
        if (mode != Mode.DEVICE) {
            return;
        }
        boolean rack = formFactorBox.getSelectedIndex() == 0;
        unitsField.setEnabled(rack);
        fractionBox.setEnabled(rack);
        widthField.setEnabled(!rack);
        heightField.setEnabled(!rack);
    }

    void load(PhysicalSpec spec) {
        base = spec != null ? spec.copy() : new PhysicalSpec();
        formFactorBox.setSelectedIndex(base.getFormFactor() == PhysicalSpec.FormFactor.LOOSE ? 1 : 0);
        unitsField.setText(base.getRackUnits() != null ? String.valueOf(base.getRackUnits()) : "");
        int tw = base.effectiveWidthTwelfths();
        fractionBox.setSelectedIndex(tw == 6 ? 1 : tw == 4 ? 2 : tw == 3 ? 3 : 0);
        widthField.setText(text(base.getWidthMm()));
        heightField.setText(text(base.getHeightMm()));
        depthField.setText(text(base.getDepthMm()));
        weightField.setText(text(base.getWeightKg()));
        powerField.setText(text(base.getPowerW()));
        syncEnabled();
    }

    /** Блок по полям формы. Некорректное число — {@link IllegalArgumentException} с понятным текстом. */
    PhysicalSpec read() {
        PhysicalSpec p = base.copy();
        switch (mode) {
            case DEVICE -> {
                boolean rack = formFactorBox.getSelectedIndex() == 0;
                p.setFormFactor(rack ? PhysicalSpec.FormFactor.RACK : PhysicalSpec.FormFactor.LOOSE);
                Double u = number(unitsField, "Высота в стойке");
                Integer units = u != null ? Integer.valueOf((int) Math.round(u)) : null;
                p.setRackUnits(rack ? units : p.getRackUnits());
                int idx = fractionBox.getSelectedIndex();
                Integer twelfths = null;
                if (idx == 1) {
                    twelfths = 6;
                } else if (idx == 2) {
                    twelfths = 4;
                } else if (idx == 3) {
                    twelfths = 3;
                }
                p.setWidthTwelfths(twelfths);
                p.setWidthMm(number(widthField, "Ширина"));
                p.setHeightMm(number(heightField, "Высота"));
                p.setDepthMm(number(depthField, "Глубина"));
                p.setWeightKg(number(weightField, "Вес"));
                p.setPowerW(number(powerField, "Мощность"));
            }
            case CASE -> {
                Double u = number(unitsField, "Высота рэка");
                p.setRackUnits(u != null ? Integer.valueOf((int) Math.round(u)) : null);
                p.setDepthMm(number(depthField, "Внутренняя глубина"));
            }
            case FURNITURE -> {
                p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
                p.setWidthMm(number(widthField, "Ширина"));
                p.setDepthMm(number(depthField, "Глубина"));
                p.setHeightMm(number(heightField, "Высота"));
                p.setWeightKg(number(weightField, "Вес"));
                p.setPowerW(number(powerField, "Мощность"));
            }
        }
        return p;
    }

    private static String text(Double v) {
        if (v == null) {
            return "";
        }
        return v == Math.rint(v) ? String.valueOf((long) Math.rint(v)) : String.valueOf(v);
    }

    private static Double number(JTextField f, String what) {
        String t = f.getText().trim().replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        double d;
        try {
            d = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + ": «" + f.getText().trim() + "» — не число");
        }
        if (d < 0 || Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException(what + ": число должно быть неотрицательным");
        }
        return d;
    }
}
