package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.MaskColorPreset;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.CanvasFit;
import com.vjstb.ledscheme.service.MaskGeometry;
import com.vjstb.ledscheme.service.MaskLimits;
import com.vjstb.ledscheme.ui.AfterEffectsJsxWriter;
import com.vjstb.ledscheme.ui.CanvasEditorPanel;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.MaskCustomColorsDialog;
import com.vjstb.ledscheme.ui.MaskExportSet;
import com.vjstb.ledscheme.ui.MaskImage;
import com.vjstb.ledscheme.ui.MaskPreviewResolution;
import com.vjstb.ledscheme.ui.MaskPreviewViewer;
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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

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

    /** Предупреждение «канвас меньше размещённых в нём масок» (запрос пользователя 2026-09-30):
     *  под выбором канваса, скрыто, пока всё вмещается. См. {@link CanvasFit}. */
    private final JLabel canvasOverflowLabel = new JLabel();

    /** Предупреждение у спиннеров размера канваса: сторона ≥ 16384 (или отказ при &gt; 30000) —
     *  обновляется прямо при вводе (запрос 2026-09-30, решение D7, см. {@link MaskLimits}). */
    private final JLabel canvasSizeHintLabel = new JLabel();

    private ContentCanvas currentCanvas;

    public VisualizationStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.canvasEditor = new CanvasEditorPanel(model, settings);
        canvasEditor.setOnChanged(this::refreshCanvasSide);

        setLayout(new BorderLayout());

        JPanel canvasSide = buildCanvasSide();
        JPanel editorWithBar = new JPanel(new BorderLayout());
        editorWithBar.add(buildPreviewResolutionBar(), BorderLayout.NORTH);
        editorWithBar.add(canvasEditor, BorderLayout.CENTER);
        JSplitPane canvasSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorWithBar, canvasSide);
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

    /** Комбобокс «Разрешение превью» над холстом — пресеты как «Resolution» в After Effects
     *  (запрос 2026-09-30, решение D7, см. {@link MaskPreviewResolution}); значение — в
     *  профиле пользователя, холст перерисовывается через слушатель настроек. */
    private JPanel buildPreviewResolutionBar() {
        JComboBox<MaskPreviewResolution> combo = new JComboBox<>(MaskPreviewResolution.values());
        combo.setSelectedItem(MaskPreviewResolution.fromName(settings.activeProfile().getMaskPreviewResolution()));
        combo.setToolTipText("<html>Как «Resolution» в After Effects: «Авто» — маски рисуются в размере"
                + " отображения;<br>дробь — в доле реального разрешения (видно, что теряется при уменьшении)."
                + "<br>Превью одного экрана больше ~64 Мпикс автоматически понижается до следующей дроби."
                + "<br>На экспорт не влияет — файлы всегда пишутся в полном разрешении.</html>");
        combo.addActionListener(e -> {
            MaskPreviewResolution r = (MaskPreviewResolution) combo.getSelectedItem();
            if (r != null && !r.name().equals(settings.activeProfile().getMaskPreviewResolution())) {
                settings.setMaskPreviewResolution(r.name());
            }
        });
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.add(new JLabel("Разрешение превью:"));
        bar.add(combo);
        return bar;
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
        // 2026-09-30 (запрос «убрать ограничение 16k», решение D7): предел спиннеров — 30000
        // (MaskLimits.MAX_SIDE_PX, предел композиции After Effects) вместо 16384; сторона
        // ≥ 16384 — только предупреждение (строка ниже + подтверждение при создании/изменении).
        JSpinner wSpin = new JSpinner(new SpinnerNumberModel(1920, 1, MaskLimits.MAX_SIDE_PX, 1));
        JSpinner hSpin = new JSpinner(new SpinnerNumberModel(1080, 1, MaskLimits.MAX_SIDE_PX, 1));
        com.vjstb.ledscheme.ui.MathFields.enableExpressions(wSpin);
        com.vjstb.ledscheme.ui.MathFields.enableExpressions(hSpin);
        canvasSizeHintLabel.setForeground(Palette.WARN);
        canvasSizeHintLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        canvasSizeHintLabel.setAlignmentX(LEFT_ALIGNMENT);
        canvasSizeHintLabel.setVisible(false);
        installCanvasSizeHint(wSpin, hSpin);
        canvasRow.add(new JLabel("Имя"));
        canvasRow.add(nameField);
        canvasRow.add(new JLabel("Ширина, px"));
        canvasRow.add(wSpin);
        canvasRow.add(new JLabel("Высота, px"));
        canvasRow.add(hSpin);
        JButton addCanvasBtn = new JButton("+ Новый канвас");
        addCanvasBtn.addActionListener(e -> {
            int nw = (Integer) wSpin.getValue();
            int nh = (Integer) hSpin.getValue();
            if (MaskLimits.isLarge(nw, nh) && !MaskLimits.exceedsMax(nw, nh) && !confirmText("Большой канвас",
                    "Сторона канваса ≥ " + MaskLimits.WARN_SIDE_PX + " px:",
                    largeCanvasDetails(nw, nh), "Всё равно создать канвас?")) {
                return;
            }
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
            // Запрос 2026-09-30: если НОВЫЙ размер обрежет уже размещённые маски -- предупредить
            // до применения, а не после экспорта.
            Scene curScene = model.getCurrentScene();
            int nw = (Integer) wSpin.getValue();
            int nh = (Integer) hSpin.getValue();
            List<CanvasFit.Overflow> willCrop = CanvasFit.overflows(currentCanvas, nw, nh, curScene, model);
            // 2026-09-30 (D7): обрезка и сторона ≥ 16k -- ОДНО подтверждение, не два диалога подряд.
            // Только если размер реально меняется -- переименование уже большого канваса не спрашиваем.
            boolean large = MaskLimits.isLarge(nw, nh) && !MaskLimits.exceedsMax(nw, nh)
                    && (nw != currentCanvas.getWidthPx() || nh != currentCanvas.getHeightPx());
            if (!willCrop.isEmpty() || large) {
                StringBuilder details = new StringBuilder();
                if (!willCrop.isEmpty()) {
                    details.append("Маски выйдут за границы канваса и в его экспорте будут обрезаны:\n")
                            .append(CanvasFit.describe(willCrop).replaceAll("(?m)^", "    "));
                }
                if (large) {
                    if (details.length() > 0) {
                        details.append("\n\n");
                    }
                    details.append(largeCanvasDetails(nw, nh));
                }
                if (!confirmText(willCrop.isEmpty() ? "Большой канвас" : "Новый размер канваса обрежет маски",
                        "Новый размер канваса " + nw + "×" + nh + " px:", details.toString(),
                        "Всё равно применить размер?")) {
                    return;
                }
            }
            try {
                model.updateCanvas(currentCanvas, nameField.getText().trim(), nw, nh);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
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
        controls.add(canvasSizeHintLabel);

        canvasOverflowLabel.setForeground(Palette.WARN);
        canvasOverflowLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        canvasOverflowLabel.setAlignmentX(LEFT_ALIGNMENT);
        canvasOverflowLabel.setVisible(false);
        controls.add(canvasOverflowLabel);

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
        // Фон -- название пресета + два образца цветов пары (запрос 2026-09-30: у «Свои цвета…»
        // пара не видна по названию, а у пресетов полезно видеть, чем именно красится экран).
        placementsTable.getColumnModel().getColumn(8).setCellRenderer(new MaskColorCellRenderer());
        // Высота × -- целое; недоступно (серым), пока экран не «сетка».
        placementsTable.getColumnModel().getColumn(10)
                .setCellEditor(com.vjstb.ledscheme.ui.MathFields.integerCellEditor());
        placementsTable.getColumnModel().getColumn(10).setCellRenderer(new MeshMultiplierRenderer());
        placementsTable.getColumnModel().getColumn(9).setPreferredWidth(90);
        placementsTable.getColumnModel().getColumn(10).setPreferredWidth(70);
        placementsTable.getColumnModel().getColumn(12).setPreferredWidth(60);
        placementsTable.getColumnModel().getColumn(13).setPreferredWidth(90);
        placementsTable.getColumnModel().getColumn(0).setCellRenderer(new ScreenSizeWarningRenderer());
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
        JButton exportSelectedCanvas = new JButton("Экспорт текущего канваса…");
        exportSelectedCanvas.setToolTipText("Маска только ВЫБРАННОГО сейчас в списке выше канваса (плюс маски"
                + " экранов, размещённых именно на нём) — без остальных канвасов и сцен проекта, в отличие от"
                + " «Экспорт масок» (та выгружает всё сразу).");
        exportSelectedCanvas.addActionListener(e -> exportSelectedCanvasMask());
        JButton exportResolume = new JButton("Экспорт под Resolume…");
        exportResolume.addActionListener(e -> exportResolumePreset());
        JButton exportAfterEffects = new JButton("Экспорт под After Effects…");
        exportAfterEffects.addActionListener(e -> exportAfterEffectsPreset());
        exportAfterEffects.setToolTipText("По .jsx-скрипту на каждый канвас текущей сцены — при запуске в AE"
                + " (File → Scripts → Run Script File) создаёт композицию размером с канвас, в ней по прекомпозиции"
                + " на каждый экран (внутри — PNG-маска экрана) и guide-слои с маской пустот и разметкой"
                + " координат. Все PNG сохраняются рядом со скриптом.");
        exportRow.add(exportMasks);
        exportRow.add(exportSelectedCanvas);
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
        refreshOverflowWarning();

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
                    ? OutputPaths.defaultFolder(project, model.getCurrentScene(), settings).getAbsolutePath()
                    : "(сначала выберите проект)");
        }
    }

    /** Строка-предупреждение под выбором канваса: какие экраны выходят за его границы (см.
     *  {@link CanvasFit}). Обновляется по слушателю модели вместе со всем остальным. */
    private void refreshOverflowWarning() {
        Scene scene = model.getCurrentScene();
        List<CanvasFit.Overflow> over = currentCanvas != null && scene != null
                ? CanvasFit.overflows(currentCanvas, scene, model) : List.of();
        if (over.isEmpty()) {
            canvasOverflowLabel.setVisible(false);
            canvasOverflowLabel.setText("");
            return;
        }
        canvasOverflowLabel.setText("<html>⚠ Канвас меньше размещённых в нём масок — в экспорте канваса они"
                + " будут обрезаны: " + CanvasFit.describe(over).replace("\n", "; ") + "</html>");
        canvasOverflowLabel.setVisible(true);
    }

    /** Подписывает строку {@link #canvasSizeHintLabel} на ввод в спиннеры: читается сам текст
     *  поля (с выражениями, как в {@code MathFields}), а не только закоммиченное значение —
     *  иначе на 30001 (спиннер его не примет) пользователь не увидел бы, почему. */
    private void installCanvasSizeHint(JSpinner wSpin, JSpinner hSpin) {
        Runnable update = () -> {
            int w = typedValue(wSpin);
            int h = typedValue(hSpin);
            String hint = MaskLimits.canvasSizeHint(w, h);
            canvasSizeHintLabel.setText(hint != null ? "⚠ " + hint : "");
            canvasSizeHintLabel.setVisible(hint != null);
        };
        for (JSpinner s : new JSpinner[]{wSpin, hSpin}) {
            s.addChangeListener(e -> update.run());
            if (s.getEditor() instanceof JSpinner.DefaultEditor ed) {
                ed.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        SwingUtilities.invokeLater(update);
                    }

                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        SwingUtilities.invokeLater(update);
                    }

                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        SwingUtilities.invokeLater(update);
                    }
                });
            }
        }
    }

    /** Значение, набранное в поле спиннера (выражение или число с разделителями), иначе —
     *  текущее значение спиннера. */
    private static int typedValue(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor ed) {
            String text = ed.getTextField().getText().replaceAll("[\\s\\u00a0\\u202f]", "");
            Double v = com.vjstb.ledscheme.ui.MathExpr.tryEval(text);
            if (v != null && !v.isNaN() && !v.isInfinite()) {
                return (int) Math.round(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)));
            }
        }
        return (Integer) spinner.getValue();
    }

    private static String largeCanvasDetails(int w, int h) {
        return w + "×" + h + " px — сторона ≥ " + MaskLimits.WARN_SIDE_PX + " px. Маски такого размера экспортируются"
                + " (потоково, без ограничения 16k), но не все медиасерверы и видеокарты их откроют:"
                + " Resolume и многие плееры ограничены 16384 px на сторону.";
    }

    /** Подтверждение с прокручиваемым списком (предупреждения про обрезку масок). */
    private boolean confirmText(String title, String intro, String details, String question) {
        JTextArea area = new JTextArea(details, Math.min(12, details.split("\n").length + 1), 50);
        area.setEditable(false);
        area.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("<html><body style='width:420px'>" + intro + "</body></html>"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(new JLabel(question), BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Перед экспортом — ОДНО общее подтверждение со списками (запросы пользователя
     *  2026-09-30): маски, выходящие за границы экспортируемых канвасов ({@link CanvasFit}),
     *  маски/канвасы со стороной ≥ 16384 и маски экранов больше 30000, которые не будут
     *  записаны ({@link MaskExportSet}, решение D7). {@code true} — можно продолжать
     *  (предупреждать не о чем или пользователь согласился). */
    private boolean confirmExport(List<Scene> scenes, List<ContentCanvas> onlyThese, boolean includeSceneName,
                                  MaskExportSet set, String question) {
        String crop = CanvasFit.report(scenes, onlyThese, model, includeSceneName);
        return MaskExportSet.confirm(this, crop, set, question);
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
        /** 2026-09-30: колонки «Растр» больше нет (запрос пользователя — не используется);
         *  вместо неё «Плашка» (имя на плашке маски) и «Разрешение» (вторая строка той же
         *  плашки); «Экран-сетка»/«Высота ×» — свойство ЭКРАНА (маска выше в N раз, как цвет
         *  — одинаково во всех канвасах). «Сетка» — по-прежнему рамки кабинетов (showGrid),
         *  не путать с «Экран-сетка». */
        private final String[] columns = {
                "Экран", "Кабинет X,px", "Кабинет Y,px", "Колонн", "Строк",
                "X,px", "Y,px", "Имя", "Фон",
                "Экран-сетка", "Высота ×",
                "Сетка", "Плашка", "Разрешение", "Номера", "Круг", "Крест", "Угол", "Лого"
        };

        private List<CanvasPlacement> placements() {
            return currentCanvas != null ? currentCanvas.getPlacements() : List.of();
        }

        Screen screenAtRow(int row) {
            List<CanvasPlacement> list = placements();
            return row >= 0 && row < list.size() ? screenById(list.get(row).getScreenId()) : null;
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
                case 1, 2, 3, 4, 5, 6, 10 -> Integer.class;
                case 8 -> MaskColorPreset.class;
                case 9, 11, 12, 13, 14, 15, 16, 17, 18 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 10) {
                Screen scr = screenAtRow(row);
                return scr != null && scr.isMaskMesh();
            }
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
                case 9 -> scr != null && scr.isMaskMesh();
                case 10 -> scr != null ? scr.getMaskHeightMultiplier() : 2;
                case 11 -> pl.isShowGrid();
                case 12 -> pl.isShowNameLabel();
                case 13 -> pl.isShowResolution();
                case 14 -> pl.isShowIds();
                case 15 -> pl.isShowCircle();
                case 16 -> pl.isShowCross();
                case 17 -> pl.isShowCorner();
                case 18 -> pl.isShowLogo();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            CanvasPlacement pl = placements().get(row);
            Screen scr = screenById(pl.getScreenId());
            switch (col) {
                case 5 -> model.movePlacement(pl, (Integer) value, pl.getY());
                case 6 -> model.movePlacement(pl, pl.getX(), (Integer) value);
                case 7 -> {
                    String name = value == null || ((String) value).isBlank() ? null : (String) value;
                    model.updatePlacementMask(pl, p -> p.setName(name));
                }
                case 8 -> {
                    // Цвет маски -- ОБЩИЙ для экрана (не per-placement, см. class-javadoc
                    // Screen#getBackground) -- пишем через экран, не через это размещение,
                    // чтобы то же самое значение сразу отразилось во ВСЕХ канвасах, где
                    // этот экран тоже размещён (см. AppModel.setMaskColor).
                    if (scr == null) {
                        return;
                    }
                    if (value == MaskColorPreset.CUSTOM) {
                        // «Свои цвета…» -- диалог с двумя образцами; открываем после выхода из
                        // редактирования ячейки, не внутри editingStopped.
                        SwingUtilities.invokeLater(() -> pickCustomMaskColors(scr));
                    } else {
                        model.setMaskColor(scr, (MaskColorPreset) value);
                    }
                }
                case 9 -> {
                    if (scr != null) {
                        model.setMaskMesh(scr, (Boolean) value);
                    }
                }
                case 10 -> {
                    if (scr != null) {
                        model.setMaskHeightMultiplier(scr, Math.max(1, (Integer) value));
                    }
                }
                case 11 -> model.updatePlacementMask(pl, p -> p.setShowGrid((Boolean) value));
                case 12 -> model.updatePlacementMask(pl, p -> p.setShowNameLabel((Boolean) value));
                case 13 -> model.updatePlacementMask(pl, p -> p.setShowResolution((Boolean) value));
                case 14 -> model.updatePlacementMask(pl, p -> p.setShowIds((Boolean) value));
                case 15 -> model.updatePlacementMask(pl, p -> p.setShowCircle((Boolean) value));
                case 16 -> model.updatePlacementMask(pl, p -> p.setShowCross((Boolean) value));
                case 17 -> model.updatePlacementMask(pl, p -> p.setShowCorner((Boolean) value));
                case 18 -> model.updatePlacementMask(pl, p -> p.setShowLogo((Boolean) value));
                default -> { }
            }
        }
    }

    /** «Свои цвета…» для экрана: стартовая пара — ранее выбранная своя (если есть), иначе
     *  цвета, которыми экран красится сейчас. Отмена диалога ничего не меняет. */
    private void pickCustomMaskColors(Screen scr) {
        Color a = scr.getMaskColorA() != null ? new Color(scr.getMaskColorA()) : scr.maskColor(0);
        Color b = scr.getMaskColorB() != null ? new Color(scr.getMaskColorB()) : scr.maskColor(1);
        Color[] picked = MaskCustomColorsDialog.show(this, scr.getName(), a, b, settings);
        if (picked != null) {
            model.setMaskCustomColors(scr, picked[0], picked[1]);
        }
    }

    /** Ячейка «Фон»: название пресета и два образца пары цветов экрана (для «Свои цвета…» —
     *  именно его пара, см. {@link Screen#maskColor(int)}). */
    private final class MaskColorCellRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Screen scr = placementsTableModel.screenAtRow(row);
            Color c0 = scr != null ? scr.maskColor(0) : MaskColorPreset.NORMAL.color(0);
            Color c1 = scr != null ? scr.maskColor(1) : MaskColorPreset.NORMAL.color(1);
            setIcon(new Icon() {
                @Override
                public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                    g.setColor(c0);
                    g.fillRect(x, y, 10, 12);
                    g.setColor(c1);
                    g.fillRect(x + 10, y, 10, 12);
                    g.setColor(Palette.BORDER);
                    g.drawRect(x, y, 20, 12);
                }

                @Override
                public int getIconWidth() {
                    return 21;
                }

                @Override
                public int getIconHeight() {
                    return 13;
                }
            });
            return this;
        }
    }

    /** Ячейка «Экран»: значок и подсказка, если маска экрана (с множителем «сетки», через
     *  {@link MaskGeometry}) со стороной ≥ 16384 — предупреждение, &gt; 30000 — маска не будет
     *  экспортироваться. Запрос 2026-09-30, решение D7: размер экрана задаётся на «Сетапе», здесь
     *  его не запретить — только показать заранее, до нажатия «Экспорт». */
    private final class ScreenSizeWarningRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            // DefaultTableCellRenderer запоминает цвет из setForeground для ВСЕХ следующих ячеек —
            // сбрасываем, иначе после одной строки с предупреждением окрасились бы и остальные.
            setForeground(null);
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setToolTipText(null);
            Screen scr = placementsTableModel.screenAtRow(row);
            if (scr == null) {
                return this;
            }
            MaskGeometry geo = MaskGeometry.of(scr, model.typeOf(scr), model.getWorkspace());
            if (MaskLimits.exceedsMax(geo.width(), geo.height())) {
                setText("⛔ " + value);
                if (!isSelected) {
                    setForeground(new Color(0xf85149));
                }
                setToolTipText("Маска " + geo.sizeLabel() + " — сторона больше " + MaskLimits.MAX_SIDE_PX
                        + " px: при экспорте этот экран будет пропущен (предел After Effects).");
            } else if (MaskLimits.isLarge(geo.width(), geo.height())) {
                setText("⚠ " + value);
                if (!isSelected) {
                    setForeground(Palette.WARN);
                }
                setToolTipText("Маска " + geo.sizeLabel() + " — сторона ≥ " + MaskLimits.WARN_SIDE_PX
                        + " px: не все медиасерверы/GPU откроют такой файл.");
            }
            return this;
        }
    }

    /** «Высота ×»: серым, пока экран не «сетка» (редактировать нельзя). */
    private final class MeshMultiplierRenderer extends DefaultTableCellRenderer {
        MeshMultiplierRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            setForeground(null); // см. ScreenSizeWarningRenderer: иначе серый «залипал» на следующих строках
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected && !placementsTableModel.isCellEditable(row, column)) {
                setForeground(Palette.MUTED);
            }
            return this;
        }
    }

    private File resolveFolder() {
        if (chosenFolder != null) {
            return chosenFolder;
        }
        Project project = model.getCurrentProject();
        return project != null ? OutputPaths.defaultFolder(project, model.getCurrentScene(), settings) : null;
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку");
        // Баг-репорт: с настроенной папкой экспорта по умолчанию в «Предпочтения →
        // Экспорт» диалог всё равно открывался в Documents — chosenFolder тут пуст,
        // пока пользователь ХОТЬ РАЗ не выбрал папку САМ в этой сессии, поэтому
        // resolveFolder() (которая как раз учитывает настройку) не подставлялась
        // вообще. Стартуем от неё же, не только от уже явно выбранной раньше.
        File initial = chosenFolder != null ? chosenFolder : resolveFolder();
        if (initial != null) {
            fc.setCurrentDirectory(initial);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            chosenFolder = fc.getSelectedFile();
            folderField.setText(chosenFolder.getAbsolutePath());
        }
    }

    /** Файлы масок экранов сцены — общий для {@link #exportMasks()} и
     *  {@link #exportSelectedCanvasMask()} (одинаковые имена файлов). */
    private void addScreenMask(MaskExportSet set, Scene scene, Screen scr, boolean labelWithScene) {
        CabinetType type = model.typeOf(scr);
        MaskImage img = MaskImage.screen(scr, type, model.getWorkspace(),
                PixelGridRenderer.GridRenderOptions.defaultForScreen(scr));
        String fname = OutputPaths.sanitize(scene.getName()) + "_" + OutputPaths.sanitize(scr.getName())
                + "_Маска_" + img.width() + "x" + img.height() + ".png";
        set.add(fname, MaskExportSet.screenLabel(scene, scr, labelWithScene), img);
    }

    private void addCanvasMask(MaskExportSet set, Scene scene, ContentCanvas c, boolean labelWithScene) {
        MaskImage img = MaskImage.canvas(c, scene, model, settings);
        String fname = OutputPaths.sanitize(scene.getName()) + "_канвас_" + OutputPaths.sanitize(c.getName())
                + "_" + img.width() + "x" + img.height() + ".png";
        set.add(fname, MaskExportSet.canvasLabel(scene, c, labelWithScene), img);
    }

    /** Маски: по одной на каждый экран ВСЕХ сцен проекта + по одной на каждый канвас
     *  ВСЕХ сцен проекта — единая кнопка, как и раньше на этапе «Вывод». Сначала
     *  показываем превью (нельзя экспортировать то, что нельзя сначала увидеть в
     *  приложении) — запись на диск происходит только по кнопке «Сохранить» в
     *  диалоге предпросмотра.
     *
     *  <p>2026-09-30 (решение D7): вместо готовых картинок — задания {@link MaskImage}
     *  (пиксели появляются только в миниатюрах и при потоковой записи), маски экранов
     *  больше 30000 px в набор не попадают и перечисляются в общем подтверждении. */
    private void exportMasks() {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите проект", "Нет проекта", JOptionPane.WARNING_MESSAGE);
            return;
        }
        MaskExportSet set = new MaskExportSet();
        try {
            for (Scene scene : project.getScenes()) {
                for (Screen scr : scene.getScreens()) {
                    addScreenMask(set, scene, scr, true);
                }
                for (ContentCanvas c : scene.getCanvases()) {
                    addCanvasMask(set, scene, c, true);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования масок: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!confirmExport(project.getScenes(), null, true, set, "Всё равно экспортировать?")) {
            return;
        }
        showMaskPreviewDialog("Предпросмотр масок (экраны + канвасы)", set);
    }

    /** Маски ТОЛЬКО выбранного сейчас канваса ({@link #currentCanvas}) + маски
     *  экранов, размещённых именно на нём (см. {@link CanvasPlacement#getScreenId()})
     *  — запрос пользователя: "добавить для экспорта масок кнопку экспорта
     *  отдельного выбранного канваса, а не всех сразу" ({@link #exportMasks()}
     *  выше выгружает ВСЁ — все экраны и канвасы ВСЕХ сцен проекта разом, что
     *  неудобно, если нужен только один конкретный канвас). Та же схема имён
     *  файлов и то же превью-перед-сохранением, что и у {@link #exportMasks()}. */
    private void exportSelectedCanvasMask() {
        if (currentCanvas == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите канвас", "Нет канваса", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите сцену", "Нет сцены", JOptionPane.WARNING_MESSAGE);
            return;
        }
        MaskExportSet set = new MaskExportSet();
        try {
            addCanvasMask(set, scene, currentCanvas, false);
            for (CanvasPlacement placement : currentCanvas.getPlacements()) {
                Screen scr = screenById(placement.getScreenId());
                if (scr == null) {
                    continue; // экран с тех пор удалён из сцены -- та же защита, что и в exportMasks()
                }
                addScreenMask(set, scene, scr, false);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования масок: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!confirmExport(List.of(scene), List.of(currentCanvas), false, set, "Всё равно экспортировать?")) {
            return;
        }
        showMaskPreviewDialog("Предпросмотр маски канваса «" + currentCanvas.getName() + "»", set);
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
        // Пресет -- текст, PNG не пишется; проверяем только размеры канвасов (≥ 16k -- Resolume
        // может не открыть, см. MaskLimits) -- в том же общем подтверждении, что и обрезка.
        MaskExportSet sizes = new MaskExportSet();
        for (ContentCanvas c : scene.getCanvases()) {
            sizes.addSizeOnly(MaskExportSet.canvasLabel(scene, c, false), c.getWidthPx(), c.getHeightPx());
        }
        if (!confirmExport(List.of(scene), null, false, sizes, "Всё равно экспортировать пресет?")) {
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
            openFolder(folder);
        }
    }

    /** Экспорт под After Effects — по прямому запросу пользователя ("jsx скрипт, который
     *  еще и подставляет в качестве базовых слоев созданные маски. Пресет должен создавать
     *  композицию по канвасу"), см. {@link AfterEffectsJsxWriter}. По одному .jsx на канвас
     *  ТЕКУЩЕЙ сцены (тот же принцип, что {@link #exportResolumePreset()}), плюс сами
     *  PNG-маски экранов, на которые ссылается скрипт, — те же файлы/то же содержимое, что
     *  и «Экспорт масок» (перезаписываются свежими при каждом запуске, чтобы .jsx никогда
     *  не сослался на устаревшую картинку).
     *
     *  <p>2026-09-30 (решение D7): PNG пишутся потоково ({@link MaskImage#writePng}) в фоне с
     *  окном прогресса и кнопкой «Отмена»; маска экрана больше 30000 px не пишется (и .jsx на
     *  неё не ссылается — {@link AfterEffectsJsxWriter} пропускает такие экраны). */
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
        String sceneNameSanitized = OutputPaths.sanitize(scene.getName());
        MaskExportSet set = new MaskExportSet();
        int maskCount = 0;
        for (ContentCanvas c : scene.getCanvases()) {
            for (CanvasPlacement pl : c.getPlacements()) {
                Screen scr = screenById(pl.getScreenId());
                if (scr == null) {
                    continue;
                }
                MaskImage img = MaskImage.screen(scr, model.typeOf(scr), model.getWorkspace(),
                        PixelGridRenderer.GridRenderOptions.defaultForScreen(scr));
                int before = set.entries().size();
                set.add(AfterEffectsJsxWriter.maskFilename(sceneNameSanitized, scr, img.width(), img.height()),
                        MaskExportSet.screenLabel(scene, scr, false), img);
                maskCount += set.entries().size() - before;
            }
            set.add(AfterEffectsJsxWriter.gapMaskFilename(sceneNameSanitized, c),
                    MaskExportSet.canvasLabel(scene, c, false) + " (пустоты)", MaskImage.canvasGap(c, scene, model));
            set.add(AfterEffectsJsxWriter.overlayFilename(sceneNameSanitized, c),
                    MaskExportSet.canvasLabel(scene, c, false) + " (разметка)", MaskImage.canvasOverlay(c, scene, model));
        }
        if (!confirmExport(List.of(scene), null, false, set, "Всё равно экспортировать пресет?")) {
            return;
        }
        folder.mkdirs();
        int scriptCount = 0;
        try {
            set.writeAll(this, "Экспорт под After Effects", folder);
            for (ContentCanvas c : scene.getCanvases()) {
                String jsx = AfterEffectsJsxWriter.buildJsx(c, scene, model, sceneNameSanitized);
                String jsxName = "AE_" + sceneNameSanitized + "_" + OutputPaths.sanitize(c.getName()) + ".jsx";
                // BOM обязателен: без него ExtendScript читает файл в системной кодировке, и
                // кириллица в именах PNG («Маска», «Пустоты») превращается в мусор -> «файл не найден».
                java.nio.file.Files.writeString(new File(folder, jsxName).toPath(), "\uFEFF" + jsx,
                        java.nio.charset.StandardCharsets.UTF_8);
                scriptCount++;
            }
        } catch (java.util.concurrent.CancellationException cancelled) {
            JOptionPane.showMessageDialog(this, "Экспорт отменён — недописанный файл удалён.", "Отменено",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования пресета: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        String skippedNote = set.skipped().isEmpty() ? ""
                : "\nНе экспортировано (сторона больше " + MaskLimits.MAX_SIDE_PX + " px): " + set.skipped().size();
        int answer = JOptionPane.showConfirmDialog(this,
                "Готово.\nСкриптов .jsx сохранено: " + scriptCount + "\nМасок сохранено: " + maskCount
                        + skippedNote
                        + "\n\nЗапустите .jsx через File → Scripts → Run Script File в After Effects.\n\n"
                        + "Открыть папку?",
                "Пресет After Effects сформирован", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            openFolder(folder);
        }
    }

    private static void openFolder(File folder) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(folder);
            }
        } catch (Exception ignored) {
            // не критично
        }
    }

    /** Модальный диалог предпросмотра: миниатюры всех изображений, которые БУДУТ
     *  сохранены, и кнопка «Сохранить всё», которая пишет их на диск только по
     *  явному подтверждению — вместо того чтобы сразу писать файлы вслепую.
     *
     *  <p>2026-09-30 (решение D7): диалог держит задания {@link MaskImage} и маленькие
     *  миниатюры, а не полные картинки (раньше — все маски проекта в полном разрешении
     *  одновременно). Крупно — двойной клик по строке или кнопка «Просмотр» (окно с
     *  прокруткой в пресете «Разрешение превью», «Авто» — вписать в окно, см.
     *  {@link MaskPreviewViewer}); запись на диск — только по «Сохранить», потоково, в фоне
     *  с прогрессом и «Отменой». */
    private void showMaskPreviewDialog(String title, MaskExportSet set) {
        List<MaskExportSet.Entry> entries = set.entries();
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, set.skipped().isEmpty()
                            ? "Нечего показывать — нет ни экранов, ни канвасов."
                            : "Нечего сохранять — все маски больше " + MaskLimits.MAX_SIDE_PX + " px.",
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
        MaskPreviewResolution previewPreset =
                MaskPreviewResolution.fromName(settings.activeProfile().getMaskPreviewResolution());

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (MaskExportSet.Entry entry : entries) {
            MaskImage img = entry.image();
            Runnable openViewer = () -> MaskPreviewViewer.show(dlg, entry.filename(), img, previewPreset);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            JLabel thumbLabel = new JLabel(new ImageIcon(img.thumbnail(220, 150)));
            thumbLabel.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
            thumbLabel.setToolTipText("Двойной клик — просмотр крупно");
            thumbLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openViewer.run();
                    }
                }
            });
            boolean large = MaskLimits.isLarge(img.width(), img.height());
            JLabel nameLabel = new JLabel("<html>" + entry.filename() + "<br><span style='color:"
                    + (large ? "#f0883e" : "#8b949e") + "'>" + img.width() + "×" + img.height() + " px"
                    + (large ? " — сторона ≥ " + MaskLimits.WARN_SIDE_PX : "") + "</span></html>");
            JButton viewBtn = new JButton("Просмотр");
            viewBtn.addActionListener(e -> openViewer.run());
            JPanel viewWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            viewWrap.add(viewBtn);
            row.add(thumbLabel, BorderLayout.WEST);
            row.add(nameLabel, BorderLayout.CENTER);
            row.add(viewWrap, BorderLayout.EAST);
            list.add(row);
            list.add(Box.createVerticalStrut(8));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(640, 560));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel folderLabel = new JLabel("Папка: " + folder.getAbsolutePath()
                + (set.skipped().isEmpty() ? "" : "   (пропущено масок больше " + MaskLimits.MAX_SIDE_PX + " px: "
                + set.skipped().size() + ")"));
        folderLabel.setForeground(Palette.MUTED);
        folderLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

        JButton save = new JButton("Сохранить всё в папку (" + entries.size() + ")");
        save.addActionListener(e -> {
            try {
                int written = set.writeAll(dlg, "Сохранение масок", folder);
                dlg.dispose();
                JOptionPane.showMessageDialog(this, "Сохранено файлов: " + written, "Готово",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (java.util.concurrent.CancellationException cancelled) {
                JOptionPane.showMessageDialog(dlg, "Сохранение отменено — недописанный файл удалён,"
                        + " уже записанные остались в папке.", "Отменено", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
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
