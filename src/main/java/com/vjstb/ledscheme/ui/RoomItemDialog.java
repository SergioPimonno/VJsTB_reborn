package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.service.AppModel;
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
 * Создание и правка ЛИЧНЫХ позиций библиотеки Планнера пультовой: «оборудование пультовой» (пульты,
 * мониторы-проверки, ИБП… — то, чего нет в пресетах схемы) и «мебель / станок / монитор». Запрос
 * 2026-10-23: «добавим в библиотеку напольные станки». Общие позиции здесь не правятся — их ведёт
 * администратор; свои габариты к общей позиции указываются через {@link RoomDimensionsDialog}.
 * Готовую личную позицию можно предложить на сервер из вкладки «Библиотека».
 */
final class RoomItemDialog extends JDialog {

    private final AppModel model;
    private final RoomEquipmentType equipment;
    private final RoomFurnitureType furniture;
    private final boolean creating;

    private final JTextField nameField = new JTextField(24);
    // оборудование
    private final JTextField companyField = new JTextField(24);
    private final JTextField categoryField = new JTextField(24);
    private final RoomDimensionsPanel devicePanel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.DEVICE);
    // мебель
    private final JComboBox<RoomFurnitureType.Kind> kindBox = new JComboBox<>(RoomFurnitureType.Kind.values());
    private final JComboBox<RoomFurnitureType.Role> roleBox = new JComboBox<>(RoomFurnitureType.Role.values());
    private final JTextField diagonalField = new JTextField(8);
    private final RoomDimensionsPanel furniturePanel = new RoomDimensionsPanel(RoomDimensionsPanel.Mode.FURNITURE);

    /** Новая позиция «оборудование пультовой». */
    static void createEquipment(Window owner, AppModel model) {
        new RoomItemDialog(owner, model, new RoomEquipmentType(), null, true).setVisible(true);
    }

    /** Новая позиция «мебель / станок / монитор». */
    static void createFurniture(Window owner, AppModel model) {
        new RoomItemDialog(owner, model, null, new RoomFurnitureType(), true).setVisible(true);
    }

    /** Правка существующей личной позиции. */
    static void editEquipment(Window owner, AppModel model, RoomEquipmentType item) {
        new RoomItemDialog(owner, model, item.copy(), null, false).setVisible(true);
    }

    static void editFurniture(Window owner, AppModel model, RoomFurnitureType item) {
        new RoomItemDialog(owner, model, null, item.copy(), false).setVisible(true);
    }

    private RoomItemDialog(Window owner, AppModel model, RoomEquipmentType equipment, RoomFurnitureType furniture,
                           boolean creating) {
        super(owner, title(equipment, creating), ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.equipment = equipment;
        this.furniture = furniture;
        this.creating = creating;

        JPanel head = new JPanel(new GridLayout(0, 2, 8, 6));
        head.add(new JLabel("Название"));
        head.add(nameField);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(head, BorderLayout.NORTH);
        if (equipment != null) {
            head.add(new JLabel("Производитель"));
            head.add(companyField);
            head.add(new JLabel("Категория (группа в списке)"));
            head.add(categoryField);
            nameField.setText(equipment.getName());
            companyField.setText(equipment.getCompany());
            categoryField.setText(equipment.getCategory());
            PhysicalSpec p = equipment.getPhysical();
            if (p == null) {
                p = new PhysicalSpec();
                p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
            }
            devicePanel.load(p);
            body.add(devicePanel, BorderLayout.CENTER);
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
            furniturePanel.load(com.vjstb.ledscheme.service.room.RoomLibraryEdit.furnitureAsSpec(furniture));
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
        content.add(UiKit.muted(UiKit.wrapHtml("Пустое поле — «не указано»: позиция будет выделена цветом в списке, "
                + "пока не заполнено всё нужное. Размеры — в миллиметрах.")), BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new java.awt.Dimension(480, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static String title(RoomEquipmentType equipment, boolean creating) {
        String what = equipment != null ? "оборудование пультовой" : "мебель / станок / монитор";
        return (creating ? "Новая позиция: " : "Позиция: ") + what;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) Math.rint(v)) : String.valueOf(v);
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
                equipment.setCategory(categoryField.getText().trim());
                equipment.setPhysical(devicePanel.read());
                if (creating) {
                    model.addRoomEquipmentType(equipment);
                } else {
                    model.updateRoomEquipmentType(equipment);
                }
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
}
