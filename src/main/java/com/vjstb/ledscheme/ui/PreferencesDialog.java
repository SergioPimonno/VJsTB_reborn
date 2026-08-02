package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.ConnectorDisplayMode;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;

/**
 * Диалог «Предпочтения»: поведенческие переключатели интерфейса, не связанные
 * с цветом или горячими клавишами (см. {@link PersonalizationDialog} — цвета/
 * профили, {@link HotkeysDialog} — горячие клавиши) — вынесены в отдельное
 * окошко, чтобы каждый раздел персонализации открывался и настраивался
 * независимо от остальных.
 */
public class PreferencesDialog extends JDialog {

    private final SettingsManager settings;
    private JCheckBox previewWidgetCheck;
    private JCheckBox canvasSnapToCenterCheck;
    private JCheckBox socketWiringCheck;
    private JCheckBox foolProofWiringCheck;
    private JCheckBox schemaScreensAsWiringCheck;
    private JCheckBox connectorDisplayModeCheck;
    private JCheckBox connectorsVerticalCheck;
    private JCheckBox loadTrackingCheck;

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
        content.add(buildBehaviorPanel());
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

    private JPanel buildBehaviorPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
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

        socketWiringCheck = new JCheckBox("Общая схема: линия связи цепляется за конкретный разъём, а не за блок целиком",
                settings.activeProfile().isSocketWiringEnabled());
        socketWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        socketWiringCheck.setToolTipText("Включено — конец линии привязывается к нужному разъёму/гнезду карты,"
                + " с проверкой числа свободных линий на нём. Выключено — линия просто соединяет два блока"
                + " оборудования целиком, разъёмы в блоках — только справочная информация о комплектации.");
        socketWiringCheck.addActionListener(e -> settings.setSocketWiringEnabled(socketWiringCheck.isSelected()));
        body.add(socketWiringCheck);

        foolProofWiringCheck = new JCheckBox("Общая схема: защита от дурака (нельзя соединять вход со входом и выход с выходом)",
                settings.activeProfile().isFoolProofWiringEnabled());
        foolProofWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        foolProofWiringCheck.setToolTipText("Блокирует попытку провести линию между двумя входами или двумя"
                + " выходами — частая случайная ошибка при рисовании схемы.");
        foolProofWiringCheck.addActionListener(e ->
                settings.setFoolProofWiringEnabled(foolProofWiringCheck.isSelected()));
        body.add(foolProofWiringCheck);

        schemaScreensAsWiringCheck = new JCheckBox("Общая схема: узел экрана показывает схему расключения его кабинетов",
                settings.activeProfile().isSchemaScreensAsWiringDiagram());
        schemaScreensAsWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaScreensAsWiringCheck.setToolTipText("Включено — узел экрана рисует уменьшенную схему коммутации его"
                + " кабинетов (как в Питании/Сигнале). Выключено — узел экрана выглядит как обычный прямоугольный"
                + " блок с названием, без деталей расключения.");
        schemaScreensAsWiringCheck.addActionListener(e ->
                settings.setSchemaScreensAsWiringDiagram(schemaScreensAsWiringCheck.isSelected()));
        body.add(schemaScreensAsWiringCheck);

        connectorDisplayModeCheck = new JCheckBox(
                "Общая схема: показывать каждый разъём карты отдельным гнездом (не группой по типу)",
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
                "Общая схема: гнёзда разъёмов у верхнего/нижнего края блока (не у левого/правого)",
                settings.activeProfile().isConnectorsVertical());
        connectorsVerticalCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectorsVerticalCheck.setToolTipText("Выключено — гнёзда идут строками сверху вниз у левого (вход)"
                + " и правого (выход) края блока, как раньше. Включено — гнёзда идут колонками слева направо,"
                + " у верхнего (вход) и нижнего (выход) края блока, подписи разъёмов повёрнуты вертикально.");
        connectorsVerticalCheck.addActionListener(e ->
                settings.setConnectorsVertical(connectorsVerticalCheck.isSelected()));
        body.add(connectorsVerticalCheck);

        loadTrackingCheck = new JCheckBox("Общая схема: контроль электрической нагрузки"
                + " (предупреждения о перегрузке цепочек/щитов)",
                settings.activeProfile().isLoadTrackingEnabled());
        loadTrackingCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadTrackingCheck.setToolTipText("Сравнивает нагрузку каждой силовой цепочки/щита с ёмкостью его разъёма"
                + " и подсвечивает превышение. Выключите для нестандартного случая, который расчёт не покрывает —"
                + " дальше считайте нагрузку самостоятельно (см. Руководство).");
        loadTrackingCheck.addActionListener(e ->
                settings.setLoadTrackingEnabled(loadTrackingCheck.isSelected()));
        body.add(loadTrackingCheck);

        return (JPanel) UiKit.section("Поведение", body);
    }

    private void refresh() {
        previewWidgetCheck.setSelected(settings.activeProfile().isPreviewWidgetEnabled());
        canvasSnapToCenterCheck.setSelected(settings.activeProfile().isCanvasSnapToCenter());
        socketWiringCheck.setSelected(settings.activeProfile().isSocketWiringEnabled());
        foolProofWiringCheck.setSelected(settings.activeProfile().isFoolProofWiringEnabled());
        schemaScreensAsWiringCheck.setSelected(settings.activeProfile().isSchemaScreensAsWiringDiagram());
        connectorDisplayModeCheck.setSelected(
                settings.activeProfile().getConnectorDisplayMode() == ConnectorDisplayMode.INDIVIDUAL);
        connectorsVerticalCheck.setSelected(settings.activeProfile().isConnectorsVertical());
        loadTrackingCheck.setSelected(settings.activeProfile().isLoadTrackingEnabled());
    }
}
