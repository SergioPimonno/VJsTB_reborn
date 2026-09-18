package com.vjstb.ledscheme.service.schemalayout;

/** Ширина строки текста заданным шрифтом схемы — абстракция над {@code
 *  java.awt.FontMetrics}, чтобы {@link NodePortLayout} не зависел от Swing/AWT
 *  напрямую и оставался легко тестируемым (docs/schema-ports-rework/PLAN.md,
 *  задача T2.1). {@link #awt()} — обычная реализация по умолчанию, безопасна в
 *  headless-среде (рисует в offscreen {@code BufferedImage}, как {@code
 *  SchemaCanvasPanel#renderImage} — экрана для этого не нужно). */
public interface TextMeasure {

    /** Ширина строки {@code text} шрифтом размера {@code fontSize}, {@code bold} —
     *  жирным начертанием (заголовки узла/карты). */
    double width(String text, int fontSize, boolean bold);

    static TextMeasure awt() {
        return AwtTextMeasure.INSTANCE;
    }
}
