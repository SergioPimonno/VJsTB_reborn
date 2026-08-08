package com.vjstb.ledscheme.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionManifestTest {

    @Test
    void simpleMajorMinorComparison() {
        assertTrue(VersionManifest.isNewer("2.0", "1.6"));
        assertFalse(VersionManifest.isNewer("1.6", "2.0"));
        assertFalse(VersionManifest.isNewer("2.0", "2.0"));
    }

    @Test
    void missingSegmentsTreatedAsZero() {
        assertTrue(VersionManifest.isNewer("2.0.1", "2.0"));
        assertFalse(VersionManifest.isNewer("2.0", "2.0.0"));
    }

    @Test
    void doesNotCompareLexicographically() {
        // "1.10" > "1.9" числово, хотя лексикографически "1.10" < "1.9"
        assertTrue(VersionManifest.isNewer("1.10", "1.9"));
    }

    @Test
    void blankOrNullCurrentTreatsCandidateAsNewer() {
        assertTrue(VersionManifest.isNewer("1.0", null));
        assertTrue(VersionManifest.isNewer("1.0", ""));
    }

    @Test
    void blankOrNullCandidateIsNeverNewer() {
        assertFalse(VersionManifest.isNewer(null, "1.0"));
        assertFalse(VersionManifest.isNewer("", "1.0"));
    }
}
