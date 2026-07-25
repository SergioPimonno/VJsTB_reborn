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
}
