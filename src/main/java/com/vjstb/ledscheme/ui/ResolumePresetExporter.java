package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;

/**
 * Генератор XML "Screen Setup" (Advanced Output) для Resolume Arena — по образцу
 * реального рабочего файла, экспортированного пользователем из Resolume 6.1
 * (см. приложенный пример: канвас с тремя размещёнными экранами "Center",
 * "Left_Portal", "Right_Portal" даёт один &lt;Screen&gt; с тремя &lt;Slice&gt;).
 * Один &lt;Screen&gt; на канвас, внутри — один &lt;Slice&gt; ("слой") на каждый
 * размещённый в канвасе экран: InputRect/OutputRect — пиксельные границы экрана
 * В КООРДИНАТАХ КАНВАСА (без смещения/варпинга: Point Mode PM_LINEAR, сетка
 * BezierWarper 4×4 — равномерная подсетка прямоугольника без единого сдвинутого
 * узла, ровно как в образце), имя слоя = имя экрана (по нему Resolume сопоставляет
 * слой с физическим выходом в собственной настройке DVI/NDI/SDI-карт — вне области
 * этого экспорта). OutputDeviceVirtual — "виртуальный экран" размером с канвас
 * целиком (в образце это и есть суммарная ширина/высота всех слоёв).
 */
public final class ResolumePresetExporter {

    private ResolumePresetExporter() {
    }

    public static String buildXml(ContentCanvas canvas, Scene scene, AppModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<XmlState name=\"").append(escape(canvas.getName())).append("\">\n");
        sb.append("<versionInfo name=\"Resolume Arena\" majorVersion=\"6\" minorVersion=\"1\""
                + " microVersion=\"0\" revision=\"61231\"/>\n");
        sb.append("<ScreenSetup name=\"ScreenSetup\">\n");
        sb.append("<Params name=\"ScreenSetupParams\"/>\n");
        sb.append("<screens>\n");
        sb.append("<Screen name=\"").append(escape(canvas.getName())).append("\" uniqueId=\"")
                .append(System.currentTimeMillis()).append("\">\n");
        sb.append("<layers>\n");

        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(scene, pl.getScreenId());
            if (scr == null) {
                continue;
            }
            ScreenStats stats = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            int x0 = pl.getX();
            int y0 = pl.getY();
            int x1 = x0 + stats.resolutionWidthPx();
            int y1 = y0 + stats.resolutionHeightPx();
            appendSlice(sb, scr.getName(), x0, y0, x1, y1);
        }

        sb.append("</layers>\n");
        sb.append("<OutputDevice>\n");
        sb.append("<OutputDeviceVirtual name=\"").append(escape(canvas.getName()))
                .append("\" deviceId=\"VirtualScreen 1\" idHash=\"").append(Math.abs((long) canvas.getId().hashCode()))
                .append("\" width=\"").append(canvas.getWidthPx()).append("\" height=\"").append(canvas.getHeightPx())
                .append("\">\n");
        sb.append("<Params name=\"Params\">\n");
        sb.append("<ParamRange name=\"Width\" default=\"").append(canvas.getWidthPx()).append("\" value=\"")
                .append(canvas.getWidthPx()).append("\"/>\n");
        sb.append("<ValueRange name=\"defaultRange\" min=\"1\" max=\"16384\"/>\n");
        sb.append("<ParamRange name=\"Height\" default=\"").append(canvas.getHeightPx()).append("\" value=\"")
                .append(canvas.getHeightPx()).append("\"/>\n");
        sb.append("</Params>\n");
        sb.append("</OutputDeviceVirtual>\n");
        sb.append("</OutputDevice>\n");
        sb.append("</Screen>\n");
        sb.append("</screens>\n");
        sb.append("</ScreenSetup>\n");
        sb.append("</XmlState>\n");
        return sb.toString();
    }

    private static void appendSlice(StringBuilder sb, String name, int x0, int y0, int x1, int y1) {
        sb.append("<Slice>\n");
        sb.append("<Params name=\"Common\">\n");
        sb.append("<Param name=\"Name\" default=\"Layer\" value=\"").append(escape(name)).append("\"/>\n");
        sb.append("</Params>\n");
        appendRect(sb, "InputRect", x0, y0, x1, y1);
        appendRect(sb, "OutputRect", x0, y0, x1, y1);
        sb.append("<Warper>\n");
        sb.append("<Params name=\"Warper\">\n");
        sb.append("<ParamChoice name=\"Point Mode\" default=\"PM_LINEAR\" value=\"PM_LINEAR\" storeChoices=\"0\"/>\n");
        sb.append("<Param name=\"Flip\" default=\"0\" value=\"0\"/>\n");
        sb.append("</Params>\n");
        sb.append("<BezierWarper controlWidth=\"4\" controlHeight=\"4\">\n");
        sb.append("<vertices>\n");
        for (int row = 0; row < 4; row++) {
            double y = y0 + row * (y1 - y0) / 3.0;
            for (int col = 0; col < 4; col++) {
                double x = x0 + col * (x1 - x0) / 3.0;
                sb.append("<v x=\"").append(Math.round(x)).append("\" y=\"").append(Math.round(y)).append("\"/>\n");
            }
        }
        sb.append("</vertices>\n");
        sb.append("</BezierWarper>\n");
        sb.append("</Warper>\n");
        sb.append("</Slice>\n");
    }

    private static void appendRect(StringBuilder sb, String tag, int x0, int y0, int x1, int y1) {
        sb.append('<').append(tag).append(" orientation=\"0\">\n");
        sb.append("<v x=\"").append(x0).append("\" y=\"").append(y0).append("\"/>\n");
        sb.append("<v x=\"").append(x1).append("\" y=\"").append(y0).append("\"/>\n");
        sb.append("<v x=\"").append(x1).append("\" y=\"").append(y1).append("\"/>\n");
        sb.append("<v x=\"").append(x0).append("\" y=\"").append(y1).append("\"/>\n");
        sb.append("</").append(tag).append(">\n");
    }

    private static Screen screenById(Scene scene, String screenId) {
        if (scene == null) {
            return null;
        }
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(screenId)) {
                return s;
            }
        }
        return null;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
