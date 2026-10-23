package com.vjstb.ledscheme.service.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomLibRef;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Приведение позиций существующих библиотек (пресеты оборудования схемы, сетевые устройства,
 * кофры) к виду Планнера: миллиметры → сантиметры, юниты, доли ширины, пометка «нет габаритов».
 * Запрос пользователя 2026-10-23: оборудование берётся из существующих библиотек, а позиции без
 * габаритов помечаются цветом — для этого адаптер обязан не терять такие позиции, а отдавать их
 * с условными размерами и списком недостающего.
 */
class RoomLibraryAdapterTest {

    private static PhysicalSpec rack(int u, double depthMm, double kg, double w, int twelfths) {
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.RACK);
        p.setRackUnits(u);
        p.setDepthMm(depthMm);
        p.setWeightKg(kg);
        p.setPowerW(w);
        p.setWidthTwelfths(twelfths);
        return p;
    }

    private static EquipmentPreset preset(String name, PhysicalSpec ph) {
        EquipmentPreset e = new EquipmentPreset();
        e.setName(name);
        e.setPhysical(ph);
        return e;
    }

    @Test
    void rackPresetBecomesARackItemInCentimetres() {
        EquipmentPreset mctrl = preset("MCTRL4K", rack(2, 372, 4.6, 30, 12));
        RoomLibraryAdapter a = new RoomLibraryAdapter(List.of(mctrl), List.of(), List.of());
        RoomItemSpec s = a.resolve(new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, mctrl.getId()));
        assertEquals(RoomItemSpec.Type.RACK, s.type());
        assertEquals(2, s.rackUnits());
        assertEquals(37.2, s.depthCm(), 1e-9);
        assertEquals(2 * RoomUnits.U_CM, s.heightCm(), 1e-9);
        assertEquals(RoomUnits.RACK_WIDTH_CM, s.widthCm(), 1e-9);
        assertEquals(4.6, s.weightKg(), 1e-9);
        assertEquals(30, s.powerW(), 1e-9);
        assertTrue(s.complete());
    }

    @Test
    void halfWidthRackItemKeepsItsFractionAndNarrowerFace() {
        EquipmentPreset half = preset("Мультивьюер 1/2", rack(1, 200, 1.2, 15, 6));
        RoomItemSpec s = new RoomLibraryAdapter(List.of(half), List.of(), List.of())
                .resolve(new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, half.getId()));
        assertEquals(6, s.widthTwelfths());
        assertEquals(RoomUnits.RACK_WIDTH_CM / 2, s.widthCm(), 1e-9);
    }

    @Test
    void presetWithoutPhysicalBlockIsKeptButMarkedAsMissingDimensions() {
        EquipmentPreset bare = preset("H5", null);
        RoomLibraryAdapter a = new RoomLibraryAdapter(List.of(bare), List.of(), List.of());
        RoomItemSpec s = a.resolve(new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, bare.getId()));
        assertFalse(s.complete());
        assertEquals(List.of("габариты не указаны"), s.missing());
        assertEquals(1, a.all().size(), "позиция не пропадает из списка");
    }

    @Test
    void rackItemWithoutDepthIsIncomplete() {
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.RACK);
        p.setRackUnits(1);
        EquipmentPreset e = preset("Без глубины", p);
        RoomItemSpec s = new RoomLibraryAdapter(List.of(e), List.of(), List.of())
                .resolve(new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, e.getId()));
        assertEquals(List.of("глубина"), s.missing());
    }

    @Test
    void looseNetworkDeviceUsesAllThreeDimensions() {
        PhysicalSpec p = new PhysicalSpec();
        p.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        p.setWidthMm(200.0);
        p.setHeightMm(30.0);
        p.setDepthMm(120.0);
        NetworkDeviceType d = new NetworkDeviceType();
        d.setName("Коммутатор 8 портов");
        d.setPhysical(p);
        RoomItemSpec s = new RoomLibraryAdapter(List.of(), List.of(d), List.of())
                .resolve(new RoomLibRef(RoomLibRef.Kind.NETWORK_DEVICE, d.getId()));
        assertEquals(RoomItemSpec.Type.LOOSE, s.type());
        assertEquals(20, s.widthCm(), 1e-9);
        assertEquals(3, s.heightCm(), 1e-9);
        assertEquals(12, s.depthCm(), 1e-9);
        assertTrue(s.complete());
    }

    @Test
    void rackCaseNeedsUnitsAndInnerDepthFromThePhysicalBlock() {
        CaseType rackCase = new CaseType();
        rackCase.setName("Рэк-кофр 12U");
        rackCase.setWidthMm(600);
        rackCase.setLengthMm(720);
        rackCase.setHeightMm(690);
        rackCase.setWeightKg(18);
        PhysicalSpec p = new PhysicalSpec();
        p.setRackUnits(12);
        p.setDepthMm(550.0);
        rackCase.setPhysical(p);
        CaseType transport = new CaseType();
        transport.setName("Кофр под кабинеты");
        RoomLibraryAdapter a = new RoomLibraryAdapter(List.of(), List.of(), List.of(rackCase, transport));
        RoomItemSpec rc = a.resolve(new RoomLibRef(RoomLibRef.Kind.CASE, rackCase.getId()));
        assertEquals(RoomItemSpec.Type.CASE, rc.type());
        assertEquals(12, rc.rackUnits());
        assertEquals(55, rc.innerDepthCm(), 1e-9);
        assertEquals(60, rc.widthCm(), 1e-9, "ширина на плане — widthMm");
        assertEquals(72, rc.depthCm(), 1e-9, "глубина на плане — lengthMm");
        assertEquals(69, rc.heightCm(), 1e-9);
        assertTrue(rc.complete());
        RoomItemSpec plain = a.resolve(new RoomLibRef(RoomLibRef.Kind.CASE, transport.getId()));
        assertEquals(2, plain.missing().size(), "кофр без юнитов — не рэк-кофр");
    }

    @Test
    void unknownReferenceDoesNotThrow() {
        RoomItemSpec s = new RoomLibraryAdapter(List.of(), List.of(), List.of())
                .resolve(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, "нет такой"));
        assertFalse(s.complete());
    }
}
