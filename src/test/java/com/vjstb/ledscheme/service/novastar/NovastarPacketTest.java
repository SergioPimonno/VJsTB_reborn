package com.vjstb.ledscheme.service.novastar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Кодирование/декодирование пакета NovaStar (см. {@link NovastarPacket}
 *  class-javadoc за статусом доверия). {@link #encodeMatchesReferenceWriteVector}
 *  — единственный тест здесь, подтверждённый НЕЗАВИСИМЫМ источником (байтовый
 *  вектор из собственного теста {@code packet.spec.ts} проекта {@code
 *  sarakusha/novastar}, не выведен из нашего же кода) — остальные тесты
 *  проверяют только ВНУТРЕННЮЮ согласованность (round-trip), это НЕ то же
 *  самое, что подтверждение реальным протоколом. */
class NovastarPacketTest {

    @Test
    void encodeMatchesReferenceWriteVector() {
        // Вектор из packages/codec/src/packet.spec.ts проекта sarakusha/novastar:
        // serno=0x15, source=COMPUTER(0xfe), destination=0, deviceType=ReceivingCard(1),
        // port=0x11, rcvIndex=0x234, io=WRITE(1), address=0x02000001, data=[0x80].
        byte[] packet = NovastarPacket.encode(NovastarPacket.REQUEST_HEAD, 0, 0x15, NovastarPacket.SOURCE_COMPUTER,
                0, 1, 0x11, 0x234, NovastarPacket.IO_WRITE, 0x02000001L, new byte[]{(byte) 0x80});

        byte[] expected = {0x55, (byte) 0xaa, 0, 0x15, (byte) 0xfe, 0, 1, 0x11, 0x34, 0x2, 1, 0, 1, 0, 0, 2, 1, 0,
                (byte) 0x80, 0x35, 0x57};
        assertArrayEquals(expected, packet);
    }

    @Test
    void encodeReadRequestProducesRequestHeaderAndZeroPaddedData() {
        byte[] packet = NovastarPacket.encodeReadRequest(1, 0, 0, 0x2000050L, 1);

        assertEquals(21, packet.length, "18-байтный заголовок + 1 байт данных-пустышки + 2 байта checksum");
        assertEquals((byte) 0x55, packet[0]);
        assertEquals((byte) 0xaa, packet[1]);
        assertEquals(NovastarPacket.IO_READ, packet[10]);
        assertEquals(0, packet[18], "данные запроса на чтение -- нулевая пустышка");
    }

    @Test
    void decodeResponseRoundTripsThroughEncode() {
        byte[] responseBytes = NovastarPacket.encode(NovastarPacket.RESPONSE_HEAD, 0, 7, 0, 0xfe, 0, 0, 0,
                NovastarPacket.IO_READ, 0x2000050L, new byte[]{5, 0});

        NovastarPacket.Response response = NovastarPacket.decodeResponse(responseBytes);

        assertNotNull(response);
        assertEquals(7, response.serno());
        assertEquals(0x2000050L, response.address());
        assertEquals(5L, NovastarPacket.decodeUIntLE(response.data()));
    }

    @Test
    void decodeResponseRejectsCorruptedChecksum() {
        byte[] responseBytes = NovastarPacket.encode(NovastarPacket.RESPONSE_HEAD, 0, 1, 0, 0, 0, 0, 0,
                NovastarPacket.IO_READ, 0, new byte[]{1});
        responseBytes[responseBytes.length - 1] ^= (byte) 0xff; // портим последний байт checksum

        assertNull(NovastarPacket.decodeResponse(responseBytes));
    }

    @Test
    void decodeResponseRejectsRequestHeader() {
        byte[] requestBytes = NovastarPacket.encodeReadRequest(1, 0, 0, 0, 1);

        assertNull(NovastarPacket.decodeResponse(requestBytes), "REQUEST_HEAD -- не ответ, а запрос");
    }

    @Test
    void decodeUIntLeIsLittleEndian() {
        assertEquals(0x0102L, NovastarPacket.decodeUIntLE(new byte[]{0x02, 0x01}));
        assertEquals(0x01020304L, NovastarPacket.decodeUIntLE(new byte[]{0x04, 0x03, 0x02, 0x01}));
    }
}
