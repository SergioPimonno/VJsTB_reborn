package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkManagerPlan;

/**
 * Обнаружение конфликтов IP-адресов внутри ОДНОЙ сети (запрос пользователя:
 * "конфликты IP — хорошая идея, давай добавим", после обсуждения доработок
 * Сетевого менеджера — "ловит опечатки до выезда на площадку"). Конфликт —
 * два подключения ({@link NetworkAttachment}) ОДНОЙ и той же сети с
 * одинаковым непустым адресом, принадлежащие РАЗНЫМ устройствам; сравнение НЕ
 * зависит от регистра и игнорирует пробелы по краям.
 *
 * <p><b>Round 8</b> — раньше проверка шла по плоскому списку устройств ОДНОЙ
 * {@code Network} (та физически владела своими устройствами). Теперь адрес
 * живёт на {@link NetworkAttachment}, а устройство — общий список плана,
 * которое может иметь НЕСКОЛЬКО подключений (к разным сетям) — конфликт
 * поэтому проверяется на уровне ОДНОГО подключения относительно ВСЕХ ДРУГИХ
 * подключений ТОЙ ЖЕ сети во всём плане, а не относительно списка устройств
 * одной сети (такого списка на модели больше не существует, см. {@code
 * service.NetworkTopology}). Устройство само с собой не сравнивается, но ДВА
 * РАЗНЫХ подключения ОДНОГО и того же устройства к ДВУМ РАЗНЫМ сетям —
 * конфликтом не считаются вообще (проверка изначально ограничена одной
 * сетью, см. {@link #hasConflict}).
 */
public final class NetworkIpConflicts {

    private NetworkIpConflicts() {
    }

    /** {@code true}, если у {@code attachment} непустой IP и хотя бы ОДНО ДРУГОЕ
     *  подключение ДРУГОГО устройства к ТОЙ ЖЕ сети ({@link
     *  NetworkAttachment#getNetworkId()}) имеет тот же адрес. */
    public static boolean hasConflict(NetworkManagerPlan plan, NetworkDevicePlacement device,
                                       NetworkAttachment attachment) {
        String ip = attachment.getIpAddress() == null ? "" : attachment.getIpAddress().trim();
        if (ip.isEmpty() || attachment.getNetworkId() == null) {
            return false;
        }
        for (NetworkDevicePlacement other : plan.getDevices()) {
            if (other == device) {
                continue;
            }
            for (NetworkAttachment a : other.getAttachments()) {
                if (!attachment.getNetworkId().equals(a.getNetworkId())) {
                    continue;
                }
                String otherIp = a.getIpAddress() == null ? "" : a.getIpAddress().trim();
                if (ip.equalsIgnoreCase(otherIp)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code true}, если У ЛЮБОГО подключения {@code device} есть конфликт (см.
     *  {@link #hasConflict(NetworkManagerPlan, NetworkDevicePlacement,
     *  NetworkAttachment)}) — для подсветки блока целиком на канвасе, где
     *  адреса по сетям не показываются по отдельности. */
    public static boolean hasAnyConflict(NetworkManagerPlan plan, NetworkDevicePlacement device) {
        for (NetworkAttachment a : device.getAttachments()) {
            if (hasConflict(plan, device, a)) {
                return true;
            }
        }
        return false;
    }
}
