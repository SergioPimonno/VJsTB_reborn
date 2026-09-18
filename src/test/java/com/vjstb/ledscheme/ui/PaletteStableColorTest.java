package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Баг-репорт: "палитру нужно сохранять между перезапусками" — цвет типа кабинета
 *  (см. {@code SceneCanvasPanel#typeColorFor}) раньше выбирался по ПОЗИЦИИ типа в
 *  списке библиотеки, из-за чего добавление/удаление ЛЮБОГО другого типа раньше
 *  него по списку сдвигало его цвет. {@link Palette#stableColorFor} — по хешу id,
 *  не зависит от списка вовсе. */
class PaletteStableColorTest {

    @Test
    void sameIdAlwaysGivesTheSameColor() {
        Color a = Palette.stableColorFor("type-mg14");
        Color b = Palette.stableColorFor("type-mg14");
        assertEquals(a, b);
    }

    @Test
    void colorIsUnaffectedByOtherIdsBeingQueriedInBetween() {
        // stableColorFor не принимает список окружающих типов вовсе — по
        // конструкции не может зависеть от того, какие ещё id существуют/в каком
        // порядке их завели, в отличие от прежнего "индекс в списке библиотеки".
        Color before = Palette.stableColorFor("type-mg14");
        Palette.stableColorFor("type-unrelated-added-earlier-in-the-library");
        Color after = Palette.stableColorFor("type-mg14");
        assertEquals(before, after);
    }

    @Test
    void differentIdsUsuallyGetDifferentColors() {
        // Не гарантия (возможна коллизия хеша), но с этими двумя конкретными
        // строками — разные, что и ожидается на практике.
        assertNotEquals(Palette.stableColorFor("type-a"), Palette.stableColorFor("type-b"));
    }
}
