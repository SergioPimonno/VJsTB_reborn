package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.ui.CabinetTypeDialog;
import com.vjstb.ledscheme.ui.CabinetTypeRenderer;
import com.vjstb.ledscheme.ui.ControllerTypeDialog;
import com.vjstb.ledscheme.ui.ListSizing;
import com.vjstb.ledscheme.ui.NamedRenderer;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.ShapeEditorPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Этап «Сетап»: состав проекта (проекты → сцены → экраны), библиотека кабинетов,
 * параметры сетки экрана и базовая сводка прерига (вес/мощность по сцене).
 */
public class SetupStagePanel extends JPanel {

    private final AppModel model;
    private boolean refreshing;

    private final DefaultListModel<Project> projModel = new DefaultListModel<>();
    private final JList<Project> projList = new JList<>(projModel);
    private final JScrollPane projScroll = new JScrollPane(projList);
    private final JTextField newProjectField = new JTextField();

    private final JPanel scenesSection;
    private final DefaultListModel<Scene> sceneModel = new DefaultListModel<>();
    private final JList<Scene> sceneList = new JList<>(sceneModel);
    private final JScrollPane sceneScroll = new JScrollPane(sceneList);
    private final JTextField newSceneField = new JTextField();

    private final JPanel screensSection;
    private final DefaultListModel<Screen> screenModel = new DefaultListModel<>();
    private final JList<Screen> screenList = new JList<>(screenModel);
    private final JScrollPane screenScroll = new JScrollPane(screenList);

    private final JPanel prerigSection;
    private final JLabel prerigScreens = new JLabel();
    private final JLabel prerigCabinets = new JLabel();
    private final JLabel prerigPower = new JLabel();
    private final JLabel prerigWeight = new JLabel();

    private final JPanel paramsSection;
    private final JTextField pName = new JTextField();
    private final JComboBox<CabinetType> pType = new JComboBox<>();
    private final JSpinner pCols = new JSpinner(new SpinnerNumberModel(3, 1, 200, 1));
    private final JSpinner pRows = new JSpinner(new SpinnerNumberModel(5, 1, 200, 1));
    private final JTextField pX = new JTextField();
    private final JTextField pY = new JTextField();
    private final JComboBox<com.vjstb.ledscheme.model.ScreenMountType> pMountType =
            new JComboBox<>(com.vjstb.ledscheme.model.ScreenMountType.values());
    private final JSpinner pRiggingPoints = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JTextField pRiggingNotes = new JTextField();

    private final DefaultListModel<CabinetType> libModel = new DefaultListModel<>();
    private final JList<CabinetType> libList = new JList<>(libModel);
    private final JScrollPane libScroll = new JScrollPane(libList);

    private final DefaultListModel<ControllerType> ctrlLibModel = new DefaultListModel<>();
    private final JList<ControllerType> ctrlLibList = new JList<>(ctrlLibModel);
    private final JScrollPane ctrlLibScroll = new JScrollPane(ctrlLibList);

    private final JPanel shapeSection;
    private final ShapeEditorPanel shapeEditor;
    private final JScrollPane shapeScroll;
    private final javax.swing.JToggleButton shapeModeBtn = new javax.swing.JToggleButton("Форма", true);
    private final javax.swing.JToggleButton typeModeBtn = new javax.swing.JToggleButton("Тип");
    private final JComboBox<CabinetType> paintTypeCombo = new JComboBox<>();
    private final JLabel shapeHint = new JLabel();

    public SetupStagePanel(AppModel model) {
        this.model = model;
        setLayout(new BorderLayout());

        // Левая колонка — навигация (Проекты/Сцены/Экраны) + параметры выбранного
        // экрана внизу; между блоками — перетаскиваемые разделители (высота каждого
        // блока регулируется пользователем, а не только по количеству элементов).
        scenesSection = buildScenes();
        screensSection = buildScreens();
        paramsSection = buildScreenParams();
        JSplitPane leftSplit3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, screensSection, paramsSection);
        leftSplit3.setResizeWeight(0.32);
        JSplitPane leftSplit2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scenesSection, leftSplit3);
        leftSplit2.setResizeWeight(0.18);
        JSplitPane leftSplit1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildProjects(), leftSplit2);
        leftSplit1.setResizeWeight(0.16);
        // resizeWeight сам по себе определяет только распределение ПРИ ИЗМЕНЕНИИ размера —
        // начальное положение разделителя Swing вычисляет отдельно (и не по resizeWeight),
        // из-за чего «Параметры экрана» без этого оказывались видны только после прокрутки
        // в самый низ. Задаём стартовую позицию явно при первом появлении на экране.
        setInitialDividerOnShow(leftSplit3, 0.32);
        setInitialDividerOnShow(leftSplit2, 0.18);
        setInitialDividerOnShow(leftSplit1, 0.16);
        JPanel left = new JPanel(new BorderLayout());
        left.add(leftSplit1, BorderLayout.CENTER);

        // Правая колонка — сводка (прериг) сверху, форма/типы ячеек, библиотека.
        JPanel right = UiKit.vbox();
        prerigSection = buildPrerig();
        right.add(prerigSection);
        shapeEditor = new ShapeEditorPanel(model);
        shapeScroll = new JScrollPane(shapeEditor);
        shapeSection = buildShapeEditor();
        right.add(shapeSection);
        right.add(buildLibrary());
        right.add(buildControllerLibrary());
        right.add(javax.swing.Box.createVerticalGlue());

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        JScrollPane rightScroll = new JScrollPane(right);
        rightScroll.setBorder(null);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        split.setDividerLocation(380);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        model.addListener(this::rebuild);
        rebuild();
    }

    /** Ставит начальное положение разделителя один раз, когда компонент реально
     *  впервые отображается (до этого момента у него ещё нет корректного размера). */
    private static void setInitialDividerOnShow(JSplitPane sp, double proportion) {
        sp.addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && sp.isShowing()) {
                    sp.setDividerLocation(proportion);
                    sp.removeHierarchyListener(this);
                }
            }
        });
    }

    // ---- проекты ----

    private JPanel buildProjects() {
        JPanel body = UiKit.vbox();
        projList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projList.setCellRenderer(new NamedRenderer<Project>(Project::getName, p -> p.getScenes().size() + " сцен"));
        projList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Project p = projList.getSelectedValue();
            if (p != null && p != model.getCurrentProject()) model.selectProject(p);
        });
        // Список растягивается на всё, что даст разделитель JSplitPane (высота — по
        // перетаскиванию пользователем, а не только по числу элементов).
        projScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        body.add(projScroll);

        JPanel addRow = new JPanel(new BorderLayout(4, 0));
        newProjectField.putClientProperty("JTextField.placeholderText", "Название проекта…");
        JButton add = new JButton("+");
        add.addActionListener(e -> {
            String name = newProjectField.getText().trim();
            if (!name.isEmpty()) { model.selectProject(model.addProject(name)); newProjectField.setText(""); }
        });
        JButton del = new JButton("✕");
        del.addActionListener(e -> {
            Project p = projList.getSelectedValue();
            if (p != null && confirm("Удалить проект со всеми сценами и экранами?")) model.deleteProject(p);
        });
        addRow.add(newProjectField, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.add(add);
        btns.add(del);
        addRow.add(btns, BorderLayout.EAST);
        body.add(UiKit.vgap());
        body.add(addRow);
        return (JPanel) UiKit.dynamicSection("Проекты", body);
    }

    // ---- сцены ----

    private JPanel buildScenes() {
        JPanel body = UiKit.vbox();
        sceneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sceneList.setCellRenderer(new NamedRenderer<Scene>(Scene::getName, s -> s.getScreens().size() + " экранов"));
        sceneList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Scene s = sceneList.getSelectedValue();
            if (s != null && s != model.getCurrentScene()) model.selectScene(s);
        });
        sceneScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        body.add(sceneScroll);

        JPanel addRow = new JPanel(new BorderLayout(4, 0));
        newSceneField.putClientProperty("JTextField.placeholderText", "Название сцены…");
        JButton add = new JButton("+");
        add.addActionListener(e -> {
            String name = newSceneField.getText().trim();
            if (!name.isEmpty()) { model.selectScene(model.addScene(name)); newSceneField.setText(""); }
        });
        JButton del = new JButton("✕");
        del.addActionListener(e -> {
            Scene s = sceneList.getSelectedValue();
            if (s != null && confirm("Удалить сцену со всеми экранами?")) model.deleteScene(s);
        });
        addRow.add(newSceneField, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.add(add);
        btns.add(del);
        addRow.add(btns, BorderLayout.EAST);
        body.add(UiKit.vgap());
        body.add(addRow);
        return (JPanel) UiKit.dynamicSection("Сцены", body);
    }

    // ---- экраны ----

    private JPanel buildScreens() {
        JPanel body = UiKit.vbox();
        screenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        screenList.setCellRenderer(new NamedRenderer<Screen>(Screen::getName, s -> s.getCols() + "×" + s.getRows()));
        screenList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Screen s = screenList.getSelectedValue();
            if (s != null && s != model.getCurrentScreen()) model.selectScreen(s);
        });
        screenScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        body.add(screenScroll);

        JButton add = new JButton("+ Добавить экран");
        add.addActionListener(e -> addScreen());
        body.add(UiKit.vgap());
        body.add(add);

        JButton arrange = new JButton("Расставить экраны без наложения");
        arrange.setToolTipText("Перестроит X/Y всех экранов сцены в ряд, чтобы они не перекрывались на «Визуализации»");
        arrange.addActionListener(e -> model.autoArrangeScreensInScene());
        body.add(UiKit.vgap());
        body.add(arrange);

        JLabel hint = UiKit.muted("Параметры нового экрана — справа, в «Параметры экрана».");
        body.add(UiKit.vgap());
        body.add(hint);
        return (JPanel) UiKit.dynamicSection("Экраны на сцене", body);
    }

    private void addScreen() {
        if (model.getCabinetTypes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала добавьте кабинет в библиотеку", "Нет кабинета",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        CabinetType type = model.getCabinetTypes().get(0);
        int n = model.getCurrentScene() != null ? model.getCurrentScene().getScreens().size() + 1 : 1;
        try {
            Screen s = model.addScreenAutoPosition("Экран " + n, type.getId(), 5, 3);
            model.selectScreen(s);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- прериг ----

    private JPanel buildPrerig() {
        JPanel body = UiKit.vbox();
        body.add(statRow("Экранов", prerigScreens));
        body.add(statRow("Кабинетов", prerigCabinets));
        body.add(statRow("Мощность сцены", prerigPower));
        body.add(statRow("Вес сцены", prerigWeight));
        body.add(UiKit.vgap());
        body.add(UiKit.muted("Точки подвеса и проверка нагрузки — будут добавлены отдельным расчётом."));
        return (JPanel) UiKit.section("Прериг сцены", body);
    }

    // ---- параметры экрана ----

    private JPanel buildScreenParams() {
        JPanel body = UiKit.vbox();
        pType.setRenderer(new CabinetTypeRenderer());
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Название"));
        form.add(pName);
        form.add(new JLabel("Кабинет"));
        form.add(pType);
        form.add(new JLabel("Колонны"));
        form.add(pCols);
        form.add(new JLabel("Строки"));
        form.add(pRows);
        body.add(form);

        JButton applyGrid = new JButton("Применить сетку");
        applyGrid.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            CabinetType type = (CabinetType) pType.getSelectedItem();
            if (scr == null || type == null) return;
            try {
                model.updateScreenGrid(scr, orDefault(pName.getText(), scr.getName()), type.getId(),
                        (Integer) pRows.getValue(), (Integer) pCols.getValue());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        body.add(UiKit.vgap());
        body.add(applyGrid);

        JPanel posForm = new JPanel(new GridLayout(0, 2, 4, 4));
        posForm.add(new JLabel("X (мм)"));
        posForm.add(pX);
        posForm.add(new JLabel("Y (мм)"));
        posForm.add(pY);
        body.add(UiKit.vgap());
        body.add(posForm);
        JButton applyPos = new JButton("Применить позицию");
        applyPos.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr != null) model.updateScreenPosition(scr, parseDouble(pX.getText()), parseDouble(pY.getText()));
        });
        body.add(UiKit.vgap());
        body.add(applyPos);

        JPanel mountForm = new JPanel(new GridLayout(0, 2, 4, 4));
        mountForm.add(new JLabel("Способ монтажа"));
        mountForm.add(pMountType);
        mountForm.add(new JLabel("Точек подвеса"));
        mountForm.add(pRiggingPoints);
        mountForm.add(new JLabel("Заметки по подвесу"));
        mountForm.add(pRiggingNotes);
        body.add(UiKit.vgap());
        body.add(mountForm);
        body.add(UiKit.muted("Расчёт точек подвеса по формуле — будет добавлен позже; пока значение вводится вручную."));

        JButton applyMount = new JButton("Применить монтаж");
        applyMount.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr == null) return;
            com.vjstb.ledscheme.model.ScreenMountType mt =
                    (com.vjstb.ledscheme.model.ScreenMountType) pMountType.getSelectedItem();
            model.updateScreenMount(scr, mt, (Integer) pRiggingPoints.getValue(), pRiggingNotes.getText());
        });
        body.add(UiKit.vgap());
        body.add(applyMount);

        JButton del = new JButton("Удалить экран");
        del.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr != null && confirm("Удалить экран?")) model.deleteScreen(scr);
        });
        body.add(UiKit.vgap());
        body.add(del);
        return (JPanel) UiKit.section("Параметры экрана", body);
    }

    // ---- форма экрана и типы по ячейкам ----

    private JPanel buildShapeEditor() {
        JPanel body = UiKit.vbox();

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
        g.add(shapeModeBtn);
        g.add(typeModeBtn);
        shapeModeBtn.addActionListener(e -> {
            shapeEditor.setMode(ShapeEditorPanel.Mode.SHAPE);
            paintTypeCombo.setEnabled(false);
            shapeHint.setText("<html>Клик по ячейке — исключить/включить (так задаётся не прямоугольная форма:"
                    + " треугольная, ступенчатая и т.д.)</html>");
        });
        typeModeBtn.addActionListener(e -> {
            shapeEditor.setMode(ShapeEditorPanel.Mode.TYPE);
            paintTypeCombo.setEnabled(true);
            shapeHint.setText("<html>Выберите тип ниже, затем клик/протяжка ЛКМ по ячейкам — покрасить их этим"
                    + " типом (например, вставка другого модуля). Пункт «тип экрана» — вернуть по умолчанию.</html>");
        });
        modeRow.add(shapeModeBtn);
        modeRow.add(typeModeBtn);
        body.add(modeRow);

        body.add(UiKit.vgap());
        paintTypeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof CabinetType ct ? ct.getName() : "— тип экрана по умолчанию —");
                return this;
            }
        });
        paintTypeCombo.setEnabled(false);
        paintTypeCombo.addActionListener(e -> {
            if (refreshing) return;
            CabinetType sel = (CabinetType) paintTypeCombo.getSelectedItem();
            shapeEditor.setPaintType(sel == null ? null : sel.getId());
        });
        body.add(paintTypeCombo);

        body.add(UiKit.vgap());
        shapeHint.setForeground(Palette.MUTED);
        shapeModeBtn.doClick(); // выставит текст подсказки для стартового режима «Форма»
        body.add(shapeHint);

        body.add(UiKit.vgap());
        shapeScroll.setBorder(javax.swing.BorderFactory.createLineBorder(Palette.BORDER));
        shapeScroll.setPreferredSize(new Dimension(200, 220));
        shapeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        body.add(shapeScroll);

        return (JPanel) UiKit.section("Форма экрана и типы по ячейкам", body);
    }

    // ---- библиотека кабинетов ----

    private JPanel buildLibrary() {
        JPanel body = UiKit.vbox();
        libList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libList.setCellRenderer(new NamedRenderer<CabinetType>(CabinetType::getName, ct ->
                UiKit.fmt(ct.getWidthMm()) + "×" + UiKit.fmt(ct.getHeightMm()) + "мм · "
                        + ct.getResolutionWidth() + "×" + ct.getResolutionHeight() + "px · "
                        + UiKit.fmt(ct.getPowerConsumptionW()) + "Вт · " + UiKit.fmt(ct.getWeightKg()) + "кг"));
        body.add(libScroll);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            CabinetType ct = new CabinetTypeDialog(topWindow(), null).showDialog();
            if (ct != null) tryRun(() -> model.addCabinetType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel == null) return;
            CabinetType ct = new CabinetTypeDialog(topWindow(), sel).showDialog();
            if (ct != null) tryRun(() -> model.updateCabinetType(ct));
        });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel != null && confirm("Удалить кабинет из библиотеки?")) tryRun(() -> model.deleteCabinetType(sel.getId()));
        });
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        body.add(crud);

        JPanel io = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton exp = new JButton("Экспорт JSON");
        exp.addActionListener(e -> exportLibrary());
        JButton imp = new JButton("Импорт JSON");
        imp.addActionListener(e -> importLibrary());
        io.add(exp);
        io.add(imp);
        body.add(io);
        return (JPanel) UiKit.section("Библиотека кабинетов", body);
    }

    // ---- библиотека контроллеров (аналог SmartLCT) ----

    private JPanel buildControllerLibrary() {
        JPanel body = UiKit.vbox();
        ctrlLibList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ctrlLibList.setCellRenderer(new NamedRenderer<ControllerType>(
                ct -> ct.getName() + (ct.getVendor().isEmpty() ? "" : " (" + ct.getVendor() + ")"),
                ct -> ct.getPortCount() + " портов · до " + ct.getMaxPixelsPerPort() + " px/порт"));
        ctrlLibScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        body.add(ctrlLibScroll);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            ControllerType ct = new ControllerTypeDialog(topWindow(), null).showDialog();
            if (ct != null) tryRun(() -> model.addControllerType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null) return;
            ControllerType ct = new ControllerTypeDialog(topWindow(), sel).showDialog();
            if (ct != null) tryRun(() -> model.updateControllerType(ct));
        });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel != null && confirm("Удалить контроллер из библиотеки?")) {
                tryRun(() -> model.deleteControllerType(sel.getId()));
            }
        });
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        body.add(crud);
        return (JPanel) UiKit.section("Библиотека контроллеров", body);
    }

    private void exportLibrary() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("led-cabinet-library.json"));
        fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            tryRun(() -> model.exportLibrary(fc.getSelectedFile()));
        }
    }

    private void importLibrary() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                int n = model.importLibrary(fc.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Импортировано кабинетов: " + n, "Импорт",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---- rebuild ----

    public void rebuild() {
        refreshing = true;
        try {
            syncList(projModel, model.getProjects());
            projList.setSelectedValue(model.getCurrentProject(), true);

            boolean hasProject = model.getCurrentProject() != null;
            scenesSection.setVisible(hasProject);
            if (hasProject) {
                syncList(sceneModel, model.getCurrentProject().getScenes());
                sceneList.setSelectedValue(model.getCurrentScene(), true);
            }

            boolean hasScene = model.getCurrentScene() != null;
            screensSection.setVisible(hasScene);
            prerigSection.setVisible(hasScene);
            if (hasScene) {
                syncList(screenModel, model.getCurrentScene().getScreens());
                screenList.setSelectedValue(model.getCurrentScreen(), true);
                rebuildPrerig();
            }

            Screen scr = model.getCurrentScreen();
            paramsSection.setVisible(scr != null);
            shapeSection.setVisible(scr != null);
            if (scr != null) {
                pName.setText(scr.getName());
                populateTypeCombo(pType, model.typeOf(scr));
                pCols.setValue(scr.getCols());
                pRows.setValue(scr.getRows());
                pX.setText(UiKit.fmt(scr.getPosXMm()));
                pY.setText(UiKit.fmt(scr.getPosYMm()));
                pMountType.setSelectedItem(scr.getMountType());
                pRiggingPoints.setValue(scr.getRiggingPointsCount());
                pRiggingNotes.setText(scr.getRiggingNotes() != null ? scr.getRiggingNotes() : "");

                CabinetType prevPaint = (CabinetType) paintTypeCombo.getSelectedItem();
                DefaultComboBoxModel<CabinetType> paintModel = new DefaultComboBoxModel<>();
                paintModel.addElement(null); // «тип экрана по умолчанию»
                for (CabinetType ct : model.getCabinetTypes()) {
                    paintModel.addElement(ct);
                }
                paintTypeCombo.setModel(paintModel);
                if (prevPaint != null && model.getCabinetTypes().contains(prevPaint)) {
                    paintTypeCombo.setSelectedItem(prevPaint);
                }
            }

            syncList(libModel, model.getCabinetTypes());
            ListSizing.fit(libList, libScroll, 2, 8);
            syncList(ctrlLibModel, model.getWorkspace().getControllerTypes());
            ListSizing.fit(ctrlLibList, ctrlLibScroll, 2, 6);
        } finally {
            refreshing = false;
        }
        shapeEditor.revalidate();
        shapeEditor.repaint();
        revalidate();
        repaint();
    }

    private void rebuildPrerig() {
        SceneStats s = model.currentSceneStats();
        if (s == null) return;
        prerigScreens.setText(String.valueOf(s.screenCount()));
        prerigCabinets.setText(String.valueOf(s.totalCabinetCount()));
        prerigPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
        prerigWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
    }

    // ---- helpers ----

    private void populateTypeCombo(JComboBox<CabinetType> combo, CabinetType select) {
        DefaultComboBoxModel<CabinetType> m = new DefaultComboBoxModel<>();
        for (CabinetType ct : model.getCabinetTypes()) {
            m.addElement(ct);
        }
        combo.setModel(m);
        if (select != null) {
            combo.setSelectedItem(select);
        }
    }

    private static <T> void syncList(DefaultListModel<T> lm, List<T> items) {
        lm.clear();
        for (T i : items) {
            lm.addElement(i);
        }
    }

    private JPanel statRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title + ": ");
        t.setForeground(Palette.MUTED);
        row.add(t, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    private java.awt.Window topWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }

    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Подтверждение", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    private void tryRun(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String orDefault(String s, String def) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? def : t;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
