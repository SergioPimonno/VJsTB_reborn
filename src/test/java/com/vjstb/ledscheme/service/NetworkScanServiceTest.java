package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Тесты чистых функций {@link NetworkScanService} — реальный запуск ping
 *  намеренно НЕ тестируется (никакой сетевой активности в тестах, как и везде
 *  в этом проекте, см. {@link NetworkPingServiceTest}). Тест в ТОМ ЖЕ пакете,
 *  вызывает package-private {@code buildOneShotPingCommand} напрямую. */
class NetworkScanServiceTest {

    @Test
    void expandRange_listsEveryAddressInclusive() {
        assertEquals(List.of("192.168.1.10", "192.168.1.11", "192.168.1.12"),
                NetworkScanService.expandRange("192.168.1.10", "192.168.1.12"));
    }

    @Test
    void expandRange_singleAddressWhenFromEqualsTo() {
        assertEquals(List.of("10.0.0.5"), NetworkScanService.expandRange("10.0.0.5", "10.0.0.5"));
    }

    @Test
    void expandRange_crossesOctetBoundaryWithoutRestriction() {
        // Запрос пользователя: "убери ограничение на маску /24" -- диапазон больше
        // не обязан оставаться в пределах одних первых трёх октетов.
        List<String> ips = NetworkScanService.expandRange("192.168.1.254", "192.168.2.2");
        assertEquals(List.of("192.168.1.254", "192.168.1.255", "192.168.2.0", "192.168.2.1", "192.168.2.2"), ips);
    }

    @Test
    void expandRange_rejectsFromGreaterThanTo() {
        assertThrows(IllegalArgumentException.class, () -> NetworkScanService.expandRange("192.168.1.20", "192.168.1.10"));
    }

    @Test
    void expandRange_rejectsTooWideRange() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NetworkScanService.expandRange("10.0.0.1", "10.10.0.1"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("слишком большой"));
    }

    @Test
    void expandRange_rejectsMalformedAddress() {
        assertThrows(IllegalArgumentException.class, () -> NetworkScanService.expandRange("не-адрес", "192.168.1.10"));
        assertThrows(IllegalArgumentException.class, () -> NetworkScanService.expandRange("192.168.1.1", "192.168.1.999"));
        assertThrows(IllegalArgumentException.class, () -> NetworkScanService.expandRange("192.168.1", "192.168.1.10"));
    }

    @Test
    void windowsUsesSinglePacketWithShortTimeout() {
        assertEquals(List.of("ping", "-n", "1", "-w", "400", "192.168.1.1"),
                NetworkScanService.buildOneShotPingCommand("Windows 11", "192.168.1.1"));
    }

    @Test
    void posixUsesSinglePacketWithShortTimeout() {
        assertEquals(List.of("ping", "-c", "1", "-W", "1", "10.0.0.5"),
                NetworkScanService.buildOneShotPingCommand("Linux", "10.0.0.5"));
    }
}
