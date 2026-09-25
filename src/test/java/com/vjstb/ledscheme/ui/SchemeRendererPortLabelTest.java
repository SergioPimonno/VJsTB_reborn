package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Баг-репорт #1: "почему на расключении нумерация порта не совпадает с выбранным
 *  портом в контроллере? в общей схеме в блоке экрана тоже отображается P3" —
 *  {@link SchemeRenderer#portLabel} показывал СЫРОЙ сквозной номер порта (считает
 *  подряд ВСЕ выходные порты карты, включая fiber-группы), расходящийся с "Портом
 *  К1·N", который сайдбар {@code SignalStagePanel.portDisplayLabel} строит через
 *  {@link ControllerInstance#ethernetPoolLocalPort}. Воспроизводит ТОЧНУЮ конфигурацию
 *  H2 с реального сервера (см. curl в этой сессии): вторая карта ("Ethernet+Fiber
 *  Output card") — 2×Fiber, ЗАТЕМ 16×Ethernet — сырой порт 3 (первый Ethernet-порт
 *  этой карты, после двух fiber) должен показываться в пуле карты, а не сырым "3".
 *
 * <p>Баг-репорт #2 (после первого фикса, "всё ещё не исправлено"): контроллеры
 * общие для ВСЕЙ СЦЕНЫ (физически хранятся под конкретным экраном, но пул общий —
 * см. {@code AppModel.controllersInScene}), а {@link SchemeRenderer#portLabel} до
 * этого фикса искал владельца порта только среди {@code scr.getControllers()} —
 * контроллер, добавленный, пока был выбран ДРУГОЙ экран сцены, вообще не находился
 * (полный откат на сырой номер, БЕЗ какого-либо резолва пула/карты — именно это и
 * воспроизводил живой баг-репорт, не просто "дырки от fiber"). Поэтому
 * {@code portLabel} теперь принимает {@code sceneControllers} (готовый список,
 * как возвращает {@code AppModel.controllersInScene}) вместо {@code Screen} —
 * см. {@link #resolvesControllerStoredOnAnotherScreenOfTheSameScene()}.
 *
 * <p>2026-09-23: библиотека контроллеров слита в {@code EquipmentPreset}
 * (category == CONTROLLER) — {@link ControllerInstance} теперь САМ владеет
 * замороженной копией карт/portCount (см. её class-javadoc) вместо живого резолва
 * через {@code Workspace.controllerTypeById}, поэтому фикстуры ниже выставляют
 * карты/portCount НАПРЯМУЮ на экземпляр, а не через отдельный библиотечный тип —
 * {@code Workspace} в подписи {@link SchemeRenderer#portLabel} остаётся (см. её
 * сигнатуру), но резолв владельца порта больше не читает его. */
class SchemeRendererPortLabelTest {

    /** Тот же H2 (3 карты: входная HDMI+DP, две выходные Ethernet+Fiber), но карты
     *  сразу заморожены на экземпляре — см. class-javadoc выше. */
    private ControllerInstance h2Instance(String label) {
        ControllerInstance ci = new ControllerInstance(null, label);
        List<SchemaCard> cards = new ArrayList<>();
        cards.add(new SchemaCard("HDMI+DP Input card",
                List.of(new CardPort("HDMI 2.0", PortDirection.IN, 1),
                        new CardPort("DisplayPort 1.2", PortDirection.IN, 1))));
        cards.add(new SchemaCard("Ethernet+Fiber Output card",
                List.of(new CardPort("Fiber MMF LC", PortDirection.OUT, 2),
                        new CardPort("Ethernet Cat5e", PortDirection.OUT, 16))));
        cards.add(new SchemaCard("Ethernet+Fiber Output Card",
                List.of(new CardPort("Fiber MMF LC", PortDirection.OUT, 2),
                        new CardPort("Ethernet Cat5e", PortDirection.OUT, 16))));
        ci.setCards(cards);
        return ci;
    }

    @Test
    void resolvesFirstEthernetPortOfFirstOutputCardAsOneNotRawThree() {
        ControllerInstance ci = h2Instance("H2 #1");
        List<ControllerInstance> sceneControllers = List.of(ci);

        // Сырой глобальный порт 3 -- первый Ethernet-порт первой выходной карты
        // (порты 1-2 той же карты -- Fiber, не входят ни в один Ethernet-пул). У H2
        // ДВЕ выходные карты -- пулов больше одного, значит формат с префиксом карты
        // ("К1·1"), тот же, что и в сайдбаре (SignalStagePanel.portDisplayLabel).
        String label = SchemeRenderer.portLabel(sceneControllers, new Workspace(), 3);

        assertEquals("PК1·1", label, "должен показывать номер В ПРЕДЕЛАХ Ethernet-пула карты, не сырой номер 3");
    }

    @Test
    void resolvesFirstEthernetPortOfSecondOutputCardWithCardPrefix() {
        ControllerInstance ci = h2Instance("H2 #1");
        List<ControllerInstance> sceneControllers = List.of(ci);

        // Вторая выходная карта: raw 1-18 -- первая карта (2 fiber + 16 ethernet),
        // raw 19-20 -- fiber второй карты, raw 21 -- первый Ethernet-порт ВТОРОЙ
        // карты -- должен показать "К2·1", т.к. пулов у H2 два.
        String label = SchemeRenderer.portLabel(sceneControllers, new Workspace(), 21);

        assertEquals("PК2·1", label);
    }

    @Test
    void singlePoolControllerShowsPlainPortNumberWithoutCardPrefix() {
        ControllerInstance ci = new ControllerInstance(null, "Simple #1");
        ci.setPortCount(8); // без карт -- нечем фильтровать, старое поведение

        assertEquals("P5", SchemeRenderer.portLabel(List.of(ci), new Workspace(), 5));
    }

    @Test
    void resolvesControllerStoredOnAnotherScreenOfTheSameScene() {
        // Воспроизводит РЕАЛЬНЫЙ баг-репорт "всё ещё не исправлено": контроллер
        // физически хранится под ОДНИМ экраном сцены, а мы подписываем цепочку на
        // ДРУГОМ экране той же сцены (обычный случай -- см. javadoc AppModel
        // .controllersInScene: "используются как ОБЩИЙ для сцены пул"). portLabel
        // получает готовый sceneControllers -- КАК ЕСЛИ БЫ его построил
        // AppModel.controllersInScene(scene) -- и обязан найти владельца порта по
        // нему, а не молча откатываться на сырой номер, как раньше при поиске
        // только среди контроллеров ОДНОГО экрана.
        ControllerInstance ci = h2Instance("H2 #1");
        List<ControllerInstance> sceneControllers = List.of(ci);

        String label = SchemeRenderer.portLabel(sceneControllers, new Workspace(), 3);

        assertEquals("PК1·1", label,
                "должен найти контроллер по ВСЕЙ сцене, а не только среди контроллеров текущего экрана");
    }

    @Test
    void multiControllerSceneShowsControllerPrefixByIndexInSceneWideList() {
        // Второй контроллер сцены -- offset должен считаться по СУММЕ портов первого
        // (не просто "он один на этом экране, значит offset=0"). Индекс "C{n}" тоже
        // считается по sceneControllers (как AppModel.portOffsetOf/portDisplayLabel),
        // а не по позиции внутри Screen.getControllers() конкретного экрана.
        ControllerInstance first = new ControllerInstance(null, "Simple #1");
        first.setPortCount(4);
        ControllerInstance second = new ControllerInstance(null, "Simple #2");
        second.setPortCount(4);
        List<ControllerInstance> sceneControllers = List.of(first, second);

        // Порт 6 = второй порт ВТОРОГО контроллера (4 порта первого + 2).
        assertEquals("C2·P2", SchemeRenderer.portLabel(sceneControllers, new Workspace(), 6));
    }
}
