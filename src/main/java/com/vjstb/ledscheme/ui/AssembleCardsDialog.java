package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.service.CardLoadout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

/**
 * Сборка конфигурации узла из карт-шаблонов пресета библиотеки: слева — состав
 * узла (список экземпляров карт в ПОРЯДКЕ размещения/отрисовки), справа —
 * библиотека доступных для этого пресета карт-шаблонов. Карта добавляется в
 * состав двойным кликом по шаблону справа или перетаскиванием его в список
 * слева; порядок в левом списке можно менять кнопками ▲/▼ или перетаскиванием
 * внутри самого списка (одинаковых карт может быть несколько — см. Task #59/#66).
 */
public class AssembleCardsDialog extends JDialog {

    private final EquipmentPreset preset;
    /** Карты-шаблоны, доступные для сборки: карты серии модели + собственные (см. AppModel#cardTemplatesOf). */
    private final List<SchemaCard> templates;
    private final JLabel limitsLabel = new JLabel(" ");
    private final DefaultListModel<SchemaCard> assembledModel = new DefaultListModel<>();
    private final JList<SchemaCard> assembledList = new JList<>(assembledModel);
    private final JList<SchemaCard> libraryList;
    private List<String> result;

    public AssembleCardsDialog(Window owner, EquipmentPreset preset) {
        this(owner, preset, preset.getDefaultCardTemplateIds(), "Состав карт — " + preset.getName(), "Добавить узел");
    }

    /** initialTemplateIds — комплектация, с которой стартует левый список (см.
     *  EquipmentPreset.defaultCardTemplateIds) — пусто, если начинать с чистого
     *  листа; title/okLabel — под конкретный сценарий вызова (добавление узла
     *  из пресета в схему, либо редактирование самой комплектации по умолчанию
     *  в библиотеке — см. LibrariesStagePanel). */
    public AssembleCardsDialog(Window owner, EquipmentPreset preset, List<String> initialTemplateIds,
                                String title, String okLabel) {
        this(owner, preset, preset.getCards(), initialTemplateIds, title, okLabel);
    }

    /** {@code templates} — карты, из которых собирается узел (для моделей из серии — карты серии плюс
     *  собственные, см. {@code AppModel#cardTemplatesOf}); лимиты входных/выходных карт берутся из
     *  {@code preset} и НЕ дают добавить карту сверх лимита (запрос 2026-10-02). */
    public AssembleCardsDialog(Window owner, EquipmentPreset preset, List<SchemaCard> templates,
                                List<String> initialTemplateIds, String title, String okLabel) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.preset = preset;
        this.templates = templates;

        DefaultListModel<SchemaCard> libraryModel = new DefaultListModel<>();
        for (SchemaCard template : templates) {
            libraryModel.addElement(template);
        }
        libraryList = new JList<>(libraryModel);
        for (String id : initialTemplateIds) {
            SchemaCard template = findTemplateById(id);
            if (template != null) {
                assembledModel.addElement(template);
            }
        }

        assembledList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assembledList.setCellRenderer(new NamedRenderer<SchemaCard>(SchemaCard::getName, SchemaCard::portsSummary));
        libraryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libraryList.setCellRenderer(new NamedRenderer<SchemaCard>(SchemaCard::getName, SchemaCard::portsSummary));

        CardTransferHandler transferHandler = new CardTransferHandler();
        libraryList.setDragEnabled(true);
        libraryList.setTransferHandler(transferHandler);
        assembledList.setDragEnabled(true);
        assembledList.setDropMode(javax.swing.DropMode.INSERT);
        assembledList.setTransferHandler(transferHandler);

        libraryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    SchemaCard sel = libraryList.getSelectedValue();
                    if (sel != null && canAddCard(sel)) {
                        assembledModel.addElement(sel);
                    }
                }
            }
        });

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        JLabel leftTitle = new JLabel("Состав узла (порядок = порядок отрисовки)");
        left.add(leftTitle, BorderLayout.NORTH);
        JScrollPane assembledScroll = new JScrollPane(assembledList);
        assembledScroll.setPreferredSize(new Dimension(240, 220));
        left.add(assembledScroll, BorderLayout.CENTER);
        JPanel leftSouth = new JPanel(new BorderLayout());
        limitsLabel.setForeground(Palette.MUTED);
        leftSouth.add(limitsLabel, BorderLayout.NORTH);
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton up = new JButton("▲");
        JButton down = new JButton("▼");
        JButton removeBtn = new JButton("Удалить");
        up.addActionListener(e -> moveSelected(-1));
        down.addActionListener(e -> moveSelected(1));
        Runnable removeSelectedAssembled = () -> {
            int idx = assembledList.getSelectedIndex();
            if (idx >= 0) {
                assembledModel.remove(idx);
            }
        };
        removeBtn.addActionListener(e -> removeSelectedAssembled.run());
        UiKit.bindDeleteKey(assembledList, removeSelectedAssembled);
        leftButtons.add(up);
        leftButtons.add(down);
        leftButtons.add(removeBtn);
        leftSouth.add(leftButtons, BorderLayout.CENTER);
        left.add(leftSouth, BorderLayout.SOUTH);
        assembledModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                updateLimitsLabel();
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                updateLimitsLabel();
            }

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                updateLimitsLabel();
            }
        });
        updateLimitsLabel();

        JPanel right = new JPanel(new BorderLayout(4, 4));
        JLabel rightTitle = new JLabel("Библиотека карт этого оборудования");
        right.add(rightTitle, BorderLayout.NORTH);
        JScrollPane libraryScroll = new JScrollPane(libraryList);
        libraryScroll.setPreferredSize(new Dimension(240, 220));
        right.add(libraryScroll, BorderLayout.CENTER);
        JLabel rightHint = new JLabel("<html>Двойной клик или перетаскивание влево — добавить экземпляр карты.</html>");
        rightHint.setForeground(Palette.MUTED);
        right.add(rightHint, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(split, BorderLayout.CENTER);

        JButton ok = new JButton(okLabel);
        ok.addActionListener(e -> {
            String problem = CardLoadout.problem(preset, assembledCards());
            if (problem != null) {
                javax.swing.JOptionPane.showMessageDialog(this, problem, "Лимит карт",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            result = new ArrayList<>();
            for (int i = 0; i < assembledModel.size(); i++) {
                result.add(assembledModel.get(i).getId());
            }
            dispose();
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(560, 340);
        setLocationRelativeTo(owner);
    }

    private void moveSelected(int delta) {
        int idx = assembledList.getSelectedIndex();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= assembledModel.size()) {
            return;
        }
        SchemaCard item = assembledModel.remove(idx);
        assembledModel.add(target, item);
        assembledList.setSelectedIndex(target);
    }

    private List<SchemaCard> assembledCards() {
        List<SchemaCard> cards = new ArrayList<>();
        for (int i = 0; i < assembledModel.size(); i++) {
            cards.add(assembledModel.get(i));
        }
        return cards;
    }

    /** Лимиты модели на входные/выходные карты: сверх лимита добавить нельзя (подсказка в счётчике). */
    private boolean canAddCard(SchemaCard card) {
        if (CardLoadout.canAdd(preset, assembledCards(), card)) {
            return true;
        }
        java.awt.Toolkit.getDefaultToolkit().beep();
        limitsLabel.setText("Лимит достигнут: " + CardLoadout.summary(preset, assembledCards()));
        limitsLabel.setForeground(new java.awt.Color(0xC0392B));
        return false;
    }

    private void updateLimitsLabel() {
        if (preset.getMaxInputCards() == null && preset.getMaxOutputCards() == null) {
            limitsLabel.setText(" ");
            return;
        }
        limitsLabel.setText(CardLoadout.summary(preset, assembledCards()));
        limitsLabel.setForeground(Palette.MUTED);
    }

    private SchemaCard findTemplateById(String id) {
        for (SchemaCard t : templates) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Один обработчик на оба списка: из библиотеки (справа) — только КОПИЯ (в
     *  библиотеке карта должна остаться), внутри состава (слева) — ПЕРЕМЕЩЕНИЕ
     *  (перетаскивание — это переупорядочивание, а не создание дубликата). */
    private class CardTransferHandler extends TransferHandler {
        private int dragSourceIndex = -1;

        @Override
        protected Transferable createTransferable(javax.swing.JComponent c) {
            JList<?> list = (JList<?>) c;
            dragSourceIndex = list == assembledList ? list.getSelectedIndex() : -1;
            Object val = list.getSelectedValue();
            if (!(val instanceof SchemaCard sc)) {
                return null;
            }
            return new StringSelection(sc.getId());
        }

        @Override
        public int getSourceActions(javax.swing.JComponent c) {
            return c == assembledList ? MOVE : COPY;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.getComponent() == assembledList && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                String templateId = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                SchemaCard template = findTemplateById(templateId);
                if (template == null) {
                    return false;
                }
                int dropIndex = assembledModel.size();
                if (support.isDrop()) {
                    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                    if (dl.getIndex() >= 0) {
                        dropIndex = dl.getIndex();
                    }
                }
                boolean internalMove = support.getDropAction() == MOVE && dragSourceIndex >= 0;
                // перестановка внутри состава число карт не меняет — лимит проверяем только для новой карты
                if (!internalMove && !canAddCard(template)) {
                    return false;
                }
                if (internalMove && dragSourceIndex < dropIndex) {
                    dropIndex--;
                }
                assembledModel.add(dropIndex, template);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        protected void exportDone(javax.swing.JComponent source, Transferable data, int action) {
            if (action == MOVE && source == assembledList && dragSourceIndex >= 0
                    && dragSourceIndex < assembledModel.size()) {
                assembledModel.remove(dragSourceIndex);
            }
            dragSourceIndex = -1;
        }
    }

    /** Показывает диалог; возвращает список id шаблонов В ПОРЯДКЕ размещения
     *  (шаблон может повторяться при нескольких экземплярах одной карты),
     *  или null при отмене. */
    public List<String> showDialog() {
        setVisible(true);
        return result;
    }
}
