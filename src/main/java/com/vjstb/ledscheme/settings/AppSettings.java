package com.vjstb.ledscheme.settings;

import java.util.ArrayList;
import java.util.List;

/** Корневой объект пользовательских настроек: список профилей + активный. */
public class AppSettings {

    private String activeProfileId;
    private List<UserProfile> profiles = new ArrayList<>();

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
}
