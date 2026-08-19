package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.VehicleType;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Смоук-тест {@link VehicleLoadSchemaImageWriter} — по прямому запросу пользователя
 *  «рендер схемы размещения аналогично рендеру схем расключения» (см. class-javadoc
 *  и {@link SchemeRenderer#renderImage} за образцом {@code dpiScale}). Проверяет
 *  только структурные инварианты (растёт с dpiScale, не падает на повороте/
 *  штабеле/примечании) — не побайтовое сравнение, это просто визуальная схема. */
class VehicleLoadSchemaImageWriterTest {

    private CaseType caseType(String name, double lengthMm, double widthMm, boolean carriesCabinets) {
        CaseType c = new CaseType();
        c.setName(name);
        c.setLengthMm(lengthMm);
        c.setWidthMm(widthMm);
        c.setHeightMm(400);
        c.setWeightKg(20);
        c.setMaxStackCount(3);
        c.setCarriesCabinets(carriesCabinets);
        return c;
    }

    private VehicleType vehicle() {
        VehicleType v = new VehicleType();
        v.setName("Газель");
        v.setCargoLengthMm(3000);
        v.setCargoWidthMm(2000);
        v.setCargoHeightMm(2000);
        v.setPayloadKg(1500);
        return v;
    }

    @Test
    void renderProducesNonEmptyImage() {
        CaseType c = caseType("Кофр", 700, 500, true);
        List<VehicleLoadSchemaImageWriter.PlacedCase> placements = List.of(
                new VehicleLoadSchemaImageWriter.PlacedCase(c, 0, 0, false, 1, ""));

        BufferedImage img = VehicleLoadSchemaImageWriter.render(vehicle(), placements, "Машина 1", 1.0);

        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }

    @Test
    void higherDpiScaleProducesLargerImage() {
        CaseType c = caseType("Кофр", 700, 500, true);
        List<VehicleLoadSchemaImageWriter.PlacedCase> placements = List.of(
                new VehicleLoadSchemaImageWriter.PlacedCase(c, 0, 0, false, 1, ""));

        BufferedImage screenDpi = VehicleLoadSchemaImageWriter.render(vehicle(), placements, "М1", 1.0);
        BufferedImage printDpi = VehicleLoadSchemaImageWriter.render(vehicle(), placements, "М1", 300.0 / 72.0);

        // Тот же приём, что и SchemeRenderer.renderImage — dpiScale масштабирует итоговый
        // растр равномерно (Graphics2D.scale), не пересчитывает внутреннюю геометрию.
        assertTrue(printDpi.getWidth() > screenDpi.getWidth());
        assertTrue(printDpi.getHeight() > screenDpi.getHeight());
    }

    @Test
    void handlesRotatedStackedAndNotedPlacementsWithoutCrashing() {
        CaseType cabinets = caseType("Кофр кабинетов", 700, 500, true);
        CaseType commutation = caseType("Кофр коммутации", 800, 600, false);
        List<VehicleLoadSchemaImageWriter.PlacedCase> placements = List.of(
                new VehicleLoadSchemaImageWriter.PlacedCase(cabinets, 0, 0, true, 3, "кубы"),
                new VehicleLoadSchemaImageWriter.PlacedCase(commutation, 900, 0, false, 1, null));

        BufferedImage img = VehicleLoadSchemaImageWriter.render(vehicle(), placements, null, 1.0);

        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }

    @Test
    void emptyPlacementsStillRendersCargoOutline() {
        BufferedImage img = VehicleLoadSchemaImageWriter.render(vehicle(), List.of(), "Пустая", 1.0);

        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }
}
