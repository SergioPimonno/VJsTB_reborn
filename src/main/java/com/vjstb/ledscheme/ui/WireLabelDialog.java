package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Заготовка структурированной подписи связи схемы: каждая стрелка — это
 * коммутация, поэтому подпись всегда имеет вид «N×Тип» (плюс метраж для питания).
 * Для питания предложены типовые разъёмы (CEE 125/63/32/16A, Schuko, переходник),
 * для сигнала — типовые интерфейсы; оба поля редактируемые (можно вписать своё).
 */
public class WireLabelDialog extends JDialog {

    private static final String[] POWER_PRESETS = {
            "CEE 125A", "CEE 63A", "CEE 32A", "CEE 16A", "Schuko", "Переходник CEE-Schuko", "ВВГ 5х6"
    };
    private static final String[] SIGNAL_PRESETS = {
            "SDI", "HDMI", "DisplayPort", "DVI", "Fiber", "Cat6/RJ45", "Genlock (SDI)", "XLR"
    };

    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<String> typeCombo;
    private final JTextField lengthField = new JTextField();
    private final JLabel preview = new JLabel(" ");
    private boolean confirmed = false;

    public WireLabelDialog(Window owner, SchemaMode mode, SchemaEdge edge) {
        this(owner, mode, edge, Set.of());
    }

    /** connectorHints — типы разъёма питания кабинетов на одном из концов связи
     *  (если связь ведёт к реальному экрану): сужает список кабелей до совместимых
     *  с этими разъёмами (PowerCon/TRUEcon), т.к. основные типы силовой коммутации
     *  (CEE, Schuko и т.д.) используются выше по цепи, а не на самом кабинете.
     *  Больше ОДНОГО значения — экран смешивает несколько типов кабинетов
     *  (переопределение типа по ячейке): список кабелей объединяет варианты под
     *  ВСЕ присутствующие типы, а не только первый попавшийся. Пустое множество —
     *  тип не определён или не применимо (например, режим «Сигнал») — список не сужается. */
    public WireLabelDialog(Window owner, SchemaMode mode, SchemaEdge edge, Set<PowerConnectorType> connectorHints) {
        this(owner, mode, edge, connectorHints, null, null, null);
    }

    /** lockedConnectorType — если связь заведена через конкретные гнёзда разъёмов
     *  (см. Task #52/#60), тип кабеля определяется автоматически по типу разъёма
     *  гнезда — пользователю остаётся указать только количество, поле типа
     *  блокируется. maxCount — максимум линий, которые ещё можно провести через
     *  это гнездо (по числу разъёмов группы за вычетом уже занятых другими
     *  связями) — null, если гнездо не определено (обычная связь узел-узел).
     *  maxCountReason — какой из ДВУХ концов связи сейчас определяет этот предел
     *  (лимит — минимум из обоих гнёзд, см. Task #70): без этого уточнения кажется
     *  багом, если у одного узла разъёмов явно больше, чем показанный максимум —
     *  на самом деле лимитирует ДРУГОЙ конец связи. */
    public WireLabelDialog(Window owner, SchemaMode mode, SchemaEdge edge, Set<PowerConnectorType> connectorHints,
                            String lockedConnectorType, Integer maxCount, String maxCountReason) {
        super(owner, "Подпись связи (коммутация)", ModalityType.APPLICATION_MODAL);

        int spinnerMax = maxCount != null ? maxCount : 999;
        countSpinner.setModel(new SpinnerNumberModel(1, 1, spinnerMax, 1));

        String[] presets = mode == SchemaMode.POWER ? powerPresetsFor(connectorHints) : SIGNAL_PRESETS;
        typeCombo = lockedConnectorType != null
                ? new JComboBox<>(new String[]{lockedConnectorType})
                : new JComboBox<>(presets);
        typeCombo.setEditable(lockedConnectorType == null);
        if (lockedConnectorType != null) {
            typeCombo.setEnabled(false);
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        form.add(new JLabel("Количество линий"));
        form.add(countSpinner);
        form.add(new JLabel(lockedConnectorType != null ? "Тип (по разъёму гнезда)" : "Тип"));
        form.add(typeCombo);
        if (mode == SchemaMode.POWER) {
            form.add(new JLabel("Метраж, м (необязательно)"));
            form.add(lengthField);
        }

        if (edge.getWireCount() != null) {
            countSpinner.setValue(Math.min(edge.getWireCount(), spinnerMax));
        }
        if (lockedConnectorType != null) {
            typeCombo.setSelectedItem(lockedConnectorType);
        } else if (edge.getWireType() != null) {
            typeCombo.setSelectedItem(edge.getWireType());
        } else if (edge.getLabel() != null) {
            typeCombo.getEditor().setItem(edge.getLabel());
        }
        if (edge.getLengthM() != null) {
            lengthField.setText(UiKit.fmt(edge.getLengthM()));
        }

        if (maxCount != null) {
            String text = "Максимум линий на этом гнезде: " + maxCount
                    + (maxCountReason != null ? " (ограничивает: " + maxCountReason + ")" : "");
            JLabel capHint = new JLabel("<html>" + text + "</html>");
            capHint.setForeground(Palette.MUTED);
            form.add(new JLabel());
            form.add(capHint);
        }
        if (lockedConnectorType == null && connectorHints.size() > 1) {
            // Экран смешивает несколько типов кабинетов (переопределение по ячейке) —
            // список кабелей объединяет варианты под все типы сразу, уточняем это,
            // иначе непонятно, почему в списке сразу и PowerCon-, и TRUEcon-варианты.
            JLabel mixHint = new JLabel("<html>Экран использует несколько типов кабинетов — уточните тип"
                    + " кабеля вручную.</html>");
            mixHint.setForeground(Palette.MUTED);
            form.add(new JLabel());
            form.add(mixHint);
        }

        countSpinner.addChangeListener(e -> updatePreview());
        typeCombo.addActionListener(e -> updatePreview());
        if (typeCombo.getEditor().getEditorComponent() instanceof javax.swing.JTextField tf) {
            tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            });
        }
        lengthField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
        });

        preview.setForeground(Palette.ACCENT);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JButton clear = new JButton("Очистить подпись");
        clear.addActionListener(e -> {
            confirmed = true;
            clearRequested = true;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(clear);
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        JPanel previewRow = new JPanel(new BorderLayout());
        previewRow.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        previewRow.add(new JLabel("Подпись: "), BorderLayout.WEST);
        previewRow.add(preview, BorderLayout.CENTER);
        content.add(previewRow);
        content.add(buttons);

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        updatePreview();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Кабель, которым реально заводят питание В кабинет — переходник с силовой
     *  магистрали (CEE16A у распределительного щита/проходной) на разъём САМОГО
     *  кабинета (PowerCon/TRUEcon). connectorHints — типы разъёмов кабинетов
     *  экрана на этом конце связи; больше ОДНОГО значения — экран смешивает
     *  несколько типов кабинетов, тогда список объединяет варианты под все типы
     *  сразу (см. WireLabelDialog(..., Set) выше), а не только под первый. */
    private static String[] powerPresetsFor(Set<PowerConnectorType> connectorHints) {
        if (connectorHints.isEmpty()) {
            return POWER_PRESETS;
        }
        java.util.LinkedHashSet<String> presets = new LinkedHashSet<>();
        for (PowerConnectorType hint : connectorHints) {
            switch (hint) {
                case POWERCON -> presets.add("CEE 16A → PowerCon");
                case TRUECON -> presets.add("CEE 16A → TrueCON");
                case OTHER -> { for (String p : POWER_PRESETS) presets.add(p); }
            }
        }
        return presets.toArray(new String[0]);
    }

    private boolean clearRequested = false;

    private String currentTypeText() {
        if (!typeCombo.isEditable()) {
            Object sel = typeCombo.getSelectedItem();
            return sel == null ? "" : String.valueOf(sel).trim();
        }
        return String.valueOf(typeCombo.getEditor().getItem()).trim();
    }

    private void updatePreview() {
        String type = currentTypeText();
        int count = (Integer) countSpinner.getValue();
        StringBuilder sb = new StringBuilder();
        sb.append(count).append('×').append(type.isEmpty() ? "…" : type);
        Double len = parseLengthOrNull();
        if (len != null && len > 0) {
            sb.append(", ").append(UiKit.fmt(len)).append("м");
        }
        preview.setText(sb.toString());
    }

    private Double parseLengthOrNull() {
        String s = lengthField.getText().trim().replace(',', '.');
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void onOk() {
        String type = currentTypeText();
        if (type.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите тип линии", "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String lenText = lengthField.getText().trim();
        if (!lenText.isEmpty() && parseLengthOrNull() == null) {
            JOptionPane.showMessageDialog(this, "Метраж: введите число", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        confirmed = true;
        dispose();
    }

    /** true, если пользователь нажал «Сохранить» (или «Очистить подпись»). */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** true, если пользователь запросил снять подпись целиком (кнопка «Очистить подпись»). */
    public boolean isClearRequested() {
        return clearRequested;
    }

    public int getCount() {
        return (Integer) countSpinner.getValue();
    }

    public String getWireType() {
        return currentTypeText();
    }

    public Double getLengthM() {
        return parseLengthOrNull();
    }
}
