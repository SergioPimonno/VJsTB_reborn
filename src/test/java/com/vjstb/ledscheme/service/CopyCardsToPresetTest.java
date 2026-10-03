package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос 2026-10-02: копирование карт-шаблонов между оборудованием — копии с новыми id карт и портов,
 *  источник не меняется. */
class CopyCardsToPresetTest {

    @Test
    void copiesGetFreshIdsAndSourceIsUntouched(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        EquipmentPreset from = new EquipmentPreset();
        EquipmentPreset to = new EquipmentPreset();
        SchemaCard card = new SchemaCard("Выход", List.of(new CardPort("SDI", PortDirection.OUT, 4)));
        from.getCards().add(card);
        to.getCards().add(new SchemaCard("Своя", List.of()));

        List<SchemaCard> added = model.copyCardsToPreset(to, List.of(card));

        assertEquals(1, added.size());
        assertEquals(2, to.getCards().size());
        assertEquals("Выход", to.getCards().get(1).getName());
        assertEquals(card.getPorts().get(0).getConnectorType(), to.getCards().get(1).getPorts().get(0).getConnectorType());
        assertNotEquals(card.getId(), to.getCards().get(1).getId());
        assertNotEquals(card.getPorts().get(0).getId(), to.getCards().get(1).getPorts().get(0).getId());
        assertEquals(1, from.getCards().size(), "источник не изменился");
    }
}
