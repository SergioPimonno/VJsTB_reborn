package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Сетка портов контроллера для расключения сигнала: ЛКМ по порту — начать
 * (перезапустить) цепочку на этот порт. Резерв назначается либо на уровне
 * порта (2×клик — {@link PortListener#onPortBackupLinkRequested}), либо на
 * уровне целого контроллера (ПКМ по строке контроллера — см. SignalStagePanel) —
 * в обоих случаях зарезервированный порт красится и блокируется здесь одинаково.
 */
public class PortPickerPanel extends JPanel {

    public interface PortListener {
        void onPortSelected(int port);
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
        // Раньше вся сетка была ОДНИМ GridLayout на всю панель — годилось, пока
        // портов было мало и они шли одним сплошным диапазоном. Теперь портов
        // может быть НЕСКОЛЬКО отдельных пулов нумерации (по одному на карту
        // контроллера, см. ControllerType.ethernetPoolCount, баг-репорт: "у меня в
        // Н2 2 выходные карты — должен видеть 2 области портов") — вертикальный
        // стек, где на каждый пул своя мини-сетка (+ заголовок карты, если пулов
        // больше одного), собирается заново в rebuild().
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Без выбранного контроллера — сетка пуста (см. rebuild(Integer, ControllerInstance)). */
    public void rebuild(Integer activePort) {
        rebuild(activePort, null);
    }

    /** selectedController — сетка показывает ТОЛЬКО порты ЭТОГО контроллера,
     *  локальными номерами (1..N), а не сквозным номером по всем контроллерам
     *  сцены — раньше "Действующее число портов" суммировало порты всех
     *  контроллеров сцены и подписи вида "1→9" сбивали с толку, к какому
     *  контроллеру какой порт относится на самом деле (см. Task #73).
     *  null (контроллеров в сцене нет вовсе) — сетка ПУСТАЯ: раньше здесь рисовалась
     *  полностью кликабельная сетка по legacy Screen.signalPortCount (никогда не
     *  редактируемому из текущего UI, всегда равному дефолтным 8) — клик создавал
     *  цепочку, ссылающуюся на порт несуществующего контроллера ("осиротевшие"
     *  данные, баг-репорт v1.5). Панель над сеткой уже сообщает "Контроллеры не
     *  назначены" — рисовать под этим сообщением активные кнопки нечего. */
    public void rebuild(Integer activePort, ControllerInstance selectedController) {
        removeAll();
        Screen scr = model.getCurrentScreen();
        if (scr == null || selectedController == null) {
            revalidate();
            repaint();
            return;
        }
        // Цепочки хранятся на уровне СЦЕНЫ, а не экрана (см. Task #78) — порт может
        // ссылаться на цепочку, физически лежащую на любом экране этой сцены.
        List<SignalChain> chains = model.getCurrentScene() != null
                ? model.getCurrentScene().getSignalChains() : List.of();
        int offset = model.portOffsetOf(scr, selectedController);
        ControllerType t = model.getWorkspace().controllerTypeById(selectedController.getControllerTypeId());
        int rangeStart = offset + 1;
        int rangeEnd = offset + (t != null ? t.effectivePortCount() : 0);
        // Отдельная область (мини-сетка) на КАЖДЫЙ пул нумерации Ethernet-портов —
        // как правило, один пул на карту контроллера (см. ControllerType
        // .ethernetPoolCount) — Ethernet-порты каждой карты нумеруются заново от 1,
        // а не сквозным номером на весь контроллер (раньше fiber-порты первой
        // группы "съедали" первые номера, и Ethernet начинался не с 1 — баг-репорт).
        int poolCount = t != null ? t.ethernetPoolCount() : 0;
        for (int poolIdx = 0; poolIdx < poolCount; poolIdx++) {
            int portsInPool = t.ethernetPortCountInPool(poolIdx);
            if (portsInPool == 0) {
                continue;
            }
            if (poolCount > 1) {
                String cardName = !t.getCards().isEmpty() ? t.getCards().get(poolIdx).getName() : "";
                JLabel header = new JLabel("Карта " + (poolIdx + 1) + (cardName.isEmpty() ? "" : " — " + cardName));
                header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
                header.setForeground(Palette.MUTED);
                header.setAlignmentX(LEFT_ALIGNMENT);
                add(header);
            }
            JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 4, 4));
            grid.setAlignmentX(LEFT_ALIGNMENT);
            grid.setOpaque(false);
            for (int local = 1; local <= portsInPool; local++) {
                int controllerLocal = t.globalPortFor(poolIdx, local);
                int port = offset + controllerLocal;
                grid.add(portButton(scr, chains, port, activePort, t, offset, local));
            }
            add(grid);
            if (poolIdx < poolCount - 1) {
                add(javax.swing.Box.createVerticalStrut(6));
            }
        }
        revalidate();
        repaint();
    }

    private JButton portButton(Screen scr, List<SignalChain> chains, int port, Integer activePort,
                                ControllerType t, int offset, int poolLocalPort) {
        SignalChain main = model.signalChainByPort(scr, port, false);
        Integer backupLink = main != null ? main.getBackupPortNumber() : null;

        // Порт, зарезервированный ПОД бэкап другого порта, — у него самого не может
        // быть собственной цепочки (main == null для такого порта), поэтому раньше
        // он не получал никакой заливки и визуально не отличался от свободного.
        // Ищем, чей это резерв, чтобы дать ту же заливку, что и у «хозяина» —
        // по аналогии с тем, как уже прописанный порт красится цветом своей цепочки.
        SignalChain reservedFor = null;
        for (SignalChain c : chains) {
            if (c.getBackupPortNumber() != null && c.getBackupPortNumber() == port) {
                reservedFor = c;
                break;
            }
        }
        // Порт целиком принадлежит контроллеру, который сам назначен резервным для
        // другого контроллера этого экрана (см. SignalStagePanel — ПКМ по строке
        // контроллера) — такой порт не может получить свою цепочку вообще, поэтому
        // тускло закрашивается и блокируется, а не просто подсвечивается.
        boolean controllerReserved = reservedFor == null && main == null && model.isPortReservedAsBackup(scr, port);

        int localPort = poolLocalPort;
        // Если резервный порт лежит в диапазоне ЭТОГО ЖЕ контроллера — раскладываем
        // его на (пул, локальный номер) той же формулой, что и основной, показывая
        // "К{пул}·{номер}" (или просто номер, если у контроллера всего один пул —
        // согласованно с основным); если резерв на ДРУГОМ контроллере или указывает
        // на fiber-порт (не входит ни в один Ethernet-пул) — локальный номер был бы
        // бессмысленным/обманчивым, показываем глобальный со знаком "P".
        String backupLabel = null;
        if (backupLink != null) {
            int backupControllerLocal = backupLink - offset;
            int[] backupPool = t != null ? t.ethernetPoolLocalPort(backupControllerLocal) : null;
            if (backupPool != null) {
                backupLabel = t.ethernetPoolCount() > 1
                        ? "К" + (backupPool[0] + 1) + "·" + backupPool[1]
                        : String.valueOf(backupPool[1]);
            } else {
                backupLabel = "P" + backupLink;
            }
        }

        // Ширина у всех кнопок ОДИНАКОВАЯ: GridLayout растягивает каждую ячейку
        // до размера самой широкой — переменная ширина раздула бы всю сетку.
        JButton b = new JButton(String.valueOf(localPort) + (backupLabel != null ? "→" + backupLabel : ""));
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
        } else if (reservedFor != null) {
            // Приглушённый (не полный) цвет "хозяина" — этот порт сам НЕ активен, а
            // только ждёт подхвата на случай отказа основного, полноценный яркий цвет
            // выглядел бы так же "занято", как у реально прописанного порта (см. Task #74).
            int idx = chains.indexOf(reservedFor);
            b.setBackground(dim(Palette.signalColor(idx)));
            b.setForeground(Palette.TEXT);
        } else if (controllerReserved) {
            b.setBackground(Palette.BORDER);
            b.setForeground(Palette.MUTED);
        }
        b.setEnabled(!controllerReserved);
        b.setToolTipText("Порт " + localPort + " (глобально P" + port + ")"
                + (main != null ? " · цепочка: " + main.getCabinetInstanceIds().size() + " каб." : " · без цепочки")
                + (backupLink != null ? " · резервный порт: " + backupLabel : "")
                + (reservedFor != null ? " · зарезервирован под бэкап порта " + reservedFor.getPortNumber() : "")
                + (controllerReserved ? " · порт принадлежит контроллеру, зарезервированному под бэкап" : "")
                + " · 2×клик — назначить резервный порт");
        b.setBorder(reservedFor != null || backupLink != null || controllerReserved
                ? BorderFactory.createLineBorder(Color.ORANGE, 2)
                : BorderFactory.createLineBorder(Palette.BORDER, 1));

        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    listener.onPortBackupLinkRequested(port);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    listener.onPortSelected(port);
                }
            }
        });
        return b;
    }

    /** Приглушённая версия цвета — блендинг к фону панели вполовину (см. Task #74:
     *  бэкап-порты должны визуально отличаться от активных, а не быть такими же яркими). */
    private static Color dim(Color c) {
        return new Color(
                (c.getRed() + Palette.PANEL.getRed()) / 2,
                (c.getGreen() + Palette.PANEL.getGreen()) / 2,
                (c.getBlue() + Palette.PANEL.getBlue()) / 2);
    }
}
