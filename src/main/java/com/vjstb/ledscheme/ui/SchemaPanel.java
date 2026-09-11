package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.stage.CurrentSchemeExporter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Общая схема площадки (yEd-подобная): холст с узлами оборудования и связями
 * + панель добавления узлов. Показывает либо схему питания, либо сигнала —
 * определяется полем mode; узлы двух схем хранятся раздельно в той же сцене.
 */
public class SchemaPanel extends JPanel {

    private static final int SIDE_WIDTH = 260;
    /** Сентинел в presetCombo — «свой текст» вместо пресета библиотеки. */
    private static final String OTHER_SENTINEL = "Другое (свой текст)";

    private final AppModel model;
    private final SchemaMode mode;
    private final SchemaCanvasPanel canvas;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;

    private final JComboBox<SchemaNodeType> typeCombo = new JComboBox<>(SchemaNodeType.values());
    /** Для не-SCREEN типов: сначала пресеты библиотеки этой категории, затем OTHER_SENTINEL. */
    private final JComboBox<Object> presetCombo = new JComboBox<>();
    private final JComboBox<Screen> screenCombo = new JComboBox<>();
    private final JTextField labelField = new JTextField();
    private final JButton saveAsPresetBtn = new JButton("💾 Сохранить как пресет");
    private final JToggleButton moveBtn = new JToggleButton("Перемещение", true);
    private final JToggleButton connectBtn = new JToggleButton("Соединение");
    private final JLabel selectionHint = new JLabel(" ");

    public SchemaPanel(AppModel model, SchemaMode mode, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.mode = mode;
        this.settings = settings;
        this.canvas = new SchemaCanvasPanel(model, mode, settings);
        canvas.setOnChanged(this::refresh);

        setLayout(new BorderLayout());
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);

        JScrollPane sideScroll = new JScrollPane(buildSide());
        sideScroll.setBorder(null);
        sideScroll.setMinimumSize(new Dimension(180, 100));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasScroll, sideScroll);
        split.setContinuousLayout(true);
        split.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "schema." + mode.name().toLowerCase() + ".split", split,
                1.0 - (SIDE_WIDTH + 20) / 1360.0);
        add(split, BorderLayout.CENTER);

        model.addListener(this::refresh);
        refresh();
    }

    public void setOnScreenActivated(Consumer<Screen> listener) {
        canvas.setOnScreenActivated(listener);
    }

    /** Переключает инструмент холста Перемещение↔Соединение по горячей клавише
     *  (см. HotkeyAction.TOGGLE_SCHEMA_INTERACTION, диспетчер — MainFrame) — в
     *  отличие от прямого canvas.setInteraction(...), синхронизирует ещё и вид
     *  переключателей moveBtn/connectBtn ниже, которые иначе остались бы показывать
     *  старый инструмент. */
    public void toggleInteraction() {
        SchemaCanvasPanel.Interaction next = canvas.getInteraction() == SchemaCanvasPanel.Interaction.MOVE
                ? SchemaCanvasPanel.Interaction.CONNECT : SchemaCanvasPanel.Interaction.MOVE;
        canvas.setInteraction(next);
        moveBtn.setSelected(next == SchemaCanvasPanel.Interaction.MOVE);
        connectBtn.setSelected(next == SchemaCanvasPanel.Interaction.CONNECT);
    }

    private JPanel buildSide() {
        JPanel body = UiKit.vboxFixedWidth(SIDE_WIDTH);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        typeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaNodeType t) {
                    setText(model.categoryLabel(t));
                }
                return this;
            }
        });
        typeCombo.addActionListener(e -> { refreshPresetCombo(); updateAddFormEnablement(); });

        JPanel addBody = UiKit.vbox();
        addBody.add(new JLabel("Тип узла"));
        addBody.add(typeCombo);
        addBody.add(UiKit.vgap());
        addBody.add(new JLabel("Экран (для типа «Экран»)"));
        screenCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Screen s) {
                    setText(s.getName());
                }
                return this;
            }
        });
        addBody.add(screenCombo);
        addBody.add(UiKit.vgap());
        addBody.add(new JLabel("Пресет (для прочих типов)"));
        presetCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EquipmentPreset p) {
                    setText(p.getName());
                } else if (value instanceof String s) {
                    setText(s);
                }
                return this;
            }
        });
        presetCombo.addActionListener(e -> updateAddFormEnablement());
        addBody.add(presetCombo);
        addBody.add(UiKit.vgap());
        addBody.add(new JLabel("Подпись (свой текст)"));
        labelField.putClientProperty("JTextField.placeholderText", "например, «Щит А1»…");
        addBody.add(labelField);
        addBody.add(UiKit.vgap());
        saveAsPresetBtn.setToolTipText("Сохранить введённую подпись как пресет библиотеки для этой категории");
        saveAsPresetBtn.addActionListener(e -> saveAsPreset());
        addBody.add(saveAsPresetBtn);
        addBody.add(UiKit.vgap());
        JButton addBtn = new JButton("+ Добавить узел");
        addBtn.addActionListener(e -> addNode());
        addBody.add(addBtn);
        body.add(UiKit.dynamicSection("Добавить узел", addBody));
        body.add(UiKit.vgap());

        if (mode == SchemaMode.SIGNAL) {
            JButton legendBtn = new JButton("+ Легенда портов");
            legendBtn.setToolTipText("Автоблок: по каждому экрану — основной и резервный контроллер/порты."
                    + " Содержимое пересчитывается само, перетаскивается и масштабируется как любой узел.");
            legendBtn.addActionListener(e -> addPortLegendNode());
            body.add(legendBtn);
            body.add(UiKit.vgap());

            JButton exportLegendBtn = new JButton("Экспорт легенды портов…");
            exportLegendBtn.setToolTipText("Сохранить ту же таблицу (экран / main / backup) отдельным PNG-файлом"
                    + " — для передачи заказчику/монтажникам без открытия программы");
            exportLegendBtn.addActionListener(e -> exportPortLegend());
            body.add(exportLegendBtn);
            body.add(UiKit.vgap());
        }

        ButtonGroup g = new ButtonGroup();
        g.add(moveBtn);
        g.add(connectBtn);
        JPanel modeRow = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
        moveBtn.addActionListener(e -> canvas.setInteraction(SchemaCanvasPanel.Interaction.MOVE));
        connectBtn.addActionListener(e -> canvas.setInteraction(SchemaCanvasPanel.Interaction.CONNECT));
        modeRow.add(moveBtn);
        modeRow.add(connectBtn);
        modeRow.setAlignmentX(LEFT_ALIGNMENT);
        modeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, modeRow.getPreferredSize().height));
        body.add(modeRow);
        body.add(UiKit.vgap());

        selectionHint.setForeground(Palette.MUTED);
        body.add(selectionHint);
        JButton delSelected = new JButton("Удалить выбранное");
        delSelected.addActionListener(e -> canvas.deleteSelected());
        UiKit.bindDeleteKey(canvas, canvas::deleteSelected);
        body.add(UiKit.vgap());
        body.add(delSelected);

        JButton clear = new JButton("Очистить схему");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Удалить все узлы и связи этой схемы (" + (mode == SchemaMode.POWER ? "питание" : "сигнал") + ")?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                model.clearSchema(mode);
            }
        });
        body.add(UiKit.vgap());
        body.add(clear);
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void updateAddFormEnablement() {
        boolean isScreen = typeCombo.getSelectedItem() == SchemaNodeType.SCREEN;
        screenCombo.setEnabled(isScreen);
        presetCombo.setEnabled(!isScreen);
        boolean customText = isScreen || presetCombo.getSelectedItem() == null
                || OTHER_SENTINEL.equals(presetCombo.getSelectedItem());
        labelField.setEnabled(!isScreen && customText);
        saveAsPresetBtn.setEnabled(!isScreen && customText);
    }

    /** Пресеты библиотеки для выбранной категории узла + сентинел «свой текст» —
     *  библиотека предлагается первой, чтобы не вводить одно и то же оборудование
     *  (Barco E2, PixelHue Q8 и т.п.) повторно на каждой сцене. */
    private void refreshPresetCombo() {
        SchemaNodeType type = (SchemaNodeType) typeCombo.getSelectedItem();
        Object prevSelection = presetCombo.getSelectedItem();
        DefaultComboBoxModel<Object> m = new DefaultComboBoxModel<>();
        if (type != null && type != SchemaNodeType.SCREEN) {
            for (EquipmentPreset p : model.presetsForCategory(mode, type)) {
                m.addElement(p);
            }
        }
        m.addElement(OTHER_SENTINEL);
        presetCombo.setModel(m);
        if (prevSelection instanceof EquipmentPreset prevPreset && model.getEquipmentPresets().contains(prevPreset)
                && prevPreset.getCategory() == type) {
            presetCombo.setSelectedItem(prevPreset);
        }
    }

    private void addNode() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SchemaNodeType type = (SchemaNodeType) typeCombo.getSelectedItem();
        int count = model.schemaNodesForCurrentScene(mode).size();
        double x = 40 + (count % 6) * 170;
        double y = 40 + (count / 6) * 100;

        if (type == SchemaNodeType.SCREEN) {
            Screen sel = (Screen) screenCombo.getSelectedItem();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "На сцене нет экранов", "Нет экранов", JOptionPane.WARNING_MESSAGE);
                return;
            }
            model.addSchemaNode(mode, type, sel.getName(), x, y, sel.getId());
            return;
        }

        Object presetSel = presetCombo.getSelectedItem();
        if (presetSel instanceof EquipmentPreset preset) {
            if (!preset.getCards().isEmpty()) {
                // У пресета есть карты-шаблоны — даём собрать реальную конфигурацию
                // узла (сколько экземпляров каждой карты), а не копировать пресет
                // один-в-один как есть (одинаковых карт в устройстве может быть
                // несколько, см. Task #59).
                java.util.List<String> cardOrder = new AssembleCardsDialog(
                        SwingUtilities.getWindowAncestor(this), preset).showDialog();
                if (cardOrder == null) {
                    return;
                }
                model.addSchemaNodeFromPresetWithCardOrder(mode, preset, x, y, cardOrder);
            } else {
                model.addSchemaNodeFromPreset(mode, preset, x, y);
            }
            return;
        }

        String label = labelField.getText().trim();
        if (label.isEmpty()) {
            label = model.categoryLabel(type);
        }
        model.addSchemaNode(mode, type, label, x, y, null);
        labelField.setText("");
    }

    private void addPortLegendNode() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int count = model.schemaNodesForCurrentScene(mode).size();
        model.addSignalPortLegendNode(40 + (count % 6) * 170, 40 + (count / 6) * 100);
    }

    /** Сохраняет ТУ ЖЕ таблицу, что рисует авто-блок «Легенда портов» на холсте (см.
     *  {@link AppModel#signalPortLegendRows(Scene)}), отдельным PNG-файлом — не
     *  привязано к тому, добавлен ли сам блок на схему. По аналогии с «Экспорт
     *  схемы…» ({@link CurrentSchemeExporter#export}): та же стартовая папка и
     *  настройка качества (DPI), тот же диалог «готово/открыть папку», только PNG
     *  вместо JPEG ({@link CurrentSchemeExporter#exportPng}) — без потерь на резких
     *  границах текста/линий таблицы, которых на фотографичной схеме расключения не
     *  так заметно, а тут были бы. */
    private void exportPortLegend() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AppModel.SignalPortLegendRow> rows = model.signalPortLegendRows(scene);
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "На сцене нет расключённых экранов — нечего экспортировать",
                    "Легенда пуста", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CurrentSchemeExporter.exportPng(this, model, settings, scene.getName() + " — легенда портов",
                dpiScale -> SchemeRenderer.renderPortLegendImage(scene.getName(), rows, dpiScale));
    }

    /** Сохраняет введённую подпись как новый пресет библиотеки этой категории
     *  (карты ввода/вывода добавляются позже, в разделе «Библиотеки»). */
    private void saveAsPreset() {
        SchemaNodeType type = (SchemaNodeType) typeCombo.getSelectedItem();
        if (type == null || type == SchemaNodeType.SCREEN) {
            return;
        }
        String suggested = labelField.getText().trim();
        String name = JOptionPane.showInputDialog(this, "Название пресета:",
                suggested.isEmpty() ? model.categoryLabel(type) : suggested);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            model.addEquipmentPreset(mode, type, name.trim(), "", null);
            refreshPresetCombo();
            JOptionPane.showMessageDialog(this, "Пресет «" + name.trim() + "» сохранён в библиотеку.",
                    "Готово", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refresh() {
        Scene scene = model.getCurrentScene();
        DefaultComboBoxModel<Screen> screenModel = new DefaultComboBoxModel<>();
        if (scene != null) {
            for (Screen s : scene.getScreens()) {
                screenModel.addElement(s);
            }
        }
        screenCombo.setModel(screenModel);
        refreshPresetCombo();
        updateAddFormEnablement();

        int selectedCount = canvas.getSelectedNodes().size();
        if (selectedCount > 1) {
            selectionHint.setText("Выбрано узлов: " + selectedCount
                    + " (Shift/Ctrl+клик или протяжка по пустому месту — изменить выделение)");
        } else if (canvas.getSelectedNode() != null) {
            selectionHint.setText("Выбран узел: " + safeLabel(canvas.getSelectedNode().getLabel()));
        } else if (canvas.getSelectedEdge() != null) {
            selectionHint.setText("Выбрана связь");
        } else {
            selectionHint.setText("Ничего не выбрано");
        }

        canvas.revalidate();
        canvas.repaint();
    }

    private static String safeLabel(String s) {
        return s == null || s.isEmpty() ? "(без подписи)" : s;
    }
}
