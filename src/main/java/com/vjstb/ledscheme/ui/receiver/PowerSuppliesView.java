package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.EditReport;
import com.vjstb.ledscheme.service.PowerSupplyChecks;
import com.vjstb.ledscheme.service.PowerSupplyLayout;
import com.vjstb.ledscheme.service.ScreenModules;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.UiKit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JToggleButton;

/**
 * Вид «Питание» → «Блоки питания» (docs/receiver-cards/PLAN.md, 6.2). Контракт между треками: содержимое
 * вида делает трек C, а этап (трек D) только создаёт его этим конструктором, кладёт в
 * переключатель видов перед «Расключением экрана» и зовёт {@link #refresh()} при показе.
 *
 * <p>Блок питает модули, а не карты. «Вручную» — протяжка ставит один блок выбранного типа на выделенные модули;
 * «Автоматически» — область дробится на прямоугольники, размер подбирает сам {@code PowerSupplyEditor} (итог —
 * строка «Подобрано: 4×2 мод. на блок, блоков: 9»). Карта питается от блока, в области которого стоит её место
 * (D19): на плашке этого блока «+К». Правило замены то же, что у карт: новый блок целиком заменяет задетые, остаток —
 * модули «без питания» (оранжевые). Инструменты: «Снять блок» / Delete, «Место блока…» (клик по модулю внутри
 * области — отсюда считается длина кабелей), флажок «кабели питания».
 */
public class PowerSuppliesView extends ModularEditorView implements PowerAssignPanel.Host, ModuleGridCanvas.Listener {

    /** Что делает клик по холсту. */
    enum Tool { SELECT, HOST }

    private final PowerSuppliesCanvas canvas = new PowerSuppliesCanvas();
    private final PowerAssignPanel assign;
    private final JButton removeBtn = new JButton("Снять блок");
    private final JToggleButton hostBtn = new JToggleButton("Место блока…");
    private final JCheckBox cablesCheck = new JCheckBox("кабели питания");

    private Tool tool = Tool.SELECT;
    private String activeSupplyId;
    private String autoInfo = "";
    private PowerSuppliesCanvas.Data data;

    public PowerSuppliesView(AppModel model, SettingsManager settings) {
        super(model, settings, "receiver.powerSplit", 0.72);
        assign = new PowerAssignPanel(this);

        removeBtn.setToolTipText("Снять выбранный блок: его модули останутся без питания (Delete)");
        removeBtn.addActionListener(e -> removeActive());
        hostBtn.setToolTipText("Модуль, на котором стоит блок: от него по прямой считается длина кабелей питания");
        hostBtn.addActionListener(e -> toggleHost());
        cablesCheck.setToolTipText("Нарисовать кабели от места блока ко всем его модулям и картам (длинные видны всегда)");
        cablesCheck.addActionListener(e -> canvas.setShowCables(cablesCheck.isSelected()));
        toolbar.add(removeBtn);
        toolbar.add(hostBtn);
        toolbar.add(cablesCheck);

        canvas.setListener(this);
        UiKit.bindDeleteKey(canvas, this::removeActive);
        installCanvas(canvas);
        installSide(assign);
        start();
    }

    // ------------------------------------------------------------------ обновление

    @Override
    protected void onScreenChanged() {
        tool = Tool.SELECT;
        activeSupplyId = null;
        autoInfo = "";
        setNote("", false);
    }

    @Override
    protected void refreshContent(Screen s) {
        Workspace ws = model.getWorkspace();
        data = s != null ? PowerSuppliesCanvas.Data.of(s, ws, cardNumbering()) : null;
        boolean modular = data != null && data.modules().isModular();
        if (s == null || s.powerSupplyById(activeSupplyId) == null) {
            activeSupplyId = null;
        }
        canvas.setData(data, ModuleGridGeometry.aspectOf(s, ws));
        canvas.setActiveSupplyId(activeSupplyId);
        assign.setTypes(model.getPowerSupplyTypes());
        assign.setAutoInfo(autoInfo);
        assign.setSupplies(data, ws, activeSupplyId);
        warningsBar.setWarnings(modular ? buildWarnings(s) : List.of());

        removeBtn.setEnabled(activeSupplyId != null);
        hostBtn.setEnabled(modular);
        hostBtn.setSelected(tool == Tool.HOST);
        hostBtn.setText(tool == Tool.HOST ? "Готово" : "Место блока…");
        setHint(hintFor());
    }

    private String hintFor() {
        if (tool == Tool.HOST) {
            return "Место блока: клик по модулю внутри области блока — блок встаёт туда, длина кабелей питания "
                    + "считается от этого места. «Готово» — выйти.";
        }
        return assign.autoMode()
                ? "Автоматически: протяните по области — она разобьётся на блоки-прямоугольники подобранного размера. "
                + "Число на модуле — шлейф его блока; карта питается от своего блока: «+К» — отдельным выходом, иначе "
                + "забирает один шлейф; красное — шлейфов не хватает или кабель длиннее допустимого."
                : "Вручную: протяните по модулям — один блок выбранного типа на эти модули (задетые блоки заменяются). "
                + "Клик по модулю — выбор блока, Delete — снять.";
    }

    // ------------------------------------------------------------------ замечания

    private List<ReceiverWarningsBar.Warning> buildWarnings(Screen s) {
        List<ReceiverWarningsBar.Warning> out = new ArrayList<>();
        PowerSupplyChecks.Result checks = data.checks();
        if (s.getPowerSupplies().isEmpty()) {
            out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.INFO,
                    "Блоки питания не назначены: протяните по модулям, чтобы поставить блок.", null));
        }
        if (!checks.modulesWithoutSupply().isEmpty()) {
            out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.WARNING,
                    "Модулей без питания: " + checks.modulesWithoutSupply().size()
                            + " (оранжевые) — в расключение питания они не попадут.", null));
        }
        if (!checks.cardsWithoutSupply().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : checks.cardsWithoutSupply()) {
                String n = data.cardNames().get(id);
                names.add(n != null ? n : "?");
            }
            out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.WARNING,
                    "Карты без питания: " + String.join(", ", names)
                            + " — место карты вне областей блоков, а карта питается от блока своей области.", null));
        }
        for (PowerSupplyChecks.SupplyProblem p : checks.problems()) {
            PowerSupplyLayout.Supply sup = checks.layout().ofSupply(p.supplyId());
            if (p.over() > 0 && sup != null) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        p.name() + ": потребителей больше, чем выходов (" + sup.used() + "/" + sup.outputs()
                                + ") — перегруз блока.", p.supplyId()));
            }
            if (!p.longModuleIds().isEmpty() && sup != null && sup.type() != null
                    && sup.type().getCableLengthMm() != null) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        p.name() + ": кабелей длиннее " + Math.round(sup.type().getCableLengthMm()) + " мм: "
                                + p.longModuleIds().size() + " (красные линии).", p.supplyId()));
            }
        }
        return out;
    }

    @Override
    protected void warningTargetClicked(String targetId) {
        selectSupply(targetId);
    }

    // ------------------------------------------------------------------ панель назначения

    @Override
    public void assignSettingsChanged() {
        setHint(hintFor());
    }

    @Override
    public void supplySelected(String supplyId) {
        selectSupply(supplyId);
    }

    private void selectSupply(String id) {
        activeSupplyId = id;
        refresh();
    }

    // ------------------------------------------------------------------ жесты на холсте

    @Override
    public void cellClicked(int row, int col) {
        Screen s = model.getCurrentScreen();
        if (s == null || data == null) {
            return;
        }
        ScreenModules.Module m = data.modules().moduleAt(row, col);
        if (m == null) {
            return;
        }
        PowerSupplyLayout.Supply sup = data.checks().layout().ofModule(m.id());
        try {
            if (tool == Tool.HOST) {
                if (sup != null) {
                    activeSupplyId = sup.instance().getId();
                    model.setPowerSupplyPlace(sup.instance().getId(), m.id());
                    setNote(sup.name() + ": место перенесено, кабели считаются от него.", false);
                }
            } else if (sup != null) {
                selectSupply(sup.instance().getId());
            } else {
                assign(new GridRect(row, col, row, col));
            }
        } catch (RuntimeException ex) {
            setNote(ex.getMessage(), true);
        }
        refresh();
    }

    @Override
    public void areaDragged(GridRect rect) {
        if (tool != Tool.SELECT) {
            return;
        }
        try {
            assign(rect);
        } catch (RuntimeException ex) {
            setNote(ex.getMessage(), true);
        }
        refresh();
    }

    /** Протяжка в режиме выбора: ставит блоки выбранного типа «Вручную» или «Автоматически». */
    private void assign(GridRect rect) {
        String typeId = assign.selectedTypeId();
        if (typeId == null) {
            setNote("Выберите тип блока питания в списке справа.", true);
            return;
        }
        boolean auto = assign.autoMode();
        EditReport r = auto ? model.assignPowerSuppliesAuto(rect, typeId)
                : model.assignPowerSuppliesManual(rect, typeId);
        setNote(r.message(), r.orphaned() > 0);
        if (auto && !r.noop()) {
            autoInfo = r.autoInfo();
        }
        if (!auto && !r.createdIds().isEmpty()) {
            activeSupplyId = r.createdIds().get(0);
        }
    }

    // ------------------------------------------------------------------ кнопки

    private void removeActive() {
        if (activeSupplyId == null) {
            return;
        }
        try {
            EditReport r = model.removePowerSupply(activeSupplyId);
            activeSupplyId = null;
            setNote(r.message(), r.orphaned() > 0);
        } catch (RuntimeException ex) {
            setNote(ex.getMessage(), true);
        }
        refresh();
    }

    private void toggleHost() {
        Screen s = model.getCurrentScreen();
        if (tool == Tool.HOST) {
            tool = Tool.SELECT;
        } else if (s == null || s.getPowerSupplies().isEmpty()) {
            setNote("Сначала назначьте блоки питания.", true);
        } else {
            tool = Tool.HOST;
        }
        refresh();
    }

    // ------------------------------------------------------------------ для тестов

    PowerSuppliesCanvas canvas() {
        return canvas;
    }

    PowerAssignPanel assignPanel() {
        return assign;
    }

    JButton removeButton() {
        return removeBtn;
    }

    JToggleButton hostButton() {
        return hostBtn;
    }

    JCheckBox cablesCheck() {
        return cablesCheck;
    }

    String activeSupplyId() {
        return activeSupplyId;
    }

    Tool tool() {
        return tool;
    }

    String note() {
        return noteText();
    }

    PowerSupplyInstance activeSupply() {
        Screen s = model.getCurrentScreen();
        return s != null ? s.powerSupplyById(activeSupplyId) : null;
    }
}
