package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomTower;

/**
 * Габарит объекта плана в сантиметрах (башня, мебель, станок, монитор) — общая основа для
 * проверок пересечений, опор и «висит в воздухе» ({@link RoomGeometry}, {@link RoomPlanCalc}).
 * Прототип: {@code bounds()}. Прямоугольник уже учитывает поворот объекта на 90°/270°.
 *
 * <p>Для станков {@code base} — высота нижней плоскости (0 для станка на полу, высота нижних
 * станков для станка-стола) и {@code top} — высота верхней плоскости; для остальных
 * {@code top == 0}. {@code height} — собственная высота объекта (башни — суммарная).
 */
public final class RoomBounds {

    public enum Type {
        TOWER, FURNITURE
    }

    public final Type type;
    public final String id;
    public final String name;
    public final double x0;
    public final double x1;
    public final double y0;
    public final double y1;
    public final double height;
    public final double weightKg;
    public final boolean deck;
    public final boolean monitor;
    public final boolean chair;
    public final boolean onTop;
    public final boolean onlyStack;
    public final RoomTower.Lay lay;
    public final RoomTower tower;
    public final RoomFurniture furniture;
    public final RoomItemSpec spec;
    public final RoomTowerCalc calc;
    public double base;
    public double top;

    RoomBounds(Type type, String id, String name, double x0, double x1, double y0, double y1, double height,
               double weightKg, boolean deck, boolean monitor, boolean chair, boolean onTop, boolean onlyStack,
               RoomTower.Lay lay, RoomTower tower, RoomFurniture furniture, RoomItemSpec spec, RoomTowerCalc calc) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.x0 = x0;
        this.x1 = x1;
        this.y0 = y0;
        this.y1 = y1;
        this.height = height;
        this.weightKg = weightKg;
        this.deck = deck;
        this.monitor = monitor;
        this.chair = chair;
        this.onTop = onTop;
        this.onlyStack = onlyStack;
        this.lay = lay;
        this.tower = tower;
        this.furniture = furniture;
        this.spec = spec;
        this.calc = calc;
    }

    public double cx() {
        return (x0 + x1) / 2;
    }

    public double cy() {
        return (y0 + y1) / 2;
    }

    public boolean isTower() {
        return type == Type.TOWER;
    }

    /** Мебель, на которой можно ставить (стол, тумба): не станок, не монитор, не стул. */
    public boolean isTableLike() {
        return type == Type.FURNITURE && !deck && !monitor && !chair;
    }

    public boolean containsCenterOf(RoomBounds o) {
        return o.cx() >= x0 && o.cx() <= x1 && o.cy() >= y0 && o.cy() <= y1;
    }
}
