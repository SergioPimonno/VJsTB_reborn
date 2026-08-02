package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.ui.CabinetTypeRenderer;
import com.vjstb.ledscheme.ui.ListSizing;
import com.vjstb.ledscheme.ui.MathFields;
import com.vjstb.ledscheme.ui.NamedRenderer;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.ShapeEditorPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import javax.swing.SwingUtilities;

/**
 * Этап «Сетап»: состав проекта (проекты → сцены → экраны), библиотека кабинетов,
 * параметры сетки экрана и базовая сводка прерига (вес/мощность по сцене).
 */
public class SetupStagePanel extends JPanel {

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
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
    private final com.vjstb.ledscheme.ui.SceneCanvasPanel prerigPreview;
    private final JButton calcRiggingBtn = new JButton("Рассчитать точки подвеса");

    private final JPanel paramsSection;
    // Явное число колонок (а не пустой конструктор) — предпочтительная ширина поля
    // тогда предсказуема и небольшая; иначе GridLayout(0,2) в узком окне раздувал
    // всю секцию «Параметры экрана» шире доступного места (поля обрезались, у
    // секции появлялся горизонтальный скролл).
    private final JTextField pName = new JTextField(10);
    private final JComboBox<CabinetType> pType = new JComboBox<>();
    private final JSpinner pCols = new JSpinner(new SpinnerNumberModel(3, 1, 200, 1));
    private final JSpinner pRows = new JSpinner(new SpinnerNumberModel(5, 1, 200, 1));
    private final JTextField pX = new JTextField(8);
    private final JTextField pY = new JTextField(8);
    private final JComboBox<com.vjstb.ledscheme.model.ScreenMountType> pMountType =
            new JComboBox<>(com.vjstb.ledscheme.model.ScreenMountType.values());
    private final JSpinner pRiggingPoints = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JTextField pRiggingNotes = new JTextField(10);
    private final JComboBox<Integer> pRefreshHz = new JComboBox<>(new Integer[]{50, 60, 120, 144, 240});
    private final JComboBox<Integer> pBitDepth = new JComboBox<>(new Integer[]{8, 10, 12});

    private final JPanel shapeSection;
    private final ShapeEditorPanel shapeEditor;
    private final JScrollPane shapeScroll;
    private final JLabel shapeHint = new JLabel();

    // Поля (не локальные переменные), чтобы rebuild() мог явно дёрнуть revalidate/repaint
    // именно на тех разделителях, чьи дети меняют видимость — иначе секции иногда не
    // перерисовываются сразу после выбора проекта/сцены, а только после следующего
    // взаимодействия с интерфейсом (см. UiKit.setInitialDividerOnShow — похожая природа).
    private final JSplitPane leftSplit3;
    private final JSplitPane leftSplit2;
    private final JSplitPane leftSplitNav;

    public SetupStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setLayout(new BorderLayout());

        // Левая колонка — навигация (Проекты/Сцены/Экраны) + параметры выбранного
        // экрана внизу; между блоками — перетаскиваемые разделители (высота каждого
        // блока регулируется пользователем, а не только по количеству элементов,
        // и запоминается в профиле настроек — переживает перезапуск).
        scenesSection = buildScenes();
        screensSection = buildScreens();
        paramsSection = buildScreenParams();
        leftSplit3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, screensSection, paramsSection);
        leftSplit3.setResizeWeight(0.32);
        leftSplit2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scenesSection, leftSplit3);
        leftSplit2.setResizeWeight(0.18);
        leftSplitNav = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildProjects(), leftSplit2);
        leftSplitNav.setResizeWeight(0.16);
        UiKit.persistentDivider(settings, "setup.screensParams", leftSplit3, 0.32);
        UiKit.persistentDivider(settings, "setup.scenes", leftSplit2, 0.18);
        UiKit.persistentDivider(settings, "setup.projects", leftSplitNav, 0.16);
        JPanel left = new JPanel(new BorderLayout());
        left.add(leftSplitNav, BorderLayout.CENTER);

        // Правая колонка — сводка (прериг) и предпросмотр/редактор формы экрана,
        // между ними перетаскиваемый разделитель (тот же приём, что и у левой
        // колонки, см. leftSplit2/leftSplit3/leftSplitNav) — раньше это была просто
        // жёстко сложенная колонка (vbox), из-за чего при небольшой высоте окна
        // «Форма экрана» уезжала за нижний край окна без возможности отдать ей
        // больше места за счёт «Прериг сцены» (баг-репорт).
        prerigPreview = new com.vjstb.ledscheme.ui.SceneCanvasPanel(model);
        prerigPreview.setShowRiggingPoints(true);
        prerigSection = buildPrerig();
        shapeEditor = new ShapeEditorPanel(model);
        shapeScroll = new JScrollPane(shapeEditor);
        shapeSection = buildShapeEditor();
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, prerigSection, shapeSection);
        rightSplit.setContinuousLayout(true);
        rightSplit.setResizeWeight(0.6);
        UiKit.persistentDivider(settings, "setup.prerigShape", rightSplit, 0.6);

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        JScrollPane rightScroll = new JScrollPane(rightSplit);
        rightScroll.setBorder(null);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        split.setContinuousLayout(true);
        UiKit.persistentDivider(settings, "setup.outer", split, 0.28);
        add(split, BorderLayout.CENTER);

        // Минимум — заголовок секции + одна позиция списка, чтобы после выбора
        // проекта/сцены содержимое было видно сразу, без ручной растяжки разделителей.
        // Расти дальше секции могут и сами (если позиций больше) и от руки (перетаскиванием).
        for (JScrollPane sp : new JScrollPane[]{projScroll, sceneScroll, screenScroll}) {
            sp.setMinimumSize(new Dimension(120, 56));
        }

        model.addListener(this::rebuild);
        rebuild();
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
        Runnable deleteSelectedProject = () -> {
            Project p = projList.getSelectedValue();
            if (p != null && confirm("Удалить проект со всеми сценами и экранами?")) model.deleteProject(p);
        };
        JButton del = new JButton("✕");
        del.addActionListener(e -> deleteSelectedProject.run());
        UiKit.bindDeleteKey(projList, deleteSelectedProject);
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
        Runnable deleteSelectedScene = () -> {
            Scene s = sceneList.getSelectedValue();
            if (s != null && confirm("Удалить сцену со всеми экранами?")) model.deleteScene(s);
        };
        JButton del = new JButton("✕");
        del.addActionListener(e -> deleteSelectedScene.run());
        UiKit.bindDeleteKey(sceneList, deleteSelectedScene);
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
        screenList.setCellRenderer(new NamedRenderer<Screen>(Screen::getName, s -> {
            CabinetType ct = model.typeOf(s);
            return s.getCols() + "×" + s.getRows() + (ct != null ? " · " + ct.getName() : "");
        }));
        screenList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            Screen s = screenList.getSelectedValue();
            if (s != null && s != model.getCurrentScreen()) model.selectScreen(s);
        });
        screenScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        body.add(screenScroll);

        // "✕" рядом со списком — как у сцен выше (buildScenes) — не заставляет
        // прокручивать вниз до «Параметры экрана», где тоже есть «Удалить экран»
        // (оставлен как есть — оба места работают с ТЕКУЩИМ выбранным экраном).
        JPanel screenBtnRow = new JPanel(new BorderLayout(4, 0));
        JButton add = new JButton("+ Добавить экран");
        add.addActionListener(e -> addScreen());
        Runnable deleteSelectedScreen = () -> {
            Screen s = screenList.getSelectedValue();
            if (s != null && confirm("Удалить экран «" + s.getName() + "»?")) model.deleteScreen(s);
        };
        JButton delScreen = new JButton("✕");
        delScreen.setToolTipText("Удалить выбранный экран");
        delScreen.addActionListener(e -> deleteSelectedScreen.run());
        UiKit.bindDeleteKey(screenList, deleteSelectedScreen);
        screenBtnRow.add(add, BorderLayout.CENTER);
        screenBtnRow.add(delScreen, BorderLayout.EAST);
        body.add(UiKit.vgap());
        body.add(screenBtnRow);

        JButton arrange = new JButton("Расставить экраны без наложения");
        arrange.setToolTipText("Перестроит X/Y всех экранов сцены в ряд, чтобы они не перекрывались на «Визуализации»");
        arrange.addActionListener(e -> model.autoArrangeScreensInScene());
        body.add(UiKit.vgap());
        body.add(arrange);

        JLabel hint = UiKit.muted("<html>Параметры нового экрана — справа, в «Параметры экрана».</html>");
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
        int n = model.getCurrentScene() != null ? model.getCurrentScene().getScreens().size() + 1 : 1;
        double[] pos = model.suggestedNextPosition(model.getCabinetTypes().get(0).getId(), 3);
        com.vjstb.ledscheme.ui.NewScreenDialog dialog = new com.vjstb.ledscheme.ui.NewScreenDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), model.getCabinetTypes(), "Экран " + n,
                pos[0], pos[1]);
        com.vjstb.ledscheme.ui.NewScreenDialog.Result r = dialog.showDialog();
        if (r == null) {
            return;
        }
        try {
            Screen s = model.addScreen(r.name(), r.cabinetTypeId(), r.rows(), r.cols(), r.posX(), r.posY());
            model.selectScreen(s);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- прериг ----

    private JPanel buildPrerig() {
        JPanel stats = UiKit.vbox();
        stats.add(statRow("Экранов", prerigScreens));
        stats.add(statRow("Кабинетов", prerigCabinets));
        stats.add(statRow("Мощность сцены", prerigPower));
        stats.add(statRow("Вес сцены", prerigWeight));
        stats.add(UiKit.vgap());

        // Мини-превью раскладки сцены: показывает все экраны сцены сразу, но
        // активным (выделенным) становится только выбранный в списке слева —
        // остальные притушены (setCompact(true)), без подписей и метража. Точки
        // подвеса (жёлтые треугольники по верхнему краю) рисуются здесь же — сама
        // SceneCanvasPanel решает это по mountType/riggingPointsCount экрана.
        // Обёрнут в свой JScrollPane (Task #7/v1.6, доработка после баг-репорта) —
        // нужен для панорамирования, когда включён детальный режим с масштабом
        // (см. ниже), и позволяет холсту растягиваться по всей высоте, которую
        // выделит ему разделитель prerigSplit, а не быть жёстко зафиксированным
        // на 220px — тесно для точной расстановки отдельных кабинетов.
        prerigPreview.setCompact(true);
        prerigPreview.setPreferredSize(new Dimension(10, 220));
        prerigPreview.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        javax.swing.JScrollPane prerigScroll = new javax.swing.JScrollPane(prerigPreview);
        prerigScroll.getVerticalScrollBar().setUnitIncrement(16);
        prerigScroll.getHorizontalScrollBar().setUnitIncrement(16);
        prerigScroll.setPreferredSize(new Dimension(10, 260));

        // Task #7/v1.6: экран целиком можно перетаскивать мышью прямо в превью выше
        // (координаты X/Y обновляются автоматически) без переключения в этот режим —
        // он нужен только чтобы дотянуться до ОТДЕЛЬНЫХ кабинетов (в свёрнутом виде
        // виден только прямоугольник экрана целиком, кабинеты внутри не различить и
        // не кликнуть).
        javax.swing.JCheckBox showCabinetsCheck = new javax.swing.JCheckBox(
                "Кабинеты по отдельности (можно двигать каждый, Shift — прилипание к соседям,"
                        + " Ctrl+колесо — масштаб)");
        showCabinetsCheck.addActionListener(e -> {
            boolean detail = showCabinetsCheck.isSelected();
            // fitToViewport=false в детальном режиме — иначе Ctrl+колесо (масштаб,
            // см. mouseWheelMoved) не работает вовсе, а именно масштаб и нужен для
            // точной расстановки отдельных кабинетов (баг-репорт: без масштаба
            // порог привязки к соседям физически недостижим мышью при мелком виде).
            prerigPreview.setDetailMode(detail, true, !detail);
            prerigPreview.revalidate();
            prerigPreview.repaint();
        });

        JPanel canvasArea = new JPanel(new BorderLayout());
        canvasArea.add(stats, BorderLayout.NORTH);
        canvasArea.add(prerigScroll, BorderLayout.CENTER);
        JPanel canvasFooter = UiKit.vbox();
        canvasFooter.add(UiKit.vgap(6));
        canvasFooter.add(showCabinetsCheck);
        canvasArea.add(canvasFooter, BorderLayout.SOUTH);

        JPanel riggingArea = UiKit.vbox();
        riggingArea.add(UiKit.formRow("Точек подвеса", pRiggingPoints));
        MathFields.enableExpressions(pRiggingPoints);
        riggingArea.add(UiKit.vgap());
        riggingArea.add(UiKit.formRow("Заметки по подвесу", pRiggingNotes));
        riggingArea.add(UiKit.vgap());

        calcRiggingBtn.setToolTipText("Активно только для экрана с монтажом «Подвес» — способ монтажа задаётся"
                + " в «Параметры экрана».");
        calcRiggingBtn.addActionListener(e -> calculateRiggingPoints());
        riggingArea.add(calcRiggingBtn);
        riggingArea.add(UiKit.vgap());
        riggingArea.add(UiKit.muted("<html>Пересчитывает точки подвеса по формуле из риг-тех таблиц (по ширине экрана),"
                + " рисует их на схеме выше и сохраняет схему отдельной картинкой.</html>"));

        // Разделитель, а не фиксированная высота (Task #7/v1.6, доработка) — тянуть
        // можно мышью за границу, «растягивая» окно прерига под текущую задачу
        // (обзор всей сцены сразу или точная расстановка одного экрана).
        JSplitPane prerigSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvasArea, riggingArea);
        prerigSplit.setContinuousLayout(true);
        prerigSplit.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "setup.prerigCanvas", prerigSplit, 0.68);

        return (JPanel) UiKit.section("Прериг сцены", prerigSplit);
    }

    /** Пересчитывает точки подвеса выбранного экрана, обновляет их на схеме сцены
     *  (мини-превью прерига) и сохраняет эту схему отдельным PNG в папку вывода
     *  проекта/сцены — «нельзя экспортировать то, что нельзя сначала увидеть». */
    private void calculateRiggingPoints() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        int suggested = com.vjstb.ledscheme.service.ScreenLogic.suggestRiggingPoints(scr);
        model.updateScreenMount(scr, scr.getMountType(), suggested, scr.getRiggingNotes());
        pRiggingPoints.setValue(suggested);
        prerigPreview.revalidate();
        prerigPreview.repaint();

        try {
            java.io.File folder = com.vjstb.ledscheme.ui.OutputPaths.defaultFolder(
                    model.getCurrentProject(), model.getCurrentScene());
            java.io.File out = new java.io.File(folder,
                    "rigging_" + com.vjstb.ledscheme.ui.OutputPaths.sanitize(scr.getName()) + ".png");
            java.awt.image.BufferedImage img = prerigPreview.renderImage(900, 500);
            javax.imageio.ImageIO.write(img, "png", out);
            JOptionPane.showMessageDialog(this, "Точек подвеса: " + suggested
                    + "\nСхема сохранена: " + out.getAbsolutePath(), "Готово", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- параметры экрана ----

    private JPanel buildScreenParams() {
        JPanel body = UiKit.vbox();
        pType.setRenderer(new CabinetTypeRenderer());
        // formRow — подпись сверху, поле снизу, на всю доступную ширину секции —
        // вместо GridLayout(0,2), который заставлял обе колонки быть шириной самого
        // широкого элемента сетки (в т.ч. длинных подписей) и просто обрезал их,
        // не умея сжиматься в узком окне.
        body.add(UiKit.formRow("Название", pName));
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Кабинет", pType));
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Колонны", pCols));
        MathFields.enableExpressions(pCols);
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Строки", pRows));
        MathFields.enableExpressions(pRows);

        body.add(UiKit.vgap(10));
        body.add(UiKit.formRow("X (мм)", pX));
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Y (мм)", pY));

        body.add(UiKit.vgap(10));
        body.add(UiKit.formRow("Способ монтажа", pMountType));
        // Точки подвеса/заметки и авторасчёт переехали в прериг сцены (там же, где
        // сводка по весу и мини-превью раскладки) — тип монтажа остаётся здесь.
        pMountType.addActionListener(e -> {
            if (refreshing) return;
            Screen scr = model.getCurrentScreen();
            if (scr == null) return;
            if (pMountType.getSelectedItem() == com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                    && (Integer) pRiggingPoints.getValue() == 0) {
                pRiggingPoints.setValue(com.vjstb.ledscheme.service.ScreenLogic.suggestRiggingPoints(scr));
            }
            calcRiggingBtn.setEnabled(pMountType.getSelectedItem() == com.vjstb.ledscheme.model.ScreenMountType.RIGGED);
        });

        body.add(UiKit.vgap(10));
        body.add(UiKit.formRow("Герцовка контента", pRefreshHz));
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Глубина цвета, бит", pBitDepth));
        body.add(UiKit.vgap());
        body.add(UiKit.muted("<html>Влияют на реальную ёмкость порта контроллера в пикселях"
                + " (см. этап «Сигнал»).</html>"));

        // Единая кнопка «Применить» вместо 4 разных — несколько похожих кнопок
        // подряд только путали (какая из них что именно сохраняет).
        JButton apply = new JButton("Применить настройки экрана");
        apply.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            CabinetType type = (CabinetType) pType.getSelectedItem();
            if (scr == null || type == null) return;
            try {
                model.updateScreenGrid(scr, orDefault(pName.getText(), scr.getName()), type.getId(),
                        (Integer) pRows.getValue(), (Integer) pCols.getValue());
                model.updateScreenPosition(scr, parseDouble(pX.getText()), parseDouble(pY.getText()));
                com.vjstb.ledscheme.model.ScreenMountType mt =
                        (com.vjstb.ledscheme.model.ScreenMountType) pMountType.getSelectedItem();
                model.updateScreenMount(scr, mt, (Integer) pRiggingPoints.getValue(), pRiggingNotes.getText());
                model.updateScreenSignalSpec(scr, (Integer) pRefreshHz.getSelectedItem(),
                        (Integer) pBitDepth.getSelectedItem());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        body.add(UiKit.vgap());
        body.add(apply);

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

        shapeHint.setForeground(Palette.MUTED);
        shapeHint.setText(UiKit.wrapHtml("<html>Клик (или протяжка ЛКМ) по ячейке — исключить/включить (так задаётся не"
                + " прямоугольная форма экрана). ПКМ (зажать и повести к пункту) — скрыть/восстановить,"
                + " изменить форму или тип конкретной ячейки. Ctrl+колесо — масштаб.</html>"));
        body.add(shapeHint);

        body.add(UiKit.vgap());
        shapeScroll.setBorder(javax.swing.BorderFactory.createLineBorder(Palette.BORDER));
        shapeScroll.setPreferredSize(new Dimension(200, 220));
        shapeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        body.add(shapeScroll);

        return (JPanel) UiKit.section("Форма экрана", body);
    }

    // ---- rebuild ----

    /** Публичный вход — выполняет пересборку отложенно (invokeLater), как и
     *  MainFrame.refresh(): вызывается синхронно из колбэков выбора в JList
     *  (ProjectList/SceneList и т.д.), и без отсрочки видимость вложенных секций
     *  внутри JSplitPane иногда не перерисовывалась сразу — только при следующем
     *  взаимодействии с интерфейсом (свайп/ресайз/клик где-то ещё). */
    public void rebuild() {
        SwingUtilities.invokeLater(this::doRebuild);
    }

    private void doRebuild() {
        refreshing = true;
        try {
            syncList(projModel, model.getProjects());
            projList.setSelectedValue(model.getCurrentProject(), true);
            // ListSizing.fit() даёт списку ЯВНЫЙ preferred/maximum размер по числу строк —
            // без этого JList с многострочным HTML-рендерером (NamedRenderer) иногда
            // на раннем layout-проходе (пока ширина ещё не установлена окончательно)
            // сообщает совершенно неверный preferred height; в JSplitPane это провоцирует
            // собственный внутренний пересчёт позиции разделителя (BasicSplitPaneUI),
            // раздувающий секцию со списком почти на всю высоту соседнего сплита.
            ListSizing.fit(projList, projScroll, 2, 6);

            boolean hasProject = model.getCurrentProject() != null;
            UiKit.setSectionVisible(scenesSection, hasProject, leftSplit2, settings, "setup.scenes", 0.18);
            if (hasProject) {
                syncList(sceneModel, model.getCurrentProject().getScenes());
                sceneList.setSelectedValue(model.getCurrentScene(), true);
                ListSizing.fit(sceneList, sceneScroll, 2, 6);
            }

            boolean hasScene = model.getCurrentScene() != null;
            Screen scr = model.getCurrentScreen();
            // screensSection и paramsSection — оба потомки ОДНОГО leftSplit3. Если оба
            // становятся видимы в одном и том же проходе doRebuild() (например,
            // восстановление выбора и сцены, и экрана из сохранённого состояния при
            // открытии проекта), нельзя вызывать setSectionVisible/restoreDividerProportion
            // по отдельности для каждого — см. javadoc restoreDividerProportion: два
            // независимых invokeLater гонятся с внутренним пересчётом JSplitPane и в сумме
            // дают абсурдный результат. Раньше это и происходило (баг-репорт: кнопка
            // «Применить настройки экрана» иногда залипает — видна, но нулевой высоты,
            // пока пользователь не потянет окно руками). Переключаем видимость обеих
            // секций напрямую и вызываем restoreDividerProportion ОДИН раз, если хотя бы
            // одна из них только что стала видимой.
            boolean screensWasVisible = screensSection.isVisible();
            boolean paramsWasVisible = paramsSection.isVisible();
            screensSection.setVisible(hasScene);
            paramsSection.setVisible(scr != null);
            if ((hasScene && !screensWasVisible) || (scr != null && !paramsWasVisible)) {
                UiKit.restoreDividerProportion(leftSplit3, settings, "setup.screensParams", 0.32);
            }
            prerigSection.setVisible(hasScene);
            if (hasScene) {
                syncList(screenModel, model.getCurrentScene().getScreens());
                screenList.setSelectedValue(model.getCurrentScreen(), true);
                ListSizing.fit(screenList, screenScroll, 2, 8);
                rebuildPrerig();
            }

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
                pRefreshHz.setSelectedItem(scr.getRefreshRateHz());
                pBitDepth.setSelectedItem(scr.getColorBitDepth());
            }
            calcRiggingBtn.setEnabled(scr != null
                    && scr.getMountType() == com.vjstb.ledscheme.model.ScreenMountType.RIGGED);

        } finally {
            refreshing = false;
        }
        shapeEditor.revalidate();
        shapeEditor.repaint();
        // Явно на каждом разделителе, чьи дети (Сцены/Экраны/Параметры) только что
        // поменяли видимость — иначе место под них не пересчитывается немедленно
        // (revalidate() на верхнем this не всегда достаточен).
        for (JSplitPane sp : new JSplitPane[]{leftSplit3, leftSplit2, leftSplitNav}) {
            sp.revalidate();
            sp.repaint();
        }
        revalidate();
        repaint();
    }

    private void rebuildPrerig() {
        SceneStats s = model.currentSceneStats();
        if (s == null) return;
        prerigScreens.setText(String.valueOf(s.screenCount()));
        prerigCabinets.setText(s.totalCabinetCount() + cabinetBreakdownSuffix(s));
        prerigPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
        prerigWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
    }

    /** «(Base: 96, Heavy: 32)» — по одному типу на позицию, нулевые (их и не должно
     *  быть в карте) не выводятся; для одного типа или пустой сцены — ничего. */
    private static String cabinetBreakdownSuffix(SceneStats s) {
        if (s.cabinetCountByType().size() < 2) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" (");
        boolean first = true;
        for (var entry : s.cabinetCountByType().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey().getName()).append(": ").append(entry.getValue());
            first = false;
        }
        sb.append(')');
        return sb.length() > 3 ? sb.toString() : "";
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
        Double v = com.vjstb.ledscheme.ui.MathExpr.tryEval(s);
        return v != null ? v : 0;
    }
}
