package com.vjstb.ledscheme.ui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Переключатель этапов работы (Сетап/Питание/Сигнал/Визуализация/Вывод), по аналогии со страницами DaVinci/Pixera. */
public class StageSwitcher extends JPanel {

    public static final String SETUP = "Сетап";
    public static final String POWER = "Питание";
    public static final String SIGNAL = "Сигнал";
    public static final String VISUALIZATION = "Визуализация";
    public static final String OUTPUT = "Вывод";
    public static final String[] STAGES = {SETUP, POWER, SIGNAL, VISUALIZATION, OUTPUT};

    private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();

    public StageSwitcher(Consumer<String> onSelect) {
        setLayout(new GridLayout(1, STAGES.length));
        setBorder(BorderFactory.createEmptyBorder());
        ButtonGroup group = new ButtonGroup();
        for (String stage : STAGES) {
            JToggleButton b = new JToggleButton(stage);
            b.setFocusPainted(false);
            b.setPreferredSize(new Dimension(120, 34));
            b.addActionListener(e -> onSelect.accept(stage));
            group.add(b);
            buttons.put(stage, b);
            add(b);
        }
        buttons.get(SETUP).setSelected(true);
    }

    public void select(String stage) {
        JToggleButton b = buttons.get(stage);
        if (b != null) {
            b.setSelected(true);
        }
    }
}
