package com.vjstb.ledscheme.settings;

import java.io.File;
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
    private final CredentialCipher credentialCipher;

    public SettingsManager(SettingsStore store) {
        this.store = store;
        this.settings = store.load();
        this.credentialCipher = new CredentialCipher(new File(store.directory(), "credential.key"));
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

    public void setAuthSession(String token, String username, String role, String teamName) {
        settings.setAuthToken(token);
        settings.setAuthUsername(username);
        settings.setAuthRole(role);
        settings.setAuthTeamName(teamName);
        persist();
    }

    public void clearAuthSession() {
        settings.setAuthToken(null);
        settings.setAuthUsername(null);
        settings.setAuthRole(null);
        settings.setAuthTeamName(null);
        persist();
    }

    /** Запомнить учётные данные для предзаполнения формы входа (не сессию — см.
     *  {@link AppSettings#getRememberedUsername}). */
    public void rememberCredentials(String username, String password) {
        settings.setRememberedUsername(username);
        settings.setRememberedPasswordEnc(credentialCipher.encrypt(password));
        persist();
    }

    public void forgetCredentials() {
        if (settings.getRememberedUsername() == null && settings.getRememberedPasswordEnc() == null) {
            return;
        }
        settings.setRememberedUsername(null);
        settings.setRememberedPasswordEnc(null);
        persist();
    }

    /** Расшифрованный запомненный пароль или {@code null}, если не запомнен/не расшифровался. */
    public String rememberedPassword() {
        String enc = settings.getRememberedPasswordEnc();
        return enc == null ? null : credentialCipher.decrypt(enc);
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

    public void setSchemaWireHops(boolean enabled) {
        activeProfile().setSchemaWireHops(enabled);
        persist();
    }

    public void setSchemaWireHopStyle(WireHopStyle style) {
        activeProfile().setSchemaWireHopStyle(style);
        persist();
    }

    public void setPreferencesMatrixView(boolean matrix) {
        activeProfile().setPreferencesMatrixView(matrix);
        persist();
    }

    public void setCabinetPaletteViewMode(CabinetPaletteViewMode mode) {
        activeProfile().setCabinetPaletteViewMode(mode);
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

    public void setSignalConnectorsVertical(boolean vertical) {
        activeProfile().setSignalConnectorsVertical(vertical);
        persist();
    }

    public void setPowerConnectorsVertical(boolean vertical) {
        activeProfile().setPowerConnectorsVertical(vertical);
        persist();
    }

    // ---- переработка гнёзд/связей общей схемы (docs/schema-ports-rework/PLAN.md,
    // задача T1.5) — новые настройки, UI пока не подключён (см. этап 3/4 PLAN.md).

    public void setSignalGroupDisplay(GroupDisplayMode mode) {
        activeProfile().setSignalGroupDisplay(mode);
        persist();
    }

    public void setPowerGroupDisplay(GroupDisplayMode mode) {
        activeProfile().setPowerGroupDisplay(mode);
        persist();
    }

    public void setSignalDefaultOrientation(com.vjstb.ledscheme.model.NodeOrientation orientation) {
        activeProfile().setSignalDefaultOrientation(orientation);
        persist();
    }

    public void setPowerDefaultOrientation(com.vjstb.ledscheme.model.NodeOrientation orientation) {
        activeProfile().setPowerDefaultOrientation(orientation);
        persist();
    }

    public void setNewEdgeRouteMode(com.vjstb.ledscheme.model.EdgeRouteMode mode) {
        activeProfile().setNewEdgeRouteMode(mode);
        persist();
    }

    public void setSchemaArrowPlacement(ArrowPlacement placement) {
        activeProfile().setSchemaArrowPlacement(placement);
        persist();
    }

    public void setOrthogonalEdgeEditing(boolean enabled) {
        activeProfile().setOrthogonalEdgeEditing(enabled);
        persist();
    }

    public void setSchemaStylePreset(SchemaStylePreset preset) {
        activeProfile().setSchemaStylePreset(preset);
        persist();
    }

    /** docs/schema-ports-rework/PLAN.md, задача T5.5, D16 — переключатель
     *  «старый способ рисования» общей схемы, глобально в профиле. */
    public void setSchemaRenderMode(SchemaRenderMode mode) {
        activeProfile().setSchemaRenderMode(mode);
        persist();
    }

    /** Длина уса связи общей схемы (px) — доводка T4.4 (пожелание пользователя
     *  2026-09-18), см. {@link UserProfile#getSchemaRouteStubPx()}. */
    public void setSchemaRouteStubPx(int px) {
        activeProfile().setSchemaRouteStubPx(px);
        persist();
    }

    /** Скорость прокрутки колесом боковой панели этапа «Сигнал» (px на единицу
     *  колеса), см. {@link UserProfile#getSignalSideScrollUnitPx()}. */
    public void setSignalSideScrollUnitPx(int px) {
        activeProfile().setSignalSideScrollUnitPx(px);
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

    public void setExportRootFolder(String path) {
        activeProfile().setExportRootFolder(path);
        persist();
    }

    public void setInspectorDocked(boolean docked) {
        activeProfile().setInspectorDocked(docked);
        persist();
    }

    public void setPowerSceneStatsEnabled(boolean enabled) {
        activeProfile().setPowerSceneStatsEnabled(enabled);
        persist();
    }

    public void setSignalSceneStatsEnabled(boolean enabled) {
        activeProfile().setSignalSceneStatsEnabled(enabled);
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

    public void setDocExportDpi(int dpi) {
        activeProfile().setDocExportDpi(dpi);
        persist();
    }

    /** Все параметры окна «Параметры экспорта» разом — одна запись профиля на диск. */
    public void setDocExportOptions(String format, int dpi, int quality) {
        activeProfile().setDocExportFormat(format);
        activeProfile().setDocExportDpi(dpi);
        activeProfile().setDocExportQuality(quality);
        persist();
    }

    public void setUiScalePercent(int percent) {
        activeProfile().setUiScalePercent(percent);
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

    public boolean isControllerLibraryMigrated() {
        return settings.isControllerLibraryMigrated();
    }

    public void setControllerLibraryMigrated(boolean migrated) {
        settings.setControllerLibraryMigrated(migrated);
        persist();
    }

    public String getDismissedUpdateVersion() {
        return settings.getDismissedUpdateVersion();
    }

    public void setDismissedUpdateVersion(String version) {
        settings.setDismissedUpdateVersion(version);
        persist();
    }

    public String getSyncServerUrlOverride() {
        return settings.getSyncServerUrlOverride();
    }

    public void setSyncServerUrlOverride(String url) {
        settings.setSyncServerUrlOverride(url);
        persist();
    }

    public String getArchiveFolder() {
        return settings.getArchiveFolder();
    }

    public void setArchiveFolder(String path) {
        settings.setArchiveFolder(path);
        persist();
    }

    /** Регистрирует цвет как «недавно использованный» для линий схемы/цепочек
     *  (см. {@code RecentColorsChooserPanel}) — вызывать ПОСЛЕ подтверждения
     *  выбора (OK диалога), не на каждый промежуточный клик по палитре. Повторный
     *  выбор того же цвета переносит его в начало списка, не дублирует запись;
     *  список ограничен {@code MAX_RECENT}. Персистентно (в отличие от прежнего
     *  чисто статического {@code RECENT} — баг-репорт: "палитру нужно сохранять
     *  между перезапусками", про цвета ЛИНИЙ соединений/цепочек расключения). */
    public void rememberRecentLineColor(int rgb) {
        List<Integer> recent = new ArrayList<>(activeProfile().getRecentLineColors());
        recent.removeIf(existing -> existing == rgb);
        recent.add(0, rgb);
        while (recent.size() > 24) {
            recent.remove(recent.size() - 1);
        }
        activeProfile().setRecentLineColors(recent);
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
