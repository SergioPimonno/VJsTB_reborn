package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.SchemaLoadCalc;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Окно «Статистика блока…» (ПКМ по силовому узлу общей схемы): загрузка блока, применённый
 * запас, вклад каждого входного разъёма в ёмкость и мощность, уходящая по каждой исходящей
 * связи. Данные — {@link SchemaLoadCalc#stats}, тот же расчёт, что у кругового индикатора на
 * блоке, поэтому цифры в окне и на значке не могут разойтись.
 */
final class NodeLoadStatsDialog {

    private NodeLoadStatsDialog() {
    }

    static void show(Window owner, String nodeTitle, SchemaLoadCalc.Stats stats, boolean kw) {
        JDialog dlg = new JDialog(owner, "Статистика блока", JDialog.ModalityType.APPLICATION_MODAL);
        JEditorPane pane = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setBackground(Palette.PANEL);
        pane.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        pane.setText(buildHtml(nodeTitle, stats, kw));
        pane.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        scroll.setPreferredSize(new Dimension(560, 420));

        JButton close = new JButton("Закрыть");
        close.addActionListener(ev -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);

        dlg.getContentPane().setLayout(new BorderLayout(0, 6));
        dlg.getContentPane().add(scroll, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(close);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    /** HTML окна — отдельным методом, чтобы его можно было проверять в тестах без показа
     *  диалога. Цвета берутся из {@link Palette} (тема может быть светлой или тёмной). */
    static String buildHtml(String nodeTitle, SchemaLoadCalc.Stats stats, boolean kw) {
        SchemaLoadCalc.NodeLoad load = stats.load();
        String text = hex(Palette.TEXT);
        String muted = hex(Palette.MUTED);
        String border = hex(Palette.BORDER);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<html><body style='font-family:sans-serif;font-size:12pt;color:").append(text).append("'>");
        sb.append("<h3 style='margin:0 0 6px 0'>").append(esc(nodeTitle)).append("</h3>");

        if (!load.capacityKnown()) {
            sb.append("<p>Ёмкость блока неизвестна: среди входных разъёмов нет ни одного распознанного "
                    + "типа (CEE/Schuko/PowerCon/TRUEcon), контроль нагрузки для него не ведётся.</p>");
        } else {
            double pct = load.fraction() * 100;
            Color status = load.overloaded() ? new Color(0xff, 0x5a, 0x36)
                    : LoadBadge.ringColor(load.fraction());
            sb.append("<p style='margin:0 0 4px 0'>Загрузка: <b><font color='").append(hex(status)).append("'>")
                    .append(Math.round(pct)).append("%</font></b> — ")
                    .append(UiKit.fmtPower(load.loadWatts(), kw)).append(" из ")
                    .append(UiKit.fmtPower(load.capacityWatts(), kw)).append("</p>");
            double diff = Math.abs(load.capacityWatts() - load.loadWatts());
            sb.append("<p style='margin:0 0 4px 0'>")
                    .append(load.overloaded() ? "Перегрузка на " : "Свободно ")
                    .append(UiKit.fmtPower(diff, kw)).append("</p>");
            sb.append("<p style='margin:0 0 10px 0'><font color='").append(muted).append("'>Запас по току: ")
                    .append(UiKit.fmt(stats.deratingPercent())).append("% (")
                    .append(stats.deratingManual() ? "задан вручную на блоке" : "по умолчанию для типа блока")
                    .append(")</font></p>");
        }

        sb.append("<p style='margin:8px 0 2px 0'><b>Входные разъёмы</b></p>");
        if (stats.inputs().isEmpty()) {
            sb.append("<p style='margin:0'><font color='").append(muted).append("'>нет распознанных</font></p>");
        } else {
            sb.append(tableOpen(border));
            sb.append(row(true, "Разъём", "Шт.", "Фаз", "Ток", "Ёмкость"));
            for (SchemaLoadCalc.InputRow r : stats.inputs()) {
                String amps = UiKit.fmt(r.effectiveAmps()) + " А";
                if (r.effectiveAmps() < r.ratingAmps()) {
                    amps += " (автомат, разъём " + UiKit.fmt(r.ratingAmps()) + " А)";
                }
                sb.append(row(false, esc(r.connectorType()), String.valueOf(r.count()),
                        String.valueOf(r.phases()), amps, UiKit.fmtPower(r.capacityWatts(), kw)));
            }
            sb.append("</table>");
        }
        if (stats.unrecognizedInputs() > 0) {
            sb.append("<p style='margin:2px 0 0 0'><font color='").append(muted).append("'>Не учтено в ёмкости "
                    + "(тип разъёма не распознан): ").append(stats.unrecognizedInputs()).append("</font></p>");
        }

        sb.append("<p style='margin:12px 0 2px 0'><b>Исходящие связи</b></p>");
        if (stats.outputs().isEmpty()) {
            sb.append("<p style='margin:0'><font color='").append(muted).append("'>нет</font></p>");
        } else {
            sb.append(tableOpen(border));
            sb.append(row(true, "Куда", "Линий", "Мощность", "Доля нагрузки"));
            double total = load.loadWatts();
            for (SchemaLoadCalc.OutputRow r : stats.outputs()) {
                String share = total > 0 ? Math.round(r.watts() / total * 100) + "%" : "—";
                sb.append(row(false, esc(r.targetLabel()), String.valueOf(r.wireCount()),
                        UiKit.fmtPower(r.watts(), kw), share));
            }
            sb.append("</table>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String tableOpen(String border) {
        return "<table border='1' cellpadding='3' cellspacing='0' style='border-color:" + border + "'>";
    }

    private static String row(boolean header, String... cells) {
        StringBuilder sb = new StringBuilder("<tr>");
        String tag = header ? "th" : "td";
        for (String c : cells) {
            sb.append('<').append(tag).append(" align='left'>").append(c).append("</").append(tag).append('>');
        }
        return sb.append("</tr>").toString();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
