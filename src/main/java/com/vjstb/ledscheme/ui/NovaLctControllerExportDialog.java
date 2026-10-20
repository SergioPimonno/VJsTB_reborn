package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NovaLctCombineHelper;
import com.vjstb.ledscheme.service.NovaLctControllerResolver;
import com.vjstb.ledscheme.service.NovaLctScrWriter;
import com.vjstb.ledscheme.service.NovaLctScreenOrder;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.settings.SettingsManager;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Экспорт шаблона расключения в NovaLCT (.scr) от КОНТРОЛЛЕРА — единственный
 *  пункт экспорта в NovaLCT (старый, привязанный к одному экрану,
 *  {@code NovaLctExportDialog}, удалён — этот диалог полностью его покрывает,
 *  см. ниже, плюс умеет контроллер, обслуживающий несколько экранов сцены сразу,
 *  см. {@link NovaLctControllerResolver}).
 *
 * <p>Если контроллер трогает РОВНО один экран — используется тот же самый,
 * УЖЕ подтверждённый {@link NovaLctScrWriter#write} напрямую (см. class-javadoc
 * {@code NovaLctScrWriter} про 100% побайтовое совпадение с реальной NovaLCT),
 * а не {@link NovaLctScrWriter#writeStandardCombined} — это гарантирует, что
 * самый частый случай (один контроллер — один экран) идёт по уже проверенному
 * пути, а не через дополнительную (пусть и покрытую тестами) объединяющую логику.
 *
 * <p>Если экранов несколько — {@link LctPresetMasterDialog} даёт разместить их на
 * комбинированной NovaLCT-сетке (см. его class-javadoc — заменил собой пикер
 * {@code ContentCanvas}, тот был не тем инструментом для этой задачи), после чего
 * пользователь выбирает: объединить их в одну виртуальную сетку (см.
 * {@link NovaLctCombineHelper#combine}, поддерживает экраны разного размера —
 * "дыры" между ними получают {@code card=0xFF} ("blank"), см. javadoc метода) —
 * рекомендуемый, подтверждённый и на вырожденном случае, и на реальном образце
 * {@code 111.scr} путь — либо экспортировать как несколько ОТДЕЛЬНЫХ экранов в
 * одном файле (см. {@link NovaLctCombineHelper#splitSeparateGrouped} /
 * {@link NovaLctScrWriter#writeStandardMultiScreen}) — сам писатель ПОДТВЕРЖДЁН
 * побайтово 4 реальными образцами (2026-08-09). В этом режиме экраны МОЖНО
 * группировать (Ctrl+клик в {@link LctPresetMasterDialog} + «Группировать») —
 * несколько экранов проекта тогда сливаются в ОДИН из нескольких NovaLCT-экранов
 * (тем же blank-механизмом, что и Combine, но в границах группы, а не всей сетки)
 * — реальный кейс, ранее вообще не поддерживавшийся ({@code splitSeparate} мог
 * только 1:1). Экран без явной группы остаётся отдельным NovaLCT-экраном, как и
 * раньше. */
public final class NovaLctControllerExportDialog {

    private NovaLctControllerExportDialog() {
    }

    public static void showExportFlow(Frame owner, AppModel model, SettingsManager settings) {
        Scene scene = model.getCurrentScene();
        List<ControllerInstance> controllers = scene != null ? model.controllersInScene(scene) : List.of();
        if (controllers.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "В текущей сцене нет ни одного контроллера.",
                    "Экспорт NovaLCT для контроллера", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<ControllerInstance> controllerCombo = new JComboBox<>(controllers.toArray(new ControllerInstance[0]));
        controllerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                if (value instanceof ControllerInstance ci) {
                    // Имя типа-источника — только для показа (карты/порты уже
                    // заморожены на самом экземпляре, см. class-javadoc ControllerInstance).
                    String typeName = model.getEquipmentPresets().stream()
                            .filter(p -> p.getId().equals(ci.getControllerTypeId()))
                            .map(com.vjstb.ledscheme.model.EquipmentPreset::getName).findFirst().orElse("?");
                    int cards = ci.getCards().size();
                    String cardsSuffix = cards > 0 ? " (" + cards + " " + cardWord(cards) + ")" : "";
                    setText(ci.getLabel() + " — " + typeName + cardsSuffix);
                }
                return this;
            }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Контроллер для экспорта:"), BorderLayout.NORTH);
        panel.add(controllerCombo, BorderLayout.CENTER);
        // ВАЖНО: JOptionPane пакует диалог ОДИН раз при показе, в отличие от обычного
        // JDialog/GuideDialog -- UiKit.bindHtmlWrapWidth рассчитан на живой, уже
        // видимый контейнер (ставит текст асинхронно через invokeLater/слушатель
        // resize), здесь на момент паковки диалога подпись ещё пустая, отчего реальная
        // (тогда уже более высокая) подпись наезжает на комбобокс поверх него (баг-репорт
        // с наложением текста). Ширина обёртки фиксирована заранее (360-24=336px) —
        // async-подгонка тут не нужна, задаём HTML сразу с готовой шириной.
        JLabel hint = new JLabel("<html><body style='width:336px'>Экспортирует ВСЁ, что расключено через"
                + " выбранный контроллер, — с любого экрана сцены, а не только текущего.</body></html>");
        panel.add(hint, BorderLayout.SOUTH);

        if (JOptionPane.showConfirmDialog(owner, panel, "Экспорт NovaLCT для контроллера",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        ControllerInstance controller = (ControllerInstance) controllerCombo.getSelectedItem();
        if (controller == null) {
            return;
        }

        List<NovaLctControllerResolver.CabinetRec> recs = NovaLctControllerResolver.resolve(scene, controller, model);
        if (recs.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                    "У этого контроллера нет ни одной расключённой сигнальной цепочки — экспортировать нечего.",
                    "Экспорт NovaLCT для контроллера", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Set<Screen> involvedScreens = new LinkedHashSet<>();
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            involvedScreens.add(r.sourceScreen());
        }

        byte[] data;
        String defaultName;
        // Тип кабинета известен однозначно только когда затронут РОВНО один экран —
        // для объединённого/раздельного мультиэкранного экспорта разные экраны могут
        // быть разных типов, предлагать скачать .rcfgx тогда было бы гаданием.
        CabinetType exportedCabinetType = null;
        if (involvedScreens.size() == 1) {
            Screen screen = involvedScreens.iterator().next();
            boolean subScreen = NovaLctControllerResolver.controllersWiringScreen(scene, screen, model).size() > 1;
            // Вид записи по умолчанию — по критериям; если экран определился как Complex, показываем
            // причины и даём выбрать (Complex / обычный). Кусок экрана (расключён несколькими
            // контроллерами) выбирает вид сам — там прежнее предупреждение.
            Boolean forceComplex = null;
            if (subScreen) {
                if (confirmStandardSizeMismatchDeclined(owner, involvedScreens, recs, scene, model, false)) {
                    return;
                }
            } else {
                Map<Screen, Boolean> kinds = askScreenKinds(owner, involvedScreens, recs, model, true);
                if (kinds == null) {
                    return; // отменено пользователем
                }
                forceComplex = kinds.get(screen);
            }
            if (subScreen) {
                // Экран расключён НЕСКОЛЬКИМИ контроллерами — этот пишет не полную
                // сетку экрана (тогда файл ушёл бы с «дырами» и NovaLCT его
                // отклоняет), а свой пере-индексированный кусок. Предупреждение о
                // «нерасключённых» тут не показываем — остальное экрана на другом
                // контроллере, это ожидаемо, а не ошибка.
                int[] placement = askSubScreenPlacement(owner, screen, recs, model);
                if (placement == null) {
                    return;
                }
                data = NovaLctScrWriter.writeResolvedSubScreen(screen, recs, model.getWorkspace(),
                        placement[0], placement[1]);
            } else {
                if (hasUnwiredWarningDeclined(owner, scene, controller, model)) {
                    return;
                }
                // НЕ NovaLctScrWriter.write(screen, ...) — тот резолвит цепочки заново
                // через ScreenLogic.cardAndLocalPort, который для контроллера, не
                // первого в сцене, роняет все цепочки (scene-wide номер порта > его
                // локальной ёмкости) и отдаёт файл без кабинетных записей — реальная
                // NovaLCT такой .scr отклоняет при импорте. Передаём уже правильно
                // разрешённые записи резолвера (см. NovaLctScrWriter.writeForResolvedScreen).
                data = NovaLctScrWriter.writeForResolvedScreen(screen, recs, model.getWorkspace(), forceComplex);
            }
            defaultName = screen.getName();
            exportedCabinetType = model.typeOf(screen);
        } else {
            // Экспорт одного контроллера — ВСЕГДА один .scr (импорт в NovaLCT
            // "Load from File" заменяет всю конфигурацию, второй файл затёр бы
            // первый). Затронутые экраны делятся: обычные (ровные) идут прежним
            // ветвлением — сшить в одно полотно (Combine) либо разными screen-ами
            // (Separate); каждый Complex-экран даёт свой Complex-блок; всё
            // собирается в ОДИН мультиэкранный .scr (writeMixedMultiScreen —
            // подтверждён побайтово реальными образцами NovaLCT). Complex-экранов
            // нет — прежний путь без изменений.
            //
            // Standard хранит размер кабинета НА ПОРТ: экран с кабинетами разных размеров
            // остаётся Standard, пока на каждом порту размер один (баг-репорт: R1 с разными
            // типами уходил в Complex, хотя на каждом порту кабинеты одинаковые). Блок
            // Combine пишет один размер на всё полотно, поэтому режим выбирается ДО деления
            // на Standard/Complex: «по портам» — для раздельных экранов, строгая проверка
            // «один размер на экран» — для Combine.
            List<Screen> complexScreens = new ArrayList<>();
            List<Screen> standardScreens = new ArrayList<>();
            partitionByExportKind(involvedScreens, recs, model, true, standardScreens, complexScreens);
            ExportMode mode = null;
            if (standardScreens.size() > 1) {
                mode = pickExportMode(owner, standardScreens.size(), complexScreens);
                if (mode == null) {
                    return; // отменено пользователем
                }
                if (mode == ExportMode.SEPARATE && !confirmMultiScreenExperimentalWarning(owner)) {
                    return;
                }
                if (mode == ExportMode.COMBINE) {
                    complexScreens.clear();
                    standardScreens.clear();
                    partitionByExportKind(involvedScreens, recs, model, false, standardScreens, complexScreens);
                }
            }
            // Экраны, определившиеся как Complex, — показываем причины и даём выбрать вид записи
            // (Complex / обычный) для каждого экрана.
            if (!complexScreens.isEmpty()) {
                Map<Screen, Boolean> kinds = askScreenKinds(owner, involvedScreens, recs, model,
                        mode != ExportMode.COMBINE);
                if (kinds == null) {
                    return; // отменено пользователем
                }
                standardScreens.clear();
                complexScreens.clear();
                for (Screen s : involvedScreens) {
                    (Boolean.TRUE.equals(kinds.get(s)) ? complexScreens : standardScreens).add(s);
                }
                // Режим не спрашивали (обычных было меньше двух), а после выбора пользователя их стало
                // больше — теперь спрашиваем.
                if (mode == null && standardScreens.size() > 1) {
                    mode = pickExportMode(owner, standardScreens.size(), complexScreens);
                    if (mode == null) {
                        return; // отменено пользователем
                    }
                    if (mode == ExportMode.SEPARATE && !confirmMultiScreenExperimentalWarning(owner)) {
                        return;
                    }
                }
            }

            if (complexScreens.isEmpty()) {
                int[] cab = firstCabinetResolution(standardScreens, model);
                LctPresetMasterDialog.Result placement = LctPresetMasterDialog.showDialog(owner,
                        new ArrayList<>(standardScreens), recs, cab[0], cab[1]);
                if (placement == null) {
                    return; // отменено пользователем
                }
                if (hasUnwiredWarningDeclined(owner, scene, controller, model)) {
                    return;
                }
                if (mode == ExportMode.COMBINE) {
                    NovaLctCombineHelper.CombineResult combined = NovaLctCombineHelper.combine(
                            placement.slots(), placement.cols(), placement.rows(), recs, model);
                    data = NovaLctScrWriter.writeStandardCombined(combined);
                } else {
                    // splitSeparateGrouped -- ОБЩИЙ случай, включающий старое 1:1 поведение
                    // splitSeparate как частный (пустая/нулевая карта групп) -- см. её javadoc.
                    List<NovaLctScreenOrder.Entry> entries = new ArrayList<>();
                    for (NovaLctCombineHelper.LabeledBlock lb : NovaLctCombineHelper.splitSeparateGroupedLabeled(
                            placement.slots(), placement.groupIdByScreenId(), recs, model)) {
                        entries.add(new NovaLctScreenOrder.Entry(lb.label(),
                                NovaLctScrWriter.MixedScreen.of(lb.block())));
                    }
                    // Номера Screen1..N назначает пользователь (по умолчанию -- авто, по карте/порту).
                    List<NovaLctScreenOrder.Entry> ordered = entries.size() >= 2
                            ? NovaLctScreenOrderDialog.showDialog(owner, entries)
                            : entries;
                    if (ordered == null) {
                        return; // отменено пользователем
                    }
                    List<NovaLctScrWriter.ScreenBlock> blocks = new ArrayList<>();
                    for (NovaLctScreenOrder.Entry e : ordered) {
                        blocks.add(e.screen().standard());
                    }
                    data = NovaLctScrWriter.writeStandardMultiScreen(blocks);
                }
                defaultName = controller.getLabel();
            } else {
                if (hasUnwiredWarningDeclined(owner, scene, controller, model)) {
                    return;
                }
                List<NovaLctScreenOrder.Entry> mixed = new ArrayList<>();
                if (standardScreens.size() == 1) {
                    mixed.add(new NovaLctScreenOrder.Entry(standardScreens.get(0).getName(),
                            NovaLctScrWriter.MixedScreen.of(NovaLctScrWriter.resolvedStandardScreen(
                                    standardScreens.get(0), recs, model.getWorkspace()))));
                } else if (standardScreens.size() > 1) {
                    int[] cab = firstCabinetResolution(standardScreens, model);
                    java.util.Set<Screen> keep = new java.util.HashSet<>(standardScreens);
                    List<NovaLctControllerResolver.CabinetRec> recsStd = new ArrayList<>();
                    for (NovaLctControllerResolver.CabinetRec r : recs) {
                        if (keep.contains(r.sourceScreen())) {
                            recsStd.add(r);
                        }
                    }
                    LctPresetMasterDialog.Result placement = LctPresetMasterDialog.showDialog(owner,
                            new ArrayList<>(standardScreens), recsStd, cab[0], cab[1]);
                    if (placement == null) {
                        return;
                    }
                    if (mode == ExportMode.COMBINE) {
                        NovaLctCombineHelper.CombineResult combined = NovaLctCombineHelper.combine(
                                placement.slots(), placement.cols(), placement.rows(), recsStd, model);
                        StringBuilder names = new StringBuilder();
                        for (Screen s : standardScreens) {
                            names.append(names.length() > 0 ? " + " : "").append(s.getName());
                        }
                        mixed.add(new NovaLctScreenOrder.Entry(names.toString(),
                                NovaLctScrWriter.MixedScreen.of(NovaLctScrWriter.standardBlock(combined))));
                    } else {
                        for (NovaLctCombineHelper.LabeledBlock lb : NovaLctCombineHelper.splitSeparateGroupedLabeled(
                                placement.slots(), placement.groupIdByScreenId(), recsStd, model)) {
                            mixed.add(new NovaLctScreenOrder.Entry(lb.label(),
                                    NovaLctScrWriter.MixedScreen.of(lb.block())));
                        }
                    }
                }
                for (Screen s : complexScreens) {
                    mixed.add(new NovaLctScreenOrder.Entry(s.getName(),
                            NovaLctScrWriter.MixedScreen.of(
                                    NovaLctScrWriter.resolvedComplexScreen(s, recs, model.getWorkspace()))));
                }
                // Номера Screen1..N назначает пользователь (по умолчанию -- авто, по карте/порту);
                // Complex-экраны на сетке не размещаются, поэтому видны именно здесь.
                List<NovaLctScreenOrder.Entry> orderedMixed = mixed.size() >= 2
                        ? NovaLctScreenOrderDialog.showDialog(owner, mixed)
                        : mixed;
                if (orderedMixed == null) {
                    return; // отменено пользователем
                }
                List<NovaLctScrWriter.MixedScreen> mixedScreens = new ArrayList<>();
                for (NovaLctScreenOrder.Entry e : orderedMixed) {
                    mixedScreens.add(e.screen());
                }
                data = NovaLctScrWriter.writeMixedMultiScreen(mixedScreens, true);
                defaultName = controller.getLabel();
            }
        }

        saveToFile(owner, data, defaultName);

        if (exportedCabinetType != null) {
            int rc = JOptionPane.showConfirmDialog(owner,
                    "Также скачать файл настроек приёмной карты (.rcfgx) для «" + exportedCabinetType.getName()
                            + "»?",
                    "Экспорт NovaLCT для контроллера", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (rc == JOptionPane.YES_OPTION) {
                CabinetConfigPickerDialog.showForType(owner, model, settings, exportedCabinetType);
            }
        }
    }

    /** Диалог размещения куска экрана, расключённого несколькими контроллерами
     *  (см. {@link NovaLctScrWriter#writeResolvedSubScreen}). {@code null} —
     *  пользователь отменил. Возвращает {@code {offsetXPx, offsetYPx}}:
     *  {@code {0, 0}} для режима «отдельный screen», иначе — введённые
     *  пользователем (по умолчанию авто {@code minCol×cabW} / {@code minRow×cabH}). */
    private static int[] askSubScreenPlacement(Frame owner, Screen screen,
            List<NovaLctControllerResolver.CabinetRec> recs, AppModel model) {
        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxCol = 0;
        int maxRow = 0;
        for (NovaLctControllerResolver.CabinetRec r : recs) {
            if (r.sourceScreen() != screen) {
                continue;
            }
            minCol = Math.min(minCol, r.col());
            maxCol = Math.max(maxCol, r.col());
            minRow = Math.min(minRow, r.row());
            maxRow = Math.max(maxRow, r.row());
        }
        CabinetType type = model.typeOf(screen);
        int cabW = type != null && type.getResolutionWidth() > 0 ? type.getResolutionWidth() : 128;
        int cabH = type != null && type.getResolutionHeight() > 0 ? type.getResolutionHeight() : 128;
        int autoX = minCol * cabW;
        int autoY = minRow * cabH;
        int wCabs = maxCol - minCol + 1;
        int hCabs = maxRow - minRow + 1;

        javax.swing.JRadioButton separateBtn = new javax.swing.JRadioButton(
                "Отдельный screen (Coordinate X/Y = 0)", true);
        javax.swing.JRadioButton keepBtn = new javax.swing.JRadioButton(
                "Сохранить положение куска на канвасе NovaLCT");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        group.add(separateBtn);
        group.add(keepBtn);

        javax.swing.JSpinner xSpinner =
                new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(autoX, 0, 1_000_000, 1));
        javax.swing.JSpinner ySpinner =
                new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(autoY, 0, 1_000_000, 1));
        xSpinner.setEnabled(false);
        ySpinner.setEnabled(false);
        keepBtn.addItemListener(e -> {
            xSpinner.setEnabled(keepBtn.isSelected());
            ySpinner.setEnabled(keepBtn.isSelected());
        });

        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(3, 3, 3, 3);
        panel.add(new JLabel("<html><body style='width:380px'>Экран «" + screen.getName()
                + "» расключён несколькими контроллерами. Этот контроллер держит блок "
                + wCabs + "×" + hCabs + " кабинетов (столбцы " + (minCol + 1) + "–" + (maxCol + 1)
                + ", ряды " + (minRow + 1) + "–" + (maxRow + 1)
                + "). В шаблон пишется только он, пере-индексированный в собственную сетку."
                + "</body></html>"), c);
        c.gridy++;
        panel.add(separateBtn, c);
        c.gridy++;
        panel.add(keepBtn, c);
        c.gridwidth = 1;
        c.gridy++;
        panel.add(new JLabel("Coordinate X, px:"), c);
        c.gridx = 1;
        panel.add(xSpinner, c);
        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Coordinate Y, px:"), c);
        c.gridx = 1;
        panel.add(ySpinner, c);

        if (JOptionPane.showConfirmDialog(owner, panel, "Экспорт NovaLCT — часть экрана",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        if (separateBtn.isSelected()) {
            return new int[]{0, 0};
        }
        return new int[]{((Number) xSpinner.getValue()).intValue(), ((Number) ySpinner.getValue()).intValue()};
    }

    /** Разрешение кабинета ({@code {w, h}} px) первого экрана списка с непустым
     *  типом — для сетки {@link LctPresetMasterDialog}. {@code {128, 128}}, если
     *  тип нигде не задан. */
    private static int[] firstCabinetResolution(List<Screen> screens, AppModel model) {
        for (Screen s : screens) {
            CabinetType t = model.typeOf(s);
            if (t != null && t.getResolutionWidth() > 0) {
                return new int[]{t.getResolutionWidth(), t.getResolutionHeight()};
            }
        }
        return new int[]{128, 128};
    }

    private enum ExportMode { COMBINE, SEPARATE }

    /** Диалог «как прописывать экраны» ({@link NovaLctScreenKindDialog}): для каждого затронутого
     *  экрана — результат автоопределения, причины Complex и выбор пользователя. Без единого
     *  Complex-экрана диалог не показывается (все — по критериям, «обычный»). {@code perPort} —
     *  размер кабинета сверяется по портам (раздельные экраны/один экран), иначе — один размер на
     *  весь экран (Combine). Возвращает «экран → прописать как Complex» либо {@code null} при отмене. */
    private static Map<Screen, Boolean> askScreenKinds(Frame owner, Set<Screen> screens,
            List<NovaLctControllerResolver.CabinetRec> recs, AppModel model, boolean perPort) {
        List<NovaLctScreenKindDialog.Row> rows = new ArrayList<>();
        Map<Screen, Boolean> auto = new LinkedHashMap<>();
        boolean anyComplex = false;
        for (Screen s : screens) {
            List<String> reasons = NovaLctScrWriter.complexReasons(s, perPort ? recs : null, model.getWorkspace());
            boolean complex = !reasons.isEmpty();
            boolean hasType = model.getWorkspace().cabinetTypeById(s.getCabinetTypeId()) != null;
            anyComplex |= complex;
            auto.put(s, complex);
            rows.add(new NovaLctScreenKindDialog.Row(s, complex, reasons, hasType));
        }
        if (!anyComplex) {
            return auto;
        }
        return NovaLctScreenKindDialog.showDialog(owner, rows);
    }

    /** Делит затронутые экраны на обычные (Standard) и Complex. {@code perPort} — Standard
     *  допускает разные размеры кабинетов на РАЗНЫХ портах (раздельные экраны), иначе — один
     *  размер на весь экран (Combine). */
    private static void partitionByExportKind(Set<Screen> screens,
            List<NovaLctControllerResolver.CabinetRec> recs, AppModel model, boolean perPort,
            List<Screen> standardOut, List<Screen> complexOut) {
        for (Screen s : screens) {
            boolean complex = perPort
                    ? NovaLctScrWriter.isComplexExport(s, recs, model.getWorkspace())
                    : NovaLctScrWriter.isComplexExport(s, model.getWorkspace());
            (complex ? complexOut : standardOut).add(s);
        }
    }

    /** Выбор режима для контроллера с несколькими затронутыми экранами —
     *  {@code null}, если пользователь отменил. */
    private static ExportMode pickExportMode(Frame owner, int screenCount, List<Screen> complexScreens) {
        javax.swing.JRadioButton combineBtn = new javax.swing.JRadioButton(
                "Объединить в 1 экран (по размещению в канвасе) — рекомендуется", true);
        javax.swing.JRadioButton separateBtn = new javax.swing.JRadioButton(
                "Отдельными экранами в одном файле");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        group.add(combineBtn);
        group.add(separateBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        // См. комментарий у аналогичного места в showExportFlow -- bindHtmlWrapWidth
        // не годится для JOptionPane (пакуется один раз, до того как async-подгонка
        // текста успевает сработать), отчего радиокнопки физически не помещались в
        // выделенную область и были невидимы (баг-репорт: "кнопки Combine/Separate
        // вообще не видно"). Ширина обёртки фиксирована заранее (380-24=356px).
        // Баг-репорт: «обслуживает 3 экрана, когда 4» — считались только обычные экраны, а
        // Complex-экран (на сетке не размещается, пишется отдельным экраном) выпадал из счёта.
        StringBuilder complexNote = new StringBuilder();
        if (!complexScreens.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Screen s : complexScreens) {
                names.append(names.length() > 0 ? ", " : "").append(escapeHtml(s.getName()));
            }
            complexNote.append("<br><br>Ещё ").append(complexScreens.size()).append(" экран(а) — Complex (")
                    .append(names).append("): он не размещается на сетке и всегда пишется отдельным экраном"
                            + " NovaLCT. Номера Screen для всех экранов — на следующих шагах.");
        }
        JLabel hint = new JLabel("<html><body style='width:356px'>Контроллер обслуживает "
                + (screenCount + complexScreens.size())
                + " экрана(ов) сразу" + (complexScreens.isEmpty() ? "" : " (" + screenCount + " обычных)")
                + " — как собрать их в один файл NovaLCT?" + complexNote + "</body></html>");
        panel.add(hint, BorderLayout.NORTH);
        JPanel radios = new JPanel(new java.awt.GridLayout(0, 1));
        radios.add(combineBtn);
        radios.add(separateBtn);
        panel.add(radios, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(owner, panel, "Экспорт NovaLCT для контроллера — режим",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        return separateBtn.isSelected() ? ExportMode.SEPARATE : ExportMode.COMBINE;
    }

    /** true — пользователь подтвердил и хочет продолжить; false — отменил. Сам бинарный
     *  формат (см. {@link NovaLctScrWriter#writeStandardMultiScreen}) и группировка
     *  (см. {@link NovaLctCombineHelper#splitSeparateGrouped}) проверены на реальных
     *  файлах NovaLCT/юнит-тестами — предупреждение здесь не про формат, а про то, что
     *  сам ЭТОТ путь целиком (UI-диалог → группировка → запись файла) прогнан через
     *  реальную загрузку в NovaLCT меньше раз, чем Combine — стоит перепроверить
     *  результат в NovaLCT перед боевым использованием. */
    private static boolean confirmMultiScreenExperimentalWarning(Frame owner) {
        int rc = JOptionPane.showConfirmDialog(owner,
                "<html><body style='width:360px'><b>Каждый экран проекта — отдельный NovaLCT-экран</b>,"
                        + " если вы явно не объединили несколько из них в группу (Ctrl+клик на сетке размещения +"
                        + " «Группировать») — тогда группа станет ОДНИМ NovaLCT-экраном."
                        + "<br><br>Формат файла подтверждён побайтово реальными образцами NovaLCT, но этот путь"
                        + " целиком (включая группировку) стоит один раз перепроверить реальной загрузкой в"
                        + " NovaLCT, прежде чем полагаться на него в боевой работе."
                        + "<br><br>Продолжить?</body></html>",
                "Экспорт NovaLCT — отдельные экраны", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return rc == JOptionPane.YES_OPTION;
    }

    /** true — пользователь увидел предупреждение и ОТКАЗАЛСЯ продолжать. Предупреждение
     *  показывается, когда экран уходит в Complex только потому, что кабинеты с галочкой
     *  «прописывать как стандартный кабинет» (см. {@code CabinetType#isExportAsStandardCabinet})
     *  отличаются по разрешению от типа экрана: Standard хранит один размер на порт, а не
     *  на кабинет. Просто явно говорим об этом, а не молча меняем формат файла. */
    private static boolean confirmStandardSizeMismatchDeclined(Frame owner, Set<Screen> screens,
            List<NovaLctControllerResolver.CabinetRec> recs, Scene scene, AppModel model,
            boolean separatePerPort) {
        StringBuilder details = new StringBuilder();
        for (Screen s : screens) {
            // Размер на порт (разные порты — разные размеры) умеют одноэкранный экспорт целого
            // экрана одним контроллером и раздельные экраны ({@code separatePerPort});
            // Combine и экспорт куска экрана пишут один размер на блок — там строгая проверка
            // «одно разрешение на экран».
            boolean perPort = screens.size() == 1
                    ? NovaLctControllerResolver.controllersWiringScreen(scene, s, model).size() <= 1
                    : separatePerPort;
            List<String> names = ScreenLogic.standardSizeMismatchTypeNames(s, model.typeOf(s), model.getWorkspace(),
                    perPort ? NovaLctScrWriter.portKeysOf(s, recs) : null);
            if (!names.isEmpty()) {
                details.append("<br>• экран «").append(escapeHtml(s.getName())).append("»: ")
                        .append(escapeHtml(String.join(", ", names)));
            }
        }
        if (details.length() == 0) {
            return false;
        }
        int rc = JOptionPane.showConfirmDialog(owner,
                "<html><body style='width:380px'><b>Экран будет записан как Complex Screen.</b>"
                        + "<br><br>Кабинеты, помеченные «прописывать как стандартный кабинет», имеют другое"
                        + " разрешение, а Standard Screen хранит один размер на порт (и один на блок при"
                        + " объединении/раздельных экранах), а не на кабинет:" + details
                        + "<br><br>Продолжить экспорт как Complex?</body></html>",
                "Экспорт NovaLCT — разные размеры кабинетов", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return rc != JOptionPane.YES_OPTION;
    }

    private static String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** true — пользователь увидел предупреждение о неполном расключении и ОТКАЗАЛСЯ
     *  продолжать (вызывающий код должен прервать экспорт). */
    private static boolean hasUnwiredWarningDeclined(Frame owner, Scene scene, ControllerInstance controller,
                                                      AppModel model) {
        if (!NovaLctScrWriter.hasUnwiredCabinetsForController(scene, controller, model)) {
            return false;
        }
        int rr = JOptionPane.showConfirmDialog(owner,
                "На затронутых экранах есть кабинеты, не расключённые через этот контроллер"
                        + " (не входят ни в одну его цепочку, либо расключены через другой контроллер)."
                        + "\nЭкспортировать шаблон как есть?",
                "Экспорт NovaLCT для контроллера", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return rr != JOptionPane.YES_OPTION;
    }

    private static void saveToFile(Frame owner, byte[] data, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Файлы прописи NovaLCT (*.scr)", "scr"));
        chooser.setSelectedFile(new File(safeFileName(defaultName) + ".scr"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".scr")) {
            target = new File(target.getParentFile(), target.getName() + ".scr");
        }
        try {
            Files.write(target.toPath(), data);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Не удалось сохранить файл:\n" + ex.getMessage(),
                    "Экспорт NovaLCT для контроллера", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(owner, "Шаблон сохранён:\n" + target.getAbsolutePath(),
                "Экспорт NovaLCT для контроллера", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String cardWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "карт";
        }
        if (mod10 == 1) {
            return "карта";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "карты";
        }
        return "карт";
    }

    private static String safeFileName(String name) {
        String s = name == null ? "controller" : name.trim();
        if (s.isEmpty()) {
            s = "controller";
        }
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
