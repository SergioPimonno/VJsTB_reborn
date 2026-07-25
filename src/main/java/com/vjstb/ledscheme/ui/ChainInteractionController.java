package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Логика построения цепочки на холсте, общая для питания и сигнала: клик по
 * непрописанному кабинету сразу начинает новую цепочку для текущей выбранной
 * фазы/порта (без отдельного клика по кнопке фазы/порта — тот теперь только
 * выбирает ЦЕЛЬ для следующей цепочки, не запуская её); кабинеты добавляются
 * кликом, протяжкой (зажатая ЛКМ) или стрелками клавиатуры; Esc завершает и
 * сохраняет цепочку (если она не пуста); ПКМ по кабинету во время построения
 * убирает его из строящейся цепочки (не путать с ПКМ по СОХРАНЁННОЙ цепочке —
 * это разрыв связи, отдельная логика в CanvasPanel).
 */
public class ChainInteractionController implements CanvasPanel.Controller {

    /** Решает, можно ли начать новую цепочку с данного кабинета (например, кабинет
     *  уже прописан в другой цепочке — нельзя), и если можно — возвращает обработчик
     *  сохранения новой цепочки для ТЕКУЩЕЙ выбранной фазы/порта. null — начинать нельзя. */
    public interface ChainStarter {
        Consumer<List<String>> startChainFor(String firstCabinetId);
    }

    private final AppModel model;
    private final Runnable onChange;

    private ChainStarter starter;
    private Consumer<List<String>> commitHandler;
    private Consumer<String> onCommitError = msg -> { };
    private boolean building;
    private final List<String> activeIds = new ArrayList<>();
    private int cursorRow = -1;
    private int cursorCol = -1;
    private String hoveredCabId;

    public ChainInteractionController(AppModel model, Runnable onChange) {
        this.model = model;
        this.onChange = onChange;
    }

    /** Регистрирует, как начинать новую цепочку по клику на непрописанный кабинет —
     *  вызывается один раз при создании (PowerStagePanel/SignalStagePanel), а не
     *  на каждый клик, чтобы «текущая фаза/порт» всегда бралась АКТУАЛЬНОЙ на
     *  момент клика, а не той, что была на момент регистрации обработчика. */
    public void setStarter(ChainStarter starter) {
        this.starter = starter;
    }

    /** Колбэк на случай, если сохранение цепочки при завершении бросит исключение
     *  (проверка модели не прошла) — вызывающая панель может показать диалог. Без
     *  этого пользователь просто не мог бы завершить/сохранить цепочку без обратной
     *  связи о причине (см. Task #64: раньше исключение всплывало необработанным и
     *  оставляло контроллер в заблокированном состоянии). */
    public void setOnCommitError(Consumer<String> onCommitError) {
        this.onCommitError = onCommitError != null ? onCommitError : msg -> { };
    }

    /** Начинает (перезапускает) построение новой цепочки для цели (фаза/порт). Прежняя — сохраняется.
     *  Оставлен для программного запуска (не по клику на кабинет) — сейчас не используется
     *  из UI напрямую, но полезен для совместимости/тестов. */
    public void startFor(Consumer<List<String>> commitHandler) {
        if (!commitCurrentSilently()) {
            // Прежняя цепочка не сохранилась (ошибка валидации) — не выбрасываем её
            // молча, оставляем как есть и не переключаемся на новую цель.
            return;
        }
        this.commitHandler = commitHandler;
        activeIds.clear();
        cursorRow = -1;
        cursorCol = -1;
        building = true;
        onChange.run();
    }

    /** Esc: завершить построение, сохранив цепочку, если в ней есть кабинеты. */
    public void finish() {
        if (!building) {
            return;
        }
        if (!commitCurrentSilently()) {
            // Ошибка сохранения (например, проверка модели не прошла) — оставляем
            // building=true и текущий commitHandler, чтобы пользователь мог поправить
            // строящуюся цепочку (убрать проблемный кабинет ПКМ) и попробовать
            // завершить снова, а не терял всё построенное без возможности исправить.
            onChange.run();
            return;
        }
        building = false;
        commitHandler = null;
        onChange.run();
    }

    /** Пытается сохранить строящуюся цепочку. true — сохранена (или сохранять было
     *  нечего); false — сохранение бросило исключение (уже сообщено через onCommitError). */
    private boolean commitCurrentSilently() {
        if (building && !activeIds.isEmpty() && commitHandler != null) {
            try {
                commitHandler.accept(new ArrayList<>(activeIds));
            } catch (RuntimeException ex) {
                onCommitError.accept(ex.getMessage());
                return false;
            }
        }
        activeIds.clear();
        return true;
    }

    public String hoveredCabinetId() {
        return hoveredCabId;
    }

    public void moveCursor(int dRow, int dCol) {
        if (!building) {
            return;
        }
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        if (cursorRow < 0 || cursorCol < 0) {
            if (!activeIds.isEmpty()) {
                CabinetInstance last = scr.cabinetById(activeIds.get(activeIds.size() - 1));
                cursorRow = last != null ? last.getRowIndex() : 0;
                cursorCol = last != null ? last.getColIndex() : 0;
            } else {
                cursorRow = 0;
                cursorCol = 0;
            }
        } else {
            cursorRow = clamp(cursorRow + dRow, 0, scr.getRows() - 1);
            cursorCol = clamp(cursorCol + dCol, 0, scr.getCols() - 1);
        }
        CabinetInstance cab = scr.cabinetAt(cursorRow, cursorCol);
        if (cab != null && !cab.isHidden() && !activeIds.contains(cab.getId())) {
            activeIds.add(cab.getId());
        }
        onChange.run();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- CanvasPanel.Controller ----

    @Override
    public boolean isChainBuilding() {
        return building;
    }

    @Override
    public List<String> activeChainCabIds() {
        return activeIds;
    }

    @Override
    public int cursorRow() {
        return cursorRow;
    }

    @Override
    public int cursorCol() {
        return cursorCol;
    }

    @Override
    public void cabinetClicked(String cabId) {
        if (cabId == null) {
            return;
        }
        Screen scr = model.getCurrentScreen();
        CabinetInstance cab = scr != null ? scr.cabinetById(cabId) : null;
        if (cab == null && model.getCurrentScene() != null) {
            // Кабинет может принадлежать ДРУГОМУ экрану сцены — сигнальная цепочка
            // может физически продолжаться на смежный экран (клик пришёл из
            // детального обзора "показать все экраны", см. SceneCanvasPanel).
            for (Screen other : model.getCurrentScene().getScreens()) {
                cab = other.cabinetById(cabId);
                if (cab != null) {
                    break;
                }
            }
        }
        // Скрытый ("удалённый") кабинет не должен становиться частью НОВОЙ
        // цепочки — ни началом, ни продолжением уже строящейся.
        if (cab == null || cab.isHidden()) {
            return;
        }
        if (!building) {
            // Клик по кабинету, когда ничего ещё не строится — начинаем новую
            // цепочку сразу для текущей выбранной фазы/порта, без отдельного
            // клика по кнопке фазы/порта (та теперь только выбирает цель).
            if (starter == null) {
                return;
            }
            Consumer<List<String>> handler = starter.startChainFor(cabId);
            if (handler == null) {
                return; // например, кабинет уже прописан в другой цепочке
            }
            commitHandler = handler;
            activeIds.clear();
            building = true;
        }
        if (!activeIds.contains(cabId)) {
            activeIds.add(cabId);
        }
        cursorRow = cab.getRowIndex();
        cursorCol = cab.getColIndex();
        onChange.run();
    }

    /** ПКМ по кабинету ПОКА строится цепочка — убирает его из строящейся цепочки
     *  (не из уже сохранённых — для тех разрыв связи делает CanvasPanel отдельно). */
    public void removeFromActive(String cabId) {
        if (!building || cabId == null || !activeIds.remove(cabId)) {
            return;
        }
        onChange.run();
    }

    @Override
    public void cabinetHovered(String cabId) {
        hoveredCabId = cabId;
    }
}
