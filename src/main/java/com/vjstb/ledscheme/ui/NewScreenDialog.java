package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ScreenDefaults;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Модальный диалог параметров нового экрана: имя, тип кабинета, размер сетки,
 *  способ монтажа, базовый офсет (X/Y мм) — предзаполненный подсказанной
 *  автопозицией.
 *
 * <p>{@link #mountTypeField} (запрос пользователя: "при создании экрана тип
 * монтажа выбирать сразу, а не только при корректировке параметров экрана")
 * — раньше новый экран ВСЕГДА получал жёстко зашитый дефолт {@link
 * ScreenMountType#RIGGED} ({@code Screen.mountType} инициализируется им же),
 * менять его сразу после создания было неудобно (нужно было открыть
 * «Параметры экрана» отдельным шагом). Значение по умолчанию в этом
 * комбобоксе — {@code RIGGED}, первый элемент {@link
 * ScreenMountType#values()} — то же, что и раньше, просто теперь явно
 * выбираемо тут же, а не только позже. */
public class NewScreenDialog extends JDialog {

    /** Заполненные и провалидированные параметры нового экрана. */
    public record Result(String name, String cabinetTypeId, int rows, int cols, double posX, double posY,
                          ScreenMountType mountType) {
    }

    private final JTextField nameField = new JTextField();
    private final JComboBox<CabinetType> typeField = new JComboBox<>();
    private final JComboBox<ScreenMountType> mountTypeField = new JComboBox<>(ScreenMountType.values());
    private final JSpinner colsField = new JSpinner(new SpinnerNumberModel(3, 1, 200, 1));
    private final JSpinner rowsField = new JSpinner(new SpinnerNumberModel(5, 1, 200, 1));
    private final JTextField xField = new JTextField();
    private final JTextField yField = new JTextField();
    /** Нужна кнопке «Указать новый кабинет…» — сохранить созданный на месте тип
     *  сразу в библиотеку (см. {@link #buildNewCabinetButton()}). */
    private final AppModel model;

    private Result result;

    public NewScreenDialog(Window owner, AppModel model, List<CabinetType> cabinetTypes, String suggestedName,
                            double suggestedX, double suggestedY) {
        this(owner, model, cabinetTypes, suggestedName, suggestedX, suggestedY, null);
    }

    /** {@code defaults} — «Параметры по умолчанию» текущей сцены (см. {@link
     *  ScreenDefaults}, {@code ui.stage.SetupStagePanel}), {@code null} —
     *  сцена их не задавала. Используется ТОЛЬКО чтобы предзаполнить комбобоксы
     *  кабинета/способа монтажа подсказанным значением вместо жёстко зашитого
     *  "первый в библиотеке"/{@code RIGGED} — пользователь по-прежнему может
     *  выбрать любой другой вариант перед созданием, тот и победит (см. javadoc
     *  {@link ScreenDefaults#applyTo}: эти два поля туда сознательно не входят,
     *  ровно чтобы явный выбор в ЭТОМ диалоге всегда был окончательным). */
    public NewScreenDialog(Window owner, AppModel model, List<CabinetType> cabinetTypes, String suggestedName,
                            double suggestedX, double suggestedY, ScreenDefaults defaults) {
        super(owner, "Новый экран", ModalityType.APPLICATION_MODAL);
        this.model = model;

        for (CabinetType t : cabinetTypes) {
            typeField.addItem(t);
        }
        typeField.setRenderer(new CabinetTypeRenderer());
        CabinetType preselectType = null;
        if (defaults != null && defaults.getCabinetTypeId() != null) {
            for (CabinetType t : cabinetTypes) {
                if (t.getId().equals(defaults.getCabinetTypeId())) {
                    preselectType = t;
                    break;
                }
            }
        }
        if (preselectType != null) {
            typeField.setSelectedItem(preselectType);
        } else if (typeField.getItemCount() > 0) {
            typeField.setSelectedIndex(0);
        }
        if (defaults != null && defaults.getMountType() != null) {
            mountTypeField.setSelectedItem(defaults.getMountType());
        }
        nameField.setText(suggestedName);
        xField.setText(UiKit.fmt(suggestedX));
        yField.setText(UiKit.fmt(suggestedY));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Название"));
        form.add(nameField);
        form.add(new JLabel("Кабинет"));
        JPanel typeRow = new JPanel(new BorderLayout(6, 0));
        typeRow.add(typeField, BorderLayout.CENTER);
        typeRow.add(buildNewCabinetButton(), BorderLayout.EAST);
        form.add(typeRow);
        form.add(new JLabel("Способ монтажа"));
        form.add(mountTypeField);
        form.add(new JLabel("Колонны"));
        form.add(colsField);
        MathFields.enableExpressions(colsField);
        form.add(new JLabel("Строки"));
        form.add(rowsField);
        MathFields.enableExpressions(rowsField);
        form.add(new JLabel("X (мм)"));
        form.add(xField);
        form.add(new JLabel("Y (мм)"));
        form.add(yField);

        JButton ok = new JButton("Создать");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Отмена");
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(form);
        content.add(buttons);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Кнопка «Указать новый кабинет…» (запрос пользователя) — открывает ту же
     *  форму, что и в окне «Библиотеки» ({@link CabinetTypeDialog}), не заставляя
     *  прерывать создание экрана, чтобы завести отсутствующий тип отдельно.
     *  Созданный тип сразу попадает в личную библиотеку ({@code
     *  model.addCabinetType}) и становится выбранным в {@link #typeField}. */
    private JButton buildNewCabinetButton() {
        JButton btn = new JButton("Новый кабинет…");
        btn.addActionListener(e -> {
            CabinetType ct = new CabinetTypeDialog(this, model, null).showDialog();
            if (ct == null) {
                return;
            }
            try {
                model.addCabinetType(ct);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            typeField.addItem(ct);
            typeField.setSelectedItem(ct);
        });
        return btn;
    }

    private void onOk() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Укажите название экрана");
            }
            CabinetType type = (CabinetType) typeField.getSelectedItem();
            if (type == null) {
                throw new IllegalArgumentException("Выберите тип кабинета");
            }
            double x = parseDouble(xField.getText(), "X");
            double y = parseDouble(yField.getText(), "Y");
            ScreenMountType mountType = (ScreenMountType) mountTypeField.getSelectedItem();
            result = new Result(name, type.getId(), (int) rowsField.getValue(), (int) colsField.getValue(), x, y,
                    mountType);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Проверка данных", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parseDouble(String s, String field) {
        try {
            return MathExpr.eval(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": введите число или выражение");
        }
    }

    /** Показывает диалог; возвращает заполненные параметры или null при отмене. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
