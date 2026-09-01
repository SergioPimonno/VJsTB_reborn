package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.ConnectorDisplayMode;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Диалог «Предпочтения»: поведенческие переключатели интерфейса, не связанные
 * с цветом или горячими клавишами (см. {@link PersonalizationDialog} — цвета/
 * профили, {@link HotkeysDialog} — горячие клавиши) — вынесены в отдельное
 * окошко, чтобы каждый раздел персонализации открывался и настраивался
 * независимо от остальных.
 *
 * <p>Группировка — по ЭТАПУ РАБОТЫ, которого настройка касается («Общие»,
 * «Сигнал», «Питание», «Генерация масок», «Синхронизация»), а не по техническому
 * подразделу («соединения»/«узлы» и т.п.), как было раньше: пользователь обычно
 * приходит сюда с вопросом «что можно настроить для сигнала/питания», а не
 * «что относится к соединениям». Внутри групп «Сигнал»/«Питание» — зеркальные
 * пары одних и тех же переключателей; название группы уже говорит, какой это
 * этап, поэтому подписи самих чекбоксов больше НЕ повторяют «Сигнал:»/
 * «Питание:» в начале (было избыточно при плоском списке/группировке по теме,
 * стало откровенно лишним при группировке по этапу).</p>
 *
 * <p>Настройки, которые не относятся ни к сигналу, ни к питанию, но и не тянут
 * на отдельную группу (форма экрана как отдельное окно, папка экспорта по
 * умолчанию), сложены в «Общие» — по тому же принципу «не привязано к
 * конкретному этапу общей схемы».</p>
 */
public class PreferencesDialog extends JDialog {

    private final SettingsManager settings;
    private JCheckBox previewWidgetCheck;
    private JCheckBox canvasSnapToCenterCheck;
    private JCheckBox shapeEditorFloatingCheck;
    private JSpinner snapThresholdSpinner;
    private JSpinner snapStrengthSpinner;
    private JCheckBox foolProofWiringCheck;
    private JCheckBox schemaScreensAsWiringCheck;
    private JLabel exportRootFolderLabel;
    private JCheckBox signalSocketWiringCheck;
    private JCheckBox signalConnectorDisplayModeCheck;
    private JCheckBox signalConnectorsVerticalCheck;
    private JCheckBox signalChainEndpointSocketsCheck;
    private JCheckBox signalSchemaAutoPopulateCheck;
    private JCheckBox signalSceneStatsCheck;
    private JCheckBox powerSocketWiringCheck;
    private JCheckBox powerConnectorDisplayModeCheck;
    private JCheckBox powerConnectorsVerticalCheck;
    private JCheckBox powerChainEndpointSocketsCheck;
    private JCheckBox powerSchemaAutoPopulateCheck;
    private JCheckBox powerSceneStatsCheck;
    private JCheckBox loadTrackingCheck;
    private JCheckBox powerUnitKwCheck;
    private JLabel maskLogoPathLabel;
    private JTextField syncServerUrlField;

    public PreferencesDialog(Window owner, SettingsManager settings) {
        super(owner, "Персонализация — предпочтения", ModalityType.MODELESS);
        this.settings = settings;
        // Обновляет чекбоксы, если настройки поменялись извне (например, тот же
        // профиль отредактировали через другое окно персонализации, или сменили
        // активный профиль целиком) — окошко может быть открыто одновременно с
        // остальными разделами персонализации.
        settings.addListener(this::refresh);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildGeneralGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildSignalGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildPowerGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildMaskGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildSyncGroup());
        content.add(Box.createVerticalStrut(10));

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        content.add(closeRow);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Настройки, не привязанные к конкретному этапу (сигнал/питание) — действуют
     *  одинаково в обоих режимах общей схемы либо касаются холста/сцены вообще
     *  (плюс «пристроенные» сюда одиночки — форма экрана отдельным окном, папка
     *  экспорта по умолчанию, — которым отдельная группа не нужна). */
    private JPanel buildGeneralGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewWidgetCheck = new JCheckBox("Мини-превью всей сцены в углу холста (Питание/Сигнал)",
                settings.activeProfile().isPreviewWidgetEnabled());
        previewWidgetCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewWidgetCheck.setToolTipText("Показывает уменьшенную схему всех экранов сцены поверх холста"
                + " с текущей прописью этапа — видно общую картину, не переключаясь между экранами.");
        previewWidgetCheck.addActionListener(e -> settings.setPreviewWidgetEnabled(previewWidgetCheck.isSelected()));
        body.add(previewWidgetCheck);

        canvasSnapToCenterCheck = new JCheckBox("«Генерация масок»: Shift-перетаскивание экрана — доп. прилипание"
                + " к центру холста",
                settings.activeProfile().isCanvasSnapToCenter());
        canvasSnapToCenterCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        canvasSnapToCenterCheck.setToolTipText("При зажатом Shift экран и так прилипает к краям холста и других"
                + " экранов — этот пункт добавляет ещё и прилипание к центру холста.");
        canvasSnapToCenterCheck.addActionListener(e ->
                settings.setCanvasSnapToCenter(canvasSnapToCenterCheck.isSelected()));
        body.add(canvasSnapToCenterCheck);

        JPanel snapRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
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
        body.add(snapRow);

        foolProofWiringCheck = new JCheckBox("«Защита от дурака» (нельзя соединять вход со входом и выход с выходом)",
                settings.activeProfile().isFoolProofWiringEnabled());
        foolProofWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        foolProofWiringCheck.setToolTipText("Блокирует попытку провести линию между двумя входами или двумя"
                + " выходами на общей схеме — частая случайная ошибка при рисовании. Действует одинаково для"
                + " сигнала и питания, отдельной настройки на каждый режим нет.");
        foolProofWiringCheck.addActionListener(e ->
                settings.setFoolProofWiringEnabled(foolProofWiringCheck.isSelected()));
        body.add(foolProofWiringCheck);

        schemaScreensAsWiringCheck = new JCheckBox("Узел экрана на общей схеме показывает схему расключения"
                + " его кабинетов",
                settings.activeProfile().isSchemaScreensAsWiringDiagram());
        schemaScreensAsWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaScreensAsWiringCheck.setToolTipText("Включено — узел экрана рисует уменьшенную схему коммутации его"
                + " кабинетов (как в Питании/Сигнале). Выключено — узел экрана выглядит как обычный прямоугольный"
                + " блок с названием, без деталей расключения. Действует одинаково для сигнала и питания.");
        schemaScreensAsWiringCheck.addActionListener(e ->
                settings.setSchemaScreensAsWiringDiagram(schemaScreensAsWiringCheck.isSelected()));
        body.add(schemaScreensAsWiringCheck);

        shapeEditorFloatingCheck = new JCheckBox("«Форма экрана»: открывать отдельным всплывающим окном,"
                + " а не областью в «Сетапе»",
                settings.activeProfile().isShapeEditorFloating());
        shapeEditorFloatingCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        shapeEditorFloatingCheck.setToolTipText("Окно живое — показывает ТЕКУЩИЙ выбранный экран и обновляется"
                + " при смене выбора, не фиксированный снимок на момент открытия. Кнопка «Изменить форму экрана»"
                + " в «Сетапе» тогда открывает/поднимает это окно вместо показа встроенной секции на месте.");
        shapeEditorFloatingCheck.addActionListener(e ->
                settings.setShapeEditorFloating(shapeEditorFloatingCheck.isSelected()));
        body.add(shapeEditorFloatingCheck);

        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
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
        body.add(exportRow);

        return (JPanel) UiKit.section("Общие", body);
    }

    /** Общая схема СИГНАЛА — зеркальная пара группы «Питание» ниже, те же
     *  переключатели в том же порядке (плюс не имеющие пары «Контроль нагрузки»/
     *  «кВт», это чисто силовые понятия). */
    private JPanel buildSignalGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        signalSocketWiringCheck = new JCheckBox(
                "Линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isSignalSocketWiringEnabled());
        signalSocketWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalSocketWiringCheck.setToolTipText("Включено — конец линии привязывается к нужному разъёму/гнезду карты,"
                + " с проверкой числа свободных линий на нём. Выключено — линия просто соединяет два блока"
                + " оборудования целиком, разъёмы в блоках — только справочная информация о комплектации. Открывает"
                + " настройки ниже в этой же группе, работающие только вместе с этим режимом. У питания — своя"
                + " отдельная копия этой настройки, в группе «Питание».");
        signalSocketWiringCheck.addActionListener(e -> {
            settings.setSignalSocketWiringEnabled(signalSocketWiringCheck.isSelected());
            applySocketDependentEnablement();
        });
        body.add(signalSocketWiringCheck);

        signalConnectorDisplayModeCheck = new JCheckBox(
                "Показывать каждый разъём карты отдельным гнездом (не группой по типу)",
                settings.activeProfile().getSignalConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        signalConnectorDisplayModeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalConnectorDisplayModeCheck.setToolTipText("Выключено — разъёмы одного типа на карте показаны одной"
                + " строкой «N×Тип» (как раньше). Включено — та же группа рисуется N отдельными строками-гнёздами,"
                + " каждое — своя точка подключения, для наглядного расключения многоканального оборудования по"
                + " отдельным линиям. У питания — своя отдельная копия этой настройки (для питания отдельные вводные"
                + " используют редко). Независимо от настройки «линия цепляется за конкретный разъём» выше — та"
                + " решает, ЧТО соединяет линия, эта — КАК разъёмы нарисованы.");
        signalConnectorDisplayModeCheck.addActionListener(e -> settings.setSignalConnectorDisplayMode(
                signalConnectorDisplayModeCheck.isSelected() ? ConnectorDisplayMode.INDIVIDUAL : ConnectorDisplayMode.GROUPED));
        body.add(signalConnectorDisplayModeCheck);

        signalConnectorsVerticalCheck = new JCheckBox(
                "Гнёзда разъёмов у верхнего/нижнего края блока (не у левого/правого)",
                settings.activeProfile().isSignalConnectorsVertical());
        signalConnectorsVerticalCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalConnectorsVerticalCheck.setToolTipText("Выключено — гнёзда идут строками сверху вниз у левого (вход)"
                + " и правого (выход) края блока, как раньше. Включено — гнёзда идут колонками слева направо,"
                + " у верхнего (вход) и нижнего (выход) края блока, подписи разъёмов повёрнуты вертикально."
                + " Отдельная настройка от питания — своя копия в группе «Питание».");
        signalConnectorsVerticalCheck.addActionListener(e ->
                settings.setSignalConnectorsVertical(signalConnectorsVerticalCheck.isSelected()));
        body.add(signalConnectorsVerticalCheck);

        signalChainEndpointSocketsCheck = new JCheckBox(
                "Вводные кабинеты цепочек — тоже гнёзда подключения",
                settings.activeProfile().isSignalChainEndpointSocketsEnabled());
        signalChainEndpointSocketsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalChainEndpointSocketsCheck.setToolTipText("Включено — на миниатюре расключения экрана (см. «Общие» →"
                + " «Узел экрана показывает схему расключения его кабинетов») вводной кабинет основной сигнальной"
                + " цепочки и, если задан резерв, последний кабинет той же цепочки — становятся гнёздами: к ним"
                + " можно подвести линию на общей схеме, как к обычному разъёму. Своя отдельная копия для питания —"
                + " в группе «Питание». Доступно только при включённой настройке «линия цепляется за конкретный"
                + " разъём» выше в этой группе.");
        signalChainEndpointSocketsCheck.addActionListener(e ->
                settings.setSignalChainEndpointSocketsEnabled(signalChainEndpointSocketsCheck.isSelected()));
        body.add(signalChainEndpointSocketsCheck);

        signalSchemaAutoPopulateCheck = new JCheckBox(
                "Автозаполнение: при переходе на общую схему добавлять расключенные экраны"
                        + " и использованные контроллеры",
                settings.activeProfile().isSignalSchemaAutoPopulateEnabled());
        signalSchemaAutoPopulateCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalSchemaAutoPopulateCheck.setToolTipText("Включено — при переключении с «Расключение экрана» на «Общая"
                + " схема» уже расключенные экраны и использованные контроллеры сцены автоматически появляются в"
                + " схеме сигнала, если их там ещё нет (контроллер зеркалит реальную комплектацию карт) — не нужно"
                + " добавлять их вручную по одному. Если ВДОБАВОК включено «Вводные кабинеты цепочек — тоже гнёзда"
                + " подключения» выше в этой группе — гнёзда экранов автоматически соединяются с соответствующими"
                + " портами использованных контроллеров. Уже добавленные вручную узлы и связи не трогает, повторный"
                + " переход дублей не создаёт, а разорванную вручную связь не восстанавливает. Доступно только при"
                + " включённой настройке «линия цепляется за конкретный разъём» выше в этой группе.");
        signalSchemaAutoPopulateCheck.addActionListener(e ->
                settings.setSignalSchemaAutoPopulateEnabled(signalSchemaAutoPopulateCheck.isSelected()));
        body.add(signalSchemaAutoPopulateCheck);

        signalSceneStatsCheck = new JCheckBox("Показывать блок «Статистика сцены» под статистикой экрана",
                settings.activeProfile().isSignalSceneStatsEnabled());
        signalSceneStatsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalSceneStatsCheck.setToolTipText("Суммарные показатели ПО ВСЕЙ сцене — отдельно от статистики активного"
                + " экрана, видна независимо от «Показать все экраны сцены». Своя отдельная копия для питания —"
                + " в группе «Питание».");
        signalSceneStatsCheck.addActionListener(e ->
                settings.setSignalSceneStatsEnabled(signalSceneStatsCheck.isSelected()));
        body.add(signalSceneStatsCheck);

        return (JPanel) UiKit.section("Сигнал", body);
    }

    /** Общая схема ПИТАНИЯ — зеркальная пара группы «Сигнал» выше (те же
     *  переключатели в том же порядке) плюс два чисто силовых понятия, которым
     *  нет аналога у сигнала: контроль нагрузки и единицы измерения мощности. */
    private JPanel buildPowerGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        powerSocketWiringCheck = new JCheckBox(
                "Линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isPowerSocketWiringEnabled());
        powerSocketWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerSocketWiringCheck.setToolTipText("То же самое, но для схемы питания — отдельная настройка"
                + " (см. одноимённый переключатель в группе «Сигнал»). Открывает настройки ниже в этой же группе,"
                + " работающие только вместе с этим режимом.");
        powerSocketWiringCheck.addActionListener(e -> {
            settings.setPowerSocketWiringEnabled(powerSocketWiringCheck.isSelected());
            applySocketDependentEnablement();
        });
        body.add(powerSocketWiringCheck);

        powerConnectorDisplayModeCheck = new JCheckBox(
                "Показывать каждый разъём щита отдельным гнездом (не группой по типу)",
                settings.activeProfile().getPowerConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        powerConnectorDisplayModeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerConnectorDisplayModeCheck.setToolTipText("То же самое, но для схемы питания — отдельная настройка"
                + " (см. группу «Сигнал»), т.к. для питания отдельные вводные используют редко (обычно хватает"
                + " группы «N×разъём»).");
        powerConnectorDisplayModeCheck.addActionListener(e -> settings.setPowerConnectorDisplayMode(
                powerConnectorDisplayModeCheck.isSelected() ? ConnectorDisplayMode.INDIVIDUAL : ConnectorDisplayMode.GROUPED));
        body.add(powerConnectorDisplayModeCheck);

        powerConnectorsVerticalCheck = new JCheckBox(
                "Гнёзда разъёмов у верхнего/нижнего края блока (не у левого/правого)",
                settings.activeProfile().isPowerConnectorsVertical());
        powerConnectorsVerticalCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerConnectorsVerticalCheck.setToolTipText("То же самое, но для схемы питания — отдельная настройка"
                + " (см. группу «Сигнал»).");
        powerConnectorsVerticalCheck.addActionListener(e ->
                settings.setPowerConnectorsVertical(powerConnectorsVerticalCheck.isSelected()));
        body.add(powerConnectorsVerticalCheck);

        powerChainEndpointSocketsCheck = new JCheckBox(
                "Вводные кабинеты цепочек — тоже гнёзда подключения",
                settings.activeProfile().isPowerChainEndpointSocketsEnabled());
        powerChainEndpointSocketsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerChainEndpointSocketsCheck.setToolTipText("То же самое, но для схемы питания — отдельная настройка"
                + " (см. группу «Сигнал»): вводной кабинет каждой силовой цепочки становится гнездом. Доступно"
                + " только при включённой настройке «линия цепляется за конкретный разъём» выше в этой группе.");
        powerChainEndpointSocketsCheck.addActionListener(e ->
                settings.setPowerChainEndpointSocketsEnabled(powerChainEndpointSocketsCheck.isSelected()));
        body.add(powerChainEndpointSocketsCheck);

        powerSchemaAutoPopulateCheck = new JCheckBox(
                "Автозаполнение: при переходе на общую схему добавлять расключенные экраны"
                        + " и заполнять «проходные»",
                settings.activeProfile().isPowerSchemaAutoPopulateEnabled());
        powerSchemaAutoPopulateCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerSchemaAutoPopulateCheck.setToolTipText("Включено — при переключении с «Расключение экрана» на «Общая"
                + " схема» уже расключенные экраны автоматически появляются в схеме питания, если их там ещё нет."
                + " У питания нет понятия контроллера — вместо этого, если ВДОБАВОК включено «Вводные кабинеты"
                + " цепочек — тоже гнёзда подключения» выше в этой группе, вводные кабинеты распределяются по"
                + " СВОБОДНЫМ разъёмам уже добавленных на схему узлов типа «Распределение» (щиты/проходные) —"
                + " только разъёмы ОСНОВНОГО (самого ёмкого) типа узла, разъёмы другого номинала/типа на том же"
                + " узле пропускаются — максимально заполняя каждый по очереди, прежде чем переходить к следующему."
                + " Новые такие узлы не создаются, их нужно разместить на схеме заранее. Уже добавленные вручную"
                + " узлы и связи не трогает, повторный переход дублей не создаёт, а разорванную вручную связь не"
                + " восстанавливает. Доступно только при включённой настройке «линия цепляется за конкретный"
                + " разъём» выше в этой группе.");
        powerSchemaAutoPopulateCheck.addActionListener(e ->
                settings.setPowerSchemaAutoPopulateEnabled(powerSchemaAutoPopulateCheck.isSelected()));
        body.add(powerSchemaAutoPopulateCheck);

        powerSceneStatsCheck = new JCheckBox("Показывать блок «Статистика сцены» под статистикой экрана",
                settings.activeProfile().isPowerSceneStatsEnabled());
        powerSceneStatsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerSceneStatsCheck.setToolTipText("Суммарные кабинеты/мощность/вес и разбивка по фазам ПО ВСЕЙ сцене —"
                + " отдельно от статистики активного экрана, видна независимо от «Показать все экраны сцены»."
                + " Своя отдельная копия для сигнала — в группе «Сигнал».");
        powerSceneStatsCheck.addActionListener(e ->
                settings.setPowerSceneStatsEnabled(powerSceneStatsCheck.isSelected()));
        body.add(powerSceneStatsCheck);

        loadTrackingCheck = new JCheckBox("Контроль электрической нагрузки"
                + " (предупреждения о перегрузке цепочек/щитов)",
                settings.activeProfile().isLoadTrackingEnabled());
        loadTrackingCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadTrackingCheck.setToolTipText("Сравнивает нагрузку каждой силовой цепочки/щита с ёмкостью его разъёма"
                + " и подсвечивает превышение. Выключите для нестандартного случая, который расчёт не покрывает —"
                + " дальше считайте нагрузку самостоятельно (см. Руководство).");
        loadTrackingCheck.addActionListener(e ->
                settings.setLoadTrackingEnabled(loadTrackingCheck.isSelected()));
        body.add(loadTrackingCheck);

        powerUnitKwCheck = new JCheckBox("Показывать мощность/нагрузку в киловаттах (кВт), а не ваттах (Вт)",
                settings.activeProfile().isPowerUnitKw());
        powerUnitKwCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerUnitKwCheck.setToolTipText("Меняет единицы отображения мощности везде в приложении (карточки узлов,"
                + " статистика этапов, экспортные документы) — на внутренний расчёт нагрузки не влияет.");
        powerUnitKwCheck.addActionListener(e ->
                settings.setPowerUnitKw(powerUnitKwCheck.isSelected()));
        body.add(powerUnitKwCheck);

        return (JPanel) UiKit.section("Питание", body);
    }

    private JPanel buildMaskGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
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
        body.add(logoRow);

        return (JPanel) UiKit.section("Генерация масок", body);
    }

    /** "Мост синхронизации" — на сетях, где прямое подключение к серверу по
     *  {@code IP:8443} блокируется (см. раздел "Мост синхронизации" на публичной
     *  веб-странице сервера), пользователь вписывает сюда альтернативный адрес
     *  вручную; пусто -- используется адрес по умолчанию, как раньше. Коммитится
     *  по потере фокуса/Enter, не по каждому нажатию клавиши -- это URL, не текст
     *  для реактивного предпросмотра. */
    private JPanel buildSyncGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setToolTipText("Альтернативный адрес сервера — используйте, если обычное подключение к"
                + " https://138.16.177.176:8443 не проходит (сеть блокирует нестандартный порт, см."
                + " раздел «Мост синхронизации» на сайте сервера). Пусто — адрес по умолчанию.");
        row.add(new JLabel("Адрес сервера (переопределение):"));
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
        row.add(syncServerUrlField);
        body.add(row);

        return (JPanel) UiKit.section("Синхронизация", body);
    }

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
        shapeEditorFloatingCheck.setSelected(settings.activeProfile().isShapeEditorFloating());
        snapThresholdSpinner.setValue(settings.activeProfile().getSnapThresholdPx());
        snapStrengthSpinner.setValue(settings.activeProfile().getSnapStrengthPercent());
        foolProofWiringCheck.setSelected(settings.activeProfile().isFoolProofWiringEnabled());
        schemaScreensAsWiringCheck.setSelected(settings.activeProfile().isSchemaScreensAsWiringDiagram());
        signalSocketWiringCheck.setSelected(settings.activeProfile().isSignalSocketWiringEnabled());
        powerSocketWiringCheck.setSelected(settings.activeProfile().isPowerSocketWiringEnabled());
        signalChainEndpointSocketsCheck.setSelected(settings.activeProfile().isSignalChainEndpointSocketsEnabled());
        powerChainEndpointSocketsCheck.setSelected(settings.activeProfile().isPowerChainEndpointSocketsEnabled());
        signalSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isSignalSchemaAutoPopulateEnabled());
        powerSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isPowerSchemaAutoPopulateEnabled());
        applySocketDependentEnablement();
        signalConnectorDisplayModeCheck.setSelected(
                settings.activeProfile().getSignalConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        powerConnectorDisplayModeCheck.setSelected(
                settings.activeProfile().getPowerConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        signalConnectorsVerticalCheck.setSelected(settings.activeProfile().isSignalConnectorsVertical());
        powerConnectorsVerticalCheck.setSelected(settings.activeProfile().isPowerConnectorsVertical());
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
