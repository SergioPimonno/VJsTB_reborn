package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Сетка портов контроллера для расключения сигнала: ЛКМ по порту — начать
 * (перезапустить) цепочку на этот порт, ПКМ — переключить пометку «бэкап»
 * (для бэкапа расключение кабинетов не требуется).
 */
public class PortPickerPanel extends JPanel {

    public interface PortListener {
        void onPortSelected(int port);
        void onPortBackupToggled(int port);
        /** Двойной клик — запросить назначение резервного (другого) порта для этого порта. */
        void onPortBackupLinkRequested(int port);
    }

    private final AppModel model;
    private final PortListener listener;

    /** Фиксированное число колонок — раскладка не зависит от текущей (возможно ещё не
     *  установленной на момент первой сборки) ширины контейнера, в отличие от FlowLayout. */
    private static final int COLUMNS = 6;

    public PortPickerPanel(AppModel model, PortListener listener) {
        this.model = model;
        this.listener = listener;
        setLayout(new GridLayout(0, COLUMNS, 4, 4));
    }

    public void rebuild(Integer activePort) {
        removeAll();
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            revalidate();
            repaint();
            return;
        }
        List<SignalChain> chains = scr.getSignalChains();
        for (int p = 1; p <= model.effectiveSignalPortCount(scr); p++) {
            add(portButton(scr, chains, p, activePort));
        }
        revalidate();
        repaint();
    }

    private JButton portButton(Screen scr, List<SignalChain> chains, int port, Integer activePort) {
        SignalChain main = scr.signalChainByPort(port, false);
        boolean hasBackup = scr.signalChainByPort(port, true) != null;
        Integer backupLink = main != null ? main.getBackupPortNumber() : null;

        // Ширина у всех кнопок ОДИНАКОВАЯ: GridLayout растягивает каждую ячейку
        // до размера самой широкой — переменная ширина раздула бы всю сетку.
        JButton b = new JButton(String.valueOf(port) + (hasBackup ? "⛨" : "") + (backupLink != null ? "→" + backupLink : ""));
        b.setPreferredSize(new Dimension(42, 30));
        b.setMargin(new java.awt.Insets(1, 1, 1, 1));
        b.setFont(b.getFont().deriveFont(10f));
        b.setFocusable(false);

        if (Objects.equals(activePort, port)) {
            b.setBackground(Palette.ACCENT);
            b.setForeground(java.awt.Color.BLACK);
        } else if (main != null) {
            int idx = chains.indexOf(main);
            b.setBackground(Palette.signalColor(idx));
            b.setForeground(java.awt.Color.BLACK);
        }
        b.setToolTipText("Порт " + port
                + (main != null ? " · цепочка: " + main.getCabinetInstanceIds().size() + " каб." : " · без цепочки")
                + (hasBackup ? " · есть бэкап-цепочка на этом же порту" : "")
                + (backupLink != null ? " · резервный порт: " + backupLink : "")
                + " · 2×клик — назначить резервный порт");
        b.setBorder(backupLink != null
                ? BorderFactory.createLineBorder(Color.ORANGE, 2)
                : (hasBackup
                        ? BorderFactory.createLineBorder(Palette.ACCENT, 2)
                        : BorderFactory.createLineBorder(Palette.BORDER, 1)));

        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    listener.onPortBackupLinkRequested(port);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    listener.onPortSelected(port);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    listener.onPortBackupToggled(port);
                }
            }
        });
        return b;
    }
}
