package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.StructureCurveMath;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureGizmoMath;
import com.vjstb.ledscheme.service.StructureLayoutMath;
import com.vjstb.ledscheme.service.StructureOrient;
import com.vjstb.ledscheme.service.StructureLayoutMath.Side;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Панель «Раскладка» окна 3D-редактора конструктива ({@link Structure3DDialog}) — запрос
 * пользователя 2026-10-22: окно «Параметры экрана» и выделение экрана в визуализаторе для группы
 * экранов, ручная расстановка башен (в том числе встык под 90° и под любым углом), стык экранов
 * под углом (группы Left/Right собираются под 90°, Center — под 120°).
 *
 * <p>Три блока: <b>режим</b> («Рамы» — как раньше: клик по рамам прячет/добавляет; «Раскладка» —
 * ЛКМ выбирает и тащит башню/экран по земле, Shift — вращает с шагом 15°, Alt — без прилипания);
 * <b>экран</b> (список экранов группы — выбор синхронен с деревом «Сетапа», поля X/Z/угол/высота над
 * землёй, «Присоединить к экрану…» с углом, «Расставить в ряд»); <b>башни</b> (галочка «вручную»,
 * список башен, поля X/Z/угол выбранной в системе экрана, «+ Башня», «Встык ▾», поворот ±15°/90°,
 * «Удалить») и предупреждения расчёта. Поля меняют МОДЕЛЬ ({@code AppModel.setStructure…}, каждая
 * правка — запись отмены); панель подписывается на неё через {@link #syncFromModel} из диалога.
 *
 * <p>Обычные Swing-компоненты — строится и в headless-среде (тест {@code StructureLayoutPanelTest}).
 */
public class StructureLayoutPanel extends JPanel {

    private static final double SPINNER_LIMIT = 1_000_000;

    private final AppModel model;
    private final Structure3DPanel view;

    private final JToggleButton framesMode = new JToggleButton("Рамы", true);
    private final JToggleButton layoutMode = new JToggleButton("Раскладка");

    private final JPanel screenSection = section("Экран");
    private final DefaultListModel<Screen> screenListModel = new DefaultListModel<>();
    private final JList<Screen> screenList = new JList<>(screenListModel);
    /** Список экранов показывается ТОЛЬКО у экрана из группы (запрос 2026-10-22: «чтобы не вводить в
     *  заблуждение»): у одиночного экрана выбирать нечего — вместо списка его имя. */
    private JScrollPane listScroll;
    private final JLabel singleScreenName = new JLabel(" ");
    private JPanel ownerLabeled;
    private final java.util.List<WrapLabel> wrapLabels = new ArrayList<>();
    private final WrapLabel screenInfo = new WrapLabel(" ", false);
    private final JSpinner poseX = spinner(0, 100);
    private final JSpinner poseZ = spinner(0, 100);
    private final JSpinner poseYaw = spinner(0, 5);
    private final JSpinner elevation = spinner(0, 100);
    private final JButton attachScreen = new JButton("Присоединить к экрану…");
    private final JButton arrangeInLine = new JButton("Расставить в ряд");
    private final JButton detachLink = new JButton("Разорвать связь");

    private final JPanel towerSection = section("Башни");
    private final JCheckBox manual = new JCheckBox("Расставлять башни вручную");
    private final JCheckBox cups = new JCheckBox("Стаканы между рамами (+50 мм)");
    private final JCheckBox ties = new JCheckBox("Считать стяжки между башнями");
    /** Стяжек на один стык башен — редактируемый параметр экрана (по умолчанию 2). */
    private final JSpinner tiesPerContact = new JSpinner(new SpinnerNumberModel(2, 1, Screen.MAX_TIES_PER_CONTACT, 1));
    /** Экран-владелец выбранной башни (группа): смена переносит башню со всеми рамами в другой экран. */
    private final JComboBox<Screen> ownerCombo = new JComboBox<>();
    private final DefaultListModel<String> towerListModel = new DefaultListModel<>();
    private final JList<String> towerList = new JList<>(towerListModel);
    private final JSpinner towerX = spinner(0, 50);
    private final JSpinner towerZ = spinner(0, 50);
    private final JSpinner towerYaw = spinner(0, 5);
    private final JSpinner towerPitch = spinner(0, 5);
    private final JSpinner towerRoll = spinner(0, 5);
    private final JSpinner towerElev = spinner(0, 100);
    /** Вариант ширины башни: рама горизонтальных элементов ({@code null} — стандартная). */
    private record WidthOption(String frameTypeId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final JComboBox<WidthOption> widthCombo = new JComboBox<>();
    private final JButton addTower = new JButton("+ Башня");
    private final JButton flushTower = new JButton("Новая встык ▾");
    private final JButton removeTower = new JButton("Удалить");
    private final WrapLabel warnings = new WrapLabel(" ", false);

    /** Идёт заполнение из модели — события компонентов не должны писать обратно в модель. */
    private boolean syncing;
    /** Время последней правки поля: серия правок подряд (вращение колеса в поле, стрелки) — ОДНА запись
     *  отмены, а не по записи на каждый щелчок. */
    private long lastEditMs;
    private static final long BURST_MS = 600;

    private boolean startsNewUndoStep() {
        long now = System.currentTimeMillis();
        boolean fresh = now - lastEditMs > BURST_MS;
        lastEditMs = now;
        return fresh;
    }
    /** Расстановка группы «в ряд» уже отложена (ждёт конца текущего события модели). */
    private boolean arrangePending;
    /** Группы (по составу экранов), которые панель уже расставляла автоматически: повторно — только
     *  кнопкой «Расставить в ряд». Иначе Ctrl+Z после авто-расстановки сразу же расставлял бы группу заново. */
    private final java.util.Set<String> autoArranged = new java.util.HashSet<>();

    public StructureLayoutPanel(AppModel model, Structure3DPanel view) {
        super(new BorderLayout());
        this.model = model;
        this.view = view;
        // Ширину панели задаёт окно (разделитель между 3D и панелью тянется): без жёсткого размера, только
        // минимум; содержимое подстраивается под ширину окна прокрутки, длинные тексты и ряды кнопок переносятся.
        setPreferredSize(new Dimension(340, 560));
        setMinimumSize(new Dimension(230, 200));
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        framesMode.setName("layoutModeFrames");
        layoutMode.setName("layoutModeLayout");
        screenList.setName("layoutScreenList");
        towerList.setName("layoutTowerList");
        manual.setName("layoutManual");
        cups.setName("layoutCups");
        ties.setName("layoutTies");
        tiesPerContact.setName("layoutTiesPerContact");
        SpinnerWheel.install(tiesPerContact);
        ownerCombo.setName("layoutTowerOwner");
        detachLink.setName("layoutDetach");
        poseX.setName("layoutPoseX");
        poseZ.setName("layoutPoseZ");
        poseYaw.setName("layoutPoseYaw");
        elevation.setName("layoutElevation");
        towerX.setName("layoutTowerX");
        towerZ.setName("layoutTowerZ");
        towerYaw.setName("layoutTowerYaw");
        towerPitch.setName("layoutTowerPitch");
        towerRoll.setName("layoutTowerRoll");
        towerElev.setName("layoutTowerElev");
        widthCombo.setName("layoutTowerWidth");
        warnings.setName("layoutWarnings");
        framesMode.setToolTipText("Клик по раме прячет её, Ctrl+клик по призраку добавляет (как раньше).");
        layoutMode.setToolTipText("<html>ЛКМ выбирает башню или экран и тащит по земле.<br>Shift+перетаскивание —"
                + " вращение (шаг 15°), Alt — без прилипания «встык».</html>");

        ScrollableStack stack = new ScrollableStack();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(buildModeRow());
        stack.add(Box.createVerticalStrut(6));
        buildScreenSection();
        stack.add(screenSection);
        stack.add(Box.createVerticalStrut(6));
        buildTowerSection();
        stack.add(towerSection);
        stack.add(Box.createVerticalStrut(6));
        warnings.setForeground(Palette.WARN);
        wrapLabels.add(screenInfo);
        wrapLabels.add(warnings);
        stack.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitWrapLabels(stack.getWidth()); // перенос и высота подписей зависят от ширины
                stack.revalidate();
            }
        });
        stack.add(warnings);

        JScrollPane scroll = new JScrollPane(stack, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        wireModeButtons();
        wireScreenControls();
        wireTowerControls();
        view.setSelectionListener(() -> {
            if (!syncing) {
                syncFromModel();
            }
        });
        syncFromModel();
    }

    // ---- построение ----

    /** Подпись, которая переносится по словам под ширину панели: перенос считается по реальной ширине в
     *  пикселях (метрики шрифта подписи), а не через CSS/{@code JTextArea} — те в узкой панели резали строки по
     *  правому краю (запрос пользователя 2026-10-22: «подписи вылезли за пределы окна»). Ширину ставит панель
     *  при каждом изменении своего размера ({@link #setAvailableWidth}). */
    private static final class WrapLabel extends JLabel {
        private String raw;
        private int available = 200;

        WrapLabel(String text, boolean small) {
            raw = text == null ? " " : text;
            if (small) {
                setFont(getFont().deriveFont(getFont().getSize2D() - 2f));
            }
            setAlignmentX(LEFT_ALIGNMENT);
            setMinimumSize(new Dimension(0, 0));
            render();
        }

        String raw() {
            return raw;
        }

        void setRaw(String text) {
            String next = text == null || text.isEmpty() ? " " : text;
            if (!next.equals(raw)) {
                raw = next;
                render();
            }
        }

        void setAvailableWidth(int px) {
            int w = Math.max(60, px);
            if (w != available) {
                available = w;
                render();
            }
        }

        private static String escape(CharSequence c) {
            return c.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private void render() {
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            StringBuilder html = new StringBuilder("<html>");
            boolean first = true;
            for (String paragraph : raw.split("\n", -1)) {
                if (!first) {
                    html.append("<br>");
                }
                first = false;
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String candidate = line.length() == 0 ? word : line + " " + word;
                    if (line.length() > 0 && fm.stringWidth(candidate) > available) {
                        html.append(escape(line)).append("<br>");
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(candidate);
                    }
                }
                html.append(escape(line));
            }
            setText(html.append("</html>").toString());
        }
    }

    /** Подгоняет ширину переноса всех подписей-абзацев под текущую ширину панели. */
    private void fitWrapLabels(int panelWidth) {
        int avail = panelWidth - 34; // рамки секций, отступы и полоса прокрутки
        for (WrapLabel l : wrapLabels) {
            l.setAvailableWidth(avail);
        }
    }

    /** Содержимое прокрутки: ширина всегда равна ширине окна прокрутки (горизонтальной прокрутки нет, всё
     *  подстраивается и переносится), высота — по содержимому. */
    private static final class ScrollableStack extends JPanel implements javax.swing.Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return Math.max(32, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JSpinner spinner(double value, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, -SPINNER_LIMIT, SPINNER_LIMIT, step));
        s.setEditor(new JSpinner.NumberEditor(s, "0.#"));
        s.setFocusable(true);
        SpinnerWheel.install(s); // колесо в активном поле меняет значение (Shift ×10, Ctrl ×0,1)
        return s;
    }

    private static JPanel labeled(String label, Component field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(86, l.getPreferredSize().height));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
        return row;
    }

    private JPanel buildModeRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        group.add(framesMode);
        group.add(layoutMode);
        row.add(framesMode);
        row.add(layoutMode);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void buildScreenSection() {
        screenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        screenList.setVisibleRowCount(4);
        screenList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                String name = value instanceof Screen s ? s.getName() : String.valueOf(value);
                return super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
            }
        });
        listScroll = new JScrollPane(screenList);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(100, 84));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        screenSection.add(listScroll);
        singleScreenName.setAlignmentX(LEFT_ALIGNMENT);
        singleScreenName.setFont(singleScreenName.getFont().deriveFont(java.awt.Font.BOLD));
        screenSection.add(singleScreenName);
        screenInfo.setAlignmentX(LEFT_ALIGNMENT);
        screenSection.add(screenInfo);
        screenSection.add(labeled("X, мм", poseX));
        screenSection.add(labeled("Z, мм", poseZ));
        screenSection.add(labeled("Угол, °", poseYaw));
        screenSection.add(labeled("Высота, мм", elevation));
        poseX.setToolTipText("Положение левого края экрана в плане группы (вправо)");
        poseZ.setToolTipText("Положение левого края экрана в плане группы (к зрителю)");
        poseYaw.setToolTipText("Поворот экрана вокруг вертикали");
        elevation.setToolTipText("Высота нижнего края экрана над землёй");
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        buttons.add(attachScreen);
        buttons.add(arrangeInLine);
        buttons.add(detachLink);
        detachLink.setToolTipText("Экран примыкает к другому под углом и едет за ним; разрыв оставляет позу как есть.");
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        screenSection.add(buttons);
    }

    private void buildTowerSection() {
        manual.setAlignmentX(LEFT_ALIGNMENT);
        manual.setToolTipText("<html>Вручную: положение и угол каждой башни задаёшь сам (встык, под любым углом).<br>"
                + "При включении текущая авто-раскладка копируется как стартовая.</html>");
        towerSection.add(manual);
        cups.setAlignmentX(LEFT_ALIGNMENT);
        cups.setToolTipText("<html>Рама 950 мм + стакан 50 мм = 1 м на модуль. Снять галочку — собирать без стаканов:<br>"
                + "башня ниже, число рам подбирается заново. Отдельный стакан — ПКМ по нему в 3D.</html>");
        towerSection.add(cups);
        ties.setAlignmentX(LEFT_ALIGNMENT);
        ties.setToolTipText("<html>Крепление между встык стоящими башнями (стыки башен × 2 стяжки) попадает в<br>"
                + "спецификацию. По умолчанию не считается.</html>");
        towerSection.add(ties);
        towerSection.add(labeled("Стяжек на стык", tiesPerContact));
        towerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        towerList.setVisibleRowCount(5);
        JScrollPane scroll = new JScrollPane(towerList);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(100, 96));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        towerSection.add(scroll);
        towerSection.add(labeled("X, мм", towerX));
        towerSection.add(labeled("Z, мм", towerZ));
        towerSection.add(labeled("Угол, °", towerYaw));
        towerSection.add(labeled("Наклон, °", towerPitch));
        towerSection.add(labeled("Крен, °", towerRoll));
        towerSection.add(labeled("Высота, мм", towerElev));
        towerPitch.setToolTipText("<html>Наклон башни вокруг её оси X (падает вперёд/назад). 90° — башня лежит вдоль"
                + " глубины.<br>Центр основания при повороте остаётся на месте.</html>");
        towerRoll.setToolTipText("<html>Крен башни вокруг её оси Z (падает вбок). 90° — башня лежит на боку вдоль"
                + " экрана.<br>Центр основания при повороте остаётся на месте.</html>");
        towerElev.setToolTipText("Высота центра основания башни над землёй (лежащая башня — на опоре, на подвесе).");
        towerX.setToolTipText("Середина передней плоскости башни в системе экрана (вдоль экрана)");
        towerZ.setToolTipText("Середина передней плоскости башни в системе экрана (к зрителю; за экраном — минус)");
        towerYaw.setToolTipText("Поворот башни вокруг вертикали");
        widthCombo.setToolTipText("<html>Ширина башни: стандартная — рамы 950 мм (шаг столбов 1000 мм); узкая —<br>"
                + "короткие рамы (850 мм) в перемычках и основании, шаг столбов 900 мм.</html>");
        towerSection.add(labeled("Ширина", widthCombo));
        ownerCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, value instanceof Screen sc ? sc.getName() : value,
                        index, isSelected, cellHasFocus);
            }
        });
        ownerCombo.setToolTipText("<html>Экран группы, которому принадлежит башня (его балласт и спецификация).<br>"
                + "Смена переносит башню со всеми рамами в другой экран — место в плане не меняется.</html>");
        ownerLabeled = labeled("Экран", ownerCombo);
        towerSection.add(ownerLabeled);
        JPanel row1 = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        row1.add(addTower);
        row1.add(flushTower);
        row1.add(removeTower);
        row1.setAlignmentX(LEFT_ALIGNMENT);
        towerSection.add(row1);
        WrapLabel gizmoHint = new WrapLabel("Двигать и вращать — гизмо на выбранной башне в режиме «Раскладка»: ось,"
                + " центр, кольцо.\nShift при перетаскивании — линии привязки.\nКолесо мыши в активном поле меняет"
                + " значение.", true);
        wrapLabels.add(gizmoHint);
        towerSection.add(gizmoHint);
        addTower.setToolTipText("Новая башня рядом с выбранной (или в центре экрана, если башен нет)");
        flushTower.setToolTipText("<html><b>Добавляет НОВУЮ башню</b> вплотную к выбранной — под 90° или параллельно."
                + "<br>Чтобы повернуть саму выбранную башню — кольцо гизмо или поле «Угол».</html>");
    }

    // ---- события ----

    private void wireModeButtons() {
        framesMode.addActionListener(e -> view.setLayoutMode(false));
        layoutMode.addActionListener(e -> view.setLayoutMode(true));
    }

    private void wireScreenControls() {
        screenList.addListSelectionListener(e -> {
            if (syncing || e.getValueIsAdjusting()) {
                return;
            }
            Screen picked = screenList.getSelectedValue();
            if (picked != null && picked != model.getCurrentScreen()) {
                model.selectScreen(picked);
            }
        });
        poseX.addChangeListener(e -> writePose());
        poseZ.addChangeListener(e -> writePose());
        poseYaw.addChangeListener(e -> writePose());
        elevation.addChangeListener(e -> {
            Screen s = model.getCurrentScreen();
            if (!syncing && s != null) {
                model.setStructureScreenElevation(s, number(elevation), startsNewUndoStep());
            }
        });
        attachScreen.addActionListener(e -> showAttachDialog());
        arrangeInLine.addActionListener(e -> {
            List<Screen> group = view.viewScreens();
            if (group.size() > 1) {
                model.arrangeStructureScreensInLine(group, 1000);
            }
        });
    }

    private void wireTowerControls() {
        manual.addActionListener(e -> {
            Screen s = model.getCurrentScreen();
            if (!syncing && s != null) {
                model.setStructureManualLayout(s, manual.isSelected());
            }
        });
        tiesPerContact.addChangeListener(e -> {
            Screen sc = model.getCurrentScreen();
            if (!syncing && sc != null) {
                model.setStructureTiesPerContact(sc, ((Number) tiesPerContact.getValue()).intValue(),
                        startsNewUndoStep());
            }
        });
        ties.addActionListener(e -> {
            Screen sc = model.getCurrentScreen();
            if (!syncing && sc != null) {
                model.setStructureCountTies(sc, ties.isSelected());
            }
        });
        ownerCombo.addActionListener(e -> {
            Screen from = model.getCurrentScreen();
            int k = view.selectedTower();
            if (!syncing && from != null && k >= 0 && ownerCombo.getSelectedItem() instanceof Screen to
                    && to != from) {
                int moved = model.moveStructureTowerToScreen(from, k, to);
                if (moved >= 0) {
                    model.selectScreen(to);
                    view.selectTower(moved);
                }
            }
        });
        detachLink.addActionListener(e -> {
            Screen sc = model.getCurrentScreen();
            if (sc != null) {
                model.detachStructureScreen(sc);
            }
        });
        cups.addActionListener(e -> {
            Screen s = model.getCurrentScreen();
            if (!syncing && s != null) {
                model.setStructureCupsEnabled(s, cups.isSelected());
            }
        });
        towerList.addListSelectionListener(e -> {
            if (!syncing && !e.getValueIsAdjusting()) {
                view.selectTower(towerList.getSelectedIndex());
            }
        });
        widthCombo.addActionListener(e -> {
            Screen s = model.getCurrentScreen();
            int k = view.selectedTower();
            if (!syncing && s != null && k >= 0 && widthCombo.getSelectedItem() instanceof WidthOption o) {
                model.setStructureTowerWidth(s, k, o.frameTypeId());
            }
        });
        towerX.addChangeListener(e -> writeTower());
        towerZ.addChangeListener(e -> writeTower());
        towerYaw.addChangeListener(e -> writeTower());
        towerPitch.addChangeListener(e -> writeTower());
        towerRoll.addChangeListener(e -> writeTower());
        towerElev.addChangeListener(e -> writeTower());
        addTower.addActionListener(e -> addTowerNearSelected());
        removeTower.addActionListener(e -> removeSelectedTower());
        flushTower.addActionListener(e -> showFlushMenu());
    }

    private static double number(JSpinner s) {
        return ((Number) s.getValue()).doubleValue();
    }

    private void writePose() {
        Screen s = model.getCurrentScreen();
        if (syncing || s == null) {
            return;
        }
        model.setStructureScreenPose(s, number(poseX), number(poseZ), number(poseYaw), startsNewUndoStep(), true);
    }

    /** Запись полей башни в модель. Менялись только углы (X, Z и высота те же) — башня поворачивается на месте:
     *  центр основания остаётся неподвижным, X/Z/высота пересчитываются (раньше — вокруг середины передней
     *  плоскости, башня «уезжала»; теперь то же для наклона и крена). Иначе поля пишутся как есть. */
    private void writeTower() {
        Screen s = model.getCurrentScreen();
        int k = view.selectedTower();
        if (syncing || s == null || k < 0) {
            return;
        }
        StructureTowerPlacement p = s.towerPlacementOf(k);
        double x = number(towerX);
        double z = number(towerZ);
        double elev = number(towerElev);
        double yaw = number(towerYaw);
        double pitch = number(towerPitch);
        double roll = number(towerRoll);
        if (p != null && Math.abs(x - p.getAnchorXMm()) < 1e-6 && Math.abs(z - p.getAnchorZMm()) < 1e-6
                && Math.abs(elev - p.getElevationMm()) < 1e-6) {
            TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), k);
            StructureOrient.TowerPose turned = new StructureOrient.TowerPose(p.getAnchorXMm(), p.getElevationMm(),
                    p.getAnchorZMm(), p.getYawDeg(), p.getPitchDeg(), p.getRollDeg())
                    .withAnglesKeepingPivot(StructureGizmoMath.pivotLocal(spec), yaw, pitch, roll);
            model.setStructureTowerPose(s, k, turned.x(), turned.z(), turned.y(), turned.yawDeg(), turned.pitchDeg(),
                    turned.rollDeg(), startsNewUndoStep(), true);
            return;
        }
        model.setStructureTowerPose(s, k, x, z, elev, yaw, pitch, roll, startsNewUndoStep(), true);
    }

    // ---- действия ----

    /** Новая башня: вплотную справа от выбранной (параллельно), иначе от последней, иначе в центре
     *  экрана за ним. Новая становится выбранной. */
    void addTowerNearSelected() {
        Screen s = model.getCurrentScreen();
        if (s == null || !s.isStructureManualLayout()) {
            return;
        }
        int target = view.selectedTower();
        if (target < 0) {
            target = s.getStructureTowerPlacements().size() - 1;
        }
        int created;
        if (target >= 0) {
            created = model.addStructureTowerFlush(s, target, Side.RIGHT, false);
        } else {
            CabinetType type = model.typeOf(s);
            double w = type != null ? StructureCurveMath.curveOf(s, type).screenWidthMm() : 4000;
            created = model.addStructureTower(s, w / 2.0, -StructureCurveMath.SCREEN_CLEARANCE_MM, 0);
        }
        if (created >= 0) {
            view.selectTower(created);
        }
    }

    void removeSelectedTower() {
        Screen s = model.getCurrentScreen();
        int k = view.selectedTower();
        if (s != null && k >= 0 && model.removeStructureTower(s, k)) {
            view.selectTower(-1);
        }
    }

    /** Поворачивает выбранную башню вокруг центра её контура на {@code deltaDeg}. */
    void turnSelected(double deltaDeg) {
        Screen s = model.getCurrentScreen();
        int k = view.selectedTower();
        StructureTowerPlacement p = s != null && k >= 0 ? s.towerPlacementOf(k) : null;
        if (p == null) {
            return;
        }
        TowerSpec spec = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), k);
        Placement turned = StructureLayoutMath.rotateAbout(
                new Placement(k, p.getAnchorXMm(), p.getAnchorZMm(), p.getYawDeg()), 0, -spec.depthMm() / 2.0,
                p.getYawDeg() + deltaDeg);
        model.setStructureTowerPlacement(s, k, turned.anchorX(), turned.anchorZ(), turned.yawDeg(), true, true);
    }

    /** Новая башня встык к выбранной с указанной стороны, под 90° или параллельно. */
    void addTowerFlush(Side side, boolean perpendicular) {
        Screen s = model.getCurrentScreen();
        int k = view.selectedTower();
        if (s == null || k < 0) {
            return;
        }
        int created = model.addStructureTowerFlush(s, k, side, perpendicular);
        if (created >= 0) {
            view.selectTower(created);
        }
    }

    private void showFlushMenu() {
        if (view.selectedTower() < 0) {
            JOptionPane.showMessageDialog(this, "Сначала выберите башню (в списке или кликом в режиме «Раскладка»).",
                    "Новая башня встык", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        addFlushItem(menu, "Новая башня справа, под 90°", Side.RIGHT, true);
        addFlushItem(menu, "Новая башня слева, под 90°", Side.LEFT, true);
        addFlushItem(menu, "Новая башня спереди (к экрану), под 90°", Side.FRONT, true);
        addFlushItem(menu, "Новая башня сзади, под 90°", Side.BACK, true);
        menu.addSeparator();
        addFlushItem(menu, "Новая башня справа, параллельно", Side.RIGHT, false);
        addFlushItem(menu, "Новая башня слева, параллельно", Side.LEFT, false);
        addFlushItem(menu, "Новая башня сзади, продолжением", Side.BACK, false);
        menu.show(flushTower, 0, flushTower.getHeight());
    }

    private void addFlushItem(JPopupMenu menu, String text, Side side, boolean perpendicular) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        item.addActionListener(e -> addTowerFlush(side, perpendicular));
        menu.add(item);
    }

    /** Окно «Присоединить экран к…»: опора, сторона, угол между лицевыми поверхностями (90° — угол,
     *  120° — излом на 60°, 180° — в линию) и сторона загиба. Результат — абсолютная поза экрана. */
    private void showAttachDialog() {
        Screen mover = model.getCurrentScreen();
        List<Screen> others = new ArrayList<>(view.viewScreens());
        others.remove(mover);
        if (mover == null || others.isEmpty()) {
            JOptionPane.showMessageDialog(this, "В группе нет другого экрана, к которому можно присоединить.",
                    "Присоединить экран", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<Screen> ref = new JComboBox<>(others.toArray(new Screen[0]));
        ref.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, value instanceof Screen s ? s.getName() : value,
                        index, isSelected, cellHasFocus);
            }
        });
        JComboBox<String> side = new JComboBox<>(new String[]{"справа от опоры", "слева от опоры"});
        JSpinner angle = new JSpinner(new SpinnerNumberModel(90, 1, 180, 5));
        JComboBox<String> dir = new JComboBox<>(new String[]{"к зрителю (вогнутый угол)", "от зрителя (выпуклый угол)"});
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Присоединить к:"));
        form.add(ref);
        form.add(new JLabel("«" + mover.getName() + "» будет:"));
        form.add(side);
        form.add(new JLabel("Угол между экранами, °:"));
        form.add(angle);
        form.add(new JLabel("Загиб:"));
        form.add(dir);
        int ok = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this), form,
                "Присоединить экран «" + mover.getName() + "»", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION && ref.getSelectedItem() instanceof Screen reference) {
            applyAttach(mover, reference, side.getSelectedIndex() == 0, ((Number) angle.getValue()).doubleValue(),
                    dir.getSelectedIndex() == 0);
        }
    }

    /** Применяет стык экранов (вынесено из диалога — для тестов и повторного использования). */
    void applyAttach(Screen mover, Screen reference, boolean moverToRight, double angleDeg, boolean towardViewer) {
        model.attachStructureScreen(mover, reference, moverToRight, angleDeg, towardViewer);
    }

    // ---- синхронизация с моделью ----

    /** Заполняет панель из МОДЕЛИ: список экранов группы, поза/высота текущего экрана, галочка и список
     *  башен, поля выбранной башни, предупреждения. Вызывается диалогом после любого изменения
     *  модели и при смене выбора башни в 3D. Группа, чьи экраны ещё не расставлены в плане (все позы
     *  нулевые — в 3D они лежали бы друг на друге), расставляется в ряд (одна запись отмены) — сразу
     *  при открытии и, отложенно, после текущего события модели. */
    public void syncFromModel() {
        Screen s = model.getCurrentScreen();
        List<Screen> group = view.viewScreens();
        String groupKey = group.stream().map(Screen::getId).collect(java.util.stream.Collectors.joining(";"));
        if (StructureLayoutMath.groupNeedsArranging(group) && !arrangePending && autoArranged.add(groupKey)) {
            arrangePending = true;
            SwingUtilities.invokeLater(() -> {
                arrangePending = false;
                List<Screen> now = view.viewScreens();
                if (StructureLayoutMath.groupNeedsArranging(now)) {
                    model.arrangeStructureScreensInLine(now, 1000);
                }
            });
        }
        syncing = true;
        try {
            boolean structure = s != null && s.getMountType() == ScreenMountType.STRUCTURE;
            boolean inGroup = group.size() > 1;
            layoutMode.setSelected(view.isLayoutMode());
            framesMode.setSelected(!view.isLayoutMode());

            screenListModel.clear();
            for (Screen g : group) {
                screenListModel.addElement(g);
            }
            screenList.setSelectedValue(s, true);
            screenList.setEnabled(inGroup);
            listScroll.setVisible(inGroup);
            singleScreenName.setVisible(!inGroup);
            singleScreenName.setText(s != null ? s.getName() : " ");
            attachScreen.setEnabled(inGroup);
            arrangeInLine.setEnabled(inGroup);
            poseX.setEnabled(inGroup);
            poseZ.setEnabled(inGroup);
            poseYaw.setEnabled(inGroup);
            elevation.setEnabled(structure);
            if (s != null) {
                poseX.setValue(s.getStructurePoseXMm());
                poseZ.setValue(s.getStructurePoseZMm());
                poseYaw.setValue(s.getStructurePoseYawDeg());
                elevation.setValue(s.getStructureScreenElevationMm());
                CabinetType t = model.typeOf(s);
                screenInfo.setRaw(t == null ? " " : String.format("%d×%d каб., %.0f×%.0f мм, глубина %.0f мм",
                        s.getCols(), s.getRows(), s.getCols() * t.getWidthMm(), s.getRows() * t.getHeightMm(),
                        StructureCurveMath.cabinetDepthOf(t)));
            } else {
                screenInfo.setRaw("Экран не выбран.");
            }
            Screen linkRef = null;
            if (s != null && s.getStructureAttachRefId() != null) {
                for (Screen g : group) {
                    if (g.getId().equals(s.getStructureAttachRefId())) {
                        linkRef = g;
                    }
                }
            }
            detachLink.setVisible(linkRef != null);
            if (linkRef != null) {
                screenInfo.setRaw(screenInfo.raw().trim() + "\nПримыкает к «" + linkRef.getName() + "» "
                        + (s.isStructureAttachRight() ? "справа" : "слева") + ", угол "
                        + String.format("%.0f", s.getStructureAttachAngleDeg()) + "°, загиб "
                        + (s.isStructureAttachToward() ? "к зрителю" : "от зрителя") + " — едет вместе с ним");
            }

            ties.setEnabled(structure);
            ties.setSelected(structure && s.isStructureCountTies());
            tiesPerContact.setEnabled(structure && s.isStructureCountTies());
            tiesPerContact.setValue(structure ? s.getStructureTiesPerContact() : Screen.DEFAULT_TIES_PER_CONTACT);
            cups.setEnabled(structure);
            cups.setSelected(structure && !s.isStructureWithoutCups());
            manual.setEnabled(structure);
            manual.setSelected(structure && s.isStructureManualLayout());
            boolean isManual = manual.isSelected();
            towerListModel.clear();
            if (isManual) {
                for (StructureTowerPlacement p : s.getStructureTowerPlacements()) {
                    towerListModel.addElement(String.format("Башня %d  (%.0f; %.0f; %.0f°)", p.getTowerIndex() + 1,
                            p.getAnchorXMm(), p.getAnchorZMm(), p.getYawDeg()));
                }
            }
            int selected = view.selectedTower();
            if (selected >= 0 && selected < towerListModel.size()) {
                towerList.setSelectedIndex(selected);
            } else {
                towerList.clearSelection();
            }
            StructureTowerPlacement sel = isManual && selected >= 0 ? s.towerPlacementOf(selected) : null;
            towerList.setEnabled(isManual);
            towerX.setEnabled(sel != null);
            towerZ.setEnabled(sel != null);
            towerYaw.setEnabled(sel != null);
            towerPitch.setEnabled(sel != null);
            towerRoll.setEnabled(sel != null);
            towerElev.setEnabled(sel != null);
            if (sel != null) {
                towerX.setValue(sel.getAnchorXMm());
                towerZ.setValue(sel.getAnchorZMm());
                towerYaw.setValue(sel.getYawDeg());
                towerPitch.setValue(sel.getPitchDeg());
                towerRoll.setValue(sel.getRollDeg());
                towerElev.setValue(sel.getElevationMm());
            }
            widthCombo.removeAllItems();
            widthCombo.addItem(new WidthOption(null, String.format("Стандартная (%.0f мм)",
                    com.vjstb.ledscheme.service.StructureCalc.DEFAULT_TOWER_SPACING_MM)));
            if (structure) {
                double cup = com.vjstb.ledscheme.service.StructureCupMath.cupHeightMm(s, model.getWorkspace());
                for (com.vjstb.ledscheme.model.StructureFrameType t : model.getStructureFrameTypes()) {
                    boolean frameKind = t.getKind() == com.vjstb.ledscheme.model.StructureFrameType.Kind.FRAME
                            || t.getKind() == com.vjstb.ledscheme.model.StructureFrameType.Kind.SHORT_FRAME;
                    if (frameKind && t.getHeightMm() != null && t.getHeightMm() > 0) {
                        widthCombo.addItem(new WidthOption(t.getId(),
                                String.format("%s (%.0f мм)", t.getName(), t.getHeightMm() + cup)));
                    }
                }
                String current = sel != null ? s.towerWidthFrameTypeOf(selected) : null;
                for (int i = 0; i < widthCombo.getItemCount(); i++) {
                    if (java.util.Objects.equals(widthCombo.getItemAt(i).frameTypeId(), current)) {
                        widthCombo.setSelectedIndex(i);
                    }
                }
            }
            widthCombo.setEnabled(sel != null);
            ownerCombo.removeAllItems();
            for (Screen g : group) {
                if (g.getMountType() == ScreenMountType.STRUCTURE && g.isStructureManualLayout()) {
                    ownerCombo.addItem(g);
                }
            }
            ownerCombo.setSelectedItem(s);
            ownerCombo.setEnabled(sel != null && ownerCombo.getItemCount() > 1);
            ownerLabeled.setVisible(inGroup);
            addTower.setEnabled(isManual);
            flushTower.setEnabled(isManual && sel != null);
            removeTower.setEnabled(sel != null);

            List<String> notes = new ArrayList<>();
            if (s != null && structure) {
                notes.addAll(StructureLayoutMath.analyzeManual(s, model.typeOf(s), model.getWorkspace()));
            }
            if (inGroup) {
                notes.addAll(StructureLayoutMath.analyzeGroup(group, model::typeOf, model.getWorkspace()));
            }
            warnings.setRaw(notes.isEmpty() ? " " : "⚠ " + String.join("\n⚠ ", notes));
        } finally {
            syncing = false;
        }
    }

    // ---- для тестов ----

    JToggleButton layoutModeButton() {
        return layoutMode;
    }

    JToggleButton framesModeButton() {
        return framesMode;
    }

    JCheckBox manualCheck() {
        return manual;
    }

    JSpinner tiesPerContactSpinner() {
        return tiesPerContact;
    }

    JCheckBox tiesCheck() {
        return ties;
    }

    JComboBox<Screen> ownerComboBox() {
        return ownerCombo;
    }

    JButton detachButton() {
        return detachLink;
    }

    JCheckBox cupsCheck() {
        return cups;
    }

    JComboBox<?> widthComboBox() {
        return widthCombo;
    }

    JList<String> towerList() {
        return towerList;
    }

    JList<Screen> screenList() {
        return screenList;
    }

    JSpinner poseXSpinner() {
        return poseX;
    }

    JSpinner poseYawSpinner() {
        return poseYaw;
    }

    JSpinner elevationSpinner() {
        return elevation;
    }

    JSpinner towerXSpinner() {
        return towerX;
    }

    JSpinner towerPitchSpinner() {
        return towerPitch;
    }

    JSpinner towerRollSpinner() {
        return towerRoll;
    }

    JSpinner towerElevSpinner() {
        return towerElev;
    }

    JSpinner towerYawSpinner() {
        return towerYaw;
    }

    JButton addTowerButton() {
        return addTower;
    }

    JButton removeTowerButton() {
        return removeTower;
    }

    JButton arrangeButton() {
        return arrangeInLine;
    }

    boolean screenListVisible() {
        return listScroll.isVisible();
    }

    String screenNameText() {
        return singleScreenName.getText();
    }

    String warningsText() {
        return warnings.raw();
    }

    String screenInfoText() {
        return screenInfo.raw();
    }

    /** Редактор показывает группу из нескольких экранов (а не один экран). */
    boolean groupMode() {
        return view.viewScreens().size() > 1;
    }
}
