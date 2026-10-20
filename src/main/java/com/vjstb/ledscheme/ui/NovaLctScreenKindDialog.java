package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Screen;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

/** Шаг экспорта в NovaLCT «Как прописывать экраны»: для каждого затронутого экрана — как он
 *  определился (обычный Standard / Complex), ПОЧЕМУ Complex (смещения, зазоры, разные размеры на
 *  порту, непрямоугольные типы без галочки и т.д.) и выбор пользователя: прописать как Complex или
 *  как обычный (запрос пользователя — раньше экран молча уходил в Complex, а потом лишь
 *  спрашивали «Продолжить как Complex?»).
 *
 * <p>Показывается, только если хотя бы один экран определился как Complex. По умолчанию выбрано
 * автоопределение; «обычный» для экрана, не отвечающего критериям, допустим (выбор пользователя),
 * но размеры/положение кабинетов в NovaLCT тогда будут отличаться от реальных — диалог об этом
 * предупреждает. Если у экрана не задан тип кабинета, обычным его записать нельзя. */
public final class NovaLctScreenKindDialog extends JDialog {

    /** Экран и результат автоопределения. */
    public record Row(Screen screen, boolean autoComplex, List<String> reasons, boolean canBeStandard) {
    }

    private final Map<Screen, JRadioButton> complexByScreen = new LinkedHashMap<>();
    private Map<Screen, Boolean> result;

    private NovaLctScreenKindDialog(Window owner, List<Row> rows) {
        super(owner, "Экспорт NovaLCT — как прописывать экраны", ModalityType.APPLICATION_MODAL);

        JLabel criteria = new JLabel("<html><body style='width:620px'><b>Обычный (Standard) экран требует:</b>"
                + "<br>• на одном порту — кабинеты одного размера (на РАЗНЫХ портах размеры могут отличаться);"
                + "<br>• кабинеты стоят встык — без зазоров и наездов (смещения допустимы, если лишь подгоняют"
                + " кабинеты вплотную);"
                + "<br>• типы кабинетов неотличимы при записи: прямоугольные либо с галочкой «прописывать как"
                + " стандартный кабинет»;"
                + "<br>• в сетке экрана есть все ячейки (вырезанные — скрытые — допустимы), задан тип кабинета;"
                + "<br>• в режиме «Объединить в 1 экран» — ещё и один размер кабинета на весь экран."
                + "<br><br>Complex подходит для любой геометрии, но настраивается в NovaLCT заметно дольше."
                + " Если выбрать «обычный» для экрана, не отвечающего критериям, размеры и положение кабинетов"
                + " в NovaLCT будут отличаться от реальных.</body></html>");

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (Row row : rows) {
            list.add(buildRow(row));
            list.add(Box.createVerticalStrut(8));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(660, Math.min(440, 90 + rows.size() * 130)));

        JButton ok = new JButton("Далее");
        ok.addActionListener(e -> {
            Map<Screen, Boolean> out = new LinkedHashMap<>();
            for (Map.Entry<Screen, JRadioButton> en : complexByScreen.entrySet()) {
                out.put(en.getKey(), en.getValue().isSelected());
            }
            result = out;
            dispose();
        });
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel(new BorderLayout(6, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(criteria, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildRow(Row row) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Palette.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JLabel title = new JLabel("<html><b>" + escape(row.screen().getName()) + "</b> — определён как "
                + (row.autoComplex() ? "<b>Complex</b>" : "обычный (Standard)") + "</html>");
        p.add(title);

        StringBuilder html = new StringBuilder("<html><body style='width:600px'>");
        if (row.reasons().isEmpty()) {
            html.append("Критериям обычного экрана отвечает.");
        } else {
            html.append("Причины:");
            for (String reason : row.reasons()) {
                html.append("<br>• ").append(escape(reason));
            }
        }
        html.append("</body></html>");
        JLabel reasons = new JLabel(html.toString());
        reasons.setForeground(row.reasons().isEmpty() ? Palette.MUTED : new Color(0xff, 0xa0, 0x00));
        p.add(reasons);

        JRadioButton complex = new JRadioButton("Прописать как Complex", row.autoComplex());
        JRadioButton standard = new JRadioButton("Прописать как обычный (Standard)", !row.autoComplex());
        standard.setEnabled(row.canBeStandard());
        ButtonGroup group = new ButtonGroup();
        group.add(complex);
        group.add(standard);
        complexByScreen.put(row.screen(), complex);
        JPanel choice = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        choice.add(complex);
        choice.add(standard);
        p.add(choice);
        return p;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Показывает диалог; возвращает «экран → прописать как Complex» (true — Complex, false —
     *  обычный) для КАЖДОЙ переданной строки либо {@code null} при отмене. */
    public static Map<Screen, Boolean> showDialog(Window owner, List<Row> rows) {
        NovaLctScreenKindDialog dlg = new NovaLctScreenKindDialog(owner, new ArrayList<>(rows));
        dlg.setVisible(true);
        return dlg.result;
    }
}
