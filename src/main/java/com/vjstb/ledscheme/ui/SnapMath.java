package com.vjstb.ledscheme.ui;

/**
 * Формула "силы" прилипания (см. UserProfile.snapStrengthPercent) — единственная
 * точка, переиспользуемая CanvasEditorPanel/SceneCanvasPanel/SchemaCanvasPanel:
 * при 100% курсор жёстко прилипает точно к найденной в пределах порога цели (как
 * было раньше, до появления этой настройки), при меньших значениях — лишь
 * частично "тянется" к ней.
 */
public final class SnapMath {

    private SnapMath() {
    }

    public static double blend(double raw, double target, int strengthPercent) {
        double t = Math.max(0, Math.min(100, strengthPercent)) / 100.0;
        return raw + t * (target - raw);
    }
}
