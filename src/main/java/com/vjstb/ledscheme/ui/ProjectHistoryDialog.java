package com.vjstb.ledscheme.ui;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.sync.ProjectArchiveClient;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * «История версий…» одного облачного проекта — кто и когда сохранял (см.
 * {@code GET /api/projects/{id}/versions}, доступно ВСЕМ, не только автору — по
 * прямому запросу пользователя: "каждый должен иметь доступ к этой истории").
 * Два действия на выбранной версии: скачать её содержимое как отдельную локальную
 * копию (для просмотра/переноса правок вручную — сама эта копия ПОМНИТ, что она из
 * СТАРОЙ ревизии, поэтому попытка сохранить её в облако корректно упрётся в
 * конфликт версий, а не тихо откатит чужие более новые правки), либо восстановить
 * эту версию как новую ТЕКУЩУЮ (см. {@code POST .../restore/{revision}} — история
 * не переписывается, откат сам становится новой записью в истории).
 */
public class ProjectHistoryDialog extends JDialog {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final ProjectArchiveClient client;
    private final String token;
    private final String cloudProjectId;
    private final AppModel model;
    private final Runnable onChanged;

    private final DefaultListModel<ProjectArchiveClient.VersionSummaryDto> listModel = new DefaultListModel<>();
    private final JList<ProjectArchiveClient.VersionSummaryDto> list = new JList<>(listModel);
    private final JLabel status = new JLabel(" ");

    ProjectHistoryDialog(Window owner, ProjectArchiveClient client, String token, String cloudProjectId,
                          String projectName, AppModel model, Runnable onChanged) {
        super(owner, "История версий — " + projectName, ModalityType.APPLICATION_MODAL);
        this.client = client;
        this.token = token;
        this.cloudProjectId = cloudProjectId;
        this.model = model;
        this.onChanged = onChanged;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new NamedRenderer<ProjectArchiveClient.VersionSummaryDto>(
                d -> "Ревизия " + d.revision(), d -> "сохранил: " + d.savedByUsername() + " · " + d.savedAt()));
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(420, 260));
        content.add(listScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        status.setForeground(Palette.MUTED);
        bottom.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton downloadVersion = new JButton("Скачать как копию");
        downloadVersion.addActionListener(e -> downloadSelected());
        buttons.add(downloadVersion);
        JButton restore = new JButton("Восстановить как текущую…");
        restore.addActionListener(e -> restoreSelected());
        buttons.add(restore);
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        load();
    }

    private void load() {
        status.setText("Загрузка…");
        new SwingWorker<List<ProjectArchiveClient.VersionSummaryDto>, Void>() {
            @Override
            protected List<ProjectArchiveClient.VersionSummaryDto> doInBackground() throws Exception {
                return client.versions(token, cloudProjectId);
            }

            @Override
            protected void done() {
                try {
                    List<ProjectArchiveClient.VersionSummaryDto> items = get();
                    listModel.clear();
                    items.forEach(listModel::addElement);
                    status.setText(items.isEmpty() ? "Истории пока нет." : " ");
                } catch (Exception ex) {
                    status.setText(CloudProjectsDialog.rootMessage(ex));
                }
            }
        }.execute();
    }

    /** Копия ПОМНИТ, из какой (не обязательно последней) ревизии она скачана — см.
     *  class-javadoc: попытка сохранить её поверх более новой облачной версии
     *  корректно уйдёт в конфликт, а не тихо откатит чужие правки. */
    private void downloadSelected() {
        ProjectArchiveClient.VersionSummaryDto selected = list.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите версию в списке.");
            return;
        }
        status.setText("Скачивание…");
        new SwingWorker<Project, Void>() {
            @Override
            protected Project doInBackground() throws Exception {
                ProjectArchiveClient.VersionDto dto = client.getVersion(token, cloudProjectId, selected.revision());
                Project p = MAPPER.readValue(dto.projectJson(), Project.class);
                p.setCloudId(cloudProjectId);
                p.setCloudRevision(dto.revision());
                return p;
            }

            @Override
            protected void done() {
                try {
                    Project imported = model.importProject(get());
                    status.setText("Скачано как новая копия «" + imported.getName() + "» (ревизия "
                            + selected.revision() + ").");
                } catch (Exception ex) {
                    status.setText(CloudProjectsDialog.rootMessage(ex));
                }
            }
        }.execute();
    }

    private void restoreSelected() {
        ProjectArchiveClient.VersionSummaryDto selected = list.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите версию в списке.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Сделать ревизию " + selected.revision() + " новой текущей версией проекта в облаке?\n"
                        + "Ничего не удаляется — это добавит ещё одну запись в историю с этим содержимым.",
                "Восстановление версии", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        status.setText("Восстановление…");
        new SwingWorker<ProjectArchiveClient.ProjectDto, Void>() {
            @Override
            protected ProjectArchiveClient.ProjectDto doInBackground() throws Exception {
                return client.restore(token, cloudProjectId, selected.revision());
            }

            @Override
            protected void done() {
                try {
                    ProjectArchiveClient.ProjectDto restored = get();
                    status.setText("Восстановлено как ревизия " + restored.revision() + ".");
                    onChanged.run();
                    load();
                } catch (Exception ex) {
                    status.setText(CloudProjectsDialog.rootMessage(ex));
                }
            }
        }.execute();
    }
}
