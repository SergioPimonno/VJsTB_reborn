package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Именованная группа экранов ОДНОЙ сцены в дереве навигации «Сетапа» (запрос
 * пользователя 2026-09-24: выделить несколько экранов шифтом → «Объединить в
 * группу»). Сама группа не владеет экранами — экран ссылается на неё по {@link
 * Screen#getGroupId()} (тот же приём «ссылка по id», что у {@code cabinetTypeId}),
 * а порядок экранов сцены ({@link Scene#getScreens()}) остаётся единственным
 * источником сквозной нумерации портов; члены группы всегда идут в этом списке
 * подряд (см. {@code AppModel#normalizeScreenOrder}), чтобы порядок строк в дереве
 * совпадал с порядком экранов.
 *
 * <p>{@link #tagColor} — цвет группы: при выборе ПРОСТАВЛЯЕТСЯ как цветная метка
 * ({@link Screen#getTagColor()}) всем экранам группы, а также экранам, которые
 * позже добавят в группу (см. {@code AppModel#setGroupColor}). {@link
 * #collapsed} — свёрнутость узла в дереве, хранится вместе с проектом.
 */
public class ScreenGroup {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private ScreenTagColor tagColor = ScreenTagColor.NONE;
    private boolean collapsed;

    public ScreenGroup() {
    }

    public ScreenGroup(String name) {
        this.name = name;
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

    public ScreenTagColor getTagColor() {
        return tagColor != null ? tagColor : ScreenTagColor.NONE;
    }

    public void setTagColor(ScreenTagColor tagColor) {
        this.tagColor = tagColor != null ? tagColor : ScreenTagColor.NONE;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    /** Копия для снимка отмены (тот же id — экраны ссылаются на группу по нему). */
    public ScreenGroup copy() {
        ScreenGroup g = new ScreenGroup(name);
        g.id = id;
        g.tagColor = tagColor;
        g.collapsed = collapsed;
        return g;
    }
}
