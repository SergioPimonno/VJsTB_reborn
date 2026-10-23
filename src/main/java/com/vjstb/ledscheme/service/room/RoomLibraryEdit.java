package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;

/**
 * Что нужно окну Планнера, чтобы пользователь мог указать габариты позиции библиотеки и
 * предложить их на сервер (запрос 2026-10-23: «предлагаем пользователю самому указать, реализуем
 * механизм предложить на сервер»): найти запись по ссылке, понять общая она или личная, достать
 * текущий физический блок и собрать черновик для {@code ProposeDialog}.
 *
 * <p>Предложение строится как <b>копия</b> записи с подставленными габаритами — локальная запись
 * не меняется: общие позиции правит только администратор, а пользователь хранит свои цифры как
 * личные габариты ({@link AppModel#setPhysicalOverride}), пока сервер не примет предложение.
 * Для общей позиции предложение идёт как правка существующей (с {@code targetItemId}), для личной —
 * как новая.
 */
public final class RoomLibraryEdit {

    private RoomLibraryEdit() {
    }

    /** Найденная позиция библиотеки. {@code serverKind} — имя вида на сервере ({@code LibraryItemKind}). */
    public record Found(RoomLibRef ref, String serverKind, Object item, boolean shared) {

        public String name() {
            return switch (item) {
                case CaseType c -> c.getName();
                case EquipmentPreset p -> p.getName();
                case NetworkDeviceType n -> n.getName();
                case RoomEquipmentType e -> e.getName();
                case RoomFurnitureType f -> f.getName();
                default -> "";
            };
        }

        public String id() {
            return ref.getId();
        }
    }

    /** Имя вида на сервере для вида ссылки раскладки. */
    public static String serverKind(RoomLibRef.Kind kind) {
        return switch (kind) {
            case CASE -> "CASE";
            case EQUIPMENT -> "EQUIPMENT";
            case NETWORK_DEVICE -> "NETWORK_DEVICE";
            case ROOM_EQUIPMENT -> "ROOM_EQUIPMENT";
            case ROOM_FURNITURE -> "ROOM_FURNITURE";
        };
    }

    /** Позиция библиотеки по ссылке или {@code null}, если её нет (удалена, не синхронизирована). */
    public static Found find(AppModel m, RoomLibRef ref) {
        if (ref == null || ref.getKind() == null || ref.getId() == null) {
            return null;
        }
        String id = ref.getId();
        String sk = serverKind(ref.getKind());
        switch (ref.getKind()) {
            case CASE:
                for (CaseType c : m.getCaseTypes()) {
                    if (c.getId().equals(id)) {
                        return new Found(ref, sk, c, m.isSharedCaseType(id));
                    }
                }
                break;
            case EQUIPMENT:
                for (EquipmentPreset p : m.getEquipmentPresets()) {
                    if (p.getId().equals(id)) {
                        return new Found(ref, sk, p, m.isSharedEquipmentPreset(id));
                    }
                }
                break;
            case NETWORK_DEVICE:
                for (NetworkDeviceType n : m.getNetworkDeviceTypes()) {
                    if (n.getId().equals(id)) {
                        return new Found(ref, sk, n, m.isSharedNetworkDeviceType(id));
                    }
                }
                break;
            case ROOM_EQUIPMENT:
                for (RoomEquipmentType e : m.getRoomEquipmentTypes()) {
                    if (e.getId().equals(id)) {
                        return new Found(ref, sk, e, m.isSharedRoomEquipmentType(id));
                    }
                }
                break;
            case ROOM_FURNITURE:
                for (RoomFurnitureType f : m.getRoomFurnitureTypes()) {
                    if (f.getId().equals(id)) {
                        return new Found(ref, sk, f, m.isSharedRoomFurnitureType(id));
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    /** Физический блок записи как есть (для мебели — собранный из её полей); может быть {@code null}. */
    public static PhysicalSpec physicalOf(Object item) {
        return switch (item) {
            case CaseType c -> c.getPhysical();
            case EquipmentPreset p -> p.getPhysical();
            case NetworkDeviceType n -> n.getPhysical();
            case RoomEquipmentType e -> e.getPhysical();
            case RoomFurnitureType f -> furnitureAsSpec(f);
            default -> null;
        };
    }

    /** Размеры, вес и мощность мебели в общем виде физического блока (диагональ в него не входит). */
    public static PhysicalSpec furnitureAsSpec(RoomFurnitureType f) {
        PhysicalSpec s = new PhysicalSpec();
        s.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        s.setWidthMm(f.getWidthMm());
        s.setDepthMm(f.getDepthMm());
        s.setHeightMm(f.getHeightMm());
        s.setWeightKg(f.getWeightKg());
        s.setPowerW(f.getPowerW());
        return s;
    }

    /** Копия записи с подставленными габаритами — это и есть то, что уходит на сервер. Исходная запись не меняется. */
    public static Object draftWith(Object item, PhysicalSpec spec) {
        PhysicalSpec phys = spec != null ? spec.copy() : null;
        switch (item) {
            case CaseType c -> {
                CaseType d = c.copy();
                d.setPhysical(phys);
                return d;
            }
            case EquipmentPreset p -> {
                EquipmentPreset d = p.copy();
                d.setPhysical(phys);
                return d;
            }
            case NetworkDeviceType n -> {
                NetworkDeviceType d = n.copy();
                d.setPhysical(phys);
                return d;
            }
            case RoomEquipmentType e -> {
                RoomEquipmentType d = e.copy();
                d.setPhysical(phys);
                return d;
            }
            case RoomFurnitureType f -> {
                RoomFurnitureType d = f.copy();
                if (phys != null) {
                    d.setWidthMm(phys.getWidthMm());
                    d.setDepthMm(phys.getDepthMm());
                    d.setHeightMm(phys.getHeightMm());
                    d.setWeightKg(phys.getWeightKg());
                    d.setPowerW(phys.getPowerW());
                }
                return d;
            }
            default -> throw new IllegalArgumentException("Неизвестный вид записи: " + item);
        }
    }

    /** Достаточно ли блока, чтобы позицию имело смысл предлагать (нет недостающих полей). */
    public static boolean isComplete(Object item, PhysicalSpec spec) {
        if (spec == null) {
            return false;
        }
        if (item instanceof CaseType) {
            return spec.getRackUnits() != null && spec.getDepthMm() != null;
        }
        if (item instanceof RoomFurnitureType) {
            return spec.getWidthMm() != null && spec.getDepthMm() != null && spec.getHeightMm() != null;
        }
        return spec.missingFields().isEmpty();
    }

    /**
     * Запоминает габариты, указанные пользователем. Личная позиция «оборудование пультовой» или
     * «мебель/станок/монитор» правится сама (это его запись); у всего остального — общих позиций и
     * пресетов других библиотек — габариты сохраняются как личные
     * ({@link AppModel#setPhysicalOverride}), потому что саму запись менять нельзя или не нужно.
     * Возвращает черновик записи с этими габаритами — его можно предложить на сервер.
     */
    public static Object saveDimensions(AppModel m, Found f, PhysicalSpec spec) {
        Object draft = draftWith(f.item(), spec);
        if (!f.shared() && draft instanceof RoomEquipmentType e && f.ref().getKind() == RoomLibRef.Kind.ROOM_EQUIPMENT) {
            m.updateRoomEquipmentType(e);
            m.setPhysicalOverride(f.ref(), null);
        } else if (!f.shared() && draft instanceof RoomFurnitureType t
                && f.ref().getKind() == RoomLibRef.Kind.ROOM_FURNITURE) {
            m.updateRoomFurnitureType(t);
            m.setPhysicalOverride(f.ref(), null);
        } else {
            m.setPhysicalOverride(f.ref(), spec);
        }
        return draft;
    }

    /** Убирает личные габариты позиции (общие данные библиотеки остаются). */
    public static void clearPersonalDimensions(AppModel m, RoomLibRef ref) {
        m.setPhysicalOverride(ref, null);
    }
}
