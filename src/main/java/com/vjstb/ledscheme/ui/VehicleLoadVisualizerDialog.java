package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.VehicleLoadPlacement;
import com.vjstb.ledscheme.model.VehicleLoadPlan;
import com.vjstb.ledscheme.model.VehicleLoadSection;
import com.vjstb.ledscheme.model.VehicleType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.VehicleCalc;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

/**
 * Визуализатор загрузки машины кофрами (запрос пользователя, первый заход/MVP —
 * см. VEHICLE_CALC_NOTES.md). Вызывается из {@link VehicleCalculatorDialog} с
 * уже подобранной машиной и строками кофров, которые менеджер ввёл в
 * калькуляторе — этот набор (тип + количество) ЖЁСТКО задаёт, что вообще можно
 * разместить: меню выбора кофра показывает только типы из этих строк, и
 * кнопка «Добавить сюда» блокируется, как только для типа размещено ровно
 * столько, сколько было указано (см. {@link #remainingFor}). Полная библиотека
 * кофров (`model.getCaseTypes()`) сюда намеренно НЕ подмешивается —
 * визуализатор показывает загрузку РЕАЛЬНОГО расчёта, а не произвольный набор.
 *
 * <p><b>Несколько машин сразу</b> (запрос пользователя: «одну сцену можно
 * укладывать в несколько машин») — диалог держит список {@link VehicleSection}
 * (каждая — свой холст {@link VehicleLoadCanvasPanel} + свой комбобокс
 * машины), кнопка «+ Добавить машину» добавляет ещё одну карточку снизу и
 * раздвигает окно (в пределах {@link #MAX_SECTIONS_VIEWPORT_HEIGHT}, дальше —
 * прокрутка). Меню выбора кофра ОБЩЕЕ на все машины — remainingFor считает
 * уже размещённое across ВСЕХ секций разом, у каждой карточки своя кнопка
 * «Добавить сюда», размещающая выбранный в меню тип именно в эту машину.
 * Так менеджер вручную распределяет кофры между несколькими машинами, если
 * одна не устраивает (не влезает / неудобной формы и т.п.).
 *
 * <p>Цветовая схема — временное правило по прямому указанию пользователя:
 * кофры "несёт кабинеты" чёрные, все остальные (коммутация и прочее) синие —
 * до реальных референсных фото, которые пользователь обещал передать позже
 * для уточнения (см. {@link VehicleLoadCanvasPanel} class-javadoc).
 *
 * <p><b>Персистентность</b> (запрос пользователя: «сохранение раскладок как
 * для всех наших схем, включая выгрузку в облако») — раскладка привязана к
 * {@link Scene} (см. {@code model.VehicleLoadPlan}, {@code
 * Scene#getVehicleLoadPlan}), НЕ к отдельному файлу/своей БД-таблице:
 * обычное поле модели проекта, которое едет вместе с остальным сохранением
 * (`WorkspaceStore`) и облачной выгрузкой (`CloudProjectsDialog` сериализует
 * весь {@code Project} целиком) — без единой строчки нового кода
 * синхронизации, та же генерическая Jackson-сериализация, что и у {@code
 * ContentCanvas}/{@code SchemaNode}. Загружается при открытии диалога (см.
 * {@link #loadOrInitSections}), сохраняется на каждое дискретное изменение
 * (см. {@link #persistPlan}, вызывается из {@link #refreshAll}).
 *
 * <p><b>«Разместить всё»</b> (запрос пользователя) — кнопка в карточке каждой
 * машины, автоматическая раскладка ВСЕХ ещё не размещённых типов кофров сразу
 * (не одного выбранного в меню, как «Добавить сюда») простым shelf-алгоритмом
 * с учётом штабелирования — см. {@link VehicleLoadCanvasPanel#autoPlaceAll}.
 * Заменяет текущую раскладку именно этой машины целиком (после подтверждения,
 * если она не пуста), бюджет считается за вычетом уже размещённого в ДРУГИХ
 * машинах (см. {@link #autoPlaceAllInSection}/{@link #placedInOtherSections})
 * — то есть при нескольких машинах в диалоге повторный клик «Разместить всё»
 * на следующей карточке распределяет именно остаток, не дублирует уже
 * пристроенное. Кофры, для которых не хватило места именно в этой машине, не
 * теряются молча — остаются в бюджете (см. {@link #remainingFor}) и
 * показываются статусом с подсказкой добавить ещё один кузов.
 */
public class VehicleLoadVisualizerDialog extends JDialog {

    private static final int MAX_SECTIONS_VIEWPORT_HEIGHT = 640;
    private static final int SECTION_HEIGHT = 260;

    private final AppModel model;
    private final Scene scene;
    private final List<VehicleSection> sections = new ArrayList<>();
    private final JPanel sectionsContainer = new JPanel();
    private final JScrollPane sectionsScroll;
    private final JList<CaseType> paletteList = new JList<>();
    private final JLabel paletteSummaryLabel = new JLabel(" ");
    private final JLabel statusLabel = UiKit.muted(" ");
    private final Map<CaseType, Integer> neededCounts;
    private boolean snapEnabled = true;

    public VehicleLoadVisualizerDialog(Window owner, AppModel model, Scene scene, VehicleType initialVehicle,
                                        List<VehicleCalc.CaseRow> initialRows) {
        super(owner, "Визуализация загрузки машины", ModalityType.MODELESS);
        this.model = model;
        this.scene = scene;
        this.neededCounts = initialRows == null ? Map.of()
                : initialRows.stream().collect(Collectors.toMap(
                        VehicleCalc.CaseRow::type, VehicleCalc.CaseRow::count, (a, b) -> a + b,
                        LinkedHashMap::new));

        sectionsContainer.setLayout(new BoxLayout(sectionsContainer, BoxLayout.Y_AXIS));
        sectionsScroll = new JScrollPane(sectionsContainer);
        sectionsScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildTopPanel(), BorderLayout.NORTH);
        // JSplitPane, а не BorderLayout.WEST+CENTER — панель кофров с фиксированной
        // шириной (setPreferredSize в buildPalettePanel) обрезала текст «нужно: N,
        // осталось: M» до горизонтальной прокрутки списка (баг-репорт со скриншотом) —
        // сплиттер даёт пользователю растянуть её самому. Тот же приём, что и у
        // ScenarioEditorPanel (левая колонка/остальное) в этом кодовой базе.
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPalettePanel(), sectionsScroll);
        split.setResizeWeight(0.25);
        content.add(split, BorderLayout.CENTER);
        content.add(buildBottomPanel(), BorderLayout.SOUTH);

        setContentPane(content);

        loadOrInitSections(initialVehicle);
        repackWindow();
        setLocationRelativeTo(owner);

        refreshAll();
    }

    /** Восстанавливает сохранённую раскладку сцены (см. class-javadoc про
     *  персистентность), если она есть и непуста; иначе — как раньше, одна
     *  пустая секция с {@code initialVehicle} (первый заход визуализатора для
     *  этой сцены). Кофры/машины, которых больше нет в библиотеке (id не
     *  резолвится), молча пропускаются — тот же приём, что и у комбобоксов
     *  SetupStagePanel при устаревшей ссылке. */
    private void loadOrInitSections(VehicleType initialVehicle) {
        VehicleLoadPlan plan = scene != null ? scene.getVehicleLoadPlan() : null;
        if (plan == null || plan.getSections().isEmpty()) {
            addSection(initialVehicle);
            return;
        }
        for (VehicleLoadSection savedSection : plan.getSections()) {
            VehicleType vt = model.getWorkspace().vehicleTypeById(savedSection.getVehicleTypeId());
            addSection(vt);
            VehicleSection section = sections.get(sections.size() - 1);
            for (VehicleLoadPlacement savedPlacement : savedSection.getPlacements()) {
                CaseType ct = model.getWorkspace().caseTypeById(savedPlacement.getCaseTypeId());
                if (ct == null) {
                    continue;
                }
                section.canvas.restorePlacement(ct, savedPlacement.getXMm(), savedPlacement.getYMm(),
                        savedPlacement.isRotated(), savedPlacement.getStackCount(), savedPlacement.getNote());
            }
            section.canvas.repaint();
        }
    }

    /** Сериализует {@link #sections} в {@code model.VehicleLoadPlan} и сохраняет
     *  в сцену (см. class-javadoc). No-op, если диалог открыт без привязки к
     *  сцене ({@code scene == null} — например, проект/сцена не выбраны). */
    private void persistPlan() {
        if (scene == null) {
            return;
        }
        VehicleLoadPlan plan = new VehicleLoadPlan();
        List<VehicleLoadSection> savedSections = new ArrayList<>();
        for (VehicleSection s : sections) {
            VehicleLoadSection saved = new VehicleLoadSection();
            VehicleType v = s.canvas.getVehicle();
            saved.setVehicleTypeId(v != null ? v.getId() : null);
            List<VehicleLoadPlacement> savedPlacements = new ArrayList<>();
            for (VehicleLoadCanvasPanel.Placement p : s.canvas.getPlacements()) {
                VehicleLoadPlacement vp = new VehicleLoadPlacement();
                vp.setCaseTypeId(p.type.getId());
                vp.setXMm(p.xMm);
                vp.setYMm(p.yMm);
                vp.setRotated(p.rotated);
                vp.setStackCount(p.stackCount);
                vp.setNote(p.note);
                savedPlacements.add(vp);
            }
            saved.setPlacements(savedPlacements);
            savedSections.add(saved);
        }
        plan.setSections(savedSections);
        model.saveVehicleLoadPlan(scene, plan);
    }

    public void setSnapEnabled(boolean snapEnabled) {
        this.snapEnabled = snapEnabled;
        for (VehicleSection s : sections) {
            s.canvas.setSnapEnabled(snapEnabled);
        }
    }

    // ---- верхняя панель: добавить ещё одну машину ----

    private JPanel buildTopPanel() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addVehicle = new JButton("+ Добавить машину");
        addVehicle.setToolTipText("Одну сцену иногда нужно грузить в несколько машин — добавляет ещё"
                + " один кузов ниже, кофры распределяются между машинами вручную (кнопка"
                + " «Добавить сюда» в карточке каждой машины).");
        addVehicle.addActionListener(e -> {
            if (model.getVehicleTypes().isEmpty()) {
                statusLabel.setText(" Библиотека машин пуста.");
                return;
            }
            addSection(null);
            repackWindow();
            refreshAll();
        });
        row.add(addVehicle);
        return row;
    }

    // ---- секции-машины ----

    private void addSection(VehicleType initial) {
        VehicleSection section = new VehicleSection(initial);
        sections.add(section);
        rebuildSectionsContainer();
    }

    private void removeSection(VehicleSection section) {
        if (sections.size() <= 1) {
            statusLabel.setText(" Нужна хотя бы одна машина.");
            return;
        }
        if (!section.canvas.getPlacements().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "В этой машине есть размещённые кофры — убрать её вместе с ними?",
                    "Убрать машину", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        sections.remove(section);
        rebuildSectionsContainer();
        repackWindow();
        refreshAll();
    }

    private void rebuildSectionsContainer() {
        sectionsContainer.removeAll();
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).wrapper.setBorder(BorderFactory.createTitledBorder("Машина " + (i + 1)));
            sectionsContainer.add(sections.get(i).wrapper);
            sectionsContainer.add(Box.createVerticalStrut(8));
        }
        sectionsContainer.revalidate();
        sectionsContainer.repaint();
    }

    private void repackWindow() {
        int height = Math.min(MAX_SECTIONS_VIEWPORT_HEIGHT, Math.max(SECTION_HEIGHT, sections.size() * SECTION_HEIGHT));
        sectionsScroll.setPreferredSize(new Dimension(680, height));
        pack();
    }

    private final class VehicleSection {
        final JComboBox<VehicleType> vehicleCombo = new JComboBox<>();
        final VehicleLoadCanvasPanel canvas = new VehicleLoadCanvasPanel();
        final JButton addHereBtn = new JButton("Добавить сюда");
        final JButton placeAllBtn = new JButton("Разместить всё");
        final JPanel wrapper = new JPanel(new BorderLayout(4, 4));

        VehicleSection(VehicleType initial) {
            canvas.setSnapEnabled(snapEnabled);
            canvas.setOnChanged(VehicleLoadVisualizerDialog.this::refreshAll);

            for (VehicleType v : model.getVehicleTypes()) {
                vehicleCombo.addItem(v);
            }
            vehicleCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
                JLabel label = new JLabel(value instanceof VehicleType v ? v.getName() : String.valueOf(value));
                label.setOpaque(true);
                label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                return label;
            });
            vehicleCombo.addActionListener(e -> {
                canvas.setVehicle((VehicleType) vehicleCombo.getSelectedItem());
                refreshAll();
            });
            if (initial != null) {
                vehicleCombo.setSelectedItem(initial);
            } else if (vehicleCombo.getItemCount() > 0) {
                vehicleCombo.setSelectedIndex(0);
            }
            canvas.setVehicle((VehicleType) vehicleCombo.getSelectedItem());

            addHereBtn.addActionListener(e -> {
                CaseType sel = paletteList.getSelectedValue();
                if (sel == null || remainingFor(sel) <= 0) {
                    return;
                }
                canvas.addPlacement(sel); // triggers onChanged -> refreshAll()
            });

            placeAllBtn.setToolTipText("Автоматически расставить в эту машину максимум ещё не размещённых"
                    + " кофров (по всем типам разом, с учётом штабелирования) — простая раскладка рядами,"
                    + " без ручной подгонки. Заменяет текущую раскладку именно этой машины.");
            placeAllBtn.addActionListener(e -> autoPlaceAllInSection(this));

            // Приём drag-n-drop из палитры слева — кофр падает туда, куда его бросили
            // (с центрированием под курсором), а не всегда в угол, как кнопка
            // «Добавить сюда» (см. VehicleLoadCanvasPanel#addPlacementAt).
            canvas.setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDrop() && support.isDataFlavorSupported(VehicleLoadCanvasPanel.CASE_TYPE_FLAVOR);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    if (!canImport(support)) {
                        return false;
                    }
                    try {
                        CaseType type = (CaseType) support.getTransferable()
                                .getTransferData(VehicleLoadCanvasPanel.CASE_TYPE_FLAVOR);
                        if (remainingFor(type) <= 0) {
                            return false;
                        }
                        Point dropPoint = support.getDropLocation().getDropPoint();
                        double[] mm = canvas.pxToMm(dropPoint);
                        canvas.addPlacementAt(type, mm[0] - type.getLengthMm() / 2.0, mm[1] - type.getWidthMm() / 2.0);
                        return true;
                    } catch (UnsupportedFlavorException | IOException ex) {
                        return false;
                    }
                }
            });

            JButton removeBtn = new JButton("Убрать машину");
            removeBtn.addActionListener(e -> removeSection(this));

            // Приближение (запрос пользователя) — кнопки для discoverability, Ctrl+колесо
            // тоже работает (см. VehicleLoadCanvasPanel), но не все догадаются держать Ctrl.
            JButton zoomOut = new JButton("−");
            zoomOut.setToolTipText("Отдалить (Ctrl+колесо мыши тоже работает)");
            zoomOut.addActionListener(e -> canvas.zoomBy(1 / 1.25));
            JButton zoomReset = new JButton("100%");
            zoomReset.setToolTipText("Сбросить масштаб");
            zoomReset.addActionListener(e -> canvas.setZoom(1.0));
            JButton zoomIn = new JButton("+");
            zoomIn.setToolTipText("Приблизить (Ctrl+колесо мыши тоже работает)");
            zoomIn.addActionListener(e -> canvas.zoomBy(1.25));

            // Две строки, не одна (баг-репорт со скриншотом: с "Разместить всё" одна
            // FlowLayout-строка стала шире, чем канвас/окно, и хвост (зум, "Убрать
            // машину") обрезался краем окна — тут нет горизонтальной прокрутки для
            // самого хедера, только для канваса ниже, см. javadoc canvasScroll) — каждая
            // строка примерно вдвое уже суммарной ширины всех кнопок, комфортно
            // помещается даже в узком окне.
            JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            headerLeft.add(new JLabel("Машина:"));
            headerLeft.add(vehicleCombo);
            headerLeft.add(addHereBtn);
            headerLeft.add(placeAllBtn);
            JPanel headerTop = new JPanel(new BorderLayout(6, 0));
            headerTop.add(headerLeft, BorderLayout.WEST);
            headerTop.add(removeBtn, BorderLayout.EAST);

            JPanel zoomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            zoomRow.add(zoomOut);
            zoomRow.add(zoomReset);
            zoomRow.add(zoomIn);

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.add(headerTop);
            header.add(zoomRow);

            // Канвас обёрнут в свой JScrollPane — при приближении (см. VehicleLoadCanvasPanel
            // #getPreferredSize) он растёт, и когда перестаёт помещаться в исходный размер
            // вьюпорта, появляется прокрутка вместо того, чтобы вылезать за карточку.
            JScrollPane canvasScroll = new JScrollPane(canvas);
            canvasScroll.setPreferredSize(new Dimension(600, 190));
            canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
            canvasScroll.getHorizontalScrollBar().setUnitIncrement(16);

            wrapper.add(header, BorderLayout.NORTH);
            wrapper.add(canvasScroll, BorderLayout.CENTER);
        }
    }

    // ---- меню выбора кофра (общее на все машины) ----

    /** Сколько ещё штук этого типа можно разместить — needed (из строк
     *  калькулятора) минус уже размещённое across ВСЕХ секций-машин. См.
     *  class-javadoc — жёсткий потолок, а не просто подсказка. */
    private int remainingFor(CaseType t) {
        int needed = neededCounts.getOrDefault(t, 0);
        int placed = sections.stream()
                .flatMap(s -> s.canvas.getPlacements().stream())
                .filter(p -> p.type == t)
                .mapToInt(p -> p.stackCount)
                .sum();
        return needed - placed;
    }

    /** Сколько штук типа {@code t} уже размещено в СЕКЦИЯХ, ОТЛИЧНЫХ от {@code
     *  exclude} — используется {@link #autoPlaceAllInSection}, чтобы посчитать
     *  бюджет "разместить всё" ИМЕННО для этой машины (текущая раскладка самой
     *  {@code exclude} будет заменена целиком, поэтому не учитывается). */
    private int placedInOtherSections(CaseType t, VehicleSection exclude) {
        return sections.stream()
                .filter(s -> s != exclude)
                .flatMap(s -> s.canvas.getPlacements().stream())
                .filter(p -> p.type == t)
                .mapToInt(p -> p.stackCount)
                .sum();
    }

    /** Кнопка «Разместить всё» одной карточки-машины (запрос пользователя: "кофры
     *  все должны максимально добавиться в машину с учётом этажирования и
     *  распределяться по машине") — считает бюджет (нужно всего минус уже
     *  размещено в ДРУГИХ машинах, см. {@link #placedInOtherSections}) по каждому
     *  типу из {@link #neededCounts} и запускает {@link
     *  VehicleLoadCanvasPanel#autoPlaceAll} именно на канвасе {@code section}.
     *  Если в этой машине уже что-то размещено вручную — спрашивает подтверждение
     *  (алгоритм заменяет раскладку целиком, не дополняет её). Остаток, который не
     *  поместился (кузов кончился раньше кофров), — не теряется молча: остаётся в
     *  {@code neededCounts}/{@code remainingFor} как и раньше, показывается статусом
     *  и подсказкой добавить ещё одну машину — тем же путём, что и обычная ручная
     *  раскладка. */
    private void autoPlaceAllInSection(VehicleSection section) {
        if (section.canvas.getVehicle() == null) {
            statusLabel.setText(" Сначала выберите машину для этой карточки.");
            return;
        }
        if (neededCounts.isEmpty()) {
            statusLabel.setText(" Нет данных из расчёта — сначала укажите кофры в калькуляторе.");
            return;
        }
        if (!section.canvas.getPlacements().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Автоматическая раскладка заменит текущее размещение кофров в этой машине. Продолжить?",
                    "Разместить всё", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        Map<CaseType, Integer> budget = new LinkedHashMap<>();
        for (Map.Entry<CaseType, Integer> entry : neededCounts.entrySet()) {
            int remaining = entry.getValue() - placedInOtherSections(entry.getKey(), section);
            if (remaining > 0) {
                budget.put(entry.getKey(), remaining);
            }
        }
        Map<CaseType, Integer> leftover = section.canvas.autoPlaceAll(budget);
        // refreshAll() перезаписывает statusLabel общей сводкой ("Размещено кофров
        // всего: N") — поэтому свой, более конкретный статус про остаток ставим
        // ПОСЛЕ него, а не до (иначе refreshAll молча стирает эту подсказку).
        refreshAll();
        int leftoverTotal = leftover.values().stream().mapToInt(Integer::intValue).sum();
        if (leftoverTotal > 0) {
            String details = leftover.entrySet().stream()
                    .map(e -> e.getKey().getName() + " × " + e.getValue())
                    .collect(Collectors.joining(", "));
            statusLabel.setText(" Не поместилось в эту машину: " + leftoverTotal + " шт. (" + details
                    + ") — добавьте ещё одну машину и повторите «Разместить всё».");
        }
    }

    private JPanel buildPalettePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Кофры (по расчёту)"));
        panel.setPreferredSize(new Dimension(280, 300)); // начальная ширина — растягивается сплиттером (см. constructor)

        paletteList.setListData(neededCounts.keySet().toArray(new CaseType[0]));
        paletteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (paletteList.getModel().getSize() > 0) {
            paletteList.setSelectedIndex(0);
        }
        paletteList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof CaseType t) {
                    label.setText(t.getName() + "  (нужно: " + neededCounts.get(t)
                            + ", осталось: " + remainingFor(t) + ")");
                    label.setIcon(new ColorSwatchIcon(t.isCarriesCabinets()
                            ? VehicleLoadCanvasPanel.COLOR_CARRIES_CABINETS : VehicleLoadCanvasPanel.COLOR_OTHER));
                }
                return label;
            }
        });
        paletteList.addListSelectionListener(e -> refreshAll());

        // Drag-n-drop источник — тащим выбранный тип прямо на нужную машину (см.
        // TransferHandler на canvas в VehicleSection). Экспортируем только если
        // для типа ещё есть что размещать — тащить нечего размещать бессмысленно.
        paletteList.setDragEnabled(true);
        paletteList.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return TransferHandler.COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                CaseType sel = paletteList.getSelectedValue();
                if (sel == null || remainingFor(sel) <= 0) {
                    return null;
                }
                return new CaseTypeTransferable(sel);
            }
        });

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        paletteSummaryLabel.setFont(paletteSummaryLabel.getFont().deriveFont(java.awt.Font.BOLD));
        top.add(paletteSummaryLabel);
        if (neededCounts.isEmpty()) {
            top.add(UiKit.muted("Нет данных из расчёта — сначала укажите кофры"
                    + " с количеством больше нуля в калькуляторе."));
        }
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(paletteList), BorderLayout.CENTER);

        if (!neededCounts.isEmpty()) {
            panel.add(UiKit.muted("Перетащите тип на нужную машину (или выберите здесь"
                    + " и нажмите «Добавить сюда» в её карточке)."), BorderLayout.SOUTH);
        }
        return panel;
    }

    private static final class CaseTypeTransferable implements Transferable {
        private final CaseType type;

        CaseTypeTransferable(CaseType type) {
            this.type = type;
        }

        @Override
        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[]{VehicleLoadCanvasPanel.CASE_TYPE_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
            return VehicleLoadCanvasPanel.CASE_TYPE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return type;
        }
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        legend.add(legendEntry("Кабинеты", VehicleLoadCanvasPanel.COLOR_CARRIES_CABINETS));
        legend.add(legendEntry("Коммутация / прочее", VehicleLoadCanvasPanel.COLOR_OTHER));
        bottom.add(legend);

        // Баг-репорт со скриншотом: обычный однострочный JLabel (через UiKit.muted без
        // "<html>"-обёртки) обрезал длинный текст подсказки вместо переноса — UiKit.wrapHtml
        // включает перенос ТОЛЬКО если строка сама начинается с "<html>" (см. её javadoc),
        // а её встроенная ширина (220px) рассчитана на узкие боковые панели, не на широкую
        // нижнюю плашку этого диалога — оборачиваем сами с более подходящей шириной.
        JLabel hint = new JLabel("<html><div style='width:900px'>Перетащите кофр мышью (за пределы кузова"
                + " или на чужой кофр — не встанет, прилипание к соседним кофрам — переключатель в окне"
                + " калькулятора). R — повернуть на 90°. Delete — убрать один уровень штабеля (или сам кофр,"
                + " если уровень один). Кофр того же типа, брошенный почти точно на другой такой же — уходит"
                + " в штабель. ПКМ по кофру — примечание менеджера (показывается под именем кофра)."
                + " Ctrl+колесо мыши — приблизить/отдалить.</div></html>");
        hint.setForeground(Palette.MUTED);
        bottom.add(hint);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.add(statusLabel, BorderLayout.WEST);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        closeRow.add(close);
        statusRow.add(closeRow, BorderLayout.EAST);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(statusRow);
        return bottom;
    }

    private JPanel legendEntry(String label, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel swatch = new JLabel(new ColorSwatchIcon(color));
        p.add(swatch);
        p.add(UiKit.muted(label));
        return p;
    }

    /** Единая точка обновления после ЛЮБОГО изменения (размещение/удаление/поворот
     *  в любой из секций, смена машины, смена выбора в меню кофров) — статус,
     *  меню кофров (счётчики "осталось" зависят от всех секций разом) и
     *  доступность кнопок «Добавить сюда» (у всех секций ОДИНАКОВОЕ состояние —
     *  оно зависит только от текущего выбора в общем меню, не от конкретной
     *  машины). */
    private void refreshAll() {
        int placedTotal = sections.stream()
                .flatMap(s -> s.canvas.getPlacements().stream())
                .mapToInt(p -> p.stackCount)
                .sum();
        statusLabel.setText(" Размещено кофров всего: " + placedTotal + " (машин: " + sections.size() + ")");

        int totalNeeded = neededCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalRemaining = neededCounts.keySet().stream().mapToInt(this::remainingFor).sum();
        paletteSummaryLabel.setText(" Осталось разместить: " + totalRemaining + " из " + totalNeeded);

        CaseType sel = paletteList.getSelectedValue();
        boolean canAdd = sel != null && remainingFor(sel) > 0;
        for (VehicleSection s : sections) {
            s.addHereBtn.setEnabled(canAdd);
        }
        paletteList.repaint();

        persistPlan();
    }

    private static final class ColorSwatchIcon implements javax.swing.Icon {
        private final Color color;

        ColorSwatchIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, 12, 12);
            g.setColor(Palette.BORDER);
            g.drawRect(x, y, 12, 12);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
