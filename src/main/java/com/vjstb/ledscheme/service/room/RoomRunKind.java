package com.vjstb.ledscheme.service.room;

/**
 * Тип трассы (кабельной трассы, по которой приходят сигналы на площадку): оптика, SDI, сеть,
 * силовая и прочая. Хранится в {@code RoomRun.kind} ключом; название и короткая метка берутся
 * отсюда. Цвета — дело рендера (токены прототипа {@code --c-optic}, {@code --c-sdi}…).
 */
public enum RoomRunKind {
    OPTIC("optic", "Оптика", "ОПТИКА"),
    SDI("sdi", "SDI", "SDI"),
    NET("net", "Сеть Cat6", "СЕТЬ"),
    PWR("pwr", "Силовая 230 В", "230 В"),
    MISC("misc", "Другая", "ТРАССА");

    private final String key;
    private final String title;
    private final String shortLabel;

    RoomRunKind(String key, String title, String shortLabel) {
        this.key = key;
        this.title = title;
        this.shortLabel = shortLabel;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String shortLabel() {
        return shortLabel;
    }

    /** Тип по ключу; неизвестный или пустой — {@link #MISC}. */
    public static RoomRunKind of(String key) {
        for (RoomRunKind k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        return MISC;
    }

    @Override
    public String toString() {
        return title;
    }
}
