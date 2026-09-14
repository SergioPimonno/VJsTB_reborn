package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Одна компьютерная сеть внутри {@link NetworkManagerPlan} — персистентная
 * форма {@code ui.NetworkManagerPanel.NetworkSection}. В отличие от {@link
 * VehicleLoadSection} (та идентифицируется только ссылкой на тип машины,
 * "машины" не именуются пользователем) — сеть ЕСТЬ пользовательская сущность
 * со своим именем, поэтому несёт {@link #id} (для добавления/переименования/
 * удаления без опоры на индекс списка) и {@link #name}.
 *
 * <p><b>Round 8 — членство, не владение.</b> Раньше {@code Network} физически
 * хранила списки устройств/связей (см. историю в git) — теперь и то, и другое
 * лежит на уровне {@link NetworkManagerPlan} ({@code devices}/{@code links}),
 * а эта сеть — просто id, на который ссылается {@code
 * NetworkDevicePlacement.getAttachments()} (см. {@link NetworkAttachment}).
 * Причина: одно физическое устройство должно уметь входить сразу в несколько
 * сетей (запрос пользователя) — при владении списком устройств это означало бы
 * дублирование самого блока на поле, чего быть не должно. */
public class Network {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    /** Цвет фоновой подложки/линий связи ЭТОЙ сети (RGB, см. {@code
     *  Color#getRGB()}) — {@code null} означает "цвет по умолчанию" (см.
     *  {@code ui.NetworkCanvasPanel#defaultColorForIndex}). Баг-репорт: "цвет
     *  линии должен зависеть от цвета выбранного для сети" — раньше все связи
     *  любой сети рисовались одним фиксированным цветом, что не позволяло на
     *  глаз отличить, каким сетям принадлежат линии, если устройства
     *  нескольких сетей физически рядом на канвасе. {@code
     *  ui.NetworkManagerPanel#addNetwork} подставляет новой сети цвет по
     *  умолчанию (по золотому углу от индекса — соседние по порядку сети
     *  получают заметно разные оттенки), пользователь может изменить кнопкой
     *  «Цвет…». */
    private Integer color;

    public Network() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }
}
