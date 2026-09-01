package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import java.util.List;

/**
 * Обнаружение конфликтов IP-адресов внутри ОДНОЙ {@code Network} (запрос
 * пользователя: "конфликты IP — хорошая идея, давай добавим", после
 * обсуждения доработок Сетевого менеджера — "ловит опечатки до выезда на
 * площадку"). Конфликт — два РАЗНЫХ устройства одной сети с одинаковым
 * непустым адресом; сравнение НЕ зависит от регистра (IPv4-адреса и так
 * цифровые, но метод написан так, чтобы не сломаться, если формат когда-то
 * расширят до hostname-подобных значений) и игнорирует пробелы по краям.
 *
 * <p>Конфликт проверяется ТОЛЬКО в пределах одной сети — у РАЗНЫХ {@code
 * Network} совпадающие адреса не считаются конфликтом (разные сети —
 * разные логические/физические сегменты, повторное использование
 * адресного пространства между ними — обычное дело, не ошибка). Оба
 * потребителя ({@code ui.NetworkCanvasPanel} — подсветка блока на канвасе,
 * {@code ui.NetworkAddressTableDialog} — подсветка строки в общей таблице)
 * вызывают этот метод СВОИМ списком устройств — списком ОДНОЙ сети, никогда
 * не смешанным списком нескольких сетей разом.
 */
public final class NetworkIpConflicts {

    private NetworkIpConflicts() {
    }

    /** {@code true}, если у {@code target} непустой IP и хотя бы одно ДРУГОЕ
     *  устройство из {@code devicesInSameNetwork} имеет тот же адрес. */
    public static boolean hasConflict(List<NetworkDevicePlacement> devicesInSameNetwork,
                                       NetworkDevicePlacement target) {
        String ip = target.getIpAddress() == null ? "" : target.getIpAddress().trim();
        if (ip.isEmpty()) {
            return false;
        }
        for (NetworkDevicePlacement other : devicesInSameNetwork) {
            if (other == target) {
                continue;
            }
            String otherIp = other.getIpAddress() == null ? "" : other.getIpAddress().trim();
            if (ip.equalsIgnoreCase(otherIp)) {
                return true;
            }
        }
        return false;
    }
}
