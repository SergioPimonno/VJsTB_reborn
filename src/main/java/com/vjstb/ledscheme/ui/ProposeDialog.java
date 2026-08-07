package com.vjstb.ledscheme.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.ProposalClient;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 * «Предложить…» — отправляет уже сохранённый локальный элемент библиотеки
 * (кабинет/контроллер/пресет/кабель/интерфейс) как предложение на сервер (см.
 * {@link ProposalClient}). Требует вход в аккаунт (см. {@link AccountDialog}) —
 * если токена нет, диалог даже не открывается.
 */
public class ProposeDialog extends JDialog {

    public static void show(Window owner, SettingsManager settings, String libraryItemKind, String itemName,
                             Object item) {
        if (settings.getSettings().getAuthToken() == null) {
            JOptionPane.showMessageDialog(owner,
                    "Сначала войдите в аккаунт (Настройки → Аккаунт…).",
                    "Нужен вход", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new ProposeDialog(owner, settings, libraryItemKind, itemName, item).setVisible(true);
    }

    private ProposeDialog(Window owner, SettingsManager settings, String libraryItemKind, String itemName,
                           Object item) {
        super(owner, "Предложить в общую библиотеку", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel name = new JLabel("Элемент: " + itemName);
        name.setAlignmentX(LEFT_ALIGNMENT);
        content.add(name);
        content.add(Box.createVerticalStrut(10));

        content.add(UiKit.muted("Обоснование (зачем добавить, где применяется):"));
        JTextArea justification = new JTextArea(4, 30);
        justification.setLineWrap(true);
        justification.setWrapStyleWord(true);
        JScrollPane justificationScroll = new JScrollPane(justification);
        justificationScroll.setAlignmentX(LEFT_ALIGNMENT);
        content.add(justificationScroll);
        content.add(Box.createVerticalStrut(10));

        JLabel status = new JLabel(" ");
        status.setForeground(Palette.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        content.add(status);
        content.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JButton submit = new JButton("Отправить");
        submit.addActionListener(e -> {
            String justificationText = justification.getText().trim();
            if (justificationText.isEmpty()) {
                status.setText("Заполните обоснование.");
                return;
            }
            submit.setEnabled(false);
            status.setText("Отправка…");
            submitProposal(settings, libraryItemKind, item, justificationText, status, submit);
        });
        buttons.add(cancel);
        buttons.add(submit);
        content.add(buttons);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private void submitProposal(SettingsManager settings, String libraryItemKind, Object item, String justification,
                                 JLabel status, JButton submit) {
        String token = settings.getSettings().getAuthToken();
        new SwingWorker<ProposalClient.ProposalDto, Void>() {
            @Override
            protected ProposalClient.ProposalDto doInBackground() throws Exception {
                String draftJson = new ObjectMapper().writeValueAsString(item);
                return new ProposalClient().submit(token, libraryItemKind, draftJson, justification);
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Отправлено — ожидает модерации.");
                    submit.setEnabled(false);
                } catch (Exception ex) {
                    submit.setEnabled(true);
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
