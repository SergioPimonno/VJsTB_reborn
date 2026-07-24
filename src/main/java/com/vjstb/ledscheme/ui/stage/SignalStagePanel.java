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
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
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
    private final JCheckBox showAllScreens = new JCheckBox("Показать все экраны сцены");
    private final SceneCanvasPanel sceneOverview;
    private final JScrollPane canvasScroll;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final SceneCanvasPanel cornerPreview;
    private final JPanel cornerPreviewHost;

    private static final int CORNER_W = 260;
    private static final int CORNER_H = 170;
    private static final int CORNER_MARGIN = 10;

    private Integer activePort;
    /** id контроллера, помеченного ПКМ как «в резерв» — следующий ЛКМ по ДРУГОМУ
     *  контроллеру в списке создаёт связку main→backup между ними (весь контроллер
     *  целиком подхватывает сигнал другого, см. AppModel.setControllerBackupLink). */
    private String pendingBackupControllerId;
    /** id контроллера, чьи порты сейчас показаны в сетке (ЛКМ по строке контроллера
     *  выбирает его) — расключение всегда идёт по ОДНОМУ конкретному контроллеру,
     *  локальными номерами портов, а не сквозной суммой по всем контроллерам сцены
     *  (см. Task #73). null — контроллеров в сцене нет вовсе (старый ручной режим). */
    private String selectedControllerId;

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

    public SignalStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.chainCtrl = new ChainInteractionController(model, this::refresh);
        // Клик по непрописанному кабинету сам начинает цепочку для ТЕКУЩЕГО
        // выбранного порта — кнопка порта (или цифровой хоткей) только выбирает
        // цель, не запуская построение сама по себе (см. selectPort ниже).
        this.chainCtrl.setStarter(cabId -> {
            Screen scr = model.getCurrentScreen();
            if (scr == null || activePort == null) {
                return null;
            }
            if (model.isPortReservedAsBackup(scr, activePort)) {
                return null;
            }
            // Проверяем по ВСЕЙ сцене, а не только этому экрану — кабинет мог быть
            // занят цепочкой, которая физически продолжается сюда с другого экрана.
            if (model.isCabinetWiredForSignal(cabId)) {
                return null;
            }
            int port = activePort;
            return ids -> model.addSignalChain(port, false, ids);
        });
        this.chainCtrl.setOnCommitError(msg ->
                JOptionPane.showMessageDialog(this, msg, "Не удалось завершить цепочку",
                        JOptionPane.ERROR_MESSAGE));
        this.canvas = new CanvasPanel(model, chainCtrl);
        this.portPicker = new PortPickerPanel(model, new PortPickerPanel.PortListener() {
            @Override
            public void onPortSelected(int port) {
                selectPort(port);
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

        this.sceneOverview = new SceneCanvasPanel(model);
        // "Показать все экраны сцены" был только для просмотра — весь смысл режима
        // (видеть всю сцену и осознанно распределять нагрузку) требует уметь
        // прописывать активный экран прямо отсюда, не переключаясь на одиночный вид.
        this.sceneOverview.setChainController(chainCtrl);

        canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        showAllScreens.addActionListener(e -> {
            boolean all = showAllScreens.isSelected();
            sceneOverview.setDetailMode(all, false);
            canvasScroll.setViewportView(all ? sceneOverview : canvas);
            updateCornerPreviewVisibility();
        });

        // Корнер-виджет (всегда видимый мини-обзор сцены в правом нижнем углу холста,
        // выключаемый в «Персонализации») — накладывается поверх canvasScroll через
        // JLayeredPane, а не встраивается в раскладку, чтобы не отнимать место у холста.
        cornerPreview = new SceneCanvasPanel(model);
        cornerPreview.setDetailMode(true, false, true);
        cornerPreviewHost = new JPanel(new BorderLayout());
        cornerPreviewHost.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        cornerPreviewHost.add(cornerPreview, BorderLayout.CENTER);
        cornerPreviewHost.setBounds(0, 0, CORNER_W, CORNER_H);

        JLayeredPane canvasLayered = new JLayeredPane();
        canvasLayered.setLayout(null);
        canvasLayered.add(canvasScroll, JLayeredPane.DEFAULT_LAYER);
        canvasLayered.add(cornerPreviewHost, JLayeredPane.PALETTE_LAYER);
        canvasLayered.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                canvasScroll.setBounds(0, 0, canvasLayered.getWidth(), canvasLayered.getHeight());
                cornerPreviewHost.setBounds(canvasLayered.getWidth() - CORNER_W - CORNER_MARGIN,
                        canvasLayered.getHeight() - CORNER_H - CORNER_MARGIN, CORNER_W, CORNER_H);
            }
        });
        settings.addListener(this::updateCornerPreviewVisibility);

        JScrollPane sideScroll = new JScrollPane(buildSide());
        sideScroll.setBorder(null);
        sideScroll.setMinimumSize(new Dimension(180, 100));

        JSplitPane perScreen = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasLayered, sideScroll);
        perScreen.setContinuousLayout(true);
        perScreen.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "signal.chainSplit", perScreen, 1.0 - (SIDE_WIDTH + 20) / 1360.0);

        schemaPanel = new SchemaPanel(model, SchemaMode.SIGNAL, settings);
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
        toggleRow.add(showAllScreens);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new ContextBar(model, true), BorderLayout.NORTH);
        top.add(toggleRow, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(viewContainer, BorderLayout.CENTER);

        model.addListener(this::refresh);
        refresh();
        updateCornerPreviewVisibility();
    }

    public ChainInteractionController chainController() {
        return chainCtrl;
    }

    public CanvasPanel canvas() {
        return canvas;
    }

    /** Контроллер, чьи порты сейчас показаны — выбранный явно (ЛКМ по строке), или
     *  первый в сцене по умолчанию, если выбор ещё не сделан/устарел (контроллер
     *  удалён). null — контроллеров в сцене нет вовсе. */
    private ControllerInstance selectedController(Screen scr) {
        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        if (sceneControllers.isEmpty()) {
            return null;
        }
        if (selectedControllerId != null) {
            for (ControllerInstance ci : sceneControllers) {
                if (ci.getId().equals(selectedControllerId)) {
                    return ci;
                }
            }
        }
        return sceneControllers.get(0);
    }

    /** Выбирает порт как цель для СЛЕДУЮЩЕЙ цепочки — сама цепочка теперь
     *  начинается по клику на непрописанный кабинет (см. ChainInteractionController
     *  .setStarter в конструкторе), а не здесь. */
    private void selectPort(int port) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        if (model.isPortReservedAsBackup(scr, port)) {
            JOptionPane.showMessageDialog(this,
                    "Порт " + port + " назначен резервным для другого порта — он подхватывает сигнал"
                            + " автоматически и не может иметь собственную ручную цепочку.",
                    "Порт зарезервирован под бэкап", JOptionPane.WARNING_MESSAGE);
            return;
        }
        activePort = port;
        // Завершает (сохраняет) то, что строилось для ПРЕЖНЕГО порта.
        chainCtrl.finish();
        refresh();
    }

    /** Хоткей 1-9/0 (глобальный обработчик в MainFrame) — то же самое, что клик по
     *  кнопке порта в сетке портов контроллера. Цифра — ЛОКАЛЬНЫЙ номер порта
     *  показанного (выбранного) контроллера, а не сквозной по сцене (см. Task #73) —
     *  переводим её в глобальный номер тем же смещением, что и сама сетка портов. */
    public void selectPortByHotkey(int localPort) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        ControllerInstance selected = selectedController(scr);
        int offset;
        int count;
        if (selected != null) {
            offset = model.portOffsetOf(scr, selected);
            com.vjstb.ledscheme.model.ControllerType t =
                    model.getWorkspace().controllerTypeById(selected.getControllerTypeId());
            count = t != null ? t.effectivePortCount() : 0;
        } else {
            offset = 0;
            count = model.effectiveSignalPortCount(scr);
        }
        if (localPort < 1 || localPort > count) {
            return;
        }
        selectPort(offset + localPort);
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
                    setText(ct.getName() + " (" + ct.effectivePortCount() + " п.)");
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
        JButton editCtrl = new JButton("Редактировать…");
        editCtrl.setToolTipText("Открыть выбранный тип для правки — исходный образец в библиотеке"
                + " не меняется, изменения сохраняются как НОВЫЙ тип");
        editCtrl.addActionListener(e -> {
            ControllerType sel = (ControllerType) controllerTypeCombo.getSelectedItem();
            if (sel == null) {
                return;
            }
            ControllerType edited = new com.vjstb.ledscheme.ui.ControllerTypeDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this), sel).showDialog();
            if (edited != null) {
                // Существующий образец в библиотеке остаётся нетронутым — правки
                // создают НОВУЮ запись (см. Task #58), поэтому не переиспользуем id.
                edited.setId(java.util.UUID.randomUUID().toString());
                try {
                    model.addControllerType(edited);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        addRow.add(controllerTypeCombo, BorderLayout.CENTER);
        JPanel addButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButtons.add(addCtrl);
        addButtons.add(editCtrl);
        addRow.add(addButtons, BorderLayout.EAST);
        controllersBody.add(UiKit.vgap());
        controllersBody.add(addRow);
        controllersBody.add(UiKit.vgap());
        controllersBody.add(UiKit.muted(
                "<html>Контроллеры общие для всей сцены — их видят все экраны сцены. ЛКМ по контроллеру"
                        + " ниже — показать и расключать ЕГО порты (локальными номерами); сетка справа"
                        + " всегда показывает порты только одного выбранного контроллера.</html>"));
        controllersSection = UiKit.dynamicSection("Контроллеры сцены", controllersBody);
        body.add(controllersSection);
        body.add(UiKit.vgap());

        portCountLabel.setForeground(Palette.MUTED);
        body.add(portCountLabel);
        body.add(UiKit.vgap());
        // portPicker наполняется кнопками в rebuild(), уже после первой сборки — динамическая секция.
        portsSection = UiKit.dynamicSection(
                "Порты контроллера (ЛКМ/хоткей 1-9,0 — цель, ПКМ — бэкап-цепочка, 2×ЛКМ — резервный порт)",
                portPicker);
        body.add(portsSection);

        hint.setForeground(Palette.MUTED);
        hint.setText("<html>Клик по непрописанному кабинету — начать цепочку для выбранного порта.<br>"
                + "Клик, зажатая ЛКМ или стрелки — добавить ещё кабинеты.<br>"
                + "ПКМ по кабинету во время построения — убрать его из цепочки.<br>"
                + "Esc — завершить и сохранить цепочку.</html>");
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
        Screen scr = model.getCurrentScreen();
        boolean has = scr != null;

        DefaultComboBoxModel<ControllerType> ctrlModel = new DefaultComboBoxModel<>();
        for (ControllerType ct : model.getWorkspace().getControllerTypes()) {
            ctrlModel.addElement(ct);
        }
        controllerTypeCombo.setModel(ctrlModel);

        controllerListPanel.removeAll();
        // Контроллеры общие для ВСЕЙ сцены (см. Task #58) — экраны одной сцены
        // должны видеть все добавленные в сцену контроллеры, а не только те,
        // что физически хранятся под текущим экраном.
        List<ControllerInstance> sceneControllers = has
                ? model.controllersInScene(model.getCurrentScene()) : List.of();
        if (has) {
            if (sceneControllers.isEmpty()) {
                controllerListPanel.add(UiKit.muted("Контроллеры не назначены"));
            }
            for (ControllerInstance ci : sceneControllers) {
                ControllerType t = model.getWorkspace().controllerTypeById(ci.getControllerTypeId());
                String label;
                if (t != null) {
                    int px = com.vjstb.ledscheme.model.ControllerType.maxPixelsFor(
                            t.getPortBandwidthMbps(), scr.getRefreshRateHz(), scr.getColorBitDepth());
                    label = ci.getLabel() + " — " + t.getName() + " (" + t.effectivePortCount() + " п. · до " + px
                            + " px/порт @" + scr.getRefreshRateHz() + "Гц/" + scr.getColorBitDepth() + "бит)";
                } else {
                    label = ci.getLabel() + " — ?";
                }
                controllerListPanel.add(controllerRow(scr, ci, label));
            }
        }
        UiKit.recapHeight(controllersSection);

        ControllerInstance selected = has ? selectedController(scr) : null;
        if (has && selected != null) {
            // Порты показанной сетки — ТОЛЬКО выбранного контроллера, локальными
            // номерами, а не сквозная сумма по всем контроллерам сцены (Task #73).
            ControllerType t = model.getWorkspace().controllerTypeById(selected.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            portCountLabel.setText("Портов: " + count + " (контроллер «" + selected.getLabel() + "»)");
        } else if (has) {
            portCountLabel.setText("Контроллеры не назначены — добавьте хотя бы один, чтобы получить порты сигнала");
        } else {
            portCountLabel.setText(" ");
        }
        portPicker.rebuild(activePort, selected);
        UiKit.recapHeight(portsSection);

        chainListPanel.removeAll();
        if (has) {
            // По всей сцене (не только scr.getSignalChains()) — та же причина, что
            // и для питания (Task #64/#68): цепочка хранится на ОДНОМ экране, а
            // начаться могла на этом.
            var chains = model.signalChainsTouchingScreen(scr);
            if (chains.isEmpty()) {
                chainListPanel.add(UiKit.muted("Цепочек ещё нет"));
            }
            for (int i = 0; i < chains.size(); i++) {
                SignalChain c = chains.get(i);
                boolean crossScreen = c.getCabinetInstanceIds().stream().anyMatch(id -> scr.cabinetById(id) == null);
                String label = (c.getPortNumber() != null ? "Порт " + c.getPortNumber() : "Без порта")
                        + (c.isBackup() ? " (бэкап)" : "") + " · " + c.getCabinetInstanceIds().size() + " каб."
                        + (c.getBackupPortNumber() != null ? " · резерв: порт " + c.getBackupPortNumber() : "")
                        + (crossScreen ? " (продолжается на другом экране)" : "");
                chainListPanel.add(chainRow(label, Palette.signalColor(i), () -> model.deleteSignalChain(c.getId())));
            }
        }
        UiKit.recapHeight(chainsSection);
        chainListPanel.revalidate();
        chainListPanel.repaint();

        if (has && showAllScreens.isSelected() && model.getCurrentScene() != null) {
            // Показана вся сцена — статистика одного активного экрана не даёт полной
            // картины, показываем сумму по всем экранам сцены (Task #71).
            java.util.List<Screen> screens = model.getCurrentScene().getScreens();
            ScreenStats s = ScreenLogic.aggregateStats(screens, model::typeOf, model.getWorkspace());
            statRes.setText(screens.size() + " экран(ов)");
            statSize.setText("—");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
        } else if (has) {
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
        sceneOverview.revalidate();
        sceneOverview.repaint();
        cornerPreview.repaint();
    }

    private void updateCornerPreviewVisibility() {
        cornerPreviewHost.setVisible(settings.activeProfile().isPreviewWidgetEnabled() && !showAllScreens.isSelected());
    }

    /** Строка контроллера: ПКМ помечает его «в резерв» (ждём цель), ЛКМ по ДРУГОМУ
     *  контроллеру, пока метка ждёт, создаёт связку основной→резерв между ними —
     *  весь контроллер целиком дублирует порты другого (см. AppModel.setControllerBackupLink),
     *  в отличие от резерва отдельного порта (2×клик по кнопке порта). */
    private javax.swing.JComponent controllerRow(Screen scr, ControllerInstance ci, String baseLabel) {
        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        String suffix = "";
        if (ci.getBackupControllerId() != null) {
            ControllerInstance backup = findController(sceneControllers, ci.getBackupControllerId());
            suffix = " → резерв: " + (backup != null ? backup.getLabel() : "?");
        } else {
            for (ControllerInstance other : sceneControllers) {
                if (ci.getId().equals(other.getBackupControllerId())) {
                    suffix = " (резерв для " + other.getLabel() + ")";
                    break;
                }
            }
        }
        boolean pending = ci.getId().equals(pendingBackupControllerId);
        ControllerInstance currentSelection = selectedController(scr);
        boolean selected = currentSelection != null && ci.getId().equals(currentSelection.getId());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        // Рамка — резерв (ПКМ, ждём цель); заливка — выбран для показа/расключения
        // портов в сетке справа (ЛКМ, см. Task #73). Оба состояния различимы одновременно.
        row.setBorder(pending ? BorderFactory.createLineBorder(Color.ORANGE, 2) : null);
        row.setBackground(selected ? Palette.ACCENT.darker() : getBackground());
        row.setOpaque(selected);
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(suffix.isEmpty() ? Palette.ACCENT : Color.ORANGE);
        JLabel text = new JLabel(baseLabel + suffix);
        if (selected) {
            text.setForeground(Palette.TEXT);
        }
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> model.removeControllerFromScene(model.getCurrentScene(), ci.getId()));
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(del, BorderLayout.EAST);
        row.setToolTipText("ЛКМ — показать/расключать порты этого контроллера в сетке справа"
                + " · ПКМ — пометить как резервный для другого контроллера");

        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    pendingBackupControllerId = pending ? null : ci.getId();
                    refresh();
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && pendingBackupControllerId != null) {
                    String backupId = pendingBackupControllerId;
                    pendingBackupControllerId = null;
                    if (!backupId.equals(ci.getId())) {
                        try {
                            model.setControllerBackupLink(scr, ci.getId(), backupId);
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(SignalStagePanel.this, ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                    refresh();
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    // Обычный ЛКМ (не в режиме назначения резерва) — выбрать этот
                    // контроллер для сетки портов (Task #73).
                    selectedControllerId = ci.getId();
                    refresh();
                }
            }
        });
        return row;
    }

    private static ControllerInstance findController(List<ControllerInstance> controllers, String id) {
        for (ControllerInstance ci : controllers) {
            if (ci.getId().equals(id)) {
                return ci;
            }
        }
        return null;
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
