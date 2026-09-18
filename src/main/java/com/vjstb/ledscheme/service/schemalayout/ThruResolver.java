package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import java.util.List;

/**
 * Разрешает, является ли ВЫХОДНАЯ группа разъёмов узла общей схемы транзитной —
 * сквозным проходом того же типа разъёма дальше по цепи, а не самостоятельным
 * отходящим выходом (docs/schema-ports-rework/PLAN.md, задача T1.3/§2.3). Транзит
 * влияет на сторону рамки, куда рисуется гнездо (см. {@link SideRules}: транзитный
 * выход — всегда снизу, независимо от роли и ориентации блока) — например, выход
 * силового щита-разветвителя «Проходная» или петля генлока контроллера.
 *
 * <p>Порядок источников — тот же принцип старшинства, что и у {@link
 * PortRoleResolver}: правка на блоке в ЭТОМ проекте → явная пометка в библиотеке
 * ({@link CardPort#getThru()}) → авто-угадывание по составу карты (для силовой схемы —
 * по составу ВСЕГО списка разъёмов узла, у неё нет карт). Смысла нет ни для {@link
 * PortDirection#IN}, ни для {@link PortDirection#IN_OUT} — им всегда возвращается
 * {@code false} (по этим направлениям связь идёт К узлу/сквозь него, а не транзитом
 * дальше — переопределения тоже не смотрятся).
 */
public final class ThruResolver {

    private ThruResolver() {
    }

    /** @param candidate  группа, для которой разрешается транзит — ДОЛЖНА входить в
     *                    {@code siblings} (иначе авто-угадывание её не увидит).
     *  @param siblings   ВСЕ группы того же охвата, что и {@code candidate}: для
     *                    сигнала — {@code card.getPorts()} одной карты (см. control-
     *                    кейс PLAN.md §2.3 — Q8: вход и выход HDMI на РАЗНЫХ картах,
     *                    поэтому НЕ транзит), для питания — {@code
     *                    node.getPowerConnectors()} целиком (у щита нет карт). */
    public static boolean isThru(CardPort candidate, List<CardPort> siblings, PortPlacement placement) {
        if (candidate.getDirection() != PortDirection.OUT) {
            return false;
        }
        if (placement != null && placement.getThruOverride() != null) {
            return placement.getThruOverride();
        }
        if (candidate.getThru() != null) {
            return candidate.getThru();
        }
        return autoThru(candidate, siblings);
    }

    /** Ровно одна IN-группа и ровно одна OUT-группа (сама {@code outPort}) того же
     *  {@link CardPort#getConnectorType()}, у ОБЕИХ {@code count == 1} — см. control-
     *  кейсы PLAN.md §2.3: «Проходная» CEE 32A 1 вход+1 выход → транзит, но 6×CEE 16A
     *  без входа того же типа — нет; MCTRL4K Genlock 1+1 → транзит; Disguise D3 XLR
     *  2 входа+2 выхода → НЕ транзит именно из-за count==2, а не из-за числа групп. */
    private static boolean autoThru(CardPort outPort, List<CardPort> siblings) {
        if (outPort.getCount() != 1) {
            return false;
        }
        List<CardPort> matchingIn = siblings.stream()
                .filter(p -> p.getDirection() == PortDirection.IN
                        && p.getConnectorType().equals(outPort.getConnectorType()))
                .toList();
        long matchingOutCount = siblings.stream()
                .filter(p -> p.getDirection() == PortDirection.OUT
                        && p.getConnectorType().equals(outPort.getConnectorType()))
                .count();
        return matchingIn.size() == 1 && matchingIn.get(0).getCount() == 1 && matchingOutCount == 1;
    }
}
