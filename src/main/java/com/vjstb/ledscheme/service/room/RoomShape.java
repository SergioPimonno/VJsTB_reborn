package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.PhysicalPart;
import java.util.ArrayList;
import java.util.List;

/**
 * Форма позиции из нескольких блоков («Разбить на блоки»): перевод из библиотеки (миллиметры, как нарисовали) в вид,
 * удобный Планеру, и геометрия блоков — габарит, масштабирование под переопределённые размеры, поворот. Позиция без
 * блоков — одна коробка по общим размерам, этот класс ей не нужен.
 *
 * <p>Общий габарит позиции — охватывающая коробка блоков, а центр позиции в плане — центр этой коробки: поэтому
 * «как нарисовали» (блок сдвинут от нуля) не смещает позицию, а расчёты, столкновения и привязка к столам работают
 * по габариту, как и раньше. Высота — верх самого высокого блока над полом (блок может висеть над полом — подвес).
 */
public final class RoomShape {

    private RoomShape() {
    }

    /** Блоки библиотеки → блоки Планера (см), центр по x/y смещён в центр габарита; некорректные блоки пропускаются. */
    public static List<RoomPart> fromModel(List<PhysicalPart> parts) {
        List<PhysicalPart> ok = new ArrayList<>();
        if (parts != null) {
            for (PhysicalPart p : parts) {
                if (p != null && p.getWidthMm() > 0 && p.getDepthMm() > 0 && p.getHeightMm() > 0) {
                    ok.add(p);
                }
            }
        }
        if (ok.isEmpty()) {
            return List.of();
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (PhysicalPart p : ok) {
            minX = Math.min(minX, p.getXMm() - p.getWidthMm() / 2);
            maxX = Math.max(maxX, p.getXMm() + p.getWidthMm() / 2);
            minY = Math.min(minY, p.getYMm() - p.getDepthMm() / 2);
            maxY = Math.max(maxY, p.getYMm() + p.getDepthMm() / 2);
        }
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        List<RoomPart> out = new ArrayList<>();
        for (PhysicalPart p : ok) {
            out.add(new RoomPart(p.getName() == null ? "" : p.getName(), RoomUnits.mmToCm(p.getWidthMm()),
                    RoomUnits.mmToCm(p.getDepthMm()), RoomUnits.mmToCm(p.getHeightMm()),
                    RoomUnits.mmToCm(p.getXMm() - cx), RoomUnits.mmToCm(p.getYMm() - cy),
                    RoomUnits.mmToCm(Math.max(0, p.getZMm())), p.getWeightKg() != null ? p.getWeightKg() : 0));
        }
        return List.copyOf(out);
    }

    /** Габарит блоков: {ширина, глубина, высота над полом}, см; для пустого списка — нули. */
    public static double[] bbox(List<RoomPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return new double[] {0, 0, 0};
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double top = 0;
        for (RoomPart p : parts) {
            minX = Math.min(minX, p.xCm() - p.wCm() / 2);
            maxX = Math.max(maxX, p.xCm() + p.wCm() / 2);
            minY = Math.min(minY, p.yCm() - p.dCm() / 2);
            maxY = Math.max(maxY, p.yCm() + p.dCm() / 2);
            top = Math.max(top, p.zCm() + p.hCm());
        }
        return new double[] {maxX - minX, maxY - minY, top};
    }

    /** Сумма весов блоков, кг (0 — вес блоков не указан). */
    public static double totalKg(List<RoomPart> parts) {
        double s = 0;
        if (parts != null) {
            for (RoomPart p : parts) {
                s += p.kg();
            }
        }
        return s;
    }

    /**
     * Блоки, подогнанные под размеры позиции: если пользователь изменил размеры объекта на плане («Параметры…», тяга
     * за край станка), блоки растягиваются пропорционально, чтобы картинка совпадала с габаритом.
     */
    public static List<RoomPart> fit(RoomItemSpec s) {
        List<RoomPart> parts = s.parts();
        if (parts.isEmpty()) {
            return parts;
        }
        double[] bb = bbox(parts);
        double sx = bb[0] > 0 ? s.widthCm() / bb[0] : 1;
        double sy = bb[1] > 0 ? s.depthCm() / bb[1] : 1;
        double sz = bb[2] > 0 ? s.heightCm() / bb[2] : 1;
        if (Math.abs(sx - 1) < 1e-6 && Math.abs(sy - 1) < 1e-6 && Math.abs(sz - 1) < 1e-6) {
            return parts;
        }
        List<RoomPart> out = new ArrayList<>();
        for (RoomPart p : parts) {
            out.add(new RoomPart(p.name(), p.wCm() * sx, p.dCm() * sy, p.hCm() * sz, p.xCm() * sx, p.yCm() * sy,
                    p.zCm() * sz, p.kg()));
        }
        return out;
    }

    /**
     * Охватывающая коробка блока в плане при повороте позиции на {@code rotation}° (кратно 90): {центр x, центр y,
     * ширина, глубина} относительно центра позиции. Ось y плана направлена вниз, поворот — по часовой стрелке.
     */
    public static double[] footprint(RoomPart p, int rotation) {
        double a = Math.toRadians(rotation);
        double cos = Math.round(Math.cos(a));
        double sin = Math.round(Math.sin(a));
        double x = p.xCm() * cos - p.yCm() * sin;
        double y = p.xCm() * sin + p.yCm() * cos;
        boolean swap = Math.abs(rotation) % 180 != 0;
        return new double[] {x, y, swap ? p.dCm() : p.wCm(), swap ? p.wCm() : p.dCm()};
    }
}
