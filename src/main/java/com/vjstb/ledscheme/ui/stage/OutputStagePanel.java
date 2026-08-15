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
    private final JComboBox<Integer> dpiCombo = new JComboBox<>(new Integer[]{72, 150, 300});
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
        JPanel dpiRow = new JPanel(new BorderLayout(6, 0));
        dpiCombo.setSelectedItem(settings.activeProfile().getDocExportDpi());
        dpiCombo.setToolTipText("Плотность пикселей JPEG-схем (питание/сигнал экранов, блок-схема площадки,"
                + " обзор сцены) — 72 (текущий стандарт для экрана) до 300 (стандарт для печати). НЕ влияет на"
                + " PNG-маски — их размер жёстко привязан к реальному разрешению LED-панели.");
        dpiCombo.addActionListener(e -> settings.setDocExportDpi((Integer) dpiCombo.getSelectedItem()));
        dpiRow.add(new JLabel("Качество, DPI"), BorderLayout.WEST);
        dpiRow.add(dpiCombo, BorderLayout.CENTER);
        body.add(UiKit.section("Качество экспортируемых схем", dpiRow));

        body.add(UiKit.vgap(10));
        JButton generate = new JButton("Сформировать пакет документации проекта");
        generate.addActionListener(e -> generate());
        body.add(generate);

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
            // Сигнальная нагрузка на порт контроллера (Task v1.4) — тот же бэкенд, что и
            // для питания выше, просто раньше нигде не вызывался на пути экспорта.
            for (com.vjstb.ledscheme.model.SignalChain chain : scene.getSignalChains()) {
                if (model.signalChainLoadStatus(scene, chain).blocksExport()) {
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
                    "<html>В проекте есть цепочки питания или сигнала с неподтверждённым превышением ёмкости"
                            + " (разъёма кабинета — для питания, порта контроллера — для сигнала).<br>Откройте этап"
                            + " «Питание»/«Сигнал» нужной сцены, проверьте цепочки, отмеченные ⚠, и подтвердите"
                            + " кнопкой «Я знаю» (или измените коммутацию).<br>Экспорт остановлен, пока такие"
                            + " цепочки есть.</html>",
                    "Перегрузка цепочек", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File folder = resolveFolder();
        // Качество JPEG-схем (питание/сигнал/блок-схема/обзор сцены), НЕ масок —
        // см. dpiCombo/UserProfile#getDocExportDpi. 1.0 = 72dpi = прежнее поведение.
        int docExportDpi = settings.activeProfile().getDocExportDpi();
        double dpiScale = docExportDpi / 72.0;

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
        boolean kw = settings.activeProfile().isPowerUnitKw();
        report.append(String.format("Суммарная мощность: %s, суммарный вес: %s кг%n%n",
                UiKit.fmtPower(totalPower, kw), UiKit.fmt(totalWeight)));

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
                report.append(String.format("  Экранов: %d, кабинетов: %d, мощность: %s, вес: %s кг%n",
                        ss.screenCount(), ss.totalCabinetCount(), UiKit.fmtPower(ss.totalPowerW(), kw),
                        UiKit.fmt(ss.totalWeightKg())));

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
                    report.append(String.format("    Мощность: %s, вес: %s кг%n",
                            UiKit.fmtPower(st.totalPowerW(), kw), UiKit.fmt(st.totalWeightKg())));
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
                            scrPowerChains, scrSignalChains, kw, dpiScale);
                    SchemeRenderer.writeJpeg(powerImg,
                            new File(powerFolder, OutputPaths.sanitize(scr.getName() + " Сила") + ".jpg"),
                            docExportDpi);
                    jpegCount++;

                    BufferedImage signalImg = SchemeRenderer.renderImage(scr, type, false, 120, model.getWorkspace(),
                            scrPowerChains, scrSignalChains, kw, dpiScale);
                    SchemeRenderer.writeJpeg(signalImg,
                            new File(signalFolder, OutputPaths.sanitize(scr.getName() + " Сигнал") + ".jpg"),
                            docExportDpi);
                    jpegCount++;

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
                        SceneCanvasPanel overview = new SceneCanvasPanel(model, settings);
                        overview.setDetailMode(true, power, false);
                        Dimension size = overview.getPreferredSize();
                        BufferedImage img = overview.renderImage(size.width, size.height, dpiScale);
                        File target = new File(power ? powerFolder : signalFolder, "_Все экраны сцены.jpg");
                        SchemeRenderer.writeJpeg(img, target, docExportDpi);
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
                    BufferedImage img = schemaCanvas.renderImage(size.width, size.height, screensAsWiring, dpiScale);
                    String modeSuffix = schemaMode == SchemaMode.POWER ? " Сила" : " Сигнал";
                    SchemeRenderer.writeJpeg(img,
                            new File(modeFolder, OutputPaths.sanitize(scene.getName() + modeSuffix) + ".jpg"),
                            docExportDpi);
                    jpegCount++;
                }

                for (ContentCanvas c : scene.getCanvases()) {
                    BufferedImage img = PixelGridRenderer.renderCanvasMask(c, model, settings);
                    PixelGridRenderer.writePng(img,
                            new File(masksFolder, "Канвас_" + OutputPaths.sanitize(c.getName()) + "_"
                                    + img.getWidth() + "x" + img.getHeight() + ".png"));
                    maskCount++;
                }

                report.append('\n');
            }

            File reportFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_отчёт.txt");
            Files.writeString(reportFile.toPath(), report.toString(), StandardCharsets.UTF_8);

            File specFile = new File(folder, OutputPaths.sanitize(project.getName()) + "_спецификация.xlsx");
            try (Workbook specWorkbook = buildEquipmentSpecWorkbook(project)) {
                com.vjstb.ledscheme.service.SpecXlsxWriter.write(specWorkbook, specFile);
            }

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
                "Вес, кг/шт", "Вес всего, кг");
        for (var entry : cabinets.entrySet()) {
            CabinetType t = entry.getKey();
            int qty = entry.getValue();
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(cabinetsSheet, t.getName(), qty,
                    t.getPowerConsumptionW() * powerScale, t.getPowerConsumptionW() * qty * powerScale,
                    t.getWeightKg(), t.getWeightKg() * qty);
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(cabinetsSheet, 6);

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
     *  ни одного конструктива. */
    private void addStructureSheet(Workbook wb, Project project) {
        Sheet sheet = com.vjstb.ledscheme.service.SpecXlsxWriter.addSheet(wb, "Конструктив",
                "Сцена", "Экран", "Вертикальных рам, шт", "Перемычек, шт", "Секций базовых рам, шт",
                "Стаканов, шт", "Болтов, шт", "Требуемый балласт, кг", "Контейнеров балласта, шт");
        for (Scene scene : project.getScenes()) {
            for (Screen scr : scene.getScreens()) {
                if (scr.getMountType() != com.vjstb.ledscheme.model.ScreenMountType.STRUCTURE) {
                    continue;
                }
                CabinetType type = model.typeOf(scr);
                com.vjstb.ledscheme.service.StructureCalc.Result r =
                        com.vjstb.ledscheme.service.StructureCalc.compute(scr, type, model.getWorkspace());
                com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, scene.getName(), scr.getName(),
                        r.verticalFrameCount(), r.peremychkaCount(), r.baseFrameCount(), r.cupCount(),
                        r.boltCount(), r.requiredBallastKg(), r.ballastContainerCount());
            }
        }
        com.vjstb.ledscheme.service.SpecXlsxWriter.autoSizeColumns(sheet, 9);
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

        int structureVertical = 0;
        int structurePeremychka = 0;
        int structureBase = 0;
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
                structureVertical += r.verticalFrameCount();
                structurePeremychka += r.peremychkaCount();
                structureBase += r.baseFrameCount();
                structureCups += r.cupCount();
                structureBolts += r.boltCount();
                structureBallastContainers += r.ballastContainerCount();
                structureBallastKg += r.requiredBallastKg();
            }
        }
        if (structureVertical + structurePeremychka + structureBase + structureCups + structureBolts
                + structureBallastContainers > 0) {
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Вертикальные рамы",
                    structureVertical, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Перемычки", structurePeremychka,
                    "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Секции базовых рам",
                    structureBase, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Стаканы", structureCups, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Болты", structureBolts, "шт");
            com.vjstb.ledscheme.service.SpecXlsxWriter.addRow(sheet, "Конструктив", "Контейнеры балласта",
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
