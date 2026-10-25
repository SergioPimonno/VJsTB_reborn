package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomLibraryEdit;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Создание и правка ЛИЧНЫХ позиций библиотеки Планера: «блок» — оборудование пультовой (в стойку или россыпью),
 * рэк-кофр и «мебель / станок / монитор». Запрос 2026-10-23: нужный блок, которого нет в библиотеке, пользователь
 * должен создать сам, сохранить в личную библиотеку и при желании предложить на сервер. Общие позиции здесь не
 * правятся — их ведёт администратор; свои габариты к общей позиции указываются через {@link RoomDimensionsDialog}.
 * Готовую личную позицию предлагает на сервер {@link RoomLibraryUi#propose}.
 */
final class RoomItemDialog extends JDialog {

    private final AppModel model;
    private final RoomEquipmentType equipment;
    private final RoomFurnitureType furniture;
    private final CaseType rackCase;
    private final boolean creating;

    private final JTextField nameField = new JTextField(24);
    // оборудование
    private final JTextField companyField = new JTextField(24);
    /** Категория — выпадающий список уже существующих групп; новую можно ввести вручную. */
    private final javax.swing.JComboBox<String> categoryBox = new javax.swing.JComboBox<>();
    private final RoomDimensionsPanel devicePanel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.DEVICE);
    // мебель
    private final JComboBox<RoomFurnitureType.Kind> kindBox = new JComboBox<>(RoomFurnitureType.Kind.values());
    private final JComboBox<RoomFurnitureType.Role> roleBox = new JComboBox<>(RoomFurnitureType.Role.values());
    private final JTextField diagonalField = new JTextField(8);
    private final RoomDimensionsPanel furniturePanel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.FURNITURE);
    // рэк-кофр
    private final JTextField unitsField = new JTextField(6);
    private final JTextField innerDepthField = new JTextField(8);
    private final JTextField outerWidthField = new JTextField(8);
    private final JTextField outerDepthField = new JTextField(8);
    private final JTextField outerHeightField = new JTextField(8);
    private final JTextField caseWeightField = new JTextField(8);

    /** Новая позиция «оборудование пультовой». */
    static void createEquipment(Window owner, AppModel model) {
        new RoomItemDialog(owner, model, new RoomEquipmentType(), null, null, true).setVisible(true);
    }

    /**
     * Новый станок, стол/мебель или монитор. Вид выбирается заранее, размеры подставляются типовые (станок 2×1 м высотой
     * 40 см, стол 180×60×75, монитор 27″) — их остаётся только поправить по своему изделию.
     */
    static void createFurniture(Window owner, AppModel model, RoomFurnitureType.Kind kind) {
        RoomFurnitureType t = new RoomFurnitureType();
        t.setKind(kind);
        switch (kind) {
            case DECK -> {
                t.setName("Станок 2×1 м");
                t.setWidthMm(2000.0);
                t.setDepthMm(1000.0);
                t.setHeightMm(400.0);
            }
            case MONITOR -> {
                t.setName("Монитор 27″");
                t.setWidthMm(610.0);
                t.setDepthMm(220.0);
                t.setHeightMm(480.0);
                t.setDiagonalIn(27.0);
                t.setWeightKg(5.5);
                t.setPowerW(35.0);
            }
            default -> {
                t.setName("Стол 180×60");
                t.setRole(RoomFurnitureType.Role.TABLE);
                t.setWidthMm(1800.0);
                t.setDepthMm(600.0);
                t.setHeightMm(750.0);
            }
        }
        new RoomItemDialog(owner, model, null, t, null, true).setVisible(true);
    }

    /** Новый рэк-кофр. */
    static void createCase(Window owner, AppModel model) {
        CaseType c = new CaseType();
        c.setWidthMm(600);
        c.setWeightKg(0);
        new RoomItemDialog(owner, model, null, null, c, true).setVisible(true);
    }

    /** Правка существующей личной позиции. */
    static void editEquipment(Window owner, AppModel model, RoomEquipmentType item) {
        new RoomItemDialog(owner, model, item.copy(), null, null, false).setVisible(true);
    }

    static void editFurniture(Window owner, AppModel model, RoomFurnitureType item) {
        new RoomItemDialog(owner, model, null, item.copy(), null, false).setVisible(true);
    }

    static void editCase(Window owner, AppModel model, CaseType item) {
        new RoomItemDialog(owner, model, null, null, item.copy(), false).setVisible(true);
    }

    private RoomItemDialog(Window owner, AppModel model, RoomEquipmentType equipment, RoomFurnitureType furniture,
                           CaseType rackCase, boolean creating) {
        super(owner, title(equipment, rackCase, creating), ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.equipment = equipment;
        this.furniture = furniture;
        this.rackCase = rackCase;
        this.creating = creating;

        JPanel head = new JPanel(new GridLayout(0, 2, 8, 6));
        head.add(new JLabel("Название"));
        head.add(nameField);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(head, BorderLayout.NORTH);
        String intro = "Пустое поле — «не указано»: позиция будет выделена цветом в списке, пока не заполнено всё нужное. "
                + "Размеры — в миллиметрах.";
        if (equipment != null) {
            head.add(new JLabel("Производитель"));
            head.add(companyField);
            head.add(new JLabel("Категория (группа в списке)"));
            head.add(categoryBox);
            nameField.setText(equipment.getName());
            companyField.setText(equipment.getCompany());
            categoryBox.setEditable(true);
            for (String c : com.vjstb.ledscheme.service.room.RoomLibraryEdit.knownCategories(model)) {
                categoryBox.addItem(c);
            }
            categoryBox.setSelectedItem(equipment.getCategory() != null && !equipment.getCategory().isBlank()
                    ? equipment.getCategory() : "Прочее оборудование");
            PhysicalSpec p = equipment.getPhysical();
            if (p == null) {
                p = new PhysicalSpec();
                p.setFormFactor(PhysicalSpec.FormFactor.RACK);
                p.setRackUnits(1);
            }
            devicePanel.load(p);
            body.add(devicePanel, BorderLayout.CENTER);
        } else if (rackCase != null) {
            nameField.setText(rackCase.getName());
            PhysicalSpec p = rackCase.getPhysical() != null ? rackCase.getPhysical() : new PhysicalSpec();
            unitsField.setText(p.getRackUnits() != null ? String.valueOf(p.getRackUnits()) : "");
            innerDepthField.setText(p.getDepthMm() != null ? trim(p.getDepthMm()) : "");
            outerWidthField.setText(rackCase.getWidthMm() > 0 ? trim(rackCase.getWidthMm()) : "");
            outerDepthField.setText(rackCase.getLengthMm() > 0 ? trim(rackCase.getLengthMm()) : "");
            outerHeightField.setText(rackCase.getHeightMm() > 0 ? trim(rackCase.getHeightMm()) : "");
            caseWeightField.setText(rackCase.getWeightKg() > 0 ? trim(rackCase.getWeightKg()) : "");
            JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
            grid.add(new JLabel("Высота рэка, U"));
            grid.add(unitsField);
            grid.add(new JLabel("Внутренняя глубина, мм"));
            grid.add(innerDepthField);
            grid.add(new JLabel("Наружная ширина, мм"));
            grid.add(outerWidthField);
            grid.add(new JLabel("Наружная глубина, мм"));
            grid.add(outerDepthField);
            grid.add(new JLabel("Наружная высота, мм"));
            grid.add(outerHeightField);
            grid.add(new JLabel("Вес пустого кофра, кг"));
            grid.add(caseWeightField);
            JButton calc = new JButton("Рассчитать наружные размеры");
            calc.setToolTipText("Наружная высота = высота юнитов + 160 мм на рамы и крышки, глубина = внутренняя + 170 мм, "
                    + "ширина 600 мм — приблизительно, поправьте по паспорту кофра");
            calc.addActionListener(e -> estimateOuter());
            JPanel wrap = new JPanel(new BorderLayout(0, 6));
            wrap.add(grid, BorderLayout.CENTER);
            wrap.add(calc, BorderLayout.SOUTH);
            body.add(wrap, BorderLayout.CENTER);
            intro = "Рэк-кофр: высота в юнитах и внутренняя глубина определяют, что в него встанет; наружные размеры нужны "
                    + "для места на плане и для перевозки. Размеры — в миллиметрах.";
        } else {
            head.add(new JLabel("Вид"));
            head.add(kindBox);
            head.add(new JLabel("Роль (стол держит башни и мониторы)"));
            head.add(roleBox);
            head.add(new JLabel("Диагональ, дюймы (для монитора)"));
            head.add(diagonalField);
            nameField.setText(furniture.getName());
            kindBox.setSelectedItem(furniture.getKind());
            roleBox.setSelectedItem(furniture.getRole());
            diagonalField.setText(furniture.getDiagonalIn() != null ? trim(furniture.getDiagonalIn()) : "");
            furniturePanel.load(RoomLibraryEdit.furnitureAsSpec(furniture));
            body.add(furniturePanel, BorderLayout.CENTER);
        }

        JButton save = new JButton("Сохранить");
        JButton cancel = new JButton("Отмена");
        save.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(save);
        buttons.add(cancel);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(UiKit.muted(UiKit.wrapHtml(intro + (creating
                ? " Позиция сохранится в вашей библиотеке; предложить её на сервер можно кнопкой «Предложить…»." : ""))),
                BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new java.awt.Dimension(500, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static String title(RoomEquipmentType equipment, CaseType rackCase, boolean creating) {
        String what = equipment != null ? "блок / оборудование" : rackCase != null ? "рэк-кофр" : "мебель / станок / монитор";
        return (creating ? "Новая позиция: " : "Позиция: ") + what;
    }

    private String categoryText() {
        Object v = categoryBox.getEditor().getItem();
        return v == null ? "" : v.toString().trim();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) Math.rint(v)) : String.valueOf(v);
    }

    private static Double number(JTextField f, String what, boolean required) {
        String t = f.getText().trim().replace(',', '.');
        if (t.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(what + ": укажите значение");
            }
            return null;
        }
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + ": «" + f.getText().trim() + "» — не число");
        }
        if (v < 0 || (required && v == 0)) {
            throw new IllegalArgumentException(what + ": число должно быть больше нуля");
        }
        return v;
    }

    private void estimateOuter() {
        try {
            double u = number(unitsField, "Высота рэка", true);
            double d = number(innerDepthField, "Внутренняя глубина", true);
            outerHeightField.setText(trim(Math.round(u * 44.45 + 160)));
            outerDepthField.setText(trim(d + 170));
            if (outerWidthField.getText().trim().isEmpty()) {
                outerWidthField.setText("600");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Рэк-кофр", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название.", "Позиция", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (equipment != null) {
                equipment.setName(name);
                equipment.setCompany(companyField.getText().trim());
                equipment.setCategory(categoryText());
                equipment.setPhysical(devicePanel.read());
                if (creating) {
                    model.addRoomEquipmentType(equipment);
                } else {
                    model.updateRoomEquipmentType(equipment);
                }
            } else if (rackCase != null) {
                saveCase(name);
            } else {
                PhysicalSpec p = furniturePanel.read();
                furniture.setName(name);
                furniture.setKind((RoomFurnitureType.Kind) kindBox.getSelectedItem());
                furniture.setRole((RoomFurnitureType.Role) roleBox.getSelectedItem());
                furniture.setWidthMm(p.getWidthMm());
                furniture.setDepthMm(p.getDepthMm());
                furniture.setHeightMm(p.getHeightMm());
                furniture.setWeightKg(p.getWeightKg());
                furniture.setPowerW(p.getPowerW());
                furniture.setParts(com.vjstb.ledscheme.model.PhysicalPart.copyOf(p.getParts()));
                String diag = diagonalField.getText().trim().replace(',', '.');
                furniture.setDiagonalIn(diag.isEmpty() ? null : Double.valueOf(diag));
                if (creating) {
                    model.addRoomFurnitureType(furniture);
                } else {
                    model.updateRoomFurnitureType(furniture);
                }
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Диагональ: «" + diagonalField.getText().trim() + "» — не число",
                    "Позиция", JOptionPane.WARNING_MESSAGE);
            return;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Не удалось сохранить", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
    }

    private void saveCase(String name) {
        int units = (int) Math.round(number(unitsField, "Высота рэка", true));
        double inner = number(innerDepthField, "Внутренняя глубина", true);
        double w = number(outerWidthField, "Наружная ширина", true);
        double d = number(outerDepthField, "Наружная глубина", true);
        double h = number(outerHeightField, "Наружная высота", true);
        Double kg = number(caseWeightField, "Вес", false);
        rackCase.setName(name);
        rackCase.setWidthMm(w);
        rackCase.setLengthMm(d);
        rackCase.setHeightMm(h);
        rackCase.setWeightKg(kg != null ? kg : 0);
        PhysicalSpec p = rackCase.getPhysical() != null ? rackCase.getPhysical() : new PhysicalSpec();
        p.setRackUnits(units);
        p.setDepthMm(inner);
        rackCase.setPhysical(p);
        if (creating) {
            model.addCaseType(rackCase);
        } else {
            model.updateCaseType(rackCase);
        }
    }
}
