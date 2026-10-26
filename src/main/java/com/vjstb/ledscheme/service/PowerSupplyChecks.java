package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardNumbering;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Проверки блоков питания экрана (docs/receiver-cards/PLAN.md, D5, D17–D19): модули без блока; карты без питания
 * (место карты вне области какого-либо блока); блок перегружен (занято шлейфов больше, чем выходов — с учётом
 * принудительного шлейфа под карту); кабель до модуля длиннее допустимого. Числа типа необязательны, пропущенное
 * ({@code null}) не проверяется. Сам расчёт занятости — {@link PowerSupplyLayout}; здесь он собран в список замечаний
 * для чек-листа и подсветки в виде.
 */
public final class PowerSupplyChecks {

    private PowerSupplyChecks() {
    }

    /** Блок, у которого есть что подсветить красным. */
    public record SupplyProblem(String supplyId, String name, int over, List<String> longModuleIds) {
    }

    /**
     * @param modulesWithoutSupply модули экрана, которых не питает ни один блок (оранжевые в виде)
     * @param cardsWithoutSupply   карты, чьё место вне областей блоков — у них нет выхода питания
     * @param problems             перегруженные блоки и блоки с длинными кабелями
     * @param layout               полная раскладка выходов (из неё же берутся номера шлейфов и имена БП)
     */
    public record Result(List<String> modulesWithoutSupply, List<String> cardsWithoutSupply,
                         List<SupplyProblem> problems, PowerSupplyLayout.Result layout) {
        public boolean ok() {
            return modulesWithoutSupply.isEmpty() && cardsWithoutSupply.isEmpty() && problems.isEmpty();
        }
    }

    public static Result check(Screen screen, Workspace workspace, ReceiverCardNumbering numbering) {
        ScreenModules sm = ScreenModules.of(screen, workspace);
        PowerSupplyLayout.Result layout = PowerSupplyLayout.compute(sm, screen.getPowerSupplies(), numbering);
        Set<String> powered = new HashSet<>();
        for (PowerSupplyInstance s : screen.getPowerSupplies()) {
            powered.addAll(s.getModuleIds());
        }
        List<String> without = new ArrayList<>();
        for (ScreenModules.Module m : sm.modules()) {
            if (!powered.contains(m.id())) {
                without.add(m.id());
            }
        }
        List<String> cardsWithout = new ArrayList<>();
        for (ReceiverCardInstance c : UnitNames.orderedCards(screen, numbering)) {
            if (layout.ofCard(c.getId()) == null) {
                cardsWithout.add(c.getId());
            }
        }
        List<SupplyProblem> problems = new ArrayList<>();
        for (PowerSupplyLayout.Supply s : layout.supplies()) {
            if (s.hasProblem()) {
                problems.add(new SupplyProblem(s.instance().getId(), s.name(), s.over(), s.longModuleIds()));
            }
        }
        return new Result(without, cardsWithout, problems, layout);
    }
}
