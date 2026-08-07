package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.sync.AuthClient;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * «Аккаунт…» — логин/регистрация на сервере (нужны только для отправки
 * предложений в общую библиотеку, см. {@link ProposeDialog}/{@link AuthClient});
 * чтение библиотеки анонимно и в аккаунте не нуждается (см. {@link LibrarySyncDialog}).
 * Если сессия уже есть — показывает статус вместо форм. Стиль сети — как
 * {@link LibrarySyncDialog}/{@link UpdateDialog}.
 */
public class AccountDialog extends JDialog {

    private final SettingsManager settings;

    public static void show(Window owner, SettingsManager settings) {
        new AccountDialog(owner, settings).setVisible(true);
    }

    private AccountDialog(Window owner, SettingsManager settings) {
        super(owner, "Аккаунт", ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        rebuild();
        setLocationRelativeTo(owner);
    }

    private void rebuild() {
        setContentPane(settings.getSettings().getAuthToken() != null ? buildLoggedInPanel() : buildFormsPanel());
        pack();
    }

    private JPanel buildLoggedInPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        String username = settings.getSettings().getAuthUsername();
        String role = settings.getSettings().getAuthRole();
        JLabel status = new JLabel("Вы вошли как " + username + " (" + role + ")");
        status.setAlignmentX(LEFT_ALIGNMENT);
        content.add(status);
        content.add(Box.createVerticalStrut(14));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton logout = new JButton("Выйти");
        logout.addActionListener(e -> {
            settings.clearAuthSession();
            rebuild();
        });
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        buttons.add(logout);
        buttons.add(close);
        content.add(buttons);
        setSize(340, 150);
        return content;
    }

    private JPanel buildFormsPanel() {
        JPanel content = new JPanel(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Войти", buildLoginForm());
        tabs.addTab("Регистрация", buildRegisterForm());
        content.add(tabs, BorderLayout.CENTER);
        setSize(360, 240);
        return content;
    }

    private JPanel buildLoginForm() {
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JLabel status = new JLabel(" ");
        JButton submit = new JButton("Войти");

        JPanel form = formPanel(username, password, null, status, submit);
        submit.addActionListener(e -> {
            String name = username.getText().trim();
            submit.setEnabled(false);
            status.setText("Вход…");
            runAuth(status, submit, name, () -> new AuthClient().login(name, new String(password.getPassword())));
        });
        return form;
    }

    private JPanel buildRegisterForm() {
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JLabel status = new JLabel(" ");
        JButton submit = new JButton("Зарегистрироваться");

        JPanel form = formPanel(username, password, confirm, status, submit);
        submit.addActionListener(e -> {
            String name = username.getText().trim();
            String pass = new String(password.getPassword());
            if (!pass.equals(new String(confirm.getPassword()))) {
                status.setText("Пароли не совпадают.");
                return;
            }
            submit.setEnabled(false);
            status.setText("Регистрация…");
            runAuth(status, submit, name, () -> new AuthClient().register(name, pass));
        });
        return form;
    }

    private interface AuthCall {
        AuthClient.AuthResult call() throws Exception;
    }

    /** Сервер не отдаёт логин обратно в ответе на login/register (только token+role) —
     *  {@code username} это то, что пользователь только что сам ввёл в форму. */
    private void runAuth(JLabel status, JButton submit, String username, AuthCall call) {
        new SwingWorker<AuthClient.AuthResult, Void>() {
            @Override
            protected AuthClient.AuthResult doInBackground() throws Exception {
                return call.call();
            }

            @Override
            protected void done() {
                submit.setEnabled(true);
                try {
                    AuthClient.AuthResult result = get();
                    settings.setAuthSession(result.token(), username, result.role());
                    rebuild();
                } catch (Exception ex) {
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private JPanel formPanel(JTextField username, JPasswordField password, JPasswordField confirm,
                              JLabel status, JButton submit) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel fields = new JPanel(new GridLayout(confirm == null ? 2 : 3, 2, 6, 6));
        fields.add(new JLabel("Логин:"));
        fields.add(username);
        fields.add(new JLabel("Пароль:"));
        fields.add(password);
        if (confirm != null) {
            fields.add(new JLabel("Повтор пароля:"));
            fields.add(confirm);
        }
        fields.setAlignmentX(LEFT_ALIGNMENT);
        content.add(fields);
        content.add(Box.createVerticalStrut(10));

        status.setForeground(Palette.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        content.add(status);
        content.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(submit);
        content.add(buttons);
        return content;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
