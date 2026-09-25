package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.VehicleLoadPlacement;
import com.vjstb.ledscheme.model.VehicleLoadPlan;
import com.vjstb.ledscheme.model.VehicleLoadSection;
import com.vjstb.ledscheme.model.VehicleType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.ExportProgressDialog;
import com.vjstb.ledscheme.ui.ExportSettingsDialog;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.PixelGridRenderer;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaCanvasPanel;
import com.vjstb.ledscheme.ui.SchemeImageWriter;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import com.vjstb.ledscheme.ui.UiKit;
import com.vjstb.ledscheme.ui.VehicleLoadSchemaImageWriter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Этап «Вывод»: выбор папки и формирование пакета документации проекта —
 * JPEG-схемы (питание/сигнал) всех экранов + отчёт (текстовый, нагрузки/веса/точки
 * подвеса, без цепочек) и спецификация (табличная, .xlsx, только количество
 * оборудования, см. {@link #buildEquipmentSpecWorkbook}).
 */
public class OutputStagePanel extends JPanel {

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final JTextField folderField = new JTextField();
    private final JLabel exportOptionsSummary = new JLabel();
    private File chosenFolder;

    public OutputStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setLayout(new BorderLayout());
        add(new ContextBar(model, false), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        model.addListener(this::refreshFolderField);
        refreshFolderField();
    }

    /** Папка вывода: явно выбранная пользователем — если её ещё не выбирали,
     *  подставляется дефолтная ~/Documents/Video/{проект} (без блокирующего
     *  «сначала выберите папку» — путь создаётся автоматически при необходимости). */
    private File resolveFolder() {
        if (chosenFolder != null) {
            return chosenFolder;
        }
        Project project = model.getCurrentProject();
        return project != null ? OutputPaths.defaultFolder(project, null, settings) : null;
    }

    private void refreshFolderField() {
        File f = resolveFolder();
        folderField.setText(f != null ? f.getAbsolutePath() : "(сначала выберите проект)");
    }

    private JPanel buildBody() {
        JPanel body = UiKit.vbox();
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderField.setEditable(false);
        folderField.setText("(не выбрана)");
        JButton choose = new JButton("Выбрать папку…");
        choose.addActionListener(e -> chooseFolder());
        folderRow.add(folderField, BorderLayout.CENTER);
        folderRow.add(choose, BorderLayout.EAST);
        body.add(UiKit.section("Папка вывода", folderRow));

        body.add(UiKit.vgap(10));
        // Формат/DPI/сжатие — в отдельном окне «Параметры экспорта» (по запросу
        // пользователя; раньше здесь был только выпадающий DPI). Тут — сводка.
        JPanel optionsRow = new JPanel(new BorderLayout(6, 0));
        JButton options = new JButton("Параметры экспорта…");
        options.addActionListener(e -> {
            if (ExportSettingsDialog.show(this, settings)) {
                refreshExportOptionsSummary();
            }
        });
        optionsRow.add(exportOptionsSummary, BorderLayout.CENTER);
        optionsRow.add(options, BorderLayout.EAST);
        refreshExportOptionsSummary();
        body.add(UiKit.section("Параметры экспорта схем", optionsRow));

        body.add(UiKit.vgap(10));
        JButton generate = new JButton("Сформировать пакет документации проекта");
        generate.addActionListener(e -> generate());
        body.add(generate);

        body.add(UiKit.vgap(16));
        JPanel piecesPanel = UiKit.vbox();
        JButton exportPower = new JButton("Экспортировать схемы этапа «Питание»");
        exportPower.addActionListener(e -> exportSchemesForMode(true));
        piecesPanel.add(exportPower);
        piecesPanel.add(UiKit.vgap(6));
        JButton exportSignal = new JButton("Экспортировать схемы этапа «Сигнал»");
        exportSignal.addActionListener(e -> exportSchemesForMode(false));
        piecesPanel.add(exportSignal);
        piecesPanel.add(UiKit.vgap(6));
        JButton exportSummary = new JButton("Сформировать сводку по проекту");
        exportSummary.addActionListener(e -> exportReportOnly());
        piecesPanel.add(exportSummary);
        piecesPanel.add(UiKit.vgap(6));
        JButton exportSpec = new JButton("Сформировать спецификацию оборудования");
        exportSpec.addActionListener(e -> exportSpecOnly());
        piecesPanel.add(exportSpec);
        body.add(UiKit.section("Экспорт по отдельности", piecesPanel));

        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    /** Цепочки проекта (любой сцены) с превышением ёмкости, ещё не подтверждённые
     *  кнопкой «Я знаю» (Task #81) — по строке на цепочку («сцена · экран · что»);
     *  пустой список — экспорт разрешён. Баг-репорт: раньше тут был просто boolean, и
     *  диалог говорил лишь «проверьте цепочки, отмеченные ⚠» — а ⚠ в списке цепочек
     *  остаётся и ПОСЛЕ «Я знаю», плюс цепочки другой сцены/экрана не видны, пока их
     *  не выбрать. Пользователь «везде прожал», а экспорт всё равно стоял, и найти
     *  виновника было нечем — теперь диалог называет его явно. */
    private List<String> unacknowledgedOverloads(Project project) {
        List<String> result = new java.util.ArrayList<>();
        for (Scene scene : project.getScenes()) {
            for (PowerChain chain : scene.getPowerChains()) {
                AppModel.ChainLoadStatus st = model.powerChainLoadStatus(scene, chain);
                if (st.blocksExport()) {
                    result.add(chainPlace(scene, chain.getCabinetInstanceIds()) + " · питание L" + chain.getPhase()
                            + ", " + chain.getCabinetInstanceIds().size() + " каб. · "
                            + UiKit.fmt(st.loadWatts()) + " Вт при допустимых " + UiKit.fmt(st.capacityWatts()) + " Вт");
                }
            }
            // Сигнальная нагрузка на порт контроллера (Task v1.4) — тот же бэкенд, что и
            // для питания выше, просто раньше нигде не вызывался на пути экспорта.
            for (SignalChain chain : scene.getSignalChains()) {
                AppModel.SignalChainLoadStatus st = model.signalChainLoadStatus(scene, chain);
                if (st.blocksExport()) {
                    result.add(chainPlace(scene, chain.getCabinetInstanceIds()) + " · сигнал, порт "
                            + chain.getPortNumber() + (chain.isBackup() ? " (бэкап)" : "") + ", "
                            + chain.getCabinetInstanceIds().size() + " каб. · "
                            + UiKit.fmt(st.loadPixels()) + " px при допустимых " + UiKit.fmt(st.capacityPixels()) + " px");
                }
            }
        }
        return result;
    }

    /** «Сцена · Экран» для цепочки — экран первого её кабинета (там, где цепочку видно
     *  в списке этапа «Питание»/«Сигнал»). */
    private static String chainPlace(Scene scene, List<String> cabinetIds) {
        String screenName = "?";
        if (!cabinetIds.isEmpty()) {
            for (Screen s : scene.getScreens()) {
                if (s.cabinetById(cabinetIds.get(0)) != null) {
                    screenName = s.getName();
                    break;
                }
            }
        }
        return "«" + scene.getName() + "» · экран «" + screenName + "»";
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void refreshExportOptionsSummary() {
        var p = settings.activeProfile();
        SchemeImageWriter.Format f = SchemeImageWriter.Format.fromId(p.getDocExportFormat());
        exportOptionsSummary.setText("Формат: " + f.name() + " · " + p.getDocExportDpi() + " DPI"
                + (f.lossy() ? " · сжатие " + p.getDocExportQuality() + "%" : ""));
    }

    /** Схема в выбранном формате: {@code dir/baseName.<ext>} (см. {@link SchemeImageWriter}). */
    private void writeScheme(BufferedImage img, File dir, String baseName, int dpi) throws java.io.IOException {
        var p = settings.activeProfile();
        SchemeImageWriter.write(img, dir, baseName, SchemeImageWriter.Format.fromId(p.getDocExportFormat()),
                dpi, p.getDocExportQuality());
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку вывода");
        // Баг-репорт: с настроенной папкой экспорта по умолчанию в «Предпочтения →
        // Экспорт» диалог всё равно открывался в Documents — chosenFolder тут пуст,
        // пока пользователь ХОТЬ РАЗ не выбрал папку САМ в этой сессии, поэтому
        // resolveFolder() (которая как раз учитывает настройку) не подставлялась
        // вообще. Стартуем от неё же, не только от уже явно выбранной раньше.
        File initial = chosenFolder != null ? chosenFolder : resolveFolder();
        if (initial != null) {
            fc.setCurrentDirectory(initial);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            chosenFolder = fc.getSelectedFile();
            folderField.setText(chosenFolder.getAbsolutePath());
        }
    }

    /** Общая проверка перед любым экспортом (пакет целиком и все отдельные кнопки ниже):
     *  проект выбран, и, если включено отслеживание нагрузки, нет неподтверждённых
     *  перегрузок цепочек. Показывает предупреждающий диалог сама и возвращает {@code
     *  null}, если экспортировать сейчас нельзя. */
    private Project requireExportableProject() {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите проект", "Нет проекта", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        List<String> overloads = settings.activeProfile().isLoadTrackingEnabled()
                ? unacknowledgedOverloads(project) : List.of();
        if (!overloads.isEmpty()) {
            StringBuilder list = new StringBuilder();
            int shown = Math.min(overloads.size(), 15);
            for (int i = 0; i < shown; i++) {
                list.append("<br>• ").append(escapeHtml(overloads.get(i)));
            }
            if (overloads.size() > shown) {
                list.append("<br>… и ещё ").append(overloads.size() - shown);
            }
            JOptionPane.showMessageDialog(this,
                    "<html>В проекте есть цепочки питания или сигнала с неподтверждённым превышением ёмкости"
                            + " (разъёма кабинета — для питания, порта контроллера — для сигнала):"
                            + list
                            + "<br><br>Откройте этап «Питание»/«Сигнал» этой сцены, выберите указанный экран и"
                            + " подтвердите цепочку кнопкой «Я знаю» (или измените коммутацию).<br>Экспорт"
                            + " остановлен, пока такие цепочки есть. Проверку можно отключить в Предпочтениях"
                            + " («Контроль электрической нагрузки»).</html>",
                    "Перегрузка цепочек", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return project;
    }

    /** Текстовый отчёт по проекту: сквозные итоги (экраны/сцены/кабинеты/мощность/вес),
     *  затем по каждой сцене и каждому её экрану — нагрузки, веса, разрешение, точки
     *  подвеса (без самих цепочек, см. комментарий у их получения в generate()). Не
     *  зависит от "текущей" сцены модели — используется и внутри пакета документации
     *  (generate()), и отдельной кнопкой «Сформировать сводку по проекту». */
    private String buildProjectReport(Project project, boolean kw) {
        StringBuilder report = new StringBuilder();
        report.append("Отчёт по проекту: ").append(project.getName()).append('\n');
        report.append("Дата: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");

        // --- итоги по проекту (сквозные по всем сценам) ---
        int totalScreens = 0;
        int totalCabinets = 0;
        double totalPower = 0;
        double totalWeight = 0;

        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                CabinetType defaultType = model.typeOf(scr);
                totalScreens++;
                for (com.vjstb.ledscheme.model.CabinetInstance c : scr.getCabinets()) {
                    if (c.isHidden()) {
                        continue;
                    }
                    CabinetType effective = defaultType;
                    if (c.getCabinetTypeId() != null) {
                        CabinetType override = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
                        if (override != null) {
                            effective = override;
                        }
                    }
                    totalCabinets++;
                    if (effective != null) {
                        totalPower += effective.getPowerConsumptionW();
                        totalWeight += effective.getWeightKg();
                    }
                }
            }
        }

        report.append("=== ИТОГО ПО ПРОЕКТУ ===\n");
        report.append(String.format("Экранов: %d, сцен: %d, кабинетов: %d%n",
                totalScreens, project.getScenes().size(), totalCabinets));
        report.append(String.format("Суммарная мощность: %s, суммарный вес: %s кг%n%n",
                UiKit.fmtPower(totalPower, kw), UiKit.fmt(totalWeight)));

        for (Scene scene : project.getScenes()) {
            SceneStats ss = ScreenLogic.sceneStats(scene, model.getWorkspace());
            report.append("Сцена: ").append(scene.getName()).append('\n');
            report.append(String.format("  Экранов: %d, кабинетов: %d, мощность: %s, вес: %s кг%n",
                    ss.screenCount(), ss.totalCabinetCount(), UiKit.fmtPower(ss.totalPowerW(), kw),
                    UiKit.fmt(ss.totalWeightKg())));

            for (Screen scr : scene.getScreens()) {
                CabinetType type = model.typeOf(scr);
                ScreenStats st = ScreenLogic.stats(scr, type, model.getWorkspace());
                report.append("  Экран «").append(scr.getName()).append("»\n");
                report.append(String.format("    Кабинет: %s, сетка: %d×%d%n",
                        type != null ? type.getName() : "—", scr.getCols(), scr.getRows()));
                report.append(String.format("    Разрешение: %d×%d px, физический размер: %s×%s мм%n",
                        st.resolutionWidthPx(), st.resolutionHeightPx(),
                        UiKit.fmt(st.physicalWidthMm()), UiKit.fmt(st.physicalHeightMm())));
                report.append(String.format("    Мощность: %s, вес: %s кг%n",
                        UiKit.fmtPower(st.totalPowerW(), kw), UiKit.fmt(st.totalWeightKg())));
                report.append(String.format("    Точек подвеса: %d%n", scr.getRiggingPointsCount()));
                report.append('\n');
            }
            report.append('\n');
        }
        return report.toString();
    }

    private void generate() {
        Project project = requireExportableProject();
        if (project == null) {
            return;
        }
        File folder = resolveFolder();
        // Качество JPEG-схем (питание/сигнал/блок-схема/обзор сцены), НЕ масок —
        // см. ExportSettingsDialog/UserProfile#getDocExportDpi. 1.0 = 72dpi = прежнее поведение.
        int docExportDpi = settings.activeProfile().getDocExportDpi();
        double dpiScale = docExportDpi / 72.0;
        boolean kw = settings.activeProfile().isPowerUnitKw();

        String report = buildProjectReport(project, kw);

        // Рендер схемы сцены целиком (SceneCanvasPanel) и маски канваса
        // (PixelGridRenderer.renderCanvasMask) читают "текущую" сцену модели, а не
        // параметр — на время экспорта временно переключаем выбор сцены, поэтому
        // сохраняем исходный, чтобы вернуть его после (см. finally ниже). Побочный
        // эффект: AppModel.selectScene очищает стек undo — то же самое произошло бы,
        // если бы пользователь вручную переключил сцену, так что это не новый риск.
        Scene origScene = model.getCurrentScene();
        Screen origScreen = model.getCurrentScreen();

        int jpegCount = 0;
        int maskCount = 0;
        ExportProgressDialog progress = null;
        try {
            int total = 2; // отчёт + спецификация
            for (Scene scene : project.getScenes()) {
                int n = scene.getScreens().size();
                total += n * 3 + (n > 1 ? 2 : 0) + SchemaMode.values().length + scene.getCanvases().size();
                if (scene.getVehicleLoadPlan() != null) {
                    total += scene.getVehicleLoadPlan().getSections().size();
                }
            }
            progress = new ExportProgressDialog(this, "Формирование пакета документации", total);
            for (Scene scene : project.getScenes()) {
                model.selectScene(scene);

                // Схемы и маски раньше сохранялись плоско в папку ПРОЕКТА — из-за
                // этого схемы всех сцен смешивались в одном месте, и не было видно,
                // какая схема к какой сцене относится, кроме как по имени файла.
                // Теперь у каждой сцены своя папка с подпапками по назначению.
                File sceneFolder = new File(folder, OutputPaths.sanitize(scene.getName()));
                File powerFolder = new File(sceneFolder, "Сила");
                File signalFolder = new File(sceneFolder, "Сигнал");
                File masksFolder = new File(sceneFolder, "Маски");
                powerFolder.mkdirs();
                signalFolder.mkdirs();
                masksFolder.mkdirs();

                for (Screen scr : scene.getScreens()) {
                    CabinetType type = model.typeOf(scr);

                    // Цепочки хранятся на уровне сцены (Task #78), а не экрана — берём
                    // только те, что физически затрагивают кабинеты ЭТОГО экрана (цепочка
                    // может начинаться на другом экране сцены и продолжаться сюда). Нужны
                    // только для рендера схем ниже — сам список цепочек в отчёт не входит
                    // (см. Task v1.4: отчёт — только нагрузки/веса/точки подвеса).
                    List<PowerChain> scrPowerChains = model.powerChainsTouchingScreen(scr);
                    List<SignalChain> scrSignalChains = model.signalChainsTouchingScreen(scr);

                    List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers =
                            model.controllersInScene(scr);
                    progress.step(scene.getName() + " · " + scr.getName() + " · схема питания");
                    BufferedImage powerImg = SchemeRenderer.renderImage(scr, type, true, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains, sceneControllers, kw, dpiScale);
                    writeScheme(powerImg, powerFolder, OutputPaths.sanitize(scr.getName() + " Сила"), docExportDpi);
                    jpegCount++;

                    progress.step(scene.getName() + " · " + scr.getName() + " · схема сигнала");
                    BufferedImage signalImg = SchemeRenderer.renderImage(scr, type, false, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains, sceneControllers, kw, dpiScale);
                    writeScheme(signalImg, signalFolder, OutputPaths.sanitize(scr.getName() + " Сигнал"),
                            docExportDpi);
                    jpegCount++;

                    progress.step(scene.getName() + " · " + scr.getName() + " · маска");
                    BufferedImage maskImg = PixelGridRenderer.renderMask(scr, type, model.getWorkspace(),
                            PixelGridRenderer.GridRenderOptions.defaultForScreen(scr));
                    PixelGridRenderer.writePng(maskImg,
                            new File(masksFolder, OutputPaths.sanitize(scr.getName()) + "_Маска_"
                                    + maskImg.getWidth() + "x" + maskImg.getHeight() + ".png"));
                    maskCount++;
                }

                // Схема сцены ЦЕЛИКОМ (все экраны сразу, как «Показать все экраны
                // сцены») — если цепочка проходит через пару экранов, по отдельным
                // схемам экранов этого не видно вовсе, только по этой общей схеме.
                // При ОДНОМ экране в сцене эта схема буквально дублирует его же
                // отдельную схему (межэкранных цепочек физически быть не может) —
                // не генерируем лишний файл.
                if (scene.getScreens().size() > 1) {
                    for (boolean power : new boolean[]{true, false}) {
                        progress.step(scene.getName() + " · все экраны сцены · " + (power ? "питание" : "сигнал"));
                        SceneCanvasPanel overview = new SceneCanvasPanel(model, settings);
                        overview.setDetailMode(true, power, false);
                        Dimension size = overview.getPreferredSize();
                        BufferedImage img = overview.renderImage(size.width, size.height, dpiScale);
                        writeScheme(img, power ? powerFolder : signalFolder, "_Все экраны сцены", docExportDpi);
                        jpegCount++;
                    }
                }

                // Общая нарисованная блок-схема площадки («Общая схема питания/сигнала»
                // из редактора) раньше вообще не попадала в пакет документации, хотя
                // на ней размечена вся коммутация оборудования (щиты/дистрибьюторы/
                // конвертеры/медиасерверы/контроллеры), а не только сами экраны. Две
                // версии: обычная (как в редакторе) и "тестовая", где вместо блока
                // экрана рисуется уменьшённая схема его расключения (см.
                // SchemaCanvasPanel.renderImage(..., renderScreenWiring=true)) — так
                // видно, к какому физическому оборудованию подключён каждый экран, не
                // открывая отдельно схему расключения каждого экрана.
                // Экраны в общей схеме рисуются блоком или схемой расключения — по
                // тому же переключателю Персонализации, что и в живом редакторе схемы
                // (см. Task #83/#84/v1.4), а не всегда обоими вариантами сразу.
                boolean screensAsWiring = settings.activeProfile().isSchemaScreensAsWiringDiagram();
                for (SchemaMode schemaMode : SchemaMode.values()) {
                    File modeFolder = schemaMode == SchemaMode.POWER ? powerFolder : signalFolder;
                    progress.step(scene.getName() + " · общая схема "
                            + (schemaMode == SchemaMode.POWER ? "питания" : "сигнала"));
                    SchemaCanvasPanel schemaCanvas = new SchemaCanvasPanel(model, schemaMode, settings);
                    Dimension size = schemaCanvas.getPreferredSize();
                    BufferedImage img = schemaCanvas.renderImage(size.width, size.height, screensAsWiring, dpiScale);
                    String modeSuffix = schemaMode == SchemaMode.POWER ? " Сила" : " Сигнал";
                    writeScheme(img, modeFolder, OutputPaths.sanitize(scene.getName() + modeSuffix), docExportDpi);
                    jpegCount++;
                }

                for (ContentCanvas c : scene.getCanvases()) {
                    progress.step(scene.getName() + " · маска канваса «" + c.getName() + "»");
                    BufferedImage img = PixelGridRenderer.renderCanvasMask(c, model, settings);
                    PixelGridRenderer.writePng(img,
                            new File(masksFolder, "Канвас_" + OutputPaths.sanitize(c.getName()) + "_"
                                    + img.getWidth() + "x" + img.getHeight() + ".png"));
                    maskCount++;
                }

                // Схема загрузки машины(-) кофрами (см. ui.VehicleLoadVisualizerDialog,
                // персистится в Scene.vehicleLoadPlan) — по прямому запросу пользователя:
                // "рендер схемы размещения аналогично рендеру схем расключения", тот же
                // dpiScale/docExportDpi, что и остальные JPEG-схемы этого экспорта. Только
                // если визуализатор для этой сцены вообще открывали и там что-то размещено —
                // не создаём папку/файлы для сцен без раскладки.
                VehicleLoadPlan loadPlan = scene.getVehicleLoadPlan();
                if (loadPlan != null && !loadPlan.getSections().isEmpty()) {
                    File transportFolder = new File(sceneFolder, "Транспорт");
                    int machineIndex = 0;
                    for (VehicleLoadSection section : loadPlan.getSections()) {
                        machineIndex++;
                        progress.step(scene.getName() + " · схема загрузки машины " + machineIndex);
                        VehicleType vt = model.getWorkspace().vehicleTypeById(section.getVehicleTypeId());
                        if (vt == null || section.getPlacements().isEmpty()) {
                            continue; // машина удалена из библиотеки или пуста — рисовать нечего
                        }
                        List<VehicleLoadSchemaImageWriter.PlacedCase> resolved = new java.util.ArrayList<>();
                        for (VehicleLoadPlacement pl : section.getPlacements()) {
                            CaseType ct = model.getWorkspace().caseTypeById(pl.getCaseTypeId());
                            if (ct == null) {
                                continue; // тип кофра удалён из библиотеки — пропускаем эту позицию
                            }
                            resolved.add(new VehicleLoadSchemaImageWriter.PlacedCase(ct, pl.getXMm(), pl.getYMm(),
                                    pl.isRotated(), pl.getStackCount(), pl.getNote()));
                        }
                        if (resolved.isEmpty()) {
                            continue;
                        }
                        transportFolder.mkdirs();
                        BufferedImage transportImg = VehicleLoadSchemaImageWriter.render(vt, resolved,
                                "Машина " + machineIndex, dpiScale);
                        writeScheme(transportImg, transportFolder,
                                OutputPaths.sanitize(scene.getName() + " Машина " + machineIndex), docExportDpi);
                        jpegCount++;
                    }
                }
            }

            progress.step("Отчёт по проекту");
            File reportFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_отчёт.txt");
            Files.writeString(reportFile.toPath(), report, StandardCharsets.UTF_8);

            progress.step("Спецификация оборудования");
            File specFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_спецификация.xlsx");
            try (Workbook specWorkbook = buildEquipmentSpecWorkbook(project)) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.write(specWorkbook, specFile);
            }

            progress.close();
            int answer = JOptionPane.showConfirmDialog(this,
                    "Готово.\nСхем сохранено: " + jpegCount + "\nМасок сохранено: " + maskCount
                            + "\nОтчёт: " + reportFile.getName() + "\nСпецификация: " + specFile.getName()
                            + "\n\nОткрыть папку?",
                    "Пакет документации сформирован", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openFolder(folder);
            }
        } catch (Exception ex) {
            if (progress != null) {
                progress.close();
            }
            JOptionPane.showMessageDialog(this, "Ошибка формирования пакета: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (progress != null) {
                progress.close();
            }
            model.selectScene(origScene);
            model.selectScreen(origScreen);
        }
    }

    /** Схемы расключения (JPEG) только одного этапа — «Питание» или «Сигнал» — без масок,
     *  отчёта, спецификации и схем транспорта: те же файлы и та же структура папок
     *  (Сцена/Сила или Сцена/Сигнал), что и в полном пакете (см. generate()), просто без
     *  остального. Для проекта, где нужно отправить смежникам схему только одной из двух
     *  сетей, не пересобирая весь пакет документации. */
    private void exportSchemesForMode(boolean power) {
        Project project = requireExportableProject();
        if (project == null) {
            return;
        }
        File folder = resolveFolder();
        int docExportDpi = settings.activeProfile().getDocExportDpi();
        double dpiScale = docExportDpi / 72.0;
        boolean kw = settings.activeProfile().isPowerUnitKw();
        String modeFolderName = power ? "Сила" : "Сигнал";
        String modeSuffix = power ? " Сила" : " Сигнал";

        Scene origScene = model.getCurrentScene();
        Screen origScreen = model.getCurrentScreen();
        int jpegCount = 0;
        ExportProgressDialog progress = null;
        try {
            int total = 0;
            for (Scene scene : project.getScenes()) {
                int n = scene.getScreens().size();
                total += n + (n > 1 ? 1 : 0) + 1;
            }
            progress = new ExportProgressDialog(this, "Экспорт схем этапа «" + modeFolderName + "»", total);
            for (Scene scene : project.getScenes()) {
                model.selectScene(scene);

                File sceneFolder = new File(folder, OutputPaths.sanitize(scene.getName()));
                File modeFolder = new File(sceneFolder, modeFolderName);
                modeFolder.mkdirs();

                for (Screen scr : scene.getScreens()) {
                    CabinetType type = model.typeOf(scr);
                    List<PowerChain> scrPowerChains = model.powerChainsTouchingScreen(scr);
                    List<SignalChain> scrSignalChains = model.signalChainsTouchingScreen(scr);
                    List<com.vjstb.ledscheme.model.ControllerInstance> sceneControllers =
                            model.controllersInScene(scr);
                    progress.step(scene.getName() + " · " + scr.getName());
                    BufferedImage img = SchemeRenderer.renderImage(scr, type, power, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains, sceneControllers, kw, dpiScale);
                    writeScheme(img, modeFolder, OutputPaths.sanitize(scr.getName() + modeSuffix), docExportDpi);
                    jpegCount++;
                }

                if (scene.getScreens().size() > 1) {
                    progress.step(scene.getName() + " · все экраны сцены");
                    SceneCanvasPanel overview = new SceneCanvasPanel(model, settings);
                    overview.setDetailMode(true, power, false);
                    Dimension size = overview.getPreferredSize();
                    BufferedImage img = overview.renderImage(size.width, size.height, dpiScale);
                    writeScheme(img, modeFolder, "_Все экраны сцены", docExportDpi);
                    jpegCount++;
                }

                boolean screensAsWiring = settings.activeProfile().isSchemaScreensAsWiringDiagram();
                SchemaMode schemaMode = power ? SchemaMode.POWER : SchemaMode.SIGNAL;
                progress.step(scene.getName() + " · общая схема");
                SchemaCanvasPanel schemaCanvas = new SchemaCanvasPanel(model, schemaMode, settings);
                Dimension size = schemaCanvas.getPreferredSize();
                BufferedImage img = schemaCanvas.renderImage(size.width, size.height, screensAsWiring, dpiScale);
                writeScheme(img, modeFolder, OutputPaths.sanitize(scene.getName() + modeSuffix), docExportDpi);
                jpegCount++;
            }

            progress.close();
            int answer = JOptionPane.showConfirmDialog(this,
                    "Готово.\nСхем сохранено: " + jpegCount + "\n\nОткрыть папку?",
                    "Схемы этапа «" + modeFolderName + "» сохранены", JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openFolder(folder);
            }
        } catch (Exception ex) {
            if (progress != null) {
                progress.close();
            }
            JOptionPane.showMessageDialog(this, "Ошибка экспорта схем: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (progress != null) {
                progress.close();
            }
            model.selectScene(origScene);
            model.selectScreen(origScreen);
        }
    }

    /** Только текстовая сводка по проекту (см. {@link #buildProjectReport}), без схем/масок/
     *  спецификации — по прямому запросу пользователя как отдельная кнопка (название
     *  «сводка», не «отчёт», чтобы не путать с «отчётом по проекту» — так называется
     *  содержимое этого же текста внутри полного пакета документации, см. generate()). */
    private void exportReportOnly() {
        Project project = requireExportableProject();
        if (project == null) {
            return;
        }
        File folder = resolveFolder();
        boolean kw = settings.activeProfile().isPowerUnitKw();
        try {
            String report = buildProjectReport(project, kw);
            File reportFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_сводка.txt");
            Files.writeString(reportFile.toPath(), report, StandardCharsets.UTF_8);

            int answer = JOptionPane.showConfirmDialog(this,
                    "Готово.\nСводка: " + reportFile.getName() + "\n\nОткрыть папку?",
                    "Сводка по проекту сформирована", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openFolder(folder);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования сводки: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Только табличная спецификация оборудования (см. {@link #buildEquipmentSpecWorkbook}),
     *  без схем/масок/сводки — отдельная кнопка по прямому запросу пользователя. */
    private void exportSpecOnly() {
        Project project = requireExportableProject();
        if (project == null) {
            return;
        }
        File folder = resolveFolder();
        try {
            File specFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_спецификация.xlsx");
            try (Workbook specWorkbook = buildEquipmentSpecWorkbook(project)) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.write(specWorkbook, specFile);
            }

            int answer = JOptionPane.showConfirmDialog(this,
                    "Готово.\nСпецификация: " + specFile.getName() + "\n\nОткрыть папку?",
                    "Спецификация сформирована", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openFolder(folder);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования спецификации: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Второй файл пакета документации — "сколько чего понадобится": кабинеты (по
     *  типу, из фактического состава экранов), оборудование общей схемы (по типу
     *  узла + подписи, БЕЗ узлов-экранов — это ссылки, не отдельное оборудование)
     *  и спецификация коммутации (см. {@link #addWiringSheets}). Никаких нагрузок/
     *  весов/цепочек здесь — это в отчёте (см. generate()) — тут только количества.
     *  Табличный формат (.xlsx, лист на раздел) вместо plain-text — спецификацию
     *  удобно открыть/отфильтровать/досчитать прямо в Excel. */
    private Workbook buildEquipmentSpecWorkbook(Project project) {
        Workbook wb = com.vjstb.ledscheme.service.SpecXlsxWriter.newWorkbook();

        Sheet info = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Инфо", "Параметр", "Значение");
        com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(info, "Проект", project.getName());
        com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(info, "Дата",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(info, 2);

        java.util.Map<CabinetType, Integer> cabinets = new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                CabinetType defaultType = model.typeOf(scr);
                for (com.vjstb.ledscheme.model.CabinetInstance c : scr.getCabinets()) {
                    if (c.isHidden()) {
                        continue;
                    }
                    CabinetType effective = defaultType;
                    if (c.getCabinetTypeId() != null) {
                        CabinetType override = model.getWorkspace().cabinetTypeById(c.getCabinetTypeId());
                        if (override != null) {
                            effective = override;
                        }
                    }
                    if (effective != null) {
                        cabinets.merge(effective, 1, Integer::sum);
                    }
                }
            }
        }
        // Заголовок и числовые ячейки мощности переключаются вместе (Вт<->кВт) --
        // единица в заголовке обязана совпадать с тем, что реально лежит в ячейках,
        // иначе таблица врёт молча (в отличие от текстовых мест выше, здесь нельзя
        // просто использовать UiKit.fmtPower -- ячейка должна остаться числом для
        // формул/сортировки в Excel, не строкой с суффиксом единицы).
        boolean xlsxKw = settings.activeProfile().isPowerUnitKw();
        double powerScale = xlsxKw ? 1.0 / 1000.0 : 1.0;
        String powerUnit = xlsxKw ? "кВт" : "Вт";
        Sheet cabinetsSheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Кабинеты",
                "Тип", "Кол-во, шт", "Мощность, " + powerUnit + "/шт", "Мощность всего, " + powerUnit,
                "Вес, кг/шт", "Вес всего, кг",
                "Линий питания, шт/кабинет", "Линий питания всего", "Линий сигнала, шт/кабинет", "Линий сигнала всего");
        for (var entry : cabinets.entrySet()) {
            CabinetType t = entry.getKey();
            int qty = entry.getValue();
            // Линии питания/сигнала на кабинет (CabinetType#powerConnectorsNeeded/
            // signalConnectorsNeeded, 0 = встроено/сквозная коммутация) — раньше в
            // спецификацию не попадали вообще, хотя это параметр каждого типа кабинета
            // (баг-репорт 2026-09-25, проект «Бармицва»: все кабинеты Dicolor с 1 линией
            // питания и 1 линией сигнала, а в спецификации про линки ни слова).
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(cabinetsSheet, t.getName(), qty,
                    t.getPowerConsumptionW() * powerScale, t.getPowerConsumptionW() * qty * powerScale,
                    t.getWeightKg(), t.getWeightKg() * qty,
                    t.getPowerConnectorsNeeded(), t.getPowerConnectorsNeeded() * qty,
                    t.getSignalConnectorsNeeded(), t.getSignalConnectorsNeeded() * qty);
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(cabinetsSheet, 10);

        // Оборудование общей схемы (щиты/дистрибьюторы/конвертеры/медиасерверы/
        // контроллеры/прочее) — узлы-экраны не считаются: это ссылка на уже
        // посчитанный выше экран, а не отдельная физическая единица оборудования.
        // Группируется по (режим схемы, тип узла, подпись) — одинаково подписанные
        // узлы одного типа считаются одной моделью оборудования.
        java.util.Map<List<String>, Integer> equipmentNodes = new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (com.vjstb.ledscheme.model.SchemaNode n : scene.getSchemaNodes()) {
                if (n.getType() == com.vjstb.ledscheme.model.SchemaNodeType.SCREEN) {
                    continue;
                }
                // Авто-блок «Легенда портов» (см. SchemaNode#isAutoPortLegend(),
                // AppModel.addSignalPortLegendNode) — это справочная таблица на холсте
                // общей схемы, не физическая единица оборудования; попадал сюда как
                // обычный CUSTOM-узел — баг-репорт 2026-09-16 "легенда портов не должна
                // появляться в спецификации".
                if (n.isAutoPortLegend()) {
                    continue;
                }
                String modeLabel = n.getMode() == com.vjstb.ledscheme.model.SchemaMode.POWER ? "Питание" : "Сигнал";
                String typeLabel = model.categoryLabel(n.getType());
                String label = n.getLabel() == null || n.getLabel().isBlank() ? typeLabel : n.getLabel();
                equipmentNodes.merge(List.of(modeLabel, typeLabel, label), 1, Integer::sum);
            }
        }
        Sheet equipmentSheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Оборудование",
                "Схема", "Тип узла", "Подпись", "Кол-во, шт");
        for (var entry : equipmentNodes.entrySet()) {
            List<String> key = entry.getKey();
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(equipmentSheet, key.get(0), key.get(1), key.get(2),
                    entry.getValue());
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(equipmentSheet, 4);

        addWiringSheets(wb, project);
        addStructureSheet(wb, project);
        addTrussSheet(wb, project);
        addOverallEquipmentSheet(wb, project, cabinets, equipmentNodes);
        // Лист "Общий список" физически создаётся последним (нужны уже посчитанные
        // выше карты/агрегаты остальных листов), но по прямому запросу пользователя
        // должен идти визуально СРАЗУ ПОСЛЕ "Инфо" — переставляем позицию листа в
        // книге отдельно от порядка его заполнения (POI: индекс 0 = "Инфо").
        wb.setSheetOrder("Общий список", 1);
        return wb;
    }

    /** Спецификация наземного конструктива (см. {@code service.StructureCalc},
     *  STRUCTURE_CALC_NOTES.md) — по одной строке на КАЖДЫЙ экран с {@code mountType ==
     *  STRUCTURE}, посчитанной от РЕАЛЬНО расставленных в 3D деталей ({@code
     *  StructureCalc.compute}, тот же вызов, что и {@code SetupStagePanel
     *  #buildStructureSpec} — единственный источник правды, не отдельно
     *  накапливаемый список). Экранов без этого способа монтажа просто нет в списке —
     *  лист может остаться пустым (только заголовок), это нормально для проекта без
     *  ни одного конструктива.
     *
     * <p>Столбец «Рам, шт» — ОБЩЕЕ число (вертикальные + перемычки + секции базы, см.
     * {@code StructureCalc.Result#totalFrameCount} javadoc), не три отдельных столбца, как
     * раньше — по прямому указанию пользователя (2026-08-20): это физически один и тот же
     * каталожный тип рамы, заказывается одним числом. */
    private void addStructureSheet(Workbook wb, Project project) {
        Sheet sheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Конструктив",
                "Сцена", "Экран", "Рам, шт", "Стаканов, шт", "Болтов, шт", "Требуемый балласт, кг",
                "Отгрузов, шт");
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE) {
                    continue;
                }
                CabinetType type = model.typeOf(scr);
                com.vjstb.ledscheme.service.StructureCalc.Result r =
                        com.vjstb.ledscheme.service.StructureCalc.compute(scr, type, model.getWorkspace());
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, scene.getName(), scr.getName(),
                        r.totalFrameCount(), r.cupCount(), r.boltCount(), r.requiredBallastKg(),
                        r.ballastContainerCount());
            }
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(sheet, 7);
    }

    /** Спецификация фермы подвеса (см. {@code service.TrussCalc}, RIGGING_CALC_NOTES.md) —
     *  по одной строке на каждый экран с {@code mountType == RIGGED} и выбранным {@code
     *  riggingTrussProfileId} (по образцу {@link #addStructureSheet} — экраны без выбранного
     *  профиля просто отсутствуют в списке, лист может остаться пустым). Тот же {@code
     *  TrussCalc.compute}, что и {@code SetupStagePanel#buildTrussSpec} — единственный
     *  источник правды. */
    private void addTrussSheet(Workbook wb, Project project) {
        Sheet sheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Фермы",
                "Сцена", "Экран", "Тип фермы", "Целевая длина, мм", "Отступ слева, мм", "Отступ справа, мм",
                "Комплект", "Сегментов, шт", "Стыков, шт", "Пальцев, шт", "Шпилек, шт",
                "Бобышек, шт (в комплекте фермы)");
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                        || scr.getRiggingTrussProfileId() == null) {
                    continue;
                }
                com.vjstb.ledscheme.model.TrussProfile profile =
                        model.getWorkspace().trussProfileById(scr.getRiggingTrussProfileId());
                com.vjstb.ledscheme.service.TrussCalc.Result r = com.vjstb.ledscheme.service.TrussCalc.compute(
                        scr, model.typeOf(scr), model.getWorkspace());
                String kitText = r.pieces() == null ? "каталог пуст"
                        : r.pieces().stream()
                                .map(p -> UiKit.fmt(p.lengthM()) + "м × " + p.count())
                                .collect(java.util.stream.Collectors.joining(", "));
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, scene.getName(), scr.getName(),
                        profile != null ? profile.getName() : "(запись удалена)", r.targetLengthMm(),
                        r.leftOffsetMm(), r.rightOffsetMm(), kitText, r.totalPieceCount(), r.jointCount(),
                        r.pinCount(), r.clipCount(), r.spigotCount());
            }
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(sheet, 12);
    }

    /** Лист «Общий список» — по прямому запросу пользователя, ОДНА сводная таблица
     *  абсолютно всего оборудования проекта сразу, а не по отдельным листам ниже
     *  (Кабинеты/Оборудование/Конструктив/Коммутация — те остаются, это ДОПОЛНИТЕЛЬНЫЙ
     *  обзорный лист, не замена). Каждая категория — те же данные, что и в
     *  соответствующем детальном листе, просто агрегированные в одну плоскую таблицу
     *  «Категория / Наименование / Кол-во / Ед.»:
     *  <ul>
     *  <li>Кабинеты — из уже посчитанной карты {@code cabinets} (см. вызывающий метод);</li>
     *  <li>Оборудование общей схемы — из {@code equipmentNodes} (та же карта, что у листа
     *  «Оборудование»);</li>
     *  <li>Такелаж — лебёдки/тали (по {@code riggingHoistTypeId}), количество = сумма
     *  {@code riggingPointsCount} экранов с этой моделью (одна лебёдка на точку подвеса);</li>
     *  <li>Конструктив — просуммированные по ВСЕМ экранам с {@code mountType == STRUCTURE}
     *  счётчики {@code StructureCalc.compute} (детализация по экранам — на листе
     *  «Конструктив»);</li>
     *  <li>Коммутация — по типу провода суммарное количество линий (детальная разбивка на
     *  куски определённой длины — на листе «Коммутация — сводная»/«Коммутация —
     *  сплайсовка»).</li>
     *  </ul>
     */
    private void addOverallEquipmentSheet(Workbook wb, Project project,
            java.util.Map<CabinetType, Integer> cabinets, java.util.Map<List<String>, Integer> equipmentNodes) {
        Sheet sheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Общий список",
                "Категория", "Наименование", "Кол-во", "Ед.");

        for (var entry : cabinets.entrySet()) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Кабинеты", entry.getKey().getName(),
                    entry.getValue(), "шт");
        }
        // Линки кабинетов (число отдельных линий питания/сигнала, которые нужно развести
        // на каждый тип кабинета) — по строке на тип, где они заданы (0 = встроено, не
        // считаем); см. лист «Кабинеты».
        for (var entry : cabinets.entrySet()) {
            CabinetType t = entry.getKey();
            if (t.getPowerConnectorsNeeded() > 0) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Линки",
                        "Линии питания: " + t.getName(), t.getPowerConnectorsNeeded() * entry.getValue(), "шт");
            }
            if (t.getSignalConnectorsNeeded() > 0) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Линки",
                        "Линии сигнала: " + t.getName(), t.getSignalConnectorsNeeded() * entry.getValue(), "шт");
            }
        }
        for (var entry : equipmentNodes.entrySet()) {
            List<String> key = entry.getKey();
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Оборудование",
                    key.get(1) + ": " + key.get(2) + " (" + key.get(0) + ")", entry.getValue(), "шт");
        }

        java.util.LinkedHashMap<String, Integer> hoists = new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                        || scr.getRiggingHoistTypeId() == null) {
                    continue;
                }
                com.vjstb.ledscheme.model.HoistType hoist =
                        model.getWorkspace().hoistTypeById(scr.getRiggingHoistTypeId());
                if (hoist != null) {
                    hoists.merge(hoist.getName(), scr.getRiggingPointsCount(), Integer::sum);
                }
            }
        }
        for (var entry : hoists.entrySet()) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Такелаж", entry.getKey(), entry.getValue(),
                    "шт");
        }

        // Фермы -- сегменты/крепёж стыков, просуммированные по каждому профилю библиотеки
        // (см. addTrussSheet за детализацией по экранам). Сегменты -- отдельная строка НА
        // КАЖДУЮ длину куска (см. trussSegmentsByLength), не одна общая сумма -- общее число
        // сегментов бесполезно для закупки, нужно знать именно сколько кусков какой длины
        // (баг-репорт 2026-09-16 "важно видеть количества по длинам сегментов"). Крепёж -- 3
        // отдельные строки, не одна: бобышки обычно уже установлены в торцах фермы заводом
        // (не закупаются отдельно), а пальцы/шпильки -- расходники, которые нужно добрать
        // (см. javadoc TrussCalc.SPIGOTS_PER_JOINT/PINS_PER_JOINT/CLIPS_PER_JOINT).
        java.util.LinkedHashMap<String, int[]> trusses = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<Double, Integer>> trussSegmentsByLength =
                new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.RIGGED
                        || scr.getRiggingTrussProfileId() == null) {
                    continue;
                }
                com.vjstb.ledscheme.model.TrussProfile profile =
                        model.getWorkspace().trussProfileById(scr.getRiggingTrussProfileId());
                if (profile == null) {
                    continue;
                }
                com.vjstb.ledscheme.service.TrussCalc.Result r = com.vjstb.ledscheme.service.TrussCalc.compute(
                        scr, model.typeOf(scr), model.getWorkspace());
                if (r.pieces() == null) {
                    continue;
                }
                trusses.merge(profile.getName(),
                        new int[]{r.pinCount(), r.clipCount(), r.spigotCount()},
                        (a, bb) -> new int[]{a[0] + bb[0], a[1] + bb[1], a[2] + bb[2]});
                java.util.LinkedHashMap<Double, Integer> byLength =
                        trussSegmentsByLength.computeIfAbsent(profile.getName(), k -> new java.util.LinkedHashMap<>());
                for (com.vjstb.ledscheme.service.CableSpecCalc.Piece piece : r.pieces()) {
                    byLength.merge(piece.lengthM(), piece.count(), Integer::sum);
                }
            }
        }
        for (var entry : trusses.entrySet()) {
            String trussName = entry.getKey();
            for (var lenEntry : trussSegmentsByLength.get(trussName).entrySet()) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Фермы",
                        trussName + " — сегментов " + UiKit.fmt(lenEntry.getKey()) + "м", lenEntry.getValue(), "шт");
            }
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Фермы", trussName + " — пальцев",
                    entry.getValue()[0], "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Фермы", trussName + " — шпилек",
                    entry.getValue()[1], "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Фермы",
                    trussName + " — бобышек (в комплекте фермы)", entry.getValue()[2], "шт");
        }

        // Рамы -- ОДНО общее число (вертикальные + перемычки + секции базы), не три строки, по
        // прямому указанию пользователя (2026-08-20, см. StructureCalc.Result#totalFrameCount
        // javadoc) -- физически один и тот же каталожный тип рамы.
        int structureFrames = 0;
        int structureCups = 0;
        int structureBolts = 0;
        int structureBallastContainers = 0;
        double structureBallastKg = 0;
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE) {
                    continue;
                }
                com.vjstb.ledscheme.service.StructureCalc.Result r = com.vjstb.ledscheme.service.StructureCalc
                        .compute(scr, model.typeOf(scr), model.getWorkspace());
                structureFrames += r.totalFrameCount();
                structureCups += r.cupCount();
                structureBolts += r.boltCount();
                structureBallastContainers += r.ballastContainerCount();
                structureBallastKg += r.requiredBallastKg();
            }
        }
        if (structureFrames + structureCups + structureBolts + structureBallastContainers > 0) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Рамы", structureFrames, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Стаканы", structureCups, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Болты", structureBolts, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Отгрузы",
                    structureBallastContainers, "шт (" + UiKit.fmt(structureBallastKg) + " кг балласта)");
        }

        java.util.LinkedHashMap<String, double[]> wires = new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (com.vjstb.ledscheme.model.SchemaEdge edge : scene.getSchemaEdges()) {
                if (!edge.hasStructuredWire()) {
                    continue;
                }
                String modeLabel = edge.getMode() == com.vjstb.ledscheme.model.SchemaMode.POWER ? "Питание"
                        : "Сигнал";
                double[] agg = wires.computeIfAbsent(modeLabel + ": " + edge.getWireType(), k -> new double[2]);
                agg[0] += edge.getWireCount();
                agg[1] += (edge.getLengthM() != null ? edge.getLengthM() : 0) * edge.getWireCount();
            }
        }
        for (var entry : wires.entrySet()) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Коммутация", entry.getKey(),
                    entry.getValue()[0], "шт линий (~" + UiKit.fmt(entry.getValue()[1]) + " м суммарно)");
        }

        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(sheet, 4);
    }

    /** Спецификация коммутации: провода/линии, подписанные структурированно (N×тип)
     *  на стрелках общих схем питания/сигнала всех сцен проекта. Два листа: «Коммутация —
     *  сводная» (минимально необходимый комплект кусков кабеля каждой длины, см.
     *  {@link #addWireTypeRows}) и «Коммутация — сплайсовка» (какие именно линии не
     *  покрылись одним куском и из чего собран их комплект — для наглядности). */
    private void addWiringSheets(Workbook wb, Project project) {
        java.util.LinkedHashMap<String, java.util.List<double[]>> powerWires = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, java.util.List<double[]>> signalWires = new java.util.LinkedHashMap<>();
        int totalEdges = 0;
        int structuredEdges = 0;

        for (Scene scene : project.getScenes()) {
            for (com.vjstb.ledscheme.model.SchemaEdge edge : scene.getSchemaEdges()) {
                totalEdges++;
                if (!edge.hasStructuredWire()) {
                    continue;
                }
                structuredEdges++;
                java.util.LinkedHashMap<String, java.util.List<double[]>> target =
                        edge.getMode() == com.vjstb.ledscheme.model.SchemaMode.POWER ? powerWires : signalWires;
                target.computeIfAbsent(edge.getWireType(), k -> new java.util.ArrayList<>())
                        .add(new double[]{edge.getLengthM() != null ? edge.getLengthM() : 0, edge.getWireCount()});
            }
        }

        Sheet purchase = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Коммутация — сводная",
                "Схема", "Тип провода", "Длина куска, м", "Кол-во, шт", "Примечание");
        Sheet splices = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Коммутация — сплайсовка",
                "Схема", "Тип провода", "Требуемая длина линии, м", "Линий, шт", "Состав комплекта");

        addWireTypeRows(purchase, splices, "Питание", powerWires);
        addWireTypeRows(purchase, splices, "Сигнал", signalWires);

        if (totalEdges > structuredEdges) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(purchase, null, null, null, null,
                    (totalEdges - structuredEdges) + " связей без структурированной подписи не учтены в подсчёте");
        }

        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(purchase, 5);
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(splices, 5);
    }

    /** Для каждого типа провода: если в библиотеке есть каталог длин катушек с таким же
     *  именем (см. model.CableLengthProfile — однородный кабель), фактическая длина каждой
     *  связи комплектуется минимально необходимым набором кусков каталога (см.
     *  service.CableSpecCalc — одним куском, либо, когда одного не хватает, несколькими
     *  через сплайсовку); иначе, если это зарегистрированный переходник (см.
     *  model.CableType) с указанной фиксированной длиной, показываем её вместо каталога
     *  (переходник не сплайсуется — это готовое изделие); иначе — тип не зарегистрирован
     *  ни в одной из двух библиотек (свободный текст), просто количество и суммарный метраж. */
    private void addWireTypeRows(Sheet purchase, Sheet splices, String modeLabel,
            java.util.LinkedHashMap<String, java.util.List<double[]>> byWireType) {
        for (var e : byWireType.entrySet()) {
            String wireType = e.getKey();
            java.util.List<double[]> lines = e.getValue();
            double totalCount = 0;
            double totalLength = 0;
            for (double[] l : lines) {
                totalCount += l[1];
                totalLength += l[0] * l[1];
            }
            com.vjstb.ledscheme.model.CableLengthProfile profile = model.cableLengthProfileByName(wireType);
            if (profile == null) {
                com.vjstb.ledscheme.model.CableType adapter = model.cableTypeByLabel(wireType);
                if (adapter != null && adapter.getFixedLengthM() != null) {
                    com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(purchase, modeLabel, wireType,
                            adapter.getFixedLengthM(), totalCount, "переходник, фиксированная длина");
                } else {
                    com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(purchase, modeLabel, wireType, null, totalCount,
                            "не зарегистрирован в библиотеке — суммарно " + UiKit.fmt(totalLength) + " м");
                }
                continue;
            }
            com.vjstb.ledscheme.service.CableSpecCalc.Breakdown breakdown =
                    com.vjstb.ledscheme.service.CableSpecCalc.breakdown(lines, profile);
            for (var byLen : breakdown.countByRoundedLengthM().entrySet()) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(purchase, modeLabel, wireType, byLen.getKey(),
                        byLen.getValue(), null);
            }
            if (breakdown.uncoveredCount() > 0) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(purchase, modeLabel, wireType, null,
                        breakdown.uncoveredCount(), "каталог длин пуст — докупите бухты вручную");
            }
            for (com.vjstb.ledscheme.service.CableSpecCalc.SpliceInfo splice : breakdown.spliced()) {
                StringBuilder kit = new StringBuilder();
                for (com.vjstb.ledscheme.service.CableSpecCalc.Piece piece : splice.pieces()) {
                    if (kit.length() > 0) {
                        kit.append(" + ");
                    }
                    kit.append(piece.count()).append('×').append(UiKit.fmt(piece.lengthM())).append(" м");
                }
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(splices, modeLabel, wireType, splice.rawLengthM(),
                        splice.lineCount(), kit.toString());
            }
        }
    }

    private void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ignored) {
            // не критично
        }
    }
}
