package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomRackLayout;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Пример проекта» Планнера для тестов и снимков: три башни, станки, столы, стулья, мониторы, четыре трассы и мелочь
 * на библиотеке, созданной прямо в модели (как у пользователя — личные позиции).
 */
final class PlannerSample {

    /** Раскладка и id башни A. */
    record Built(ControlRoomLayout layout, String towerA) {
    }

    private PlannerSample() {
    }

    private static RoomLibRef room(RoomEquipmentType t) {
        return new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, t.getId());
    }

    private static RoomLibRef furn(RoomFurnitureType t) {
        return new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, t.getId());
    }

    private static RoomEquipmentType rack(AppModel m, String name, int u, double depthMm, double kg, double w, int twelfths) {
        RoomEquipmentType e = new RoomEquipmentType();
        e.setName(name);
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.RACK);
        p.setRackUnits(u);
        p.setDepthMm(depthMm);
        p.setWeightKg(kg);
        p.setPowerW(w);
        p.setWidthTwelfths(twelfths == 12 ? null : twelfths);
        e.setPhysical(p);
        m.addRoomEquipmentType(e);
        return e;
    }

    private static RoomEquipmentType loose(AppModel m, String name, double w, double h, double d) {
        RoomEquipmentType e = new RoomEquipmentType();
        e.setName(name);
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        p.setWidthMm(w);
        p.setHeightMm(h);
        p.setDepthMm(d);
        p.setWeightKg(0.2);
        e.setPhysical(p);
        m.addRoomEquipmentType(e);
        return e;
    }

    private static RoomFurnitureType furniture(AppModel m, String name, RoomFurnitureType.Kind kind,
                                               RoomFurnitureType.Role role, double w, double d, double h, Double diag) {
        RoomFurnitureType t = new RoomFurnitureType();
        t.setName(name);
        t.setKind(kind);
        t.setRole(role);
        t.setWidthMm(w * 10);
        t.setDepthMm(d * 10);
        t.setHeightMm(h * 10);
        t.setWeightKg(10.0);
        t.setDiagonalIn(diag);
        m.addRoomFurnitureType(t);
        return t;
    }

    private static RoomRackItem item(RoomLibRef ref, int u0, int c0) {
        RoomRackItem i = new RoomRackItem();
        i.setRef(ref);
        i.setU0(u0);
        i.setC0(c0);
        return i;
    }

    private static RoomFurniture place(RoomLibRef ref, double x, double y, boolean onTop) {
        RoomFurniture f = new RoomFurniture();
        f.setRef(ref);
        f.setXCm(x);
        f.setYCm(y);
        f.setOnTop(onTop);
        return f;
    }

    private static CaseType rackCase(AppModel m, String name, int u, double inner) {
        CaseType c = new CaseType();
        c.setName(name);
        c.setWidthMm(600);
        c.setHeightMm(u * 44.45 + 160);
        c.setLengthMm((inner + 170));
        c.setWeightKg(5 + u * 1.2);
        PhysicalSpec p = new PhysicalSpec();
        p.setRackUnits(u);
        p.setDepthMm(inner);
        c.setPhysical(p);
        m.addCaseType(c);
        return c;
    }

    static Built build(AppModel m) {
        CaseType c6 = rackCase(m, "Рэк-кофр 6U · гл. 450", 6, 450);
        CaseType c8 = rackCase(m, "Рэк-кофр 8U · гл. 450", 8, 450);
        CaseType c10 = rackCase(m, "Рэк-кофр 10U · гл. 550", 10, 550);
        RoomEquipmentType sw = rack(m, "Коммутатор 24×1G", 1, 300, 4, 40, 12);
        RoomEquipmentType opt = rack(m, "Оптическая панель LC×24", 1, 250, 2, 0, 12);
        RoomEquipmentType pdu = rack(m, "PDU 8 розеток", 1, 100, 2, 0, 12);
        RoomEquipmentType ups = rack(m, "ИБП 1500 ВА", 2, 400, 22, 40, 12);
        RoomEquipmentType led = rack(m, "LED-процессор", 2, 400, 7, 80, 12);
        RoomEquipmentType ctl = rack(m, "LED-контроллер", 1, 300, 3, 30, 12);
        RoomEquipmentType mview = rack(m, "Мультивьюер", 1, 250, 2, 25, 12);
        RoomEquipmentType mix = rack(m, "Видеомикшер", 3, 450, 9, 150, 12);
        RoomEquipmentType half1 = rack(m, "Мультивьюер 1/2 rack", 1, 200, 1.2, 15, 6);
        RoomEquipmentType half2 = rack(m, "Коммутатор 8 портов 1/2 rack", 1, 220, 1.5, 12, 6);
        RoomEquipmentType srv = rack(m, "Медиасервер", 4, 550, 16, 650, 12);
        RoomEquipmentType h2s = loose(m, "Конвертер HDMI→SDI", 110, 30, 70);
        RoomEquipmentType s2h = loose(m, "Конвертер SDI→HDMI", 110, 30, 70);
        RoomEquipmentType osdi = loose(m, "Оптический конвертер SDI", 100, 35, 65);
        RoomEquipmentType spl = loose(m, "Разветвитель HDMI 1×4", 150, 25, 90);
        RoomFurnitureType tbl180 = furniture(m, "Стол 180×60", RoomFurnitureType.Kind.FURNITURE, RoomFurnitureType.Role.TABLE, 180, 60, 75, null);
        RoomFurnitureType tbl120 = furniture(m, "Стол 120×60", RoomFurnitureType.Kind.FURNITURE, RoomFurnitureType.Role.TABLE, 120, 60, 75, null);
        RoomFurnitureType chair = furniture(m, "Стул оператора", RoomFurnitureType.Kind.FURNITURE, RoomFurnitureType.Role.CHAIR, 50, 50, 95, null);
        RoomFurnitureType d21 = furniture(m, "Станок 2×1 м", RoomFurnitureType.Kind.DECK, RoomFurnitureType.Role.OTHER, 200, 100, 40, null);
        RoomFurnitureType d11 = furniture(m, "Станок 1×1 м", RoomFurnitureType.Kind.DECK, RoomFurnitureType.Role.OTHER, 100, 100, 40, null);
        RoomFurnitureType d205 = furniture(m, "Станок 2×0,5 м", RoomFurnitureType.Kind.DECK, RoomFurnitureType.Role.OTHER, 200, 50, 40, null);
        RoomFurnitureType d105 = furniture(m, "Станок 1×0,5 м", RoomFurnitureType.Kind.DECK, RoomFurnitureType.Role.OTHER, 100, 50, 40, null);
        RoomFurnitureType mon27 = furniture(m, "Монитор 27″", RoomFurnitureType.Kind.MONITOR, RoomFurnitureType.Role.OTHER, 61, 22, 48, 27.0);
        RoomFurnitureType mon32 = furniture(m, "Монитор 32″", RoomFurnitureType.Kind.MONITOR, RoomFurnitureType.Role.OTHER, 72, 24, 55, 32.0);

        ControlRoomLayout l = m.addControlRoomLayout("FOH");
        RoomTower a = RoomRackLayout.newTower(l, new RoomLibRef(RoomLibRef.Kind.CASE, c6.getId()), 70, 261);
        a.setName("Башня A · LED-процессинг");
        a.getSegments().get(0).getItems().addAll(List.of(item(room(sw), 0, 0), item(room(opt), 1, 0), item(room(pdu), 2, 0), item(room(ups), 4, 0)));
        RoomSegment a2 = new RoomSegment();
        a2.setCaseRef(new RoomLibRef(RoomLibRef.Kind.CASE, c8.getId()));
        a2.getItems().addAll(List.of(item(room(led), 0, 0), item(room(led), 2, 0), item(room(ctl), 4, 0)));
        a.getSegments().add(a2);
        RoomTower b = RoomRackLayout.newTower(l, new RoomLibRef(RoomLibRef.Kind.CASE, c10.getId()), 140, 266);
        b.setName("Башня B · Видеопульт");
        b.getSegments().get(0).getItems().addAll(List.of(item(room(mview), 0, 0), item(room(mix), 1, 0), item(room(half1), 4, 0),
                item(room(half2), 4, 6), item(room(pdu), 5, 0), item(room(srv), 6, 0)));
        RoomTower cT = RoomRackLayout.newStackTower(l, room(ctl), 220, 300);
        cT.setName("Башня C · Контроллеры стопкой");
        RoomRackLayout.addToStack(cT, room(sw));
        RoomRackLayout.addToStack(cT, room(opt));

        l.getFurniture().add(place(furn(d21), 100, 250, false));
        l.getFurniture().add(place(furn(d21), 300, 250, false));
        l.getFurniture().add(place(furn(d21), 500, 250, false));
        l.getFurniture().add(place(furn(d11), 650, 250, false));
        l.getFurniture().add(place(furn(d205), 100, 325, false));
        l.getFurniture().add(place(furn(d205), 300, 325, false));
        l.getFurniture().add(place(furn(d205), 500, 325, false));
        l.getFurniture().add(place(furn(d105), 650, 325, false));
        l.getFurniture().add(place(furn(d105), 650, 225, true));
        l.getFurniture().add(place(furn(tbl180), 360, 245, false));
        l.getFurniture().add(place(furn(tbl120), 530, 245, false));
        l.getFurniture().add(place(furn(chair), 360, 315, false));
        l.getFurniture().add(place(furn(chair), 530, 315, false));
        l.getFurniture().add(place(furn(mon27), 315, 230, false));
        l.getFurniture().add(place(furn(mon27), 395, 230, false));
        l.getFurniture().add(place(furn(mon32), 530, 230, false));
        RoomRun optic = run("Оптика (закладная)", RoomRunKind.OPTIC, 110, 0, 110, 175, 340, 175);
        RoomRun sdi = run("SDI", RoomRunKind.SDI, 190, 0, 190, 145, 400, 145);
        RoomRun net = run("Сеть Cat6", RoomRunKind.NET, 270, 0, 270, 115, 460, 115);
        RoomRun pwr = run("Силовая 230 В", RoomRunKind.PWR, 610, 0, 610, 300);
        l.getRuns().addAll(List.of(optic, sdi, net, pwr));
        RoomEditor.placeLoose(l, room(h2s), 190, 90);
        RoomEditor.placeLoose(l, room(h2s), 190, 120);
        RoomEditor.placeLoose(l, room(s2h), 300, 145);
        RoomEditor.placeLoose(l, room(osdi), 110, 100);
        RoomEditor.placeLoose(l, room(spl), 430, 190);
        RoomEditor.addLooseToBox(l, room(h2s));

        return new Built(l, a.getId());
    }

    private static RoomRun run(String name, RoomRunKind kind, double... xy) {
        RoomRun r = new RoomRun();
        r.setKind(kind.key());
        r.setName(name);
        for (int i = 0; i < xy.length; i += 2) {
            r.getPoints().add(new RoomPoint(xy[i], xy[i + 1]));
        }
        return r;
    }
}
