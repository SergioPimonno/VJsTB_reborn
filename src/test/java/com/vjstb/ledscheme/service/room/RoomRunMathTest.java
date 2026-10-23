package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import org.junit.jupiter.api.Test;

/**
 * Трассы кабеля: привязка мелочи («отпустите ближе 30 см — встанет на трассу и запомнит расстояние
 * от ввода»), редактирование вершин, подпись названием вдоль линии и разворот направления
 * (запросы пользователя 2026-10-23: гибкое рисование трасс, название трассы как обозначение).
 */
class RoomRunMathTest {

    private final ControlRoomLayout l = RoomPlanFixture.sample();

    private RoomRun run(String id) {
        return l.getRuns().stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void lengthAndPointAtFollowThePolyline() {
        RoomRun sdi = run("sdi"); // 145 вниз, затем 210 вправо
        assertEquals(355, RoomRunMath.length(sdi), 1e-9);
        RoomPoint p = RoomRunMath.pointAt(sdi, 195);
        assertEquals(240, p.getX(), 1e-9);
        assertEquals(145, p.getY(), 1e-9);
        RoomPoint end = RoomRunMath.pointAt(sdi, 9999);
        assertEquals(400, end.getX(), 1e-9, "дальше конца трасса не уходит");
    }

    @Test
    void looseItemSnapsToTheNearestRunOnlyWithinThirtyCentimetres() {
        RoomRunMath.Nearest near = RoomRunMath.snapToRuns(l.getRuns(), 240, 150);
        assertNotNull(near);
        assertEquals("sdi", near.runId());
        assertEquals(195, near.s(), 1e-9);
        assertNull(RoomRunMath.snapToRuns(l.getRuns(), 430, 250), "дальше 30 см от любой трассы — свободно");
    }

    @Test
    void drawnVerticesSnapToTheGridAndTheWalls() {
        RoomPoint wall = RoomRunMath.snapPoint(700, 450, 3, 7);
        assertEquals(0, wall.getX(), 1e-9);
        assertEquals(0, wall.getY(), 1e-9, "у стены сцены точка прилипает к краю");
        RoomPoint inside = RoomRunMath.snapPoint(700, 450, 303, 222);
        assertEquals(305, inside.getX(), 1e-9);
        assertEquals(220, inside.getY(), 1e-9);
        RoomPoint far = RoomRunMath.snapPoint(700, 450, 900, 100);
        assertEquals(700, far.getX(), 1e-9, "за площадку точку не выпускаем");
    }

    @Test
    void labelGoesAlongTheFirstSegmentUprightAndFallsBackToTheLongest() {
        RoomRunMath.LabelPos sdi = RoomRunMath.labelPos(run("sdi"));
        assertEquals(190, sdi.x(), 1e-9);
        assertEquals(72.5, sdi.y(), 1e-9);
        assertEquals(-90, sdi.angleDeg(), 1e-9, "вертикальная линия читается снизу вверх");
        RoomRun shortFirst = new RoomRun();
        shortFirst.getPoints().add(new RoomPoint(0, 0));
        shortFirst.getPoints().add(new RoomPoint(0, 40));
        shortFirst.getPoints().add(new RoomPoint(200, 40));
        RoomRunMath.LabelPos p = RoomRunMath.labelPos(shortFirst);
        assertEquals(100, p.x(), 1e-9, "первый отрезок короче 60 см — подпись на самом длинном");
        assertEquals(0, p.angleDeg(), 1e-9);
        assertTrue(p.angleDeg() >= -90 && p.angleDeg() < 90);
    }

    @Test
    void reversingARunKeepsLooseItemsOnTheSameSpots() {
        RoomRun sdi = run("sdi");
        RoomLooseItem l1 = l.getLoose().stream().filter(x -> x.getId().equals("l1")).findFirst().orElseThrow();
        RoomPoint before = RoomRunMath.pointAt(sdi, l1.getSCm());
        RoomRunMath.reverse(sdi, l.getLoose());
        assertEquals(400, sdi.getPoints().get(0).getX(), 1e-9, "направление изменено");
        RoomPoint after = RoomRunMath.pointAt(sdi, l1.getSCm());
        assertEquals(before.getX(), after.getX(), 1e-9);
        assertEquals(before.getY(), after.getY(), 1e-9);
    }
}
