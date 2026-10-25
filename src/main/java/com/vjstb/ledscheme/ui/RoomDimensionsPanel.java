package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PhysicalSpec;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Поля физического блока ({@link PhysicalSpec}) позиции библиотеки для Планера пультовой: размеры в
 * миллиметрах (как в библиотеке), вес — кг, мощность — Вт. Три вида набора полей по тому, что значимо
 * для позиции: прибор ({@link Mode#DEVICE}: в стойку или россыпью), кофр ({@link Mode#CASE}: только
 * юниты и внутренняя глубина — наружные размеры у кофра свои) и мебель/станок/монитор
 * ({@link Mode#FURNITURE}: ширина, глубина, высота, вес, мощность).
 *
 * <p>Пустое поле — «не указано» ({@code null}), а не ноль: для Планера это разные вещи (ноль весит
 * ноль, «не указано» помечается цветом). Поля, которых нет в выбранном виде набора, сохраняются из
 * исходного блока как есть.
 */
final class RoomDimensionsPanel extends JPanel {

    enum Mode { DEVICE, CASE, FURNITURE }

    private static final String[] FORM_FACTORS = {"В стойку 19″", "Россыпью", "Отдельно"};
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
    /** Блоки формы («Разбить на блоки»); {@code null} — позиция одной коробкой по размерам в полях. */
    private java.util.List<com.vjstb.ledscheme.model.PhysicalPart> parts;
    private final javax.swing.JButton partsButton = new javax.swing.JButton();

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
                row("Форма", partsButton);
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
                row("Форма", partsButton);
            }
        }
        partsButton.addActionListener(e -> editParts());
        syncEnabled();
    }

    private void row(String label, java.awt.Component field) {
        add(new JLabel(label));
        add(field);
    }

    private boolean hasParts() {
        return parts != null && !parts.isEmpty();
    }

    private void syncEnabled() {
        boolean has = hasParts();
        if (mode == Mode.DEVICE) {
            boolean rack = formFactorBox.getSelectedIndex() == 0;
            unitsField.setEnabled(rack);
            fractionBox.setEnabled(rack);
            // из блоков габарит считается сам; у «В стойку» блоки не рисуются, размеры задают юниты и глубина
            widthField.setEnabled(!rack && !has);
            heightField.setEnabled(!rack && !has);
            depthField.setEnabled(rack || !has);
        } else if (mode == Mode.FURNITURE) {
            widthField.setEnabled(!has);
            heightField.setEnabled(!has);
            depthField.setEnabled(!has);
        }
        weightField.setEnabled(!has || com.vjstb.ledscheme.model.PhysicalPart.totalWeightKg(parts) == null);
        partsButton.setText(has ? "Блоков: " + parts.size() + " — изменить…" : "Разбить на блоки…");
        partsButton.setToolTipText(has ? "Размеры и вес считаются по блокам; «Одной коробкой» в окне блоков убирает их"
                : "Позиция по умолчанию — одна коробка. Блоки нужны, если форма сложнее: экран на подставке, стол, пульт");
    }

    /** Габарит блоков в полях (мм) — они только показываются, редактируются блоки. */
    private void showBoxOfParts() {
        if (!hasParts()) {
            return;
        }
        java.util.List<com.vjstb.ledscheme.service.room.RoomPart> shape =
                com.vjstb.ledscheme.service.room.RoomShape.fromModel(parts);
        double[] bb = com.vjstb.ledscheme.service.room.RoomShape.bbox(shape);
        widthField.setText(text(Math.round(bb[0] * 100) / 10.0));
        depthField.setText(text(Math.round(bb[1] * 100) / 10.0));
        heightField.setText(text(Math.round(bb[2] * 100) / 10.0));
        Double kg = com.vjstb.ledscheme.model.PhysicalPart.totalWeightKg(parts);
        if (kg != null) {
            weightField.setText(text(kg));
        }
    }

    /** Окно блоков: начинается с одной коробки по текущим размерам, если блоков ещё нет. */
    private void editParts() {
        com.vjstb.ledscheme.model.PhysicalPart seed;
        try {
            seed = seedBox();
        } catch (IllegalArgumentException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Блоки", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean rack = mode == Mode.DEVICE && formFactorBox.getSelectedIndex() == 0;
        java.util.List<com.vjstb.ledscheme.model.PhysicalPart> r =
                RoomPartsDialog.show(javax.swing.SwingUtilities.getWindowAncestor(this), parts, seed, rack);
        if (r == null) {
            return;
        }
        parts = r.isEmpty() ? null : r;
        showBoxOfParts();
        syncEnabled();
    }

    /** Текущая коробка позиции как единственный начальный блок. */
    private com.vjstb.ledscheme.model.PhysicalPart seedBox() {
        boolean rack = mode == Mode.DEVICE && formFactorBox.getSelectedIndex() == 0;
        Double w = number(widthField, "Ширина");
        Double h = number(heightField, "Высота");
        Double d = number(depthField, "Глубина");
        Double kg = number(weightField, "Вес");
        if (rack) {
            Double u = number(unitsField, "Высота в стойке");
            int idx = fractionBox.getSelectedIndex();
            int tw = idx == 1 ? 6 : idx == 2 ? 4 : idx == 3 ? 3 : 12;
            w = 482.6 * tw / 12.0;
            h = (u != null ? u : 1.0) * 44.45;
        }
        return new com.vjstb.ledscheme.model.PhysicalPart("Корпус", w != null ? w : 400, d != null ? d : 300,
                h != null ? h : 100, 0, 0, 0, kg);
    }

    void load(PhysicalSpec spec) {
        base = spec != null ? spec.copy() : new PhysicalSpec();
        parts = com.vjstb.ledscheme.model.PhysicalPart.copyOf(base.getParts());
        formFactorBox.setSelectedIndex(base.getFormFactor() == PhysicalSpec.FormFactor.LOOSE ? 1
                : base.getFormFactor() == PhysicalSpec.FormFactor.STANDALONE ? 2 : 0);
        unitsField.setText(base.getRackUnits() != null ? String.valueOf(base.getRackUnits()) : "");
        int tw = base.effectiveWidthTwelfths();
        fractionBox.setSelectedIndex(tw == 6 ? 1 : tw == 4 ? 2 : tw == 3 ? 3 : 0);
        widthField.setText(text(base.getWidthMm()));
        heightField.setText(text(base.getHeightMm()));
        depthField.setText(text(base.getDepthMm()));
        weightField.setText(text(base.getWeightKg()));
        powerField.setText(text(base.getPowerW()));
        showBoxOfParts();
        syncEnabled();
    }

    /** Блок по полям формы. Некорректное число — {@link IllegalArgumentException} с понятным текстом. */
    PhysicalSpec read() {
        PhysicalSpec p = base.copy();
        switch (mode) {
            case DEVICE -> {
                int ff = formFactorBox.getSelectedIndex();
                boolean rack = ff == 0;
                p.setFormFactor(rack ? PhysicalSpec.FormFactor.RACK
                        : ff == 2 ? PhysicalSpec.FormFactor.STANDALONE : PhysicalSpec.FormFactor.LOOSE);
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
                p.setParts(com.vjstb.ledscheme.model.PhysicalPart.copyOf(parts));
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
                p.setParts(com.vjstb.ledscheme.model.PhysicalPart.copyOf(parts));
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
