package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomLibRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Каталог в памяти: позиции по ссылке. Используется в тестах и как простой запасной
 *  вариант; приложение подставляет {@code RoomLibraryAdapter}. */
public class MapRoomCatalog implements RoomCatalog {

    private final Map<RoomLibRef, RoomItemSpec> items = new LinkedHashMap<>();

    public MapRoomCatalog add(RoomItemSpec spec) {
        items.put(spec.ref(), spec);
        return this;
    }

    @Override
    public RoomItemSpec resolve(RoomLibRef ref) {
        RoomItemSpec s = ref != null ? items.get(ref) : null;
        return s != null ? s : RoomItemSpec.unknown(ref);
    }

    @Override
    public List<RoomItemSpec> all() {
        return new ArrayList<>(items.values());
    }
}
