package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
import javax.swing.JTextField;
import javax.swing.JToggleButton;

/**
 * Общая схема площадки (yEd-подобная): холст с узлами оборудования и связями
 * + панель добавления узлов. Показывает либо схему питания, либо сигнала —
 * определяется полем mode; узлы двух схем хранятся раздельно в той же сцене.
 */
public class SchemaPanel extends JPanel {

    private static final int SIDE_WIDTH = 260;

    private final AppModel model;
    private final SchemaMode mode;
    private final SchemaCanvasPanel canvas;

    private final JComboBox<SchemaNodeType> typeCombo = new JComboBox<>(SchemaNodeType.values());
    private final JComboBox<Screen> screenCombo = new JComboBox<>();
    private final JTextField labelField = new JTextField();
    private final JToggleButton moveBtn = new JToggleButton("Перемещение", true);
    private final JToggleButton connectBtn = new JToggleButton("Соединение");
    private final JLabel selectionHint = new JLabel(" ");

    public SchemaPanel(AppModel model, SchemaMode mode) {
        this.model = model;
        this.mode = mode;
        this.canvas = new SchemaCanvasPanel(model, mode);
        canvas.setOnChanged(this::refresh);

        setLayout(new BorderLayout());
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        add(canvasScroll, BorderLayout.CENTER);
        add(buildSide(), BorderLayout.EAST);

        model.addListener(this::refresh);
        refresh();
    }

    public void setOnScreenActivated(Consumer<Screen> listener) {
        canvas.setOnScreenActivated(listener);
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
                    setText(t.getLabel());
                }
                return this;
            }
        });
        typeCombo.addActionListener(e -> updateAddFormEnablement());

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
        addBody.add(new JLabel("Подпись (для прочих типов)"));
        labelField.putClientProperty("JTextField.placeholderText", "например, «Щит А1»…");
        addBody.add(labelField);
        addBody.add(UiKit.vgap());
        JButton addBtn = new JButton("+ Добавить узел");
        addBtn.addActionListener(e -> addNode());
        addBody.add(addBtn);
        body.add(UiKit.dynamicSection("Добавить узел", addBody));
        body.add(UiKit.vgap());

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
        body.add(UiKit.muted("<html>Перемещение — тащите узел мышью.<br>"
                + "Соединение — клик по первому узлу, потом по второму.<br>"
                + "ПКМ по узлу/связи — переименовать, подписать, удалить.<br>"
                + "2×клик по узлу «Экран» — открыть его цепочки.</html>"));
        body.add(UiKit.vgap());

        selectionHint.setForeground(Palette.MUTED);
        body.add(selectionHint);
        JButton delSelected = new JButton("Удалить выбранное");
        delSelected.addActionListener(e -> canvas.deleteSelected());
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
        labelField.setEnabled(!isScreen);
    }

    private void addNode() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SchemaNodeType type = (SchemaNodeType) typeCombo.getSelectedItem();
        String screenRefId = null;
        String label;
        if (type == SchemaNodeType.SCREEN) {
            Screen sel = (Screen) screenCombo.getSelectedItem();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "На сцене нет экранов", "Нет экранов", JOptionPane.WARNING_MESSAGE);
                return;
            }
            screenRefId = sel.getId();
            label = sel.getName();
        } else {
            label = labelField.getText().trim();
            if (label.isEmpty()) {
                label = type.getLabel();
            }
        }
        int count = model.schemaNodesForCurrentScene(mode).size();
        double x = 40 + (count % 6) * 170;
        double y = 40 + (count / 6) * 100;
        model.addSchemaNode(mode, type, label, x, y, screenRefId);
        labelField.setText("");
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
        updateAddFormEnablement();

        if (canvas.getSelectedNode() != null) {
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
