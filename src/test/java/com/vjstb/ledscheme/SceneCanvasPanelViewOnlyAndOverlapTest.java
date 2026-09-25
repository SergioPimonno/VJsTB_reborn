package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.ScreenLogic;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.SceneCanvasPanel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Две правки корнер-превью/наложения кабинетов:
 *  (1) в режиме «только навигация» ({@link SceneCanvasPanel#setViewOnly}) кабинеты
 *  НЕЛЬЗЯ таскать (баг-репорт: «схватить и перетащить кабинеты — категорически
 *  недопустимо»), колесо зумит, протяжка двигает вид;
 *  (2) наложение кабинета на соседей больше не скрывает соседей — «стоящие на месте»
 *  подсвечиваются красным ({@link ScreenLogic#overlappedCabinetIds}). */
class SceneCanvasPanelViewOnlyAndOverlapTest {

    private AppModel model;
    private SettingsManager settings;
    private CabinetType type;
    private Screen screen;

    private void setUp(Path dir, int cols) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        type = new CabinetType();
        type.setName("500x500");
        type.setWidthMm(500);
        type.setHeightMm(500);
        model.addCabinetType(type);
        screen = model.addScreen("E", type.getId(), 1, cols, 0, 0);
        model.selectScreen(screen);
    }

    private void mouse(SceneCanvasPanel panel, int id, int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void movingACabinetOverNeighboursDoesNotHideThem(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetInstance moved = screen.getCabinets().get(0);
        CabinetInstance neighbour = screen.getCabinets().get(1);

        model.updateCabinetOffset(moved, 300.0, 0.0);

        assertFalse(neighbour.isHidden(), "наложение не должно отключать лежащий под кабинетом");
        assertFalse(moved.isHidden());
    }

    @Test
    void overlappedInPlaceCabinetsAreReportedButDisplacedOnesAreNot(@TempDir Path dir) {
        setUp(dir, 3);
        CabinetInstance moved = screen.getCabinets().get(0);
        CabinetInstance neighbour = screen.getCabinets().get(1);
        CabinetInstance far = screen.getCabinets().get(2);

        assertTrue(ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()).isEmpty(),
                "ровная сетка — ничего не наложено");

        moved.setOffsetXMm(300);
        Set<String> covered = ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace());
        assertEquals(Set.of(neighbour.getId()), covered,
                "красным помечается только сосед на своём месте, не сам сдвинутый и не дальний");
        assertFalse(covered.contains(far.getId()));

        neighbour.setHidden(true);
        assertTrue(ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()).isEmpty(),
                "скрытый кабинет не считается накрытым");
    }

    @Test
    void overlappedNeighbourIsPaintedRed(@TempDir Path dir) {
        setUp(dir, 2);
        // Сдвигаем кабинет 0 на 250 мм вправо — он наезжает на кабинет 1 (500..1000 мм).
        model.updateCabinetOffset(screen.getCabinets().get(0), 250.0, 0.0);

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false);
        BufferedImage img = canvas.renderImage(400, 300);

        // Сетка с (40,40), 0.25 px/мм: кабинет 1 — x 165..290; кабинет 0 сдвинут и
        // занимает 102..227 px. Точка x=260 — только сосед, вне сдвинутого кабинета.
        int rgb = img.getRGB(260, 100);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        assertTrue(r - g > 80, "накрытый кабинет на своём месте должен быть красным, rgb=" + Integer.toHexString(rgb));

        // Точка только внутри сдвинутого кабинета (x=110) красной быть не должна.
        int own = img.getRGB(110, 100);
        assertTrue(((own >> 16) & 0xFF) - ((own >> 8) & 0xFF) < 40,
                "сам сдвинутый кабинет красным не подсвечивается");
    }

    @Test
    void redHighlightIsDrawnAboveTheTypeOverrideFill(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetType other = new CabinetType();
        other.setName("other 500x500");
        other.setWidthMm(500);
        other.setHeightMm(500);
        model.addCabinetType(other);
        // Накрываемому кабинету назначен другой тип — он получает цветную заливку типа.
        model.setCabinetTypeOverride(screen.getCabinets().get(1).getId(), other.getId());
        model.updateCabinetOffset(screen.getCabinets().get(0), 250.0, 0.0);

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false);
        BufferedImage img = canvas.renderImage(400, 300);

        int rgb = img.getRGB(260, 100); // только накрытый кабинет 1
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        assertTrue(r - g > 60, "красное должно лежать поверх заливки типа, rgb=" + Integer.toHexString(rgb));
    }

    @Test
    void wiringCanvasAlsoPaintsOverlappedNeighbourRed(@TempDir Path dir) {
        setUp(dir, 2);
        model.updateCabinetOffset(screen.getCabinets().get(0), 250.0, 0.0);

        com.vjstb.ledscheme.ui.CanvasPanel canvas = new com.vjstb.ledscheme.ui.CanvasPanel(model, settings,
                new com.vjstb.ledscheme.ui.CanvasPanel.Controller() {
                    public boolean isChainBuilding() { return false; }
                    public java.util.List<String> activeChainCabIds() { return java.util.List.of(); }
                    public int cursorRow() { return -1; }
                    public int cursorCol() { return -1; }
                    public void cabinetClicked(String cabId) { }
                    public void cabinetHovered(String cabId) { }
                    public void removeFromActive(String cabId) { }
                });
        canvas.setSize(400, 250);
        BufferedImage img = paintToImage(canvas);

        // Ячейка 84px (BASE), отступ 30: кабинет 1 — x 114..198, сдвинутый кабинет 0 —
        // 72..156 (250 мм = полячейки = 42px). Точка x=185 — только сосед на своём месте.
        int rgb = img.getRGB(185, 60);
        assertTrue(((rgb >> 16) & 0xFF) - ((rgb >> 8) & 0xFF) > 80,
                "накрытый кабинет должен быть красным и на холсте расключения, rgb=" + Integer.toHexString(rgb));
    }

    @Test
    void complementaryTrianglesAreNotAnOverlapButIdenticalOnesAre(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetInstance a = screen.getCabinets().get(0);
        CabinetInstance b = screen.getCabinets().get(1);
        // Два прямоугольных треугольника, сложенные в один квадрат — штатная раскладка
        // из баг-репорта, габаритные прямоугольники совпадают, но формы не пересекаются.
        a.setShapeOverride(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        a.setRotationOverride(0);
        b.setShapeOverride(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        b.setRotationOverride(180);
        b.setOffsetXMm(-500);
        assertTrue(ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()).isEmpty(),
                "комплементарные треугольники — не наложение");

        b.setRotationOverride(0); // теперь та же фигура в том же месте
        assertEquals(Set.of(a.getId()), ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()),
                "совпадающие фигуры — наложение, красным помечается тот, что стоит на месте");
    }

    @Test
    void twoDisplacedCabinetsOverlappingMarkTheOneUnderneath(@TempDir Path dir) {
        setUp(dir, 3);
        CabinetInstance a = screen.getCabinets().get(0);
        CabinetInstance b = screen.getCabinets().get(2);
        // Оба вытащены за свои ячейки в общую область над ячейкой 1.
        a.setOffsetXMm(400);
        b.setOffsetXMm(-400);
        assertEquals(Set.of(screen.getCabinets().get(1).getId(), a.getId()),
                ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()),
                "средний накрыт обоими, из двух вытащенных красным — нижний (раньше в списке)");
    }

    @Test
    void touchingEdgesAreNotAnOverlap(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetInstance a = screen.getCabinets().get(0);
        a.setOffsetYMm(-500); // над экраном, встык по горизонтали не пересекается ни с кем
        assertTrue(ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()).isEmpty());
        a.setOffsetYMm(-499.8); // округление привязки, в допуске
        a.setOffsetXMm(0);
        assertTrue(ScreenLogic.overlappedCabinetIds(screen, type, model.getWorkspace()).isEmpty());
    }

    @Test
    void shiftSnapUsesActualSizeOfNarrowNeighbour(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetType narrow = new CabinetType();
        narrow.setName("250x500");
        narrow.setWidthMm(250);
        narrow.setHeightMm(500);
        model.addCabinetType(narrow);
        CabinetInstance left = screen.getCabinets().get(0);
        CabinetInstance right = screen.getCabinets().get(1);
        model.setCabinetTypeOverride(left.getId(), narrow.getId()); // занимает 0..250 мм

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false);
        canvas.setSize(400, 300);

        // Правый кабинет (500..1000 мм = x 165..290 px) тянем влево на 240 мм (60 px):
        // его левый край оказывается на 260 мм, в 10 мм от ФАКТИЧЕСКОГО правого края
        // узкого соседа (250 мм) — привязка обязана его поймать. Со старым расчётом
        // от номинальной ширины (500) целей рядом не было.
        int mods = java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                mods, 200, 80, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                mods, 140, 80, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                mods, 140, 80, 1, false, MouseEvent.BUTTON1));

        assertEquals(-250.0, right.getOffsetXMm(), 1.0,
                "левый край должен прилипнуть к фактическому правому краю узкого соседа (250 мм)");
    }

    @Test
    void hitTestPicksTopmostAndRespectsTriangleShape(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetInstance a = screen.getCabinets().get(0);
        CabinetInstance b = screen.getCabinets().get(1);
        // b накладывается на a: обе ячейки в одном месте, b позже в списке (сверху).
        b.setOffsetXMm(-500);
        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false);
        canvas.setSize(400, 300);
        // Ячейка 0: x 40..165, y 40..165.
        Object[] hit = canvas.screenAndCabinetAtForTest(100, 100);
        assertEquals(b, hit[1], "при наложении хватается верхний (нарисованный позже) кабинет");

        // Верхний — треугольник с прямым углом слева снизу (0°): правый верхний угол
        // ячейки пуст, там должен находиться нижний прямоугольный кабинет.
        b.setShapeOverride(com.vjstb.ledscheme.model.CabinetShape.TRIANGLE);
        b.setRotationOverride(0);
        assertEquals(a, canvas.screenAndCabinetAtForTest(155, 45)[1],
                "клик по пустой половине треугольника уходит кабинету под ним");
        assertEquals(b, canvas.screenAndCabinetAtForTest(50, 150)[1],
                "клик внутри самого треугольника по-прежнему хватает его");
    }

    @Test
    void viewOnlyPanelNeverMovesCabinetsOrScreens(@TempDir Path dir) {
        setUp(dir, 2);
        CabinetInstance cab = screen.getCabinets().get(0);
        double screenX = screen.getPosXMm();
        double screenY = screen.getPosYMm();

        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false, true);
        canvas.setViewOnly(true);
        canvas.setSize(300, 200);

        mouse(canvas, MouseEvent.MOUSE_PRESSED, 60, 60);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 90, 80);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 140, 110);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, 140, 110);

        assertEquals(0.0, cab.getOffsetXMm(), 1e-9, "в превью кабинет таскать нельзя");
        assertEquals(0.0, cab.getOffsetYMm(), 1e-9);
        assertEquals(screenX, screen.getPosXMm(), 1e-9, "и экран целиком — тоже");
        assertEquals(screenY, screen.getPosYMm(), 1e-9);
    }

    @Test
    void viewOnlyWheelZoomsInAndDoubleClickResets(@TempDir Path dir) {
        setUp(dir, 4);
        SceneCanvasPanel canvas = new SceneCanvasPanel(model, settings);
        canvas.setDetailMode(true, false, true);
        canvas.setViewOnly(true);
        canvas.setSize(300, 200);

        BufferedImage before = canvas.renderImage(300, 200);
        // Ctrl не нужен — колесо в этом виджете всегда масштабирует.
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                40, 40, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -3));
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                40, 40, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -3));
        BufferedImage zoomed = paintToImage(canvas);
        assertFalse(sameImage(before, zoomed), "после прокрутки колеса вид должен измениться (зум)");

        // Двойной клик без протяжки — вернуться ко всей сцене.
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                40, 40, 2, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                40, 40, 2, false, MouseEvent.BUTTON1));
        assertTrue(sameImage(before, paintToImage(canvas)), "двойной клик должен вернуть вид «вся сцена»");
    }

    /** Реальная перерисовка компонента (с трансформацией вида), а не {@code renderImage}. */
    private static BufferedImage paintToImage(javax.swing.JComponent canvas) {
        BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        canvas.paint(g);
        g.dispose();
        return img;
    }

    private static boolean sameImage(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
