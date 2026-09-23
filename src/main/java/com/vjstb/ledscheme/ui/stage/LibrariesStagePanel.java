package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableLengthProfile;
import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.NetworkDeviceType;
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
import com.vjstb.ledscheme.ui.NetworkDeviceTypeDialog;
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
import javax.swing.JComboBox;
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

    /** Ширина зоны попадания по чекбоксу-тумблеру палитры в начале строки
     *  {@link #libList} (см. {@link CabinetPaletteCellRenderer}) — с запасом
     *  больше реального {@code JCheckBox.getPreferredSize()} (обычно ~20px),
     *  чтобы не мазать мимо на разных L&F/масштабах интерфейса. */
    private static final int CABINET_PALETTE_CHECK_WIDTH = 28;

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

    private final DefaultListModel<NetworkDeviceType> networkDeviceModel = new DefaultListModel<>();
    private final JList<NetworkDeviceType> networkDeviceList = new JList<>(networkDeviceModel);
    private final JScrollPane networkDeviceScroll = new JScrollPane(networkDeviceList);

    private NamedRenderer<CabinetType> libRenderer;
    private NamedRenderer<ControllerType> ctrlLibRenderer;
    private NamedRenderer<EquipmentPreset> powerPresetRenderer;
    private NamedRenderer<CableType> cableRenderer;
    private NamedRenderer<CableLengthProfile> cableLengthProfileRenderer;
    private NamedRenderer<InterfaceType> interfaceTypeRenderer;
    private NamedRenderer<NetworkDeviceType> networkDeviceRenderer;

    private javax.swing.JComponent exportImportSection;
    private javax.swing.JComponent cabinetsSection;
    private javax.swing.JComponent controllersSection;
    private javax.swing.JComponent powerPresetsSection;
    private javax.swing.JComponent signalEquipmentSection;
    private javax.swing.JComponent cableSection;
    private javax.swing.JComponent cableLengthProfileSection;
    private javax.swing.JComponent interfaceTypeSection;
    private javax.swing.JComponent networkDeviceSection;

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
        networkDeviceSection = buildNetworkDeviceLibrary();
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
        body.add(UiKit.vgap(10));
        body.add(networkDeviceSection);
        body.add(javax.swing.Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        for (JScrollPane sp : new JScrollPane[]{libScroll, ctrlLibScroll, powerPresetScroll, cableScroll,
                cableLengthProfileScroll, interfaceTypeScroll, networkDeviceScroll}) {
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
        libList.setCellRenderer(new CabinetPaletteCellRenderer());
        // Быстрый тумблер видимости в палитре прямо в строке списка (запрос
        // пользователя) — без открытия формы редактирования, см.
        // CabinetPaletteCellRenderer (чекбокс слева) + этот клик-хэндлер
        // (JList не умеет собственные интерактивные ячейки, как JTable —
        // попадание по чекбоксу вычисляется вручную по границам ячейки).
        // Только для личных типов — общие редактируются исключительно через
        // синк/админ-консоль (см. блок ниже про edit/del.setEnabled(!shared)).
        libList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int index = libList.locationToIndex(e.getPoint());
                if (index < 0) return;
                java.awt.Rectangle bounds = libList.getCellBounds(index, index);
                if (bounds == null || e.getX() - bounds.x > CABINET_PALETTE_CHECK_WIDTH) return;
                CabinetType ct = libModel.getElementAt(index);
                if (model.isSharedCabinetType(ct.getId())) return;
                CabinetType edited = ct.copy();
                edited.setVisibleInPalette(!ct.isVisibleInPalette());
                tryRun(() -> model.updateCabinetType(edited));
                e.consume();
            }
        });

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
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего кабинета и сразу открыть её на редактирование —"
                + " оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно предложатся как патч"
                + " общего элемента.");
        copyEdit.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel == null || !model.isSharedCabinetType(sel.getId())) return;
            List<String> names = model.getCabinetTypes().stream().map(CabinetType::getName).toList();
            CabinetType result = copySharedAndEdit(sel.getId(), "CABINET",
                    () -> {
                        CabinetType c = sel.copy();
                        c.setId(java.util.UUID.randomUUID().toString());
                        c.setName(uniqueCopyName(sel.getName(), names));
                        return c;
                    },
                    model::addCabinetType,
                    seed -> new CabinetTypeDialog(topWindow(), model, seed).showDialog(),
                    model::updateCabinetType,
                    CabinetType::getName);
            libList.setSelectedValue(result, true);
        });
        JButton enableAllInPalette = new JButton("Показать все в палитре");
        enableAllInPalette.setToolTipText("Включить видимость в палитре (меню выбора типа кабинета) сразу для всех"
                + " личных типов — общие типы не трогает, ими управляет админ-консоль.");
        enableAllInPalette.addActionListener(e -> tryRun(model::enableAllInPalette));
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        crud.add(propose);
        crud.add(copyEdit);
        crud.add(enableAllInPalette);
        String sharedTip = "Общие элементы редактируются только через админ-консоль";
        libList.addListSelectionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            boolean shared = sel != null && model.isSharedCabinetType(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            copyEdit.setEnabled(shared);
            edit.setToolTipText(shared ? sharedTip : null);
            del.setToolTipText(shared ? sharedTip : null);
            propose.setToolTipText(shared ? sharedTip : null);
        });
        return (JPanel) UiKit.dynamicSection("Библиотека кабинетов", listSectionBody(libScroll, crud));
    }

    /** Оборачивает {@link #libRenderer} чекбоксом слева, отражающим {@code
     *  CabinetType#isVisibleInPalette()} — клик по нему ловит отдельный
     *  {@code MouseAdapter} на {@link #libList} (см. {@link #buildLibrary()}),
     *  сам чекбокс здесь только рисуется (стандартный для рендереров списков
     *  Swing приём "печати" одного переиспользуемого компонента — не настоящий
     *  интерактивный элемент внутри ячейки). */
    private final class CabinetPaletteCellRenderer implements javax.swing.ListCellRenderer<CabinetType> {
        private final javax.swing.JCheckBox check = new javax.swing.JCheckBox();
        private final JPanel row = new JPanel(new BorderLayout(2, 0));

        CabinetPaletteCellRenderer() {
            check.setOpaque(false);
            check.setFocusPainted(false);
            check.setToolTipText("Показывать этот кабинет в меню выбора типа (радиалка/дропдаун) — палитра");
            row.setOpaque(true);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends CabinetType> list, CabinetType value,
                int index, boolean isSelected, boolean cellHasFocus) {
            java.awt.Component label = libRenderer.getListCellRendererComponent(list, value, index, isSelected,
                    cellHasFocus);
            row.removeAll();
            row.add(check, BorderLayout.WEST);
            row.add(label, BorderLayout.CENTER);
            row.setBackground(label.getBackground());
            check.setBackground(label.getBackground());
            boolean shared = value != null && model.isSharedCabinetType(value.getId());
            check.setEnabled(!shared);
            check.setSelected(value != null && value.isVisibleInPalette());
            return row;
        }
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
            if (ct != null) {
                tryRun(() -> model.addControllerType(ct));
                // refresh() (вызванный listener'ом model.changed() внутри tryRun выше)
                // каждый раз пересобирает ctrlLibModel с нуля (см. syncList) и теряет
                // выделение — без этой строки только что добавленный контроллер не
                // выделялся, и «Карты…»/«Изменить» молча не работали, пока пользователь
                // не кликнет по нему в списке сам (баг-репорт в чате 2026-09-23).
                ctrlLibList.setSelectedValue(ct, true);
            }
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
            boolean shared = model.isSharedControllerType(sel.getId());
            CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), sel.getName(),
                    CardsConfigDialog.forController(model, sel), model, shared);
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
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего контроллера (включая карты) и сразу открыть её на"
                + " редактирование — оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно"
                + " предложатся как патч общего элемента.");
        copyEdit.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null || !model.isSharedControllerType(sel.getId())) return;
            List<String> names = model.getControllerTypes().stream().map(ControllerType::getName).toList();
            ControllerType result = copySharedAndEdit(sel.getId(), "CONTROLLER",
                    () -> {
                        ControllerType c = sel.copy();
                        c.setId(java.util.UUID.randomUUID().toString());
                        c.setName(uniqueCopyName(sel.getName(), names));
                        return c;
                    },
                    model::addControllerType,
                    seed -> new ControllerTypeDialog(topWindow(), model, seed).showDialog(),
                    model::updateControllerType,
                    ControllerType::getName);
            ctrlLibList.setSelectedValue(result, true);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        crud.add(propose);
        crud.add(copyEdit);
        String ctrlSharedTip = "Общие элементы редактируются только через админ-консоль";
        ctrlLibList.addListSelectionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            boolean shared = sel != null && model.isSharedControllerType(sel.getId());
            edit.setEnabled(sel != null && !shared);
            cardsBtn.setEnabled(sel != null);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            copyEdit.setEnabled(shared);
            String tip = shared ? ctrlSharedTip : null;
            edit.setToolTipText(tip);
            cardsBtn.setToolTipText(shared ? "Общий элемент — только просмотр карт" : null);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
            copyEdit.setToolTipText(shared ? null : "Доступно только для общих элементов");
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
            boolean shared = model.isSharedEquipmentPreset(sel.getId());
            String dlgTitle = sel.getName().isEmpty() ? model.categoryLabel(sel.getCategory()) : sel.getName();
            if (mode == SchemaMode.POWER) {
                PowerConnectorsConfigDialog dlg = new PowerConnectorsConfigDialog(topWindow(), dlgTitle,
                        PowerConnectorsConfigDialog.forPreset(model, sel), model, shared);
                dlg.setVisible(true);
            } else {
                CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), dlgTitle,
                        CardsConfigDialog.forPreset(model, sel), model, shared);
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
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего пресета (включая разъёмы/карты) и сразу открыть её"
                + " на редактирование — оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно"
                + " предложатся как патч общего элемента.");
        copyEdit.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel == null || !model.isSharedEquipmentPreset(sel.getId())) return;
            List<String> names = model.getEquipmentPresets().stream().map(EquipmentPreset::getName).toList();
            EquipmentPreset copy = model.addEquipmentPresetCopy(sel, uniqueCopyName(sel.getName(), names));
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, copy).showDialog();
            if (r != null) {
                tryRun(() -> model.updateEquipmentPreset(copy, mode, r.category(), r.name(), r.description(),
                        r.customCategoryLabel(), r.company()));
            }
            if (settings.getSettings().getAuthToken() != null) {
                ProposeDialog.show(topWindow(), settings, "EQUIPMENT", copy.getName(), copy, sel.getId());
            }
            presetList.setSelectedValue(copy, true);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        crud.add(propose);
        crud.add(copyEdit);
        String presetSharedTip = "Общие элементы редактируются только через админ-консоль";
        presetList.addListSelectionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            boolean shared = sel != null && model.isSharedEquipmentPreset(sel.getId());
            edit.setEnabled(sel != null && !shared);
            cardsBtn.setEnabled(sel != null);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            copyEdit.setEnabled(shared);
            String tip = shared ? presetSharedTip : null;
            edit.setToolTipText(tip);
            cardsBtn.setToolTipText(shared ? "Общий элемент — только просмотр" : null);
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
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего каталога длин и сразу открыть её на редактирование —"
                + " оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно предложатся как патч"
                + " общего элемента.");
        copyEdit.addActionListener(e -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            if (sel == null || !model.isSharedCableLengthProfile(sel.getId())) return;
            List<String> names = model.getCableLengthProfiles().stream().map(CableLengthProfile::getName).toList();
            CableLengthProfile result = copySharedAndEdit(sel.getId(), "CABLE_LENGTH_PROFILE",
                    () -> {
                        CableLengthProfile c = sel.copy();
                        c.setId(java.util.UUID.randomUUID().toString());
                        c.setName(uniqueCopyName(sel.getName(), names));
                        return c;
                    },
                    model::addCableLengthProfile,
                    seed -> new CableLengthProfileDialog(topWindow(), seed).showDialog(),
                    model::updateCableLengthProfile,
                    CableLengthProfile::getName);
            cableLengthProfileList.setSelectedValue(result, true);
        });
        addRow.add(add);
        addRow.add(edit);
        addRow.add(del);
        addRow.add(propose);
        addRow.add(copyEdit);
        String cableLengthSharedTip = "Общие элементы редактируются только через админ-консоль";
        cableLengthProfileList.addListSelectionListener(e -> {
            CableLengthProfile sel = cableLengthProfileList.getSelectedValue();
            boolean shared = sel != null && model.isSharedCableLengthProfile(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            copyEdit.setEnabled(shared);
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

    /** Сентинел «роль не задана» в комбобоксе — сам JComboBox<InterfaceRole> с
     *  {@code null}-элементом работает штатно (см. renderer ниже), выделять
     *  отдельную строку-заглушку не нужно, в отличие от {@code NO_SUBCATEGORY}
     *  в EquipmentPresetDialog (там список — String, а не enum). */
    private static final InterfaceRole[] ROLE_OPTIONS_WITH_NONE;
    static {
        InterfaceRole[] values = InterfaceRole.values();
        ROLE_OPTIONS_WITH_NONE = new InterfaceRole[values.length + 1];
        System.arraycopy(values, 0, ROLE_OPTIONS_WITH_NONE, 1, values.length);
        // [0] остаётся null — «не задана».
    }

    private JPanel buildInterfaceTypeSection() {
        interfaceTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        interfaceTypeRenderer = new NamedRenderer<InterfaceType>(
                InterfaceType::getName,
                t -> (t.getVersions().isEmpty() ? "" : String.join(", ", t.getVersions()) + " · ")
                        + "Роль по умолчанию: " + (t.getDefaultRole() == null ? "не задана" : t.getDefaultRole().getLabel()),
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

        // Роль по умолчанию (docs/schema-ports-rework/PLAN.md, задача T5.1) — единственное
        // новое поле InterfaceType, которое стоит дать проставить прямо здесь, БЕЗ
        // отдельного диалога: у видов интерфейса в клиенте вообще нет полноценного
        // редактора — имя/версии добавляются/переименовываются ТОЛЬКО через
        // админ-консоль (Task #135/v2.0, см. комментарий у секции ниже) и полноценный
        // редактор роли по умолчанию для ОБЩЕЙ библиотеки — тоже её забота (T5.2). Но
        // ждать этого не нужно: личные (несинхронизированные) записи можно пометить
        // сразу же здесь — например, до предложения нового вида в общую библиотеку.
        JComboBox<InterfaceRole> defaultRoleCombo = new JComboBox<>(ROLE_OPTIONS_WITH_NONE);
        defaultRoleCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                setText(value == null ? "не задана" : ((InterfaceRole) value).getLabel());
                return this;
            }
        });
        JButton applyRole = new JButton("Применить роль");
        applyRole.addActionListener(e -> {
            InterfaceType sel = interfaceTypeList.getSelectedValue();
            if (sel == null || model.isSharedInterfaceType(sel.getId())) {
                return;
            }
            applyInterfaceTypeDefaultRole(sel, (InterfaceRole) defaultRoleCombo.getSelectedItem());
        });
        addRow.add(new JLabel("Роль по умолчанию:"));
        addRow.add(defaultRoleCombo);
        addRow.add(applyRole);
        addRow.add(del);
        addRow.add(propose);
        String sharedTip = "Общие элементы редактируются только через админ-консоль";
        interfaceTypeList.addListSelectionListener(e -> {
            InterfaceType sel = interfaceTypeList.getSelectedValue();
            boolean shared = sel != null && model.isSharedInterfaceType(sel.getId());
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            defaultRoleCombo.setEnabled(sel != null && !shared);
            applyRole.setEnabled(sel != null && !shared);
            if (sel != null) {
                defaultRoleCombo.setSelectedItem(sel.getDefaultRole());
            }
            String tip = shared ? sharedTip : null;
            del.setToolTipText(tip);
            propose.setToolTipText(shared ? "Уже входит в общую библиотеку" : null);
            defaultRoleCombo.setToolTipText(tip);
            applyRole.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Виды интерфейса", listSectionBody(interfaceTypeScroll, addRow));
    }

    /** Проставляет роль по умолчанию НАПРЯМУЮ на объект вида интерфейса и сохраняет
     *  личную библиотеку тем же способом, что {@code AppModel.persist()} делает под
     *  капотом — {@code AppModel} на момент задачи T5.1 «горячий» файл (правит
     *  параллельный агент этапов 2-4 схемы, PLAN.md §0 правило 10) и добавлять туда
     *  новый мутатор нельзя, а готового метода для этого поля там ещё нет ({@code
     *  InterfaceType} в клиенте вообще не имеет ни одного add/update-метода — виды
     *  интерфейса добавляются/переименовываются только через админ-консоль). {@code
     *  sel} — живая ссылка на элемент {@code workspace.getLibrary().getInterfaceTypes()}
     *  (см. {@code AppModel.getInterfaceTypes()}), а не копия, поэтому прямая мутация
     *  поля корректна; {@code LibraryStore} с тем же путём к файлу, что использует
     *  {@code AppModel} (родительская папка workspace-файла, см. конструктор AppModel),
     *  замыкает сохранение на диск. Когда AppModel перестанет быть «горячим» — стоит
     *  завести туда обычный updateInterfaceTypeDefaultRole(...) и убрать этот обход. */
    private void applyInterfaceTypeDefaultRole(InterfaceType type, InterfaceRole role) {
        type.setDefaultRole(role);
        java.io.File libraryFile = new java.io.File(model.getStore().getWorkspaceFile().getParentFile(), "library.json");
        new com.vjstb.ledscheme.store.LibraryStore(libraryFile).save(model.getWorkspace().getLibrary());
        refresh();
    }

    // ---- сетевое оборудование (каталог для Сетевого менеджера, ui.NetworkManagerPanel) ----
    //      Раньше был виден/редактируем ТОЛЬКО изнутри Сетевого менеджера (палитра
    //      каталога устройств, кнопка "+ Новый тип..." — только создание, без
    //      Изменить/Удалить/Предложить), хотя весь CRUD (add/update/delete,
    //      isSharedNetworkDeviceType) в AppModel уже был реализован — баг-репорт
    //      "сетевое оборудование не видно в библиотеках и недоступно для
    //      редактирования". Секция здесь — точная копия остальных (см. buildLibrary),
    //      просто даёт этому каталогу тот же полноценный доступ, что у CabinetType/
    //      ControllerType/CableType, вместо единственной точки входа через палитру.

    private JPanel buildNetworkDeviceLibrary() {
        networkDeviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        networkDeviceRenderer = new NamedRenderer<NetworkDeviceType>(
                NetworkDeviceType::getName,
                t -> t.getCategory().getLabel() + " · " + Math.max(1, t.getEthernetPortCount()) + " сет. порт(ов)"
                        + (t.getOpticalPortCount() > 0 ? " · " + t.getOpticalPortCount() + " опт. порт(ов)" : "")
                        + (t.getDescription() == null || t.getDescription().isEmpty() ? "" : " · " + t.getDescription())
                        + (t.getCompany() == null || t.getCompany().isEmpty() ? "" : " · Компания: " + t.getCompany()),
                t -> model.isSharedNetworkDeviceType(t.getId()));
        networkDeviceList.setCellRenderer(networkDeviceRenderer);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            NetworkDeviceType t = new NetworkDeviceTypeDialog(topWindow(), null).showDialog();
            if (t != null) tryRun(() -> model.addNetworkDeviceType(t));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            NetworkDeviceType sel = networkDeviceList.getSelectedValue();
            if (sel == null) return;
            NetworkDeviceType t = new NetworkDeviceTypeDialog(topWindow(), sel).showDialog();
            if (t != null) tryRun(() -> model.updateNetworkDeviceType(t));
        });
        Runnable deleteSelectedNetworkDeviceType = () -> {
            NetworkDeviceType sel = networkDeviceList.getSelectedValue();
            if (sel != null && confirm("Удалить тип сетевого оборудования «" + sel.getName() + "» из библиотеки?")) {
                tryRun(() -> model.deleteNetworkDeviceType(sel.getId()));
            }
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedNetworkDeviceType.run());
        UiKit.bindDeleteKey(networkDeviceList, deleteSelectedNetworkDeviceType);
        JButton propose = new JButton("Предложить…");
        propose.addActionListener(e -> {
            NetworkDeviceType sel = networkDeviceList.getSelectedValue();
            if (sel != null) ProposeDialog.show(topWindow(), settings, "NETWORK_DEVICE", sel.getName(), sel);
        });
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего типа сетевого оборудования и сразу открыть её на"
                + " редактирование — оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно"
                + " предложатся как патч общего элемента.");
        copyEdit.addActionListener(e -> {
            NetworkDeviceType sel = networkDeviceList.getSelectedValue();
            if (sel == null || !model.isSharedNetworkDeviceType(sel.getId())) return;
            List<String> names = model.getNetworkDeviceTypes().stream().map(NetworkDeviceType::getName).toList();
            NetworkDeviceType result = copySharedAndEdit(sel.getId(), "NETWORK_DEVICE",
                    () -> {
                        NetworkDeviceType t = sel.copy();
                        t.setId(java.util.UUID.randomUUID().toString());
                        t.setName(uniqueCopyName(sel.getName(), names));
                        return t;
                    },
                    model::addNetworkDeviceType,
                    seed -> new NetworkDeviceTypeDialog(topWindow(), seed).showDialog(),
                    model::updateNetworkDeviceType,
                    NetworkDeviceType::getName);
            networkDeviceList.setSelectedValue(result, true);
        });
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        crud.add(propose);
        crud.add(copyEdit);
        String sharedTip = "Общие элементы редактируются только через админ-консоль";
        networkDeviceList.addListSelectionListener(e -> {
            NetworkDeviceType sel = networkDeviceList.getSelectedValue();
            boolean shared = sel != null && model.isSharedNetworkDeviceType(sel.getId());
            edit.setEnabled(sel != null && !shared);
            del.setEnabled(sel != null && !shared);
            propose.setEnabled(sel != null && !shared);
            copyEdit.setEnabled(shared);
            String tip = shared ? sharedTip : null;
            edit.setToolTipText(tip);
            del.setToolTipText(tip);
            propose.setToolTipText(tip);
        });
        return (JPanel) UiKit.dynamicSection("Библиотека сетевого оборудования",
                listSectionBody(networkDeviceScroll, crud));
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
        JButton copyEdit = new JButton("Скопировать и править…");
        copyEdit.setToolTipText("Создать личную копию общего типа оборудования (включая карты) и сразу открыть"
                + " её на редактирование — оригинал не меняется; если вы вошли в аккаунт, эти же правки заодно"
                + " предложатся как патч общего элемента.");
        copyEdit.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel == null || !model.isSharedEquipmentPreset(sel.getId())) return;
            List<String> names = model.getEquipmentPresets().stream().map(EquipmentPreset::getName).toList();
            EquipmentPreset copy = model.addEquipmentPresetCopy(sel, uniqueCopyName(sel.getName(), names));
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), model, copy).showDialog();
            if (r != null) {
                tryRun(() -> model.updateEquipmentPreset(copy, SchemaMode.SIGNAL, r.category(), r.name(),
                        r.description(), r.customCategoryLabel(), r.company()));
            }
            if (settings.getSettings().getAuthToken() != null) {
                ProposeDialog.show(topWindow(), settings, "EQUIPMENT", copy.getName(), copy, sel.getId());
            }
            signalPresetList.setSelectedValue(copy, true);
        });
        leftCrud.add(add);
        leftCrud.add(edit);
        leftCrud.add(del);
        leftCrud.add(propose);
        leftCrud.add(copyEdit);
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
            copyEdit.setEnabled(shared);
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

        networkDeviceRenderer.setFixedWidth(rw);
        syncList(networkDeviceModel, model.getNetworkDeviceTypes());
        ListSizing.fit(networkDeviceList, networkDeviceScroll, 2, 6, w);
        recapSection(networkDeviceSection);
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

    /** Имя для личной копии общего элемента библиотеки, гарантированно не
     *  совпадающее (без учёта регистра) ни с одним из {@code existingNames} — все
     *  типы библиотеки здесь требуют уникальное имя при добавлении (см.
     *  requireUniqueXxxName в AppModel), а копия по умолчанию называется как
     *  оригинал. */
    private static String uniqueCopyName(String baseName, List<String> existingNames) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (String n : existingNames) {
            taken.add(n.toLowerCase());
        }
        String candidate = baseName + " (копия)";
        int n = 2;
        while (taken.contains(candidate.toLowerCase())) {
            candidate = baseName + " (копия " + n + ")";
            n++;
        }
        return candidate;
    }

    /** «Скопировать и править…» для общего (расшаренного) элемента библиотеки — тех
     *  видов, где редактор возвращает готовый отредактированный объект целиком
     *  (CABINET/CONTROLLER/CABLE_LENGTH_PROFILE/NETWORK_DEVICE; у EQUIPMENT редактор
     *  отдаёт только часть полей отдельным Result — там копия и правка собраны
     *  прямо в месте вызова, без этого общего метода). Раньше правка общего элемента
     *  либо не давала выполнить, либо (только "Предложить…") сразу пыталась уйти на
     *  сервер и требовала входа в аккаунт, теряя правки при отказе — см. обсуждение
     *  в чате 2026-09-23. Теперь: 1) сразу делает независимую личную копию
     *  {@code makeCopy} (новый id/имя — оригинал не трогаем, правило 4 CLAUDE.md),
     *  2) сохраняет её через {@code addCopy}, 3) тут же открывает обычный редактор
     *  {@code editCopy} НАД копией — можно отменить, копия при этом остаётся с
     *  исходными значениями, 4) если правки сохранены — применяет их через
     *  {@code saveEdit}, 5) если пользователь авторизован — параллельно (не вместо
     *  локальной копии) предлагает те же правки как патч исходного общего элемента
     *  через {@link ProposeDialog} с {@code targetItemId = sharedId}. Возвращает
     *  живую ссылку на копию (её и нужно выделить в списке — {@code editCopy} может
     *  вернуть другой объект-черновик с тем же id, но реально в библиотеке остаётся
     *  {@code copy}, отредактированный на месте через saveEdit). */
    private <T> T copySharedAndEdit(String sharedId, String kind,
                                     java.util.function.Supplier<T> makeCopy,
                                     java.util.function.Consumer<T> addCopy,
                                     java.util.function.Function<T, T> editCopy,
                                     java.util.function.Consumer<T> saveEdit,
                                     java.util.function.Function<T, String> nameOf) {
        T copy = makeCopy.get();
        tryRun(() -> addCopy.accept(copy));
        T edited = editCopy.apply(copy);
        if (edited != null) {
            tryRun(() -> saveEdit.accept(edited));
        }
        if (settings.getSettings().getAuthToken() != null) {
            ProposeDialog.show(topWindow(), settings, kind, nameOf.apply(copy), copy, sharedId);
        }
        return copy;
    }

    private void tryRun(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
