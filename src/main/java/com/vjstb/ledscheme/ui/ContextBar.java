package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

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
        screenCombo.setRenderer(new NamedRenderer<Screen>(Screen::getName, s -> {
            com.vjstb.ledscheme.model.CabinetType ct = model.typeOf(s);
            return s.getCols() + "×" + s.getRows() + (ct != null ? " · " + ct.getName() : "");
        }));
        // Иначе попап открывается шириной самого комбобокса — длинные названия
        // проектов/сцен заворачиваются NamedRenderer'ом (он подгоняет HTML под
        // list.getWidth(), см. его javadoc) в кашу из перекрывающихся строк вместо
        // аккуратного расширения окошка (жалоба пользователя 2026-09-30, скриншот:
        // "Бармицва Мини-" наехало на "Сц4" снизу).
        widenPopupToFitContents(projectCombo);
        widenPopupToFitContents(sceneCombo);
        widenPopupToFitContents(screenCombo);

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

    /** Подгоняет и ширину, и высоту попапа комбобокса ПЕРЕД тем, как он реально
     *  показывается — стандартный для Swing приём (JPopupMenu#setPopupSize), т.к.
     *  ни JComboBox, ни его UI не делают этого сами: попап всегда открывается
     *  шириной самого комбобокса (см. {@link #preferredPopupWidth}), а высоту
     *  Swing на этот момент ещё не успел честно пересчитать под текущий список
     *  (см. {@link #preferredPopupHeight}). */
    private static void widenPopupToFitContents(JComboBox<?> combo) {
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (!(combo.getUI().getAccessibleChild(combo, 0) instanceof JPopupMenu popup)) {
                    return;
                }
                int width = Math.max(combo.getWidth(), preferredPopupWidth(combo));
                popup.setPopupSize(width, preferredPopupHeight(combo));
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    /** Ширина, вмещающая ПОЛНУЮ (нераспахнутую) подпись каждого элемента — рендерер
     *  ({@link NamedRenderer}) сам подгоняет HTML под {@code list.getWidth()}, а у
     *  этого "пробного" списка она 0 (не показан, не переложен) — поэтому рендерер
     *  НЕ ограничивает ширину (см. его собственную проверку {@code w > 60}) и
     *  {@code getPreferredSize()} возвращает естественную, ничем не урезанную
     *  ширину подписи — ровно то, что нужно измерить. */
    @SuppressWarnings("unchecked")
    private static int preferredPopupWidth(JComboBox<?> combo) {
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        JList<Object> probe = new JList<>();
        int max = 0;
        for (int i = 0; i < combo.getItemCount(); i++) {
            Component c = renderer.getListCellRendererComponent(probe, combo.getItemAt(i), i, false, false);
            max = Math.max(max, c.getPreferredSize().width);
        }
        // Запас под скроллбар/внутренние отступы попапа — без него самая широкая
        // строка вплотную прилегает к правому краю.
        return max > 0 ? max + 24 : 0;
    }

    /** Высота, вмещающая РОВНО видимые строки (не больше {@link
     *  JComboBox#getMaximumRowCount()}) — {@code popup.getPreferredSize()} здесь
     *  ненадёжен: попап на момент {@code popupMenuWillBecomeVisible} ещё не
     *  переложен под новую (только что расширенную {@link #preferredPopupWidth})
     *  ширину, поэтому отдаёт то ли устаревший, то ли рассчитанный на большее
     *  число строк размер — попап открывался явно выше, чем нужно для самого
     *  списка, даже когда в нём один-единственный элемент (жалоба пользователя
     *  2026-09-30, скриншот: «Сцена 1» с пустым полем под ним на полпопапа).
     *  Меряем сами, той же техникой, что и {@link #preferredPopupWidth} —
     *  "пробным" списком нулевой ширины, чтобы {@link NamedRenderer} не оборачивал
     *  HTML (та же причина, по которой это корректно и для ширины). */
    @SuppressWarnings("unchecked")
    private static int preferredPopupHeight(JComboBox<?> combo) {
        int itemCount = combo.getItemCount();
        if (itemCount == 0) {
            return 0;
        }
        int visibleCount = Math.min(itemCount, combo.getMaximumRowCount());
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        JList<Object> probe = new JList<>();
        int sum = 0;
        for (int i = 0; i < visibleCount; i++) {
            Component c = renderer.getListCellRendererComponent(probe, combo.getItemAt(i), i, false, false);
            sum += c.getPreferredSize().height;
        }
        return sum + 6;
    }
}
