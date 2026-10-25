package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог Планера пультовой над библиотеками приложения (PLAN.md, раздел 5): пресеты оборудования
 * общей схемы ({@link EquipmentPreset}), сетевые устройства ({@link NetworkDeviceType}), кофры
 * ({@link CaseType}), оборудование пультовой ({@link RoomEquipmentType}) и мебель/станки/мониторы
 * ({@link RoomFurnitureType}) — общие и личные списки вместе. Размеры и вес берутся из
 * физического блока позиции ({@link PhysicalSpec}); физика библиотеки в миллиметрах, планер
 * считает в сантиметрах — пересчёт ровно здесь.
 *
 * <p>Позиция без физического блока (или без нужных полей) не пропадает: она попадает в каталог
 * с условными размерами и пометкой в {@link RoomItemSpec#missing()} — так Планер выделяет её
 * цветом и предлагает пользователю указать габариты (запрос 2026-10-23). Рэк-кофром считается
 * {@code CaseType} с заполненными юнитами в физическом блоке; для таких кофров принято: ширина на
 * плане = {@code widthMm}, глубина = {@code lengthMm}, высота = {@code heightMm} (размеры кофра
 * для перевозки; внутренняя глубина — {@code physical.depthMm}).
 *
 * <p><b>Личные габариты.</b> Пользователь может указать габариты позиции сам
 * ({@code Library.physicalOverrides}, ключ {@link RoomLibRef#key()}). Они подставляются вместо
 * физического блока только пока общих данных не хватает ({@code missingFields} непусто или блока
 * нет): как только администратор заполнит позицию на сервере, общие данные главнее — иначе устаревшая
 * личная цифра тихо перебила бы справочник. Для мебели/станков/мониторов блок личных габаритов
 * заполняет ширину, глубину, высоту, вес и мощность (диагональ монитора берётся только из библиотеки).
 */
public class RoomLibraryAdapter implements RoomCatalog {

    private final Map<RoomLibRef, RoomItemSpec> items = new LinkedHashMap<>();
    private final Map<String, PhysicalSpec> overrides;
    /** Позиции, для которых сейчас действуют личные габариты. */
    private final java.util.Set<RoomLibRef> usedOverrides = new java.util.HashSet<>();

    public RoomLibraryAdapter(List<EquipmentPreset> presets, List<NetworkDeviceType> devices, List<CaseType> cases) {
        this(presets, devices, cases, List.of(), List.of(), Map.of());
    }

    public RoomLibraryAdapter(List<EquipmentPreset> presets, List<NetworkDeviceType> devices, List<CaseType> cases,
                              List<RoomEquipmentType> roomEquipment, List<RoomFurnitureType> furniture,
                              Map<String, PhysicalSpec> personalPhysical) {
        this.overrides = personalPhysical != null ? personalPhysical : Map.of();
        for (CaseType c : cases) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.CASE, c.getId());
            items.put(ref, caseSpec(ref, c, effective(ref, c.getPhysical(), RoomLibraryAdapter::caseMissing)));
        }
        for (EquipmentPreset p : presets) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.EQUIPMENT, p.getId());
            items.put(ref, deviceSpec(ref, p.getName(), p.getCategory() != null ? p.getCategory().getLabel() : "",
                    effective(ref, p.getPhysical(), PhysicalSpec::missingFields)));
        }
        for (NetworkDeviceType d : devices) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.NETWORK_DEVICE, d.getId());
            items.put(ref, deviceSpec(ref, d.getName(), "Сеть",
                    effective(ref, d.getPhysical(), PhysicalSpec::missingFields)));
        }
        for (RoomEquipmentType t : roomEquipment) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, t.getId());
            String category = t.getCategory() != null && !t.getCategory().isBlank() ? t.getCategory() : "Пультовая";
            items.put(ref, deviceSpec(ref, t.getName(), category,
                    effective(ref, t.getPhysical(), PhysicalSpec::missingFields)));
        }
        for (RoomFurnitureType t : furniture) {
            RoomLibRef ref = new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, t.getId());
            items.put(ref, furnitureSpec(ref, t));
        }
    }

    /** Каталог из общих и личных списков библиотеки текущей модели. */
    public static RoomLibraryAdapter of(AppModel model) {
        return new RoomLibraryAdapter(model.getEquipmentPresets(), model.getNetworkDeviceTypes(), model.getCaseTypes(),
                model.getRoomEquipmentTypes(), model.getRoomFurnitureTypes(), model.getPhysicalOverrides());
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

    /** Есть ли у позиции личные габариты, и действуют ли они сейчас (общих данных не хватает). */
    public boolean usesPersonalPhysical(RoomLibRef ref) {
        return ref != null && usedOverrides.contains(ref);
    }

    /** Физический блок позиции с учётом личных габаритов. */
    private PhysicalSpec effective(RoomLibRef ref, PhysicalSpec shared,
                                   java.util.function.Function<PhysicalSpec, List<String>> missingOf) {
        PhysicalSpec own = overrides.get(ref.key());
        boolean sharedComplete = shared != null && missingOf.apply(shared).isEmpty();
        if (own != null && !sharedComplete) {
            usedOverrides.add(ref);
            return own;
        }
        return shared;
    }

    /** Кофр считается описанным, когда известны юниты и внутренняя глубина. */
    private static List<String> caseMissing(PhysicalSpec p) {
        List<String> m = new ArrayList<>();
        if (p.getRackUnits() == null) {
            m.add("юниты (U) — не рэк-кофр");
        }
        if (p.getDepthMm() == null) {
            m.add("внутренняя глубина");
        }
        return m;
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
        boolean standalone = ph.getFormFactor() == PhysicalSpec.FormFactor.STANDALONE;
        double w = ph.getWidthMm() != null ? RoomUnits.mmToCm(ph.getWidthMm()) : standalone ? 40 : 10;
        double h = ph.getHeightMm() != null ? RoomUnits.mmToCm(ph.getHeightMm()) : standalone ? 20 : 5;
        double d = ph.getDepthMm() != null ? RoomUnits.mmToCm(ph.getDepthMm()) : standalone ? 30 : 10;
        List<RoomPart> parts = RoomShape.fromModel(ph.getParts());
        if (!parts.isEmpty()) {
            // из блоков габарит и вес считаются сами
            double[] bb = RoomShape.bbox(parts);
            w = bb[0];
            d = bb[1];
            h = bb[2];
            kg = RoomShape.totalKg(parts) > 0 ? RoomShape.totalKg(parts) : kg;
        }
        return new RoomItemSpec(ref, standalone ? RoomItemSpec.Type.DEVICE : RoomItemSpec.Type.LOOSE, RoomItemSpec.Role.OTHER, name, category, w, h, d, 0, 12, 0,
                kg, p, null, missing, parts);
    }

    private static RoomItemSpec caseSpec(RoomLibRef ref, CaseType c, PhysicalSpec ph) {
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

    /** Условные размеры для позиции без данных, см: ширина, глубина, высота. */
    private static double[] placeholder(RoomFurnitureType.Kind kind) {
        return switch (kind) {
            case DECK -> new double[] {100, 100, 20};
            case MONITOR -> new double[] {55, 20, 35};
            default -> new double[] {100, 60, 75};
        };
    }

    private RoomItemSpec furnitureSpec(RoomLibRef ref, RoomFurnitureType t) {
        RoomFurnitureType.Kind kind = t.getKind() != null ? t.getKind() : RoomFurnitureType.Kind.FURNITURE;
        Double w = t.getWidthMm();
        Double d = t.getDepthMm();
        Double h = t.getHeightMm();
        Double kg = t.getWeightKg();
        Double pw = t.getPowerW();
        PhysicalSpec own = overrides.get(ref.key());
        boolean incomplete = t.missingFields().stream().anyMatch(m -> !m.equals("диагональ"));
        if (own != null && incomplete) {
            usedOverrides.add(ref);
            w = own.getWidthMm() != null ? own.getWidthMm() : w;
            d = own.getDepthMm() != null ? own.getDepthMm() : d;
            h = own.getHeightMm() != null ? own.getHeightMm() : h;
            kg = own.getWeightKg() != null ? own.getWeightKg() : kg;
            pw = own.getPowerW() != null ? own.getPowerW() : pw;
        }
        List<String> missing = new ArrayList<>();
        List<RoomPart> parts = RoomShape.fromModel(t.getParts());
        if (!parts.isEmpty()) {
            double[] bb = RoomShape.bbox(parts);
            w = bb[0] * 10;
            d = bb[1] * 10;
            h = bb[2] * 10;
            kg = RoomShape.totalKg(parts) > 0 ? Double.valueOf(RoomShape.totalKg(parts)) : kg;
        }
        if (w == null) {
            missing.add("ширина");
        }
        if (d == null) {
            missing.add("глубина");
        }
        if (h == null) {
            missing.add("высота");
        }
        if (kind == RoomFurnitureType.Kind.MONITOR && t.getDiagonalIn() == null) {
            missing.add("диагональ");
        }
        double[] ph = placeholder(kind);
        double wc = w != null ? RoomUnits.mmToCm(w) : ph[0];
        double dc = d != null ? RoomUnits.mmToCm(d) : ph[1];
        double hc = h != null ? RoomUnits.mmToCm(h) : ph[2];
        RoomItemSpec.Type type = switch (kind) {
            case DECK -> RoomItemSpec.Type.DECK;
            case MONITOR -> RoomItemSpec.Type.MONITOR;
            default -> RoomItemSpec.Type.FURNITURE;
        };
        RoomItemSpec.Role role = switch (t.getRole() != null ? t.getRole() : RoomFurnitureType.Role.OTHER) {
            case TABLE -> RoomItemSpec.Role.TABLE;
            case CHAIR -> RoomItemSpec.Role.CHAIR;
            default -> RoomItemSpec.Role.OTHER;
        };
        String category = switch (kind) {
            case DECK -> "Станки";
            case MONITOR -> "Мониторы";
            default -> "Мебель";
        };
        return new RoomItemSpec(ref, type, role, t.getName(), category, wc, hc, dc, 0, 12, 0,
                kg != null ? kg : 0, pw != null ? pw : 0, t.getDiagonalIn(), missing, parts);
    }
}
