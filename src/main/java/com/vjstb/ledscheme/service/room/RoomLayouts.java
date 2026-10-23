package com.vjstb.ledscheme.service.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.Scene;
import java.util.List;

/**
 * Операции со списком раскладок пультовой у сцены: создать, переименовать, дублировать,
 * удалить. У каждой сцены свой список, раскладок может быть несколько (решение пользователя
 * 2026-10-23, PLAN.md раздел 6.1); образец — листы общей схемы ({@code SchemaSheet}).
 * Раскладки не связаны со схемами и экранами сцены, поэтому дублирование — простая глубокая
 * копия с новым id и уникальным названием.
 */
public final class RoomLayouts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoomLayouts() {
    }

    /** Новая пустая раскладка в конце списка; пустое название заменяется на «Раскладка N». */
    public static ControlRoomLayout create(Scene scene, String name) {
        ControlRoomLayout l = new ControlRoomLayout(uniqueName(scene, name, "Раскладка " + (scene.getControlRoomLayouts().size() + 1)));
        scene.getControlRoomLayouts().add(l);
        reindex(scene);
        return l;
    }

    public static void rename(Scene scene, ControlRoomLayout layout, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty() && !trimmed.equals(layout.getName())) {
            layout.setName(uniqueNameExcept(scene, trimmed, layout));
        }
    }

    /** Полная копия раскладки сразу после исходной, с новым id и названием «… (копия)». */
    public static ControlRoomLayout duplicate(Scene scene, ControlRoomLayout source) {
        try {
            ControlRoomLayout copy = MAPPER.readValue(MAPPER.writeValueAsString(source), ControlRoomLayout.class);
            copy.setId(java.util.UUID.randomUUID().toString());
            copy.setName(uniqueName(scene, source.getName() + " (копия)", source.getName() + " (копия)"));
            List<ControlRoomLayout> list = scene.getControlRoomLayouts();
            int at = Math.max(0, list.indexOf(source)) + 1;
            list.add(Math.min(at, list.size()), copy);
            reindex(scene);
            return copy;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Не удалось скопировать раскладку", e);
        }
    }

    public static void delete(Scene scene, ControlRoomLayout layout) {
        scene.getControlRoomLayouts().remove(layout);
        reindex(scene);
    }

    /** Пересчёт {@code orderIndex} по порядку в списке. */
    public static void reindex(Scene scene) {
        List<ControlRoomLayout> list = scene.getControlRoomLayouts();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setOrderIndex(i);
        }
    }

    private static String uniqueName(Scene scene, String wanted, String fallback) {
        String base = wanted == null || wanted.isBlank() ? fallback : wanted.trim();
        return uniqueNameExcept(scene, base, null);
    }

    private static String uniqueNameExcept(Scene scene, String base, ControlRoomLayout except) {
        String name = base;
        int n = 2;
        while (taken(scene, name, except)) {
            name = base + " " + n++;
        }
        return name;
    }

    private static boolean taken(Scene scene, String name, ControlRoomLayout except) {
        for (ControlRoomLayout l : scene.getControlRoomLayouts()) {
            if (l != except && l.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
