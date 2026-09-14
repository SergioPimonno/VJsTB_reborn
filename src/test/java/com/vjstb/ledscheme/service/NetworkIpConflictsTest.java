package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import org.junit.jupiter.api.Test;

/** Round 8: адрес живёт на {@link NetworkAttachment}, не на самом устройстве —
 *  конфликт проверяется на уровне ОДНОГО подключения относительно всех ДРУГИХ
 *  подключений ТОЙ ЖЕ сети во всём {@link NetworkManagerPlan} (см. javadoc
 *  {@link NetworkIpConflicts}), а не по плоскому списку устройств одной сети,
 *  как раньше (у модели больше нет такого списка). */
class NetworkIpConflictsTest {

    private static final String NET_A = "net-a";

    private NetworkManagerPlan planWith(NetworkDevicePlacement... devices) {
        NetworkManagerPlan plan = new NetworkManagerPlan();
        for (NetworkDevicePlacement d : devices) {
            plan.getDevices().add(d);
        }
        return plan;
    }

    private NetworkDevicePlacement deviceWithIp(String networkId, String ip) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        NetworkAttachment a = new NetworkAttachment(networkId);
        a.setIpAddress(ip);
        p.getAttachments().add(a);
        return p;
    }

    @Test
    void noConflictWhenIpIsUnique() {
        NetworkDevicePlacement a = deviceWithIp(NET_A, "192.168.1.10");
        NetworkDevicePlacement b = deviceWithIp(NET_A, "192.168.1.11");
        NetworkManagerPlan plan = planWith(a, b);

        assertFalse(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
        assertFalse(NetworkIpConflicts.hasConflict(plan, b, b.getAttachments().get(0)));
    }

    @Test
    void conflictWhenTwoDevicesShareTheSameIpInTheSameNetwork() {
        NetworkDevicePlacement a = deviceWithIp(NET_A, "192.168.1.10");
        NetworkDevicePlacement b = deviceWithIp(NET_A, "192.168.1.10");
        NetworkManagerPlan plan = planWith(a, b);

        assertTrue(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
        assertTrue(NetworkIpConflicts.hasConflict(plan, b, b.getAttachments().get(0)));
    }

    @Test
    void sameIpInDifferentNetworksIsNotAConflict() {
        NetworkDevicePlacement a = deviceWithIp("net-a", "192.168.1.10");
        NetworkDevicePlacement b = deviceWithIp("net-b", "192.168.1.10");
        NetworkManagerPlan plan = planWith(a, b);

        assertFalse(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
        assertFalse(NetworkIpConflicts.hasConflict(plan, b, b.getAttachments().get(0)));
    }

    @Test
    void blankIpNeverConflicts() {
        NetworkDevicePlacement a = deviceWithIp(NET_A, "");
        NetworkDevicePlacement b = deviceWithIp(NET_A, "");
        NetworkManagerPlan plan = planWith(a, b);

        assertFalse(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
        assertFalse(NetworkIpConflicts.hasConflict(plan, b, b.getAttachments().get(0)));
    }

    @Test
    void singleDeviceNeverConflictsWithItself() {
        NetworkDevicePlacement a = deviceWithIp(NET_A, "192.168.1.10");
        NetworkManagerPlan plan = planWith(a);
        assertFalse(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
    }

    @Test
    void deviceWithTwoAttachmentsToTheSameNetworkDoesNotConflictWithItself() {
        // Не реалистичный кейс в UI (одно устройство — одно подключение на сеть), но
        // модель этого не запрещает -- убеждаемся, что "другое" в проверке означает
        // "другое УСТРОЙСТВО", а не "другое подключение", иначе такой блок ложно
        // конфликтовал бы сам с собой.
        NetworkDevicePlacement a = new NetworkDevicePlacement();
        NetworkAttachment a1 = new NetworkAttachment(NET_A);
        a1.setIpAddress("192.168.1.10");
        NetworkAttachment a2 = new NetworkAttachment(NET_A);
        a2.setIpAddress("192.168.1.10");
        a.getAttachments().add(a1);
        a.getAttachments().add(a2);
        NetworkManagerPlan plan = planWith(a);

        assertFalse(NetworkIpConflicts.hasConflict(plan, a, a1));
    }

    @Test
    void ipComparisonIsTrimmedAndCaseInsensitive() {
        NetworkDevicePlacement a = deviceWithIp(NET_A, " 192.168.1.10 ");
        NetworkDevicePlacement b = deviceWithIp(NET_A, "192.168.1.10");
        NetworkManagerPlan plan = planWith(a, b);
        assertTrue(NetworkIpConflicts.hasConflict(plan, a, a.getAttachments().get(0)));
    }

    @Test
    void hasAnyConflictChecksAllAttachmentsOfADevice() {
        NetworkDevicePlacement a = new NetworkDevicePlacement();
        NetworkAttachment okAttachment = new NetworkAttachment("net-a");
        okAttachment.setIpAddress("192.168.1.10");
        NetworkAttachment conflictingAttachment = new NetworkAttachment("net-b");
        conflictingAttachment.setIpAddress("192.168.2.10");
        a.getAttachments().add(okAttachment);
        a.getAttachments().add(conflictingAttachment);

        NetworkDevicePlacement b = deviceWithIp("net-b", "192.168.2.10");

        NetworkManagerPlan plan = planWith(a, b);
        assertTrue(NetworkIpConflicts.hasAnyConflict(plan, a));
        assertTrue(NetworkIpConflicts.hasAnyConflict(plan, b));
    }
}
