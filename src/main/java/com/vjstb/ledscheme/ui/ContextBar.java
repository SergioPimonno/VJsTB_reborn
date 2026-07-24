package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Компактная строка выбора «Проект → Сцена → (Экран)», используется на этапах,
 * где сама структура проекта не редактируется (Питание, Сигнал, Генерация масок, Вывод) —
 * навигация по уже созданным сущностям без громоздких списков.
 */
public class ContextBar extends JPanel {

    private final AppModel model;
    private final boolean includeScreen;
    private boolean refreshing;

    private final JComboBox<Project> projectCombo = new JComboBox<>();
    private final JComboBox<Scene> sceneCombo = new JComboBox<>();
    private final JComboBox<Screen> screenCombo = new JComboBox<>();

    public ContextBar(AppModel model, boolean includeScreen) {
        this.model = model;
        this.includeScreen = includeScreen;
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        projectCombo.setRenderer(new NamedRenderer<Project>(Project::getName, null));
        sceneCombo.setRenderer(new NamedRenderer<Scene>(Scene::getName, null));
        screenCombo.setRenderer(new NamedRenderer<Screen>(Screen::getName, s -> s.getCols() + "×" + s.getRows()));

        add(new JLabel("Проект:"));
        add(projectCombo);
        add(new JLabel("Сцена:"));
        add(sceneCombo);
        if (includeScreen) {
            add(new JLabel("Экран:"));
            add(screenCombo);
        }

        projectCombo.addActionListener(e -> {
            if (refreshing) return;
            Project p = (Project) projectCombo.getSelectedItem();
            if (p != null && p != model.getCurrentProject()) model.selectProject(p);
        });
        sceneCombo.addActionListener(e -> {
            if (refreshing) return;
            Scene s = (Scene) sceneCombo.getSelectedItem();
            if (s != null && s != model.getCurrentScene()) model.selectScene(s);
        });
        screenCombo.addActionListener(e -> {
            if (refreshing) return;
            Screen s = (Screen) screenCombo.getSelectedItem();
            if (s != null && s != model.getCurrentScreen()) model.selectScreen(s);
        });

        model.addListener(this::rebuild);
        rebuild();
    }

    public void rebuild() {
        refreshing = true;
        try {
            populate(projectCombo, model.getProjects(), model.getCurrentProject());
            List<Scene> scenes = model.getCurrentProject() != null ? model.getCurrentProject().getScenes() : List.of();
            populate(sceneCombo, scenes, model.getCurrentScene());
            if (includeScreen) {
                List<Screen> screens = model.getCurrentScene() != null ? model.getCurrentScene().getScreens() : List.of();
                populate(screenCombo, screens, model.getCurrentScreen());
            }
        } finally {
            refreshing = false;
        }
    }

    private <T> void populate(JComboBox<T> combo, List<T> items, T select) {
        DefaultComboBoxModel<T> m = new DefaultComboBoxModel<>();
        for (T i : items) {
            m.addElement(i);
        }
        combo.setModel(m);
        if (select != null) {
            combo.setSelectedItem(select);
        }
        combo.setEnabled(!items.isEmpty());
    }
}
