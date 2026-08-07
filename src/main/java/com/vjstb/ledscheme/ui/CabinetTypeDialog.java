package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetShape;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
import javax.swing.JTextField;

/** Модальный диалог добавления/редактирования типа кабинета в библиотеке. */
public class CabinetTypeDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextField widthField = new JTextField();
    private final JTextField heightField = new JTextField();
    private final JTextField depthField = new JTextField();
    private final JTextField resWField = new JTextField();
    private final JTextField resHField = new JTextField();
    private final JTextField powerField = new JTextField();
    private final JTextField weightField = new JTextField();
    /** Допустимые формы этой модели кабинета — какие вообще физически существуют
     *  (сужает выбор в радиальном меню "Форма" на конкретной ячейке, см. Task #79/v1.4). */
    private final Map<CabinetShape, JCheckBox> shapeChecks = new LinkedHashMap<>();
    /** Форма по умолчанию — какая из отмеченных выше применяется новым ячейкам этого
     *  типа; список вариантов всегда ограничен отмеченными допустимыми формами. */
    private final JComboBox<CabinetShape> defaultShapeField = new JComboBox<>(CabinetShape.values());
    /** Угол непрямоугольной формы (Task #91/v1.5) — в каком углу ячейки стоит прямой
     *  угол треугольника (0°=левый нижний, далее по часовой стрелке — см.
     *  CabinetType.getRotationDeg/SchemeRenderer.trianglePolygon). Только 4 варианта
     *  осмысленны физически (шаг в четверть оборота), поэтому комбобокс, а не
     *  произвольное число. Активно, только пока среди допустимых форм есть хотя бы
     *  одна непрямоугольная — для чисто прямоугольного кабинета угол не нужен. */
    private final JComboBox<Integer> rotationField = new JComboBox<>(new Integer[]{0, 90, 180, 270});
    private final JLabel rotationLabel = new JLabel("Угол непрямоугольной формы");
    /** Живой предпросмотр формы+угла (Task #92/v1.5) — вместо того чтобы гадать по
     *  голому числу градусов, какой угол именно куда повернёт треугольник. */
    private final ShapePreviewPanel previewPanel = new ShapePreviewPanel();
    private final JLabel previewLabel = new JLabel("Предпросмотр формы");
    private JPanel previewRow;
    private final JComboBox<PowerConnectorType> powerConnectorTypeField = new JComboBox<>(PowerConnectorType.values());
    /** Номинал разъёма (А) для типа "Другой" — для PowerCon/TRUEcon номинал фиксирован
     *  (16А, ограничение вводного кабеля, см. PowerCalc), для произвольного разъёма
     *  его нужно указать вручную, иначе расчёт нагрузки (Task #80/#81) не сможет
     *  проверить цепочки этого типа кабинета. */
    private final JTextField customConnectorAmpField = new JTextField();
    private final JLabel customConnectorAmpLabel = new JLabel("Номинал разъёма "
            + "«Другой», А (для расчёта нагрузки)");
    private final JTextField powerConnectorsField = new JTextField();
    private final JTextField signalConnectorsField = new JTextField();
    private final JComboBox<String> companyField = new JComboBox<>();

    private CabinetType result;

    public CabinetTypeDialog(Window owner, AppModel model, CabinetType existing) {
        super(owner, existing == null ? "Новый кабинет" : "Редактирование кабинета", ModalityType.APPLICATION_MODAL);
        companyField.setEditable(true);
        companyField.setModel(new javax.swing.DefaultComboBoxModel<>(model.getKnownCompanies().toArray(new String[0])));

        // Форма разбита на ДВЕ отдельные GridLayout-панели (formTop/formBottom), а не
        // одну общую — GridLayout заставляет ВСЕ строки сетки иметь одинаковую
        // высоту; предпросмотр формы (previewRow, ~64px) между ними рисуется как
        // самостоятельная панель BoxLayout-секции, а не строка внутри GridLayout —
        // иначе он растягивал КАЖДУЮ строку всей формы до 64px, и диалог (14+ строк)
        // занимал весь экран (баг-репорт, Task #94/v1.5).
        JPanel formTop = new JPanel(new GridLayout(0, 2, 8, 6));
        formTop.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        formTop.add(new JLabel("Название"));
        formTop.add(nameField);
        formTop.add(new JLabel("Компания (владелец техники)"));
        formTop.add(companyField);
        formTop.add(new JLabel("Ширина (мм)"));
        formTop.add(widthField);
        formTop.add(new JLabel("Высота (мм)"));
        formTop.add(heightField);
        formTop.add(new JLabel("Глубина (мм, необязательно)"));
        formTop.add(depthField);
        formTop.add(new JLabel("Разрешение W (px)"));
        formTop.add(resWField);
        formTop.add(new JLabel("Разрешение H (px)"));
        formTop.add(resHField);
        formTop.add(new JLabel("Мощность (Вт)"));
        formTop.add(powerField);
        formTop.add(new JLabel("Вес (кг)"));
        formTop.add(weightField);
        JPanel shapesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (CabinetShape s : CabinetShape.values()) {
            JCheckBox cb = new JCheckBox(s.getLabel());
            cb.addActionListener(ev -> onShapeCheckChanged());
            shapeChecks.put(s, cb);
            shapesPanel.add(cb);
        }
        formTop.add(new JLabel("Допустимые формы"));
        formTop.add(shapesPanel);
        formTop.add(new JLabel("Форма по умолчанию"));
        formTop.add(defaultShapeField);
        formTop.add(rotationLabel);
        formTop.add(rotationField);

        // Предпросмотр формы+угла — сразу после поля угла, но ВНЕ GridLayout (см.
        // комментарий выше). Строится ДО refreshRotationVisibility()/refreshPreview()
        // ниже — им нужна ссылка на previewRow, чтобы скрывать панель целиком для
        // чисто прямоугольных кабинетов.
        previewRow = new JPanel(new BorderLayout(10, 0));
        previewRow.setBorder(BorderFactory.createEmptyBorder(0, 14, 6, 14));
        previewRow.add(previewLabel, BorderLayout.WEST);
        JPanel previewHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        previewHolder.add(previewPanel);
        previewRow.add(previewHolder, BorderLayout.CENTER);

        JPanel formBottom = new JPanel(new GridLayout(0, 2, 8, 6));
        formBottom.setBorder(BorderFactory.createEmptyBorder(6, 14, 14, 14));
        formBottom.add(new JLabel("Тип разъёма питания"));
        formBottom.add(powerConnectorTypeField);
        formBottom.add(customConnectorAmpLabel);
        formBottom.add(customConnectorAmpField);
        powerConnectorTypeField.addActionListener(ev -> refreshCustomConnectorAmpVisibility());
        formBottom.add(new JLabel("Линий питания на кабинет (0 = встроено)"));
        formBottom.add(powerConnectorsField);
        formBottom.add(new JLabel("Линий сигнала на кабинет (0 = встроено)"));
        formBottom.add(signalConnectorsField);

        CabinetType src = existing != null ? existing : new CabinetType();
        nameField.setText(src.getName());
        companyField.getEditor().setItem(src.getCompany() != null ? src.getCompany() : "");
        widthField.setText(num(src.getWidthMm()));
        heightField.setText(num(src.getHeightMm()));
        depthField.setText(src.getDepthMm() != null ? num(src.getDepthMm()) : "");
        resWField.setText(String.valueOf(src.getResolutionWidth()));
        resHField.setText(String.valueOf(src.getResolutionHeight()));
        powerField.setText(num(src.getPowerConsumptionW()));
        weightField.setText(num(src.getWeightKg()));
        Set<CabinetShape> allowedShapes = src.getAllowedShapes();
        for (var entry : shapeChecks.entrySet()) {
            entry.getValue().setSelected(allowedShapes.contains(entry.getKey()));
        }
        refreshDefaultShapeOptions();
        defaultShapeField.setSelectedItem(src.getShape());
        rotationField.setSelectedItem(nearestQuarterTurn(src.getRotationDeg()));
        defaultShapeField.addActionListener(ev -> refreshPreview());
        rotationField.addActionListener(ev -> refreshPreview());
        refreshRotationVisibility();
        refreshPreview();
        powerConnectorTypeField.setSelectedItem(src.getPowerConnectorType());
        customConnectorAmpField.setText(src.getCustomConnectorAmpRating() != null
                ? num(src.getCustomConnectorAmpRating()) : "");
        refreshCustomConnectorAmpVisibility();
        powerConnectorsField.setText(String.valueOf(src.getPowerConnectorsNeeded()));
        signalConnectorsField.setText(String.valueOf(src.getSignalConnectorsNeeded()));

        JButton ok = new JButton("Сохранить");
        ok.addActionListener(e -> onOk(existing));
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(formTop);
        content.add(previewRow);
        content.add(formBottom);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Не даём снять последнюю галочку — хотя бы одна форма должна остаться
     *  допустимой, иначе форму по умолчанию неоткуда будет взять. */
    private void onShapeCheckChanged() {
        boolean anyChecked = shapeChecks.values().stream().anyMatch(JCheckBox::isSelected);
        if (!anyChecked) {
            shapeChecks.values().iterator().next().setSelected(true);
        }
        refreshDefaultShapeOptions();
        refreshRotationVisibility();
        refreshPreview();
    }

    /** Угол нужен, только пока среди допустимых форм есть хотя бы одна непрямоугольная —
     *  чисто прямоугольному кабинету ориентировать нечего. */
    private void refreshRotationVisibility() {
        boolean anyNonRectangle = shapeChecks.entrySet().stream()
                .anyMatch(e -> e.getValue().isSelected() && e.getKey() != CabinetShape.RECTANGLE);
        rotationLabel.setEnabled(anyNonRectangle);
        rotationField.setEnabled(anyNonRectangle);
        previewLabel.setVisible(anyNonRectangle);
        previewPanel.setVisible(anyNonRectangle);
        if (previewRow != null) {
            previewRow.setVisible(anyNonRectangle);
        }
    }

    private void refreshPreview() {
        CabinetShape shape = (CabinetShape) defaultShapeField.getSelectedItem();
        Integer rotation = (Integer) rotationField.getSelectedItem();
        previewPanel.update(shape, rotation != null ? rotation : 0);
    }

    private static int nearestQuarterTurn(double deg) {
        int q = Math.floorMod(Math.round(deg / 90.0), 4);
        return q * 90;
    }

    /** Список формы по умолчанию всегда ограничен отмеченными допустимыми формами —
     *  иначе можно было бы выбрать значением по умолчанию форму, которую сам же
     *  только что снял с допустимых. */
    private void refreshDefaultShapeOptions() {
        CabinetShape prevSelected = (CabinetShape) defaultShapeField.getSelectedItem();
        java.util.List<CabinetShape> checked = new java.util.ArrayList<>();
        for (var entry : shapeChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                checked.add(entry.getKey());
            }
        }
        defaultShapeField.setModel(new javax.swing.DefaultComboBoxModel<>(checked.toArray(new CabinetShape[0])));
        if (prevSelected != null && checked.contains(prevSelected)) {
            defaultShapeField.setSelectedItem(prevSelected);
        } else if (!checked.isEmpty()) {
            defaultShapeField.setSelectedItem(checked.get(0));
        }
    }

    /** Поле номинала актуально только для типа "Другой" — для PowerCon/TRUEcon
     *  номинал фиксирован в PowerCalc и не редактируется здесь. */
    private void refreshCustomConnectorAmpVisibility() {
        boolean isOther = powerConnectorTypeField.getSelectedItem() == PowerConnectorType.OTHER;
        customConnectorAmpLabel.setVisible(isOther);
        customConnectorAmpField.setVisible(isOther);
        customConnectorAmpField.setEnabled(isOther);
    }

    private void onOk(CabinetType existing) {
        try {
            CabinetType ct = existing != null ? existing.copy() : new CabinetType();
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название кабинета");
            }
            ct.setName(name);
            String company = String.valueOf(companyField.getEditor().getItem()).trim();
            ct.setCompany(company.isEmpty() ? null : company);
            ct.setWidthMm(parsePositive(widthField.getText(), "Ширина"));
            ct.setHeightMm(parsePositive(heightField.getText(), "Высота"));
            ct.setDepthMm(depthField.getText().trim().isEmpty() ? null : parsePositive(depthField.getText(), "Глубина"));
            ct.setResolutionWidth((int) parsePositive(resWField.getText(), "Разрешение W"));
            ct.setResolutionHeight((int) parsePositive(resHField.getText(), "Разрешение H"));
            ct.setPowerConsumptionW(parseNonNeg(powerField.getText(), "Мощность"));
            ct.setWeightKg(parseNonNeg(weightField.getText(), "Вес"));
            Set<CabinetShape> allowed = new LinkedHashSet<>();
            for (var entry : shapeChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    allowed.add(entry.getKey());
                }
            }
            CabinetShape defaultShape = (CabinetShape) defaultShapeField.getSelectedItem();
            if (defaultShape == null || !allowed.contains(defaultShape)) {
                defaultShape = allowed.iterator().next();
            }
            ct.setShape(defaultShape);
            ct.setAllowedShapes(allowed);
            Integer rotation = (Integer) rotationField.getSelectedItem();
            ct.setRotationDeg(rotation != null ? rotation : 0);
            PowerConnectorType connectorType = (PowerConnectorType) powerConnectorTypeField.getSelectedItem();
            ct.setPowerConnectorType(connectorType);
            if (connectorType == PowerConnectorType.OTHER && !customConnectorAmpField.getText().trim().isEmpty()) {
                ct.setCustomConnectorAmpRating(parsePositive(customConnectorAmpField.getText(), "Номинал разъёма"));
            } else {
                ct.setCustomConnectorAmpRating(null);
            }
            ct.setPowerConnectorsNeeded((int) parseNonNeg(powerConnectorsField.getText(), "Линий питания"));
            ct.setSignalConnectorsNeeded((int) parseNonNeg(signalConnectorsField.getText(), "Линий сигнала"));
            result = ct;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parsePositive(String s, String field) {
        double v = parseNonNeg(s, field);
        if (v <= 0) {
            throw new IllegalArgumentException(field + ": значение должно быть больше 0");
        }
        return v;
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

    private static String num(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** Показывает диалог; возвращает заполненный тип или null при отмене. */
    public CabinetType showDialog() {
        setVisible(true);
        return result;
    }

    static Component vgap() {
        return Box.createVerticalStrut(6);
    }

    /** Небольшая живая отрисовка выбранной формы+угла (Task #92/v1.5) — тот же
     *  реальный контур, что и в схемах (SchemeRenderer.outlineCabinetShape), вместо
     *  того чтобы угадывать по голому числу градусов, куда именно повернётся
     *  прямой угол треугольника. */
    private static final class ShapePreviewPanel extends JPanel {
        private CabinetShape shape = CabinetShape.RECTANGLE;
        private int rotationDeg;

        ShapePreviewPanel() {
            setPreferredSize(new Dimension(64, 64));
            setOpaque(false);
        }

        void update(CabinetShape shape, int rotationDeg) {
            this.shape = shape != null ? shape : CabinetShape.RECTANGLE;
            this.rotationDeg = rotationDeg;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad = 4;
            int size = Math.min(getWidth(), getHeight()) - pad * 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g2.setColor(Palette.PANEL);
            g2.fillRect(x, y, size, size);
            g2.setColor(new Color(0x2f, 0x81, 0xf7).darker());
            SchemeRenderer.fillCabinetShape(g2, x, y, size, size, shape, rotationDeg);
            g2.setColor(Palette.ACCENT);
            SchemeRenderer.outlineCabinetShape(g2, x, y, size, size, shape, rotationDeg);
            g2.dispose();
        }
    }
}
