package com.vjstb.ledscheme.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Держит текущие пользовательские настройки (персонализация + профили), сохраняет
 * на диск при каждом изменении (как AppModel — автосохранение) и оповещает слушателей
 * (UI), чтобы перерисовать элементы, зависящие от палитры/раскладки.
 */
public class SettingsManager {

    private final SettingsStore store;
    private AppSettings settings;
    private final List<Runnable> listeners = new ArrayList<>();

    public SettingsManager(SettingsStore store) {
        this.store = store;
        this.settings = store.load();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void fireChanged() {
        for (Runnable r : listeners) {
            r.run();
        }
    }

    private void persist() {
        store.save(settings);
        fireChanged();
    }

    public AppSettings getSettings() {
        return settings;
    }

    public UserProfile activeProfile() {
        for (UserProfile p : settings.getProfiles()) {
            if (p.getId().equals(settings.getActiveProfileId())) {
                return p;
            }
        }
        return settings.getProfiles().get(0);
    }

    public void setActiveProfile(String id) {
        settings.setActiveProfileId(id);
        persist();
    }

    public void setLibrarySyncGlobalSeq(long seq) {
        settings.setLibrarySyncGlobalSeq(seq);
        persist();
    }

    public void setAuthSession(String token, String username, String role) {
        settings.setAuthToken(token);
        settings.setAuthUsername(username);
        settings.setAuthRole(role);
        persist();
    }

    public void clearAuthSession() {
        settings.setAuthToken(null);
        settings.setAuthUsername(null);
        settings.setAuthRole(null);
        persist();
    }

    public UserProfile createProfile(String name) {
        UserProfile p = new UserProfile();
        p.setName(name);
        settings.getProfiles().add(p);
        settings.setActiveProfileId(p.getId());
        persist();
        return p;
    }

    public void renameActiveProfile(String name) {
        activeProfile().setName(name);
        persist();
    }

    /** Профилей всегда должен остаться хотя бы один — последний удалить нельзя. */
    public void deleteProfile(String id) {
        if (settings.getProfiles().size() <= 1) {
            return;
        }
        boolean wasActive = id.equals(settings.getActiveProfileId());
        settings.getProfiles().removeIf(p -> p.getId().equals(id));
        if (wasActive) {
            settings.setActiveProfileId(settings.getProfiles().get(0).getId());
        }
        persist();
    }

    public void updateActiveProfileColors(Integer phase1, Integer phase2, Integer phase3, Integer phaseNone,
                                           Integer accent, java.util.List<Integer> signalColors) {
        UserProfile p = activeProfile();
        p.setPhase1Color(phase1);
        p.setPhase2Color(phase2);
        p.setPhase3Color(phase3);
        p.setPhaseNoneColor(phaseNone);
        p.setAccentColor(accent);
        p.setSignalColors(signalColors);
        persist();
    }

    public void setPreviewWidgetEnabled(boolean enabled) {
        activeProfile().setPreviewWidgetEnabled(enabled);
        persist();
    }

    public void setCanvasSnapToCenter(boolean enabled) {
        activeProfile().setCanvasSnapToCenter(enabled);
        persist();
    }

    public void setSnapThresholdPx(int px) {
        activeProfile().setSnapThresholdPx(px);
        persist();
    }

    public void setSnapStrengthPercent(int percent) {
        activeProfile().setSnapStrengthPercent(percent);
        persist();
    }

    public void setSignalSocketWiringEnabled(boolean enabled) {
        activeProfile().setSignalSocketWiringEnabled(enabled);
        persist();
    }

    public void setPowerSocketWiringEnabled(boolean enabled) {
        activeProfile().setPowerSocketWiringEnabled(enabled);
        persist();
    }

    public void setSignalChainEndpointSocketsEnabled(boolean enabled) {
        activeProfile().setSignalChainEndpointSocketsEnabled(enabled);
        persist();
    }

    public void setPowerChainEndpointSocketsEnabled(boolean enabled) {
        activeProfile().setPowerChainEndpointSocketsEnabled(enabled);
        persist();
    }

    public void setSignalSchemaAutoPopulateEnabled(boolean enabled) {
        activeProfile().setSignalSchemaAutoPopulateEnabled(enabled);
        persist();
    }

    public void setPowerSchemaAutoPopulateEnabled(boolean enabled) {
        activeProfile().setPowerSchemaAutoPopulateEnabled(enabled);
        persist();
    }

    public void setFoolProofWiringEnabled(boolean enabled) {
        activeProfile().setFoolProofWiringEnabled(enabled);
        persist();
    }

    public void setSchemaScreensAsWiringDiagram(boolean enabled) {
        activeProfile().setSchemaScreensAsWiringDiagram(enabled);
        persist();
    }

    public void setSignalConnectorDisplayMode(ConnectorDisplayMode mode) {
        activeProfile().setSignalConnectorDisplayMode(mode);
        persist();
    }

    public void setPowerConnectorDisplayMode(ConnectorDisplayMode mode) {
        activeProfile().setPowerConnectorDisplayMode(mode);
        persist();
    }

    public void setConnectorsVertical(boolean vertical) {
        activeProfile().setConnectorsVertical(vertical);
        persist();
    }

    public void setLoadTrackingEnabled(boolean enabled) {
        activeProfile().setLoadTrackingEnabled(enabled);
        persist();
    }

    public void setPowerUnitKw(boolean enabled) {
        activeProfile().setPowerUnitKw(enabled);
        persist();
    }

    public void setMaskLogoImagePath(String path) {
        activeProfile().setMaskLogoImagePath(path);
        persist();
    }

    public void setDarkTheme(boolean dark) {
        activeProfile().setDarkTheme(dark);
        persist();
    }

    public void setLafStyle(String styleId) {
        activeProfile().setLafStyle(styleId);
        persist();
    }

    public void setFontFamily(String fontFamily) {
        activeProfile().setFontFamily(fontFamily);
        persist();
    }

    public KeyCombo bindingFor(HotkeyAction action) {
        return activeProfile().bindingFor(action);
    }

    public void setBinding(HotkeyAction action, KeyCombo combo) {
        activeProfile().getKeyBindings().put(action.getId(), combo);
        persist();
    }

    public void resetBinding(HotkeyAction action) {
        activeProfile().getKeyBindings().remove(action.getId());
        persist();
    }

    public boolean isOnboardingCompleted() {
        return settings.isOnboardingCompleted();
    }

    public void setOnboardingCompleted(boolean completed) {
        settings.setOnboardingCompleted(completed);
        persist();
    }

    public String getDismissedUpdateVersion() {
        return settings.getDismissedUpdateVersion();
    }

    public void setDismissedUpdateVersion(String version) {
        settings.setDismissedUpdateVersion(version);
        persist();
    }

    public double getLayoutProportion(String key, double defaultValue) {
        Double v = activeProfile().getLayout().get(key);
        return v != null ? v : defaultValue;
    }

    /** Сохраняет позицию разделителя без немедленного оповещения слушателей — вызывается
     *  часто во время перетаскивания, полноценный fireChanged() тут не нужен и вреден
     *  (спровоцировал бы каскад перерисовок на каждый пиксель перетаскивания). */
    public void setLayoutProportion(String key, double value) {
        activeProfile().getLayout().put(key, value);
        store.save(settings);
    }
}
