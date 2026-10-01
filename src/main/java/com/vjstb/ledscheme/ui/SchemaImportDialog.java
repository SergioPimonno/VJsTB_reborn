package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.ui.NetworkCanvasPanel.ImportGroup;
import com.vjstb.ledscheme.ui.NetworkCanvasPanel.SchemaImportPreview;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Предпросмотр автопереноса сетей из общей схемы (кнопка «Перенести из
 *  схемы…» в {@code NetworkManagerPanel}, диф считает {@link
 *  NetworkCanvasPanel#previewSchemaImport}) — по группе-строке на связную
 *  компоненту графа схемы: чекбокс (включена по умолчанию), редактируемое
 *  имя будущей сети, состав только для чтения. Модальный, как {@code
 *  NetworkDeviceParamsDialog} (синхронное решение, не длительный процесс —
 *  в отличие от {@link NetworkScanDialog}, тут нечему идти в фоне). {@link
 *  #showDialog()} возвращает {@code true}, если пользователь нажал
 *  «Импортировать выбранное» — тогда {@link #getSelectedGroups()} отдаёт
 *  ТОЛЬКО отмеченные группы, с именами после правки пользователем. */
public class SchemaImportDialog extends JDialog {

    private record Row(ImportGroup original, JCheckBox include, JTextField name) {
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean confirmed;

    private final JPanel groupsPanel = new JPanel();
    private final JPanel northPanel = new JPanel(new BorderLayout());
    private final JLabel skippedLabel = new JLabel();
    private final JButton importBtn = new JButton("Импортировать выбранное");
    private final JComboBox<SchemaSheet> sheetCombo;

    /** Диалог для одной, заранее посчитанной схемы — без выбора схемы (одна схема
     *  сигнала на сцене, прежнее поведение). */
    public SchemaImportDialog(Window owner, SchemaImportPreview preview) {
        this(owner, List.of(), null, id -> preview);
    }

    /** Диалог с выбором схемы сигнала (запрос пользователя 2026-09-30, пункт 8: «в
     *  сетевом менеджере при импорте из общей схемы — выбор схемы сигнала»). Комбобокс
     *  схем показывается сверху ТОЛЬКО если схем сигнала несколько — с одной схемой
     *  выбирать нечего, диалог выглядит как раньше. Смена схемы пересчитывает
     *  предпросмотр ({@code previewOf}), введённые для прежней схемы галочки/имена
     *  групп сбрасываются — группы другой схемы это другие компоненты графа.
     *
     *  @param signalSheets  схемы сигнала сцены по порядку
     *  @param initialSheetId  выбранная при открытии схема (обычно открытая сейчас)
     *  @param previewOf  предпросмотр по id схемы ({@code NetworkCanvasPanel
     *         #previewSchemaImport(String)}) */
    public SchemaImportDialog(Window owner, List<SchemaSheet> signalSheets, String initialSheetId,
            Function<String, SchemaImportPreview> previewOf) {
        super(owner, "Перенести из схемы", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String firstId = initialSheetId;
        if (signalSheets.size() > 1) {
            sheetCombo = new JComboBox<>(signalSheets.toArray(new SchemaSheet[0]));
            for (SchemaSheet s : signalSheets) {
                if (s.getId().equals(initialSheetId)) {
                    sheetCombo.setSelectedItem(s);
                }
            }
            JPanel pick = new JPanel(new BorderLayout(6, 0));
            pick.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
            pick.add(new JLabel("Схема сигнала:"), BorderLayout.WEST);
            pick.add(sheetCombo, BorderLayout.CENTER);
            northPanel.add(pick, BorderLayout.NORTH);
            sheetCombo.addActionListener(e -> showPreview(previewOf.apply(selectedSheetId())));
            firstId = selectedSheetId();
        } else {
            sheetCombo = null;
            if (signalSheets.size() == 1) {
                firstId = signalSheets.get(0).getId();
            }
        }
        content.add(northPanel, BorderLayout.NORTH);

        groupsPanel.setLayout(new BoxLayout(groupsPanel, BoxLayout.Y_AXIS));
        content.add(new JScrollPane(groupsPanel), BorderLayout.CENTER);
        showPreview(previewOf.apply(firstId));

        importBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(importBtn);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(460, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    private String selectedSheetId() {
        SchemaSheet s = sheetCombo == null ? null : (SchemaSheet) sheetCombo.getSelectedItem();
        return s == null ? null : s.getId();
    }

    /** Перестраивает список групп под предпросмотр выбранной схемы: старые строки и
     *  подпись «уже в менеджере» убираются, кнопка импорта доступна, только если есть
     *  что переносить. */
    private void showPreview(SchemaImportPreview preview) {
        rows.clear();
        groupsPanel.removeAll();
        northPanel.remove(skippedLabel);
        if (preview.alreadyImportedCount() > 0) {
            skippedLabel.setText(preview.alreadyImportedCount() + " устройств уже в менеджере — пропущены.");
            skippedLabel.setFont(skippedLabel.getFont().deriveFont(Font.ITALIC));
            skippedLabel.setForeground(Color.GRAY);
            skippedLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
            northPanel.add(skippedLabel, BorderLayout.SOUTH);
        }
        if (preview.newGroups().isEmpty()) {
            JLabel empty = new JLabel(preview.alreadyImportedCount() > 0
                    ? "Все сетевые устройства схемы уже перенесены в менеджер."
                    : "На общей схеме нет сетевых устройств/связей для переноса.");
            empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
            groupsPanel.add(empty);
        } else {
            for (ImportGroup group : preview.newGroups()) {
                groupsPanel.add(groupRow(group));
                groupsPanel.add(Box.createVerticalStrut(6));
            }
        }
        importBtn.setEnabled(!preview.newGroups().isEmpty());
        northPanel.revalidate();
        northPanel.repaint();
        groupsPanel.revalidate();
        groupsPanel.repaint();
    }

    /** Id выбранной в диалоге схемы сигнала ({@code null} — выбора не было, схема одна). */
    public String getSelectedSheetId() {
        return selectedSheetId();
    }

    private JPanel groupRow(ImportGroup group) {
        JCheckBox include = new JCheckBox();
        include.setSelected(true);
        JTextField name = new JTextField(group.suggestedName());

        List<String> memberLabels = new ArrayList<>();
        group.switches().forEach(s -> memberLabels.add(s.label() + " (коммутатор)"));
        group.devices().forEach(d -> memberLabels.add(d.label()));
        JLabel members = new JLabel("<html>" + String.join(", ", memberLabels) + "</html>");
        members.setFont(members.getFont().deriveFont(members.getFont().getSize2D() - 1f));
        members.setForeground(Color.DARK_GRAY);

        rows.add(new Row(group, include, name));

        JPanel row = new JPanel(new BorderLayout(6, 2));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 2, 6, 2)));
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(include, BorderLayout.WEST);
        top.add(name, BorderLayout.CENTER);
        row.add(top, BorderLayout.NORTH);
        row.add(members, BorderLayout.CENTER);
        return row;
    }

    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }

    /** Только отмеченные группы, с именами, которые пользователь мог
     *  поправить в поле — состав устройств/связей группы (кроме имени) не
     *  меняется в этом диалоге. */
    public List<ImportGroup> getSelectedGroups() {
        return rows.stream()
                .filter(r -> r.include().isSelected())
                .map(r -> new ImportGroup(r.name().getText().isBlank() ? r.original().suggestedName()
                                : r.name().getText().trim(),
                        r.original().devices(), r.original().switches(), r.original().links()))
                .collect(Collectors.toList());
    }
}
