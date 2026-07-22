package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Главное окно приложения: координирует модель, боковую панель, холст, горячие клавиши. */
public class MainFrame extends JFrame implements CanvasPanel.Controller {

    private final AppModel model;
    private final CanvasPanel canvas;
    private final SidebarPanel sidebar;

    private final JButton undoButton = new JButton("↶ Отменить");
    private final JLabel modeBadge = new JLabel();
    private final JLabel statusBar = new JLabel(" ");

    // transient chain-building state (не сохраняется)
    private boolean chainBuilding = false;
    private final List<String> activeChainCabIds = new ArrayList<>();
    private String hoveredCabId;

    public MainFrame(AppModel model) {
        super("LED Scheme Designer");
        this.model = model;
        this.canvas = new CanvasPanel(model, this);
        this.sidebar = new SidebarPanel(model, this);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);

        JScrollPane sideScroll = new JScrollPane(sidebar);
        sideScroll.setBorder(null);
        sideScroll.getVerticalScrollBar().setUnitIncrement(16);
        sideScroll.setMinimumSize(new Dimension(300, 100));
        sideScroll.setPreferredSize(new Dimension(330, 100));

        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideScroll, canvasScroll);
        split.setDividerLocation(330);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusBar.setForeground(Palette.MUTED);
        add(statusBar, BorderLayout.SOUTH);

        model.addListener(this::refresh);
        installShortcuts();
        refresh();
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        undoButton.setToolTipText("Отменить последнее действие (Ctrl+Z)");
        undoButton.addActionListener(e -> model.undo());
        bar.add(undoButton);

        JButton shortcutsButton = new JButton("⌨ Горячие клавиши");
        shortcutsButton.addActionListener(e -> showShortcuts());
        bar.add(shortcutsButton);

        JButton fitButton = new JButton("Масштаб 100%");
        fitButton.addActionListener(e -> canvas.resetZoom());
        bar.add(fitButton);

        bar.add(javax.swing.Box.createHorizontalGlue());
        modeBadge.setForeground(Palette.ACCENT);
        bar.add(modeBadge);
        return bar;
    }

    // ---- CanvasPanel.Controller ----

    @Override
    public boolean isChainBuilding() {
        return chainBuilding;
    }

    @Override
    public List<String> activeChainCabIds() {
        return activeChainCabIds;
    }

    @Override
    public void cabinetClicked(String cabId) {
        if (!chainBuilding) {
            return;
        }
        if (!activeChainCabIds.contains(cabId)) {
            activeChainCabIds.add(cabId);
            canvas.repaint();
            updateStatus();
        }
    }

    @Override
    public void cabinetHovered(String cabId) {
        hoveredCabId = cabId;
    }

    // ---- chain building coordination (вызывается из боковой панели и шорткатов) ----

    public void startChain() {
        if (model.getCurrentScreen() == null) {
            return;
        }
        chainBuilding = true;
        activeChainCabIds.clear();
        refresh();
        setStatus("Кликайте по кабинетам на схеме в порядке цепочки. Enter — завершить, Esc — отмена.");
    }

    public void cancelChain() {
        chainBuilding = false;
        activeChainCabIds.clear();
        refresh();
    }

    public void finishChain() {
        if (!chainBuilding) {
            return;
        }
        if (activeChainCabIds.isEmpty()) {
            cancelChain();
            return;
        }
        try {
            if (model.getMode() == AppModel.Mode.POWER) {
                model.addPowerChain(model.getActivePhase(), new ArrayList<>(activeChainCabIds));
            } else {
                model.addSignalChain(sidebar.getSignalPort(), sidebar.isSignalBackup(), new ArrayList<>(activeChainCabIds));
            }
            chainBuilding = false;
            activeChainCabIds.clear();
            refresh();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isChainBuildingActive() {
        return chainBuilding;
    }

    // ---- shortcuts ----

    private void installShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || !isActive()) {
                    return false;
                }
                // Ctrl+Z — всегда
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                    model.undo();
                    return true;
                }
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus instanceof JTextComponent) {
                    return false; // не мешаем вводу текста
                }
                if (model.getCurrentScreen() == null) {
                    return false;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P -> { model.setMode(AppModel.Mode.POWER); return true; }
                    case KeyEvent.VK_S -> { model.setMode(AppModel.Mode.SIGNAL); return true; }
                    case KeyEvent.VK_N -> { if (!chainBuilding) startChain(); return true; }
                    case KeyEvent.VK_ENTER -> { if (chainBuilding) finishChain(); return true; }
                    case KeyEvent.VK_ESCAPE -> { if (chainBuilding) cancelChain(); return true; }
                    case KeyEvent.VK_1 -> { if (model.getMode() == AppModel.Mode.POWER) model.setActivePhase(1); return true; }
                    case KeyEvent.VK_2 -> { if (model.getMode() == AppModel.Mode.POWER) model.setActivePhase(2); return true; }
                    case KeyEvent.VK_3 -> { if (model.getMode() == AppModel.Mode.POWER) model.setActivePhase(3); return true; }
                    case KeyEvent.VK_DELETE -> {
                        if (!chainBuilding && hoveredCabId != null) {
                            model.toggleCabinetHidden(hoveredCabId);
                            return true;
                        }
                        return false;
                    }
                    default -> { return false; }
                }
            }
        });
    }

    private void showShortcuts() {
        String msg = """
                Ctrl+Z — отменить действие
                P / S — режим Питание / Сигнал
                N — новая цепочка
                Enter — завершить цепочку
                Esc — отмена построения цепочки
                1 / 2 / 3 — фаза L1 / L2 / L3 (питание)
                Del — скрыть кабинет под курсором
                Ctrl+колесо — масштаб холста""";
        JOptionPane.showMessageDialog(this, msg, "Горячие клавиши", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- refresh ----

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            undoButton.setEnabled(model.canUndo());
            undoButton.setText(model.undoDepth() > 0 ? "↶ Отменить (" + model.undoDepth() + ")" : "↶ Отменить");
            modeBadge.setText(model.getMode() == AppModel.Mode.POWER ? "⚡ Питание" : "📡 Сигнал");
            sidebar.rebuild();
            canvas.revalidate();
            canvas.repaint();
            updateStatus();
        });
    }

    private void updateStatus() {
        if (chainBuilding) {
            return; // сообщение о построении уже показано
        }
        if (model.getCurrentScreen() != null) {
            setStatus("Экран «" + model.getCurrentScreen().getName() + "»");
        } else if (model.getCurrentProject() != null) {
            setStatus("Проект «" + model.getCurrentProject().getName() + "»");
        } else {
            setStatus("Создайте или выберите проект слева.");
        }
    }

    public void setStatus(String text) {
        statusBar.setText(text);
    }

    static void styleSectionBorder(JComponent c, String title) {
        c.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.BORDER), title));
    }
}
