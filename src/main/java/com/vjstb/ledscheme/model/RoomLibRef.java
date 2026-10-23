package com.vjstb.ledscheme.model;

/**
 * Ссылка на позицию библиотеки из раскладки пультовой (см. {@link ControlRoomLayout}): вид
 * источника и его id. Раскладка хранит только ссылки и собственные переопределения, размеры
 * берутся из библиотеки в момент расчёта — так правка габаритов в библиотеке доходит до всех
 * раскладок (как {@code Screen.riggingHoistTypeId}).
 */
public class RoomLibRef {

    /** Источник позиции в библиотеке. */
    public enum Kind {
        /** {@code CaseType} — рэк-кофр. */
        CASE,
        /** {@code EquipmentPreset} — модель оборудования общей схемы (контроллер, сервер, конвертер…). */
        EQUIPMENT,
        /** {@code NetworkDeviceType} — сетевое устройство. */
        NETWORK_DEVICE,
        /** Оборудование, которого нет в схеме (конвертеры, ИБП, патч-панели…) — вид библиотеки добавляется отдельным треком. */
        ROOM_EQUIPMENT,
        /** Мебель, станки, мониторы — вид библиотеки добавляется отдельным треком. */
        ROOM_FURNITURE
    }

    /** Откуда позиция: кофр, пресет оборудования схемы, сетевое устройство, либо новые виды для планнера. */
    private Kind kind;
    /** Идентификатор позиции в соответствующем списке библиотеки. */
    private String id;

    public RoomLibRef() {
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RoomLibRef(Kind kind, String id) {
        this.kind = kind;
        this.id = id;
    }

    /** Ключ для карт и сообщений: {@code KIND:id}. Название без префикса get — иначе Jackson записал бы его в JSON. */
    public String key() {
        return kind + ":" + id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RoomLibRef r && r.kind == kind && java.util.Objects.equals(r.id, id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, id);
    }

}
