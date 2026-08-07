package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Пошаговый интерактивный сценарий (Руководство/стартовое окно) — упорядоченный
 * список {@link ScenarioStep}, проигрывается кликами по хотспотам прямо в
 * приложении (см. ScenarioPlayerDialog), а не просто просматривается как видео.
 * Общая справочная данные — редактируется только через отдельную админ-консоль
 * (ledscheme-admin, ScenarioEditorPanel) и синхронизируется на клиент как часть
 * {@link Library} (см. AppModel.applyLibrarySyncItems, кейс
 * INTERACTIVE_SCENARIOS) — тот же принцип, что уже у {@link ContentSection}.
 */
public class Scenario {

    private String id = UUID.randomUUID().toString();
    private String title = "";
    private List<ScenarioStep> steps = new ArrayList<>();

    public Scenario() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ScenarioStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ScenarioStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public Scenario copy() {
        Scenario s = new Scenario();
        s.id = id;
        s.title = title;
        s.steps = new ArrayList<>();
        for (ScenarioStep step : steps) {
            s.steps.add(step.copy());
        }
        return s;
    }
}
