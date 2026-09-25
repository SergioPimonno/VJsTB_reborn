package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Одноразовая миграция устаревшей библиотеки контроллеров ({@code
 *  ControllerType}) в {@link com.vjstb.ledscheme.model.EquipmentPreset} (category
 *  == CONTROLLER) — библиотека контроллеров слита в пресеты оборудования,
 *  2026-09-23 (см. {@code AppModel#migrateControllerLibraryToEquipmentPresets}).
 *
 *  <p>Явное требование: миграция НЕ должна происходить незаметно в фоне —
 *  пользователь видит предпросмотр (что именно станет пресетами, какие
 *  экземпляры контроллеров на сценах затронуты — у них замораживается копия
 *  карт/портов, см. class-javadoc {@code ControllerInstance}) и подтверждает
 *  явной кнопкой, прежде чем что-либо записывается. Тот же принцип
 *  "предпросмотр → явное применение", что уже использует {@link
 *  SchemaImportDialog} для другого одноразового переноса данных. Отмена не
 *  трогает ничего — диалог появится снова при следующем запуске (или его можно
 *  вызвать вручную из меню). */
public class ControllerLibraryMigrationDialog extends JDialog {

    private boolean migrated;

    public ControllerLibraryMigrationDialog(Window owner, AppModel model) {
        super(owner, "Перенос библиотеки контроллеров в оборудование", ModalityType.APPLICATION_MODAL);

        AppModel.ControllerMigrationPreview preview = model.controllerLibraryMigrationPreview();

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel intro = new JLabel("<html>Библиотека контроллеров теперь — часть библиотеки оборудования"
                + " сигнала (раздел «Оборудование сигнала», категория «Контроллер»), вместо отдельной"
                + " «Библиотеки контроллеров». Ниже — что произойдёт при переносе.</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        content.add(intro, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(sectionLabel(preview.types().isEmpty()
                ? "Типов контроллеров для переноса нет."
                : "Станут пресетами оборудования (" + preview.types().size() + "):"));
        for (AppModel.ControllerMigrationPreviewItem item : preview.types()) {
            JLabel row = new JLabel("• " + item.name() + " — " + item.portCount() + " вых. портов"
                    + (item.shared() ? " (общий)" : " (личный)"));
            row.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 0));
            body.add(row);
        }
        body.add(Box.createVerticalStrut(10));

        body.add(sectionLabel(preview.affectedControllerLabels().isEmpty()
                ? "Уже размещённых на экранах контроллеров нет."
                : "Уже размещённые контроллеры (их текущая нумерация портов не изменится, "
                        + preview.affectedControllerLabels().size() + "):"));
        for (String label : preview.affectedControllerLabels()) {
            JLabel row = new JLabel("• " + label);
            row.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 0));
            body.add(row);
        }

        JScrollPane scroll = new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(460, 320));
        content.add(scroll, BorderLayout.CENTER);

        JButton apply = new JButton("Выполнить перенос");
        apply.setEnabled(!preview.isEmpty());
        apply.addActionListener(e -> {
            try {
                model.migrateControllerLibraryToEquipmentPresets();
                migrated = true;
                dispose();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton skip = new JButton(preview.isEmpty() ? "Закрыть" : "Не сейчас");
        skip.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(skip);
        buttons.add(apply);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(Color.DARK_GRAY);
        return label;
    }

    /** Показывает диалог; возвращает {@code true}, если перенос был выполнен
     *  (в т.ч. когда мигрировать было нечего и пользователь просто закрыл) —
     *  вызывающий код (см. {@code MainFrame}) должен в ЭТОМ случае проставить
     *  {@code AppSettings#setControllerLibraryMigrated(true)}, а при "Не сейчас"
     *  (реально было что переносить, но пользователь отказался) — НЕ проставлять,
     *  чтобы диалог показался снова при следующем запуске. */
    public boolean showDialog() {
        setVisible(true);
        return migrated;
    }
}
