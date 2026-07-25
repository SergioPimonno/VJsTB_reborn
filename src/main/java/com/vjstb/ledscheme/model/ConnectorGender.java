package com.vjstb.ledscheme.model;

/** Пол разъёма (общепринятая для силовой/сигнальной коммутации терминология
 *  «мама»/«папа») — используется при СБОРКЕ подписи комбинированного кабеля из
 *  двух выбранных разъёмов (см. библиотеку кабелей), а не как отдельное поле
 *  модели данных где-либо ещё. */
public enum ConnectorGender {
    MALE("папа"),
    FEMALE("мама");

    private final String label;

    ConnectorGender(String label) {
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
