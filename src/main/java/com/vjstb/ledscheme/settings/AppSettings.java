package com.vjstb.ledscheme.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/** Корневой объект пользовательских настроек: список профилей + активный. */
public class AppSettings {

    private String activeProfileId;
    private List<UserProfile> profiles = new ArrayList<>();
    /** Показан ли уже приветственный тур при первом запуске (см. ui.OnboardingDialog) —
     *  false по умолчанию (в т.ч. для старых файлов настроек, сохранённых до появления
     *  этого поля — тур покажется один раз и им же). Доступен повторно из Настроек. */
    private boolean onboardingCompleted = false;
    /** Прошла ли одноразовая миграция библиотеки контроллеров (устаревший
     *  ControllerType) в EquipmentPreset (category == CONTROLLER, 2026-09-23) — см.
     *  ui.ControllerLibraryMigrationDialog/AppModel#migrateControllerLibraryToEquipmentPresets.
     *  false по умолчанию (в т.ч. для старых файлов настроек) — диалог миграции
     *  покажется при следующем запуске, если в workspace остались непустые
     *  controllerTypes/sharedControllerTypes; после успешного прогона (или если
     *  мигрировать оказалось нечего) ставится в true и диалог больше не появляется
     *  сам — доступен повторно из меню на случай ручного повторного запуска. */
    private boolean controllerLibraryMigrated = false;
    /** Курсор последней успешной синхронизации библиотеки с сервером (см.
     *  sync.LibrarySyncClient/AppModel.applyLibrarySyncItems) — 0 означает "ещё ни
     *  разу не синхронизировались", тогда сервер отдаёт всю библиотеку целиком. */
    private long librarySyncGlobalSeq = 0;
    /** Сессия входа на сервер (см. sync.AuthClient/ui.AccountDialog) — все null,
     *  если пользователь не входил в аккаунт; нужна только для отправки предложений
     *  в общую библиотеку, чтение библиотеки анонимно и токена не требует.
     *
     *  <p>{@link JsonIgnore} — по запросу пользователя (2026-09-25) сессия живёт только
     *  в памяти: после перезапуска приложения нужно войти заново (форма входа
     *  предзаполнена, если включено «Запомнить логин и пароль» — см.
     *  {@link #rememberedUsername}). Токен, оставшийся в settings.json от старых версий,
     *  при чтении игнорируется и при следующем сохранении из файла пропадает. */
    @JsonIgnore
    private String authToken;
    @JsonIgnore
    private String authUsername;
    @JsonIgnore
    private String authRole;
    /** Команда пользователя — только для отображения (см. AccountDialog), реальный
     *  контроль доступа к облачным проектам смотрит в БД на сервере при каждом
     *  запросе, не в это поле. {@code null} — команда не назначена администратором
     *  (в т.ч. у всех сессий, залогиненных ДО появления этого поля — обновится при
     *  следующем входе). */
    @JsonIgnore
    private String authTeamName;
    /** «Запомнить логин и пароль» в форме входа (ui.AccountDialog): логин открытым
     *  текстом, пароль — зашифрованным (см. {@link CredentialCipher}). Оба null — не
     *  запоминать. Запоминаются ТОЛЬКО учётные данные, не сама сессия. */
    private String rememberedUsername;
    private String rememberedPasswordEnc;
    /** Версия, для которой пользователь уже закрыл уведомление об обновлении (см.
     *  ui.UpdateNoticeDialog/App.checkForUpdatesInBackground) — не переспрашиваем
     *  снова про ЭТУ ЖЕ версию при следующих запусках, но уведомим про более новую,
     *  если она появится. null — ничего ещё не закрывали. */
    private String dismissedUpdateVersion;
    /** Ручной "мост синхронизации" (см. sync.LibrarySyncClient#resolveBaseUrl) -- если
     *  задан (непустая строка), все sync-клиенты ходят СЮДА вместо
     *  {@code LibrarySyncClient.DEFAULT_BASE_URL}. Нужен на сетях, где прямое
     *  подключение к серверу по IP:8443 блокируется (см. раздел "Мост синхронизации"
     *  на публичной веб-странице сервера) -- пользователь вписывает альтернативный
     *  адрес вручную в Настройках. {@code null}/пусто -- использовать адрес по
     *  умолчанию, как раньше. */
    private String syncServerUrlOverride;
    /** Папка локального архива проектов (см. ui.LocalArchiveDialog/store.LocalArchiveStore) —
     *  выбирается пользователем один раз; {@code null}/пусто — архив ещё ни разу не
     *  настраивался (диалог архива запросит папку при первом использовании). Один
     *  путь на всю программу (не на профиль) — архив не связан с персонализацией. */
    private String archiveFolder;

    public String getActiveProfileId() {
        return activeProfileId;
    }

    public void setActiveProfileId(String activeProfileId) {
        this.activeProfileId = activeProfileId;
    }

    public List<UserProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<UserProfile> profiles) {
        this.profiles = profiles;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }

    public boolean isControllerLibraryMigrated() {
        return controllerLibraryMigrated;
    }

    public void setControllerLibraryMigrated(boolean controllerLibraryMigrated) {
        this.controllerLibraryMigrated = controllerLibraryMigrated;
    }

    public long getLibrarySyncGlobalSeq() {
        return librarySyncGlobalSeq;
    }

    public void setLibrarySyncGlobalSeq(long librarySyncGlobalSeq) {
        this.librarySyncGlobalSeq = librarySyncGlobalSeq;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthRole() {
        return authRole;
    }

    public void setAuthRole(String authRole) {
        this.authRole = authRole;
    }

    public String getAuthTeamName() {
        return authTeamName;
    }

    public void setAuthTeamName(String authTeamName) {
        this.authTeamName = authTeamName;
    }

    public String getDismissedUpdateVersion() {
        return dismissedUpdateVersion;
    }

    public void setDismissedUpdateVersion(String dismissedUpdateVersion) {
        this.dismissedUpdateVersion = dismissedUpdateVersion;
    }

    public String getSyncServerUrlOverride() {
        return syncServerUrlOverride;
    }

    public void setSyncServerUrlOverride(String syncServerUrlOverride) {
        this.syncServerUrlOverride = syncServerUrlOverride;
    }

    public String getArchiveFolder() {
        return archiveFolder;
    }

    public void setArchiveFolder(String archiveFolder) {
        this.archiveFolder = archiveFolder;
    }

    public String getRememberedUsername() {
        return rememberedUsername;
    }

    public void setRememberedUsername(String rememberedUsername) {
        this.rememberedUsername = rememberedUsername;
    }

    public String getRememberedPasswordEnc() {
        return rememberedPasswordEnc;
    }

    public void setRememberedPasswordEnc(String rememberedPasswordEnc) {
        this.rememberedPasswordEnc = rememberedPasswordEnc;
    }
}
