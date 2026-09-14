package com.vjstb.ledscheme.service.novastar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Высокоуровневый опрос статуса ВСЕХ видео-портов ОДНОГО контроллера — по
 * одному TCP-запросу на регистр через {@link NovastarClient} (новое
 * соединение на каждый запрос, см. его javadoc). ЭКСПЕРИМЕНТАЛЬНО, см. {@link
 * NovastarPacket} class-javadoc про статус доверия к формату/семантике —
 * вызывается из {@code ui.NetworkManagerPanel} в фоновом потоке (НЕ на EDT,
 * каждый порт — минимум два TCP-round-trip'а, суммарно может занять заметное
 * время на медленной сети).
 */
public final class NovastarPortStatusService {

    private NovastarPortStatusService() {
    }

    /** {@code cardCount} — {@code null}, если регистр "включён" ответил, но
     *  регистр "число карт" — нет (частичный ответ, оставляем как есть,
     *  а не подставляем 0 — 0 карт и "не удалось узнать" не одно и то же). */
    public record PortStatus(boolean enabled, Long cardCount) {
    }

    /** {@code portCount} — число ВЫХОДНЫХ портов ВИДЕО контроллера ({@code
     *  ControllerType#effectivePortCount()}), НЕ порт управления Сетевого
     *  менеджера (см. {@code model.NetworkDevicePlacement
     *  #isNovastarStatusEnabled} javadoc). Возвращает статус ТОЛЬКО тех
     *  портов, для которых пришёл ответ хотя бы на "включён" — недоступные
     *  порты в карте отсутствуют, а не присутствуют с "неизвестным" статусом
     *  (тот же принцип, что фоновый пинг: "если есть информация"). Ключ
     *  карты — номер порта 1-based. */
    public static Map<Integer, PortStatus> readAll(String host, int portCount, int timeoutMs) {
        Map<Integer, PortStatus> result = new LinkedHashMap<>();
        for (int i = 0; i < portCount; i++) {
            long enableAddr = NovastarAddresses.PORT_ENABLE_ADDR + NovastarAddresses.PORT_ENABLE_OCCUPANCY * i;
            Optional<Long> enable = NovastarClient.readRegister(host, NovastarClient.DEFAULT_PORT, 0, 0, enableAddr,
                    1, timeoutMs);
            if (enable.isEmpty()) {
                continue;
            }
            long cardAddr = NovastarAddresses.NUMBER_OF_CARD_ADDR + i * NovastarAddresses.NUM_OF_CARD_OCCUPANCY
                    + NovastarAddresses.CARD_TYPE_SCANNER * NovastarAddresses.CARD_TYPE_STRIDE;
            Optional<Long> cards = NovastarClient.readRegister(host, NovastarClient.DEFAULT_PORT, 0, 0, cardAddr,
                    (int) NovastarAddresses.CARD_TYPE_STRIDE, timeoutMs);
            result.put(i + 1, new PortStatus(enable.get() != 0, cards.orElse(null)));
        }
        return result;
    }
}
