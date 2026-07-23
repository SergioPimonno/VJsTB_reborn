package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.CanvasPanel;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.PortPickerPanel;
import com.vjstb.ledscheme.ui.SchemaPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * Этап «Сигнал»: холст + расключение по портам контроллера. ЛКМ по порту — начать
 * цепочку, ПКМ — переключить бэкап (без прорисовки кабинетов для резервного порта);
 * либо (по переключателю) общая yEd-подобная схема сигнала площадки.
 */
public class SignalStagePanel extends JPanel {

    private static final String VIEW_CHAIN = "chain";
    private static final String VIEW_SCHEMA = "schema";

    private final AppModel model;
    private final CanvasPanel canvas;
    private final ChainInteractionController chainCtrl;
    private final PortPickerPanel portPicker;
    private final CardLayout viewCards = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewCards);
    private final SchemaPanel schemaPanel;
    private final JToggleButton chainViewBtn = new JToggleButton("Расключение экрана", true);
    private final JToggleButton schemaViewBtn = new JToggleButton("Общая схема сигнала");

    private Integer activePort;

    private final JSpinner portCountSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 128, 1));
    private final JLabel hint = new JLabel(" ");
    private final JPanel chainListPanel = new JPanel();
    private javax.swing.JComponent portsSection;
    private javax.swing.JComponent chainsSection;

    private final JComboBox<ControllerType> controllerTypeCombo = new JComboBox<>();
    private final JPanel controllerListPanel = new JPanel();
    private javax.swing.JComponent controllersSection;
    private final JLabel portCountLabel = new JLabel(" ");

    private final JLabel statRes = new JLabel("—");
    private final JLabel statSize = new JLabel("—");
    private final JLabel statCount = new JLabel("—");
    private final JLabel statPower = new JLabel("—");
    private final JLabel statWeight = new JLabel("—");

    public SignalStagePanel(AppModel model) {
        this.model = model;
        this.chainCtrl = new ChainInteractionController(model, this::refresh);
        this.canvas = new CanvasPanel(model, chainCtrl);
        this.portPicker = new PortPickerPanel(model, new PortPickerPanel.PortListener() {
            @Override
            public void onPortSelected(int port) {
                Screen scr = model.getCurrentScreen();
                if (scr == null) return;
                activePort = port;
                chainCtrl.startFor(ids -> model.addSignalChain(port, false, ids));
            }

            @Override
            public void onPortBackupToggled(int port) {
                if (model.getCurrentScreen() == null) return;
                model.toggleSignalPortBackup(port);
            }

            @Override
            public void onPortBackupLinkRequested(int port) {
                Screen scr = model.getCurrentScreen();
                if (scr == null) return;
                SignalChain main = scr.signalChainByPort(port, false);
                Integer current = main != null ? main.getBackupPortNumber() : null;
                String input = JOptionPane.showInputDialog(SignalStagePanel.this,
                        "Номер резервного порта для порта " + port + " (пусто — снять):",
                        current != null ? String.valueOf(current) : "");
                if (input == null) return;
                input = input.trim();
                try {
                    Integer backupPort = input.isEmpty() ? null : Integer.valueOf(input);
                    int maxPort = model.effectiveSignalPortCount(scr);
                    if (backupPort != null && (backupPort < 1 || backupPort > maxPort)) {
                        JOptionPane.showMessageDialog(SignalStagePanel.this,
                                "Номер порта должен быть от 1 до " + maxPort,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    model.setSignalBackupPortLink(port, backupPort);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(SignalStagePanel.this, "Введите число порта",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(SignalStagePanel.this, ex.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel perScreen = new JPanel(new BorderLayout());
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        perScreen.add(canvasScroll, BorderLayout.CENTER);

        JScrollPane sideScroll = new JScrollPane(buildSide());
        sideScroll.setBorder(null);
        sideScroll.setPreferredSize(new Dimension(SIDE_WIDTH + 20, 100));
        perScreen.add(sideScroll, BorderLayout.EAST);

        schemaPanel = new SchemaPanel(model, SchemaMode.SIGNAL);
        schemaPanel.setOnScreenActivated(scr -> {
            model.selectScreen(scr);
            chainViewBtn.setSelected(true);
            viewCards.show(viewContainer, VIEW_CHAIN);
        });

        viewContainer.add(perScreen, VIEW_CHAIN);
        viewContainer.add(schemaPanel, VIEW_SCHEMA);

        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(chainViewBtn);
        viewGroup.add(schemaViewBtn);
        chainViewBtn.addActionListener(e -> viewCards.show(viewContainer, VIEW_CHAIN));
        schemaViewBtn.addActionListener(e -> viewCards.show(viewContainer, VIEW_SCHEMA));
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        toggleRow.add(chainViewBtn);
        toggleRow.add(schemaViewBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new ContextBar(model, true), BorderLayout.NORTH);
        top.add(toggleRow, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(viewContainer, BorderLayout.CENTER);

        model.addListener(this::refresh);
        refresh();
    }

    public ChainInteractionController chainController() {
        return chainCtrl;
    }

    public CanvasPanel canvas() {
        return canvas;
    }

    private static final int SIDE_WIDTH = 300;

    private JPanel buildSide() {
        JPanel body = UiKit.vboxFixedWidth(SIDE_WIDTH);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        controllerListPanel.setLayout(new BoxLayout(controllerListPanel, BoxLayout.Y_AXIS));
        JPanel controllersBody = UiKit.vbox();
        controllersBody.add(controllerListPanel);
        JPanel addRow = new JPanel(new BorderLayout(4, 0));
        addRow.setAlignmentX(LEFT_ALIGNMENT);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        controllerTypeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ControllerType ct) {
                    setText(ct.getName() + " (" + ct.getPortCount() + " п.)");
                }
                return this;
            }
        });
        JButton addCtrl = new JButton("+ добавить");
        addCtrl.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            ControllerType sel = (ControllerType) controllerTypeCombo.getSelectedItem();
            if (scr != null && sel != null) {
                model.addControllerToScreen(scr, sel.getId());
            }
        });
        addRow.add(controllerTypeCombo, BorderLayout.CENTER);
        addRow.add(addCtrl, BorderLayout.EAST);
        controllersBody.add(UiKit.vgap());
        controllersBody.add(addRow);
        controllersBody.add(UiKit.vgap());
        controllersBody.add(UiKit.muted(
                "<html>Если назначен хотя бы один контроллер — доступные порты сигнала считаются"
                        + " по сумме их портов; ручное число портов ниже игнорируется.</html>"));
        controllersSection = UiKit.dynamicSection("Контроллеры экрана (библиотека — в Сетапе)", controllersBody);
        body.add(controllersSection);
        body.add(UiKit.vgap());

        JPanel countRow = new JPanel(new GridLayout(1, 2, 4, 0));
        countRow.add(new JLabel("Портов (вручную):"));
        countRow.add(portCountSpinner);
        countRow.setAlignmentX(LEFT_ALIGNMENT);
        // Как и секции: GridLayout/BorderLayout по умолчанию не ограничены по высоте —
        // без явного предела эта строка раздувается на всё свободное место в BoxLayout.
        countRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, countRow.getPreferredSize().height));
        portCountSpinner.addChangeListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr != null && (Integer) portCountSpinner.getValue() != scr.getSignalPortCount()) {
                model.updateSignalPortCount(scr, (Integer) portCountSpinner.getValue());
            }
        });
        body.add(countRow);
        portCountLabel.setForeground(Palette.MUTED);
        body.add(portCountLabel);
        body.add(UiKit.vgap());
        // portPicker наполняется кнопками в rebuild(), уже после первой сборки — динамическая секция.
        portsSection = UiKit.dynamicSection(
                "Порты контроллера (ЛКМ — цепочка, ПКМ — бэкап-цепочка, 2×ЛКМ — резервный порт)", portPicker);
        body.add(portsSection);

        hint.setForeground(Palette.MUTED);
        hint.setText("<html>Клик, зажатая ЛКМ или стрелки — добавить кабинеты.<br>Esc — завершить цепочку.</html>");
        body.add(UiKit.vgap());
        body.add(hint);

        JButton clear = new JButton("Очистить цепочки сигнала");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Очистить все цепочки сигнала на экране?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                model.clearChainsOfMode();
            }
        });
        body.add(UiKit.vgap());
        body.add(clear);

        chainListPanel.setLayout(new BoxLayout(chainListPanel, BoxLayout.Y_AXIS));
        body.add(UiKit.vgap());
        chainsSection = UiKit.dynamicSection("Существующие цепочки", chainListPanel);
        body.add(chainsSection);

        JPanel statsBody = UiKit.vbox();
        statsBody.add(statRow("Разрешение", statRes));
        statsBody.add(statRow("Физический размер", statSize));
        statsBody.add(statRow("Кабинетов", statCount));
        statsBody.add(statRow("Мощность", statPower));
        statsBody.add(statRow("Вес", statWeight));
        body.add(UiKit.vgap());
        body.add(UiKit.section("Статистика экрана", statsBody));
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void refresh() {
        if (!chainCtrl.isChainBuilding()) {
            activePort = null;
        }
        Screen scr = model.getCurrentScreen();
        boolean has = scr != null;

        DefaultComboBoxModel<ControllerType> ctrlModel = new DefaultComboBoxModel<>();
        for (ControllerType ct : model.getWorkspace().getControllerTypes()) {
            ctrlModel.addElement(ct);
        }
        controllerTypeCombo.setModel(ctrlModel);

        controllerListPanel.removeAll();
        if (has) {
            if (scr.getControllers().isEmpty()) {
                controllerListPanel.add(UiKit.muted("Контроллеры не назначены"));
            }
            for (ControllerInstance ci : scr.getControllers()) {
                ControllerType t = model.getWorkspace().controllerTypeById(ci.getControllerTypeId());
                String label = ci.getLabel() + " — " + (t != null ? t.getName() + " (" + t.getPortCount() + " п.)" : "?");
                controllerListPanel.add(chainRow(label, Palette.ACCENT,
                        () -> model.removeControllerFromScreen(scr, ci.getId())));
            }
        }
        UiKit.recapHeight(controllersSection);

        boolean hasControllers = has && !scr.getControllers().isEmpty();
        portCountSpinner.setEnabled(has && !hasControllers);
        if (has) {
            portCountSpinner.setValue(scr.getSignalPortCount());
            portCountLabel.setText(hasControllers
                    ? "Действующее число портов: " + model.effectiveSignalPortCount(scr) + " (по контроллерам)"
                    : " ");
        } else {
            portCountLabel.setText(" ");
        }
        portPicker.rebuild(activePort);
        UiKit.recapHeight(portsSection);

        chainListPanel.removeAll();
        if (has) {
            var chains = scr.getSignalChains();
            if (chains.isEmpty()) {
                chainListPanel.add(UiKit.muted("Цепочек ещё нет"));
            }
            for (int i = 0; i < chains.size(); i++) {
                SignalChain c = chains.get(i);
                String label = (c.getPortNumber() != null ? "Порт " + c.getPortNumber() : "Без порта")
                        + (c.isBackup() ? " (бэкап)" : "") + " · " + c.getCabinetInstanceIds().size() + " каб."
                        + (c.getBackupPortNumber() != null ? " · резерв: порт " + c.getBackupPortNumber() : "");
                chainListPanel.add(chainRow(label, Palette.signalColor(i), () -> model.deleteSignalChain(c.getId())));
            }
        }
        UiKit.recapHeight(chainsSection);
        chainListPanel.revalidate();
        chainListPanel.repaint();

        if (has) {
            ScreenStats s = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            statRes.setText(s.resolutionWidthPx() + " × " + s.resolutionHeightPx() + " px");
            statSize.setText(UiKit.fmt(s.physicalWidthMm()) + " × " + UiKit.fmt(s.physicalHeightMm()) + " мм");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
        } else {
            statRes.setText("—");
            statSize.setText("—");
            statCount.setText("—");
            statPower.setText("—");
            statWeight.setText("—");
        }

        canvas.revalidate();
        canvas.repaint();
    }

    private javax.swing.JComponent chainRow(String label, java.awt.Color dot, Runnable onDelete) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(dot);
        JLabel text = new JLabel(label);
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> onDelete.run());
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(del, BorderLayout.EAST);
        return row;
    }

    private JPanel statRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title + ": ");
        t.setForeground(Palette.MUTED);
        row.add(t, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }
}
