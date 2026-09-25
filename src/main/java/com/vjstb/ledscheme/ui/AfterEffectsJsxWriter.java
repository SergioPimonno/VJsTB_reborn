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
 * в AE (File → Scripts → Run Script File) создаёт композицию размером с канвас.
 *
 * <p>Переделано (2026-09-25) по образцу экспорта pixl Grid, который пользователь прислал
 * как эталон: в композиции канваса — чёрная подложка, по прекомпозиции на каждый
 * размещённый экран (размером с экран, внутри — подложка и PNG-маска ИМЕННО этого экрана,
 * тот же файл, что даёт кнопка «Экспорт масок», см. {@link #maskFilename}), слой
 * прекомпа ставится anchor point [0,0] в позицию экрана на канвасе; сверху два
 * guide-слоя — маска пустот канваса ({@link #gapMaskFilename}) и разметка с координатами
 * «TL:x,y» ({@link #overlayFilename}). Слои не блокируются — решение пользователя.
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
        sb.append("// AVE_ToolBox -- шаблон композиции для After Effects\n");
        sb.append("// Сцена: ").append(jsComment(scene.getName())).append(", канвас: ")
                .append(jsComment(canvas.getName())).append("\n");
        sb.append("// PNG-файлы ищутся РЯДОМ с этим .jsx-файлом.\n");
        sb.append("(function () {\n");
        sb.append("    app.beginUndoGroup(\"Импорт AVE_ToolBox -- ").append(js(canvas.getName()))
                .append("\");\n");
        sb.append("    if (!app.project) app.newProject();\n");
        sb.append("    var scriptFolder = new File($.fileName).parent;\n");
        sb.append("    function importPng(filename) {\n");
        sb.append("        var f = new File(scriptFolder.fsName + \"/\" + filename);\n");
        sb.append("        if (!f.exists) {\n");
        sb.append("            alert(\"Файл не найден рядом со скриптом: \" + filename);\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        return app.project.importFile(new ImportOptions(f));\n");
        sb.append("    }\n");
        sb.append("    var DUR = ").append(DEFAULT_DURATION_SEC).append(";\n");
        sb.append("    var FPS = ").append(DEFAULT_FRAME_RATE).append(";\n");
        // Баг-репорт: "в названиях масок также было название экрана + его разрешение, для
        // канваса аналогично" -- имя композиции/слоя в AE (не только сам PNG-файл маски)
        // тоже несёт разрешение, тем же "_ШxВ" суффиксом, что и в maskFilename ниже, чтобы
        // не приходилось открывать свойства слоя/композиции в AE, чтобы узнать габариты.
        int cw = canvas.getWidthPx();
        int ch = canvas.getHeightPx();
        String compName = canvas.getName() + "_" + cw + "x" + ch;
        sb.append("    var comp = app.project.items.addComp(\"").append(js(compName)).append("\", ")
                .append(cw).append(", ").append(ch).append(", 1.0, DUR, FPS);\n");
        sb.append("    comp.layers.addSolid([0, 0, 0], \"Background\", ").append(cw).append(", ").append(ch)
                .append(", 1.0);\n");

        // Структура -- по образцу pixl Grid (эталон прислал пользователь): у каждого экрана
        // своя прекомпозиция размером с экран (подложка + его PNG-маска), в композицию
        // канваса она кладётся слоем с anchor point в левом верхнем углу -- тогда Position
        // слоя буквально равна смещению экрана в канвасе (x0,y0), без пересчёта через
        // половину габарита. Контент под конкретный экран делается прямо в его прекомпе.
        // Слои намеренно НЕ блокируются (в отличие от pixl) -- по решению пользователя.
        for (CanvasPlacement pl : canvas.getPlacements()) {
            Screen scr = screenById(scene, pl.getScreenId());
            if (scr == null) {
                continue;
            }
            ScreenStats stats = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            int w = stats.resolutionWidthPx();
            int h = stats.resolutionHeightPx();
            String filename = maskFilename(sceneNameSanitized, scr, w, h);
            String name = scr.getName() + "_" + w + "x" + h;
            String v = safeVarName(scr.getId());
            sb.append("    // ").append(jsComment(name)).append(" @ ").append(pl.getX()).append(",")
                    .append(pl.getY()).append("\n");
            sb.append("    var pre_").append(v).append(" = app.project.items.addComp(\"").append(js(name))
                    .append("\", ").append(w).append(", ").append(h).append(", 1.0, DUR, FPS);\n");
            sb.append("    pre_").append(v).append(".layers.addSolid([0, 0, 0], \"Background\", ").append(w)
                    .append(", ").append(h).append(", 1.0);\n");
            sb.append("    var footage_").append(v).append(" = importPng(\"").append(js(filename)).append("\");\n");
            sb.append("    if (footage_").append(v).append(") pre_").append(v).append(".layers.add(footage_")
                    .append(v).append(");\n");
            sb.append("    var layer_").append(v).append(" = comp.layers.add(pre_").append(v).append(");\n");
            sb.append("    layer_").append(v).append(".property(\"Anchor Point\").setValue([0, 0]);\n");
            sb.append("    layer_").append(v).append(".property(\"Position\").setValue([").append(pl.getX())
                    .append(", ").append(pl.getY()).append("]);\n");
        }

        // Справочные слои поверх экранов -- guide-слои: видны в AE, но не попадают в
        // рендер (пользователь выбрал это вместо обычных слоёв, как у pixl, чтобы маска
        // пустот и подписи не испортили финальный контент).
        appendGuideLayer(sb, "gap", gapMaskFilename(sceneNameSanitized, canvas));
        appendGuideLayer(sb, "overlay", overlayFilename(sceneNameSanitized, canvas));

        sb.append("    comp.openInViewer();\n");
        sb.append("    app.endUndoGroup();\n");
        sb.append("})();\n");
        return sb.toString();
    }

    private static void appendGuideLayer(StringBuilder sb, String var, String filename) {
        sb.append("    var ").append(var).append(" = importPng(\"").append(js(filename)).append("\");\n");
        sb.append("    if (").append(var).append(") {\n");
        sb.append("        var ").append(var).append("Layer = comp.layers.add(").append(var).append(");\n");
        sb.append("        ").append(var).append("Layer.property(\"Anchor Point\").setValue([0, 0]);\n");
        sb.append("        ").append(var).append("Layer.property(\"Position\").setValue([0, 0]);\n");
        sb.append("        ").append(var).append("Layer.guideLayer = true;\n");
        sb.append("    }\n");
    }

    /** PNG «пустот» канваса (чёрное вне кабинетов, прозрачное над ними) -- см.
     *  {@link PixelGridRenderer#renderCanvasGapMask}. */
    public static String gapMaskFilename(String sceneNameSanitized, ContentCanvas canvas) {
        return sceneNameSanitized + "_" + OutputPaths.sanitize(canvas.getName()) + "_Пустоты_"
                + canvas.getWidthPx() + "x" + canvas.getHeightPx() + ".png";
    }

    /** PNG-оверлей с рамками и координатами экранов -- см.
     *  {@link PixelGridRenderer#renderCanvasOverlay}. */
    public static String overlayFilename(String sceneNameSanitized, ContentCanvas canvas) {
        return sceneNameSanitized + "_" + OutputPaths.sanitize(canvas.getName()) + "_Разметка_"
                + canvas.getWidthPx() + "x" + canvas.getHeightPx() + ".png";
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
