package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CanvasPlacement;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;

/**
 * Генератор .jsx (ExtendScript) для Adobe After Effects — по прямому запросу пользователя:
 * "jsx скрипт, который еще и подставляет в качестве базовых слоев созданные маски. Пресет
 * должен создавать композицию по канвасу". Один .jsx на канвас — тот же принцип "один файл
 * на канвас", что уже использует {@link ResolumePresetExporter} для Resolume: при запуске
 * в AE (File → Scripts → Run Script File) создаёт ОДНУ композицию размером с канвас и по
 * одному слою на каждый размещённый в канвасе экран, footage слоя — PNG-маска ИМЕННО этого
 * экрана (тот же файл/то же содержимое, что и кнопка «Экспорт масок» — см.
 * {@link #maskFilename}), позиция слоя — граница экрана в координатах канваса (тот же
 * расчёт X/Y, что {@link ResolumePresetExporter}).
 *
 * <p>Путь к PNG-файлам вычисляется ОТНОСИТЕЛЬНО расположения самого .jsx ({@code
 * $.fileName} — встроенная переменная ExtendScript с путём к текущему исполняемому
 * скрипту), а не абсолютным путём, зашитым на момент экспорта — если пользователь перенесёт
 * всю папку (скрипт + PNG рядом) на другую машину, скрипт продолжит работать, находя маски
 * рядом с собой.
 */
public final class AfterEffectsJsxWriter {

    private AfterEffectsJsxWriter() {
    }

    /** Длительность/частота кадров стартовой композиции — экспортёр не знает финальных
     *  параметров монтажа конкретного проекта, это только отправная точка шаблона, обе
     *  величины свободно меняются пользователем в самой AE после импорта. */
    private static final double DEFAULT_DURATION_SEC = 10.0;
    private static final double DEFAULT_FRAME_RATE = 25.0;

    public static String buildJsx(ContentCanvas canvas, Scene scene, AppModel model, String sceneNameSanitized) {
        StringBuilder sb = new StringBuilder();
        sb.append("// LED Scheme Designer -- шаблон композиции для After Effects\n");
        sb.append("// Сцена: ").append(jsComment(scene.getName())).append(", канвас: ")
                .append(jsComment(canvas.getName())).append("\n");
        sb.append("// PNG-маски экранов ищутся РЯДОМ с этим .jsx-файлом.\n");
        sb.append("(function () {\n");
        sb.append("    app.beginUndoGroup(\"Импорт LED Scheme Designer -- ").append(js(canvas.getName()))
                .append("\");\n");
        sb.append("    var scriptFolder = new File($.fileName).parent;\n");
        sb.append("    function importMask(filename) {\n");
        sb.append("        var f = new File(scriptFolder.fsName + \"/\" + filename);\n");
        sb.append("        if (!f.exists) {\n");
        sb.append("            alert(\"Файл маски не найден рядом со скриптом: \" + filename);\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        return app.project.importFile(new ImportOptions(f));\n");
        sb.append("    }\n");
        sb.append("    var comp = app.project.items.addComp(\"").append(js(canvas.getName())).append("\", ")
                .append(canvas.getWidthPx()).append(", ").append(canvas.getHeightPx()).append(", 1.0, ")
                .append(DEFAULT_DURATION_SEC).append(", ").append(DEFAULT_FRAME_RATE).append(");\n");

        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(scene, pl.getScreenId());
            if (scr == null) {
                continue;
            }
            ScreenStats stats = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            int w = stats.resolutionWidthPx();
            int h = stats.resolutionHeightPx();
            int x0 = pl.getX();
            int y0 = pl.getY();
            String filename = maskFilename(sceneNameSanitized, scr, w, h);
            String var = "footage_" + safeVarName(scr.getId());
            sb.append("    var ").append(var).append(" = importMask(\"").append(js(filename)).append("\");\n");
            sb.append("    if (").append(var).append(") {\n");
            sb.append("        var layer = comp.layers.add(").append(var).append(");\n");
            sb.append("        layer.name = \"").append(js(scr.getName())).append("\";\n");
            // Явно фиксируем anchor point в центре слоя, не полагаясь на дефолт AE --
            // тогда Position (координата AE-канваса, тоже отсчитываемая от anchor point)
            // однозначно совпадает с левым верхним углом экрана (x0,y0) + половина
            // габарита, как и у ResolumePresetExporter.
            sb.append("        layer.property(\"Anchor Point\").setValue([").append(w / 2.0).append(", ")
                    .append(h / 2.0).append("]);\n");
            sb.append("        layer.property(\"Position\").setValue([").append(x0 + w / 2.0).append(", ")
                    .append(y0 + h / 2.0).append("]);\n");
            sb.append("    }\n");
        }

        sb.append("    app.endUndoGroup();\n");
        sb.append("})();\n");
        return sb.toString();
    }

    /** Имя файла маски экрана — ДОЛЖНО совпадать байт-в-байт с тем, что пишет кнопка
     *  «Экспорт масок» ({@code VisualizationStagePanel#exportMasks}), иначе .jsx не найдёт
     *  файл, если пользователь уже экспортировал маски отдельно в ту же папку. */
    public static String maskFilename(String sceneNameSanitized, Screen scr, int widthPx, int heightPx) {
        return sceneNameSanitized + "_" + OutputPaths.sanitize(scr.getName()) + "_Маска_" + widthPx + "x" + heightPx
                + ".png";
    }

    private static String safeVarName(String id) {
        return id == null ? "x" : id.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static Screen screenById(Scene scene, String screenId) {
        for (Screen s : scene.getScreens()) {
            if (s.getId().equals(screenId)) {
                return s;
            }
        }
        return null;
    }

    private static String js(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsComment(String s) {
        return s == null ? "" : s.replace("\n", " ").replace("\r", "");
    }
}
