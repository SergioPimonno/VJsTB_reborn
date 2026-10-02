package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Баг-репорт 2026-10-02: ригтех брал ширину экрана «по настройкам» (колонки × размер кабинета
 *  по умолчанию — у Left 2 это 14 × 500 = 7 м), а кабинеты, реально стоящие в экране (часть
 *  левых колонок вырезана и заменена узкими), занимают 6,5 м — именно эта цифра должна
 *  отражаться в длине фермы и в точках подвеса. */
class TrussVisibleSpanTest {

    private static CabinetType type(String name, double w, double weight) {
        CabinetType ct = new CabinetType();
        ct.setName(name);
        ct.setWidthMm(w);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weight);
        return ct;
    }

    private static CabinetInstance cab(Screen s, int row, int col) {
        return s.getCabinets().stream().filter(c -> c.getRowIndex() == row && c.getColIndex() == col)
                .findFirst().orElseThrow();
    }

    @Test
    void trussLengthAndRiggingFollowTheActuallyUsedCabinetsNotTheNominalGrid(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType base = model.addCabinetType(type("MG7s", 500, 10));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen s = model.addScreen("Left 2", base.getId(), 2, 14, 0, 0);

        // номинальная сетка 14 × 500 = 7 м; вырезаем всю самую левую колонку (col 0) и ещё
        // половину следующей (смещение на 250) — видимые кабинеты начинаются с 750, до 7000: 6,25 м
        cab(s, 0, 0).setHidden(true);
        cab(s, 1, 0).setHidden(true);
        cab(s, 0, 1).setOffsetXMm(250);
        cab(s, 1, 1).setOffsetXMm(250);

        TrussCalc.Span span = TrussCalc.visibleSpan(s, base, model.getWorkspace());
        assertEquals(750, span.minX(), 1e-6);
        assertEquals(7000, span.maxX(), 1e-6);
        assertEquals(6250, TrussCalc.screenWidthMm(s, base, model.getWorkspace()), 1e-6,
                "ширина экрана — протяжённость видимых кабинетов, а не колонки × кабинет");
        assertEquals(6250, TrussCalc.suggestTrussLengthMm(s, base, model.getWorkspace()), 1e-6,
                "ферма по умолчанию перекрывает ровно реальную ширину");

        // точки подвеса и ферма: левый край фермы совпадает с левым краем видимых кабинетов (отступ 0),
        // в координатах сетки — это 750 мм правее начала сетки (grid-отступ отрицателен)
        TrussCalc.Result truss = TrussCalc.compute(s, base, model.getWorkspace());
        assertEquals(6250, truss.targetLengthMm(), 1e-6);
        assertEquals(0, truss.leftOffsetMm(), 1e-6);
        assertEquals(-750, truss.gridLeftOffsetMm(), 1e-6);

        RiggingCalc.Result rig = RiggingCalc.compute(s, base, model.getWorkspace(), 2);
        double first = rig.points().get(0).xMm();
        double last = rig.points().get(1).xMm();
        assertTrue(first >= 750 && last <= 7000, "точки подвеса стоят над видимыми кабинетами: " + first + ".." + last);
    }

    @Test
    void screenWithoutCutoutsIsUnchanged(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType base = model.addCabinetType(type("MG7s", 500, 10));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", base.getId(), 2, 14, 0, 0);

        assertEquals(7000, TrussCalc.screenWidthMm(s, base, model.getWorkspace()), 1e-6);
        TrussCalc.Result truss = TrussCalc.compute(s, base, model.getWorkspace());
        assertEquals(truss.leftOffsetMm(), truss.gridLeftOffsetMm(), 1e-9);
        assertEquals(truss.rightOffsetMm(), truss.gridRightOffsetMm(), 1e-9);
    }

    @Test
    void overriddenNarrowCabinetShrinksTheVisibleSpan(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        CabinetType base = model.addCabinetType(type("MG7s", 500, 10));
        CabinetType narrow = model.addCabinetType(type("Narrow", 250, 5));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        Screen s = model.addScreen("E", base.getId(), 1, 4, 0, 0);

        // последний кабинет заменён узким типом: правый край видимых кабинетов — 1500 + 250
        cab(s, 0, 3).setCabinetTypeId(narrow.getId());

        assertEquals(1750, TrussCalc.screenWidthMm(s, base, model.getWorkspace()), 1e-6);
    }
}
