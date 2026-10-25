package com.vjstb.ledscheme.service.room;

/**
 * Блок формы позиции для Планера, в сантиметрах: размеры коробки и её положение. {@code xCm}/{@code yCm} — от центра
 * позиции (вправо и к лицевой стороне), {@code zCm} — низ блока над полом позиции. См. {@link RoomShape}.
 */
public record RoomPart(String name, double wCm, double dCm, double hCm, double xCm, double yCm, double zCm,
                       double kg) {
}
