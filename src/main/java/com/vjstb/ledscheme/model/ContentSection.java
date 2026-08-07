package com.vjstb.ledscheme.model;

/**
 * Один раздел текста Руководства/Приветствия — заголовок вкладки/шага + текст
 * (HTML). Начиная с Task #135/v2.0 — общая справочная данные, редактируется
 * только через отдельную админ-консоль (ledscheme-admin) и синхронизируется на
 * клиент как часть {@link Library} (см. AppModel.applyLibrarySyncItems, кейсы
 * GUIDE_TEXT/ONBOARDING_TEXT).
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
