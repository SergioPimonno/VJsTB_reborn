package com.vjstb.ledscheme.model;

/**
 * Один шаг интерактивного сценария (Руководство/стартовое окно) — заголовок,
 * текст (HTML), необязательная картинка (base64) и необязательный хотспот на
 * ней (прямоугольник в ОТНОСИТЕЛЬНЫХ координатах картинки 0..1 — не зависит от
 * масштаба показа). {@code hotspotX/Y/Width/Height} все null — у шага нет
 * хотспота, переход на следующий шаг идёт кнопкой «Далее» вместо клика по
 * картинке (см. ScenarioPlayerDialog); {@code imageBase64} null — текстовый шаг
 * без картинки вовсе (тоже без хотспота).
 */
public class ScenarioStep {

    private String title = "";
    private String bodyHtml = "";
    private String imageBase64;
    private Double hotspotX;
    private Double hotspotY;
    private Double hotspotWidth;
    private Double hotspotHeight;

    public ScenarioStep() {
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

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public Double getHotspotX() {
        return hotspotX;
    }

    public void setHotspotX(Double hotspotX) {
        this.hotspotX = hotspotX;
    }

    public Double getHotspotY() {
        return hotspotY;
    }

    public void setHotspotY(Double hotspotY) {
        this.hotspotY = hotspotY;
    }

    public Double getHotspotWidth() {
        return hotspotWidth;
    }

    public void setHotspotWidth(Double hotspotWidth) {
        this.hotspotWidth = hotspotWidth;
    }

    public Double getHotspotHeight() {
        return hotspotHeight;
    }

    public void setHotspotHeight(Double hotspotHeight) {
        this.hotspotHeight = hotspotHeight;
    }

    /** Есть ли у шага кликабельный хотспот — переход требует клика по картинке
     *  вместо кнопки «Далее» (см. ScenarioPlayerDialog). */
    public boolean hasHotspot() {
        return hotspotX != null && hotspotY != null && hotspotWidth != null && hotspotHeight != null;
    }

    public ScenarioStep copy() {
        ScenarioStep s = new ScenarioStep();
        s.title = title;
        s.bodyHtml = bodyHtml;
        s.imageBase64 = imageBase64;
        s.hotspotX = hotspotX;
        s.hotspotY = hotspotY;
        s.hotspotWidth = hotspotWidth;
        s.hotspotHeight = hotspotHeight;
        return s;
    }
}
