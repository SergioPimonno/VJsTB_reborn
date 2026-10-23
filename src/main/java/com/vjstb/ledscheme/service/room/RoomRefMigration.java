package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;

/**
 * Перенос ссылок раскладки пультовой на новый id позиции библиотеки. Нужен синхронизации: личная
 * запись с тем же именем, что пришла с сервера, «продвигается» в общую и меняет id (см.
 * {@code AppModel#applyOne}); без переноса позиции раскладок молча стали бы «неизвестными» —
 * тот же класс бага, что уже ловили на {@code Screen.cabinetTypeId}.
 */
public final class RoomRefMigration {

    private RoomRefMigration() {
    }

    /** Заменяет ссылки {@code kind:oldId} на {@code kind:newId} во всех местах раскладки; возвращает число замен. */
    public static int migrate(ControlRoomLayout layout, RoomLibRef.Kind kind, String oldId, String newId) {
        int n = 0;
        for (RoomTower t : layout.getTowers()) {
            for (RoomSegment s : t.getSegments()) {
                n += swap(s.getCaseRef(), kind, oldId, newId);
                for (RoomRackItem it : s.getItems()) {
                    n += swap(it.getRef(), kind, oldId, newId);
                }
            }
        }
        for (RoomLooseItem l : layout.getLoose()) {
            n += swap(l.getRef(), kind, oldId, newId);
        }
        for (RoomFurniture f : layout.getFurniture()) {
            n += swap(f.getRef(), kind, oldId, newId);
        }
        return n;
    }

    private static int swap(RoomLibRef ref, RoomLibRef.Kind kind, String oldId, String newId) {
        if (ref != null && ref.getKind() == kind && oldId.equals(ref.getId())) {
            ref.setId(newId);
            return 1;
        }
        return 0;
    }

    /** Сколько позиций раскладки ссылаются на {@code kind:id} — для предупреждения перед удалением позиции библиотеки. */
    public static int count(ControlRoomLayout layout, RoomLibRef.Kind kind, String id) {
        int n = 0;
        for (RoomTower t : layout.getTowers()) {
            for (RoomSegment s : t.getSegments()) {
                n += matches(s.getCaseRef(), kind, id);
                for (RoomRackItem it : s.getItems()) {
                    n += matches(it.getRef(), kind, id);
                }
            }
        }
        for (RoomLooseItem l : layout.getLoose()) {
            n += matches(l.getRef(), kind, id);
        }
        for (RoomFurniture f : layout.getFurniture()) {
            n += matches(f.getRef(), kind, id);
        }
        return n;
    }

    private static int matches(RoomLibRef ref, RoomLibRef.Kind kind, String id) {
        return ref != null && ref.getKind() == kind && id.equals(ref.getId()) ? 1 : 0;
    }
}
