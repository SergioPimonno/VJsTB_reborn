package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.ui.WireLabelDialog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Обогащение "голого" номинала линии (см. баг-репорт: связь щит-проходной →
 *  экран показывала только "CEE 16A" вместо "CEE 16A → TrueCON", когда тип был
 *  зафиксирован конкретным гнездом распределения). */
class WireLabelDialogTest {

    @Test
    void appendsAdapterSuffixForSingleHint() {
        assertEquals("CEE 16A → TrueCON",
                WireLabelDialog.withConnectorHint("CEE 16A", Set.of(PowerConnectorType.TRUECON)));
        assertEquals("CEE 16A → PowerCon",
                WireLabelDialog.withConnectorHint("CEE 16A", Set.of(PowerConnectorType.POWERCON)));
    }

    @Test
    void leavesAlreadyCombinedLabelsUntouched() {
        assertEquals("CEE 16A → TrueCON",
                WireLabelDialog.withConnectorHint("CEE 16A → TrueCON", Set.of(PowerConnectorType.POWERCON)));
    }

    @Test
    void leavesAmbiguousOrUnknownHintsUntouched() {
        assertEquals("CEE 16A", WireLabelDialog.withConnectorHint("CEE 16A", Set.of()));
        assertEquals("CEE 16A", WireLabelDialog.withConnectorHint("CEE 16A", Set.of(PowerConnectorType.OTHER)));
        assertEquals("CEE 16A", WireLabelDialog.withConnectorHint("CEE 16A",
                Set.of(PowerConnectorType.POWERCON, PowerConnectorType.TRUECON)));
    }

    @Test
    void lockedSocketOffersBothBareAndAdapterOptions() {
        // Баг-репорт: связь через гнездо "CEE 16A" на проходной, ведущая к экрану с
        // кабинетами TRUEcon, должна ПРЕДЛАГАТЬ "CEE 16A → TrueCON" как выбираемый
        // вариант — не только голый номинал гнезда без права выбора.
        String[] options = WireLabelDialog.lockedOptionsFor("CEE 16A", Set.of(PowerConnectorType.TRUECON),
                List.of(), List.of());
        assertTrue(List.of(options).contains("CEE 16A"));
        assertTrue(List.of(options).contains("CEE 16A → TrueCON"));
    }

    @Test
    void lockedSocketIncludesMatchingLibraryCable() {
        String[] options = WireLabelDialog.lockedOptionsFor("CEE 32A", Set.of(), List.of(),
                List.of("CEE 32A → 6xPowerCon (сплиттер)", "SDI"));
        assertTrue(List.of(options).contains("CEE 32A"));
        assertTrue(List.of(options).contains("CEE 32A → 6xPowerCon (сплиттер)"),
                "Кабель библиотеки, начинающийся с номинала гнезда, должен предлагаться");
        assertTrue(!List.of(options).contains("SDI"), "Не относящийся к номиналу гнезда кабель не должен предлагаться");
    }

    @Test
    void lockedSocketIncludesMatchingLengthProfiles() {
        // Баг-репорт: гнездо "Fiber", в каталоге длин зарегистрированы "Fiber MMF LC"
        // и "Fiber MMF SC" — раньше каталог длин тут вообще не смотрели, доступен был
        // только голый абстрактный "Fiber", хотя конкретные зарегистрированные типы
        // уже есть в библиотеке и по ним считается сплайсовка.
        String[] options = WireLabelDialog.lockedOptionsFor("Fiber", Set.of(),
                List.of("Fiber MMF LC", "Fiber MMF SC", "HDMI"), List.of());
        assertTrue(List.of(options).contains("Fiber"));
        assertTrue(List.of(options).contains("Fiber MMF LC"));
        assertTrue(List.of(options).contains("Fiber MMF SC"));
        assertTrue(!List.of(options).contains("HDMI"), "Не относящийся к номиналу гнезда каталог не должен предлагаться");
    }

    @Test
    void lockedSocketWithAlreadyCombinedTypeStaysAsIs() {
        // Если само гнездо УЖЕ промаркировано с переходником — ничего добавлять не нужно.
        String[] options = WireLabelDialog.lockedOptionsFor("CEE 16A → TrueCON",
                Set.of(PowerConnectorType.POWERCON), List.of(), List.of());
        assertEquals(1, options.length);
        assertEquals("CEE 16A → TrueCON", options[0]);
    }

    @Test
    void lockedSocketFiltersOutLibraryCableForWrongConnector() {
        // Защита от дурака (Task #88): у кабинетов экрана ровно один разъём ввода
        // питания — кабель библиотеки под ДРУГОЙ разъём физически не подойдёт и не
        // должен даже предлагаться, иначе инженер может по ошибке выбрать его.
        String[] options = WireLabelDialog.lockedOptionsFor("CEE 16A", Set.of(PowerConnectorType.TRUECON), List.of(),
                List.of("CEE 16A (мама) → TRUEcon (папа)", "CEE 16A (мама) → PowerCon TRUE1 (папа)"));
        assertTrue(List.of(options).contains("CEE 16A (мама) → TRUEcon (папа)"),
                "Кабель под разъём кабинета (TRUEcon) должен предлагаться");
        assertTrue(!List.of(options).contains("CEE 16A (мама) → PowerCon TRUE1 (папа)"),
                "Кабель под ДРУГОЙ разъём кабинета (PowerCon) не должен предлагаться");
    }

    @Test
    void lockedSocketDoesNotFilterWhenConnectorUnknown() {
        // Пустой набор намёков или PowerConnectorType.OTHER — тип разъёма кабинета не
        // определён/не один из PowerCon/TRUEcon: сузить список нельзя, не зная, что
        // именно несовместимо, поэтому все кабели библиотеки под этот номинал остаются.
        String[] options = WireLabelDialog.lockedOptionsFor("CEE 16A", Set.of(PowerConnectorType.OTHER), List.of(),
                List.of("CEE 16A (мама) → TRUEcon (папа)", "CEE 16A (мама) → PowerCon TRUE1 (папа)"));
        assertTrue(List.of(options).contains("CEE 16A (мама) → TRUEcon (папа)"));
        assertTrue(List.of(options).contains("CEE 16A (мама) → PowerCon TRUE1 (папа)"));
    }
}
