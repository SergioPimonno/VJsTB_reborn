package com.vjstb.ledscheme.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Потоковая запись PNG по строкам — без полного изображения в памяти (запрос 2026-09-30:
 * убрать ограничение 16k на сторону маски/канваса, решение D7). {@code ImageIO} требует
 * готовый {@link BufferedImage} целиком: канвас 30000×8000 — это ~1 ГБ в памяти ещё до
 * записи. Здесь вызывающий код ({@link MaskImage#writePng}) подаёт изображение полосами
 * ({@link #writeRows}), память ∝ ширине × высоте полосы.
 *
 * <p>Формат — минимальный валидный PNG: IHDR (8 бит на канал, RGB или RGBA, без
 * чересстрочности), IDAT-чанки до 64 КБ из ОДНОГО deflate-потока, фильтр строки 0 (None) —
 * маски почти целиком из однотонных областей, deflate сжимает их и так; IEND. Пиксели
 * RGBA пишутся НЕпредумноженными (как их отдаёт {@code getRGB} для {@code TYPE_INT_ARGB}) —
 * PNG хранит именно так, поэтому результат попиксельно равен {@code ImageIO.write} той же
 * картинки (проверяется тестом {@code StreamingPngWriterTest}).
 */
public final class StreamingPngWriter implements AutoCloseable {

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final int IDAT_CHUNK = 64 * 1024;

    private final int width;
    private final int height;
    private final boolean alpha;
    private final DataOutputStream file;
    private final DeflaterOutputStream deflate;
    private final Deflater deflater;
    private final byte[] rowBuf;
    private final int[] argbRow;
    private int rowsWritten;
    private int idatChunks;

    public StreamingPngWriter(File target, int width, int height, boolean alpha) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Размер PNG должен быть положительным: " + width + "×" + height);
        }
        // Та же защита, что в PixelGridRenderer.writePng: папку могли удалить после открытия
        // диалога предпросмотра — гарантируем её прямо перед записью.
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.file = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(target), 1 << 16));
        try {
            file.write(SIGNATURE);
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(width);
            d.writeInt(height);
            d.writeByte(8);                 // бит на канал
            d.writeByte(alpha ? 6 : 2);     // 6 = RGBA, 2 = RGB
            d.writeByte(0);                 // deflate
            d.writeByte(0);                 // стандартные фильтры
            d.writeByte(0);                 // без чересстрочности
            writeChunk("IHDR", ihdr.toByteArray(), ihdr.size());
        } catch (IOException e) {
            file.close();
            throw e;
        }
        this.deflater = new Deflater(6);
        this.deflate = new DeflaterOutputStream(new IdatStream(), deflater, IDAT_CHUNK);
        this.rowBuf = new byte[1 + width * (alpha ? 4 : 3)];
        this.argbRow = new int[width];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int rowsWritten() {
        return rowsWritten;
    }

    /** Сколько IDAT-чанков уже записано (для тестов: большой PNG должен резаться на несколько). */
    int idatChunks() {
        return idatChunks;
    }

    /** Дописывает строки {@code 0..rows-1} полосы {@code strip} (ширина полосы = ширине PNG). */
    public void writeRows(BufferedImage strip, int rows) throws IOException {
        if (strip.getWidth() != width) {
            throw new IllegalArgumentException("Ширина полосы " + strip.getWidth() + " ≠ " + width);
        }
        if (rows > strip.getHeight()) {
            throw new IllegalArgumentException("В полосе " + strip.getHeight() + " строк, запрошено " + rows);
        }
        // Быстрый путь: полосы, которые создаёт MaskImage, — TYPE_INT_RGB/ARGB с плотной
        // раскладкой; читаем массив напрямую вместо getRGB по строке (на 30000 px в ширину это
        // заметно быстрее). Иначе — общий путь через getRGB.
        int[] data = null;
        int stride = 0;
        int base = 0;
        int type = strip.getType();
        if ((type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB)
                && strip.getRaster().getDataBuffer() instanceof DataBufferInt dbi
                && strip.getSampleModel() instanceof SinglePixelPackedSampleModel sm
                && strip.getRaster().getParent() == null) {
            data = dbi.getData();
            stride = sm.getScanlineStride();
            base = dbi.getOffset();
        }
        boolean opaqueSource = type == BufferedImage.TYPE_INT_RGB;
        for (int y = 0; y < rows; y++) {
            if (rowsWritten >= height) {
                throw new IllegalStateException("Все " + height + " строк PNG уже записаны");
            }
            int[] row;
            int off;
            if (data != null) {
                row = data;
                off = base + y * stride;
            } else {
                strip.getRGB(0, y, width, 1, argbRow, 0, width);
                row = argbRow;
                off = 0;
            }
            int p = 0;
            rowBuf[p++] = 0; // фильтр None
            for (int x = 0; x < width; x++) {
                int c = row[off + x];
                rowBuf[p++] = (byte) (c >> 16);
                rowBuf[p++] = (byte) (c >> 8);
                rowBuf[p++] = (byte) c;
                if (alpha) {
                    rowBuf[p++] = opaqueSource ? (byte) 0xFF : (byte) (c >>> 24);
                }
            }
            deflate.write(rowBuf, 0, p);
            rowsWritten++;
        }
    }

    /** Завершает файл. Если записаны не все строки — {@link IOException} (файл недописан,
     *  вызывающий код его удаляет — см. {@link MaskImage#writePng}). */
    @Override
    public void close() throws IOException {
        try {
            if (rowsWritten != height) {
                throw new IOException("Записано " + rowsWritten + " строк из " + height);
            }
            deflate.finish();
            deflate.flush();
            writeChunk("IEND", new byte[0], 0);
        } finally {
            deflater.end();
            file.close();
        }
    }

    private void writeChunk(String type, byte[] data, int len) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        file.writeInt(len);
        file.write(typeBytes);
        file.write(data, 0, len);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data, 0, len);
        file.writeInt((int) crc.getValue());
    }

    /** Нарезает сжатый поток на IDAT-чанки. */
    private final class IdatStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                int n = Math.min(len, IDAT_CHUNK);
                byte[] chunk = off == 0 && n == b.length ? b : Arrays.copyOfRange(b, off, off + n);
                writeChunk("IDAT", chunk, n);
                idatChunks++;
                off += n;
                len -= n;
            }
        }
    }
}
