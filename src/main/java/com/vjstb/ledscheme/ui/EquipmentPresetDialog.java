package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.EquipmentSeries;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Модальный диалог добавления/редактирования пресета оборудования в библиотеке:
 *  категория (тип узла общей схемы), название, описание, а для категории
 *  «Прочее оборудование» — ещё и подкатегория из справочника админ-режима
 *  (Task #12: раньше AdminDialog умел редактировать список подкатегорий, но
 *  сохранить подкатегорию на пресете было негде — этот диалог был единственным
 *  местом, где категория пресета вообще задаётся). Комплектация карт
 *  редактируется отдельно ({@link CardsConfigDialog}) — уже для сохранённого пресета.
 *
 *  <p>Для {@code category == CONTROLLER} (библиотека контроллеров слита в
 *  EquipmentPreset, 2026-09-23) показывается дополнительный блок полей —
 *  производитель/число портов/пропускная способность/входные порты/loop-порт,
 *  перенесённый из бывшего {@code ControllerTypeDialog} буквально (та же
 *  взаимная пересчитка пропускной способности ⟷ пикселей на порт при опорных
 *  60 Гц/8 бит) — как и подкатегория у CUSTOM, виден только для своей категории. */
public class EquipmentPresetDialog extends JDialog {

    /** Сентинел «без подкатегории» в комбобоксе — не может совпасть с реальным
     *  названием подкатегории (список подкатегорий — общая справочная данные,
     *  синхронизируется с сервера, см. AppModel.getCustomEquipmentCategories). */
    private static final String NO_SUBCATEGORY = "— без подкатегории —";
    private static final String[] BANDWIDTH_PRESETS = {"100", "1000", "2500", "10000"};

    private final JComboBox<SchemaNodeType> categoryField = new JComboBox<>(SchemaNodeType.values());
    private final JTextField nameField = new JTextField();
    private final JTextField descriptionField = new JTextField();
    private final JComboBox<String> subcategoryField = new JComboBox<>();
    private final JLabel subcategoryLabel = new JLabel("Подкатегория");
    private final JComboBox<String> companyField = new JComboBox<>();

    // ---- серия оборудования (запрос 2026-10-02): список серий этой категории + лимиты карт ----
    private final JComboBox<EquipmentSeries> seriesField = new JComboBox<>();
    private final JLabel seriesLabel = new JLabel("Серия");
    private final JSpinner maxInputSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));
    private final JSpinner maxOutputSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));
    private final JLabel maxInputLabel = new JLabel("Макс. входных карт (-1 = без лимита)");
    private final JLabel maxOutputLabel = new JLabel("Макс. выходных карт (-1 = без лимита)");
    private final AppModel model;
    private final boolean seriesApplicable;
    /** Серия, записанная в пресете, и менял ли её пользователь: серия может отсутствовать в локальной
     *  библиотеке (ещё не синхронизирована) — тогда сохранение не должно молча отвязывать пресет. */
    private final String existingSeriesId;
    private boolean seriesTouched;
    private boolean refillingSeries;

    // ---- видно только для category == CONTROLLER (перенесено из ControllerTypeDialog) ----
    private final JPanel controllerFieldsPanel;
    private final JTextField vendorField = new JTextField();
    private final JTextField portCountField = new JTextField();
    private final JComboBox<String> bandwidthCombo = new JComboBox<>(BANDWIDTH_PRESETS);
    private final JTextField pixelsField = new JTextField();
    private final JTextField inputPortsField = new JTextField();
    private final JCheckBox loopPortCheck = new JCheckBox("Есть Loop-порт (например, HDMI loop у VX1000)");
    private boolean syncingBandwidth;

    public record Result(SchemaNodeType category, String name, String description, String customCategoryLabel,
                          String company, String vendor, int portCount, double portBandwidthMbps,
                          int inputPortCount, boolean loopPort, String seriesId, Integer maxInputCards,
                          Integer maxOutputCards) {
    }

    private Result result;

    public EquipmentPresetDialog(Window owner, AppModel model, EquipmentPreset existing) {
        this(owner, model, existing, null);
    }

    /** initialCategory — категория, выбранная по умолчанию для НОВОГО пресета
     *  (existing == null), например текущая подсвеченная категория в дереве
     *  библиотеки — без этого «Добавить» в категории Y всегда предлагал бы
     *  первую по счёту категорию (SOURCE) вместо ожидаемой Y. Игнорируется при
     *  редактировании существующего пресета (тогда категория берётся из него). */
    public EquipmentPresetDialog(Window owner, AppModel model, EquipmentPreset existing, SchemaNodeType initialCategory) {
        this(owner, model, existing, initialCategory, existing != null ? existing.getMode() : SchemaMode.SIGNAL);
    }

    /** {@code mode} — режим схемы библиотеки, в которой создаётся/правится пресет: серии (общий каталог
     *  карт) бывают только у оборудования СИГНАЛА, для питания строки серии нет. */
    public EquipmentPresetDialog(Window owner, AppModel model, EquipmentPreset existing, SchemaNodeType initialCategory,
                                  SchemaMode mode) {
        super(owner, existing == null ? "Новый пресет оборудования" : "Редактирование пресета",
                ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.seriesApplicable = mode == SchemaMode.SIGNAL;
        this.existingSeriesId = existing != null ? existing.getSeriesId() : null;

        companyField.setEditable(true);
        companyField.setModel(new javax.swing.DefaultComboBoxModel<>(model.getKnownCompanies().toArray(new String[0])));

        categoryField.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaNodeType t) {
                    setText(model.categoryLabel(t));
                }
                return this;
            }
        });

        subcategoryField.addItem(NO_SUBCATEGORY);
        for (String c : model.getCustomEquipmentCategories()) {
            subcategoryField.addItem(c);
        }
        bandwidthCombo.setEditable(true);
        // Дефолты для НОВОГО пресета (existing == null) — те же, что были у
        // ControllerType по умолчанию (см. её бывшие field-инициализаторы).
        portCountField.setText("8");
        bandwidthField().setText(UiKit.fmt(1000));
        pixelsField.setText(String.valueOf(ControllerInstance.maxPixelsFor(1000,
                ControllerInstance.REFERENCE_HZ, ControllerInstance.REFERENCE_BIT_DEPTH)));
        inputPortsField.setText("0");
        controllerFieldsPanel = buildControllerFieldsPanel();
        Runnable syncCategoryVisibility = () -> {
            boolean isCustom = categoryField.getSelectedItem() == SchemaNodeType.CUSTOM;
            subcategoryLabel.setVisible(isCustom);
            subcategoryField.setVisible(isCustom);
            controllerFieldsPanel.setVisible(categoryField.getSelectedItem() == SchemaNodeType.CONTROLLER);
        };
        categoryField.addActionListener(e -> {
            syncCategoryVisibility.run();
            refillSeries(null);
        });
        seriesField.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof EquipmentSeries s ? s.getName() : "— без серии —");
                return this;
            }
        });
        seriesField.setToolTipText("Серия даёт модели общий каталог карт (карты серии доступны при сборке узла"
                + " и в «Картах…»); сами серии создаются в админ-консоли.");
        seriesField.addActionListener(e -> {
            if (!refillingSeries) {
                seriesTouched = true;
            }
            syncSeriesVisibility();
        });
        String limitTip = "Сколько карт можно собрать в узле этой модели (-1 — без ограничения). Смешанная карта"
                + " считается и во входных, и в выходных.";
        maxInputSpinner.setToolTipText(limitTip);
        maxOutputSpinner.setToolTipText(limitTip);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Категория"));
        form.add(categoryField);
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Компания (владелец техники)"));
        form.add(companyField);
        form.add(new JLabel("Описание"));
        form.add(descriptionField);
        form.add(subcategoryLabel);
        form.add(subcategoryField);
        form.add(seriesLabel);
        form.add(seriesField);
        form.add(maxInputLabel);
        form.add(maxInputSpinner);
        form.add(maxOutputLabel);
        form.add(maxOutputSpinner);

        if (existing != null) {
            categoryField.setSelectedItem(existing.getCategory());
            nameField.setText(existing.getName());
            companyField.getEditor().setItem(existing.getCompany() != null ? existing.getCompany() : "");
            descriptionField.setText(existing.getDescription());
            String cur = existing.getCustomCategoryLabel();
            if (cur != null && !cur.isEmpty()) {
                subcategoryField.setSelectedItem(cur);
            }
            vendorField.setText(existing.getVendor());
            portCountField.setText(String.valueOf(existing.getPortCount()));
            bandwidthField().setText(UiKit.fmt(existing.getPortBandwidthMbps()));
            pixelsField.setText(String.valueOf(ControllerInstance.maxPixelsFor(existing.getPortBandwidthMbps(),
                    ControllerInstance.REFERENCE_HZ, ControllerInstance.REFERENCE_BIT_DEPTH)));
            inputPortsField.setText(String.valueOf(existing.getInputPortCount()));
            loopPortCheck.setSelected(existing.isLoopPort());
            maxInputSpinner.setValue(existing.getMaxInputCards() != null ? existing.getMaxInputCards() : -1);
            maxOutputSpinner.setValue(existing.getMaxOutputCards() != null ? existing.getMaxOutputCards() : -1);
        } else if (initialCategory != null) {
            categoryField.setSelectedItem(initialCategory);
        }
        syncCategoryVisibility.run();
        refillSeries(existingSeriesId);

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        content.add(controllerFieldsPanel);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Серии, доступные для выбранной категории оборудования (серия привязана к категории: серия
     *  контроллеров — только контроллерам, серия медиасерверов — только серверам). */
    private List<EquipmentSeries> seriesForCategory(SchemaNodeType category) {
        List<EquipmentSeries> result = new ArrayList<>();
        for (EquipmentSeries s : model.getEquipmentSeries()) {
            if (s.getCategory() == category) {
                result.add(s);
            }
        }
        return result;
    }

    /** Пересобирает выпадающий список серий под выбранную категорию; {@code selectId} — какую выбрать. */
    private void refillSeries(String selectId) {
        refillingSeries = true;
        try {
            seriesField.removeAllItems();
            seriesField.addItem(null);
            EquipmentSeries toSelect = null;
            for (EquipmentSeries s : seriesForCategory((SchemaNodeType) categoryField.getSelectedItem())) {
                seriesField.addItem(s);
                if (s.getId().equals(selectId)) {
                    toSelect = s;
                }
            }
            seriesField.setSelectedItem(toSelect);
        } finally {
            refillingSeries = false;
        }
        syncSeriesVisibility();
    }

    /** Строка серии видна, только если для категории есть серии (или пресет уже записан в серию), лимиты
     *  — когда серия выбрана. Для питания серий нет. */
    private void syncSeriesVisibility() {
        boolean hasSeries = seriesApplicable
                && (seriesField.getItemCount() > 1 || existingSeriesId != null);
        seriesLabel.setVisible(hasSeries);
        seriesField.setVisible(hasSeries);
        boolean limits = hasSeries && seriesField.getSelectedItem() != null;
        maxInputLabel.setVisible(limits);
        maxInputSpinner.setVisible(limits);
        maxOutputLabel.setVisible(limits);
        maxOutputSpinner.setVisible(limits);
        if (isShowing() || getContentPane().getComponentCount() > 0) {
            pack();
        }
    }

    /** Блок полей контроллера — буквально перенесён из бывшего ControllerTypeDialog
     *  (включая взаимный пересчёт пропускной способности ⟷ пикселей на порт при
     *  опорных 60 Гц/8 бит), просто теперь как условно видимая секция ЭТОГО
     *  диалога вместо отдельного модального окна. */
    private JPanel buildControllerFieldsPanel() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createTitledBorder("Параметры контроллера"));
        form.add(new JLabel("Производитель"));
        form.add(vendorField);
        form.add(new JLabel("Число портов вывода"));
        form.add(portCountField);
        form.add(new JLabel("Пропускная способность порта, Мбит/с"));
        form.add(bandwidthCombo);
        form.add(new JLabel("Пикселей на порт (при 60 Гц/8 бит)"));
        form.add(pixelsField);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        JLabel inputLabel = new JLabel("Входных портов");
        inputLabel.setForeground(Palette.MUTED);
        inputPortsField.setColumns(4);
        inputRow.add(inputLabel, BorderLayout.WEST);
        inputRow.add(inputPortsField, BorderLayout.CENTER);
        inputRow.add(loopPortCheck, BorderLayout.EAST);

        bandwidthField().getDocument().addDocumentListener(simpleListener(this::syncFromBandwidth));
        pixelsField.getDocument().addDocumentListener(simpleListener(this::syncFromPixels));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(form);
        panel.add(Box.createVerticalStrut(4));
        panel.add(inputRow);
        return panel;
    }

    private JTextField bandwidthField() {
        return (JTextField) bandwidthCombo.getEditor().getEditorComponent();
    }

    private DocumentListener simpleListener(Runnable onChange) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onChange.run(); }
            public void removeUpdate(DocumentEvent e) { onChange.run(); }
            public void changedUpdate(DocumentEvent e) { onChange.run(); }
        };
    }

    private void syncFromBandwidth() {
        if (syncingBandwidth) return;
        try {
            double mbps = Double.parseDouble(bandwidthField().getText().trim().replace(',', '.'));
            int px = ControllerInstance.maxPixelsFor(mbps, ControllerInstance.REFERENCE_HZ,
                    ControllerInstance.REFERENCE_BIT_DEPTH);
            syncingBandwidth = true;
            pixelsField.setText(String.valueOf(px));
        } catch (NumberFormatException ignored) {
            // пользователь ещё печатает — не мешаем
        } finally {
            syncingBandwidth = false;
        }
    }

    private void syncFromPixels() {
        if (syncingBandwidth) return;
        try {
            int px = Integer.parseInt(pixelsField.getText().trim());
            double mbps = ControllerInstance.bandwidthForPixels(px, ControllerInstance.REFERENCE_HZ,
                    ControllerInstance.REFERENCE_BIT_DEPTH);
            syncingBandwidth = true;
            bandwidthField().setText(UiKit.fmt(Math.round(mbps * 10) / 10.0));
        } catch (NumberFormatException ignored) {
            // пользователь ещё печатает — не мешаем
        } finally {
            syncingBandwidth = false;
        }
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите название пресета", "Проверка данных",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        SchemaNodeType category = (SchemaNodeType) categoryField.getSelectedItem();
        String subcategory = category == SchemaNodeType.CUSTOM ? (String) subcategoryField.getSelectedItem() : null;
        if (NO_SUBCATEGORY.equals(subcategory)) {
            subcategory = null;
        }
        String company = String.valueOf(companyField.getEditor().getItem()).trim();
        String vendor = "";
        int portCount = 0;
        double portBandwidthMbps = 1000;
        int inputPortCount = 0;
        boolean loopPort = false;
        if (category == SchemaNodeType.CONTROLLER) {
            try {
                vendor = vendorField.getText().trim();
                portCount = (int) parseNonNeg(portCountField.getText(), "Число портов");
                portBandwidthMbps = parseNonNeg(bandwidthField().getText(), "Пропускная способность порта");
                inputPortCount = (int) parseNonNeg(inputPortsField.getText(), "Входных портов");
                loopPort = loopPortCheck.isSelected();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        EquipmentSeries series = (EquipmentSeries) seriesField.getSelectedItem();
        // серия, которой ещё нет в локальной библиотеке, и пользователь её не трогал — не отвязываем
        String seriesId = !seriesApplicable ? null
                : series != null ? series.getId()
                : seriesTouched ? null : existingSeriesId;
        int maxIn = (Integer) maxInputSpinner.getValue();
        int maxOut = (Integer) maxOutputSpinner.getValue();
        boolean limits = seriesId != null;
        result = new Result(category, name, descriptionField.getText().trim(), subcategory,
                company.isEmpty() ? null : company, vendor, portCount, portBandwidthMbps, inputPortCount, loopPort,
                seriesId, limits && maxIn >= 0 ? maxIn : null, limits && maxOut >= 0 ? maxOut : null);
        dispose();
    }

    private double parseNonNeg(String s, String field) {
        try {
            double v = MathExpr.eval(s);
            if (v < 0) {
                throw new IllegalArgumentException(field + ": значение не может быть отрицательным");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": введите число");
        }
    }

    /** Показывает диалог; возвращает заполненные данные или null при отмене. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
