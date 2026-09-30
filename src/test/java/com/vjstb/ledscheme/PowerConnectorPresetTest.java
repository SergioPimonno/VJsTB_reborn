package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.Library;
import com.vjstb.ledscheme.model.PowerConnectorPreset;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.LibraryStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-09-30: "Тип разъёма" в PowerConnectorsConfigDialog был жёстко зашитым
 *  списком имён без возможности редактирования — теперь это личная библиотека
 *  ({@link PowerConnectorPreset}, Библиотеки → «Разъёмы питания»), по умолчанию равная
 *  прежнему зашитому списку. */
class PowerConnectorPresetTest {

    private static final List<String> DEFAULT_NAMES = List.of("PowerCon TRUE1", "PowerCon 20A", "CEE 16A",
            "CEE 32A", "CEE 63A", "CEE 125A", "Schuko", "IEC C13", "IEC C19", "Powerlock");

    @Test
    void freshLibraryDefaultsToThePreviouslyHardcodedList() {
        Library library = new Library();
        assertEquals(DEFAULT_NAMES, names(library.getPowerConnectorPresets()));
    }

    /** library.json, сохранённый ДО этой правки — поля powerConnectorPresets в файле нет
     *  вовсе. Загрузка должна дать тот же список умолчаний, что и у совсем новой
     *  библиотеки — не пустой список (значит "по умолчанию равно текущему списку"
     *  выполняется и для уже существующих пользователей, не только для новых). */
    @Test
    void oldLibraryJsonWithoutTheFieldGetsTheDefaultList(@TempDir Path dir) throws IOException {
        File libraryFile = new File(dir.toFile(), "library.json");
        new ObjectMapper().writeValue(libraryFile, java.util.Map.of("cabinetTypes", List.of()));

        Library loaded = new LibraryStore(libraryFile).load();
        assertEquals(DEFAULT_NAMES, names(loaded.getPowerConnectorPresets()));
    }

    /** А если пользователь явно удалил все разъёмы (сохранённый пустой список) —
     *  загрузка не должна тайком вернуть дефолты обратно. */
    @Test
    void explicitlyEmptyListIsRespectedNotResetToDefaults(@TempDir Path dir) throws IOException {
        File libraryFile = new File(dir.toFile(), "library.json");
        new ObjectMapper().writeValue(libraryFile, java.util.Map.of("powerConnectorPresets", List.of()));

        Library loaded = new LibraryStore(libraryFile).load();
        assertTrue(loaded.getPowerConnectorPresets().isEmpty());
    }

    private static List<String> names(List<PowerConnectorPreset> presets) {
        return presets.stream().map(PowerConnectorPreset::getName).collect(Collectors.toList());
    }

    // ---- AppModel CRUD ----

    private static AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    @Test
    void addRenameDelete(@TempDir Path dir) {
        AppModel model = model(dir);
        assertEquals(DEFAULT_NAMES, names(model.getPowerConnectorPresets()));

        PowerConnectorPreset added = model.addPowerConnectorPreset("  CEE 32A T2  ");
        assertEquals("CEE 32A T2", added.getName(), "обрезаются пробелы по краям");
        assertTrue(names(model.getPowerConnectorPresets()).contains("CEE 32A T2"));

        model.renamePowerConnectorPreset(added, "CEE 32A Type 2");
        assertEquals("CEE 32A Type 2", added.getName());
        assertFalse(names(model.getPowerConnectorPresets()).contains("CEE 32A T2"));

        model.deletePowerConnectorPreset(added);
        assertFalse(names(model.getPowerConnectorPresets()).contains("CEE 32A Type 2"));
        assertEquals(DEFAULT_NAMES.size(), model.getPowerConnectorPresets().size());
    }

    @Test
    void addingDuplicateNameCaseInsensitiveReturnsExistingInsteadOfDuplicating(@TempDir Path dir) {
        AppModel model = model(dir);
        int before = model.getPowerConnectorPresets().size();

        PowerConnectorPreset again = model.addPowerConnectorPreset("cee 32a");
        assertEquals("CEE 32A", again.getName(), "вернулась существующая запись, не новая");
        assertEquals(before, model.getPowerConnectorPresets().size(), "дубликат не добавляется");
    }

    @Test
    void blankNameIsRejected(@TempDir Path dir) {
        AppModel model = model(dir);
        assertThrows(IllegalArgumentException.class, () -> model.addPowerConnectorPreset("   "));
        assertThrows(IllegalArgumentException.class,
                () -> model.renamePowerConnectorPreset(model.getPowerConnectorPresets().get(0), ""));
    }
}
