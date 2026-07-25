package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.SceneStats;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.OutputPaths;
import com.vjstb.ledscheme.ui.PixelGridRenderer;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaCanvasPanel;
import com.vjstb.ledscheme.ui.SchemeRenderer;
import com.vjstb.ledscheme.ui.UiKit;
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
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Этап «Вывод»: выбор папки и формирование пакета документации проекта —
 * JPEG-схемы (питание/сигнал) всех экранов + два текстовых файла: отчёт (нагрузки/
 * веса/точки подвеса, без цепочек) и спецификация (только количество оборудования,
 * см. {@link #buildEquipmentSpec}).
 */
public class OutputStagePanel extends JPanel {

    private final AppModel model;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final JTextField folderField = new JTextField();
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
        return project != null ? OutputPaths.defaultFolder(project, null) : null;
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
        JButton generate = new JButton("Сформировать пакет документации проекта");
        generate.addActionListener(e -> generate());
        body.add(generate);

        body.add(UiKit.vgap(10));
        body.add(UiKit.muted("<html>Для каждой сцены проекта в её собственной папке будут созданы подпапки"
                + " «Сила», «Сигнал» и «Маски». В «Сила»/«Сигнал» — JPEG-схема каждого экрана сцены, общая схема"
                + " «Все экраны сцены» (по ней видно цепочки, идущие через пару экранов — на схеме одного экрана"
                + " это не видно), а также нарисованная блок-схема площадки («Общая схема питания/сигнала» из"
                + " редактора) — узлы экранов рисуются блоком или уменьшённой схемой расключения, смотря по"
                + " переключателю в Персонализации. В «Маски» — тестовые маски (Pixel Grid) всех экранов и канвасов"
                + " сцены. Плюс два текстовых файла на весь проект: <b>«…_отчёт.txt»</b> — итоговые суммарные цифры"
                + " (нагрузка, вес), затем разбор по сценам/экранам (состав, разрешение/габариты, мощность/вес,"
                + " точек подвеса) — без цепочек; <b>«…_спецификация.txt»</b> — только количество используемого"
                + " оборудования: кабинеты по типам, оборудование общей схемы по типу узла (щиты/дистрибьюторы/"
                + "контроллеры/медиасерверы/конвертеры и т.д.), спецификация коммутации (провода/линии,"
                + " подписанные N×тип на стрелках общих схем — с метражом для питания).</html>"));
        body.add(UiKit.vgap(10));
        body.add(UiKit.muted("Пресеты под медиасерверы (Resolume и т.п.) — на этапе «Генерация масок», отдельной кнопкой."));
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    /** true, если в проекте (любой сцене) есть цепочка питания с превышением ёмкости
     *  разъёма её кабинетов, ещё не подтверждённая кнопкой «Я знаю» (Task #81) —
     *  блокирует «Сформировать пакет документации», пока инженер не разберётся. */
    private boolean hasUnacknowledgedOverload(Project project) {
        for (Scene scene : project.getScenes()) {
            for (PowerChain chain : scene.getPowerChains()) {
                if (model.powerChainLoadStatus(scene, chain).blocksExport()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выберите папку вывода");
        if (chosenFolder != null) {
            fc.setCurrentDirectory(chosenFolder);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            chosenFolder = fc.getSelectedFile();
            folderField.setText(chosenFolder.getAbsolutePath());
        }
    }

    private void generate() {
        Project project = model.getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите проект", "Нет проекта", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (settings.activeProfile().isLoadTrackingEnabled() && hasUnacknowledgedOverload(project)) {
            JOptionPane.showMessageDialog(this,
                    "<html>В проекте есть цепочки питания с неподтверждённой перегрузкой"
                            + " (превышение ёмкости разъёма кабинета).<br>Откройте этап «Питание» нужной сцены,"
                            + " проверьте цепочки, отмеченные ⚠, и подтвердите кнопкой «Я знаю»"
                            + " (или измените коммутацию).<br>Экспорт остановлен, пока такие цепочки есть.</html>",
                    "Перегрузка цепочек питания", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File folder = resolveFolder();

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
        report.append(String.format("Суммарная мощность: %s Вт, суммарный вес: %s кг%n%n",
                UiKit.fmt(totalPower), UiKit.fmt(totalWeight)));

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
        try {
            for (Scene scene : project.getScenes()) {
                model.selectScene(scene);

                SceneStats ss = ScreenLogic.sceneStats(scene, model.getWorkspace());
                report.append("Сцена: ").append(scene.getName()).append('\n');
                report.append(String.format("  Экранов: %d, кабинетов: %d, мощность: %s Вт, вес: %s кг%n",
                        ss.screenCount(), ss.totalCabinetCount(), UiKit.fmt(ss.totalPowerW()), UiKit.fmt(ss.totalWeightKg())));

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
                    ScreenStats st = ScreenLogic.stats(scr, type, model.getWorkspace());
                    report.append("  Экран «").append(scr.getName()).append("»\n");
                    report.append(String.format("    Кабинет: %s, сетка: %d×%d%n",
                            type != null ? type.getName() : "—", scr.getCols(), scr.getRows()));
                    report.append(String.format("    Разрешение: %d×%d px, физический размер: %s×%s мм%n",
                            st.resolutionWidthPx(), st.resolutionHeightPx(),
                            UiKit.fmt(st.physicalWidthMm()), UiKit.fmt(st.physicalHeightMm())));
                    report.append(String.format("    Мощность: %s Вт, вес: %s кг%n",
                            UiKit.fmt(st.totalPowerW()), UiKit.fmt(st.totalWeightKg())));
                    report.append(String.format("    Точек подвеса: %d%n", scr.getRiggingPointsCount()));
                    report.append('\n');

                    // Цепочки хранятся на уровне сцены (Task #78), а не экрана — берём
                    // только те, что физически затрагивают кабинеты ЭТОГО экрана (цепочка
                    // может начинаться на другом экране сцены и продолжаться сюда). Нужны
                    // только для рендера схем ниже — сам список цепочек в отчёт не входит
                    // (см. Task v1.4: отчёт — только нагрузки/веса/точки подвеса).
                    List<PowerChain> scrPowerChains = model.powerChainsTouchingScreen(scr);
                    List<SignalChain> scrSignalChains = model.signalChainsTouchingScreen(scr);

                    BufferedImage powerImg = SchemeRenderer.renderImage(scr, type, true, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains);
                    SchemeRenderer.writeJpeg(powerImg,
                            new File(powerFolder, OutputPaths.sanitize(scr.getName()) + ".jpg"));
                    jpegCount++;

                    BufferedImage signalImg = SchemeRenderer.renderImage(scr, type, false, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains);
                    SchemeRenderer.writeJpeg(signalImg,
                            new File(signalFolder, OutputPaths.sanitize(scr.getName()) + ".jpg"));
                    jpegCount++;

                    BufferedImage maskImg = PixelGridRenderer.renderMask(scr, type, model.getWorkspace());
                    PixelGridRenderer.writePng(maskImg,
                            new File(masksFolder, OutputPaths.sanitize(scr.getName()) + "_Маска.png"));
                    maskCount++;
                }

                // Схема сцены ЦЕЛИКОМ (все экраны сразу, как «Показать все экраны
                // сцены») — если цепочка проходит через пару экранов, по отдельным
                // схемам экранов этого не видно вовсе, только по этой общей схеме.
                if (!scene.getScreens().isEmpty()) {
                    for (boolean power : new boolean[]{true, false}) {
                        SceneCanvasPanel overview = new SceneCanvasPanel(model);
                        overview.setDetailMode(true, power, false);
                        Dimension size = overview.getPreferredSize();
                        BufferedImage img = overview.renderImage(size.width, size.height);
                        File target = new File(power ? powerFolder : signalFolder, "_Все экраны сцены.jpg");
                        SchemeRenderer.writeJpeg(img, target);
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
                    SchemaCanvasPanel schemaCanvas = new SchemaCanvasPanel(model, schemaMode, settings);
                    Dimension size = schemaCanvas.getPreferredSize();
                    BufferedImage img = schemaCanvas.renderImage(size.width, size.height, screensAsWiring);
                    SchemeRenderer.writeJpeg(img, new File(modeFolder, "_Блок-схема.jpg"));
                    jpegCount++;
                }

                for (ContentCanvas c : scene.getCanvases()) {
                    BufferedImage img = PixelGridRenderer.renderCanvasMask(c, model);
                    PixelGridRenderer.writePng(img,
                            new File(masksFolder, "Канвас_" + OutputPaths.sanitize(c.getName()) + ".png"));
                    maskCount++;
                }

                report.append('\n');
            }

            File reportFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_отчёт.txt");
            Files.writeString(reportFile.toPath(), report.toString(), StandardCharsets.UTF_8);

            File specFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_спецификация.txt");
            Files.writeString(specFile.toPath(), buildEquipmentSpec(project), StandardCharsets.UTF_8);

            int answer = JOptionPane.showConfirmDialog(this,
                    "Готово.\nJPEG-схем сохранено: " + jpegCount + "\nМасок сохранено: " + maskCount
                            + "\nОтчёт: " + reportFile.getName() + "\nСпецификация: " + specFile.getName()
                            + "\n\nОткрыть папку?",
                    "Пакет документации сформирован", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                openFolder(folder);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка формирования пакета: " + ex.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            model.selectScene(origScene);
            model.selectScreen(origScreen);
        }
    }

    /** Второй файл пакета документации — "сколько чего понадобится": кабинеты (по
     *  типу, из фактического состава экранов), оборудование общей схемы (по типу
     *  узла + подписи, БЕЗ узлов-экранов — это ссылки, не отдельное оборудование)
     *  и спецификация коммутации (см. {@link #buildWiringSpec}). Никаких нагрузок/
     *  весов/цепочек здесь — это в отчёте (см. generate()) — тут только количества. */
    private String buildEquipmentSpec(Project project) {
        StringBuilder sb = new StringBuilder();
        sb.append("Спецификация оборудования по проекту: ").append(project.getName()).append('\n');
        sb.append("Дата: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("\n\n");

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
        sb.append("=== КАБИНЕТЫ ===\n");
        if (cabinets.isEmpty()) {
            sb.append("(нет активных кабинетов)\n");
        }
        for (var entry : cabinets.entrySet()) {
            CabinetType t = entry.getKey();
            int qty = entry.getValue();
            sb.append(String.format("  %s — %d шт. · %s Вт/шт (%s Вт) · %s кг/шт (%s кг)%n",
                    t.getName(), qty, UiKit.fmt(t.getPowerConsumptionW()), UiKit.fmt(t.getPowerConsumptionW() * qty),
                    UiKit.fmt(t.getWeightKg()), UiKit.fmt(t.getWeightKg() * qty)));
        }
        sb.append('\n');

        // Оборудование общей схемы (щиты/дистрибьюторы/конвертеры/медиасерверы/
        // контроллеры/прочее) — узлы-экраны не считаются: это ссылка на уже
        // посчитанный выше экран, а не отдельная физическая единица оборудования.
        // Группируется по (режим схемы, тип узла, подпись) — одинаково подписанные
        // узлы одного типа считаются одной моделью оборудования.
        java.util.Map<String, Integer> equipmentNodes = new java.util.LinkedHashMap<>();
        for (Scene scene : project.getScenes()) {
            for (com.vjstb.ledscheme.model.SchemaNode n : scene.getSchemaNodes()) {
                if (n.getType() == com.vjstb.ledscheme.model.SchemaNodeType.SCREEN) {
                    continue;
                }
                String modeLabel = n.getMode() == com.vjstb.ledscheme.model.SchemaMode.POWER ? "Питание" : "Сигнал";
                String label = n.getLabel() == null || n.getLabel().isBlank() ? n.getType().getLabel() : n.getLabel();
                String key = modeLabel + " · " + n.getType().getLabel() + " · " + label;
                equipmentNodes.merge(key, 1, Integer::sum);
            }
        }
        sb.append("=== ОБОРУДОВАНИЕ ОБЩЕЙ СХЕМЫ ===\n");
        if (equipmentNodes.isEmpty()) {
            sb.append("(в общей схеме нет узлов оборудования)\n");
        }
        for (var entry : equipmentNodes.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" — ").append(entry.getValue()).append(" шт.\n");
        }
        sb.append('\n');

        sb.append(buildWiringSpec(project));
        return sb.toString();
    }

    /** Спецификация коммутации: провода/линии, подписанные структурированно (N×тип)
     *  на стрелках общих схем питания/сигнала всех сцен проекта — сгруппированы по типу,
     *  для питания также суммируется метраж (N линий × длина каждой). */
    private String buildWiringSpec(Project project) {
        java.util.LinkedHashMap<String, double[]> powerWires = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, double[]> signalWires = new java.util.LinkedHashMap<>();
        int totalEdges = 0;
        int structuredEdges = 0;

        for (Scene scene : project.getScenes()) {
            for (com.vjstb.ledscheme.model.SchemaEdge edge : scene.getSchemaEdges()) {
                totalEdges++;
                if (!edge.hasStructuredWire()) {
                    continue;
                }
                structuredEdges++;
                java.util.LinkedHashMap<String, double[]> target =
                        edge.getMode() == com.vjstb.ledscheme.model.SchemaMode.POWER ? powerWires : signalWires;
                double[] agg = target.computeIfAbsent(edge.getWireType(), k -> new double[2]);
                agg[0] += edge.getWireCount();
                if (edge.getLengthM() != null) {
                    agg[1] += edge.getWireCount() * edge.getLengthM();
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== СПЕЦИФИКАЦИЯ КОММУТАЦИИ (провода/линии по общим схемам) ===\n");
        if (powerWires.isEmpty() && signalWires.isEmpty()) {
            sb.append("(нет связей с подписью вида N×тип — задайте их в общей схеме питания/сигнала: клик по"
                    + " чипу «+ подпись» на стрелке)\n");
        } else {
            if (!powerWires.isEmpty()) {
                sb.append("Питание:\n");
                for (var e : powerWires.entrySet()) {
                    String lenPart = e.getValue()[1] > 0 ? " · " + UiKit.fmt(e.getValue()[1]) + " м суммарно" : "";
                    sb.append(String.format("  %s — %s лин.%s%n", e.getKey(), UiKit.fmt(e.getValue()[0]), lenPart));
                }
            }
            if (!signalWires.isEmpty()) {
                sb.append("Сигнал:\n");
                for (var e : signalWires.entrySet()) {
                    sb.append(String.format("  %s — %s лин.%n", e.getKey(), UiKit.fmt(e.getValue()[0])));
                }
            }
            if (totalEdges > structuredEdges) {
                sb.append("(").append(totalEdges - structuredEdges)
                        .append(" связей без структурированной подписи не учтены в подсчёте)\n");
            }
        }
        sb.append('\n');
        return sb.toString();
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
