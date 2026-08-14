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
    private final JButton toggleShapeBtn = new JButton("Изменить форму экрана");
    /** «Форма экрана» раньше показывалась ВСЕГДА, пока выбран экран — редактор нужен
     *  редко (только вырезание/переформовка ячеек), а место в правой колонке отнимал
     *  постоянно (баг-репорт: "мешается"). Теперь секция скрыта по умолчанию и
     *  появляется только по кнопке {@link #toggleShapeBtn} — см. {@code refresh()}
     *  (условие {@code scr != null && shapeEditorRequested}). НЕ персистентно
     *  (сбрасывается на скрыто при каждом запуске) — сознательно, это разовое
     *  действие "хочу поправить форму", а не постоянный режим работы. */
    private boolean shapeEditorRequested = false;

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
    private final JSpinner pRiggingSafetyFactor = new JSpinner(new SpinnerNumberModel(5.0, 1.0, 20.0, 0.5));
    /** Пусто — грузоподъёмность лебёдки не указана (см. Screen#getRiggingHoistCapacityKg). */
    private final JTextField pRiggingHoistCapacity = new JTextField(10);
    /** Библиотечная модель лебёдки (см. HoistType/Screen#getRiggingHoistTypeId) —
     *  {@code null} = «Ввести вручную», тогда действует {@link #pRiggingHoistCapacity}. */
    private final JComboBox<com.vjstb.ledscheme.model.HoistType> pRiggingHoistType = new JComboBox<>();
    private final JComboBox<Integer> pRefreshHz = new JComboBox<>(new Integer[]{50, 60, 120, 144, 240});
    private final JComboBox<Integer> pBitDepth = new JComboBox<>(new Integer[]{8, 10, 12});

    // ---- наземный конструктив (см. service.StructureCalc, STRUCTURE_CALC_NOTES.md) ----
    private final JButton calcStructureBtn = new JButton("Рассчитать конструктив");
    private final JSpinner pStructureTowerHeight = new JSpinner(new SpinnerNumberModel(3000.0, 0.0, 50_000.0, 100.0));
    private final JSpinner pStructureTowerSpacing = new JSpinner(new SpinnerNumberModel(1000.0, 100.0, 50_000.0, 50.0));
    private final JSpinner pStructureTowerCount = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JSpinner pStructureVerticalFrames = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner pStructurePeremychkaLevels = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner pStructureExtendedBaseSections = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
    private final javax.swing.JCheckBox pStructureIncludeBase = new javax.swing.JCheckBox("Базовая (опорная) рама под каждой башней", true);
    private final JComboBox<com.vjstb.ledscheme.model.StructureFrameType> pStructureFrameType = new JComboBox<>();
    private final JComboBox<com.vjstb.ledscheme.model.StructureFrameType> pStructureShortFrameType = new JComboBox<>();
    private final JComboBox<com.vjstb.ledscheme.model.StructureFrameType> pStructureCupType = new JComboBox<>();
    private final JComboBox<com.vjstb.ledscheme.model.StructureFrameType> pStructureBallastType = new JComboBox<>();
    /** 0 = экран стоит на земле. */
    private final JTextField pStructureScreenElevation = new JTextField(6);
    private final JTextField pStructureNotes = new JTextField(10);
    private final JButton toggle3DBtn = new JButton("Показать 3D");
    /** Отдельное всплывающее окно (не встроенная панель — баг-репорт "вынеси в отдельное
     *  окно, как калькулятор видеотайминга", см. {@code ui.Structure3DDialog}), ленивое —
     *  GL-контекст создаётся только по первому нажатию {@link #toggle3DBtn}. {@code null} —
     *  ещё не открывалось в этой сессии редактора экрана. */
    private com.vjstb.ledscheme.ui.Structure3DDialog structure3DDialog;

    /** Поля подвеса и поля конструктива показываются ТОЛЬКО для своего способа монтажа
     *  (см. refresh()/{@link #applyMountTypeVisibility}) — баг-репорт: с обоими блоками
     *  видимыми одновременно всегда «Прериг сцены» разрасталась настолько, что кнопки
     *  расчёта уезжали за пределы окна без прокрутки, а сам способ монтажа экрана делает
     *  осмысленным ровно ОДИН из двух блоков за раз. */
    private JPanel riggingFieldsPanel;
    private JPanel structureFieldsPanel;

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
    private final JSplitPane rightSplit;
    private JSplitPane prerigSplit;

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
        prerigPreview = new com.vjstb.ledscheme.ui.SceneCanvasPanel(model, settings);
        prerigPreview.setShowRiggingPoints(true);
        prerigSection = buildPrerig();
        shapeEditor = new ShapeEditorPanel(model, settings);
        shapeScroll = new JScrollPane(shapeEditor);
        shapeSection = buildShapeEditor();
        // Одноразовая миграция: до фикса UiKit.stretchToViewport «Форма экрана» была
        // жёстко зажата потолком 220px, поэтому у части пользователей могла осесть
        // сильно перекошенная доля разделителя (например, 0.8 — руками растягивали
        // «Прериг сцены», т.к. отдавать место «Форме экрана» сверх 220px всё равно
        // было бессмысленно из-за потолка). Теперь потолка нет — сбрасываем один раз,
        // дальше ручной выбор пользователя не трогаем (см. флаг ниже). Дефолт 0.58,
        // не 0.5 — «Прериг сцены» (мини-схема раскладки) обычно нужнее просторнее,
        // чем «Форма экрана» (баг-репорт: окно визуализации прерига слишком мелкое).
        if (settings.getLayoutProportion("setup.prerigShape.migratedV2", 0) == 0) {
            settings.setLayoutProportion("setup.prerigShape", 0.58);
            settings.setLayoutProportion("setup.prerigShape.migratedV2", 1);
        }
        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, prerigSection, shapeSection);
        rightSplit.setContinuousLayout(true);
        rightSplit.setResizeWeight(0.5);
        UiKit.persistentDivider(settings, "setup.prerigShape", rightSplit, 0.58);

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        // stretchToViewport(rightSplit) -- см. javadoc метода: без этой обёртки JSplitPane
        // не растягивается на высоту окна внутри JScrollPane (сам не Scrollable), из-за
        // чего «Прериг сцены»/«Форма экрана» вычисляли начальную долю разделителя от
        // заниженной высоты и «Форма экрана» пропадала/появлялась крошечной (баг-репорт).
        JScrollPane rightScroll = new JScrollPane(UiKit.stretchToViewport(rightSplit));
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
        // Переключатель Вт/кВт (Персонализация) не меняет модель — без этого подписчика
        // prerigPower оставался бы со старым текстом до следующего model-триггерного
        // rebuild (баг-репорт: галочка включена, но «Мощность сцены» не поменялась).
        settings.addListener(this::rebuild);
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

        // Кнопка формы экрана -- не привязана к способу монтажа, видна всегда (в отличие
        // от rigging/structure блоков ниже, каждый из которых имеет смысл только для
        // СВОЕГО mountType).
        toggleShapeBtn.setToolTipText("Показать/скрыть редактор формы экрана (вырезание ячеек, арки и т.п.) —"
                + " по умолчанию скрыт, чтобы не занимать место, когда форма не редактируется.");
        toggleShapeBtn.addActionListener(e -> {
            shapeEditorRequested = !shapeEditorRequested;
            rebuild();
        });
        JPanel alwaysVisibleButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        alwaysVisibleButtonsRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        alwaysVisibleButtonsRow.add(toggleShapeBtn);
        riggingArea.add(alwaysVisibleButtonsRow);
        riggingArea.add(UiKit.vgap(10));

        riggingFieldsPanel = UiKit.vbox();
        riggingFieldsPanel.add(UiKit.formRow("Точек подвеса", pRiggingPoints));
        MathFields.enableExpressions(pRiggingPoints);
        riggingFieldsPanel.add(UiKit.vgap());
        riggingFieldsPanel.add(UiKit.formRow("Заметки по подвесу", pRiggingNotes));
        riggingFieldsPanel.add(UiKit.vgap());
        riggingFieldsPanel.add(UiKit.formRow("Мин. коэфф. запаса прочности оборудования", pRiggingSafetyFactor));
        riggingFieldsPanel.add(UiKit.vgap());
        pRiggingHoistType.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText("Ввести вручную…");
                } else if (value instanceof com.vjstb.ledscheme.model.HoistType h) {
                    setText(h.getName() + " — WLL " + UiKit.fmt(h.getWllKg()) + " кг");
                }
                return c;
            }
        });
        pRiggingHoistType.setToolTipText("Модель лебёдки/тали из общей библиотеки — грузоподъёмность (WLL) берётся"
                + " из паспортного значения записи. «Ввести вручную…» — использовать поле ниже.");
        pRiggingHoistType.addActionListener(e -> {
            if (refreshing) return;
            pRiggingHoistCapacity.setEnabled(pRiggingHoistType.getSelectedItem() == null);
        });
        riggingFieldsPanel.add(UiKit.formRow("Модель лебёдки (библиотека)", pRiggingHoistType));
        riggingFieldsPanel.add(UiKit.vgap());
        pRiggingHoistCapacity.setToolTipText("Грузоподъёмность (WLL) выбранной лебёдки/тали, кг — одна модель на"
                + " все точки этого экрана. Пусто — не проверять превышение, только показать нагрузку."
                + " Игнорируется, если выше выбрана модель из библиотеки.");
        riggingFieldsPanel.add(UiKit.formRow("Грузоподъёмность лебёдки, кг (вручную)", pRiggingHoistCapacity));
        riggingFieldsPanel.add(UiKit.vgap());

        calcRiggingBtn.setToolTipText("Способ монтажа задаётся в «Параметры экрана».");
        calcRiggingBtn.addActionListener(e -> calculateRiggingPoints());
        riggingFieldsPanel.add(calcRiggingBtn);
        riggingArea.add(riggingFieldsPanel);
        riggingArea.add(UiKit.vgap(10));

        structureFieldsPanel = UiKit.vbox();
        structureFieldsPanel.add(UiKit.formRow("Высота башни, мм", pStructureTowerHeight));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Шаг башен, мм", pStructureTowerSpacing));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Башен", pStructureTowerCount));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Сегментов рамы на башню (в каждом из 2 рядов)", pStructureVerticalFrames));
        structureFieldsPanel.add(UiKit.vgap());
        pStructurePeremychkaLevels.setToolTipText("Число уровней перемычек (передний↔задний ряд внутри одной"
                + " башни) по высоте — стартовое предложение по интервалу из Персонализации, конкретная"
                + " расстановка редактируется поячеечно прямо в 3D-превью.");
        structureFieldsPanel.add(UiKit.formRow("Уровней перемычек", pStructurePeremychkaLevels));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureExtendedBaseSections.setToolTipText("Доп. секции выноса базовой рамы назад (каждая — ширина"
                + " короткой рамы, ~0.5м) сверх обязательного ядра под объёмом башни — увеличивают площадь опоры"
                + " под балласт.");
        structureFieldsPanel.add(UiKit.formRow("Доп. секций выноса под балласт", pStructureExtendedBaseSections));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureIncludeBase.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        structureFieldsPanel.add(pStructureIncludeBase);
        structureFieldsPanel.add(UiKit.vgap());

        setStructureFrameRenderer(pStructureFrameType);
        setStructureFrameRenderer(pStructureShortFrameType);
        setStructureFrameRenderer(pStructureCupType);
        setStructureFrameRenderer(pStructureBallastType);
        structureFieldsPanel.add(UiKit.formRow("Тип рамы (библиотека)", pStructureFrameType));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Тип короткой рамы (библиотека)", pStructureShortFrameType));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Тип стакана (библиотека)", pStructureCupType));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Тип контейнера балласта (библиотека)", pStructureBallastType));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureScreenElevation.setToolTipText("Высота нижнего края экрана над землёй, мм — 0 = экран стоит на"
                + " земле. Влияет только на отображение в 3D (виден зазор между истинной землёй и приподнятым"
                + " экраном), НЕ на проверку «высота башни < высоты экрана».");
        structureFieldsPanel.add(UiKit.formRow("Подъём экрана от земли, мм", pStructureScreenElevation));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Заметки по конструктиву", pStructureNotes));
        structureFieldsPanel.add(UiKit.vgap());

        calcStructureBtn.setToolTipText("Способ монтажа задаётся в «Параметры экрана». Требует независимой"
                + " инженерной перепроверки перед монтажом — см. STRUCTURE_CALC_NOTES.md.");
        calcStructureBtn.addActionListener(e -> calculateStructure());
        structureFieldsPanel.add(calcStructureBtn);
        structureFieldsPanel.add(UiKit.vgap());

        toggle3DBtn.setToolTipText("Открыть/закрыть отдельное окно с 3D-превью посчитанного конструктива"
                + " (только просмотр -- числа по-прежнему редактируются полями выше; клик по существующей"
                + " детали в 3D убирает её, по подсвеченному \"призраку\" -- добавляет). Требует OpenGL, на"
                + " некоторых системах может быть недоступно -- тогда вместо картинки покажется сообщение.");
        toggle3DBtn.addActionListener(e -> {
            if (structure3DDialog != null && structure3DDialog.isShowing()) {
                structure3DDialog.dispose();
                return;
            }
            structure3DDialog = new com.vjstb.ledscheme.ui.Structure3DDialog(topWindow(), model);
            // Сброс текста кнопки, если окно закрыли крестиком, а не этой же кнопкой --
            // иначе кнопка молча осталась бы говорить «Скрыть 3D» для уже закрытого окна.
            structure3DDialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    toggle3DBtn.setText("Показать 3D");
                }
            });
            structure3DDialog.setVisible(true);
            toggle3DBtn.setText("Скрыть 3D");
        });
        structureFieldsPanel.add(toggle3DBtn);
        riggingArea.add(structureFieldsPanel);
        riggingArea.add(UiKit.vgap());

        // Собственный JScrollPane для riggingArea (тот же приём, что у canvasArea/
        // prerigScroll и shapeSection/shapeScroll выше) -- riggingArea НЕ Scrollable и
        // ничем не ограничена по высоте (поля подвеса/конструктива), а prerigSplit может
        // выделить ей меньше места, чем нужно для показа всего сразу. Без своего скролла
        // содержимое, не поместившееся в отведённую разделителем высоту, было бы просто
        // обрезано без какого-либо способа до него докрутить -- баг-репорт «окно 3D
        // появляется где-то за рамками, не могу открыть» (актуально ДО того, как 3D
        // переехало в отдельное окно, см. Structure3DDialog, но остальным полям блока
        // конструктива этот скролл всё ещё нужен), см. также javadoc dynamicSection выше.
        JScrollPane riggingScroll = new JScrollPane(riggingArea);
        riggingScroll.setBorder(null);
        riggingScroll.getVerticalScrollBar().setUnitIncrement(16);
        riggingScroll.setMinimumSize(new Dimension(120, 90));

        // Разделитель, а не фиксированная высота (Task #7/v1.6, доработка) — тянуть
        // можно мышью за границу, «растягивая» окно прерига под текущую задачу
        // (обзор всей сцены сразу или точная расстановка одного экрана). Поле (не
        // локальная переменная) — doRebuild() явно перевосстанавливает его долю
        // ПОСЛЕ rightSplit (см. там же) — иначе он вычисляет начальную позицию от
        // ещё маленькой высоты prerigSection (тот же класс гонки, что и у самого
        // rightSplit) и "запекает" сплюснутый холст навсегда (баг-репорт: окно
        // визуализации прерига не растягивается по высоте, хотя место есть).
        prerigSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvasArea, riggingScroll);
        prerigSplit.setContinuousLayout(true);
        prerigSplit.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "setup.prerigCanvas", prerigSplit, 0.68);

        // dynamicSection, НЕ section -- содержимое riggingArea меняется уже ПОСЛЕ
        // первой сборки (видимость riggingFieldsPanel/structureFieldsPanel по способу
        // монтажа, лениво добавляемый structure3DContainer при первом «Показать 3D»).
        // section() фиксирует maximumSize ОДИН раз при конструировании -- если бы мы
        // использовали её тут, эта высота навсегда осталась бы такой, какой была ДО
        // появления 3D-панели, а BasicSplitPaneUI ограничивает перетаскивание
        // разделителя ИМЕННО этим maximumSize (см. getMaximumDividerLocation) -- ни
        // прокрутка, ни ручное перетаскивание разделителя не могли бы после этого
        // показать вновь появившееся содержимое (баг-репорт: «окно 3D появляется где-то
        // за рамками, не могу открыть»). Вместо этого пересчитываем потолок явно в
        // applyMountTypeVisibility() и в обработчике toggle3DBtn -- см. там же.
        return (JPanel) UiKit.dynamicSection("Прериг сцены", prerigSplit);
    }

    /** Показывает ТОЛЬКО блок полей, осмысленный для {@code mountType} (rigging для
     *  RIGGED, конструктив для STRUCTURE, ни один — для LAYER/FLOOR/{@code null}) —
     *  баг-репорт: оба блока видимыми одновременно разрастались настолько, что кнопки
     *  расчёта уезжали за пределы окна прерига без прокрутки. */
    private void applyMountTypeVisibility(com.vjstb.ledscheme.model.ScreenMountType mountType) {
        riggingFieldsPanel.setVisible(mountType == com.vjstb.ledscheme.model.ScreenMountType.RIGGED);
        structureFieldsPanel.setVisible(mountType == com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE);
        riggingFieldsPanel.revalidate();
        structureFieldsPanel.revalidate();
        if (prerigSplit != null) {
            prerigSplit.revalidate();
            prerigSplit.repaint();
        }
        // См. javadoc dynamicSection в buildPrerig() -- потолок высоты «Прериг сцены»
        // должен пересчитываться при каждой смене видимого блока полей, иначе он
        // останется таким, каким был ДО переключения (слишком маленьким или слишком
        // большим), и разделитель rightSplit физически не даст показать содержимое.
        if (prerigSection != null) {
            UiKit.recapHeight(prerigSection);
        }
    }

    /** Пересчитывает точки подвеса выбранного экрана (количество — см.
     *  {@code ScreenLogic#suggestRiggingPoints}, нагрузку по каждой точке — см.
     *  {@link com.vjstb.ledscheme.service.RiggingCalc#compute}, читает всегда
     *  СВЕЖИЙ вес типов кабинетов — правка веса типа сразу отражается на
     *  следующем расчёте, ничего не кэшируется), обновляет их на схеме сцены
     *  (мини-превью прерига) и сохраняет ОТДЕЛЬНУЮ схему PNG в папку вывода
     *  проекта/сцены через {@link com.vjstb.ledscheme.ui.RiggingSchemaImageWriter}
     *  — сетка ЯЧЕЕК экрана (не общий вид сцены сверху, тот не показывал отдельные
     *  кабинеты/вырезы — баг-репорт «экспортируемая схема не информативна») с
     *  точками подвеса на верхней кромке и таблицей нагрузки по каждой точке снизу. */
    private void calculateRiggingPoints() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        double safetyFactor = ((Number) pRiggingSafetyFactor.getValue()).doubleValue();
        Double hoistCapacity = parseHoistCapacity();
        String hoistTypeId = selectedHoistTypeId();
        // suggestRiggingPoints должен видеть ИМЕННО то, что выбрано на форме сейчас
        // (лебёдку/грузоподъёмность пользователь мог только что поменять и ещё не
        // сохранить) -- считаем на ЧЕРНОВОЙ копии экрана, не трогая сохранённый scr,
        // иначе первое нажатие «Рассчитать» после смены лебёдки использовало бы
        // старое значение (баг-репорт: «при перерасчёте количество лебёдок не меняется»).
        Screen preview = scr.copy();
        preview.setRiggingHoistCapacityKg(hoistCapacity);
        preview.setRiggingHoistTypeId(hoistTypeId);
        int suggested = com.vjstb.ledscheme.service.ScreenLogic.suggestRiggingPoints(
                preview, model.typeOf(scr), model.getWorkspace());
        model.updateScreenMount(scr, scr.getMountType(), suggested, scr.getRiggingNotes(), safetyFactor, hoistCapacity,
                hoistTypeId);
        pRiggingPoints.setValue(suggested);
        prerigPreview.revalidate();
        prerigPreview.repaint();

        com.vjstb.ledscheme.service.RiggingCalc.Result result = com.vjstb.ledscheme.service.RiggingCalc.compute(
                scr, model.typeOf(scr), model.getWorkspace(), suggested);
        StringBuilder loadMsg = new StringBuilder();
        loadMsg.append(String.format("Точек подвеса: %d%n", suggested));
        loadMsg.append(String.format("Вес кабинетов: %.1f кг, с наценкой на крепёж (+%d%%): %.1f кг%n",
                result.totalCabinetWeightKg(), (int) Math.round(com.vjstb.ledscheme.service.RiggingCalc.HARDWARE_ALLOWANCE * 100),
                result.totalWeightWithHardwareKg()));
        loadMsg.append(String.format("Требуемая min WLL на точку: %.1f кг (мин. коэфф. запаса оборудования %.1f:1)%n",
                result.requiredWllPerPointKg(), safetyFactor));
        boolean anyOver = false;
        for (com.vjstb.ledscheme.service.RiggingCalc.PointLoad p : result.points()) {
            loadMsg.append(String.format("  Точка %d: %.1f кг%s%n", p.index() + 1, p.loadKg(),
                    p.overCapacity() ? " — ПРЕВЫШЕНИЕ грузоподъёмности лебёдки!" : ""));
            anyOver |= p.overCapacity();
        }

        try {
            java.io.File folder = com.vjstb.ledscheme.ui.OutputPaths.defaultFolder(
                    model.getCurrentProject(), model.getCurrentScene());
            java.io.File out = new java.io.File(folder,
                    "rigging_" + com.vjstb.ledscheme.ui.OutputPaths.sanitize(scr.getName()) + ".png");
            java.awt.image.BufferedImage img = com.vjstb.ledscheme.ui.RiggingSchemaImageWriter.render(
                    scr, model.typeOf(scr), result);
            javax.imageio.ImageIO.write(img, "png", out);
            loadMsg.append("\nСхема сохранена: ").append(out.getAbsolutePath());
            JOptionPane.showMessageDialog(this, loadMsg.toString(), "Готово",
                    anyOver ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Пусто/некорректно — считаем, что грузоподъёмность лебёдки не указана (тот же
     *  паттерн, что у остальных опциональных числовых текстовых полей проекта). */
    private Double parseHoistCapacity() {
        String text = pRiggingHoistCapacity.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.'));
            return v > 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** {@code null} -- выбрано «Ввести вручную» (см. {@link #pRiggingHoistType}), тогда
     *  действует {@link #parseHoistCapacity()}. */
    private String selectedHoistTypeId() {
        Object sel = pRiggingHoistType.getSelectedItem();
        return sel instanceof com.vjstb.ledscheme.model.HoistType h ? h.getId() : null;
    }

    /** Пусто/некорректно -- 0 (экран на земле, см. Screen#getStructureScreenElevationMm). */
    private double parseScreenElevation() {
        String text = pStructureScreenElevation.getText();
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.'));
            return Math.max(0, v);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void setStructureFrameRenderer(JComboBox<com.vjstb.ledscheme.model.StructureFrameType> combo) {
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText("Не выбрано");
                } else if (value instanceof com.vjstb.ledscheme.model.StructureFrameType t) {
                    setText(t.getName());
                }
                return c;
            }
        });
    }

    /** Заполняет комбобокс элементами библиотеки конструктива указанного вида (одна
     *  библиотека {@code StructureFrameType} на все 4 вида, см. class-javadoc модели) —
     *  {@code null} в начале списка означает «не выбрано». */
    private void populateStructureFrameCombo(JComboBox<com.vjstb.ledscheme.model.StructureFrameType> combo,
            com.vjstb.ledscheme.model.StructureFrameType.Kind kind, String selectId) {
        DefaultComboBoxModel<com.vjstb.ledscheme.model.StructureFrameType> m = new DefaultComboBoxModel<>();
        m.addElement(null);
        com.vjstb.ledscheme.model.StructureFrameType toSelect = null;
        for (com.vjstb.ledscheme.model.StructureFrameType t : model.getStructureFrameTypes()) {
            if (t.getKind() != kind) {
                continue;
            }
            m.addElement(t);
            if (selectId != null && selectId.equals(t.getId())) {
                toSelect = t;
            }
        }
        combo.setModel(m);
        combo.setSelectedItem(toSelect);
    }

    private static String structureFrameTypeId(JComboBox<com.vjstb.ledscheme.model.StructureFrameType> combo) {
        Object sel = combo.getSelectedItem();
        return sel instanceof com.vjstb.ledscheme.model.StructureFrameType t ? t.getId() : null;
    }

    /** Пересчитывает количество железа наземного конструктива (см. {@code StructureCalc})
     *  для выбранного экрана: сначала предлагает башни/сегменты рамы/уровни перемычек/
     *  секции выноса по текущим (возможно, ещё не сохранённым) значениям формы -- на
     *  ЧЕРНОВОЙ копии экрана, тем же приёмом, что и {@link #calculateRiggingPoints()} для
     *  лебёдки -- затем сохраняет всё через {@link AppModel#updateScreenStructure} и
     *  показывает итоговую сводку (счётчики + предупреждения о превышении безопасной/
     *  экранной высоты). */
    private void calculateStructure() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        double towerHeight = ((Number) pStructureTowerHeight.getValue()).doubleValue();
        double spacing = ((Number) pStructureTowerSpacing.getValue()).doubleValue();
        boolean includeBase = pStructureIncludeBase.isSelected();
        double screenElevation = parseScreenElevation();
        String frameTypeId = structureFrameTypeId(pStructureFrameType);
        String shortFrameTypeId = structureFrameTypeId(pStructureShortFrameType);
        String cupTypeId = structureFrameTypeId(pStructureCupType);
        String ballastTypeId = structureFrameTypeId(pStructureBallastType);
        CabinetType screenType = model.typeOf(scr);

        Screen preview = scr.copy();
        preview.setStructureTowerHeightMm(towerHeight);
        preview.setStructureTowerSpacingMm(spacing);
        int suggestedTowers = com.vjstb.ledscheme.service.StructureCalc.suggestTowerCount(preview, screenType);
        com.vjstb.ledscheme.model.StructureFrameType frameType =
                (com.vjstb.ledscheme.model.StructureFrameType) pStructureFrameType.getSelectedItem();
        int suggestedVertical = com.vjstb.ledscheme.service.StructureCalc.suggestVerticalFramesPerTower(preview, frameType);
        double frameHeightMm = frameType != null && frameType.getHeightMm() != null && frameType.getHeightMm() > 0
                ? frameType.getHeightMm() : 950.0;
        int suggestedPeremychkaLevels =
                com.vjstb.ledscheme.service.StructureCalc.suggestPeremychkaLevels(towerHeight, frameHeightMm);
        double screenHeightMm = screenType != null ? scr.getRows() * screenType.getHeightMm() : 0;
        double screenWidthMm = screenType != null ? scr.getCols() * screenType.getWidthMm() : 0;
        int suggestedExtendedBaseSections =
                com.vjstb.ledscheme.service.StructureCalc.suggestExtendedBaseSections(screenHeightMm, screenWidthMm);

        pStructureTowerCount.setValue(suggestedTowers);
        pStructureVerticalFrames.setValue(suggestedVertical);
        pStructurePeremychkaLevels.setValue(suggestedPeremychkaLevels);
        pStructureExtendedBaseSections.setValue(suggestedExtendedBaseSections);

        model.updateScreenStructure(scr, towerHeight, spacing, suggestedTowers, suggestedVertical,
                suggestedPeremychkaLevels, suggestedExtendedBaseSections, includeBase, frameTypeId, shortFrameTypeId,
                cupTypeId, ballastTypeId, screenElevation, pStructureNotes.getText());

        com.vjstb.ledscheme.service.StructureCalc.Result result =
                com.vjstb.ledscheme.service.StructureCalc.compute(scr, screenType, model.getWorkspace());

        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Башен: %d, сегментов рамы на башню: %d, уровней перемычек: %d%n",
                suggestedTowers, suggestedVertical, suggestedPeremychkaLevels));
        msg.append(String.format("Вертикальных рам: %d%n", result.verticalFrameCount()));
        msg.append(String.format("Перемычек: %d%n", result.peremychkaCount()));
        msg.append(String.format("Секций базовых рам: %d%n", result.baseFrameCount()));
        msg.append(String.format("Стаканов: %d%n", result.cupCount()));
        msg.append(String.format("Болтов: %d%n", result.boltCount()));
        if (result.requiredBallastKg() > 0) {
            msg.append(String.format("Требуемый балласт (≈ вес экрана): %.1f кг (%d контейнеров)%n",
                    result.requiredBallastKg(), result.ballastContainerCount()));
        }
        boolean warnSafe = result.exceedsSafeHeightWarning();
        boolean warnScreen = result.exceedsScreenHeightWarning();
        if (warnSafe) {
            msg.append(String.format("%nВНИМАНИЕ: высота башни %.0f мм превышает безопасный предел %.0f мм —"
                            + " конструктив такой высоты без отдельного инженерного расчёта не строим!%n",
                    result.totalTowerHeightMm(), com.vjstb.ledscheme.service.StructureCalc.MAX_SAFE_TOWER_HEIGHT_MM));
        }
        if (warnScreen) {
            msg.append(String.format("%nВНИМАНИЕ: высота башни %.0f мм должна быть строго меньше высоты экрана"
                    + " %.0f мм!%n", result.totalTowerHeightMm(), screenHeightMm));
        }
        msg.append("\nТребует независимой инженерной перепроверки перед монтажом — см. STRUCTURE_CALC_NOTES.md.");
        JOptionPane.showMessageDialog(this, msg.toString(), "Готово",
                (warnSafe || warnScreen) ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
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
                pRiggingPoints.setValue(com.vjstb.ledscheme.service.ScreenLogic.suggestRiggingPoints(
                        scr, model.typeOf(scr), model.getWorkspace()));
            }
            applyMountTypeVisibility((com.vjstb.ledscheme.model.ScreenMountType) pMountType.getSelectedItem());
        });

        body.add(UiKit.vgap(10));
        body.add(UiKit.formRow("Герцовка контента", pRefreshHz));
        body.add(UiKit.vgap());
        body.add(UiKit.formRow("Глубина цвета, бит", pBitDepth));
        body.add(UiKit.vgap());

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
                model.updateScreenMount(scr, mt, (Integer) pRiggingPoints.getValue(), pRiggingNotes.getText(),
                        ((Number) pRiggingSafetyFactor.getValue()).doubleValue(), parseHoistCapacity(),
                        selectedHoistTypeId());
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
        // БЕЗ жёсткого максимума высоты (раньше был захардкожен потолок 220px) --
        // тот потолок и был основной причиной бага «Форма экрана» не растёт даже
        // когда rightSplit явно выделяет ей больше места, см. javadoc
        // UiKit.stretchToViewport и комментарий у rightSplit в конструкторе.
        shapeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
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
            // prerigSection и shapeSection — тот же класс бага, что screensSection/
            // paramsSection выше (оба потомки ОДНОГО rightSplit, оба могут стать видимы
            // в этом же проходе — например, у нового проекта первый добавленный экран
            // сразу делает истинными и hasScene, и scr != null): один вызов
            // restoreDividerProportion на общий разделитель, не по одному на секцию
            // (баг-репорт: «Форма экрана» не появляется, пока не потянуть разделитель
            // руками, и «Прериг сцены» из-за того же не получает причитающуюся долю
            // высоты автоматически).
            boolean prerigWasVisible = prerigSection.isVisible();
            boolean shapeWasVisible = shapeSection.isVisible();
            prerigSection.setVisible(hasScene);
            if (hasScene) {
                syncList(screenModel, model.getCurrentScene().getScreens());
                screenList.setSelectedValue(model.getCurrentScreen(), true);
                ListSizing.fit(screenList, screenScroll, 2, 8);
                rebuildPrerig();
            }

            shapeSection.setVisible(scr != null && shapeEditorRequested);
            toggleShapeBtn.setEnabled(scr != null);
            toggleShapeBtn.setText(shapeEditorRequested ? "Скрыть форму экрана" : "Изменить форму экрана");
            if ((hasScene && !prerigWasVisible) || (scr != null && shapeEditorRequested && !shapeWasVisible)) {
                UiKit.restoreDividerProportion(rightSplit, settings, "setup.prerigShape", 0.58);
                // prerigSplit (холст vs точки подвеса ВНУТРИ "Прериг сцены") зависит от
                // высоты prerigSection, которую только что поменял вызов выше — если
                // восстановить его в ТОМ ЖЕ цикле EDT, он схватит ещё старую (маленькую)
                // высоту и навсегда запомнит сплюснутый холст. Дополнительный
                // invokeLater даёт restoreDividerProportion(rightSplit, ...) выше реально
                // применить новую высоту первым.
                SwingUtilities.invokeLater(() ->
                        UiKit.restoreDividerProportion(prerigSplit, settings, "setup.prerigCanvas", 0.68));
            }
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
                pRiggingSafetyFactor.setValue(scr.getRiggingSafetyFactorMin());
                pRiggingHoistCapacity.setText(scr.getRiggingHoistCapacityKg() != null
                        ? UiKit.fmt(scr.getRiggingHoistCapacityKg()) : "");
                populateHoistTypeCombo(scr.getRiggingHoistTypeId());
                pRiggingHoistCapacity.setEnabled(pRiggingHoistType.getSelectedItem() == null);
                pRefreshHz.setSelectedItem(scr.getRefreshRateHz());
                pBitDepth.setSelectedItem(scr.getColorBitDepth());

                pStructureTowerHeight.setValue(scr.getStructureTowerHeightMm());
                pStructureTowerSpacing.setValue(scr.getStructureTowerSpacingMm());
                pStructureTowerCount.setValue(scr.getStructureTowerCount());
                pStructureVerticalFrames.setValue(scr.getStructureVerticalFramesPerTower());
                pStructurePeremychkaLevels.setValue(scr.getStructurePeremychkaLevels());
                pStructureExtendedBaseSections.setValue(scr.getStructureExtendedBaseSections());
                pStructureIncludeBase.setSelected(scr.isStructureIncludeBaseFrame());
                populateStructureFrameCombo(pStructureFrameType,
                        com.vjstb.ledscheme.model.StructureFrameType.Kind.FRAME, scr.getStructureFrameTypeId());
                populateStructureFrameCombo(pStructureShortFrameType,
                        com.vjstb.ledscheme.model.StructureFrameType.Kind.SHORT_FRAME, scr.getStructureShortFrameTypeId());
                populateStructureFrameCombo(pStructureCupType,
                        com.vjstb.ledscheme.model.StructureFrameType.Kind.CUP, scr.getStructureCupTypeId());
                populateStructureFrameCombo(pStructureBallastType,
                        com.vjstb.ledscheme.model.StructureFrameType.Kind.BALLAST_CONTAINER, scr.getStructureBallastTypeId());
                pStructureScreenElevation.setText(scr.getStructureScreenElevationMm() > 0
                        ? UiKit.fmt(scr.getStructureScreenElevationMm()) : "");
                pStructureNotes.setText(scr.getStructureNotes() != null ? scr.getStructureNotes() : "");
            }
            applyMountTypeVisibility(scr != null ? scr.getMountType() : null);

        } finally {
            refreshing = false;
        }
        shapeEditor.revalidate();
        shapeEditor.repaint();
        // Явно на каждом разделителе, чьи дети (Сцены/Экраны/Параметры/Прериг/Форма
        // экрана) только что поменяли видимость — иначе место под них не
        // пересчитывается немедленно (revalidate() на верхнем this не всегда
        // достаточен).
        for (JSplitPane sp : new JSplitPane[]{leftSplit3, leftSplit2, leftSplitNav, rightSplit, prerigSplit}) {
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
        prerigPower.setText(UiKit.fmtPower(s.totalPowerW(), settings.activeProfile().isPowerUnitKw()));
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

    /** {@code null} в начале списка -- пункт «Ввести вручную», см. javadoc
     *  {@link #pRiggingHoistType}. {@code selectId} не найден в текущей библиотеке
     *  (запись удалена) -- остаёмся на «Ввести вручную», НЕ стираем
     *  {@code riggingHoistTypeId} молча (пользователь увидит несовпадение и решит сам). */
    private void populateHoistTypeCombo(String selectId) {
        DefaultComboBoxModel<com.vjstb.ledscheme.model.HoistType> m = new DefaultComboBoxModel<>();
        m.addElement(null);
        com.vjstb.ledscheme.model.HoistType toSelect = null;
        for (com.vjstb.ledscheme.model.HoistType h : model.getHoistTypes()) {
            m.addElement(h);
            if (selectId != null && selectId.equals(h.getId())) {
                toSelect = h;
            }
        }
        pRiggingHoistType.setModel(m);
        pRiggingHoistType.setSelectedItem(toSelect);
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
