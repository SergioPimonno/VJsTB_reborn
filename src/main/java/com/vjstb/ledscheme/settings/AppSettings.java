package com.vjstb.ledscheme.settings;

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
    /** Курсор последней успешной синхронизации библиотеки с сервером (см.
     *  sync.LibrarySyncClient/AppModel.applyLibrarySyncItems) — 0 означает "ещё ни
     *  разу не синхронизировались", тогда сервер отдаёт всю библиотеку целиком. */
    private long librarySyncGlobalSeq = 0;
    /** Сессия входа на сервер (см. sync.AuthClient/ui.AccountDialog) — все три null,
     *  если пользователь не входил в аккаунт; нужна только для отправки предложений
     *  в общую библиотеку, чтение библиотеки анонимно и токена не требует. */
    private String authToken;
    private String authUsername;
    private String authRole;
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
}
