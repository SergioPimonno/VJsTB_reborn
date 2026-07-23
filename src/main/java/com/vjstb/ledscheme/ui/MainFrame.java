package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.stage.OutputStagePanel;
import com.vjstb.ledscheme.ui.stage.PowerStagePanel;
import com.vjstb.ledscheme.ui.stage.SetupStagePanel;
import com.vjstb.ledscheme.ui.stage.SignalStagePanel;
import com.vjstb.ledscheme.ui.stage.VisualizationStagePanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Главное окно: верхнее меню, переключатель этапов работы (Сетап/Питание/Сигнал/
 * Визуализация/Вывод) и область содержимого текущего этапа. Координирует общие
 * горячие клавиши (отмена, построение цепочек на активном этапе).
 */
public class MainFrame extends JFrame {

    private final AppModel model;

    private final SetupStagePanel setupStage;
    private final PowerStagePanel powerStage;
    private final SignalStagePanel signalStage;
    private final VisualizationStagePanel visualizationStage;
    private final OutputStagePanel outputStage;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel statusBar = new JLabel(" ");
    private final javax.swing.JButton undoButton = new javax.swing.JButton("↶ Отменить");

    private String currentStage = StageSwitcher.SETUP;

    public MainFrame(AppModel model) {
        super("LED Scheme Designer");
        this.model = model;

        setupStage = new SetupStagePanel(model);
        powerStage = new PowerStagePanel(model);
        signalStage = new SignalStagePanel(model);
        visualizationStage = new VisualizationStagePanel(model);
        outputStage = new OutputStagePanel(model);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 860);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(new MainMenuBar(this, model, this::showShortcuts));

        JPanel top = new JPanel(new BorderLayout());
        StageSwitcher switcher = new StageSwitcher(this::switchStage);
        top.add(switcher, BorderLayout.CENTER);
        JPanel toolRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4));
        undoButton.setToolTipText("Отменить последнее действие (Ctrl+Z)");
        undoButton.addActionListener(e -> model.undo());
        javax.swing.JButton shortcutsBtn = new javax.swing.JButton("⌨");
        shortcutsBtn.setToolTipText("Горячие клавиши");
        shortcutsBtn.addActionListener(e -> showShortcuts());
        toolRow.add(undoButton);
        toolRow.add(shortcutsBtn);
        top.add(toolRow, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(top, BorderLayout.NORTH);

        content.add(setupStage, StageSwitcher.SETUP);
        content.add(powerStage, StageSwitcher.POWER);
        content.add(signalStage, StageSwitcher.SIGNAL);
        content.add(visualizationStage, StageSwitcher.VISUALIZATION);
        content.add(outputStage, StageSwitcher.OUTPUT);
        add(content, BorderLayout.CENTER);

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusBar.setForeground(Palette.MUTED);
        add(statusBar, BorderLayout.SOUTH);

        model.addListener(this::refresh);
        installShortcuts();
        refresh();
    }

    private void switchStage(String stage) {
        // переключение на цепочко-строящий этап завершает построение, начатое на другом
        if (!stage.equals(StageSwitcher.POWER)) {
            powerStage.chainController().finish();
        }
        if (!stage.equals(StageSwitcher.SIGNAL)) {
            signalStage.chainController().finish();
        }
        currentStage = stage;
        if (stage.equals(StageSwitcher.POWER)) {
            model.setMode(AppModel.Mode.POWER);
        } else if (stage.equals(StageSwitcher.SIGNAL)) {
            model.setMode(AppModel.Mode.SIGNAL);
        }
        cards.show(content, stage);
        refresh();
    }

    private void installShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || !isActive()) {
                    return false;
                }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                    model.undo();
                    return true;
                }
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus instanceof JTextComponent) {
                    return false; // не мешаем вводу текста
                }
                ChainInteractionController ctrl = activeChainController();
                if (ctrl == null) {
                    return false;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> { ctrl.finish(); return true; }
                    case KeyEvent.VK_UP -> { ctrl.moveCursor(-1, 0); return true; }
                    case KeyEvent.VK_DOWN -> { ctrl.moveCursor(1, 0); return true; }
                    case KeyEvent.VK_LEFT -> { ctrl.moveCursor(0, -1); return true; }
                    case KeyEvent.VK_RIGHT -> { ctrl.moveCursor(0, 1); return true; }
                    case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                        if (!ctrl.isChainBuilding()) {
                            String hovered = hoveredCabinetOnActiveStage();
                            if (hovered != null) {
                                model.toggleCabinetHidden(hovered);
                                return true;
                            }
                        }
                        return false;
                    }
                    default -> { return false; }
                }
            }
        });
    }

    private ChainInteractionController activeChainController() {
        if (currentStage.equals(StageSwitcher.POWER)) {
            return powerStage.chainController();
        }
        if (currentStage.equals(StageSwitcher.SIGNAL)) {
            return signalStage.chainController();
        }
        return null;
    }

    private String hoveredCabinetOnActiveStage() {
        if (currentStage.equals(StageSwitcher.POWER)) {
            return powerStage.chainController().hoveredCabinetId();
        }
        if (currentStage.equals(StageSwitcher.SIGNAL)) {
            return signalStage.chainController().hoveredCabinetId();
        }
        return null;
    }

    private void showShortcuts() {
        String msg = """
                Ctrl+Z — отменить действие (везде)

                На этапах «Питание» / «Сигнал»:
                Клик по фазе/порту — начать новую цепочку
                Клик, зажатая ЛКМ или стрелки — добавить кабинеты
                Esc — завершить цепочку
                ПКМ по порту (Сигнал) — переключить бэкап
                Del — скрыть/показать кабинет под курсором (вне построения)
                Ctrl+колесо — масштаб холста""";
        JOptionPane.showMessageDialog(this, msg, "Горячие клавиши", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refresh() {
        SwingUtilities.invokeLater(() -> {
            undoButton.setEnabled(model.canUndo());
            undoButton.setText(model.undoDepth() > 0 ? "↶ Отменить (" + model.undoDepth() + ")" : "↶ Отменить");
            updateStatus();
        });
    }

    private void updateStatus() {
        if (model.getCurrentScreen() != null) {
            statusBar.setText("Экран «" + model.getCurrentScreen().getName() + "»  ·  этап: " + currentStage);
        } else if (model.getCurrentScene() != null) {
            statusBar.setText("Сцена «" + model.getCurrentScene().getName() + "»  ·  этап: " + currentStage);
        } else if (model.getCurrentProject() != null) {
            statusBar.setText("Проект «" + model.getCurrentProject().getName() + "»  ·  этап: " + currentStage);
        } else {
            statusBar.setText("Создайте или выберите проект в разделе «Сетап»  ·  этап: " + currentStage);
        }
    }
}
