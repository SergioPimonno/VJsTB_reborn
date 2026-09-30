package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30, п. 9: «Сохранить как пресет» в форме «Добавить узел»
 *  сохраняла ПУСТОЙ пресет (только подпись, без карт) — теперь пресет создаётся из
 *  блока на схеме через меню ПКМ, {@link AppModel#saveSchemaNodeAsPreset}, вместе со
 *  всеми картами и разъёмами питания. Тесты фиксируют: содержимое совпадает, id
 *  свежие, копия глубокая, пустой узел отвергается, замена личного пресета сохраняет
 *  id, общий пресет не заменяется. Отдельный файл — {@code AppModelTest} правят
 *  параллельные агенты волны. */
class SaveSchemaNodeAsPresetTest {

    private AppModel freshModel(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        return model;
    }

    /** Узел сигнала с двумя картами (у одной — порт с ролью/thru) и без разъёмов питания. */
    private SchemaNode signalNodeWithCards(AppModel model) {
        SchemaNode node = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "Мой блок", 0, 0, null);
        CardPort genlock = new CardPort("Genlock", PortDirection.OUT, 1);
        genlock.setRole(InterfaceRole.SYNC);
        genlock.setThru(Boolean.TRUE);
        node.getCards().add(new SchemaCard("Синхро", new ArrayList<>(List.of(genlock))));
        node.getCards().add(new SchemaCard("Выход",
                new ArrayList<>(List.of(new CardPort("Cat6/RJ45", PortDirection.OUT, 4)))));
        return node;
    }

    private static List<String> signature(List<SchemaCard> cards) {
        List<String> sig = new ArrayList<>();
        for (SchemaCard c : cards) {
            sig.add("card:" + c.getName());
            for (CardPort p : c.getPorts()) {
                sig.add(portSig(p));
            }
        }
        return sig;
    }

    private static String portSig(CardPort p) {
        return p.getConnectorType() + "|" + p.getDirection() + "|" + p.getCount() + "|" + p.getRole() + "|" + p.getThru();
    }

    @Test
    void savedPresetHasSameCardsAndPortsButFreshIds(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);

        EquipmentPreset preset = model.saveSchemaNodeAsPreset(node, "  Мой пресет ", " описание ");

        assertEquals("Мой пресет", preset.getName());
        assertEquals("описание", preset.getDescription());
        assertEquals(SchemaMode.SIGNAL, preset.getMode());
        assertEquals(SchemaNodeType.CONTROLLER, preset.getCategory());
        assertEquals(signature(node.getCards()), signature(preset.getCards()));
        for (int i = 0; i < node.getCards().size(); i++) {
            SchemaCard src = node.getCards().get(i);
            SchemaCard dst = preset.getCards().get(i);
            assertNotEquals(src.getId(), dst.getId());
            for (int j = 0; j < src.getPorts().size(); j++) {
                assertNotEquals(src.getPorts().get(j).getId(), dst.getPorts().get(j).getId());
            }
        }
        assertTrue(model.getEquipmentPresets().contains(preset));
    }

    @Test
    void powerConnectorsAreSavedToo(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Щит", 0, 0, null);
        CardPort in = new CardPort("CEE 32A", PortDirection.IN, 1);
        CardPort out = new CardPort("CEE 16A", PortDirection.OUT, 6);
        node.getPowerConnectors().add(in);
        node.getPowerConnectors().add(out);

        EquipmentPreset preset = model.saveSchemaNodeAsPreset(node, "Щит 32", null);

        assertEquals(2, preset.getPowerConnectors().size());
        assertEquals(portSig(in), portSig(preset.getPowerConnectors().get(0)));
        assertEquals(portSig(out), portSig(preset.getPowerConnectors().get(1)));
        assertNotEquals(in.getId(), preset.getPowerConnectors().get(0).getId());
        assertEquals("", preset.getDescription());
    }

    @Test
    void roundTripThroughAddSchemaNodeFromPresetGivesSameSet(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        EquipmentPreset preset = model.saveSchemaNodeAsPreset(node, "Мой пресет", "");

        SchemaNode again = model.addSchemaNodeFromPreset(SchemaMode.SIGNAL, preset, 10, 10);

        assertEquals(signature(node.getCards()), signature(again.getCards()));
        assertEquals("Мой пресет", again.getLabel());
    }

    @Test
    void emptyNodeIsRejected(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode empty = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Пусто", 0, 0, null);
        int before = model.getEquipmentPresets().size();

        assertThrows(IllegalArgumentException.class, () -> model.saveSchemaNodeAsPreset(empty, "X", ""));
        assertEquals(before, model.getEquipmentPresets().size());
    }

    @Test
    void blankNameIsRejected(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        assertThrows(IllegalArgumentException.class, () -> model.saveSchemaNodeAsPreset(node, "   ", ""));
    }

    @Test
    void editingNodeCardsAfterSavingDoesNotChangePreset(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        EquipmentPreset preset = model.saveSchemaNodeAsPreset(node, "Мой пресет", "");
        List<String> before = signature(preset.getCards());

        node.getCards().get(1).getPorts().get(0).setCount(99);
        node.getCards().get(0).setName("Переименована");
        node.getCards().remove(1);

        assertEquals(before, signature(preset.getCards()));
    }

    @Test
    void replacingPersonalPresetKeepsItsIdAndNameAndSwapsContents(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        EquipmentPreset preset = model.saveSchemaNodeAsPreset(node, "Мой пресет", "описание");
        String id = preset.getId();

        node.getCards().remove(1);
        node.getCards().add(new SchemaCard("Новая",
                new ArrayList<>(List.of(new CardPort("HDMI", PortDirection.IN, 1)))));
        assertSame(preset, model.findPersonalPresetForNode(node, " мой ПРЕСЕТ "));
        model.replaceEquipmentPresetContents(preset, node);

        assertEquals(id, preset.getId());
        assertEquals("Мой пресет", preset.getName());
        assertEquals("описание", preset.getDescription());
        assertEquals(signature(node.getCards()), signature(preset.getCards()));
        assertTrue(preset.getDefaultCardTemplateIds().isEmpty());
        assertEquals(1, model.getEquipmentPresets().stream().filter(p -> "Мой пресет".equals(p.getName())).count());
    }

    @Test
    void presetOfOtherCategoryWithSameNameIsNotAMatch(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Мой пресет", "", null);

        assertNull(model.findPersonalPresetForNode(node, "Мой пресет"));
    }

    @Test
    void sharedPresetIsNeverReplacedNorMatched(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        SchemaNode node = signalNodeWithCards(model);
        EquipmentPreset shared = new EquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "Общий", "");
        model.getWorkspace().getSharedEquipmentPresets().add(shared);
        assertTrue(model.isSharedEquipmentPreset(shared.getId()));

        assertNull(model.findPersonalPresetForNode(node, "Общий"));
        assertThrows(IllegalArgumentException.class, () -> model.replaceEquipmentPresetContents(shared, node));
        assertTrue(shared.getCards().isEmpty());
    }
}
