package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
    /** Допустимые формы этой модели кабинета — какие вообще физически существуют
     *  (сужает выбор в радиальном меню "Форма" на конкретной ячейке, см. Task #79/v1.4). */
    private final Map<CabinetShape, JCheckBox> shapeChecks = new LinkedHashMap<>();
    /** Форма по умолчанию — какая из отмеченных выше применяется новым ячейкам этого
     *  типа; список вариантов всегда ограничен отмеченными допустимыми формами. */
    private final JComboBox<CabinetShape> defaultShapeField = new JComboBox<>(CabinetShape.values());
    private final JComboBox<PowerConnectorType> powerConnectorTypeField = new JComboBox<>(PowerConnectorType.values());
    /** Номинал разъёма (А) для типа "Другой" — для PowerCon/TRUEcon номинал фиксирован
     *  (16А, ограничение вводного кабеля, см. PowerCalc), для произвольного разъёма
     *  его нужно указать вручную, иначе расчёт нагрузки (Task #80/#81) не сможет
     *  проверить цепочки этого типа кабинета. */
    private final JTextField customConnectorAmpField = new JTextField();
    private final JLabel customConnectorAmpLabel = new JLabel("Номинал разъёма "
            + "«Другой», А (для расчёта нагрузки)");
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
        JPanel shapesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (CabinetShape s : CabinetShape.values()) {
            JCheckBox cb = new JCheckBox(s.getLabel());
            cb.addActionListener(ev -> onShapeCheckChanged());
            shapeChecks.put(s, cb);
            shapesPanel.add(cb);
        }
        form.add(new JLabel("Допустимые формы"));
        form.add(shapesPanel);
        form.add(new JLabel("Форма по умолчанию"));
        form.add(defaultShapeField);
        form.add(new JLabel("Тип разъёма питания"));
        form.add(powerConnectorTypeField);
        form.add(customConnectorAmpLabel);
        form.add(customConnectorAmpField);
        powerConnectorTypeField.addActionListener(ev -> refreshCustomConnectorAmpVisibility());
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
        Set<CabinetShape> allowedShapes = src.getAllowedShapes();
        for (var entry : shapeChecks.entrySet()) {
            entry.getValue().setSelected(allowedShapes.contains(entry.getKey()));
        }
        refreshDefaultShapeOptions();
        defaultShapeField.setSelectedItem(src.getShape());
        powerConnectorTypeField.setSelectedItem(src.getPowerConnectorType());
        customConnectorAmpField.setText(src.getCustomConnectorAmpRating() != null
                ? num(src.getCustomConnectorAmpRating()) : "");
        refreshCustomConnectorAmpVisibility();
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

    /** Не даём снять последнюю галочку — хотя бы одна форма должна остаться
     *  допустимой, иначе форму по умолчанию неоткуда будет взять. */
    private void onShapeCheckChanged() {
        boolean anyChecked = shapeChecks.values().stream().anyMatch(JCheckBox::isSelected);
        if (!anyChecked) {
            shapeChecks.values().iterator().next().setSelected(true);
        }
        refreshDefaultShapeOptions();
    }

    /** Список формы по умолчанию всегда ограничен отмеченными допустимыми формами —
     *  иначе можно было бы выбрать значением по умолчанию форму, которую сам же
     *  только что снял с допустимых. */
    private void refreshDefaultShapeOptions() {
        CabinetShape prevSelected = (CabinetShape) defaultShapeField.getSelectedItem();
        java.util.List<CabinetShape> checked = new java.util.ArrayList<>();
        for (var entry : shapeChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                checked.add(entry.getKey());
            }
        }
        defaultShapeField.setModel(new javax.swing.DefaultComboBoxModel<>(checked.toArray(new CabinetShape[0])));
        if (prevSelected != null && checked.contains(prevSelected)) {
            defaultShapeField.setSelectedItem(prevSelected);
        } else if (!checked.isEmpty()) {
            defaultShapeField.setSelectedItem(checked.get(0));
        }
    }

    /** Поле номинала актуально только для типа "Другой" — для PowerCon/TRUEcon
     *  номинал фиксирован в PowerCalc и не редактируется здесь. */
    private void refreshCustomConnectorAmpVisibility() {
        boolean isOther = powerConnectorTypeField.getSelectedItem() == PowerConnectorType.OTHER;
        customConnectorAmpLabel.setVisible(isOther);
        customConnectorAmpField.setVisible(isOther);
        customConnectorAmpField.setEnabled(isOther);
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
            Set<CabinetShape> allowed = new LinkedHashSet<>();
            for (var entry : shapeChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    allowed.add(entry.getKey());
                }
            }
            CabinetShape defaultShape = (CabinetShape) defaultShapeField.getSelectedItem();
            if (defaultShape == null || !allowed.contains(defaultShape)) {
                defaultShape = allowed.iterator().next();
            }
            ct.setShape(defaultShape);
            ct.setAllowedShapes(allowed);
            PowerConnectorType connectorType = (PowerConnectorType) powerConnectorTypeField.getSelectedItem();
            ct.setPowerConnectorType(connectorType);
            if (connectorType == PowerConnectorType.OTHER && !customConnectorAmpField.getText().trim().isEmpty()) {
                ct.setCustomConnectorAmpRating(parsePositive(customConnectorAmpField.getText(), "Номинал разъёма"));
            } else {
                ct.setCustomConnectorAmpRating(null);
            }
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
