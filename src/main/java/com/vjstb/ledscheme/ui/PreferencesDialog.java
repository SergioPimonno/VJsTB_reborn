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
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Диалог «Предпочтения»: поведенческие переключатели интерфейса, не связанные
 * с цветом или горячими клавишами (см. {@link PersonalizationDialog} — цвета/
 * профили, {@link HotkeysDialog} — горячие клавиши) — вынесены в отдельное
 * окошко, чтобы каждый раздел персонализации открывался и настраивался
 * независимо от остальных. Переключатели сгруппированы по темам (холст, общая
 * схема — соединения, общая схема — узлы/автозаполнение, нагрузка, маски) —
 * плоский список из десятка не связанных на вид галочек плохо читается.
 */
public class PreferencesDialog extends JDialog {

    private final SettingsManager settings;
    private JCheckBox previewWidgetCheck;
    private JCheckBox canvasSnapToCenterCheck;
    private JSpinner snapThresholdSpinner;
    private JSpinner snapStrengthSpinner;
    private JCheckBox socketWiringCheck;
    private JCheckBox chainEndpointSocketsCheck;
    private JCheckBox signalSchemaAutoPopulateCheck;
    private JCheckBox powerSchemaAutoPopulateCheck;
    private JCheckBox foolProofWiringCheck;
    private JCheckBox schemaScreensAsWiringCheck;
    private JCheckBox connectorDisplayModeCheck;
    private JCheckBox connectorsVerticalCheck;
    private JCheckBox loadTrackingCheck;
    private JLabel maskLogoPathLabel;

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
        content.add(buildCanvasGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildSchemaConnectionsGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildSchemaNodesGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildLoadGroup());
        content.add(Box.createVerticalStrut(8));
        content.add(buildMaskGroup());
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

    private JPanel buildCanvasGroup() {
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

        return (JPanel) UiKit.section("Холст и сцена", body);
    }

    private JPanel buildSchemaConnectionsGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        socketWiringCheck = new JCheckBox("Линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isSocketWiringEnabled());
        socketWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        socketWiringCheck.setToolTipText("Включено — конец линии привязывается к нужному разъёму/гнезду карты,"
                + " с проверкой числа свободных линий на нём. Выключено — линия просто соединяет два блока"
                + " оборудования целиком, разъёмы в блоках — только справочная информация о комплектации. Открывает"
                + " настройки ниже, работающие только вместе с этим режимом.");
        socketWiringCheck.addActionListener(e -> {
            settings.setSocketWiringEnabled(socketWiringCheck.isSelected());
            applySocketDependentEnablement();
        });
        body.add(socketWiringCheck);

        foolProofWiringCheck = new JCheckBox("«Защита от дурака» (нельзя соединять вход со входом и выход с выходом)",
                settings.activeProfile().isFoolProofWiringEnabled());
        foolProofWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        foolProofWiringCheck.setToolTipText("Блокирует попытку провести линию между двумя входами или двумя"
                + " выходами — частая случайная ошибка при рисовании схемы.");
        foolProofWiringCheck.addActionListener(e ->
                settings.setFoolProofWiringEnabled(foolProofWiringCheck.isSelected()));
        body.add(foolProofWiringCheck);

        connectorDisplayModeCheck = new JCheckBox(
                "Показывать каждый разъём карты отдельным гнездом (не группой по типу)",
                settings.activeProfile().getConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        connectorDisplayModeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectorDisplayModeCheck.setToolTipText("Выключено — разъёмы одного типа на карте показаны одной строкой"
                + " «N×Тип» (как раньше). Включено — та же группа рисуется N отдельными строками-гнёздами, каждое —"
                + " своя точка подключения, для наглядного расключения многоканального оборудования по отдельным"
                + " линиям. Независимо от настройки «линия цепляется за конкретный разъём» выше — та решает, ЧТО"
                + " соединяет линия, эта — КАК разъёмы нарисованы.");
        connectorDisplayModeCheck.addActionListener(e -> settings.setConnectorDisplayMode(
                connectorDisplayModeCheck.isSelected() ? ConnectorDisplayMode.INDIVIDUAL : ConnectorDisplayMode.GROUPED));
        body.add(connectorDisplayModeCheck);

        connectorsVerticalCheck = new JCheckBox(
                "Гнёзда разъёмов у верхнего/нижнего края блока (не у левого/правого)",
                settings.activeProfile().isConnectorsVertical());
        connectorsVerticalCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectorsVerticalCheck.setToolTipText("Выключено — гнёзда идут строками сверху вниз у левого (вход)"
                + " и правого (выход) края блока, как раньше. Включено — гнёзда идут колонками слева направо,"
                + " у верхнего (вход) и нижнего (выход) края блока, подписи разъёмов повёрнуты вертикально.");
        connectorsVerticalCheck.addActionListener(e ->
                settings.setConnectorsVertical(connectorsVerticalCheck.isSelected()));
        body.add(connectorsVerticalCheck);

        return (JPanel) UiKit.section("Общая схема — соединения", body);
    }

    private JPanel buildSchemaNodesGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        schemaScreensAsWiringCheck = new JCheckBox("Узел экрана показывает схему расключения его кабинетов",
                settings.activeProfile().isSchemaScreensAsWiringDiagram());
        schemaScreensAsWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaScreensAsWiringCheck.setToolTipText("Включено — узел экрана рисует уменьшенную схему коммутации его"
                + " кабинетов (как в Питании/Сигнале). Выключено — узел экрана выглядит как обычный прямоугольный"
                + " блок с названием, без деталей расключения.");
        schemaScreensAsWiringCheck.addActionListener(e ->
                settings.setSchemaScreensAsWiringDiagram(schemaScreensAsWiringCheck.isSelected()));
        body.add(schemaScreensAsWiringCheck);

        chainEndpointSocketsCheck = new JCheckBox(
                "Вводные кабинеты цепочек — тоже гнёзда подключения",
                settings.activeProfile().isChainEndpointSocketsEnabled());
        chainEndpointSocketsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        chainEndpointSocketsCheck.setToolTipText("Включено — на миниатюре расключения экрана (см. настройку выше)"
                + " вводной кабинет каждой силовой цепочки, а для сигнала — вводной кабинет основной цепочки и,"
                + " если задан резерв, последний кабинет той же цепочки — становятся гнёздами: к ним можно"
                + " подвести линию на общей схеме, как к обычному разъёму. Доступно только при включённой"
                + " настройке «линия цепляется за конкретный разъём» в группе «Соединения» выше.");
        chainEndpointSocketsCheck.addActionListener(e ->
                settings.setChainEndpointSocketsEnabled(chainEndpointSocketsCheck.isSelected()));
        body.add(chainEndpointSocketsCheck);

        signalSchemaAutoPopulateCheck = new JCheckBox(
                "Автозаполнение сигнала: при переходе на общую схему добавлять расключенные экраны"
                        + " и использованные контроллеры",
                settings.activeProfile().isSignalSchemaAutoPopulateEnabled());
        signalSchemaAutoPopulateCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        signalSchemaAutoPopulateCheck.setToolTipText("Включено — при переключении с «Расключение экрана» на «Общая"
                + " схема» уже расключенные экраны и использованные контроллеры сцены автоматически появляются в"
                + " схеме сигнала, если их там ещё нет (контроллер зеркалит реальную комплектацию карт) — не нужно"
                + " добавлять их вручную по одному. Если ВДОБАВОК включено «вводные кабинеты цепочек — тоже гнёзда"
                + " подключения» выше — гнёзда экранов автоматически соединяются с соответствующими портами"
                + " использованных контроллеров. Уже добавленные вручную узлы и связи не трогает, повторный переход"
                + " дублей не создаёт. Доступно только при включённой настройке «линия цепляется за конкретный"
                + " разъём» в группе «Соединения» выше.");
        signalSchemaAutoPopulateCheck.addActionListener(e ->
                settings.setSignalSchemaAutoPopulateEnabled(signalSchemaAutoPopulateCheck.isSelected()));
        body.add(signalSchemaAutoPopulateCheck);

        powerSchemaAutoPopulateCheck = new JCheckBox(
                "Автозаполнение питания: при переходе на общую схему добавлять расключенные экраны"
                        + " и заполнять «проходные»",
                settings.activeProfile().isPowerSchemaAutoPopulateEnabled());
        powerSchemaAutoPopulateCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        powerSchemaAutoPopulateCheck.setToolTipText("Включено — при переключении с «Расключение экрана» на «Общая"
                + " схема» уже расключенные экраны автоматически появляются в схеме питания, если их там ещё нет."
                + " У питания нет понятия контроллера — вместо этого, если ВДОБАВОК включено «вводные кабинеты"
                + " цепочек — тоже гнёзда подключения» выше, вводные кабинеты распределяются по СВОБОДНЫМ разъёмам"
                + " уже добавленных на схему узлов типа «Распределение» (щиты/проходные), максимально заполняя"
                + " каждый по очереди, прежде чем переходить к следующему — новые такие узлы не создаются, их нужно"
                + " разместить на схеме заранее. Уже добавленные вручную узлы и связи не трогает, повторный переход"
                + " дублей не создаёт. Доступно только при включённой настройке «линия цепляется за конкретный"
                + " разъём» в группе «Соединения» выше.");
        powerSchemaAutoPopulateCheck.addActionListener(e ->
                settings.setPowerSchemaAutoPopulateEnabled(powerSchemaAutoPopulateCheck.isSelected()));
        body.add(powerSchemaAutoPopulateCheck);

        return (JPanel) UiKit.section("Общая схема — узлы и автозаполнение", body);
    }

    private JPanel buildLoadGroup() {
        JPanel body = UiKit.vbox();
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

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

        return (JPanel) UiKit.section("Общая схема — нагрузка", body);
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

    /** chainEndpointSocketsCheck/signalSchemaAutoPopulateCheck/powerSchemaAutoPopulateCheck
     *  работают только вместе с socketWiringCheck (без него общая схема не различает
     *  конкретные гнёзда/порты вообще) — недоступны для включения, пока он выключен,
     *  чтобы не создавать видимость рабочей настройки там, где она молча ничего не
     *  даёт. Уже включённое состояние при выключении мастер-переключателя не
     *  сбрасывается автоматически — только становится недоступным для изменения,
     *  пока мастер снова не включат. */
    private void applySocketDependentEnablement() {
        boolean enabled = socketWiringCheck.isSelected();
        chainEndpointSocketsCheck.setEnabled(enabled);
        signalSchemaAutoPopulateCheck.setEnabled(enabled);
        powerSchemaAutoPopulateCheck.setEnabled(enabled);
    }

    private void refresh() {
        previewWidgetCheck.setSelected(settings.activeProfile().isPreviewWidgetEnabled());
        canvasSnapToCenterCheck.setSelected(settings.activeProfile().isCanvasSnapToCenter());
        snapThresholdSpinner.setValue(settings.activeProfile().getSnapThresholdPx());
        snapStrengthSpinner.setValue(settings.activeProfile().getSnapStrengthPercent());
        socketWiringCheck.setSelected(settings.activeProfile().isSocketWiringEnabled());
        foolProofWiringCheck.setSelected(settings.activeProfile().isFoolProofWiringEnabled());
        schemaScreensAsWiringCheck.setSelected(settings.activeProfile().isSchemaScreensAsWiringDiagram());
        chainEndpointSocketsCheck.setSelected(settings.activeProfile().isChainEndpointSocketsEnabled());
        signalSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isSignalSchemaAutoPopulateEnabled());
        powerSchemaAutoPopulateCheck.setSelected(settings.activeProfile().isPowerSchemaAutoPopulateEnabled());
        applySocketDependentEnablement();
        connectorDisplayModeCheck.setSelected(
                settings.activeProfile().getConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        connectorsVerticalCheck.setSelected(settings.activeProfile().isConnectorsVertical());
        loadTrackingCheck.setSelected(settings.activeProfile().isLoadTrackingEnabled());
        String logoPath = settings.activeProfile().getMaskLogoImagePath();
        maskLogoPathLabel.setText(logoPath != null ? new File(logoPath).getName() : "не задан");
    }
}
