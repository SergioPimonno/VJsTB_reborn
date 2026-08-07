package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.Scenario;
import com.vjstb.ledscheme.model.ScenarioStep;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.sync.LibrarySyncClient;
import com.vjstb.ledscheme.ui.ScenarioListDialog;
import com.vjstb.ledscheme.ui.ScenarioPlayerDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Сквозной тест новой фичи «Интерактивные примеры» в РЕАЛЬНОМ, по-настоящему
 * собранном UI (те же классы, что видит пользователь, реальный paint-цикл — не
 * headless-заглушка), по образцу {@link GuiSmokeTest}: строит окна и закрывает,
 * не показывая на экране (ScenarioPlayerDialog/ScenarioListDialog теперь имеют
 * публичные конструкторы отдельно от статического show(...), ровно как MainFrame).
 * Проверяет весь путь: синк с сервера (реальный JSON → AppModel.applyLibrarySyncItems,
 * тот же код, что реально дёргает LibrarySyncDialog) → список сценариев →
 * проигрыватель, включая реальный хит-тест хотспота (клик мимо — не продвигает,
 * клик внутри — продвигает, шаг без хотспота — идёт кнопкой «Далее»).
 */
class ScenarioFeatureGuiTest {

    // 1×1 px PNG, base64 — реальный декодируемый файл (ImageIO.read должен успешно
    // его прочитать), размер намеренно минимален — тест проверяет логику, не рендер.
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void scenarioSyncsFromServerAndPlaysInRealDialogs(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));

            // Тот же payload и тот же путь (dto.kind()=="INTERACTIVE_SCENARIOS"), что
            // реально приходит с сервера через LibrarySyncClient.fetchChanges().
            String payload = "{\"scenarios\":[{\"id\":\"s1\",\"title\":\"Тестовый сценарий\",\"steps\":["
                    + "{\"title\":\"Шаг с хотспотом\",\"bodyHtml\":\"Нажмите на подсвеченную область\","
                    + "\"imageBase64\":\"" + TINY_PNG_BASE64 + "\","
                    + "\"hotspotX\":0.2,\"hotspotY\":0.2,\"hotspotWidth\":0.4,\"hotspotHeight\":0.4},"
                    + "{\"title\":\"Средний шаг без хотспота\",\"bodyHtml\":\"Жмите «Далее».\"},"
                    + "{\"title\":\"Финальный шаг\",\"bodyHtml\":\"Готово, сценарий пройден.\"}]}]}";
            AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(
                    new LibrarySyncClient.LibraryItemDto("global-interactive-scenarios", "INTERACTIVE_SCENARIOS",
                            "Интерактивные сценарии", payload, 1, false)));
            assertEquals(1, summary.added(), "синк должен добавить один сценарий");

            List<Scenario> scenarios = model.getWorkspace().getLibrary().getInteractiveScenarios();
            assertEquals(1, scenarios.size());
            Scenario scenario = scenarios.get(0);
            assertEquals("Тестовый сценарий", scenario.getTitle());
            assertEquals(3, scenario.getSteps().size());
            ScenarioStep step1 = scenario.getSteps().get(0);
            ScenarioStep step2 = scenario.getSteps().get(1);
            ScenarioStep step3 = scenario.getSteps().get(2);
            assertTrue(step1.hasHotspot());
            assertFalse(step2.hasHotspot());
            assertFalse(step3.hasHotspot());

            // Реальный список сценариев — то же окно, что «Настройки → Интерактивные
            // примеры…» и кнопка в GuideDialog реально открывают. Строим ОБА случая
            // (пусто/непусто), чтобы поймать ошибку сборки в любой из веток.
            new ScenarioListDialog(null, List.of()).dispose();
            new ScenarioListDialog(null, scenarios).dispose();

            // Реальный проигрыватель — та же логика, что видит пользователь.
            ScenarioPlayerDialog player = new ScenarioPlayerDialog(null, scenario);
            player.pack();
            try {
                assertEquals(0, player.getStepIndex());
                assertFalse(player.isBackEnabled(), "на первом шаге «Назад» должна быть недоступна");
                assertFalse(player.isNextEnabled(),
                        "шаг с хотспотом не должен продвигаться кнопкой «Далее» — только кликом");

                player.simulateImageClickAt(0.05, 0.05); // мимо хотспота (0.2..0.6 x 0.2..0.6)
                assertEquals(0, player.getStepIndex(), "клик мимо хотспота не должен продвигать шаг");

                player.simulateImageClickAt(0.4, 0.4); // внутри хотспота (0.2..0.6 x 0.2..0.6)
                assertEquals(1, player.getStepIndex(), "клик внутри хотспота должен продвинуть на следующий шаг");
                assertTrue(player.isBackEnabled());
                assertTrue(player.isNextEnabled(), "средний шаг без хотспота продвигается кнопкой «Далее»");

                player.goToNext();
                assertEquals(2, player.getStepIndex(), "«Далее» на среднем шаге должна продвинуть дальше");
                assertFalse(player.isNextEnabled(), "на последнем шаге «Далее» недоступна");
                assertTrue(player.isBackEnabled());

                player.goToPrevious();
                player.goToPrevious();
                assertEquals(0, player.getStepIndex(), "два «Назад» должны вернуть к первому шагу");
            } finally {
                player.dispose();
            }
        });
    }
}
