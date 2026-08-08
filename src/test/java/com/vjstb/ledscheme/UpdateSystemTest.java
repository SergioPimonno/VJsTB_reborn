package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Тесты механизма обновления (см. update.UpdateManager) — только формирование
 *  ссылок на ассеты релиза, без сети. Разбор манифеста версий переехал на сервер
 *  (см. update.VersionManifest, update.VersionManifestTest про isNewer()) — списка
 *  версий тут больше нет. */
class UpdateSystemTest {

    @Test
    void assetNamesMatchPublishedReleaseConvention() {
        // Совпадает с реально загруженными ассетами релиза v1.3 на GitHub
        // (led-scheme-v1.3.jar, LED-Scheme-Designer-v1.3-mac.dmg).
        String jarUrl = com.vjstb.ledscheme.update.UpdateManager.downloadUrlFor("v1.3");
        assertTrue(jarUrl.contains("v1.3/"), jarUrl);
        assertTrue(jarUrl.endsWith(".jar") || jarUrl.endsWith(".dmg"), jarUrl);
    }
}
