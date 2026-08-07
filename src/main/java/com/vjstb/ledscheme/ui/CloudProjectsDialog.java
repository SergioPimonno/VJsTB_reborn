package com.vjstb.ledscheme.ui;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.ProjectArchiveClient;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * «Облачные проекты…» — общее командное хранилище (см. {@code ProjectArchiveController}):
 * любой вошедший инженер/менеджер видит и правит ЛЮБОЙ проект, не только свои.
 * Обязательный контроль версий — сохранение отклоняется (без тихой перезаписи), если
 * кто-то уже сохранил более новую версию с тех пор, как открыта локальная копия; см.
 * {@link Project#getCloudRevision()} и {@link ProjectArchiveClient.ProjectConflictException}.
 *
 * <p>Два списка рядом: слева локальные проекты этого компьютера, справа — все проекты
 * команды в облаке. «Сохранить в облако» создаёт новую запись (если у локального
 * проекта ещё нет привязки) или обновляет существующую по ПРИВЯЗАННОЙ ревизии;
 * «Скачать копию» кладёт выбранный облачный проект в локальный список под новым
 * локальным id, но с привязкой к тому же облачному id/ревизии. «История версий…»
 * показывает, кто и когда сохранял, с возможностью открыть/восстановить старую версию.
 */
public class CloudProjectsDialog extends JDialog {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final AppModel model;
    private final SettingsManager settings;
    private final ProjectArchiveClient client = new ProjectArchiveClient();

    private final DefaultListModel<Project> localModel = new DefaultListModel<>();
    private final JList<Project> localList = new JList<>(localModel);
    private final DefaultListModel<ProjectArchiveClient.ProjectSummaryDto> cloudModel = new DefaultListModel<>();
    private final JList<ProjectArchiveClient.ProjectSummaryDto> cloudList = new JList<>(cloudModel);
    private final JLabel status = new JLabel(" ");

    /** id облачного проекта -> его сводка из последнего refreshCloud() — чтобы
     *  подсветить локальные копии, отставшие от актуальной ревизии в команде. */
    private final Map<String, ProjectArchiveClient.ProjectSummaryDto> cloudById = new HashMap<>();

    public static void show(Window owner, AppModel model, SettingsManager settings) {
        if (settings.getSettings().getAuthToken() == null) {
            JOptionPane.showMessageDialog(owner,
                    "Нужен вход в аккаунт — облачные проекты видны только вошедшим коллегам"
                            + " (Настройки → Аккаунт…).",
                    "Требуется вход", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new CloudProjectsDialog(owner, model, settings).setVisible(true);
    }

    private CloudProjectsDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Облачные проекты", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.settings = settings;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Локальные проекты"), BorderLayout.NORTH);
        localList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localList.setCellRenderer(new NamedRenderer<Project>(this::localTitle, this::localMeta));
        for (Project p : model.getProjects()) {
            localModel.addElement(p);
        }
        JScrollPane localScroll = new JScrollPane(localList);
        localScroll.setPreferredSize(new Dimension(260, 260));
        left.add(localScroll, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JLabel("Проекты команды"), BorderLayout.NORTH);
        cloudList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cloudList.setCellRenderer(new NamedRenderer<ProjectArchiveClient.ProjectSummaryDto>(
                d -> d.name() + " (ревизия " + d.revision() + ")",
                d -> "автор: " + d.ownerUsername() + " · обновлён: " + d.updatedAt()));
        JScrollPane cloudScroll = new JScrollPane(cloudList);
        cloudScroll.setPreferredSize(new Dimension(260, 260));
        right.add(cloudScroll, BorderLayout.CENTER);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton refreshCloud = new JButton("Обновить список");
        refreshCloud.addActionListener(e -> refreshCloud());
        rightButtons.add(refreshCloud);
        JButton history = new JButton("История версий…");
        history.addActionListener(e -> showHistory());
        rightButtons.add(history);
        right.add(rightButtons, BorderLayout.SOUTH);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.add(Box.createVerticalGlue());
        JButton upload = new JButton("Сохранить в облако →");
        upload.setToolTipText("Новый проект — создаёт запись в облаке; уже привязанный — обновляет,"
                + " если никто не сохранил более новую версию с момента скачивания");
        upload.addActionListener(e -> upload());
        mid.add(upload);
        mid.add(Box.createVerticalStrut(8));
        JButton download = new JButton("← Скачать копию");
        download.addActionListener(e -> download());
        mid.add(download);
        mid.add(Box.createVerticalGlue());

        JPanel lists = new JPanel(new GridLayout(1, 3, 8, 0));
        lists.add(left);
        lists.add(mid);
        lists.add(right);
        content.add(lists, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        status.setForeground(Palette.MUTED);
        bottom.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteCloud = new JButton("Удалить из облака");
        deleteCloud.addActionListener(e -> deleteCloud());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(deleteCloud);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        refreshCloud();
    }

    private String token() {
        return settings.getSettings().getAuthToken();
    }

    private String localTitle(Project p) {
        return p.getName();
    }

    private String localMeta(Project p) {
        String base = "сцен: " + p.getScenes().size();
        if (p.getCloudId() == null) {
            return base + " · не в облаке";
        }
        ProjectArchiveClient.ProjectSummaryDto cloud = cloudById.get(p.getCloudId());
        if (cloud != null && p.getCloudRevision() != null && cloud.revision() > p.getCloudRevision()) {
            return base + " · в облаке (доступна новая версия " + cloud.revision() + ")";
        }
        return base + " · в облаке, ревизия " + p.getCloudRevision();
    }

    private void refreshCloud() {
        status.setText("Загрузка списка…");
        new SwingWorker<List<ProjectArchiveClient.ProjectSummaryDto>, Void>() {
            @Override
            protected List<ProjectArchiveClient.ProjectSummaryDto> doInBackground() throws Exception {
                return client.list(token());
            }

            @Override
            protected void done() {
                try {
                    List<ProjectArchiveClient.ProjectSummaryDto> items = get();
                    cloudModel.clear();
                    cloudById.clear();
                    for (ProjectArchiveClient.ProjectSummaryDto d : items) {
                        cloudModel.addElement(d);
                        cloudById.put(d.id(), d);
                    }
                    status.setText(items.isEmpty() ? "В облаке пока нет проектов." : " ");
                    localList.repaint();
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private void upload() {
        Project selected = localList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите локальный проект слева.");
            return;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(selected);
        } catch (Exception ex) {
            status.setText("Не удалось сериализовать проект: " + rootMessage(ex));
            return;
        }
        boolean isUpdate = selected.getCloudId() != null;
        String name = selected.getName();
        status.setText(isUpdate ? "Сохранение…" : "Загрузка…");
        new SwingWorker<ProjectArchiveClient.ProjectDto, Void>() {
            @Override
            protected ProjectArchiveClient.ProjectDto doInBackground() throws Exception {
                return isUpdate
                        ? client.update(token(), selected.getCloudId(), selected.getCloudRevision(), name, json)
                        : client.create(token(), name, json);
            }

            @Override
            protected void done() {
                try {
                    ProjectArchiveClient.ProjectDto saved = get();
                    if (isUpdate) {
                        model.updateProjectCloudRevision(selected, saved.revision());
                    } else {
                        model.linkProjectToCloud(selected, saved.id(), saved.revision());
                    }
                    status.setText("Сохранено в облако (ревизия " + saved.revision() + ").");
                    localList.repaint();
                    refreshCloud();
                } catch (Exception ex) {
                    if (unwrapConflict(ex) instanceof ProjectArchiveClient.ProjectConflictException conflict) {
                        handleConflict(selected, conflict);
                    } else {
                        status.setText(rootMessage(ex));
                    }
                }
            }
        }.execute();
    }

    /** Принудительный контроль версий: единственный выход из конфликта — скачать
     *  актуальную версию как ОТДЕЛЬНУЮ копию и вручную перенести свои правки. Кнопки
     *  "всё равно перезаписать" намеренно нет — иначе контроль версий не был бы
     *  принудительным. */
    private void handleConflict(Project local, ProjectArchiveClient.ProjectConflictException conflict) {
        status.setText("Отклонено: в облаке уже ревизия " + conflict.currentRevision()
                + ", ваша копия основана на более старой.");
        int choice = JOptionPane.showConfirmDialog(this,
                "Кто-то уже сохранил более новую версию «" + local.getName() + "» (ревизия "
                        + conflict.currentRevision() + ") с тех пор, как вы её скачали.\n\n"
                        + "Сохранить поверх нельзя — так вы стёрли бы чужие правки. Скачать актуальную"
                        + " версию как отдельную локальную копию, чтобы перенести свои изменения вручную?",
                "Конфликт версий", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            downloadById(local.getCloudId());
        }
    }

    private void download() {
        ProjectArchiveClient.ProjectSummaryDto selected = cloudList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите облачный проект справа.");
            return;
        }
        downloadById(selected.id());
    }

    private void downloadById(String cloudId) {
        status.setText("Скачивание…");
        new SwingWorker<Project, Void>() {
            @Override
            protected Project doInBackground() throws Exception {
                ProjectArchiveClient.ProjectDto dto = client.get(token(), cloudId);
                Project p = MAPPER.readValue(dto.projectJson(), Project.class);
                p.setCloudId(dto.id());
                p.setCloudRevision(dto.revision());
                return p;
            }

            @Override
            protected void done() {
                try {
                    Project imported = model.importProject(get());
                    localModel.addElement(imported);
                    status.setText("Скачано как новая копия «" + imported.getName() + "».");
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private void showHistory() {
        ProjectArchiveClient.ProjectSummaryDto selected = cloudList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите облачный проект справа.");
            return;
        }
        new ProjectHistoryDialog(this, client, token(), selected.id(), selected.name(), model,
                this::refreshCloud).setVisible(true);
    }

    private void deleteCloud() {
        ProjectArchiveClient.ProjectSummaryDto selected = cloudList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите облачный проект справа.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить «" + selected.name() + "» из облака вместе со всей историей версий?"
                        + " Локальные копии не пострадают.\nУдалить может только автор или админ.",
                "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        status.setText("Удаление…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                client.delete(token(), selected.id());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Удалено из облака.");
                    refreshCloud();
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private static Throwable unwrapConflict(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof ProjectArchiveClient.ProjectConflictException) {
                return t;
            }
            t = t.getCause();
        }
        return ex;
    }

    static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
