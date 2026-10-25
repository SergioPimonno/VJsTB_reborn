package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalPart;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Позиции из блоков на плане и на виде спереди: рисуются по блокам, выбираются по блокам, пространство между ножками сквозное. */
class RoomPartsUiTest {

    @Test
    void tableFromBlocksIsDrawnAndPickedByItsBlocks(@TempDir Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        RoomFurnitureType t = new RoomFurnitureType();
        t.setName("Стол из блоков");
        t.setKind(RoomFurnitureType.Kind.FURNITURE);
        t.setRole(RoomFurnitureType.Role.TABLE);
        t.setParts(List.of(
                new PhysicalPart("Столешница", 1800, 600, 50, 0, 0, 700, null),
                new PhysicalPart("Ножка", 40, 560, 700, -850, 0, 0, null),
                new PhysicalPart("Ножка", 40, 560, 700, 850, 0, 0, null)));
        m.addRoomFurnitureType(t);
        ControlRoomLayout l = m.addControlRoomLayout("FOH");
        RoomFurniture f = new RoomFurniture();
        f.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, t.getId()));
        f.setXCm(300);
        f.setYCm(200);
        l.getFurniture().add(f);

        PlannerSession session = new PlannerSession(m);
        session.setLayout(l);
        PlanView plan = new PlanView(session);
        ElevationView elev = new ElevationView(session);
        BufferedImage img = new BufferedImage(900, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        plan.paint(g, 900, 400);
        elev.paint(g, 900, 400);
        g.dispose();

        assertEquals(PlanView.Hit.Kind.FURN, plan.hit(300, 200).kind(), "стол выбирается на плане по габариту");
        ElevationView.Pick top = elev.pickAt(300, 74);
        assertNotNull(top, "столешница хватается на виде спереди");
        assertEquals(f.getId(), top.id());
        ElevationView.Pick leg = elev.pickAt(215, 30);
        assertNotNull(leg, "ножка хватается");
        assertNull(elev.pickAt(300, 30), "между ножками клик проходит сквозь стол");
        assertEquals(0.0, top.baseHeight(), 1e-9, "низ позиции — пол, а не низ столешницы");
    }
}
