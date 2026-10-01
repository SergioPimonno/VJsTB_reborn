package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.FloorCalc;
import com.vjstb.ledscheme.settings.HotkeyAction;
import com.vjstb.ledscheme.settings.KeyCombo;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Немодальное окно плана напольного каркаса: 2D-план ({@link FloorPlanPanel}) или, с
 * 2026-10-01, 3D-редактор ({@link FloorPlan3DPanel}) — переключатель в {@link
 * FloorPlanViewPanel}. Открывается
 * кнопкой «Показать план» из сводки «Рассчитать пол» ({@code ui.stage.SetupStagePanel}).
 * Отдельное окно, а не встроенная панель — тот же довод, что у {@link Structure3DDialog}:
 * в узкой карточке инспектора «Прерига сцены» план крупного пола не читается.
 *
 * <p>Текст сводки ({@link #summaryText}) — статический чистый метод: его же показывает
 * окно «Рассчитать пол», и его можно проверить в тесте без создания окон (в headless-среде
 * {@code JDialog} создать нельзя).
 */
public class FloorPlanDialog extends JDialog {

    private final Screen screen;
    private final FloorPlanViewPanel view;
    private final AppModel.Listener modelListener;

    /**
     * С 2026-10-01 (3D-редактор пола) окно содержит {@link FloorPlanViewPanel} — переключатель
     * «2D схема» / «3D редактор» (выбор запоминается в профиле). Окно подписано на модель
     * (отписка при закрытии — {@link AppModel#removeListener}), поэтому правки рам в 3D, Ctrl+Z
     * и «Рассчитать пол» сразу пересчитывают 2D-план, 3D-вид и итоги. Ctrl+Z (по привязке
     * пользователя) работает и когда фокус в этом окне — общий диспетчер горячих клавиш {@code
     * MainFrame} срабатывает только при активном главном окне.
     */
    public FloorPlanDialog(Window owner, AppModel model, Screen screen, SettingsManager settings) {
        super(owner, "План напольного каркаса — " + screen.getName(), ModalityType.MODELESS);
        this.screen = screen;
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        view = new FloorPlanViewPanel(model, screen, settings, true);
        content.add(view, BorderLayout.CENTER);
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1040, 720);
        setLocationRelativeTo(owner);

        modelListener = view::refresh;
        model.addListener(modelListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                model.removeListener(modelListener);
            }
        });
        KeyCombo undo = settings != null ? settings.bindingFor(HotkeyAction.UNDO)
                : KeyCombo.ofKey(KeyEvent.VK_Z, true, false, false);
        if (undo != null && undo.getKeyCode() != null) {
            int mods = (undo.isCtrl() ? InputEvent.CTRL_DOWN_MASK : 0)
                    | (undo.isShift() ? InputEvent.SHIFT_DOWN_MASK : 0)
                    | (undo.isAlt() ? InputEvent.ALT_DOWN_MASK : 0);
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(undo.getKeyCode(), mods), "floorUndo");
            getRootPane().getActionMap().put("floorUndo", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    model.undo();
                }
            });
        }
    }

    public Screen getScreen() {
        return screen;
    }

    /** Пересчитать вид (вызывающий код — после «Рассчитать пол», если окно уже открыто). */
    public void refresh() {
        view.refresh();
    }

    /** Сводка расчёта пола для окна «Рассчитать пол». Стыки здесь показываются (это
     *  вспомогательная информация калькулятора), а в XLSX-спецификацию НЕ идут —
     *  правка пользователя 2026-10-01: в спецификации только позиции (рамы, стаканы,
     *  болты, ножки, зубы). */
    public static String summaryText(String screenName, FloorCalc.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Напольный каркас — экран «").append(screenName).append("»\n\n");
        sb.append(String.format("Рам: %d%s%n", r.frameCount(),
                r.frameTypeName() != null ? " («" + r.frameTypeName() + "»)" : ""));
        sb.append(String.format("Шаг рам: %.0f × %.0f мм — %d×%d кабинета на раму (зазор стакана %.0f мм)%n",
                r.framePitchXMm(), r.framePitchYMm(), r.cabinetsPerFrameX(), r.cabinetsPerFrameY(),
                r.cupGapMm()));
        sb.append(String.format("Стыков по короткой стороне (со стаканами): %d%n", r.shortSideJointCount()));
        sb.append(String.format("Стыков по длинной стороне (вплотную): %d%n", r.longSideJointCount()));
        sb.append(String.format("Стаканов: %d%s%n", r.cupCount(),
                r.cupTypeName() != null ? " («" + r.cupTypeName() + "»)" : ""));
        sb.append(String.format("Болтов: %d (2 на каждый стык)%n", r.boltCount()));
        sb.append(String.format("Ножек: %d (4 на раму)%n", r.legCount()));
        sb.append(String.format("Зубов: %d (%d на кабинет × %d опёртых кабинетов)%n", r.toothCount(),
                r.teethPerCabinet(), r.supportedCabinetCount()));
        sb.append(String.format("Кабинетов без опоры: %d%n", r.unsupportedCabinetCount()));
        sb.append(String.format("Средняя нагрузка: %s кг/м² (кабинеты %s кг + рамы %s кг на %s м²)%n",
                UiKit.fmt(Math.round(r.averageLoadKgPerM2() * 10) / 10.0),
                UiKit.fmt(Math.round(r.cabinetWeightKg() * 10) / 10.0),
                UiKit.fmt(Math.round(r.frameWeightKg() * 10) / 10.0),
                UiKit.fmt(Math.round(r.areaM2() * 100) / 100.0)));
        if (!r.warnings().isEmpty()) {
            sb.append("\nПредупреждения:\n");
            for (String w : r.warnings()) {
                sb.append("· ").append(w).append('\n');
            }
        }
        sb.append("\nВысота ножек, вес стаканов/болтов/ножек/зубов в расчёте не учитываются.");
        return sb.toString();
    }
}
