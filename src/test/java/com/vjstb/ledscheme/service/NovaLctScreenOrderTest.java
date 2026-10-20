package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Порядок NovaLCT-экранов (Screen1..N), назначаемый пользователем на шаге «Порядок экранов».
 *
 * <p>Запрос пользователя: на одном контроллере в NovaLCT несколько Screen, и проектные
 * экраны нужно уметь назначать на конкретные Screen. Раньше порядок был зашит в писателе
 * (сортировка по Sending Card/Port). Тесты фиксируют: авто-порядок прежний; порядок, который
 * NovaLCT отклоняет (на одной карте больший порт раньше меньшего), определяется как
 * нарушение; заданный порядок писатель сохраняет, а без {@code keepOrder} — сортирует как раньше. */
class NovaLctScreenOrderTest {

    private static NovaLctScrWriter.ScreenBlock block(int card, int port) {
        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
        cells.put(new NovaLctScrWriter.CellKey(0, 0), new NovaLctScrWriter.Rec(0, 0, card, port, 1));
        cells.put(new NovaLctScrWriter.CellKey(0, 1), new NovaLctScrWriter.Rec(1, 0, card, port, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 0), new NovaLctScrWriter.Rec(0, 1, card, port, 2));
        cells.put(new NovaLctScrWriter.CellKey(1, 1), new NovaLctScrWriter.Rec(1, 1, card, port, 3));
        return new NovaLctScrWriter.ScreenBlock(2, 2, 128, 128, 0, cells);
    }

    private static NovaLctScreenOrder.Entry entry(String label, int card, int port) {
        return new NovaLctScreenOrder.Entry(label,
                NovaLctScrWriter.MixedScreen.of(block(card, port)));
    }

    @Test
    void autoOrderSortsByCardThenPort() {
        List<NovaLctScreenOrder.Entry> auto = NovaLctScreenOrder.autoOrder(List.of(
                entry("C", 1, 0), entry("B", 0, 4), entry("A", 0, 1)));
        assertEquals(List.of("A", "B", "C"), auto.stream().map(NovaLctScreenOrder.Entry::label).toList());
    }

    @Test
    void sameCardHigherPortBeforeLowerPortIsAViolation() {
        List<NovaLctScreenOrder.Entry> bad = List.of(entry("Right", 0, 4), entry("Left", 0, 1));
        assertNotNull(NovaLctScreenOrder.violation(bad));
        assertNull(NovaLctScreenOrder.violation(NovaLctScreenOrder.autoOrder(bad)));
    }

    @Test
    void differentCardsInAnyOrderAreNotAViolationButWarnWhenDescending() {
        List<NovaLctScreenOrder.Entry> swapped = List.of(entry("OnCard2", 1, 0), entry("OnCard1", 0, 0));
        assertNull(NovaLctScreenOrder.violation(swapped));
        assertNotNull(NovaLctScreenOrder.warning(swapped));
        assertNull(NovaLctScreenOrder.warning(NovaLctScreenOrder.autoOrder(swapped)));
    }

    @Test
    void complexScreenParticipatesByItsFirstCardPort() {
        NovaLctScrWriter.ComplexScreenBlock cplx = new NovaLctScrWriter.ComplexScreenBlock(128, 128, List.of(
                new NovaLctScrWriter.ComplexRec(0, 2, 0, 0, 0, 128, 128)));
        NovaLctScreenOrder.Entry c = new NovaLctScreenOrder.Entry("R1", NovaLctScrWriter.MixedScreen.of(cplx));
        assertTrue(c.isComplex());
        assertEquals(0, c.card());
        assertEquals(2, c.port());
        assertNotNull(NovaLctScreenOrder.violation(List.of(c, entry("L1", 0, 0))));
    }

    @Test
    void writerKeepsGivenOrderWhenAskedAndSortsOtherwise() {
        NovaLctScrWriter.MixedScreen low = NovaLctScrWriter.MixedScreen.of(block(0, 0));
        NovaLctScrWriter.MixedScreen high = NovaLctScrWriter.MixedScreen.of(block(1, 0));

        byte[] sortedByDefault = NovaLctScrWriter.writeMixedMultiScreen(List.of(high, low));
        byte[] sortedExplicit = NovaLctScrWriter.writeMixedMultiScreen(List.of(low, high), true);
        assertArrayEquals(sortedExplicit, sortedByDefault, "без keepOrder порядок вычисляется как раньше");

        byte[] reversed = NovaLctScrWriter.writeMixedMultiScreen(List.of(high, low), true);
        assertFalse(java.util.Arrays.equals(sortedExplicit, reversed),
                "с keepOrder=true Screen1 — первый переданный экран, а не отсортированный");
    }
}
