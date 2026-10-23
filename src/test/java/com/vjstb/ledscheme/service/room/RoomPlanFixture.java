package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;

/**
 * «Пример проекта» из прототипа ({@code docs/control-room-planner/planner.html}, функция
 * {@code sample()}) и его условный каталог — общая основа сценарных тестов. Данные оборудования
 * условные (как в прототипе), id объектов фиксированы, чтобы тесты могли на них ссылаться.
 * Эталонные числа тестов (итоги, единственное замечание, пол станков 7 × 1,5 м) сняты с
 * прототипа, который пользователь проверил вживую.
 */
final class RoomPlanFixture {

    static RoomLibRef eq(String id) {
        return new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, id);
    }

    static RoomLibRef furn(String id) {
        return new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, id);
    }

    static RoomLibRef cs(String id) {
        return new RoomLibRef(RoomLibRef.Kind.CASE, id);
    }

    /** Рэк-кофр как в прототипе: ширина 60, высота юниты + 16 см на рамы, глубина внутри + 17 см на крышки. */
    private static RoomItemSpec rackCase(String id, int u, double innerDepth) {
        return RoomItemSpec.caseOf(cs(id), "Рэк-кофр " + u + "U", u, innerDepth, 60, u * RoomUnits.U_CM + 16,
                innerDepth + 17, Math.round(5 + u * 1.2));
    }

    static MapRoomCatalog catalog() {
        MapRoomCatalog c = new MapRoomCatalog();
        c.add(rackCase("c4_35", 4, 35)).add(rackCase("c6_45", 6, 45)).add(rackCase("c8_45", 8, 45))
                .add(rackCase("c10_55", 10, 55));
        c.add(RoomItemSpec.rack(eq("sw24"), "Коммутатор 24×1G", 1, 30, 4, 40, 12));
        c.add(RoomItemSpec.rack(eq("optpp"), "Оптическая панель LC×24", 1, 25, 2, 0, 12));
        c.add(RoomItemSpec.rack(eq("pdu"), "PDU 8 розеток", 1, 10, 2, 0, 12));
        c.add(RoomItemSpec.rack(eq("ups2"), "ИБП 1500 ВА", 2, 40, 22, 40, 12));
        c.add(RoomItemSpec.rack(eq("ledp2"), "LED-процессор", 2, 40, 7, 80, 12));
        c.add(RoomItemSpec.rack(eq("ledc1"), "LED-контроллер", 1, 30, 3, 30, 12));
        c.add(RoomItemSpec.rack(eq("fan1"), "Блок вентиляторов", 1, 15, 1.5, 30, 12));
        c.add(RoomItemSpec.rack(eq("mview1"), "Мультивьюер", 1, 25, 2, 25, 12));
        c.add(RoomItemSpec.rack(eq("mix3"), "Видеомикшер", 3, 45, 9, 150, 12));
        c.add(RoomItemSpec.rack(eq("mview_h"), "Мультивьюер 1/2 rack", 1, 20, 1.2, 15, 6));
        c.add(RoomItemSpec.rack(eq("sw8h"), "Коммутатор 8 портов 1/2 rack", 1, 22, 1.5, 12, 6));
        c.add(RoomItemSpec.rack(eq("msrv4"), "Медиасервер", 4, 55, 16, 650, 12));
        c.add(RoomItemSpec.rack(eq("cat6pp"), "Патч-панель Cat6 24×", 1, 10, 1.2, 0, 12));
        c.add(RoomItemSpec.loose(eq("ctl_s"), "LED-контроллер компактный", 31, 4.5, 20, 1.2, 25));
        c.add(RoomItemSpec.loose(eq("ctl_m"), "LED-контроллер средний", 38, 6.5, 25, 2.2, 45));
        c.add(RoomItemSpec.loose(eq("h2s"), "Конвертер HDMI→SDI", 11, 3, 7, 0.15, 3));
        c.add(RoomItemSpec.loose(eq("s2h"), "Конвертер SDI→HDMI", 11, 3, 7, 0.15, 3));
        c.add(RoomItemSpec.loose(eq("osdi"), "Оптический конвертер SDI", 10, 3.5, 6.5, 0.2, 3));
        c.add(RoomItemSpec.loose(eq("mcnv"), "Медиаконвертер Ethernet↔оптика", 11, 3, 7, 0.2, 5));
        c.add(RoomItemSpec.loose(eq("spl14"), "Разветвитель HDMI 1×4", 15, 2.5, 9, 0.4, 5));
        c.add(RoomItemSpec.loose(eq("sw8"), "Коммутатор 8 портов настольный", 20, 3, 12, 0.5, 8));
        c.add(RoomItemSpec.loose(eq("psu"), "Блок питания внешний", 10, 4, 6, 0.3, 0));
        c.add(RoomItemSpec.furniture(furn("tbl180"), "Стол 180×60", RoomItemSpec.Role.TABLE, 180, 60, 75, 18));
        c.add(RoomItemSpec.furniture(furn("tbl120"), "Стол 120×60", RoomItemSpec.Role.TABLE, 120, 60, 75, 12));
        c.add(RoomItemSpec.furniture(furn("chair"), "Стул оператора", RoomItemSpec.Role.CHAIR, 50, 50, 95, 6));
        c.add(RoomItemSpec.deck(furn("dk100x100"), "Станок 1×1 м", 100, 100, 40, 14));
        c.add(RoomItemSpec.deck(furn("dk200x100"), "Станок 2×1 м", 200, 100, 40, 26));
        c.add(RoomItemSpec.deck(furn("dk100x50"), "Станок 1×0,5 м", 100, 50, 40, 8));
        c.add(RoomItemSpec.deck(furn("dk200x50"), "Станок 2×0,5 м", 200, 50, 40, 14));
        c.add(RoomItemSpec.monitor(furn("mon27"), "Монитор 27″", 27, 61, 22, 48, 5.5, 35));
        c.add(RoomItemSpec.monitor(furn("mon32"), "Монитор 32″", 32, 72, 24, 55, 7.5, 50));
        return c;
    }

    private static RoomRackItem item(String id, String lib, int u0, int c0) {
        RoomRackItem i = new RoomRackItem();
        i.setId(id);
        i.setRef(eq(lib));
        i.setU0(u0);
        i.setC0(c0);
        return i;
    }

    private static RoomSegment caseSeg(String id, String caseId, RoomRackItem... items) {
        RoomSegment s = new RoomSegment();
        s.setId(id);
        s.setCaseRef(cs(caseId));
        for (RoomRackItem i : items) {
            s.getItems().add(i);
        }
        return s;
    }

    private static RoomTower tower(String id, String tag, String name, double x, double y, RoomSegment... segs) {
        RoomTower t = new RoomTower();
        t.setId(id);
        t.setTag(tag);
        t.setName(name);
        t.setXCm(x);
        t.setYCm(y);
        for (RoomSegment s : segs) {
            t.getSegments().add(s);
        }
        return t;
    }

    private static RoomFurniture furniture(String id, String lib, double x, double y, boolean onTop) {
        RoomFurniture f = new RoomFurniture();
        f.setId(id);
        f.setRef(furn(lib));
        f.setXCm(x);
        f.setYCm(y);
        f.setOnTop(onTop);
        return f;
    }

    private static RoomLooseItem loose(String id, String lib, RoomLooseItem.Mode mode, String run, Double s, Double x,
                                       Double y) {
        RoomLooseItem l = new RoomLooseItem();
        l.setId(id);
        l.setRef(eq(lib));
        l.setMode(mode);
        l.setRunId(run);
        l.setSCm(s);
        l.setXCm(x);
        l.setYCm(y);
        return l;
    }

    private static RoomRun run(String id, String kind, String name, double... xy) {
        RoomRun r = new RoomRun();
        r.setId(id);
        r.setKind(kind);
        r.setName(name);
        for (int i = 0; i < xy.length; i += 2) {
            r.getPoints().add(new RoomPoint(xy[i], xy[i + 1]));
        }
        return r;
    }

    /** Пример проекта: три башни (две в кофрах, одна стопкой без кофра), станки и столы, мониторы, трассы. */
    static ControlRoomLayout sample() {
        ControlRoomLayout L = new ControlRoomLayout("FOH");
        L.getTowers().add(tower("tA", "A", "Башня A · LED-процессинг", 70, 261,
                caseSeg("cA1", "c6_45", item("a1", "sw24", 0, 0), item("a2", "optpp", 1, 0), item("a3", "pdu", 2, 0),
                        item("a4", "ups2", 4, 0)),
                caseSeg("cA2", "c8_45", item("a5", "ledp2", 0, 0), item("a6", "ledp2", 2, 0), item("a7", "ledc1", 4, 0),
                        item("a8", "fan1", 5, 0))));
        L.getTowers().add(tower("tB", "B", "Башня B · Видеопульт", 140, 266,
                caseSeg("cB1", "c10_55", item("b3", "mview1", 0, 0), item("b2", "mix3", 1, 0),
                        item("b5", "mview_h", 4, 0), item("b6", "sw8h", 4, 6), item("b4", "pdu", 5, 0),
                        item("b1", "msrv4", 6, 0))));
        RoomSegment stack = new RoomSegment();
        stack.setId("sC1");
        stack.setStack(true);
        stack.getItems().add(item("c1", "ledc1", 0, 0));
        stack.getItems().add(item("c2", "ctl_m", 0, 0));
        stack.getItems().add(item("c3", "ctl_s", 0, 0));
        stack.getItems().add(item("c4", "ctl_s", 0, 0));
        L.getTowers().add(tower("tC", "C", "Башня C · Контроллеры стопкой", 220, 300, stack));

        L.getLoose().add(loose("l1", "h2s", RoomLooseItem.Mode.RUN, "sdi", 195.0, null, null));
        L.getLoose().add(loose("l2", "h2s", RoomLooseItem.Mode.RUN, "sdi", 230.0, null, null));
        L.getLoose().add(loose("l3", "s2h", RoomLooseItem.Mode.RUN, "sdi", 315.0, null, null));
        L.getLoose().add(loose("l4", "s2h", RoomLooseItem.Mode.RUN, "sdi", 345.0, null, null));
        L.getLoose().add(loose("l5", "osdi", RoomLooseItem.Mode.RUN, "optic", 215.0, null, null));
        L.getLoose().add(loose("l6", "osdi", RoomLooseItem.Mode.RUN, "optic", 260.0, null, null));
        L.getLoose().add(loose("l7", "mcnv", RoomLooseItem.Mode.RUN, "optic", 330.0, null, null));
        L.getLoose().add(loose("l8", "spl14", RoomLooseItem.Mode.FREE, null, null, 430.0, 190.0));
        L.getLoose().add(loose("l9", "sw8", RoomLooseItem.Mode.FREE, null, null, 520.0, 185.0));
        L.getLoose().add(loose("l10", "psu", RoomLooseItem.Mode.BOX, null, null, null, null));
        L.getLoose().add(loose("l11", "psu", RoomLooseItem.Mode.BOX, null, null, null, null));

        L.getFurniture().add(furniture("d1", "dk200x100", 100, 250, false));
        L.getFurniture().add(furniture("d2", "dk200x100", 300, 250, false));
        L.getFurniture().add(furniture("d3", "dk200x100", 500, 250, false));
        L.getFurniture().add(furniture("d4", "dk100x100", 650, 250, false));
        L.getFurniture().add(furniture("d5", "dk200x50", 100, 325, false));
        L.getFurniture().add(furniture("d6", "dk200x50", 300, 325, false));
        L.getFurniture().add(furniture("d7", "dk200x50", 500, 325, false));
        L.getFurniture().add(furniture("d8", "dk100x50", 650, 325, false));
        L.getFurniture().add(furniture("d9", "dk100x50", 650, 225, true));
        L.getFurniture().add(furniture("f1", "tbl180", 360, 245, false));
        L.getFurniture().add(furniture("f2", "tbl120", 530, 245, false));
        L.getFurniture().add(furniture("f3", "chair", 360, 315, false));
        L.getFurniture().add(furniture("f4", "chair", 530, 315, false));
        L.getFurniture().add(furniture("m1", "mon27", 315, 230, false));
        L.getFurniture().add(furniture("m2", "mon27", 395, 230, false));
        L.getFurniture().add(furniture("m3", "mon32", 530, 230, false));

        L.getRuns().add(run("optic", "optic", "Оптика (закладная)", 110, 0, 110, 175, 340, 175));
        L.getRuns().add(run("sdi", "sdi", "SDI", 190, 0, 190, 145, 400, 145));
        L.getRuns().add(run("net", "net", "Сеть Cat6", 270, 0, 270, 115, 460, 115));
        L.getRuns().add(run("pwr", "pwr", "Силовая 230 В", 610, 0, 610, 300));
        return L;
    }

    static RoomTower tower(ControlRoomLayout l, String id) {
        return l.getTowers().stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
    }

    static RoomFurniture furniture(ControlRoomLayout l, String id) {
        return l.getFurniture().stream().filter(f -> f.getId().equals(id)).findFirst().orElseThrow();
    }

    static RoomSegment segment(ControlRoomLayout l, String towerId, String segId) {
        return tower(l, towerId).getSegments().stream().filter(s -> s.getId().equals(segId)).findFirst().orElseThrow();
    }

    private RoomPlanFixture() {
    }
}
