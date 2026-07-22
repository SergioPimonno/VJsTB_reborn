package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
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
    }

    public void exportLibrary(File file) {
        store.exportLibrary(workspace.getCabinetTypes(), file);
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

    public void deleteScreen(Screen screen) {
        if (currentScene == null) {
            return;
        }
        currentScene.getScreens().remove(screen);
        if (currentScreen == screen) {
            currentScreen = null;
            undoStack.clear();
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
