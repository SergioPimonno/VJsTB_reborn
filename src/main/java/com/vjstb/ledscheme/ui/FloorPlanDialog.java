package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.FloorCalc;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

/**
 * Немодальное окно с 2D-планом напольного каркаса ({@link FloorPlanPanel}) — открывается
 * кнопкой «Показать план» из сводки «Рассчитать пол» ({@code ui.stage.SetupStagePanel}).
 * Отдельное окно, а не встроенная панель — тот же довод, что у {@link Structure3DDialog}:
 * в узкой карточке инспектора «Прерига сцены» план крупного пола не читается.
 *
 * <p>Текст сводки ({@link #summaryText}) — статический чистый метод: его же показывает
 * окно «Рассчитать пол», и его можно проверить в тесте без создания окон (в headless-среде
 * {@code JDialog} создать нельзя).
 */
public class FloorPlanDialog extends JDialog {

    public FloorPlanDialog(Window owner, Screen screen, CabinetType type, FloorCalc.Result result) {
        super(owner, "План напольного каркаса — " + screen.getName(), ModalityType.MODELESS);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new FloorPlanPanel(screen, type, result), BorderLayout.CENTER);
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(960, 640);
        setLocationRelativeTo(owner);
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
