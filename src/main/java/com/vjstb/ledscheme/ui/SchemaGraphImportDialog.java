package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.graphimport.GraphMlReader;
import com.vjstb.ledscheme.service.graphimport.ImportedGraph;
import com.vjstb.ledscheme.service.graphimport.NameMatcher;
import com.vjstb.ledscheme.service.graphimport.SchemaGraphImport;
import com.vjstb.ledscheme.service.graphimport.SchemaGraphImport.Action;
import com.vjstb.ledscheme.service.graphimport.SchemaGraphImport.NodePlan;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Импорт схемы из yEd {@code .graphml} на новый лист общей схемы (запрос 2026-10-25): выбор файла, затем таблица
 * «блок файла → чем заменить»: оборудование библиотеки (подобрано по названию, показаны ещё варианты и оценка
 * совпадения), пустой блок с тем же названием или «не переносить»; картинки экранов пользователь сопоставляет с
 * экранами проекта сам. После импорта — отчёт (что подобрано приблизительно, чего нет в библиотеке, какие связи без
 * гнёзд). Логика — в {@link SchemaGraphImport}, здесь только окно.
 */
public final class SchemaGraphImportDialog extends JDialog {

    private static File lastDirectory;

    /** Один пункт выпадающего списка строки таблицы. */
    private static final class Option {
        final Action action;
        final EquipmentPreset preset;
        final double score;
        final boolean confirmed;
        final Screen screen;
        final String text;

        Option(Action action, EquipmentPreset preset, double score, boolean confirmed, Screen screen, String text) {
            this.action = action;
            this.preset = preset;
            this.score = score;
            this.confirmed = confirmed;
            this.screen = screen;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private record Row(NodePlan plan, JComboBox<Option> combo) {
    }

    private final AppModel model;
    private final SchemaMode mode;
    private final ImportedGraph graph;
    private final List<Row> rows = new ArrayList<>();
    private final JTextField sheetName = new JTextField(28);
    private boolean imported;
    private final boolean showReport;

    /** Спрашивает файл и показывает окно импорта; ничего не делает, если пользователь отказался. */
    public static void run(Component owner, AppModel model, SchemaMode mode,
            com.vjstb.ledscheme.settings.SettingsManager settings) {
        if (model.getCurrentScene() == null) {
            JOptionPane.showMessageDialog(owner, "Сначала выберите сцену", "Импорт схемы",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(lastDirectory);
        fc.setDialogTitle("Импорт схемы из yEd (.graphml)");
        fc.setFileFilter(new FileNameExtensionFilter("Схема yEd (*.graphml)", "graphml"));
        if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        lastDirectory = file.getParentFile();
        ImportedGraph graph;
        try {
            graph = GraphMlReader.read(file.toPath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Не удалось прочитать файл",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (graph.nodes().isEmpty()) {
            JOptionPane.showMessageDialog(owner, "В файле нет блоков.", "Импорт схемы", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SchemaGraphImportDialog(owner, model, mode, graph, settings, true).setVisible(true);
    }

    /** Окно для тестов: без показа отчёта (он модальный). */
    static SchemaGraphImportDialog create(Component owner, AppModel model, SchemaMode mode, ImportedGraph graph,
            com.vjstb.ledscheme.settings.SettingsManager settings) {
        return new SchemaGraphImportDialog(owner, model, mode, graph, settings, false);
    }

    private final com.vjstb.ledscheme.settings.SettingsManager settings;

    private SchemaGraphImportDialog(Component owner, AppModel model, SchemaMode mode, ImportedGraph graph,
            com.vjstb.ledscheme.settings.SettingsManager settings, boolean showReport) {
        super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), "Импорт схемы: " + graph.name(), ModalityType.APPLICATION_MODAL);
        this.showReport = showReport;
        this.settings = settings;
        this.model = model;
        this.mode = mode;
        this.graph = graph;

        List<EquipmentPreset> library = model.getEquipmentPresets();
        List<NodePlan> plans = SchemaGraphImport.propose(graph, mode, library);
        Scene scene = model.getCurrentScene();
        long images = plans.stream().filter(p -> p.node().kind() == ImportedGraph.Kind.IMAGE).count();

        JLabel info = new JLabel("<html>Файл «" + escape(graph.name()) + "»: блоков " + plans.size() + " (картинок "
                + images + "), связей " + graph.edges().size() + ". Импорт создаст НОВЫЙ лист схемы "
                + (mode == SchemaMode.POWER ? "питания" : "сигнала") + "; текущие схемы не меняются.</html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        c.gridy = y++;
        c.gridx = 0;
        grid.add(bold("Блок в файле"), c);
        c.gridx = 1;
        grid.add(bold("Что создать"), c);
        for (NodePlan plan : plans) {
            JComboBox<Option> combo = buildCombo(plan, scene);
            rows.add(new Row(plan, combo));
            c.gridy = y++;
            c.gridx = 0;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            boolean image = plan.node().kind() == ImportedGraph.Kind.IMAGE;
            String text = image ? "картинка" + (plan.node().label().isBlank() ? "" : " «" + plan.node().label() + "»")
                    : plan.node().label();
            JLabel lbl = new JLabel(text.isBlank() ? "(без названия)" : text);
            lbl.setToolTipText(image ? "Рисунок экрана из файла: выберите экран проекта" : null);
            grid.add(lbl, c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            grid.add(combo, c);
            if (!image) {
                c.gridx = 2;
                c.fill = GridBagConstraints.NONE;
                c.weightx = 0;
                JButton other = new JButton("…");
                other.setToolTipText("Выбрать любое оборудование из библиотеки");
                other.addActionListener(e -> chooseFromLibrary(combo, library));
                grid.add(other, c);
            }
        }
        // одинаковые блоки файла (восемь «Socapex->6x16A») выбираются один раз: выбор в одной строке переносится на
        // остальные строки с той же подписью
        for (Row r : rows) {
            if (r.plan().node().kind() != ImportedGraph.Kind.IMAGE) {
                r.combo().addActionListener(e -> propagate(r));
            }
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setPreferredSize(new Dimension(760, 420));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        sheetName.setText("Импорт " + graph.name());
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nameRow.add(new JLabel("Название нового листа:"));
        nameRow.add(sheetName);

        JButton ok = new JButton("Импортировать");
        ok.addActionListener(e -> doImport(plans));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(ok);
        buttons.add(cancel);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(nameRow, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(info, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private boolean propagating;

    /** Переносит выбор строки {@code source} на строки с той же подписью блока. */
    private void propagate(Row source) {
        if (propagating) {
            return;
        }
        Option chosen = (Option) source.combo().getSelectedItem();
        if (chosen == null) {
            return;
        }
        propagating = true;
        try {
            String label = source.plan().node().label();
            for (Row other : rows) {
                if (other == source || other.plan().node().kind() == ImportedGraph.Kind.IMAGE
                        || !other.plan().node().label().equals(label)) {
                    continue;
                }
                JComboBox<Option> combo = other.combo();
                Option match = null;
                for (int i = 0; i < combo.getItemCount(); i++) {
                    Option o = combo.getItemAt(i);
                    if (o.action == chosen.action && (chosen.action != Action.PRESET || o.preset == chosen.preset)) {
                        match = o;
                        break;
                    }
                }
                if (match == null && chosen.action == Action.PRESET) {
                    match = new Option(Action.PRESET, chosen.preset, chosen.score, chosen.confirmed, null, chosen.text);
                    combo.insertItemAt(match, 0);
                }
                if (match != null) {
                    combo.setSelectedItem(match);
                }
            }
        } finally {
            propagating = false;
        }
    }

    private static JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
        return l;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Пункты строки: у оборудования — кандидаты библиотеки, пустой блок, пропуск; у картинки — пропуск и экраны. */
    private JComboBox<Option> buildCombo(NodePlan plan, Scene scene) {
        JComboBox<Option> combo = new JComboBox<>();
        if (plan.node().kind() == ImportedGraph.Kind.IMAGE) {
            Option skip = new Option(Action.SKIP, null, 0, false, null, "— не переносить —");
            combo.addItem(skip);
            Option sameName = null;
            for (Screen s : scene.getScreens()) {
                Option o = new Option(Action.SCREEN, null, 0, false, s, "Экран «" + s.getName() + "»");
                combo.addItem(o);
                if (sameName == null && s.getName() != null && s.getName().trim().equalsIgnoreCase(plan.node().label().trim())) {
                    sameName = o;
                }
            }
            // экрана ещё нет в проекте — картинку можно перенести пустым блоком с её подписью (схему потом можно
            // привязать к экранам вручную: пункт «Изменить тип/экран» блока)
            Option stub = null;
            if (!plan.node().label().isBlank()) {
                stub = new Option(Action.STUB, null, 0, false, null, "Пустой блок «" + plan.node().label() + "»");
                combo.addItem(stub);
            }
            // название экрана совпало с подписью картинки — подставляем его; в проекте вообще нет экранов — пустой блок
            combo.setSelectedItem(sameName != null ? sameName : scene.getScreens().isEmpty() && stub != null ? stub : skip);
            return combo;
        }
        Option selected = null;
        for (NameMatcher.Candidate cand : plan.candidates()) {
            Option o = new Option(Action.PRESET, cand.preset(), cand.score(), false, null,
                    cand.preset().getName() + " — " + cand.reason().label() + ", " + Math.round(cand.score() * 100) + "%");
            combo.addItem(o);
            if (plan.action() == Action.PRESET && plan.preset() == cand.preset()) {
                selected = o;
            }
        }
        Option stub = new Option(Action.STUB, null, 0, false, null,
                "Пустой блок «" + (plan.node().label().isBlank() ? "Блок" : plan.node().label()) + "»");
        combo.addItem(stub);
        combo.addItem(new Option(Action.SKIP, null, 0, false, null, "— не переносить —"));
        combo.setSelectedItem(selected != null ? selected : stub);
        return combo;
    }

    /** «…»: любой пресет библиотеки этого режима; выбранный добавляется в список строки и выбирается. */
    private void chooseFromLibrary(JComboBox<Option> combo, List<EquipmentPreset> library) {
        List<EquipmentPreset> all = new ArrayList<>();
        for (EquipmentPreset p : library) {
            if (p.getMode() == mode && p.getName() != null && !p.getName().isBlank()) {
                all.add(p);
            }
        }
        all.sort(Comparator.comparing(p -> p.getName().toLowerCase(java.util.Locale.ROOT)));
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(this, "В библиотеке нет оборудования для этой схемы.", "Библиотека",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object[] items = all.stream().map(p -> new Object() {
            @Override
            public String toString() {
                return p.getName() + " (" + p.getCategory().getLabel() + ")";
            }
        }).toArray();
        Object pick = JOptionPane.showInputDialog(this, "Оборудование из библиотеки:", "Выбор оборудования",
                JOptionPane.PLAIN_MESSAGE, null, items, items[0]);
        if (pick == null) {
            return;
        }
        EquipmentPreset chosen = all.get(java.util.Arrays.asList(items).indexOf(pick));
        Option o = new Option(Action.PRESET, chosen, 1.0, true, null, chosen.getName() + " — выбрано вручную");
        combo.insertItemAt(o, 0);
        combo.setSelectedItem(o);
    }

    private void doImport(List<NodePlan> plans) {
        for (Row r : rows) {
            Option o = (Option) r.combo().getSelectedItem();
            if (o == null) {
                continue;
            }
            switch (o.action) {
                case PRESET -> r.plan().usePreset(o.preset, o.score, o.confirmed);
                case STUB -> r.plan().useStub();
                case SCREEN -> r.plan().useScreen(o.screen);
                default -> r.plan().skip();
            }
        }
        if (plans.stream().allMatch(p -> p.action() == Action.SKIP)) {
            JOptionPane.showMessageDialog(this, "Ничего не выбрано для переноса.", "Импорт схемы",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        SchemaGraphImport.Result result;
        try {
            // подгонка блоков — тем же способом, что кнопка «Подогнать блоки» (по настройкам отображения групп гнёзд)
            SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, mode, settings);
            result = SchemaGraphImport.apply(model, mode, sheetName.getText(), graph, plans,
                    nodes -> canvas.fitNodesToContent(nodes));
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Не удалось выполнить импорт: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        imported = true;
        dispose();
        if (showReport) {
            showReport(result);
        }
    }

    private void showReport(SchemaGraphImport.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Создан лист «").append(r.sheetName()).append("».\n\n");
        sb.append("Блоков: ").append(r.nodes()).append(" (экранов ").append(r.screens()).append(", пустых ")
                .append(r.stubs()).append(")\n");
        sb.append("Связей: ").append(r.edges()).append(", из них с подобранными гнёздами ").append(r.edgesWithPorts())
                .append('\n');
        if (r.edgesDropped() > 0) {
            sb.append("Не перенесено связей (блок пропущен): ").append(r.edgesDropped()).append('\n');
        }
        if (!r.notes().isEmpty()) {
            sb.append("\nНа что обратить внимание:\n");
            for (String n : r.notes()) {
                sb.append(" • ").append(n).append('\n');
            }
        }
        sb.append("\nВсё это одно действие — Ctrl+Z убирает лист целиком. Подписи линий про порты («1-4 main…»)"
                + " сохранены текстом.");
        JTextArea area = new JTextArea(sb.toString(), 16, 64);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(getOwner(), new JScrollPane(area), "Импорт схемы", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Для тестов: выполнен ли импорт. */
    boolean imported() {
        return imported;
    }

    int rowCountForTest() {
        return rows.size();
    }

    /** Для тестов: пункты выпадающего списка строки {@code row} (подписи). */
    List<String> optionsForTest(int row) {
        List<String> out = new ArrayList<>();
        JComboBox<Option> combo = rows.get(row).combo();
        for (int i = 0; i < combo.getItemCount(); i++) {
            out.add(combo.getItemAt(i).toString());
        }
        return out;
    }

    String selectedForTest(int row) {
        return String.valueOf(rows.get(row).combo().getSelectedItem());
    }

    String labelOfRowForTest(int row) {
        return rows.get(row).plan().node().label();
    }

    boolean isImageRowForTest(int row) {
        return rows.get(row).plan().node().kind() == ImportedGraph.Kind.IMAGE;
    }

    void chooseForTest(int row, String startsWith) {
        JComboBox<Option> combo = rows.get(row).combo();
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).toString().startsWith(startsWith)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException(startsWith + " not in " + optionsForTest(row));
    }

    void importForTest() {
        doImport(rows.stream().map(Row::plan).toList());
    }
}
