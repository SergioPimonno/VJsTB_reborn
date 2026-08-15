package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.StructureBaseFrameCell;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructurePeremychkaCell;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Операции над экраном: построение/перестроение сетки кабинетов, пересчёт
 * характеристик, снимок/восстановление состояния (для «отменить»).
 * Не зависит от UI.
 */
public final class ScreenLogic {

    private ScreenLogic() {
    }

    /** Переводит свободное мм-смещение ячейки (см. {@link CabinetInstance#getOffsetXMm()}/
     *  {@code getOffsetYMm}) в экранные пиксели ТЕКУЩЕГО контекста отрисовки — единая
     *  формула для всех мест, что рисуют сетку кабинетов (Task #7/v1.6): большинство из
     *  них не имеют единого «мм на пиксель» масштаба (размер ячейки на экране считается
     *  по произвольному пиксельному бюджету, лишь СОХРАНЯЯ пропорцию ширина/высота типа
     *  кабинета — см. SchemeRenderer.cellSize/CanvasPanel.cabW), поэтому локальный
     *  масштаб выводится из уже посчитанного размера самой ячейки (cellPx/typeMm), а не
     *  берётся откуда-то извне. typeMm<=0 — защита от деления на 0 (некорректный тип). */
    public static double offsetPx(double offsetMm, double cellPx, double typeMm) {
        return typeMm > 0 ? offsetMm * (cellPx / typeMm) : 0;
    }

    /** Границы экрана В ЕГО ЛОКАЛЬНЫХ координатах (0,0 = левый верхний угол сетки
     *  без смещений) — [minX, minY, maxX, maxY] в мм. По умолчанию это просто
     *  номинальная сетка [0,0, cols*widthMm, rows*heightMm], НО если какой-то
     *  кабинет свободным смещением (см. {@link CabinetInstance#getOffsetXMm()}/
     *  {@code getOffsetYMm}, Task #7/v1.6) вытащен ЗА эти пределы, граница
     *  расширяется, чтобы включить его — иначе (баг-репорт) вытащенный за номинальную
     *  сетку кабинет становился недоступен для клика/перетаскивания (весь расчёт
     *  «попал ли курсор в экран» проверял только номинальный прямоугольник) или
     *  визуально обрезался масштабом миниатюры схемы, посчитанным без него. Общая
     *  точка правды для SceneCanvasPanel (обзор сцены/прериг) и SchemaCanvasPanel
     *  (миниатюра расключения экрана в общей схеме) — раньше каждый считал только
     *  свою локальную копию нужной геометрии, теперь одна и та же формула.
     *  workspace (может быть null — тогда переопределение типа по ячейке не
     *  разрешается) нужен, чтобы кабинет с ФАКТИЧЕСКИ БОЛЬШИМ типом (переопределение
     *  {@link CabinetInstance#getCabinetTypeId()}, например 500×1000мм ячейка в
     *  экране с типом 500×500мм по умолчанию) тоже расширял границы — иначе он
     *  просто не поместился бы в номинальную ячейку даже без всякого смещения. */
    public static double[] cabinetExtentMm(Screen s, CabinetType t, Workspace workspace) {
        double minX = 0, minY = 0;
        double maxX = s.getCols() * t.getWidthMm();
        double maxY = s.getRows() * t.getHeightMm();
        for (CabinetInstance cab : s.getCabinets()) {
            CabinetType eff = effectiveType(cab, t, workspace);
            double ew = eff != null ? eff.getWidthMm() : t.getWidthMm();
            double eh = eff != null ? eff.getHeightMm() : t.getHeightMm();
            double x0 = cab.getColIndex() * t.getWidthMm() + cab.getOffsetXMm();
            double y0 = cab.getRowIndex() * t.getHeightMm() + cab.getOffsetYMm();
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x0 + ew);
            maxY = Math.max(maxY, y0 + eh);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /** Перегрузка без workspace — не разрешает переопределение типа по ячейке
     *  (только свободное смещение). Оставлена для мест, где workspace недоступен
     *  или разрешение типа заведомо не нужно. */
    public static double[] cabinetExtentMm(Screen s, CabinetType t) {
        return cabinetExtentMm(s, t, null);
    }

    /** Экран физически представляет собой РОВНУЮ прямоугольную сетку одинаковых
     *  кабинетов — false, если есть скрытые ("вырезанные") ячейки, свободное мм-
     *  смещение (Task #7/v1.6) или переопределение типа хотя бы у одной ячейки
     *  (разный физический размер, Issue B/v1.6). Нужно экспорту в NovaLCT (см.
     *  NovaLctScrWriter) — формат "Standard Screen" (простая прямоугольная сетка,
     *  row/col-индекс на кабинет) и "Complex Screen" (произвольные прямоугольники,
     *  явные пиксельные X/Y/Width/Height на кабинет) в самом NovaLCT — РАЗНЫЕ
     *  бинарные структуры (подтверждено побайтовым разбором реального образца
     *  Complex Screen — там нет ни привычного 6-байтового якоря, ни JSON-маркера
     *  простого формата вовсе), выбор писать которую нужно делать ДО генерации
     *  байтов, а не пытаться впихнуть неровную форму в простой формат. */
    public static boolean isUniformRectangularGrid(Screen s, CabinetType defaultType, Workspace workspace) {
        for (CabinetInstance cab : s.getCabinets()) {
            if (cab.isHidden()) {
                return false;
            }
            if (cab.getOffsetXMm() != 0 || cab.getOffsetYMm() != 0) {
                return false;
            }
            CabinetType eff = effectiveType(cab, defaultType, workspace);
            if (eff != null && defaultType != null && !eff.getId().equals(defaultType.getId())) {
                return false;
            }
        }
        return s.getCabinets().size() == s.getRows() * s.getCols();
    }

    /** Эффективный размер ячейки (px) для конкретного кабинета — если у него
     *  переопределён тип (см. {@link #effectiveType}) и он физически отличается по
     *  габаритам от типа экрана по умолчанию, ячейка рисуется ПРОПОРЦИОНАЛЬНО
     *  больше/меньше номинальной cellW/cellH, а не втискивается в неё — иначе
     *  комбинация кабинетов разных габаритов на одном экране визуально неотличима
     *  от однородной сетки (баг-репорт: добавил кабинет 500×1000мм в экран с типом
     *  500×500мм — «не изменил размеры модельки, которые должны были подстроиться
     *  под его габариты»). Привязка (левый верхний угол ячейки, см. cabX/cabY)
     *  остаётся номинальной — растёт/уменьшается только сам прямоугольник, поэтому
     *  более крупный кабинет визуально перекрывает соседние ячейки вместо
     *  перестройки всей сетки. */
    public static double effectiveCellW(CabinetType effective, CabinetType defaultType, double cellW) {
        if (effective == null || defaultType == null || defaultType.getWidthMm() <= 0) {
            return cellW;
        }
        return cellW * (effective.getWidthMm() / defaultType.getWidthMm());
    }

    public static double effectiveCellH(CabinetType effective, CabinetType defaultType, double cellH) {
        if (effective == null || defaultType == null || defaultType.getHeightMm() <= 0) {
            return cellH;
        }
        return cellH * (effective.getHeightMm() / defaultType.getHeightMm());
    }

    /** Заполняет экран сеткой rows x cols новых кабинетов. Цепочки теперь хранятся
     *  на уровне сцены (см. Task #78), а не экрана — для только что созданного
     *  экрана их всё равно ещё ни у кого нет, дополнительная очистка не нужна. */
    public static void buildGrid(Screen screen) {
        List<CabinetInstance> cabinets = new ArrayList<>();
        for (int r = 0; r < screen.getRows(); r++) {
            for (int c = 0; c < screen.getCols(); c++) {
                cabinets.add(new CabinetInstance(r, c));
            }
        }
        screen.setCabinets(cabinets);
    }

    /**
     * Меняет размер сетки, сохраняя существующие кабинеты в пределах новых границ.
     * Кабинеты, вышедшие за границы, удаляются, а ссылки на них — из цепочек СЦЕНЫ
     * (см. Task #78 — цепочки больше не хранятся на самом экране, поэтому для
     * прунинга нужна сцена, которой принадлежит экран; null — прунить нечего,
     * например если экран ещё нигде не числится).
     */
    public static void resizeGrid(Screen screen, int newRows, int newCols, Scene scene) {
        Set<String> removedIds = new HashSet<>();
        List<CabinetInstance> kept = new ArrayList<>();
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.getRowIndex() < newRows && c.getColIndex() < newCols) {
                kept.add(c);
            } else {
                removedIds.add(c.getId());
            }
        }
        for (int r = 0; r < newRows; r++) {
            for (int c = 0; c < newCols; c++) {
                if (findAt(kept, r, c) == null) {
                    kept.add(new CabinetInstance(r, c));
                }
            }
        }
        screen.setCabinets(kept);
        screen.setRows(newRows);
        screen.setCols(newCols);

        if (!removedIds.isEmpty() && scene != null) {
            for (PowerChain chain : scene.getPowerChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            scene.getPowerChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
            for (SignalChain chain : scene.getSignalChains()) {
                chain.getCabinetInstanceIds().removeAll(removedIds);
            }
            scene.getSignalChains().removeIf(ch -> ch.getCabinetInstanceIds().isEmpty());
        }
    }

    /** Башня в позиции {@code towerX} (мм, центр) поддерживает хотя бы один РЕАЛЬНО
     *  существующий (не {@code hidden}) кабинет НА САМОМ НИЖНЕМ РЯДУ экрана в своей зоне
     *  ответственности ({@code towerX} ± половина шага башен) — по прямому указанию
     *  пользователя (Phase 2.2): "если в экране есть отсутствующие кабинеты, то для них башни
     *  не строятся". Проверяется ИМЕННО нижний ряд (не любой ряд в столбце) — баг-репорт:
     *  экран с проёмом-аркой (вырезаны нижние ряды, верхние остались) всё равно "видел"
     *  видимый кабинет где-то выше в столбце и считал башню нужной, хотя башне, растущей от
     *  земли, физически нечего поддерживать под этим проёмом — башня опирается на землю и
     *  растёт вверх, а не "телепортируется" через пустоту к оставшимся верхним кабинетам.
     *  {@code type == null} — форма экрана неизвестна, не фильтруем (безопасный дефолт —
     *  строить, а не молча пропускать). */
    private static boolean towerHasCabinetContent(Screen screen, CabinetType type, double towerX, double spacing) {
        if (type == null) {
            return true;
        }
        double halfSpan = spacing / 2.0;
        double cellW = type.getWidthMm();
        int bottomRow = screen.getRows() - 1;
        for (CabinetInstance cab : screen.getCabinets()) {
            if (cab.isHidden() || cab.getRowIndex() != bottomRow) {
                continue;
            }
            double left = cab.getColIndex() * cellW;
            double right = left + cellW;
            if (right > towerX - halfSpan && left < towerX + halfSpan) {
                return true;
            }
        }
        return false;
    }

    /** Пересобирает списки реально существующих ячеек ОБЪЁМНОЙ башни наземного конструктива
     *  ({@link Screen#getStructureFrameCells()}/{@code getStructurePeremychkaCells()}/
     *  {@code getStructureBaseFrameCells()}) под новые номинальные границы сетки — ТОТ ЖЕ
     *  приём, что {@link #resizeGrid} для {@link CabinetInstance}: позиции, всё ещё
     *  попадающие в новые границы, СОХРАНЯЮТ СВОЮ ЗАПИСЬ КАК ЕСТЬ (включая {@code hidden} —
     *  «Рассчитать конструктив» после правки только высоты башни не должно молча возвращать
     *  вручную убранные пользователем сегменты, тот же принцип, что {@code hidden} у
     *  {@link CabinetInstance} переживает resize сетки экрана), позиции без записи ВНУТРИ
     *  новых границ получают новую видимую (hidden=false) запись, а записи ВНЕ новых границ
     *  выбрасываются целиком (как у resizeGrid). Четыре независимые оси (Phase 2.1 — башня
     *  объёмная, не плоская; Phase 2.2 — добавлены усилительные рамы выноса): вертикальные рамы
     *  ядра по (башня × ряд[перед/зад] × сегмент), перемычки — по (башня × уровень), базовые
     *  рамы — по (башня × секция выноса, 0=ядро), усилительные рамы выноса — по (башня × секция
     *  выноса, начиная с 1, {@code row == 2} в том же списке, что и рамы ядра, см.
     *  {@code StructureFrameCell} class-javadoc). {@code Screen#isStructureIncludeBaseFrame()}
     *  сюда сознательно НЕ передаётся — этот чекбокс влияет только на то, показывает/считает ли
     *  конструктив базовые/усилительные рамы вообще (см. {@code StructureCalc}/
     *  {@code ui.Structure3DPanel}), сама расстановка внутри списка от него не зависит,
     *  вызывающая сторона ({@code AppModel#updateScreenStructure}) сохраняет флаг на
     *  {@code Screen} отдельно.
     *
     * <p><b>Round 5 — задний ряд короче переднего</b> (баг-репорт по фото реальной башни: с
     *  фронта видна ОДНА полноразмерная лестничная рама, задний ряд — просто короткая опора):
     *  {@code row == 0} (передний) ограничен {@code verticalFramesPerTower}, {@code row == 1}
     *  (задний) — отдельным, обычно намного меньшим {@code backRowSegments}.
     *
     * <p><b>Форма экрана (Phase 2.2)</b>: башня по номинальному шагу, под которой на экране НЕТ
     *  ни одного видимого кабинета (см. {@link #towerHasCabinetContent}), ПОЛНОСТЬЮ исключается
     *  из всех трёх осей — как если бы её индекс был вне {@code towerCount} — это касается и
     *  сохранения уже существующих (в т.ч. вручную отредактированных) ячеек: если форма экрана
     *  изменилась и башня осталась без кабинетов под собой, её ячейки выбрасываются при
     *  следующем «Рассчитать». Ручное добавление кликом в 3D ({@code AppModel
     *  #toggleStructureFrameCell}) этому правилу НЕ подчиняется — фильтр действует только здесь,
     *  при автогенерации.
     *
     * <p><b>Round 10 — {@code coreBaseSectionCount}</b> (баг-репорт: "плита должна не
     *  рендериться как N рам, а являться N отдельными рамами. Башни могут быть глубиной 0.5,
     *  в таком случае текущее основание не будет подходить по габаритам") — "ядро" базовой рамы
     *  (секция выноса, ближайшая к экрану) раньше было ВСЕГДА ровно одной {@code
     *  StructureBaseFrameCell} (index 0), покрывающей ВСЮ глубину под обоими рядами сразу
     *  (произвольный, не каталожный размер). Теперь вызывающая сторона ({@code
     *  AppModel#updateScreenStructure}, знающая реальные габариты выбранных типов рамы через
     *  {@code Workspace}) сама считает {@code StructureCalc#coreBaseSectionCount(frameW,
     *  sectionDepthMm)} и передаёт сюда — ядро занимает индексы {@code [0, coreBaseSectionCount)},
     *  каждый — РЕАЛЬНЫЙ, независимо переключаемый модуль каталожного размера, а не одна
     *  большая плита; секции выноса начинаются с {@code coreBaseSectionCount}, а не жёстко с 1.
     *  {@code ScreenLogic} сам не знает габаритов рамы (нет доступа к {@code Workspace}) —
     *  параметр всегда приходит уже вычисленным, как и остальные номинальные счётчики здесь. */
    public static void regenerateStructureCells(Screen screen, CabinetType type, int towerCount,
            int verticalFramesPerTower, int backRowSegments, int peremychkaLevels, int extendedBaseSections,
            int coreBaseSectionCount) {
        double spacing = screen.getStructureTowerSpacingMm();
        Set<Integer> validTowers = new HashSet<>();
        for (int t = 0; t < towerCount; t++) {
            if (towerHasCabinetContent(screen, type, t * spacing, spacing)) {
                validTowers.add(t);
            }
        }

        List<StructureFrameCell> frames = screen.getStructureFrameCells();
        List<StructureFrameCell> keptFrames = new ArrayList<>();
        for (StructureFrameCell c : frames) {
            int rowLimit = c.getRow() == 0 ? verticalFramesPerTower : backRowSegments;
            if (validTowers.contains(c.getTowerIndex()) && c.getRow() < 2 && c.getSegmentIndex() < rowLimit) {
                keptFrames.add(c);
            }
        }
        for (int t : validTowers) {
            for (int row = 0; row < 2; row++) {
                int rowLimit = row == 0 ? verticalFramesPerTower : backRowSegments;
                for (int seg = 0; seg < rowLimit; seg++) {
                    int ft = t;
                    int fr = row;
                    int fs = seg;
                    if (keptFrames.stream().noneMatch(c -> c.matches(ft, fr, fs))) {
                        keptFrames.add(new StructureFrameCell(ft, fr, fs));
                    }
                }
            }
        }

        // Round 10 (баг-репорт: "эта пластина должна не рендериться как 2 рамы, а являться 2
        // отдельными рамами") -- "ядро" базовой рамы больше не ОДНА секция (index 0), а
        // {@code coreSections} штук РЕАЛЬНЫХ, независимо переключаемых модулей каталожного
        // размера (см. StructureCalc#coreBaseSectionCount) -- усилительные рамы выноса
        // (row == 2) по-прежнему идут ТОЛЬКО в секциях выноса, но те теперь начинаются с
        // {@code coreSections}, а не жёстко с 1.
        int coreSections = Math.max(1, coreBaseSectionCount);
        int sectionCount = coreSections + Math.max(0, extendedBaseSections);
        for (StructureFrameCell c : frames) {
            if (validTowers.contains(c.getTowerIndex()) && c.getRow() == 2
                    && c.getSegmentIndex() >= coreSections && c.getSegmentIndex() < sectionCount) {
                keptFrames.add(c);
            }
        }
        for (int t : validTowers) {
            for (int section = coreSections; section < sectionCount; section++) {
                int ft = t;
                int fsec = section;
                if (keptFrames.stream().noneMatch(c -> c.matches(ft, 2, fsec))) {
                    keptFrames.add(new StructureFrameCell(ft, 2, fsec));
                }
            }
        }
        screen.setStructureFrameCells(keptFrames);

        // Round 14 — откат Round 13: пользователь явно попросил вернуть сплошную стену
        // конструктива по умолчанию ("пусть для экрана генерится стена конструктива, при
        // необходимости инженер сам удалит ненужные рамы") — перемычка/база между СОСЕДНИМИ
        // башнями (Round 5-7) снова заполняют автоматически ЛЮБОЙ валидный зазор (обе башни
        // валидны, см. towerHasCabinetContent), а не только сохраняют то, что расставлено
        // вручную — убрать лишнее одним кликом по существующей детали в 3D проще, чем
        // достраивать недостающее по одному призраку за раз.
        List<StructurePeremychkaCell> peremychki = screen.getStructurePeremychkaCells();
        List<StructurePeremychkaCell> keptPeremychki = new ArrayList<>();
        for (StructurePeremychkaCell c : peremychki) {
            boolean gapValid = validTowers.contains(c.getTowerIndex()) && validTowers.contains(c.getTowerIndex() + 1);
            if (gapValid && c.getLevelIndex() < peremychkaLevels) {
                keptPeremychki.add(c);
            }
        }
        for (int t : validTowers) {
            if (!validTowers.contains(t + 1)) {
                continue;
            }
            for (int row = 0; row < 2; row++) {
                for (int level = 0; level < peremychkaLevels; level++) {
                    int ft = t;
                    int fr = row;
                    int fl = level;
                    if (keptPeremychki.stream().noneMatch(c -> c.matches(ft, fr, fl))) {
                        keptPeremychki.add(new StructurePeremychkaCell(ft, fr, fl));
                    }
                }
            }
        }
        screen.setStructurePeremychkaCells(keptPeremychki);

        // То же самое для опорной рамы (Round 7) — соединение соседних башен основанием тоже
        // снова заполняется автоматически, тем же правилом, что и перемычка выше.
        List<StructureBaseFrameCell> baseCells = screen.getStructureBaseFrameCells();
        List<StructureBaseFrameCell> keptBase = new ArrayList<>();
        for (StructureBaseFrameCell c : baseCells) {
            boolean gapValid = validTowers.contains(c.getTowerIndex()) && validTowers.contains(c.getTowerIndex() + 1);
            if (gapValid && c.getSectionIndex() < sectionCount) {
                keptBase.add(c);
            }
        }
        for (int t : validTowers) {
            if (!validTowers.contains(t + 1)) {
                continue;
            }
            for (int section = 0; section < sectionCount; section++) {
                int ft = t;
                int fsec = section;
                if (keptBase.stream().noneMatch(c -> c.matches(ft, fsec))) {
                    keptBase.add(new StructureBaseFrameCell(ft, fsec));
                }
            }
        }
        screen.setStructureBaseFrameCells(keptBase);
    }

    /** Фактический тип кабинета: переопределение по ячейке (если задано и разрешимо
     *  через workspace), иначе тип экрана по умолчанию. */
    public static CabinetType effectiveType(CabinetInstance c, CabinetType defaultType, Workspace workspace) {
        if (workspace != null && c.getCabinetTypeId() != null) {
            CabinetType override = workspace.cabinetTypeById(c.getCabinetTypeId());
            if (override != null) {
                return override;
            }
        }
        return defaultType;
    }

    /** Разбирает сквозной (в рамках экрана) номер порта сигнальной цепочки
     *  (см. {@link SignalChain#getPortNumber()}, нумеруется подряд по назначенным
     *  экрану контроллерам — тот же обход, что и SchemeRenderer.controllerForPort)
     *  на пару (индекс карты, локальный порт НА карте, оба 0-based) — так, как эту
     *  пару хранит бинарный формат NovaLCT .scr (см. {@link NovaLctScrWriter}).
     *  У контроллера без карт (плоский portCount) карта считается единственной
     *  нулевой. null — порт вне диапазона всех контроллеров экрана. */
    public static int[] cardAndLocalPort(Screen scr, Workspace workspace, int globalPort) {
        if (workspace == null) {
            return null;
        }
        int offset = 0;
        for (ControllerInstance ci : scr.getControllers()) {
            ControllerType t = workspace.controllerTypeById(ci.getControllerTypeId());
            int count = t != null ? t.effectivePortCount() : 0;
            if (globalPort > offset && globalPort <= offset + count) {
                int local = globalPort - offset;
                if (t == null || t.getCards().isEmpty()) {
                    return new int[]{0, local - 1};
                }
                int cardIdx = 0;
                for (SchemaCard c : t.getCards()) {
                    int cardOutputs = c.totalOutputs();
                    if (local <= cardOutputs) {
                        return new int[]{cardIdx, local - 1};
                    }
                    local -= cardOutputs;
                    cardIdx++;
                }
                return new int[]{Math.max(0, cardIdx - 1), 0};
            }
            offset += count;
        }
        return null;
    }

    /** Прямоугольник ФАКТИЧЕСКИ занимаемого кабинетом места на сетке экрана, В ММ,
     *  в локальных координатах экрана (0,0 = номинальный левый верхний угол сетки) —
     *  [x, y, ширина, высота] — учитывает и свободное смещение (offsetXMm/YMm,
     *  Task #7/v1.6), и переопределение физического размера по ячейке (effectiveType,
     *  см. эффективный размер в SchemeRenderer/CanvasPanel/SceneCanvasPanel). Нужен
     *  для проверки наложения кабинетов друг на друга (см. AppModel.updateCabinetOffset/
     *  setCabinetTypeOverride) — оба источника наложения (сдвинули ИЛИ поменяли тип
     *  на другой физический размер) сводятся к одной и той же геометрии в мм, без
     *  привязки к какому-либо конкретному масштабу отрисовки. */
    public static double[] cabinetRectMm(CabinetInstance cab, CabinetType defaultType, Workspace workspace) {
        CabinetType eff = effectiveType(cab, defaultType, workspace);
        double w = eff != null ? eff.getWidthMm() : defaultType.getWidthMm();
        double h = eff != null ? eff.getHeightMm() : defaultType.getHeightMm();
        double x = cab.getColIndex() * defaultType.getWidthMm() + cab.getOffsetXMm();
        double y = cab.getRowIndex() * defaultType.getHeightMm() + cab.getOffsetYMm();
        return new double[]{x, y, w, h};
    }

    /** Пересекаются ли два прямоугольника [x,y,w,h] — строго (общая граница без
     *  общей площади наложением не считается), обычный AABB-тест. */
    public static boolean rectsOverlap(double[] a, double[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    private static CabinetInstance findAt(List<CabinetInstance> list, int row, int col) {
        for (CabinetInstance c : list) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    /** Характеристики экрана без учёта переопределения типа кабинета по ячейкам. */
    public static ScreenStats stats(Screen screen, CabinetType defaultType) {
        return stats(screen, defaultType, null);
    }

    /** Суммарные характеристики НЕСКОЛЬКИХ экранов (вся сцена) — для режима «показать
     *  все экраны сцены» на этапах Питание/Сигнал, где статистика одного активного
     *  экрана не даёт полной картины по нагрузке всей сцены (см. Task #71).
     *  Разрешение/физический размер не агрегируются (экраны — не единый прямоугольник,
     *  просто список независимых сеток) — в возвращаемой записи это 0, UI показывает
     *  вместо них количество экранов отдельно. */
    public static ScreenStats aggregateStats(List<Screen> screens,
                                              java.util.function.Function<Screen, CabinetType> typeResolver,
                                              Workspace workspace) {
        int active = 0;
        int[] phaseCounts = new int[4];
        double[] phasePower = new double[4];
        double totalPower = 0;
        double totalWeight = 0;
        for (Screen s : screens) {
            ScreenStats st = stats(s, typeResolver.apply(s), workspace);
            active += st.activeCabinetCount();
            totalPower += st.totalPowerW();
            totalWeight += st.totalWeightKg();
            for (int i = 1; i <= 3; i++) {
                phaseCounts[i] += st.phaseCabinetCounts()[i];
                phasePower[i] += st.phasePowerW()[i];
            }
        }
        return new ScreenStats(0, 0, 0, 0, active, totalPower, totalWeight, phaseCounts, phasePower);
    }

    /**
     * Характеристики экрана. Разрешение/габариты считаются по типу экрана
     * (геометрия сетки — единая), а вес/мощность/нагрузка по фазам — по
     * ФАКТИЧЕСКОМУ типу каждого кабинета (с учётом переопределения по ячейкам,
     * если передан workspace для его разрешения).
     */
    public static ScreenStats stats(Screen screen, CabinetType defaultType, Workspace workspace) {
        double w = defaultType != null ? defaultType.getWidthMm() : 0;
        double h = defaultType != null ? defaultType.getHeightMm() : 0;
        int rw = defaultType != null ? defaultType.getResolutionWidth() : 0;
        int rh = defaultType != null ? defaultType.getResolutionHeight() : 0;

        int active = 0;
        int[] phaseCounts = new int[4];
        double[] phasePower = new double[4];
        double totalPower = 0;
        double totalWeight = 0;
        for (CabinetInstance c : screen.getCabinets()) {
            if (c.isHidden()) {
                continue;
            }
            active++;
            CabinetType effective = effectiveType(c, defaultType, workspace);
            double p = effective != null ? effective.getPowerConsumptionW() : 0;
            double wt = effective != null ? effective.getWeightKg() : 0;
            totalPower += p;
            totalWeight += wt;
            if (c.getPhase() >= 1 && c.getPhase() <= 3) {
                phaseCounts[c.getPhase()]++;
                phasePower[c.getPhase()] += p;
            }
        }

        return new ScreenStats(
                screen.getCols() * w,
                screen.getRows() * h,
                screen.getCols() * rw,
                screen.getRows() * rh,
                active,
                totalPower,
                totalWeight,
                phaseCounts,
                phasePower
        );
    }

    /** Базовая сводка по сцене (прериг): суммарный вес/мощность и разбивка кабинетов
     *  по фактическому типу (для отображения «128 (Base: 96, Heavy: 32)» в прериге). */
    public static SceneStats sceneStats(Scene scene, Workspace workspace) {
        int cabinets = 0;
        double power = 0;
        double weight = 0;
        java.util.Map<CabinetType, Integer> byType = new java.util.LinkedHashMap<>();
        for (Screen s : scene.getScreens()) {
            CabinetType defaultType = workspace.cabinetTypeById(s.getCabinetTypeId());
            ScreenStats st = stats(s, defaultType, workspace);
            cabinets += st.activeCabinetCount();
            power += st.totalPowerW();
            weight += st.totalWeightKg();
            for (CabinetInstance c : s.getCabinets()) {
                if (c.isHidden()) {
                    continue;
                }
                CabinetType effective = effectiveType(c, defaultType, workspace);
                if (effective != null) {
                    byType.merge(effective, 1, Integer::sum);
                }
            }
        }
        return new SceneStats(scene.getScreens().size(), cabinets, power, weight, byType);
    }

    /** Авторасчёт КОЛИЧЕСТВА точек подвеса — минимум из референсных таблиц риг-тех
     *  расчёта заказчика: «Hanging bar» = ширина экрана в модулях / 2 (стандартный
     *  сегмент несущей балки перекрывает 2 модуля по ширине), не меньше 2 (подвес
     *  не бывает на одной точке) — УВЕЛИЧЕННЫЙ, если реальная нагрузка на точку
     *  превышает грузоподъёмность выбранной лебёдки (см. javadoc
     *  {@link RiggingCalc#suggestPointCount}, которому этот метод делегирует —
     *  оставлен здесь для обратной совместимости вызывающего кода). Пользователь
     *  может скорректировать количество вручную под конкретную ферму/траверс. */
    public static int suggestRiggingPoints(Screen screen, com.vjstb.ledscheme.model.CabinetType defaultType,
                                            com.vjstb.ledscheme.model.Workspace workspace) {
        return RiggingCalc.suggestPointCount(screen, defaultType, workspace);
    }

    // ---- undo: снимок и восстановление состояния экрана ----

    public static Screen snapshot(Screen screen) {
        return screen.copy();
    }

    /** Восстанавливает изменяемое состояние экрана из снимка, сохраняя сам объект. */
    public static void restore(Screen live, Screen snapshot) {
        live.setName(snapshot.getName());
        live.setCabinetTypeId(snapshot.getCabinetTypeId());
        live.setRows(snapshot.getRows());
        live.setCols(snapshot.getCols());
        live.setPosXMm(snapshot.getPosXMm());
        live.setPosYMm(snapshot.getPosYMm());
        live.setSignalPortCount(snapshot.getSignalPortCount());
        live.setRefreshRateHz(snapshot.getRefreshRateHz());
        live.setColorBitDepth(snapshot.getColorBitDepth());
        live.setMountType(snapshot.getMountType());
        live.setRiggingPointsCount(snapshot.getRiggingPointsCount());
        live.setRiggingNotes(snapshot.getRiggingNotes());
        live.setRiggingSafetyFactorMin(snapshot.getRiggingSafetyFactorMin());
        live.setRiggingHoistCapacityKg(snapshot.getRiggingHoistCapacityKg());

        List<CabinetInstance> cabs = new ArrayList<>();
        for (CabinetInstance c : snapshot.getCabinets()) {
            cabs.add(c.copy());
        }
        live.setCabinets(cabs);

        List<com.vjstb.ledscheme.model.ControllerInstance> ctrls = new ArrayList<>();
        for (com.vjstb.ledscheme.model.ControllerInstance c : snapshot.getControllers()) {
            ctrls.add(c.copy());
        }
        live.setControllers(ctrls);
        // Цепочки питания/сигнала больше не восстанавливаются здесь — они хранятся
        // на уровне сцены, а не экрана (см. Task #78); AppModel.undo() снимает и
        // восстанавливает их отдельно, вместе со снимком экрана.
    }
}
