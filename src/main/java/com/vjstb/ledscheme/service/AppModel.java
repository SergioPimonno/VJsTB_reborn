package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
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

    private final Deque<Screen> undoStack = new ArrayDeque<>();
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
        existing.setMaxPixelsPerPort(edited.getMaxPixelsPerPort());
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

    // ---- контроллеры, назначенные экрану ----

    /** Добавляет экран экрану контроллер выбранного типа. */
    public ControllerInstance addControllerToScreen(Screen screen, String controllerTypeId) {
        if (workspace.controllerTypeById(controllerTypeId) == null) {
            throw new IllegalArgumentException("Тип контроллера не найден");
        }
        pushUndo();
        int n = screen.getControllers().size() + 1;
        ControllerInstance ci = new ControllerInstance(controllerTypeId, "Контроллер " + n);
        screen.getControllers().add(ci);
        changed();
        return ci;
    }

    public void removeControllerFromScreen(Screen screen, String controllerInstanceId) {
        pushUndo();
        screen.getControllers().removeIf(c -> c.getId().equals(controllerInstanceId));
        changed();
    }

    /** Суммарное число портов сигнала: по назначенным контроллерам, если они есть,
     *  иначе — по вручную заданному signalPortCount (для экранов без контроллеров). */
    public int effectiveSignalPortCount(Screen screen) {
        if (screen.getControllers().isEmpty()) {
            return screen.getSignalPortCount();
        }
        int total = 0;
        for (ControllerInstance ci : screen.getControllers()) {
            ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            if (t != null) {
                total += t.getPortCount();
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
        double[] pos = nextFreePosition(cabinetTypeId, cols);
        return addScreen(name, cabinetTypeId, rows, cols, pos[0], pos[1]);
    }

    private double[] nextFreePosition(String cabinetTypeId, int cols) {
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
        ScreenLogic.resizeGrid(screen, rows, cols);
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

    public SchemaEdge addSchemaEdge(SchemaMode mode, String fromNodeId, String toNodeId, String label) {
        if (currentScene == null) {
            throw new IllegalStateException("Не выбрана сцена");
        }
        if (fromNodeId.equals(toNodeId)) {
            throw new IllegalArgumentException("Нельзя соединить узел сам с собой");
        }
        for (SchemaEdge e : currentScene.getSchemaEdges()) {
            if (e.getMode() == mode
                    && ((e.getFromNodeId().equals(fromNodeId) && e.getToNodeId().equals(toNodeId))
                        || (e.getFromNodeId().equals(toNodeId) && e.getToNodeId().equals(fromNodeId)))) {
                return e; // уже соединены — не дублируем
            }
        }
        SchemaEdge edge = new SchemaEdge(mode, fromNodeId, toNodeId, label);
        currentScene.getSchemaEdges().add(edge);
        changed();
        return edge;
    }

    public void updateSchemaEdgeLabel(SchemaEdge edge, String label) {
        edge.setLabel(label);
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

    // ---- chains ----

    public void addPowerChain(int phase, List<String> cabinetIds) {
        if (currentScreen == null || cabinetIds.isEmpty()) {
            return;
        }
        validateCabinetIds(cabinetIds);
        pushUndo();
        for (String cabId : cabinetIds) {
            CabinetInstance cab = currentScreen.cabinetById(cabId);
            if (cab != null) {
                cab.setPhase(phase);
            }
        }
        currentScreen.getPowerChains().add(new PowerChain(phase, cabinetIds));
        changed();
    }

    public void addSignalChain(Integer port, boolean backup, List<String> cabinetIds) {
        if (currentScreen == null || cabinetIds.isEmpty()) {
            return;
        }
        validateCabinetIds(cabinetIds);
        pushUndo();
        currentScreen.getSignalChains().add(new SignalChain(port, backup, cabinetIds));
        changed();
    }

    public void deletePowerChain(String chainId) {
        if (currentScreen == null) {
            return;
        }
        pushUndo();
        currentScreen.getPowerChains().removeIf(c -> c.getId().equals(chainId));
        changed();
    }

    public void deleteSignalChain(String chainId) {
        if (currentScreen == null) {
            return;
        }
        pushUndo();
        currentScreen.getSignalChains().removeIf(c -> c.getId().equals(chainId));
        changed();
    }

    /**
     * Переключает пометку «бэкап» для порта: бэкап-цепочка не требует расключения
     * кабинетов — она лишь декларирует, что у порта есть резерв. Возвращает
     * новое состояние (true — бэкап включён).
     */
    public boolean toggleSignalPortBackup(int port) {
        if (currentScreen == null) {
            return false;
        }
        pushUndo();
        SignalChain existing = currentScreen.signalChainByPort(port, true);
        boolean nowBackup;
        if (existing != null) {
            currentScreen.getSignalChains().remove(existing);
            nowBackup = false;
        } else {
            currentScreen.getSignalChains().add(new SignalChain(port, true, List.of()));
            nowBackup = true;
        }
        changed();
        return nowBackup;
    }

    /**
     * Назначает/снимает для порта {@code port} резервный порт {@code backupPort}
     * (другой номер порта, который подхватит сигнал при отказе основного).
     * Если у порта ещё нет основной цепочки — создаётся пустая (только с портом),
     * чтобы было куда сохранить ссылку.
     */
    public void setSignalBackupPortLink(int port, Integer backupPort) {
        if (currentScreen == null) {
            return;
        }
        if (backupPort != null && backupPort == port) {
            throw new IllegalArgumentException("Резервный порт должен отличаться от основного");
        }
        pushUndo();
        SignalChain main = currentScreen.signalChainByPort(port, false);
        if (main == null) {
            main = new SignalChain(port, false, List.of());
            currentScreen.getSignalChains().add(main);
        }
        main.setBackupPortNumber(backupPort);
        changed();
    }

    public void updateSignalPortCount(Screen screen, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Портов должно быть не меньше 1");
        }
        pushUndo();
        screen.setSignalPortCount(count);
        changed();
    }

    public void clearChainsOfMode() {
        if (currentScreen == null) {
            return;
        }
        pushUndo();
        if (mode == Mode.POWER) {
            currentScreen.getPowerChains().clear();
        } else {
            currentScreen.getSignalChains().clear();
        }
        changed();
    }

    private void validateCabinetIds(List<String> ids) {
        for (String id : ids) {
            if (currentScreen.cabinetById(id) == null) {
                throw new IllegalArgumentException("Кабинет не принадлежит этому экрану");
            }
        }
    }

    // ---- undo ----

    private void pushUndo() {
        if (currentScreen == null) {
            return;
        }
        undoStack.push(ScreenLogic.snapshot(currentScreen));
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    public void undo() {
        if (currentScreen == null || undoStack.isEmpty()) {
            return;
        }
        Screen snap = undoStack.pop();
        ScreenLogic.restore(currentScreen, snap);
        changed();
    }
}
