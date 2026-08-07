package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/** Мелкие переиспользуемые построители панелей для страниц этапов. */
public final class UiKit {

    private UiKit() {
    }

    /** Привязывает Delete/Backspace на компоненте к действию — списки/панели с
     *  кнопкой «✕»/«Удалить» получают тот же эффект по горячей клавише, пока сам
     *  компонент в фокусе (WHEN_FOCUSED — не перехватывает клавишу нигде за
     *  пределами этого конкретного списка/панели, поэтому не конфликтует с другими
     *  хоткеями на Delete, например TOGGLE_HIDDEN в редакторе кабинетов). action
     *  сам решает, есть ли сейчас что удалять (например, ничего не делает, если
     *  в списке нет выделения) — вызывается безусловно по нажатию клавиши. */
    public static void bindDeleteKey(JComponent comp, Runnable action) {
        javax.swing.InputMap im = comp.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap am = comp.getActionMap();
        Object key = "ledScheme.delete";
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), key);
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), key);
        am.put(key, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    public static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /**
     * Вертикальный бокс с ПРИНУДИТЕЛЬНО фиксированной шириной (высота остаётся
     * динамической — считается живьём через super.getPreferredSize()). Нужен для
     * боковых панелей: без этого ширина «плывёт» от самого широкого потомка
     * (например, сетки кнопок), раздувая всю колонку за пределы окна.
     */
    public static JPanel vboxFixedWidth(int width) {
        JPanel p = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(width, d.height);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    public static Component vgap() {
        return Box.createVerticalStrut(6);
    }

    public static Component vgap(int px) {
        return Box.createVerticalStrut(px);
    }

    /**
     * Секция с заголовком для СТАТИЧНОГО содержимого (формы, кнопки, текст),
     * которое больше не меняется после вызова. Максимальная высота фиксируется
     * сразу: JViewport растягивает непрокручиваемый контент, который меньше
     * видимой области, поэтому без явного предела BoxLayout отдаёт лишнее место
     * первой попавшейся секции (GridLayout/BorderLayout по умолчанию не ограничены
     * по высоте) вместо завершающего glue.
     *
     * Для содержимого, наполняемого ПОСЛЕ первой сборки (списки, зависящие от
     * данных) используйте {@link #dynamicSection} и вызывайте {@link #recapHeight}
     * после каждого обновления.
     */
    public static JComponent section(String title, JComponent body) {
        JPanel p = sectionPanel(title, body);
        recapHeight(p);
        return p;
    }

    /** Секция для содержимого, которое наполняется позже — высоту нужно пересчитывать явно. */
    public static JComponent dynamicSection(String title, JComponent body) {
        return sectionPanel(title, body);
    }

    /** Заново фиксирует maximumSize секции по текущему preferredSize — вызывать после смены содержимого. */
    public static void recapHeight(JComponent section) {
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
    }

    private static JPanel sectionPanel(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)));
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    /**
     * Строка формы «подпись сверху, поле снизу» вместо GridLayout(0,2) «подпись
     * слева, поле справа». GridLayout заставляет ОБЕ колонки иметь одинаковую
     * ширину — равную самому широкому потомку по ВСЕЙ сетке (включая длинные
     * подписи вроде «Заметки по подвесу» или «Способ монтажа»), и не умеет её
     * уменьшать при сужении окна — превышающая ширина просто обрезается. Подпись
     * сверху занимает всего одну колонку, поле — растягивается на всю доступную
     * ширину узкой боковой панели без переполнения.
     */
    public static JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = muted(label);
        row.add(l);
        row.add(Box.createVerticalStrut(2));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        row.add(field);
        // Высота фиксируется ПОСЛЕ добавления обоих потомков — иначе preferredSize
        // считался бы по ещё пустой панели (нулевая высота), пряча содержимое.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** Ширина (px), на которую переносятся длинные HTML-подсказки в muted(...).
     *  Отслеживание ФАКТИЧЕСКОЙ ширины колонки живьём через getParent().getWidth()
     *  не работает надёжно: на первом проходе раскладки родитель ещё не знает свою
     *  ширину (или знает устаревшую с прошлого прохода), а другого пересчёта без
     *  явного ресайза не происходит — подсказка остаётся неперенесённой. Фиксированная
     *  ширина в стиле HTML не зависит от этой гонки: рендерер переносит текст по
     *  словам сразу, детерминированно, и с запасом умещается в любую разумно узкую
     *  боковую колонку (у самой узкой — «Библиотеки»/«Питание» — не меньше ~260px). */
    private static final int HINT_WRAP_PX = 220;

    /** Оборачивает HTML-текст (вида "&lt;html&gt;...&lt;/html&gt;") в div с
     *  фиксированной шириной переноса — см. {@link #HINT_WRAP_PX}. Не-HTML текст
     *  возвращается как есть. Используется и в {@link #muted}, и напрямую там, где
     *  текст подсказки задаётся через setText(...) на уже созданной JLabel. */
    public static String wrapHtml(String text) {
        if (text != null && text.regionMatches(true, 0, "<html>", 0, 6) && text.length() >= 6) {
            return "<html><div style='width:" + HINT_WRAP_PX + "px'>" + text.substring(6);
        }
        return text;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(wrapHtml(text));
        l.setForeground(Palette.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        // JLabel с HTML-содержимым отдаёт getMaximumSize().width == Integer.MAX_VALUE
        // (блочный HTML-контент теоретически может занять любую ширину) — в
        // BoxLayout.Y_AXIS (см. LibrariesStagePanel) это ломает расчёт горизонтальной
        // раскладки СОСЕДНИХ детей контейнера: список (JScrollPane) над этой подсказкой
        // получает заметно МЕНЬШЕ положенной ширины вместо всей доступной — отсюда
        // разный видимый отступ у секций библиотеки в зависимости от того, есть ли у
        // них такая подсказка снизу (баг-репорт "оффсет окошка библиотеки кабинетов",
        // подтверждено диагностикой: без этой подсказки список ложится ровно, с ней —
        // нет). Ограничиваем максимум конечным (но заведомо большим) значением, как у
        // остальных "растягивающихся" компонентов этих панелей (JPanel по умолчанию
        // отдаёт Short.MAX_VALUE, а не Integer.MAX_VALUE, и с ними бага нет).
        Dimension max = l.getMaximumSize();
        l.setMaximumSize(new Dimension(Short.MAX_VALUE, max.height));
        return l;
    }

    /** Перевязывает HTML-содержимое {@code label} к ТЕКУЩЕЙ ширине {@code widthSource}
     *  при каждом изменении размера — в отличие от {@link #wrapHtml} (годится только
     *  для узких боковых колонок фиксированной ширины), это для body-текста в
     *  РЕСАЙЗИМЫХ диалогах: захардкоженная «width: Npx» подгонка под ширину только НА
     *  МОМЕНТ создания диалога — при ресайзе пользователем текст либо обрезается/
     *  вылезает за границу (диалог уже, чем Npx), либо остаётся немотивированно узким
     *  посреди широкого диалога (баг-репорт: текст шага сценария вылезал за край окна
     *  после ресайза). {@code rawHtmlSupplier} отдаёт HTML БЕЗ внешней обёртки
     *  <html><body> — оборачивание берёт на себя этот метод. {@code widthSource} —
     *  компонент, чья РЕАЛЬНО доступная ширина важна (например, JScrollPane/вьюпорт,
     *  в котором лежит label), а не сам label — его собственная ширина следует за
     *  (растущим) preferred size, а не за фактически выделенным местом.
     *  <p>Возвращает сам rewrap-{@code Runnable} — вызывающий код дёргает его вручную
     *  при смене содержимого (например, переход на другой шаг сценария/другую секцию
     *  руководства), а не только при ресайзе. */
    public static Runnable bindHtmlWrapWidth(JLabel label, JComponent widthSource,
            java.util.function.Supplier<String> rawHtmlSupplier) {
        Runnable rewrap = () -> {
            int w = widthSource.getWidth();
            if (w <= 0) {
                return;
            }
            int wrapPx = Math.max(100, w - 24);
            label.setText("<html><body style='width:" + wrapPx + "px'>" + rawHtmlSupplier.get() + "</body></html>");
        };
        widthSource.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                rewrap.run();
            }
        });
        SwingUtilities.invokeLater(rewrap);
        return rewrap;
    }

    public static String fmt(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }

    /**
     * Делает позицию разделителя JSplitPane персонализируемой: восстанавливает долю,
     * сохранённую в активном профиле пользователя (или defaultProportion, если ещё
     * не сохранялась), и запоминает новую долю при перетаскивании — так раскладка
     * окон этапа переживает перезапуск приложения (см. меню «Персонализация»).
     * key должен быть уникален в пределах приложения (например, "setup.library").
     *
     * Сохранение подписано на mouseReleased самого «рычажка» разделителя, а НЕ на
     * DIVIDER_LOCATION_PROPERTY вообще: этот property меняется и от программных
     * setDividerLocation(...) — в частности, от setSectionVisible при скрытии/показе
     * секций (JSplitPane сам схлопывает скрытого потомка в 0 и раздувает видимого
     * почти на всю высоту). Если сохранять КАЖДОЕ такое изменение, испорченная
     * пропорция (например, 0.99 от автоматического раздутия) записывается в профиль
     * и потом воспроизводится setSectionVisible на следующий показ секции — раздел
     * так и остаётся видимым, но нулевой высоты. Реальное намерение пользователя
     * фиксируем только по факту отпускания мыши после руч ного перетаскивания.
     */
    public static void persistentDivider(SettingsManager settings, String key, JSplitPane sp,
                                          double defaultProportion) {
        double initial = settings.getLayoutProportion(key, defaultProportion);
        setInitialDividerOnShow(sp, initial);
        Runnable save = () -> {
            int total = sp.getOrientation() == JSplitPane.VERTICAL_SPLIT ? sp.getHeight() : sp.getWidth();
            if (total <= 0) {
                return;
            }
            double proportion = sp.getDividerLocation() / (double) total;
            if (proportion > 0 && proportion < 1) {
                settings.setLayoutProportion(key, proportion);
            }
        };
        if (sp.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui && ui.getDivider() != null) {
            ui.getDivider().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    save.run();
                }
            });
        }
    }

    /**
     * Показывает/скрывает секцию — потомка {@link JSplitPane} (например, «Сцены»,
     * видимую только при выбранном проекте). JSplitPane, когда один из потомков
     * скрывается через setVisible(false), схлопывает его долю разделителя в 0 —
     * а при последующем повторном показе САМ эту долю не восстанавливает (в отличие
     * от resizeWeight-логики при обычном ресайзе окна). Из-за этого ранее скрытая
     * секция технически становится «видимой», но нулевой высоты — то есть просто не
     * появляется на экране, пока пользователь не потянет разделитель руками. Явно
     * переустанавливаем долю (из профиля или дефолтную) при каждом переходе
     * скрыта→видима.
     */
    public static void setSectionVisible(JComponent section, boolean visible, JSplitPane sp,
                                          SettingsManager settings, String key, double defaultProportion) {
        boolean wasVisible = section.isVisible();
        section.setVisible(visible);
        if (visible && !wasVisible) {
            restoreDividerProportion(sp, settings, key, defaultProportion);
        }
    }

    /**
     * Восстанавливает долю разделителя (из профиля или дефолтную) на СЛЕДУЮЩЕМ
     * цикле EDT, а не сразу: другие секции того же вызова doRebuild() могли
     * только что поменять видимость выше по дереву, и sp.getHeight() здесь ещё
     * вернёт устаревший размер до завершения layout-прохода.
     *
     * ВАЖНО: если ОБА потомка одного и того же JSplitPane переключают видимость
     * в одном doRebuild() (например, «Экраны» и «Параметры экрана» оба становятся
     * видимы в один и тот же проход — leftSplit3), нужен ОДИН вызов на общий
     * разделитель, а не по одному на каждый потомок: два независимых invokeLater,
     * каждый со своим setDividerLocation, гонятся с внутренним resize-weight-
     * пересчётом JSplitPane при промежуточных изменениях размера и в сумме дают
     * абсурдный результат (например, «Экраны» разрастаются на 99% высоты, а
     * «Параметры» схлопываются почти в 0) — вызывающий код должен сам определить,
     * произошёл ли ХОТЯ БЫ ОДИН переход скрыта→видима, и вызвать это ОДИН раз.
     */
    public static void restoreDividerProportion(JSplitPane sp, SettingsManager settings, String key,
                                                 double defaultProportion) {
        SwingUtilities.invokeLater(() -> {
            boolean vertical = sp.getOrientation() == JSplitPane.VERTICAL_SPLIT;
            int total = vertical ? sp.getHeight() : sp.getWidth();
            if (total > 0) {
                double proportion = settings.getLayoutProportion(key, defaultProportion);
                sp.setDividerLocation((int) Math.round(proportion * total));
            }
        });
    }

    /** Ставит начальное положение разделителя один раз, когда компонент реально
     *  впервые отображается (до этого момента у него ещё нет корректного размера).
     *  Позиция зажимается между минимальными размерами обеих сторон — так секция
     *  с сохранённой (например, из предыдущей узкой раскладки) слишком маленькой
     *  долей всё равно показывает хотя бы одну позицию списка при первом открытии,
     *  а не требует ручной растяжки; дальнейший рост — уже перетаскиванием или
     *  когда содержимого в принципе больше, чем помещается.
     *
     * Само измерение (sp.getHeight()/getWidth()) откладывается на СЛЕДУЮЩИЙ цикл
     * EDT после события SHOWING_CHANGED, а не читается синхронно внутри колбэка:
     * при вложенных JSplitPane (Проекты > Сцены > Экраны > Параметры) все уровни
     * становятся «showing» практически одновременно в рамках одного и того же
     * прохода, и внутренние сплиты в этот самый момент ещё видят РАЗМЕР РОДИТЕЛЯ
     * ДО того, как тот сам получит свою финальную высоту от вышестоящих уровней —
     * пропорция фиксируется по переходному, а не окончательному размеру (тот же
     * класс бага, что и в setSectionVisible). Один лишний invokeLater даёт всей
     * цепочке родителей дозавершить раскладку. */
    public static void setInitialDividerOnShow(JSplitPane sp, double proportion) {
        sp.addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && sp.isShowing()) {
                    sp.removeHierarchyListener(this);
                    javax.swing.SwingUtilities.invokeLater(() -> applyInitialDivider(sp, proportion));
                }
            }
        });
    }

    /** Оборачивает {@code view} так, чтобы внутри {@link javax.swing.JScrollPane} он
     *  растягивался на всю высоту/ширину видимой области, а не садился на свой
     *  preferred size, как обычный компонент — {@link JSplitPane} сам НЕ реализует
     *  {@link javax.swing.Scrollable}, поэтому без этой обёртки JScrollPane даёт ему
     *  только preferred-размер (сумму preferred-высот его потомков), а не реальную
     *  высоту окна приложения. Из-за этого {@link #setInitialDividerOnShow} читал
     *  заниженный {@code sp.getHeight()} при первом показе и «схлопывал» один из
     *  потомков почти в 0 — баг-репорт: секция «Форма экрана» пропадала целиком и
     *  появлялась крошечной только после того, как пользователь вручную тянул
     *  соседний разделитель (это пересчитывало layout и попутно фиксировало
     *  испорченную, близкую к 0, пропорцию). */
    public static JComponent stretchToViewport(JComponent view) {
        return new ViewportStretchPanel(view);
    }

    private static final class ViewportStretchPanel extends JPanel implements javax.swing.Scrollable {
        ViewportStretchPanel(JComponent view) {
            super(new BorderLayout());
            setOpaque(false);
            add(view, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    private static void applyInitialDivider(JSplitPane sp, double proportion) {
        boolean vertical = sp.getOrientation() == JSplitPane.VERTICAL_SPLIT;
        int total = vertical ? sp.getHeight() : sp.getWidth();
        int loc = (int) Math.round(proportion * total);
        Component first = vertical ? sp.getTopComponent() : sp.getLeftComponent();
        Component second = vertical ? sp.getBottomComponent() : sp.getRightComponent();
        int minFirst = first != null ? (vertical ? first.getMinimumSize().height : first.getMinimumSize().width) : 0;
        int minSecond = second != null ? (vertical ? second.getMinimumSize().height : second.getMinimumSize().width) : 0;
        loc = Math.max(loc, minFirst);
        if (total > 0) {
            loc = Math.min(loc, Math.max(minFirst, total - minSecond - sp.getDividerSize()));
        }
        sp.setDividerLocation(loc);
        sp.revalidate();
        sp.repaint();
    }
}
