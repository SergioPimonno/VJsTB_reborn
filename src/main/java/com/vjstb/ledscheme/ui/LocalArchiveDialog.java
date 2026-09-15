package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.store.LocalArchiveStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * «Архив проектов…» — локальный аналог {@link CloudProjectsDialog} (та же раскладка:
 * два списка + кнопки переноса посередине), но без сервера и без истории версий —
 * контроль версий/автосохранение для архива сознательно не ведутся (папка архива не
 * трогается ни одним {@code AppModel.changed()}). Слева — рабочие проекты этого
 * компьютера, справа — проекты, убранные в архив; «В архив →» перемещает выбранный
 * рабочий проект в папку архива и убирает его из рабочего списка, «← Извлечь»
 * возвращает архивный проект обратно в рабочий список (с тем же id) и удаляет файл
 * архива. Чтобы внести правки в заархивированный проект, его нужно сначала извлечь —
 * прямого редактирования "в архиве" не предусмотрено, как и договаривались.
 *
 * <p>Папка архива задаётся пользователем один раз (см. {@link SettingsManager#getArchiveFolder}) —
 * кнопка «Папка архива…» вверху окна позволяет выбрать/сменить её в любой момент.
 */
public class LocalArchiveDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppModel model;
    private final SettingsManager settings;
    private final LocalArchiveStore store = new LocalArchiveStore();

    private final DefaultListModel<Project> workingModel = new DefaultListModel<>();
    private final JList<Project> workingList = new JList<>(workingModel);
    private final DefaultListModel<Project> archivedModel = new DefaultListModel<>();
    private final JList<Project> archivedList = new JList<>(archivedModel);
    private final JLabel folderLabel = new JLabel();
    private final JLabel status = new JLabel(" ");

    public static void show(Window owner, AppModel model, SettingsManager settings) {
        new LocalArchiveDialog(owner, model, settings).setVisible(true);
    }

    private LocalArchiveDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Архив проектов", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.settings = settings;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton chooseFolder = new JButton("Папка архива…");
        chooseFolder.addActionListener(e -> chooseFolder());
        folderRow.add(chooseFolder);
        folderRow.add(folderLabel);
        content.add(folderRow, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Рабочие проекты"), BorderLayout.NORTH);
        workingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workingList.setCellRenderer(new NamedRenderer<Project>(Project::getName, this::meta));
        for (Project p : model.getProjects()) {
            workingModel.addElement(p);
        }
        JScrollPane workingScroll = new JScrollPane(workingList);
        workingScroll.setPreferredSize(new Dimension(260, 260));
        left.add(workingScroll, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JLabel("В архиве"), BorderLayout.NORTH);
        archivedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archivedList.setCellRenderer(new NamedRenderer<Project>(Project::getName, this::meta));
        JScrollPane archivedScroll = new JScrollPane(archivedList);
        archivedScroll.setPreferredSize(new Dimension(260, 260));
        right.add(archivedScroll, BorderLayout.CENTER);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.add(Box.createVerticalGlue());
        JButton toArchive = new JButton("В архив →");
        toArchive.setToolTipText("Убрать проект с рабочего стола в папку архива — для правок его нужно будет"
                + " сначала извлечь обратно");
        toArchive.addActionListener(e -> archiveSelected());
        mid.add(toArchive);
        mid.add(Box.createVerticalStrut(8));
        JButton fromArchive = new JButton("← Извлечь");
        fromArchive.setToolTipText("Вернуть проект из архива в рабочий список для редактирования");
        fromArchive.addActionListener(e -> restoreSelected());
        mid.add(fromArchive);
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
        JButton deleteArchived = new JButton("Удалить из архива навсегда");
        deleteArchived.addActionListener(e -> deleteSelected());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(deleteArchived);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        refreshFolderLabel();
        refreshArchivedList();
    }

    private String meta(Project p) {
        return "сцен: " + p.getScenes().size() + " · изменён: "
                + Instant.ofEpochMilli(p.getUpdatedAt()).atZone(ZoneId.systemDefault()).format(DATE_FMT);
    }

    private File archiveDir() {
        String path = settings.getArchiveFolder();
        return path != null && !path.isBlank() ? new File(path) : null;
    }

    private void refreshFolderLabel() {
        File dir = archiveDir();
        folderLabel.setText(dir != null ? dir.getAbsolutePath() : "не выбрана");
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку для архива проектов");
        File current = archiveDir();
        if (current != null) {
            fc.setCurrentDirectory(current);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            settings.setArchiveFolder(fc.getSelectedFile().getAbsolutePath());
            refreshFolderLabel();
            refreshArchivedList();
        }
    }

    private void refreshArchivedList() {
        archivedModel.clear();
        File dir = archiveDir();
        if (dir == null) {
            return;
        }
        for (Project p : store.list(dir)) {
            archivedModel.addElement(p);
        }
    }

    private boolean requireFolder() {
        if (archiveDir() != null) {
            return true;
        }
        status.setText("Сначала выберите папку архива.");
        return false;
    }

    private void archiveSelected() {
        Project selected = workingList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите рабочий проект слева.");
            return;
        }
        if (!requireFolder()) {
            return;
        }
        try {
            store.save(archiveDir(), selected);
        } catch (RuntimeException ex) {
            status.setText(CloudProjectsDialog.rootMessage(ex));
            return;
        }
        model.removeProjectForArchive(selected);
        workingModel.removeElement(selected);
        archivedModel.addElement(selected);
        status.setText("«" + selected.getName() + "» перемещён в архив.");
    }

    private void restoreSelected() {
        Project selected = archivedList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите архивный проект справа.");
            return;
        }
        if (!requireFolder()) {
            return;
        }
        model.restoreProjectFromArchive(selected);
        try {
            store.delete(archiveDir(), selected.getId());
        } catch (RuntimeException ex) {
            status.setText("Проект возвращён в работу, но файл архива не удалось удалить: "
                    + CloudProjectsDialog.rootMessage(ex));
            archivedModel.removeElement(selected);
            workingModel.addElement(selected);
            return;
        }
        archivedModel.removeElement(selected);
        workingModel.addElement(selected);
        status.setText("«" + selected.getName() + "» возвращён в рабочий список.");
    }

    private void deleteSelected() {
        Project selected = archivedList.getSelectedValue();
        if (selected == null) {
            status.setText("Выберите архивный проект справа.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить «" + selected.getName() + "» из архива навсегда? Это действие необратимо.",
                "Подтверждение", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            store.delete(archiveDir(), selected.getId());
        } catch (RuntimeException ex) {
            status.setText(CloudProjectsDialog.rootMessage(ex));
            return;
        }
        archivedModel.removeElement(selected);
        status.setText("Удалено из архива.");
    }
}
