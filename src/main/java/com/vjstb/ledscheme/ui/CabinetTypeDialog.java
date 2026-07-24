package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Модальный диалог добавления/редактирования типа кабинета в библиотеке. */
public class CabinetTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextField widthField = new JTextField();
    private final JTextField heightField = new JTextField();
    private final JTextField depthField = new JTextField();
    private final JTextField resWField = new JTextField();
    private final JTextField resHField = new JTextField();
    private final JTextField powerField = new JTextField();
    private final JTextField weightField = new JTextField();
    private final JComboBox<CabinetShape> shapeField = new JComboBox<>(CabinetShape.values());
    private final JComboBox<PowerConnectorType> powerConnectorTypeField = new JComboBox<>(PowerConnectorType.values());
    private final JTextField powerConnectorsField = new JTextField();
    private final JTextField signalConnectorsField = new JTextField();

    private CabinetType result;

    public CabinetTypeDialog(Window owner, CabinetType existing) {
        super(owner, existing == null ? "Новый кабинет" : "Редактирование кабинета", ModalityType.APPLICATION_MODAL);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Ширина (мм)"));
        form.add(widthField);
        form.add(new JLabel("Высота (мм)"));
        form.add(heightField);
        form.add(new JLabel("Глубина (мм, необязательно)"));
        form.add(depthField);
        form.add(new JLabel("Разрешение W (px)"));
        form.add(resWField);
        form.add(new JLabel("Разрешение H (px)"));
        form.add(resHField);
        form.add(new JLabel("Мощность (Вт)"));
        form.add(powerField);
        form.add(new JLabel("Вес (кг)"));
        form.add(weightField);
        form.add(new JLabel("Форма"));
        form.add(shapeField);
        form.add(new JLabel("Тип разъёма питания"));
        form.add(powerConnectorTypeField);
        form.add(new JLabel("Линий питания на кабинет (0 = встроено)"));
        form.add(powerConnectorsField);
        form.add(new JLabel("Линий сигнала на кабинет (0 = встроено)"));
        form.add(signalConnectorsField);

        CabinetType src = existing != null ? existing : new CabinetType();
        nameField.setText(src.getName());
        widthField.setText(num(src.getWidthMm()));
        heightField.setText(num(src.getHeightMm()));
        depthField.setText(src.getDepthMm() != null ? num(src.getDepthMm()) : "");
        resWField.setText(String.valueOf(src.getResolutionWidth()));
        resHField.setText(String.valueOf(src.getResolutionHeight()));
        powerField.setText(num(src.getPowerConsumptionW()));
        weightField.setText(num(src.getWeightKg()));
        shapeField.setSelectedItem(src.getShape());
        powerConnectorTypeField.setSelectedItem(src.getPowerConnectorType());
        powerConnectorsField.setText(String.valueOf(src.getPowerConnectorsNeeded()));
        signalConnectorsField.setText(String.valueOf(src.getSignalConnectorsNeeded()));

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk(existing));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private void onOk(CabinetType existing) {
        try {
            CabinetType ct = existing != null ? existing.copy() : new CabinetType();
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название кабинета");
            }
            ct.setName(name);
            ct.setWidthMm(parsePositive(widthField.getText(), "Ширина"));
            ct.setHeightMm(parsePositive(heightField.getText(), "Высота"));
            ct.setDepthMm(depthField.getText().trim().isEmpty() ? null : parsePositive(depthField.getText(), "Глубина"));
            ct.setResolutionWidth((int) parsePositive(resWField.getText(), "Разрешение W"));
            ct.setResolutionHeight((int) parsePositive(resHField.getText(), "Разрешение H"));
            ct.setPowerConsumptionW(parseNonNeg(powerField.getText(), "Мощность"));
            ct.setWeightKg(parseNonNeg(weightField.getText(), "Вес"));
            ct.setShape((CabinetShape) shapeField.getSelectedItem());
            ct.setPowerConnectorType((PowerConnectorType) powerConnectorTypeField.getSelectedItem());
            ct.setPowerConnectorsNeeded((int) parseNonNeg(powerConnectorsField.getText(), "Линий питания"));
            ct.setSignalConnectorsNeeded((int) parseNonNeg(signalConnectorsField.getText(), "Линий сигнала"));
            result = ct;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parsePositive(String s, String field) {
        double v = parseNonNeg(s, field);
        if (v <= 0) {
            throw new IllegalArgumentException(field + ": значение должно быть больше 0");
        }
        return v;
    }

    private double parseNonNeg(String s, String field) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            if (v < 0) {
                throw new IllegalArgumentException(field + ": значение не может быть отрицательным");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": введите число");
        }
    }

    private static String num(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** Показывает диалог; возвращает заполненный тип или null при отмене. */
    public CabinetType showDialog() {
        setVisible(true);
        return result;
    }

    static Component vgap() {
        return Box.createVerticalStrut(6);
    }
}
