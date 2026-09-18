package com.vjstb.ledscheme.service.schemalayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.PortPlacement;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/schema-ports-rework/PLAN.md, задача T1.3, §2.3 — все перечисленные там
 *  контрольные случаи эвристики (взяты из реального проекта «Бармицва»), плюс
 *  порядок старшинства источников роли и правило «в питании роль всегда POWER». */
class PortRoleResolverTest {

    private static InterfaceRole heuristic(String connectorType, SchemaNodeType nodeType) {
        return PortRoleResolver.resolve(SchemaMode.SIGNAL, nodeType,
                new CardPort(connectorType, PortDirection.OUT, 1), null, List.of());
    }

    @Test
    void barMitzvahControlCases() {
        assertEquals(InterfaceRole.LED_DATA, heuristic("Cat6/RJ45", SchemaNodeType.CONTROLLER), "MCTRL4K");
        assertEquals(InterfaceRole.LED_DATA, heuristic("Fiber", SchemaNodeType.CONVERTER), "CVT4K");
        assertEquals(InterfaceRole.NETWORK, heuristic("Ethernet Cat6", SchemaNodeType.SERVER), "Disguise D3");
        assertEquals(InterfaceRole.NETWORK, heuristic("Ethernet Cat5e", SchemaNodeType.CUSTOM), "PixelHue Q8 (MVR)");
        assertEquals(InterfaceRole.VIDEO, heuristic("Fiber", SchemaNodeType.CUSTOM), "PixelHue Q8 (output card)");
        assertEquals(InterfaceRole.SYNC, heuristic("Genlock (SDI)", SchemaNodeType.CONVERTER), "Blackmagic");
        assertEquals(InterfaceRole.LED_DATA, heuristic("Ethernet", SchemaNodeType.SCREEN), "экран");
        assertEquals(InterfaceRole.OTHER, heuristic("BNC", SchemaNodeType.SERVER), "16xBNC");
    }

    @Test
    void heuristicOrderingPreventsGenlockSdiFalsePositive() {
        // "Genlock (SDI)" содержит подстроку "sdi" — без проверки синхро ПЕРВОЙ уехало бы в VIDEO.
        assertEquals(InterfaceRole.SYNC, heuristic("Genlock (SDI)", SchemaNodeType.CONTROLLER));
        assertEquals(InterfaceRole.VIDEO, heuristic("SDI", SchemaNodeType.CONTROLLER), "голый SDI — видео, как обычно");
    }

    @Test
    void heuristicCoversRemainingCategories() {
        assertEquals(InterfaceRole.AUDIO, heuristic("XLR", SchemaNodeType.SERVER));
        assertEquals(InterfaceRole.CONTROL, heuristic("USB", SchemaNodeType.SERVER));
        assertEquals(InterfaceRole.VIDEO, heuristic("DisplayPort 1.2", SchemaNodeType.SERVER));
        assertEquals(InterfaceRole.VIDEO, heuristic("HDMI 2.0", SchemaNodeType.SERVER));
        assertEquals(InterfaceRole.NETWORK, heuristic("RJ45", SchemaNodeType.SERVER), "не LED-узел — обычная сеть");
    }

    @Test
    void powerModeAlwaysReturnsPowerRegardlessOfOverridesOrLibrary() {
        CardPort port = new CardPort("CEE 32A", PortDirection.OUT, 1);
        port.setRole(InterfaceRole.VIDEO); // намеренно противоречивое значение в библиотеке
        PortPlacement placement = new PortPlacement(port.getId());
        placement.setRoleOverride(InterfaceRole.SYNC);
        assertEquals(InterfaceRole.POWER,
                PortRoleResolver.resolve(SchemaMode.POWER, SchemaNodeType.DISTRO, port, placement, List.of()));
    }

    @Test
    void projectOverrideOutranksEverythingElse() {
        CardPort port = new CardPort("HDMI 2.0", PortDirection.IN, 1);
        port.setRole(InterfaceRole.AUDIO);
        PortPlacement placement = new PortPlacement(port.getId());
        placement.setRoleOverride(InterfaceRole.CONTROL);
        assertEquals(InterfaceRole.CONTROL,
                PortRoleResolver.resolve(SchemaMode.SIGNAL, SchemaNodeType.SERVER, port, placement, List.of()));
    }

    @Test
    void cardPortRoleOutranksLibraryAndHeuristic() {
        CardPort port = new CardPort("Ethernet", PortDirection.OUT, 1);
        port.setRole(InterfaceRole.LED_DATA); // явно помечено в пресете, хотя это не CONTROLLER/CONVERTER/SCREEN
        InterfaceType ethernetType = new InterfaceType("Ethernet", List.of());
        ethernetType.setDefaultRole(InterfaceRole.NETWORK);
        assertEquals(InterfaceRole.LED_DATA,
                PortRoleResolver.resolve(SchemaMode.SIGNAL, SchemaNodeType.SERVER, port, null, List.of(ethernetType)));
    }

    @Test
    void libraryDefaultRoleOutranksHeuristicAndPicksMostSpecificMatch() {
        CardPort port = new CardPort("HDMI 2.1", PortDirection.OUT, 1);
        InterfaceType generic = new InterfaceType("HD", List.of());
        generic.setDefaultRole(InterfaceRole.CONTROL);
        InterfaceType specific = new InterfaceType("HDMI", List.of());
        specific.setDefaultRole(InterfaceRole.LED_DATA); // например, специально помечено для LED-карты
        assertEquals(InterfaceRole.LED_DATA,
                PortRoleResolver.resolve(SchemaMode.SIGNAL, SchemaNodeType.SERVER, port, null,
                        List.of(generic, specific)),
                "самый длинный совпавший префикс библиотеки побеждает, не первый по списку");
    }

    // ---- resolveForLibrary (T5.1) — тот же порядок источников, но без PortPlacement
    //      и часто без конкретного SchemaNodeType (клиентские редакторы библиотеки,
    //      не блок на схеме) ----

    private static InterfaceRole libraryHeuristic(String connectorType, SchemaNodeType nodeTypeOrNull) {
        return PortRoleResolver.resolveForLibrary(new CardPort(connectorType, PortDirection.OUT, 1),
                nodeTypeOrNull, List.of());
    }

    @Test
    void resolveForLibraryMatchesPlanControlCasesWithKnownNodeType() {
        // Контрольные случаи PLAN.md §2.3 — те же, что и у resolve(), но через
        // библиотечный путь входа (CardsConfigDialog и т.п. знают категорию
        // пресета/контроллера, даже когда блок ещё не поставлен на схему).
        assertEquals(InterfaceRole.LED_DATA, libraryHeuristic("Cat6/RJ45", SchemaNodeType.CONTROLLER), "MCTRL4K");
        assertEquals(InterfaceRole.LED_DATA, libraryHeuristic("Fiber", SchemaNodeType.CONVERTER), "CVT4K");
        assertEquals(InterfaceRole.NETWORK, libraryHeuristic("Ethernet Cat6", SchemaNodeType.SERVER), "Disguise D3");
        assertEquals(InterfaceRole.SYNC, libraryHeuristic("Genlock (SDI)", SchemaNodeType.CONVERTER), "Blackmagic");
    }

    @Test
    void resolveForLibraryFallsBackGracefullyWithoutNodeType() {
        // Категория пресета неизвестна вызывающему (null) — эвристика Ethernet/Fiber
        // не может отличить LED-данные от обычной сети, откатывается на "обычный"
        // вариант (см. isLedFacingNode(null) == false), а не падает/бросает.
        assertEquals(InterfaceRole.NETWORK, libraryHeuristic("Ethernet", null));
        assertEquals(InterfaceRole.VIDEO, libraryHeuristic("Fiber", null));
    }

    @Test
    void resolveForLibraryPrefersExplicitPortRoleThenLibraryDefault() {
        CardPort explicit = new CardPort("Ethernet", PortDirection.OUT, 1);
        explicit.setRole(InterfaceRole.LED_DATA);
        assertEquals(InterfaceRole.LED_DATA,
                PortRoleResolver.resolveForLibrary(explicit, SchemaNodeType.SERVER, List.of()),
                "явная роль гнезда побеждает эвристику даже без совпадения с типом узла");

        CardPort fromLibrary = new CardPort("Genlock", PortDirection.OUT, 1);
        InterfaceType genlock = new InterfaceType("Genlock", List.of());
        genlock.setDefaultRole(InterfaceRole.SYNC);
        assertEquals(InterfaceRole.SYNC,
                PortRoleResolver.resolveForLibrary(fromLibrary, null, List.of(genlock)),
                "роль вида интерфейса из библиотеки не требует известного SchemaNodeType");
    }
}
