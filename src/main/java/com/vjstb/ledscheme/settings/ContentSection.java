package com.vjstb.ledscheme.settings;

/**
 * Один раздел редактируемого текстового контента приложения (Руководство,
 * шаги приветствия) — заголовок вкладки/шага + текст (HTML). Пользователь
 * переписывает эти разделы через {@link com.vjstb.ledscheme.ui.ContentEditorDialog},
 * не трогая исходный код.
 */
public class ContentSection {

    private String title = "";
    private String bodyHtml = "";

    public ContentSection() {
    }

    public ContentSection(String title, String bodyHtml) {
        this.title = title;
        this.bodyHtml = bodyHtml;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public ContentSection copy() {
        return new ContentSection(title, bodyHtml);
    }
}
