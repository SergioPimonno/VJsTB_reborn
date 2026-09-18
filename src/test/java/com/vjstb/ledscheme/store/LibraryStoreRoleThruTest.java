package com.vjstb.ledscheme.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.Library;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T5.1 — приёмка "синхронизация библиотеки
 *  переносит поля": {@link InterfaceType#getDefaultRole()} и {@link CardPort#getRole()}/
 *  {@link CardPort#getThru()} — два новых поля из T1.1 (ledscheme-model) — должны
 *  пережить обычный цикл сохранения/загрузки {@code library.json} БЕЗ сети, тем же
 *  способом, что {@link LibraryStoreTest} проверяет остальные поля библиотеки (это и
 *  есть путь синхронизации на клиенте — сервер хранит элемент как непрозрачный
 *  payloadJson, ровно то же дерево полей, что здесь сериализуется в файл). */
class LibraryStoreRoleThruTest {

    @Test
    void interfaceTypeDefaultRoleSurvivesRoundTrip(@TempDir Path dir) {
        Library library = new Library();
        InterfaceType genlock = new InterfaceType("Genlock", List.of("Blackburst", "Tri-Level"));
        genlock.setDefaultRole(InterfaceRole.SYNC);
        InterfaceType untouched = new InterfaceType("XLR", List.of());
        // defaultRole остаётся null — старые виды интерфейса без роли (до задачи
        // T1.1) не должны получить её "из ниоткуда" при обычном сохранении/загрузке.
        library.setInterfaceTypes(new ArrayList<>(List.of(genlock, untouched)));

        File libraryFile = new File(dir.toFile(), "library.json");
        new LibraryStore(libraryFile).save(library);
        Library reloaded = new LibraryStore(libraryFile).load();

        assertEquals(2, reloaded.getInterfaceTypes().size());
        InterfaceType reloadedGenlock = byName(reloaded.getInterfaceTypes(), "Genlock");
        assertEquals(InterfaceRole.SYNC, reloadedGenlock.getDefaultRole());
        InterfaceType reloadedXlr = byName(reloaded.getInterfaceTypes(), "XLR");
        assertNull(reloadedXlr.getDefaultRole());
    }

    @Test
    void cardPortRoleAndThruInsideEquipmentPresetSurviveRoundTrip(@TempDir Path dir) {
        // Контрольный случай PLAN.md §2.3: MCTRL4K — петля генлока (транзит, роль
        // SYNC явно) и выход Cat6/RJ45 (роль/транзит НЕ заданы явно — угадываются на
        // лету клиентом, поэтому в библиотеке должны остаться именно null, не какое-то
        // "посчитанное" значение — иначе угадывание на лету стало бы неотличимо от
        // явной правки в таблице CardsConfigDialog, см. её "(угадано)"-пометку).
        CardPort genlockLoop = new CardPort("Genlock", PortDirection.OUT, 1);
        genlockLoop.setRole(InterfaceRole.SYNC);
        genlockLoop.setThru(Boolean.TRUE);
        CardPort dataOut = new CardPort("Cat6/RJ45", PortDirection.OUT, 1);

        SchemaCard card = new SchemaCard("Выход", new ArrayList<>(List.of(genlockLoop, dataOut)));
        EquipmentPreset preset = new EquipmentPreset();
        preset.setMode(SchemaMode.SIGNAL);
        preset.setCategory(SchemaNodeType.CONTROLLER);
        preset.setName("MCTRL4K");
        preset.setCards(new ArrayList<>(List.of(card)));

        Library library = new Library();
        library.setEquipmentPresets(new ArrayList<>(List.of(preset)));

        File libraryFile = new File(dir.toFile(), "library.json");
        new LibraryStore(libraryFile).save(library);
        Library reloaded = new LibraryStore(libraryFile).load();

        List<CardPort> ports = reloaded.getEquipmentPresets().get(0).getCards().get(0).getPorts();
        CardPort reloadedLoop = byConnectorType(ports, "Genlock");
        assertEquals(InterfaceRole.SYNC, reloadedLoop.getRole());
        assertEquals(Boolean.TRUE, reloadedLoop.getThru());

        CardPort reloadedData = byConnectorType(ports, "Cat6/RJ45");
        assertNull(reloadedData.getRole());
        assertNull(reloadedData.getThru());
    }

    private static InterfaceType byName(List<InterfaceType> types, String name) {
        return types.stream().filter(t -> t.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("не найден вид интерфейса " + name));
    }

    private static CardPort byConnectorType(List<CardPort> ports, String connectorType) {
        return ports.stream().filter(p -> p.getConnectorType().equals(connectorType)).findFirst()
                .orElseThrow(() -> new AssertionError("не найдено гнездо " + connectorType));
    }
}
