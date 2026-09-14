package com.vjstb.ledscheme.service.novastar;

import java.util.Arrays;

/**
 * Кодирование/декодирование одного пакета протокола NovaStar (TCP:5200,
 * используется NovaLCT и контроллерами вроде MCTRL4k) — недокументированный
 * протокол вендора, формат восстановлен по реверс-инжинерингу проекта
 * {@code https://github.com/sarakusha/novastar} (packages/codec/src/Packet.ts).
 *
 * <p>Заголовок (18 байт) + данные переменной длины ({@code length} байт) +
 * контрольная сумма (2 байта): head(u16LE) / ack(u8) / serno(u8) / source(u8)
 * / destination(u8) / deviceType(u8) / port(u8) / rcvIndex(u16LE) / io(u8) /
 * reserved(u8, пропускается) / address(u32LE) / length(u16LE) / data(length
 * байт) / checksum(u16LE). Итоговый размер пакета — {@code 20 + length}.
 *
 * <p><b>"CRC16" в исходном проекте — НЕ настоящий CRC</b>, несмотря на имя:
 * обычная сумма байт со 2-го (ack) по последний байт {@code data}
 * включительно, с затравкой {@code 0x5555}, по модулю 65536, записанная как
 * little-endian.
 *
 * <p><b>Статус доверия к формату (важно перед использованием)</b> — низкий
 * уровень (заголовок/checksum) подтверждён БАЙТ В БАЙТ: у исходного проекта
 * есть собственный тест ({@code packet.spec.ts}) с готовым байтовым вектором
 * для WRITE-запроса, {@link NovastarPacketTest} воспроизводит его через
 * {@link #encode} и сверяет побайтово — это НЕ предположение, а
 * подтверждённый факт. READ-запрос ({@link #encodeReadRequest}) выведен из
 * того же {@code encode} по СТРУКТУРНОЙ аналогии (общий Struct на read/write
 * в исходном проекте подразумевает одинаковый размер {@code data} по обе
 * стороны) — эта часть уже НЕ подтверждена независимым тестовым вектором,
 * только внутренней согласованностью (round-trip encode→decode).
 * <b>Конкретные адреса регистров</b> (порт включён / число откликнувшихся
 * карт, см. {@code NovastarAddresses}) взяты из декомпилированных .dll
 * вендора тем же исходным проектом — то есть не подобраны вручную, но
 * СЕМАНТИКА возвращаемых по ним значений на реальном контроллере не
 * проверялась (в исходном проекте для них тоже нет теста/перехваченного
 * трафика). См. {@code model.NetworkDevicePlacement#isNovastarStatusEnabled}
 * javadoc — функция экспериментальная, деградирует до "нет данных" на любую
 * ошибку, не показывает ложных статусов.
 */
public final class NovastarPacket {

    public static final int REQUEST_HEAD = 0xaa55;
    public static final int RESPONSE_HEAD = 0x55aa;
    public static final int SOURCE_COMPUTER = 0xfe;
    public static final int IO_READ = 0;
    public static final int IO_WRITE = 1;

    private NovastarPacket() {
    }

    /** Собирает пакет любого вида (запрос/ответ, чтение/запись) — общий
     *  примитив, которым проверен байт в байт (см. {@link NovastarPacketTest}
     *  для WRITE-вектора) и через который выражены {@link
     *  #encodeReadRequest}. */
    public static byte[] encode(int head, int ack, int serno, int source, int destination, int deviceType,
                                int port, int rcvIndex, int io, long address, byte[] data) {
        int length = data.length;
        byte[] packet = new byte[20 + length];
        writeU16LE(packet, 0, head);
        packet[2] = (byte) ack;
        packet[3] = (byte) serno;
        packet[4] = (byte) source;
        packet[5] = (byte) destination;
        packet[6] = (byte) deviceType;
        packet[7] = (byte) port;
        writeU16LE(packet, 8, rcvIndex);
        packet[10] = (byte) io;
        packet[11] = 0; // reserved
        writeU32LE(packet, 12, address);
        writeU16LE(packet, 16, length);
        System.arraycopy(data, 0, packet, 18, length);
        writeU16LE(packet, 18 + length, checksum(packet));
        return packet;
    }

    /** Запрос НА ЧТЕНИЕ регистра {@code address} — {@code readLength} байт
     *  данных на СТОРОНЕ ЗАПРОСА нулевые (пустышка, см. class-javadoc про то,
     *  почему это структурная аналогия, не подтверждённый факт), {@code
     *  readLength} в заголовке — сколько байт ожидается в ответе. */
    public static byte[] encodeReadRequest(int serno, int destination, int deviceType, long address,
                                            int readLength) {
        return encode(REQUEST_HEAD, 0, serno, SOURCE_COMPUTER, destination, deviceType, 0, 0, IO_READ, address,
                new byte[readLength]);
    }

    public record Response(int ack, int serno, int destination, long address, byte[] data) {
    }

    /** Разбирает и проверяет ОТВЕТНЫЙ пакет — {@code null}, если заголовок не
     *  {@link #RESPONSE_HEAD}, длины не хватает, или контрольная сумма не
     *  сошлась (защитный разбор — отклоняет молча, не бросает исключение,
     *  тот же принцип, что декодер исходного проекта на мусор в потоке). */
    public static Response decodeResponse(byte[] raw) {
        if (raw.length < 20) {
            return null;
        }
        if (readU16LE(raw, 0) != RESPONSE_HEAD) {
            return null;
        }
        int length = readU16LE(raw, 16);
        int total = 20 + length;
        if (raw.length < total) {
            return null;
        }
        if (checksum(raw) != readU16LE(raw, 18 + length)) {
            return null;
        }
        int ack = raw[2] & 0xff;
        int serno = raw[3] & 0xff;
        int destination = raw[5] & 0xff;
        long address = readU32LE(raw, 12);
        byte[] data = Arrays.copyOfRange(raw, 18, 18 + length);
        return new Response(ack, serno, destination, address, data);
    }

    /** Сумма байт [2 .. конец data) с затравкой 0x5555 по модулю 65536 — см.
     *  class-javadoc про то, почему это НЕ настоящий CRC16 несмотря на имя в
     *  исходном проекте. Читает {@code length} из фиксированного смещения 16
     *  самого буфера — вызывающая сторона должна успеть записать его туда
     *  ДО вызова (см. {@link #encode}: length пишется перед checksum). */
    private static int checksum(byte[] packet) {
        int length = readU16LE(packet, 16);
        int sum = 0x5555;
        for (int i = 2; i < 18 + length; i++) {
            sum = (sum + (packet[i] & 0xff)) & 0xffff;
        }
        return sum;
    }

    private static void writeU16LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static void writeU32LE(byte[] buf, int offset, long value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >>> 8) & 0xff);
        buf[offset + 2] = (byte) ((value >>> 16) & 0xff);
        buf[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static int readU16LE(byte[] buf, int offset) {
        return (buf[offset] & 0xff) | ((buf[offset + 1] & 0xff) << 8);
    }

    private static long readU32LE(byte[] buf, int offset) {
        return (buf[offset] & 0xffL) | ((buf[offset + 1] & 0xffL) << 8)
                | ((buf[offset + 2] & 0xffL) << 16) | ((buf[offset + 3] & 0xffL) << 24);
    }

    /** Little-endian беззнаковое значение из {@code data} (1-4 байта) — тот
     *  же приём, что {@code decodeUIntLE} исходного проекта. */
    public static long decodeUIntLE(byte[] data) {
        long value = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            value = (value << 8) | (data[i] & 0xff);
        }
        return value;
    }
}
