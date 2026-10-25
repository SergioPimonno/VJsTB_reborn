package com.vjstb.ledscheme.service.room;

/**
 * Что выбрано в Планере: башня, блок в кофре, предмет в стопке, мебель/станок/монитор, мелочь или
 * трасса. Чистая структура — ни Swing, ни раскладки; ссылается на объекты по id, поэтому переживает
 * пересчёт и отмену (если объекта больше нет, выбор просто сбрасывается).
 *
 * @param towerId башня, в которой лежит блок или предмет (для TOWER — её же id)
 * @param segId   слой башни (кофр или стопка) для блока и предмета
 */
public record RoomSelection(Type type, String id, String towerId, String segId) {

    public enum Type {
        TOWER, RITEM, SITEM, FURN, LOOSE, RUN
    }

    public static RoomSelection tower(String id) {
        return new RoomSelection(Type.TOWER, id, id, null);
    }

    public static RoomSelection rackItem(String id, String towerId, String segId) {
        return new RoomSelection(Type.RITEM, id, towerId, segId);
    }

    public static RoomSelection stackItem(String id, String towerId, String segId) {
        return new RoomSelection(Type.SITEM, id, towerId, segId);
    }

    public static RoomSelection furniture(String id) {
        return new RoomSelection(Type.FURN, id, null, null);
    }

    public static RoomSelection loose(String id) {
        return new RoomSelection(Type.LOOSE, id, null, null);
    }

    public static RoomSelection run(String id) {
        return new RoomSelection(Type.RUN, id, null, null);
    }

    /** Относится ли выбор к башне {@code towerId} (сама башня или её блок/предмет). */
    public boolean touchesTower(String towerId) {
        return this.towerId != null && this.towerId.equals(towerId);
    }
}
