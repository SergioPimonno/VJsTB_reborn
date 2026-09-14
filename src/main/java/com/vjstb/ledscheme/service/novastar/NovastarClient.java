package com.vjstb.ledscheme.service.novastar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

/**
 * Синхронный ОДНОРАЗОВЫЙ TCP-запрос к контроллеру NovaStar (порт 5200 по
 * умолчанию) — новое соединение НА КАЖДЫЙ вызов {@link #readRegister}, без
 * держания постоянной сессии между опросами (тот же принцип простоты, что
 * {@code service.NetworkScanService}/{@code NetworkPingService} — ни то, ни
 * другое тоже не держит долгоживущих соединений к устройствам площадки).
 * ЭКСПЕРИМЕНТАЛЬНО — см. {@link NovastarPacket} class-javadoc про статус
 * доверия к формату/семантике.
 */
public final class NovastarClient {

    public static final int DEFAULT_PORT = 5200;

    private static int sernoCounter;

    private NovastarClient() {
    }

    /** Читает регистр {@code address} длиной {@code readLength} байт (1, 2
     *  или 4 — см. {@link NovastarPacket#decodeUIntLE}) — {@code
     *  Optional.empty()} на ЛЮБУЮ ошибку (нет соединения, таймаут, битый
     *  ответ) — статус "нет данных", не проброс исключения выше (тот же
     *  принцип, что пинг: недоступность устройства — обычный результат, а не
     *  аварийная ситуация приложения). */
    public static Optional<Long> readRegister(String host, int port, int destination, int deviceType, long address,
                                               int readLength, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] request = NovastarPacket.encodeReadRequest(nextSerno(), destination, deviceType, address,
                    readLength);
            out.write(request);
            out.flush();

            byte[] header = readFully(in, 18);
            if (header == null) {
                return Optional.empty();
            }
            int length = (header[16] & 0xff) | ((header[17] & 0xff) << 8);
            byte[] rest = readFully(in, length + 2);
            if (rest == null) {
                return Optional.empty();
            }
            byte[] full = new byte[18 + length + 2];
            System.arraycopy(header, 0, full, 0, 18);
            System.arraycopy(rest, 0, full, 18, rest.length);

            NovastarPacket.Response response = NovastarPacket.decodeResponse(full);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(NovastarPacket.decodeUIntLE(response.data()));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static synchronized int nextSerno() {
        sernoCounter = (sernoCounter + 1) & 0xff;
        return sernoCounter;
    }

    private static byte[] readFully(InputStream in, int count) throws IOException {
        if (count == 0) {
            return new byte[0];
        }
        byte[] buf = new byte[count];
        int total = 0;
        while (total < count) {
            int n = in.read(buf, total, count - total);
            if (n < 0) {
                return null;
            }
            total += n;
        }
        return buf;
    }
}
