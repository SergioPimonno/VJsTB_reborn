package com.vjstb.ledscheme.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Диалог «Шрифт схемы…» — два размера в одном окне: для блоков и для подписей
 * линий ТЕКУЩЕЙ схемы (запрос пользователя 2026-09-30, docs/masks-and-schema-sheets/
 * PLAN.md, пункт 1: «задать размер шрифта для всей схемы»). Отдельный класс, а не
 * вложенный метод {@code SchemaPanel}, чтобы не тащить Swing-разметку в панель; сам
 * диалог ничего не пишет в модель — вызывающий код передаёт результат в {@code
 * AppModel#setSchemaSheetFontSizes}.
 *
 * <p>Спиннеры 0..72, 0 — «стандартный размер» (в модели {@code null}); отмена
 * диалога ничего не меняет — {@link #show} возвращает {@code null}.
 */
final class SchemaFontSizeDialog {

    /** Результат диалога; {@code null} в поле — стандартный размер. */
    record Result(Integer nodeSize, Integer edgeSize) {
    }

    private SchemaFontSizeDialog() {
    }

    /** @param nodeSize текущий размер блоков схемы ({@code null} — стандартный)
     *  @param edgeSize текущий размер подписей линий ({@code null} — стандартный)
     *  @return выбранные размеры либо {@code null}, если диалог отменён */
    static Result show(Component owner, String schemeName, Integer nodeSize, Integer edgeSize) {
        JSpinner nodeSpinner = new JSpinner(new SpinnerNumberModel(nodeSize != null ? nodeSize : 0, 0, 72, 1));
        JSpinner edgeSpinner = new JSpinner(new SpinnerNumberModel(edgeSize != null ? edgeSize : 0, 0, 72, 1));
        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
        fields.add(new JLabel("Блоки, пункты:"));
        fields.add(nodeSpinner);
        fields.add(new JLabel("Подписи линий, пункты:"));
        fields.add(edgeSpinner);
        JPanel panel = new JPanel(new BorderLayout(6, 8));
        panel.add(new JLabel("<html>Размер по умолчанию для схемы «" + escape(schemeName) + "».<br>"
                + "0 — стандартный. Блоки и линии, у которых размер задан<br>"
                + "отдельно (ПКМ → «Размер шрифта…»), не меняются.</html>"), BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(owner, panel, "Шрифт схемы", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        int n = (Integer) nodeSpinner.getValue();
        int e = (Integer) edgeSpinner.getValue();
        return new Result(n > 0 ? n : null, e > 0 ? e : null);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
