package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-09-30 (решение D7): маски больше 16k пишутся потоково полосами
 *  ({@link StreamingPngWriter}), без полного изображения в памяти. Собственный писатель PNG —
 *  значит, нужна проверка, что результат — ВАЛИДНЫЙ PNG, который читает стандартный
 *  {@code ImageIO}, в обоих режимах (RGB для масок, RGBA для пустот/разметки After Effects), и
 *  что большой поток режется на несколько IDAT-чанков. */
class StreamingPngWriterTest {

    private static BufferedImage noise(int w, int h, int type, long seed) {
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = type == BufferedImage.TYPE_INT_ARGB ? rnd.nextInt(256) : 0xFF;
                img.setRGB(x, y, (a << 24) | (rnd.nextInt(0x1000000)));
            }
        }
        return img;
    }

    /** Пишет {@code src} полосами по {@code stripRows} строк — как MaskImage.writePng. */
    private static int writeInStrips(BufferedImage src, File file, boolean alpha, int stripRows) throws IOException {
        StreamingPngWriter w = new StreamingPngWriter(file, src.getWidth(), src.getHeight(), alpha);
        try (w) {
            for (int y0 = 0; y0 < src.getHeight(); y0 += stripRows) {
                int rows = Math.min(stripRows, src.getHeight() - y0);
                BufferedImage strip = new BufferedImage(src.getWidth(), rows, src.getType());
                // Копия «как есть» (не drawImage: SrcOver полупрозрачного на прозрачное округляет цвет).
                int[] px = src.getRGB(0, y0, src.getWidth(), rows, null, 0, src.getWidth());
                strip.setRGB(0, 0, src.getWidth(), rows, px, 0, src.getWidth());
                w.writeRows(strip, rows);
            }
        }
        return w.idatChunks(); // после close: последний IDAT дописывается при finish()
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        int w = expected.getWidth();
        int[] e = expected.getRGB(0, 0, w, expected.getHeight(), null, 0, w);
        int[] a = actual.getRGB(0, 0, w, actual.getHeight(), null, 0, w);
        assertArrayEquals(e, a);
    }

    @Test
    void rgbPngIsReadableAndPixelExact(@TempDir Path dir) throws IOException {
        BufferedImage src = noise(97, 61, BufferedImage.TYPE_INT_RGB, 1);
        File f = dir.resolve("rgb.png").toFile();
        writeInStrips(src, f, false, 10);

        byte[] head = Files.readAllBytes(f.toPath());
        assertEquals((byte) 137, head[0]);
        assertEquals('P', head[1]);
        BufferedImage read = ImageIO.read(f);
        assertNotNull(read, "ImageIO должен распознать файл как PNG");
        assertSamePixels(src, read);
    }

    @Test
    void rgbaPngKeepsAlphaPixelExact(@TempDir Path dir) throws IOException {
        BufferedImage src = noise(64, 33, BufferedImage.TYPE_INT_ARGB, 2);
        File f = dir.resolve("rgba.png").toFile();
        writeInStrips(src, f, true, 7);

        BufferedImage read = ImageIO.read(f);
        assertNotNull(read);
        assertTrue(read.getColorModel().hasAlpha(), "RGBA-файл должен читаться с альфой");
        assertSamePixels(src, read);
    }

    @Test
    void largeStreamIsSplitIntoSeveralIdatChunks(@TempDir Path dir) throws IOException {
        // Шум почти не сжимается: 300×300×3 ≈ 270 КБ данных → несколько IDAT по 64 КБ.
        BufferedImage src = noise(300, 300, BufferedImage.TYPE_INT_RGB, 3);
        File f = dir.resolve("big.png").toFile();
        int chunks = writeInStrips(src, f, false, 64);
        assertTrue(chunks > 1, "ожидалось несколько IDAT, получено " + chunks);
        assertEquals(chunks, countChunks(f, "IDAT"));
        assertSamePixels(src, ImageIO.read(f));
    }

    @Test
    void closingBeforeAllRowsAreWrittenFails(@TempDir Path dir) throws IOException {
        File f = dir.resolve("short.png").toFile();
        StreamingPngWriter w = new StreamingPngWriter(f, 10, 10, false);
        w.writeRows(new BufferedImage(10, 5, BufferedImage.TYPE_INT_RGB), 5);
        assertThrows(IOException.class, w::close);
    }

    @Test
    void stripOfWrongWidthIsRejected(@TempDir Path dir) throws IOException {
        File f = dir.resolve("w.png").toFile();
        try (StreamingPngWriter w = new StreamingPngWriter(f, 10, 1, false)) {
            assertThrows(IllegalArgumentException.class,
                    () -> w.writeRows(new BufferedImage(11, 1, BufferedImage.TYPE_INT_RGB), 1));
            w.writeRows(new BufferedImage(10, 1, BufferedImage.TYPE_INT_RGB), 1);
        }
    }

    private static int countChunks(File f, String type) throws IOException {
        int n = 0;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            in.skipNBytes(8);
            while (true) {
                int len = in.readInt();
                byte[] t = new byte[4];
                in.readFully(t);
                String name = new String(t, java.nio.charset.StandardCharsets.US_ASCII);
                in.skipNBytes(len + 4L);
                if (name.equals(type)) {
                    n++;
                }
                if (name.equals("IEND")) {
                    return n;
                }
            }
        }
    }
}
