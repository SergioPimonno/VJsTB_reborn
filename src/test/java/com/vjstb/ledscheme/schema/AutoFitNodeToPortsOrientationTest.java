package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T2.2 — {@code AppModel#autoFitNodeToPorts}
 *  теперь растит ОБЕ оси через {@link com.vjstb.ledscheme.service.schemalayout.NodePortLayout},
 *  а не только высоту (см. DIALOG.md, реплика 1, п.5: раньше в вертикальном режиме
 *  ширины не хватало на все гнёзда TOP/BOTTOM-сторон). */
class AutoFitNodeToPortsOrientationTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void downOrientationGrowsWidthToFitManySyncGroupsSideBySide(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL", 0, 0, null);
        node.setOrientation(NodeOrientation.DOWN);

        double widthBefore = node.getWidth();
        // Много РАЗНЫХ развёрнутых видео-групп (роль VIDEO, IN) — при DOWN уходят на
        // сторону TOP/BOTTOM (повёрнуто на 90° от базового LEFT/RIGHT) и требуют
        // немало ширины, если разложены КОЛОНКАМИ, а не строками, как в RIGHT.
        model.addCardToNode(node, "Basic Set", List.of(
                new CardPort("HDMI 2.0", PortDirection.IN, 8),
                new CardPort("DisplayPort 1.2", PortDirection.IN, 8),
                new CardPort("SDI", PortDirection.IN, 8)));

        assertTrue(node.getWidth() > widthBefore,
                "автоподгон при DOWN должен вырасти по ШИРИНЕ (раньше растил только высоту)");
    }

    @Test
    void rightOrientationStillGrowsHeightForManyRows(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "D3", 0, 0, null);
        // orientation не задана явно -> RIGHT по умолчанию (см. AppModel#autoFitNodeToPorts).

        double heightBefore = node.getHeight();
        model.addCardToNode(node, "Basic Set", List.of(
                new CardPort("DisplayPort 1.2", PortDirection.OUT, 1),
                new CardPort("Ethernet Cat6", PortDirection.OUT, 3),
                new CardPort("Genlock Blackburst", PortDirection.IN, 1),
                new CardPort("XLR", PortDirection.IN, 2),
                new CardPort("XLR", PortDirection.OUT, 2),
                new CardPort("BNC", PortDirection.IN, 16)));

        assertTrue(node.getHeight() > heightBefore, "как и раньше, при RIGHT автоподгон растит высоту под строки");
    }

    @Test
    void autoFitNeverShrinksManuallyEnlargedNode(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "N", 0, 0, null);
        model.resizeSchemaNode(node, 900, 900);

        model.addCardToNode(node, "Card", List.of(new CardPort("HDMI", PortDirection.IN, 1)));

        assertTrue(node.getWidth() >= 900 && node.getHeight() >= 900,
                "автоподгон только растит — не должен уменьшать вручную увеличенный узел");
    }
}
