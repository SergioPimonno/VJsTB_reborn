package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.MaskLimits;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Набор файлов одного экспорта масок (кнопки этапа «Генерация масок», пакет документации)
 * и проверки размера перед ним — запрос пользователя 2026-09-30 (решение D7):
 * <ul>
 *   <li>маска/канвас со стороной ≥ {@link MaskLimits#WARN_SIDE_PX} — в наборе остаётся, но
 *       перед экспортом показывается в списке предупреждения;</li>
 *   <li>маска со стороной &gt; {@link MaskLimits#MAX_SIDE_PX} (бывает только у экрана: размер
 *       экрана задаётся на «Сетапе», а канвас такого размера модель не создаст) — в набор НЕ
 *       попадает, а перечисляется в том же подтверждении; остальные файлы пишутся.</li>
 * </ul>
 * Размеры берутся из самих {@link MaskImage} — а те из {@code MaskGeometry}, т.е. с
 * множителем экрана-«сетки».
 */
public final class MaskExportSet {

    /** Файл набора: имя файла, подпись для списков предупреждений, задание рендера. */
    public record Entry(String filename, String label, MaskImage image) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<MaskLimits.Item> checked = new ArrayList<>();
    private final List<MaskLimits.Item> skipped = new ArrayList<>();

    /** Добавляет маску в набор. Сторона &gt; {@link MaskLimits#MAX_SIDE_PX} — не добавляет
     *  (попадёт в {@link #skipped()}). Повтор того же имени файла (экран размещён в нескольких
     *  канвасах) не дублируется. @return {@code true}, если маска будет записана */
    public boolean add(String filename, String label, MaskImage image) {
        MaskLimits.Item item = new MaskLimits.Item(label, image.width(), image.height());
        for (Entry e : entries) {
            if (e.filename().equals(filename)) {
                return true;
            }
        }
        if (MaskLimits.exceedsMax(image.width(), image.height())) {
            if (!skipped.contains(item)) {
                skipped.add(item);
            }
            return false;
        }
        checked.add(item);
        entries.add(new Entry(filename, label, image));
        return true;
    }

    /** Только для проверки размера — файл не пишется (пресет Resolume: XML, а не PNG). */
    public void addSizeOnly(String label, int widthPx, int heightPx) {
        MaskLimits.Item item = new MaskLimits.Item(label, widthPx, heightPx);
        if (MaskLimits.exceedsMax(widthPx, heightPx)) {
            skipped.add(item);
        } else {
            checked.add(item);
        }
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Маски/канвасы набора со стороной ≥ 16384 (в пределах 30000) — для предупреждения. */
    public List<MaskLimits.Item> large() {
        return MaskLimits.large(checked);
    }

    /** Маски, которые НЕ будут записаны (сторона &gt; 30000). */
    public List<MaskLimits.Item> skipped() {
        return Collections.unmodifiableList(skipped);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Подпись маски экрана для списков: «[Сцена — ]экран «имя»». */
    public static String screenLabel(Scene scene, Screen screen, boolean includeScene) {
        return (includeScene && scene != null ? scene.getName() + " — " : "") + "экран «" + screen.getName() + "»";
    }

    /** Подпись маски канваса для списков: «[Сцена — ]канвас «имя»». */
    public static String canvasLabel(Scene scene, ContentCanvas canvas, boolean includeScene) {
        return (includeScene && scene != null ? scene.getName() + " — " : "") + "канвас «" + canvas.getName() + "»";
    }

    /** Текст единого подтверждения (без вопроса) или {@code null}, если предупреждать не о
     *  чем: обрезка канвасами ({@code cropReport}, см. {@code CanvasFit.report}), стороны
     *  ≥ 16384 и маски больше 30000, которые не будут записаны. Отдельно от диалога — чтобы
     *  проверять тестом. */
    public static String warningText(String cropReport, MaskExportSet set) {
        StringBuilder sb = new StringBuilder();
        if (cropReport != null && !cropReport.isBlank()) {
            sb.append("Канвас обрежет маски — часть масок выходит за границы канваса и в PNG канваса будет"
                    + " обрезана (в пресетах Resolume/After Effects координаты останутся как есть):\n");
            sb.append(cropReport).append('\n');
        }
        List<MaskLimits.Item> large = set != null ? set.large() : List.of();
        if (!large.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("Сторона ≥ ").append(MaskLimits.WARN_SIDE_PX).append(" px — не все медиасерверы и видеокарты"
                    + " откроют такие файлы (Resolume и многие плееры ограничены 16384 px):\n");
            sb.append(indent(MaskLimits.describe(large))).append('\n');
        }
        List<MaskLimits.Item> skipped = set != null ? set.skipped() : List.of();
        if (!skipped.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("Сторона больше ").append(MaskLimits.MAX_SIDE_PX).append(" px — эти маски НЕ будут"
                    + " экспортированы (предел After Effects); остальные файлы будут записаны:\n");
            sb.append(indent(MaskLimits.describe(skipped))).append('\n');
        }
        return sb.length() == 0 ? null : sb.toString().stripTrailing();
    }

    private static String indent(String s) {
        return s.replaceAll("(?m)^", "    ");
    }

    /** Одно общее подтверждение перед экспортом (вместо двух диалогов подряд «обрезка» +
     *  «≥16k»). {@code true} — можно продолжать (предупреждать не о чем или пользователь
     *  согласился). */
    public static boolean confirm(Component parent, String cropReport, MaskExportSet set, String question) {
        String text = warningText(cropReport, set);
        if (text == null) {
            return true;
        }
        JTextArea area = new JTextArea(text, Math.min(16, text.split("\n").length + 1), 60);
        area.setEditable(false);
        area.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Проверьте набор перед экспортом:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(new JLabel(question), BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(parent, panel, "Проверка перед экспортом", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Пишет все файлы набора в {@code folder} потоково ({@link MaskImage#writePng}) в фоне с
     *  окном прогресса. @return число записанных файлов; отмена — {@link java.util.concurrent.CancellationException} */
    public int writeAll(Component owner, String title, File folder) throws Exception {
        try (ExportProgressDialog progress = new ExportProgressDialog(owner, title, entries.size())) {
            int written = 0;
            for (Entry e : entries) {
                progress.step(e.filename());
                File target = new File(folder, e.filename());
                progress.runInBackground(e.filename(), p -> {
                    e.image().writePng(target, MaskImage.defaultStripRows(e.image().width()), p);
                    return null;
                });
                written++;
            }
            return written;
        }
    }
}
