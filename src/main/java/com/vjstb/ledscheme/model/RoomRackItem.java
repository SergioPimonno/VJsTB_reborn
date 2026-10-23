package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Блок в слоте кофра или в стопке башни. В кофре {@code u0} — верхний юнит (с нуля сверху),
 * {@code c0} — колонка в двенадцатых от левого края (для долей ширины); в стопке оба не используются.
 */
public class RoomRackItem {

    private String id = UUID.randomUUID().toString();
    private RoomLibRef ref;
    private int u0 = 0;
    private int c0 = 0;

    public RoomRackItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RoomLibRef getRef() {
        return ref;
    }

    public void setRef(RoomLibRef ref) {
        this.ref = ref;
    }

    public int getU0() {
        return u0;
    }

    public void setU0(int u0) {
        this.u0 = u0;
    }

    public int getC0() {
        return c0;
    }

    public void setC0(int c0) {
        this.c0 = c0;
    }

}
