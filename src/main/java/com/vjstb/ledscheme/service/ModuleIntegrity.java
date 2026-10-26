package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.PowerChain;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Целостность карт и блоков питания (docs/receiver-cards/PLAN.md, раздел 5): всё, что может «убрать» модуль или
 * звено, обязано вычистить за собой карты, блоки и цепочки — иначе в цепочке остался бы id несуществующего звена, а
 * карта/блок ссылались бы на скрытую ячейку.
 * <ul>
 *   <li>скрытие модуля, удаление ячейки при смене размера сетки, смена типа ячейки/экрана так, что она перестала быть
 *   модулем, снятие флага «Приёмная карта отдельно» у типа — модуль выпадает из карты и блока;</li>
 *   <li>карта или блок, у которых не осталось модулей, удаляются;</li>
 *   <li>удалённые карты/блоки и удаление всего экрана — их id выпадают из цепочек сцены.</li>
 * </ul>
 * Методы без {@code Scene} меняют только экран и возвращают id исчезнувших звеньев — чтобы вызывающий убрал их из цепочек
 * ({@link #dropFromChains}); {@code AppModel} делает это в одной записи отмены с самой правкой.
 */
public final class ModuleIntegrity {

    private ModuleIntegrity() {
    }

    /** Исчезнувшие звенья: id карт и id блоков. */
    public record Lost(List<String> cardIds, List<String> supplyIds) {
        public List<String> all() {
            List<String> out = new ArrayList<>(cardIds);
            out.addAll(supplyIds);
            return out;
        }

        public boolean isEmpty() {
            return cardIds.isEmpty() && supplyIds.isEmpty();
        }
    }

    /** Что случится с картами и блоками при снятии флага модуля у типа (текст подтверждения — на стороне UI). */
    public record Impact(int cardsRemoved, int suppliesRemoved, int cardsShrunk, int suppliesShrunk) {
        /** Есть ли что терять — если нет, подтверждение не нужно. */
        public boolean any() {
            return cardsRemoved + suppliesRemoved + cardsShrunk + suppliesShrunk > 0;
        }
    }

    /** Убирает указанные модули из карт и блоков экрана; опустевшие карты/блоки удаляются. */
    public static Lost onModulesLost(Screen screen, Collection<String> moduleIds) {
        Set<String> lost = new HashSet<>(moduleIds);
        return filter(screen, id -> !lost.contains(id));
    }

    /**
     * Приводит экран в согласие с реальными модулями: из карт и блоков уходит всё, что больше не модуль (ячейки нет,
     * она скрыта или её эффективный тип потерял флаг). Вызывается после смены типов/сетки и при загрузке библиотеки.
     */
    public static Lost reconcile(Screen screen, Workspace workspace) {
        if (screen.getReceiverCards().isEmpty() && screen.getPowerSupplies().isEmpty()) {
            return new Lost(List.of(), List.of());
        }
        ScreenModules sm = ScreenModules.of(screen, workspace);
        Lost lost = filter(screen, id -> sm.module(id) != null);
        // автообласть без блоков бессмысленна, но безвредна: оставляем — пересчёт при смене типа создаст блоки заново
        return lost;
    }

    /** {@link #reconcile} по всем экранам рабочего пространства, с чисткой цепочек сцен. */
    public static void reconcileWorkspace(Workspace workspace) {
        for (Project p : workspace.getProjects()) {
            for (Scene scene : p.getScenes()) {
                for (Screen s : scene.getScreens()) {
                    Lost lost = reconcile(s, workspace);
                    dropFromChains(scene, lost.all());
                }
            }
        }
    }

    private static Lost filter(Screen screen, java.util.function.Predicate<String> keep) {
        List<String> removedCards = new ArrayList<>();
        for (ReceiverCardInstance c : new ArrayList<>(screen.getReceiverCards())) {
            c.getModuleIds().removeIf(id -> !keep.test(id));
            if (c.getManualPortOrder() != null) {
                c.getManualPortOrder().removeIf(id -> !keep.test(id));
            }
            if (c.getHostModuleId() != null && !c.getModuleIds().contains(c.getHostModuleId())) {
                c.setHostModuleId(null);
            }
            if (c.getModuleIds().isEmpty()) {
                screen.getReceiverCards().remove(c);
                removedCards.add(c.getId());
            }
        }
        List<String> removedSupplies = new ArrayList<>();
        for (PowerSupplyInstance s : new ArrayList<>(screen.getPowerSupplies())) {
            s.getModuleIds().removeIf(id -> !keep.test(id));
            if (s.getHostModuleId() != null && !s.getModuleIds().contains(s.getHostModuleId())) {
                s.setHostModuleId(null);
            }
            if (s.getModuleIds().isEmpty()) {
                screen.getPowerSupplies().remove(s);
                removedSupplies.add(s.getId());
            }
        }
        return new Lost(removedCards, removedSupplies);
    }

    /** Убирает звенья из ВСЕХ цепочек сцены (цепочка может идти через несколько экранов); цепочки, которые от этого
     *  опустели, удаляются — как при скрытии обычного кабинета. */
    public static void dropFromChains(Scene scene, Collection<String> unitIds) {
        if (scene == null || unitIds.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>(unitIds);
        for (SignalChain ch : new ArrayList<>(scene.getSignalChains())) {
            if (ch.getCabinetInstanceIds().removeAll(ids) && ch.getCabinetInstanceIds().isEmpty()) {
                scene.getSignalChains().remove(ch);
            }
        }
        for (PowerChain ch : new ArrayList<>(scene.getPowerChains())) {
            if (ch.getCabinetInstanceIds().removeAll(ids) && ch.getCabinetInstanceIds().isEmpty()) {
                scene.getPowerChains().remove(ch);
            }
        }
    }

    /** Экран удаляется из сцены: его карты и блоки пропадают вместе с ним, их звенья — из цепочек. */
    public static void onScreenRemoved(Scene scene, Screen screen) {
        List<String> ids = new ArrayList<>();
        for (ReceiverCardInstance c : screen.getReceiverCards()) {
            ids.add(c.getId());
        }
        for (PowerSupplyInstance p : screen.getPowerSupplies()) {
            ids.add(p.getId());
        }
        dropFromChains(scene, ids);
    }

    /**
     * Сколько карт и блоков пострадает, если у типа кабинета {@code cabinetTypeId} снять флаг модуля: «удалены» —
     * те, у которых не останется ни одного модуля; «сократятся» — потеряют часть модулей. Считается по всем
     * проектам до самого снятия — для подтверждения «N карт, M блоков и их цепочки будут удалены».
     */
    public static Impact impactOfClearingModuleFlag(Workspace workspace, String cabinetTypeId) {
        int cardsRemoved = 0;
        int suppliesRemoved = 0;
        int cardsShrunk = 0;
        int suppliesShrunk = 0;
        for (Project p : workspace.getProjects()) {
            for (Scene scene : p.getScenes()) {
                for (Screen s : scene.getScreens()) {
                    if (s.getReceiverCards().isEmpty() && s.getPowerSupplies().isEmpty()) {
                        continue;
                    }
                    ScreenModules after = ScreenModules.ofWithoutFlag(s, workspace, cabinetTypeId);
                    for (ReceiverCardInstance c : s.getReceiverCards()) {
                        int kept = countKept(after, c.getModuleIds());
                        if (kept == 0) {
                            cardsRemoved++;
                        } else if (kept < c.getModuleIds().size()) {
                            cardsShrunk++;
                        }
                    }
                    for (PowerSupplyInstance sp : s.getPowerSupplies()) {
                        int kept = countKept(after, sp.getModuleIds());
                        if (kept == 0) {
                            suppliesRemoved++;
                        } else if (kept < sp.getModuleIds().size()) {
                            suppliesShrunk++;
                        }
                    }
                }
            }
        }
        return new Impact(cardsRemoved, suppliesRemoved, cardsShrunk, suppliesShrunk);
    }

    private static int countKept(ScreenModules sm, List<String> ids) {
        int n = 0;
        for (String id : ids) {
            if (sm.module(id) != null) {
                n++;
            }
        }
        return n;
    }
}
