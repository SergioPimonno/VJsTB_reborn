package com.vjstb.ledscheme.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Запись схемы пакета документации в выбранный в «Параметрах экспорта» формат
 * (этап «Вывод», см. {@code UserProfile#getDocExportFormat}). Маски сюда не идут —
 * они всегда PNG (пиксель-в-пиксель под LED-панель, см. {@code PixelGridRenderer}).
 * <p>У форматов свои пределы стороны картинки: JPEG — 65500, WebP — 16383. Всё, что
 * больше, ужимается по месту, а DPI в метаданных снижается пропорционально —
 * физический размер при печати тот же, теряется только плотность (см. баг-репорт у
 * {@link SchemeRenderer#writeJpeg}).
 */
public final class SchemeImageWriter {

    public enum Format {
        JPG("jpg", "JPG — компактно, с небольшими потерями"),
        PNG("png", "PNG — без потерь, файлы крупнее"),
        WEBP("webp", "WebP — компактнее JPG (сторона до 16383 px)"),
        PDF("pdf", "PDF — страница по размеру схемы, для печати");

        public final String ext;
        public final String label;

        Format(String ext, String label) {
            this.ext = ext;
            this.label = label;
        }

        /** Неизвестное/пустое значение из профиля (например, от будущей версии) → JPG. */
        public static Format fromId(String id) {
            if (id != null) {
                for (Format f : values()) {
                    if (f.name().equalsIgnoreCase(id)) {
                        return f;
                    }
                }
            }
            return JPG;
        }

        /** Есть ли у формата настройка качества (сжатие с потерями). */
        public boolean lossy() {
            return this == JPG || this == WEBP;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static final int WEBP_MAX_DIMENSION = 16383;
    /** Предел стороны страницы PDF — 200 дюймов (14400 pt), больше Acrobat не открывает. */
    static final float PDF_MAX_PAGE_PT = 14400f;

    private SchemeImageWriter() {
    }

    /**
     * Пишет {@code img} в {@code dir/baseName.<ext>} и возвращает итоговый файл.
     *
     * @param baseName имя без расширения — вызывающий уже прогнал его через
     *                 {@link OutputPaths#sanitize}
     * @param quality  1..100, только для JPG/WebP
     */
    public static File write(BufferedImage img, File dir, String baseName, Format format, int dpi, int quality)
            throws IOException {
        File file = new File(dir, baseName + "." + format.ext);
        float q = Math.max(1, Math.min(100, quality)) / 100f;
        switch (format) {
            case JPG -> SchemeRenderer.writeJpeg(img, file, dpi, q);
            case PNG -> writePng(img, file, dpi);
            case WEBP -> writeWebp(img, file, q);
            case PDF -> writePdf(img, file, dpi);
            default -> throw new IllegalStateException(format.name());
        }
        return file;
    }

    static void writePng(BufferedImage img, File file, int dpi) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(img), param);
            setPngDpi(metadata, dpi);
            writer.write(null, new IIOImage(img, null, metadata), param);
        } finally {
            writer.dispose();
        }
    }

    /** pHYs-чанк: без него PNG, как и JPEG без JFIF-плотности, печатается «72dpi». */
    private static void setPngDpi(IIOMetadata metadata, int dpi) throws IIOInvalidTreeException {
        String ppm = Integer.toString((int) Math.round(dpi / 0.0254));
        IIOMetadataNode phys = new IIOMetadataNode("pHYs");
        phys.setAttribute("pixelsPerUnitXAxis", ppm);
        phys.setAttribute("pixelsPerUnitYAxis", ppm);
        phys.setAttribute("unitSpecifier", "meter");
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        root.appendChild(phys);
        metadata.mergeTree("javax_imageio_png_1.0", root);
    }

    static void writeWebp(BufferedImage img, File file, float quality) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("webp");
        if (!it.hasNext()) {
            // Плагин регистрируется через META-INF/services — если его нет в сборке, это
            // ошибка упаковки, а не пользователя; сообщение попадёт в диалог ошибки экспорта.
            throw new IOException("WebP не поддерживается этой сборкой (нет ImageIO-плагина)");
        }
        img = fitInto(toRgb(img), WEBP_MAX_DIMENSION);
        ImageWriter writer = it.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    String chosen = types[0];
                    for (String t : types) {
                        if (t.toLowerCase().contains("lossy")) {
                            chosen = t;
                        }
                    }
                    param.setCompressionType(chosen);
                }
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** Одна страница ровно по размеру схемы в физических единицах ({@code dpi}):
     *  картинка без потерь (схема — линии и текст, JPEG-артефакты на них заметны).
     *  Страница длиннее 200 дюймов ужимается целиком — пиксели при этом не теряются. */
    static void writePdf(BufferedImage img, File file, int dpi) throws IOException {
        float w = img.getWidth() * 72f / dpi;
        float h = img.getHeight() * 72f / dpi;
        float k = Math.min(1f, PDF_MAX_PAGE_PT / Math.max(w, h));
        w *= k;
        h *= k;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(w, h));
            doc.addPage(page);
            PDImageXObject ximg = LosslessFactory.createFromImage(doc, toRgb(img));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(ximg, 0, 0, w, h);
            }
            doc.save(file);
        }
    }

    private static BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage fitInto(BufferedImage img, int maxSide) {
        int side = Math.max(img.getWidth(), img.getHeight());
        if (side <= maxSide) {
            return img;
        }
        double k = (double) maxSide / side;
        int w = Math.max(1, Math.min(maxSide, (int) Math.floor(img.getWidth() * k)));
        int h = Math.max(1, Math.min(maxSide, (int) Math.floor(img.getHeight() * k)));
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }
}
