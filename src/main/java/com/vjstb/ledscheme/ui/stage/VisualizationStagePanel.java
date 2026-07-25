package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.CanvasEditorPanel;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.PixelGridRenderer;
import com.vjstb.ledscheme.ui.ResolumePresetExporter;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Этап «Генерация масок»: канвасы — виртуальные выходные кадры для компоновки
 * контента, в которых экраны размещаются на пиксельных позициях (как Advanced
 * Output в Resolume). Отсюда же — экспорт тестовых масок (экраны + канвасы целиком)
 * и отдельными кнопками — пресеты под конкретный медиасервер. Раскладка сцены по
 * координатам (мм) переехала в мини-превью прерига (Сетап) и корнер-виджет
 * Питание/Сигнал — здесь она больше не дублируется.
 */
public class VisualizationStagePanel extends JPanel {

    private static final int SIDE_WIDTH = 300;

    private final AppModel model;
    private final CanvasEditorPanel canvasEditor;

    private File chosenFolder;
    private final JTextField folderField = new JTextField();
    private final JComboBox<ContentCanvas> canvasCombo = new JComboBox<>();
    private final JComboBox<Screen> addScreenCombo = new JComboBox<>();
    private ContentCanvas currentCanvas;

    public VisualizationStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.canvasEditor = new CanvasEditorPanel(model, settings);
        canvasEditor.setOnChanged(this::refreshCanvasSide);

        setLayout(new BorderLayout());

        JPanel canvasSide = buildCanvasSide();
        JScrollPane canvasSideScroll = new JScrollPane(canvasSide);
        canvasSideScroll.setBorder(null);
        canvasSideScroll.setMinimumSize(new Dimension(180, 100));
        JSplitPane canvasSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasEditor, canvasSideScroll);
        canvasSplit.setContinuousLayout(true);
        canvasSplit.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "visualization.canvasSplit", canvasSplit, 1.0 - (SIDE_WIDTH + 20) / 1360.0);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new ContextBar(model, false), BorderLayout.NORTH);
        add(top, BorderLayout.NORTH);
        add(canvasSplit, BorderLayout.CENTER);

        model.addListener(this::refreshCanvasSide);
        refreshCanvasSide();
    }

    // ---- канвасы ----

    private JPanel buildCanvasSide() {
        JPanel body = UiKit.vboxFixedWidth(SIDE_WIDTH);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        canvasCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ContentCanvas c) {
                    setText(c.getName() + " (" + c.getWidthPx() + "×" + c.getHeightPx() + ")");
                }
                return this;
            }
        });
        canvasCombo.addActionListener(e -> {
            currentCanvas = (ContentCanvas) canvasCombo.getSelectedItem();
            canvasEditor.setCanvas(currentCanvas);
        });
        body.add(UiKit.section("Канвас", canvasCombo));
        body.add(UiKit.vgap(8));

        JPanel newForm = new JPanel(new GridLayout(0, 2, 4, 4));
        JTextField nameField = new JTextField("Резолюм 1080p");
        JSpinner wSpin = new JSpinner(new SpinnerNumberModel(1920, 1, 16384, 1));
        JSpinner hSpin = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 1));
        newForm.add(new JLabel("Название"));
        newForm.add(nameField);
        newForm.add(new JLabel("Ширина, px"));
        newForm.add(wSpin);
        newForm.add(new JLabel("Высота, px"));
        newForm.add(hSpin);
        JButton addCanvasBtn = new JButton("+ Новый канвас");
        addCanvasBtn.addActionListener(e -> {
            try {
                ContentCanvas c = model.addCanvas(nameField.getText().trim().isEmpty()
                                ? "Канвас" : nameField.getText().trim(),
                        (Integer) wSpin.getValue(), (Integer) hSpin.getValue());
                currentCanvas = c;
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton resizeBtn = new JButton("Применить размер/имя к выбранному");
        resizeBtn.addActionListener(e -> {
            if (currentCanvas == null) return;
            model.updateCanvas(currentCanvas, nameField.getText().trim(),
                    (Integer) wSpin.getValue(), (Integer) hSpin.getValue());
        });
        JButton deleteCanvasBtn = new JButton("Удалить канвас");
        deleteCanvasBtn.addActionListener(e -> {
            if (currentCanvas != null && JOptionPane.showConfirmDialog(this, "Удалить канвас «"
                    + currentCanvas.getName() + "»?", "Подтверждение", JOptionPane.OK_CANCEL_OPTION)
                    == JOptionPane.OK_OPTION) {
                model.deleteCanvas(currentCanvas);
                currentCanvas = null;
            }
        });
        body.add(newForm);
        body.add(UiKit.vgap(6));
        body.add(addCanvasBtn);
        body.add(UiKit.vgap(4));
        body.add(resizeBtn);
        body.add(UiKit.vgap(4));
        body.add(deleteCanvasBtn);

        body.add(UiKit.vgap(14));
        addScreenCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Screen s) {
                    setText(s.getName());
                }
                return this;
            }
        });
        JButton addScreenBtn = new JButton("+ Разместить экран в канвасе");
        addScreenBtn.addActionListener(e -> {
            Screen sel = (Screen) addScreenCombo.getSelectedItem();
            if (currentCanvas != null && sel != null) {
                model.addScreenToCanvas(currentCanvas, sel.getId(), 0, 0);
            }
        });
        body.add(UiKit.section("Добавить экран в канвас", addScreenCombo));
        body.add(UiKit.vgap(6));
        body.add(addScreenBtn);

        JButton removeSelectedBtn = new JButton("Убрать выбранный экран из канваса");
        removeSelectedBtn.addActionListener(e -> canvasEditor.deleteSelected());
        body.add(UiKit.vgap(10));
        body.add(removeSelectedBtn);

        body.add(UiKit.vgap(10));
        body.add(UiKit.muted("<html>Канвас — виртуальный выходной кадр (как Advanced Output в Resolume)."
                + " Разрешение настраивается как разрешение сигнала; экраны внутри перетаскиваются мышью"
                + " (с зажатым Shift — прилипание к краям других экранов, краям канваса и, опционально,"
                + " к центру — см. «Персонализация») — так задаётся компоновка контента.</html>"));

        body.add(UiKit.vgap(14));
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderField.setEditable(false);
        JButton chooseFolderBtn = new JButton("Папка…");
        chooseFolderBtn.addActionListener(e -> chooseFolder());
        folderRow.add(folderField, BorderLayout.CENTER);
        folderRow.add(chooseFolderBtn, BorderLayout.EAST);
        body.add(UiKit.section("Папка для масок/пресетов", folderRow));

        body.add(UiKit.vgap(10));
        JButton exportMasks = new JButton("Экспорт масок (экраны + канвасы)…");
        exportMasks.addActionListener(e -> exportMasks());
        body.add(exportMasks);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Тестовая маска (Pixel Grid) для каждого экрана сцены и для каждого канваса"
                + " целиком — чередующаяся заливка, пиксельная сетка, номера кабинетов, подписи.</html>"));

        body.add(UiKit.vgap(14));
        JButton exportResolume = new JButton("Экспорт под Resolume…");
        exportResolume.addActionListener(e -> exportResolumePreset());
        body.add(exportResolume);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted(
                "<html>XML «Screen Setup» (Advanced Output) — по одному файлу на канвас текущей сцены, готов"
                        + " к прямому импорту в Resolume. Маска НЕ генерируется этой кнопкой.</html>"));

        body.add(UiKit.vgap(10));
        JButton exportAfterEffects = new JButton("Экспорт под After Effects… (скоро)");
        exportAfterEffects.setEnabled(false);
        exportAfterEffects.setToolTipText("Появится после получения образца формата от пользователя");
        body.add(exportAfterEffects);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted(
                "<html>Заготовлено, но пока неактивно — пришлите образец проекта/скрипта After Effects,"
                        + " и кнопка будет доделана и включена.</html>"));
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void refreshCanvasSide() {
        DefaultComboBoxModel<ContentCanvas> cm = new DefaultComboBoxModel<>();
        for (ContentCanvas c : model.canvasesForCurrentScene()) {
            cm.addElement(c);
        }
        canvasCombo.setModel(cm);
        if (currentCanvas != null && model.canvasesForCurrentScene().contains(currentCanvas)) {
            canvasCombo.setSelectedItem(currentCanvas);
        } else {
            currentCanvas = cm.getSize() > 0 ? cm.getElementAt(0) : null;
            canvasCombo.setSelectedItem(currentCanvas);
        }
        canvasEditor.setCanvas(currentCanvas);

        DefaultComboBoxModel<Screen> sm = new DefaultComboBoxModel<>();
        Scene scene = model.getCurrentScene();
        if (scene != null) {
            for (Screen s : scene.getScreens()) {
                sm.addElement(s);
            }
        }
        addScreenCombo.setModel(sm);

        if (chosenFolder == null) {
            Project project = model.getCurrentProject();
            folderField.setText(project != null
                    ? OutputPaths.defaultFolder(project, model.getCurrentScene()).getAbsolutePath()
                    : "(сначала выберите проект)");
        }
    }

    private File resolveFolder() {
        if (chosenFolder != null) {
            return chosenFolder;
        }
        Project project = model.getCurrentProject();
        return project != null ? OutputPaths.defaultFolder(project, model.getCurrentScene()) : null;
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку");
        if (chosenFolder != null) {
            fc.setCurrentDirectory(chosenFolder);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            chosenFolder = fc.getSelectedFile();
            folderField.setText(chosenFolder.getAbsolutePath());
        }
    }

    private record NamedImage(String filename, BufferedImage image) {
    }

    /** Маски: по одной на каждый экран ВСЕХ сцен проекта + по одной на каждый канвас
     *  ВСЕХ сцен проекта — единая кнопка, как и раньше на этапе «Вывод». Сначала
     *  показываем превью (нельзя экспортировать то, что нельзя сначала увидеть в
     *  приложении) — запись на диск происходит только по кнопке «Сохранить» в
     *  диалоге предпросмотра. */
    private void exportMasks() {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите проект", "Нет проекта", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<NamedImage> images = new ArrayList<>();
        try {
            for (Scene scene : project.getScenes()) {
                for (Screen scr : scene.getScreens()) {
                    com.vjstb.ledscheme.model.CabinetType type = model.typeOf(scr);
                    BufferedImage img = PixelGridRenderer.renderMask(scr, type, model.getWorkspace());
                    String fname = OutputPaths.sanitize(scene.getName()) + "_" + OutputPaths.sanitize(scr.getName())
                            + "_Маска.png";
                    images.add(new NamedImage(fname, img));
                }
                for (ContentCanvas c : scene.getCanvases()) {
                    BufferedImage img = PixelGridRenderer.renderCanvasMask(c, model);
                    String fname = OutputPaths.sanitize(scene.getName()) + "_канвас_" + OutputPaths.sanitize(c.getName()) + ".png";
                    images.add(new NamedImage(fname, img));
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования масок: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        showMaskPreviewDialog("Предпросмотр масок (экраны + канвасы)", images);
    }

    /** Отдельная кнопка-пресет (не входит в общий пакет и НЕ генерирует маску —
     *  вместо этого пишет XML "Screen Setup" (Advanced Output), который Resolume
     *  умеет импортировать напрямую: один &lt;Screen&gt; на канвас, один
     *  &lt;Slice&gt; ("слой", имя = имя экрана) на каждый размещённый в канвасе
     *  экран, координаты — пиксельные границы экрана в системе координат канваса
     *  (см. {@link ResolumePresetExporter}, разобран по образцу реального файла,
     *  присланного пользователем). Пишет по одному .xml на каждый канвас ТЕКУЩЕЙ
     *  сцены сразу в выбранную папку — файл текстовый, показывать превью-миниатюру
     *  как для масок не имеет смысла. */
    private void exportResolumePreset() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (scene.getCanvases().isEmpty()) {
            JOptionPane.showMessageDialog(this, "В сцене нет ни одного канваса", "Нечего экспортировать",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        File folder = resolveFolder();
        if (folder == null) {
            JOptionPane.showMessageDialog(this, "Не удалось определить папку сохранения — выберите её вручную",
                    "Нет папки", JOptionPane.WARNING_MESSAGE);
            return;
        }
        folder.mkdirs();
        int count = 0;
        try {
            for (ContentCanvas c : scene.getCanvases()) {
                String xml = ResolumePresetExporter.buildXml(c, scene, model);
                String fname = "Resolume_" + OutputPaths.sanitize(scene.getName()) + "_"
                        + OutputPaths.sanitize(c.getName()) + ".xml";
                java.nio.file.Files.writeString(new File(folder, fname).toPath(), xml,
                        java.nio.charset.StandardCharsets.UTF_8);
                count++;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования пресета: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Готово.\nФайлов Resolume Screen Setup сохранено: " + count + "\n\nОткрыть папку?",
                "Пресет Resolume сформирован", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(folder);
                }
            } catch (Exception ignored) {
                // не критично
            }
        }
    }

    /** Модальный диалог предпросмотра: миниатюры всех изображений, которые БУДУТ
     *  сохранены, и кнопка «Сохранить всё», которая пишет их на диск только по
     *  явному подтверждению — вместо того чтобы сразу писать файлы вслепую. */
    private void showMaskPreviewDialog(String title, List<NamedImage> images) {
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Нечего показывать — нет ни экранов, ни канвасов.",
                    "Пусто", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File folder = resolveFolder();
        if (folder == null) {
            // new File(null, name) незаметно превращается в ОТНОСИТЕЛЬНЫЙ путь (от
            // рабочей папки процесса) вместо явной ошибки — если та не пишется
            // (например, папка установки), ImageIO падает с нечитаемым "Can't
            // create an ImageOutputStream!" без объяснения причины. Явно и заранее
            // требуем выбранную папку вместо того чтобы разбираться с этим позже.
            JOptionPane.showMessageDialog(this, "Не удалось определить папку сохранения — выберите её вручную",
                    "Нет папки", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), title, JDialog.ModalityType.APPLICATION_MODAL);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (NamedImage ni : images) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            Image thumb = ni.image().getScaledInstance(220, -1, Image.SCALE_SMOOTH);
            JLabel thumbLabel = new JLabel(new ImageIcon(thumb));
            thumbLabel.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
            JLabel nameLabel = new JLabel("<html>" + ni.filename() + "<br><span style='color:#8b949e'>"
                    + ni.image().getWidth() + "×" + ni.image().getHeight() + " px</span></html>");
            row.add(thumbLabel, BorderLayout.WEST);
            row.add(nameLabel, BorderLayout.CENTER);
            list.add(row);
            list.add(Box.createVerticalStrut(8));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(560, 560));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel folderLabel = new JLabel("Папка: " + (folder != null ? folder.getAbsolutePath() : "—"));
        folderLabel.setForeground(Palette.MUTED);
        folderLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

        JButton save = new JButton("Сохранить всё в папку (" + images.size() + ")");
        save.addActionListener(e -> {
            try {
                for (NamedImage ni : images) {
                    PixelGridRenderer.writePng(ni.image(), new File(folder, ni.filename()));
                }
                dlg.dispose();
                JOptionPane.showMessageDialog(this, "Сохранено файлов: " + images.size(), "Готово",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Ошибка сохранения: " + ex.getMessage(), "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dlg.dispose());
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout());
        content.add(folderLabel, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
}
