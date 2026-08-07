package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.service.ProjectorCalc.AmbientLight;
import com.vjstb.ledscheme.service.ProjectorCalc.AspectRatio;
import com.vjstb.ledscheme.service.ProjectorCalc.BrightnessCheck;
import com.vjstb.ledscheme.service.ProjectorCalc.BrightnessStatus;
import com.vjstb.ledscheme.service.ProjectorCalc.ImageSize;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFit;
import com.vjstb.ledscheme.service.ProjectorCalc.LensFitStatus;
import com.vjstb.ledscheme.service.ProjectorCalc.ThrowDistanceRange;
import org.junit.jupiter.api.Test;

class ProjectorCalcTest {

    @Test
    void throwRatioIsDistanceOverWidth() {
        ImageSize size = new ImageSize(4.0, 2.25);
        assertEquals(2.0, ProjectorCalc.requiredThrowRatio(8.0, size), 1e-9);
    }

    @Test
    void imageSizeFromDiagonalMatches169() {
        ImageSize size = ProjectorCalc.imageSizeFromDiagonal(3.0, AspectRatio.R16_9);
        assertEquals(3.0, Math.hypot(size.widthM(), size.heightM()), 1e-9);
        assertEquals(16.0 / 9.0, size.widthM() / size.heightM(), 1e-9);
    }

    @Test
    void lensFitPassesWithinRange() {
        ImageSize size = new ImageSize(4.0, 2.25);
        LensFit fit = ProjectorCalc.checkLensFit(8.0, size, 1.5, 2.5);
        assertEquals(LensFitStatus.FITS, fit.status());
        assertEquals(2.0, fit.requiredRatio(), 1e-9);
    }

    @Test
    void lensFitFailsTooShort() {
        ImageSize size = new ImageSize(4.0, 2.25);
        // ratio = 8/4 = 2.0, но объектив максимум даёт 1.5 -> проектор физически
        // слишком близко (нужно увеличить дистанцию, чтобы попасть в диапазон объектива)
        LensFit fit = ProjectorCalc.checkLensFit(8.0, size, 0.8, 1.5);
        assertEquals(LensFitStatus.TOO_SHORT_INCREASE_DISTANCE, fit.status());
    }

    @Test
    void lensFitFailsTooLong() {
        ImageSize size = new ImageSize(4.0, 2.25);
        // ratio = 2.0, минимум объектива 2.5 -> дистанция слишком большая
        LensFit fit = ProjectorCalc.checkLensFit(8.0, size, 2.5, 3.5);
        assertEquals(LensFitStatus.TOO_LONG_DECREASE_DISTANCE, fit.status());
    }

    @Test
    void throwDistanceRangeRoundTripsWithRequiredRatio() {
        ImageSize size = new ImageSize(4.0, 2.25);
        ThrowDistanceRange range = ProjectorCalc.throwDistanceRange(size, 1.5, 2.5);
        assertEquals(6.0, range.minM(), 1e-9);
        assertEquals(10.0, range.maxM(), 1e-9);

        assertEquals(LensFitStatus.FITS, ProjectorCalc.checkLensFit(range.minM(), size, 1.5, 2.5).status());
        assertEquals(LensFitStatus.FITS, ProjectorCalc.checkLensFit(range.maxM(), size, 1.5, 2.5).status());
    }

    @Test
    void verticalOffsetPercentConvertsToMeters() {
        ImageSize size = new ImageSize(4.0, 2.0);
        assertEquals(0.2, ProjectorCalc.verticalOffsetM(size, 10.0), 1e-9);
    }

    @Test
    void brightnessLuxIsLumensTimesGainOverArea() {
        ImageSize size = new ImageSize(4.0, 2.0);
        assertEquals(650.0, ProjectorCalc.screenIlluminanceLux(5200.0, 1.0, size), 1e-9);
    }

    @Test
    void brightnessRatingFailsInBrightRoomWithLowLumens() {
        ImageSize size = new ImageSize(4.0, 2.0);
        BrightnessCheck check = ProjectorCalc.checkBrightness(400.0, 1.0, size, AmbientLight.BRIGHT);
        assertEquals(BrightnessStatus.FAIL, check.status());
    }
}
