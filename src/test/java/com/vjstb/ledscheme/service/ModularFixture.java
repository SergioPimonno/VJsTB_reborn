package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.GridRect;
import com.vjstb.ledscheme.model.PowerSupplyInstance;
import com.vjstb.ledscheme.model.PowerSupplyType;
import com.vjstb.ledscheme.model.ReceiverCardInstance;
import com.vjstb.ledscheme.model.ReceiverCardType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.util.ArrayList;
import java.util.List;

/**
 * Общая заготовка тестов модульных экранов: рабочее пространство без AppModel (карты и блоки проверяются как чистая
 * логика, без диска и без UI), экран {@code rows × cols} из МОДУЛЕЙ 250×250 мм, 64×64 пикселя, 30 Вт. Шаг 250 мм
 * выбран, чтобы расстояния были круглыми: соседи — 250, диагональ — 353,6, через один — 500.
 */
final class ModularFixture {

    static final double STEP_MM = 250;

    final Workspace ws = new Workspace();
    final CabinetType module = new CabinetType();
    final CabinetType ordinary = new CabinetType();
    final Screen screen = new Screen();

    ModularFixture(int rows, int cols) {
        module.setName("Модуль");
        module.setWidthMm(STEP_MM);
        module.setHeightMm(STEP_MM);
        module.setResolutionWidth(64);
        module.setResolutionHeight(64);
        module.setPowerConsumptionW(30);
        module.setReceiverCardSeparate(true);
        ordinary.setName("Кабинет");
        ordinary.setWidthMm(STEP_MM);
        ordinary.setHeightMm(STEP_MM);
        ordinary.setResolutionWidth(128);
        ordinary.setResolutionHeight(128);
        ordinary.setPowerConsumptionW(100);
        ws.getCabinetTypes().add(module);
        ws.getCabinetTypes().add(ordinary);
        screen.setName("Экран");
        screen.setCabinetTypeId(module.getId());
        screen.setRows(rows);
        screen.setCols(cols);
        ScreenLogic.buildGrid(screen);
    }

    CabinetInstance cab(int row, int col) {
        return screen.cabinetAt(row, col);
    }

    String id(int row, int col) {
        return cab(row, col).getId();
    }

    /** Делает ячейку обычным (не модульным) кабинетом смешанного экрана. */
    void makeOrdinary(int row, int col) {
        cab(row, col).setCabinetTypeId(ordinary.getId());
    }

    static GridRect rect(int r0, int c0, int r1, int c1) {
        return new GridRect(r0, c0, r1, c1);
    }

    ReceiverCardType cardType(String name, Integer outputs, Double ribbonMm) {
        ReceiverCardType t = new ReceiverCardType();
        t.setName(name);
        t.setModuleOutputs(outputs);
        t.setRibbonLengthMm(ribbonMm);
        ws.getReceiverCardTypes().add(t);
        return t;
    }

    PowerSupplyType psuType(String name, Integer outputs, Double cableMm, boolean cardOutput) {
        PowerSupplyType t = new PowerSupplyType();
        t.setName(name);
        t.setOutputs(outputs);
        t.setCableLengthMm(cableMm);
        t.setCardOutput(cardOutput);
        t.setRatedPowerW(300.0);
        ws.getPowerSupplyTypes().add(t);
        return t;
    }

    ReceiverCardInstance cardAt(int row, int col) {
        String id = id(row, col);
        for (ReceiverCardInstance c : screen.getReceiverCards()) {
            if (c.getModuleIds().contains(id)) {
                return c;
            }
        }
        return null;
    }

    PowerSupplyInstance supplyAt(int row, int col) {
        String id = id(row, col);
        for (PowerSupplyInstance s : screen.getPowerSupplies()) {
            if (s.getModuleIds().contains(id)) {
                return s;
            }
        }
        return null;
    }

    /** Карта, собранная прямо в данных (минуя редактор): модули — прямоугольник, в порядке чтения. */
    ReceiverCardInstance addCard(String typeId, int r0, int c0, int r1, int c1) {
        List<String> ids = new ArrayList<>();
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                ids.add(id(r, c));
            }
        }
        ReceiverCardInstance card = new ReceiverCardInstance(typeId, ids);
        screen.getReceiverCards().add(card);
        return card;
    }

    PowerSupplyInstance addSupply(String typeId, int r0, int c0, int r1, int c1) {
        List<String> ids = new ArrayList<>();
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                ids.add(id(r, c));
            }
        }
        PowerSupplyInstance s = new PowerSupplyInstance(typeId, ids);
        screen.getPowerSupplies().add(s);
        return s;
    }
}
