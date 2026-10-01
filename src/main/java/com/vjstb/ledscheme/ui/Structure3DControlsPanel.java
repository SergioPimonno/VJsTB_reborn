package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCurveMath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;

/**
 * Ползунки формы экрана, радиуса, зазора и числа башен в окне 3D-редактора конструктива
 * ({@link Structure3DDialog}) — запрос пользователя 2026-10-01: «давай параметры радиуса, зазора
 * и количества башен продублируем в 3д редакторе и сделаем их как в виджете ползунками, это очень
 * удобно». Те же значения, что поля блока «Наземный конструктив» в «Сетапе»: оба места показывают
 * одно состояние МОДЕЛИ (диалог подписан на {@link AppModel#addListener} и зовёт {@link
 * #syncFromModel}; «Сетап» перестраивает свои поля по тому же оповещению).
 *
 * <p>Изменение ползунка пересчитывает конструктив тем же путём, что «Предварительный расчёт»
 * ({@link StructureSliderController}) и сразу перерисовывает 3D. Во время перетаскивания события
 * ползунка КОАЛЕСЦИРУЮТСЯ таймером ({@link #LIVE_DELAY_MS}: пересчитывается только последнее
 * значение за интервал), отпускание пересчитывает сразу и завершает жест — одна запись отмены на
 * жест. Подписи — живые (значение и производные: угол между кабинетами, угол дуги), под
 * ползунками — строка предупреждений расчёта (коллизии оснований, минимальный зазор, край
 * экрана). Радиус неактивен у прямого экрана, число башен — пока башни не раздельные (стена).
 *
 * <p>Обычные Swing-компоненты — создаются и в headless-среде (тест {@code
 * Structure3DControlsPanelTest}); с 3D-видом не пересекаются: клики по рамам и орбита камеры —
 * на GL-панели, ползунки — отдельной строкой над ней.
 */
public class Structure3DControlsPanel extends JPanel {

    /** Интервал коалесцирования событий перетаскивания, мс. */
    static final int LIVE_DELAY_MS = 70;
    /** Ширина строки предупреждений в CSS-px (HTML переносит длинный текст расчёта по словам;
     *  Swing масштабирует CSS-px ~×1,3 — 680 даёт ~890 экранных px при окне 980). */
    private static final int STATUS_WIDTH_PX = 680;

    private final AppModel model;
    private final StructureSliderController controller;
    private final JComboBox<ScreenCurveType> shapeCombo = new JComboBox<>(ScreenCurveType.values());
    private final JSlider radiusSlider = new JSlider(0, StructureSliderMath.radiusTickCount(), 15);
    private final JSlider gapSlider = new JSlider(0, StructureSliderMath.gapTickCount(), 0);
    private final JSlider towersSlider = new JSlider(0, StructureSliderMath.TOWER_COUNT_MIN_MAX, 0);
    private final JLabel radiusLabel = new JLabel(" ");
    private final JLabel gapLabel = new JLabel(" ");
    private final JLabel towersLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final Timer liveTimer;
    /** Идёт заполнение из модели — события компонентов не должны пересчитывать конструктив. */
    private boolean syncing;

    public Structure3DControlsPanel(AppModel model) {
        super(new BorderLayout(0, 2));
        this.model = model;
        this.controller = new StructureSliderController(model);
        liveTimer = new Timer(LIVE_DELAY_MS, e -> applyFromSliders(true));
        liveTimer.setRepeats(false);

        shapeCombo.setName("structure3dShape");
        radiusSlider.setName("structure3dRadius");
        gapSlider.setName("structure3dGap");
        towersSlider.setName("structure3dTowers");
        statusLabel.setName("structure3dStatus");
        shapeCombo.setToolTipText("Форма экрана (как «Форма экрана» в «Сетапе»). Изогнутый экран строится"
                + " раздельными башнями; смена стена ↔ раздельные башни строит сетку заново.");
        radiusSlider.setToolTipText("Радиус лицевой поверхности экрана, 2,5…30 м, шаг 0,5 м");
        gapSlider.setToolTipText("<html>Зазор между башнями, шаг 500 мм. Прямой экран: 0 — стена с общими"
                + " столбами.<br>Изогнутый: не меньше 500 мм.</html>");
        towersSlider.setToolTipText("Число раздельных башен (каждая — 2 столба). 0 — авто.");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JPanel shapeBox = column(new JLabel("Форма экрана"), shapeCombo);
        row.add(shapeBox);
        row.add(column(radiusLabel, radiusSlider));
        row.add(column(gapLabel, gapSlider));
        row.add(column(towersLabel, towersSlider));
        for (JSlider s : List.of(radiusSlider, gapSlider, towersSlider)) {
            s.setPreferredSize(new Dimension(200, s.getPreferredSize().height));
            // ширина колонки -- по подписи (у радиуса она длинная): ползунок тянется на всю колонку
            s.setMaximumSize(new Dimension(Integer.MAX_VALUE, s.getPreferredSize().height));
            s.setFocusable(false); // стрелки клавиатуры остаются за 3D-видом/окном
        }
        add(row, BorderLayout.CENTER);
        statusLabel.setForeground(Palette.WARN);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        add(statusLabel, BorderLayout.SOUTH);

        shapeCombo.addActionListener(e -> {
            if (!syncing) {
                applyFromSliders(false);
            }
        });
        for (JSlider s : List.of(radiusSlider, gapSlider, towersSlider)) {
            s.addChangeListener(e -> onSliderChanged(s));
        }
        syncFromModel();
    }

    private static JPanel column(JLabel label, java.awt.Component control) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        label.setAlignmentX(LEFT_ALIGNMENT);
        if (control instanceof javax.swing.JComponent jc) {
            jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        p.add(label);
        p.add(Box.createVerticalStrut(2));
        p.add(control);
        return p;
    }

    private void onSliderChanged(JSlider source) {
        if (syncing) {
            return;
        }
        updateLabelsFromSliders();
        if (source.getValueIsAdjusting()) {
            liveTimer.restart(); // коалесцирование: пересчёт последнего значения за интервал
        } else {
            liveTimer.stop();
            applyFromSliders(false); // отпускание / щелчок — сразу и завершает жест
        }
    }

    /** Пересчитывает конструктив по текущим положениям ползунков. */
    private void applyFromSliders(boolean adjusting) {
        if (syncing || !controller.applicable()) {
            return;
        }
        ScreenCurveType type = selectedType();
        controller.apply(type, StructureSliderMath.tickToRadiusMm(radiusSlider.getValue()),
                StructureSliderMath.tickToGapMm(gapSlider.getValue(), type), towersSlider.getValue(), adjusting);
    }

    private ScreenCurveType selectedType() {
        Object sel = shapeCombo.getSelectedItem();
        return sel instanceof ScreenCurveType t ? t : ScreenCurveType.FLAT;
    }

    /** Живые подписи по положению ползунков (до пересчёта модели), чтобы число под пальцем
     *  менялось без задержки коалесцирования. */
    private void updateLabelsFromSliders() {
        Screen s = model.getCurrentScreen();
        ScreenCurveType type = selectedType();
        double radius = StructureSliderMath.tickToRadiusMm(radiusSlider.getValue());
        double gap = StructureSliderMath.tickToGapMm(gapSlider.getValue(), type);
        boolean separate = StructureCurveMath.separateTowers(type, gap);
        radiusLabel.setText(radiusText(s, type.isCurved(), radius));
        gapLabel.setText(StructureSliderMath.gapLabel(separate, gap));
        int towers = towersSlider.getValue();
        towersLabel.setText(StructureSliderMath.towerLabel(separate, towers,
                towers > 0 ? towers : Math.max(1, controller.autoTowerCount())));
    }

    private String radiusText(Screen s, boolean curved, double radiusMm) {
        CabinetType t = s != null ? model.typeOf(s) : null;
        double w = t != null && t.getWidthMm() > 0 ? t.getWidthMm() : 500;
        double angle = StructureCurveMath.cabinetAngleDeg(w, radiusMm);
        double arc = Double.isNaN(angle) || s == null ? Double.NaN : angle * s.getCols();
        return StructureSliderMath.radiusLabel(curved, s != null && s.isStructureCurveByAngle(), radiusMm, angle, arc);
    }

    /** Заполняет ползунки, подписи и строку предупреждений из ТЕКУЩЕГО экрана модели —
     *  вызывается при открытии и после каждого изменения модели (в т.ч. от полей «Сетапа»,
     *  Ctrl+Z и самих ползунков). Ползунок, который сейчас тянут мышью, не трогается — иначе
     *  он «дёргался» бы под пальцем. */
    public void syncFromModel() {
        Screen s = model.getCurrentScreen();
        boolean structure = s != null && s.getMountType() == ScreenMountType.STRUCTURE;
        syncing = true;
        try {
            shapeCombo.setEnabled(structure);
            if (!structure) {
                radiusSlider.setEnabled(false);
                gapSlider.setEnabled(false);
                towersSlider.setEnabled(false);
                radiusLabel.setText("Радиус");
                gapLabel.setText("Зазор между башнями");
                towersLabel.setText("Число башен");
                statusLabel.setText(s == null ? "Экран не выбран." : "Ползунки — только для наземного конструктива.");
                return;
            }
            ScreenCurveType type = s.getStructureCurveType();
            boolean separate = StructureCurveMath.separateTowers(s);
            shapeCombo.setSelectedItem(type);
            setIfIdle(radiusSlider, StructureSliderMath.radiusToTick(s.getStructureCurveRadiusMm()));
            gapSlider.setMinimum(StructureSliderMath.minGapTick(type));
            setIfIdle(gapSlider, StructureSliderMath.gapToTick(s.getStructureTowerGapMm()));
            int auto = controller.autoTowerCount();
            int max = StructureSliderMath.maxTowerCount(auto);
            if (!towersSlider.getValueIsAdjusting()) {
                towersSlider.setMaximum(Math.max(max, s.getStructureSeparateTowerCount()));
            }
            setIfIdle(towersSlider, StructureSliderMath.clampTowerCount(s.getStructureSeparateTowerCount(),
                    towersSlider.getMaximum()));
            radiusSlider.setEnabled(type.isCurved());
            gapSlider.setEnabled(true);
            towersSlider.setEnabled(separate);

            radiusLabel.setText(radiusText(s, type.isCurved(), s.getStructureCurveRadiusMm()));
            gapLabel.setText(StructureSliderMath.gapLabel(separate, s.getStructureTowerGapMm()));
            int standing = separate ? s.getStructureTowerCount() / 2 : s.getStructureTowerCount();
            towersLabel.setText(StructureSliderMath.towerLabel(separate, s.getStructureSeparateTowerCount(),
                    separate ? Math.max(standing, auto) : standing));
            List<String> warnings = controller.warnings();
            statusLabel.setText(warnings.isEmpty() ? " "
                    : "<html><div style='width:" + STATUS_WIDTH_PX + "px'>⚠ " + String.join("<br>⚠ ", warnings)
                    + "</div></html>");
        } finally {
            syncing = false;
        }
    }

    private static void setIfIdle(JSlider slider, int value) {
        if (!slider.getValueIsAdjusting() && slider.getValue() != value) {
            slider.setValue(value);
        }
    }

    // ---- для тестов ----

    JSlider radiusSlider() {
        return radiusSlider;
    }

    JSlider gapSlider() {
        return gapSlider;
    }

    JSlider towersSlider() {
        return towersSlider;
    }

    JComboBox<ScreenCurveType> shapeCombo() {
        return shapeCombo;
    }

    String radiusText() {
        return radiusLabel.getText();
    }

    String gapText() {
        return gapLabel.getText();
    }

    String towersText() {
        return towersLabel.getText();
    }

    String statusText() {
        return statusLabel.getText();
    }

    StructureSliderController controller() {
        return controller;
    }
}
