package com.vjstb.ledscheme.model;

/** Ориентация потока узла общей схемы (docs/schema-ports-rework/PLAN.md, задача T1.2) —
 *  заменяет собой старую булеву настройку персонализации «Гнёзда разъёмов у верхнего/
 *  нижнего края блока»: там был один флаг на всю схему и жёсткое «вход сверху, выход
 *  снизу», здесь — свойство КАЖДОГО узла с четырьмя направлениями, как поворот блока
 *  в Simulink/yEd. {@link #RIGHT} — поток слева направо (вход слева, выход справа) —
 *  поведение по умолчанию, совпадает со старым горизонтальным режимом. Стороны сторон
 *  по ролям интерфейса (см. клиентский {@code SideRules}) для {@link #RIGHT}
 *  поворачиваются по часовой стрелке на 90° для {@link #DOWN}, на 180° для {@link #LEFT}
 *  и против часовой на 90° для {@link #UP} — так что смена ориентации разворачивает
 *  блок целиком, а не только заголовок. */
public enum NodeOrientation {
    RIGHT("→"), DOWN("↓"), LEFT("←"), UP("↑");

    private final String label;

    NodeOrientation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
