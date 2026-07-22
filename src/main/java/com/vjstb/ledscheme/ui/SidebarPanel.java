package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Боковая панель управления: проекты, сцены, экраны, режим/цепочки, статистика, библиотека. */
public class SidebarPanel extends JPanel {

    private final AppModel model;
    private final MainFrame frame;
    private boolean refreshing;

    // projects
    private final DefaultListModel<Project> projModel = new DefaultListModel<>();
    private final JList<Project> projList = new JList<>(projModel);
    private final JTextField newProjectField = new JTextField();

    // scenes
    private final JPanel scenesSection;
    private final DefaultListModel<Scene> sceneModel = new DefaultListModel<>();
    private final JList<Scene> sceneList = new JList<>(sceneModel);
    private final JTextField newSceneField = new JTextField();

    // screens
    private final JPanel screensSection;
    private final DefaultListModel<Screen> screenModel = new DefaultListModel<>();
    private final JList<Screen> screenList = new JList<>(screenModel);

    // screen params
    private final JPanel paramsSection;
    private final JTextField pName = new JTextField();
    private final JComboBox<CabinetType> pType = new JComboBox<>();
    private final JSpinner pCols = new JSpinner(new SpinnerNumberModel(3, 1, 200, 1));
    private final JSpinner pRows = new JSpinner(new SpinnerNumberModel(5, 1, 200, 1));
    private final JTextField pX = new JTextField();
    private final JTextField pY = new JTextField();

    // mode / chains
    private final JPanel modeSection;
    private final JToggleButton modePower = new JToggleButton("⚡ Питание");
    private final JToggleButton modeSignal = new JToggleButton("📡 Сигнал");
    private final JPanel phasePanel;
    private final JToggleButton phase1 = new JToggleButton("L1");
    private final JToggleButton phase2 = new JToggleButton("L2");
    private final JToggleButton phase3 = new JToggleButton("L3");
    private final JPanel signalParams;
    private final JTextField signalPort = new JTextField();
    private final JCheckBox signalBackup = new JCheckBox("резерв");
    private final JButton newChainBtn = new JButton("+ Новая цепочка");
    private final JButton finishChainBtn = new JButton("✓ Завершить");
    private final JButton cancelChainBtn = new JButton("✕ Отмена");
    private final JButton clearChainsBtn = new JButton("Очистить цепочки режима");
    private final JPanel chainListPanel = new JPanel();

    // stats
    private final JPanel statsSection;
    private final JLabel statRes = new JLabel();
    private final JLabel statSize = new JLabel();
    private final JLabel statCount = new JLabel();
    private final JLabel statPower = new JLabel();
    private final JLabel statWeight = new JLabel();
    private final JLabel statPhases = new JLabel();

    // export
    private final JPanel exportSection;

    // library
    private final DefaultListModel<CabinetType> libModel = new DefaultListModel<>();
    private final JList<CabinetType> libList = new JList<>(libModel);

    private static File lastExportDir;

    public SidebarPanel(AppModel model, MainFrame frame) {
        this.model = model;
        this.frame = frame;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        add(buildProjects());
        scenesSection = buildScenes();
        add(scenesSection);
        screensSection = buildScreens();
        add(screensSection);
        paramsSection = buildScreenParams();
        add(paramsSection);
        modeSection = buildModeChains();
        this.phasePanel = (JPanel) phase1.getParent();
        this.signalParams = signalParamsPanel;
        add(modeSection);
        statsSection = buildStats();
        add(statsSection);
        exportSection = buildExport();
        add(exportSection);
        add(buildLibrary());
        add(Box.createVerticalGlue());

        rebuild();
    }

    // ---- sections ----

    private JComponent section(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)));
        p.add(body, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private JPanel buildProjects() {
        JPanel body = vbox();
        projList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projList.setCellRenderer(new NamedRenderer<Project>(Project::getName,
                p -> p.getScenes().size() + " сцен"));
        projList.setVisibleRowCount(4);
        projList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Project p = projList.getSelectedValue();
            if (p != null && p != model.getCurrentProject()) model.selectProject(p);
        });
        body.add(new JScrollPane(projList));

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
        body.add(vgap());
        body.add(addRow);
        return (JPanel) section("Проекты", body);
    }

    private JPanel buildScenes() {
        JPanel body = vbox();
        sceneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sceneList.setCellRenderer(new NamedRenderer<>(Scene::getName, s -> s.getScreens().size() + " экранов"));
        sceneList.setVisibleRowCount(3);
        sceneList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Scene s = sceneList.getSelectedValue();
            if (s != null && s != model.getCurrentScene()) model.selectScene(s);
        });
        body.add(new JScrollPane(sceneList));

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
        body.add(vgap());
        body.add(addRow);
        return (JPanel) section("Сцены", body);
    }

    private JPanel buildScreens() {
        JPanel body = vbox();
        screenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        screenList.setCellRenderer(new NamedRenderer<>(Screen::getName,
                s -> s.getCols() + "×" + s.getRows()));
        screenList.setVisibleRowCount(4);
        screenList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Screen s = screenList.getSelectedValue();
            if (s != null && s != model.getCurrentScreen()) model.selectScreen(s);
        });
        body.add(new JScrollPane(screenList));

        JButton add = new JButton("+ Добавить экран");
        add.addActionListener(e -> addScreen());
        body.add(vgap());
        body.add(add);
        JLabel hint = new JLabel("Параметры нового экрана — в разделе «Параметры экрана» ниже.");
        hint.setForeground(Palette.MUTED);
        body.add(vgap());
        body.add(hint);
        return (JPanel) section("Экраны на сцене", body);
    }

    /** Создаёт экран с настройками по умолчанию; далее он настраивается в «Параметры экрана». */
    private void addScreen() {
        if (model.getCabinetTypes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала добавьте кабинет в библиотеку", "Нет кабинета",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        CabinetType type = model.getCabinetTypes().get(0);
        int n = model.getCurrentScene() != null ? model.getCurrentScene().getScreens().size() + 1 : 1;
        try {
            Screen s = model.addScreen("Экран " + n, type.getId(), 5, 3, 0, 0);
            model.selectScreen(s);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildScreenParams() {
        JPanel body = vbox();
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
        body.add(vgap());
        body.add(applyGrid);

        JPanel posForm = new JPanel(new GridLayout(0, 2, 4, 4));
        posForm.add(new JLabel("X (мм)"));
        posForm.add(pX);
        posForm.add(new JLabel("Y (мм)"));
        posForm.add(pY);
        body.add(vgap());
        body.add(posForm);
        JButton applyPos = new JButton("Применить позицию");
        applyPos.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr != null) model.updateScreenPosition(scr, parseDouble(pX.getText()), parseDouble(pY.getText()));
        });
        body.add(vgap());
        body.add(applyPos);

        JButton del = new JButton("Удалить экран");
        del.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr != null && confirm("Удалить экран?")) model.deleteScreen(scr);
        });
        body.add(vgap());
        body.add(del);
        return (JPanel) section("Параметры экрана", body);
    }

    private JPanel signalParamsPanel;

    private JPanel buildModeChains() {
        JPanel body = vbox();

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(modePower);
        modeGroup.add(modeSignal);
        modePower.addActionListener(e -> model.setMode(AppModel.Mode.POWER));
        modeSignal.addActionListener(e -> model.setMode(AppModel.Mode.SIGNAL));
        modeRow.add(modePower);
        modeRow.add(modeSignal);
        body.add(modeRow);

        JPanel phaseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        phaseRow.add(new JLabel("Фаза:"));
        ButtonGroup phaseGroup = new ButtonGroup();
        for (JToggleButton b : List.of(phase1, phase2, phase3)) {
            phaseGroup.add(b);
            phaseRow.add(b);
        }
        phase1.addActionListener(e -> model.setActivePhase(1));
        phase2.addActionListener(e -> model.setActivePhase(2));
        phase3.addActionListener(e -> model.setActivePhase(3));
        body.add(phaseRow);

        signalParamsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        signalParamsPanel.add(new JLabel("Порт:"));
        signalPort.setColumns(4);
        signalParamsPanel.add(signalPort);
        signalParamsPanel.add(signalBackup);
        body.add(signalParamsPanel);

        newChainBtn.addActionListener(e -> frame.startChain());
        finishChainBtn.addActionListener(e -> frame.finishChain());
        cancelChainBtn.addActionListener(e -> frame.cancelChain());
        clearChainsBtn.addActionListener(e -> { if (confirm("Очистить все цепочки текущего режима?")) model.clearChainsOfMode(); });
        body.add(vgap());
        body.add(newChainBtn);
        JPanel fc = new JPanel(new GridLayout(1, 2, 4, 0));
        fc.add(finishChainBtn);
        fc.add(cancelChainBtn);
        body.add(fc);
        body.add(clearChainsBtn);

        chainListPanel.setLayout(new BoxLayout(chainListPanel, BoxLayout.Y_AXIS));
        body.add(vgap());
        body.add(new JLabel("Существующие цепочки:"));
        body.add(chainListPanel);

        return (JPanel) section("Режим схемы", body);
    }

    private JPanel buildStats() {
        JPanel body = vbox();
        body.add(statRow("Разрешение", statRes));
        body.add(statRow("Физический размер", statSize));
        body.add(statRow("Кабинетов", statCount));
        body.add(statRow("Мощность", statPower));
        body.add(statRow("Вес", statWeight));
        body.add(vgap());
        statPhases.setForeground(Palette.MUTED);
        body.add(statPhases);
        return (JPanel) section("Статистика экрана", body);
    }

    private JPanel buildExport() {
        JPanel body = vbox();
        JButton one = new JButton("Экспорт экрана в JPEG…");
        one.addActionListener(e -> {
            Screen s = model.getCurrentScreen();
            if (s != null) {
                exportScreens(List.of(s));
            }
        });
        JButton all = new JButton("Все экраны сцены в JPEG…");
        all.addActionListener(e -> {
            if (model.getCurrentScene() != null && !model.getCurrentScene().getScreens().isEmpty()) {
                exportScreens(model.getCurrentScene().getScreens());
            }
        });
        JLabel note = new JLabel("Для каждого экрана сохраняются обе схемы: питание и сигнал.");
        note.setForeground(Palette.MUTED);
        body.add(one);
        body.add(vgap());
        body.add(all);
        body.add(vgap());
        body.add(note);
        return (JPanel) section("Экспорт схемы (JPEG)", body);
    }

    private void exportScreens(List<Screen> screens) {
        if (screens.isEmpty()) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку для сохранения JPEG");
        if (lastExportDir != null) {
            fc.setCurrentDirectory(lastExportDir);
        }
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = fc.getSelectedFile();
        lastExportDir = dir;
        boolean batch = screens.size() > 1;

        int count = 0;
        try {
            for (int i = 0; i < screens.size(); i++) {
                Screen scr = screens.get(i);
                CabinetType type = model.typeOf(scr);
                String prefix = batch ? String.format("%02d_", i + 1) : "";
                for (boolean power : new boolean[]{true, false}) {
                    java.awt.image.BufferedImage img = SchemeRenderer.renderImage(scr, type, power, 120);
                    String fname = prefix + sanitize(scr.getName()) + "_" + (power ? "Питание" : "Сигнал") + ".jpg";
                    SchemeRenderer.writeJpeg(img, new File(dir, fname));
                    count++;
                }
            }
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка сохранения: " + ex.getMessage(), "Экспорт",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "Сохранено файлов: " + count + "\nв папке:\n" + dir.getAbsolutePath() + "\n\nОткрыть папку?",
                "Экспорт завершён", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            openFolder(dir);
        }
    }

    private void openFolder(File dir) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception ignored) {
            // не критично
        }
    }

    private static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", " ");
        return s.isEmpty() ? "экран" : s;
    }

    private JPanel buildLibrary() {
        JPanel body = vbox();
        libList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libList.setCellRenderer(new NamedRenderer<>(CabinetType::getName, ct ->
                fmt(ct.getWidthMm()) + "×" + fmt(ct.getHeightMm()) + "мм · "
                        + ct.getResolutionWidth() + "×" + ct.getResolutionHeight() + "px · "
                        + fmt(ct.getPowerConsumptionW()) + "Вт · " + fmt(ct.getWeightKg()) + "кг"));
        libList.setVisibleRowCount(5);
        body.add(new JScrollPane(libList));

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            CabinetType ct = new CabinetTypeDialog(frame, null).showDialog();
            if (ct != null) tryRun(() -> model.addCabinetType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel == null) return;
            CabinetType ct = new CabinetTypeDialog(frame, sel).showDialog();
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
        return (JPanel) section("Библиотека кабинетов", body);
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
            if (hasScene) {
                syncList(screenModel, model.getCurrentScene().getScreens());
                screenList.setSelectedValue(model.getCurrentScreen(), true);
            }

            Screen scr = model.getCurrentScreen();
            boolean hasScreen = scr != null;
            paramsSection.setVisible(hasScreen);
            modeSection.setVisible(hasScreen);
            statsSection.setVisible(hasScreen);
            exportSection.setVisible(hasScreen);
            if (hasScreen) {
                pName.setText(scr.getName());
                populateTypeCombo(pType, model.typeOf(scr));
                pCols.setValue(scr.getCols());
                pRows.setValue(scr.getRows());
                pX.setText(fmt(scr.getPosXMm()));
                pY.setText(fmt(scr.getPosYMm()));
                rebuildModeChains(scr);
                rebuildStats(scr);
            }

            syncList(libModel, model.getCabinetTypes());
        } finally {
            refreshing = false;
        }
        revalidate();
        repaint();
    }

    private void rebuildModeChains(Screen scr) {
        boolean power = model.getMode() == AppModel.Mode.POWER;
        modePower.setSelected(power);
        modeSignal.setSelected(!power);
        phasePanel.setVisible(power);
        signalParams.setVisible(!power);
        switch (model.getActivePhase()) {
            case 1 -> phase1.setSelected(true);
            case 2 -> phase2.setSelected(true);
            case 3 -> phase3.setSelected(true);
            default -> phase1.setSelected(true);
        }

        boolean building = frame.isChainBuildingActive();
        newChainBtn.setEnabled(!building);
        finishChainBtn.setEnabled(building);
        cancelChainBtn.setEnabled(building);

        chainListPanel.removeAll();
        if (power) {
            List<PowerChain> chains = scr.getPowerChains();
            if (chains.isEmpty()) {
                chainListPanel.add(mutedLabel("Цепочек ещё нет"));
            }
            for (PowerChain c : chains) {
                chainListPanel.add(chainRow("L" + c.getPhase() + " · " + c.getCabinetInstanceIds().size() + " каб.",
                        Palette.phaseColor(c.getPhase()), () -> model.deletePowerChain(c.getId())));
            }
        } else {
            List<SignalChain> chains = scr.getSignalChains();
            if (chains.isEmpty()) {
                chainListPanel.add(mutedLabel("Цепочек ещё нет"));
            }
            for (int i = 0; i < chains.size(); i++) {
                SignalChain c = chains.get(i);
                String label = (c.getPortNumber() != null ? "Порт " + c.getPortNumber() : "Без порта")
                        + (c.isBackup() ? " (резерв)" : "") + " · " + c.getCabinetInstanceIds().size() + " каб.";
                chainListPanel.add(chainRow(label, Palette.signalColor(i), () -> model.deleteSignalChain(c.getId())));
            }
        }
        chainListPanel.revalidate();
        chainListPanel.repaint();
    }

    private void rebuildStats(Screen scr) {
        ScreenStats s = ScreenLogic.stats(scr, model.typeOf(scr));
        statRes.setText(s.resolutionWidthPx() + " × " + s.resolutionHeightPx() + " px");
        statSize.setText(fmt(s.physicalWidthMm()) + " × " + fmt(s.physicalHeightMm()) + " мм");
        statCount.setText(String.valueOf(s.activeCabinetCount()));
        statPower.setText(fmt(s.totalPowerW()) + " Вт");
        statWeight.setText(fmt(s.totalWeightKg()) + " кг");
        statPhases.setText(String.format("<html>L1: %d каб. · %s Вт<br>L2: %d каб. · %s Вт<br>L3: %d каб. · %s Вт</html>",
                s.phaseCabinetCounts()[1], fmt(s.phasePowerW()[1]),
                s.phaseCabinetCounts()[2], fmt(s.phasePowerW()[2]),
                s.phaseCabinetCounts()[3], fmt(s.phasePowerW()[3])));
    }

    // ---- accessors for MainFrame ----

    public Integer getSignalPort() {
        String t = signalPort.getText().trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isSignalBackup() {
        return signalBackup.isSelected();
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

    private JComponent chainRow(String label, java.awt.Color dot, Runnable onDelete) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(dot);
        JLabel text = new JLabel(label);
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> onDelete.run());
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(del, BorderLayout.EAST);
        return row;
    }

    private JComponent statRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title + ": ");
        t.setForeground(Palette.MUTED);
        row.add(t, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    private JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Palette.MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private Component vgap() {
        return Box.createVerticalStrut(6);
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

    static String fmt(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
