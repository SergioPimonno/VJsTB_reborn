package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог Планнера пультовой над существующими библиотеками приложения (PLAN.md, раздел 5):
 * пресеты оборудования общей схемы ({@link EquipmentPreset}), сетевые устройства
 * ({@link NetworkDeviceType}) и кофры ({@link CaseType}) — общие и личные списки вместе.
 * Размеры и вес берутся из физического блока позиции ({@link PhysicalSpec}); физика библиотеки
 * в миллиметрах, планнер считает в сантиметрах — пересчёт ровно здесь.
 *
 * <p>Позиция без физического блока (или без нужных полей) не пропадает: она попадает в каталог
 * с условными размерами и пометкой в {@link RoomItemSpec#missing()} — так Планнер выделяет её
 * цветом и предлагает пользователю указать габариты (запрос 2026-10-23). Рэк-кофром считается
 * {@code CaseType} с заполненными юнитами в физическом блоке; для таких кофров принято: ширина на
 * плане = {@code widthMm}, глубина = {@code lengthMm}, высота = {@code heightMm} (размеры кофра
 * для перевозки; внутренняя глубина — {@code physical.depthMm}).
 *
 * <p>Виды библиотеки для мебели, станков, мониторов и прочего оборудования пультовой добавляются
 * отдельным треком; ссылки на них пока резолвятся как «неизвестная позиция».
 */
public class RoomLibraryAdapter implements RoomCatalog {

    private final Map<RoomLibRef, RoomItemSpec> items = new LinkedHashMap<>();

    public RoomLibraryAdapter(List<EquipmentPreset> presets, List<NetworkDeviceType> devices, List<CaseType> cases) {
        for (CaseType c : cases) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.CASE, c.getId());
            items.put(ref, caseSpec(ref, c));
        }
        for (EquipmentPreset p : presets) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, p.getId());
            items.put(ref, deviceSpec(ref, p.getName(), p.getCategory() != null ? p.getCategory().getLabel() : "",
                    p.getPhysical()));
        }
        for (NetworkDeviceType d : devices) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.NETWORK_DEVICE, d.getId());
            items.put(ref, deviceSpec(ref, d.getName(), "Сеть", d.getPhysical()));
        }
    }

    /** Каталог из общих и личных списков библиотеки текущей модели. */
    public static RoomLibraryAdapter of(AppModel model) {
        return new RoomLibraryAdapter(model.getEquipmentPresets(), model.getNetworkDeviceTypes(), model.getCaseTypes());
    }

    @Override
    public RoomItemSpec resolve(RoomLibRef ref) {
        RoomItemSpec s = ref != null ? items.get(ref) : null;
        return s != null ? s : RoomItemSpec.unknown(ref);
    }

    @Override
    public List<RoomItemSpec> all() {
        return new ArrayList<>(items.values());
    }

    private static RoomItemSpec deviceSpec(RoomLibRef ref, String name, String category, PhysicalSpec ph) {
        if (ph == null) {
            return new RoomItemSpec(ref, RoomItemSpec.Type.LOOSE, RoomItemSpec.Role.OTHER, name, category, 10, 5, 10, 0,
                    12, 0, 0, 0, null, List.of("габариты не указаны"));
        }
        List<String> missing = ph.missingFields();
        double kg = ph.getWeightKg() != null ? ph.getWeightKg() : 0;
        double p = ph.getPowerW() != null ? ph.getPowerW() : 0;
        if (ph.getFormFactor() == PhysicalSpec.FormFactor.RACK) {
            int u = ph.getRackUnits() != null ? ph.getRackUnits() : 1;
            double depth = ph.getDepthMm() != null ? RoomUnits.mmToCm(ph.getDepthMm()) : 30;
            return new RoomItemSpec(ref, RoomItemSpec.Type.RACK, RoomItemSpec.Role.OTHER, name, category,
                    RoomUnits.RACK_WIDTH_CM * ph.effectiveWidthTwelfths() / 12.0, u * RoomUnits.U_CM, depth, u,
                    ph.effectiveWidthTwelfths(), 0, kg, p, null, missing);
        }
        double w = ph.getWidthMm() != null ? RoomUnits.mmToCm(ph.getWidthMm()) : 10;
        double h = ph.getHeightMm() != null ? RoomUnits.mmToCm(ph.getHeightMm()) : 5;
        double d = ph.getDepthMm() != null ? RoomUnits.mmToCm(ph.getDepthMm()) : 10;
        return new RoomItemSpec(ref, RoomItemSpec.Type.LOOSE, RoomItemSpec.Role.OTHER, name, category, w, h, d, 0, 12, 0,
                kg, p, null, missing);
    }

    private static RoomItemSpec caseSpec(RoomLibRef ref, CaseType c) {
        PhysicalSpec ph = c.getPhysical();
        List<String> missing = new ArrayList<>();
        int u = 0;
        double inner = 0;
        if (ph == null || ph.getRackUnits() == null) {
            missing.add("юниты (U) — не рэк-кофр");
        } else {
            u = ph.getRackUnits();
        }
        if (ph == null || ph.getDepthMm() == null) {
            missing.add("внутренняя глубина");
        } else {
            inner = RoomUnits.mmToCm(ph.getDepthMm());
        }
        return new RoomItemSpec(ref, RoomItemSpec.Type.CASE, RoomItemSpec.Role.OTHER, c.getName(), "Кофры",
                RoomUnits.mmToCm(c.getWidthMm()), RoomUnits.mmToCm(c.getHeightMm()), RoomUnits.mmToCm(c.getLengthMm()), u,
                12, inner, c.getWeightKg(), 0, null, missing);
    }
}
