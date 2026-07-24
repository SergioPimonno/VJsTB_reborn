package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControllerType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Модальный диалог добавления/редактирования типа контроллера в библиотеке.
 * Пропускная способность порта и число пикселей на порт (при опорных 60 Гц/8 бит,
 * как в даташитах) взаимозависимы — правка одного поля сразу пересчитывает другое,
 * чтобы можно было работать в удобных единицах (кому — Мбит/с, кому — пиксели).
 */
public class ControllerTypeDialog extends JDialog {

    private static final String[] BANDWIDTH_PRESETS = {"100", "1000", "2500", "10000"};

    private final JTextField nameField = new JTextField();
    private final JTextField vendorField = new JTextField();
    private final JTextField portCountField = new JTextField();
    private final JComboBox<String> bandwidthCombo = new JComboBox<>(BANDWIDTH_PRESETS);
    private final JTextField pixelsField = new JTextField();
    private final JTextField inputPortsField = new JTextField();
    private final JCheckBox loopPortCheck = new JCheckBox("Есть Loop-порт (например, HDMI loop у VX1000)");
    private boolean syncing;

    private ControllerType result;

    public ControllerTypeDialog(Window owner, ControllerType existing) {
        super(owner, existing == null ? "Новый контроллер" : "Редактирование контроллера", ModalityType.APPLICATION_MODAL);
        bandwidthCombo.setEditable(true);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        form.add(new JLabel("Название/модель"));
        form.add(nameField);
        form.add(new JLabel("Производитель"));
        form.add(vendorField);
        form.add(new JLabel("Число портов вывода"));
        form.add(portCountField);
        form.add(new JLabel("Пропускная способность порта, Мбит/с"));
        form.add(bandwidthCombo);
        form.add(new JLabel("Пикселей на порт (при 60 Гц/8 бит)"));
        form.add(pixelsField);

        // входные порты — второстепенная информация, вынесена ниже отдельной
        // приглушённой строкой, чтобы не бросалась в глаза среди основных полей
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        JLabel inputLabel = new JLabel("Входных портов");
        inputLabel.setForeground(Palette.MUTED);
        inputPortsField.setColumns(4);
        inputRow.add(inputLabel, BorderLayout.WEST);
        inputRow.add(inputPortsField, BorderLayout.CENTER);
        inputRow.add(loopPortCheck, BorderLayout.EAST);

        ControllerType src = existing != null ? existing : new ControllerType();
        nameField.setText(src.getName());
        vendorField.setText(src.getVendor());
        portCountField.setText(String.valueOf(src.getPortCount()));
        bandwidthCombo.getEditor().setItem(UiKit.fmt(src.getPortBandwidthMbps()));
        pixelsField.setText(String.valueOf(src.referencePixelsPerPort()));
        inputPortsField.setText(String.valueOf(src.getInputPortCount()));
        loopPortCheck.setSelected(src.isLoopPort());

        // взаимный пересчёт: правка одного поля живо обновляет другое (при опорных 60Гц/8бит)
        docListener(bandwidthField(), () -> syncFromBandwidth());
        pixelsField.getDocument().addDocumentListener(simpleListener(this::syncFromPixels));

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
        content.add(Box.createVerticalStrut(4));
        content.add(inputRow);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private JTextField bandwidthField() {
        return (JTextField) bandwidthCombo.getEditor().getEditorComponent();
    }

    private void docListener(JTextField field, Runnable onChange) {
        field.getDocument().addDocumentListener(simpleListener(onChange));
    }

    private DocumentListener simpleListener(Runnable onChange) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onChange.run(); }
            public void removeUpdate(DocumentEvent e) { onChange.run(); }
            public void changedUpdate(DocumentEvent e) { onChange.run(); }
        };
    }

    private void syncFromBandwidth() {
        if (syncing) return;
        try {
            double mbps = Double.parseDouble(bandwidthField().getText().trim().replace(',', '.'));
            int px = ControllerType.maxPixelsFor(mbps, ControllerType.REFERENCE_HZ, ControllerType.REFERENCE_BIT_DEPTH);
            syncing = true;
            pixelsField.setText(String.valueOf(px));
        } catch (NumberFormatException ignored) {
            // пользователь ещё печатает — не мешаем
        } finally {
            syncing = false;
        }
    }

    private void syncFromPixels() {
        if (syncing) return;
        try {
            int px = Integer.parseInt(pixelsField.getText().trim());
            double mbps = ControllerType.bandwidthForPixels(px, ControllerType.REFERENCE_HZ, ControllerType.REFERENCE_BIT_DEPTH);
            syncing = true;
            bandwidthField().setText(UiKit.fmt(Math.round(mbps * 10) / 10.0));
        } catch (NumberFormatException ignored) {
            // пользователь ещё печатает — не мешаем
        } finally {
            syncing = false;
        }
    }

    private void onOk(ControllerType existing) {
        try {
            ControllerType ct = existing != null ? existing.copy() : new ControllerType();
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название контроллера");
            }
            ct.setName(name);
            ct.setVendor(vendorField.getText().trim());
            ct.setPortCount((int) parsePositive(portCountField.getText(), "Число портов"));
            ct.setPortBandwidthMbps(parsePositive(bandwidthField().getText(), "Пропускная способность порта"));
            ct.setInputPortCount((int) parseNonNeg(inputPortsField.getText(), "Входных портов"));
            ct.setLoopPort(loopPortCheck.isSelected());
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

    /** Показывает диалог; возвращает заполненный тип или null при отмене. */
    public ControllerType showDialog() {
        setVisible(true);
        return result;
    }
}
