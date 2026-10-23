package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Отдельное всплывающее окно с 3D-превью конструктива ({@link Structure3DPanel}) — раньше
 * панель жила встроенной в {@code ui.stage.SetupStagePanel} (внутри узкой боковой колонки
 * «Прериг сцены»), там ей физически не хватало места (баг-репорт: "давай вынесем в отдельное
 * всплывающее окно, как калькулятор видеотаймингов") — тот же паттерн, что
 * {@link VideoTimingCalculatorDialog}: немодальный {@code JDialog} (не блокирует основное
 * окно — можно одновременно крутить 3D и менять числа в «Параметры экрана»/«Прериг сцены»),
 * фиксированный крупный стартовый размер (не {@code pack()} — GL-панель сама не диктует
 * "естественный" размер компоновке так, как это делают обычные Swing-виджеты в
 * {@code VideoTimingCalculatorDialog}), но окно свободно resizable пользователем.
 *
 * <p><b>Селектор "Рама для добавления"</b> (Phase 2.2, "конструктор с предварительным
 * аппаратно рассчитанным шаблоном" — прямая цитата пользователя): расчёт расставляет
 * номинальный тип рамы, но пользователь может переключить тип, с которым будет создана
 * СЛЕДУЮЩАЯ ячейка при клике по призраку в 3D (например, короткая рама на крайней башне,
 * где номинальная не помещается впритык к краю экрана, или конкретная метровая рама для
 * усиления в зоне выноса) — см. {@link Structure3DPanel#setActiveNewCellFrameTypeId}.
 *
 * <p><b>Ползунки формы/радиуса/зазора/числа башен</b> (запрос 2026-10-01: «продублируем в 3д
 * редакторе … ползунками, это очень удобно») — {@link Structure3DControlsPanel}: пересчёт тем же
 * путём, что «Предварительный расчёт», одна запись отмены на жест, предупреждения расчёта строкой
 * под ползунками. Окно подписано на модель, поэтому поля «Сетапа» и ползунки всегда показывают
 * одно состояние.
 *
 * <p><b>Панель «Раскладка»</b> (запрос 2026-10-22) — {@link StructureLayoutPanel}: группа экранов в
 * общем плане (позы, стык под углом), ручная расстановка башен (встык, под любым углом),
 * режимы «Рамы»/«Раскладка» ({@link Structure3DPanel#setLayoutMode}).
 */
public class Structure3DDialog extends JDialog {

    private final Structure3DPanel panel;
    private final Structure3DControlsPanel controls;
    private final StructureLayoutPanel layoutPanel;
    private final AppModel model;
    /** Подписка на модель (2026-10-01, ползунки радиуса/зазора/числа башен): 3D-вид и ползунки
     *  перерисовываются после ЛЮБОГО изменения — полей «Сетапа», Ctrl+Z, самих ползунков.
     *  Снимается при закрытии окна ({@link AppModel#removeListener}). */
    private final AppModel.Listener modelListener = this::refresh;

    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    /** Горячая клавиша режима «Рамы»/«Раскладка» — диспетчер живёт, пока окно открыто (снимается в {@link #dispose()}). */
    private final java.awt.KeyEventDispatcher modeKeyDispatcher = this::dispatchModeKey;

    public Structure3DDialog(Window owner, AppModel model) {
        this(owner, model, null);
    }

    /** {@code settings} — для переназначаемых горячих клавиш окна (Персонализация → «Горячие клавиши»); {@code null} —
     *  значения по умолчанию. */
    public Structure3DDialog(Window owner, AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        super(owner, "3D-превью конструктива", ModalityType.MODELESS);
        this.model = model;
        this.settings = settings;

        panel = new Structure3DPanel(model);
        controls = new Structure3DControlsPanel(model);
        layoutPanel = new StructureLayoutPanel(model, panel);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // Панель «Раскладка» (2026-10-22): режим «Рамы/Раскладка», параметры экранов группы и ручная
        // расстановка башен -- справа от 3D-вида; граница между ними ТЯНЕТСЯ (ширина панели не фиксирована,
        // запрос «ширина инспектора не меняется»), 3D забирает остаток при изменении размера окна.
        panel.setMinimumSize(new Dimension(320, 240));
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, panel,
                layoutPanel);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        split.setDividerSize(8);
        split.setBorder(null);
        content.add(split, BorderLayout.CENTER);

        // Стандартные CAD-виды -- быстрый сброс угла камеры на канонический ракурс (без
        // изменения зума/панорамирования), тот же смысл, что navcube/view-cube в CAD-пакетах,
        // просто кнопками, а не 3D-виджетом (в проекте нет готовых иконок под него).
        JPanel viewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        viewRow.add(new JLabel("Вид:"));
        viewRow.add(viewButton(panel, "Спереди", 0, 0));
        viewRow.add(viewButton(panel, "Сбоку", 90, 0));
        viewRow.add(viewButton(panel, "Сзади", 180, 0));
        viewRow.add(viewButton(panel, "Сверху", 0, 89));

        // Легенда цветов индикатора осей в углу вида (Structure3DPanel.drawAxisGizmo) --
        // обычным Swing-текстом, не GL-шрифтом (в этом проекте GL-текст нигде не рендерится,
        // заводить его ради 3 подписей избыточно) -- запрошено пользователем: "чтобы я мог
        // сказать, вокруг какой оси тебе надо элементы выстраивать". СВОЯ строка, а не в одном
        // FlowLayout-ряду с кнопками видов -- баг-репорт "легенда не видна": в узком окне
        // FlowLayout сжимал/переносил её так, что она пропадала из видимой области.
        JPanel legendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel axisLegend = new JLabel("<html>Оси: X — <b><font color='#F24D4D'>красный</font></b>"
                + " (право/лево), Y — <b><font color='#39C639'>зелёный</font></b> (верх/низ),"
                + " Z — <b><font color='#4D8CFF'>синий</font></b> (к экрану/вглубь башни)</html>");
        legendRow.add(axisLegend);

        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.add(viewRow);
        // Ползунки формы/радиуса/зазора/числа башен (запрос 2026-10-01) -- своей строкой между
        // кнопками видов и легендой осей, над GL-видом: клики по рамам и орбита камеры их не
        // задевают.
        topStack.add(controls);
        topStack.add(legendRow);
        content.add(topStack, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(new JLabel("Рама для добавления:"));
        JComboBox<StructureFrameType> frameTypeCombo = new JComboBox<>();
        DefaultComboBoxModel<StructureFrameType> comboModel = new DefaultComboBoxModel<>();
        comboModel.addElement(null);
        for (StructureFrameType t : model.getStructureFrameTypes()) {
            if (t.getKind() == StructureFrameType.Kind.FRAME || t.getKind() == StructureFrameType.Kind.SHORT_FRAME) {
                comboModel.addElement(t);
            }
        }
        frameTypeCombo.setModel(comboModel);
        frameTypeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Object display = value instanceof StructureFrameType t ? t.getName() : "(как обычно)";
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        frameTypeCombo.addActionListener(e -> {
            Object sel = frameTypeCombo.getSelectedItem();
            panel.setActiveNewCellFrameTypeId(sel instanceof StructureFrameType t ? t.getId() : null);
        });
        bottom.add(frameTypeCombo);

        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(new Dimension(1300, 780));
        split.setDividerLocation(1300 - 16 - 360);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        model.addListener(modelListener);
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(modeKeyDispatcher);
    }

    @Override
    public void dispose() {
        model.removeListener(modelListener);
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(modeKeyDispatcher);
        super.dispose();
    }

    private boolean dispatchModeKey(java.awt.event.KeyEvent e) {
        if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED || !isActive()) {
            return false;
        }
        java.awt.Component focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus instanceof javax.swing.text.JTextComponent) {
            return false; // пробел в поле ввода — это пробел
        }
        com.vjstb.ledscheme.settings.KeyCombo combo = settings != null
                ? settings.bindingFor(com.vjstb.ledscheme.settings.HotkeyAction.TOGGLE_STRUCTURE_MODE)
                : com.vjstb.ledscheme.settings.HotkeyAction.TOGGLE_STRUCTURE_MODE.getDefaultCombo();
        if (combo.matchesKey(e)) {
            layoutPanel.toggleMode();
            return true;
        }
        return false;
    }

    /** Обновляет 3D-вид и ползунки. С 2026-10-01 окно само подписано на модель (у {@code
     *  AppModel} появился {@code removeListener} — подписка снимается в {@link #dispose()}, не
     *  утекает); явный вызов из {@code SetupStagePanel#calculateStructure} оставлен — безвреден. */
    public void refresh() {
        panel.refresh();
        controls.syncFromModel();
        layoutPanel.syncFromModel();
    }

    private static JButton viewButton(Structure3DPanel panel, String label, double yawDeg, double pitchDeg) {
        JButton b = new JButton(label);
        b.addActionListener(e -> panel.setViewAngle(yawDeg, pitchDeg));
        return b;
    }
}
