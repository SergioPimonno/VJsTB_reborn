package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CableType;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.ui.CabinetTypeDialog;
import com.vjstb.ledscheme.ui.CabinetTypeRenderer;
import com.vjstb.ledscheme.ui.CardsConfigDialog;
import com.vjstb.ledscheme.ui.ControllerTypeDialog;
import com.vjstb.ledscheme.ui.EquipmentPresetDialog;
import com.vjstb.ledscheme.ui.ListSizing;
import com.vjstb.ledscheme.ui.NamedRenderer;
import com.vjstb.ledscheme.ui.PowerConnectorsConfigDialog;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
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

    private final DefaultListModel<CabinetType> libModel = new DefaultListModel<>();
    private final JList<CabinetType> libList = new JList<>(libModel);
    private final JScrollPane libScroll = new JScrollPane(libList);

    private final DefaultListModel<ControllerType> ctrlLibModel = new DefaultListModel<>();
    private final JList<ControllerType> ctrlLibList = new JList<>(ctrlLibModel);
    private final JScrollPane ctrlLibScroll = new JScrollPane(ctrlLibList);

    private final DefaultListModel<EquipmentPreset> powerPresetModel = new DefaultListModel<>();
    private final JList<EquipmentPreset> powerPresetList = new JList<>(powerPresetModel);
    private final JScrollPane powerPresetScroll = new JScrollPane(powerPresetList);

    private final DefaultListModel<EquipmentPreset> signalPresetModel = new DefaultListModel<>();
    private final JList<EquipmentPreset> signalPresetList = new JList<>(signalPresetModel);
    private final JScrollPane signalPresetScroll = new JScrollPane(signalPresetList);

    private final DefaultListModel<SchemaCard> signalCardModel = new DefaultListModel<>();
    private final JList<SchemaCard> signalCardList = new JList<>(signalCardModel);
    private final JScrollPane signalCardScroll = new JScrollPane(signalCardList);

    private final DefaultListModel<CableType> cableModel = new DefaultListModel<>();
    private final JList<CableType> cableList = new JList<>(cableModel);
    private final JScrollPane cableScroll = new JScrollPane(cableList);

    private NamedRenderer<CabinetType> libRenderer;
    private NamedRenderer<ControllerType> ctrlLibRenderer;
    private NamedRenderer<EquipmentPreset> powerPresetRenderer;
    private NamedRenderer<CableType> cableRenderer;

    private javax.swing.JComponent exportImportSection;
    private javax.swing.JComponent cabinetsSection;
    private javax.swing.JComponent controllersSection;
    private javax.swing.JComponent powerPresetsSection;
    private javax.swing.JComponent signalEquipmentSection;
    private javax.swing.JComponent cableSection;

    /** Ширина содержимого этапа (окно минус вертикальный скроллбар минус паддинг
     *  body) — пересчитывается живьём при ресайзе (см. конструктор), а не
     *  фиксируется один раз при первой сборке, иначе список либо "утекает" за
     *  край при узком окне, либо оставляет пустое место при широком (Task #100 —
     *  повторный баг-репорт после промежуточного фикса с жёстким capWidth=720). */
    private int contentWidth = 700;

    public LibrariesStagePanel(AppModel model) {
        this.model = model;
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
                "Экспорт / импорт библиотек",
                new com.vjstb.ledscheme.ui.LibraryExportImportPanel(model));
        cabinetsSection = buildLibrary();
        controllersSection = buildControllerLibrary();
        powerPresetsSection = buildEquipmentPresetSection(SchemaMode.POWER,
                "Оборудование питания (пресеты для схемы)", powerPresetList, powerPresetScroll);
        signalEquipmentSection = buildSignalEquipmentSection();
        cableSection = buildCableLibrary();
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
        body.add(javax.swing.Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        for (JScrollPane sp : new JScrollPane[]{libScroll, ctrlLibScroll, powerPresetScroll, cableScroll}) {
            sp.setMinimumSize(new Dimension(200, 80));
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

    /** Пересчитывает МАКСИМАЛЬНУЮ ширину (см. applyContentWidth) И высоту секции по
     *  ЖИВОМУ preferredSize (не setPreferredSize — тот бы заморозил значение и
     *  сломал пересчёт при следующем изменении числа строк списка внутри) — для
     *  секций со списком, чьё содержимое (число строк) меняется после сборки. */
    private void recapSection(javax.swing.JComponent section) {
        section.setMaximumSize(new Dimension(contentWidth, section.getPreferredSize().height));
    }

    // ---- библиотека кабинетов ----

    private JPanel buildLibrary() {
        JPanel body = UiKit.vbox();
        libList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libRenderer = new NamedRenderer<CabinetType>(CabinetType::getName, ct ->
                UiKit.fmt(ct.getWidthMm()) + "×" + UiKit.fmt(ct.getHeightMm()) + "мм · "
                        + ct.getResolutionWidth() + "×" + ct.getResolutionHeight() + "px · "
                        + UiKit.fmt(ct.getPowerConsumptionW()) + "Вт · " + UiKit.fmt(ct.getWeightKg()) + "кг");
        libList.setCellRenderer(libRenderer);
        body.add(libScroll);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            CabinetType ct = new CabinetTypeDialog(topWindow(), null).showDialog();
            if (ct != null) tryRun(() -> model.addCabinetType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel == null) return;
            CabinetType ct = new CabinetTypeDialog(topWindow(), sel).showDialog();
            if (ct != null) tryRun(() -> model.updateCabinetType(ct));
        });
        Runnable deleteSelectedCabinetType = () -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel != null && confirm("Удалить кабинет из библиотеки?")) tryRun(() -> model.deleteCabinetType(sel.getId()));
        };
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> deleteSelectedCabinetType.run());
        UiKit.bindDeleteKey(libList, deleteSelectedCabinetType);
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        body.add(crud);
        return (JPanel) UiKit.dynamicSection("Библиотека кабинетов", body);
    }

    // ---- библиотека контроллеров (аналог SmartLCT) ----

    private JPanel buildControllerLibrary() {
        JPanel body = UiKit.vbox();
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
                        + (ct.isLoopPort() ? " · Loop" : ""));
        ctrlLibList.setCellRenderer(ctrlLibRenderer);
        body.add(ctrlLibScroll);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            ControllerType ct = new ControllerTypeDialog(topWindow(), null).showDialog();
            if (ct != null) tryRun(() -> model.addControllerType(ct));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null) return;
            ControllerType ct = new ControllerTypeDialog(topWindow(), sel).showDialog();
            if (ct != null) tryRun(() -> model.updateControllerType(ct));
        });
        JButton cardsBtn = new JButton("Карты…");
        cardsBtn.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel == null) return;
            CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), sel.getName(),
                    CardsConfigDialog.forController(model, sel));
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
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        body.add(crud);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Для модульных контроллеров (например, Novastar H-серии) задайте карты"
                + " вывода вместо ручного числа портов — «Вых. портов» выше тогда считается по картам.</html>"));
        return (JPanel) UiKit.dynamicSection("Библиотека контроллеров", body);
    }

    // ---- пресеты оборудования (для схемы питания/сигнала) ----

    private JPanel buildEquipmentPresetSection(SchemaMode mode, String title, JList<EquipmentPreset> presetList,
                                                JScrollPane presetScroll) {
        JPanel body = UiKit.vbox();
        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        NamedRenderer<EquipmentPreset> renderer = new NamedRenderer<EquipmentPreset>(
                p -> p.getName() + " (" + p.getCategory().getLabel() + ")",
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + (mode == SchemaMode.POWER
                                ? "разъёмов: " + p.getPowerConnectors().size()
                                : "карт: " + p.getCards().size()));
        presetList.setCellRenderer(renderer);
        if (mode == SchemaMode.POWER) {
            powerPresetRenderer = renderer;
        }
        body.add(presetScroll);

        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), null).showDialog();
            if (r != null) tryRun(() -> model.addEquipmentPreset(mode, r.category(), r.name(), r.description(), null));
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel == null) return;
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), sel).showDialog();
            if (r != null) tryRun(() -> model.updateEquipmentPreset(sel, mode, r.category(), r.name(), r.description()));
        });
        JButton cardsBtn = new JButton(mode == SchemaMode.POWER ? "Разъёмы…" : "Карты…");
        cardsBtn.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel == null) return;
            String dlgTitle = sel.getName().isEmpty() ? sel.getCategory().getLabel() : sel.getName();
            if (mode == SchemaMode.POWER) {
                PowerConnectorsConfigDialog dlg = new PowerConnectorsConfigDialog(topWindow(), dlgTitle,
                        PowerConnectorsConfigDialog.forPreset(model, sel), model);
                dlg.setVisible(true);
            } else {
                CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), dlgTitle,
                        CardsConfigDialog.forPreset(model, sel));
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
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        body.add(crud);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Пресеты доступны при добавлении узла в общей схеме "
                + (mode == SchemaMode.POWER ? "питания" : "сигнала")
                + " (категория узла подставляет сначала пресеты этой категории, затем — свой текст).</html>"));
        return (JPanel) UiKit.dynamicSection(title, body);
    }

    // ---- библиотека кабелей/переходников (WireLabelDialog/PowerConnectorsConfigDialog) ----

    private JPanel buildCableLibrary() {
        JPanel body = UiKit.vbox();
        cableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cableRenderer = new NamedRenderer<CableType>(
                c -> (c.getMode() == SchemaMode.POWER ? "[Питание] " : "[Сигнал] ") + c.getLabel(), c -> "");
        cableList.setCellRenderer(cableRenderer);
        body.add(cableScroll);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("+ Добавить кабель…");
        add.addActionListener(e -> {
            com.vjstb.ledscheme.ui.CableTypeDialog dlg = new com.vjstb.ledscheme.ui.CableTypeDialog(topWindow());
            String label = dlg.showDialog();
            if (label != null) {
                tryRun(() -> model.addCableType(dlg.getMode(), label));
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
        addRow.add(add);
        addRow.add(del);
        body.add(addRow);
        body.add(UiKit.vgap(4));
        body.add(UiKit.muted("Кабели/переходники (например, комбинированные вроде «CEE 16A → TrueCON»): "
                + "выберите разъём и исполнение («папа»/«мама») на каждом конце — подпись соберётся "
                + "автоматически. Также предлагаются вдобавок к встроенным при подписи связи схемы и при "
                + "заведении разъёмов распределения, откуда их можно сохранить кнопкой «В библиотеку кабелей»."));
        return (JPanel) UiKit.dynamicSection("Кабели", body);
    }

    // ---- оборудование сигнала: слева тип оборудования, справа его карты-шаблоны ----

    private JPanel buildSignalEquipmentSection() {
        signalPresetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalPresetList.setCellRenderer(new NamedRenderer<EquipmentPreset>(
                p -> p.getName() + " (" + p.getCategory().getLabel() + ")",
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + "карт: " + p.getCards().size()));
        signalPresetScroll.setMinimumSize(new Dimension(180, 120));

        JPanel left = UiKit.vbox();
        left.add(UiKit.muted("Тип оборудования (библиотека)"));
        left.add(signalPresetScroll);
        JPanel leftCrud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), null).showDialog();
            if (r != null) {
                tryRun(() -> model.addEquipmentPreset(SchemaMode.SIGNAL, r.category(), r.name(), r.description(), null));
            }
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel == null) return;
            EquipmentPresetDialog.Result r = new EquipmentPresetDialog(topWindow(), sel).showDialog();
            if (r != null) {
                tryRun(() -> model.updateEquipmentPreset(sel, SchemaMode.SIGNAL, r.category(), r.name(), r.description()));
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
        leftCrud.add(add);
        leftCrud.add(edit);
        leftCrud.add(del);
        left.add(leftCrud);

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
                    CardsConfigDialog.forPreset(model, sel));
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
        rightCrud.add(cardAdd);
        rightCrud.add(cardDel);
        right.add(rightCrud);
        right.add(UiKit.vgap(6));
        right.add(UiKit.muted("<html>Одинаковых карт в устройстве может быть несколько — их количество"
                + " задаётся при добавлении узла в схему, а не здесь.</html>"));

        signalPresetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshSignalCards();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.45);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        // Раньше здесь был отдельный жёсткий предел ширины (Task #95/v1.5) — больше
        // не нужен: ширина уже ограничена на уровне секции (см. applyContentWidth),
        // а BorderLayout/BoxLayout сами передают эту фактическую ширину вниз до
        // split, не требуя дублирующего предела здесь же.

        JPanel body = UiKit.vbox();
        body.add(split);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Пресеты доступны при добавлении узла в общей схеме сигнала (категория"
                + " узла подставляет сначала пресеты этой категории, затем — свой текст). При добавлении узла"
                + " можно задать, сколько экземпляров каждой карты реально стоит в устройстве.</html>"));
        return (JPanel) UiKit.dynamicSection("Оборудование сигнала (пресеты для схемы)", body);
    }

    private void refreshSignalCards() {
        EquipmentPreset sel = signalPresetList.getSelectedValue();
        List<SchemaCard> cards = sel == null ? List.of() : sel.getCards();
        syncList(signalCardModel, cards);
        // Внутри JSplitPane — ширина уже ограничена половиной split (см.
        // применение contentWidth к секции в applyContentWidth), список сам
        // растягивается на выделенную ему половину (capWidth=false), а не на
        // отдельно заданную ширину.
        ListSizing.fit(signalCardList, signalCardScroll, 2, 6, false);
    }

    private void refresh() {
        int w = listWidth();
        libRenderer.setFixedWidth(w);
        syncList(libModel, model.getCabinetTypes());
        ListSizing.fit(libList, libScroll, 2, 8, w);
        recapSection(cabinetsSection);
        ctrlLibRenderer.setFixedWidth(w);
        syncList(ctrlLibModel, model.getWorkspace().getControllerTypes());
        ListSizing.fit(ctrlLibList, ctrlLibScroll, 2, 6, w);
        recapSection(controllersSection);
        powerPresetRenderer.setFixedWidth(w);
        syncList(powerPresetModel, presetsForMode(SchemaMode.POWER));
        ListSizing.fit(powerPresetList, powerPresetScroll, 2, 6, w);
        recapSection(powerPresetsSection);

        EquipmentPreset selSignalPreset = signalPresetList.getSelectedValue();
        List<EquipmentPreset> signalPresets = presetsForMode(SchemaMode.SIGNAL);
        syncList(signalPresetModel, signalPresets);
        ListSizing.fit(signalPresetList, signalPresetScroll, 2, 6, false);
        if (selSignalPreset != null && signalPresets.contains(selSignalPreset)) {
            signalPresetList.setSelectedValue(selSignalPreset, false);
        }
        refreshSignalCards();
        recapSection(signalEquipmentSection);

        cableRenderer.setFixedWidth(w);
        syncList(cableModel, model.getCableTypes());
        ListSizing.fit(cableList, cableScroll, 2, 6, w);
        recapSection(cableSection);
    }

    private List<EquipmentPreset> presetsForMode(SchemaMode mode) {
        List<EquipmentPreset> result = new java.util.ArrayList<>();
        for (EquipmentPreset p : model.getEquipmentPresets()) {
            if (p.getMode() == mode) {
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
