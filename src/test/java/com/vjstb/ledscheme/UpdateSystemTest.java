package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.update.VersionManifest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Тесты формата манифеста версий (см. VersionManifest) — только разбор текста,
 *  без сети (см. класс-джавадок VersionManifest про формат). */
class UpdateSystemTest {

    @Test
    void parsesWellFormedManifest() {
        String text = """
                # комментарий
                1.3|v1.3|yes|
                1.4|v1.4|yes|бета

                1.2|v1.2|no|снята с публикации
                """;
        List<VersionManifest.Entry> entries = VersionManifest.parse(text);
        assertEquals(3, entries.size());
        assertEquals("1.3", entries.get(0).version());
        assertEquals("v1.3", entries.get(0).releaseTag());
        assertTrue(entries.get(0).available());
        assertTrue(entries.get(1).available());
        assertEquals("бета", entries.get(1).notes());
        assertFalse(entries.get(2).available());
    }

    @Test
    void ignoresMalformedLines() {
        String text = """
                justtext
                1.3|v1.3|yes|
                |v1.5|yes|
                1.6||yes|
                """;
        List<VersionManifest.Entry> entries = VersionManifest.parse(text);
        assertEquals(1, entries.size());
        assertEquals("1.3", entries.get(0).version());
    }

    @Test
    void assetNamesMatchPublishedReleaseConvention() {
        // Совпадает с реально загруженными ассетами релиза v1.3 на GitHub
        // (led-scheme-v1.3.jar, LED-Scheme-Designer-v1.3-mac.dmg).
        String jarUrl = com.vjstb.ledscheme.update.UpdateManager.downloadUrlFor("v1.3");
        assertTrue(jarUrl.contains("v1.3/"), jarUrl);
        assertTrue(jarUrl.endsWith(".jar") || jarUrl.endsWith(".dmg"), jarUrl);
    }
}
