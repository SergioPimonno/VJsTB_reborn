package com.vjstb.ledscheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.Network;
import com.vjstb.ledscheme.model.NetworkAttachment;
import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkManagerPlan;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.NetworkTopology;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.NetworkCanvasPanel;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** «Экспорт карты сети…» ({@link NetworkCanvasPanel#renderImage}) и «Выровнять
 *  сеть» ({@link NetworkCanvasPanel#autoArrangeNetwork}) — оба одобрены
 *  пользователем как продолжение Round 8/9 Сетевого менеджера, см.
 *  NETWORK_MANAGER_NOTES.md. Только СТРУКТУРНЫЕ инварианты (растёт с
 *  содержимым, масштабируется по dpiScale, не падает на пустом плане, сетка не
 *  накладывает устройства друг на друга) — не побайтовое сравнение картинки,
 *  тот же принцип, что {@code SchemeExportTest}. */
class NetworkCanvasPanelRenderTest {

    private AppModel model(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
    }

    private SettingsManager settings(Path dir) {
        return new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
    }

    private NetworkManagerPlan planWithOneDevice(String networkId, double x, double y) {
        NetworkManagerPlan plan = new NetworkManagerPlan();
        NetworkDevicePlacement p = new NetworkDevicePlacement();
        p.setCustomLabel("Свитч");
        p.setXMm(x);
        p.setYMm(y);
        p.getAttachments().add(new NetworkAttachment(networkId));
        plan.getDevices().add(p);
        return plan;
    }

    @Test
    void renderImageGrowsWithDevicePosition(@TempDir Path dir) {
        AppModel m = model(dir);
        SettingsManager s = settings(dir);

        NetworkCanvasPanel small = new NetworkCanvasPanel(m, s);
        small.setPlan(planWithOneDevice("net", 0, 0), null);
        Dimension smallSize = small.getPreferredSize();

        NetworkCanvasPanel large = new NetworkCanvasPanel(m, s);
        large.setPlan(planWithOneDevice("net", 2000, 2000), null);
        Dimension largeSize = large.getPreferredSize();

        BufferedImage smallImg = small.renderImage(smallSize.width, smallSize.height, 1.0);
        BufferedImage largeImg = large.renderImage(largeSize.width, largeSize.height, 1.0);

        assertNotNull(smallImg);
        assertNotNull(largeImg);
        assertTrue(largeImg.getWidth() > smallImg.getWidth());
        assertTrue(largeImg.getHeight() > smallImg.getHeight());
    }

    @Test
    void renderImageDpiScaleMultipliesPixelDimensions(@TempDir Path dir) {
        AppModel m = model(dir);
        SettingsManager s = settings(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(m, s);
        canvas.setPlan(planWithOneDevice("net", 100, 100), null);
        Dimension size = canvas.getPreferredSize();

        BufferedImage base = canvas.renderImage(size.width, size.height, 1.0);
        double scale = 300 / 72.0; // как в CurrentSchemeExporter (dpi/72.0)
        BufferedImage scaled = canvas.renderImage(size.width, size.height, scale);

        assertEquals(Math.round(size.width * scale), scaled.getWidth());
        assertEquals(Math.round(size.height * scale), scaled.getHeight());
        assertEquals(base.getWidth(), size.width, "dpiScale=1.0 не меняет логический размер");
    }

    @Test
    void renderImageOnEmptyPlanDoesNotCrash(@TempDir Path dir) {
        AppModel m = model(dir);
        SettingsManager s = settings(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(m, s);
        canvas.setPlan(new NetworkManagerPlan(), null);
        Dimension size = canvas.getPreferredSize();

        BufferedImage img = canvas.renderImage(size.width, size.height, 1.0);
        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    void autoArrangeNetworkSpreadsOverlappingDevicesAndLeavesOtherNetworksAlone(@TempDir Path dir) {
        AppModel m = model(dir);
        SettingsManager s = settings(dir);
        NetworkCanvasPanel canvas = new NetworkCanvasPanel(m, s);

        NetworkManagerPlan plan = new NetworkManagerPlan();
        Network netA = new Network();
        netA.setId("net-a");
        Network netB = new Network();
        netB.setId("net-b");
        plan.getNetworks().add(netA);
        plan.getNetworks().add(netB);

        // Три устройства сети A свалены в одну точку -- типичный итог многократного
        // скана/добавления без ручной раскладки.
        for (int i = 0; i < 3; i++) {
            NetworkDevicePlacement p = new NetworkDevicePlacement();
            p.setXMm(500);
            p.setYMm(500);
            p.getAttachments().add(new NetworkAttachment("net-a"));
            plan.getDevices().add(p);
        }
        NetworkDevicePlacement other = new NetworkDevicePlacement();
        other.setXMm(999);
        other.setYMm(999);
        other.getAttachments().add(new NetworkAttachment("net-b"));
        plan.getDevices().add(other);

        canvas.setPlan(plan, null);
        canvas.autoArrangeNetwork("net-a");

        List<NetworkDevicePlacement> arranged = NetworkTopology.devicesInNetwork(plan, "net-a");
        Set<String> positions = new HashSet<>();
        for (NetworkDevicePlacement p : arranged) {
            positions.add(p.getXMm() + "," + p.getYMm());
        }
        assertEquals(3, positions.size(), "устройства сети должны разъехаться по разным клеткам сетки");
        assertEquals(999.0, other.getXMm(), "устройство ДРУГОЙ сети трогать не должно");
        assertEquals(999.0, other.getYMm());
    }
}
