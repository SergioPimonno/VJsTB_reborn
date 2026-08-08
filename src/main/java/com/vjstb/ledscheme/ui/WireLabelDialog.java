package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
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
 * Список типов — СТРОГО из двух библиотек (никаких хардкодных абстрактных
 * пресетов вроде голого "CEE 16A"/"HDMI" текстом, который ни на что не ссылается):
 * {@link CableLengthProfile} (однородный кабель, для которого считается сплайсовка
 * в спецификации — см. service.CableSpecCalc) и {@link CableType} (переходники —
 * разъёмы на концах разные, у них может быть фиксированная длина вместо каталога,
 * см. class-javadoc CableType). Комбобокс всё равно остаётся редактируемым —
 * свободный текст допускается как запасной вариант (пока нужного типа ещё нет в
 * библиотеке), кнопки внизу дают тут же зарегистрировать его в нужной библиотеке
 * (локально и/или предложить в общую — см. {@link #registerAsAdapter}/
 * {@link #registerAsLengthProfile}) — кроме случая коммутации через гнёзда (см.
 * lockedConnectorType), где список сужен до вариантов под конкретный разъём.
 */
public class WireLabelDialog extends JDialog {

    private final AppModel model;
    private final SettingsManager settings;
    private final SchemaMode mode;
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<String> typeCombo;
    private final JTextField lengthField = new JTextField();
    private final JLabel preview = new JLabel(" ");
    private boolean confirmed = false;

    public WireLabelDialog(Window owner, AppModel model, SettingsManager settings, SchemaMode mode, SchemaEdge edge) {
        this(owner, model, settings, mode, edge, Set.of());
    }

    /** connectorHints — типы разъёма питания кабинетов на одном из концов связи
     *  (если связь ведёт к реальному экрану): сужает список кабелей до совместимых
     *  с этими разъёмами (PowerCon/TRUEcon), т.к. основные типы силовой коммутации
     *  (CEE, Schuko и т.д.) используются выше по цепи, а не на самом кабинете.
     *  Больше ОДНОГО значения — экран смешивает несколько типов кабинетов
     *  (переопределение типа по ячейке): список кабелей объединяет варианты под
     *  ВСЕ присутствующие типы, а не только первый попавшийся. Пустое множество —
     *  тип не определён или не применимо (например, режим «Сигнал») — список не сужается. */
    public WireLabelDialog(Window owner, AppModel model, SettingsManager settings, SchemaMode mode, SchemaEdge edge,
                            Set<PowerConnectorType> connectorHints) {
        this(owner, model, settings, mode, edge, connectorHints, null, null, null, true);
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
     *  показанный максимум — на самом деле лимитирует ДРУГОЙ конец связи.
     *  enforceCap — жёстко ограничивать спиннер числом maxCount (обычное поведение,
     *  часть «Защиты от дурака»): при выключенной защите инженер может знать про
     *  особый случай (например, физически смонтированный лишний резервный кабель),
     *  который расчёт не учитывает — тогда ограничение снимается целиком (до 999),
     *  а не просто ослабляется, иначе оно бы просто перепрыгнуло к другому пределу
     *  без реальной возможности переопределить как считает нужным сам инженер. */
    public WireLabelDialog(Window owner, AppModel model, SettingsManager settings, SchemaMode mode, SchemaEdge edge,
                            Set<PowerConnectorType> connectorHints, String lockedConnectorType, Integer maxCount,
                            String maxCountReason, boolean enforceCap) {
        this(owner, model, settings, mode, edge, connectorHints, lockedConnectorType, maxCount, maxCountReason,
                enforceCap, false);
    }

    /** forceSingleCable — связь заведена через КОНКРЕТНОЕ гнездо при включённом
     *  режиме отображения разъёмов «по одному» (см. ConnectorDisplayMode.INDIVIDUAL,
     *  Task #2/v1.6): каждая отдельно нарисованная точка-гнездо — это ровно один
     *  физический разъём, поэтому «сколько кабелей в этой линии» перестаёт быть
     *  осмысленным вопросом — всегда 1, спиннер блокируется (не скрывается целиком —
     *  так виднее, что значение НЕ забыто, а намеренно зафиксировано). */
    public WireLabelDialog(Window owner, AppModel model, SettingsManager settings, SchemaMode mode, SchemaEdge edge,
                            Set<PowerConnectorType> connectorHints, String lockedConnectorType, Integer maxCount,
                            String maxCountReason, boolean enforceCap, boolean forceSingleCable) {
        super(owner, "Подпись связи (коммутация)", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.settings = settings;
        this.mode = mode;

        int spinnerMax = enforceCap && maxCount != null ? maxCount : 999;
        countSpinner.setModel(new SpinnerNumberModel(1, 1, spinnerMax, 1));
        MathFields.enableExpressions(countSpinner);
        if (forceSingleCable) {
            countSpinner.setValue(1);
            countSpinner.setEnabled(false);
        }

        // Список типов — строго из библиотек (см. class-javadoc): каталог длин
        // (однородный кабель, сплайсуется) + кабели/переходники (разные концы,
        // опционально фиксированная длина). Никаких хардкодных абстрактных пресетов.
        List<String> lengthProfileNames = model != null ? model.cableLengthProfilesForMode(mode).stream()
                .map(CableLengthProfile::getName).toList() : List.of();
        List<String> adapterLabels = model != null ? model.cableTypesForMode(mode).stream()
                .map(CableType::getLabel).toList() : List.of();
        boolean locked = lockedConnectorType != null;
        String[] presets;
        if (locked) {
            // Не намертво фиксируем на голом номинале гнезда — предлагаем ещё и
            // релевантный(е) переходник(и) под разъём кабинета на другом конце связи,
            // включая совпадающие кабели библиотеки, чтобы их можно было ВЫБРАТЬ
            // (не просто получить один синтезированный вариант без права выбора).
            presets = lockedOptionsFor(lockedConnectorType, connectorHints, lengthProfileNames, adapterLabels);
        } else {
            presets = mode == SchemaMode.POWER ? powerPresetsFor(connectorHints, lengthProfileNames, adapterLabels)
                    : mergeLists(lengthProfileNames, adapterLabels);
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
        // Раньше поле показывалось только для питания (у сигнала обычно короткие
        // патч-кабели, длина не важна для спецификации) — но теперь у сигнала тоже
        // бывают зарегистрированные однородные типы со сплайсовкой (например, Fiber
        // MMF LC/SC на десятки-сотни метров между МХ/CVT), для которых длина как раз
        // критична, а задать её было негде (баг-репорт). Показываем всегда,
        // необязательно — короткие сигнальные кабели просто оставляют поле пустым.
        form.add(new JLabel("Метраж, м (необязательно)"));
        form.add(lengthField);

        if (!forceSingleCable && edge.getWireCount() != null) {
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

        if (forceSingleCable) {
            JLabel singleHint = new JLabel("<html>Каждый разъём нарисован отдельным гнездом (см. Персонализация —"
                    + " «показывать каждый разъём карты отдельным гнездом») — эта линия всегда одна.</html>");
            singleHint.setForeground(Palette.MUTED);
            form.add(new JLabel());
            form.add(singleHint);
        } else if (maxCount != null) {
            String text = "Максимум линий на этом гнезде: " + maxCount
                    + (maxCountReason != null ? " (ограничивает: " + maxCountReason + ")" : "")
                    + (enforceCap ? "" : " — защита от дурака выключена, ограничение не применяется");
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
        JButton saveAsAdapter = new JButton("💾 Как переходник…");
        saveAsAdapter.setToolTipText("Разъёмы на концах разные — сохранить в библиотеку переходников"
                + " (см. class-javadoc CableType), появится в списке в следующий раз");
        saveAsAdapter.addActionListener(e -> registerAsAdapter());
        saveAsAdapter.setEnabled(model != null);
        JButton saveAsLengthProfile = new JButton("💾 Как каталог длин…");
        saveAsLengthProfile.setToolTipText("Однородный кабель (одинаковый разъём на обоих концах) — сохранить"
                + " в каталог длин, чтобы для него считалась сплайсовка в спецификации");
        saveAsLengthProfile.addActionListener(e -> registerAsLengthProfile());
        saveAsLengthProfile.setEnabled(model != null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(saveAsAdapter);
        buttons.add(saveAsLengthProfile);
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
     *  lengthProfileNames — зарегистрированные однородные типы (каталог длин),
     *  adapterLabels — зарегистрированные переходники (см. AppModel.cableTypesForMode). */
    private static String[] powerPresetsFor(Set<PowerConnectorType> connectorHints, List<String> lengthProfileNames,
                                             List<String> adapterLabels) {
        if (connectorHints.isEmpty()) {
            return mergeLists(lengthProfileNames, adapterLabels);
        }
        java.util.LinkedHashSet<String> presets = new LinkedHashSet<>();
        for (PowerConnectorType hint : connectorHints) {
            switch (hint) {
                case POWERCON -> presets.add("CEE 16A → PowerCon");
                case TRUECON -> presets.add("CEE 16A → TrueCON");
                // Разъём кабинета неизвестен (не PowerCon/TrueCon специфично) —
                // нет конкретного переходника для подсказки, показываем как есть
                // зарегистрированные однородные типы.
                case OTHER -> presets.addAll(lengthProfileNames);
            }
        }
        presets.addAll(compatibleLibraryCables(adapterLabels, connectorHints));
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

    private static String[] mergeLists(List<String> a, List<String> b) {
        LinkedHashSet<String> all = new LinkedHashSet<>(a);
        all.addAll(b);
        return all.toArray(new String[0]);
    }

    /** Список вариантов ТИПА для связи, заведённой через конкретное гнездо (см.
     *  lockedConnectorType в SchemaCanvasPanel.editEdgeLabel) — всегда включает голый
     *  номинал самого гнезда, плюс:
     *  — зарегистрированные однородные типы каталога длин, чьё имя начинается с этого
     *  номинала (например, «Fiber MMF LC»/«Fiber MMF SC» для голого «Fiber» —
     *  баг-репорт: раньше каталог длин тут вообще не смотрели, доступен был только
     *  абстрактный голый номинал, хотя в библиотеке уже есть конкретные типы);
     *  — если разъём кабинета на другом конце известен (ровно один намёк), переходник
     *  под него (тот же вариант, что и в свободном вводе — "CEE 16A → TrueCON"/
     *  "CEE 16A → PowerCon"), плюс любые переходники библиотеки, чья подпись содержит
     *  номинал гнезда (например, если инженер уже сохранил свой собственный вариант
     *  переходника под этот номинал). Дубликаты и уже комбинированные значения
     *  (гнездо само промаркировано с "→") не плодятся. */
    public static String[] lockedOptionsFor(String bareType, Set<PowerConnectorType> connectorHints,
                                             List<String> lengthProfileNames, List<String> adapterLabels) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(bareType);
        if (!bareType.contains("→")) {
            for (String name : lengthProfileNames) {
                if (name.startsWith(bareType)) {
                    options.add(name);
                }
            }
            List<String> adapterCandidates = new java.util.ArrayList<>();
            for (String lib : adapterLabels) {
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

    /** Разъёмы на концах разные (см. class-javadoc CableType) — сохраняет локально
     *  в библиотеку переходников, затем сразу предлагает отправить его же в общую
     *  библиотеку на модерацию (см. {@link #offerToPropose}): админ не может
     *  предусмотреть заранее все переходники, которые реально нужны на площадке. */
    private void registerAsAdapter() {
        if (model == null) {
            return;
        }
        CableTypeDialog dlg = new CableTypeDialog(SwingUtilities.getWindowAncestor(this), model, mode);
        String label = dlg.showDialog();
        if (label == null) {
            return;
        }
        CableType saved = model.addCableType(dlg.getMode(), label, dlg.getFixedLengthM());
        JOptionPane.showMessageDialog(this, "Сохранено в библиотеку переходников: " + label,
                "Библиотека кабелей", JOptionPane.INFORMATION_MESSAGE);
        offerToPropose("CABLE", saved.getLabel(), saved);
    }

    /** Однородный кабель (одинаковый разъём на обоих концах) — сохраняет локально
     *  в каталог длин (для сплайсовки, см. class-javadoc CableLengthProfile), затем
     *  так же предлагает его в общую библиотеку (см. {@link #registerAsAdapter}). */
    private void registerAsLengthProfile() {
        if (model == null) {
            return;
        }
        String prefill = currentTypeText();
        CableLengthProfile created = new CableLengthProfileDialog(SwingUtilities.getWindowAncestor(this), null, mode,
                prefill.isEmpty() ? null : prefill).showDialog();
        if (created == null) {
            return;
        }
        try {
            model.addCableLengthProfile(created);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Сохранено в каталог длин: " + created.getName(),
                "Каталог длин кабелей", JOptionPane.INFORMATION_MESSAGE);
        offerToPropose("CABLE_LENGTH_PROFILE", created.getName(), created);
    }

    /** Сразу за локальным сохранением — предложить тот же элемент в общую
     *  библиотеку на модерацию (см. ProposeDialog), чтобы не нужно было отдельно
     *  искать его потом на этапе «Библиотеки». ProposeDialog сам разберётся, если
     *  пользователь не вошёл в аккаунт (см. его javadoc), поэтому вызывается
     *  безусловно; settings == null (в контексте без настроек) — пропускаем. */
    private void offerToPropose(String libraryItemKind, String itemName, Object item) {
        if (settings == null) {
            return;
        }
        ProposeDialog.show(SwingUtilities.getWindowAncestor(this), settings, libraryItemKind, itemName, item);
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
