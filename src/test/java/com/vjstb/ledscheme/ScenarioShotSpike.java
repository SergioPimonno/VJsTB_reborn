package com.vjstb.ledscheme;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.ContentCanvas;
import com.vjstb.ledscheme.model.ControllerType;
import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkDeviceCategory;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.NetworkLink;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.MainFrame;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** SPIKE / генератор ассетов для интерактивных примеров — НЕ регрессия. Строит
 *  настоящий MainFrame с наполненным демо-воркспейсом, проходит по этапам и видам,
 *  рендерит каждый в PNG и вычисляет относительные (0..1) прямоугольники ключевых
 *  кнопок для хотспотов. Удаляется после сборки сценариев. */
class ScenarioShotSpike {

    /** Куда писать PNG + hotspots.json. По умолчанию — tools/interactive-scenarios/shots
     *  в репозитории (git-ignored), можно переопределить: -Dscenario.shots.dir=… */
    private static final File OUT = new File(System.getProperty("scenario.shots.dir",
            "tools/interactive-scenarios/shots"));

    private static final int W = 1500;
    private static final int H = 920;

    @Test
    void renderStages() throws Exception {
        // Не регрессия — генератор ассетов. Обычный прогон пропускает; запуск вручную:
        //   mvn test -Dtest=ScenarioShotSpike -Dscenario.shots=true
        Assumptions.assumeTrue(Boolean.getBoolean("scenario.shots"),
                "включается флагом -Dscenario.shots=true (генератор скринов интерактивных примеров)");
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "нет дисплея");
        deleteRec(OUT);
        OUT.mkdirs();
        File tmp = new File(OUT, "ws");
        tmp.mkdirs();

        MainFrame[] frameRef = new MainFrame[1];
        Map<String, double[]> hotspots = new LinkedHashMap<>();

        SwingUtilities.invokeAndWait(() -> {
            AppModel model = new AppModel(new WorkspaceStore(new File(tmp, "ws.json")));
            buildSampleWorkspace(model);
            SettingsManager settings = new SettingsManager(new SettingsStore(new File(tmp, "settings.json")));
            MainFrame frame = new MainFrame(model, settings);
            frame.setSize(W, H);
            frame.setLocation(40, 30);
            frame.setVisible(true);
            frameRef[0] = frame;
        });
        Thread.sleep(900);

        // хотспоты кнопок этапов (позиция одинакова на всех кадрах) + видов
        SwingUtilities.invokeAndWait(() -> {
            Container cp = frameRef[0].getContentPane();
            for (String t : new String[]{"Сетап", "Питание", "Сигнал", "Генерация масок", "Вывод", "Библиотеки"}) {
                putHotspot(hotspots, "stage:" + t, cp, t);
            }
        });

        shoot(frameRef[0], "setup", "Сетап", null);
        shoot(frameRef[0], "power", "Питание", null);
        SwingUtilities.invokeAndWait(() -> {
            Container cp = frameRef[0].getContentPane();
            putHotspot(hotspots, "view:Общая схема питания", cp, "Общая схема питания");
        });
        shoot(frameRef[0], "power-schema", "Питание", "Общая схема питания");
        shoot(frameRef[0], "signal", "Сигнал", "Расключение экрана");
        SwingUtilities.invokeAndWait(() -> {
            Container cp = frameRef[0].getContentPane();
            putHotspot(hotspots, "view:Общая схема сигнала", cp, "Общая схема сигнала");
            putHotspot(hotspots, "view:Сетевой менеджер", cp, "Сетевой менеджер");
        });
        shoot(frameRef[0], "signal-schema", "Сигнал", "Общая схема сигнала");
        SwingUtilities.invokeAndWait(() -> clickButton(frameRef[0].getContentPane(), "Сетевой менеджер"));
        Thread.sleep(450);
        SwingUtilities.invokeAndWait(() -> {
            Container cp = frameRef[0].getContentPane();
            putHotspot(hotspots, "net:Найти устройства", cp, "Найти устройства (скан IP)…");
            putHotspot(hotspots, "net:Таблица адресов", cp, "Таблица адресов…");
        });
        shoot(frameRef[0], "network", "Сигнал", "Сетевой менеджер");
        SwingUtilities.invokeAndWait(() -> clickButton(frameRef[0].getContentPane(), "Генерация масок"));
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> putHotspot(hotspots, "masks:Разместить экран",
                frameRef[0].getContentPane(), "+ Разместить экран в канвасе"));
        shoot(frameRef[0], "masks", "Генерация масок", null);
        shoot(frameRef[0], "output", "Вывод", null);
        shoot(frameRef[0], "libraries", "Библиотеки", null);

        SwingUtilities.invokeAndWait(() -> frameRef[0].dispose());

        try (FileWriter w = new FileWriter(new File(OUT, "hotspots.json"))) {
            w.write("{\n");
            int i = 0;
            for (Map.Entry<String, double[]> e : hotspots.entrySet()) {
                double[] r = e.getValue();
                w.write(String.format(java.util.Locale.ROOT, "  \"%s\": [%.5f, %.5f, %.5f, %.5f]%s%n",
                        e.getKey(), r[0], r[1], r[2], r[3], ++i < hotspots.size() ? "," : ""));
            }
            w.write("}\n");
        }
        System.out.println("SHOTS + hotspots.json WRITTEN TO " + OUT.getAbsolutePath());
    }

    private void shoot(MainFrame frame, String name, String stage, String view) throws Exception {
        SwingUtilities.invokeAndWait(() -> clickButton(frame.getContentPane(), stage));
        Thread.sleep(350);
        if (view != null) {
            SwingUtilities.invokeAndWait(() -> clickButton(frame.getContentPane(), view));
            Thread.sleep(450);
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                Container cp = frame.getContentPane();
                BufferedImage img = new BufferedImage(cp.getWidth(), cp.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                cp.printAll(g);
                g.dispose();
                ImageIO.write(img, "png", new File(OUT, name + ".png"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    // ---- демо-воркспейс ----

    private void buildSampleWorkspace(AppModel model) {
        CabinetType ct = new CabinetType();
        ct.setName("Absen PL2.5");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(192);
        ct.setResolutionHeight(192);
        ct.setPowerConsumptionW(140);
        ct.setWeightKg(7.5);
        model.addCabinetType(ct);

        ControllerType conType = new ControllerType();
        conType.setName("Novastar MCTRL4K");
        conType.setVendor("Novastar");
        conType.setPortCount(16);
        conType.setPortBandwidthMbps(3900);
        conType.setInputPortCount(2);
        model.addControllerType(conType);

        model.selectProject(model.addProject("Демо-тур"));
        model.selectScene(model.addScene("Главная сцена"));
        Screen main = model.addScreen("Экран центр", ct.getId(), 8, 5, 0, 0);
        model.selectScreen(main);
        try {
            model.addScreenAutoPosition("Экран боковой", ct.getId(), 4, 3);
        } catch (Exception ignore) {
            // не критично для кадров
        }
        model.selectScreen(main);

        List<String> cab = new ArrayList<>();
        for (var c : model.getCurrentScreen().getCabinets()) {
            cab.add(c.getId());
        }
        safe(() -> model.addPowerChain(1, List.of(cab.get(0), cab.get(1), cab.get(2), cab.get(3))));
        safe(() -> model.addPowerChain(2, List.of(cab.get(8), cab.get(9), cab.get(10))));
        safe(() -> model.addPowerChain(3, List.of(cab.get(16), cab.get(17), cab.get(18), cab.get(19))));

        safe(() -> model.addControllerToScreen(model.getCurrentScreen(), conType.getId()));
        safe(() -> model.addSignalChain(1, false, List.of(cab.get(0), cab.get(1), cab.get(2), cab.get(3), cab.get(4))));
        safe(() -> model.addSignalChain(2, false, List.of(cab.get(5), cab.get(6), cab.get(7), cab.get(8), cab.get(9))));
        safe(() -> model.addSignalChain(3, false, List.of(cab.get(10), cab.get(11), cab.get(12))));

        // общая схема сигнала: медиасервер → контроллер → экран
        safe(() -> {
            SchemaNode server = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SERVER, "Медиасервер disguise", 80, 90, null);
            SchemaNode ctrl = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CONTROLLER, "MCTRL4K", 360, 90, null);
            SchemaNode scr = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SCREEN, "Экран центр", 640, 90, main.getId());
            model.addSchemaEdge(SchemaMode.SIGNAL, server.getId(), ctrl.getId(), "2×CAT6A");
            model.addSchemaEdge(SchemaMode.SIGNAL, ctrl.getId(), scr.getId(), "6×CAT6");
        });
        // общая схема питания: источник → распределение → экран
        safe(() -> {
            SchemaNode src = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит 63А", 80, 90, null);
            SchemaNode distro = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.DISTRO, "Дистро 32А", 360, 90, null);
            SchemaNode scr = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "Экран центр", 640, 90, main.getId());
            model.addSchemaEdge(SchemaMode.POWER, src.getId(), distro.getId(), "1×CEE63A · 12 м");
            model.addSchemaEdge(SchemaMode.POWER, distro.getId(), scr.getId(), "3×CEE32A · 8 м");
        });

        safe(() -> model.addEquipmentPreset(SchemaMode.POWER, SchemaNodeType.SOURCE,
                "Силовой щит 63А", "3 фазы, вход CEE63A", List.of()));
        safe(() -> model.addEquipmentPreset(SchemaMode.SIGNAL, SchemaNodeType.SERVER,
                "disguise gx 2c", "медиасервер", List.of()));

        // сетевой менеджер
        NetworkDeviceType sw = new NetworkDeviceType();
        sw.setName("Cisco SG350-28");
        sw.setCategory(NetworkDeviceCategory.SWITCH);
        sw.setPortCount(28);
        model.addNetworkDeviceType(sw);
        NetworkDeviceType rt = new NetworkDeviceType();
        rt.setName("MikroTik hEX S");
        rt.setCategory(NetworkDeviceCategory.ROUTER);
        rt.setPortCount(5);
        model.addNetworkDeviceType(rt);

        NetworkManagerPlan plan = new NetworkManagerPlan();
        Network prod = new Network();
        prod.setName("Продакшн LAN");
        prod.setColor(0x33aa55);
        NetworkDevicePlacement d1 = dev("Свитч сцены", sw.getId(), "192.168.10.2", 40, 60, 28, true, "http://192.168.10.2");
        NetworkDevicePlacement d2 = dev("Медиасервер", null, "192.168.10.10", 260, 60, 4, false, null);
        NetworkDevicePlacement d3 = dev("Контроллер MCTRL4K", null, "192.168.10.10", 260, 240, 2, false, null); // конфликт IP с d2
        prod.getDevices().add(d1);
        prod.getDevices().add(d2);
        prod.getDevices().add(d3);
        NetworkLink l1 = new NetworkLink();
        l1.setFromDeviceId(d1.getId());
        l1.setFromPort(1);
        l1.setToDeviceId(d2.getId());
        l1.setToPort(1);
        l1.setLabel("CAT6A");
        NetworkLink l2 = new NetworkLink();
        l2.setFromDeviceId(d1.getId());
        l2.setFromPort(2);
        l2.setToDeviceId(d3.getId());
        l2.setToPort(1);
        prod.getLinks().add(l1);
        prod.getLinks().add(l2);

        Network mgmt = new Network();
        mgmt.setName("Управление");
        mgmt.setColor(0x3576d8);
        NetworkDevicePlacement m1 = dev("Роутер MikroTik", rt.getId(), "10.0.0.1", 40, 60, 5, true, "http://10.0.0.1");
        NetworkDevicePlacement m2 = dev("Ноутбук инженера", null, "10.0.0.50", 260, 60, 1, false, null);
        mgmt.getDevices().add(m1);
        mgmt.getDevices().add(m2);
        NetworkLink l3 = new NetworkLink();
        l3.setFromDeviceId(m1.getId());
        l3.setFromPort(1);
        l3.setToDeviceId(m2.getId());
        l3.setToPort(1);
        mgmt.getLinks().add(l3);

        plan.getNetworks().add(prod);
        plan.getNetworks().add(mgmt);
        safe(() -> model.saveNetworkManagerPlan(model.getCurrentScene(), plan));

        // канвас масок
        safe(() -> {
            ContentCanvas canvas = model.addCanvas("Резолюм 1080p", 1920, 1080);
            model.addScreenToCanvas(canvas, main.getId(), 0, 0);
        });
    }

    private static NetworkDevicePlacement dev(String label, String typeId, String ip, double x, double y,
                                              int ports, boolean web, String url) {
        NetworkDevicePlacement d = new NetworkDevicePlacement();
        d.setCustomLabel(label);
        d.setDeviceTypeId(typeId);
        d.setIpAddress(ip);
        d.setSubnetMask("255.255.255.0");
        d.setXMm(x);
        d.setYMm(y);
        d.setPortCount(ports);
        d.setHasWebInterface(web);
        d.setWebInterfaceUrl(url);
        return d;
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRec(k);
            }
        }
        f.delete();
    }

    private interface Risky {
        void run() throws Exception;
    }

    private static void safe(Risky r) {
        try {
            r.run();
        } catch (Exception e) {
            System.out.println("sample step skipped: " + e);
        }
    }

    // ---- обход дерева компонентов ----

    private static void clickButton(Container root, String text) {
        AbstractButton b = findButton(root, text);
        if (b != null) {
            b.doClick();
        }
    }

    private static void putHotspot(Map<String, double[]> out, String key, Container contentPane, String buttonText) {
        AbstractButton b = findButton(contentPane, buttonText);
        if (b == null || !b.isShowing()) {
            return;
        }
        Point p = SwingUtilities.convertPoint(b.getParent(), b.getLocation(), contentPane);
        Rectangle r = new Rectangle(p.x, p.y, b.getWidth(), b.getHeight());
        double cw = contentPane.getWidth();
        double ch = contentPane.getHeight();
        // чуть ужимаем прямоугольник внутрь кнопки, чтобы промах по краю не считался
        double padX = r.width * 0.06;
        double padY = r.height * 0.10;
        out.put(key, new double[]{
                (r.x + padX) / cw, (r.y + padY) / ch,
                (r.width - 2 * padX) / cw, (r.height - 2 * padY) / ch});
    }

    private static AbstractButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton ab && text.equals(stripHtml(ab.getText()))) {
                return ab;
            }
            if (c instanceof Container inner) {
                AbstractButton found = findButton(inner, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String stripHtml(String s) {
        return s == null ? null : s.replaceAll("<[^>]+>", "").trim();
    }
}
