package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaCard;
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
    private final DefaultListModel<SchemaCard> assembledModel = new DefaultListModel<>();
    private final JList<SchemaCard> assembledList = new JList<>(assembledModel);
    private final JList<SchemaCard> libraryList;
    private List<String> result;

    public AssembleCardsDialog(Window owner, EquipmentPreset preset) {
        super(owner, "Состав карт — " + preset.getName(), ModalityType.APPLICATION_MODAL);
        this.preset = preset;

        DefaultListModel<SchemaCard> libraryModel = new DefaultListModel<>();
        for (SchemaCard template : preset.getCards()) {
            libraryModel.addElement(template);
        }
        libraryList = new JList<>(libraryModel);

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
                    if (sel != null) {
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
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton up = new JButton("▲");
        JButton down = new JButton("▼");
        JButton removeBtn = new JButton("Удалить");
        up.addActionListener(e -> moveSelected(-1));
        down.addActionListener(e -> moveSelected(1));
        removeBtn.addActionListener(e -> {
            int idx = assembledList.getSelectedIndex();
            if (idx >= 0) {
                assembledModel.remove(idx);
            }
        });
        leftButtons.add(up);
        leftButtons.add(down);
        leftButtons.add(removeBtn);
        left.add(leftButtons, BorderLayout.SOUTH);

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

        JButton ok = new JButton("Добавить узел");
        ok.addActionListener(e -> {
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

    private SchemaCard findTemplateById(String id) {
        for (SchemaCard t : preset.getCards()) {
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
