package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Таблицы «Карты подключения модулей» (docs/receiver-cards/PLAN.md, раздел 7, трек E): для техника, который в
 * NovaLCT / на площадке расставляет номера модулей и тянет шлейфы. Формат — как у соседних табличных выгрузок проекта
 * (спецификация): листы {@code .xlsx} через {@link SpecXlsxWriter}, числа — числовыми ячейками (фильтруются и
 * сортируются в Excel), всё остальное — текстом.
 *
 * <ul>
 *   <li>«Приёмные карты»: карта → модель → место → номер выхода → модуль (ряд, столбец); внизу — модули без карты;</li>
 *   <li>«Блоки питания»: блок → модель → место → шлейф → кто питается (модуль либо карта); внизу — модули без
 *   питания.</li>
 * </ul>
 * Имена К1…КN / БП1…БПn и обозначение номера модуля берутся по Предпочтениям пользователя — те же, что на холстах и в
 * картинках, чтобы таблица и схема не расходились. Ряды и столбцы — с единицы, как их считает пользователь.
 */
public final class ConnectionMapTables {

    private ConnectionMapTables() {
    }

    /** Одна таблица: имя листа, заголовки, строки. */
    public record Table(String sheetName, String[] headers, List<Object[]> rows) {
        /** Добавляет таблицу листом в книгу. */
        public void addTo(Workbook wb) {
            Sheet sheet = SpecXlsxWriter.addSheet(wb, sheetName, headers);
            for (Object[] row : rows) {
                SpecXlsxWriter.addRow(sheet, row);
            }
            SpecXlsxWriter.autoSizeColumns(sheet, headers.length);
        }
    }

    public static final String NO_CARD = "— без карты —";
    public static final String NO_SUPPLY = "— без питания —";

    /**
     * Таблица карт по модульным экранам {@code screens} (остальные пропускаются).
     *
     * @param numberFormat шаблон обозначения номера модуля из Предпочтений ({@code {n}}, {@code {card}})
     */
    public static Table cards(List<Screen> screens, Workspace ws, ReceiverCardNumbering numbering, String numberFormat) {
        List<Object[]> rows = new ArrayList<>();
        for (Screen screen : screens) {
            ScreenModules sm = ScreenModules.of(screen, ws);
            if (!sm.isModular()) {
                continue;
            }
            Map<String, String> names = UnitNames.cardNames(screen, numbering);
            for (ReceiverCardInstance card : UnitNames.orderedCards(screen, numbering)) {
                String name = names.get(card.getId());
                String model = cardModel(ws, card);
                String place = placeText(sm, UnitNames.placeOf(screen, card));
                List<String> order = ModuleNumbering.order(card, screen);
                for (int i = 0; i < order.size(); i++) {
                    ScreenModules.Module m = sm.module(order.get(i));
                    if (m != null) {
                        rows.add(new Object[]{screen.getName(), name, model, place, i + 1,
                                ModuleNumbering.label(numberFormat, name, i + 1), m.row() + 1, m.col() + 1});
                    }
                }
                // Модули карты без номера (ручной порядок, не все отмечены): в таблице они есть, номер — «?».
                for (String id : ModuleNumbering.unnumbered(card, screen)) {
                    ScreenModules.Module m = sm.module(id);
                    if (m != null) {
                        rows.add(new Object[]{screen.getName(), name, model, place, "?",
                                ModuleNumbering.label(numberFormat, name, 0), m.row() + 1, m.col() + 1});
                    }
                }
            }
            for (String id : ReceiverCardChecks.check(screen, ws).modulesWithoutCard()) {
                ScreenModules.Module m = sm.module(id);
                if (m != null) {
                    rows.add(new Object[]{screen.getName(), NO_CARD, null, null, null, null, m.row() + 1,
                            m.col() + 1});
                }
            }
        }
        return new Table("Приёмные карты", new String[]{"Экран", "Карта", "Модель", "Место карты", "Номер выхода",
                "Обозначение на модуле", "Модуль: ряд", "Модуль: столбец"}, rows);
    }

    /** Таблица блоков питания по модульным экранам {@code screens}. */
    public static Table supplies(List<Screen> screens, Workspace ws, ReceiverCardNumbering numbering) {
        List<Object[]> rows = new ArrayList<>();
        for (Screen screen : screens) {
            ScreenModules sm = ScreenModules.of(screen, ws);
            if (!sm.isModular()) {
                continue;
            }
            Map<String, String> cardNames = UnitNames.cardNames(screen, numbering);
            PowerSupplyChecks.Result checks = PowerSupplyChecks.check(screen, ws, numbering);
            for (PowerSupplyLayout.Supply sup : checks.layout().supplies()) {
                String model = supplyModel(sup.type(), sup.instance().getPowerSupplyTypeId());
                String place = placeText(sm, sup.placeModuleId());
                for (PowerSupplyLayout.CardFeed cf : sup.cards()) {
                    ReceiverCardInstance card = screen.receiverCardById(cf.cardId());
                    String cardName = cardNames.get(cf.cardId());
                    ScreenModules.Module host = card != null ? sm.module(UnitNames.placeOf(screen, card)) : null;
                    rows.add(new Object[]{screen.getName(), sup.name(), model, place,
                            cf.dedicated() ? "К" : (Object) cf.output(), "карта " + (cardName != null ? cardName : "?"),
                            host != null ? host.row() + 1 : null, host != null ? host.col() + 1 : null});
                }
                for (PowerSupplyLayout.ModuleFeed mf : sup.modules()) {
                    ScreenModules.Module m = sm.module(mf.moduleId());
                    if (m != null) {
                        rows.add(new Object[]{screen.getName(), sup.name(), model, place, mf.output(), "модуль",
                                m.row() + 1, m.col() + 1});
                    }
                }
            }
            for (String id : checks.modulesWithoutSupply()) {
                ScreenModules.Module m = sm.module(id);
                if (m != null) {
                    rows.add(new Object[]{screen.getName(), NO_SUPPLY, null, null, null, "модуль", m.row() + 1,
                            m.col() + 1});
                }
            }
        }
        return new Table("Блоки питания", new String[]{"Экран", "Блок", "Модель", "Место блока", "Шлейф",
                "Питает", "Ряд", "Столбец"}, rows);
    }

    private static String cardModel(Workspace ws, ReceiverCardInstance card) {
        if (card.getReceiverCardTypeId() == null) {
            return "—";
        }
        ReceiverCardType t = ws.receiverCardTypeById(card.getReceiverCardTypeId());
        return t != null ? t.getName() : "(тип не найден)";
    }

    private static String supplyModel(PowerSupplyType type, String typeId) {
        if (typeId == null) {
            return "—";
        }
        return type != null ? type.getName() : "(тип не найден)";
    }

    /** «ряд 2, столбец 3» — ячейка с 1; пусто, если места нет. */
    private static String placeText(ScreenModules sm, String placeModuleId) {
        CabinetInstance c = sm.cabinet(placeModuleId);
        return c != null ? "ряд " + (c.getRowIndex() + 1) + ", столбец " + (c.getColIndex() + 1) : "";
    }
}
