package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.Palette;
import com.vjstb.ledscheme.ui.UiKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * Общий каркас видов «Приёмные карты» и «Блоки питания»: тулбар над холстом, слева холст со строкой замен, подсказкой
 * и (у карт) палитрой нумерации под ним, справа — панель назначения, снизу — полоса замечаний. Вид сам определяет
 * текущий экран через {@link AppModel} и перерисовывается на каждое изменение модели и настроек — этапу (трек D)
 * остаётся создать вид конструктором и вызывать {@code refresh()} при показе.
 *
 * <p>Все правки идут через методы {@code AppModel} (одна запись отмены на операцию), вид не хранит копий карт и
 * блоков: после любой правки — в том числе «Отменить» из главного меню — он целиком перечитывает экран. Своё
 * состояние вида (выбранный тип, режим, активная карта, инструмент, строка замен) живёт в подклассах и сбрасывается
 * при смене экрана.
 */
abstract class ModularEditorView extends JPanel implements AppModel.Listener {

    protected final AppModel model;
    protected final SettingsManager settings;
    protected final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JLabel note = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JPanel left = new JPanel(new BorderLayout());
    private final JPanel south = new JPanel();
    private final JPanel belowHost = new JPanel(new BorderLayout());
    private final JScrollPane canvasScroll = new JScrollPane();
    private final JScrollPane sideScroll = new JScrollPane();
    protected final ReceiverWarningsBar warningsBar;
    private final JSplitPane split;
    private Runnable rewrap;
    private String hintText = "";
    private String noteText = "";
    private String lastScreenId;

    ModularEditorView(AppModel model, SettingsManager settings, String splitKey, double splitProportion) {
        super(new BorderLayout());
        this.model = model;
        this.settings = settings;
        this.warningsBar = new ReceiverWarningsBar(this::warningTargetClicked);

        canvasScroll.setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        canvasScroll.getViewport().setBackground(Palette.BG);
        note.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        hint.setForeground(Palette.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        note.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        belowHost.setAlignmentX(LEFT_ALIGNMENT);
        south.add(note);
        south.add(hint);
        south.add(belowHost);
        left.add(canvasScroll, BorderLayout.CENTER);
        left.add(south, BorderLayout.SOUTH);
        left.setMinimumSize(new Dimension(320, 200));

        sideScroll.setBorder(null);
        sideScroll.setMinimumSize(new Dimension(240, 100));
        sideScroll.getVerticalScrollBar().setUnitIncrement(16);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, sideScroll);
        split.setContinuousLayout(true);
        split.setResizeWeight(1.0);
        UiKit.persistentDivider(settings, splitKey, split, splitProportion);

        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(warningsBar, BorderLayout.SOUTH);

        rewrap = UiKit.bindHtmlWrapWidth(hint, left, () -> hintText);
    }

    /** Подклассы вызывают в конце своего конструктора: подписка на модель и настройки и первое заполнение. */
    protected final void start() {
        model.addListener(this);
        settings.addListener(this::refresh);
        refresh();
    }

    protected final void installCanvas(JComponent canvas) {
        canvasScroll.setViewportView(canvas);
    }

    protected final void installSide(JComponent side) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        wrap.add(side, BorderLayout.NORTH);
        sideScroll.setViewportView(wrap);
        sideScroll.setPreferredSize(new Dimension(330, 200));
    }

    protected final void installBelow(JComponent below) {
        belowHost.add(below, BorderLayout.CENTER);
    }

    @Override
    public void onModelChanged() {
        refresh();
    }

    /** Перечитать модель (вид показан, сменились сцена/экран, правка пришла извне). */
    public void refresh() {
        Screen s = model.getCurrentScreen();
        String id = s != null ? s.getId() : null;
        if (!Objects.equals(id, lastScreenId)) {
            lastScreenId = id;
            onScreenChanged();
        }
        refreshContent(s);
        revalidate();
        repaint();
    }

    /** Экран сменился: сбросить выбор, инструмент, строку замен. */
    protected abstract void onScreenChanged();

    /** Перечитать данные экрана {@code s} (может быть {@code null}) во все части вида. */
    protected abstract void refreshContent(Screen s);

    /** Клик по замечанию в полосе — выбрать карту/блок, к которой оно относится. */
    protected abstract void warningTargetClicked(String targetId);

    // ------------------------------------------------------------------ строка замен и подсказка

    /** Строка под холстом: итог последней правки («Заменено карт: 2, модулей без карты: 5»); {@code warn} — оранжевая. */
    protected final void setNote(String text, boolean warn) {
        noteText = text == null ? "" : text;
        note.setText(noteText.isEmpty() ? " " : noteText);
        note.setForeground(warn ? Palette.WARN : Palette.MUTED);
    }

    protected final String noteText() {
        return noteText;
    }

    protected final void setHint(String text) {
        hintText = text == null ? "" : text;
        if (left.getWidth() > 0) {
            rewrap.run();
        } else {
            hint.setText(hintText.isEmpty() ? " " : hintText);
        }
    }

    String hintText() {
        return hintText;
    }

    // ------------------------------------------------------------------ настройки профиля

    protected final ReceiverCardNumbering cardNumbering() {
        return settings.activeProfile().getReceiverCardNumbering();
    }

    protected final String numberFormat() {
        return settings.activeProfile().getModuleNumberFormat();
    }

    ReceiverWarningsBar warningsBar() {
        return warningsBar;
    }
}
