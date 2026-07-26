package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.CanvasPanel;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import com.vjstb.ledscheme.ui.ChainPatterns;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.RadialMenu;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;

/** Этап «Питание»: холст + построение цепочек расключения питания по фазам L1/L2/L3,
 *  либо (по переключателю) общая yEd-подобная схема питания площадки. */
public class PowerStagePanel extends JPanel {

    private static final String VIEW_CHAIN = "chain";
    private static final String VIEW_SCHEMA = "schema";

    private final AppModel model;
    private final CanvasPanel canvas;
    private final ChainInteractionController chainCtrl;
    private final CardLayout viewCards = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewCards);
    private final SchemaPanel schemaPanel;
    private final JToggleButton chainViewBtn = new JToggleButton("Расключение экрана", true);
    private final JToggleButton schemaViewBtn = new JToggleButton("Общая схема питания");
    private final JCheckBox showAllScreens = new JCheckBox("Показать все экраны сцены");
    private final JToggleButton quickConnectBtn = new JToggleButton("⚡ Быстрое подключение");
    private final SceneCanvasPanel sceneOverview;
    private final JScrollPane canvasScroll;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final SceneCanvasPanel cornerPreview;
    private final JPanel cornerPreviewHost;

    private static final int CORNER_W = 260;
    private static final int CORNER_H = 170;
    private static final int CORNER_MARGIN = 10;

    private final JToggleButton phase1 = new JToggleButton("L1");
    private final JToggleButton phase2 = new JToggleButton("L2");
    private final JToggleButton phase3 = new JToggleButton("L3");
    private final JPanel chainListPanel = new JPanel();
    private javax.swing.JComponent chainsSection;
    private javax.swing.JComponent statsSection;

    private final JLabel statCabinetType = new JLabel("—");
    private final JLabel statRes = new JLabel("—");
    private final JLabel statSize = new JLabel("—");
    private final JLabel statCount = new JLabel("—");
    private final JLabel statPower = new JLabel("—");
    private final JLabel statWeight = new JLabel("—");
    private final JLabel statPhases = new JLabel();

    public PowerStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.chainCtrl = new ChainInteractionController(model, this::refresh);
        // Клик по непрописанному кабинету сам начинает цепочку для ТЕКУЩЕЙ выбранной
        // фазы — кнопка фазы (или хоткей 1/2/3) только выбирает цель, не запускает
        // построение сама по себе (см. selectPhase ниже).
        this.chainCtrl.setStarter(cabId -> {
            Screen scr = model.getCurrentScreen();
            if (scr == null) {
                return null;
            }
            // По ВСЕЙ сцене, а не только этому экрану — силовая цепочка тоже может
            // физически продолжаться с одного экрана на другой (см. Task #64).
            if (model.isCabinetWiredForPower(cabId)) {
                return null;
            }
            int phase = model.getActivePhase();
            return ids -> model.addPowerChain(phase, ids);
        });
        this.chainCtrl.setOnCommitError(msg ->
                JOptionPane.showMessageDialog(this, msg, "Не удалось завершить цепочку",
                        JOptionPane.ERROR_MESSAGE));
        this.canvas = new CanvasPanel(model, chainCtrl);
        // «Быстрое подключение» (как в NovaLCT) — протяжка выделяет прямоугольную
        // область кабинетов, радиальное меню выбирает шаблон серпантина, скрытые и
        // уже занятые кабинеты области просто выпадают из последовательности.
        this.canvas.setQuickConnectListener((rowStart, rowEnd, colStart, colEnd, screenX, screenY) ->
                showQuickConnectMenu(rowStart, rowEnd, colStart, colEnd, screenX, screenY));
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
            sceneOverview.setDetailMode(all, true);
            canvasScroll.setViewportView(all ? sceneOverview : canvas);
            updateCornerPreviewVisibility();
        });

        // Корнер-виджет (всегда видимый мини-обзор сцены в правом нижнем углу холста,
        // выключаемый в «Персонализации») — накладывается поверх canvasScroll через
        // JLayeredPane, а не встраивается в раскладку, чтобы не отнимать место у холста.
        cornerPreview = new SceneCanvasPanel(model);
        cornerPreview.setDetailMode(true, true, true);
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
        UiKit.persistentDivider(settings, "power.chainSplit", perScreen, 1.0 - (SIDE_WIDTH + 20) / 1360.0);

        schemaPanel = new SchemaPanel(model, SchemaMode.POWER, settings);
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
        quickConnectBtn.setToolTipText("Протяжка ЛКМ по холсту выделяет область — радиальное меню предложит"
                + " шаблон серпантина для быстрой прописки (как в NovaLCT)");
        quickConnectBtn.addActionListener(e -> canvas.setQuickConnectMode(quickConnectBtn.isSelected()));
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        toggleRow.add(chainViewBtn);
        toggleRow.add(schemaViewBtn);
        toggleRow.add(showAllScreens);
        toggleRow.add(quickConnectBtn);

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

    /** Радиальное меню из 8 шаблонов серпантина (NovaLCT-style «Быстрая прописка») —
     *  выбор шаблона строит и сразу сохраняет цепочку для ТЕКУЩЕЙ выбранной фазы
     *  по всем не скрытым и ещё не занятым кабинетам выделенной области. */
    private void showQuickConnectMenu(int rowStart, int rowEnd, int colStart, int colEnd, int screenX, int screenY) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        java.util.List<RadialMenu.Item> items = new java.util.ArrayList<>();
        for (ChainPatterns.Pattern pattern : ChainPatterns.Pattern.values()) {
            items.add(RadialMenu.Item.leaf(pattern.getLabel(), new com.vjstb.ledscheme.ui.ChainPatternIcon(pattern), () -> {
                java.util.List<String> ids = ChainPatterns.orderedIds(scr, rowStart, rowEnd, colStart, colEnd,
                        pattern, cabId -> !model.isCabinetWiredForPower(cabId));
                String error = chainCtrl.buildAndCommit(ids);
                if (error != null) {
                    JOptionPane.showMessageDialog(this, error, "Быстрое подключение", JOptionPane.ERROR_MESSAGE);
                }
            }));
        }
        RadialMenu.show(canvas, screenX, screenY, items);
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

        JPanel phaseRow = new JPanel(new GridLayout(1, 3, 4, 0));
        ButtonGroup g = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{phase1, phase2, phase3}) {
            g.add(b);
            phaseRow.add(b);
        }
        phase1.addActionListener(e -> selectPhase(1));
        phase2.addActionListener(e -> selectPhase(2));
        phase3.addActionListener(e -> selectPhase(3));
        body.add(UiKit.section("Фаза", phaseRow));
        body.add(UiKit.vgap());

        JButton clear = new JButton("Очистить цепочки питания");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Очистить все цепочки питания на экране?",
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
        statsBody.add(statRow("Тип кабинета", statCabinetType));
        statsBody.add(statRow("Разрешение", statRes));
        statsBody.add(statRow("Физический размер", statSize));
        statsBody.add(statRow("Кабинетов", statCount));
        statsBody.add(statRow("Мощность", statPower));
        statsBody.add(statRow("Вес", statWeight));
        statsBody.add(UiKit.vgap());
        statPhases.setForeground(Palette.MUTED);
        statsBody.add(statPhases);
        body.add(UiKit.vgap());
        // statPhases изначально пустой (текст на 3 строки появляется только в refresh()) —
        // поэтому тоже «динамическая» секция, пересчитываем высоту после заполнения.
        statsSection = UiKit.dynamicSection("Статистика экрана", statsBody);
        body.add(statsSection);
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void selectPhase(int phase) {
        model.setActivePhase(phase);
        // Завершает (сохраняет) то, что строилось для ПРЕЖНЕЙ фазы — новая цепочка
        // теперь начинается не здесь, а по клику на непрописанный кабинет (см.
        // ChainInteractionController.setStarter выше).
        chainCtrl.finish();
    }

    /** Хоткей 1/2/3 (глобальный обработчик в MainFrame) — то же самое, что клик по
     *  кнопке фазы, плюс визуально выделяет соответствующую кнопку. */
    public void selectPhaseByHotkey(int phase) {
        JToggleButton b = switch (phase) {
            case 1 -> phase1;
            case 2 -> phase2;
            case 3 -> phase3;
            default -> null;
        };
        if (b != null) {
            b.setSelected(true);
            selectPhase(phase);
        }
    }

    private void refresh() {
        Screen scr = model.getCurrentScreen();
        boolean has = scr != null;
        phase1.setEnabled(has);
        phase2.setEnabled(has);
        phase3.setEnabled(has);
        switch (model.getActivePhase()) {
            case 1 -> phase1.setSelected(true);
            case 2 -> phase2.setSelected(true);
            case 3 -> phase3.setSelected(true);
            default -> phase1.setSelected(true);
        }

        chainListPanel.removeAll();
        if (has) {
            // По всей сцене (не только scr.getPowerChains()) — цепочка, начатая на
            // ЭТОМ экране, но физически хранящаяся на другом (см. Task #64/#68),
            // иначе тут вообще не появлялась бы, хотя часть её кабинетов — свои.
            var chains = model.powerChainsTouchingScreen(scr);
            if (chains.isEmpty()) {
                chainListPanel.add(UiKit.muted("Цепочек ещё нет"));
            }
            boolean trackLoad = settings.activeProfile().isLoadTrackingEnabled();
            Scene scene = model.getCurrentScene();
            for (PowerChain c : chains) {
                boolean crossScreen = c.getCabinetInstanceIds().stream().anyMatch(id -> scr.cabinetById(id) == null);
                String suffix = crossScreen ? " (продолжается на другом экране)" : "";
                // Текущая нагрузка цепочки показывается ВСЕГДА (это и есть итоговое число,
                // с которым сравнивается ёмкость разъёма) — независимо от того, известен ли
                // сам номинал разъёма (см. capacityKnown) и включён ли контроль перегрузки
                // целиком; предупреждение/кнопка «Я знаю» — единственное, что зависит от
                // настройки «Контроль электрической нагрузки».
                AppModel.ChainLoadStatus status = model.powerChainLoadStatus(scene, c);
                String label = "L" + c.getPhase() + " · " + c.getCabinetInstanceIds().size() + " каб." + suffix
                        + " · " + UiKit.fmt(status.loadWatts()) + " Вт";
                AppModel.ChainLoadStatus warnStatus = trackLoad ? status : null;
                chainListPanel.add(chainRow(label, Palette.phaseColor(c.getPhase()), warnStatus,
                        () -> model.acknowledgePowerChainOverload(scene, c), () -> model.deletePowerChain(c.getId())));
            }
        }
        UiKit.recapHeight(chainsSection);
        chainListPanel.revalidate();
        chainListPanel.repaint();

        if (has && showAllScreens.isSelected() && model.getCurrentScene() != null) {
            // Показана вся сцена — статистика одного активного экрана не даёт полной
            // картины нагрузки, показываем сумму по всем экранам сцены (Task #71).
            List<Screen> screens = model.getCurrentScene().getScreens();
            ScreenStats s = ScreenLogic.aggregateStats(screens, model::typeOf, model.getWorkspace());
            statCabinetType.setText(cabinetTypeSummary(screens));
            statRes.setText(screens.size() + " экран(ов)");
            statSize.setText("—");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
            statPhases.setText(String.format(
                    "<html>L1: %d каб. · %s Вт<br>L2: %d каб. · %s Вт<br>L3: %d каб. · %s Вт</html>",
                    s.phaseCabinetCounts()[1], UiKit.fmt(s.phasePowerW()[1]),
                    s.phaseCabinetCounts()[2], UiKit.fmt(s.phasePowerW()[2]),
                    s.phaseCabinetCounts()[3], UiKit.fmt(s.phasePowerW()[3])));
        } else if (has) {
            ScreenStats s = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            com.vjstb.ledscheme.model.CabinetType ct = model.typeOf(scr);
            statCabinetType.setText(ct != null ? ct.getName() : "—");
            statRes.setText(s.resolutionWidthPx() + " × " + s.resolutionHeightPx() + " px");
            statSize.setText(UiKit.fmt(s.physicalWidthMm()) + " × " + UiKit.fmt(s.physicalHeightMm()) + " мм");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
            statPhases.setText(String.format(
                    "<html>L1: %d каб. · %s Вт<br>L2: %d каб. · %s Вт<br>L3: %d каб. · %s Вт</html>",
                    s.phaseCabinetCounts()[1], UiKit.fmt(s.phasePowerW()[1]),
                    s.phaseCabinetCounts()[2], UiKit.fmt(s.phasePowerW()[2]),
                    s.phaseCabinetCounts()[3], UiKit.fmt(s.phasePowerW()[3])));
        } else {
            statCabinetType.setText("—");
            statRes.setText("—");
            statSize.setText("—");
            statCount.setText("—");
            statPower.setText("—");
            statWeight.setText("—");
            statPhases.setText("");
        }
        UiKit.recapHeight(statsSection);

        canvas.revalidate();
        canvas.repaint();
        sceneOverview.revalidate();
        sceneOverview.repaint();
        cornerPreview.repaint();
    }

    private void updateCornerPreviewVisibility() {
        cornerPreviewHost.setVisible(settings.activeProfile().isPreviewWidgetEnabled() && !showAllScreens.isSelected());
    }

    private javax.swing.JComponent chainRow(String label, java.awt.Color dot, AppModel.ChainLoadStatus status,
                                             Runnable onAcknowledge, Runnable onDelete) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(dot);
        boolean overloaded = status != null && status.overloaded();
        JLabel text = new JLabel((overloaded ? "⚠ " : "") + label);
        if (overloaded) {
            text.setForeground(Palette.WARN);
            text.setToolTipText("Перегрузка: " + UiKit.fmt(status.loadWatts()) + " Вт при допустимых "
                    + UiKit.fmt(status.capacityWatts()) + " Вт на разъём кабинета");
        }
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (overloaded && !status.acknowledged()) {
            JButton ack = new JButton("Я знаю");
            ack.setToolTipText("Подтвердить перегрузку — предупреждение и блокировка экспорта снимутся,"
                    + " пока нагрузка не вырастет ещё больше");
            ack.addActionListener(e -> onAcknowledge.run());
            east.add(ack);
        }
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> onDelete.run());
        east.add(del);
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(east, BorderLayout.EAST);
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

    /** Тип кабинета для показанного набора экранов — единое название, если у всех
     *  один и тот же тип, иначе краткая сводка «разные (N)» (см. Task #101 —
     *  показывать тип кабинета экрана прямо в статистике для удобства). */
    private String cabinetTypeSummary(List<Screen> screens) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Screen s : screens) {
            com.vjstb.ledscheme.model.CabinetType t = model.typeOf(s);
            names.add(t != null ? t.getName() : "?");
        }
        if (names.isEmpty()) {
            return "—";
        }
        return names.size() == 1 ? names.iterator().next() : "разные (" + names.size() + ")";
    }
}
