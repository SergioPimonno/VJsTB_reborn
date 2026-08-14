package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.AssembleCardsDialog;
import com.vjstb.ledscheme.ui.CabinetTypeDialog;
import com.vjstb.ledscheme.ui.CabinetTypeRenderer;
import com.vjstb.ledscheme.ui.CableLengthProfileDialog;
import com.vjstb.ledscheme.ui.CardsConfigDialog;
import com.vjstb.ledscheme.ui.ControllerTypeDialog;
import com.vjstb.ledscheme.ui.EquipmentPresetDialog;
import com.vjstb.ledscheme.ui.ListSizing;
import com.vjstb.ledscheme.ui.NamedRenderer;
import com.vjstb.ledscheme.ui.PowerConnectorsConfigDialog;
import com.vjstb.ledscheme.ui.ProposeDialog;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

/**
 * Этап «Библиотеки»: общие для всех проектов справочники оборудования —
 * кабинеты, контроллеры (перенесены сюда из «Сетапа», не дублируются) и пресеты
 * оборудования (для быстрой вставки узлов общей схемы питания/сигнала, см.
 * {@link com.vjstb.ledscheme.ui.SchemaPanel}).
 */
public class LibrariesStagePanel extends JPanel {

    private final AppModel model;
    private final SettingsManager settings;

    private final DefaultListModel<CabinetType> libModel = new DefaultListModel<>();
    private final JList<CabinetType> libList = new JList<>(libModel);
    private final JScrollPane libScroll = new JScrollPane(libList);

    private final DefaultListModel<ControllerType> ctrlLibModel = new DefaultListModel<>();
    private final JList<ControllerType> ctrlLibList = new JList<>(ctrlLibModel);
    private final JScrollPane ctrlLibScroll = new JScrollPane(ctrlLibList);

    /** Категории пресетов оборудования — все значения SchemaNodeType, кроме SCREEN
     *  (тот зарезервирован под узел-ссылку на экран схемы, не под оборудование). */
    private static final SchemaNodeType[] EQUIPMENT_CATEGORIES = {
            SchemaNodeType.SOURCE, SchemaNodeType.DISTRO, SchemaNodeType.CONVERTER,
            SchemaNodeType.SERVER, SchemaNodeType.CONTROLLER, SchemaNodeType.MONITOR, SchemaNodeType.CUSTOM
    };

    private final DefaultListModel<SchemaNodeType> powerCategoryModel = new DefaultListModel<>();
    private final JList<SchemaNodeType> powerCategoryList = new JList<>(powerCategoryModel);
    private final JScrollPane powerCategoryScroll = new JScrollPane(powerCategoryList);

    private final DefaultListModel<EquipmentPreset> powerPresetModel = new DefaultListModel<>();
    private final JList<EquipmentPreset> powerPresetList = new JList<>(powerPresetModel);
    private final JScrollPane powerPresetScroll = new JScrollPane(powerPresetList);

    private final DefaultListModel<SchemaNodeType> signalCategoryModel = new DefaultListModel<>();
    private final JList<SchemaNodeType> signalCategoryList = new JList<>(signalCategoryModel);
    private final JScrollPane signalCategoryScroll = new JScrollPane(signalCategoryList);

    private final DefaultListModel<EquipmentPreset> signalPresetModel = new DefaultListModel<>();
    private final JList<EquipmentPreset> signalPresetList = new JList<>(signalPresetModel);
    private final JScrollPane signalPresetScroll = new JScrollPane(signalPresetList);

    private final DefaultListModel<SchemaCard> signalCardModel = new DefaultListModel<>();
    private final JList<SchemaCard> signalCardList = new JList<>(signalCardModel);
    private final JScrollPane signalCardScroll = new JScrollPane(signalCardList);

    private final DefaultListModel<CableType> cableModel = new DefaultListModel<>();
    private final JList<CableType> cableList = new JList<>(cableModel);
    private final JScrollPane cableScroll = new JScrollPane(cableList);

    private final DefaultListModel<CableLengthProfile> cableLengthProfileModel = new DefaultListModel<>();
    private final JList<CableLengthProfile> cableLengthProfileList = new JList<>(cableLengthProfileModel);
    private final JScrollPane cableLengthProfileScroll = new JScrollPane(cableLengthProfileList);

    private final DefaultListModel<InterfaceType> interfaceTypeModel = new DefaultListModel<>();
    private final JList<InterfaceType> interfaceTypeList = new JList<>(interfaceTypeModel);
    private final JScrollPane interfaceTypeScroll = new JScrollPane(interfaceTypeList);

    private NamedRenderer<CabinetType> libRenderer;
    private NamedRenderer<ControllerType> ctrlLibRenderer;
    private NamedRenderer<EquipmentPreset> powerPresetRenderer;
    private NamedRenderer<CableType> cableRenderer;
    private NamedRenderer<CableLengthProfile> cableLengthProfileRenderer;
    private NamedRenderer<InterfaceType> interfaceTypeRenderer;

    private javax.swing.JComponent exportImportSection;
    private javax.swing.JComponent cabinetsSection;
    private javax.swing.JComponent controllersSection;
    private javax.swing.JComponent powerPresetsSection;
    private javax.swing.JComponent signalEquipmentSection;
    private javax.swing.JComponent cableSection;
    private javax.swing.JComponent cableLengthProfileSection;
    private javax.swing.JComponent interfaceTypeSection;

    /** Ширина содержимого этапа (окно минус вертикальный скроллбар минус паддинг
     *  body) — пересчитывается живьём при ресайзе (см. конструктор), а не
     *  фиксируется один раз при первой сборке, иначе список либо "утекает" за
     *  край при узком окне, либо оставляет пустое место при широком (Task #100 —
     *  повторный баг-репорт после промежуточного фикса с жёстким capWidth=720). */
    private int contentWidth = 700;

    public LibrariesStagePanel(AppModel model, SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        setLayout(new BorderLayout());

        JPanel body = UiKit.vbox();
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // Секции (UiKit.section) сами по себе НАМЕРЕННО безграничны по ширине
        // (см. UiKit.recapHeight — фиксирует только высоту), чтобы заголовок с
        // рамкой красиво тянулся во всю ширину этапа — оставляем как есть, но
        // ЖИВЬЁМ ограничиваем эту ширину реальной шириной вьюпорта этапа (за
        // вычетом вертикального скроллбара, который сам JViewport уже не считает
        // своей шириной) через applyContentWidth ниже, а не один раз фиксированным
        // числом при сборке — иначе при широком окне остаётся пустое место, а при
        // узком содержимое вылезает за край (Task #100, повторный баг-репорт).
        exportImportSection = (javax.swing.JComponent) UiKit.section(
                "Экспорт / импорт личных библиотек",
                new com.vjstb.ledscheme.ui.LibraryExportImportPanel(model));
        cabinetsSection = buildLibrary();
        controllersSection = buildControllerLibrary();
        powerPresetsSection = buildEquipmentPresetSection(SchemaMode.POWER,
                "Оборудование питания (пресеты для схемы)", powerPresetList, powerPresetScroll,
                powerCategoryList, powerCategoryScroll);
        signalEquipmentSection = buildSignalEquipmentSection();
        cableSection = buildCableLibrary();
        cableLengthProfileSection = buildCableLengthProfileLibrary();
        interfaceTypeSection = buildInterfaceTypeSection();
        body.add(exportImportSection);
        body.add(UiKit.vgap(10));
        body.add(cabinetsSection);
        body.add(UiKit.vgap(10));
        body.add(controllersSection);
        body.add(UiKit.vgap(10));
        body.add(powerPresetsSection);
        body.add(UiKit.vgap(10));
        body.add(signalEquipmentSection);
        body.add(UiKit.vgap(10));
        body.add(cableSection);
        body.add(UiKit.vgap(10));
        body.add(cableLengthProfileSection);
        body.add(UiKit.vgap(10));
        body.add(interfaceTypeSection);
        body.add(javax.swing.Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        for (JScrollPane sp : new JScrollPane[]{libScroll, ctrlLibScroll, powerPresetScroll, cableScroll,
                cableLengthProfileScroll, interfaceTypeScroll}) {
            sp.setMinimumSize(new Dimension(200, 80));
            // ВСЕГДА показывать вертикальный скроллбар (даже когда все позиции
            // помещаются) — иначе два списка одинаковой ширины секции переносят
            // текст по-разному в зависимости от того, есть ли у НИХ КОНКРЕТНО
            // прокрутка прямо сейчас, и визуально расходятся по правому отступу
            // (баг-репорт: "оффсет окошка библиотеки кабинетов" против библиотеки
            // контроллеров) — см. rendererWidth()/SCROLLBAR_RESERVE выше.
            sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        }

        // scroll.getViewport().getWidth() УЖЕ не включает ширину вертикального
        // скроллбара (она "снаружи" вьюпорта) — не нужно вычитать её вручную.
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyContentWidth(scroll.getViewport().getWidth());
            }
        });

        model.addListener(this::refresh);
        refresh();
    }

    /** Пересчитывает ширину секций и вложенных списков под реальную ширину
     *  вьюпорта этапа — вызывается и живьём при ресайзе окна, и из refresh()
     *  (последним известным значением), чтобы обновление данных не сбрасывало
     *  подстроенную ширину обратно на дефолт. */
    private void applyContentWidth(int viewportWidth) {
        if (viewportWidth <= 0) {
            return;
        }
        contentWidth = Math.max(320, viewportWidth - 20 /* паддинг body */);
        if (exportImportSection != null) {
            // Статичное содержимое (без списков) — высота не меняется, capSectionWidth
            // здесь просто переустанавливает предыдущую высоту с новой шириной.
            Dimension current = exportImportSection.getMaximumSize();
            exportImportSection.setMaximumSize(new Dimension(contentWidth, current.height));
            exportImportSection.revalidate();
        }
        refresh();
    }

    private int listWidth() {
        // Заголовок секции + её внутренние отступы (см. UiKit.sectionPanel) съедают
        // часть ширины — запас, чтобы список не вылезал за рамку секции и не получал
        // свой собственный горизонтальный скроллбар из-за пары лишних пикселей.
        return Math.max(200, contentWidth - 60);
    }

    /** Ширина вертикального скроллбара, ВСЕГДА резервируемая под текст в
     *  NamedRenderer — намеренно не зависит от того, показан ли скроллбар у
     *  КОНКРЕТНОГО списка прямо сейчас. Если резервировать место только когда
     *  скроллбар реально есть, два списка с одинаковой шириной секции (например,
     *  «Библиотека кабинетов» с прокруткой и «Библиотека контроллеров» без неё)
     *  переносят текст по-разному и визуально расходятся по правому отступу —
     *  ровно баг-репорт "оффсет окошка библиотеки кабинетов". Постоянный отступ
     *  устраняет расхождение независимо от текущего количества позиций. */
    private static final int SCROLLBAR_RESERVE = 18;

    private int rendererWidth(int w) {
        return Math.max(160, w - SCROLLBAR_RESERVE);
    }

    /** Пересчитывает МАКСИМАЛЬНУЮ ширину (см. applyContentWidth) И высоту секции по
     *  ЖИВОМУ preferredSize (не setPreferredSize — тот бы заморозил значение и
     *  сломал пересчёт при следующем изменении числа строк списка внутри) — для
     *  секций со списком, чьё содержимое (число строк) меняется после сборки. */
    private void recapSection(javax.swing.JComponent section) {
        section.setMaximumSize(new Dimension(contentWidth, section.getPreferredSize().height));
    }

    /** Тело секции со списком: список в CENTER, остальные элементы (кнопки,
     *  подсказка) снизу в SOUTH — оба ВСЕГДА получают полную ширину секции
     *  безусловно (в отличие от BoxLayout.Y_AXIS, который делит "поперечную"
     *  ширину между детьми через SizeRequirements.calculateAlignedPositions;
     *  на практике это давало то одному, то другому ребёнку заметно МЕНЬШЕ
     *  положенной ширины при нескольких детях с разным maximumSize в одном
     *  BoxLayout-контейнере — подтверждено диагностикой с реальными числами:
     *  список с подсказкой-«муткой» снизу ужимался почти на 200px против
     *  списка без неё при абсолютно одинаковой ширине секции; баг-репорт
     *  "оффсет окошка библиотеки кабинетов", повторный). BorderLayout не
     *  занимается таким "дележом" — CENTER/SOUTH всегда получают 100% ширины
     *  родителя, поэтому переносим сюда всю "поперечную" геометрию целиком. */
    private static JPanel listSectionBody(java.awt.Component center, java.awt.Component... southParts) {
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(center, BorderLayout.CENTER);
        JPanel south = UiKit.vbox();
        for (java.awt.Component c : southParts) {
            south.add(c);
        }
        body.add(south, BorderLayout.SOUTH);
        return body;
    }

    // ---- библиотека кабинетов ----

    private JPanel buildLibrary() {
        libList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libRenderer = new NamedRenderer<CabinetType>(
                CabinetType::getName, ct ->
                UiKit.fmt(ct.getWidthMm()) + "×" + UiKit.fmt(ct.getHeightMm()) + "мм · "
                        + ct.getResolutionWidth() + "×" + ct.getResolutionHeight() + "px · "
                        + UiKit.fmtPower(ct.getPowerConsumptionW(), settings.activeProfile().isPowerUnitKw())
                        + " · " + UiKit.fmt(ct.getWeightKg()) + "кг"
                        + (ct.getCompany() == null || ct.getCompany().isEmpty() ? "" : " · Компания: " + ct.getCompany()),
                ct -> model.isSharedCabinetType(ct.getId()));
        libList.setCellRenderer(libRenderer);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            CabinetType ct = new CabinetTypeDialog(topWindow(), model, null).showDialog();
            if (ct != null) tryRun(() -> model.addCabinetType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel == null) return;
            CabinetType ct = new CabinetTypeDialog(topWindow(), model, sel).showDialog();
            if (ct != null) tryRun(() -> model.updateCabinetType(ct));
        });
        Runnable deleteSelectedCabinetType = () -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel != null && confirm("Удалить кабинет из библиотеки?")) tryRun(() -> model.deleteCabinetType(sel.getId()));
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedCabinetType.run());
        UiKit.bindDeleteKey(libList, deleteSelectedCabinetType);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "CABINET", sel.getName(), sel);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        crud.add(propose);
        String sharedTip = "Общие элементы редактируются только через админ-консоль";
        libList.addListSelectionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            boolean shared = sel != null && model.isSharedCabinetType(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? sharedTip : null;
            edit.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Библиотека кабинетов", listSectionBody(libScroll, crud));
    }

    // ---- библиотека контроллеров (аналог SmartLCT) ----

    private JPanel buildControllerLibrary() {
        ctrlLibList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ctrlLibRenderer = new NamedRenderer<ControllerType>(
                ct -> ct.getName() + (ct.getVendor().isEmpty() ? "" : " (" + ct.getVendor() + ")"),
                ct -> ct.effectivePortCount() + " вых. портов" + (ct.getCards().isEmpty() ? "" : " (по картам)")
                        + " · " + UiKit.fmt(ct.getPortBandwidthMbps()) + " Мбит/с"
                        + " (до " + ct.referencePixelsPerPort() + " px @60Гц/8бит)"
                        + (ct.effectiveInputPortCount() > 0
                                ? " · вх. портов: " + ct.effectiveInputPortCount()
                                        + (ct.inputPortTypesSummary().isEmpty() ? "" : " (" + ct.inputPortTypesSummary() + ")")
                                : "")
                        + (ct.isLoopPort() ? " · Loop" : "")
                        + (ct.getCompany() == null || ct.getCompany().isEmpty() ? "" : " · Компания: " + ct.getCompany()),
                ct -> model.isSharedControllerType(ct.getId()));
        ctrlLibList.setCellRenderer(ctrlLibRenderer);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            ControllerType ct = new ControllerTypeDialog(topWindow(), model, null).showDialog();
            if (ct != null) tryRun(() -> model.addControllerType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null) return;
            ControllerType ct = new ControllerTypeDialog(topWindow(), model, sel).showDialog();
            if (ct != null) tryRun(() -> model.updateControllerType(ct));
        });
        JButton cardsBtn = new JButton("Карты…");
        cardsBtn.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null) return;
            CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), sel.getName(),
                    CardsConfigDialog.forController(model, sel), model);
            dlg.setVisible(true);
        });
        Runnable deleteSelectedControllerType = () -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel != null && confirm("Удалить контроллер из библиотеки?")) {
                tryRun(() -> model.deleteControllerType(sel.getId()));
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedControllerType.run());
        UiKit.bindDeleteKey(ctrlLibList, deleteSelectedControllerType);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "CONTROLLER", sel.getName(), sel);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        crud.add(propose);
        String ctrlSharedTip = "Общие элементы редактируются только через админ-консоль";
        ctrlLibList.addListSelectionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            boolean shared = sel != null && model.isSharedControllerType(sel.getId());
            edit.setEnabled(sel != null && !shared);
            cardsBtn.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? ctrlSharedTip : null;
            edit.setToolTipText(tip);
            cardsBtn.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Библиотека контроллеров", listSectionBody(ctrlLibScroll, crud));
    }

    // ---- пресеты оборудования (для схемы питания/сигнала) ----

    /** Общий рендерер категории (SchemaNodeType) — подпись через model.categoryLabel
     *  (учитывает админ-переименование встроенной категории, см. П.12), а не
     *  toString умолчальный DefaultListCellRenderer (значение и так уже toString==
     *  getLabel по умолчанию, если категория не переименована, но подпись должна
     *  идти через модель, иначе переименование не будет видно в этом дереве),
     *  переиспользуется для питания и сигнала одинаково. */
    @SuppressWarnings("rawtypes")
    private javax.swing.ListCellRenderer categoryRenderer() {
        return new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaNodeType t) {
                    setText(model.categoryLabel(t));
                }
                return this;
            }
        };
    }

    /** Категория, выделенная сейчас в дереве библиотеки — первая по списку, если
     *  выделения ещё нет (первая сборка) вместо неопределённого "ничего не показывать". */
    private static SchemaNodeType selectedOrFirstCategory(JList<SchemaNodeType> categoryList) {
        SchemaNodeType sel = categoryList.getSelectedValue();
        return sel != null ? sel : EQUIPMENT_CATEGORIES[0];
    }

    // ---- пресеты оборудования питания: категории | пресеты этой категории ----

    private JPanel buildEquipmentPresetSection(SchemaMode mode, String title, JList<EquipmentPreset> presetList,
                                                JScrollPane presetScroll, JList<SchemaNodeType> categoryList,
                                                JScrollPane categoryScroll) {
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setCellRenderer(categoryRenderer());
        for (SchemaNodeType t : EQUIPMENT_CATEGORIES) {
            ((DefaultListModel<SchemaNodeType>) categoryList.getModel()).addElement(t);
        }
        categoryList.setSelectedIndex(0);
        categoryScroll.setMinimumSize(new Dimension(160, 120));

        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        NamedRenderer<EquipmentPreset> renderer = new NamedRenderer<EquipmentPreset>(
                EquipmentPreset::getName,
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + (mode == SchemaMode.POWER
                                ? "разъёмов: " + p.getPowerConnectors().size()
                                : "карт: " + p.getCards().size())
                        + (p.getCompany() == null || p.getCompany().isEmpty() ? "" : " · Компания: " + p.getCompany()),
                p -> model.isSharedEquipmentPreset(p.getId()));
        presetList.setCellRenderer(renderer);
        presetScroll.setMinimumSize(new Dimension(200, 120));

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, null,
                    selectedOrFirstCategory(categoryList)).showDialog();
            if (r != null) {
                tryRun(() -> model.addEquipmentPreset(mode, r.category(), r.name(), r.description(), null,
                        r.customCategoryLabel(), r.company()));
            }
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel == null) return;
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, sel).showDialog();
            if (r != null) {
                tryRun(() -> model.updateEquipmentPreset(sel, mode, r.category(), r.name(), r.description(),
                        r.customCategoryLabel(), r.company()));
            }
        });
        JButton cardsBtn = new JButton(mode == SchemaMode.POWER ? "Разъёмы…" : "Карты…");
        cardsBtn.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel == null) return;
            String dlgTitle = sel.getName().isEmpty() ? model.categoryLabel(sel.getCategory()) : sel.getName();
            if (mode == SchemaMode.POWER) {
                PowerConnectorsConfigDialog dlg = new PowerConnectorsConfigDialog(topWindow(), dlgTitle,
                        PowerConnectorsConfigDialog.forPreset(model, sel), model);
                dlg.setVisible(true);
            } else {
                CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), dlgTitle,
                        CardsConfigDialog.forPreset(model, sel), model);
                dlg.setVisible(true);
            }
        });
        Runnable deleteSelectedPreset = () -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel != null && confirm("Удалить пресет «" + sel.getName() + "» из библиотеки?")) {
                tryRun(() -> model.deleteEquipmentPreset(sel));
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedPreset.run());
        UiKit.bindDeleteKey(presetList, deleteSelectedPreset);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "EQUIPMENT", sel.getName(), sel);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        crud.add(propose);
        String presetSharedTip = "Общие элементы редактируются только через админ-консоль";
        presetList.addListSelectionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            boolean shared = sel != null && model.isSharedEquipmentPreset(sel.getId());
            edit.setEnabled(sel != null && !shared);
            cardsBtn.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? presetSharedTip : null;
            edit.setToolTipText(tip);
            cardsBtn.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });

        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshPowerPresets();
            }
        });

        JPanel categoryPane = UiKit.vbox();
        categoryPane.add(UiKit.muted("Категория"));
        categoryPane.add(categoryScroll);

        JPanel presetsPane = UiKit.vbox();
        presetsPane.add(UiKit.muted("Пресеты этой категории"));
        presetsPane.add(presetScroll);
        presetsPane.add(crud);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryPane, presetsPane);
        split.setResizeWeight(0.28);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);

        return (JPanel) UiKit.dynamicSection(title, listSectionBody(split));
    }

    // ---- библиотека кабелей/переходников (WireLabelDialog/PowerConnectorsConfigDialog) ----

    private JPanel buildCableLibrary() {
        cableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cableRenderer = new NamedRenderer<CableType>(
                c -> (c.getMode() == SchemaMode.POWER ? "[Питание] " : "[Сигнал] ") + c.getLabel(),
                c -> c.getFixedLengthM() != null ? UiKit.fmt(c.getFixedLengthM()) + " м (фиксированная длина)" : "",
                c -> model.isSharedCableType(c.getId()));
        cableList.setCellRenderer(cableRenderer);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("+ Добавить кабель…");
        add.addActionListener(e -> {
            com.vjstb.ledscheme.ui.CableTypeDialog dlg = new com.vjstb.ledscheme.ui.CableTypeDialog(topWindow(), model);
            String label = dlg.showDialog();
            if (label != null) {
                tryRun(() -> model.addCableType(dlg.getMode(), label, dlg.getFixedLengthM()));
            }
        });
        Runnable deleteSelectedCable = () -> {
            CableType sel = cableList.getSelectedValue();
            if (sel != null && confirm("Удалить кабель «" + sel.getLabel() + "» из библиотеки?")) {
                model.deleteCableType(sel);
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedCable.run());
        UiKit.bindDeleteKey(cableList, deleteSelectedCable);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            CableType sel = cableList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "CABLE", sel.getLabel(), sel);
        });
        addRow.add(add);
        addRow.add(del);
        addRow.add(propose);
        String cableSharedTip = "Общие элементы редактируются только через админ-консоль";
        cableList.addListSelectionListener(e -> {
            CableType sel = cableList.getSelectedValue();
            boolean shared = sel != null && model.isSharedCableType(sel.getId());
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? cableSharedTip : null;
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Кабели", listSectionBody(cableScroll, addRow));
    }

    // ---- каталоги доступных длин катушек кабеля (для автоспецификации в "Выходных данных") ----

    private JPanel buildCableLengthProfileLibrary() {
        cableLengthProfileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cableLengthProfileRenderer = new NamedRenderer<CableLengthProfile>(
                p -> (p.getMode() == null ? "" : p.getMode() == SchemaMode.POWER ? "[Питание] " : "[Сигнал] ")
                        + p.getName(),
                p -> {
                    java.util.List<String> lens = new java.util.ArrayList<>();
                    for (Double len : p.getAvailableLengthsM()) {
                        lens.add(len == Math.floor(len) ? String.valueOf(len.intValue()) : String.valueOf(len));
                    }
                    return String.join(", ", lens) + " м · запас " + (p.getMarginPercent() == Math.floor(p.getMarginPercent())
                            ? String.valueOf((int) p.getMarginPercent()) : String.valueOf(p.getMarginPercent())) + "%";
                },
                p -> model.isSharedCableLengthProfile(p.getId()));
        cableLengthProfileList.setCellRenderer(cableLengthProfileRenderer);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("+ Добавить каталог…");
        add.addActionListener(e -> {
            CableLengthProfile created = new CableLengthProfileDialog(topWindow(), null).showDialog();
            if (created != null) {
                tryRun(() -> model.addCableLengthProfile(created));
            }
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            if (sel != null) {
                CableLengthProfile edited = new CableLengthProfileDialog(topWindow(), sel).showDialog();
                if (edited != null) {
                    tryRun(() -> model.updateCableLengthProfile(edited));
                }
            }
        });
        Runnable deleteSelectedCableLengthProfile = () -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            if (sel != null && confirm("Удалить каталог длин «" + sel.getName() + "» из библиотеки?")) {
                model.deleteCableLengthProfile(sel.getId());
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedCableLengthProfile.run());
        UiKit.bindDeleteKey(cableLengthProfileList, deleteSelectedCableLengthProfile);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "CABLE_LENGTH_PROFILE", sel.getName(), sel);
        });
        addRow.add(add);
        addRow.add(edit);
        addRow.add(del);
        addRow.add(propose);
        String cableLengthSharedTip = "Общие элементы редактируются только через админ-консоль";
        cableLengthProfileList.addListSelectionListener(e -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            boolean shared = sel != null && model.isSharedCableLengthProfile(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? cableLengthSharedTip : null;
            edit.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Каталог длин кабелей", listSectionBody(cableLengthProfileScroll, addRow));
    }

    // ---- виды интерфейса (HDMI/DisplayPort/SDI/...) — общая справочная данные,
    //      добавление/изменение — только через отдельную админ-консоль
    //      (Task #135/v2.0); отсюда — просмотр, удаление СВОИХ личных записей
    //      (например, дублей/черновиков) и предложить новый вид ----

    private JPanel buildInterfaceTypeSection() {
        interfaceTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        interfaceTypeRenderer = new NamedRenderer<InterfaceType>(
                InterfaceType::getName,
                t -> t.getVersions().isEmpty() ? "" : String.join(", ", t.getVersions()),
                t -> model.isSharedInterfaceType(t.getId()));
        interfaceTypeList.setCellRenderer(interfaceTypeRenderer);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        Runnable deleteSelectedInterfaceType = () -> {
            InterfaceType sel = interfaceTypeList.getSelectedValue();
            if (sel != null && confirm("Удалить вид интерфейса «" + sel.getName() + "» из личной библиотеки?")) {
                model.deleteInterfaceType(sel.getId());
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedInterfaceType.run());
        UiKit.bindDeleteKey(interfaceTypeList, deleteSelectedInterfaceType);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            InterfaceType sel = interfaceTypeList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "INTERFACE", sel.getName(), sel);
        });
        addRow.add(del);
        addRow.add(propose);
        String sharedTip = "Общие элементы редактируются только через админ-консоль";
        interfaceTypeList.addListSelectionListener(e -> {
            InterfaceType sel = interfaceTypeList.getSelectedValue();
            boolean shared = sel != null && model.isSharedInterfaceType(sel.getId());
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            String tip = shared ? sharedTip : null;
            del.setToolTipText(tip);
            propose.setToolTipText(shared ? "Уже входит в общую библиотеку" : null);
        });
        return (JPanel) UiKit.dynamicSection("Виды интерфейса", listSectionBody(interfaceTypeScroll, addRow));
    }

    // ---- оборудование сигнала: слева тип оборудования, справа его карты-шаблоны ----

    private JPanel buildSignalEquipmentSection() {
        signalCategoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalCategoryList.setCellRenderer(categoryRenderer());
        for (SchemaNodeType t : EQUIPMENT_CATEGORIES) {
            signalCategoryModel.addElement(t);
        }
        signalCategoryList.setSelectedIndex(0);
        signalCategoryScroll.setMinimumSize(new Dimension(150, 120));

        signalPresetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalPresetList.setCellRenderer(new NamedRenderer<EquipmentPreset>(
                EquipmentPreset::getName,
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + "карт: " + p.getCards().size()
                        + (p.getCompany() == null || p.getCompany().isEmpty() ? "" : " · Компания: " + p.getCompany()),
                p -> model.isSharedEquipmentPreset(p.getId())));
        signalPresetScroll.setMinimumSize(new Dimension(180, 120));

        JPanel categoryPane = UiKit.vbox();
        categoryPane.add(UiKit.muted("Категория"));
        categoryPane.add(signalCategoryScroll);

        JPanel left = UiKit.vbox();
        left.add(UiKit.muted("Тип оборудования этой категории"));
        left.add(signalPresetScroll);
        JPanel leftCrud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, null,
                    selectedOrFirstCategory(signalCategoryList)).showDialog();
            if (r != null) {
                tryRun(() -> model.addEquipmentPreset(SchemaMode.SIGNAL, r.category(), r.name(), r.description(), null,
                        r.customCategoryLabel(), r.company()));
            }
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel == null) return;
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, sel).showDialog();
            if (r != null) {
                tryRun(() -> model.updateEquipmentPreset(sel, SchemaMode.SIGNAL, r.category(), r.name(), r.description(),
                        r.customCategoryLabel(), r.company()));
            }
        });
        Runnable deleteSelectedSignalPreset = () -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel != null && confirm("Удалить тип оборудования «" + sel.getName() + "» из библиотеки?")) {
                tryRun(() -> model.deleteEquipmentPreset(sel));
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedSignalPreset.run());
        UiKit.bindDeleteKey(signalPresetList, deleteSelectedSignalPreset);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "EQUIPMENT", sel.getName(), sel);
        });
        leftCrud.add(add);
        leftCrud.add(edit);
        leftCrud.add(del);
        leftCrud.add(propose);
        left.add(leftCrud);

        signalCategoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshSignalPresets();
            }
        });

        signalCardList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalCardList.setCellRenderer(new NamedRenderer<SchemaCard>(SchemaCard::getName, SchemaCard::portsSummary));
        signalCardScroll.setMinimumSize(new Dimension(200, 120));

        JPanel right = UiKit.vbox();
        right.add(UiKit.muted("Карты-шаблоны выбранного оборудования"));
        right.add(signalCardScroll);
        JPanel rightCrud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton cardAdd = new JButton("Добавить карту");
        cardAdd.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "Сначала выберите тип оборудования слева",
                        "Карты", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), sel.getName(),
                    CardsConfigDialog.forPreset(model, sel), model);
            dlg.setVisible(true);
        });
        Runnable deleteSelectedSignalCard = () -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            SchemaCard card = signalCardList.getSelectedValue();
            if (sel == null || card == null) return;
            if (confirm("Удалить карту-шаблон «" + card.getName() + "»?")) {
                tryRun(() -> model.removeCardFromPreset(sel, card.getId()));
            }
        };
        JButton cardDel = new JButton("Удалить карту");
        cardDel.addActionListener(e -> deleteSelectedSignalCard.run());
        UiKit.bindDeleteKey(signalCardList, deleteSelectedSignalCard);
        JButton defaultLoadoutBtn = new JButton("По умолчанию…");
        defaultLoadoutBtn.setToolTipText("Задать комплектацию, с которой будет стартовать сборка узла из этого"
                + " пресета в общей схеме, вместо пустого списка каждый раз");
        defaultLoadoutBtn.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "Сначала выберите тип оборудования слева",
                        "Комплектация по умолчанию", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (sel.getCards().isEmpty()) {
                JOptionPane.showMessageDialog(this, "У этого оборудования ещё нет карт-шаблонов — сначала"
                        + " добавьте хотя бы одну кнопкой «Добавить карту»", "Комплектация по умолчанию",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            List<String> order = new AssembleCardsDialog(topWindow(), sel, sel.getDefaultCardTemplateIds(),
                    "Комплектация по умолчанию — " + sel.getName(), "Сохранить по умолчанию").showDialog();
            if (order != null) {
                tryRun(() -> model.setDefaultCardLoadout(sel, order));
            }
        });
        rightCrud.add(cardAdd);
        rightCrud.add(cardDel);
        rightCrud.add(defaultLoadoutBtn);
        right.add(rightCrud);
        right.add(UiKit.vgap(6));

        String signalPresetSharedTip = "Общие элементы редактируются только через админ-консоль";
        signalPresetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshSignalCards();
            }
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            boolean shared = sel != null && model.isSharedEquipmentPreset(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            cardAdd.setEnabled(sel != null && !shared);
            cardDel.setEnabled(sel != null && !shared);
            defaultLoadoutBtn.setEnabled(sel != null && !shared);
            String tip = shared ? signalPresetSharedTip : null;
            edit.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
            cardAdd.setToolTipText(tip);
            cardDel.setToolTipText(tip);
        });

        JSplitPane presetsAndCards = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        presetsAndCards.setResizeWeight(0.45);
        presetsAndCards.setBorder(BorderFactory.createEmptyBorder());
        presetsAndCards.setContinuousLayout(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryPane, presetsAndCards);
        split.setResizeWeight(0.2);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        // Раньше здесь был отдельный жёсткий предел ширины (Task #95/v1.5) — больше
        // не нужен: ширина уже ограничена на уровне секции (см. applyContentWidth),
        // а BorderLayout/BoxLayout сами передают эту фактическую ширину вниз до
        // split, не требуя дублирующего предела здесь же.

        return (JPanel) UiKit.dynamicSection("Оборудование сигнала (пресеты для схемы)", listSectionBody(split));
    }

    private void refreshSignalCards() {
        EquipmentPreset sel = signalPresetList.getSelectedValue();
        List<SchemaCard> cards = sel == null ? List.of() : sel.getCards();
        syncList(signalCardModel, cards);
        // Внутри JSplitPane — ширина уже ограничена его долей (см. применение
        // contentWidth к секции в applyContentWidth), список сам растягивается
        // на выделенную ему часть (capWidth=false), а не на отдельно заданную ширину.
        ListSizing.fit(signalCardList, signalCardScroll, 2, 6, false);
    }

    /** Пересобирает список пресетов ПИТАНИЯ под текущую выбранную категорию (левая
     *  панель дерева библиотеки) — вызывается и из refresh(), и при смене категории. */
    private void refreshPowerPresets() {
        EquipmentPreset selPreset = powerPresetList.getSelectedValue();
        SchemaNodeType category = selectedOrFirstCategory(powerCategoryList);
        List<EquipmentPreset> presets = presetsForModeAndCategory(SchemaMode.POWER, category);
        syncList(powerPresetModel, presets);
        ListSizing.fit(powerPresetList, powerPresetScroll, 2, 8, false);
        if (selPreset != null && presets.contains(selPreset)) {
            powerPresetList.setSelectedValue(selPreset, false);
        }
    }

    /** То же для СИГНАЛА — плюс пересобирает список карт справа (см. refreshSignalCards),
     *  т.к. смена категории обычно меняет и текущий выбранный пресет (карты — его). */
    private void refreshSignalPresets() {
        EquipmentPreset selPreset = signalPresetList.getSelectedValue();
        SchemaNodeType category = selectedOrFirstCategory(signalCategoryList);
        List<EquipmentPreset> presets = presetsForModeAndCategory(SchemaMode.SIGNAL, category);
        syncList(signalPresetModel, presets);
        ListSizing.fit(signalPresetList, signalPresetScroll, 2, 8, false);
        if (selPreset != null && presets.contains(selPreset)) {
            signalPresetList.setSelectedValue(selPreset, false);
        }
        refreshSignalCards();
    }

    private void refresh() {
        int w = listWidth();
        int rw = rendererWidth(w);
        libRenderer.setFixedWidth(rw);
        syncList(libModel, model.getCabinetTypes());
        ListSizing.fit(libList, libScroll, 2, 8, w);
        recapSection(cabinetsSection);
        ctrlLibRenderer.setFixedWidth(rw);
        syncList(ctrlLibModel, model.getControllerTypes());
        ListSizing.fit(ctrlLibList, ctrlLibScroll, 2, 6, w);
        recapSection(controllersSection);
        ListSizing.fit(powerCategoryList, powerCategoryScroll, EQUIPMENT_CATEGORIES.length,
                EQUIPMENT_CATEGORIES.length, false);
        refreshPowerPresets();
        recapSection(powerPresetsSection);

        ListSizing.fit(signalCategoryList, signalCategoryScroll, EQUIPMENT_CATEGORIES.length,
                EQUIPMENT_CATEGORIES.length, false);
        refreshSignalPresets();
        recapSection(signalEquipmentSection);

        cableRenderer.setFixedWidth(rw);
        syncList(cableModel, model.getCableTypes());
        ListSizing.fit(cableList, cableScroll, 2, 6, w);
        recapSection(cableSection);

        cableLengthProfileRenderer.setFixedWidth(rw);
        syncList(cableLengthProfileModel, model.getCableLengthProfiles());
        ListSizing.fit(cableLengthProfileList, cableLengthProfileScroll, 2, 6, w);
        recapSection(cableLengthProfileSection);

        interfaceTypeRenderer.setFixedWidth(rw);
        syncList(interfaceTypeModel, model.getInterfaceTypes());
        ListSizing.fit(interfaceTypeList, interfaceTypeScroll, 2, 6, w);
        recapSection(interfaceTypeSection);
    }

    private List<EquipmentPreset> presetsForModeAndCategory(SchemaMode mode, SchemaNodeType category) {
        List<EquipmentPreset> result = new java.util.ArrayList<>();
        for (EquipmentPreset p : model.getEquipmentPresets()) {
            if (p.getMode() == mode && p.getCategory() == category) {
                result.add(p);
            }
        }
        return result;
    }

    private static <T> void syncList(DefaultListModel<T> lm, List<T> items) {
        lm.clear();
        for (T i : items) {
            lm.addElement(i);
        }
    }

    private java.awt.Window topWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }

    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Подтверждение", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    private void tryRun(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
