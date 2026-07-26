package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.HotkeyAction;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.stage.LibrariesStagePanel;
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
 * Генерация масок/Вывод) и область содержимого текущего этапа. Координирует общие
 * горячие клавиши (отмена, построение цепочек на активном этапе).
 */
public class MainFrame extends JFrame {

    private final AppModel model;
    private final SettingsManager settings;

    private final SetupStagePanel setupStage;
    private final PowerStagePanel powerStage;
    private final SignalStagePanel signalStage;
    private final VisualizationStagePanel visualizationStage;
    private final OutputStagePanel outputStage;
    private final LibrariesStagePanel librariesStage;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel statusBar = new JLabel(" ");
    private final javax.swing.JButton undoButton = new javax.swing.JButton("↶ Отменить");

    private String currentStage = StageSwitcher.SETUP;

    public MainFrame(AppModel model, SettingsManager settings) {
        super("LED Scheme Designer");
        this.model = model;
        this.settings = settings;

        setupStage = new SetupStagePanel(model, settings);
        powerStage = new PowerStagePanel(model, settings);
        signalStage = new SignalStagePanel(model, settings);
        visualizationStage = new VisualizationStagePanel(model, settings);
        outputStage = new OutputStagePanel(model, settings);
        librariesStage = new LibrariesStagePanel(model);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 860);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(new MainMenuBar(this, model, settings, this::showShortcuts));

        JPanel top = new JPanel(new BorderLayout());
        StageSwitcher switcher = new StageSwitcher(this::switchStage);
        top.add(switcher, BorderLayout.CENTER);
        JPanel toolRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4));
        undoButton.setToolTipText("Отменить последнее действие (Ctrl+Z)");
        undoButton.addActionListener(e -> model.undo());
        javax.swing.JButton shortcutsBtn = new javax.swing.JButton("⌨");
        shortcutsBtn.setToolTipText("Горячие клавиши");
        shortcutsBtn.addActionListener(e -> showShortcuts());
        javax.swing.JButton guideBtn = new javax.swing.JButton("📖 Руководство");
        guideBtn.setToolTipText("Как пользоваться: построение цепочек, контроллеры/порты, радиальное меню,"
                + " коммутация через гнёзда, библиотека карт");
        guideBtn.addActionListener(e -> new GuideDialog(this, settings).setVisible(true));
        toolRow.add(undoButton);
        toolRow.add(guideBtn);
        toolRow.add(shortcutsBtn);
        top.add(toolRow, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(top, BorderLayout.NORTH);

        content.add(setupStage, StageSwitcher.SETUP);
        content.add(powerStage, StageSwitcher.POWER);
        content.add(signalStage, StageSwitcher.SIGNAL);
        content.add(visualizationStage, StageSwitcher.VISUALIZATION);
        content.add(outputStage, StageSwitcher.OUTPUT);
        content.add(librariesStage, StageSwitcher.LIBRARIES);
        add(content, BorderLayout.CENTER);

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusBar.setForeground(Palette.MUTED);
        add(statusBar, BorderLayout.SOUTH);

        model.addListener(this::refresh);
        // после смены палитры (Персонализация) поля Palette обновляются, но холсты
        // рисуются через Graphics2D напрямую и сами не перерисуются — просим окно целиком.
        settings.addListener(this::repaint);
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
                // Действия ниже переназначаемы (см. HotkeysDialog) — раньше были
                // жёстко зашиты на конкретные коды клавиш,
                // из-за чего пользователь с нерабочей физической клавишей Esc не мог
                // ничем её заменить и физически не мог завершить построение цепочки.
                if (settings.bindingFor(HotkeyAction.UNDO).matchesKey(e)) {
                    model.undo();
                    return true;
                }
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus instanceof JTextComponent) {
                    return false; // не мешаем вводу текста
                }
                // Цифровые хоткеи фазы (Питание, 1-3) / порта (Сигнал, 1-9, 0 = 10) —
                // выбирают цель для следующей цепочки без клика мышью по кнопке фазы/
                // порта (см. PowerStagePanel.selectPhaseByHotkey/SignalStagePanel
                // .selectPortByHotkey). Не переназначаемы — их 10 штук, отдельная
                // горячая клавиша на каждую избыточна.
                if (!e.isControlDown() && !e.isAltDown() && e.getKeyCode() >= KeyEvent.VK_0
                        && e.getKeyCode() <= KeyEvent.VK_9) {
                    int digit = e.getKeyCode() - KeyEvent.VK_0;
                    if (currentStage.equals(StageSwitcher.POWER) && digit >= 1 && digit <= 3) {
                        powerStage.selectPhaseByHotkey(digit);
                        return true;
                    }
                    if (currentStage.equals(StageSwitcher.SIGNAL)) {
                        signalStage.selectPortByHotkey(digit == 0 ? 10 : digit);
                        return true;
                    }
                }
                ChainInteractionController ctrl = activeChainController();
                if (ctrl == null) {
                    return false;
                }
                if (settings.bindingFor(HotkeyAction.FINISH_CHAIN).matchesKey(e)) {
                    ctrl.finish();
                    return true;
                }
                if (settings.bindingFor(HotkeyAction.MOVE_UP).matchesKey(e)) {
                    ctrl.moveCursor(-1, 0);
                    return true;
                }
                if (settings.bindingFor(HotkeyAction.MOVE_DOWN).matchesKey(e)) {
                    ctrl.moveCursor(1, 0);
                    return true;
                }
                if (settings.bindingFor(HotkeyAction.MOVE_LEFT).matchesKey(e)) {
                    ctrl.moveCursor(0, -1);
                    return true;
                }
                if (settings.bindingFor(HotkeyAction.MOVE_RIGHT).matchesKey(e)) {
                    ctrl.moveCursor(0, 1);
                    return true;
                }
                if (settings.bindingFor(HotkeyAction.TOGGLE_HIDDEN).matchesKey(e)
                        || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (!ctrl.isChainBuilding()) {
                        String hovered = hoveredCabinetOnActiveStage();
                        if (hovered != null) {
                            model.toggleCabinetHidden(hovered);
                            return true;
                        }
                    }
                    return false;
                }
                return false;
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
        String msg = settings.bindingFor(HotkeyAction.UNDO).label() + " — отменить действие (везде)\n\n"
                + "На этапах «Питание» / «Сигнал»:\n"
                + "Клик по фазе/порту — начать новую цепочку\n"
                + "Клик, зажатая ЛКМ или стрелки — добавить кабинеты\n"
                + settings.bindingFor(HotkeyAction.FINISH_CHAIN).label() + " — завершить цепочку\n"
                + "ПКМ по порту (Сигнал) — переключить бэкап\n"
                + settings.bindingFor(HotkeyAction.TOGGLE_HIDDEN).label()
                + " — скрыть/показать кабинет под курсором (вне построения)\n"
                + "Ctrl+колесо — масштаб холста\n\n"
                + "Все горячие клавиши выше можно переназначить: Персонализация → «Горячие клавиши».";
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
