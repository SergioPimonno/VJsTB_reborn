package com.vjstb.ledscheme.model;

/**
 * Стартовые значения для НОВЫХ экранов конкретной сцены — кнопка «Параметры по
 * умолчанию» в прериге сцены ({@code ui.stage.SetupStagePanel}), запрос
 * пользователя 2026-09-15: "выставлять для каждого экрана руками, когда в
 * сцене используется только другой тип кабинетов — фрустрирующе". Мотивирующие
 * примеры пользователя — тип кабинета (сейчас новому экрану всегда подставляется
 * первый по списку в общей библиотеке, а не реально используемый в этой сцене)
 * и герцовка контента (в России обычно 50 Гц, а {@link Screen#getRefreshRateHz()}
 * жёстко инициализируется 60).
 *
 * <p>Применяются РОВНО ОДИН РАЗ — в момент создания экрана (см.
 * {@code service.AppModel#addScreen}), переносом непустых полей на только что
 * созданный {@link Screen}. Дальше это обычные поля экрана: правки, сделанные
 * пользователем позже через «Параметры экрана»/«Подвес»/«Конструктив», НЕ
 * перезаписываются повторным применением дефолтов — явный ответ пользователя на
 * уточняющий вопрос ("если пользователь потом их изменяет — сохраняются правки
 * пользователя"). Значит это НЕ live-шаблон, транслируемый на уже существующие
 * экраны сцены, а только стартовая точка для будущих.
 *
 * <p>Хранится на {@link Scene#getScreenDefaults()} — per-сцена, а не глобально
 * и не per-проект, т.к. разные сцены одного проекта часто используют разное
 * оборудование (см. исходный запрос). Все поля nullable — {@code null} означает
 * "не переопределено", тогда действует обычный хардкод-дефолт самого
 * {@link Screen} (см. его инициализаторы полей).
 *
 * <p>{@link #cabinetTypeId}/{@link #mountType} — единственные два поля, которые
 * НЕ применяются через {@link #applyTo(Screen)}: для них в диалоге создания
 * экрана ({@code ui.NewScreenDialog}) и так есть явный выбор пользователя на
 * этот конкретный экран, который должен побеждать безусловно — вместо повторного
 * применения дефолта поверх они используются только чтобы ПРЕДЗАПОЛНИТЬ
 * соответствующие комбобоксы диалога, см. {@code NewScreenDialog}.
 */
public class ScreenDefaults {

    private String cabinetTypeId;
    private ScreenMountType mountType;
    private Integer refreshRateHz;
    private Integer colorBitDepth;
    private MaskColorPreset background;
    private ScreenTagColor tagColor;
    private Double riggingSafetyFactorMin;
    private String riggingHoistTypeId;
    private String riggingTrussProfileId;
    private Double structureTowerHeightMm;
    private Double structureBaseExtensionMm;
    private Double structureBallastRatio;
    private String structureFrameTypeId;
    private String structureCupTypeId;
    private String structureBallastTypeId;

    public String getCabinetTypeId() {
        return cabinetTypeId;
    }

    public void setCabinetTypeId(String cabinetTypeId) {
        this.cabinetTypeId = cabinetTypeId;
    }

    public ScreenMountType getMountType() {
        return mountType;
    }

    public void setMountType(ScreenMountType mountType) {
        this.mountType = mountType;
    }

    public Integer getRefreshRateHz() {
        return refreshRateHz;
    }

    public void setRefreshRateHz(Integer refreshRateHz) {
        this.refreshRateHz = refreshRateHz;
    }

    public Integer getColorBitDepth() {
        return colorBitDepth;
    }

    public void setColorBitDepth(Integer colorBitDepth) {
        this.colorBitDepth = colorBitDepth;
    }

    public MaskColorPreset getBackground() {
        return background;
    }

    public void setBackground(MaskColorPreset background) {
        this.background = background;
    }

    public ScreenTagColor getTagColor() {
        return tagColor;
    }

    public void setTagColor(ScreenTagColor tagColor) {
        this.tagColor = tagColor;
    }

    public Double getRiggingSafetyFactorMin() {
        return riggingSafetyFactorMin;
    }

    public void setRiggingSafetyFactorMin(Double riggingSafetyFactorMin) {
        this.riggingSafetyFactorMin = riggingSafetyFactorMin;
    }

    public String getRiggingHoistTypeId() {
        return riggingHoistTypeId;
    }

    public void setRiggingHoistTypeId(String riggingHoistTypeId) {
        this.riggingHoistTypeId = riggingHoistTypeId;
    }

    public String getRiggingTrussProfileId() {
        return riggingTrussProfileId;
    }

    public void setRiggingTrussProfileId(String riggingTrussProfileId) {
        this.riggingTrussProfileId = riggingTrussProfileId;
    }

    public Double getStructureTowerHeightMm() {
        return structureTowerHeightMm;
    }

    public void setStructureTowerHeightMm(Double structureTowerHeightMm) {
        this.structureTowerHeightMm = structureTowerHeightMm;
    }

    public Double getStructureBaseExtensionMm() {
        return structureBaseExtensionMm;
    }

    public void setStructureBaseExtensionMm(Double structureBaseExtensionMm) {
        this.structureBaseExtensionMm = structureBaseExtensionMm;
    }

    public Double getStructureBallastRatio() {
        return structureBallastRatio;
    }

    public void setStructureBallastRatio(Double structureBallastRatio) {
        this.structureBallastRatio = structureBallastRatio;
    }

    public String getStructureFrameTypeId() {
        return structureFrameTypeId;
    }

    public void setStructureFrameTypeId(String structureFrameTypeId) {
        this.structureFrameTypeId = structureFrameTypeId;
    }

    public String getStructureCupTypeId() {
        return structureCupTypeId;
    }

    public void setStructureCupTypeId(String structureCupTypeId) {
        this.structureCupTypeId = structureCupTypeId;
    }

    public String getStructureBallastTypeId() {
        return structureBallastTypeId;
    }

    public void setStructureBallastTypeId(String structureBallastTypeId) {
        this.structureBallastTypeId = structureBallastTypeId;
    }

    /** Переносит заданные (не {@code null}) поля на только что созданный экран —
     *  см. class-javadoc про то, почему {@link #cabinetTypeId}/{@link #mountType}
     *  сюда намеренно не входят. */
    public void applyTo(Screen s) {
        if (refreshRateHz != null) {
            s.setRefreshRateHz(refreshRateHz);
        }
        if (colorBitDepth != null) {
            s.setColorBitDepth(colorBitDepth);
        }
        if (background != null) {
            s.setBackground(background);
        }
        if (tagColor != null) {
            s.setTagColor(tagColor);
        }
        if (riggingSafetyFactorMin != null) {
            s.setRiggingSafetyFactorMin(riggingSafetyFactorMin);
        }
        if (riggingHoistTypeId != null) {
            s.setRiggingHoistTypeId(riggingHoistTypeId);
        }
        if (riggingTrussProfileId != null) {
            s.setRiggingTrussProfileId(riggingTrussProfileId);
        }
        if (structureTowerHeightMm != null) {
            s.setStructureTowerHeightMm(structureTowerHeightMm);
        }
        if (structureBaseExtensionMm != null) {
            s.setStructureBaseExtensionMm(structureBaseExtensionMm);
        }
        if (structureBallastRatio != null) {
            s.setStructureBallastRatio(structureBallastRatio);
        }
        if (structureFrameTypeId != null) {
            s.setStructureFrameTypeId(structureFrameTypeId);
        }
        if (structureCupTypeId != null) {
            s.setStructureCupTypeId(structureCupTypeId);
        }
        if (structureBallastTypeId != null) {
            s.setStructureBallastTypeId(structureBallastTypeId);
        }
    }
}
