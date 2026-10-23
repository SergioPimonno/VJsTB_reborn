package com.vjstb.ledscheme.service.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Отмена и возврат правок раскладки пультовой (своя история окна, как {@code hist} прототипа: до 60
 * снимков). Снимок — JSON раскладки целиком; восстановление переписывает ту же самую раскладку на месте,
 * не меняя её id, название и порядок в списке сцены, чтобы ссылки окна и списка не терялись.
 * Вызывающий делает {@link #push} ПЕРЕД каждой правкой; новая правка сбрасывает «вернуть».
 */
public final class RoomHistory {

    public static final int LIMIT = 60;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();

    /** Запомнить состояние до правки. */
    public void push(ControlRoomLayout layout) {
        undo.push(snapshot(layout));
        while (undo.size() > LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Откатить последнюю правку; {@code false}, если откатывать нечего. */
    public boolean undo(ControlRoomLayout layout) {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(snapshot(layout));
        restore(layout, undo.pop());
        return true;
    }

    public boolean redo(ControlRoomLayout layout) {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(snapshot(layout));
        restore(layout, redo.pop());
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }

    private static String snapshot(ControlRoomLayout layout) {
        try {
            return MAPPER.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось запомнить раскладку", e);
        }
    }

    private static void restore(ControlRoomLayout target, String json) {
        try {
            ControlRoomLayout from = MAPPER.readValue(json, ControlRoomLayout.class);
            target.setRoomWidthCm(from.getRoomWidthCm());
            target.setRoomDepthCm(from.getRoomDepthCm());
            target.setTowers(from.getTowers());
            target.setLoose(from.getLoose());
            target.setFurniture(from.getFurniture());
            target.setRuns(from.getRuns());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось восстановить раскладку", e);
        }
    }
}
