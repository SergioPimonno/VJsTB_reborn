package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code paintScheme}'s {@code showCabinetIndexLabels} parameter — сводные картинки
 *  масштаба площадки (см. {@link SchemeRenderer#renderScreensOverviewImage}, баг-репорт:
 *  "нумерацию кабинетов уберём") не рисуют подписи "строка,столбец" на кабинетах, а
 *  интерактивный холст прописи (остальные перегрузки {@code paintScheme}) — рисует, как
 *  раньше. */
class SchemeRendererCabinetIndexLabelTest {

    private static final int LABEL_COLOR = 0xc0c8d0;

    @Test
    void suppressingIndexLabelsRemovesTheLabelPixelsThatTheDefaultOverloadDraws(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        CabinetType t = new CabinetType();
        t.setName("P3 500x500");
        t.setWidthMm(500);
        t.setHeightMm(500);
        t.setResolutionWidth(128);
        t.setResolutionHeight(128);
        model.addCabinetType(t);
        Screen scr = model.addScreen("Экран 1", t.getId(), 1, 1, 0, 0);

        BufferedImage withLabels = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage withoutLabels = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        paint(withLabels, scr, t, model, true);
        paint(withoutLabels, scr, t, model, false);

        assertTrue(containsColor(withLabels, LABEL_COLOR),
                "контрольная перегрузка с подписями должна рисовать подпись строка,столбец");
        assertFalse(containsColor(withoutLabels, LABEL_COLOR),
                "showCabinetIndexLabels=false не должен оставлять пиксели подписи");
    }

    private static void paint(BufferedImage img, Screen scr, CabinetType t, AppModel model, boolean showLabels) {
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        SchemeRenderer.paintScheme(g2, scr, t, false, 180, 180, 0, 0, model.getWorkspace(),
                List.of(), List.of(), List.of(), false, showLabels);
        g2.dispose();
    }

    private static boolean containsColor(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) {
                    return true;
                }
            }
        }
        return false;
    }
}
