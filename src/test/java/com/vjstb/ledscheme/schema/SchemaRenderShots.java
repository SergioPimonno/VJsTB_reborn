package com.vjstb.ledscheme.schema;

import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SchemaRenderMode;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.ui.SchemaCanvasPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** SPIKE / генератор эталонных картинок (docs/schema-ports-rework/PLAN.md, задача
 *  T0.2) — НЕ регрессия. Рендерит {@link SchemaFixtures#buildScene} через {@link
 *  SchemaCanvasPanel#renderImage} для сигнала и питания, во всех 4 ориентациях
 *  ({@link NodeOrientation}) и обоих пресетах оформления ({@link SchemaStylePreset},
 *  этап 3 PLAN.md). Не требует дисплея (offscreen {@code BufferedImage}, тот же
 *  приём, что {@code SchemeExportTest}) — в отличие от {@code
 *  com.vjstb.ledscheme.ScenarioShotSpike}, которому нужен живой {@code MainFrame}.
 *
 * <p>Размер узлов в фикстуре зафиксирован под {@link NodeOrientation#RIGHT}
 * (автоподгон при её построении) — картинки для DOWN/LEFT/UP поэтому могут
 * показывать "+N ещё" там, где при пересчёте автоподгона под ЭТУ ориентацию места
 * хватило бы; это ожидаемо и не баг генератора — сам факт, что рендер не падает и
 * корректно показывает overflow вместо путаницы, и есть проверяемое поведение.
 *
 * <p>Координатор сохраняет картинки "до" вне репозитория перед началом этапа и
 * сравнивает с "после" по завершении — см. PLAN.md §5 "Точки остановки у
 * пользователя". Запуск вручную:
 * {@code mvn test -Dtest=SchemaRenderShots -Dschema.shots=true}
 * (папка — {@code -Dschema.shots.dir=...}, по умолчанию {@code target/schema-shots}). */
class SchemaRenderShots {

    private static final File OUT = new File(System.getProperty("schema.shots.dir", "target/schema-shots"));

    @Test
    void renderCurrentSchemaVariants() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("schema.shots"),
                "включается флагом -Dschema.shots=true (генератор эталонных картинок T0.2)");
        deleteRec(OUT);
        OUT.mkdirs();

        Path tmp = Files.createTempDirectory("schema-shots");
        AppModel model = SchemaFixtures.buildScene(tmp);

        for (SchemaStylePreset preset : SchemaStylePreset.values()) {
            for (NodeOrientation orientation : NodeOrientation.values()) {
                SettingsManager settings = new SettingsManager(new SettingsStore(
                        new File(tmp.toFile(), "settings-" + preset + "-" + orientation.name() + ".json")));
                settings.setSchemaStylePreset(preset);
                settings.setSignalDefaultOrientation(orientation);
                settings.setPowerDefaultOrientation(orientation);
                for (SchemaMode mode : SchemaMode.values()) {
                    SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, mode, settings);
                    double maxX = 0;
                    double maxY = 0;
                    for (SchemaNode n : model.getCurrentScene().getSchemaNodes()) {
                        if (n.getMode() == mode) {
                            maxX = Math.max(maxX, n.getX() + n.getWidth());
                            maxY = Math.max(maxY, n.getY() + n.getHeight());
                        }
                    }
                    BufferedImage img = canvas.renderImage((int) maxX + 60, (int) maxY + 60, true, 1.0);
                    File out = new File(OUT, "schema_" + mode + "_" + preset + "_" + orientation.name() + ".png");
                    ImageIO.write(img, "png", out);
                }
            }
        }
        // CLASSIC (T5.5, D16) не знает про ориентацию/пресеты — один снимок на режим,
        // для наглядного сравнения "как было" рядом с MODERN в пользовательском гиде (T7.4).
        SettingsManager classicSettings = new SettingsManager(new SettingsStore(
                new File(tmp.toFile(), "settings-classic.json")));
        classicSettings.setSchemaRenderMode(SchemaRenderMode.CLASSIC);
        for (SchemaMode mode : SchemaMode.values()) {
            SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, mode, classicSettings);
            double maxX = 0;
            double maxY = 0;
            for (SchemaNode n : model.getCurrentScene().getSchemaNodes()) {
                if (n.getMode() == mode) {
                    maxX = Math.max(maxX, n.getX() + n.getWidth());
                    maxY = Math.max(maxY, n.getY() + n.getHeight());
                }
            }
            BufferedImage img = canvas.renderImage((int) maxX + 60, (int) maxY + 60, true, 1.0);
            File out = new File(OUT, "schema_" + mode + "_CLASSIC.png");
            ImageIO.write(img, "png", out);
        }

        System.out.println("SHOTS WRITTEN TO " + OUT.getAbsolutePath());
    }

    private static void deleteRec(File f) {
        if (!f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRec(c);
            }
        }
        f.delete();
    }
}
