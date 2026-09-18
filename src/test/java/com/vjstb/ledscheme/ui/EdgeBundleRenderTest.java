package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Point;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Разведка T6.2 (docs/schema-ports-rework/PLAN.md, реплика пользователя 2026-09-18,
 *  DIALOG.md): пользователь спросил, реализовано ли слияние нескольких связей в одно
 *  свёрнутое гнездо ("пучок", D8/T4.3 {@code EdgeBundles}) — если да, отдельный блок-
 *  «шина» (Ring) не нужен, это ровно тот же визуальный эффект. Проверка на самом
 *  простом случае: 3 связи в одну ПРИНУДИТЕЛЬНО свёрнутую группу разъёмов питания
 *  (3×CEE 16A) — до фикса все три должны сходиться в ОДНУ точку (это уже верно, т.к.
 *  свёрнутая группа — один пин), но БЕЗ подписи "×3" и без короткого общего ствола,
 *  т.к. {@link com.vjstb.ledscheme.service.schemalayout.EdgeBundles} нигде не
 *  вызывается из {@code SchemaCanvasPanel} (T4.3 сделан, T4.4 не подключил). */
class EdgeBundleRenderTest {

    @Test
    void threeEdgesIntoOneForciblyCollapsedGroupShareOnePinAndGetABundleLabel(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Шина", 400, 200, null);
        CardPort in = model.addPowerConnectorToNode(distro, "CEE 16A", PortDirection.IN, 3, 1, null);
        model.setGroupCollapsed(distro, in.getId(), Boolean.TRUE);

        SchemaNode s1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort o1 = model.addPowerConnectorToNode(s1, "CEE 16A", PortDirection.OUT, 1, 1, null);
        SchemaNode s2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "B", 0, 200, null);
        CardPort o2 = model.addPowerConnectorToNode(s2, "CEE 16A", PortDirection.OUT, 1, 1, null);
        SchemaNode s3 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "C", 0, 400, null);
        CardPort o3 = model.addPowerConnectorToNode(s3, "CEE 16A", PortDirection.OUT, 1, 1, null);

        model.addSchemaEdge(SchemaMode.POWER, s1.getId(), o1.getId(), distro.getId(), in.getId(), null);
        model.addSchemaEdge(SchemaMode.POWER, s2.getId(), o2.getId(), distro.getId(), in.getId(), null);
        SchemaEdge third = model.addSchemaEdge(SchemaMode.POWER, s3.getId(), o3.getId(), distro.getId(), in.getId(), null);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);

        Point p1 = canvas.socketPositionForTest(distro, in.getId(), model.getCurrentScene().getSchemaEdges().get(0));
        Point p3 = canvas.socketPositionForTest(distro, in.getId(), third);
        assertEquals(p1, p3, "свёрнутая группа — один пин, все три связи должны указывать в одну и ту же точку");

        int bundleSize = canvas.bundleSizeForTest(distro, in.getId());
        assertEquals(3, bundleSize, "три связи в одно свёрнутое гнездо — пучок из 3, а не 0/1");
    }
}
