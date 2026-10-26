package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.EditReport;
import com.vjstb.ledscheme.service.ReceiverCardChecks;
import com.vjstb.ledscheme.service.ScreenModules;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.UiKit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JToggleButton;

/**
 * Вид «Сигнал» → «Приёмные карты» (docs/receiver-cards/PLAN.md, 6.1). Контракт между треками: содержимое
 * вида делает трек C, а этап (трек D) только создаёт его этим конструктором, кладёт в
 * переключатель видов перед «Расключением экрана» и зовёт {@link #refresh()} при показе.
 *
 * <p>Работа в виде: слева холст (модули серые, карты — заливкой), справа «Тип карты» → «Как назначать» → «Карты
 * экрана». «Вручную» — протяжка по модулям ставит одну карту выбранного типа; «Автоматически» — протяжка по области
 * разбивает её на карты Г × В (пунктир показывает разбиение до отпускания мыши). Новая карта целиком заменяет
 * задетые (D10) — итог в строке под холстом. Клик по модулю с картой выбирает её; Delete или «Снять карту» снимает.
 * Инструменты над холстом: «Порядок вручную…» (клики по модулям выбранной карты = 1, 2, 3…), «Место карты…» (клик по
 * модулю, на котором стоит карта), флажок «шлейфы». Под холстом — палитра нумерации модулей всего экрана.
 *
 * <p>Логики карт здесь нет: каждое действие — один вызов {@code AppModel} (одна запись отмены), вид только решает,
 * какой вызов соответствует жесту.
 */
public class ReceiverCardsView extends ModularEditorView implements CardAssignPanel.Host, ModuleGridCanvas.Listener {

    /** Что делает клик по холсту. */
    enum Tool { SELECT, ORDER, HOST }

    private final ReceiverCardsCanvas canvas = new ReceiverCardsCanvas();
    private final CardAssignPanel assign;
    private final NumberingPalette palette;
    private final JButton removeBtn = new JButton("Снять карту");
    private final JToggleButton orderBtn = new JToggleButton("Порядок вручную…");
    private final JButton resetOrderBtn = new JButton("Сбросить к шаблону");
    private final JToggleButton hostBtn = new JToggleButton("Место карты…");
    private final JCheckBox ribbonsCheck = new JCheckBox("шлейфы");

    private Tool tool = Tool.SELECT;
    private String activeCardId;
    private ReceiverCardsCanvas.Data data;

    public ReceiverCardsView(AppModel model, SettingsManager settings) {
        super(model, settings, "receiver.cardsSplit", 0.72);
        assign = new CardAssignPanel(this);
        palette = new NumberingPalette(p -> {
            try {
                model.setModuleNumbering(p);
            } catch (RuntimeException ex) {
                setNote(ex.getMessage(), true);
            }
        });

        removeBtn.setToolTipText("Снять выбранную карту: её модули останутся без карты (Delete)");
        removeBtn.addActionListener(e -> removeActive());
        orderBtn.setToolTipText("Клики по модулям выбранной карты задают порядок подключения: 1, 2, 3…");
        orderBtn.addActionListener(e -> toggleTool(Tool.ORDER));
        resetOrderBtn.setToolTipText("Вернуть выбранной карте нумерацию по шаблону экрана");
        resetOrderBtn.addActionListener(e -> resetOrder());
        hostBtn.setToolTipText("Модуль, на котором стоит карта: от него по прямой считается длина шлейфов");
        hostBtn.addActionListener(e -> toggleTool(Tool.HOST));
        ribbonsCheck.setToolTipText("Нарисовать шлейфы от места карты ко всем её модулям (длинные видны всегда)");
        ribbonsCheck.addActionListener(e -> canvas.setShowRibbons(ribbonsCheck.isSelected()));
        toolbar.add(removeBtn);
        toolbar.add(orderBtn);
        toolbar.add(resetOrderBtn);
        toolbar.add(hostBtn);
        toolbar.add(ribbonsCheck);

        canvas.setListener(this);
        UiKit.bindDeleteKey(canvas, this::removeActive);
        // та же причина, что у блоков питания: после клика по строке фокус в таблице, а не на холсте
        UiKit.bindDeleteKey(assign.cardsTable(), this::removeActive);
        installCanvas(canvas);
        installSide(assign);
        installBelow(palette);
        start();
    }

    // ------------------------------------------------------------------ обновление

    @Override
    protected void onScreenChanged() {
        tool = Tool.SELECT;
        activeCardId = null;
        setNote("", false);
    }

    @Override
    protected void refreshContent(Screen s) {
        Workspace ws = model.getWorkspace();
        data = s != null ? ReceiverCardsCanvas.Data.of(s, ws, cardNumbering(), numberFormat()) : null;
        boolean modular = data != null && data.modules().isModular();
        if (s == null || s.receiverCardById(activeCardId) == null) {
            activeCardId = null;
        }
        canvas.setData(data, ModuleGridGeometry.aspectOf(s, ws));
        canvas.setActiveCardId(activeCardId);
        palette.setPattern(s != null ? s.getModuleNumbering() : new com.vjstb.ledscheme.model.ModuleNumberingPattern());
        assign.setTypes(model.getReceiverCardTypes());
        assign.setCards(data, ws, activeCardId);
        warningsBar.setWarnings(modular ? buildWarnings(s, ws) : List.of());

        ReceiverCardInstance active = s != null ? s.receiverCardById(activeCardId) : null;
        removeBtn.setEnabled(active != null);
        orderBtn.setEnabled(modular);
        hostBtn.setEnabled(modular);
        resetOrderBtn.setEnabled(active != null && active.getManualPortOrder() != null);
        orderBtn.setSelected(tool == Tool.ORDER);
        orderBtn.setText(tool == Tool.ORDER ? "Готово" : "Порядок вручную…");
        hostBtn.setSelected(tool == Tool.HOST);
        hostBtn.setText(tool == Tool.HOST ? "Готово" : "Место карты…");
        canvas.setPreviewProvider(assign.autoMode() && tool == Tool.SELECT && data != null
                ? rect -> ReceiverCardsCanvas.autoTiles(data.modules(), rect, assign.autoW(), assign.autoH()) : null);
        setHint(hintFor());
    }

    private String hintFor() {
        return switch (tool) {
            case ORDER -> "Порядок вручную: кликайте модули выбранной карты по порядку подключения. «Готово» — выйти; "
                    + "модуль без номера показан знаком «?».";
            case HOST -> "Место карты: клик по модулю, на котором стоит карта, — от него по прямой считается длина "
                    + "шлейфов. «Готово» — выйти.";
            default -> assign.autoMode()
                    ? "Автоматически: протяните по области — пунктир покажет разбиение на карты Г × В, при отпускании "
                    + "карты встанут (остаток у края — карты меньшего размера)."
                    : "Вручную: протяните по модулям — карта выбранного типа ставится при отпускании, задетые карты "
                    + "заменяются. Клик по карте — выбор, Delete — снять.";
        };
    }

    // ------------------------------------------------------------------ замечания

    private List<ReceiverWarningsBar.Warning> buildWarnings(Screen s, Workspace ws) {
        List<ReceiverWarningsBar.Warning> out = new ArrayList<>();
        ReceiverCardChecks.Result checks = data.checks();
        if (s.getReceiverCards().isEmpty()) {
            out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.INFO,
                    "Карты не назначены: протяните по модулям, чтобы поставить приёмную карту.", null));
        }
        int without = checks.modulesWithoutCard().size();
        if (without > 0) {
            out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.WARNING,
                    "Модулей без карты: " + without + " (оранжевые) — в расключение сигнала они не попадут.", null));
        }
        for (ReceiverCardInstance card : data.cards()) {
            ReceiverCardChecks.CardCheck cc = checks.card(card.getId());
            if (cc == null) {
                continue;
            }
            String name = data.names().get(card.getId());
            ReceiverCardType type = card.getReceiverCardTypeId() != null
                    ? ws.receiverCardTypeById(card.getReceiverCardTypeId()) : null;
            if (type == null) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.WARNING,
                        name + ": тип карты не найден в библиотеке — лимиты не проверяются.", card.getId()));
                continue;
            }
            if (cc.overOutputs()) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        name + ": модулей больше, чем выходов карты (" + cc.moduleCount() + "/" + cc.outputs() + ").",
                        card.getId()));
            }
            if (cc.longRibbonCount() > 0) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        name + ": шлейфов длиннее " + Math.round(type.getRibbonLengthMm()) + " мм: "
                                + cc.longRibbonCount() + " (красные линии).", card.getId()));
            }
            if (cc.widthExceeded()) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        name + ": ширина " + cc.resolutionWidthPx() + " px больше допустимой " + type.getMaxWidthPx()
                                + " px.", card.getId()));
            }
            if (cc.heightExceeded()) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        name + ": высота " + cc.resolutionHeightPx() + " px больше допустимой " + type.getMaxHeightPx()
                                + " px.", card.getId()));
            }
            if (cc.pixelsExceeded()) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.ERROR,
                        name + ": " + cc.pixels() + " px больше допустимых " + type.getMaxPixels() + ".",
                        card.getId()));
            }
            if (!cc.unnumberedModuleIds().isEmpty()) {
                out.add(new ReceiverWarningsBar.Warning(ReceiverWarningsBar.Level.WARNING,
                        name + ": модулей без номера: " + cc.unnumberedModuleIds().size()
                                + " (порядок вручную не закончен, «?»).", card.getId()));
            }
        }
        return out;
    }

    @Override
    protected void warningTargetClicked(String targetId) {
        selectCard(targetId);
    }

    // ------------------------------------------------------------------ панель назначения

    @Override
    public void assignSettingsChanged() {
        canvas.setPreviewProvider(assign.autoMode() && tool == Tool.SELECT && data != null
                ? rect -> ReceiverCardsCanvas.autoTiles(data.modules(), rect, assign.autoW(), assign.autoH()) : null);
        setHint(hintFor());
    }

    @Override
    public void cardSelected(String cardId) {
        selectCard(cardId);
    }

    private void selectCard(String id) {
        activeCardId = id;
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
        ReceiverCardInstance card = cardOf(s, m.id());
        try {
            switch (tool) {
                case ORDER -> {
                    if (card == null) {
                        return;
                    }
                    if (!card.getId().equals(activeCardId)) {
                        // первая отметка на другой карте только выбирает её и начинает порядок заново (как в прототипе)
                        activeCardId = card.getId();
                        model.setReceiverCardManualOrder(card.getId(), List.of());
                    } else {
                        model.appendReceiverCardManualOrder(card.getId(), m.id());
                    }
                }
                case HOST -> {
                    if (card != null) {
                        activeCardId = card.getId();
                        model.setReceiverCardPlace(card.getId(), m.id());
                        setNote(data.names().get(card.getId()) + ": место перенесено, шлейфы считаются от него.", false);
                    }
                }
                default -> {
                    if (card != null) {
                        selectCard(card.getId());
                    } else {
                        assign(new GridRect(row, col, row, col));
                    }
                }
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

    /** Протяжка в режиме выбора: ставит карты выбранного типа «Вручную» или «Автоматически». */
    private void assign(GridRect rect) {
        String typeId = assign.selectedTypeId();
        if (typeId == null) {
            setNote("Выберите тип приёмной карты в списке справа.", true);
            return;
        }
        boolean auto = assign.autoMode();
        EditReport r = auto
                ? model.assignReceiverCardsAuto(rect, typeId, assign.autoW(), assign.autoH())
                : model.assignReceiverCardsManual(rect, typeId);
        setNote(r.message(), r.orphaned() > 0);
        if (!auto && !r.createdIds().isEmpty()) {
            activeCardId = r.createdIds().get(0);
        }
    }

    private static ReceiverCardInstance cardOf(Screen s, String moduleId) {
        for (ReceiverCardInstance c : s.getReceiverCards()) {
            if (c.getModuleIds().contains(moduleId)) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ кнопки

    private void removeActive() {
        if (activeCardId == null) {
            return;
        }
        try {
            EditReport r = model.removeReceiverCard(activeCardId);
            activeCardId = null;
            setNote(r.message(), r.orphaned() > 0);
        } catch (RuntimeException ex) {
            setNote(ex.getMessage(), true);
        }
        refresh();
    }

    private void resetOrder() {
        if (activeCardId == null) {
            return;
        }
        try {
            model.resetReceiverCardOrder(activeCardId);
        } catch (RuntimeException ex) {
            setNote(ex.getMessage(), true);
        }
        refresh();
    }

    private void toggleTool(Tool t) {
        Screen s = model.getCurrentScreen();
        if (tool == t) {
            tool = Tool.SELECT;
        } else if (s == null || s.getReceiverCards().isEmpty()) {
            tool = Tool.SELECT;
            setNote("Сначала назначьте карты.", true);
        } else {
            tool = t;
            if (t == Tool.ORDER) {
                ReceiverCardInstance c = s.receiverCardById(activeCardId);
                if (c == null) {
                    c = first(s);
                }
                activeCardId = c.getId();
                try {
                    model.setReceiverCardManualOrder(c.getId(), List.of());
                } catch (RuntimeException ex) {
                    setNote(ex.getMessage(), true);
                }
            }
        }
        refresh();
    }

    /** Первая карта в порядке нумерации К1…КN. */
    private ReceiverCardInstance first(Screen s) {
        if (data != null && !data.cards().isEmpty()) {
            return data.cards().get(0);
        }
        return s.getReceiverCards().get(0);
    }

    // ------------------------------------------------------------------ для тестов

    ReceiverCardsCanvas canvas() {
        return canvas;
    }

    CardAssignPanel assignPanel() {
        return assign;
    }

    NumberingPalette palette() {
        return palette;
    }

    JButton removeButton() {
        return removeBtn;
    }

    JToggleButton orderButton() {
        return orderBtn;
    }

    JButton resetOrderButton() {
        return resetOrderBtn;
    }

    JToggleButton hostButton() {
        return hostBtn;
    }

    JCheckBox ribbonsCheck() {
        return ribbonsCheck;
    }

    String activeCardId() {
        return activeCardId;
    }

    Tool tool() {
        return tool;
    }

    String note() {
        return noteText();
    }
}
