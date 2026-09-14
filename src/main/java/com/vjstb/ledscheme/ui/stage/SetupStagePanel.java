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
    private final JButton alignCabinetsBtn = new JButton("Выровнять кабинеты по сетке");

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
    private final JComboBox<com.vjstb.ledscheme.model.ScreenTagColor> pTagColor =
            new JComboBox<>(com.vjstb.ledscheme.model.ScreenTagColor.values());
    private final JSpinner pRiggingPoints = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JTextField pRiggingNotes = new JTextField(10);
    private final JSpinner pRiggingSafetyFactor = new JSpinner(new SpinnerNumberModel(5.0, 1.0, 20.0, 0.5));
    /** Пусто — грузоподъёмность лебёдки не указана (см. Screen#getRiggingHoistCapacityKg). */
    private final JTextField pRiggingHoistCapacity = new JTextField(10);
    /** Библиотечная модель лебёдки (см. HoistType/Screen#getRiggingHoistTypeId) —
     *  {@code null} = «Ввести вручную», тогда действует {@link #pRiggingHoistCapacity}. */
    private final JComboBox<com.vjstb.ledscheme.model.HoistType> pRiggingHoistType = new JComboBox<>();

    // ---- ферма подвеса (см. service.TrussCalc, RIGGING_CALC_NOTES.md) ----
    /** {@code null} = профиль фермы не выбран — BOM не считается, но геометрия
     *  (длина/отступы) всё равно влияет на расстановку точек подвеса. */
    private final JComboBox<com.vjstb.ledscheme.model.TrussProfile> pRiggingTrussProfile = new JComboBox<>();
    /** Пусто — длина фермы не переопределена, действует авто (см. TrussCalc#suggestTrussLengthMm). */
    private final JTextField pRiggingTrussLength = new JTextField(10);
    private final javax.swing.JCheckBox pRiggingTrussSymmetric =
            new javax.swing.JCheckBox("Равномерный отступ от краёв экрана", true);
    /** Действует только когда {@link #pRiggingTrussSymmetric} снят. */
    private final JTextField pRiggingTrussManualOffset = new JTextField(10);
    private final JTextField pRiggingTrussNotes = new JTextField(10);
    private final JButton calcTrussBtn = new JButton("Рассчитать фермы");
    private final JButton buildTrussSpecBtn = new JButton("Собрать спецификацию");
    private final JButton buildTrussSpecSceneBtn = new JButton("Собрать спецификацию для сцены");

    private final JComboBox<Integer> pRefreshHz = new JComboBox<>(new Integer[]{50, 60, 120, 144, 240});
    private final JComboBox<Integer> pBitDepth = new JComboBox<>(new Integer[]{8, 10, 12});

    // ---- наземный конструктив (см. service.StructureCalc, STRUCTURE_CALC_NOTES.md) ----
    // Round (баг-репорт: "убирай эти параметры, они только мешают и не помогают") -- пять
    // номинальных счётчиков сетки (башен/сегментов переднего-заднего ряда/уровней перемычек/
    // секций выноса) убраны из UI целиком: реальная детальная расстановка правится поячеечно
    // прямо в 3D-превью (клик по существующей детали/призраку, см. Structure3DPanel), эти
    // спиннеры лишь дублировали то же самое числом и путались с ним при повторном расчёте.
    // Кнопка теперь строит только СТАРТОВУЮ сетку по формулам-подсказкам (см.
    // calculateStructure) -- отсюда и новое название.
    private final JButton calcStructureBtn = new JButton("Предварительный расчёт конструктива");
    private final JButton buildStructureSpecBtn = new JButton("Собрать спецификацию");
    private final JSpinner pStructureTowerHeight = new JSpinner(new SpinnerNumberModel(3000.0, 0.0, 50_000.0, 100.0));
    /** «Вынос базы под балласт, мм» — заменил бывший «Шаг башен» (2026-08-19, тот ни на что не
     *  влиял в итоговой ведомости материалов, см. StructureCalc.DEFAULT_TOWER_SPACING_MM):
     *  насколько дополнительно база выступает под балласт, инженер вводит вручную — формула
     *  оказалась ненадёжной ("аппаратно не получается его просчитывать эффективно"). */
    private final JSpinner pStructureBaseExtension = new JSpinner(new SpinnerNumberModel(500.0, 0.0, 50_000.0, 50.0));
    /** Коэффициент отношения требуемого веса балласта к весу экрана (2026-08-19) — раньше
     *  жёстко 1:1, теперь редактируемый (дефолт 0.6 — "в реальности хорошо если 6:10"). */
    private final JSpinner pStructureBallastRatio = new JSpinner(new SpinnerNumberModel(0.6, 0.05, 5.0, 0.05));
    private final JComboBox<com.vjstb.ledscheme.model.StructureFrameType> pStructureFrameType = new JComboBox<>();
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
    private JPanel trussFieldsPanel;
    private JPanel structureFieldsPanel;

    // Поля (не локальные переменные), чтобы rebuild() мог явно дёрнуть revalidate/repaint
    // именно на тех разделителях, чьи дети меняют видимость — иначе секции иногда не
    // перерисовываются сразу после выбора проекта/сцены, а только после следующего
    // взаимодействия с интерфейсом (см. UiKit.setInitialDividerOnShow — похожая природа).
    private final JSplitPane leftSplit3;
    private final JSplitPane leftSplit2;
    private final JSplitPane leftSplitNav;

    /** v3.0: «Прериг сцены» больше не делит высоту с постоянно видимым блоком полей —
     *  холст ({@link #prerigLayered}) занимает всю секцию целиком, а {@link
     *  #riggingFieldsPanel}/{@link #trussFieldsPanel}/{@link #structureFieldsPanel}
     *  (построены как раньше, без изменений) показываются ПО ЗАПРОСУ — либо плавающей
     *  карточкой поверх холста ({@link #floatingCard}, слой {@code PALETTE_LAYER} в
     *  {@link #prerigLayered}), либо, если пользователь перетащил карточку к правому
     *  краю ({@link #inspectorDocked}), закреплённой колонкой ({@link #dockPanel},
     *  {@code BorderLayout.EAST} в {@link #prerigCanvasHost}). Открывается кликом по
     *  экрану на холсте ({@link com.vjstb.ledscheme.ui.SceneCanvasPanel.RigLevelListener})
     *  или кнопкой в верхней панели ({@link #riggingQuickBtn}/{@link
     *  #structureQuickBtn}) — см. {@link #showInspector}/{@link #closeInspector}/
     *  {@link #dockCurrentInspector}. <b>Одна карточка "Подвес" на лебёдки+ферму
     *  вместе</b> (не две отдельных, как в первой версии v3.0) — баг-репорт
     *  2026-09-14: экран должен быть единственным помеченным элементом сцены,
     *  лебёдки/ферма — его зависимые атрибуты, не самостоятельные "метки" со своими
     *  отдельными карточками. */
    private javax.swing.JLayeredPane prerigLayered;
    private JPanel prerigCanvasHost;
    private JPanel dockPanel;
    private JPanel dockZoneIndicator;
    private JPanel floatingCard;
    private JPanel riggingCard;
    private JPanel structureCard;
    private String openInspectorLevel;
    private boolean inspectorDocked;
    private JButton riggingQuickBtn;
    private JButton structureQuickBtn;

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

        // v3.0: правая колонка — теперь только "Прериг сцены" целиком, без соседней
        // «Формы экрана» (та функция переехала в сам холст — ПКМ по кабинету в
        // режиме «Кабинеты по отдельности» открывает то же радиальное меню, что
        // раньше показывал отдельный редактор, см. SceneCanvasPanel — баг-репорт
        // 2026-09-14 "избавляемся от отдельного окна"). Разделитель между "Прериг
        // сцены" и чем-то ещё больше не нужен — секция одна.
        prerigPreview = new com.vjstb.ledscheme.ui.SceneCanvasPanel(model, settings);
        prerigPreview.setShowRiggingPoints(true);
        prerigSection = buildPrerig();

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        // stretchToViewport(prerigSection) -- см. javadoc метода: без этой обёртки
        // JPanel не растягивается на высоту окна внутри JScrollPane (сам не
        // Scrollable), из-за чего «Прериг сцены» вычисляла начальную высоту от
        // заниженного значения и появлялась крошечной (баг-репорт).
        JScrollPane rightScroll = new JScrollPane(UiKit.stretchToViewport(prerigSection));
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
        UiKit.enableListReorder(screenList, (from, drop) -> {
            Scene scene = model.getCurrentScene();
            if (scene != null) {
                model.reorderScreens(scene, from, drop);
            }
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
            Screen s = model.addScreen(r.name(), r.cabinetTypeId(), r.rows(), r.cols(), r.posX(), r.posY(),
                    r.mountType());
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

        // v3.0: быстрый доступ к панелям уровней подвеса/конструктива — раньше это
        // были постоянно видимые блоки полей ПОД холстом (см. class-javadoc {@link
        // #prerigLayered}), теперь холст растягивается на всю секцию, а панели
        // открываются по запросу — этой кнопкой или кликом по соответствующему
        // уровню прямо на холсте (см. setRigLevelListener ниже). Кнопка «Форма
        // экрана» отсюда убрана — та же правка ячеек (вырезание/переформовка)
        // теперь прямо на холсте, ПКМ по кабинету в режиме «Кабинеты по отдельности»
        // (см. SceneCanvasPanel, баг-репорт 2026-09-14 "избавляемся от отдельного
        // окна").
        riggingQuickBtn = new JButton("Подвес…");
        riggingQuickBtn.setToolTipText("Лебёдки (модель/WLL, точки, запас прочности) и ферма (тип/длина/отступы)"
                + " одного экрана вместе — то же самое открывается кликом по ферме/точкам над экраном на холсте.");
        riggingQuickBtn.addActionListener(e -> showInspector("rigging"));
        structureQuickBtn = new JButton("Конструктив…");
        structureQuickBtn.setToolTipText("Наземный конструктив (башня/рама/балласт) выбранного экрана.");
        structureQuickBtn.addActionListener(e -> showInspector("structure"));
        JPanel quickButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickButtonsRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        quickButtonsRow.add(riggingQuickBtn);
        quickButtonsRow.add(structureQuickBtn);
        stats.add(quickButtonsRow);
        stats.add(UiKit.vgap());

        // Мини-превью раскладки сцены: показывает все экраны сцены сразу, но
        // активным (выделенным) становится только выбранный в списке слева —
        // остальные притушены (setCompact(true)), без подписей и метража. Ферма и
        // точки подвеса рисуются здесь же — сама SceneCanvasPanel решает это по
        // mountType/riggingPointsCount экрана. Обёрнут в свой JScrollPane (Task
        // #7/v1.6, доработка после баг-репорта) — нужен для панорамирования, когда
        // включён детальный режим с масштабом (см. ниже), и позволяет холсту
        // растягиваться на всю высоту секции (с v3.0 холст больше ни с чем эту
        // высоту не делит, см. class-javadoc {@link #prerigLayered}) — тесно
        // фиксированных 220px было мало для точной расстановки отдельных кабинетов.
        prerigPreview.setCompact(true);
        prerigPreview.setPreferredSize(new Dimension(10, 220));
        prerigPreview.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        prerigPreview.setToolTipText("Перетаскивание экрана — переместить; Shift во время перетаскивания —"
                + " прилипание к краям соседних экранов. Средняя кнопка мыши — панорамирование."
                + " Ctrl+колесо — масштаб.");
        // Клик по ферме/точкам над экраном на холсте — та же точка входа, что и
        // кнопка «Подвес…» выше; клик выбирает ещё и сам экран (см.
        // SceneCanvasPanel#rigLevelAt — отдельный экран сцены может быть не текущим),
        // иначе открытая панель молча относилась бы не к тому экрану.
        prerigPreview.setRigLevelListener(screen -> {
            model.selectScreen(screen);
            showInspector("rigging");
        });
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
        showCabinetsCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        // Баг-репорт: сдвинутый вручную (в режиме выше) кабинет уводит NovaLCT-экспорт
        // в Complex-раскладку (см. NovaLctScrWriter.isComplexExport), а руками попасть
        // перетаскиванием ровно обратно в 0,0 неудобно/ненадёжно — кнопка одним кликом
        // возвращает ВСЕ кабинеты текущего экрана на номинальную сетку (см.
        // AppModel.alignScreenCabinetsToGrid), после чего экспорт сам определит экран
        // снова как обычный (Standard), т.к. проверка (isUniformRectangularGrid) читает
        // те же offsetXMm/offsetYMm заново при каждом экспорте, ничего не кэширует.
        // Видна ТОЛЬКО при включённом чекбоксе выше (баг-репорт: кнопка вне режима
        // отдельных кабинетов не имеет смысла — двигать нечего — и раньше ломала
        // раскладку footer'а, см. ниже) — по умолчанию скрыта, т.к. чекбокс по
        // умолчанию выключен.
        alignCabinetsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        alignCabinetsBtn.setVisible(false);
        alignCabinetsBtn.setToolTipText("Сбросить свободное смещение всех кабинетов текущего экрана обратно на"
                + " номинальную сетку — в частности, возвращает экспорт в NovaLCT к обычной"
                + " (Standard) раскладке, если он ушёл в Complex из-за случайно сдвинутого кабинета.");
        alignCabinetsBtn.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            if (scr == null) {
                return;
            }
            model.alignScreenCabinetsToGrid(scr);
            prerigPreview.revalidate();
            prerigPreview.repaint();
        });

        // v3.0: холст живёт в JLayeredPane — базовый слой (DEFAULT_LAYER, prerigScroll)
        // растягивается на весь размер через переопределённый doLayout (JLayeredPane
        // сам ничего не раскладывает), панель уровня добавляется поверх (PALETTE_LAYER)
        // с произвольными координатами (setBounds), не участвует в layout базового
        // слоя — см. showInspector/dockCurrentInspector/wireCardDrag ниже.
        prerigLayered = new javax.swing.JLayeredPane() {
            @Override
            public void doLayout() {
                synchronized (getTreeLock()) {
                    prerigScroll.setBounds(0, 0, getWidth(), getHeight());
                }
            }
        };
        prerigLayered.add(prerigScroll, javax.swing.JLayeredPane.DEFAULT_LAYER);

        dockZoneIndicator = new JPanel();
        dockZoneIndicator.setOpaque(true);
        dockZoneIndicator.setBackground(new java.awt.Color(Palette.ACCENT.getRed(), Palette.ACCENT.getGreen(),
                Palette.ACCENT.getBlue(), 70));
        dockZoneIndicator.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Palette.ACCENT));
        dockZoneIndicator.setVisible(false);
        prerigLayered.add(dockZoneIndicator, javax.swing.JLayeredPane.DEFAULT_LAYER + 1);

        dockPanel = new JPanel(new BorderLayout());
        dockPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.BORDER));

        prerigCanvasHost = new JPanel(new BorderLayout());
        prerigCanvasHost.add(prerigLayered, BorderLayout.CENTER);

        JPanel canvasArea = new JPanel(new BorderLayout());
        canvasArea.add(stats, BorderLayout.NORTH);
        canvasArea.add(prerigCanvasHost, BorderLayout.CENTER);
        // BoxLayout (не FlowLayout) — каждый компонент на СВОЕЙ строке. FlowLayout
        // раньше ставил чекбокс и кнопку в один ряд, но при недостаточной ширине
        // панели переносил кнопку на вторую строку ТОЛЬКО визуально: FlowLayout#
        // getPreferredSize() всегда считает высоту как для ОДНОЙ строки (не умеет
        // предсказывать перенос заранее), поэтому BorderLayout.SOUTH выделял footer'у
        // высоту только одной строки, а перенесённая кнопка молча обрезалась родителем
        // — баг-репорт «кнопка не видна». BoxLayout вертикально суммирует preferred-
        // высоты детей честно, обрезания при любой ширине панели не будет.
        JPanel canvasFooter = UiKit.vbox();
        canvasFooter.add(UiKit.vgap(6));
        canvasFooter.add(showCabinetsCheck);
        canvasFooter.add(UiKit.vgap(4));
        canvasFooter.add(alignCabinetsBtn);
        canvasArea.add(canvasFooter, BorderLayout.SOUTH);

        showCabinetsCheck.addActionListener(e -> {
            boolean detail = showCabinetsCheck.isSelected();
            // fitToViewport=false в детальном режиме — иначе Ctrl+колесо (масштаб,
            // см. mouseWheelMoved) не работает вовсе, а именно масштаб и нужен для
            // точной расстановки отдельных кабинетов (баг-репорт: без масштаба
            // порог привязки к соседям физически недостижим мышью при мелком виде).
            prerigPreview.setDetailMode(detail, true, !detail);
            alignCabinetsBtn.setVisible(detail);
            canvasFooter.revalidate();
            canvasFooter.repaint();
            if (prerigSection != null) {
                UiKit.recapHeight(prerigSection);
            }
            canvasArea.revalidate();
            canvasArea.repaint();
            prerigPreview.revalidate();
            prerigPreview.repaint();
        });

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

        calcRiggingBtn.setToolTipText("Способ монтажа задаётся в «Параметры экрана». Точки подвеса считаются от"
                + " краёв РЕАЛЬНОЙ фермы (см. блок «Ферма подвеса» ниже), не от ширины экрана.");
        calcRiggingBtn.addActionListener(e -> calculateRiggingPoints());
        riggingFieldsPanel.add(calcRiggingBtn);

        trussFieldsPanel = UiKit.vbox();
        pRiggingTrussProfile.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText("Не выбрано");
                } else if (value instanceof com.vjstb.ledscheme.model.TrussProfile t) {
                    setText(t.getName() + " — " + t.getAvailableLengthsM().size() + " длин");
                }
                return c;
            }
        });
        pRiggingTrussProfile.setToolTipText("Типоразмерный ряд фермы из общей библиотеки (список доступных длин"
                + " сегментов) — комплект под целевую длину подбирается минимальным числом кусков.");
        trussFieldsPanel.add(UiKit.formRow("Тип фермы (библиотека)", pRiggingTrussProfile));
        trussFieldsPanel.add(UiKit.vgap());
        pRiggingTrussLength.setToolTipText("Целевая длина фермы, мм — пусто означает авто (равна физической"
                + " ширине экрана). Определяет и комплект сегментов, и расстановку точек подвеса.");
        trussFieldsPanel.add(UiKit.formRow("Длина фермы, мм (переопределение)", pRiggingTrussLength));
        trussFieldsPanel.add(UiKit.vgap());
        pRiggingTrussSymmetric.setToolTipText("Свес фермы за края экрана (или недостача, если ферма короче)"
                + " делится поровну между левым и правым краем.");
        pRiggingTrussSymmetric.addActionListener(e ->
                pRiggingTrussManualOffset.setEnabled(!pRiggingTrussSymmetric.isSelected()));
        trussFieldsPanel.add(pRiggingTrussSymmetric);
        trussFieldsPanel.add(UiKit.vgap());
        pRiggingTrussManualOffset.setToolTipText("Ручной отступ левого края фермы от левого края экрана, мм —"
                + " положительное значение = ферма нависает левее края экрана. Действует, только если снят"
                + " флажок «Равномерный отступ» выше.");
        trussFieldsPanel.add(UiKit.formRow("Отступ фермы слева, мм (вручную)", pRiggingTrussManualOffset));
        trussFieldsPanel.add(UiKit.vgap());
        trussFieldsPanel.add(UiKit.formRow("Заметки по ферме", pRiggingTrussNotes));
        trussFieldsPanel.add(UiKit.vgap());
        calcTrussBtn.setToolTipText("Считает и сохраняет целевую длину/отступы фермы + комплект сегментов."
                + " Точки подвеса пересчитываются от неё при следующем нажатии «" + calcRiggingBtn.getText() + "».");
        calcTrussBtn.addActionListener(e -> calculateTruss());
        trussFieldsPanel.add(calcTrussBtn);
        trussFieldsPanel.add(UiKit.vgap());
        buildTrussSpecBtn.setToolTipText("Считает спецификацию (комплект сегментов + соединители) фермы по"
                + " текущим сохранённым параметрам. Тот же список автоматически попадает в общую спецификацию"
                + " проекта (лист «Фермы», этап «Вывод»).");
        buildTrussSpecBtn.addActionListener(e -> buildTrussSpec());
        trussFieldsPanel.add(buildTrussSpecBtn);
        buildTrussSpecSceneBtn.setToolTipText("Считает спецификацию фермы сразу по ВСЕМ экранам текущей сцены"
                + " (не только по выбранному) и суммирует комплект сегментов — тот же расчёт, что лист «Фермы»"
                + " на этапе «Вывод», но только для этой сцены и прямо здесь.");
        buildTrussSpecSceneBtn.addActionListener(e -> buildTrussSpecForScene());
        trussFieldsPanel.add(buildTrussSpecSceneBtn);

        structureFieldsPanel = UiKit.vbox();
        pStructureTowerHeight.setToolTipText("Автоматически подставляется равной собственной высоте экрана при"
                + " выборе экрана — башня строится вплотную под весь экран. Можно переопределить вручную; если"
                + " указанное значение выше высоты экрана, «Рассчитать конструктив» покажет предупреждение.");
        structureFieldsPanel.add(UiKit.formRow("Высота башни, мм", pStructureTowerHeight));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureBaseExtension.setToolTipText("Полная глубина базы под балласт (ВКЛЮЧАЯ обязательный модуль"
                + " под самой башней, не сверх него) — площадь опоры/рычаг устойчивости. Формулой не считается"
                + " («аппаратно не получается его просчитывать эффективно») — вводится вручную по месту.");
        structureFieldsPanel.add(UiKit.formRow("Вынос базы под балласт, мм", pStructureBaseExtension));
        structureFieldsPanel.add(UiKit.vgap());

        setStructureFrameRenderer(pStructureFrameType);
        setStructureFrameRenderer(pStructureCupType);
        setStructureFrameRenderer(pStructureBallastType);
        structureFieldsPanel.add(UiKit.formRow("Тип рамы (библиотека)", pStructureFrameType));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Тип стакана (библиотека)", pStructureCupType));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Тип контейнера балласта (библиотека)", pStructureBallastType));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureBallastRatio.setToolTipText("Требуемый вес балласта = вес отгружаемого экрана × этот"
                + " коэффициент. Раньше было жёстко 1:1 (1.0) — теперь редактируемо, реалистичнее около 0.6"
                + " (6:10).");
        structureFieldsPanel.add(UiKit.formRow("Коэфф. отгруз/масса экрана", pStructureBallastRatio));
        structureFieldsPanel.add(UiKit.vgap());
        pStructureScreenElevation.setToolTipText("Высота нижнего края экрана над землёй, мм — 0 = экран стоит на"
                + " земле. Влияет только на отображение в 3D (виден зазор между истинной землёй и приподнятым"
                + " экраном), НЕ на проверку высоты башни относительно высоты экрана.");
        structureFieldsPanel.add(UiKit.formRow("Подъём экрана от земли, мм", pStructureScreenElevation));
        structureFieldsPanel.add(UiKit.vgap());
        structureFieldsPanel.add(UiKit.formRow("Заметки по конструктиву", pStructureNotes));
        structureFieldsPanel.add(UiKit.vgap());

        calcStructureBtn.setToolTipText("Строит СТАРТОВУЮ сетку конструктива по формулам-подсказкам (число башен/"
                + "сегментов/перемычек/секций выноса) — дальнейшая точная расстановка (добавить/убрать отдельную"
                + " раму, перемычку, секцию) правится кликами прямо в 3D-превью, эта кнопка лишь задаёт разумную"
                + " отправную точку. Способ монтажа задаётся в «Параметры экрана». Требует независимой инженерной"
                + " перепроверки перед монтажом — см. STRUCTURE_CALC_NOTES.md.");
        calcStructureBtn.addActionListener(e -> calculateStructure());
        structureFieldsPanel.add(calcStructureBtn);
        structureFieldsPanel.add(UiKit.vgap());

        buildStructureSpecBtn.setToolTipText("Считает спецификацию (ведомость материалов) конструктива по РЕАЛЬНО"
                + " расставленным в 3D деталям — рамы/перемычки/базовые секции/стаканы/болты/балласт. Тот же"
                + " список автоматически попадает в общую спецификацию проекта (лист «Конструктив», этап"
                + " «Вывод»), нажимать её отдельно для этого не обязательно — кнопка нужна, чтобы свериться"
                + " по текущему экрану сразу здесь.");
        buildStructureSpecBtn.addActionListener(e -> buildStructureSpec());
        structureFieldsPanel.add(buildStructureSpecBtn);
        structureFieldsPanel.add(UiKit.vgap());

        toggle3DBtn.setToolTipText("Открыть/закрыть отдельное окно с 3D-превью конструктива — клик по"
                + " существующей детали убирает её; добавление — ТОЛЬКО с зажатым Ctrl (без Ctrl \"призраки\""
                + " не показываются и не кликаются, это и есть основной способ детальной правки, см. подсказку"
                + " у «" + calcStructureBtn.getText() + "»). Требует OpenGL, на некоторых системах может быть"
                + " недоступно -- тогда вместо картинки покажется сообщение.");
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

        // Одна карточка "Подвес" на лебёдки+ферму вместе (не две отдельных) — см.
        // class-javadoc prerigLayered про баг-репорт 2026-09-14.
        JPanel riggingCombined = UiKit.vbox();
        riggingCombined.add(riggingFieldsPanel);
        riggingCombined.add(UiKit.vgap(14));
        riggingCombined.add(new JLabel("Ферма подвеса"));
        riggingCombined.add(UiKit.vgap());
        riggingCombined.add(trussFieldsPanel);
        riggingCard = wrapAsInspectorCard("Подвес", riggingCombined);
        structureCard = wrapAsInspectorCard("Конструктив", structureFieldsPanel);

        return (JPanel) UiKit.dynamicSection("Прериг сцены", canvasArea);
    }

    private static final int DOCK_ZONE_PX = 64;

    /** Оборачивает уже построенную (см. {@link #buildPrerig}) панель полей в карточку
     *  с заголовком — перетаскиваемым (см. {@link #wireCardDrag}) для закрепления
     *  вправо и кнопкой закрытия. Содержимое строится ОДИН раз, как и раньше, просто
     *  без постоянного места в layout — {@link #showInspector} только перевешивает
     *  готовую карточку между плавающим слоем ({@link #prerigLayered}) и закреплённой
     *  колонкой ({@link #dockPanel}). */
    private JPanel wrapAsInspectorCard(String title, JPanel content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        card.setBackground(Palette.PANEL);
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Palette.PANEL);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 4)));
        head.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
        head.setToolTipText("Перетащите к правому краю холста, чтобы закрепить панель колонкой.");
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(java.awt.Font.BOLD));
        JButton closeBtn = new JButton("✕");
        closeBtn.setMargin(new java.awt.Insets(0, 4, 0, 4));
        closeBtn.setToolTipText("Закрыть панель");
        closeBtn.addActionListener(e -> closeInspector());
        head.add(titleLbl, BorderLayout.WEST);
        head.add(closeBtn, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(null);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(contentScroll, BorderLayout.CENTER);
        wireCardDrag(head, card);
        return card;
    }

    /** Перетаскивание за шапку карточки — свободное перемещение, пока карточка
     *  плавающая ({@link #floatingCard}); отпускание правее {@link #DOCK_ZONE_PX} от
     *  правого края холста переключает её в закреплённую колонку ({@link
     *  #dockCurrentInspector}). Закреплённая карточка этим слушателем не
     *  перетаскивается — открепление только кнопкой «✕» в {@link
     *  #wrapAsInspectorCard} (полное закрытие, не просто возврат к плаванию —
     *  сознательное упрощение первой версии). */
    private void wireCardDrag(javax.swing.JComponent head, JPanel card) {
        java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
            int pressScreenX, pressScreenY, cardOrigX, cardOrigY;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (inspectorDocked || card != floatingCard) {
                    return;
                }
                pressScreenX = e.getXOnScreen();
                pressScreenY = e.getYOnScreen();
                cardOrigX = card.getX();
                cardOrigY = card.getY();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (inspectorDocked || card != floatingCard) {
                    return;
                }
                int nx = cardOrigX + (e.getXOnScreen() - pressScreenX);
                int ny = Math.max(0, cardOrigY + (e.getYOnScreen() - pressScreenY));
                card.setLocation(nx, ny);
                setDockZoneHighlighted(nx + card.getWidth() > prerigLayered.getWidth() - DOCK_ZONE_PX);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                setDockZoneHighlighted(false);
                if (inspectorDocked || card != floatingCard) {
                    return;
                }
                if (card.getX() + card.getWidth() > prerigLayered.getWidth() - DOCK_ZONE_PX) {
                    dockCurrentInspector();
                }
            }
        };
        head.addMouseListener(drag);
        head.addMouseMotionListener(drag);
    }

    /** Небольшая подсветка правого края холста, пока перетаскиваемая карточка над
     *  зоной докинга ({@link #DOCK_ZONE_PX} от края) — по фидбэку, иначе непонятно
     *  без пробы отпустить, что карточка вообще закрепится. Полоса, не отдельное
     *  окно — просто ещё один компонент {@link #prerigLayered} на слое НИЖЕ
     *  плавающей карточки ({@code DEFAULT_LAYER+1} < {@code PALETTE_LAYER}), видима
     *  только пока действительно "горячо" (не всё время перетаскивания). */
    private void setDockZoneHighlighted(boolean hot) {
        if (dockZoneIndicator == null) {
            return;
        }
        if (hot) {
            dockZoneIndicator.setBounds(Math.max(0, prerigLayered.getWidth() - DOCK_ZONE_PX), 0,
                    DOCK_ZONE_PX, prerigLayered.getHeight());
        }
        dockZoneIndicator.setVisible(hot);
    }

    private JPanel cardFor(String level) {
        if ("rigging".equals(level)) {
            return riggingCard;
        }
        if ("structure".equals(level)) {
            return structureCard;
        }
        return null;
    }

    /** Открывает карточку уровня {@code level} ("hoist"/"truss"/"structure") — либо
     *  плавающей поверх холста (по умолчанию), либо, если пользователь уже закрепил
     *  панель вправо ({@link #inspectorDocked}), сразу в закреплённой колонке. Общая
     *  точка входа и для клика по уровню на холсте ({@link
     *  com.vjstb.ledscheme.ui.SceneCanvasPanel.RigLevelListener}), и для кнопок
     *  «Лебёдки…»/«Ферма…»/«Конструктив…» в шапке. */
    private void showInspector(String level) {
        JPanel card = cardFor(level);
        if (card == null) {
            return;
        }
        openInspectorLevel = level;
        if (inspectorDocked) {
            dockPanel.removeAll();
            dockPanel.add(card, BorderLayout.CENTER);
            dockPanel.revalidate();
            dockPanel.repaint();
            return;
        }
        if (floatingCard != null && floatingCard != card) {
            prerigLayered.remove(floatingCard);
        }
        floatingCard = card;
        if (card.getParent() != prerigLayered) {
            prerigLayered.add(card, javax.swing.JLayeredPane.PALETTE_LAYER);
            int cw = 280;
            int ch = Math.max(160, Math.min(360, prerigLayered.getHeight() - 24));
            card.setBounds(16, 16, cw, ch);
        }
        prerigLayered.moveToFront(card);
        prerigLayered.revalidate();
        prerigLayered.repaint();
    }

    /** Закрывает текущую панель уровня целиком — и плавающую карточку, и закреплённую
     *  колонку, если она была раскрыта. */
    private void closeInspector() {
        if (inspectorDocked) {
            dockPanel.removeAll();
            prerigCanvasHost.remove(dockPanel);
            inspectorDocked = false;
            prerigCanvasHost.revalidate();
            prerigCanvasHost.repaint();
        }
        if (floatingCard != null) {
            prerigLayered.remove(floatingCard);
            floatingCard = null;
            prerigLayered.revalidate();
            prerigLayered.repaint();
        }
        openInspectorLevel = null;
    }

    /** Перетаскивание карточки к правому краю холста (см. {@link #wireCardDrag})
     *  закрепляет её колонкой вместо плавающего окна — переиспользует ТУ ЖЕ карточку
     *  (не строит копию), просто меняет родителя. */
    private void dockCurrentInspector() {
        if (openInspectorLevel == null) {
            return;
        }
        JPanel card = cardFor(openInspectorLevel);
        if (card == null) {
            return;
        }
        if (floatingCard == card) {
            prerigLayered.remove(floatingCard);
            floatingCard = null;
        }
        inspectorDocked = true;
        dockPanel.removeAll();
        dockPanel.add(card, BorderLayout.CENTER);
        dockPanel.setPreferredSize(new Dimension(280, 10));
        prerigCanvasHost.add(dockPanel, BorderLayout.EAST);
        prerigCanvasHost.revalidate();
        prerigCanvasHost.repaint();
        prerigLayered.revalidate();
        prerigLayered.repaint();
    }

    /** Показывает/скрывает быстрые кнопки уровней (rigging для RIGGED, конструктив
     *  для STRUCTURE, ни одной — для LAYER/FLOOR/{@code null}) и закрывает открытую
     *  панель уровня, если она перестала быть осмысленной для нового способа монтажа
     *  (например, экран переключили с RIGGED на STRUCTURE при открытой «Ферма…»). */
    private void applyMountTypeVisibility(com.vjstb.ledscheme.model.ScreenMountType mountType) {
        boolean rigged = mountType == com.vjstb.ledscheme.model.ScreenMountType.RIGGED;
        boolean structure = mountType == com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE;
        if (riggingQuickBtn != null) {
            riggingQuickBtn.setVisible(rigged);
            structureQuickBtn.setVisible(structure);
        }
        if (openInspectorLevel != null) {
            boolean stillValid = (rigged && "rigging".equals(openInspectorLevel))
                    || (structure && "structure".equals(openInspectorLevel));
            if (!stillValid) {
                closeInspector();
            }
        }
        if (prerigSection != null) {
            UiKit.recapHeight(prerigSection);
            prerigSection.revalidate();
            prerigSection.repaint();
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
        // (лебёдку/грузоподъёмность/ферму пользователь мог только что поменять и ещё не
        // сохранить) -- считаем на ЧЕРНОВОЙ копии экрана, не трогая сохранённый scr,
        // иначе первое нажатие «Рассчитать» после смены лебёдки использовало бы
        // старое значение (баг-репорт: «при перерасчёте количество лебёдок не меняется»).
        // Поля фермы (длина/отступ) добавлены сюда же (не только лебёдка) -- точки теперь
        // считаются от РЕАЛЬНОЙ фермы (см. RiggingCalc class-javadoc), поэтому кнопка должна
        // реагировать и на них, даже если пользователь ещё не нажимал «Рассчитать фермы».
        Screen preview = scr.copy();
        preview.setRiggingHoistCapacityKg(hoistCapacity);
        preview.setRiggingHoistTypeId(hoistTypeId);
        preview.setRiggingTrussLengthMm(parseTrussLengthOverride());
        preview.setRiggingTrussSymmetricOffset(pRiggingTrussSymmetric.isSelected());
        preview.setRiggingTrussManualLeftOffsetMm(parseTrussManualOffset());
        int suggested = com.vjstb.ledscheme.service.ScreenLogic.suggestRiggingPoints(
                preview, model.typeOf(scr), model.getWorkspace());
        model.updateScreenMount(scr, scr.getMountType(), suggested, scr.getRiggingNotes(), safetyFactor, hoistCapacity,
                hoistTypeId);
        pRiggingPoints.setValue(suggested);
        prerigPreview.revalidate();
        prerigPreview.repaint();

        // RiggingCalc.compute/PNG используют preview (не scr) -- точки подвеса должны
        // отражать ТЕКУЩИЕ (возможно ещё не сохранённые кнопкой «Рассчитать фермы») поля
        // фермы формы, а не то, что последний раз было персистировано в scr.
        com.vjstb.ledscheme.service.RiggingCalc.Result result = com.vjstb.ledscheme.service.RiggingCalc.compute(
                preview, model.typeOf(scr), model.getWorkspace(), suggested);
        com.vjstb.ledscheme.service.TrussCalc.Result trussGeom = com.vjstb.ledscheme.service.TrussCalc.compute(
                preview, model.typeOf(scr), model.getWorkspace());
        StringBuilder loadMsg = new StringBuilder();
        loadMsg.append(String.format("Точек подвеса: %d%n", suggested));
        loadMsg.append(String.format("Ферма: длина %.0f мм, отступы слева/справа %.0f/%.0f мм%n",
                trussGeom.targetLengthMm(), trussGeom.leftOffsetMm(), trussGeom.rightOffsetMm()));
        boolean trussWarn = trussGeom.shorterThanScreenWarning();
        if (trussWarn) {
            loadMsg.append("ВНИМАНИЕ: ферма короче ширины экрана — не перекрывает его целиком!\n");
        }
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
                    model.getCurrentProject(), model.getCurrentScene(), settings);
            java.io.File out = new java.io.File(folder,
                    "rigging_" + com.vjstb.ledscheme.ui.OutputPaths.sanitize(scr.getName()) + ".png");
            java.awt.image.BufferedImage img = com.vjstb.ledscheme.ui.RiggingSchemaImageWriter.render(
                    scr, model.typeOf(scr), result, trussGeom);
            javax.imageio.ImageIO.write(img, "png", out);
            loadMsg.append("\nСхема сохранена: ").append(out.getAbsolutePath());
            JOptionPane.showMessageDialog(this, loadMsg.toString(), "Готово",
                    (anyOver || trussWarn) ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Сохраняет параметры фермы формы ({@link AppModel#updateScreenTruss}) и показывает
     *  сводку (целевая длина/отступы/предупреждение о короткой ферме + комплект сегментов
     *  из библиотеки/число соединителей на стыках, см. {@code TrussCalc}) — по образцу
     *  {@link #calculateRiggingPoints()}. Точки подвеса НЕ пересчитываются автоматически
     *  этой кнопкой (они читают ферму напрямую из {@code Screen} при следующем нажатии «"
     *  + calcRiggingBtn.getText() + "») — тот же принцип разделения, что у {@link
     *  #calculateStructure()}/{@link #calculateRiggingPoints()} (два независимых расчёта,
     *  каждый сохраняется своей кнопкой). */
    private void calculateTruss() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        String trussProfileId = selectedTrussProfileId();
        Double lengthOverride = parseTrussLengthOverride();
        boolean symmetric = pRiggingTrussSymmetric.isSelected();
        Double manualOffset = parseTrussManualOffset();
        model.updateScreenTruss(scr, trussProfileId, lengthOverride, symmetric, manualOffset,
                pRiggingTrussNotes.getText());
        prerigPreview.revalidate();
        prerigPreview.repaint();

        com.vjstb.ledscheme.service.TrussCalc.Result result = com.vjstb.ledscheme.service.TrussCalc.compute(
                scr, model.typeOf(scr), model.getWorkspace());
        boolean warn = result.shorterThanScreenWarning();
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Целевая длина фермы: %.0f мм%n", result.targetLengthMm()));
        msg.append(String.format("Отступы слева/справа: %.0f/%.0f мм%n", result.leftOffsetMm(), result.rightOffsetMm()));
        if (warn) {
            msg.append("ВНИМАНИЕ: ферма короче ширины экрана — не перекрывает его целиком!\n");
        }
        if (result.profileMissing()) {
            msg.append("Тип фермы не выбран — комплект сегментов не посчитан.");
        } else if (result.catalogEmpty()) {
            msg.append("В библиотечном профиле фермы нет ни одной длины — комплект сегментов не посчитан.");
            warn = true;
        } else {
            msg.append(String.format("%nСегментов: %d, соединителей на стыках: %d%n",
                    result.totalPieceCount(), result.connectorCount()));
            for (com.vjstb.ledscheme.service.CableSpecCalc.Piece p : result.pieces()) {
                msg.append(String.format("  %.2f м × %d%n", p.lengthM(), p.count()));
            }
            msg.append("\nТочки подвеса теперь пересчитываются от этой фермы — нажмите «")
                    .append(calcRiggingBtn.getText()).append("», чтобы обновить их расстановку.");
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Ферма подвеса",
                warn ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** Ведомость материалов фермы по ТЕКУЩИМ сохранённым параметрам ({@code Screen}, не по
     *  форме, если она ещё не сохранена кнопкой «{@code Рассчитать фермы}») — тот же {@code
     *  TrussCalc.compute}, что и общая спецификация проекта на этапе «Вывод» считает для
     *  листа «Фермы»; кнопка здесь просто даёт свериться по текущему экрану сразу на месте,
     *  без выгрузки всего проекта — по образцу {@link #buildStructureSpec()}. */
    private void buildTrussSpec() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        com.vjstb.ledscheme.service.TrussCalc.Result result = com.vjstb.ledscheme.service.TrussCalc.compute(
                scr, model.typeOf(scr), model.getWorkspace());
        StringBuilder msg = new StringBuilder();
        msg.append("Спецификация фермы — экран «").append(scr.getName()).append("»\n\n");
        msg.append(String.format("Целевая длина: %.0f мм, отступы слева/справа: %.0f/%.0f мм%n",
                result.targetLengthMm(), result.leftOffsetMm(), result.rightOffsetMm()));
        if (result.shorterThanScreenWarning()) {
            msg.append("ВНИМАНИЕ: ферма короче ширины экрана.\n");
        }
        if (result.profileMissing()) {
            msg.append("\nТип фермы не выбран.");
        } else if (result.catalogEmpty()) {
            msg.append("\nВ библиотечном профиле фермы нет ни одной длины.");
        } else {
            msg.append(String.format("%nСегментов: %d%n", result.totalPieceCount()));
            for (com.vjstb.ledscheme.service.CableSpecCalc.Piece p : result.pieces()) {
                msg.append(String.format("  %.2f м × %d%n", p.lengthM(), p.count()));
            }
            msg.append(String.format("Соединителей: %d%n", result.connectorCount()));
            msg.append("\nЭтот же список войдёт в общую спецификацию проекта (лист «Фермы») на этапе «Вывод».");
        }
        msg.append("\nТребует независимой инженерной перепроверки перед монтажом — см. RIGGING_CALC_NOTES.md.");
        JOptionPane.showMessageDialog(this, msg.toString(), "Спецификация фермы", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Та же спецификация фермы, что {@link #buildTrussSpec()}, но по ВСЕМ экранам ТЕКУЩЕЙ
     *  сцены сразу, а не только по выбранному — читает {@code Screen} напрямую (текущие
     *  сохранённые параметры каждого экрана, форма влияет только на выбранный), пропускает
     *  экраны без {@code mountType == RIGGED} или без выбранного профиля фермы (тот же
     *  фильтр, что {@code OutputStagePanel#addTrussSheet} — единственный источник правды
     *  для листа «Фермы» на этапе «Вывод», здесь та же логика просто агрегирована по сцене
     *  и показана сразу на месте, без выгрузки всего проекта). Комплекты сегментов
     *  суммируются по длине, но СНАЧАЛА группируются по типу фермы (библиотечному профилю)
     *  — разные типы физически несовместимы (разные соединители/сечение), смешивать их в
     *  один плоский список по одной длине нельзя, даже если длины совпадают числом. */
    private void buildTrussSpecForScene() {
        Scene scene = model.getCurrentScene();
        if (scene == null) {
            return;
        }
        java.util.TreeMap<String, java.util.TreeMap<Double, Integer>> kitTotals = new java.util.TreeMap<>();
        int screensCounted = 0;
        int totalConnectors = 0;
        StringBuilder warnings = new StringBuilder();
        for (Screen scr : scene.getScreens()) {
            if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                    || scr.getRiggingTrussProfileId() == null) {
                continue;
            }
            com.vjstb.ledscheme.service.TrussCalc.Result result = com.vjstb.ledscheme.service.TrussCalc.compute(
                    scr, model.typeOf(scr), model.getWorkspace());
            screensCounted++;
            if (result.shorterThanScreenWarning()) {
                warnings.append("  «").append(scr.getName()).append("» — ферма короче ширины экрана\n");
            }
            if (result.catalogEmpty()) {
                warnings.append("  «").append(scr.getName())
                        .append("» — в библиотечном профиле нет ни одной длины, комплект не учтён\n");
                continue;
            }
            totalConnectors += result.connectorCount();
            com.vjstb.ledscheme.model.TrussProfile profile =
                    model.getWorkspace().trussProfileById(scr.getRiggingTrussProfileId());
            String profileName = profile != null ? profile.getName() : "(запись удалена)";
            java.util.TreeMap<Double, Integer> byLength =
                    kitTotals.computeIfAbsent(profileName, k -> new java.util.TreeMap<>());
            for (com.vjstb.ledscheme.service.CableSpecCalc.Piece p : result.pieces()) {
                byLength.merge(p.lengthM(), p.count(), Integer::sum);
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Спецификация фермы — сцена «").append(scene.getName()).append("»\n\n");
        if (screensCounted == 0) {
            msg.append("Нет экранов с фермой подвеса (mountType = RIGGED + выбранный тип фермы).");
            JOptionPane.showMessageDialog(this, msg.toString(), "Спецификация фермы (сцена)",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        msg.append(String.format("Экранов с фермой: %d%n", screensCounted));
        if (kitTotals.isEmpty()) {
            msg.append("Комплект сегментов не посчитан ни для одного экрана.\n");
        } else {
            int totalPieces = kitTotals.values().stream()
                    .flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
            msg.append(String.format("Сегментов всего: %d, соединителей на стыках всего: %d%n",
                    totalPieces, totalConnectors));
            for (var typeEntry : kitTotals.entrySet()) {
                msg.append("  ").append(typeEntry.getKey()).append(":\n");
                for (var entry : typeEntry.getValue().entrySet()) {
                    msg.append(String.format("    %.2f м × %d%n", entry.getKey(), entry.getValue()));
                }
            }
        }
        if (warnings.length() > 0) {
            msg.append("\nВНИМАНИЕ:\n").append(warnings);
        }
        msg.append("\nТот же набор войдёт в общую спецификацию проекта (лист «Фермы») на этапе «Вывод».");
        msg.append("\nТребует независимой инженерной перепроверки перед монтажом — см. RIGGING_CALC_NOTES.md.");
        JOptionPane.showMessageDialog(this, msg.toString(), "Спецификация фермы (сцена)",
                warnings.length() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** {@code null} -- выбрано «Не выбрано» (см. {@link #pRiggingTrussProfile}), тогда BOM
     *  фермы не считается ({@code TrussCalc.Result#profileMissing()}). */
    private String selectedTrussProfileId() {
        Object sel = pRiggingTrussProfile.getSelectedItem();
        return sel instanceof com.vjstb.ledscheme.model.TrussProfile t ? t.getId() : null;
    }

    /** Пусто/некорректно/неположительно -- {@code null} (авто, см.
     *  Screen#getRiggingTrussLengthMm), тот же паттерн, что {@link #parseHoistCapacity()}. */
    private Double parseTrussLengthOverride() {
        String text = pRiggingTrussLength.getText();
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

    /** Пусто/некорректно -- {@code null}. В отличие от {@link #parseTrussLengthOverride()}
     *  ОТРИЦАТЕЛЬНЫЕ значения допустимы (несимметричный ручной отступ может законно тянуть
     *  ферму вправо, см. TrussCalc#leftOffsetMm). */
    private Double parseTrussManualOffset() {
        String text = pRiggingTrussManualOffset.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
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

    /** «Подходящий шаблон» библиотеки вместо «Не выбрано»/«Ввести вручную» — когда у
     *  экрана ЕЩЁ НЕТ сохранённого выбора ({@code selectId == null}), начальный выбор в
     *  комбобоксе — первая запись библиотеки по списку, а не пустой сентинел. Висячий/
     *  устаревший FK (запись удалена) НЕ подменяется этим — тот случай остаётся на
     *  сентинеле (пользователь увидит несовпадение и решит сам), см. вызывающие места. */
    private static <T> T pickDefault(List<T> candidates) {
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Заполняет комбобокс элементами библиотеки конструктива указанного вида (одна
     *  библиотека {@code StructureFrameType} на все 4 вида, см. class-javadoc модели) —
     *  {@code null} в начале списка означает «не выбрано». */
    private void populateStructureFrameCombo(JComboBox<com.vjstb.ledscheme.model.StructureFrameType> combo,
            com.vjstb.ledscheme.model.StructureFrameType.Kind kind, String selectId) {
        DefaultComboBoxModel<com.vjstb.ledscheme.model.StructureFrameType> m = new DefaultComboBoxModel<>();
        m.addElement(null);
        List<com.vjstb.ledscheme.model.StructureFrameType> candidates = model.getStructureFrameTypes().stream()
                .filter(t -> t.getKind() == kind).toList();
        com.vjstb.ledscheme.model.StructureFrameType toSelect = selectId == null ? pickDefault(candidates) : null;
        for (com.vjstb.ledscheme.model.StructureFrameType t : candidates) {
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
     *  для выбранного экрана: башни/сегменты рамы/уровни перемычек/секции выноса
     *  вычисляются ПОЛНОСТЬЮ формулами-подсказками (см. {@code StructureCalc.suggestXxx})
     *  на ЧЕРНОВОЙ копии экрана, тем же приёмом, что и {@link #calculateRiggingPoints()} для
     *  лебёдки -- пользователь их больше не подкручивает числом (баг-репорт: "убирай эти
     *  параметры, они только мешают и не помогают" — номинальные спиннеры Round'ов 8-11
     *  убраны из UI целиком), только КЛИКАМИ в 3D-превью после того, как эта кнопка
     *  построит стартовую сетку. Сохраняет через {@link AppModel#updateScreenStructure} и
     *  показывает короткое подтверждение + предупреждения о превышении безопасной/
     *  экранной высоты (сама ведомость материалов — отдельно, {@link #buildStructureSpec()},
     *  по РЕАЛЬНО расставленным в 3D деталям, не по этим стартовым числам). */
    private void calculateStructure() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        double towerHeight = ((Number) pStructureTowerHeight.getValue()).doubleValue();
        double baseExtensionMm = ((Number) pStructureBaseExtension.getValue()).doubleValue();
        double ballastRatio = ((Number) pStructureBallastRatio.getValue()).doubleValue();
        double screenElevation = parseScreenElevation();
        String frameTypeId = structureFrameTypeId(pStructureFrameType);
        String cupTypeId = structureFrameTypeId(pStructureCupType);
        String ballastTypeId = structureFrameTypeId(pStructureBallastType);
        CabinetType screenType = model.typeOf(scr);

        int towers = com.vjstb.ledscheme.service.StructureCalc.suggestTowerCount(scr, screenType);
        com.vjstb.ledscheme.model.StructureFrameType frameType =
                (com.vjstb.ledscheme.model.StructureFrameType) pStructureFrameType.getSelectedItem();
        Screen preview = scr.copy();
        preview.setStructureTowerHeightMm(towerHeight);
        int vertical = com.vjstb.ledscheme.service.StructureCalc.suggestVerticalFramesPerTower(preview, frameType);
        double frameHeightMm = frameType != null && frameType.getHeightMm() != null && frameType.getHeightMm() > 0
                ? frameType.getHeightMm() : 950.0;
        int backRowSegments = com.vjstb.ledscheme.service.StructureCalc.suggestBackRowSegments(frameHeightMm);
        // Уровни перемычек ограничены ФИЗИЧЕСКОЙ высотой заднего ряда (Round 5) -- перемычка
        // крепится к его перекладине, выше короткого заднего ряда крепить не к чему.
        double backRowHeightMm = backRowSegments * frameHeightMm;
        int peremychkaLevels =
                com.vjstb.ledscheme.service.StructureCalc.suggestPeremychkaLevels(backRowHeightMm, frameHeightMm);
        double screenHeightMm = screenType != null ? scr.getRows() * screenType.getHeightMm() : 0;

        model.updateScreenStructure(scr, towerHeight, towers, vertical,
                backRowSegments, peremychkaLevels, baseExtensionMm, ballastRatio, frameTypeId,
                cupTypeId, ballastTypeId, screenElevation, pStructureNotes.getText());
        // 3D-окно не подписано на модель (см. Structure3DPanel#refresh() javadoc) -- если оно
        // уже открыто, без этого явного вызова пересчёт не отразился бы в картинке, пока
        // пользователь не закроет и не откроет окно заново (баг-репорт).
        if (structure3DDialog != null && structure3DDialog.isShowing()) {
            structure3DDialog.refresh();
        }

        com.vjstb.ledscheme.service.StructureCalc.Result result =
                com.vjstb.ledscheme.service.StructureCalc.compute(scr, screenType, model.getWorkspace());
        boolean warnSafe = result.exceedsSafeHeightWarning();
        boolean warnScreen = result.exceedsScreenHeightWarning();

        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Стартовая сетка построена: %d башен, %d сегментов переднего ряда, %d заднего,"
                + " %d уровней перемычек, вынос базы %.0f мм.%n", towers, vertical, backRowSegments,
                peremychkaLevels, baseExtensionMm));
        msg.append("Дальнейшая точная расстановка (добавить/убрать раму, перемычку, секцию) — кликами в"
                + " 3D-превью. Итоговую ведомость материалов смотрите через «" + buildStructureSpecBtn.getText()
                + "» после того, как закончите правки.");
        if (warnSafe) {
            msg.append(String.format("%n%nВНИМАНИЕ: высота башни %.0f мм превышает безопасный предел %.0f мм —"
                            + " конструктив такой высоты без отдельного инженерного расчёта не строим!%n",
                    result.totalTowerHeightMm(), com.vjstb.ledscheme.service.StructureCalc.MAX_SAFE_TOWER_HEIGHT_MM));
        }
        if (warnScreen) {
            msg.append(String.format("%n%nВНИМАНИЕ: высота башни %.0f мм превышает высоту экрана %.0f мм —"
                    + " башня должна быть не выше экрана!%n", result.totalTowerHeightMm(), screenHeightMm));
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Стартовая сетка построена",
                (warnSafe || warnScreen) ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** Ведомость материалов конструктива по РЕАЛЬНО расставленным в 3D деталям (не по
     *  стартовым числам {@link #calculateStructure()}) — тот же {@code StructureCalc.compute},
     *  что и общая спецификация проекта на этапе «Вывод» (см. {@code OutputStagePanel
     *  #addStructureSheet}) считает для листа «Конструктив»; кнопка здесь просто даёт
     *  свериться по текущему экрану сразу на месте, без выгрузки всего проекта. */
    private void buildStructureSpec() {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        CabinetType screenType = model.typeOf(scr);
        com.vjstb.ledscheme.service.StructureCalc.Result result =
                com.vjstb.ledscheme.service.StructureCalc.compute(scr, screenType, model.getWorkspace());

        // По прямому указанию пользователя (2026-08-20): спецификация выдаёт ОДНО общее число
        // рам (вертикальные + перемычки + секции базы — физически один и тот же каталожный
        // тип, см. StructureCalc.Result#totalFrameCount javadoc), не три отдельные строки,
        // порядок — рамы/стаканы/болты/отгрузы.
        StringBuilder msg = new StringBuilder();
        msg.append("Спецификация конструктива — экран «").append(scr.getName()).append("»\n\n");
        msg.append(String.format("Рам: %d (из них вертикальных %d, перемычек %d, секций базы %d)%n",
                result.totalFrameCount(), result.verticalFrameCount(), result.peremychkaCount(),
                result.baseFrameCount()));
        msg.append(String.format("Стаканов: %d%n", result.cupCount()));
        msg.append(String.format("Болтов: %d%n", result.boltCount()));
        if (result.requiredBallastKg() > 0) {
            msg.append(String.format("Отгрузов: %d (≈%.1f кг балласта)%n",
                    result.ballastContainerCount(), result.requiredBallastKg()));
        }
        msg.append("\nЭтот же список войдёт в общую спецификацию проекта (лист «Конструктив») на этапе «Вывод».");
        msg.append("\nТребует независимой инженерной перепроверки перед монтажом — см. STRUCTURE_CALC_NOTES.md.");
        JOptionPane.showMessageDialog(this, msg.toString(), "Спецификация конструктива",
                JOptionPane.INFORMATION_MESSAGE);
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
        // Цветная метка зоны/площадки (v3.0) — чисто визуальная тонировка заливки на
        // схеме прерига, не связана с расчётами; применяется сразу по выбору (как
        // pMountType выше), не через общую кнопку «Применить» ниже.
        pTagColor.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof com.vjstb.ledscheme.model.ScreenTagColor tag && c instanceof JLabel lbl) {
                    java.awt.Color swatch = tag.color();
                    lbl.setIcon(new javax.swing.Icon() {
                        @Override
                        public void paintIcon(java.awt.Component comp, java.awt.Graphics g, int x, int y) {
                            if (swatch != null) {
                                g.setColor(swatch);
                                g.fillRect(x, y + 2, 12, 12);
                                g.setColor(java.awt.Color.BLACK);
                                g.drawRect(x, y + 2, 12, 12);
                            }
                        }

                        @Override
                        public int getIconWidth() {
                            return 16;
                        }

                        @Override
                        public int getIconHeight() {
                            return 16;
                        }
                    });
                }
                return c;
            }
        });
        pTagColor.setToolTipText("Чисто визуальная тонировка заливки экрана на схеме прерига — для группировки"
                + " по зоне/площадке на глаз, ни на что не влияет.");
        pTagColor.addActionListener(e -> {
            if (refreshing) return;
            Screen scr = model.getCurrentScreen();
            if (scr == null) return;
            model.setScreenTagColor(scr, (com.vjstb.ledscheme.model.ScreenTagColor) pTagColor.getSelectedItem());
        });
        body.add(UiKit.formRow("Метка (цвет зоны)", pTagColor));

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
            // v3.0: "Прериг сцены" больше не делит правую колонку с "Формой экрана"
            // (той больше нет — см. class-javadoc про SceneCanvasPanel/ПКМ) — просто
            // видимость, без общего JSplitPane-разделителя и restoreDividerProportion.
            prerigSection.setVisible(hasScene);
            if (hasScene) {
                syncList(screenModel, model.getCurrentScene().getScreens());
                screenList.setSelectedValue(model.getCurrentScreen(), true);
                ListSizing.fit(screenList, screenScroll, 2, 8);
                rebuildPrerig();
            }
            if (scr != null) {
                pName.setText(scr.getName());
                populateTypeCombo(pType, model.typeOf(scr));
                pCols.setValue(scr.getCols());
                pRows.setValue(scr.getRows());
                pX.setText(UiKit.fmt(scr.getPosXMm()));
                pY.setText(UiKit.fmt(scr.getPosYMm()));
                pMountType.setSelectedItem(scr.getMountType());
                pTagColor.setSelectedItem(scr.getTagColor());
                pRiggingPoints.setValue(scr.getRiggingPointsCount());
                pRiggingNotes.setText(scr.getRiggingNotes() != null ? scr.getRiggingNotes() : "");
                pRiggingSafetyFactor.setValue(scr.getRiggingSafetyFactorMin());
                pRiggingHoistCapacity.setText(scr.getRiggingHoistCapacityKg() != null
                        ? UiKit.fmt(scr.getRiggingHoistCapacityKg()) : "");
                populateHoistTypeCombo(scr.getRiggingHoistTypeId());
                pRiggingHoistCapacity.setEnabled(pRiggingHoistType.getSelectedItem() == null);
                populateTrussProfileCombo(scr.getRiggingTrussProfileId());
                pRiggingTrussLength.setText(scr.getRiggingTrussLengthMm() != null
                        ? UiKit.fmt(scr.getRiggingTrussLengthMm()) : "");
                pRiggingTrussLength.setToolTipText(String.format("Целевая длина фермы, мм — пусто означает авто"
                        + " (сейчас %.0f мм, физическая ширина экрана).",
                        com.vjstb.ledscheme.service.TrussCalc.suggestTrussLengthMm(scr, model.typeOf(scr))));
                pRiggingTrussSymmetric.setSelected(scr.isRiggingTrussSymmetricOffset());
                pRiggingTrussManualOffset.setEnabled(!scr.isRiggingTrussSymmetricOffset());
                pRiggingTrussManualOffset.setText(scr.getRiggingTrussManualLeftOffsetMm() != null
                        ? UiKit.fmt(scr.getRiggingTrussManualLeftOffsetMm()) : "");
                pRiggingTrussNotes.setText(scr.getRiggingTrussNotes() != null ? scr.getRiggingTrussNotes() : "");
                pRefreshHz.setSelectedItem(scr.getRefreshRateHz());
                pBitDepth.setSelectedItem(scr.getColorBitDepth());

                // Высота башни автоматически приравнивается к собственной физической высоте
                // экрана (2026-08-19, по прямому указанию пользователя) -- НО только для
                // экрана, для которого конструктив ЕЩЁ НИ РАЗУ не рассчитывался (пустой
                // structureFrameCells). Баг-репорт (тот же день, следующий заход): doRebuild()
                // выполняется ПОСЛЕ КАЖДОГО изменения модели, включая само нажатие
                // «Рассчитать конструктив» (AppModel.updateScreenStructure тоже вызывает
                // changed()) -- если пересчитывать autoTowerHeight здесь БЕЗУСЛОВНО на каждый
                // ребилд, поле молча откатывается к авто-значению сразу после того, как
                // пользователь ввёл своё и нажал «Рассчитать», из-за чего его правка выглядела
                // как "не сохранившаяся" (реально сохранялась, но тут же маскировалась). Для
                // уже рассчитанного экрана показываем ПЕРСИСТИРОВАННОЕ значение, как и любое
                // другое поле формы -- ровно то, что описал пользователь: "калькулятор должен
                // держать в памяти свои стартовые значения" (авто-подсказка только один раз,
                // до первого расчёта), а не подставлять их поверх уже введённых пользователем.
                boolean structureNeverCalculated = scr.getStructureFrameCells().isEmpty();
                if (structureNeverCalculated) {
                    double autoTowerHeight = com.vjstb.ledscheme.service.StructureCalc.suggestTowerHeightMm(
                            scr, model.typeOf(scr));
                    pStructureTowerHeight.setValue(
                            autoTowerHeight > 0 ? autoTowerHeight : scr.getStructureTowerHeightMm());
                } else {
                    pStructureTowerHeight.setValue(scr.getStructureTowerHeightMm());
                }
                // Вынос базы -- НЕ авто-подсказка (2026-08-20, откачено в тот же день, что
                // добавлено: "забываем про котангенсы, раньше рассчитывалось лучше" -- см.
                // StructureCalc class-javadoc за полной историей, не изобретай новую формулу
                // для этого поля без прямого запроса) -- всегда персистированное значение, как
                // и было после Round 19 (в отличие от высоты башни выше, для этого поля авто-
                // подстановка для нового экрана больше не делается вовсе).
                pStructureBaseExtension.setValue(scr.getStructureBaseExtensionMm());
                pStructureBallastRatio.setValue(scr.getStructureBallastRatio());
                populateStructureFrameCombo(pStructureFrameType,
                        com.vjstb.ledscheme.model.StructureFrameType.Kind.FRAME, scr.getStructureFrameTypeId());
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
        List<com.vjstb.ledscheme.model.HoistType> candidates = model.getHoistTypes();
        com.vjstb.ledscheme.model.HoistType toSelect = selectId == null ? pickDefault(candidates) : null;
        for (com.vjstb.ledscheme.model.HoistType h : candidates) {
            m.addElement(h);
            if (selectId != null && selectId.equals(h.getId())) {
                toSelect = h;
            }
        }
        pRiggingHoistType.setModel(m);
        pRiggingHoistType.setSelectedItem(toSelect);
    }

    /** {@code null} в начале списка -- «не выбрано», см. javadoc {@link #pRiggingTrussProfile}.
     *  {@code selectId} не найден в текущей библиотеке (запись удалена) -- остаёмся на «не
     *  выбрано», та же логика, что {@link #populateHoistTypeCombo}. */
    private void populateTrussProfileCombo(String selectId) {
        DefaultComboBoxModel<com.vjstb.ledscheme.model.TrussProfile> m = new DefaultComboBoxModel<>();
        m.addElement(null);
        List<com.vjstb.ledscheme.model.TrussProfile> candidates = model.getTrussProfiles();
        com.vjstb.ledscheme.model.TrussProfile toSelect = selectId == null ? pickDefault(candidates) : null;
        for (com.vjstb.ledscheme.model.TrussProfile t : candidates) {
            m.addElement(t);
            if (selectId != null && selectId.equals(t.getId())) {
                toSelect = t;
            }
        }
        pRiggingTrussProfile.setModel(m);
        pRiggingTrussProfile.setSelectedItem(toSelect);
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
