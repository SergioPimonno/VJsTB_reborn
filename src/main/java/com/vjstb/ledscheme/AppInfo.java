package com.vjstb.ledscheme;

/**
 * Общие сведения о приложении (версия, автор, ссылка на репозиторий) — единый
 * источник, чтобы меню «Настройки» и «Справка → О программе» не расходились
 * (раньше версия была отдельно захардкожена в диалоге «О программе» и со временем
 * устарела). Обновляется вручную при выпуске новой версии, синхронно с git-тегом
 * релиза (см. README/GitHub Releases).
 */
public final class AppInfo {

    public static final String VERSION = "2.0";
    public static final String AUTHOR = "SergioPimonno";
    public static final String REPOSITORY_URL = "https://github.com/SergioPimonno/VJsTB_reborn";
    public static final String NEW_ISSUE_URL = REPOSITORY_URL + "/issues/new";

    private AppInfo() {
    }
}
