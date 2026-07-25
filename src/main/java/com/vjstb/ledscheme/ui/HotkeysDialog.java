package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.HotkeyAction;
import com.vjstb.ledscheme.settings.KeyCombo;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Диалог «Горячие клавиши»: переназначение сочетаний для действий приложения —
 * вынесен из общего окна персонализации в отдельное окошко (см. также
 * {@link PersonalizationDialog} — цвета/профили, {@link PreferencesDialog} —
 * поведенческие переключатели), чтобы каждый раздел настраивался независимо.
 */
public class HotkeysDialog extends JDialog {

    private final SettingsManager settings;
    private final Map<HotkeyAction, JLabel> comboLabels = new LinkedHashMap<>();

    public HotkeysDialog(Window owner, SettingsManager settings) {
        super(owner, "Персонализация — горячие клавиши", ModalityType.MODELESS);
        this.settings = settings;
        settings.addListener(this::refresh);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildHotkeysPanel());
        content.add(Box.createVerticalStrut(10));

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        content.add(closeRow);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Секция «Горячие клавиши»: у каждого переназначаемого действия — текущая
     *  комбинация, кнопка «Назначить…» (открывает захват следующей нажатой клавиши
     *  или кнопки мыши, в т.ч. с модификаторами — например, Shift+ЛКМ) и «Сброс»
     *  к встроенному значению по умолчанию. */
    private JPanel buildHotkeysPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (HotkeyAction action : HotkeyAction.values()) {
            body.add(hotkeyRow(action));
        }
        body.add(Box.createVerticalStrut(4));
        JLabel hint = new JLabel("<html>«Назначить…» — далее нажмите клавишу (можно с Ctrl/Shift/Alt) или"
                + " кликните нужной кнопкой мыши в открывшемся окошке.</html>");
        hint.setForeground(Palette.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        return (JPanel) UiKit.section("Горячие клавиши", body);
    }

    private JPanel hotkeyRow(HotkeyAction action) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(new JLabel(action.getLabel()), BorderLayout.CENTER);

        JLabel comboLabel = new JLabel(settings.bindingFor(action).label());
        comboLabel.setForeground(Palette.ACCENT);
        comboLabel.setPreferredSize(new Dimension(90, 20));
        comboLabel.setHorizontalAlignment(JLabel.CENTER);
        comboLabels.put(action, comboLabel);

        JButton rebind = new JButton("Назначить…");
        rebind.addActionListener(e -> {
            KeyCombo combo = captureCombo();
            if (combo != null) {
                settings.setBinding(action, combo);
            }
        });
        JButton reset = new JButton("Сброс");
        reset.addActionListener(e -> settings.resetBinding(action));

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        east.add(comboLabel);
        east.add(rebind);
        east.add(reset);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    /** Открывает модальное окошко, ждущее следующую клавишу или клик мышью
     *  (с учётом текущих модификаторов Ctrl/Shift/Alt), и возвращает результат —
     *  или null, если пользователь нажал «Отмена». Пресекается только кнопка
     *  «Отмена»: сам Esc должен оставаться назначаемым (например, чтобы перенести
     *  завершение цепочки на другую клавишу, если физическая Esc не работает). */
    private KeyCombo captureCombo() {
        JDialog dlg = new JDialog(this, "Новая комбинация", ModalityType.APPLICATION_MODAL);
        KeyCombo[] result = new KeyCombo[1];

        JLabel label = new JLabel("<html>Нажмите нужную клавишу (можно удерживать Ctrl/Shift/Alt)<br>"
                + "или кликните нужной кнопкой мыши в этом окне.</html>");
        label.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel content = new JPanel(new BorderLayout());
        content.setFocusable(true);
        content.add(label, BorderLayout.CENTER);

        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);
        content.add(south, BorderLayout.SOUTH);

        content.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int kc = e.getKeyCode();
                if (kc == KeyEvent.VK_SHIFT || kc == KeyEvent.VK_CONTROL || kc == KeyEvent.VK_ALT) {
                    return; // модификатор сам по себе — не комбинация, ждём следующую клавишу
                }
                result[0] = KeyCombo.ofKey(kc, e.isControlDown(), e.isShiftDown(), e.isAltDown());
                dlg.dispose();
            }
        });
        content.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                result[0] = KeyCombo.ofMouse(e.getButton(), e.isControlDown(), e.isShiftDown(), e.isAltDown());
                dlg.dispose();
            }
        });
        dlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                content.requestFocusInWindow();
            }
        });

        dlg.setContentPane(content);
        dlg.setSize(380, 150);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result[0];
    }

    private void refresh() {
        for (var entry : comboLabels.entrySet()) {
            entry.getValue().setText(settings.bindingFor(entry.getKey()).label());
        }
    }
}
