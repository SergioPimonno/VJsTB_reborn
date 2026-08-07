package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.ControllerInstance;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SignalChain;

import java.util.ArrayList;
import java.util.List;

/** Собирает ВСЕ кабинеты сцены (с ЛЮБОГО её экрана — цепочка физически может
 *  проходить через несколько экранов, см. Task #54), расключённые через
 *  КОНКРЕТНЫЙ {@link ControllerInstance} — для контроллер-центричного
 *  NovaLCT-экспорта (в отличие от {@link NovaLctScrWriter#write}, привязанного
 *  к одному {@link Screen}).
 *
 * <p>Намеренно НЕ использует {@link ScreenLogic#cardAndLocalPort} — тот сам
 * ищет контроллер по номеру порта, суммируя ТОЛЬКО {@code screen.getControllers()}
 * ОДНОГО экрана (см. его javadoc), что даёт неверное смещение, если владеющий
 * контроллер физически хранится под ДРУГИМ экраном той же сцены (см.
 * {@link AppModel#controllersInScene} — контроллеры физически по экранам, но
 * пул портов общий для сцены). Здесь контроллер уже ИЗВЕСТЕН заранее, поэтому
 * смещение считается напрямую через {@link AppModel#portOffsetOf(Scene,
 * ControllerInstance)} и {@link ControllerType#ethernetPoolLocalPort(int)} —
 * тот же метод, которым уже пользуется {@code SignalStagePanel} для показа
 * "Карта N" по конкретному контроллеру. */
public final class NovaLctControllerResolver {

    private NovaLctControllerResolver() {
    }

    /** Один кабинет, расключённый через резолвимый контроллер — где бы физически
     *  ни находился (см. {@code sourceScreen}). {@code cardIndex}/{@code portInPool}
     *  0-based — те же конвенции, что и {@link NovaLctScrWriter.Rec#card()}/
     *  {@link NovaLctScrWriter.Rec#port()}. {@code seq} — позиция в СВОЕЙ цепочке
     *  (0-based), как и в {@code Rec}. */
    public record CabinetRec(Screen sourceScreen, int col, int row, int cardIndex, int portInPool, int seq) {
    }

    /** Резолвит все кабинеты сцены, расключённые через {@code controller}. Пустой
     *  список, если контроллер не найден/у сцены нет цепочек на его порты. Скрытые
     *  ({@link CabinetInstance#isHidden()}) кабинеты и записи с {@code portNumber
     *  == null} пропускаются — как и в {@link NovaLctScrWriter#hasUnwiredCabinets}. */
    public static List<CabinetRec> resolve(Scene scene, ControllerInstance controller, AppModel model) {
        List<CabinetRec> result = new ArrayList<>();
        if (scene == null || controller == null || model == null || model.getWorkspace() == null) {
            return result;
        }
        ControllerType type = model.getWorkspace().controllerTypeById(controller.getControllerTypeId());
        if (type == null) {
            return result;
        }
        int offset = model.portOffsetOf(scene, controller);
        int portCount = type.effectivePortCount();

        for (SignalChain chain : scene.getSignalChains()) {
            Integer port = chain.getPortNumber();
            if (port == null || port <= offset || port > offset + portCount) {
                continue; // не задан, или принадлежит другому контроллеру сцены
            }
            int controllerLocal = port - offset;
            int[] pool = type.ethernetPoolLocalPort(controllerLocal);
            if (pool == null) {
                continue; // fiber-порт или вне диапазона -- в расключение NovaLCT не входит
            }
            int cardIndex = pool[0];
            int portInPool = pool[1] - 1; // 0-based, как в NovaLctScrWriter.Rec

            List<String> ids = chain.getCabinetInstanceIds();
            for (int seq = 0; seq < ids.size(); seq++) {
                CabinetLocation loc = findCabinet(scene, ids.get(seq));
                if (loc == null || loc.cabinet.isHidden()) {
                    continue;
                }
                result.add(new CabinetRec(loc.screen, loc.cabinet.getColIndex(), loc.cabinet.getRowIndex(),
                        cardIndex, portInPool, seq));
            }
        }
        return result;
    }

    private record CabinetLocation(Screen screen, CabinetInstance cabinet) {
    }

    private static CabinetLocation findCabinet(Scene scene, String cabinetId) {
        for (Screen s : scene.getScreens()) {
            CabinetInstance c = s.cabinetById(cabinetId);
            if (c != null) {
                return new CabinetLocation(s, c);
            }
        }
        return null;
    }
}
