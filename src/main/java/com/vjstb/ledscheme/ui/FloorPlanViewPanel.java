package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.FloorCalc;
import com.vjstb.ledscheme.settings.FloorPlanViewMode;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * Содержимое окна плана напольного каркаса ({@link FloorPlanDialog}) с переключателем режимов
 * «2D схема» / «3D редактор» — запрос пользователя 2026-10-01: «для пола давай сделаем
 * переключаемый режим отображения — либо 2D схема как сейчас, либо 3D редактор как для
 * конструктива». Вынесено из диалога в обычную {@code JPanel}, чтобы переключатель и
 * сохранение выбора проверялись тестами без окон (в headless-среде {@code JDialog} не создать).
 *
 * <p>2D — прежний {@link FloorPlanPanel}; 3D — {@link FloorPlan3DPanel}, создаётся ЛЕНИВО при
 * первом переключении (в 2D-режиме GL вообще не трогается). Выбор режима запоминается в
 * профиле ({@link SettingsManager#setFloorPlanViewMode}), по умолчанию 2D. Если GL недоступен,
 * 3D-режим показывает текстовую заглушку, 2D работает как раньше.
 *
 * <p>Окно не считает само: после любой правки модели (клик в 3D, Ctrl+Z, «Рассчитать пол»)
 * вызывающий код дёргает {@link #refresh()} — расчёт {@link FloorCalc#compute} берётся заново
 * и раздаётся 2D-плану, 3D-виду и строке итогов внизу.
 */
public class FloorPlanViewPanel extends JPanel {

    static final String CARD_2D = "2d";
    static final String CARD_3D = "3d";

    private final AppModel model;
    private final Screen screen;
    private final SettingsManager settings;
    private final boolean tryGl;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final FloorPlanPanel plan2d;
    private FloorPlan3DPanel view3d;
    private final JToggleButton mode2dBtn = new JToggleButton(FloorPlanViewMode.PLAN_2D.getLabel());
    private final JToggleButton mode3dBtn = new JToggleButton(FloorPlanViewMode.EDITOR_3D.getLabel());
    private final JPanel controls3d = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JCheckBox showCabinets = new JCheckBox("Кабинеты", true);
    private final JLabel hint3d = new JLabel("ЛКМ по раме — убрать · Ctrl+ЛКМ по зелёному призраку — вернуть/добавить"
            + " · перетаскивание — вращать, средняя кнопка — сдвиг, колесо — масштаб · Ctrl+Z — отменить");
    private final JLabel totals = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private FloorPlanViewMode mode;

    /**
     * @param settings может быть {@code null} (тогда выбор режима не запоминается).
     * @param tryGl    {@code false} — 3D-режим без попытки создать GL (тесты).
     */
    public FloorPlanViewPanel(AppModel model, Screen screen, SettingsManager settings, boolean tryGl) {
        super(new BorderLayout(0, 6));
        this.model = model;
        this.screen = screen;
        this.settings = settings;
        this.tryGl = tryGl;
        CabinetType type = model.typeOf(screen);
        plan2d = new FloorPlanPanel(screen, type, FloorCalc.compute(screen, type, model.getWorkspace()));
        center.add(plan2d, CARD_2D);
        add(center, BorderLayout.CENTER);

        ButtonGroup group = new ButtonGroup();
        group.add(mode2dBtn);
        group.add(mode3dBtn);
        mode2dBtn.setToolTipText("Плоский план сверху — как раньше: рамы, стаканы, кабинеты без опоры оранжевым.");
        mode3dBtn.setToolTipText("3D-вид пола с редактированием рам кликом, как у наземного конструктива.");
        mode2dBtn.addActionListener(e -> setMode(FloorPlanViewMode.PLAN_2D, true));
        mode3dBtn.addActionListener(e -> setMode(FloorPlanViewMode.EDITOR_3D, true));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeRow.add(new JLabel("Вид:"));
        modeRow.add(mode2dBtn);
        modeRow.add(mode3dBtn);
        controls3d.add(new JLabel("   Камера:"));
        controls3d.add(viewButton("Сверху", 0, 89));
        controls3d.add(viewButton("Спереди", 0, 30));
        controls3d.add(viewButton("Сбоку", 90, 30));
        controls3d.add(viewButton("3/4", -25, 35));
        showCabinets.setToolTipText("Скрыть плитки кабинетов, чтобы увидеть рамы, стаканы, ножки и зубы.");
        showCabinets.addActionListener(e -> {
            if (view3d != null) {
                view3d.setShowCabinets(showCabinets.isSelected());
            }
        });
        controls3d.add(showCabinets);
        modeRow.add(controls3d);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        modeRow.setAlignmentX(LEFT_ALIGNMENT);
        hint3d.setAlignmentX(LEFT_ALIGNMENT);
        hint3d.setForeground(Palette.MUTED);
        hint3d.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 0));
        top.add(modeRow);
        top.add(hint3d);
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        totals.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setForeground(Palette.MUTED);
        bottom.add(totals);
        bottom.add(status);
        add(bottom, BorderLayout.SOUTH);

        FloorPlanViewMode initial = settings != null ? settings.activeProfile().getFloorPlanViewMode()
                : FloorPlanViewMode.PLAN_2D;
        setMode(initial, false);
        refresh();
    }

    private JButton viewButton(String label, double yaw, double pitch) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            if (view3d != null) {
                view3d.setViewAngle(yaw, pitch);
            }
        });
        return b;
    }

    /** Переключение режима; {@code remember} — записать выбор в профиль (клик пользователя). */
    public void setMode(FloorPlanViewMode newMode, boolean remember) {
        mode = newMode != null ? newMode : FloorPlanViewMode.PLAN_2D;
        boolean is3d = mode == FloorPlanViewMode.EDITOR_3D;
        if (is3d && view3d == null) {
            view3d = new FloorPlan3DPanel(model, screen, tryGl);
            view3d.setShowCabinets(showCabinets.isSelected());
            view3d.setStatusListener(this::showStatus);
            center.add(view3d, CARD_3D);
        }
        cards.show(center, is3d ? CARD_3D : CARD_2D);
        mode2dBtn.setSelected(!is3d);
        mode3dBtn.setSelected(is3d);
        controls3d.setVisible(is3d && view3d.isGlAvailable());
        hint3d.setVisible(is3d && view3d.isGlAvailable());
        if (remember && settings != null && settings.activeProfile().getFloorPlanViewMode() != mode) {
            settings.setFloorPlanViewMode(mode);
        }
        revalidate();
        repaint();
    }

    public FloorPlanViewMode getMode() {
        return mode;
    }

    /** Пересчитать и раздать результат всем видам — после любой правки модели. */
    public void refresh() {
        CabinetType type = model.typeOf(screen);
        FloorCalc.Result r = FloorCalc.compute(screen, type, model.getWorkspace());
        plan2d.update(type, r);
        if (view3d != null) {
            view3d.refresh();
        }
        totals.setText(totalsText(r));
    }

    /** Строка итогов под видом — та же сводка, что в легенде 2D-плана, чтобы в 3D-режиме
     *  было видно, как правка рамы меняет железо. */
    static String totalsText(FloorCalc.Result r) {
        return String.format("Рам: %d · Стаканов: %d · Болтов: %d · Ножек: %d · Зубов: %d · Без опоры: %d"
                        + " · Средняя нагрузка: %s кг/м²", r.frameCount(), r.cupCount(), r.boltCount(), r.legCount(),
                r.toothCount(), r.unsupportedCabinetCount(),
                UiKit.fmt(Math.round(r.averageLoadKgPerM2() * 10) / 10.0));
    }

    private void showStatus(String text) {
        status.setText(text == null || text.isEmpty() ? " " : text);
    }

    FloorPlanPanel plan2d() {
        return plan2d;
    }

    FloorPlan3DPanel view3d() {
        return view3d;
    }

    String totalsLabelText() {
        return totals.getText();
    }

    String visibleCard() {
        return mode == FloorPlanViewMode.EDITOR_3D ? CARD_3D : CARD_2D;
    }
}
