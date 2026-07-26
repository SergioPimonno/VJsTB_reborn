package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.List;
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
import javax.swing.SwingUtilities;

/**
 * Заготовка структурированной подписи связи схемы: каждая стрелка — это
 * коммутация, поэтому подпись всегда имеет вид «N×Тип» (плюс метраж для питания).
 * Для питания предложены типовые разъёмы (CEE 125/63/32/16A, Schuko, переходник),
 * для сигнала — типовые интерфейсы, плюс кабели из пользовательской библиотеки
 * (см. AppModel.cableTypesForMode) — оба списка редактируемы (можно вписать своё),
 * кроме случая коммутации через гнёзда (см. lockedConnectorType).
 */
public class WireLabelDialog extends JDialog {

    private static final String[] POWER_PRESETS = {
            "CEE 125A", "CEE 63A", "CEE 32A", "CEE 16A", "Schuko", "Переходник CEE-Schuko", "ВВГ 5х6"
    };
    private static final String[] SIGNAL_PRESETS = {
            "SDI", "HDMI", "DisplayPort", "DVI", "Fiber", "Cat6/RJ45", "Genlock (SDI)", "XLR"
    };

    private final AppModel model;
    private final SchemaMode mode;
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<String> typeCombo;
    private final JTextField lengthField = new JTextField();
    private final JLabel preview = new JLabel(" ");
    private boolean confirmed = false;

    public WireLabelDialog(Window owner, AppModel model, SchemaMode mode, SchemaEdge edge) {
        this(owner, model, mode, edge, Set.of());
    }

    /** connectorHints — типы разъёма питания кабинетов на одном из концов связи
     *  (если связь ведёт к реальному экрану): сужает список кабелей до совместимых
     *  с этими разъёмами (PowerCon/TRUEcon), т.к. основные типы силовой коммутации
     *  (CEE, Schuko и т.д.) используются выше по цепи, а не на самом кабинете.
     *  Больше ОДНОГО значения — экран смешивает несколько типов кабинетов
     *  (переопределение типа по ячейке): список кабелей объединяет варианты под
     *  ВСЕ присутствующие типы, а не только первый попавшийся. Пустое множество —
     *  тип не определён или не применимо (например, режим «Сигнал») — список не сужается. */
    public WireLabelDialog(Window owner, AppModel model, SchemaMode mode, SchemaEdge edge,
                            Set<PowerConnectorType> connectorHints) {
        this(owner, model, mode, edge, connectorHints, null, null, null);
    }

    /** lockedConnectorType — если связь заведена через конкретные гнёзда разъёмов
     *  (см. Task #52/#60), гнездо само определяет БАЗОВЫЙ номинал (пользователь не
     *  может вписать произвольный текст), но если известен тип разъёма кабинета на
     *  другом конце связи (connectorHints), список сужается до нескольких РЕЛЕВАНТНЫХ
     *  вариантов — голого номинала и переходника(ов) под этот разъём (в т.ч. из
     *  библиотеки кабелей), а не намертво фиксируется на голом номинале гнезда (баг-
     *  репорт: "доступен только CEE 16A, не CEE16A-TrueCon"). maxCount — максимум
     *  линий, которые ещё можно провести через это гнездо (по числу разъёмов группы
     *  за вычетом уже занятых другими связями) — null, если гнездо не определено
     *  (обычная связь узел-узел). maxCountReason — какой из ДВУХ концов связи сейчас
     *  определяет этот предел (лимит — минимум из обоих гнёзд, см. Task #70): без
     *  этого уточнения кажется багом, если у одного узла разъёмов явно больше, чем
     *  показанный максимум — на самом деле лимитирует ДРУГОЙ конец связи. */
    public WireLabelDialog(Window owner, AppModel model, SchemaMode mode, SchemaEdge edge,
                            Set<PowerConnectorType> connectorHints, String lockedConnectorType, Integer maxCount,
                            String maxCountReason) {
        super(owner, "Подпись связи (коммутация)", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.mode = mode;

        int spinnerMax = maxCount != null ? maxCount : 999;
        countSpinner.setModel(new SpinnerNumberModel(1, 1, spinnerMax, 1));
        MathFields.enableExpressions(countSpinner);

        List<String> libraryLabels = model != null ? model.cableTypesForMode(mode).stream()
                .map(CableType::getLabel).toList() : List.of();
        boolean locked = lockedConnectorType != null;
        String[] presets;
        if (locked) {
            // Не намертво фиксируем на голом номинале гнезда — предлагаем ещё и
            // релевантный(е) переходник(и) под разъём кабинета на другом конце связи,
            // включая совпадающие кабели библиотеки, чтобы их можно было ВЫБРАТЬ
            // (не просто получить один синтезированный вариант без права выбора).
            presets = lockedOptionsFor(lockedConnectorType, connectorHints, libraryLabels);
        } else {
            presets = mode == SchemaMode.POWER ? powerPresetsFor(connectorHints, libraryLabels)
                    : mergeWithLibrary(SIGNAL_PRESETS, libraryLabels);
        }
        typeCombo = new JComboBox<>(presets);
        // Гнездо всё равно ограничивает СВОБОДНЫЙ текст (нельзя вписать что угодно —
        // это конкретный физический разъём), но выбор среди предложенных релевантных
        // вариантов остаётся доступным (комбобокс не блокируется целиком).
        typeCombo.setEditable(!locked);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        form.add(new JLabel("Количество линий"));
        form.add(countSpinner);
        form.add(new JLabel(locked ? "Тип (по разъёму гнезда)" : "Тип"));
        form.add(typeCombo);
        if (mode == SchemaMode.POWER) {
            form.add(new JLabel("Метраж, м (необязательно)"));
            form.add(lengthField);
        }

        if (edge.getWireCount() != null) {
            countSpinner.setValue(Math.min(edge.getWireCount(), spinnerMax));
        }
        if (locked) {
            // Предпочитаем самый информативный вариант по умолчанию — переходник,
            // если он есть в предложенном списке, иначе голый номинал гнезда.
            String preferred = presets.length > 1 ? presets[presets.length - 1] : lockedConnectorType;
            typeCombo.setSelectedItem(preferred);
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
        if (!locked && connectorHints.size() > 1) {
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
        if (typeCombo.isEditable() && typeCombo.getEditor().getEditorComponent() instanceof javax.swing.JTextField tf) {
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
        JButton saveToLibrary = new JButton("💾 В библиотеку кабелей");
        saveToLibrary.setToolTipText("Сохранить текущий тип как кабель библиотеки — будет предложен"
                + " в списке в следующий раз");
        saveToLibrary.addActionListener(e -> saveCurrentTypeToLibrary());
        saveToLibrary.setEnabled(model != null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(saveToLibrary);
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
     *  сразу (см. WireLabelDialog(..., Set) выше), а не только под первый.
     *  libraryLabels — пользовательские кабели (см. AppModel.cableTypesForMode),
     *  добавляются в конец списка. */
    private static String[] powerPresetsFor(Set<PowerConnectorType> connectorHints, List<String> libraryLabels) {
        if (connectorHints.isEmpty()) {
            return mergeWithLibrary(POWER_PRESETS, libraryLabels);
        }
        java.util.LinkedHashSet<String> presets = new LinkedHashSet<>();
        for (PowerConnectorType hint : connectorHints) {
            switch (hint) {
                case POWERCON -> presets.add("CEE 16A → PowerCon");
                case TRUECON -> presets.add("CEE 16A → TrueCON");
                case OTHER -> { for (String p : POWER_PRESETS) presets.add(p); }
            }
        }
        presets.addAll(compatibleLibraryCables(libraryLabels, connectorHints));
        return presets.toArray(new String[0]);
    }

    /** Защита от дурака (Task #88): у каждого типа кабинета — ровно один разъём
     *  ввода питания (см. CabinetType.getPowerConnectorType), поэтому кабель, чей
     *  адаптерный конец рассчитан под ДРУГОЙ разъём, физически не подойдёт к
     *  кабинетам этого экрана — такие кабели library просто не показываем, а не
     *  добавляем в список "на всякий случай" (иначе ничто не мешает по ошибке
     *  выбрать кабель под чужой разъём). Если разъём кабинета неизвестен (нет
     *  намёков вовсе, или среди намёков есть {@link PowerConnectorType#OTHER}) —
     *  не сужаем: заведомо не можем сказать, что именно несовместимо. */
    private static List<String> compatibleLibraryCables(List<String> libraryLabels, Set<PowerConnectorType> hints) {
        if (hints.isEmpty() || hints.contains(PowerConnectorType.OTHER)) {
            return libraryLabels;
        }
        List<String> compatible = new java.util.ArrayList<>();
        for (String lib : libraryLabels) {
            String lower = lib.toLowerCase();
            for (PowerConnectorType hint : hints) {
                String keyword = connectorKeyword(hint);
                if (keyword != null && lower.contains(keyword)) {
                    compatible.add(lib);
                    break;
                }
            }
        }
        return compatible;
    }

    private static String connectorKeyword(PowerConnectorType hint) {
        return switch (hint) {
            case POWERCON -> "powercon";
            case TRUECON -> "truecon";
            case OTHER -> null;
        };
    }

    private static String[] mergeWithLibrary(String[] builtins, List<String> libraryLabels) {
        LinkedHashSet<String> all = new LinkedHashSet<>(List.of(builtins));
        all.addAll(libraryLabels);
        return all.toArray(new String[0]);
    }

    /** Список вариантов ТИПА для связи, заведённой через конкретное гнездо (см.
     *  lockedConnectorType в SchemaCanvasPanel.editEdgeLabel) — всегда включает голый
     *  номинал самого гнезда, плюс, если разъём кабинета на другом конце известен
     *  (ровно один намёк), переходник под него (тот же вариант, что и в свободном
     *  вводе — "CEE 16A → TrueCON"/"CEE 16A → PowerCon"), плюс любые кабели
     *  библиотеки, чья подпись содержит номинал гнезда (например, если инженер уже
     *  сохранил свой собственный вариант переходника под этот номинал). Дубликаты
     *  и уже комбинированные значения (гнездо само промаркировано с "→") не плодятся. */
    public static String[] lockedOptionsFor(String bareType, Set<PowerConnectorType> connectorHints,
                                             List<String> libraryLabels) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(bareType);
        if (!bareType.contains("→")) {
            List<String> adapterCandidates = new java.util.ArrayList<>();
            for (String lib : libraryLabels) {
                if (lib.startsWith(bareType) && lib.contains("→")) {
                    adapterCandidates.add(lib);
                }
            }
            options.addAll(compatibleLibraryCables(adapterCandidates, connectorHints));
            String enriched = withConnectorHint(bareType, connectorHints);
            if (!enriched.equals(bareType)) {
                options.add(enriched);
            }
        }
        return options.toArray(new String[0]);
    }

    /** Дополняет "голый" номинал линии (например «CEE 16A», как промаркировано само
     *  гнездо распределения) пометкой переходника под тип разъёма кабинета экрана —
     *  тем же вариантом, что и {@link #powerPresetsFor}. Ничего не меняет, если тип
     *  уже содержит "→" (сам уже адаптер) или намёков несколько/нет — тогда
     *  неоднозначно, какой именно адаптер подразумевать. */
    public static String withConnectorHint(String bareType, Set<PowerConnectorType> connectorHints) {
        if (bareType == null || bareType.contains("→") || connectorHints.size() != 1) {
            return bareType;
        }
        PowerConnectorType hint = connectorHints.iterator().next();
        String suffix = switch (hint) {
            case POWERCON -> " → PowerCon";
            case TRUECON -> " → TrueCON";
            case OTHER -> null;
        };
        return suffix != null ? bareType + suffix : bareType;
    }

    private boolean clearRequested = false;

    private String currentTypeText() {
        if (!typeCombo.isEditable()) {
            Object sel = typeCombo.getSelectedItem();
            return sel == null ? "" : String.valueOf(sel).trim();
        }
        return String.valueOf(typeCombo.getEditor().getItem()).trim();
    }

    private void saveCurrentTypeToLibrary() {
        if (model == null) {
            return;
        }
        CableTypeDialog dlg = new CableTypeDialog(SwingUtilities.getWindowAncestor(this), mode);
        String label = dlg.showDialog();
        if (label == null) {
            return;
        }
        model.addCableType(dlg.getMode(), label);
        JOptionPane.showMessageDialog(this, "Сохранено в библиотеку кабелей: " + label, "Библиотека кабелей",
                JOptionPane.INFORMATION_MESSAGE);
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
        return MathExpr.tryEval(lengthField.getText());
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
