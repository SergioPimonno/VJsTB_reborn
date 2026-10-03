package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CardKind;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.EquipmentSeries;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-02: серии оборудования (H-серия Novastar, VFC Disguise D3) — общий каталог карт на
 *  модели серии, лимиты входных/выходных карт у модели, синхронизация вида EQUIPMENT_SERIES. */
class EquipmentSeriesTest {

    private static AppModel newModel(Path dir) {
        AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        return m;
    }

    private static SchemaCard card(String name, PortDirection dir) {
        return new SchemaCard(name, List.of(new CardPort("X", dir, 1)));
    }

    private static EquipmentSeries series() {
        EquipmentSeries s = new EquipmentSeries();
        s.setName("H Series");
        s.setCategory(SchemaNodeType.CONTROLLER);
        s.setCards(List.of(card("HDMI вход", PortDirection.IN), card("Выход 4×RJ45", PortDirection.OUT)));
        return s;
    }

    private static EquipmentPreset seriesModel(EquipmentSeries s, String name, Integer maxIn, Integer maxOut) {
        EquipmentPreset p = new EquipmentPreset();
        p.setMode(SchemaMode.SIGNAL);
        p.setCategory(SchemaNodeType.CONTROLLER);
        p.setName(name);
        p.setSeriesId(s.getId());
        p.setMaxInputCards(maxIn);
        p.setMaxOutputCards(maxOut);
        return p;
    }

    @Test
    void cardKindIsDerivedFromPortsUnlessSetExplicitly() {
        assertEquals(CardKind.INPUT, card("a", PortDirection.IN).effectiveKind());
        assertEquals(CardKind.OUTPUT, card("b", PortDirection.OUT).effectiveKind());
        assertEquals(CardKind.MIXED, card("c", PortDirection.IN_OUT).effectiveKind());
        assertEquals(CardKind.MIXED, new SchemaCard("пустая", List.of()).effectiveKind());
        SchemaCard forced = card("d", PortDirection.IN);
        forced.setKind(CardKind.OUTPUT);
        assertEquals(CardKind.OUTPUT, forced.effectiveKind());
        assertEquals(CardKind.OUTPUT, forced.copy().getKind(), "явный вид копируется");
    }

    @Test
    void limitsCountInputOutputAndMixedSeparately() {
        EquipmentPreset p = new EquipmentPreset();
        p.setName("H2");
        p.setMaxInputCards(1);
        p.setMaxOutputCards(2);
        SchemaCard in = card("in", PortDirection.IN);
        SchemaCard out = card("out", PortDirection.OUT);
        SchemaCard mixed = card("mix", PortDirection.IN_OUT);

        assertTrue(CardLoadout.canAdd(p, List.of(), in));
        assertFalse(CardLoadout.canAdd(p, List.of(in), in), "второй входной сверх лимита 1");
        assertTrue(CardLoadout.canAdd(p, List.of(in), out));
        assertFalse(CardLoadout.canAdd(p, List.of(in), mixed), "смешанная занимает и входной слот");
        assertTrue(CardLoadout.canAdd(p, List.of(out, out), in));
        assertFalse(CardLoadout.canAdd(p, List.of(out, out), out));
        assertNull(CardLoadout.problem(p, List.of(in, out, out)));
        assertNotNull(CardLoadout.problem(p, List.of(in, in)));
        assertEquals("Входные 1/1 · Выходные 2/2", CardLoadout.summary(p, List.of(in, out, out)));
        p.setMaxInputCards(null);
        assertEquals("Входные 1/∞ · Выходные 0/2", CardLoadout.summary(p, List.of(in)));
    }

    @Test
    void modelsOfOneSeriesShareCardsAndDifferByLimits(@TempDir Path dir) {
        AppModel m = newModel(dir);
        EquipmentSeries s = series();
        m.getWorkspace().getSharedEquipmentSeries().add(s);
        EquipmentPreset h2 = seriesModel(s, "H2", 1, 1);
        EquipmentPreset h9 = seriesModel(s, "H9", 4, 6);
        SchemaCard own = card("Своя", PortDirection.OUT);
        h9.getCards().add(own);

        assertEquals(2, m.cardTemplatesOf(h2).size());
        assertEquals(3, m.cardTemplatesOf(h9).size(), "карты серии + собственные карты модели");
        assertEquals(s.getCards().get(0).getId(), m.cardTemplatesOf(h9).get(0).getId());
        assertTrue(m.isSeriesCard(h9, s.getCards().get(0).getId()));
        assertFalse(m.isSeriesCard(h9, own.getId()));

        String in = s.getCards().get(0).getId();
        String out = s.getCards().get(1).getId();
        SchemaNode node = m.addSchemaNodeFromPresetWithCardOrder(SchemaMode.SIGNAL, h9, 0, 0,
                List.of(in, in, in, out, out));
        assertEquals(5, node.getCards().size());
        assertFalse(node.getCards().get(0).getId().equals(in), "карты узла получают свежие id");
    }

    @Test
    void exceedingTheModelLimitIsRejected(@TempDir Path dir) {
        AppModel m = newModel(dir);
        EquipmentSeries s = series();
        m.getWorkspace().getSharedEquipmentSeries().add(s);
        EquipmentPreset h2 = seriesModel(s, "H2", 1, 1);
        String in = s.getCards().get(0).getId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> m.addSchemaNodeFromPresetWithCardOrder(SchemaMode.SIGNAL, h2, 0, 0, List.of(in, in)));

        assertTrue(ex.getMessage().contains("H2"), ex.getMessage());
        assertTrue(m.schemaNodesForCurrentScene(SchemaMode.SIGNAL).isEmpty(), "узел не создан");
    }

    @Test
    void presetWithoutLimitsOrSeriesBehavesAsBefore(@TempDir Path dir) {
        AppModel m = newModel(dir);
        EquipmentPreset p = new EquipmentPreset();
        p.setMode(SchemaMode.SIGNAL);
        SchemaCard c = card("A", PortDirection.IN);
        p.getCards().add(c);

        assertEquals(1, m.cardTemplatesOf(p).size());
        SchemaNode node = m.addSchemaNodeFromPresetWithCardOrder(SchemaMode.SIGNAL, p, 0, 0,
                List.of(c.getId(), c.getId(), c.getId()));
        assertEquals(3, node.getCards().size());
    }

    @Test
    void seriesArrivesAndIsRemovedThroughLibrarySync(@TempDir Path dir) throws Exception {
        AppModel m = newModel(dir);
        EquipmentSeries s = series();
        String json = new ObjectMapper().writeValueAsString(s);

        m.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                s.getId(), "EQUIPMENT_SERIES", s.getName(), json, 1, false)));
        assertEquals(1, m.getEquipmentSeries().size());
        assertEquals(2, m.getEquipmentSeries().get(0).getCards().size());

        EquipmentPreset h5 = seriesModel(s, "H5", 2, 2);
        assertEquals(2, m.cardTemplatesOf(h5).size());

        m.applyLibrarySyncItems(List.of(new LibrarySyncClient.LibraryItemDto(
                s.getId(), "EQUIPMENT_SERIES", s.getName(), json, 2, true)));
        assertTrue(m.getEquipmentSeries().isEmpty());
        assertEquals(0, m.cardTemplatesOf(h5).size(), "серии нет — модель работает только со своими картами");
    }

    @Test
    void choosingASeriesOnAPresetOpensItsCardsAndClearingItDropsTheLimits(@TempDir Path dir) {
        AppModel m = newModel(dir);
        EquipmentSeries s = series();
        m.getWorkspace().getSharedEquipmentSeries().add(s);
        EquipmentPreset p = new EquipmentPreset();
        p.setMode(SchemaMode.SIGNAL);
        p.setCategory(SchemaNodeType.CONTROLLER);
        p.setName("H5");
        assertEquals(0, m.cardTemplatesOf(p).size());

        m.setEquipmentPresetSeries(p, s.getId(), 4, 2);
        assertEquals(2, m.cardTemplatesOf(p).size(), "выбор серии открывает доступ к картам серии");
        assertEquals(4, p.getMaxInputCards());
        assertEquals(2, p.getMaxOutputCards());

        m.setEquipmentPresetSeries(p, null, 4, 2);
        assertNull(p.getSeriesId());
        assertNull(p.getMaxInputCards());
        assertNull(p.getMaxOutputCards());
        assertEquals(0, m.cardTemplatesOf(p).size());
    }
}
