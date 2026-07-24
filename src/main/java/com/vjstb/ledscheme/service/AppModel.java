package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Центральное состояние приложения и все операции над данными. Держит рабочее
 * пространство, текущий выбор (проект/сцена/экран), режим схемы, стек «отменить»,
 * автосохранение и оповещение UI об изменениях.
 */
public class AppModel {

    public enum Mode { POWER, SIGNAL }

    public interface Listener {
        void onModelChanged();
    }

    private static final int UNDO_LIMIT = 100;

    private final WorkspaceStore store;
    private Workspace workspace;

    private Project currentProject;
    private Scene currentScene;
    private Screen currentScreen;

    private Mode mode = Mode.POWER;
    private int activePhase = 1;

    /** Снимок для «отменить»: состояние текущего экрана + цепочки его сцены на тот
     *  момент. Цепочки хранятся на уровне сцены, а не экрана (см. Task #78), поэтому
     *  снимок ОДНОГО экрана (как было раньше) больше не покрывает отмену
     *  добавления/удаления/разрыва цепочки — нужен отдельный снимок списков сцены. */
    private record UndoEntry(Screen screenSnapshot, List<PowerChain> powerChainsSnapshot,
                              List<SignalChain> signalChainsSnapshot) {
    }

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final List<Listener> listeners = new ArrayList<>();

    public AppModel(WorkspaceStore store) {
        this.store = store;
        this.workspace = store.load();
    }

    // ---- listeners ----

    public void addListener(Listener l) {
        listeners.add(l);
    }

    private void fireChanged() {
        for (Listener l : listeners) {
            l.onModelChanged();
        }
    }

    private void persist() {
        store.save(workspace);
    }

    private void changed() {
        persist();
        fireChanged();
    }

    // ---- getters ----

    public Workspace getWorkspace() {
        return workspace;
    }

    public WorkspaceStore getStore() {
        return store;
    }

    public List<CabinetType> getCabinetTypes() {
        return workspace.getCabinetTypes();
    }

    public List<Project> getProjects() {
        return workspace.getProjects();
    }

    public Project getCurrentProject() {
        return currentProject;
    }

    public Scene getCurrentScene() {
        return currentScene;
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public Mode getMode() {
        return mode;
    }

    public int getActivePhase() {
        return activePhase;
    }

    public CabinetType typeOf(Screen screen) {
        return screen == null ? null : workspace.cabinetTypeById(screen.getCabinetTypeId());
    }

    /** Базовая сводка по текущей сцене (прериг): суммарный вес/мощность экранов. */
    public SceneStats currentSceneStats() {
        return currentScene == null ? null : ScreenLogic.sceneStats(currentScene, workspace);
    }

    public boolean canUndo() {
        return currentScreen != null && !undoStack.isEmpty();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    // ---- selection ----

    public void selectProject(Project p) {
        currentProject = p;
        currentScene = null;
        currentScreen = null;
        undoStack.clear();
        fireChanged();
    }

    public void selectScene(Scene s) {
        currentScene = s;
        currentScreen = null;
        undoStack.clear();
        fireChanged();
    }

    public void selectScreen(Screen s) {
        currentScreen = s;
        undoStack.clear();
        fireChanged();
    }

    public void setMode(Mode m) {
        mode = m;
        fireChanged();
    }

    public void setActivePhase(int phase) {
        activePhase = phase;
        fireChanged();
    }

    // ---- cabinet library ----

    public CabinetType addCabinetType(CabinetType type) {
        requireUniqueName(type.getName(), null);
        workspace.getCabinetTypes().add(type);
        changed();
        return type;
    }

    public void updateCabinetType(CabinetType edited) {
        requireUniqueName(edited.getName(), edited.getId());
        CabinetType existing = workspace.cabinetTypeById(edited.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Кабинет не найден в библиотеке");
        }
        existing.setName(edited.getName());
        existing.setWidthMm(edited.getWidthMm());
        existing.setHeightMm(edited.getHeightMm());
        existing.setDepthMm(edited.getDepthMm());
        existing.setResolutionWidth(edited.getResolutionWidth());
        existing.setResolutionHeight(edited.getResolutionHeight());
        existing.setPowerConsumptionW(edited.getPowerConsumptionW());
        existing.setWeightKg(edited.getWeightKg());
        existing.setShape(edited.getShape());
        existing.setPowerConnectorsNeeded(edited.getPowerConnectorsNeeded());
        existing.setSignalConnectorsNeeded(edited.getSignalConnectorsNeeded());
        changed();
    }

    public void deleteCabinetType(String id) {
        if (isCabinetTypeInUse(id)) {
            throw new IllegalStateException("Кабинет используется на экране и не может быть удалён из библиотеки");
        }
        workspace.getCabinetTypes().removeIf(ct -> ct.getId().equals(id));
        changed();
    }

    public boolean isCabinetTypeInUse(String id) {
        for (Project p : workspace.getProjects()) {
            for (Scene s : p.getScenes()) {
                for (Screen scr : s.getScreens()) {
                    if (id.equals(scr.getCabinetTypeId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void requireUniqueName(String name, String ignoreId) {
        for (CabinetType ct : workspace.getCabinetTypes()) {
            if (ct.getName().equalsIgnoreCase(name) && !ct.getId().equals(ignoreId)) {
                throw new IllegalStateException("Кабинет с именем \"" + name + "\" уже есть в библиотеке");
            }
        }
    }

    /** Импорт: существующие по имени обновляются, новые добавляются. */
    public int importLibrary(File file) {
        List<CabinetType> incoming = store.importLibrary(file);
        for (CabinetType inc : incoming) {
            CabinetType match = null;
            for (CabinetType ct : workspace.getCabinetTypes()) {
                if (ct.getName().equalsIgnoreCase(inc.getName())) {
                    match = ct;
                    break;
                }
            }
            if (match != null) {
                inc.setId(match.getId());
                updateCabinetTypeNoFire(match, inc);
            } else {
                workspace.getCabinetTypes().add(inc);
            }
        }
        changed();
        return incoming.size();
    }

    private void updateCabinetTypeNoFire(CabinetType existing, CabinetType edited) {
        existing.setName(edited.getName());
        existing.setWidthMm(edited.getWidthMm());
        existing.setHeightMm(edited.getHeightMm());
        existing.setDepthMm(edited.getDepthMm());
        existing.setResolutionWidth(edited.getResolutionWidth());
        existing.setResolutionHeight(edited.getResolutionHeight());
        existing.setPowerConsumptionW(edited.getPowerConsumptionW());
        existing.setWeightKg(edited.getWeightKg());
        existing.setShape(edited.getShape());
        existing.setPowerConnectorsNeeded(edited.getPowerConnectorsNeeded());
        existing.setSignalConnectorsNeeded(edited.getSignalConnectorsNeeded());
    }

    public void exportLibrary(File file) {
        store.exportLibrary(workspace.getCabinetTypes(), file);
    }

    // ---- controller types (библиотека контроллеров, аналог SmartLCT) ----

    public ControllerType addControllerType(ControllerType type) {
        requireUniqueControllerName(type.getName(), null);
        workspace.getControllerTypes().add(type);
        changed();
        return type;
    }

    public void updateControllerType(ControllerType edited) {
        requireUniqueControllerName(edited.getName(), edited.getId());
        ControllerType existing = workspace.controllerTypeById(edited.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Контроллер не найден в библиотеке");
        }
        existing.setName(edited.getName());
        existing.setVendor(edited.getVendor());
        existing.setPortCount(edited.getPortCount());
        existing.setPortBandwidthMbps(edited.getPortBandwidthMbps());
        existing.setInputPortCount(edited.getInputPortCount());
        changed();
    }

    public SchemaCard addCardToController(ControllerType ct, String name, List<CardPort> ports) {
        SchemaCard card = new SchemaCard(name, ports);
        ct.getCards().add(card);
        changed();
        return card;
    }

    public void removeCardFromController(ControllerType ct, String cardId) {
        ct.getCards().removeIf(c -> c.getId().equals(cardId));
        changed();
    }

    public void deleteControllerType(String id) {
        if (isControllerTypeInUse(id)) {
            throw new IllegalStateException("Контроллер назначен экрану и не может быть удалён из библиотеки");
        }
        workspace.getControllerTypes().removeIf(ct -> ct.getId().equals(id));
        changed();
    }

    public boolean isControllerTypeInUse(String id) {
        for (Project p : workspace.getProjects()) {
            for (Scene s : p.getScenes()) {
                for (Screen scr : s.getScreens()) {
                    for (ControllerInstance ci : scr.getControllers()) {
                        if (id.equals(ci.getControllerTypeId())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void requireUniqueControllerName(String name, String ignoreId) {
        for (ControllerType ct : workspace.getControllerTypes()) {
            if (ct.getName().equalsIgnoreCase(name) && !ct.getId().equals(ignoreId)) {
                throw new IllegalStateException("Контроллер с именем \"" + name + "\" уже есть в библиотеке");
            }
        }
    }

    // ---- контроллеры, общие для СЦЕНЫ (не для одного экрана — см. Task #58) ----

    /** Сцена, содержащая указанный экран — контроллеры общие для всей сцены, у
     *  Screen нет обратной ссылки на Scene, поэтому ищем по всем проектам. */
    private Scene sceneContaining(Screen screen) {
        for (Project p : workspace.getProjects()) {
            for (Scene s : p.getScenes()) {
                if (s.getScreens().contains(screen)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Все контроллеры, добавленные к ЛЮБОМУ экрану этой сцены — хранятся физически
     *  под конкретными экранами (без миграции формата на диске), но используются
     *  как ОБЩИЙ для сцены пул: экраны одной сцены должны видеть все добавленные
     *  в сцену контроллеры, а не только те, что добавили именно на этом экране. */
    public List<ControllerInstance> controllersInScene(Scene scene) {
        List<ControllerInstance> all = new ArrayList<>();
        if (scene != null) {
            for (Screen s : scene.getScreens()) {
                all.addAll(s.getControllers());
            }
        }
        return all;
    }

    /** Добавляет сцене (физически — переданному экрану, см. {@link #controllersInScene})
     *  контроллер выбранного типа. */
    public ControllerInstance addControllerToScreen(Screen screen, String controllerTypeId) {
        if (workspace.controllerTypeById(controllerTypeId) == null) {
            throw new IllegalArgumentException("Тип контроллера не найден");
        }
        pushUndo();
        Scene scene = sceneContaining(screen);
        int n = controllersInScene(scene).size() + 1;
        ControllerInstance ci = new ControllerInstance(controllerTypeId, "Контроллер " + n);
        screen.getControllers().add(ci);
        changed();
        return ci;
    }

    /** Удаляет контроллер из сцены — ищет его среди ВСЕХ экранов сцены (физическое
     *  место хранения не важно вызывающему коду, контроллеры общие для сцены). */
    public void removeControllerFromScene(Scene scene, String controllerInstanceId) {
        if (scene == null) {
            return;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getControllers().stream().anyMatch(c -> c.getId().equals(controllerInstanceId))) {
                removeControllerFromScreen(s, controllerInstanceId);
                return;
            }
        }
    }

    public void removeControllerFromScreen(Screen screen, String controllerInstanceId) {
        pushUndo();
        screen.getControllers().removeIf(c -> c.getId().equals(controllerInstanceId));
        // Не оставляем висячую ссылку — если удаляемый контроллер был чьим-то
        // резервом (или чей-то резерв удаляли), связка снимается вместе с ним.
        // Резерв мог указывать на контроллер с ДРУГОГО экрана той же сцены.
        for (ControllerInstance ci : controllersInScene(sceneContaining(screen))) {
            if (controllerInstanceId.equals(ci.getBackupControllerId())) {
                ci.setBackupControllerId(null);
            }
        }
        changed();
    }

    /** Контроллер, которому принадлежит порт {@code port} (порты нумеруются подряд
     *  по всем контроллерам СЦЕНЫ экрана: 1..N1 — первый, N1+1..N1+N2 — второй и
     *  т.д.); null — контроллеров в сцене нет (порты вручную) или порт вне диапазона. */
    public ControllerInstance controllerForPort(Screen screen, int port) {
        int offset = 0;
        for (ControllerInstance ci : controllersInScene(sceneContaining(screen))) {
            ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            if (port > offset && port <= offset + count) {
                return ci;
            }
            offset += count;
        }
        return null;
    }

    /**
     * Назначает/снимает для контроллера {@code mainId} резервный контроллер
     * {@code backupId}, который целиком подхватывает сигнал при отказе основного —
     * один контроллер полностью дублирует все порты другого (в отличие от
     * {@link #setSignalBackupPortLink}, где резерв назначается отдельному порту).
     * Оба контроллера ищутся по всей СЦЕНЕ экрана, не только по нему самому.
     */
    public void setControllerBackupLink(Screen screen, String mainId, String backupId) {
        Scene scene = sceneContaining(screen);
        ControllerInstance main = controllerById(scene, mainId);
        if (main == null) {
            throw new IllegalArgumentException("Основной контроллер не найден");
        }
        if (backupId != null) {
            if (backupId.equals(mainId)) {
                throw new IllegalArgumentException("Резервный контроллер должен отличаться от основного");
            }
            ControllerInstance backup = controllerById(scene, backupId);
            if (backup == null) {
                throw new IllegalArgumentException("Резервный контроллер не найден");
            }
            if (backup.getBackupControllerId() != null) {
                throw new IllegalArgumentException("Этот контроллер уже резервирует другой — сначала снимите ту связку");
            }
            int offset = portOffsetOf(scene, backup);
            ControllerType backupType = workspace.controllerTypeById(backup.getControllerTypeId());
            int count = backupType != null ? backupType.effectivePortCount() : 0;
            // По всей сцене, а не только screen.getSignalChains() — цепочка на порту
            // резервируемого контроллера могла быть построена (и физически храниться,
            // до Task #78 — привязанной к экрану, где она была ЗАВЕРШЕНА) при
            // просмотре ЛЮБОГО экрана сцены, не только этого.
            for (SignalChain c : scene.getSignalChains()) {
                if (c.getPortNumber() != null && c.getPortNumber() > offset && c.getPortNumber() <= offset + count
                        && !c.getCabinetInstanceIds().isEmpty()) {
                    throw new IllegalArgumentException("У резервного контроллера уже есть собственная цепочка"
                            + " (порт " + c.getPortNumber() + ") — сначала очистите её");
                }
            }
        }
        pushUndo();
        main.setBackupControllerId(backupId);
        changed();
    }

    private ControllerInstance controllerById(Scene scene, String id) {
        for (ControllerInstance ci : controllersInScene(scene)) {
            if (ci.getId().equals(id)) {
                return ci;
            }
        }
        return null;
    }

    private int portOffsetOf(Scene scene, ControllerInstance target) {
        int offset = 0;
        for (ControllerInstance ci : controllersInScene(scene)) {
            if (ci == target) {
                return offset;
            }
            ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            offset += t != null ? t.effectivePortCount() : 0;
        }
        return offset;
    }

    /** Публичная версия {@link #portOffsetOf(Scene, ControllerInstance)} для UI —
     *  нужна, чтобы показать/расключать порты КОНКРЕТНОГО выбранного контроллера
     *  локальными номерами (1..N), а не суммарным сквозным номером по сцене (см.
     *  Task #73: суммирование портов по всем контроллерам сцены сильно дизориентирует). */
    public int portOffsetOf(Screen screen, ControllerInstance target) {
        return portOffsetOf(sceneContaining(screen), target);
    }

    /** true, если этот контроллер уже назначен чьим-то резервным (backupControllerId
     *  другого контроллера) — такой контроллер целиком отдан под подхват сигнала
     *  основного и не должен получать собственную независимую прописку портов. */
    public boolean isControllerReservedAsBackup(Screen screen, String controllerInstanceId) {
        for (ControllerInstance ci : controllersInScene(sceneContaining(screen))) {
            if (controllerInstanceId.equals(ci.getBackupControllerId())) {
                return true;
            }
        }
        return false;
    }

    /** Суммарное число портов сигнала: по контроллерам, добавленным в СЦЕНУ этого
     *  экрана (если они есть), иначе — по вручную заданному signalPortCount. */
    public int effectiveSignalPortCount(Screen screen) {
        List<ControllerInstance> all = controllersInScene(sceneContaining(screen));
        if (all.isEmpty()) {
            return screen.getSignalPortCount();
        }
        int total = 0;
        for (ControllerInstance ci : all) {
            ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            if (t != null) {
                total += t.effectivePortCount();
            }
        }
        return Math.max(total, 1);
    }

    // ---- projects ----

    public Project addProject(String name) {
        Project p = new Project(name);
        workspace.getProjects().add(p);
        changed();
        return p;
    }

    public void deleteProject(Project p) {
        workspace.getProjects().remove(p);
        if (currentProject == p) {
            currentProject = null;
            currentScene = null;
            currentScreen = null;
            undoStack.clear();
        }
        changed();
    }

    // ---- scenes ----

    public Scene addScene(String name) {
        if (currentProject == null) {
            throw new IllegalStateException("Не выбран проект");
        }
        Scene s = new Scene(name);
        s.setOrderIndex(currentProject.getScenes().size());
        currentProject.getScenes().add(s);
        currentProject.setUpdatedAt(System.currentTimeMillis());
        changed();
        return s;
    }

    public void deleteScene(Scene s) {
        if (currentProject == null) {
            return;
        }
        currentProject.getScenes().remove(s);
        if (currentScene == s) {
            currentScene = null;
            currentScreen = null;
            undoStack.clear();
        }
        changed();
    }

    // ---- screens ----

    /**
     * Добавляет экран, автоматически размещая его правее уже существующих на сцене
     * (без наложения) — по умолчанию новые экраны иначе оказались бы все в (0,0).
     */
    public Screen addScreenAutoPosition(String name, String cabinetTypeId, int rows, int cols) {
        double[] pos = suggestedNextPosition(cabinetTypeId, cols);
        return addScreen(name, cabinetTypeId, rows, cols, pos[0], pos[1]);
    }

    /** Предлагаемая позиция (X, Y мм) для нового экрана — правее уже существующих
     *  на текущей сцене, без наложения. Используется для предзаполнения диалога
     *  создания экрана. */
    public double[] suggestedNextPosition(String cabinetTypeId, int cols) {
        if (currentScene == null || currentScene.getScreens().isEmpty()) {
            return new double[]{0, 0};
        }
        double gapMm = 200;
        double maxRight = 0;
        for (Screen s : currentScene.getScreens()) {
            CabinetType t = workspace.cabinetTypeById(s.getCabinetTypeId());
            if (t == null) {
                continue;
            }
            double right = s.getPosXMm() + s.getCols() * t.getWidthMm();
            maxRight = Math.max(maxRight, right);
        }
        return new double[]{maxRight + gapMm, 0};
    }

    public Screen addScreen(String name, String cabinetTypeId, int rows, int cols, double posX, double posY) {
        if (currentScene == null) {
            throw new IllegalStateException("Не выбрана сцена");
        }
        if (workspace.cabinetTypeById(cabinetTypeId) == null) {
            throw new IllegalArgumentException("Не выбран тип кабинета");
        }
        Screen scr = new Screen();
        scr.setName(name);
        scr.setCabinetTypeId(cabinetTypeId);
        scr.setRows(rows);
        scr.setCols(cols);
        scr.setPosXMm(posX);
        scr.setPosYMm(posY);
        ScreenLogic.buildGrid(scr);
        scr.setRiggingPointsCount(ScreenLogic.suggestRiggingPoints(scr));
        currentScene.getScreens().add(scr);
        changed();
        return scr;
    }

    public void updateScreenGrid(Screen screen, String name, String cabinetTypeId, int rows, int cols) {
        if (workspace.cabinetTypeById(cabinetTypeId) == null) {
            throw new IllegalArgumentException("Не выбран тип кабинета");
        }
        screen.setName(name);
        screen.setCabinetTypeId(cabinetTypeId);
        ScreenLogic.resizeGrid(screen, rows, cols, sceneContaining(screen));
        undoStack.clear(); // id кабинетов могли измениться — снимки недействительны
        changed();
    }

    public void updateScreenPosition(Screen screen, double posX, double posY) {
        pushUndo();
        screen.setPosXMm(posX);
        screen.setPosYMm(posY);
        changed();
    }

    /** Заготовка под расчёт точек подвеса: способ монтажа + вручную вводимое кол-во/заметки. */
    public void updateScreenMount(Screen screen, ScreenMountType mountType, int riggingPointsCount, String riggingNotes) {
        pushUndo();
        screen.setMountType(mountType);
        screen.setRiggingPointsCount(Math.max(0, riggingPointsCount));
        screen.setRiggingNotes(riggingNotes);
        changed();
    }

    /** Герцовка/глубина цвета контента экрана — влияют на реальную ёмкость порта
     *  контроллера в пикселях (см. {@link ControllerType#maxPixelsFor}). */
    public void updateScreenSignalSpec(Screen screen, int refreshRateHz, int colorBitDepth) {
        if (refreshRateHz <= 0) {
            throw new IllegalArgumentException("Герцовка должна быть больше 0");
        }
        if (colorBitDepth <= 0) {
            throw new IllegalArgumentException("Глубина цвета должна быть больше 0");
        }
        pushUndo();
        screen.setRefreshRateHz(refreshRateHz);
        screen.setColorBitDepth(colorBitDepth);
        changed();
    }

    /** Переставляет все экраны текущей сцены в ряд без наложения (по X, Y=0). */
    /**
     * Расставляет все экраны сцены в один ряд слева направо без наложения,
     * выравнивая их по нижнему краю (по полу) — как реальные экраны разной
     * высоты, стоящие рядом на одном уровне. Не проходит через pushUndo():
     * это массовая перестановка всей сцены, а не изменение одного экрана.
     */
    public void autoArrangeScreensInScene() {
        if (currentScene == null) {
            return;
        }
        double gapMm = 200;
        double maxHeight = 0;
        for (Screen s : currentScene.getScreens()) {
            CabinetType t = workspace.cabinetTypeById(s.getCabinetTypeId());
            if (t != null) {
                maxHeight = Math.max(maxHeight, s.getRows() * t.getHeightMm());
            }
        }
        double x = 0;
        for (Screen s : currentScene.getScreens()) {
            CabinetType t = workspace.cabinetTypeById(s.getCabinetTypeId());
            double h = t != null ? s.getRows() * t.getHeightMm() : 0;
            s.setPosXMm(x);
            s.setPosYMm(maxHeight - h);
            x += (t != null ? s.getCols() * t.getWidthMm() : 0) + gapMm;
        }
        changed();
    }

    /** true, если хотя бы два экрана текущей сцены перекрываются по прямоугольникам (мм). */
    public boolean currentSceneHasOverlap() {
        if (currentScene == null) {
            return false;
        }
        List<Screen> screens = currentScene.getScreens();
        for (int i = 0; i < screens.size(); i++) {
            CabinetType ti = workspace.cabinetTypeById(screens.get(i).getCabinetTypeId());
            if (ti == null) {
                continue;
            }
            Screen a = screens.get(i);
            double aw = a.getCols() * ti.getWidthMm();
            double ah = a.getRows() * ti.getHeightMm();
            for (int j = i + 1; j < screens.size(); j++) {
                Screen b = screens.get(j);
                CabinetType tj = workspace.cabinetTypeById(b.getCabinetTypeId());
                if (tj == null) {
                    continue;
                }
                double bw = b.getCols() * tj.getWidthMm();
                double bh = b.getRows() * tj.getHeightMm();
                boolean disjoint = a.getPosXMm() + aw <= b.getPosXMm()
                        || b.getPosXMm() + bw <= a.getPosXMm()
                        || a.getPosYMm() + ah <= b.getPosYMm()
                        || b.getPosYMm() + bh <= a.getPosYMm();
                if (!disjoint) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- общая схема площадки (питание/сигнал): узлы-оборудование + связи, yEd-подобный редактор ----
    // Схема живёт на уровне СЦЕНЫ (площадки), а не экрана, поэтому не проходит через
    // pushUndo()/undoStack (тот снимает состояние только текущего экрана) — как и
    // autoArrangeScreensInScene() выше.

    public List<SchemaNode> schemaNodesForCurrentScene(SchemaMode mode) {
        if (currentScene == null) {
            return List.of();
        }
        List<SchemaNode> result = new ArrayList<>();
        for (SchemaNode n : currentScene.getSchemaNodes()) {
            if (n.getMode() == mode) {
                result.add(n);
            }
        }
        return result;
    }

    public List<SchemaEdge> schemaEdgesForCurrentScene(SchemaMode mode) {
        if (currentScene == null) {
            return List.of();
        }
        List<SchemaEdge> result = new ArrayList<>();
        for (SchemaEdge e : currentScene.getSchemaEdges()) {
            if (e.getMode() == mode) {
                result.add(e);
            }
        }
        return result;
    }

    public SchemaNode addSchemaNode(SchemaMode mode, SchemaNodeType type, String label, double x, double y,
                                     String screenRefId) {
        if (currentScene == null) {
            throw new IllegalStateException("Не выбрана сцена");
        }
        SchemaNode n = new SchemaNode(mode, type, label, x, y, screenRefId);
        currentScene.getSchemaNodes().add(n);
        changed();
        return n;
    }

    /** Перемещение узла (драг мышью) — вызывать один раз по отпусканию кнопки, не на каждый кадр. */
    public void moveSchemaNode(SchemaNode node, double x, double y) {
        node.setX(x);
        node.setY(y);
        changed();
    }

    private static final double SCHEMA_NODE_MIN_WIDTH = 110;
    private static final double SCHEMA_NODE_MIN_HEIGHT = 44;

    /** Изменение размера узла (драг за угол) — вызывать один раз по отпусканию кнопки. */
    public void resizeSchemaNode(SchemaNode node, double width, double height) {
        node.setWidth(Math.max(SCHEMA_NODE_MIN_WIDTH, width));
        node.setHeight(Math.max(SCHEMA_NODE_MIN_HEIGHT, height));
        changed();
    }

    /** Геометрия построчной отрисовки гнёзд разъёмов — те же числа, что и в
     *  SchemaCanvasPanel.computeSocketRects, продублированы здесь намеренно (модель
     *  не должна зависеть от UI-класса) для расчёта минимальной высоты под все порты. */
    private static final int PORT_ROW_H = 13;
    private static final int PORT_ROWS_TOP_OFFSET = 34;
    private static final int PORT_ROWS_BOTTOM_PAD = 6;

    /** Растягивает высоту узла так, чтобы были видны ВСЕ его порты (карты сигнала
     *  или разъёмы питания), не обрезаясь в «+N ещё» — вызывается при создании узла
     *  из пресета и при добавлении карты/разъёма к уже существующему узлу. Только
     *  РАСТЯГИВАЕТ, когда узел ниже нужного — не уменьшает то, что пользователь уже
     *  подстроил вручную крупнее необходимого. */
    public void autoFitNodeToPorts(SchemaNode node) {
        int portCount = 0;
        for (SchemaCard c : node.getCards()) {
            portCount += c.getPorts().size();
        }
        portCount += node.getPowerConnectors().size();
        if (portCount == 0) {
            return;
        }
        double needed = PORT_ROWS_TOP_OFFSET + portCount * PORT_ROW_H + PORT_ROWS_BOTTOM_PAD;
        if (node.getHeight() < needed) {
            node.setHeight(needed);
        }
    }

    public void updateSchemaNode(SchemaNode node, String label, SchemaNodeType type, String screenRefId) {
        node.setLabel(label);
        node.setType(type);
        node.setScreenRefId(screenRefId);
        changed();
    }

    public void deleteSchemaNode(SchemaNode node) {
        if (currentScene == null) {
            return;
        }
        currentScene.getSchemaNodes().remove(node);
        currentScene.getSchemaEdges().removeIf(e ->
                node.getId().equals(e.getFromNodeId()) || node.getId().equals(e.getToNodeId()));
        changed();
    }

    /**
     * Добавляет связь между узлами. Между одной и той же парой узлов может быть
     * несколько связей разного типа/цвета (например, отдельно силовая линия и
     * отдельно резервная) — они не дедуплицируются, каждый клик «Соединение»
     * создаёт новую связь; на холсте параллельные связи разносятся визуально.
     */
    public SchemaEdge addSchemaEdge(SchemaMode mode, String fromNodeId, String toNodeId, String label) {
        return addSchemaEdge(mode, fromNodeId, null, toNodeId, null, label);
    }

    /** То же самое, но привязано к конкретным гнёздам (CardPort) на каждом узле —
     *  используется, когда включена настройка «коммутация через гнёзда разъёмов»:
     *  тогда связь на схеме показывает, какой именно разъём с каким соединён,
     *  а не просто «узел с узлом». fromPortId/toPortId — null для обычного соединения. */
    public SchemaEdge addSchemaEdge(SchemaMode mode, String fromNodeId, String fromPortId,
                                     String toNodeId, String toPortId, String label) {
        if (currentScene == null) {
            throw new IllegalStateException("Не выбрана сцена");
        }
        if (fromNodeId.equals(toNodeId)) {
            throw new IllegalArgumentException("Нельзя соединить узел сам с собой");
        }
        SchemaEdge edge = new SchemaEdge(mode, fromNodeId, toNodeId, label);
        edge.setFromPortId(fromPortId);
        edge.setToPortId(toPortId);
        currentScene.getSchemaEdges().add(edge);
        changed();
        return edge;
    }

    public void updateSchemaEdgeLabel(SchemaEdge edge, String label) {
        edge.setLabel(label);
        edge.setWireCount(null);
        edge.setWireType(null);
        edge.setLengthM(null);
        changed();
    }

    /** Структурированная подпись связи «N×тип» (+ метраж для питания) — коммутация,
     *  которую обозначает стрелка. Такие связи учитываются в спецификации на «Выводе». */
    public void updateSchemaEdgeWire(SchemaEdge edge, int count, String wireType, Double lengthM) {
        if (count <= 0) {
            throw new IllegalArgumentException("Количество линий должно быть больше 0");
        }
        if (wireType == null || wireType.isBlank()) {
            throw new IllegalArgumentException("Укажите тип линии");
        }
        edge.setWireCount(count);
        edge.setWireType(wireType.trim());
        edge.setLengthM(lengthM != null && lengthM > 0 ? lengthM : null);
        edge.setLabel(edge.displayLabel());
        changed();
    }

    public void deleteSchemaEdge(SchemaEdge edge) {
        if (currentScene == null) {
            return;
        }
        currentScene.getSchemaEdges().remove(edge);
        changed();
    }

    public void clearSchema(SchemaMode mode) {
        if (currentScene == null) {
            return;
        }
        currentScene.getSchemaNodes().removeIf(n -> n.getMode() == mode);
        currentScene.getSchemaEdges().removeIf(e -> e.getMode() == mode);
        changed();
    }

    // ---- карты ввода/вывода узла (медиасерверы/видеопроцессоры) ----

    public SchemaCard addCardToNode(SchemaNode node, String name, List<CardPort> ports) {
        SchemaCard card = new SchemaCard(name, ports);
        node.getCards().add(card);
        autoFitNodeToPorts(node);
        changed();
        return card;
    }

    public void removeCardFromNode(SchemaNode node, String cardId) {
        node.getCards().removeIf(c -> c.getId().equals(cardId));
        changed();
    }

    // ---- разъёмы питания узла (щиты/дистрибьюторы и т.п., схема ПИТАНИЯ) ----

    public CardPort addPowerConnectorToNode(SchemaNode node, String connectorType, PortDirection direction, int count) {
        CardPort port = new CardPort(connectorType, direction, count);
        node.getPowerConnectors().add(port);
        autoFitNodeToPorts(node);
        changed();
        return port;
    }

    public void removePowerConnectorFromNode(SchemaNode node, String portId) {
        node.getPowerConnectors().removeIf(p -> p.getId().equals(portId));
        changed();
    }

    // ---- пресеты оборудования (библиотека, для быстрой вставки узлов схемы) ----

    public List<EquipmentPreset> getEquipmentPresets() {
        return workspace.getEquipmentPresets();
    }

    public List<EquipmentPreset> presetsForCategory(SchemaMode mode, SchemaNodeType category) {
        List<EquipmentPreset> result = new ArrayList<>();
        for (EquipmentPreset p : workspace.getEquipmentPresets()) {
            if (p.getMode() == mode && p.getCategory() == category) {
                result.add(p);
            }
        }
        return result;
    }

    public EquipmentPreset addEquipmentPreset(SchemaMode mode, SchemaNodeType category, String name,
                                               String description, List<SchemaCard> cards) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Укажите название пресета");
        }
        EquipmentPreset preset = new EquipmentPreset(mode, category, trimmed,
                description == null ? "" : description.trim());
        if (cards != null) {
            for (SchemaCard c : cards) {
                preset.getCards().add(c.copy());
            }
        }
        workspace.getEquipmentPresets().add(preset);
        changed();
        return preset;
    }

    public void updateEquipmentPreset(EquipmentPreset preset, SchemaMode mode, SchemaNodeType category, String name,
                                       String description) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Укажите название пресета");
        }
        preset.setMode(mode);
        preset.setCategory(category);
        preset.setName(trimmed);
        preset.setDescription(description == null ? "" : description.trim());
        changed();
    }

    public void deleteEquipmentPreset(EquipmentPreset preset) {
        workspace.getEquipmentPresets().remove(preset);
        changed();
    }

    public SchemaCard addCardToPreset(EquipmentPreset preset, String name, List<CardPort> ports) {
        SchemaCard card = new SchemaCard(name, ports);
        preset.getCards().add(card);
        changed();
        return card;
    }

    public void removeCardFromPreset(EquipmentPreset preset, String cardId) {
        preset.getCards().removeIf(c -> c.getId().equals(cardId));
        changed();
    }

    public CardPort addPowerConnectorToPreset(EquipmentPreset preset, String connectorType, PortDirection direction, int count) {
        CardPort port = new CardPort(connectorType, direction, count);
        preset.getPowerConnectors().add(port);
        changed();
        return port;
    }

    public void removePowerConnectorFromPreset(EquipmentPreset preset, String portId) {
        preset.getPowerConnectors().removeIf(p -> p.getId().equals(portId));
        changed();
    }

    /** Создаёт узел схемы из пресета: копирует название, карты и разъёмы питания в
     *  новый узел (для type == SCREEN пресеты не применяются — там имя/данные берутся
     *  из экрана). */
    public SchemaNode addSchemaNodeFromPreset(SchemaMode mode, EquipmentPreset preset, double x, double y) {
        SchemaNode node = addSchemaNode(mode, preset.getCategory(), preset.getName(), x, y, null);
        for (SchemaCard c : preset.getCards()) {
            node.getCards().add(duplicateCardWithFreshIds(c));
        }
        for (CardPort p : preset.getPowerConnectors()) {
            node.getPowerConnectors().add(duplicatePortWithFreshId(p));
        }
        autoFitNodeToPorts(node);
        changed();
        return node;
    }

    /** Как {@link #addSchemaNodeFromPreset}, но карты собираются из ШАБЛОНОВ
     *  пресета в ЯВНО заданном порядке (шаблон может повторяться в списке — так
     *  задаются несколько одинаковых карт в одном устройстве, см. Task #59), а не
     *  копируются один-в-один как есть. Порядок списка — это и порядок размещения/
     *  отрисовки карт в получившемся узле (см. Task #66: пользователь собирает
     *  этот список в AssembleCardsDialog двойным кликом/перетаскиванием карт
     *  из библиотеки, в любой нужной последовательности). templateIdsInOrder —
     *  id шаблонов (preset.getCards()[i].getId()), с повторами при дублях. */
    public SchemaNode addSchemaNodeFromPresetWithCardOrder(SchemaMode mode, EquipmentPreset preset,
                                                            double x, double y, List<String> templateIdsInOrder) {
        SchemaNode node = addSchemaNode(mode, preset.getCategory(), preset.getName(), x, y, null);
        for (String templateId : templateIdsInOrder) {
            for (SchemaCard template : preset.getCards()) {
                if (template.getId().equals(templateId)) {
                    node.getCards().add(duplicateCardWithFreshIds(template));
                    break;
                }
            }
        }
        for (CardPort p : preset.getPowerConnectors()) {
            node.getPowerConnectors().add(duplicatePortWithFreshId(p));
        }
        autoFitNodeToPorts(node);
        changed();
        return node;
    }

    /** Копия карты с НОВЫМИ id (у самой карты и у каждого её разъёма) — в отличие
     *  от {@link SchemaCard#copy()} (сохраняет id как есть — годится для "редактировать
     *  как новый" в библиотеке, где копия ЗАМЕНЯЕТ оригинал, а не сосуществует с ним),
     *  эта версия обязательна всякий раз, когда из ОДНОГО пресета/шаблона создаётся
     *  НЕСКОЛЬКО независимых узлов схемы (или несколько карт одного шаблона в одном
     *  узле): с одинаковыми id гнёзда разных физических блоков были бы неразличимы
     *  для коммутации через гнёзда (Task #52) и для подсчёта занятых/свободных линий
     *  гнезда (SchemaCanvasPanel.usedCount сравнивает по portId) — без этого лимит
     *  на гнездо одного блока (например "Проходная") ошибочно делился бы на ВСЕ
     *  узлы, созданные из того же пресета, вместо того чтобы считаться отдельно для
     *  каждого физического экземпляра. */
    private static SchemaCard duplicateCardWithFreshIds(SchemaCard template) {
        SchemaCard c = template.copy();
        c.setId(java.util.UUID.randomUUID().toString());
        for (CardPort p : c.getPorts()) {
            p.setId(java.util.UUID.randomUUID().toString());
        }
        return c;
    }

    /** Копия одиночного гнезда (разъём питания вне карты) с НОВЫМ id — см.
     *  {@link #duplicateCardWithFreshIds}, та же причина. */
    private static CardPort duplicatePortWithFreshId(CardPort template) {
        CardPort p = template.copy();
        p.setId(java.util.UUID.randomUUID().toString());
        return p;
    }

    // ---- канвасы компоновки контента (выходные кадры сигнала, сцена-scoped) ----

    public List<ContentCanvas> canvasesForCurrentScene() {
        return currentScene == null ? List.of() : currentScene.getCanvases();
    }

    public ContentCanvas addCanvas(String name, int widthPx, int heightPx) {
        if (currentScene == null) {
            throw new IllegalStateException("Не выбрана сцена");
        }
        ContentCanvas c = new ContentCanvas();
        c.setName(name);
        c.setWidthPx(Math.max(1, widthPx));
        c.setHeightPx(Math.max(1, heightPx));
        currentScene.getCanvases().add(c);
        changed();
        return c;
    }

    public void updateCanvas(ContentCanvas canvas, String name, int widthPx, int heightPx) {
        canvas.setName(name);
        canvas.setWidthPx(Math.max(1, widthPx));
        canvas.setHeightPx(Math.max(1, heightPx));
        changed();
    }

    public void deleteCanvas(ContentCanvas canvas) {
        if (currentScene == null) {
            return;
        }
        currentScene.getCanvases().remove(canvas);
        changed();
    }

    public CanvasPlacement addScreenToCanvas(ContentCanvas canvas, String screenId, int x, int y) {
        for (CanvasPlacement p : canvas.getPlacements()) {
            if (p.getScreenId().equals(screenId)) {
                return p; // уже размещён — не дублируем
            }
        }
        CanvasPlacement p = new CanvasPlacement(screenId, x, y);
        canvas.getPlacements().add(p);
        changed();
        return p;
    }

    public void movePlacement(CanvasPlacement placement, int x, int y) {
        placement.setX(x);
        placement.setY(y);
        changed();
    }

    public void removePlacement(ContentCanvas canvas, String placementId) {
        canvas.getPlacements().removeIf(p -> p.getId().equals(placementId));
        changed();
    }

    public void deleteScreen(Screen screen) {
        if (currentScene == null) {
            return;
        }
        currentScene.getScreens().remove(screen);
        if (currentScreen == screen) {
            currentScreen = null;
            undoStack.clear();
        }
        // узлы схемы, ссылавшиеся на удалённый экран, тоже теряют смысл
        List<String> orphanNodeIds = new ArrayList<>();
        for (SchemaNode n : currentScene.getSchemaNodes()) {
            if (screen.getId().equals(n.getScreenRefId())) {
                orphanNodeIds.add(n.getId());
            }
        }
        currentScene.getSchemaNodes().removeIf(n -> orphanNodeIds.contains(n.getId()));
        currentScene.getSchemaEdges().removeIf(e ->
                orphanNodeIds.contains(e.getFromNodeId()) || orphanNodeIds.contains(e.getToNodeId()));
        for (ContentCanvas c : currentScene.getCanvases()) {
            c.getPlacements().removeIf(p -> screen.getId().equals(p.getScreenId()));
        }
        changed();
    }

    // ---- cabinet edits ----

    public void toggleCabinetHidden(String cabId) {
        if (currentScreen == null) {
            return;
        }
        CabinetInstance cab = currentScreen.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        pushUndo();
        cab.setHidden(!cab.isHidden());
        changed();
    }

    /** Назначает конкретной ячейке отдельный тип кабинета (null — вернуть тип экрана по умолчанию). */
    public void setCabinetTypeOverride(String cabId, String cabinetTypeId) {
        if (currentScreen == null) {
            return;
        }
        CabinetInstance cab = currentScreen.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        if (cabinetTypeId != null && workspace.cabinetTypeById(cabinetTypeId) == null) {
            throw new IllegalArgumentException("Тип кабинета не найден в библиотеке");
        }
        pushUndo();
        cab.setCabinetTypeId(cabinetTypeId);
        changed();
    }

    /** Назначает конкретной ячейке отдельную форму (null — вернуть форму эффективного типа кабинета). */
    public void setCabinetShapeOverride(String cabId, com.vjstb.ledscheme.model.CabinetShape shape) {
        if (currentScreen == null) {
            return;
        }
        CabinetInstance cab = currentScreen.cabinetById(cabId);
        if (cab == null) {
            return;
        }
        pushUndo();
        cab.setShapeOverride(shape);
        changed();
    }

    // ---- chains ----

    public void addPowerChain(int phase, List<String> cabinetIds) {
        if (currentScreen == null || currentScene == null || cabinetIds.isEmpty()) {
            return;
        }
        // Как и сигнальная цепочка, силовая цепочка физически может продолжаться с
        // одного экрана на другой (общий ввод/проходной щит на смежные экраны) —
        // раньше проверка была строго по текущему экрану (validateCabinetIds), из-за
        // чего ЛЮБАЯ попытка завершить цепочку, зашедшую на другой экран, бросала
        // исключение и застревала (ChainInteractionController.finish() не ожидал
        // ошибки от commitHandler) — пользователь физически не мог завершить
        // построение (см. Task #64).
        validateCabinetIdsAcrossScene(cabinetIds);
        pushUndo();
        for (String cabId : cabinetIds) {
            CabinetInstance cab = cabinetInScene(cabId);
            if (cab != null) {
                cab.setPhase(phase);
            }
        }
        // Цепочка хранится на уровне СЦЕНЫ, а не конкретного экрана (см. Task #78,
        // независимый менеджер цепочек) — раньше привязка к "текущему на момент
        // завершения" экрану была источником повторяющихся багов: экран, где
        // цепочка физически НАЧИНАЛАСЬ, но не был активен при завершении, не видел
        // её вовсе (список цепочек, поиск по порту, отрисовка) — а теперь она
        // принадлежит сцене целиком, и каждый экран сам решает, какую её часть
        // рисовать, по факту наличия своих кабинетов в списке.
        currentScene.getPowerChains().add(new PowerChain(phase, cabinetIds));
        changed();
    }

    public void addSignalChain(Integer port, boolean backup, List<String> cabinetIds) {
        if (currentScreen == null || currentScene == null || cabinetIds.isEmpty()) {
            return;
        }
        // Проверка здесь, а не только в UI-колбэке (SignalStagePanel.onPortSelected) —
        // защита от любого другого пути вызова (сейчас или в будущем), а не только
        // от клика по кнопке порта. Порт, отданный под резерв другого порта, не
        // должен получать собственную ручную цепочку ни при каких обстоятельствах.
        if (port != null && isPortReservedAsBackup(currentScreen, port)) {
            throw new IllegalStateException("Порт " + port + " зарезервирован как резервный для другого порта"
                    + " и не может иметь собственную цепочку");
        }
        // Сигнальная цепочка (в отличие от питания) физически может продолжаться с
        // одного экрана на другой (общий даунлинк на смежные экраны) — поэтому
        // кабинет ищется по ВСЕЙ сцене, а не только на текущем экране.
        validateCabinetIdsAcrossScene(cabinetIds);
        pushUndo();
        // Если для порта уже есть цепочка-заглушка (создана setSignalBackupPortLink
        // только чтобы было куда сохранить резервный порт, кабинетов ещё нет) —
        // заполняем именно её, а не добавляем вторую запись того же порта: резерв
        // и реальная прокладка — один и тот же порт, а не два разных.
        SignalChain existing = port != null ? signalChainByPortInScene(currentScene, port, backup) : null;
        if (existing != null && existing.getCabinetInstanceIds().isEmpty()) {
            existing.setCabinetInstanceIds(new ArrayList<>(cabinetIds));
        } else {
            currentScene.getSignalChains().add(new SignalChain(port, backup, cabinetIds));
        }
        changed();
    }

    public void deletePowerChain(String chainId) {
        if (currentScene == null) {
            return;
        }
        pushUndo();
        currentScene.getPowerChains().removeIf(c -> c.getId().equals(chainId));
        changed();
    }

    public void deleteSignalChain(String chainId) {
        if (currentScene == null) {
            return;
        }
        pushUndo();
        currentScene.getSignalChains().removeIf(c -> c.getId().equals(chainId));
        changed();
    }

    /** Разрывает цепочку питания в указанном месте (между кабинетами linkIndex и
     *  linkIndex+1), не удаляя всю цепочку — вместо неё остаются одна или две
     *  цепочки по обе стороны разрыва (та же фаза). */
    public void splitPowerChainLink(String chainId, int linkIndex) {
        if (currentScene == null) {
            return;
        }
        PowerChain chain = currentScene.getPowerChains().stream()
                .filter(c -> c.getId().equals(chainId)).findFirst().orElse(null);
        if (chain == null) {
            return;
        }
        List<String> ids = chain.getCabinetInstanceIds();
        if (linkIndex < 0 || linkIndex >= ids.size() - 1) {
            return;
        }
        List<String> first = new ArrayList<>(ids.subList(0, linkIndex + 1));
        List<String> second = new ArrayList<>(ids.subList(linkIndex + 1, ids.size()));
        pushUndo();
        currentScene.getPowerChains().removeIf(c -> c.getId().equals(chainId));
        if (!first.isEmpty()) {
            currentScene.getPowerChains().add(new PowerChain(chain.getPhase(), first));
        }
        if (!second.isEmpty()) {
            currentScene.getPowerChains().add(new PowerChain(chain.getPhase(), second));
        }
        changed();
    }

    /** То же для цепочки сигнала (см. {@link #splitPowerChainLink}) — порт/бэкап
     *  сохраняются на обеих половинах. */
    public void splitSignalChainLink(String chainId, int linkIndex) {
        if (currentScene == null) {
            return;
        }
        SignalChain chain = currentScene.getSignalChains().stream()
                .filter(c -> c.getId().equals(chainId)).findFirst().orElse(null);
        if (chain == null) {
            return;
        }
        List<String> ids = chain.getCabinetInstanceIds();
        if (linkIndex < 0 || linkIndex >= ids.size() - 1) {
            return;
        }
        List<String> first = new ArrayList<>(ids.subList(0, linkIndex + 1));
        List<String> second = new ArrayList<>(ids.subList(linkIndex + 1, ids.size()));
        pushUndo();
        currentScene.getSignalChains().removeIf(c -> c.getId().equals(chainId));
        if (!first.isEmpty()) {
            currentScene.getSignalChains().add(new SignalChain(chain.getPortNumber(), chain.isBackup(), first));
        }
        if (!second.isEmpty()) {
            currentScene.getSignalChains().add(new SignalChain(chain.getPortNumber(), chain.isBackup(), second));
        }
        changed();
    }

    /**
     * Назначает/снимает для порта {@code port} резервный порт {@code backupPort}
     * (другой номер порта, который подхватит сигнал при отказе основного).
     * Если у порта ещё нет основной цепочки — создаётся пустая (только с портом),
     * чтобы было куда сохранить ссылку.
     */
    public void setSignalBackupPortLink(int port, Integer backupPort) {
        if (currentScreen == null || currentScene == null) {
            return;
        }
        if (backupPort != null && backupPort == port) {
            throw new IllegalArgumentException("Резервный порт должен отличаться от основного");
        }
        if (backupPort != null) {
            SignalChain backupMain = signalChainByPortInScene(currentScene, backupPort, false);
            if (backupMain != null && !backupMain.getCabinetInstanceIds().isEmpty()) {
                throw new IllegalArgumentException("Порт " + backupPort + " уже используется для собственной"
                        + " цепочки — сначала очистите её, чтобы отдать порт под резерв");
            }
        }
        pushUndo();
        SignalChain main = signalChainByPortInScene(currentScene, port, false);
        if (main == null) {
            main = new SignalChain(port, false, List.of());
            currentScene.getSignalChains().add(main);
        }
        main.setBackupPortNumber(backupPort);
        changed();
    }

    /** Как {@link Screen#signalChainByPort} раньше — но по ВСЕЙ сцене экрана, а не
     *  только по его собственному списку (цепочки теперь хранятся на сцене, см.
     *  Task #78). Публичная версия — для UI (порт-пикер, диалог резерва), которому
     *  нужен поиск по конкретному экрану, а не по currentScene напрямую. */
    public SignalChain signalChainByPort(Screen screen, int port, boolean backup) {
        return signalChainByPortInScene(sceneContaining(screen), port, backup);
    }

    private SignalChain signalChainByPortInScene(Scene scene, int port, boolean backup) {
        if (scene == null) {
            return null;
        }
        for (SignalChain c : scene.getSignalChains()) {
            if (c.getPortNumber() != null && c.getPortNumber() == port && c.isBackup() == backup) {
                return c;
            }
        }
        return null;
    }

    /** true, если этот порт уже назначен чьим-то резервным (backupPortNumber другого
     *  порта) — такой порт не должен получать собственную ручную цепочку: он целиком
     *  отдан под подхват сигнала основного порта, а не под независимый контент. */
    public boolean isPortReservedAsBackup(Screen screen, int port) {
        Scene scene = sceneContaining(screen);
        if (scene != null) {
            for (SignalChain c : scene.getSignalChains()) {
                if (c.getBackupPortNumber() != null && c.getBackupPortNumber() == port) {
                    return true;
                }
            }
        }
        // Порт также зарезервирован, если ЦЕЛИКОМ принадлежит контроллеру, который
        // сам назначен резервным для другого контроллера этого экрана.
        ControllerInstance owner = controllerForPort(screen, port);
        return owner != null && isControllerReservedAsBackup(screen, owner.getId());
    }

    /** Число активных резервных связок портов сцены этого экрана (для учёта в общей
     *  схеме сигнала — на каждую нужна отдельная витая пара между основным и
     *  резервным портом). */
    public int backupPortLinkCount(Screen screen) {
        Scene scene = sceneContaining(screen);
        if (scene == null) {
            return 0;
        }
        int n = 0;
        for (SignalChain c : scene.getSignalChains()) {
            if (c.getBackupPortNumber() != null) {
                n++;
            }
        }
        return n;
    }

    public void updateSignalPortCount(Screen screen, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Портов должно быть не меньше 1");
        }
        pushUndo();
        screen.setSignalPortCount(count);
        changed();
    }

    /** Очищает цепочки ТЕКУЩЕГО режима, которые физически затрагивают текущий
     *  экран (кнопка «Очистить цепочки…» — сформулирована как «на этом экране»).
     *  Цепочки теперь общие для сцены (см. Task #78), поэтому "весь список
     *  очистить" больше не то же самое, что "на этом экране" — фильтруем по
     *  касанию кабинетов текущего экрана, а не берём список целиком. Кросс-
     *  экранная цепочка, затрагивающая и другой экран, тоже удалится целиком —
     *  как и раньше, когда она физически "жила" на одном экране. */
    public void clearChainsOfMode() {
        if (currentScreen == null || currentScene == null) {
            return;
        }
        pushUndo();
        if (mode == Mode.POWER) {
            currentScene.getPowerChains().removeIf(c ->
                    c.getCabinetInstanceIds().stream().anyMatch(id -> currentScreen.cabinetById(id) != null));
        } else {
            currentScene.getSignalChains().removeIf(c ->
                    c.getCabinetInstanceIds().stream().anyMatch(id -> currentScreen.cabinetById(id) != null));
        }
        changed();
    }

    /** И силовая, и сигнальная цепочка могут физически продолжаться с одного экрана
     *  сцены на другой — кабинет ищется по всей сцене, а не только по текущему экрану. */
    private void validateCabinetIdsAcrossScene(List<String> ids) {
        for (String id : ids) {
            if (cabinetInScene(id) == null) {
                throw new IllegalArgumentException("Кабинет не найден в сцене");
            }
        }
    }

    private CabinetInstance cabinetInScene(String id) {
        if (currentScene == null) {
            return null;
        }
        for (Screen s : currentScene.getScreens()) {
            CabinetInstance c = s.cabinetById(id);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** true, если этот кабинет (на любом экране текущей сцены) уже используется
     *  какой-либо сигнальной цепочкой — в т.ч. цепочкой, которая физически
     *  продолжается сюда с ДРУГОГО экрана. Цепочки хранятся на уровне сцены (см.
     *  Task #78), поэтому это просто прямая проверка одного списка, а не обход
     *  всех экранов сцены, как было раньше. */
    public boolean isCabinetWiredForSignal(String cabinetId) {
        if (currentScene == null) {
            return false;
        }
        for (SignalChain c : currentScene.getSignalChains()) {
            if (c.getCabinetInstanceIds().contains(cabinetId)) {
                return true;
            }
        }
        return false;
    }

    /** Как {@link #isCabinetWiredForSignal}, но для силовой цепочки. */
    public boolean isCabinetWiredForPower(String cabinetId) {
        if (currentScene == null) {
            return false;
        }
        for (PowerChain c : currentScene.getPowerChains()) {
            if (c.getCabinetInstanceIds().contains(cabinetId)) {
                return true;
            }
        }
        return false;
    }

    /** Все цепочки питания сцены, которые физически затрагивают этот экран (т.е.
     *  среди их кабинетов есть хотя бы один кабинет этого экрана) — не только
     *  экраны, на которых цепочка "хранится", раз хранение больше не привязано к
     *  конкретному экрану (см. Task #78: цепочки — общий список сцены). */
    public List<PowerChain> powerChainsTouchingScreen(Screen screen) {
        List<PowerChain> result = new ArrayList<>();
        Scene scene = sceneContaining(screen);
        if (scene == null) {
            return result;
        }
        for (PowerChain c : scene.getPowerChains()) {
            if (c.getCabinetInstanceIds().stream().anyMatch(id -> screen.cabinetById(id) != null)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Как {@link #powerChainsTouchingScreen}, но для сигнала. */
    public List<SignalChain> signalChainsTouchingScreen(Screen screen) {
        List<SignalChain> result = new ArrayList<>();
        Scene scene = sceneContaining(screen);
        if (scene == null) {
            return result;
        }
        for (SignalChain c : scene.getSignalChains()) {
            if (c.getCabinetInstanceIds().stream().anyMatch(id -> screen.cabinetById(id) != null)) {
                result.add(c);
            }
        }
        return result;
    }

    // ---- undo ----

    private void pushUndo() {
        if (currentScreen == null) {
            return;
        }
        Scene scene = sceneContaining(currentScreen);
        List<PowerChain> pc = new ArrayList<>();
        List<SignalChain> sc = new ArrayList<>();
        if (scene != null) {
            for (PowerChain c : scene.getPowerChains()) {
                pc.add(c.copy());
            }
            for (SignalChain c : scene.getSignalChains()) {
                sc.add(c.copy());
            }
        }
        undoStack.push(new UndoEntry(ScreenLogic.snapshot(currentScreen), pc, sc));
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    public void undo() {
        if (currentScreen == null || undoStack.isEmpty()) {
            return;
        }
        UndoEntry snap = undoStack.pop();
        ScreenLogic.restore(currentScreen, snap.screenSnapshot());
        Scene scene = sceneContaining(currentScreen);
        if (scene != null) {
            scene.setPowerChains(snap.powerChainsSnapshot());
            scene.setSignalChains(snap.signalChainsSnapshot());
        }
        changed();
    }
}
