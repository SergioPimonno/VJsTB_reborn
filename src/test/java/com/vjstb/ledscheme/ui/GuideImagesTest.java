package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ContentSection;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Картинки руководства: каждая ссылка {@code guide:файл.png} во встроенном тексте указывает на настоящий файл
 * в ресурсах {@code /guide/}, а после подстановки адрес читается как картинка. Раздел про Планер обязателен.
 */
class GuideImagesTest {

    private static final Pattern REF = Pattern.compile("guide:([A-Za-z0-9_.-]+[.]png)");

    @Test
    void everyGuideImageReferencedInTheDefaultTextExistsAndDecodes() throws Exception {
        int refs = 0;
        for (ContentSection s : GuideDialog.defaultSections()) {
            Matcher m = REF.matcher(s.getBodyHtml());
            while (m.find()) {
                refs++;
                URL url = GuideDialog.class.getResource("/guide/" + m.group(1));
                assertNotNull(url, "нет ресурса /guide/" + m.group(1) + " (раздел «" + s.getTitle() + "»)");
                try (InputStream in = url.openStream()) {
                    assertNotNull(ImageIO.read(in), "не читается как картинка: " + m.group(1));
                }
            }
        }
        assertTrue(refs >= 5, "раздел Планера ссылается на свои иллюстрации, найдено ссылок: " + refs);
    }

    @Test
    void plannerSectionExists() {
        assertTrue(GuideDialog.defaultSections().stream().anyMatch(s -> "Планер пультовой".equals(s.getTitle())));
    }

    @Test
    void resolveReplacesTheSchemeWithAResourceUrlThatOpens() throws Exception {
        String out = GuideDialog.resolveGuideImages("<img src=\"guide:planner-deck.png\" width=\"500\">");
        assertFalse(out.contains("guide:"), out);
        Matcher m = Pattern.compile("src=\"([^\"]+)\"").matcher(out);
        assertTrue(m.find());
        try (InputStream in = new URL(m.group(1)).openStream()) {
            assertNotNull(ImageIO.read(in));
        }
        assertEquals("<b>без картинок</b>", GuideDialog.resolveGuideImages("<b>без картинок</b>"));
    }
}
