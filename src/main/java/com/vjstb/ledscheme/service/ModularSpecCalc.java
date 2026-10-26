package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Позиции спецификации сцены, которые дают МОДУЛЬНЫЕ экраны (docs/receiver-cards/PLAN.md, раздел 7, трек E): приёмные
 * карты и блоки питания по моделям, сигнальные шлейфы «модуль — карта» (по числу модулей в картах, с длиной
 * {@code ribbonLengthMm}, если она указана у типа карты) и кабели питания (по занятым выходам блока, включая
 * выход «К»/шлейф под карту, с длиной {@code cableLengthMm} типа блока).
 *
 * <p>Считается только по сцене и без POI, чтобы тестироваться отдельно от {@code OutputStagePanel}. Немодульные экраны
 * не дают ни одной строки — спецификация проектов без модулей остаётся прежней. Позиции не привязаны к схемам сцены
 * (карты и блоки питания стоят на ЭКРАНЕ, а не в общей схеме), поэтому в таблице это отдельный столбец «Экраны», а
 * в «Общем списке» — обычное количество.
 */
public final class ModularSpecCalc {

    private ModularSpecCalc() {
    }

    /** Подписи категорий (столбец «Тип узла» листа «Оборудование» и «Категория» «Общего списка»). */
    public static final String CARDS = "Приёмные карты";
    public static final String SUPPLIES = "Блоки питания";
    public static final String RIBBONS = "Шлейфы сигнала (модуль — карта)";
    public static final String CABLES = "Кабели питания (блок — модуль/карта)";

    /** Строка: режим («Сигнал»/«Питание»), категория, наименование, количество. */
    public record Row(String modeLabel, String category, String label, int count) {
    }

    /** true — в сцене есть хотя бы один модульный экран. */
    public static boolean hasModular(Scene scene, Workspace ws) {
        return ChainLinks.sceneHasModularScreen(scene, ws);
    }

    /** Строки по сцене: карты, блоки, шлейфы, кабели (порядок моделей — по первому появлению). Пустой список — модульных
     *  экранов нет. */
    public static List<Row> rows(Scene scene, Workspace ws) {
        Map<String, Integer> cards = new LinkedHashMap<>();
        Map<String, Integer> supplies = new LinkedHashMap<>();
        Map<String, Integer> ribbons = new LinkedHashMap<>();
        Map<String, Integer> cables = new LinkedHashMap<>();
        if (scene == null || ws == null) {
            return List.of();
        }
        for (Screen screen : scene.getScreens()) {
            ScreenModules sm = ScreenModules.of(screen, ws);
            if (!sm.isModular()) {
                continue;
            }
            for (ReceiverCardInstance card : screen.getReceiverCards()) {
                int modules = existingModules(sm, card);
                if (modules == 0) {
                    continue;
                }
                ReceiverCardType t = card.getReceiverCardTypeId() != null
                        ? ws.receiverCardTypeById(card.getReceiverCardTypeId()) : null;
                cards.merge(modelName(t != null ? t.getName() : null), 1, Integer::sum);
                ribbons.merge(ribbonLabel(t), modules, Integer::sum);
            }
            for (PowerSupplyLayout.Supply sup : PowerSupplyLayout.compute(screen, ws, ReceiverCardNumbering.DEFAULT)
                    .supplies()) {
                PowerSupplyType t = sup.type();
                supplies.merge(modelName(t != null ? t.getName() : null), 1, Integer::sum);
                int outputs = sup.cards().size() + sup.modules().size();
                if (outputs > 0) {
                    cables.merge(cableLabel(t), outputs, Integer::sum);
                }
            }
        }
        List<Row> out = new ArrayList<>();
        cards.forEach((label, n) -> out.add(new Row("Сигнал", CARDS, label, n)));
        supplies.forEach((label, n) -> out.add(new Row("Питание", SUPPLIES, label, n)));
        ribbons.forEach((label, n) -> out.add(new Row("Сигнал", RIBBONS, label, n)));
        cables.forEach((label, n) -> out.add(new Row("Питание", CABLES, label, n)));
        return out;
    }

    private static int existingModules(ScreenModules sm, ReceiverCardInstance card) {
        int n = 0;
        for (String id : card.getModuleIds()) {
            if (sm.module(id) != null) {
                n++;
            }
        }
        return n;
    }

    private static String modelName(String name) {
        return name != null && !name.isBlank() ? name : "тип не указан";
    }

    /** «К8» либо «К8, 600 мм»: длина шлейфа входит в наименование позиции — шлейфы разной длины заказывают отдельно. */
    private static String ribbonLabel(ReceiverCardType t) {
        String base = "для карты «" + modelName(t != null ? t.getName() : null) + "»";
        Double len = t != null ? t.getRibbonLengthMm() : null;
        return len != null ? base + ", " + fmtMm(len) + " мм" : base;
    }

    private static String cableLabel(PowerSupplyType t) {
        String base = "от блока «" + modelName(t != null ? t.getName() : null) + "»";
        Double len = t != null ? t.getCableLengthMm() : null;
        return len != null ? base + ", " + fmtMm(len) + " мм" : base;
    }

    private static String fmtMm(double mm) {
        return mm == Math.rint(mm) ? Long.toString(Math.round(mm)) : String.format(Locale.ROOT, "%.1f", mm);
    }
}
