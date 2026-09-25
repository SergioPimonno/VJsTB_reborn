package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link AppModel#migrateControllerLibraryToEquipmentPresets()} — одноразовый
 *  перенос устаревшего {@link ControllerType} в {@link EquipmentPreset} (category
 *  == CONTROLLER, слияние библиотеки контроллеров, 2026-09-23). Симулирует
 *  ДОРЕФОРМЕННЫЙ workspace (личный {@code ControllerType} + ссылающийся на него
 *  {@code ControllerInstance} экрана — ровно та форма, что была бы в сохранении
 *  реального пользователя до апгрейда) и проверяет, что после переноса нумерация
 *  портов ЧИСЛЕННО не меняется ни на бит — та же карта/порт, что дала бы старая
 *  живая резолюция через {@code workspace.controllerTypeById(...)}. */
class ControllerLibraryMigrationTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128() {
        CabinetType ct = new CabinetType();
        ct.setName("Test 128x128");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    @Test
    void migrationConvertsLegacyControllerTypeToEquipmentPresetPreservingNumbering(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 2, 2, 0, 0);
        model.selectScreen(screen);

        // Дореформенный workspace: библиотечный ControllerType напрямую в списке
        // (как было бы у существующего пользователя до апгрейда) — H2-подобный,
        // две выходные Ethernet-карты по 4 порта.
        ControllerType legacy = new ControllerType();
        legacy.setName("H2");
        legacy.setVendor("Novastar");
        legacy.setPortBandwidthMbps(1000);
        legacy.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        legacy.getCards().add(new SchemaCard("Карта 2", List.of(new CardPort("Ethernet", PortDirection.OUT, 4))));
        model.getWorkspace().getControllerTypes().add(legacy);

        ControllerInstance ci = new ControllerInstance(legacy.getId(), "Контроллер 1");
        screen.getControllers().add(ci);

        List<String> ids = screen.getCabinets().stream().map(CabinetInstance::getId).toList();
        model.addSignalChain(1, false, List.of(ids.get(0)));

        // Значения ДО миграции — как их вычислила бы старая живая резолюция
        // (workspace.controllerTypeById + методы ControllerType), для сравнения.
        int expectedPortCount = legacy.effectivePortCount();
        int[] expectedPool = legacy.ethernetPoolLocalPort(1);

        model.migrateControllerLibraryToEquipmentPresets();

        assertTrue(model.getWorkspace().getControllerTypes().isEmpty(),
                "старый список типов контроллеров должен опустеть после переноса");
        EquipmentPreset preset = model.getEquipmentPresets().stream()
                .filter(p -> p.getId().equals(legacy.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("мигрированный пресет должен сохранить исходный id"));
        assertEquals(SchemaNodeType.CONTROLLER, preset.getCategory());
        assertEquals("H2", preset.getName());
        assertEquals("Novastar", preset.getVendor());
        assertEquals(2, preset.getCards().size());

        // Экземпляр заморозил ТУ ЖЕ комплектацию — нумерация не сдвинулась ни на бит.
        assertEquals(expectedPortCount, ci.effectivePortCount());
        assertArrayEquals(expectedPool, ci.ethernetPoolLocalPort(1));

        // Контроллер-центричный .scr-путь по-прежнему резолвит тот же (карта, порт).
        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, ci, model);
        assertEquals(1, recs.size());
        assertEquals(0, recs.get(0).cardIndex());
        assertEquals(0, recs.get(0).portInPool());
    }

    @Test
    void migrationReusesExistingSameNamedControllerPresetInsteadOfDuplicating(@TempDir Path dir) {
        // Баг-репорт 2026-09-24: до полного слияния библиотек CONTROLLER уже был
        // доступной категорией у обычных пресетов оборудования — у пользователя
        // одновременно существовал общий ДЕКОРАТИВНЫЙ пресет "MCTRL4k" (с реальными
        // Ethernet-картами, никогда не участвовавший в расключении) И отдельная
        // библиотека контроллеров с "MCTRL4k" того же названия (плоский portCount,
        // без карт). Миграция "в лоб" создавала ВТОРОЙ пресет с тем же именем и без
        // карт, новые/существующие контроллеры сцены привязывались к нему — порты
        // "терялись" (0 Ethernet, "выбрана карта по умолчанию, порты не показываются").
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        // Уже существующий общий декоративный пресет-"дубль" — с реальной картой
        // (16 портов Cat6/RJ45 — та же реальная формулировка разъёма, что у
        // пользователя, НЕ содержащая слова "Ethernet" буквально).
        EquipmentPreset decorative = new EquipmentPreset(com.vjstb.ledscheme.model.SchemaMode.SIGNAL,
                SchemaNodeType.CONTROLLER, "MCTRL4k", "");
        decorative.getCards().add(new SchemaCard("Basic Set", List.of(new CardPort("Cat6/RJ45", PortDirection.OUT, 16))));
        model.getWorkspace().getSharedEquipmentPresets().add(decorative);

        // Устаревший ControllerType того же имени — реальная библиотека контроллеров,
        // плоский portCount, без карт (как оказалось у пользователя).
        ControllerType legacy = new ControllerType();
        legacy.setName("MCTRL4k");
        legacy.setVendor("Novastar");
        legacy.setPortCount(16);
        model.getWorkspace().getSharedControllerTypes().add(legacy);

        ControllerInstance ci = new ControllerInstance(legacy.getId(), "Контроллер 1");
        screen.getControllers().add(ci);

        model.migrateControllerLibraryToEquipmentPresets();

        long mctrlCount = model.getEquipmentPresets().stream()
                .filter(p -> p.getCategory() == SchemaNodeType.CONTROLLER && "MCTRL4k".equalsIgnoreCase(p.getName()))
                .count();
        assertEquals(1, mctrlCount, "миграция не должна плодить дубликат пресета с тем же именем");

        // Ссылка контроллера переехала на УЖЕ существующий (декоративный) пресет —
        // с его реальной картой, а не на пустышку с плоским portCount.
        assertEquals(decorative.getId(), ci.getControllerTypeId());
    }
}
