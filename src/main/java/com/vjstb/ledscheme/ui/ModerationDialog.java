package com.vjstb.ledscheme.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import com.vjstb.ledscheme.sync.ProposalClient;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * «Модерация предложений…» — список предложений "на рассмотрении" (см.
 * {@code GET /api/proposals/pending} на сервере) + approve/reject. Требует роль
 * MODERATOR/ADMIN (см. {@link AccountDialog}) — иначе даже не открывается,
 * тот же паттерн проверки, что и в {@link ProposeDialog}.
 */
public class ModerationDialog extends JDialog {

    private final DefaultListModel<ProposalClient.ProposalDto> listModel = new DefaultListModel<>();
    private final JList<ProposalClient.ProposalDto> list = new JList<>(listModel);
    private final JLabel status = new JLabel(" ");
    private final JTextField note = new JTextField();
    private final JButton approve = new JButton("Одобрить");
    private final JButton reject = new JButton("Отклонить");
    private final SettingsManager settings;

    public static void show(Window owner, SettingsManager settings) {
        String role = settings.getSettings().getAuthRole();
        if (!"MODERATOR".equals(role) && !"ADMIN".equals(role)) {
            JOptionPane.showMessageDialog(owner,
                    "Нужна роль модератора/админа. Войдите под соответствующим аккаунтом (Настройки → Аккаунт…).",
                    "Недостаточно прав", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new ModerationDialog(owner, settings).setVisible(true);
    }

    private ModerationDialog(Window owner, SettingsManager settings) {
        super(owner, "Модерация предложений", ModalityType.APPLICATION_MODAL);
        this.settings = settings;

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new NamedRenderer<ProposalClient.ProposalDto>(this::summaryLine, this::detailLine));
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(520, 260));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(listScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel noteRow = new JPanel(new BorderLayout(6, 0));
        noteRow.add(new JLabel("Комментарий модератора:"), BorderLayout.WEST);
        noteRow.add(note, BorderLayout.CENTER);
        bottom.add(noteRow);
        bottom.add(Box.createVerticalStrut(6));

        status.setForeground(Palette.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(status);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        approve.setEnabled(false);
        reject.setEnabled(false);
        approve.addActionListener(e -> decide(true));
        reject.addActionListener(e -> decide(false));
        buttons.add(close);
        buttons.add(reject);
        buttons.add(approve);
        bottom.add(buttons);
        content.add(bottom, BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            boolean has = list.getSelectedValue() != null;
            approve.setEnabled(has);
            reject.setEnabled(has);
        });

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        loadPending();
    }

    private String summaryLine(ProposalClient.ProposalDto p) {
        return "[" + p.libraryItemKind() + "] " + nameFromDraft(p.draftJson()) + " — автор: " + p.authorUserId();
    }

    private String detailLine(ProposalClient.ProposalDto p) {
        return "Обоснование: " + p.justification();
    }

    private String nameFromDraft(String draftJson) {
        try {
            JsonNode node = new ObjectMapper().readTree(draftJson);
            JsonNode name = node.get("name");
            return name != null && !name.isNull() ? name.asText() : "(без имени)";
        } catch (Exception malformedDraftJson) {
            return "(без имени)";
        }
    }

    private void loadPending() {
        status.setText("Загрузка…");
        String token = settings.getSettings().getAuthToken();
        new SwingWorker<List<ProposalClient.ProposalDto>, Void>() {
            @Override
            protected List<ProposalClient.ProposalDto> doInBackground() throws Exception {
                return new ProposalClient(LibrarySyncClient.resolveBaseUrl(settings)).pending(token);
            }

            @Override
            protected void done() {
                try {
                    List<ProposalClient.ProposalDto> items = get();
                    listModel.clear();
                    items.forEach(listModel::addElement);
                    status.setText(items.isEmpty() ? "Нет предложений на рассмотрении." : " ");
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private void decide(boolean approved) {
        ProposalClient.ProposalDto selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        approve.setEnabled(false);
        reject.setEnabled(false);
        status.setText(approved ? "Одобрение…" : "Отклонение…");
        String token = settings.getSettings().getAuthToken();
        String noteText = note.getText().trim();
        new SwingWorker<ProposalClient.ProposalDto, Void>() {
            @Override
            protected ProposalClient.ProposalDto doInBackground() throws Exception {
                String base = LibrarySyncClient.resolveBaseUrl(settings);
                return approved ? new ProposalClient(base).approve(token, selected.id(), noteText)
                        : new ProposalClient(base).reject(token, selected.id(), noteText);
            }

            @Override
            protected void done() {
                try {
                    get();
                    listModel.removeElement(selected);
                    note.setText("");
                    status.setText(approved ? "Одобрено." : "Отклонено.");
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                    approve.setEnabled(true);
                    reject.setEnabled(true);
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
