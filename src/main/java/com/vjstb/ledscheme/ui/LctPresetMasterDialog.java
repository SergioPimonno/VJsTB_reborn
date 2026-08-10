package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NovaLctCombineHelper;
import com.vjstb.ledscheme.service.NovaLctControllerResolver;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.DefaultListModel;

/** «LCTPresetMaster» — редактор размещения экранов сцены на комбинированной
 *  NovaLCT-сетке (шаг ВНУТРИ {@code NovaLctControllerExportDialog}, заменивший
 *  собой пикер уже существующего {@code ContentCanvas} — тот создан для другой
 *  задачи, позиционирования под видео-контент в произвольных пикселях; см.
 *  class-javadoc {@link NovaLctCombineHelper}). Визуально ближе к реальному
 *  интерфейсу NovaLCT (Basic Information: Columns/Rows, раскраска ячеек по
 *  Sending Card Number, номера столбцов/строк), чем к абстрактному канвасу —
 *  по прямой просьбе пользователя после того, как канвас-based объединение
 *  экранов разной высоты дало в реальной NovaLCT "Failed to load screen
 *  information file!" (реальная причина была не в этом — см. blank-механизм в
 *  {@link NovaLctCombineHelper#combine} — но пользователь отдельно подтвердил,
 *  что канвас в принципе не тот инструмент для этой задачи).
 *
 * <p>Взаимодействие: уже размещённый на сетке экран перетаскивается мышью
 * (drag, всегда снаппится к целым ячейкам — сетка и так дискретна), как в
 * {@link CanvasEditorPanel}. Ещё НЕ размещённые экраны — в списке слева:
 * выбор экрана в списке ("вооружает" его) + клик по сетке ставит экран туда
 * (упрощённый эквивалент drag&drop МЕЖДУ компонентами, без тяжёлого Swing DnD
 * API — начальная расстановка кликом, дальнейшая перестановка — уже полноценный
 * drag прямо по сетке).
 *
 * <p><b>Группировка</b> (для Separate-экспорта — {@code Combine} по-прежнему
 * сливает ВСЁ безусловно, эта раскладка тут не участвует): Ctrl/Cmd+клик по уже
 * размещённым экранам добавляет/убирает их из текущего выбора (оранжевая
 * рамка, независима от drag/{@code selected}) — «Группировать» присваивает
 * выбранным (2+) один общий id, «Разгруппировать» снимает его. Сгруппированные
 * экраны рисуются с пунктирным контуром вокруг общего bounding box и при
 * экспорте сливаются в ОДИН NovaLCT-экран тем же blank-механизмом, что и
 * {@link NovaLctCombineHelper#combine} (см. {@link NovaLctCombineHelper
 * #splitSeparateGrouped}), только в границах группы, а не всей сетки. Экран
 * без группы — как и раньше, свой собственный NovaLCT-экран. */
public final class LctPresetMasterDialog extends JDialog {

    /** Пастельная палитра Sending Card Number — по мотивам цветов реальной NovaLCT
     *  (розовый/зелёный/жёлтый/фиолетовый на скриншоте пользователя); циклична,
     *  если карт больше цветов. */
    private static final Color[] CARD_PALETTE = {
            new Color(0xf5, 0xb8, 0xc9), // card 1 -- розовый
            new Color(0xb9, 0xe4, 0xb0), // card 2 -- зелёный
            new Color(0xf7, 0xe6, 0x9e), // card 3 -- жёлтый
            new Color(0xd6, 0xb8, 0xea), // card 4 -- фиолетовый
            new Color(0xa9, 0xe0, 0xe6), // card 5 -- голубой
            new Color(0xf5, 0xcf, 0xa0), // card 6 -- оранжевый
    };
    private static final Color BLANK_COLOR = new Color(0x6a, 0x6f, 0x76);
    private static final Color UNWIRED_COLOR = new Color(0x45, 0x4b, 0x54);
    /** Подсветка Ctrl+клик выбора (кандидаты на группировку, см. GridPanel.multiSelected) --
     *  отдельный цвет от Palette.ACCENT, которым рисуется рамка УЖЕ сгруппированных экранов,
     *  чтобы "выбираю" и "уже сгруппировано" не путались визуально. */
    private static final Color GROUP_SELECT_COLOR = new Color(0xff, 0xa0, 0x00);

    /** {@code groupIdByScreenId} — экраны с ОДНИМ и тем же id сливаются в один
     *  NovaLCT-экран при Separate-экспорте (см. {@link NovaLctCombineHelper
     *  #splitSeparateGrouped}); экран без записи — своя единственная "группа",
     *  как и было до появления группировки. Пуста, если пользователь ничего не
     *  группировал — тогда поведение Separate идентично тому, что было раньше. */
    public record Result(List<NovaLctCombineHelper.ScreenSlot> slots, int cols, int rows,
                          Map<String, Integer> groupIdByScreenId) {
    }

    private final GridPanel gridPanel;
    private final DefaultListModel<Screen> unplacedModel = new DefaultListModel<>();
    private final JList<Screen> unplacedList = new JList<>(unplacedModel);
    private final JSpinner colsSpinner;
    private final JSpinner rowsSpinner;
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton removeBtn = new JButton("Убрать с сетки");
    private final JButton groupBtn = new JButton("Группировать");
    private final JButton ungroupBtn = new JButton("Разгруппировать");
    private final JButton okBtn = new JButton("Далее");

    private Result result;

    private LctPresetMasterDialog(Window owner, List<Screen> involvedScreens,
                                   List<NovaLctControllerResolver.CabinetRec> recs, int cabW, int cabH) {
        super(owner, "LCTPresetMaster — размещение экранов", ModalityType.APPLICATION_MODAL);

        // Авто-раскладка при первом открытии: экраны слева направо по порядку --
        // не пустая сетка при первом показе (см. class-javadoc).
        List<NovaLctCombineHelper.ScreenSlot> initialSlots = new ArrayList<>();
        int runningCol = 0;
        int maxRows = 1;
        for (Screen s : involvedScreens) {
            initialSlots.add(new NovaLctCombineHelper.ScreenSlot(s, runningCol, 0));
            runningCol += s.getCols();
            maxRows = Math.max(maxRows, s.getRows());
        }
        int initialCols = Math.max(1, runningCol);
        int initialRows = maxRows;

        colsSpinner = new JSpinner(new SpinnerNumberModel(initialCols, 1, 2000, 1));
        rowsSpinner = new JSpinner(new SpinnerNumberModel(initialRows, 1, 2000, 1));
        MathFields.enableExpressions(colsSpinner);
        MathFields.enableExpressions(rowsSpinner);

        gridPanel = new GridPanel(recs, cabW, cabH);
        gridPanel.setCols(initialCols);
        gridPanel.setRows(initialRows);
        for (NovaLctCombineHelper.ScreenSlot slot : initialSlots) {
            gridPanel.place(slot.screen(), slot.colOffset(), slot.rowOffset());
        }
        gridPanel.setPreferredSize(new Dimension(760, 420));

        colsSpinner.addChangeListener(e -> {
            int v = (Integer) colsSpinner.getValue();
            if (!gridPanel.tryResize(v, gridPanel.rows)) {
                colsSpinner.setValue(gridPanel.cols);
            } else {
                gridPanel.repaint();
                updateStatus();
            }
        });
        rowsSpinner.addChangeListener(e -> {
            int v = (Integer) rowsSpinner.getValue();
            if (!gridPanel.tryResize(gridPanel.cols, v)) {
                rowsSpinner.setValue(gridPanel.rows);
            } else {
                gridPanel.repaint();
                updateStatus();
            }
        });

        for (Screen s : involvedScreens) {
            // Уже расставлены авто-раскладкой выше -- в списке "не размещённых"
            // не появляются, пока пользователь сам не уберёт их с сетки.
        }

        unplacedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unplacedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                if (value instanceof Screen s) {
                    setText(s.getName() + " (" + s.getCols() + "×" + s.getRows() + ")");
                }
                return this;
            }
        });

        gridPanel.setOnEmptyCellClicked((col, row) -> {
            Screen armed = unplacedList.getSelectedValue();
            if (armed == null) {
                return;
            }
            if (!gridPanel.place(armed, col, row)) {
                statusLabel.setText("Не помещается на сетке в этой позиции (пересечение или выход за границы)");
                return;
            }
            unplacedModel.removeElement(armed);
            gridPanel.repaint();
            updateStatus();
        });
        gridPanel.setOnChanged(this::updateStatus);

        removeBtn.addActionListener(e -> {
            Screen removed = gridPanel.removeSelected();
            if (removed != null) {
                unplacedModel.addElement(removed);
                gridPanel.repaint();
                updateStatus();
            }
        });
        groupBtn.addActionListener(e -> {
            gridPanel.groupSelected();
            gridPanel.repaint();
            updateStatus();
        });
        ungroupBtn.addActionListener(e -> {
            gridPanel.ungroupSelected();
            gridPanel.repaint();
            updateStatus();
        });

        okBtn.addActionListener(e -> onOk());
        JButton cancelBtn = new JButton("Отмена");
        cancelBtn.addActionListener(e -> { result = null; dispose(); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Columns:"));
        top.add(colsSpinner);
        top.add(new JLabel("Rows:"));
        top.add(rowsSpinner);

        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        side.add(new JLabel("Не размещены:"), BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(unplacedList);
        listScroll.setPreferredSize(new Dimension(180, 200));
        side.add(listScroll, BorderLayout.CENTER);

        JPanel groupHint = new JPanel();
        groupHint.setLayout(new BoxLayout(groupHint, BoxLayout.Y_AXIS));
        groupHint.add(UiKit.muted("Ctrl+клик по экранам на сетке — выбрать несколько"
                + " для объединения в один NovaLCT-экран (при экспорте «Отдельными экранами»)"));
        JPanel groupButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        groupButtons.add(groupBtn);
        groupButtons.add(ungroupBtn);
        groupHint.add(groupButtons);
        groupHint.add(removeBtn);
        side.add(groupHint, BorderLayout.SOUTH);

        JPanel legend = buildLegend();

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.add(new JScrollPane(gridPanel), BorderLayout.CENTER);
        center.add(side, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelBtn);
        buttons.add(okBtn);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(top, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        JPanel southWrap = new JPanel();
        southWrap.setLayout(new BoxLayout(southWrap, BoxLayout.Y_AXIS));
        southWrap.add(legend);
        southWrap.add(bottom);
        content.add(southWrap, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(okBtn);
        updateStatus();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        for (int i = 0; i < CARD_PALETTE.length; i++) {
            legend.add(swatch(CARD_PALETTE[i], "Sending Card " + (i + 1)));
        }
        legend.add(swatch(UNWIRED_COLOR, "Не расключено"));
        legend.add(swatch(BLANK_COLOR, "Blank"));
        return legend;
    }

    private JPanel swatch(Color c, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JPanel box = new JPanel();
        box.setBackground(c);
        box.setPreferredSize(new Dimension(14, 14));
        box.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        p.add(box);
        p.add(UiKit.muted(text));
        return p;
    }

    private void updateStatus() {
        String overlapReason = gridPanel.overlapReason();
        boolean allPlaced = unplacedModel.isEmpty();
        if (overlapReason != null) {
            statusLabel.setForeground(Color.RED);
            statusLabel.setText(overlapReason);
            okBtn.setEnabled(false);
        } else if (!allPlaced) {
            statusLabel.setForeground(Palette.MUTED);
            statusLabel.setText("Разместите все экраны на сетке (осталось: " + unplacedModel.size() + ")");
            okBtn.setEnabled(false);
        } else {
            statusLabel.setForeground(Palette.MUTED);
            statusLabel.setText("Blank-ячеек: " + gridPanel.blankCount());
            okBtn.setEnabled(true);
        }
        groupBtn.setEnabled(gridPanel.canGroupSelected());
        ungroupBtn.setEnabled(gridPanel.canUngroupSelected());
    }

    private void onOk() {
        result = new Result(gridPanel.slots(), gridPanel.cols, gridPanel.rows, gridPanel.groupIdByScreenId());
        dispose();
    }

    /** Показывает диалог; возвращает финальную раскладку или {@code null} при отмене. */
    public static Result showDialog(Window owner, List<Screen> involvedScreens,
            List<NovaLctControllerResolver.CabinetRec> recs, int cabW, int cabH) {
        LctPresetMasterDialog dlg = new LctPresetMasterDialog(owner, involvedScreens, recs, cabW, cabH);
        dlg.setVisible(true);
        return dlg.result;
    }

    /** Сетка комбинированного NovaLCT-экрана — рисует cols×rows ячеек (масштаб под
     *  панель), красит занятые ячейки по Sending Card Number резолвнутого кабинета,
     *  непокрытые — серым (blank-предпросмотр). Перетаскивание уже размещённого
     *  экрана — мышью, ВСЕГДА снаппится к целым ячейкам. */
    private static final class GridPanel extends JPanel {
        private static final int PADDING = 28;

        private final Map<String, Map<Long, Integer>> cardIndexByScreenIdAndCell;
        private final int cabW;
        private final int cabH;

        private int cols = 1;
        private int rows = 1;
        private final List<NovaLctCombineHelper.ScreenSlot> placed = new ArrayList<>();
        private NovaLctCombineHelper.ScreenSlot selected;
        private NovaLctCombineHelper.ScreenSlot dragging;
        private int dragStartCol, dragStartRow, dragPressCol, dragPressRow;

        // Группировка для Separate-экспорта (см. NovaLctCombineHelper.splitSeparateGrouped) --
        // НЕ связана с combine()/выбором Combine-режима, тот по-прежнему сливает ВСЁ. Экран без
        // записи здесь -- своя единственная группа (обратная совместимость со старым 1:1
        // splitSeparate). multiSelected -- отдельный от "selected"/drag набор, Screen как ключ
        // (не ScreenSlot -- тот пересоздаётся при каждом drag, ссылка стала бы невалидной).
        private final Map<String, Integer> groupIdByScreenId = new HashMap<>();
        private final java.util.Set<Screen> multiSelected = new java.util.LinkedHashSet<>();
        private int nextGroupId = 1;

        private java.util.function.BiConsumer<Integer, Integer> onEmptyCellClicked = (c, r) -> { };
        private Runnable onChanged = () -> { };

        GridPanel(List<NovaLctControllerResolver.CabinetRec> recs, int cabW, int cabH) {
            this.cabW = cabW;
            this.cabH = cabH;
            this.cardIndexByScreenIdAndCell = new HashMap<>();
            for (NovaLctControllerResolver.CabinetRec r : recs) {
                cardIndexByScreenIdAndCell
                        .computeIfAbsent(r.sourceScreen().getId(), k -> new HashMap<>())
                        .put(cellKey(r.col(), r.row()), r.cardIndex());
            }
            setBackground(Palette.BG);
            setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    Point cell = cellAt(e.getPoint());
                    if (cell == null) {
                        return;
                    }
                    NovaLctCombineHelper.ScreenSlot hit = slotAt(cell.x, cell.y);
                    if (hit != null && (e.isControlDown() || e.isMetaDown())) {
                        // Ctrl/Cmd+клик -- ТОЛЬКО переключает участие в группировке, не начинает
                        // drag (иначе одно и то же нажатие пыталось бы и группировать, и двигать).
                        if (!multiSelected.remove(hit.screen())) {
                            multiSelected.add(hit.screen());
                        }
                        selected = hit;
                        repaint();
                        onChanged.run();
                        return;
                    }
                    if (hit == null || !multiSelected.contains(hit.screen())) {
                        multiSelected.clear();
                    }
                    selected = hit;
                    if (hit != null) {
                        dragging = hit;
                        dragStartCol = hit.colOffset();
                        dragStartRow = hit.rowOffset();
                        dragPressCol = cell.x;
                        dragPressRow = cell.y;
                    } else {
                        onEmptyCellClicked.accept(cell.x, cell.y);
                    }
                    repaint();
                    onChanged.run();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging == null) {
                        return;
                    }
                    Point cell = cellAt(e.getPoint());
                    if (cell == null) {
                        return;
                    }
                    int newCol = dragStartCol + (cell.x - dragPressCol);
                    int newRow = dragStartRow + (cell.y - dragPressRow);
                    Screen s = dragging.screen();
                    newCol = Math.max(0, Math.min(newCol, Math.max(0, cols - s.getCols())));
                    newRow = Math.max(0, Math.min(newRow, Math.max(0, rows - s.getRows())));
                    int idx = placed.indexOf(dragging);
                    NovaLctCombineHelper.ScreenSlot moved = new NovaLctCombineHelper.ScreenSlot(s, newCol, newRow);
                    placed.set(idx, moved);
                    dragging = moved;
                    selected = moved;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging != null) {
                        dragging = null;
                        onChanged.run();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setOnEmptyCellClicked(java.util.function.BiConsumer<Integer, Integer> cb) {
            this.onEmptyCellClicked = cb;
        }

        void setOnChanged(Runnable r) {
            this.onChanged = r;
        }

        void setCols(int c) {
            this.cols = c;
        }

        void setRows(int r) {
            this.rows = r;
        }

        /** true — уместилось (без выхода за пределы уже существующей сетки cols×rows
         *  и без пересечения с уже размещёнными). Не проверяет "лучшее" место — ставит
         *  ровно туда, куда кликнули (верхний левый угол экрана). */
        boolean place(Screen s, int col, int row) {
            if (col < 0 || row < 0 || col + s.getCols() > cols || row + s.getRows() > rows) {
                return false;
            }
            NovaLctCombineHelper.ScreenSlot candidate = new NovaLctCombineHelper.ScreenSlot(s, col, row);
            placed.add(candidate);
            if (overlapReason() != null) {
                placed.remove(candidate);
                return false;
            }
            selected = candidate;
            return true;
        }

        Screen removeSelected() {
            if (selected == null) {
                return null;
            }
            placed.remove(selected);
            Screen s = selected.screen();
            groupIdByScreenId.remove(s.getId());
            multiSelected.remove(s);
            selected = null;
            return s;
        }

        boolean canGroupSelected() {
            return multiSelected.size() >= 2;
        }

        boolean canUngroupSelected() {
            for (Screen s : multiSelected) {
                if (groupIdByScreenId.containsKey(s.getId())) {
                    return true;
                }
            }
            return false;
        }

        /** Присваивает всем выбранным (Ctrl+клик) экранам ОДИН новый id группы — они
         *  сольются в один NovaLCT-экран при {@code splitSeparateGrouped} (см. её
         *  javadoc). Если какой-то из них уже состоял в другой группе — покидает её
         *  (остальные участники той группы просто останутся, вплоть до вырождения в
         *  группу из одного элемента, что эквивалентно "без группы вообще"). */
        void groupSelected() {
            if (multiSelected.size() < 2) {
                return;
            }
            int gid = nextGroupId++;
            for (Screen s : multiSelected) {
                groupIdByScreenId.put(s.getId(), gid);
            }
        }

        void ungroupSelected() {
            for (Screen s : multiSelected) {
                groupIdByScreenId.remove(s.getId());
            }
        }

        Map<String, Integer> groupIdByScreenId() {
            return new HashMap<>(groupIdByScreenId);
        }

        /** true — новый размер применён (все уже размещённые экраны по-прежнему
         *  умещаются); false — отклонено (спиннер обязан откатиться). */
        boolean tryResize(int newCols, int newRows) {
            for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                Screen s = slot.screen();
                if (slot.colOffset() + s.getCols() > newCols || slot.rowOffset() + s.getRows() > newRows) {
                    return false;
                }
            }
            this.cols = newCols;
            this.rows = newRows;
            return true;
        }

        List<NovaLctCombineHelper.ScreenSlot> slots() {
            return new ArrayList<>(placed);
        }

        String overlapReason() {
            Map<Long, Screen> owner = new HashMap<>();
            for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                Screen s = slot.screen();
                for (int c = slot.colOffset(); c < slot.colOffset() + s.getCols(); c++) {
                    for (int r = slot.rowOffset(); r < slot.rowOffset() + s.getRows(); r++) {
                        Screen prev = owner.putIfAbsent(cellKey(c, r), s);
                        if (prev != null && prev != s) {
                            return "Экраны «" + prev.getName() + "» и «" + s.getName() + "» перекрываются";
                        }
                    }
                }
            }
            return null;
        }

        int blankCount() {
            boolean[][] covered = new boolean[cols][rows];
            for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                Screen s = slot.screen();
                for (int c = slot.colOffset(); c < slot.colOffset() + s.getCols() && c < cols; c++) {
                    for (int r = slot.rowOffset(); r < slot.rowOffset() + s.getRows() && r < rows; r++) {
                        if (c >= 0 && r >= 0) {
                            covered[c][r] = true;
                        }
                    }
                }
            }
            int blank = 0;
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (!covered[c][r]) {
                        blank++;
                    }
                }
            }
            return blank;
        }

        private NovaLctCombineHelper.ScreenSlot slotAt(int col, int row) {
            for (int i = placed.size() - 1; i >= 0; i--) {
                NovaLctCombineHelper.ScreenSlot slot = placed.get(i);
                Screen s = slot.screen();
                if (col >= slot.colOffset() && col < slot.colOffset() + s.getCols()
                        && row >= slot.rowOffset() && row < slot.rowOffset() + s.getRows()) {
                    return slot;
                }
            }
            return null;
        }

        private static long cellKey(int col, int row) {
            return (((long) col) << 32) | (row & 0xffffffffL);
        }

        private double cellSize() {
            double availW = Math.max(50, getWidth() - PADDING * 2);
            double availH = Math.max(50, getHeight() - PADDING * 2 - 20);
            return Math.max(4, Math.min(availW / Math.max(1, cols), availH / Math.max(1, rows)));
        }

        private Point cellAt(Point p) {
            double size = cellSize();
            int c = (int) Math.floor((p.x - PADDING) / size);
            int r = (int) Math.floor((p.y - PADDING - 20) / size);
            if (c < 0 || r < 0 || c >= cols || r >= rows) {
                return null;
            }
            return new Point(c, r);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(400, cols * 24 + PADDING * 2),
                    Math.max(300, rows * 24 + PADDING * 2 + 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double size = cellSize();
            int top = PADDING + 20;

            Font headFont = getFont().deriveFont(Font.PLAIN, (float) Math.max(8, Math.min(11, size * 0.4)));
            g2.setFont(headFont);
            g2.setColor(Palette.MUTED);
            for (int c = 0; c < cols; c++) {
                String label = String.valueOf(c + 1);
                int x = (int) (PADDING + c * size);
                g2.drawString(label, x + 2, top - 6);
            }
            for (int r = 0; r < rows; r++) {
                String label = String.valueOf(r + 1);
                int y = (int) (top + r * size);
                g2.drawString(label, 4, y + (int) (size / 2) + 4);
            }

            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    int x = (int) (PADDING + c * size);
                    int y = (int) (top + r * size);
                    NovaLctCombineHelper.ScreenSlot slot = slotAt(c, r);
                    Color fill;
                    if (slot == null) {
                        fill = BLANK_COLOR;
                    } else {
                        int localCol = c - slot.colOffset();
                        int localRow = r - slot.rowOffset();
                        Map<Long, Integer> byCell = cardIndexByScreenIdAndCell.get(slot.screen().getId());
                        Integer cardIndex = byCell != null ? byCell.get(cellKey(localCol, localRow)) : null;
                        fill = cardIndex != null
                                ? CARD_PALETTE[cardIndex % CARD_PALETTE.length]
                                : UNWIRED_COLOR;
                    }
                    g2.setColor(fill);
                    g2.fillRect(x, y, (int) Math.ceil(size), (int) Math.ceil(size));
                    g2.setColor(Palette.BORDER);
                    g2.setStroke(new BasicStroke(0.6f));
                    g2.drawRect(x, y, (int) size, (int) size);
                }
            }

            Font labelFont = getFont().deriveFont(Font.BOLD, 12f);
            g2.setFont(labelFont);
            for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                Screen s = slot.screen();
                int x = (int) (PADDING + slot.colOffset() * size);
                int y = (int) (top + slot.rowOffset() * size);
                int w = (int) (s.getCols() * size);
                int h = (int) (s.getRows() * size);
                boolean isSelected = slot.equals(selected);
                g2.setColor(isSelected ? Color.WHITE : new Color(0x20, 0x22, 0x26));
                g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.6f));
                g2.drawRect(x, y, w, h);
                g2.setColor(new Color(0, 0, 0, 170));
                int nameW = g2.getFontMetrics().stringWidth(s.getName());
                g2.fillRect(x, y, nameW + 8, 18);
                g2.setColor(isSelected ? Palette.ACCENT : Color.WHITE);
                g2.drawString(s.getName(), x + 4, y + 14);
            }

            // Ctrl+клик выбор (кандидаты на группировку) -- отдельная от "selected"/drag
            // подсветка, см. поле multiSelected.
            if (!multiSelected.isEmpty()) {
                g2.setColor(GROUP_SELECT_COLOR);
                g2.setStroke(new BasicStroke(2.5f));
                for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                    if (!multiSelected.contains(slot.screen())) {
                        continue;
                    }
                    Screen s = slot.screen();
                    int x = (int) (PADDING + slot.colOffset() * size);
                    int y = (int) (top + slot.rowOffset() * size);
                    int w = (int) (s.getCols() * size);
                    int h = (int) (s.getRows() * size);
                    g2.drawRect(x, y, w, h);
                }
            }

            // Уже сгруппированные экраны (2+ участника) -- пунктирная рамка вокруг
            // bounding box группы, тот же контур, что построит buildGroupBlock при
            // экспорте (см. NovaLctCombineHelper.splitSeparateGrouped).
            if (!groupIdByScreenId.isEmpty()) {
                Map<Integer, List<NovaLctCombineHelper.ScreenSlot>> byGroup = new HashMap<>();
                for (NovaLctCombineHelper.ScreenSlot slot : placed) {
                    Integer gid = groupIdByScreenId.get(slot.screen().getId());
                    if (gid != null) {
                        byGroup.computeIfAbsent(gid, k -> new ArrayList<>()).add(slot);
                    }
                }
                g2.setColor(Palette.ACCENT);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{6f, 4f}, 0));
                for (List<NovaLctCombineHelper.ScreenSlot> group : byGroup.values()) {
                    if (group.size() < 2) {
                        continue;
                    }
                    int gMinCol = Integer.MAX_VALUE, gMinRow = Integer.MAX_VALUE;
                    int gMaxCol = Integer.MIN_VALUE, gMaxRow = Integer.MIN_VALUE;
                    for (NovaLctCombineHelper.ScreenSlot slot : group) {
                        Screen s = slot.screen();
                        gMinCol = Math.min(gMinCol, slot.colOffset());
                        gMinRow = Math.min(gMinRow, slot.rowOffset());
                        gMaxCol = Math.max(gMaxCol, slot.colOffset() + s.getCols());
                        gMaxRow = Math.max(gMaxRow, slot.rowOffset() + s.getRows());
                    }
                    int x = (int) (PADDING + gMinCol * size) - 3;
                    int y = (int) (top + gMinRow * size) - 3;
                    int w = (int) ((gMaxCol - gMinCol) * size) + 6;
                    int h = (int) ((gMaxRow - gMinRow) * size) + 6;
                    g2.drawRoundRect(x, y, w, h, 8, 8);
                }
            }

            g2.dispose();
        }
    }
}
