package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Разведка T6.2 (docs/schema-ports-rework/PLAN.md, реплика пользователя 2026-09-18,
 *  DIALOG.md): пользователь спросил, реализовано ли слияние нескольких связей в одно
 *  свёрнутое гнездо ("пучок", D8/T4.3 {@code EdgeBundles}) — если да, отдельный блок-
 *  «шина» (Ring) не нужен, это ровно тот же визуальный эффект. Проверка на самом
 *  простом случае: 3 связи в одну ПРИНУДИТЕЛЬНО свёрнутую группу разъёмов питания
 *  (3×CEE 16A) — до фикса все три должны сходиться в ОДНУ точку (это уже верно, т.к.
 *  свёрнутая группа — один пин), но БЕЗ подписи "×3" и без короткого общего ствола,
 *  т.к. {@link com.vjstb.ledscheme.service.schemalayout.EdgeBundles} нигде не
 *  вызывается из {@code SchemaCanvasPanel} (T4.3 сделан, T4.4 не подключил). */
class EdgeBundleRenderTest {

    @Test
    void threeEdgesIntoOneForciblyCollapsedGroupShareOnePinAndGetABundleLabel(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Шина", 400, 200, null);
        CardPort in = model.addPowerConnectorToNode(distro, "CEE 16A", PortDirection.IN, 3, 1, null);
        model.setGroupCollapsed(distro, in.getId(), Boolean.TRUE);

        SchemaNode s1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort o1 = model.addPowerConnectorToNode(s1, "CEE 16A", PortDirection.OUT, 1, 1, null);
        SchemaNode s2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "B", 0, 200, null);
        CardPort o2 = model.addPowerConnectorToNode(s2, "CEE 16A", PortDirection.OUT, 1, 1, null);
        SchemaNode s3 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "C", 0, 400, null);
        CardPort o3 = model.addPowerConnectorToNode(s3, "CEE 16A", PortDirection.OUT, 1, 1, null);

        model.addSchemaEdge(SchemaMode.POWER, s1.getId(), o1.getId(), distro.getId(), in.getId(), null);
        model.addSchemaEdge(SchemaMode.POWER, s2.getId(), o2.getId(), distro.getId(), in.getId(), null);
        SchemaEdge third = model.addSchemaEdge(SchemaMode.POWER, s3.getId(), o3.getId(), distro.getId(), in.getId(), null);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);

        Point p1 = canvas.socketPositionForTest(distro, in.getId(), model.getCurrentScene().getSchemaEdges().get(0));
        Point p3 = canvas.socketPositionForTest(distro, in.getId(), third);
        assertEquals(p1, p3, "свёрнутая группа — один пин, все три связи должны указывать в одну и ту же точку");

        int bundleSize = canvas.bundleSizeForTest(distro, in.getId());
        assertEquals(3, bundleSize, "три связи в одно свёрнутое гнездо — пучок из 3, а не 0/1");
    }

    /** Запрос пользователя 2026-09-30: раньше маркер пучка стоял фиксированным
     *  16px-стволом у самого гнезда (см. {@code EdgeBundles.TRUNK_LENGTH}) — теперь
     *  чип общей подписи должен стоять в РЕАЛЬНОЙ точке расхождения маршрутов. Две
     *  связи из ОДНОГО гнезда идут через один и тот же общий излом (250,200),
     *  прежде чем разойтись к разным получателям — чип обязан оказаться заметно
     *  правее самого гнезда (x≈0), у этого излома, а не в 16px от него. */
    @Test
    void bundleChipSitsAtTheRealDivergencePointNotAtAFixedStub(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 3, 1, null);
        model.setGroupCollapsed(source, out.getId(), Boolean.TRUE);

        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "A", 500, 0, null);
        CardPort in1 = model.addPowerConnectorToNode(t1, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 400, null);
        CardPort in2 = model.addPowerConnectorToNode(t2, "CEE 32A", PortDirection.IN, 1, 1, null);

        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t1.getId(), in1.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t2.getId(), in2.getId(), null);
        model.setSchemaEdgeWaypoints(e1, List.of(new EdgeWaypoint(250, 200)));
        model.setSchemaEdgeWaypoints(e2, List.of(new EdgeWaypoint(250, 200)));
        // шина включается ЯВНО (запрос 2026-10-01); необъединённый значок «×N» — элемент редактора
        model.setSchemaBusMerged(source, out.getId(), true);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);
        canvas.renderImage(1200, 800, false);

        Rectangle chip = canvas.bundleChipRectForTest(source, out.getId());
        assertTrue(chip != null, "кабели ещё не подписаны — чип общей подписи должен быть доступен");
        assertTrue(chip.x > 150, "чип должен стоять у точки расхождения (250,200), не у фиксированного короткого ствола");
    }

    /** Если связи пучка уже подписаны РАЗНЫМИ типами кабеля, общий чип не
     *  показывается — запрос пользователя: "если разные — подпишет каждый кабель
     *  отдельно", как обычным меню связи (индивидуальные чипы у самих связей) —
     *  но сам маркер под ПКМ остаётся (размер шрифта применим всегда, ортогонален
     *  типу кабеля, см. {@link SchemaCanvasPanel#showBundleChipMenu}), просто без
     *  пункта «Подпись шины…» в его меню. */
    @Test
    void bundleChipOffersOnlyFontSizeWhenCablesAreAlreadyLabeledDifferently(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 1, null);
        model.setGroupCollapsed(source, out.getId(), Boolean.TRUE);

        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "A", 500, 0, null);
        CardPort in1 = model.addPowerConnectorToNode(t1, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 400, null);
        CardPort in2 = model.addPowerConnectorToNode(t2, "CEE 32A", PortDirection.IN, 1, 1, null);

        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t1.getId(), in1.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t2.getId(), in2.getId(), null);
        model.updateSchemaEdgeWire(e1, 1, "CEE 32A · 3ф", null);
        model.updateSchemaEdgeWire(e2, 1, "Schuko", null);
        // объединение вручную невозможно (пункт меню неактивен), но на уровне модели состояние
        // «объединено» допустимо — маркер остаётся, общей подписи у него нет
        model.setSchemaBusMerged(source, out.getId(), true);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);
        canvas.renderImage(1200, 800, false);

        assertTrue(canvas.bundleChipRectForTest(source, out.getId()) != null,
                "маркер пучка остаётся под ПКМ даже с разными типами -- нужен для смены размера шрифта");
        assertFalse(canvas.bundleChipEditableForTest(source, out.getId()),
                "кабели пучка подписаны разными типами -- общий чип подписи не должен предлагаться");
    }

    /** Ядро фичи "подписать шину один раз" (запрос пользователя 2026-09-30):
     *  {@link AppModel#updateSchemaEdgesWireShared} пишет ОДИН И ТОТ ЖЕ тип во ВСЕ
     *  связи пучка, но НЕ навязывает count каждой связи (тот, что уже стоял на
     *  связи, сохраняется — только пустой выставляется в 1). */
    @Test
    void sharedWireUpdateAppliesTypeToAllEdgesWithoutOverwritingExistingCounts(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));

        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "B", 0, 100, null);
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "C", 0, 200, null);
        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, a.getId(), c.getId(), null);
        e2.setWireCount(2);

        model.updateSchemaEdgesWireShared(List.of(e1, e2), "CEE 32A · 3ф", 12.5);

        assertEquals("CEE 32A · 3ф", e1.getWireType());
        assertEquals(1, e1.getWireCount(), "у связи без своего count — проставляется 1, не общее число пучка");
        assertEquals("CEE 32A · 3ф", e2.getWireType());
        assertEquals(2, e2.getWireCount(), "существующий count связи не перезаписывается общей подписью шины");
        assertEquals(12.5, e1.getLengthM());
        assertEquals(12.5, e2.getLengthM());

        model.clearSchemaEdgesWireShared(List.of(e1, e2));
        assertNull(e1.getWireType());
        assertNull(e2.getWireType());
        assertNull(e1.getLabel());
        assertNull(e2.getLabel());
    }

    /** Запрос пользователя 2026-10-01 (заменяет поведение 2026-09-30): подпись шины —
     *  ОПЦИОНАЛЬНАЯ. По умолчанию подписи отдельных линий видны, даже если у всех связей
     *  одинаковый тип (раньше шина включалась сама и прятала подписи, которые инженер
     *  задал отдельным линиям). Объединение в шину — явное действие и прячет подписи линий;
     *  появление подписи у отдельной линии шину выключает. */
    @Test
    void individualLabelsStayVisibleUntilTheBusIsMergedExplicitly(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));

        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 1, null);
        model.setGroupCollapsed(source, out.getId(), Boolean.TRUE);
        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "A", 500, 0, null);
        CardPort in1 = model.addPowerConnectorToNode(t1, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 400, null);
        CardPort in2 = model.addPowerConnectorToNode(t2, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t1.getId(), in1.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t2.getId(), in2.getId(), null);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);

        // две связи одним и тем же типом (сценарий пользователя) — шина НЕ включается сама
        model.updateSchemaEdgesWireShared(List.of(e1, e2), "CEE 32A · 3ф", null);
        assertFalse(source.isBusMerged(out.getId()), "по умолчанию шина выключена");
        assertFalse(canvas.suppressedByBundleLabelForTest(e1),
                "подписи отдельных линий видны, пока шина не объединена");
        assertFalse(canvas.suppressedByBundleLabelForTest(e2));

        model.setSchemaBusMerged(source, out.getId(), true);
        assertTrue(canvas.suppressedByBundleLabelForTest(e1),
                "после «Объединить в шину» подписи линий заменяет подпись шины");
        assertTrue(canvas.suppressedByBundleLabelForTest(e2));

        // подпись у отдельной линии выключает шину этой группы
        model.updateSchemaEdgeWire(e2, 1, "Schuko", null);
        assertFalse(source.isBusMerged(out.getId()), "появление подписи линии выключает шину");
        assertFalse(canvas.suppressedByBundleLabelForTest(e1));
        assertFalse(canvas.suppressedByBundleLabelForTest(e2));
    }

    /** Общая подпись шины ({@code updateSchemaEdgesWireShared}) НЕ считается «подписью
     *  отдельной линии» — шину она не выключает, иначе нельзя было бы подписать шину. */
    @Test
    void editingTheBusLabelKeepsTheBusMerged(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 1, null);
        model.setGroupCollapsed(source, out.getId(), Boolean.TRUE);
        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "A", 500, 0, null);
        CardPort in1 = model.addPowerConnectorToNode(t1, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 400, null);
        CardPort in2 = model.addPowerConnectorToNode(t2, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t1.getId(), in1.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t2.getId(), in2.getId(), null);

        model.setSchemaBusMerged(source, out.getId(), true);
        model.updateSchemaEdgesWireShared(List.of(e1, e2), "CEE 32A · 3ф", 5.0);

        assertTrue(source.isBusMerged(out.getId()), "подпись шины не выключает шину");
    }

    /** Запрос 2026-10-01 («ее тоже я должен мочь двигать»): смещение подписи шины
     *  хранится в проекте, двигает чип, отменяется одной записью, копируется со снимком. */
    @Test
    void busLabelOffsetMovesTheChipAndIsUndoable(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 1, null);
        model.setGroupCollapsed(source, out.getId(), Boolean.TRUE);
        SchemaNode t1 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "A", 500, 0, null);
        CardPort in1 = model.addPowerConnectorToNode(t1, "CEE 32A", PortDirection.IN, 1, 1, null);
        SchemaNode t2 = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "B", 500, 400, null);
        CardPort in2 = model.addPowerConnectorToNode(t2, "CEE 32A", PortDirection.IN, 1, 1, null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t1.getId(), in1.getId(), null);
        model.addSchemaEdge(SchemaMode.POWER, source.getId(), out.getId(), t2.getId(), in2.getId(), null);
        model.setSchemaBusMerged(source, out.getId(), true);

        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.POWER, settings);
        canvas.renderImage(1200, 800, false);
        Rectangle before = canvas.bundleChipRectForTest(source, out.getId());

        model.setSchemaBusLabelOffset(source, out.getId(), 40, -25);
        canvas.renderImage(1200, 800, false);
        Rectangle moved = canvas.bundleChipRectForTest(source, out.getId());
        assertEquals(before.x + 40, moved.x);
        assertEquals(before.y - 25, moved.y);
        assertArrayEquals(new double[]{40, -25}, source.copy().getBusLabelOffsets().get(out.getId()));

        model.undo();
        canvas.renderImage(1200, 800, false);
        assertEquals(before, canvas.bundleChipRectForTest(fresh(model, source), out.getId()),
                "сдвиг подписи шины отменяется одной записью");
    }

    private static SchemaNode fresh(AppModel model, SchemaNode node) {
        return model.getCurrentScene().getSchemaNodes().stream()
                .filter(n -> n.getId().equals(node.getId())).findFirst().orElseThrow();
    }

    /** «Объединить в шину»/«Разделить шину» — по одной записи отмены (снимок возвращает копии
     *  узлов, поэтому проверяем по свежему экземпляру из сцены). */
    @Test
    void mergingAndSplittingTheBusAreSingleUndoSteps(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        SchemaNode source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Источник", 0, 200, null);
        CardPort out = model.addPowerConnectorToNode(source, "CEE 32A", PortDirection.OUT, 2, 1, null);

        model.setSchemaBusMerged(source, out.getId(), true);
        assertTrue(fresh(model, source).isBusMerged(out.getId()));
        model.undo();
        assertFalse(fresh(model, source).isBusMerged(out.getId()), "объединение отменяется одной записью");

        model.setSchemaBusMerged(fresh(model, source), out.getId(), true);
        model.setSchemaBusMerged(fresh(model, source), out.getId(), false);
        assertFalse(fresh(model, source).isBusMerged(out.getId()));
        model.undo();
        assertTrue(fresh(model, source).isBusMerged(out.getId()), "разделение тоже отменяется");
    }

    /** Старый проект (до явной шины): у узла нет полей шины — все шины выключены. */
    @Test
    void oldNodesHaveNoMergedBuses() {
        SchemaNode n = new SchemaNode();
        assertTrue(n.getMergedBusPortIds().isEmpty());
        assertTrue(n.getBusLabelOffsets().isEmpty());
        assertFalse(n.isBusMerged("any"));
    }

    /** Запрос пользователя 2026-09-30: "для плашки шины недоступно изменение
     *  высоты шрифта" — {@link AppModel#setSchemaEdgesFontSize} пишет ОДИН И ТОТ
     *  ЖЕ размер во ВСЕ связи пучка сразу, одна запись отмены. */
    @Test
    void sharedFontSizeUpdateAppliesToAllEdgesOfTheBundle(@TempDir Path dir) {
        AppModel model = new AppModel(new com.vjstb.ledscheme.store.WorkspaceStore(
                new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));

        SchemaNode a = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "B", 0, 100, null);
        SchemaNode c = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "C", 0, 200, null);
        SchemaEdge e1 = model.addSchemaEdge(SchemaMode.POWER, a.getId(), b.getId(), null);
        SchemaEdge e2 = model.addSchemaEdge(SchemaMode.POWER, a.getId(), c.getId(), null);

        model.setSchemaEdgesFontSize(List.of(e1, e2), 16);
        assertEquals(16, e1.getFontSize());
        assertEquals(16, e2.getFontSize());

        model.setSchemaEdgesFontSize(List.of(e1, e2), null);
        assertNull(e1.getFontSize());
        assertNull(e2.getFontSize());
    }
}
