package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PhysicalPart;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Форма позиции из блоков: габарит, центр, масштаб под переопределённые размеры, поворот. */
class RoomShapeTest {

    private static PhysicalPart part(String n, double w, double d, double h, double x, double y, double z) {
        return new PhysicalPart(n, w, d, h, x, y, z, null);
    }

    @Test
    void shapeIsCenteredOnItsBoundingBoxNoMatterHowItWasDrawn() {
        // панель 1000×70×600 над колонной 100×100×500, всё нарисовано со сдвигом x = +300
        List<RoomPart> parts = RoomShape.fromModel(List.of(
                part("Панель", 1000, 70, 600, 300, 0, 500),
                part("Колонна", 100, 100, 500, 300, 0, 0)));
        assertEquals(2, parts.size());
        assertEquals(0.0, parts.get(0).xCm(), 1e-9, "центр габарита — в нуле, а не в x = 30 см");
        double[] bb = RoomShape.bbox(parts);
        assertEquals(100.0, bb[0], 1e-9, "ширина 1000 мм");
        assertEquals(10.0, bb[1], 1e-9, "глубина: самый глубокий блок — колонна 100 мм");
        assertEquals(110.0, bb[2], 1e-9, "высота — верх панели над полом: 500 + 600 мм");
    }

    @Test
    void invalidBlocksAreSkippedAndEmptyListMeansNoShape() {
        assertTrue(RoomShape.fromModel(null).isEmpty());
        assertTrue(RoomShape.fromModel(List.of(part("нулевой", 0, 10, 10, 0, 0, 0))).isEmpty());
        assertEquals(1, RoomShape.fromModel(List.of(part("ок", 10, 10, 10, 0, 0, 0),
                part("плохой", 10, -1, 10, 0, 0, 0))).size());
    }

    @Test
    void fitStretchesBlocksToTheOverriddenSizeOfTheItem() {
        List<RoomPart> parts = RoomShape.fromModel(List.of(part("a", 1000, 100, 500, 0, 0, 0)));
        RoomItemSpec wider = new RoomItemSpec(null, RoomItemSpec.Type.DEVICE, RoomItemSpec.Role.OTHER, "x", "", 200, 50,
                10, 0, 12, 0, 1, 1, null, List.of(), parts);
        RoomPart p = RoomShape.fit(wider).get(0);
        assertEquals(200.0, p.wCm(), 1e-9, "блок растянут вместе с позицией");
        assertEquals(10.0, p.dCm(), 1e-9);
    }

    @Test
    void footprintFollowsTheRotationOfTheItem() {
        RoomPart p = new RoomPart("p", 100, 20, 10, 30, 0, 0, 0);
        double[] r0 = RoomShape.footprint(p, 0);
        assertEquals(30.0, r0[0], 1e-9);
        assertEquals(100.0, r0[2], 1e-9);
        double[] r90 = RoomShape.footprint(p, 90);
        assertEquals(0.0, r90[0], 1e-9, "блок со сдвигом вправо при повороте на 90° уезжает к лицу (вниз на плане)");
        assertEquals(30.0, r90[1], 1e-9);
        assertEquals(20.0, r90[2], 1e-9, "ширина и глубина меняются местами");
        assertEquals(100.0, r90[3], 1e-9);
    }

    @Test
    void totalWeightIgnoresBlocksWithoutWeight() {
        assertEquals(7.0, RoomShape.totalKg(List.of(new RoomPart("a", 1, 1, 1, 0, 0, 0, 3),
                new RoomPart("b", 1, 1, 1, 0, 0, 0, 4))), 1e-9);
        assertEquals(0.0, RoomShape.totalKg(List.of()), 1e-9);
    }
}
