package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkIpConflictsTest {

    private NetworkDevicePlacement deviceWithIp(String ip) {
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setIpAddress(ip);
        return p;
    }

    @Test
    void noConflictWhenIpIsUnique() {
        NetworkDevicePlacement a = deviceWithIp("192.168.1.10");
        NetworkDevicePlacement b = deviceWithIp("192.168.1.11");
        List<NetworkDevicePlacement> devices = List.of(a, b);

        assertFalse(NetworkIpConflicts.hasConflict(devices, a));
        assertFalse(NetworkIpConflicts.hasConflict(devices, b));
    }

    @Test
    void conflictWhenTwoDevicesShareTheSameIp() {
        NetworkDevicePlacement a = deviceWithIp("192.168.1.10");
        NetworkDevicePlacement b = deviceWithIp("192.168.1.10");
        List<NetworkDevicePlacement> devices = List.of(a, b);

        assertTrue(NetworkIpConflicts.hasConflict(devices, a));
        assertTrue(NetworkIpConflicts.hasConflict(devices, b));
    }

    @Test
    void blankIpNeverConflicts() {
        NetworkDevicePlacement a = deviceWithIp("");
        NetworkDevicePlacement b = deviceWithIp("");
        List<NetworkDevicePlacement> devices = List.of(a, b);

        assertFalse(NetworkIpConflicts.hasConflict(devices, a));
        assertFalse(NetworkIpConflicts.hasConflict(devices, b));
    }

    @Test
    void singleDeviceNeverConflictsWithItself() {
        NetworkDevicePlacement a = deviceWithIp("192.168.1.10");
        assertFalse(NetworkIpConflicts.hasConflict(List.of(a), a));
    }

    @Test
    void ipComparisonIsTrimmedAndCaseInsensitive() {
        NetworkDevicePlacement a = deviceWithIp(" 192.168.1.10 ");
        NetworkDevicePlacement b = deviceWithIp("192.168.1.10");
        assertTrue(NetworkIpConflicts.hasConflict(List.of(a, b), a));
    }
}
