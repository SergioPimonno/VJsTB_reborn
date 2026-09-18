package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T5.1 — приёмка "роли сохраняются в пресет
 *  и копируются в блок при добавлении из пресета": {@link AppModel#addSchemaNodeFromPreset}
 *  уходит через {@code SchemaCard.copy()}/{@code CardPort.copy()} (не пересобирает
 *  гнёзда с нуля), а эти {@code copy()} уже копируют {@code role}/{@code thru} (T1.1,
 *  ledscheme-model) — этот тест фиксирует, что цепочка работает целиком end-to-end
 *  без ручной правки AppModel в этой задаче (файл "горячий", см. PLAN.md §0 правило 10).
 *  Отдельный файл, а не новый метод в {@code AppModelTest} — тот сейчас правит
 *  параллельный агент (см. git status на момент начала T5.1). */
class SchemaNodeFromPresetRoleThruTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    @Test
    void cardPortRoleAndThruSurviveInstantiationFromPreset(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        EquipmentPreset preset = model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER,
                "MCTRL4K", "Видеопроцессор", null);
        CardPort genlockLoop = new CardPort("Genlock", PortDirection.OUT, 1);
        genlockLoop.setRole(InterfaceRole.SYNC);
        genlockLoop.setThru(Boolean.TRUE);
        CardPort dataOut = new CardPort("Cat6/RJ45", PortDirection.OUT, 1);
        // role/thru намеренно не заданы — должны остаться null и на узле (угадывание
        // происходит на лету при отрисовке/в таблицах редактора, не "запекается" при
        // копировании из пресета).
        model.addCardToPreset(preset, "Выход", java.util.List.of(genlockLoop, dataOut));

        SchemaNode node = model.addSchemaNodeFromPreset(SchemaMode.SIGNAL, preset, 0, 0);

        CardPort nodeLoop = portByType(node, "Genlock");
        assertEquals(InterfaceRole.SYNC, nodeLoop.getRole());
        assertEquals(Boolean.TRUE, nodeLoop.getThru());
        // Копия, а не общая ссылка — id гнезда обязан обновиться (см. соседний тест
        // multipleNodesFromSamePresetGetIndependentPortIds в AppModelTest на ту же
        // причину), иначе лимит "максимум линий на гнезде" делился бы на все узлы.
        assertNotEquals(genlockLoop.getId(), nodeLoop.getId());

        CardPort nodeData = portByType(node, "Cat6/RJ45");
        assertEquals(null, nodeData.getRole());
        assertEquals(null, nodeData.getThru());
    }

    @Test
    void powerConnectorThruSurvivesInstantiationFromPreset(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        EquipmentPreset preset = model.addEquipmentPreset(SchemaMode.POWER, SchemaNodeType.DISTRO,
                "Проходная", "Power passthrough", null);
        CardPort in = model.addPowerConnectorToPreset(preset, "CEE 32A", PortDirection.IN, 1);
        CardPort out = model.addPowerConnectorToPreset(preset, "CEE 32A", PortDirection.OUT, 1);
        // В библиотеке транзит явно НЕ проставлен (null = авто) — эта же связка
        // "1 вход + 1 выход одного типа, count==1" — контрольный случай PLAN.md §2.3
        // ("Проходная" CEE 32A). Тест здесь про то, что null переживает копирование,
        // а не про сам расчёт авто-угадывания (см. ThruResolverTest).
        SchemaNode node = model.addSchemaNodeFromPreset(SchemaMode.POWER, preset, 0, 0);
        CardPort nodeOut = node.getPowerConnectors().stream()
                .filter(p -> p.getDirection() == PortDirection.OUT).findFirst().orElseThrow();
        assertEquals(null, nodeOut.getThru());
        assertNotEquals(out.getId(), nodeOut.getId());
    }

    private static CardPort portByType(SchemaNode node, String connectorType) {
        for (var card : node.getCards()) {
            for (CardPort p : card.getPorts()) {
                if (p.getConnectorType().equals(connectorType)) {
                    return p;
                }
            }
        }
        throw new AssertionError("не найдено гнездо " + connectorType);
    }
}
