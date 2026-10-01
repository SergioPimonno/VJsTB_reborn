package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.AppInfo;
import org.junit.jupiter.api.Test;

/** Страница «Что нового» приветственного тура (запрос 2026-10-01) берёт текст из ресурса
 *  {@code whats-new.html}. Его легко забыть обновить при выпуске новой версии — тогда
 *  пользователи после обновления увидели бы список изменений прошлой версии. Тест
 *  привязан к {@link AppInfo#VERSION}: пока версию подняли, а ресурс не поправили, он
 *  красный (шаг релизного чеклиста — скилл release-client). */
class WhatsNewResourceTest {

    @Test
    void resourceExistsAndIsForTheCurrentVersion() {
        String html = OnboardingDialog.loadWhatsNewHtml();

        assertFalse(html.isBlank());
        assertTrue(html.contains("<!-- whats-new: " + AppInfo.VERSION + " -->"),
                "whats-new.html описывает не текущую версию " + AppInfo.VERSION
                        + " — обновите src/main/resources/whats-new.html");
    }
}
