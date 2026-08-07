package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.ControllerInstance;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-байтовый регрессионный тест на {@link NovaLctScrWriter} — фиксирует РЕАЛЬНЫЙ вывод NovaLCT (не наш
 * собственный, а байты, сохранённые самой NovaLCT и подтверждённые загрузкой обратно в неё
 * пользователем), чтобы рефакторинг {@code writeStandard}/{@code writeStandardCore} (выделение под
 * контроллер-центричный экспорт, см. {@link NovaLctControllerResolver}/{@link NovaLctCombineHelper}) не мог незаметно
 * сломать уже подтверждённый одноэкранный путь. Байты образцов — Base64 двух реальных файлов
 * NovaLCT: {@code t1.scr} (тривиальный 1×1 экран, 0 записей) и {@code scr1.scr} (сложный 4×3 экран,
 * 11 записей, НЕ построчная цепочка — column-major обход и происхождение поля 0x14b были
 * расшифрованы именно на этом образце).
 */
class NovaLctScrWriterTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type128() {
        CabinetType ct = new CabinetType();
        ct.setName("Test 128x128");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        return ct;
    }

    private ControllerType singleCardController() {
        ControllerType ct = new ControllerType();
        ct.setName("Test controller");
        ct.setPortCount(20);
        ct.getCards().add(new SchemaCard("Карта 1", List.of(new CardPort("Ethernet", PortDirection.OUT, 20))));
        return ct;
    }

    @Test
    void matchesRealSample_1x1Trivial(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 1, 1, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        ControllerInstance controller = model.addControllerToScreen(screen, controllerType.getId());

        CabinetInstance origin = screen.cabinetAt(0, 0);
        model.addSignalChain(1, false, List.of(origin.getId()));

        byte[] actual = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        byte[] expected = decode(T1_SCR_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void matchesRealSample_4x3ComplexChain(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType type = model.addCabinetType(type128());
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", type.getId(), 3, 4, 0, 0);
        model.selectScreen(screen);

        ControllerType controllerType = model.addControllerType(singleCardController());
        model.addControllerToScreen(screen, controllerType.getId());

        int[][] seqOrderColRow = {
                {0, 2}, {0, 1}, {0, 0}, {1, 0}, {1, 1}, {1, 2},
                {2, 2}, {2, 1}, {2, 0}, {3, 0}, {3, 1}, {3, 2},
        };
        List<String> ids = new ArrayList<>();
        for (int[] colRow : seqOrderColRow) {
            CabinetInstance cab = screen.cabinetAt(colRow[1], colRow[0]);
            ids.add(cab.getId());
        }
        model.addSignalChain(1, false, ids);

        byte[] actual = NovaLctScrWriter.write(screen, scene, model.getWorkspace());
        byte[] expected = decode(SCR1_SCR_BASE64);
        assertArrayEquals(expected, actual);
    }

    @Test
    void firstCardAndPortSkipBlankSentinelWhenPickingHeaderDefault() {
        // Столбец 0 -- целиком blank (card=0xFF), реальные записи только в столбце 1.
        // Column-major обход посещает столбец 0 первым -- заголовочные card/port
        // (смещения 0x149/0x14a) обязаны взять их из первой РЕАЛЬНОЙ записи (card=3),
        // а не ошибочно унаследовать 0xFF от blank-ячейки.
        Map<NovaLctScrWriter.CellKey, NovaLctScrWriter.Rec> cells = new HashMap<>();
        cells.put(new NovaLctScrWriter.CellKey(0, 1), new NovaLctScrWriter.Rec(1, 0, 255, 0, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 0), new NovaLctScrWriter.Rec(0, 1, 3, 7, 0));
        cells.put(new NovaLctScrWriter.CellKey(1, 1), new NovaLctScrWriter.Rec(1, 1, 3, 7, 1));

        byte[] data = NovaLctScrWriter.writeStandardCore(2, 2, 128, 128, 0, cells);
        assertEquals(3, data[0x149] & 0xff, "Заголовочный Sending Card не должен взять blank-сентинел 0xFF");
        assertEquals(7, data[0x14a] & 0xff, "Заголовочный Ethernet Port не должен взять blank-сентинел");
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static final String T1_SCR_BASE64 =
            "RFNDScsXgAAAAOgAAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA2gRAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAApAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAABGwAAAAEAAAAAAAEAAQAAAAAAAAAAAAAAAACAAIAAAUIAW3sic2kiOjAsIngxIjowLCJ5MSI6MCwieDIi"
            + "OjAsInkyIjowLCJ4MyI6MCwieTMiOjAsIng0IjowLCJ5NCI6MH1d6gPnAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAQABAAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String SCR1_SCR_BASE64 =
            "RFNDSXoogAAAAKMBAACtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6QP8AAEBkAZgBAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADuA1kiAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAXwEAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAB1gAAAAEAAAAAAAQAAwAAAAIAAAAAAAAAAACAAIAAAQAAAQAAAIAAAAABAIAAgAABAAAAAAAAAAEAAAIA"
            + "gACAAAEAAAMAgAAAAAEAAACAAIAAAQAABACAAIAAAQABAIAAgAABAAAFAIAAAAEBAAIAgACAAAEAAAgAAAEAAAIAAACAAIAAAQAA"
            + "BwAAAYAAAgABAIAAgAABAAAGAAABAAECAAIAgACAAAEAAAkAgAEAAAMAAACAAIAAAQAACgCAAYAAAwABAIAAgAABAAALAIABAAED"
            + "AAIAgACAAAFCAFt7InNpIjowLCJ4MSI6MCwieTEiOjAsIngyIjowLCJ5MiI6MCwieDMiOjAsInkzIjowLCJ4NCI6MCwieTQiOjB9"
            + "XeoD5wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAQAB5AAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
}
