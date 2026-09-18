package com.vjstb.ledscheme.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.NodeOrientation;
import com.vjstb.ledscheme.settings.ArrowPlacement;
import com.vjstb.ledscheme.settings.ConnectorDisplayMode;
import com.vjstb.ledscheme.settings.GroupDisplayMode;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.settings.UserProfile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T1.5: новые настройки ориентации/
 *  раскладки/маршрута/стиля общей схемы, обратная совместимость со старыми
 *  профилями (ДВЕ волны легаси — самая старая {@code connectorsVertical} и более
 *  новая раздельная {@code signal/powerConnectorsVertical}/{@code
 *  signal/powerConnectorDisplayMode}), и то, что старые геттеры/сеттеры продолжают
 *  работать, делегируя в новые поля (акцептанс-критерий T1.5). */
class SchemaSettingsMigrationTest {

    @Test
    void newProfileDefaultsMatchPreviousBehaviorWithoutAnyExplicitChoice(@TempDir Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        UserProfile p = settings.activeProfile();

        assertEquals(NodeOrientation.RIGHT, p.getSignalDefaultOrientation());
        assertEquals(NodeOrientation.RIGHT, p.getPowerDefaultOrientation());
        assertTrue(!p.isSignalConnectorsVertical(), "как и раньше — по умолчанию false (гнёзда слева/справа)");
        // ALWAYS_COLLAPSED, не AUTO — пользователь попросил вернуть старое поведение
        // "группировать порты одного типа в одно гнездо даже при подключении линий"
        // после того, как увидел AUTO вживую (реплика 2026-09-17, отменяет решение D5
        // от 2026-09-16 — см. javadoc GroupDisplayMode).
        assertEquals(GroupDisplayMode.ALWAYS_COLLAPSED, p.getSignalGroupDisplay());
        assertEquals(ConnectorDisplayMode.GROUPED, p.getSignalConnectorDisplayMode(), "старый геттер видит эквивалент GROUPED");
        assertEquals(EdgeRouteMode.AUTO, p.getNewEdgeRouteMode());
        assertEquals(ArrowPlacement.TARGET, p.getSchemaArrowPlacement());
        assertTrue(p.isOrthogonalEdgeEditing());
        assertEquals(SchemaStylePreset.SCREEN, p.getSchemaStylePreset());
    }

    @Test
    void newOrientationSetterIsVisibleThroughLegacyGetterAndSurvivesReload(@TempDir Path dir) {
        File file = new File(dir.toFile(), "settings.json");
        SettingsManager settings = new SettingsManager(new SettingsStore(file));
        settings.setSignalDefaultOrientation(NodeOrientation.LEFT);

        // Старый геттер видит только 2 из 4 состояний — DOWN означает "вертикально",
        // всё остальное (в т.ч. новое LEFT/UP) читается как "не вертикально".
        assertTrue(!settings.activeProfile().isSignalConnectorsVertical());

        SettingsManager reloaded = new SettingsManager(new SettingsStore(file));
        assertEquals(NodeOrientation.LEFT, reloaded.activeProfile().getSignalDefaultOrientation());
    }

    @Test
    void legacySetterIsVisibleThroughNewOrientationGetterAndSurvivesReload(@TempDir Path dir) {
        File file = new File(dir.toFile(), "settings.json");
        SettingsManager settings = new SettingsManager(new SettingsStore(file));
        settings.setPowerConnectorsVertical(true);
        assertEquals(NodeOrientation.DOWN, settings.activeProfile().getPowerDefaultOrientation());

        SettingsManager reloaded = new SettingsManager(new SettingsStore(file));
        assertEquals(NodeOrientation.DOWN, reloaded.activeProfile().getPowerDefaultOrientation());
        assertTrue(reloaded.activeProfile().isPowerConnectorsVertical());
    }

    @Test
    void connectorDisplayModeDelegatesBothWays(@TempDir Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        settings.setSignalGroupDisplay(GroupDisplayMode.ALWAYS_EXPANDED);
        assertEquals(ConnectorDisplayMode.INDIVIDUAL, settings.activeProfile().getSignalConnectorDisplayMode());

        settings.setPowerConnectorDisplayMode(ConnectorDisplayMode.INDIVIDUAL);
        assertEquals(GroupDisplayMode.ALWAYS_EXPANDED, settings.activeProfile().getPowerGroupDisplay());
    }

    @Test
    void oldestLegacySingularFieldMigratesToBothModesOrientation(@TempDir Path dir) throws Exception {
        // Имитация файла настроек, сохранённого ДО разделения на сигнал/питание вовсе
        // (самая старая версия из существующих — единственное поле connectorsVertical
        // на весь профиль, JSON пишется вручную, т.к. этот приём давно удалён из кода).
        File file = new File(dir.toFile(), "settings.json");
        Files.writeString(file.toPath(), """
                {"activeProfileId":"p1","profiles":[{"id":"p1","name":"Default","connectorsVertical":true}]}
                """);

        SettingsManager settings = new SettingsManager(new SettingsStore(file));
        UserProfile p = settings.activeProfile();
        assertEquals(NodeOrientation.DOWN, p.getSignalDefaultOrientation());
        assertEquals(NodeOrientation.DOWN, p.getPowerDefaultOrientation());
        assertTrue(p.isSignalConnectorsVertical());
        assertTrue(p.isPowerConnectorsVertical());
    }

    @Test
    void newerLegacyPerModeFieldsMigrateToNewFields(@TempDir Path dir) throws Exception {
        // Имитация файла настроек, сохранённого ПОСЛЕ разделения сигнал/питание, но ДО
        // этой переработки — есть только старые имена полей.
        File file = new File(dir.toFile(), "settings.json");
        Files.writeString(file.toPath(), """
                {"activeProfileId":"p1","profiles":[{"id":"p1","name":"Default",
                "signalConnectorsVertical":true,"powerConnectorsVertical":false,
                "signalConnectorDisplayMode":"INDIVIDUAL","powerConnectorDisplayMode":"GROUPED"}]}
                """);

        SettingsManager settings = new SettingsManager(new SettingsStore(file));
        UserProfile p = settings.activeProfile();
        assertEquals(NodeOrientation.DOWN, p.getSignalDefaultOrientation());
        assertEquals(NodeOrientation.RIGHT, p.getPowerDefaultOrientation());
        assertEquals(GroupDisplayMode.ALWAYS_EXPANDED, p.getSignalGroupDisplay());
        // GROUPED -> ALWAYS_COLLAPSED, не AUTO — старый GROUPED буквально означал
        // "всегда одна строка «N×Тип»" без учёта занятости связями (см. javadoc
        // UserProfile#setPowerConnectorDisplayMode).
        assertEquals(GroupDisplayMode.ALWAYS_COLLAPSED, p.getPowerGroupDisplay());
    }

    @Test
    void newEdgeRouteModeSetterRejectsManualBySteppingDownToStraight(@TempDir Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setNewEdgeRouteMode(EdgeRouteMode.MANUAL);
        assertEquals(EdgeRouteMode.STRAIGHT, settings.activeProfile().getNewEdgeRouteMode(),
                "MANUAL как умолчание для НОВЫХ связей бессмысленно — сеттер должен понижать его");
    }
}
