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
 */
public class Structure3DDialog extends JDialog {

    private final Structure3DPanel panel;

    public Structure3DDialog(Window owner, AppModel model) {
        super(owner, "3D-превью конструктива", ModalityType.MODELESS);

        panel = new Structure3DPanel(model);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(panel, BorderLayout.CENTER);

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
        setSize(new Dimension(900, 720));
        setLocationRelativeTo(owner);
    }

    /** Обновляет 3D-вид (например, после пересчёта конструктива, пока диалог уже открыт) —
     *  {@code AppModel} не поддерживает {@code removeListener}, а новая панель создаётся при
     *  каждом открытии диалога, поэтому подписка на {@code addListener} здесь не заводится
     *  (утекла бы) — вызывающий код (см. {@code SetupStagePanel#calculateStructure}) явно
     *  дёргает этот метод, если диалог уже показан. */
    public void refresh() {
        panel.refresh();
    }

    private static JButton viewButton(Structure3DPanel panel, String label, double yawDeg, double pitchDeg) {
        JButton b = new JButton(label);
        b.addActionListener(e -> panel.setViewAngle(yawDeg, pitchDeg));
        return b;
    }
}
