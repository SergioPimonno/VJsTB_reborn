package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Компактная строка выбора «Проект → Сцена → (Экран)», используется на этапах,
 * где сама структура проекта не редактируется (Питание, Сигнал, Генерация масок, Вывод) —
 * навигация по уже созданным сущностям без громоздких списков.
 *
 * <p>Режим «схема» ({@link #setSchemaMode}, запрос пользователя 2026-09-30, пункт 8,
 * docs/masks-and-schema-sheets/PLAN.md, трек C2): пока на этапе открыта ОБЩАЯ схема,
 * «Экран:» не нужен (схема не привязана к одному экрану), а нужен выбор схемы из
 * нескольких на сцене — подпись и список «Экран:» заменяются на «Схема:» со списком
 * схем текущего режима и кнопками «＋ / ✎ / ⧉ / 🗑». «Проект» и «Сцена» остаются как были.
 */
public class ContextBar extends JPanel {

    private final AppModel model;
    private final boolean includeScreen;
    private boolean refreshing;

    private final JComboBox<Project> projectCombo = new JComboBox<>();
    private final JComboBox<Scene> sceneCombo = new JComboBox<>();
    private final JComboBox<Screen> screenCombo = new JComboBox<>();
    private final JLabel screenLabel = new JLabel("Экран:");

    // ---- режим «схема» (см. setSchemaMode) ----
    private final JLabel schemaLabel = new JLabel("Схема:");
    private final JComboBox<SchemaSheet> schemaCombo = new JComboBox<>();
    private final JButton schemaAddBtn = new JButton(glyph("＋", "+"));
    private final JButton schemaRenameBtn = new JButton(glyph("✎", "Имя"));
    private final JButton schemaDuplicateBtn = new JButton(glyph("⧉", "Копия"));
    private final JButton schemaDeleteBtn = new JButton(glyph("🗑", "×"));
    /** {@code null} — обычный режим («Экран:»); иначе режим показанной общей схемы. */
    private SchemaMode schemaMode;

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
        // Тот же приём для списка схем: названия схем пользовательские и могут быть
        // длинными («Резервный тракт, зона B»).
        schemaCombo.setRenderer(new NamedRenderer<SchemaSheet>(SchemaSheet::getName, null));
        widenPopupToFitContents(schemaCombo);

        add(new JLabel("Проект:"));
        add(projectCombo);
        add(new JLabel("Сцена:"));
        add(sceneCombo);
        if (includeScreen) {
            add(screenLabel);
            add(screenCombo);
        }
        // Элементы режима «схемы» добавляются всегда (FlowLayout пропускает
        // невидимые компоненты), показываются только в setSchemaMode(!= null).
        add(schemaLabel);
        add(schemaCombo);
        add(schemaAddBtn);
        add(schemaRenameBtn);
        add(schemaDuplicateBtn);
        add(schemaDeleteBtn);
        schemaAddBtn.setToolTipText("Новая схема (сразу спросит название)");
        schemaRenameBtn.setToolTipText("Переименовать текущую схему");
        schemaDuplicateBtn.setToolTipText("Дублировать текущую схему вместе с блоками и связями");
        schemaDeleteBtn.setToolTipText("Удалить текущую схему");
        for (JButton b : new JButton[]{schemaAddBtn, schemaRenameBtn, schemaDuplicateBtn, schemaDeleteBtn}) {
            b.setMargin(new java.awt.Insets(1, 6, 1, 6));
            b.setFocusable(false);
        }
        schemaAddBtn.addActionListener(e -> addSchema());
        schemaRenameBtn.addActionListener(e -> renameSchema());
        schemaDuplicateBtn.addActionListener(e -> duplicateSchema());
        schemaDeleteBtn.addActionListener(e -> deleteSchema());
        schemaCombo.addActionListener(e -> {
            if (refreshing) return;
            SchemaSheet sh = (SchemaSheet) schemaCombo.getSelectedItem();
            if (sh != null && sh != model.currentSchemaSheet(sh.getMode())) model.selectSchemaSheet(sh);
        });
        applySchemaModeVisibility();

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
            if (schemaMode != null) {
                List<SchemaSheet> sheets = model.schemaSheets(schemaMode);
                populate(schemaCombo, sheets, model.currentSchemaSheet(schemaMode));
                // Последнюю схему режима удалять нельзя (инвариант «хотя бы одна
                // схема на режим», см. AppModel#deleteSchemaSheet) — кнопка неактивна
                // и объясняет почему, а не молча ничего не делает.
                boolean canDelete = sheets.size() > 1;
                schemaDeleteBtn.setEnabled(canDelete);
                schemaDeleteBtn.setToolTipText(canDelete ? "Удалить текущую схему"
                        : "Нельзя удалить единственную схему этого режима");
                boolean hasSheet = !sheets.isEmpty();
                schemaAddBtn.setEnabled(hasSheet);
                schemaRenameBtn.setEnabled(hasSheet);
                schemaDuplicateBtn.setEnabled(hasSheet);
            }
        } finally {
            refreshing = false;
        }
    }

    /**
     * Переключает строку между обычным видом («Экран:») и видом общей схемы
     * («Схема:» + кнопки управления схемами). {@code null} — обычный вид. Вызывается
     * этапами «Питание»/«Сигнал» вместе с переключателями «Расключение экрана /
     * Общая схема / Сетевой менеджер» (в «Сетевом менеджере» и «Расключении» —
     * обычный вид с экраном).
     */
    public void setSchemaMode(SchemaMode mode) {
        if (this.schemaMode == mode) {
            return;
        }
        this.schemaMode = mode;
        applySchemaModeVisibility();
        rebuild();
        revalidate();
        repaint();
    }

    /** Режим показанной схемы или {@code null}. */
    public SchemaMode getSchemaMode() {
        return schemaMode;
    }

    private void applySchemaModeVisibility() {
        boolean schema = schemaMode != null;
        screenLabel.setVisible(includeScreen && !schema);
        screenCombo.setVisible(includeScreen && !schema);
        for (java.awt.Component c : new java.awt.Component[]{schemaLabel, schemaCombo, schemaAddBtn,
                schemaRenameBtn, schemaDuplicateBtn, schemaDeleteBtn}) {
            c.setVisible(schema);
        }
    }

    private void addSchema() {
        if (schemaMode == null || model.getCurrentScene() == null) {
            return;
        }
        String name = SchemaSheetNameDialog.ask(this, model, schemaMode, null, "Новая схема",
                model.suggestSchemaSheetName(schemaMode));
        if (name != null) {
            model.addSchemaSheet(schemaMode, name);
        }
    }

    private void renameSchema() {
        SchemaSheet cur = schemaMode == null ? null : model.currentSchemaSheet(schemaMode);
        if (cur == null) {
            return;
        }
        String name = SchemaSheetNameDialog.ask(this, model, schemaMode, cur, "Название схемы", cur.getName());
        if (name != null) {
            model.renameSchemaSheet(cur, name);
        }
    }

    private void duplicateSchema() {
        SchemaSheet cur = schemaMode == null ? null : model.currentSchemaSheet(schemaMode);
        if (cur != null) {
            model.duplicateSchemaSheet(cur);
        }
    }

    private void deleteSchema() {
        SchemaSheet cur = schemaMode == null ? null : model.currentSchemaSheet(schemaMode);
        if (cur == null || model.schemaSheets(schemaMode).size() <= 1) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить схему «" + cur.getName() + "» вместе со всеми её блоками и связями?\n"
                        + "Действие можно отменить (Ctrl+Z).",
                "Удаление схемы", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            model.deleteSchemaSheet(cur);
        }
    }

    /** {@code preferred}, если шрифт кнопок умеет его рисовать, иначе {@code
     *  fallback}: символы «＋/⧉/🗑» лежат в шрифтах-«символах» (Segoe UI Symbol),
     *  подбираемых Swing только при наличии в системе — без проверки на другой
     *  машине кнопка могла бы показать пустой квадрат вместо значка. */
    private static String glyph(String preferred, String fallback) {
        return new JButton().getFont().canDisplayUpTo(preferred) == -1 ? preferred : fallback;
    }

    // ---- для тестов ----

    /** Только для тестов — виден ли комбобокс «Экран:». */
    boolean screenComboVisibleForTest() {
        return screenCombo.isVisible();
    }

    /** Только для тестов — виден ли комбобокс «Схема:». */
    boolean schemaComboVisibleForTest() {
        return schemaCombo.isVisible();
    }

    /** Только для тестов — комбобокс схем. */
    JComboBox<SchemaSheet> schemaComboForTest() {
        return schemaCombo;
    }

    /** Только для тестов — кнопка «🗑». */
    JButton schemaDeleteButtonForTest() {
        return schemaDeleteBtn;
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
