package com.vjstb.ledscheme.service.schemalayout;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** {@link TextMeasure#awt()} — измеряет через offscreen {@code BufferedImage}
 *  (тот же приём, что {@code SchemaCanvasPanel#renderImage}/{@code
 *  AppModel#autoFitNodeToPorts} — не требует экрана, безопасно в headless). Один
 *  общий 1×1 буфер на процесс: создание {@code BufferedImage.createGraphics()} не
 *  бесплатно, а вызывается это на каждую подпись каждого узла при каждой
 *  перерисовке. {@link FontMetrics} по размеру/начертанию кэшируются — тот же
 *  {@link Font} с теми же метриками используется для всех подписей одного вида. */
final class AwtTextMeasure implements TextMeasure {

    static final TextMeasure INSTANCE = new AwtTextMeasure();

    private static final Graphics2D G2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private static final ConcurrentMap<Long, FontMetrics> METRICS_CACHE = new ConcurrentHashMap<>();

    private AwtTextMeasure() {
    }

    @Override
    public double width(String text, int fontSize, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long key = (long) fontSize << 1 | (bold ? 1 : 0);
        FontMetrics fm = METRICS_CACHE.computeIfAbsent(key,
                k -> G2.getFontMetrics(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, fontSize)));
        synchronized (G2) {
            return fm.stringWidth(text);
        }
    }
}
