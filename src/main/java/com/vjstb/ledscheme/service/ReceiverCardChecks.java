package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Проверки приёмных карт экрана (docs/receiver-cards/PLAN.md, D5, D12, D13, D16): модули без карты; модулей больше
 * выходов карты; шлейф длиннее допустимого; пиксели карты против {@code maxWidthPx / maxHeightPx / maxPixels} типа.
 * Все числа типа необязательны ({@code null} — «не указано, не проверять»), поэтому и проверка молча пропускается.
 * Длина шлейфа — по прямой между центрами модуля-«места» карты и модуля (D5); размеры берутся из типов кабинетов
 * ({@link ScreenModules}), если размера нет (0) — длина не проверяется.
 *
 * <p>Класс только считает; цвета состояний («без карты» — оранжевый, перегруз и длинный шлейф — красный) выбирает вид.
 */
public final class ReceiverCardChecks {

    private ReceiverCardChecks() {
    }

    /** Итог по одной карте. */
    public record CardCheck(String cardId, String typeId, int moduleCount, Integer outputs, boolean overOutputs,
                            List<String> overLimitModuleIds, List<String> unnumberedModuleIds,
                            List<String> longRibbonModuleIds, int resolutionWidthPx, int resolutionHeightPx,
                            long pixels, boolean widthExceeded, boolean heightExceeded, boolean pixelsExceeded) {
        /** «⚠N» в списке карт — сколько шлейфов длиннее допустимого. */
        public int longRibbonCount() {
            return longRibbonModuleIds.size();
        }

        /** Есть ли что подсветить предупреждением (перегруз выходов, длинные шлейфы, лимиты пикселей). */
        public boolean hasProblem() {
            return overOutputs || !longRibbonModuleIds.isEmpty() || widthExceeded || heightExceeded || pixelsExceeded;
        }
    }

    /** Итог по экрану. */
    public record Result(List<String> modulesWithoutCard, List<CardCheck> cards) {
        /** Проверка конкретной карты или {@code null}. */
        public CardCheck card(String cardId) {
            for (CardCheck c : cards) {
                if (c.cardId().equals(cardId)) {
                    return c;
                }
            }
            return null;
        }

        /** Всё в порядке: ни одного модуля без карты и ни одного предупреждения по картам. */
        public boolean ok() {
            if (!modulesWithoutCard.isEmpty()) {
                return false;
            }
            for (CardCheck c : cards) {
                if (c.hasProblem()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static Result check(Screen screen, Workspace workspace) {
        ScreenModules sm = ScreenModules.of(screen, workspace);
        Map<String, CabinetInstance> idx = ScreenModules.index(screen);
        Set<String> carded = new HashSet<>();
        for (ReceiverCardInstance c : screen.getReceiverCards()) {
            carded.addAll(c.getModuleIds());
        }
        List<String> without = new ArrayList<>();
        for (ScreenModules.Module m : sm.modules()) {
            if (!carded.contains(m.id())) {
                without.add(m.id());
            }
        }
        List<CardCheck> cards = new ArrayList<>();
        for (ReceiverCardInstance card : screen.getReceiverCards()) {
            cards.add(checkCard(screen, workspace, sm, idx, card));
        }
        return new Result(without, cards);
    }

    private static CardCheck checkCard(Screen screen, Workspace ws, ScreenModules sm,
                                       Map<String, CabinetInstance> idx, ReceiverCardInstance card) {
        ReceiverCardType type = card.getReceiverCardTypeId() != null && ws != null
                ? ws.receiverCardTypeById(card.getReceiverCardTypeId()) : null;
        List<String> numbered = ModuleNumbering.order(card, screen, idx);
        List<String> unnumbered = new ArrayList<>();
        List<CabinetInstance> members = new ArrayList<>();
        for (String id : card.getModuleIds()) {
            CabinetInstance c = idx.get(id);
            if (c != null && !c.isHidden()) {
                members.add(c);
                if (!numbered.contains(id)) {
                    unnumbered.add(id);
                }
            }
        }
        Integer outputs = type != null ? type.getModuleOutputs() : null;
        List<String> overLimit = new ArrayList<>();
        if (outputs != null) {
            for (int i = outputs; i < numbered.size(); i++) {
                overLimit.add(numbered.get(i));
            }
        }
        List<String> longRibbon = new ArrayList<>();
        Double ribbon = type != null ? type.getRibbonLengthMm() : null;
        String place = UnitNames.placeOf(idx, card.getModuleIds(), card.getHostModuleId());
        CabinetInstance placeCab = place != null ? idx.get(place) : null;
        if (ribbon != null && placeCab != null) {
            for (CabinetInstance c : members) {
                if (c != placeCab && sm.distanceMm(placeCab, c) > ribbon) {
                    longRibbon.add(c.getId());
                }
            }
        }

        // Разрешение карты — ширина × высота описанного прямоугольника модулей в пикселях: по каждому столбцу берём
        // самую широкую ячейку, по каждой строке — самую высокую (модули разных типов в одной карте бывают).
        TreeMap<Integer, Integer> colW = new TreeMap<>();
        TreeMap<Integer, Integer> rowH = new TreeMap<>();
        long pixels = 0;
        for (CabinetInstance c : members) {
            CabinetType eff = sm.effectiveType(c);
            int w = eff != null ? eff.getResolutionWidth() : 0;
            int h = eff != null ? eff.getResolutionHeight() : 0;
            pixels += (long) w * h;
            colW.merge(c.getColIndex(), w, Math::max);
            rowH.merge(c.getRowIndex(), h, Math::max);
        }
        int resW = 0;
        int resH = 0;
        if (!members.isEmpty()) {
            int c0 = colW.firstKey();
            int c1 = colW.lastKey();
            int r0 = rowH.firstKey();
            int r1 = rowH.lastKey();
            int fillW = colW.values().iterator().next();
            int fillH = rowH.values().iterator().next();
            for (int c = c0; c <= c1; c++) {
                resW += colW.getOrDefault(c, fillW);
            }
            for (int r = r0; r <= r1; r++) {
                resH += rowH.getOrDefault(r, fillH);
            }
        }
        boolean wEx = type != null && type.getMaxWidthPx() != null && resW > type.getMaxWidthPx();
        boolean hEx = type != null && type.getMaxHeightPx() != null && resH > type.getMaxHeightPx();
        boolean pEx = type != null && type.getMaxPixels() != null && pixels > type.getMaxPixels();
        boolean over = outputs != null && members.size() > outputs;
        return new CardCheck(card.getId(), card.getReceiverCardTypeId(), members.size(), outputs, over, overLimit,
                unnumbered, longRibbon, resW, resH, pixels, wEx, hEx, pEx);
    }
}
