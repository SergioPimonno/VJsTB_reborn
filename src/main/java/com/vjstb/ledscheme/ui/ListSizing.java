package com.vjstb.ledscheme.ui;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;

/**
 * Подгоняет высоту списка (в скролл-панели) под количество элементов, в отличие
 * от фиксированного числа видимых строк — короткие списки не занимают лишнее
 * место, длинные ограничиваются максимумом строк и дальше скроллятся.
 */
public final class ListSizing {

    private ListSizing() {
    }

    /** Ширина остаётся безграничной (см. capWidth=false ниже) — прежнее поведение,
     *  для списков, вложенных в колонку ФИКСИРОВАННОЙ ширины (см.
     *  UiKit.vboxFixedWidth в SetupStagePanel): там список и должен заполнять всю
     *  ширину колонки целиком, ограничивает её именно колонка-обёртка снаружи. */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows) {
        fit(list, scroll, minRows, maxRows, false);
    }

    /** capWidth=true — вдобавок к высоте ограничивает и МАКСИМАЛЬНУЮ ШИРИНУ по факту
     *  содержимого, а не оставляет её безграничной — для списков в секциях НА ВСЮ
     *  ширину этапа без своей колонки-обёртки фиксированной ширины (см.
     *  LibrariesStagePanel): без этого BoxLayout/JSplitPane растягивал список на всё
     *  окно, оставляя пустой серый фон справа от реального содержимого (баг-репорт,
     *  Task #95/v1.5: "масштабирование интерфейса растягивается в пустоту"). */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows, boolean capWidth) {
        int count = Math.max(list.getModel().getSize(), minRows);
        int rows = Math.min(count, maxRows);
        int h = rowsHeight(list, rows);
        int w = Math.max(scroll.getPreferredSize().width, 100);
        scroll.setPreferredSize(new Dimension(w, h));
        scroll.setMaximumSize(new Dimension(capWidth ? w : Integer.MAX_VALUE, h));
        // Как и в перегрузке с явной шириной ниже — одного scroll.revalidate()
        // недостаточно, когда список лежит в JSplitPane (см. LibrariesStagePanel,
        // дерево категорий): родитель может остаться "valid" со старой высотой
        // одной из панелей до тех пор, пока пользователь не потянет за границу
        // сплиттера руками (баг-репорт: "все 3 окна разной высоты и меняются
        // только после передергивания по ширине"). Инвалидируем/пересобираем
        // родителя, а не только сам scroll.
        java.awt.Container parent = scroll.getParent();
        if (parent != null) {
            parent.invalidate();
            parent.revalidate();
            parent.repaint();
        } else {
            scroll.revalidate();
        }
    }

    /** capWidth=true с ЗАДАННОЙ шириной вместо ширины по факту текущего содержимого —
     *  для панелей, которые сами отслеживают доступную ширину окна (за вычетом
     *  вертикального скроллбара) и пересчитывают её при ресайзе живьём, а не один
     *  раз при первой сборке (см. LibrariesStagePanel — баг-репорт: списки были
     *  зафиксированы на жёстких пиксельных значениях, из-за чего длинный текст
     *  элемента вылезал за ширину и получал СВОЙ горизонтальный скроллбар, а сама
     *  панель не подстраивалась под реальную ширину окна). */
    public static void fit(JList<?> list, JScrollPane scroll, int minRows, int maxRows, int width) {
        int count = Math.max(list.getModel().getSize(), minRows);
        int rows = Math.min(count, maxRows);
        int h = rowsHeight(list, rows);
        scroll.setPreferredSize(new Dimension(width, h));
        scroll.setMaximumSize(new Dimension(width, h));
        // scroll.revalidate() САМО ПО СЕБЕ не всегда заставляет BoxLayout
        // непосредственного родителя (vbox секции) реально пересчитать позицию/
        // ширину этого списка заново — на практике родитель иногда остаётся
        // "valid" со старой (более узкой, от предыдущего прохода ресайза) шириной
        // ребёнка, из-за чего список в одной секции визуально "уезжает"
        // относительно другой при абсолютно одинаковом коде (баг-репорт:
        // "оффсет окошка библиотеки кабинетов" — воспроизведено и подтверждено
        // диагностикой: preferredSize/maximumSize были верны, а РЕАЛЬНЫЕ bounds —
        // нет). Явно инвалидируем и пересобираем родителя, а не только сам scroll.
        java.awt.Container parent = scroll.getParent();
        if (parent != null) {
            parent.invalidate();
            parent.revalidate();
            parent.repaint();
        } else {
            scroll.revalidate();
        }
    }

    /** Высота под {@code rows} строк — считается по РЕАЛЬНОМУ preferredSize, который
     *  вернул рендерер списка для каждого элемента, а не по грубой формуле
     *  "2.3 строки шрифта" (см. estimateRowHeight ниже, оставлен как fallback для
     *  строк за пределами модели, когда minRows больше числа элементов). Раньше
     *  единая формула недооценивала высоту карточек с длинной мета-строкой (много
     *  разъёмов у одной карты — например, "Decklink 8K Pro") — их HTML-мета
     *  переносится на 2-3 строки, а не всегда на одну, и список либо обрезал
     *  последнюю видимую строку, либо визуально "ломался" (баг-репорт: "когда
     *  информации в карточке много, её отображение ломается"). */
    private static int rowsHeight(JList<?> list, int rows) {
        int fallbackRowH = estimateRowHeight(list);
        int total = 6;
        if (rows <= 0) {
            return total;
        }
        ListModel<?> model = list.getModel();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ListCellRenderer renderer = list.getCellRenderer();
        int measured = Math.min(rows, model.getSize());
        for (int i = 0; i < measured; i++) {
            Object value = model.getElementAt(i);
            int rowH = fallbackRowH;
            if (renderer != null) {
                @SuppressWarnings("unchecked")
                Component c = renderer.getListCellRendererComponent(list, value, i, false, false);
                rowH = Math.max(c.getPreferredSize().height, fallbackRowH);
            }
            total += rowH;
        }
        if (rows > measured) {
            total += (rows - measured) * fallbackRowH;
        }
        return total;
    }

    private static int estimateRowHeight(JList<?> list) {
        // рендерер списка рисует двухстрочный HTML (заголовок + мелкая мета-строка) —
        // используется как минимум/запасной вариант, см. rowsHeight выше.
        int base = list.getFontMetrics(list.getFont()).getHeight();
        return (int) Math.round(base * 2.3) + 8;
    }
}
