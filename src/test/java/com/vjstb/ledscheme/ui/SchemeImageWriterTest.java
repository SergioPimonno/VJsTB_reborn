package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Форматы схем из окна «Параметры экспорта» (JPG/PNG/WebP/PDF), см. {@link SchemeImageWriter}. */
class SchemeImageWriterTest {

    private static BufferedImage sample(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.RED);
        g.drawLine(0, 0, w - 1, h - 1);
        g.dispose();
        return img;
    }

    @Test
    void fromId_unknownOrEmpty_fallsBackToJpg() {
        assertEquals(SchemeImageWriter.Format.JPG, SchemeImageWriter.Format.fromId(null));
        assertEquals(SchemeImageWriter.Format.JPG, SchemeImageWriter.Format.fromId("TIFF"));
        assertEquals(SchemeImageWriter.Format.PDF, SchemeImageWriter.Format.fromId("pdf"));
    }

    @Test
    void jpg_writesFileWithJpgExtension(@TempDir Path dir) throws Exception {
        File f = SchemeImageWriter.write(sample(200, 100), dir.toFile(), "Экран A Сила",
                SchemeImageWriter.Format.JPG, 150, 80);
        assertTrue(f.getName().endsWith(".jpg"));
        BufferedImage back = ImageIO.read(f);
        assertEquals(200, back.getWidth());
        assertEquals(100, back.getHeight());
    }

    @Test
    void png_isLosslessAndCarriesDpi(@TempDir Path dir) throws Exception {
        BufferedImage img = sample(64, 32);
        File f = SchemeImageWriter.write(img, dir.toFile(), "s", SchemeImageWriter.Format.PNG, 300, 92);
        assertTrue(f.getName().endsWith(".png"));
        BufferedImage back = ImageIO.read(f);
        assertEquals(img.getRGB(10, 5), back.getRGB(10, 5));
        assertEquals(img.getRGB(63, 31), back.getRGB(63, 31));

        try (var iis = ImageIO.createImageInputStream(f)) {
            var reader = ImageIO.getImageReaders(iis).next();
            reader.setInput(iis);
            var meta = reader.getImageMetadata(0);
            var root = (org.w3c.dom.Element) meta.getAsTree("javax_imageio_png_1.0");
            var phys = (org.w3c.dom.Element) root.getElementsByTagName("pHYs").item(0);
            assertNotNull(phys, "pHYs не записан");
            // 300 dpi = 11811 px/m
            assertEquals("11811", phys.getAttribute("pixelsPerUnitXAxis"));
        }
    }

    @Test
    void webp_writesReadableFile(@TempDir Path dir) throws Exception {
        File f = SchemeImageWriter.write(sample(300, 120), dir.toFile(), "s", SchemeImageWriter.Format.WEBP, 72, 90);
        assertTrue(f.getName().endsWith(".webp"));
        assertTrue(f.length() > 0);
        BufferedImage back = ImageIO.read(f);
        assertNotNull(back, "WebP не читается обратно");
        assertEquals(300, back.getWidth());
        assertEquals(120, back.getHeight());
    }

    @Test
    void webp_oversized_isDownscaledToFormatLimit(@TempDir Path dir) throws Exception {
        File f = SchemeImageWriter.write(sample(20000, 40), dir.toFile(), "wide", SchemeImageWriter.Format.WEBP,
                300, 90);
        BufferedImage back = ImageIO.read(f);
        assertEquals(SchemeImageWriter.WEBP_MAX_DIMENSION, back.getWidth());
    }

    @Test
    void pdf_pageSizeFollowsDpi(@TempDir Path dir) throws Exception {
        // 600x300 px при 300 dpi = 2x1 дюйма = 144x72 pt
        File f = SchemeImageWriter.write(sample(600, 300), dir.toFile(), "s", SchemeImageWriter.Format.PDF, 300, 92);
        assertTrue(f.getName().endsWith(".pdf"));
        try (PDDocument doc = Loader.loadPDF(f)) {
            assertEquals(1, doc.getNumberOfPages());
            PDRectangle box = doc.getPage(0).getMediaBox();
            assertEquals(144f, box.getWidth(), 0.5f);
            assertEquals(72f, box.getHeight(), 0.5f);
        }
    }
}
