package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.update.VersionManifest;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Ненавязчивое уведомление «доступна новая версия» — показывается автоматически
 * при запуске (см. App.checkForUpdatesInBackground), НЕ модальное (не блокирует
 * работу с уже открытым приложением, в отличие от {@link UpdateDialog}, который
 * открывается только по явному действию пользователя из меню). «Обновить сейчас»
 * открывает обычный {@link UpdateDialog}; «Позже» запоминает версию в настройках
 * (см. SettingsManager.setDismissedUpdateVersion), чтобы про ИМЕННО ЭТУ версию
 * больше не напоминать — при выходе более новой версии уведомление придёт снова.
 */
public class UpdateNoticeDialog extends JDialog {

    public static void show(Window owner, SettingsManager settings, VersionManifest.Entry newest) {
        new UpdateNoticeDialog(owner, settings, newest).setVisible(true);
    }

    private UpdateNoticeDialog(Window owner, SettingsManager settings, VersionManifest.Entry newest) {
        super(owner, "Доступно обновление", ModalityType.MODELESS);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Доступна версия " + newest.version());
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        if (newest.notes() != null && !newest.notes().isBlank()) {
            JLabel notes = new JLabel("<html><body style='width: 320px'>" + newest.notes() + "</body></html>");
            notes.setForeground(Palette.MUTED);
            notes.setAlignmentX(LEFT_ALIGNMENT);
            content.add(notes);
            content.add(Box.createVerticalStrut(10));
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton later = new JButton("Позже");
        later.addActionListener(e -> {
            settings.setDismissedUpdateVersion(newest.version());
            dispose();
        });
        JButton updateNow = new JButton("Обновить сейчас…");
        updateNow.addActionListener(e -> {
            dispose();
            UpdateDialog.show(owner, settings);
        });
        buttons.add(later);
        buttons.add(updateNow);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(content, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);

        setContentPane(wrapper);
        getRootPane().setDefaultButton(updateNow);
        pack();
        setLocationRelativeTo(owner);
    }
}
