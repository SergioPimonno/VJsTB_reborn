package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.WireHopStyle;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Диалог «Предпочтения»: поведенческие переключатели интерфейса, не связанные
 * с цветом или горячими клавишами (см. {@link PersonalizationDialog} — цвета/
 * профили, {@link HotkeysDialog} — горячие клавиши) — вынесены в отдельное
 * окошко, чтобы каждый раздел персонализации открывался и настраивался
 * независимо от остальных.
 *
 * <p>Одни и те же настройки показываются в одной из двух РАСКЛАДОК; выбор —
 * профильный флаг {@code UserProfile.preferencesMatrixView}, который
 * переключается ВЫПАДАЮЩИМ СПИСКОМ в окне «цвета и профили»
 * ({@link PersonalizationDialog}), не здесь:</p>
 *
 * <ul>
 *   <li><b>Список по этапам</b> (по умолчанию) — группы «Общие», «Сигнал»,
 *   «Питание», «Генерация масок», «Синхронизация» стопкой сверху вниз. Внутри
 *   «Сигнал»/«Питание» — зеркальные пары одних и тех же переключателей; название
 *   группы уже задаёт этап, поэтому подписи чекбоксов НЕ повторяют «Сигнал:»/
 *   «Питание:» в начале.</li>
 *   <li><b>Матрица «функция × этап»</b> — таблица: строка = настройка, колонки
 *   «Общие»/«Сигнал»/«Питание», в ячейке либо чекбокс, либо «—» там, где этап к
 *   настройке неприменим. Зеркальные пары сигнал/питание физически стоят в одной
 *   строке — сразу видно, что это одна настройка дважды, а не два разных вопроса.
 *   «Сквозные» настройки (защита от дурака, узел-как-схема, форма мостиков
 *   дропдауном, форма экрана окном) занимают строку целиком; настройки со своей
 *   кнопкой/полем (логотип масок, папка экспорта, адрес сервера) — строками-span
 *   внизу.</li>
 * </ul>
 *
 * <p>Матрица может не вмещаться в стандартную ширину окна — окно тогда просто
 * растягивается под неё ({@link #pack()} после смены раскладки).</p>
 */
public class PreferencesDialog extends JDialog {

    /** Ширина колонок этапов в матрице — чтобы «—» и чекбоксы стояли ровными столбцами. */
    private static final int STAGE_COL_W = 74;
    /** Маркер ячейки матрицы «этот этап к настройке неприменим» — рисуется как «—». */
    private static final Object DASH = new Object();

    private final SettingsManager settings;

    // Переключатели создаются один раз в createControls() и живут в обеих раскладках.
    private JCheckBox previewWidgetCheck;
    private JCheckBox canvasSnapToCenterCheck;
    private JSpinner snapThresholdSpinner;
    private JSpinner snapStrengthSpinner;
    private JCheckBox foolProofWiringCheck;
    private JCheckBox schemaScreensAsWiringCheck;
    private JComboBox<WireHopStyle> wireHopStyleCombo;
    /** Гасит слушатель комбобокса на время программной установки значения в
     *  {@link #refresh()} — иначе {@code setSelectedItem} сам дёрнул бы сеттер
     *  настроек (у {@code JComboBox}, в отличие от {@code JCheckBox.setSelected},
     *  это событие летит). */
    private boolean refreshingWireHop;
    private JComboBox<SchemaStylePreset> schemaStylePresetCombo;
    /** Тот же приём, что {@link #refreshingWireHop} — гасит слушатель на время
     *  программной установки значения в {@link #refresh()} (docs/schema-ports-
     *  rework/PLAN.md, задача T3.1). */
    private boolean refreshingSchemaStylePreset;
    private JComboBox<com.vjstb.ledscheme.settings.SchemaRenderMode> schemaRenderModeCombo;
    /** Тот же приём, что {@link #refreshingSchemaStylePreset} (docs/schema-ports-
     *  rework/PLAN.md, задача T5.5). */
    private boolean refreshingSchemaRenderMode;
    private JSpinner schemaRouteStubSpinner;
    private JLabel exportRootFolderLabel;
    private JCheckBox signalSocketWiringCheck;
    private JCheckBox signalChainEndpointSocketsCheck;
    private JCheckBox signalSchemaAutoPopulateCheck;
    private JCheckBox signalSceneStatsCheck;
    private JCheckBox powerSocketWiringCheck;
    private JCheckBox powerChainEndpointSocketsCheck;
    private JCheckBox powerSchemaAutoPopulateCheck;
    private JCheckBox powerSceneStatsCheck;
    private JCheckBox loadTrackingCheck;
    private JCheckBox powerUnitKwCheck;
    private JLabel maskLogoPathLabel;
    private JTextField syncServerUrlField;

    // Ориентация узла по умолчанию/раскрытие незанятых групп (docs/schema-ports-
    // rework/PLAN.md, задача T3.3) — заменяют собой прежние signalConnectorsVertical
    // Check/signalConnectorDisplayModeCheck (см. UserProfile — те теперь только
    // СОВМЕСТИМЫЙ фасад поверх этих настоящих полей, читать/писать напрямую).
    private JComboBox<com.vjstb.ledscheme.model.NodeOrientation> signalOrientationCombo;
    private JComboBox<com.vjstb.ledscheme.model.NodeOrientation> powerOrientationCombo;
    private JComboBox<com.vjstb.ledscheme.settings.GroupDisplayMode> signalGroupDisplayCombo;
    private JComboBox<com.vjstb.ledscheme.settings.GroupDisplayMode> powerGroupDisplayCombo;

    // Строки-панели (кнопки/спиннеры/поле/дропдаун) — тоже общие для обеих раскладок.
    private JPanel snapRow;
    private JPanel wireHopRow;
    private JPanel schemaStylePresetRow;
    private JPanel schemaRenderModeRow;
    private JPanel schemaRouteStubRow;
    private JPanel exportRow;
    private JPanel logoRow;
    private JPanel syncRow;

    /** Все чекбоксы разом — для {@link #restoreLabels()} / {@link #stripLabels()}. */
    private JCheckBox[] allChecks;

    private JPanel bodyHost;
    /** Раскладка, под которую сейчас собрано тело окна — чтобы пересобирать только при смене. */
    private boolean lastMatrixView;

    public PreferencesDialog(Window owner, SettingsManager settings) {
        super(owner, "Персонализация — предпочтения", ModalityType.MODELESS);
        this.settings = settings;
        // Реагируем на внешние изменения настроек: смену активного профиля, правку
        // тех же чекбоксов из другого окна персонализации И смену раскладки
        // список/матрица дропдауном в окне «цвета и профили».
        settings.addListener(this::onSettingsChanged);

        createControls();

        bodyHost = new JPanel(new BorderLayout());
        bodyHost.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(bodyHost, BorderLayout.CENTER);
        root.add(closeRow, BorderLayout.SOUTH);
        setContentPane(root);

        rebuildBody();
        setLocationRelativeTo(owner);
    }

    // ==================================================================
    //  Создание элементов управления (один раз)
    // ==================================================================

    private void createControls() {
        previewWidgetCheck = check("Мини-превью всей сцены в углу холста (Питание/Сигнал)",
                settings.activeProfile().isPreviewWidgetEnabled(),
                "Показывает уменьшенную схему всех экранов сцены поверх холста с текущей прописью этапа — видно"
                        + " общую картину, не переключаясь между экранами.",
                settings::setPreviewWidgetEnabled);

        canvasSnapToCenterCheck = check("«Генерация масок»: Shift-перетаскивание экрана — доп. прилипание"
                        + " к центру холста",
                settings.activeProfile().isCanvasSnapToCenter(),
                "При зажатом Shift экран и так прилипает к краям холста и других экранов — этот пункт добавляет"
                        + " ещё и прилипание к центру холста.",
                settings::setCanvasSnapToCenter);

        snapRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        snapRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        snapRow.setToolTipText("Общая настройка для холста «Генерация масок», кабинетов внутри экрана и общей"
                + " схемы: порог — на каком расстоянии начинает притягивать; сила — насколько жёстко (100% —"
                + " прилипает точно к цели, меньше — курсор лишь частично тянется к ней, не прилипая намертво).");
        snapRow.add(new JLabel("Прилипание при Shift-перетаскивании — порог (px):"));
        snapThresholdSpinner = new JSpinner(
                new SpinnerNumberModel(settings.activeProfile().getSnapThresholdPx(), 2, 40, 1));
        snapThresholdSpinner.addChangeListener(e ->
                settings.setSnapThresholdPx((Integer) snapThresholdSpinner.getValue()));
        MathFields.enableExpressions(snapThresholdSpinner);
        snapRow.add(snapThresholdSpinner);
        snapRow.add(new JLabel("сила (%):"));
        snapStrengthSpinner = new JSpinner(
                new SpinnerNumberModel(settings.activeProfile().getSnapStrengthPercent(), 10, 100, 5));
        snapStrengthSpinner.addChangeListener(e ->
                settings.setSnapStrengthPercent((Integer) snapStrengthSpinner.getValue()));
        MathFields.enableExpressions(snapStrengthSpinner);
        snapRow.add(snapStrengthSpinner);

        foolProofWiringCheck = check("«Защита от дурака» (нельзя соединять вход со входом и выход с выходом)",
                settings.activeProfile().isFoolProofWiringEnabled(),
                "Блокирует попытку провести линию между двумя входами или двумя выходами на общей схеме — частая"
                        + " случайная ошибка при рисовании. Действует одинаково для сигнала и питания, отдельной"
                        + " настройки на каждый режим нет.",
                settings::setFoolProofWiringEnabled);

        schemaScreensAsWiringCheck = check("Узел экрана на общей схеме показывает схему расключения"
                        + " его кабинетов",
                settings.activeProfile().isSchemaScreensAsWiringDiagram(),
                "Включено — узел экрана рисует уменьшенную схему коммутации его кабинетов (как в Питании/Сигнале)."
                        + " Выключено — узел экрана выглядит как обычный прямоугольный блок с названием, без деталей"
                        + " расключения. Действует одинаково для сигнала и питания.",
                settings::setSchemaScreensAsWiringDiagram);

        wireHopRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        wireHopRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        wireHopRow.setToolTipText("В месте пересечения двух линий связи верхняя рисуется дугой в обход нижней, а не"
                + " простым перекрестьем. Сверху считается линия, чей сегмент в точке пересечения длиннее. Только"
                + " внешний вид, действует на сигнал и питание сразу. «Дуга» — компактная полукруглая (как в ГОСТ);"
                + " «усечённая дуга» держит просвет по всей ширине, когда одна дуга накрывает две близкие линии;"
                + " «не рисовать обходы» — обычное перекрестье.");
        wireHopRow.add(new JLabel("«Мостики» на пересечениях линий связи:"));
        wireHopStyleCombo = new JComboBox<>(WireHopStyle.values());
        wireHopStyleCombo.setSelectedItem(settings.activeProfile().getSchemaWireHopStyle());
        wireHopStyleCombo.addActionListener(e -> {
            if (!refreshingWireHop) {
                settings.setSchemaWireHopStyle((WireHopStyle) wireHopStyleCombo.getSelectedItem());
            }
        });
        wireHopRow.add(wireHopStyleCombo);

        schemaStylePresetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        schemaStylePresetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaStylePresetRow.setToolTipText("«Экранный» — обычный вид схемы, следует теме интерфейса (тёмная/светлая)."
                + " «Печатный» — белый фон, жёлтые блоки, номинальные цвета линий, для печати/PDF. Действует на"
                + " сигнал и питание сразу; пользовательский цвет отдельной связи важнее пресета.");
        schemaStylePresetRow.add(new JLabel("Оформление общей схемы:"));
        schemaStylePresetCombo = new JComboBox<>(SchemaStylePreset.values());
        schemaStylePresetCombo.setSelectedItem(settings.activeProfile().getSchemaStylePreset());
        schemaStylePresetCombo.addActionListener(e -> {
            if (!refreshingSchemaStylePreset) {
                settings.setSchemaStylePreset((SchemaStylePreset) schemaStylePresetCombo.getSelectedItem());
            }
        });
        schemaStylePresetRow.add(schemaStylePresetCombo);

        // Переключатель «старый способ рисования» (docs/schema-ports-rework/
        // PLAN.md, задача T5.5, D16) — рядом с «Оформление общей схемы» (D12),
        // тот же паттерн ряда/комбобокса, что и у неё.
        schemaRenderModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        schemaRenderModeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaRenderModeRow.setToolTipText("«Современный» — гнёзда на рамке блока, роли интерфейсов, ориентация"
                + " блока, ортогональные связи, перетаскивание групп гнёзд (по умолчанию). «Классический» —"
                + " прежний вид схемы (до появления этих возможностей): гнёзда строкой у одного из краёв блока,"
                + " без ролей/ориентации/орто-трассировки/перетаскивания групп/сетевых блоков-устройств."
                + " Действует на сигнал и питание сразу, глобально для всех проектов этого профиля.");
        schemaRenderModeRow.add(new JLabel("Способ отрисовки общей схемы:"));
        schemaRenderModeCombo = new JComboBox<>(com.vjstb.ledscheme.settings.SchemaRenderMode.values());
        schemaRenderModeCombo.setSelectedItem(settings.activeProfile().getSchemaRenderMode());
        schemaRenderModeCombo.addActionListener(e -> {
            if (!refreshingSchemaRenderMode) {
                settings.setSchemaRenderMode(
                        (com.vjstb.ledscheme.settings.SchemaRenderMode) schemaRenderModeCombo.getSelectedItem());
            }
        });
        schemaRenderModeRow.add(schemaRenderModeCombo);

        // Длина уса связи общей схемы (пожелание пользователя 2026-09-18: раньше
        // была жёстко зашита в OrthogonalRouter (12px), из-за чего последний
        // поворот перед гнездом визуально "жался" к самому блоку) — тот же паттерн
        // спиннера, что и snapRow выше.
        schemaRouteStubRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        schemaRouteStubRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaRouteStubRow.setToolTipText("Длина прямого отрезка перед первым/последним поворотом ортогональной"
                + " связи (режим маршрута «Авто под 90°») — та же величина и отступ, с которым линия обходит"
                + " ЧУЖИЕ блоки на своём пути, а не только точку входа в своё гнездо. Больше значение — заметнее"
                + " отступ от блоков по всему маршруту, вместо линий, жмущихся вплотную к рамкам.");
        schemaRouteStubRow.add(new JLabel("Отступ связей общей схемы от блоков (px):"));
        schemaRouteStubSpinner = new JSpinner(
                new SpinnerNumberModel(settings.activeProfile().getSchemaRouteStubPx(), 0, 200, 2));
        schemaRouteStubSpinner.addChangeListener(e ->
                settings.setSchemaRouteStubPx((Integer) schemaRouteStubSpinner.getValue()));
        MathFields.enableExpressions(schemaRouteStubSpinner);
        schemaRouteStubRow.add(schemaRouteStubSpinner);

        exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        exportRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportRow.setToolTipText("Папка, в которую по умолчанию сохраняются экспортированные схемы/маски/пресеты"
                + " (пока не выбрана папка явно на конкретном этапе) — по умолчанию ~/Documents/Video. Явный выбор"
                + " папки кнопкой «Папка…» на этапе Вывод/Генерация масок по-прежнему приоритетнее.");
        JButton exportChooseBtn = new JButton("Папка экспорта…");
        exportChooseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Выберите папку по умолчанию для экспорта");
            String current = settings.activeProfile().getExportRootFolder();
            if (current != null && !current.isBlank()) {
                fc.setCurrentDirectory(new File(current));
            }
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                settings.setExportRootFolder(fc.getSelectedFile().getAbsolutePath());
            }
        });
        JButton exportClearBtn = new JButton("Сбросить");
        exportClearBtn.addActionListener(e -> settings.setExportRootFolder(null));
        exportRootFolderLabel = new JLabel();
        exportRow.add(exportChooseBtn);
        exportRow.add(exportClearBtn);
        exportRow.add(exportRootFolderLabel);

        signalSocketWiringCheck = check(
                "Линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isSignalSocketWiringEnabled(),
                "Включено — конец линии привязывается к нужному разъёму/гнезду карты, с проверкой числа свободных"
                        + " линий на нём. Выключено — линия просто соединяет два блока оборудования целиком, разъёмы"
                        + " в блоках — только справочная информация о комплектации. Открывает настройки ниже, работающие"
                        + " только вместе с этим режимом. У питания — своя отдельная копия этой настройки.",
                v -> {
                    settings.setSignalSocketWiringEnabled(v);
                    applySocketDependentEnablement();
                });

        signalOrientationCombo = orientationCombo(settings.activeProfile().getSignalDefaultOrientation(),
                "Ориентация блоков по умолчанию для схемы СИГНАЛА (→/↓/←/↑, как поворот блока в yEd/Simulink) —"
                        + " каждый блок можно дополнительно повернуть отдельно (меню блока/Ctrl+R), это умолчание"
                        + " только для новых/непереопределённых. Заменяет собой прежнее «гнёзда сверху/снизу».",
                settings::setSignalDefaultOrientation);
        signalGroupDisplayCombo = groupDisplayCombo(settings.activeProfile().getSignalGroupDisplay(),
                "«Авто» — незанятая группа разъёмов сворачивается в одно гнездо «N×Тип», как только на неё"
                        + " подводят связь — разворачивается в отдельные пронумерованные гнёзда. «Всегда развёрнуты» —"
                        + " все группы держатся развёрнутыми независимо от занятости. Отдельную группу можно"
                        + " дополнительно принудительно свернуть/развернуть (меню группы, ПКМ по гнезду).",
                settings::setSignalGroupDisplay);

        signalChainEndpointSocketsCheck = check(
                "Вводные кабинеты цепочек — тоже гнёзда подключения",
                settings.activeProfile().isSignalChainEndpointSocketsEnabled(),
                "Включено — на миниатюре расключения экрана (см. «Узел экрана показывает схему расключения его"
                        + " кабинетов») вводной кабинет основной сигнальной цепочки и, если задан резерв, последний"
                        + " кабинет той же цепочки — становятся гнёздами: к ним можно подвести линию на общей схеме,"
                        + " как к обычному разъёму. Своя отдельная копия для питания. Доступно только при включённой"
                        + " настройке «линия цепляется за конкретный разъём».",
                settings::setSignalChainEndpointSocketsEnabled);

        signalSchemaAutoPopulateCheck = check(
                "Автозаполнение: при переходе на общую схему добавлять расключенные экраны"
                        + " и использованные контроллеры",
                settings.activeProfile().isSignalSchemaAutoPopulateEnabled(),
                "Включено — при переключении с «Расключение экрана» на «Общая схема» уже расключенные экраны и"
                        + " использованные контроллеры сцены автоматически появляются в схеме сигнала, если их там ещё"
                        + " нет (контроллер зеркалит реальную комплектацию карт) — не нужно добавлять их вручную по"
                        + " одному. Если ВДОБАВОК включено «Вводные кабинеты цепочек — тоже гнёзда подключения» —"
                        + " гнёзда экранов автоматически соединяются с соответствующими портами использованных"
                        + " контроллеров. Уже добавленные вручную узлы и связи не трогает, повторный переход дублей не"
                        + " создаёт, а разорванную вручную связь не восстанавливает. Доступно только при включённой"
                        + " настройке «линия цепляется за конкретный разъём».",
                settings::setSignalSchemaAutoPopulateEnabled);

        signalSceneStatsCheck = check("Показывать блок «Статистика сцены» под статистикой экрана",
                settings.activeProfile().isSignalSceneStatsEnabled(),
                "Суммарные показатели ПО ВСЕЙ сцене — отдельно от статистики активного экрана, видна независимо от"
                        + " «Показать все экраны сцены». Своя отдельная копия для питания.",
                settings::setSignalSceneStatsEnabled);

        powerSocketWiringCheck = check(
                "Линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isPowerSocketWiringEnabled(),
                "То же самое, но для схемы питания — отдельная настройка (см. одноимённый переключатель в группе"
                        + " «Сигнал»). Открывает настройки ниже, работающие только вместе с этим режимом.",
                v -> {
                    settings.setPowerSocketWiringEnabled(v);
                    applySocketDependentEnablement();
                });

        powerOrientationCombo = orientationCombo(settings.activeProfile().getPowerDefaultOrientation(),
                "То же самое, но для схемы питания — отдельная настройка (см. группу «Сигнал»).",
                settings::setPowerDefaultOrientation);
        powerGroupDisplayCombo = groupDisplayCombo(settings.activeProfile().getPowerGroupDisplay(),
                "То же самое, но для схемы питания — отдельная настройка (см. группу «Сигнал»).",
                settings::setPowerGroupDisplay);

        powerChainEndpointSocketsCheck = check(
                "Вводные кабинеты цепочек — тоже гнёзда подключения",
                settings.activeProfile().isPowerChainEndpointSocketsEnabled(),
                "То же самое, но для схемы питания — отдельная настройка (см. группу «Сигнал»): вводной кабинет"
                        + " каждой силовой цепочки становится гнездом. Доступно только при включённой настройке"
                        + " «линия цепляется за конкретный разъём».",
                settings::setPowerChainEndpointSocketsEnabled);

        powerSchemaAutoPopulateCheck = check(
                "Автозаполнение: при переходе на общую схему добавлять расключенные экраны"
                        + " и заполнять «проходные»",
                settings.activeProfile().isPowerSchemaAutoPopulateEnabled(),
                "Включено — при переключении с «Расключение экрана» на «Общая схема» уже расключенные экраны"
                        + " автоматически появляются в схеме питания, если их там ещё нет. У питания нет понятия"
                        + " контроллера — вместо этого, если ВДОБАВОК включено «Вводные кабинеты цепочек — тоже гнёзда"
                        + " подключения», вводные кабинеты распределяются по СВОБОДНЫМ разъёмам уже добавленных на схему"
                        + " узлов типа «Распределение» (щиты/проходные) — только разъёмы ОСНОВНОГО (самого ёмкого) типа"
                        + " узла, разъёмы другого номинала/типа на том же узле пропускаются — максимально заполняя каждый"
                        + " по очереди, прежде чем переходить к следующему. Новые такие узлы не создаются, их нужно"
                        + " разместить на схеме заранее. Уже добавленные вручную узлы и связи не трогает, повторный"
                        + " переход дублей не создаёт, а разорванную вручную связь не восстанавливает. Доступно только"
                        + " при включённой настройке «линия цепляется за конкретный разъём».",
                settings::setPowerSchemaAutoPopulateEnabled);

        powerSceneStatsCheck = check("Показывать блок «Статистика сцены» под статистикой экрана",
                settings.activeProfile().isPowerSceneStatsEnabled(),
                "Суммарные кабинеты/мощность/вес и разбивка по фазам ПО ВСЕЙ сцене — отдельно от статистики активного"
                        + " экрана, видна независимо от «Показать все экраны сцены». Своя отдельная копия для сигнала.",
                settings::setPowerSceneStatsEnabled);

        loadTrackingCheck = check("Контроль электрической нагрузки"
                        + " (предупреждения о перегрузке цепочек/щитов)",
                settings.activeProfile().isLoadTrackingEnabled(),
                "Сравнивает нагрузку каждой силовой цепочки/щита с ёмкостью его разъёма и подсвечивает превышение."
                        + " Выключите для нестандартного случая, который расчёт не покрывает — дальше считайте нагрузку"
                        + " самостоятельно (см. Руководство).",
                settings::setLoadTrackingEnabled);

        powerUnitKwCheck = check("Показывать мощность/нагрузку в киловаттах (кВт), а не ваттах (Вт)",
                settings.activeProfile().isPowerUnitKw(),
                "Меняет единицы отображения мощности везде в приложении (карточки узлов, статистика этапов,"
                        + " экспортные документы) — на внутренний расчёт нагрузки не влияет.",
                settings::setPowerUnitKw);

        logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoRow.setToolTipText("Свой логотип для «Генерация масок»: настраивается один раз здесь и дальше"
                + " применяется на любом гриде любого проекта, где включён чекбокс «Лого» в таблице гридов.");
        JButton logoBtn = new JButton("Логотип для масок…");
        logoBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Выберите файл логотипа");
            fc.setFileFilter(new FileNameExtensionFilter("Изображения (PNG, JPG, GIF, BMP)",
                    "png", "jpg", "jpeg", "gif", "bmp"));
            String current = settings.activeProfile().getMaskLogoImagePath();
            if (current != null) {
                fc.setSelectedFile(new File(current));
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                settings.setMaskLogoImagePath(fc.getSelectedFile().getAbsolutePath());
            }
        });
        JButton logoClearBtn = new JButton("Убрать");
        logoClearBtn.addActionListener(e -> settings.setMaskLogoImagePath(null));
        maskLogoPathLabel = new JLabel();
        logoRow.add(logoBtn);
        logoRow.add(logoClearBtn);
        logoRow.add(maskLogoPathLabel);

        syncRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        syncRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncRow.setToolTipText("Альтернативный адрес сервера — используйте, если обычное подключение к"
                + " https://ledschemedesigner.ru:8443 не проходит (сеть блокирует нестандартный порт)."
                + " Пусто — адрес по умолчанию.");
        syncRow.add(new JLabel("Адрес сервера (переопределение):"));
        syncServerUrlField = new JTextField(24);
        Runnable commit = () -> {
            String text = syncServerUrlField.getText().trim();
            settings.setSyncServerUrlOverride(text.isEmpty() ? null : text);
        };
        syncServerUrlField.addActionListener(e -> commit.run());
        syncServerUrlField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commit.run();
            }
        });
        syncRow.add(syncServerUrlField);

        allChecks = new JCheckBox[] {
                previewWidgetCheck, canvasSnapToCenterCheck, foolProofWiringCheck, schemaScreensAsWiringCheck,
                signalSocketWiringCheck,
                signalChainEndpointSocketsCheck,
                signalSchemaAutoPopulateCheck, signalSceneStatsCheck, powerSocketWiringCheck,
                powerChainEndpointSocketsCheck,
                powerSchemaAutoPopulateCheck, powerSceneStatsCheck, loadTrackingCheck, powerUnitKwCheck,
        };
    }

    /** Общий конструктор чекбокса: подпись хранится в client-property, чтобы матрица
     *  могла её временно снять (текст уходит в первую колонку таблицы), а список —
     *  вернуть. */
    private JCheckBox check(String text, boolean selected, String tooltip, Consumer<Boolean> apply) {
        JCheckBox c = new JCheckBox(text, selected);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setToolTipText(tooltip);
        c.putClientProperty("fullText", text);
        c.addActionListener(e -> apply.accept(c.isSelected()));
        return c;
    }

    /** Тот же приём гашения слушателя на программную установку, что {@link
     *  #refreshingWireHop}/{@link #refreshingSchemaStylePreset} — один общий флаг на
     *  все четыре новых дропдауна ({@link #signalOrientationCombo}/{@link
     *  #powerOrientationCombo}/{@link #signalGroupDisplayCombo}/{@link
     *  #powerGroupDisplayCombo}), т.к. {@link #refresh()} всегда переустанавливает
     *  их все разом (docs/schema-ports-rework/PLAN.md, задача T3.3). */
    private boolean refreshingSchemaLayoutCombos;

    private JComboBox<com.vjstb.ledscheme.model.NodeOrientation> orientationCombo(
            com.vjstb.ledscheme.model.NodeOrientation selected, String tooltip,
            Consumer<com.vjstb.ledscheme.model.NodeOrientation> apply) {
        JComboBox<com.vjstb.ledscheme.model.NodeOrientation> combo =
                new JComboBox<>(com.vjstb.ledscheme.model.NodeOrientation.values());
        combo.setSelectedItem(selected);
        combo.setToolTipText(tooltip);
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.addActionListener(e -> {
            if (!refreshingSchemaLayoutCombos) {
                apply.accept((com.vjstb.ledscheme.model.NodeOrientation) combo.getSelectedItem());
            }
        });
        return combo;
    }

    private JComboBox<com.vjstb.ledscheme.settings.GroupDisplayMode> groupDisplayCombo(
            com.vjstb.ledscheme.settings.GroupDisplayMode selected, String tooltip,
            Consumer<com.vjstb.ledscheme.settings.GroupDisplayMode> apply) {
        JComboBox<com.vjstb.ledscheme.settings.GroupDisplayMode> combo =
                new JComboBox<>(com.vjstb.ledscheme.settings.GroupDisplayMode.values());
        combo.setSelectedItem(selected);
        combo.setToolTipText(tooltip);
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.addActionListener(e -> {
            if (!refreshingSchemaLayoutCombos) {
                apply.accept((com.vjstb.ledscheme.settings.GroupDisplayMode) combo.getSelectedItem());
            }
        });
        return combo;
    }

    /** Строка «подпись + дропдаун» для списка по этапам — сам дропдаун (см. {@link
     *  #orientationCombo}/{@link #groupDisplayCombo}) переиспользуется и в матрице
     *  ({@link #buildMatrixBody()}), там БЕЗ подписи (у ячейки уже есть своя, см.
     *  {@link #triple}) — тот же приём, что {@link #wireHopRow}/{@link
     *  #schemaStylePresetRow}, только строится заново при каждой пересборке списка,
     *  а не хранится полем, т.к. подпись тут не нужна матрице. */
    private JPanel comboRow(String label, JComponent combo) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel(label));
        row.add(combo);
        return row;
    }

    private void restoreLabels() {
        for (JCheckBox c : allChecks) {
            c.setText((String) c.getClientProperty("fullText"));
        }
    }

    private void stripLabels() {
        for (JCheckBox c : allChecks) {
            c.setText("");
        }
    }

    // ==================================================================
    //  Пересборка тела окна при смене раскладки
    // ==================================================================

    private void onSettingsChanged() {
        if (settings.activeProfile().isPreferencesMatrixView() != lastMatrixView) {
            rebuildBody();
        } else {
            refresh();
        }
    }

    private void rebuildBody() {
        lastMatrixView = settings.activeProfile().isPreferencesMatrixView();
        bodyHost.removeAll();
        bodyHost.add(lastMatrixView ? buildMatrixBody() : buildListBody(), BorderLayout.CENTER);
        refresh();
        bodyHost.revalidate();
        bodyHost.repaint();
        pack();
    }

    // ==================================================================
    //  Раскладка 1 — список по этапам
    // ==================================================================

    private JComponent buildListBody() {
        restoreLabels();
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(UiKit.section("Общие", stack(previewWidgetCheck, canvasSnapToCenterCheck, snapRow,
                foolProofWiringCheck, schemaScreensAsWiringCheck, wireHopRow, schemaStylePresetRow,
                schemaRenderModeRow, schemaRouteStubRow, exportRow)));
        content.add(Box.createVerticalStrut(8));
        content.add(UiKit.section("Сигнал", stack(signalSocketWiringCheck,
                comboRow("Ориентация блоков по умолчанию:", signalOrientationCombo),
                comboRow("Незанятые группы гнёзд:", signalGroupDisplayCombo),
                signalChainEndpointSocketsCheck, signalSchemaAutoPopulateCheck,
                signalSceneStatsCheck)));
        content.add(Box.createVerticalStrut(8));
        content.add(UiKit.section("Питание", stack(powerSocketWiringCheck,
                comboRow("Ориентация блоков по умолчанию:", powerOrientationCombo),
                comboRow("Незанятые группы гнёзд:", powerGroupDisplayCombo),
                powerChainEndpointSocketsCheck, powerSchemaAutoPopulateCheck,
                powerSceneStatsCheck, loadTrackingCheck, powerUnitKwCheck)));
        content.add(Box.createVerticalStrut(8));
        content.add(UiKit.section("Генерация масок", stack(logoRow)));
        content.add(Box.createVerticalStrut(8));
        content.add(UiKit.section("Синхронизация", stack(syncRow)));

        return content;
    }

    /** Вертикальный бокс из переданных компонентов, все выровнены по левому краю. */
    private JPanel stack(JComponent... items) {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent c : items) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(c);
        }
        return body;
    }

    // ==================================================================
    //  Раскладка 2 — матрица «функция × этап»
    // ==================================================================

    private JComponent buildMatrixBody() {
        stripLabels();
        // Верхнюю и левую линию сетки рисует контейнер, нижнюю и правую — каждая
        // ячейка (см. cellBorder): двойных линий на стыках не возникает.
        JPanel g = new JPanel(new GridBagLayout());
        g.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, gridColor()));
        int[] row = {0};

        g.add(headCell("Настройка", FlowLayout.LEFT), cellGbc(0, row[0], 1));
        g.add(headCell("Общие", FlowLayout.CENTER), cellGbc(1, row[0], 1));
        g.add(headCell("Сигнал", FlowLayout.CENTER), cellGbc(2, row[0], 1));
        g.add(headCell("Питание", FlowLayout.CENTER), cellGbc(3, row[0], 1));
        row[0]++;

        category(g, row, "Холст и сцена");
        triple(g, row, "Мини-превью всей сцены в углу холста", null,
                previewWidgetCheck, DASH, DASH);
        triple(g, row, "«Генерация масок»: доп. прилипание Shift к центру холста", null,
                canvasSnapToCenterCheck, DASH, DASH);
        span(g, row, "Прилипание при Shift — порог / сила", snapRow);

        category(g, row, "Действует на сигнал и питание сразу");
        span(g, row, "«Защита от дурака»: нельзя вход↔вход и выход↔выход", foolProofWiringCheck);
        span(g, row, "Узел экрана = мини-схема расключения его кабинетов", schemaScreensAsWiringCheck);
        span(g, row, "«Мостики» на пересечениях линий связи (обход, как в ГОСТ)", wireHopRow);
        span(g, row, "Оформление общей схемы (экранное/печатное)", schemaStylePresetRow);
        span(g, row, "Способ отрисовки общей схемы (современный/классический)", schemaRenderModeRow);
        span(g, row, "Отступ связей общей схемы от блоков (px)", schemaRouteStubRow);

        category(g, row, "Коммутация через гнёзда разъёмов");
        triple(g, row, "Линия цепляется за конкретный разъём, а не за блок", "мастер-переключатель для строк ниже",
                DASH, signalSocketWiringCheck, powerSocketWiringCheck);
        triple(g, row, "Ориентация блоков по умолчанию", null,
                DASH, signalOrientationCombo, powerOrientationCombo);
        triple(g, row, "Незанятые группы гнёзд", null,
                DASH, signalGroupDisplayCombo, powerGroupDisplayCombo);
        triple(g, row, "Вводные кабинеты цепочек — тоже гнёзда подключения", null,
                DASH, signalChainEndpointSocketsCheck, powerChainEndpointSocketsCheck);
        triple(g, row, "Автозаполнение схемы при переходе с расключения", null,
                DASH, signalSchemaAutoPopulateCheck, powerSchemaAutoPopulateCheck);
        triple(g, row, "Блок «Статистика сцены» под статистикой экрана", null,
                DASH, signalSceneStatsCheck, powerSceneStatsCheck);

        category(g, row, "Нагрузка и единицы — только питание");
        triple(g, row, "Контроль электрической нагрузки (перегрузка цепочек/щитов)", null,
                DASH, DASH, loadTrackingCheck);
        triple(g, row, "Показывать мощность/нагрузку в кВт, а не Вт", null,
                DASH, DASH, powerUnitKwCheck);

        category(g, row, "Отдельные параметры — кнопка/поле, не флаг");
        span(g, row, "Логотип для «Генерация масок»", logoRow);
        span(g, row, "Папка экспорта по умолчанию", exportRow);
        span(g, row, "Адрес сервера (переопределение синхронизации)", syncRow);

        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0;
        glue.gridy = row[0];
        glue.gridwidth = 4;
        glue.weighty = 1;
        glue.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        g.add(filler, glue);

        return g;
    }

    /** Цвет линий сетки матрицы — берём тот же, чем LaF рисует границы компонентов. */
    private static java.awt.Color gridColor() {
        java.awt.Color c = UIManager.getColor("Component.borderColor");
        if (c == null) {
            c = UIManager.getColor("Separator.foreground");
        }
        return c != null ? c : java.awt.Color.GRAY;
    }

    /** Нижняя + правая граница ячейки (верхняя и левая — на контейнере таблицы). */
    private static javax.swing.border.Border cellBorder(int bottomThickness) {
        return BorderFactory.createMatteBorder(0, 0, bottomThickness, 1, gridColor());
    }

    private GridBagConstraints cellGbc(int x, int y, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = (x == 0) ? 1.0 : 0.0;
        return c;
    }

    private JComponent headCell(String text, int align) {
        JPanel p = new JPanel(new FlowLayout(align, 6, 5));
        p.setOpaque(false);
        p.setBorder(cellBorder(2));
        if (align != FlowLayout.LEFT) {
            p.setPreferredSize(new Dimension(STAGE_COL_W, 26));
            p.setMinimumSize(new Dimension(STAGE_COL_W, 26));
        }
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l);
        return p;
    }

    /** Строка-заголовок раздела: одна ячейка на всю ширину таблицы, с фоновой заливкой. */
    private void category(JPanel g, int[] row, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        p.setOpaque(true);
        p.setBackground(categoryBackground());
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 1, gridColor()));
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() - 1f));
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        p.add(l);
        g.add(p, cellGbc(0, row[0]++, 4));
    }

    private static java.awt.Color categoryBackground() {
        java.awt.Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            return new java.awt.Color(128, 128, 128, 32);
        }
        int d = 10;
        return new java.awt.Color(Math.max(0, base.getRed() - d),
                Math.max(0, base.getGreen() - d), Math.max(0, base.getBlue() - d));
    }

    /** Строка с ячейкой на каждый этап: компонент, {@link #DASH} («—») или {@code null} (пусто). */
    private void triple(JPanel g, int[] row, String name, String meta, Object general, Object signal, Object power) {
        int y = row[0]++;
        g.add(nameCell(name, meta), cellGbc(0, y, 1));
        g.add(stageCell(general), cellGbc(1, y, 1));
        g.add(stageCell(signal), cellGbc(2, y, 1));
        g.add(stageCell(power), cellGbc(3, y, 1));
    }

    /** Строка общего пункта: колонка-подпись + одна КРУПНАЯ ячейка на все три этапа. */
    private void span(JPanel g, int[] row, String name, JComponent comp) {
        int y = row[0]++;
        g.add(nameCell(name, null), cellGbc(0, y, 1));
        JPanel big = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        big.setOpaque(false);
        big.setBorder(cellBorder(1));
        big.add(comp);
        g.add(big, cellGbc(1, y, 3));
    }

    private JComponent nameCell(String name, String meta) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(cellBorder(1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        JLabel n = new JLabel(name);
        n.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(n);
        if (meta != null) {
            JLabel m = new JLabel(meta);
            m.setAlignmentX(Component.LEFT_ALIGNMENT);
            m.setFont(m.getFont().deriveFont(m.getFont().getSize2D() - 2f));
            m.setForeground(UIManager.getColor("Label.disabledForeground"));
            col.add(m);
        }
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent stageCell(Object what) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        p.setBorder(cellBorder(1));
        p.setPreferredSize(new Dimension(STAGE_COL_W, 28));
        p.setMinimumSize(new Dimension(STAGE_COL_W, 28));
        if (what == DASH) {
            JLabel dash = new JLabel("—");
            dash.setForeground(UIManager.getColor("Label.disabledForeground"));
            p.add(dash);
        } else if (what instanceof Component c) {
            p.add(c);
        }
        return p;
    }

    // ==================================================================
    //  Общее для обеих раскладок
    // ==================================================================

    /** signalChainEndpointSocketsCheck/signalSchemaAutoPopulateCheck работают только
     *  вместе со СВОИМ signalSocketWiringCheck (без него общая схема сигнала не
     *  различает конкретные гнёзда/порты вообще) — и точно так же power-варианты со
     *  своим powerSocketWiringCheck — недоступны для включения, пока их мастер
     *  выключен, чтобы не создавать видимость рабочей настройки там, где она молча
     *  ничего не даёт. Уже включённое состояние при выключении мастер-переключателя
     *  не сбрасывается автоматически — только становится недоступным для изменения,
     *  пока мастер снова не включат. */
    private void applySocketDependentEnablement() {
        boolean signalEnabled = signalSocketWiringCheck.isSelected();
        signalChainEndpointSocketsCheck.setEnabled(signalEnabled);
        signalSchemaAutoPopulateCheck.setEnabled(signalEnabled);
        boolean powerEnabled = powerSocketWiringCheck.isSelected();
        powerChainEndpointSocketsCheck.setEnabled(powerEnabled);
        powerSchemaAutoPopulateCheck.setEnabled(powerEnabled);
    }

    private void refresh() {
        previewWidgetCheck.setSelected(settings.activeProfile().isPreviewWidgetEnabled());
        canvasSnapToCenterCheck.setSelected(settings.activeProfile().isCanvasSnapToCenter());
        snapThresholdSpinner.setValue(settings.activeProfile().getSnapThresholdPx());
        snapStrengthSpinner.setValue(settings.activeProfile().getSnapStrengthPercent());
        foolProofWiringCheck.setSelected(settings.activeProfile().isFoolProofWiringEnabled());
        schemaScreensAsWiringCheck.setSelected(settings.activeProfile().isSchemaScreensAsWiringDiagram());
        refreshingWireHop = true;
        wireHopStyleCombo.setSelectedItem(settings.activeProfile().getSchemaWireHopStyle());
        refreshingWireHop = false;
        refreshingSchemaStylePreset = true;
        schemaStylePresetCombo.setSelectedItem(settings.activeProfile().getSchemaStylePreset());
        refreshingSchemaStylePreset = false;
        refreshingSchemaRenderMode = true;
        schemaRenderModeCombo.setSelectedItem(settings.activeProfile().getSchemaRenderMode());
        refreshingSchemaRenderMode = false;
        schemaRouteStubSpinner.setValue(settings.activeProfile().getSchemaRouteStubPx());
        signalSocketWiringCheck.setSelected(settings.activeProfile().isSignalSocketWiringEnabled());
        powerSocketWiringCheck.setSelected(settings.activeProfile().isPowerSocketWiringEnabled());
        signalChainEndpointSocketsCheck.setSelected(settings.activeProfile().isSignalChainEndpointSocketsEnabled());
        powerChainEndpointSocketsCheck.setSelected(settings.activeProfile().isPowerChainEndpointSocketsEnabled());
        signalSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isSignalSchemaAutoPopulateEnabled());
        powerSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isPowerSchemaAutoPopulateEnabled());
        applySocketDependentEnablement();
        refreshingSchemaLayoutCombos = true;
        signalOrientationCombo.setSelectedItem(settings.activeProfile().getSignalDefaultOrientation());
        powerOrientationCombo.setSelectedItem(settings.activeProfile().getPowerDefaultOrientation());
        signalGroupDisplayCombo.setSelectedItem(settings.activeProfile().getSignalGroupDisplay());
        powerGroupDisplayCombo.setSelectedItem(settings.activeProfile().getPowerGroupDisplay());
        refreshingSchemaLayoutCombos = false;
        signalSceneStatsCheck.setSelected(settings.activeProfile().isSignalSceneStatsEnabled());
        powerSceneStatsCheck.setSelected(settings.activeProfile().isPowerSceneStatsEnabled());
        loadTrackingCheck.setSelected(settings.activeProfile().isLoadTrackingEnabled());
        powerUnitKwCheck.setSelected(settings.activeProfile().isPowerUnitKw());
        String logoPath = settings.activeProfile().getMaskLogoImagePath();
        maskLogoPathLabel.setText(logoPath != null ? new File(logoPath).getName() : "не задан");
        String exportRoot = settings.activeProfile().getExportRootFolder();
        exportRootFolderLabel.setText(exportRoot != null && !exportRoot.isBlank()
                ? exportRoot : "не задана (по умолчанию ~/Documents/Video)");
        String urlOverride = settings.getSyncServerUrlOverride();
        if (!syncServerUrlField.getText().equals(urlOverride != null ? urlOverride : "")) {
            syncServerUrlField.setText(urlOverride != null ? urlOverride : "");
        }
    }
}
