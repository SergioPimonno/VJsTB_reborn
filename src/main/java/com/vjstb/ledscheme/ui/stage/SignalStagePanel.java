package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.service.ScreenStats;
import com.vjstb.ledscheme.ui.CanvasPanel;
import com.vjstb.ledscheme.ui.ChainInteractionController;
import com.vjstb.ledscheme.ui.ChainPatterns;
import com.vjstb.ledscheme.ui.ContextBar;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.PortPickerPanel;
import com.vjstb.ledscheme.ui.RadialMenu;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import com.vjstb.ledscheme.ui.SchemaPanel;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * Этап «Сигнал»: холст + расключение по портам контроллера. ЛКМ по порту — начать
 * цепочку, ПКМ — переключить бэкап (без прорисовки кабинетов для резервного порта);
 * либо (по переключателю) общая yEd-подобная схема сигнала площадки.
 */
public class SignalStagePanel extends JPanel {

    private static final String VIEW_CHAIN = "chain";
    private static final String VIEW_SCHEMA = "schema";

    private final AppModel model;
    private final CanvasPanel canvas;
    private final ChainInteractionController chainCtrl;
    private final PortPickerPanel portPicker;
    private final CardLayout viewCards = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewCards);
    private final SchemaPanel schemaPanel;
    private final JToggleButton chainViewBtn = new JToggleButton("Расключение экрана", true);
    private final JToggleButton schemaViewBtn = new JToggleButton("Общая схема сигнала");
    private final JCheckBox showAllScreens = new JCheckBox("Показать все экраны сцены");
    private final JToggleButton quickConnectBtn = new JToggleButton("⚡ Быстрое подключение");
    private final SceneCanvasPanel sceneOverview;
    private final JScrollPane canvasScroll;
    private final com.vjstb.ledscheme.settings.SettingsManager settings;
    private final SceneCanvasPanel cornerPreview;
    private final JPanel cornerPreviewHost;

    private static final int CORNER_W = 260;
    private static final int CORNER_H = 170;
    private static final int CORNER_MARGIN = 10;

    private Integer activePort;
    /** id контроллера, помеченного ПКМ как «в резерв» — следующий ЛКМ по ДРУГОМУ
     *  контроллеру в списке создаёт связку main→backup между ними (весь контроллер
     *  целиком подхватывает сигнал другого, см. AppModel.setControllerBackupLink). */
    private String pendingBackupControllerId;
    /** id контроллера, чьи порты сейчас показаны в сетке (ЛКМ по строке контроллера
     *  выбирает его) — расключение всегда идёт по ОДНОМУ конкретному контроллеру,
     *  локальными номерами портов, а не сквозной суммой по всем контроллерам сцены
     *  (см. Task #73). null — контроллеров в сцене нет вовсе (старый ручной режим). */
    private String selectedControllerId;
    /** Индекс пула/карты (0-based, см. ControllerType.ethernetPoolCount), которую
     *  сейчас "показывают" цифровые хоткеи 1-9/0 и выбор по умолчанию — раньше
     *  хоткеи всегда целили в пул 0, а первая карта контроллера часто входная
     *  (без единого выходного Ethernet-порта, например у Novastar H-серии) — тогда
     *  хоткеи молча не делали вообще ничего (баг-репорт). null — ещё не выбрано
     *  явно, тогда берётся первый непустой пул (см. resolveCardPool). Сбрасывается
     *  при смене выбранного контроллера. */
    private Integer selectedCardPool;

    private final JPanel chainListPanel = new JPanel();
    private javax.swing.JComponent portsSection;
    private javax.swing.JComponent chainsSection;
    private final JComboBox<Integer> cardPoolCombo = new JComboBox<>();
    private JPanel cardPoolRow;

    private final JComboBox<ControllerType> controllerTypeCombo = new JComboBox<>();
    private final JPanel controllerListPanel = new JPanel();
    private javax.swing.JComponent controllersSection;
    private final JLabel portCountLabel = new JLabel(" ");

    private final JLabel statCabinetType = new JLabel("—");
    private final JLabel statRes = new JLabel("—");
    private final JLabel statSize = new JLabel("—");
    private final JLabel statCount = new JLabel("—");
    private final JLabel statPower = new JLabel("—");
    private final JLabel statWeight = new JLabel("—");

    public SignalStagePanel(AppModel model, com.vjstb.ledscheme.settings.SettingsManager settings) {
        this.model = model;
        this.settings = settings;
        this.chainCtrl = new ChainInteractionController(model, this::refresh);
        // Клик по непрописанному кабинету сам начинает цепочку для ТЕКУЩЕГО
        // выбранного порта — кнопка порта (или цифровой хоткей) только выбирает
        // цель, не запуская построение сама по себе (см. selectPort ниже).
        this.chainCtrl.setStarter(cabId -> {
            Screen scr = model.getCurrentScreen();
            if (scr == null || activePort == null) {
                return null;
            }
            if (model.isControllerLevelBackupPort(scr, activePort)) {
                return null;
            }
            // Проверяем по ВСЕЙ сцене, а не только этому экрану — кабинет мог быть
            // занят цепочкой, которая физически продолжается сюда с другого экрана.
            if (model.isCabinetWiredForSignal(cabId)) {
                return null;
            }
            int port = activePort;
            return ids -> {
                model.addSignalChain(port, false, ids);
                // Автопереход на следующий свободный порт ТОЙ ЖЕ карты после
                // сохранения цепочки — раньше activePort оставался прежним, пока
                // пользователь явно не кликал другую кнопку; при быстрой прописке
                // нескольких серпантинов подряд («Быстрое подключение» много раз без
                // выбора нового порта между ними) это тихо сажало ВТОРУЮ цепочку на
                // ТОТ ЖЕ порт первой (баг-репорт: два "Порт К2·1" в списке цепочек).
                advanceActivePortAfterCommit(port);
            };
        });
        this.chainCtrl.setOnCommitError(msg ->
                JOptionPane.showMessageDialog(this, msg, "Не удалось завершить цепочку",
                        JOptionPane.ERROR_MESSAGE));
        this.canvas = new CanvasPanel(model, chainCtrl);
        // «Быстрое подключение» (как в NovaLCT) — протяжка выделяет прямоугольную
        // область кабинетов, радиальное меню выбирает шаблон серпантина, скрытые и
        // уже занятые кабинеты области просто выпадают из последовательности.
        this.canvas.setQuickConnectListener((rowStart, rowEnd, colStart, colEnd, screenX, screenY) ->
                showQuickConnectMenu(rowStart, rowEnd, colStart, colEnd, screenX, screenY));
        this.portPicker = new PortPickerPanel(model, new PortPickerPanel.PortListener() {
            @Override
            public void onPortSelected(int port) {
                selectPort(port);
            }

            @Override
            public void onPortBackupLinkRequested(int port) {
                // Диалог работает номером В ПРЕДЕЛАХ ETHERNET-ПУЛА той же карты, что и
                // сам исходный порт (см. ControllerType.ethernetPoolLocalPort/
                // ethernetPoolCount, Task #17 follow-up: "отдельные пулы нумерации на
                // карту") — так же, как и сама сетка портов; раньше номер был локальным
                // ко ВСЕМУ контроллеру сквозным числом с "дырками" от fiber-портов
                // (см. Task #73/#75), что не совпадало с новой пер-карточной нумерацией
                // сетки. Резерв на ДРУГОЙ карте/контроллере этим диалогом не назначить —
                // для подавляющего большинства контроллеров (одна карта или без карт)
                // никакого ограничения на самом деле нет.
                Screen scr = model.getCurrentScreen();
                if (scr == null) return;
                ControllerInstance selected = selectedController(scr);
                if (selected == null) return;
                int offset = model.portOffsetOf(scr, selected);
                ControllerType t = model.getWorkspace().controllerTypeById(selected.getControllerTypeId());
                int[] pool = t != null ? t.ethernetPoolLocalPort(port - offset) : null;
                if (pool == null) return;
                int poolIdx = pool[0];
                int maxLocal = t.ethernetPortCountInPool(poolIdx);
                SignalChain main = model.signalChainByPort(scr, port, false);
                Integer currentGlobal = main != null ? main.getBackupPortNumber() : null;
                Integer currentLocal = null;
                String crossControllerNote = "";
                if (currentGlobal != null) {
                    int[] currentPool = t.ethernetPoolLocalPort(currentGlobal - offset);
                    if (currentPool != null && currentPool[0] == poolIdx) {
                        currentLocal = currentPool[1];
                    } else {
                        crossControllerNote = " (сейчас резерв — порт другой карты/контроллера сцены;"
                                + " это окно назначает резерв только в пределах текущей карты)";
                    }
                }
                String cardNote = t.ethernetPoolCount() > 1 ? " (карта " + (poolIdx + 1) + ")" : "";
                String input = JOptionPane.showInputDialog(SignalStagePanel.this,
                        "Номер резервного порта" + cardNote + " для порта " + pool[1]
                                + crossControllerNote + " (пусто — снять):",
                        currentLocal != null ? String.valueOf(currentLocal) : "");
                if (input == null) return;
                input = input.trim();
                try {
                    Integer backupLocal = input.isEmpty() ? null : Integer.valueOf(input);
                    if (backupLocal != null && (backupLocal < 1 || backupLocal > maxLocal)) {
                        JOptionPane.showMessageDialog(SignalStagePanel.this,
                                "Номер порта должен быть от 1 до " + maxLocal,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    Integer backupGlobal = backupLocal != null ? offset + t.globalPortFor(poolIdx, backupLocal) : null;
                    model.setSignalBackupPortLink(port, backupGlobal);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(SignalStagePanel.this, "Введите число порта",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(SignalStagePanel.this, ex.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        this.sceneOverview = new SceneCanvasPanel(model, settings);
        // "Показать все экраны сцены" был только для просмотра — весь смысл режима
        // (видеть всю сцену и осознанно распределять нагрузку) требует уметь
        // прописывать активный экран прямо отсюда, не переключаясь на одиночный вид.
        this.sceneOverview.setChainController(chainCtrl);

        canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        showAllScreens.addActionListener(e -> {
            boolean all = showAllScreens.isSelected();
            sceneOverview.setDetailMode(all, false);
            canvasScroll.setViewportView(all ? sceneOverview : canvas);
            updateCornerPreviewVisibility();
        });

        // Корнер-виджет (всегда видимый мини-обзор сцены в правом нижнем углу холста,
        // выключаемый в «Персонализации») — накладывается поверх canvasScroll через
        // JLayeredPane, а не встраивается в раскладку, чтобы не отнимать место у холста.
        cornerPreview = new SceneCanvasPanel(model, settings);
        cornerPreview.setDetailMode(true, false, true);
        cornerPreviewHost = new JPanel(new BorderLayout());
        cornerPreviewHost.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        cornerPreviewHost.add(cornerPreview, BorderLayout.CENTER);
        cornerPreviewHost.setBounds(0, 0, CORNER_W, CORNER_H);

        JLayeredPane canvasLayered = new JLayeredPane();
        canvasLayered.setLayout(null);
        canvasLayered.add(canvasScroll, JLayeredPane.DEFAULT_LAYER);
        canvasLayered.add(cornerPreviewHost, JLayeredPane.PALETTE_LAYER);
        canvasLayered.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                canvasScroll.setBounds(0, 0, canvasLayered.getWidth(), canvasLayered.getHeight());
                cornerPreviewHost.setBounds(canvasLayered.getWidth() - CORNER_W - CORNER_MARGIN,
                        canvasLayered.getHeight() - CORNER_H - CORNER_MARGIN, CORNER_W, CORNER_H);
            }
        });
        settings.addListener(this::updateCornerPreviewVisibility);

        JScrollPane sideScroll = new JScrollPane(buildSide());
        sideScroll.setBorder(null);
        sideScroll.setMinimumSize(new Dimension(180, 100));

        JSplitPane perScreen = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasLayered, sideScroll);
        perScreen.setContinuousLayout(true);
        perScreen.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, "signal.chainSplit", perScreen, 1.0 - (SIDE_WIDTH + 20) / 1360.0);

        schemaPanel = new SchemaPanel(model, SchemaMode.SIGNAL, settings);
        schemaPanel.setOnScreenActivated(scr -> {
            model.selectScreen(scr);
            chainViewBtn.setSelected(true);
            viewCards.show(viewContainer, VIEW_CHAIN);
        });

        viewContainer.add(perScreen, VIEW_CHAIN);
        viewContainer.add(schemaPanel, VIEW_SCHEMA);

        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(chainViewBtn);
        viewGroup.add(schemaViewBtn);
        chainViewBtn.addActionListener(e -> viewCards.show(viewContainer, VIEW_CHAIN));
        schemaViewBtn.addActionListener(e -> viewCards.show(viewContainer, VIEW_SCHEMA));
        quickConnectBtn.setToolTipText("Протяжка ЛКМ по холсту выделяет область — радиальное меню предложит"
                + " шаблон серпантина для быстрой прописки (как в NovaLCT)");
        quickConnectBtn.addActionListener(e -> canvas.setQuickConnectMode(quickConnectBtn.isSelected()));
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        toggleRow.add(chainViewBtn);
        toggleRow.add(schemaViewBtn);
        toggleRow.add(showAllScreens);
        toggleRow.add(quickConnectBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new ContextBar(model, true), BorderLayout.NORTH);
        top.add(toggleRow, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(viewContainer, BorderLayout.CENTER);

        model.addListener(this::refresh);
        refresh();
        updateCornerPreviewVisibility();
    }

    /** Радиальное меню из 8 шаблонов серпантина (NovaLCT-style «Быстрая прописка») —
     *  выбор шаблона строит и сразу сохраняет цепочку для ТЕКУЩЕГО выбранного порта
     *  по всем не скрытым и ещё не занятым (для сигнала) кабинетам выделенной области. */
    private void showQuickConnectMenu(int rowStart, int rowEnd, int colStart, int colEnd, int screenX, int screenY) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        java.util.List<RadialMenu.Item> items = new java.util.ArrayList<>();
        for (ChainPatterns.Pattern pattern : ChainPatterns.Pattern.values()) {
            items.add(RadialMenu.Item.leaf(pattern.getLabel(), new com.vjstb.ledscheme.ui.ChainPatternIcon(pattern), () -> {
                java.util.List<String> ids = ChainPatterns.orderedIds(scr, rowStart, rowEnd, colStart, colEnd,
                        pattern, cabId -> !model.isCabinetWiredForSignal(cabId));
                String error = chainCtrl.buildAndCommit(ids);
                if (error != null) {
                    JOptionPane.showMessageDialog(this, error, "Быстрое подключение", JOptionPane.ERROR_MESSAGE);
                }
            }));
        }
        RadialMenu.show(canvas, screenX, screenY, items);
    }

    public ChainInteractionController chainController() {
        return chainCtrl;
    }

    public CanvasPanel canvas() {
        return canvas;
    }

    /** Человекочитаемая подпись ГЛОБАЛЬНОГО номера порта — локальный номер в пределах
     *  его собственного контроллера (плюс "C{n}·" перед ним, если контроллеров в
     *  сцене несколько — иначе к какому контроллеру относится порт неоднозначно),
     *  а не сквозной номер по всей сцене. Раньше список "Существующие цепочки"
     *  показывал сквозной номер, который не соответствовал локальным номерам сетки
     *  портов (см. Task #75) — выглядело так, будто цепочка "потеряла" свой порт. */
    private String portDisplayLabel(Screen scr, int globalPort) {
        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        ControllerInstance owner = model.controllerForPort(scr, globalPort);
        if (owner == null) {
            return String.valueOf(globalPort);
        }
        int offset = model.portOffsetOf(scr, owner);
        int controllerLocal = globalPort - offset;
        ControllerType t = model.getWorkspace().controllerTypeById(owner.getControllerTypeId());
        // Раскладываем на (пул/карта, номер В ПРЕДЕЛАХ ETHERNET-пула) — та же формула,
        // что и сетка портов (см. PortPickerPanel/ControllerType.ethernetPoolLocalPort) —
        // раньше здесь показывался номер, сквозной по ВСЕМУ контроллеру (с "дырками"
        // от fiber-портов), не совпадающий с новой пер-карточной нумерацией сетки.
        int[] pool = t != null ? t.ethernetPoolLocalPort(controllerLocal) : null;
        String portPart;
        if (pool != null && t.ethernetPoolCount() > 1) {
            portPart = "К" + (pool[0] + 1) + "·" + pool[1];
        } else {
            portPart = String.valueOf(pool != null ? pool[1] : controllerLocal);
        }
        if (sceneControllers.size() <= 1) {
            return portPart;
        }
        int idx = sceneControllers.indexOf(owner) + 1;
        return "C" + idx + "·" + portPart;
    }

    /** Индексы карт/пулов КОНТРОЛЛЕРА, у которых реально есть хотя бы один выходной
     *  Ethernet-порт — карты только со входными портами (например, входная карта
     *  H2) или только с fiber-портами дают пустой пул и не участвуют в выборе
     *  "текущей карты" (незачем предлагать переключиться на карту, где вообще
     *  нечего расключать). Пустой список — у контроллера вовсе нет выходных
     *  Ethernet-портов (t == null или все карты входные/оптические). */
    private static List<Integer> nonEmptyEthernetPools(ControllerType t) {
        if (t == null) {
            return List.of();
        }
        List<Integer> result = new java.util.ArrayList<>();
        for (int p = 0; p < t.ethernetPoolCount(); p++) {
            if (t.ethernetPortCountInPool(p) > 0) {
                result.add(p);
            }
        }
        return result;
    }

    /** Контроллер, чьи порты сейчас показаны — выбранный явно (ЛКМ по строке), или
     *  первый в сцене по умолчанию, если выбор ещё не сделан/устарел (контроллер
     *  удалён). null — контроллеров в сцене нет вовсе. */
    private ControllerInstance selectedController(Screen scr) {
        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        if (sceneControllers.isEmpty()) {
            return null;
        }
        if (selectedControllerId != null) {
            for (ControllerInstance ci : sceneControllers) {
                if (ci.getId().equals(selectedControllerId)) {
                    return ci;
                }
            }
        }
        return sceneControllers.get(0);
    }

    /** Выбирает порт как цель для СЛЕДУЮЩЕЙ цепочки — сама цепочка теперь
     *  начинается по клику на непрописанный кабинет (см. ChainInteractionController
     *  .setStarter в конструкторе), а не здесь. */
    private void selectPort(int port) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        if (model.controllersInScene(model.getCurrentScene()).isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "В сцене нет ни одного контроллера — сначала добавьте контроллер выше,"
                            + " иначе цепочка будет ссылаться на несуществующий порт.",
                    "Контроллер не назначен", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (model.isControllerLevelBackupPort(scr, port)) {
            JOptionPane.showMessageDialog(this,
                    "Порт " + port + " принадлежит контроллеру, целиком зарезервированному под резерв другого"
                            + " контроллера сцены, — не может иметь собственную цепочку.",
                    "Порт зарезервирован под бэкап", JOptionPane.WARNING_MESSAGE);
            return;
        }
        activePort = port;
        // Завершает (сохраняет) то, что строилось для ПРЕЖНЕГО порта.
        chainCtrl.finish();
        refresh();
    }

    /** После сохранения цепочки на {@code justCommittedPort} — переводит activePort
     *  на СЛЕДУЮЩИЙ свободный (без своей цепочки, не зарезервированный под бэкап)
     *  Ethernet-порт ТОЙ ЖЕ карты/пула. Не находит такой — activePort остаётся как
     *  есть (пул исчерпан или контроллер без карт/пулов), клик по кнопке порта
     *  по-прежнему остаётся основным способом переключиться. Без этого при
     *  нескольких «Быстрых подключениях» подряд (протяжка → выбор шаблона серпантина
     *  → повтор, без явного клика по новой кнопке порта между ними) вторая цепочка
     *  молча садилась на ТОТ ЖЕ порт, что и первая (баг-репорт: два одинаковых
     *  "Порт К2·1" в списке цепочек при быстрой последовательной прописке). */
    private void advanceActivePortAfterCommit(int justCommittedPort) {
        Screen scr = model.getCurrentScreen();
        ControllerInstance owner = scr != null ? model.controllerForPort(scr, justCommittedPort) : null;
        if (owner == null) {
            return;
        }
        ControllerType t = model.getWorkspace().controllerTypeById(owner.getControllerTypeId());
        if (t == null) {
            return;
        }
        int offset = model.portOffsetOf(scr, owner);
        int[] pool = t.ethernetPoolLocalPort(justCommittedPort - offset);
        if (pool == null) {
            return;
        }
        int poolIdx = pool[0];
        int poolSize = t.ethernetPortCountInPool(poolIdx);
        for (int local = pool[1] + 1; local <= poolSize; local++) {
            int candidateGlobal = offset + t.globalPortFor(poolIdx, local);
            if (model.signalChainByPort(scr, candidateGlobal, false) == null
                    && !model.isPortReservedAsBackup(scr, candidateGlobal)) {
                activePort = candidateGlobal;
                return;
            }
        }
    }

    /** Хоткей 1-9/0 (глобальный обработчик в MainFrame) — то же самое, что клик по
     *  кнопке порта в сетке портов контроллера. Цифра — номер порта В ПРЕДЕЛАХ
     *  Ethernet-пула ТЕКУЩЕЙ выбранной карты показанного контроллера (см.
     *  selectedCardPool/ControllerType.ethernetPoolLocalPort/globalPortFor) — не
     *  обязательно первой: у многих контроллеров (например Novastar H-серии)
     *  первая карта входная и не имеет вообще ни одного выходного Ethernet-порта,
     *  раньше хоткей был жёстко привязан к пулу 0 и на таких контроллерах молча
     *  ничего не делал (баг-репорт: "первые карты обычно входные"). Для
     *  контроллеров с одной (или без) картой выбор не показывается пользователю
     *  и всегда указывает на единственный существующий пул — поведение не
     *  меняется. */
    public void selectPortByHotkey(int poolLocalPort) {
        Screen scr = model.getCurrentScreen();
        if (scr == null) {
            return;
        }
        ControllerInstance selected = selectedController(scr);
        if (selected == null) {
            // Контроллеров в сцене нет вовсе — раньше здесь падали на legacy
            // Screen.signalPortCount и хоткей мог создать цепочку на несуществующий
            // порт (тот же баг, что и в selectPort/PortPickerPanel).
            return;
        }
        int offset = model.portOffsetOf(scr, selected);
        ControllerType t = model.getWorkspace().controllerTypeById(selected.getControllerTypeId());
        if (t == null) {
            return;
        }
        List<Integer> nonEmptyPools = nonEmptyEthernetPools(t);
        if (nonEmptyPools.isEmpty()) {
            return;
        }
        int pool = selectedCardPool != null && nonEmptyPools.contains(selectedCardPool)
                ? selectedCardPool : nonEmptyPools.get(0);
        int controllerLocal = t.globalPortFor(pool, poolLocalPort);
        if (controllerLocal < 0) {
            return;
        }
        selectPort(offset + controllerLocal);
    }

    private static final int SIDE_WIDTH = 300;

    private JPanel buildSide() {
        JPanel body = UiKit.vboxFixedWidth(SIDE_WIDTH);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        controllerListPanel.setLayout(new BoxLayout(controllerListPanel, BoxLayout.Y_AXIS));
        JPanel controllersBody = UiKit.vbox();
        controllersBody.add(controllerListPanel);
        JPanel addRow = new JPanel(new BorderLayout(4, 0));
        addRow.setAlignmentX(LEFT_ALIGNMENT);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        controllerTypeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ControllerType ct) {
                    setText(ct.getName() + " (" + ct.effectivePortCount() + " п.)");
                }
                return this;
            }
        });
        JButton addCtrl = new JButton("+ добавить");
        addCtrl.addActionListener(e -> {
            Screen scr = model.getCurrentScreen();
            ControllerType sel = (ControllerType) controllerTypeCombo.getSelectedItem();
            if (scr != null && sel != null) {
                model.addControllerToScreen(scr, sel.getId());
            }
        });
        JButton editCtrl = new JButton("Редактировать…");
        editCtrl.setToolTipText("Открыть выбранный тип для правки — исходный образец в библиотеке"
                + " не меняется, изменения сохраняются как НОВЫЙ тип");
        editCtrl.addActionListener(e -> {
            ControllerType sel = (ControllerType) controllerTypeCombo.getSelectedItem();
            if (sel == null) {
                return;
            }
            ControllerType edited = new com.vjstb.ledscheme.ui.ControllerTypeDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this), model, sel).showDialog();
            if (edited != null) {
                // Существующий образец в библиотеке остаётся нетронутым — правки
                // создают НОВУЮ запись (см. Task #58), поэтому не переиспользуем id.
                edited.setId(java.util.UUID.randomUUID().toString());
                try {
                    model.addControllerType(edited);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JButton cardsCtrl = new JButton("Карты…");
        cardsCtrl.setToolTipText("Комплектация карт выбранного (в списке слева) типа контроллера — правит"
                + " СРАЗУ библиотечный тип (как и одноимённая кнопка в разделе «Библиотеки»), без создания"
                + " нового типа: раньше собрать карты для контроллера можно было только через отдельный"
                + " заход в «Библиотеки», что было неудобно прямо во время расключения сцены.");
        cardsCtrl.addActionListener(e -> {
            ControllerType sel = (ControllerType) controllerTypeCombo.getSelectedItem();
            if (sel == null) {
                return;
            }
            com.vjstb.ledscheme.ui.CardsConfigDialog dlg = new com.vjstb.ledscheme.ui.CardsConfigDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this),
                    sel.getName(), com.vjstb.ledscheme.ui.CardsConfigDialog.forController(model, sel), model);
            dlg.setVisible(true);
        });
        addRow.add(controllerTypeCombo, BorderLayout.CENTER);
        JPanel addButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButtons.add(addCtrl);
        addButtons.add(editCtrl);
        addButtons.add(cardsCtrl);
        addRow.add(addButtons, BorderLayout.EAST);
        controllersBody.add(UiKit.vgap());
        controllersBody.add(addRow);
        controllersSection = UiKit.dynamicSection("Контроллеры сцены", controllersBody);
        body.add(controllersSection);
        body.add(UiKit.vgap());

        portCountLabel.setForeground(Palette.MUTED);
        body.add(portCountLabel);
        body.add(UiKit.vgap());

        // Видна, только если у контроллера НЕСКОЛЬКО карт с выходными Ethernet-
        // портами — выбирает, какую из них показывают хоткеи 1-9/0 (сама сетка
        // портов справа всегда рисует ВСЕ карты сразу, этот выбор влияет только
        // на хоткеи, см. selectPortByHotkey).
        cardPoolCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Integer poolIdx) {
                    Screen scr = model.getCurrentScreen();
                    ControllerInstance sel = scr != null ? selectedController(scr) : null;
                    ControllerType t = sel != null
                            ? model.getWorkspace().controllerTypeById(sel.getControllerTypeId()) : null;
                    String cardName = t != null && !t.getCards().isEmpty() && poolIdx < t.getCards().size()
                            ? t.getCards().get(poolIdx).getName() : "";
                    setText("Карта " + (poolIdx + 1) + (cardName.isEmpty() ? "" : " — " + cardName));
                }
                return this;
            }
        });
        cardPoolCombo.addActionListener(e -> {
            Integer sel = (Integer) cardPoolCombo.getSelectedItem();
            if (sel != null && !sel.equals(selectedCardPool)) {
                selectedCardPool = sel;
                refresh();
            }
        });
        cardPoolRow = new JPanel(new BorderLayout(6, 0));
        cardPoolRow.setAlignmentX(LEFT_ALIGNMENT);
        cardPoolRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        cardPoolRow.add(new JLabel("Текущая карта (для хоткеев)"), BorderLayout.WEST);
        cardPoolRow.add(cardPoolCombo, BorderLayout.CENTER);
        body.add(cardPoolRow);
        body.add(UiKit.vgap());

        // portPicker наполняется кнопками в rebuild(), уже после первой сборки — динамическая секция.
        portsSection = UiKit.dynamicSection("Порты контроллера", portPicker);
        body.add(portsSection);
        body.add(UiKit.vgap());

        JButton clear = new JButton("Очистить цепочки сигнала");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Очистить все цепочки сигнала на экране?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                model.clearChainsOfMode();
            }
        });
        body.add(UiKit.vgap());
        body.add(clear);

        chainListPanel.setLayout(new BoxLayout(chainListPanel, BoxLayout.Y_AXIS));
        body.add(UiKit.vgap());
        chainsSection = UiKit.dynamicSection("Существующие цепочки", chainListPanel);
        body.add(chainsSection);

        JPanel statsBody = UiKit.vbox();
        statsBody.add(statRow("Тип кабинета", statCabinetType));
        statsBody.add(statRow("Разрешение", statRes));
        statsBody.add(statRow("Физический размер", statSize));
        statsBody.add(statRow("Кабинетов", statCount));
        statsBody.add(statRow("Мощность", statPower));
        statsBody.add(statRow("Вес", statWeight));
        body.add(UiKit.vgap());
        body.add(UiKit.section("Статистика экрана", statsBody));
        body.add(javax.swing.Box.createVerticalGlue());

        return body;
    }

    private void refresh() {
        Screen scr = model.getCurrentScreen();
        boolean has = scr != null;

        DefaultComboBoxModel<ControllerType> ctrlModel = new DefaultComboBoxModel<>();
        for (ControllerType ct : model.getControllerTypes()) {
            ctrlModel.addElement(ct);
        }
        controllerTypeCombo.setModel(ctrlModel);

        controllerListPanel.removeAll();
        // Контроллеры общие для ВСЕЙ сцены (см. Task #58) — экраны одной сцены
        // должны видеть все добавленные в сцену контроллеры, а не только те,
        // что физически хранятся под текущим экраном.
        List<ControllerInstance> sceneControllers = has
                ? model.controllersInScene(model.getCurrentScene()) : List.of();
        if (has) {
            if (sceneControllers.isEmpty()) {
                controllerListPanel.add(UiKit.muted("Контроллеры не назначены"));
            }
            for (ControllerInstance ci : sceneControllers) {
                ControllerType t = model.getWorkspace().controllerTypeById(ci.getControllerTypeId());
                String label;
                if (t != null) {
                    int px = com.vjstb.ledscheme.model.ControllerType.maxPixelsFor(
                            t.getPortBandwidthMbps(), scr.getRefreshRateHz(), scr.getColorBitDepth());
                    label = ci.getLabel() + " — " + t.getName() + " (" + t.effectivePortCount() + " п. · до " + px
                            + " px/порт @" + scr.getRefreshRateHz() + "Гц/" + scr.getColorBitDepth() + "бит)";
                } else {
                    label = ci.getLabel() + " — ?";
                }
                controllerListPanel.add(controllerRow(scr, ci, label));
            }
        }
        UiKit.recapHeight(controllersSection);

        ControllerInstance selected = has ? selectedController(scr) : null;
        if (has && selected != null) {
            // Порты показанной сетки — ТОЛЬКО выбранного контроллера, локальными
            // номерами, а не сквозная сумма по всем контроллерам сцены (Task #73).
            ControllerType t = model.getWorkspace().controllerTypeById(selected.getControllerTypeId());
            int total = t != null ? t.effectivePortCount() : 0;
            int usable = t != null ? t.effectiveEthernetPortCount() : 0;
            // Если на картах есть fiber-группы — они физически существуют (считаются
            // в total, влияют на смещение портов следующего контроллера сцены), но не
            // предлагаются для расключения экрана (см. PortPickerPanel, Task #17):
            // явно показываем оба числа, иначе "Портов: 18" выглядит как расхождение
            // с сеткой, где реально доступно меньше (баг-репорт: "всё ещё 18 портов").
            String countText = usable == total ? String.valueOf(total)
                    : usable + " Ethernet из " + total + " (" + (total - usable) + " fiber/оптика — не для расключения экрана)";
            portCountLabel.setText("Портов: " + countText + " (контроллер «" + selected.getLabel() + "»)");
        } else if (has) {
            portCountLabel.setText("Контроллеры не назначены — добавьте хотя бы один, чтобы получить порты сигнала");
        } else {
            portCountLabel.setText(" ");
        }
        portPicker.rebuild(activePort, selected);
        UiKit.recapHeight(portsSection);

        // Выбор "текущей карты" для хоткеев — показываем переключатель, только если
        // у контроллера реально ЕСТЬ выбор (2+ карты с выходными Ethernet-портами);
        // сама сетка портов выше рисует ВСЕ карты одновременно независимо от этого.
        ControllerType selectedType = has && selected != null
                ? model.getWorkspace().controllerTypeById(selected.getControllerTypeId()) : null;
        List<Integer> nonEmptyPools = nonEmptyEthernetPools(selectedType);
        if (selectedCardPool == null || !nonEmptyPools.contains(selectedCardPool)) {
            selectedCardPool = nonEmptyPools.isEmpty() ? null : nonEmptyPools.get(0);
        }
        cardPoolRow.setVisible(nonEmptyPools.size() > 1);
        if (nonEmptyPools.size() > 1) {
            DefaultComboBoxModel<Integer> poolModel = new DefaultComboBoxModel<>();
            for (int p : nonEmptyPools) {
                poolModel.addElement(p);
            }
            cardPoolCombo.setModel(poolModel);
            cardPoolCombo.setSelectedItem(selectedCardPool);
        }

        chainListPanel.removeAll();
        if (has) {
            // По всей сцене (не только scr.getSignalChains()) — та же причина, что
            // и для питания (Task #64/#68): цепочка хранится на ОДНОМ экране, а
            // начаться могла на этом.
            var chains = model.signalChainsTouchingScreen(scr);
            if (chains.isEmpty()) {
                chainListPanel.add(UiKit.muted("Цепочек ещё нет"));
            }
            boolean trackLoad = settings.activeProfile().isLoadTrackingEnabled();
            Scene scene = model.getCurrentScene();
            for (int i = 0; i < chains.size(); i++) {
                SignalChain c = chains.get(i);
                boolean crossScreen = c.getCabinetInstanceIds().stream().anyMatch(id -> scr.cabinetById(id) == null);
                // Как и в Питании (Task #80) — текущая нагрузка (в пикселях) показывается
                // ВСЕГДА, предупреждение/кнопка «Я знаю» — только если включён контроль
                // нагрузки в Предпочтениях (см. AppModel.signalChainLoadStatus, дублирует
                // уже готовый паттерн PowerStagePanel.refresh()).
                AppModel.SignalChainLoadStatus status = model.signalChainLoadStatus(scene, c);
                String label = (c.getPortNumber() != null ? "Порт " + portDisplayLabel(scr, c.getPortNumber()) : "Без порта")
                        + (c.isBackup() ? " (бэкап)" : "") + " · " + c.getCabinetInstanceIds().size() + " каб."
                        + (c.getBackupPortNumber() != null
                                ? " · резерв: порт " + portDisplayLabel(scr, c.getBackupPortNumber()) : "")
                        + (crossScreen ? " (продолжается на другом экране)" : "")
                        + " · " + UiKit.fmt(status.loadPixels()) + " px";
                AppModel.SignalChainLoadStatus warnStatus = trackLoad ? status : null;
                chainListPanel.add(chainRow(label, com.vjstb.ledscheme.ui.SchemeRenderer.chainColor(c, i), warnStatus,
                        () -> model.acknowledgeSignalChainOverload(scene, c), () -> model.deleteSignalChain(c.getId()),
                        c));
            }
        }
        UiKit.recapHeight(chainsSection);
        chainListPanel.revalidate();
        chainListPanel.repaint();

        if (has && showAllScreens.isSelected() && model.getCurrentScene() != null) {
            // Показана вся сцена — статистика одного активного экрана не даёт полной
            // картины, показываем сумму по всем экранам сцены (Task #71).
            java.util.List<Screen> screens = model.getCurrentScene().getScreens();
            ScreenStats s = ScreenLogic.aggregateStats(screens, model::typeOf, model.getWorkspace());
            statCabinetType.setText(cabinetTypeSummary(screens));
            statRes.setText(screens.size() + " экран(ов)");
            statSize.setText("—");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
        } else if (has) {
            ScreenStats s = ScreenLogic.stats(scr, model.typeOf(scr), model.getWorkspace());
            com.vjstb.ledscheme.model.CabinetType ct = model.typeOf(scr);
            statCabinetType.setText(ct != null ? ct.getName() : "—");
            statRes.setText(s.resolutionWidthPx() + " × " + s.resolutionHeightPx() + " px");
            statSize.setText(UiKit.fmt(s.physicalWidthMm()) + " × " + UiKit.fmt(s.physicalHeightMm()) + " мм");
            statCount.setText(String.valueOf(s.activeCabinetCount()));
            statPower.setText(UiKit.fmt(s.totalPowerW()) + " Вт");
            statWeight.setText(UiKit.fmt(s.totalWeightKg()) + " кг");
        } else {
            statCabinetType.setText("—");
            statRes.setText("—");
            statSize.setText("—");
            statCount.setText("—");
            statPower.setText("—");
            statWeight.setText("—");
        }

        canvas.revalidate();
        canvas.repaint();
        sceneOverview.revalidate();
        sceneOverview.repaint();
        cornerPreview.repaint();
    }

    private void updateCornerPreviewVisibility() {
        cornerPreviewHost.setVisible(settings.activeProfile().isPreviewWidgetEnabled() && !showAllScreens.isSelected());
    }

    /** Строка контроллера: ПКМ помечает его «в резерв» (ждём цель), ЛКМ по ДРУГОМУ
     *  контроллеру, пока метка ждёт, создаёт связку основной→резерв между ними —
     *  весь контроллер целиком дублирует порты другого (см. AppModel.setControllerBackupLink),
     *  в отличие от резерва отдельного порта (2×клик по кнопке порта). */
    private javax.swing.JComponent controllerRow(Screen scr, ControllerInstance ci, String baseLabel) {
        List<ControllerInstance> sceneControllers = model.controllersInScene(model.getCurrentScene());
        String suffix = "";
        if (ci.getBackupControllerId() != null) {
            ControllerInstance backup = findController(sceneControllers, ci.getBackupControllerId());
            suffix = " → резерв: " + (backup != null ? backup.getLabel() : "?");
        } else {
            for (ControllerInstance other : sceneControllers) {
                if (ci.getId().equals(other.getBackupControllerId())) {
                    suffix = " (резерв для " + other.getLabel() + ")";
                    break;
                }
            }
        }
        boolean pending = ci.getId().equals(pendingBackupControllerId);
        ControllerInstance currentSelection = selectedController(scr);
        boolean selected = currentSelection != null && ci.getId().equals(currentSelection.getId());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        // Рамка — резерв (ПКМ, ждём цель); заливка — выбран для показа/расключения
        // портов в сетке справа (ЛКМ, см. Task #73). Оба состояния различимы одновременно.
        row.setBorder(pending ? BorderFactory.createLineBorder(Color.ORANGE, 2) : null);
        row.setBackground(selected ? Palette.ACCENT.darker() : getBackground());
        row.setOpaque(selected);
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(suffix.isEmpty() ? Palette.ACCENT : Color.ORANGE);
        JLabel text = new JLabel(baseLabel + suffix);
        if (selected) {
            text.setForeground(Palette.TEXT);
        }
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> model.removeControllerFromScene(model.getCurrentScene(), ci.getId()));
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(del, BorderLayout.EAST);
        row.setToolTipText("ЛКМ — показать/расключать порты этого контроллера в сетке справа"
                + " · ПКМ — пометить как резервный для другого контроллера");

        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    pendingBackupControllerId = pending ? null : ci.getId();
                    refresh();
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && pendingBackupControllerId != null) {
                    String backupId = pendingBackupControllerId;
                    pendingBackupControllerId = null;
                    if (!backupId.equals(ci.getId())) {
                        try {
                            model.setControllerBackupLink(scr, ci.getId(), backupId);
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(SignalStagePanel.this, ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                    refresh();
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    // Обычный ЛКМ (не в режиме назначения резерва) — выбрать этот
                    // контроллер для сетки портов (Task #73).
                    selectedControllerId = ci.getId();
                    selectedCardPool = null; // сброс -- при новом контроллере снова первая непустая карта
                    refresh();
                }
            }
        });
        return row;
    }

    private static ControllerInstance findController(List<ControllerInstance> controllers, String id) {
        for (ControllerInstance ci : controllers) {
            if (ci.getId().equals(id)) {
                return ci;
            }
        }
        return null;
    }

    private javax.swing.JComponent chainRow(String label, java.awt.Color dot, AppModel.SignalChainLoadStatus status,
                                             Runnable onAcknowledge, Runnable onDelete, SignalChain chain) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(dot);
        boolean overloaded = status != null && status.overloaded();
        JLabel text = new JLabel((overloaded ? "⚠ " : "") + label);
        if (overloaded) {
            text.setForeground(Palette.WARN);
            text.setToolTipText("Превышена ёмкость порта: " + UiKit.fmt(status.loadPixels()) + " px при допустимых "
                    + UiKit.fmt(status.capacityPixels()) + " px");
        }
        // ПКМ по строке — свой цвет цепочки вместо цвета по индексу (Task #4), тот же
        // приём, что и в PowerStagePanel.chainRow/SchemaCanvasPanel.showEdgeMenu.
        // ЛКМ по строке (не по кнопкам справа) — возобновить построение ЭТОЙ цепочки
        // (Task #11/v1.6). Слушатель регистрируется на row, dotLabel И text — см.
        // комментарий в PowerStagePanel.chainRow про то, почему одного row мало.
        java.awt.event.MouseAdapter rowMouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (maybeShowMenu(e)) {
                    return;
                }
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    chainCtrl.resumeEditing(new java.util.ArrayList<>(chain.getCabinetInstanceIds()),
                            ids -> model.updateSignalChain(chain.getId(), ids));
                }
            }

            private boolean maybeShowMenu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return false;
                }
                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                javax.swing.JMenuItem colorItem = new javax.swing.JMenuItem("Цвет цепочки…");
                colorItem.addActionListener(ev -> {
                    java.awt.Color initial = chain.getColor() != null ? new java.awt.Color(chain.getColor()) : dot;
                    java.awt.Color chosen = javax.swing.JColorChooser.showDialog(SignalStagePanel.this,
                            "Цвет цепочки сигнала", initial);
                    if (chosen != null) {
                        model.setSignalChainColor(chain, chosen.getRGB());
                    }
                });
                menu.add(colorItem);
                if (chain.getColor() != null) {
                    javax.swing.JMenuItem reset = new javax.swing.JMenuItem("Сбросить цвет");
                    reset.addActionListener(ev -> model.setSignalChainColor(chain, null));
                    menu.add(reset);
                }
                menu.show(e.getComponent(), e.getX(), e.getY());
                return true;
            }
        };
        row.addMouseListener(rowMouse);
        dotLabel.addMouseListener(rowMouse);
        text.addMouseListener(rowMouse);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (overloaded && !status.acknowledged()) {
            JButton ack = new JButton("Я знаю");
            ack.setToolTipText("Подтвердить превышение — предупреждение и блокировка экспорта снимутся,"
                    + " пока нагрузка не вырастет ещё больше");
            ack.addActionListener(e -> onAcknowledge.run());
            east.add(ack);
        }
        JButton del = new JButton("✕");
        del.setMargin(new java.awt.Insets(0, 4, 0, 4));
        del.addActionListener(e -> onDelete.run());
        east.add(del);
        row.add(dotLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private JPanel statRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title + ": ");
        t.setForeground(Palette.MUTED);
        row.add(t, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    /** Тип кабинета для показанного набора экранов — единое название, если у всех
     *  один и тот же тип, иначе краткая сводка «разные (N)» (см. Task #101 —
     *  показывать тип кабинета экрана прямо в статистике для удобства). */
    private String cabinetTypeSummary(java.util.List<Screen> screens) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Screen s : screens) {
            com.vjstb.ledscheme.model.CabinetType t = model.typeOf(s);
            names.add(t != null ? t.getName() : "?");
        }
        if (names.isEmpty()) {
            return "—";
        }
        return names.size() == 1 ? names.iterator().next() : "разные (" + names.size() + ")";
    }
}
