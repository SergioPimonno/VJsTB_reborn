package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.service.NetworkPingService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Живой пинг устройства — ПКМ по блоку в {@link NetworkCanvasPanel} → «Пинг…»
 *  (баг-репорт: раньше ждали фиксированные 4 пакета и показывали весь вывод
 *  одним куском ПОСЛЕ завершения — пользователю нужно видеть ответы по мере
 *  поступления). Немодальный, ОДИН на устройство за раз (вызывающая сторона
 *  следит за этим) — пинг непрерывный (см. {@link NetworkPingService#startPing}),
 *  останавливается при закрытии окна ЛЮБЫМ способом (крестик, Ctrl+F4 и т.д. —
 *  {@link WindowAdapter#windowClosing}), не только по кнопке «Стоп», чтобы
 *  процесс ping.exe не оставался висеть в фоне после закрытия. */
public class NetworkPingDialog extends JDialog {

    private final JTextArea output = new JTextArea(18, 56);
    private final JScrollPane scroll;
    private NetworkPingService.PingSession session;

    public NetworkPingDialog(Window owner, String host) {
        super(owner, "Пинг " + host, ModalityType.MODELESS);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        scroll = new JScrollPane(output);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(scroll, BorderLayout.CENTER);

        JButton stopBtn = new JButton("Остановить");
        stopBtn.addActionListener(e -> stopPing());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(stopBtn);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopPing();
            }
        });

        session = NetworkPingService.startPing(host, this::appendLine, () -> {
            appendLine("--- пинг остановлен ---");
            stopBtn.setEnabled(false);
        });
    }

    private void appendLine(String line) {
        output.append(line + "\n");
        JScrollBar bar = scroll.getVerticalScrollBar();
        SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    private void stopPing() {
        if (session != null) {
            session.stop();
            session = null;
        }
    }
}
