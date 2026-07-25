package com.vjstb.ledscheme;

/**
 * Общие сведения о приложении (версия) — используется механизмом обновления
 * (см. update/UpdateManager) для показа текущей версии в диалоге «Обновить
 * версию». Обновляется вручную при выпуске новой версии, синхронно с
 * git-тегом релиза (см. README/GitHub Releases).
 */
public final class AppInfo {

    public static final String VERSION = "1.3";

    private AppInfo() {
    }
}
