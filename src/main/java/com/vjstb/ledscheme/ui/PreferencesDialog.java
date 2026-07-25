package com.vjstb.ledscheme.ui;

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

        previewWidgetCheck = new JCheckBox("Мини-превью сцены в углу холста (Питание/Сигнал)",
                settings.activeProfile().isPreviewWidgetEnabled());
        previewWidgetCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewWidgetCheck.addActionListener(e -> settings.setPreviewWidgetEnabled(previewWidgetCheck.isSelected()));
        body.add(previewWidgetCheck);

        canvasSnapToCenterCheck = new JCheckBox("Shift-перетаскивание в канвасе: прилипание к центру канваса",
                settings.activeProfile().isCanvasSnapToCenter());
        canvasSnapToCenterCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        canvasSnapToCenterCheck.addActionListener(e ->
                settings.setCanvasSnapToCenter(canvasSnapToCenterCheck.isSelected()));
        body.add(canvasSnapToCenterCheck);

        socketWiringCheck = new JCheckBox("Общая схема: коммутация через гнёзда разъёмов (а не узел целиком)",
                settings.activeProfile().isSocketWiringEnabled());
        socketWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        socketWiringCheck.addActionListener(e -> settings.setSocketWiringEnabled(socketWiringCheck.isSelected()));
        body.add(socketWiringCheck);

        foolProofWiringCheck = new JCheckBox("Общая схема: защита от дурака (нельзя соединять вход со входом и выход с выходом)",
                settings.activeProfile().isFoolProofWiringEnabled());
        foolProofWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        foolProofWiringCheck.addActionListener(e ->
                settings.setFoolProofWiringEnabled(foolProofWiringCheck.isSelected()));
        body.add(foolProofWiringCheck);

        schemaScreensAsWiringCheck = new JCheckBox("Общая схема: узел экрана — схема расключения (а не просто блок)",
                settings.activeProfile().isSchemaScreensAsWiringDiagram());
        schemaScreensAsWiringCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemaScreensAsWiringCheck.addActionListener(e ->
                settings.setSchemaScreensAsWiringDiagram(schemaScreensAsWiringCheck.isSelected()));
        body.add(schemaScreensAsWiringCheck);

        loadTrackingCheck = new JCheckBox("Общая схема: контроль электрической нагрузки"
                + " (предупреждения о перегрузке цепочек/щитов)",
                settings.activeProfile().isLoadTrackingEnabled());
        loadTrackingCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadTrackingCheck.setToolTipText("Выключите для нестандартного случая, который расчёт не покрывает —"
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
        loadTrackingCheck.setSelected(settings.activeProfile().isLoadTrackingEnabled());
    }
}
