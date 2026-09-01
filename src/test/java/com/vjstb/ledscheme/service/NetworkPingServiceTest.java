package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Тесты только чистой функции построения команды ping — реальный запуск процесса
 *  намеренно НЕ тестируется (никакой сетевой активности в тестах, как и везде в
 *  этом проекте), см. class-javadoc {@link NetworkPingService}. Тест в ТОМ ЖЕ
 *  пакете, поэтому вызывает package-private {@code buildPingCommand} напрямую,
 *  без рефлексии. */
class NetworkPingServiceTest {

    @Test
    void windowsWrapsThroughCmdWithUtf8CodePageAndContinuousPing() {
        assertEquals(java.util.List.of("cmd", "/c", "chcp 65001>nul && ping -t 192.168.1.1"),
                NetworkPingService.buildPingCommand("Windows 11", "192.168.1.1"));
    }

    @Test
    void linuxUsesPlainContinuousPing() {
        assertEquals(java.util.List.of("ping", "10.0.0.5"),
                NetworkPingService.buildPingCommand("Linux", "10.0.0.5"));
    }

    @Test
    void macUsesPlainContinuousPing() {
        assertEquals(java.util.List.of("ping", "host.local"),
                NetworkPingService.buildPingCommand("Mac OS X", "host.local"));
    }
}
