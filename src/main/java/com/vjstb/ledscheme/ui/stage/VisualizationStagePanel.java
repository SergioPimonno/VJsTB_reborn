package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.AfterEffectsJsxWriter;
import com.vjstb.ledscheme.ui.CanvasEditorPanel;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.PixelGridRenderer;
import com.vjstb.ledscheme.ui.PreferencesDialog;
import com.vjstb.ledscheme.ui.ResolumePresetExporter;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * Этап «Генерация масок»: канвасы — виртуальные выходные кадры для компоновки
 * контента, в которых экраны размещаются на пиксельных позициях (как Advanced
 * Output в Resolume). Таблица «гридов» ниже холста (в духе референсного PixL) —
 * по одной строке на каждый РАЗМЕЩЁННЫЙ на текущем канвасе экран (не отдельная
 * сущность и без выбора типа кабинета — тип уже известен из самого экрана,
 * заданного на «Сетапе»); каждая строка настраивает СВОЙ грид независимо: имя,
 * цвет чек-борда и какие элементы маски рисовать. Отсюда же — экспорт тестовых
 * масок (экраны + канвасы целиком) и отдельными кнопками — пресеты под конкретный
 * медиасервер. Раскладка сцены по координатам (мм) переехала в мини-превью
 * прерига (Сетап) и корнер-виджет Питание/Сигнал — здесь она больше не дублируется.
 */
public class VisualizationStagePanel extends JPanel {

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final CanvasEditorPanel canvasEditor;
    private final PlacementsTableModel placementsTableModel = new PlacementsTableModel();
    private final JTable placementsTable = new JTable(placementsTableModel);

    private File chosenFolder;
    private final JTextField folderField = new JTextField();
    private final JComboBox<ContentCanvas> canvasCombo = new JComboBox<>();
    private final JComboBox<Screen> addScreenCombo = new JComboBox<>();

    private final JCheckBox largeGridNamesCheck = new JCheckBox("Крупные имена гридов");
    private final JCheckBox dropShadowCheck = new JCheckBox("Тень текста");
    private final JButton textColorBtn = new JButton("Цвет текста…");
    private Color currentTextColor = Color.WHITE;
    /** Подавляет обратные вызовы в AppModel при программной синхронизации контролов
     *  глобальных настроек маски под смену выбранного канваса (см. syncCanvasMaskSettingsControls) —
     *  тот же приём, что раньше использовался для комбобокса цвета маски экрана. */
    private boolean syncingCanvasMaskControls;

    private ContentCanvas currentCanvas;

    public VisualizationStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.canvasEditor = new CanvasEditorPanel(model, settings);
        canvasEditor.setOnChanged(this::refreshCanvasSide);

        setLayout(new BorderLayout());

        JPanel canvasSide = buildCanvasSide();
        JSplitPane canvasSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvasEditor, canvasSide);
        canvasSplit.setContinuousLayout(true);
        canvasSplit.setResizeWeight(0.62);
        UiKit.persistentDivider(settings, "visualization.canvasSplitV", canvasSplit, 0.62);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new ContextBar(model, false), BorderLayout.NORTH);
        add(top, BorderLayout.NORTH);
        add(canvasSplit, BorderLayout.CENTER);

        model.addListener(this::refreshCanvasSide);
        refreshCanvasSide();
    }

    // ---- канвасы ----

    private JPanel buildCanvasSide() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        body.add(controls, BorderLayout.NORTH);
        body.add(buildPlacementsTablePanel(), BorderLayout.CENTER);
        body.add(buildExportPanel(), BorderLayout.SOUTH);

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
            syncCanvasMaskSettingsControls();
            placementsTableModel.fireTableDataChanged();
        });

        JPanel canvasRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        canvasRow.add(new JLabel("Канвас:"));
        canvasRow.add(canvasCombo);
        JTextField nameField = new JTextField("Резолюм 1080p", 14);
        JSpinner wSpin = new JSpinner(new SpinnerNumberModel(1920, 1, 16384, 1));
        JSpinner hSpin = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 1));
        com.vjstb.ledscheme.ui.MathFields.enableExpressions(wSpin);
        com.vjstb.ledscheme.ui.MathFields.enableExpressions(hSpin);
        canvasRow.add(new JLabel("Имя"));
        canvasRow.add(nameField);
        canvasRow.add(new JLabel("Ширина, px"));
        canvasRow.add(wSpin);
        canvasRow.add(new JLabel("Высота, px"));
        canvasRow.add(hSpin);
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
        JButton resizeBtn = new JButton("Применить размер/имя");
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
        canvasRow.add(addCanvasBtn);
        canvasRow.add(resizeBtn);
        canvasRow.add(deleteCanvasBtn);
        controls.add(canvasRow);

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
        JButton removeSelectedBtn = new JButton("Убрать выбранный грид (строку) из канваса");
        removeSelectedBtn.addActionListener(e -> {
            int row = placementsTable.getSelectedRow();
            if (currentCanvas == null || row < 0 || row >= currentCanvas.getPlacements().size()) {
                return;
            }
            CanvasPlacement pl = currentCanvas.getPlacements().get(row);
            model.removePlacement(currentCanvas, pl.getId());
        });
        JPanel placeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        placeRow.add(new JLabel("Добавить экран:"));
        placeRow.add(addScreenCombo);
        placeRow.add(addScreenBtn);
        placeRow.add(removeSelectedBtn);
        controls.add(placeRow);

        textColorBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Цвет имени грида", currentTextColor);
            if (chosen != null) {
                currentTextColor = chosen;
                pushCanvasMaskSettings();
            }
        });
        largeGridNamesCheck.addActionListener(e -> pushCanvasMaskSettings());
        dropShadowCheck.addActionListener(e -> pushCanvasMaskSettings());
        JButton logoPrefsBtn = new JButton("Логотип маски (в «Предпочтениях»)…");
        logoPrefsBtn.addActionListener(e -> new PreferencesDialog(
                SwingUtilities.getWindowAncestor(this), settings).setVisible(true));
        JPanel maskGlobalsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        maskGlobalsRow.add(largeGridNamesCheck);
        maskGlobalsRow.add(dropShadowCheck);
        maskGlobalsRow.add(textColorBtn);
        maskGlobalsRow.add(logoPrefsBtn);
        controls.add(maskGlobalsRow);

        return body;
    }

    private JPanel buildPlacementsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        placementsTable.setRowHeight(22);
        placementsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        placementsTable.getColumnModel().getColumn(8)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(MaskColorPreset.values())));
        placementsTable.getColumnModel().getColumn(5)
                .setCellEditor(com.vjstb.ledscheme.ui.MathFields.integerCellEditor());
        placementsTable.getColumnModel().getColumn(6)
                .setCellEditor(com.vjstb.ledscheme.ui.MathFields.integerCellEditor());
        JScrollPane scroll = new JScrollPane(placementsTable);
        scroll.setPreferredSize(new Dimension(200, 180));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildExportPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(UiKit.vgap(4));
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderField.setEditable(false);
        JButton chooseFolderBtn = new JButton("Папка…");
        chooseFolderBtn.addActionListener(e -> chooseFolder());
        folderRow.add(folderField, BorderLayout.CENTER);
        folderRow.add(chooseFolderBtn, BorderLayout.EAST);
        body.add(UiKit.section("Папка для масок/пресетов", folderRow));

        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton exportMasks = new JButton("Экспорт масок (экраны + канвасы)…");
        exportMasks.addActionListener(e -> exportMasks());
        JButton exportResolume = new JButton("Экспорт под Resolume…");
        exportResolume.addActionListener(e -> exportResolumePreset());
        JButton exportAfterEffects = new JButton("Экспорт под After Effects…");
        exportAfterEffects.addActionListener(e -> exportAfterEffectsPreset());
        exportAfterEffects.setToolTipText("По .jsx-скрипту на каждый канвас текущей сцены — при запуске в AE"
                + " (File → Scripts → Run Script File) создаёт композицию размером с канвас и по слою на каждый"
                + " размещённый экран, footage слоя — PNG-маска этого экрана (сохраняется рядом со скриптом).");
        exportRow.add(exportMasks);
        exportRow.add(exportResolume);
        exportRow.add(exportAfterEffects);
        body.add(exportRow);

        return body;
    }

    private void pushCanvasMaskSettings() {
        if (syncingCanvasMaskControls || currentCanvas == null) {
            return;
        }
        model.updateCanvasMaskSettings(currentCanvas, largeGridNamesCheck.isSelected(),
                currentTextColor != null ? currentTextColor.getRGB() : null,
                dropShadowCheck.isSelected());
    }

    /** Подставляет в контролы глобальных настроек маски значения ВЫБРАННОГО канваса —
     *  без этого они всегда показывали бы состояние по умолчанию, даже если у канваса
     *  уже что-то настроено (тот же приём, что раньше был у mask-цвета экрана). */
    private void syncCanvasMaskSettingsControls() {
        syncingCanvasMaskControls = true;
        if (currentCanvas != null) {
            largeGridNamesCheck.setSelected(currentCanvas.isLargeGridNames());
            dropShadowCheck.setSelected(currentCanvas.isDropShadow());
            currentTextColor = currentCanvas.getTextColorRgb() != null
                    ? new Color(currentCanvas.getTextColorRgb()) : Color.WHITE;
        } else {
            largeGridNamesCheck.setSelected(false);
            dropShadowCheck.setSelected(false);
            currentTextColor = Color.WHITE;
        }
        syncingCanvasMaskControls = false;
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
        syncCanvasMaskSettingsControls();
        placementsTableModel.fireTableDataChanged();

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

    private Screen screenById(String id) {
        Scene scene = model.getCurrentScene();
        if (scene == null || id == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** Таблица «гридов» текущего канваса в духе референсного PixL — одна строка на
     *  каждый {@link CanvasPlacement} (уже размещённый на канвасе экран, см. class
     *  javadoc). Читает данные напрямую из currentCanvas.getPlacements() (без своего
     *  снимка), поэтому fireTableDataChanged() после любого внешнего изменения модели
     *  (см. refreshCanvasSide, подписан на model.addListener) — единственное, что
     *  нужно для актуальности; правки самой таблицы идут через AppModel-мутаторы,
     *  которые тоже вызывают model.addListener и приходят сюда тем же путём. */
    private final class PlacementsTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Экран", "Кабинет X,px", "Кабинет Y,px", "Колонн", "Строк",
                "X,px", "Y,px", "Имя", "Фон",
                "Сетка", "Растр", "Номера", "Круг", "Крест", "Угол", "Лого"
        };

        private List<CanvasPlacement> placements() {
            return currentCanvas != null ? currentCanvas.getPlacements() : List.of();
        }

        @Override
        public int getRowCount() {
            return placements().size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case 1, 2, 3, 4, 5, 6 -> Integer.class;
                case 8 -> MaskColorPreset.class;
                case 9, 10, 11, 12, 13, 14, 15 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col >= 5;
        }

        @Override
        public Object getValueAt(int row, int col) {
            CanvasPlacement pl = placements().get(row);
            Screen scr = screenById(pl.getScreenId());
            CabinetType type = scr != null ? model.typeOf(scr) : null;
            return switch (col) {
                case 0 -> scr != null ? scr.getName() : "?";
                case 1 -> type != null ? type.getResolutionWidth() : 0;
                case 2 -> type != null ? type.getResolutionHeight() : 0;
                case 3 -> scr != null ? scr.getCols() : 0;
                case 4 -> scr != null ? scr.getRows() : 0;
                case 5 -> pl.getX();
                case 6 -> pl.getY();
                case 7 -> pl.getName() != null ? pl.getName() : "";
                case 8 -> scr != null ? scr.getBackground() : MaskColorPreset.NORMAL;
                case 9 -> pl.isShowGrid();
                case 10 -> pl.isShowRaster();
                case 11 -> pl.isShowIds();
                case 12 -> pl.isShowCircle();
                case 13 -> pl.isShowCross();
                case 14 -> pl.isShowCorner();
                case 15 -> pl.isShowLogo();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            CanvasPlacement pl = placements().get(row);
            switch (col) {
                case 5 -> model.movePlacement(pl, (Integer) value, pl.getY());
                case 6 -> model.movePlacement(pl, pl.getX(), (Integer) value);
                case 7 -> {
                    String name = value == null || ((String) value).isBlank() ? null : (String) value;
                    model.updatePlacementMaskConfig(pl, name, pl.isShowGrid(), pl.isShowRaster(),
                            pl.isShowIds(), pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(), pl.isShowLogo());
                }
                case 8 -> {
                    // Цвет маски -- ОБЩИЙ для экрана (не per-placement, см. class-javadoc
                    // Screen#getBackground) -- пишем через экран, не через это размещение,
                    // чтобы то же самое значение сразу отразилось во ВСЕХ канвасах, где
                    // этот экран тоже размещён (см. AppModel.setMaskColor).
                    Screen scr = screenById(pl.getScreenId());
                    if (scr != null) {
                        model.setMaskColor(scr, (MaskColorPreset) value);
                    }
                }
                case 9 -> model.updatePlacementMaskConfig(pl, pl.getName(), (Boolean) value,
                        pl.isShowRaster(), pl.isShowIds(), pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(),
                        pl.isShowLogo());
                case 10 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        (Boolean) value, pl.isShowIds(), pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(),
                        pl.isShowLogo());
                case 11 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        pl.isShowRaster(), (Boolean) value, pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(),
                        pl.isShowLogo());
                case 12 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        pl.isShowRaster(), pl.isShowIds(), (Boolean) value, pl.isShowCross(), pl.isShowCorner(),
                        pl.isShowLogo());
                case 13 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        pl.isShowRaster(), pl.isShowIds(), pl.isShowCircle(), (Boolean) value, pl.isShowCorner(),
                        pl.isShowLogo());
                case 14 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        pl.isShowRaster(), pl.isShowIds(), pl.isShowCircle(), pl.isShowCross(), (Boolean) value,
                        pl.isShowLogo());
                case 15 -> model.updatePlacementMaskConfig(pl, pl.getName(), pl.isShowGrid(),
                        pl.isShowRaster(), pl.isShowIds(), pl.isShowCircle(), pl.isShowCross(), pl.isShowCorner(),
                        (Boolean) value);
                default -> { }
            }
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
                    CabinetType type = model.typeOf(scr);
                    BufferedImage img = PixelGridRenderer.renderMask(scr, type, model.getWorkspace(),
                            PixelGridRenderer.GridRenderOptions.defaultForScreen(scr));
                    String fname = OutputPaths.sanitize(scene.getName()) + "_" + OutputPaths.sanitize(scr.getName())
                            + "_Маска_" + img.getWidth() + "x" + img.getHeight() + ".png";
                    images.add(new NamedImage(fname, img));
                }
                for (ContentCanvas c : scene.getCanvases()) {
                    BufferedImage img = PixelGridRenderer.renderCanvasMask(c, model, settings);
                    String fname = OutputPaths.sanitize(scene.getName()) + "_канвас_" + OutputPaths.sanitize(c.getName())
                            + "_" + img.getWidth() + "x" + img.getHeight() + ".png";
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

    /** Экспорт под After Effects — по прямому запросу пользователя ("jsx скрипт, который
     *  еще и подставляет в качестве базовых слоев созданные маски. Пресет должен создавать
     *  композицию по канвасу"), см. {@link AfterEffectsJsxWriter}. По одному .jsx на канвас
     *  ТЕКУЩЕЙ сцены (тот же принцип, что {@link #exportResolumePreset()}), плюс сами
     *  PNG-маски экранов, на которые ссылается скрипт, — те же файлы/то же содержимое, что
     *  и «Экспорт масок» (перезаписываются свежими при каждом запуске, чтобы .jsx никогда
     *  не сослался на устаревшую картинку). */
    private void exportAfterEffectsPreset() {
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
        String sceneNameSanitized = OutputPaths.sanitize(scene.getName());
        int scriptCount = 0;
        int maskCount = 0;
        try {
            for (ContentCanvas c : scene.getCanvases()) {
                for (CanvasPlacement pl : c.getPlacements()) {
                    Screen scr = screenById(pl.getScreenId());
                    if (scr == null) {
                        continue;
                    }
                    CabinetType type = model.typeOf(scr);
                    BufferedImage img = PixelGridRenderer.renderMask(scr, type, model.getWorkspace(),
                            PixelGridRenderer.GridRenderOptions.defaultForScreen(scr));
                    String fname = AfterEffectsJsxWriter.maskFilename(sceneNameSanitized, scr, img.getWidth(),
                            img.getHeight());
                    javax.imageio.ImageIO.write(img, "png", new File(folder, fname));
                    maskCount++;
                }
                String jsx = AfterEffectsJsxWriter.buildJsx(c, scene, model, sceneNameSanitized);
                String jsxName = "AE_" + sceneNameSanitized + "_" + OutputPaths.sanitize(c.getName()) + ".jsx";
                java.nio.file.Files.writeString(new File(folder, jsxName).toPath(), jsx,
                        java.nio.charset.StandardCharsets.UTF_8);
                scriptCount++;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования пресета: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Готово.\nСкриптов .jsx сохранено: " + scriptCount + "\nМасок сохранено: " + maskCount
                        + "\n\nЗапустите .jsx через File → Scripts → Run Script File в After Effects.\n\n"
                        + "Открыть папку?",
                "Пресет After Effects сформирован", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
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
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
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
