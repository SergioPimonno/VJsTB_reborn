package com.vjstb.ledscheme.service.novastar;

/**
 * Адреса регистров NovaStar, нужные для чтения статуса ВИДЕО-портов вывода
 * (не путать с портом управления Сетевого менеджера, см. {@code
 * model.NetworkDevicePlacement#isNovastarStatusEnabled} javadoc) — константы
 * взяты из декомпилированных .dll вендора проектом {@code
 * sarakusha/novastar} (packages/native/generated/AddressMapping.ts —
 * механически извлечены из настоящего кода Novastar, не подобраны вручную),
 * см. {@link NovastarPacket} class-javadoc про статус доверия к СЕМАНТИКЕ
 * этих конкретных регистров (не проверена на реальном контроллере).
 */
public final class NovastarAddresses {

    private NovastarAddresses() {
    }

    /** Базовый адрес "порт включён" (1 байт на порт) — следующий порт на
     *  {@link #PORT_ENABLE_OCCUPANCY} байт дальше. */
    public static final long PORT_ENABLE_ADDR = 33_554_512L; // 0x2000050
    public static final long PORT_ENABLE_OCCUPANCY = 9;

    /** Базовый адрес "число откликнувшихся карт" — 2-байтное LE значение на
     *  комбинацию (порт, тип карты); {@link #NUM_OF_CARD_OCCUPANCY} — шаг
     *  адреса на СЛЕДУЮЩИЙ ПОРТ, {@link #CARD_TYPE_STRIDE} — шаг на
     *  СЛЕДУЮЩИЙ ТИП карты (и одновременно ширина одного значения в байтах —
     *  оба факта следуют из одной и той же величины в исходном проекте,
     *  регистр каждого типа карты — ровно 2 байта). */
    public static final long NUMBER_OF_CARD_ADDR = 51_380_224L; // 0x3100000
    public static final long NUM_OF_CARD_OCCUPANCY = 32; // 0x20
    public static final long CARD_TYPE_STRIDE = 2;
    /** "Приёмная карта" (Scanner Card) — тип, который нам нужен (не
     *  функциональные карты 1-15). */
    public static final int CARD_TYPE_SCANNER = 0;
}
