package com.vjstb.ledscheme.settings;

/**
 * Режим отрисовки разъёмов узла общей схемы (Task #2/v1.6) — независимая ось от
 * {@link UserProfile#isSocketWiringEnabled()} (та решает, цепляется ли линия связи
 * за конкретное гнездо или за узел целиком; эта — только про то, КАК гнёзда рисуются
 * на блоке). GROUPED — как было раньше: одна строка на группу разъёмов одного типа
 * с текстом «N×Тип» (например, дистрибьютор с 6 разъёмами — одна строка «6×CEE 32A»).
 * INDIVIDUAL — та же группа разворачивается в N отдельных строк-гнёзд, каждое своя
 * точка подключения — нагляднее для коммутации отдельных линий на многоканальном
 * оборудовании, когда важно видеть каждый физический разъём по отдельности.
 */
public enum ConnectorDisplayMode {
    GROUPED("Группой по типу"),
    INDIVIDUAL("Каждый разъём отдельно");

    private final String label;

    ConnectorDisplayMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
