package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.CabinetType;
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
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

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

    public LibrariesStagePanel(AppModel model) {
        this.model = model;
        setLayout(new BorderLayout());

        JPanel body = UiKit.vbox();
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(buildLibrary());
        body.add(UiKit.vgap(10));
        body.add(buildControllerLibrary());
        body.add(UiKit.vgap(10));
        body.add(buildEquipmentPresetSection(SchemaMode.POWER, "Оборудование питания (пресеты для схемы)",
                powerPresetList, powerPresetScroll));
        body.add(UiKit.vgap(10));
        body.add(buildSignalEquipmentSection());
        body.add(javax.swing.Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        for (JScrollPane sp : new JScrollPane[]{libScroll, ctrlLibScroll, powerPresetScroll}) {
            sp.setMinimumSize(new Dimension(200, 80));
        }

        model.addListener(this::refresh);
        refresh();
    }

    // ---- библиотека кабинетов ----

    private JPanel buildLibrary() {
        JPanel body = UiKit.vbox();
        libList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libList.setCellRenderer(new NamedRenderer<CabinetType>(CabinetType::getName, ct ->
                UiKit.fmt(ct.getWidthMm()) + "×" + UiKit.fmt(ct.getHeightMm()) + "мм · "
                        + ct.getResolutionWidth() + "×" + ct.getResolutionHeight() + "px · "
                        + UiKit.fmt(ct.getPowerConsumptionW()) + "Вт · " + UiKit.fmt(ct.getWeightKg()) + "кг"));
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
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            CabinetType sel = libList.getSelectedValue();
            if (sel != null && confirm("Удалить кабинет из библиотеки?")) tryRun(() -> model.deleteCabinetType(sel.getId()));
        });
        crud.add(add);
        crud.add(edit);
        crud.add(del);
        body.add(crud);

        JPanel io = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton exp = new JButton("Экспорт JSON");
        exp.addActionListener(e -> exportLibrary());
        JButton imp = new JButton("Импорт JSON");
        imp.addActionListener(e -> importLibrary());
        io.add(exp);
        io.add(imp);
        body.add(io);
        return (JPanel) UiKit.section("Библиотека кабинетов", body);
    }

    // ---- библиотека контроллеров (аналог SmartLCT) ----

    private JPanel buildControllerLibrary() {
        JPanel body = UiKit.vbox();
        ctrlLibList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ctrlLibList.setCellRenderer(new NamedRenderer<ControllerType>(
                ct -> ct.getName() + (ct.getVendor().isEmpty() ? "" : " (" + ct.getVendor() + ")"),
                ct -> ct.effectivePortCount() + " вых. портов" + (ct.getCards().isEmpty() ? "" : " (по картам)")
                        + " · " + UiKit.fmt(ct.getPortBandwidthMbps()) + " Мбит/с"
                        + " (до " + ct.referencePixelsPerPort() + " px @60Гц/8бит)"
                        + (ct.effectiveInputPortCount() > 0
                                ? " · вх. портов: " + ct.effectiveInputPortCount()
                                        + (ct.inputPortTypesSummary().isEmpty() ? "" : " (" + ct.inputPortTypesSummary() + ")")
                                : "")
                        + (ct.isLoopPort() ? " · Loop" : "")));
        ctrlLibScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
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
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            ControllerType sel = ctrlLibList.getSelectedValue();
            if (sel != null && confirm("Удалить контроллер из библиотеки?")) {
                tryRun(() -> model.deleteControllerType(sel.getId()));
            }
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        body.add(crud);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Для модульных контроллеров (например, Novastar H-серии) задайте карты"
                + " вывода вместо ручного числа портов — «Вых. портов» выше тогда считается по картам.</html>"));
        return (JPanel) UiKit.section("Библиотека контроллеров", body);
    }

    // ---- пресеты оборудования (для схемы питания/сигнала) ----

    private JPanel buildEquipmentPresetSection(SchemaMode mode, String title, JList<EquipmentPreset> presetList,
                                                JScrollPane presetScroll) {
        JPanel body = UiKit.vbox();
        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        presetList.setCellRenderer(new NamedRenderer<EquipmentPreset>(
                p -> p.getName() + " (" + p.getCategory().getLabel() + ")",
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + (mode == SchemaMode.POWER
                                ? "разъёмов: " + p.getPowerConnectors().size()
                                : "карт: " + p.getCards().size())));
        presetScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
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
                        PowerConnectorsConfigDialog.forPreset(model, sel));
                dlg.setVisible(true);
            } else {
                CardsConfigDialog dlg = new CardsConfigDialog(topWindow(), dlgTitle,
                        CardsConfigDialog.forPreset(model, sel));
                dlg.setVisible(true);
            }
        });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            EquipmentPreset sel = presetList.getSelectedValue();
            if (sel != null && confirm("Удалить пресет «" + sel.getName() + "» из библиотеки?")) {
                tryRun(() -> model.deleteEquipmentPreset(sel));
            }
        });
        crud.add(add);
        crud.add(edit);
        crud.add(cardsBtn);
        crud.add(del);
        body.add(crud);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Пресеты доступны при добавлении узла в общей схеме "
                + (mode == SchemaMode.POWER ? "питания" : "сигнала")
                + " (категория узла подставляет сначала пресеты этой категории, затем — свой текст).</html>"));
        return (JPanel) UiKit.section(title, body);
    }

    // ---- оборудование сигнала: слева тип оборудования, справа его карты-шаблоны ----

    private JPanel buildSignalEquipmentSection() {
        signalPresetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalPresetList.setCellRenderer(new NamedRenderer<EquipmentPreset>(
                p -> p.getName() + " (" + p.getCategory().getLabel() + ")",
                p -> (p.getDescription() == null || p.getDescription().isEmpty() ? "" : p.getDescription() + " · ")
                        + "карт: " + p.getCards().size()));
        signalPresetScroll.setPreferredSize(new Dimension(260, 220));
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
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            if (sel != null && confirm("Удалить тип оборудования «" + sel.getName() + "» из библиотеки?")) {
                tryRun(() -> model.deleteEquipmentPreset(sel));
            }
        });
        leftCrud.add(add);
        leftCrud.add(edit);
        leftCrud.add(del);
        left.add(leftCrud);

        signalCardList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        signalCardList.setCellRenderer(new NamedRenderer<SchemaCard>(SchemaCard::getName, SchemaCard::portsSummary));
        signalCardScroll.setPreferredSize(new Dimension(300, 220));
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
        JButton cardDel = new JButton("Удалить карту");
        cardDel.addActionListener(e -> {
            EquipmentPreset sel = signalPresetList.getSelectedValue();
            SchemaCard card = signalCardList.getSelectedValue();
            if (sel == null || card == null) return;
            if (confirm("Удалить карту-шаблон «" + card.getName() + "»?")) {
                tryRun(() -> model.removeCardFromPreset(sel, card.getId()));
            }
        });
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

        JPanel body = UiKit.vbox();
        body.add(split);
        body.add(UiKit.vgap(6));
        body.add(UiKit.muted("<html>Пресеты доступны при добавлении узла в общей схеме сигнала (категория"
                + " узла подставляет сначала пресеты этой категории, затем — свой текст). При добавлении узла"
                + " можно задать, сколько экземпляров каждой карты реально стоит в устройстве.</html>"));
        return (JPanel) UiKit.section("Оборудование сигнала (пресеты для схемы)", body);
    }

    private void refreshSignalCards() {
        EquipmentPreset sel = signalPresetList.getSelectedValue();
        List<SchemaCard> cards = sel == null ? List.of() : sel.getCards();
        syncList(signalCardModel, cards);
        ListSizing.fit(signalCardList, signalCardScroll, 2, 6);
    }

    private void exportLibrary() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("led-cabinet-library.json"));
        fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            tryRun(() -> model.exportLibrary(fc.getSelectedFile()));
        }
    }

    private void importLibrary() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                int n = model.importLibrary(fc.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Импортировано кабинетов: " + n, "Импорт",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refresh() {
        syncList(libModel, model.getCabinetTypes());
        ListSizing.fit(libList, libScroll, 2, 8);
        syncList(ctrlLibModel, model.getWorkspace().getControllerTypes());
        ListSizing.fit(ctrlLibList, ctrlLibScroll, 2, 6);
        syncList(powerPresetModel, presetsForMode(SchemaMode.POWER));
        ListSizing.fit(powerPresetList, powerPresetScroll, 2, 6);

        EquipmentPreset selSignalPreset = signalPresetList.getSelectedValue();
        List<EquipmentPreset> signalPresets = presetsForMode(SchemaMode.SIGNAL);
        syncList(signalPresetModel, signalPresets);
        ListSizing.fit(signalPresetList, signalPresetScroll, 2, 6);
        if (selSignalPreset != null && signalPresets.contains(selSignalPreset)) {
            signalPresetList.setSelectedValue(selSignalPreset, false);
        }
        refreshSignalCards();
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
