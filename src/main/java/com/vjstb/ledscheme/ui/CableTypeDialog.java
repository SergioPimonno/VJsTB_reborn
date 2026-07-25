package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ConnectorGender;
import com.vjstb.ledscheme.model.SchemaMode;
import java.awt.BorderLayout;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Мини-конструктор комбинированного кабеля (по аналогии с диалогами комплектации
 * карт): вместо свободного текста инженер выбирает пару разъёмов на концах кабеля
 * из готовых пресетов, а итоговая подпись собирается автоматически —
 * «{тип на выходе} (мама) → {тип на входе} (папа)». Исполнение («папа»/«мама») не
 * выбирается вручную, а жёстко определяется ролью конца кабеля: «мама» всегда
 * вставляется в выходной разъём (розетку источника/распределения), «папа» —
 * во входной разъём (вилку нагрузки/оборудования) — таков общепринятый стандарт
 * коммутации, выбора тут нет. Результат — обычная строка, совместимая с
 * существующей библиотекой кабелей (см. AppModel.addCableType), без изменений
 * модели данных.
 */
public class CableTypeDialog extends JDialog {

    private static final String[] POWER_CONNECTOR_PRESETS = {
            "CEE 125A", "CEE 63A", "CEE 32A", "CEE 16A", "Schuko",
            "PowerCon TRUE1", "PowerCon 20A", "TRUEcon", "IEC C13", "IEC C19", "Powerlock"
    };
    private static final String[] SIGNAL_CONNECTOR_PRESETS = {
            "SDI", "HDMI", "DisplayPort", "DVI", "Fiber", "Cat6/RJ45", "Genlock (SDI)", "XLR", "BNC"
    };

    private static final ConnectorGender OUTPUT_END_GENDER = ConnectorGender.FEMALE;
    private static final ConnectorGender INPUT_END_GENDER = ConnectorGender.MALE;

    private final JComboBox<SchemaMode> modeCombo = new JComboBox<>(SchemaMode.values());
    private final JComboBox<String> typeComboOutput = new JComboBox<>();
    private final JComboBox<String> typeComboInput = new JComboBox<>();
    private final JLabel preview = new JLabel(" ");
    private String result;

    public CableTypeDialog(Window owner) {
        this(owner, SchemaMode.POWER);
    }

    public CableTypeDialog(Window owner, SchemaMode initialMode) {
        super(owner, "Новый кабель/переходник", ModalityType.APPLICATION_MODAL);
        typeComboOutput.setEditable(true);
        typeComboInput.setEditable(true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel modeRow = new JPanel(new GridLayout(0, 2, 6, 4));
        modeRow.add(new JLabel("Режим"));
        modeRow.add(modeCombo);
        form.add(modeRow);
        form.add(Box.createVerticalStrut(6));

        JPanel endOutput = new JPanel(new GridLayout(0, 2, 6, 4));
        endOutput.setBorder(BorderFactory.createTitledBorder(
                "Выход источника — «" + OUTPUT_END_GENDER.getLabel() + "»"));
        endOutput.add(new JLabel("Тип"));
        endOutput.add(typeComboOutput);
        form.add(endOutput);
        form.add(Box.createVerticalStrut(6));

        JPanel endInput = new JPanel(new GridLayout(0, 2, 6, 4));
        endInput.setBorder(BorderFactory.createTitledBorder(
                "Вход нагрузки — «" + INPUT_END_GENDER.getLabel() + "»"));
        endInput.add(new JLabel("Тип"));
        endInput.add(typeComboInput);
        form.add(endInput);
        form.add(Box.createVerticalStrut(8));

        preview.setForeground(Palette.MUTED);
        form.add(preview);

        content.add(form, BorderLayout.CENTER);

        JButton save = new JButton("Сохранить");
        save.addActionListener(e -> onSave());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(save);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);

        modeCombo.addActionListener(e -> {
            populateTypesFor((SchemaMode) modeCombo.getSelectedItem());
            updatePreview();
        });
        bindPreviewUpdates(typeComboOutput);
        bindPreviewUpdates(typeComboInput);

        modeCombo.setSelectedItem(initialMode);
        populateTypesFor(initialMode);
        updatePreview();

        pack();
        setLocationRelativeTo(owner);
    }

    private void bindPreviewUpdates(JComboBox<String> combo) {
        combo.addActionListener(e -> updatePreview());
        Object editorComponent = combo.getEditor().getEditorComponent();
        if (editorComponent instanceof javax.swing.JTextField field) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updatePreview();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updatePreview();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updatePreview();
                }
            });
        }
    }

    private void populateTypesFor(SchemaMode mode) {
        String[] presets = mode == SchemaMode.POWER ? POWER_CONNECTOR_PRESETS : SIGNAL_CONNECTOR_PRESETS;
        typeComboOutput.setModel(new javax.swing.DefaultComboBoxModel<>(presets));
        typeComboInput.setModel(new javax.swing.DefaultComboBoxModel<>(presets));
        typeComboOutput.setSelectedIndex(0);
        typeComboInput.setSelectedIndex(presets.length > 1 ? 1 : 0);
    }

    private String typeOutput() {
        return String.valueOf(typeComboOutput.getEditor().getItem()).trim();
    }

    private String typeInput() {
        return String.valueOf(typeComboInput.getEditor().getItem()).trim();
    }

    private void updatePreview() {
        String output = typeOutput();
        String input = typeInput();
        if (output.isEmpty() || input.isEmpty()) {
            preview.setText(" ");
            return;
        }
        preview.setText(buildLabel(output, input));
    }

    private static String buildLabel(String outputType, String inputType) {
        return outputType + " (" + OUTPUT_END_GENDER.getLabel() + ") → " + inputType + " (" + INPUT_END_GENDER.getLabel() + ")";
    }

    private void onSave() {
        String output = typeOutput();
        String input = typeInput();
        if (output.isEmpty() || input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип разъёма на обоих концах кабеля",
                    "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = buildLabel(output, input);
        dispose();
    }

    public SchemaMode getMode() {
        return (SchemaMode) modeCombo.getSelectedItem();
    }

    /** Показывает диалог; возвращает собранную подпись кабеля или null при отмене. */
    public String showDialog() {
        setVisible(true);
        return result;
    }
}
