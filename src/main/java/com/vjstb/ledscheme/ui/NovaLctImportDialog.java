package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.NovaLctScrParser;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Заготовка импорта прописи NovaLCT (.scr) — см. {@link NovaLctScrParser}: пока
 *  только выбор файла и просмотр разобранной структуры (экраны/карты/порты/
 *  порядок прописи), без создания экранов и цепочек в проекте — формат
 *  расшифрован по 5 контролируемым образцам пользователя, не по документации
 *  производителя, и не проверен на конфигурациях сложнее протестированных
 *  (например, других паттернах серпантина или неполном прямоугольнике экрана). */
public final class NovaLctImportDialog {

    private NovaLctImportDialog() {
    }

    public static void showImportFlow(Frame owner) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Файлы прописи NovaLCT (*.scr)", "scr"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(chooser.getSelectedFile().toPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось прочитать файл:\n" + ex.getMessage(),
                    "Импорт из NovaLCT", JOptionPane.ERROR_MESSAGE);
            return;
        }
        NovaLctScrParser.ImportResult result;
        try {
            result = NovaLctScrParser.parse(data);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось разобрать файл:\n" + ex.getMessage(),
                    "Импорт из NovaLCT", JOptionPane.ERROR_MESSAGE);
            return;
        }
        showSummary(owner, chooser.getSelectedFile().getName(), result);
    }

    private static void showSummary(Frame owner, String fileName, NovaLctScrParser.ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Файл: ").append(fileName).append('\n');
        sb.append("Найдено экранов: ").append(result.screens().size()).append("\n\n");
        for (NovaLctScrParser.ImportedScreen scr : result.screens()) {
            int expected = scr.width * scr.height;
            sb.append("Экран #").append(scr.index + 1).append(": ").append(scr.width).append('×')
                    .append(scr.height).append(" (").append(scr.cabinets.size()).append('/').append(expected)
                    .append(" кабинетов распознано)\n");
            Map<String, List<NovaLctScrParser.CabinetEntry>> chains = scr.chainsByCardPort();
            for (Map.Entry<String, List<NovaLctScrParser.CabinetEntry>> e : chains.entrySet()) {
                String[] parts = e.getKey().split("/");
                int card = Integer.parseInt(parts[0]) + 1;
                int port = Integer.parseInt(parts[1]) + 1;
                sb.append("  Карта ").append(card).append(", порт ").append(port).append(": ")
                        .append(e.getValue().size()).append(" кабинетов, порядок прописи: ");
                List<NovaLctScrParser.CabinetEntry> list = e.getValue();
                int show = Math.min(list.size(), 6);
                for (int i = 0; i < show; i++) {
                    NovaLctScrParser.CabinetEntry c = list.get(i);
                    sb.append('(').append(c.row()).append(',').append(c.col()).append(')');
                    if (i < show - 1) {
                        sb.append("→");
                    }
                }
                if (list.size() > show) {
                    sb.append("→…(ещё ").append(list.size() - show).append(')');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append("Это просмотр разобранной структуры — создание экранов и цепочек расключения\n"
                + "в проекте по этим данным ещё не реализовано (заготовка).");

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 480));
        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(owner, panel, "Импорт из NovaLCT — предпросмотр",
                JOptionPane.PLAIN_MESSAGE);
    }
}
