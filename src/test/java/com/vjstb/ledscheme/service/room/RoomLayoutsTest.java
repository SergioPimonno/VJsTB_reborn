package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.model.Scene;
import org.junit.jupiter.api.Test;

/**
 * Раскладки пультовой принадлежат сцене: у каждой сцены свой список, раскладок может быть
 * несколько (решение пользователя 2026-10-23). Тесты защищают именно это и сохранность данных:
 * дублирование — глубокая копия (правка копии не трогает оригинал), сцена с раскладками
 * переживает запись и чтение JSON (enum-ы, вложенные слои, трассы), а старый проект без поля
 * открывается с пустым списком.
 */
class RoomLayoutsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void eachSceneHasItsOwnListOfLayouts() {
        Scene a = new Scene("Сцена A");
        Scene b = new Scene("Сцена B");
        RoomLayouts.create(a, "Основная");
        RoomLayouts.create(a, "Запасная");
        RoomLayouts.create(b, "Основная");
        assertEquals(2, a.getControlRoomLayouts().size());
        assertEquals(1, b.getControlRoomLayouts().size(), "у другой сцены список свой");
        assertEquals(1, a.getControlRoomLayouts().get(1).getOrderIndex());
    }

    @Test
    void namesStayUniqueInsideAScene() {
        Scene s = new Scene("Сцена");
        RoomLayouts.create(s, "FOH");
        ControlRoomLayout second = RoomLayouts.create(s, "foh");
        assertEquals("foh 2", second.getName());
        ControlRoomLayout blank = RoomLayouts.create(s, "  ");
        assertEquals("Раскладка 3", blank.getName(), "пустое название заменяется номером");
        RoomLayouts.rename(s, blank, "FOH");
        assertEquals("FOH 3", blank.getName(), "«FOH» и «foh 2» уже заняты (регистр не различается)");
    }

    @Test
    void duplicateIsADeepCopyWithANewIdPlacedRightAfterTheSource() {
        Scene s = new Scene("Сцена");
        ControlRoomLayout src = RoomPlanFixture.sample();
        s.getControlRoomLayouts().add(src);
        ControlRoomLayout last = RoomLayouts.create(s, "Другая");
        ControlRoomLayout copy = RoomLayouts.duplicate(s, src);
        assertEquals(1, s.getControlRoomLayouts().indexOf(copy), "копия сразу после исходной");
        assertEquals(2, s.getControlRoomLayouts().indexOf(last));
        assertNotEquals(src.getId(), copy.getId());
        assertEquals("FOH (копия)", copy.getName());
        assertEquals(src.getTowers().size(), copy.getTowers().size());
        RoomTower tc = copy.getTowers().get(0);
        tc.setXCm(999);
        tc.getSegments().get(0).getItems().clear();
        assertEquals(70, src.getTowers().get(0).getXCm(), 1e-9, "правка копии не трогает оригинал");
        assertEquals(4, src.getTowers().get(0).getSegments().get(0).getItems().size());
    }

    @Test
    void deleteReindexesTheRest() {
        Scene s = new Scene("Сцена");
        ControlRoomLayout a = RoomLayouts.create(s, "A");
        ControlRoomLayout b = RoomLayouts.create(s, "B");
        RoomLayouts.delete(s, a);
        assertEquals(1, s.getControlRoomLayouts().size());
        assertEquals(0, b.getOrderIndex());
        RoomLayouts.delete(s, b);
        assertTrue(s.getControlRoomLayouts().isEmpty(), "список может стать пустым");
    }

    @Test
    void sceneWithLayoutsSurvivesAJsonRoundTrip() throws Exception {
        Scene s = new Scene("Сцена");
        s.getControlRoomLayouts().add(RoomPlanFixture.sample());
        Scene back = MAPPER.readValue(MAPPER.writeValueAsString(s), Scene.class);
        assertEquals(1, back.getControlRoomLayouts().size());
        ControlRoomLayout l = back.getControlRoomLayouts().get(0);
        assertEquals(3, l.getTowers().size());
        assertEquals(RoomLooseItem.Mode.RUN, l.getLoose().get(0).getMode());
        assertEquals("sdi", l.getLoose().get(0).getRunId());
        assertEquals(4, l.getRuns().size());
        assertEquals(3, l.getRuns().get(1).getPoints().size());
        assertTrue(l.getTowers().get(2).getSegments().get(0).isStack(), "стопка без кофра сохраняется");
        assertTrue(l.getFurniture().stream().anyMatch(f -> f.getId().equals("d9") && f.isOnTop()), "станок-стол сохраняется");
        // расчёт на прочитанной раскладке даёт те же итоги, что на исходной
        RoomCatalog cat = RoomPlanFixture.catalog();
        assertEquals(RoomPlanCalc.calc(s.getControlRoomLayouts().get(0), cat).totals(),
                RoomPlanCalc.calc(l, cat).totals());
    }

    @Test
    void oldProjectWithoutTheFieldOpensWithAnEmptyList() throws Exception {
        Scene old = MAPPER.readValue("{\"id\":\"s1\",\"name\":\"Старая сцена\"}", Scene.class);
        assertTrue(old.getControlRoomLayouts().isEmpty());
    }
}
