package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.CanvasPanel;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.Palette;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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

    private final JToggleButton phase1 = new JToggleButton("L1");
    private final JToggleButton phase2 = new JToggleButton("L2");
    private final JToggleButton phase3 = new JToggleButton("L3");
    private final JLabel hint = new JLabel(" ");
    private final JPanel chainListPanel = new JPanel();
    private javax.swing.JComponent chainsSection;
    private javax.swing.JComponent statsSection;

    private final JLabel statRes = new JLabel("—");
    private final JLabel statSize = new JLabel("—");
    private final JLabel statCount = new JLabel("—");
    private final JLabel statPower = new JLabel("—");
    private final JLabel statWeight = new JLabel("—");
    private final JLabel statPhases = new JLabel();

    public PowerStagePanel(AppModel model) {
        this.model = model;
        this.chainCtrl = new ChainInteractionController(model, this::refresh);
        this.canvas = new CanvasPanel(model, chainCtrl);

        JPanel perScreen = new JPanel(new BorderLayout());
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        perScreen.add(canvasScroll, BorderLayout.CENTER);

        JScrollPane sideScroll = new JScrollPane(buildSide());
        sideScroll.setBorder(null);
        sideScroll.setPreferredSize(new Dimension(SIDE_WIDTH + 20, 100));
        perScreen.add(sideScroll, BorderLayout.EAST);

        schemaPanel = new SchemaPanel(model, SchemaMode.POWER);
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

        JPanel phaseRow = new JPanel(new GridLayout(1, 3, 4, 0));
        ButtonGroup g = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{phase1, phase2, phase3}) {
            g.add(b);
            phaseRow.add(b);
        }
        phase1.addActionListener(e -> selectPhase(1));
        phase2.addActionListener(e -> selectPhase(2));
        phase3.addActionListener(e -> selectPhase(3));
        body.add(UiKit.section("Фаза (клик — новая цепочка)", phaseRow));

        hint.setForeground(Palette.MUTED);
        hint.setText("<html>Клик, зажатая ЛКМ или стрелки — добавить кабинеты.<br>Esc — завершить цепочку.</html>");
        body.add(UiKit.vgap());
        body.add(hint);

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
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        chainCtrl.startFor(ids -> model.addPowerChain(phase, ids));
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
            var chains = scr.getPowerChains();
            if (chains.isEmpty()) {
                chainListPanel.add(UiKit.muted("Цепочек ещё нет"));
            }
            for (PowerChain c : chains) {
                chainListPanel.add(chainRow("L" + c.getPhase() + " · " + c.getCabinetInstanceIds().size() + " каб.",
                        Palette.phaseColor(c.getPhase()), () -> model.deletePowerChain(c.getId())));
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
            statPhases.setText(String.format(
                    "<html>L1: %d каб. · %s Вт<br>L2: %d каб. · %s Вт<br>L3: %d каб. · %s Вт</html>",
                    s.phaseCabinetCounts()[1], UiKit.fmt(s.phasePowerW()[1]),
                    s.phaseCabinetCounts()[2], UiKit.fmt(s.phasePowerW()[2]),
                    s.phaseCabinetCounts()[3], UiKit.fmt(s.phasePowerW()[3])));
        } else {
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
