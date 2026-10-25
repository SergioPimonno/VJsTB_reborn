package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PhysicalPart;
import com.vjstb.ledscheme.service.room.RoomPart;
import com.vjstb.ledscheme.service.room.RoomShape;
import com.vjstb.ledscheme.ui.room.RoomPartsPainter;
import com.vjstb.ledscheme.ui.room.RoomTheme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * «Разбить на блоки»: форма позиции из нескольких коробок (монитор = панель + колонна + основание, стол = столешница +
 * ножки, пульт с крыльями). Позиция по умолчанию — одна коробка по общим размерам, и девять десятых оборудования так и
 * остаётся: окно нужно тем немногим, у кого форма не коробка. Размеры блоков — в миллиметрах от центра позиции
 * ({@code x} вправо, {@code y} к лицевой стороне, {@code z} — низ блока над полом). Общий габарит и вес позиции потом
 * считаются по блокам. Справа внизу — те же виды спереди и сверху, что на плане.
 *
 * <p>Результат: {@link #show} возвращает список блоков, пустой список — «вернуть одну коробку», {@code null} — отмена.
 */
public final class RoomPartsDialog extends JDialog {

    /** Заготовки блоков кнопками «+»: название → {ширина, глубина, высота, x, y, z}, мм. */
    private static final Map<String, double[]> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Панель", new double[] {1470, 70, 850, 0, 0, 900});
        PRESETS.put("Колонна", new double[] {100, 100, 840, 0, 0, 60});
        PRESETS.put("Основание", new double[] {600, 600, 60, 0, 0, 0});
        PRESETS.put("Короб", new double[] {600, 400, 400, 0, 0, 0});
        PRESETS.put("Ножка", new double[] {60, 60, 120, 0, 0, 0});
    }

    private final List<PhysicalPart> parts = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final JTextField nameField = new JTextField(14);
    private final JSpinner widthSp = spinner(10, 20000, 10);
    private final JSpinner depthSp = spinner(10, 20000, 10);
    private final JSpinner heightSp = spinner(10, 20000, 10);
    private final JSpinner xSp = spinner(-10000, 10000, 10);
    private final JSpinner ySp = spinner(-10000, 10000, 10);
    private final JSpinner zSp = spinner(0, 10000, 10);
    private final JSpinner kgSp = spinner(0, 2000, 0.5);
    private final Preview preview = new Preview();
    private final JLabel total = new JLabel(" ");
    private boolean loading;
    private List<PhysicalPart> result;

    private RoomPartsDialog(Window owner, List<PhysicalPart> start, PhysicalPart seed, boolean rackNote) {
        super(owner, "Блоки формы", ModalityType.APPLICATION_MODAL);
        if (start != null && !start.isEmpty()) {
            for (PhysicalPart p : start) {
                parts.add(p.copy());
            }
        } else if (seed != null) {
            parts.add(seed.copy());
        }
        for (PhysicalPart p : parts) {
            listModel.addElement(label(p));
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelected();
            }
        });

        JPanel add = new JPanel(new GridLayout(0, 2, 4, 4));
        for (String key : PRESETS.keySet()) {
            JButton b = new JButton("+ " + key);
            b.addActionListener(e -> addPreset(key));
            add.add(b);
        }
        JButton dup = new JButton("Дублировать");
        dup.addActionListener(e -> duplicate());
        JButton del = new JButton("Убрать");
        del.addActionListener(e -> remove());
        add.add(dup);
        add.add(del);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.add(add, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(250, 360));

        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 4));
        fields.add(new JLabel("Название блока"));
        fields.add(nameField);
        fields.add(new JLabel("Ширина, мм"));
        fields.add(widthSp);
        fields.add(new JLabel("Глубина, мм"));
        fields.add(depthSp);
        fields.add(new JLabel("Высота, мм"));
        fields.add(heightSp);
        fields.add(new JLabel("Сдвиг вправо, мм"));
        fields.add(xSp);
        fields.add(new JLabel("Сдвиг к лицу, мм"));
        fields.add(ySp);
        fields.add(new JLabel("Над полом, мм"));
        fields.add(zSp);
        fields.add(new JLabel("Вес блока, кг"));
        fields.add(kgSp);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.add(fields, BorderLayout.NORTH);
        right.add(preview, BorderLayout.CENTER);
        right.add(total, BorderLayout.SOUTH);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> {
            result = new ArrayList<>(parts);
            dispose();
        });
        JButton one = new JButton("Одной коробкой");
        one.setToolTipText("Убрать блоки: позиция снова одна коробка по общим размерам");
        one.addActionListener(e -> {
            result = new ArrayList<>();
            dispose();
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(one);
        buttons.add(ok);
        buttons.add(cancel);

        String note = "Блок — прямоугольная коробка. Общий габарит и вес позиции считаются по всем блокам; центр позиции — центр "
                + "габарита. Низ блока над полом — «Над полом» (подвес, ножки, колонна над основанием).";
        if (rackNote) {
            note += " У позиции «В стойку» форма в стойке не рисуется — блоки пригодятся, если переключить «Отдельно».";
        }
        JPanel content = new JPanel(new BorderLayout(12, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        content.add(UiKit.muted(UiKit.wrapHtml("<html>" + note)), BorderLayout.NORTH);
        content.add(left, BorderLayout.WEST);
        content.add(right, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        for (JSpinner s : new JSpinner[] {widthSp, depthSp, heightSp, xSp, ySp, zSp, kgSp}) {
            s.addChangeListener(e -> changed());
        }
        if (!parts.isEmpty()) {
            list.setSelectedIndex(0);
        }
        loadSelected();
        pack();
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(owner);
    }

    /**
     * Открывает окно блоков. {@code existing} — текущие блоки позиции (или {@code null}); если их нет, редактирование
     * начинается с одного блока {@code seed} — текущей коробки позиции. Возвращает блоки, пустой список («одной
     * коробкой») или {@code null} при отмене.
     */
    public static List<PhysicalPart> show(Window owner, List<PhysicalPart> existing, PhysicalPart seed, boolean rackNote) {
        RoomPartsDialog d = new RoomPartsDialog(owner, existing, seed, rackNote);
        d.setVisible(true);
        return d.result;
    }

    /** Окно без показа — для проверки в тестах. */
    static RoomPartsDialog create(Window owner, List<PhysicalPart> existing, PhysicalPart seed) {
        return new RoomPartsDialog(owner, existing, seed, false);
    }

    List<PhysicalPart> result() {
        return result;
    }

    // ---------------------------------------------------------------- правки

    private static JSpinner spinner(double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(min < 0 ? 0.0 : Math.max(min, 0.0), min, max, step));
        s.setEditor(new JSpinner.NumberEditor(s, "0.#"));
        return s;
    }

    private static double val(JSpinner s) {
        return ((Number) s.getValue()).doubleValue();
    }

    private static String label(PhysicalPart p) {
        String n = p.getName() == null || p.getName().isBlank() ? "Блок" : p.getName();
        return n + "  ·  " + Math.round(p.getWidthMm()) + "×" + Math.round(p.getDepthMm()) + "×" + Math.round(p.getHeightMm());
    }

    private PhysicalPart current() {
        int i = list.getSelectedIndex();
        return i >= 0 && i < parts.size() ? parts.get(i) : null;
    }

    private void loadSelected() {
        PhysicalPart p = current();
        loading = true;
        try {
            boolean on = p != null;
            for (JComponent c : new JComponent[] {nameField, widthSp, depthSp, heightSp, xSp, ySp, zSp, kgSp}) {
                c.setEnabled(on);
            }
            if (p != null) {
                nameField.setText(p.getName());
                widthSp.setValue(p.getWidthMm());
                depthSp.setValue(p.getDepthMm());
                heightSp.setValue(p.getHeightMm());
                xSp.setValue(p.getXMm());
                ySp.setValue(p.getYMm());
                zSp.setValue(p.getZMm());
                kgSp.setValue(p.getWeightKg() != null ? p.getWeightKg() : 0.0);
            }
        } finally {
            loading = false;
        }
        refreshPreview();
    }

    private void changed() {
        PhysicalPart p = current();
        if (loading || p == null) {
            return;
        }
        p.setName(nameField.getText());
        p.setWidthMm(val(widthSp));
        p.setDepthMm(val(depthSp));
        p.setHeightMm(val(heightSp));
        p.setXMm(val(xSp));
        p.setYMm(val(ySp));
        p.setZMm(val(zSp));
        p.setWeightKg(val(kgSp) > 0 ? val(kgSp) : null);
        listModel.set(list.getSelectedIndex(), label(p));
        refreshPreview();
    }

    private void addPreset(String key) {
        double[] v = PRESETS.get(key);
        parts.add(new PhysicalPart(key, v[0], v[1], v[2], v[3], v[4], v[5], null));
        listModel.addElement(label(parts.get(parts.size() - 1)));
        list.setSelectedIndex(parts.size() - 1);
    }

    private void duplicate() {
        PhysicalPart p = current();
        if (p == null) {
            return;
        }
        PhysicalPart c = p.copy();
        c.setXMm(c.getXMm() + 140);
        parts.add(c);
        listModel.addElement(label(c));
        list.setSelectedIndex(parts.size() - 1);
    }

    private void remove() {
        int i = list.getSelectedIndex();
        if (i < 0) {
            return;
        }
        parts.remove(i);
        listModel.remove(i);
        if (!parts.isEmpty()) {
            list.setSelectedIndex(Math.min(i, parts.size() - 1));
        } else {
            loadSelected();
        }
    }

    private void refreshPreview() {
        List<RoomPart> shape = RoomShape.fromModel(parts);
        double[] bb = RoomShape.bbox(shape);
        double kg = RoomShape.totalKg(shape);
        total.setText(parts.isEmpty() ? "Блоков нет — позиция будет одной коробкой по общим размерам."
                : "Габарит: " + Math.round(bb[0] * 10) + "×" + Math.round(bb[1] * 10) + "×" + Math.round(bb[2] * 10) + " мм"
                        + (kg > 0 ? " · вес блоков " + (Math.round(kg * 10) / 10.0) + " кг" : "") + " · блоков: " + parts.size());
        preview.set(parts, list.getSelectedIndex());
    }

    // ---------------------------------------------------------------- предпросмотр

    /** Вид спереди и вид сверху рядом — теми же средствами, что и план. */
    private static final class Preview extends JComponent {
        private List<RoomPart> shape = List.of();
        private int selected = -1;

        Preview() {
            setPreferredSize(new Dimension(520, 230));
        }

        void set(List<PhysicalPart> model, int selected) {
            this.shape = RoomShape.fromModel(model);
            // индексы совпадают, пока у блоков нет нулевых размеров (поля не дают такого ввести)
            this.selected = selected >= 0 && selected < shape.size() ? selected : -1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoomTheme th = RoomTheme.current();
            g.setColor(th.floor);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (shape.isEmpty()) {
                g.dispose();
                return;
            }
            double[] bb = RoomShape.bbox(shape);
            int half = getWidth() / 2;
            paintView(g, th, 0, half, bb, true);
            paintView(g, th, half, getWidth() - half, bb, false);
            g.setColor(th.muted);
            g.drawString("Спереди", 8, 14);
            g.drawString("Сверху", half + 8, 14);
            g.setColor(th.grid);
            g.drawLine(half, 0, half, getHeight());
            g.dispose();
        }

        private void paintView(Graphics2D g0, RoomTheme th, int x0, int w, double[] bb, boolean front) {
            Graphics2D g = (Graphics2D) g0.create(x0, 0, w, getHeight());
            double worldH = front ? bb[2] : bb[1];
            double scale = Math.min((w - 40) / Math.max(bb[0], 1), (getHeight() - 56) / Math.max(worldH, 1));
            scale = Math.max(0.05, scale);
            AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, front ? getHeight() - 24 : getHeight() / 2.0 + 6);
            at.scale(scale, scale);
            g.transform(at);
            float line = (float) (1.2 / scale);
            if (front) {
                g.setColor(th.deckLine);
                g.fill(new java.awt.geom.Rectangle2D.Double(-w / scale, 0, 2 * w / scale, 4 / scale));
                RoomPartsPainter.front(g, shape, 0, 0, 0, th.furn, th.ink, th.accent, line, selected);
            } else {
                RoomPartsPainter.plan(g, shape, 0, 0, 0, th.furn, th.ink, th.accent, line, selected);
            }
            g.dispose();
        }
    }
}
