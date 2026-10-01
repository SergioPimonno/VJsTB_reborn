package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaSheet;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Небольшое модальное окно «название схемы» — для кнопок «＋» (новая схема) и «✎»
 * (переименовать) в {@link ContextBar} (запрос пользователя 2026-09-30, пункт 8:
 * «окно редактирования названия схемы»; docs/masks-and-schema-sheets/PLAN.md, трек
 * C2). Раньше названий у схем не было вовсе — на режим приходилась ровно одна.
 *
 * <p>Ошибка («не пусто и уникально в режиме») показывается под полем ПРЯМО ВО ВРЕМЯ
 * ввода, а «OK» неактивна, пока название не годится — иначе пользователь узнавал бы
 * о занятом имени только исключением из {@code AppModel.addSchemaSheet}. Саму
 * проверку диалог НЕ дублирует: берёт готовый текст ошибки из {@link
 * AppModel#schemaSheetNameProblem} (см. {@link #problemOf}), чтобы правило жило в
 * одном месте — модели, которая всё равно бросит исключение на тот же ввод.
 */
public final class SchemaSheetNameDialog extends JDialog {

    private final AppModel model;
    private final SchemaMode mode;
    private final SchemaSheet ignore;
    private final JTextField field = new JTextField(26);
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton okButton = new JButton("OK");
    private String result;

    private SchemaSheetNameDialog(Component parent, AppModel model, SchemaMode mode, SchemaSheet ignore,
            String title, String initial) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.mode = mode;
        this.ignore = ignore;

        field.setText(initial == null ? "" : initial);
        errorLabel.setForeground(new Color(0xB00020));

        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        body.add(new JLabel("Название схемы:"), BorderLayout.NORTH);
        body.add(field, BorderLayout.CENTER);
        body.add(errorLabel, BorderLayout.SOUTH);

        JButton cancel = new JButton("Отмена");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        buttons.add(okButton);
        buttons.add(cancel);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        okButton.addActionListener(e -> confirm());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        field.addActionListener(e -> confirm());
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                revalidateInput();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                revalidateInput();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                revalidateInput();
            }
        });
        revalidateInput();

        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 360), getHeight()));
        setLocationRelativeTo(parent);
        // Выделенный текст при открытии: для переименования можно сразу печатать
        // поверх, для нового названия-подсказки («Схема питания 2») — тоже.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                field.requestFocusInWindow();
                field.selectAll();
            }
        });
    }

    /**
     * Показывает окно и возвращает введённое (обрезанное) название либо {@code null},
     * если пользователь отменил.
     *
     * @param ignore переименовываемая схема (её собственное имя не считается
     *               занятым) или {@code null} для новой схемы
     * @param initial начальный текст — имя схемы либо подсказка для новой
     *                ({@link AppModel#suggestSchemaSheetName})
     */
    public static String ask(Component parent, AppModel model, SchemaMode mode, SchemaSheet ignore,
            String title, String initial) {
        SchemaSheetNameDialog d = new SchemaSheetNameDialog(parent, model, mode, ignore, title, initial);
        d.setVisible(true);
        return d.result;
    }

    /**
     * Проверка введённого названия — русский текст ошибки или {@code null}, если
     * годится. Вынесено в статический метод, чтобы тестироваться без окна
     * (headless): делегирует правилу модели {@link AppModel#schemaSheetNameProblem}.
     */
    public static String problemOf(AppModel model, SchemaMode mode, String text, SchemaSheet ignore) {
        return model.schemaSheetNameProblem(mode, text, ignore);
    }

    private void revalidateInput() {
        String problem = problemOf(model, mode, field.getText(), ignore);
        errorLabel.setText(problem == null ? " " : problem);
        okButton.setEnabled(problem == null);
        // Подсветка поля красным — стандартный для Swing приём (outline-свойство
        // Look&Feel'ов вроде FlatLaf/Metal; на других L&F просто игнорируется).
        field.putClientProperty("JComponent.outline", problem == null ? null : "error");
    }

    private void confirm() {
        if (problemOf(model, mode, field.getText(), ignore) != null) {
            return;
        }
        result = field.getText().trim();
        dispose();
    }
}
