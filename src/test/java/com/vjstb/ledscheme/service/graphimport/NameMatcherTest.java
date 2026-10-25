package com.vjstb.ledscheme.service.graphimport;

import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.port;
import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.power;
import static com.vjstb.ledscheme.service.graphimport.GraphImportFixtures.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.graphimport.NameMatcher.Candidate;
import com.vjstb.ledscheme.service.graphimport.NameMatcher.Reason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Подбор оборудования по названию на фрагменте библиотеки пользователя (имена из его {@code library.json}). */
class NameMatcherTest {

    private final List<EquipmentPreset> library = new ArrayList<>(List.of(
            signal("CVT4k", port("Fiber", PortDirection.IN, 2), port("Cat6/RJ45", PortDirection.OUT, 16)),
            signal("MCTRL4k", port("Fiber", PortDirection.OUT, 2)),
            signal("H2"),
            signal("H2", port("Fiber MMF LC", PortDirection.OUT, 2)),
            power("AlpenBox 125A->8xSocapex", port("CEE 125A", PortDirection.IN, 1), port("Socapex", PortDirection.OUT, 8)),
            power("AlpenBox 125A->4x32A", port("CEE 125A", PortDirection.IN, 1), port("CEE 32A", PortDirection.OUT, 4)),
            power("Проходная Socapex", port("Socapex", PortDirection.IN, 1), port("Socapex", PortDirection.OUT, 1)),
            power("UPS FOH")));

    @Test
    void exactNameWithAndWithoutTrailingNoteInBrackets() {
        List<Candidate> c = NameMatcher.rank("CVT4k", library, SchemaMode.SIGNAL, 5);
        assertEquals("CVT4k", c.get(0).preset().getName());
        assertEquals(Reason.EXACT, c.get(0).reason());
        assertEquals(1.0, c.get(0).score());
        assertEquals(Reason.EXACT, NameMatcher.rank("CVT4k (Main)", library, SchemaMode.SIGNAL, 5).get(0).reason());
        assertEquals(Reason.EXACT, NameMatcher.rank("cvt4K", library, SchemaMode.SIGNAL, 5).get(0).reason());
    }

    @Test
    void ofTwoPresetsWithTheSameNameTheOneWithPortsWins() {
        List<Candidate> c = NameMatcher.rank("H2", library, SchemaMode.SIGNAL, 5);
        assertEquals(1, c.stream().filter(x -> x.preset().getName().equals("H2")).count());
        assertEquals("Fiber MMF LC", c.get(0).preset().getCards().get(0).getPorts().get(0).getConnectorType());
    }

    @Test
    void sameWordsInAnotherOrder() {
        Candidate c = NameMatcher.rank("FOH UPS", library, SchemaMode.POWER, 5).get(0);
        assertEquals("UPS FOH", c.preset().getName());
        assertEquals(Reason.WORD_ORDER, c.reason());
        assertTrue(c.score() >= NameMatcher.AUTO_SCORE);
    }

    @Test
    void sameDistroAndInputButAnotherOutputSetNeedsConfirmation() {
        List<Candidate> c = NameMatcher.rank("AlpenBox 125A->4xSocapex", library, SchemaMode.POWER, 5);
        Candidate best = c.get(0);
        assertEquals("AlpenBox 125A->8xSocapex", best.preset().getName());
        assertEquals(Reason.SPEC_NEAR, best.reason());
        assertTrue(best.score() < NameMatcher.AUTO_SCORE && best.score() >= NameMatcher.PRESELECT_SCORE, "" + best.score());
        // 4x32A той же серии — тоже предлагается, но слабее
        assertTrue(c.stream().anyMatch(x -> x.preset().getName().equals("AlpenBox 125A->4x32A")
                && x.score() < best.score()));
    }

    @Test
    void anUnknownDeviceHasNoCandidatesAndAWeakSharedWordIsOnlyOffered() {
        assertTrue(NameMatcher.rank("Mediabort", library, SchemaMode.SIGNAL, 5).isEmpty());
        List<Candidate> c = NameMatcher.rank("Socapex->6x16A", library, SchemaMode.POWER, 5);
        assertEquals("Проходная Socapex", c.get(0).preset().getName());
        assertEquals(Reason.SHARED_WORD, c.get(0).reason());
        assertFalse(c.get(0).score() >= NameMatcher.PRESELECT_SCORE, "общее слово не подставляется без спроса");
    }

    @Test
    void onlyPresetsOfTheRequestedModeAreConsidered() {
        assertTrue(NameMatcher.rank("AlpenBox 125A->8xSocapex", library, SchemaMode.SIGNAL, 5).isEmpty());
        assertSame(SchemaMode.POWER,
                NameMatcher.rank("AlpenBox 125A->8xSocapex", library, SchemaMode.POWER, 5).get(0).preset().getMode());
    }

    @Test
    void connectorFamiliesFromLibraryNamesAndEdgeLabels() {
        assertEquals("fiber", EdgeHints.familyOfConnector("Fiber MMF LC"));
        assertEquals("ethernet", EdgeHints.familyOfConnector("Cat6/RJ45"));
        assertEquals("ethernet", EdgeHints.familyOfConnector("Ethernet Cat5e"));
        assertEquals("cee32", EdgeHints.familyOfConnector("CEE 32A"));
        assertEquals("genlock", EdgeHints.familyOfConnector("Genlock (SDI)"));
        assertEquals("dp", EdgeHints.familyOfConnector("DisplayPort 1.2"));

        assertEquals("fiber", EdgeHints.familyOfLabel("fiber 1"));
        assertEquals("fiber", EdgeHints.familyOfLabel("fiber5"));
        assertEquals("sdi", EdgeHints.familyOfLabel("SDI"));
        assertEquals("cee32", EdgeHints.familyOfLabel("CEE32A"));
        assertEquals("truecon", EdgeHints.familyOfLabel("6x16A->TRUECON"));
        assertEquals(null, EdgeHints.familyOfLabel("1-4 main / 5-8 backup"));
        assertEquals(null, EdgeHints.familyOfLabel(""));
    }
}
