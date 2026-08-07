package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-байтовый регрессионный тест на Complex Screen ({@link NovaLctScrWriter#write}
 * автодиспетчит сюда для неровных экранов) — байты {@code ether1.scr}/{@code ether2.scr},
 * реально сохранённые NovaLCT из экрана 4×6 (нижний ряд кабинетов 128×64, остальные
 * 128×128), отличающихся РОВНО одним Ethernet-портом (Sending Card 1, Port 1 vs Port 2).
 * Формат реверс-инжинирен декомпиляцией (ildasm) реального
 * {@code Nova.LCT.GigabitSystem.HWConfigAccessor.Accessors.CommonInfoAccessor.
 * SoftWareSpaceAnalyser.ScreenInfoToArray1600} — тот же метод строит и Standard, и
 * Complex Screen (оба — разные типы {@code ILEDDisplayInfo}), поэтому обе контрольные
 * суммы и большая часть преамбулы совпадают с уже подтверждённым Standard Screen —
 * см. class-javadoc {@link NovaLctScrWriter#writeComplex}.
 */
class NovaLctComplexScrWriterTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128() {
        CabinetType ct = new CabinetType();
        ct.setName("128x128");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    private CabinetType type128x64() {
        CabinetType ct = new CabinetType();
        ct.setName("128x64");
        ct.setWidthMm(500);
        ct.setHeightMm(250);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(64);
        return ct;
    }

    private ControllerType singleCardController() {
        ControllerType ct = new ControllerType();
        ct.setName("Test controller");
        ct.setPortCount(20);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20))));
        return ct;
    }

    /** Строит общий 4x6 экран (нижний ряд -- 128x64), расключает 24 кабинета в
     *  ТОЧНОМ порядке "Receiving Card" 1..24 из реальной таблицы NovaLCT, отличается
     *  только номер порта ({@code globalPort}: 1 для ether1.scr/Port 1, 2 для
     *  ether2.scr/Port 2). */
    private byte[] buildAndWrite(Path dir, int globalPort) {
        AppModel model = freshModel(dir);
        CabinetType base = model.addCabinetType(type128());
        CabinetType tall64 = model.addCabinetType(type128x64());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("Screen1", base.getId(), 6, 4, 0, 0); // 6 rows, 4 cols
        model.selectScreen(screen);

        // Нижний ряд (row=5, Y=640) -- 128x64, как в реальном образце.
        for (int col = 0; col < 4; col++) {
            CabinetInstance cab = screen.cabinetAt(5, col);
            model.setCabinetTypeOverride(cab.getId(), tall64.getId());
        }

        ControllerType ct = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, ct.getId());

        // (row,col) в точном порядке Receiving Card 1..24 из реальной таблицы --
        // снейк-раскладка: столбец 0 снизу вверх, столбец 1 сверху вниз, и т.д.
        int[][] order = {
                {5, 0}, {4, 0}, {3, 0}, {2, 0}, {1, 0}, {0, 0},
                {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1},
                {5, 2}, {4, 2}, {3, 2}, {2, 2}, {1, 2}, {0, 2},
                {0, 3}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
        };
        List<String> ids = new ArrayList<>();
        for (int[] rc : order) {
            ids.add(screen.cabinetAt(rc[0], rc[1]).getId());
        }
        model.addSignalChain(globalPort, false, ids);

        return NovaLctScrWriter.write(screen, scene, model.getWorkspace());
    }

    @Test
    void matchesRealSample_ether1_port1(@TempDir Path dir) {
        byte[] actual = buildAndWrite(dir, 1);
        assertArrayEquals(decode(ETHER1_SCR_BASE64), actual);
    }

    @Test
    void matchesRealSample_ether2_port2(@TempDir Path dir) {
        byte[] actual = buildAndWrite(dir, 2);
        assertArrayEquals(decode(ETHER2_SCR_BASE64), actual);
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static final String ETHER1_SCR_BASE64 =
            "RFNDSYYrgAAAABMCAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAADuA6UlAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABhgEAAAIAGAAAAAAAAAAA"
            + "AIACAAAAAIAAQAAAAAEAAAAAAgAAAACAAIAAAAACAAAAgAEAAAAAgACAAAAAAwAAAAABAAAAAIAAgAAAAAQAAACAAAAAAACAAIAAAAAFAAAAAA"
            + "AAAAAAgACAAAAABgCAAAAAAAAAAIAAgAAAAAcAgACAAAAAAACAAIAAAAAIAIAAAAEAAAAAgACAAAAACQCAAIABAAAAAIAAgAAAAAoAgAAAAgAA"
            + "AACAAIAAAAALAIAAgAIAAAAAgABAAAAADAAAAYACAAAAAIAAQAAAAA0AAAEAAgAAAACAAIAAAAAOAAABgAEAAAAAgACAAAAADwAAAQABAAAAAI"
            + "AAgAAAABAAAAGAAAAAAACAAIAAAAARAAABAAAAAAAAgACAAAAAEgCAAQAAAAAAAIAAgAAAABMAgAGAAAAAAACAAIAAAAAUAIABAAEAAAAAgACA"
            + "AAAAFQCAAYABAAAAAIAAgAAAABYAgAEAAgAAAACAAIAAAAAXAIABgAIAAAAAgABAAAIAW13qA+cAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAABAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

    private static final String ETHER2_SCR_BASE64 =
            "RFNDSbYrgAAAABMCAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAADuA70lAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABhgEAAAIAGAAAAAABAAAA"
            + "AIACAAAAAIAAQAAAAQEAAAAAAgAAAACAAIAAAAECAAAAgAEAAAAAgACAAAABAwAAAAABAAAAAIAAgAAAAQQAAACAAAAAAACAAIAAAAEFAAAAAA"
            + "AAAAAAgACAAAABBgCAAAAAAAAAAIAAgAAAAQcAgACAAAAAAACAAIAAAAEIAIAAAAEAAAAAgACAAAABCQCAAIABAAAAAIAAgAAAAQoAgAAAAgAA"
            + "AACAAIAAAAELAIAAgAIAAAAAgABAAAABDAAAAYACAAAAAIAAQAAAAQ0AAAEAAgAAAACAAIAAAAEOAAABgAEAAAAAgACAAAABDwAAAQABAAAAAI"
            + "AAgAAAARAAAAGAAAAAAACAAIAAAAERAAABAAAAAAAAgACAAAABEgCAAQAAAAAAAIAAgAAAARMAgAGAAAAAAACAAIAAAAEUAIABAAEAAAAAgACA"
            + "AAABFQCAAYABAAAAAIAAgAAAARYAgAEAAgAAAACAAIAAAAEXAIABgAIAAAAAgABAAAIAW13qA+cAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAABAAEAAeQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
}
