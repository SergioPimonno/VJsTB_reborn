package com.vjstb.ledscheme.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Баг-репорт: "при прописи экрана и использовании контроллера с картами Н2 в
 *  качестве доступных карт показывается входная карта, а не вторая выходная...
 *  для прописи должны быть доступны только карты с Ethernet портами, причём
 *  output". Воспроизводит точную конфигурацию H-серии Novastar — первая карта
 *  в списке ЧИСТО ВХОДНАЯ (HDMI+DP Input, только IN-порты), затем ДВЕ выходные
 *  Ethernet-карты — и проверяет, что {@link ControllerInstance#sendingCardAt}
 *  (используется {@code ui.PortPickerPanel}/{@code ui.stage.SignalStagePanel}
 *  для подписи "Карта N — <имя>") резолвит ПРАВИЛЬНУЮ карту по индексу ПУЛА
 *  Ethernet-нумерации, а не по сырому индексу в {@code getCards()} — раньше
 *  {@code getCards().get(poolIndex)} на pool 0 попадал в ПЕРВУЮ карту списка
 *  (входную), хотя реальный первый пул — это ВТОРАЯ карта (первая с выходным
 *  Ethernet-портом).
 *
 * <p>2026-09-23: перенесено с бывшего {@code ControllerType} (теперь
 * {@code @Deprecated}, используется только для миграции старых сохранений) на
 * {@link ControllerInstance} — та же логика нумерации карт/портов, но теперь
 * на замороженной комплектации экземпляра (см. её class-javadoc), а не на
 * библиотечном типе. */
class ControllerInstanceEthernetPoolTest {

    private ControllerInstance h2WithInputCardFirst() {
        ControllerInstance ci = new ControllerInstance();
        List<SchemaCard> cards = new ArrayList<>();
        cards.add(new SchemaCard("HDMI+DP Input card",
                List.of(new CardPort("HDMI", PortDirection.IN, 1), new CardPort("DP", PortDirection.IN, 1))));
        cards.add(new SchemaCard("Ethernet+Fiber Output card",
                List.of(new CardPort("Ethernet", PortDirection.OUT, 16))));
        cards.add(new SchemaCard("Ethernet+Fiber Output Card 2",
                List.of(new CardPort("Ethernet", PortDirection.OUT, 16))));
        ci.setCards(cards);
        return ci;
    }

    @Test
    void inputOnlyCardIsExcludedFromEthernetPoolCount() {
        ControllerInstance ci = h2WithInputCardFirst();
        // Три карты в getCards(), но только ДВЕ имеют выходной Ethernet -- пул
        // нумерации должен считать только их, не все три.
        assertEquals(2, ci.ethernetPoolCount());
    }

    @Test
    void sendingCardAtSkipsTheInputOnlyCardNotJustRawIndex() {
        ControllerInstance ci = h2WithInputCardFirst();

        // pool 0 -- ПЕРВАЯ карта С ETHERNET-ВЫХОДОМ, то есть ВТОРАЯ карта в сыром
        // списке (индекс 1), а НЕ cards.get(0) (входная карта) -- это и был баг.
        assertEquals("Ethernet+Fiber Output card", ci.sendingCardAt(0).getName());
        assertEquals("Ethernet+Fiber Output Card 2", ci.sendingCardAt(1).getName());
        assertNull(ci.sendingCardAt(2), "пулов только два -- индекс 2 не существует");
    }

    @Test
    void ethernetPortCountInPoolMatchesTheCorrectCard() {
        ControllerInstance ci = h2WithInputCardFirst();
        assertEquals(16, ci.ethernetPortCountInPool(0));
        assertEquals(16, ci.ethernetPortCountInPool(1));
    }

    @Test
    void inputCardPortsDoNotParticipateInOutputPortNumberingAtAll() {
        ControllerInstance ci = h2WithInputCardFirst();
        // effectivePortCount()/isEffectivePortEthernet нумеруют ТОЛЬКО выходные
        // порты (effectivePortCount суммирует totalOutputs() по картам) -- входные
        // порты не занимают номера в этой нумерации вообще, а не просто помечены
        // "не Ethernet": "локальный порт 1" здесь -- это первый порт ПЕРВОЙ карты
        // с выходом (32 порта = 16+16 двух выходных карт, входная карта с её
        // 2 портами в этот счёт не входит совсем).
        assertEquals(32, ci.effectivePortCount());
        for (int i = 1; i <= 32; i++) {
            assertEquals(true, ci.isEffectivePortEthernet(i), "порт " + i + " должен быть Ethernet");
        }
    }
}
