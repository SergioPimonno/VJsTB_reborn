package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран составляется из сетки rows x cols однотипных LED cabinets.
 * Ссылается на тип кабинета из библиотеки по cabinetTypeId; характеристики
 * (разрешение, габариты, вес, потребление) вычисляются из типа и размера сетки.
 */
public class Screen {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String cabinetTypeId;
    private int rows;
    private int cols;
    /** Позиция левого верхнего угла экрана в сцене, мм. */
    private double posXMm;
    private double posYMm;
    /** Количество портов контроллера, доступных для расключения сигнала на этом экране. */
    private int signalPortCount = 8;
    /** Герцовка контента, Гц — влияет на реальную ёмкость порта контроллера в пикселях. */
    private int refreshRateHz = 60;
    /** Глубина цвета на канал, бит (8/10/12) — тоже влияет на ёмкость порта. */
    private int colorBitDepth = 8;

    /** Цвет чек-борда маски этого экрана (см. {@code ui.PixelGridRenderer}) — ОДИН на
     *  экран, применяется одинаково во ВСЕХ канвасах, где этот экран размещён (2026-08-13,
     *  баг-репорт: раньше цвет хранился per-{@code CanvasPlacement}, и один и тот же
     *  экран мог показывать разный цвет маски в разных канвасах — см. class-javadoc
     *  {@code CanvasPlacement} про историю этого поля). Меняется через
     *  {@code AppModel#setMaskColor}, не напрямую сеттером. */
    private MaskColorPreset background = MaskColorPreset.NORMAL;

    /** Способ монтажа — влияет на применимость расчёта точек подвеса. */
    private ScreenMountType mountType = ScreenMountType.RIGGED;
    /** Количество точек подвеса — авторасчёт см. {@code service.RiggingCalc}/
     *  {@code ScreenLogic#suggestRiggingPoints}, либо вручную скорректировано
     *  пользователем под конкретную ферму/траверс. */
    private int riggingPointsCount = 0;
    private String riggingNotes;
    /** Минимальный требуемый коэффициент запаса прочности сертификации подъёмного
     *  оборудования (5:1 — типовой минимум для временного шоу-монтажа по PLASA/
     *  ESTA, 8:1+ — для стационарных/публичных инсталляций) — ТРЕБОВАНИЕ к тому,
     *  на что должно быть рассчитано выбранное оборудование (breaking strength ÷
     *  WLL), НЕ множитель, накручиваемый на вычисленную нагрузку поверх — WLL,
     *  указанный производителем/сертификатом, уже учитывает этот запас. Расчёт
     *  (см. {@code service.RiggingCalc#compute}) сравнивает нагрузку точки
     *  НАПРЯМУЮ с {@link #riggingHoistCapacityKg}, этот коэффициент — только
     *  напоминание/фильтр при выборе конкретной модели лебёдки/тали. */
    private double riggingSafetyFactorMin = 5.0;
    /** Грузоподъёмность (WLL, кг) реально выбранной лебёдки/тали — ОДНА модель на
     *  все точки подвеса этого экрана (типовая практика: одинаковое оборудование
     *  на всех точках одного экрана). null — не указана, расчёт покажет только
     *  нагрузку по точкам без проверки превышения. FALLBACK для проектов,
     *  сохранённых до появления {@link #riggingHoistTypeId} — если тот задан, он
     *  побеждает (см. {@code service.RiggingCalc#effectiveHoistCapacityKg}), это
     *  поле игнорируется, но НЕ стирается (позволяет откатиться, если библиотечная
     *  запись впоследствии удалена на сервере). */
    private Double riggingHoistCapacityKg;
    /** FK на {@code HoistType} общей библиотеки (см. RIGGING_CALC_NOTES.md) — по
     *  образцу {@link #cabinetTypeId}. null — оборудование не выбрано из каталога,
     *  используется {@link #riggingHoistCapacityKg} напрямую. */
    private String riggingHoistTypeId;

    /** Наземный конструктив (см. {@code service.StructureCalc}, STRUCTURE_CALC_NOTES.md) —
     *  поля ниже осмысленны только при {@link #mountType} == STRUCTURE, тот же принцип, что
     *  и riggingXxx выше для RIGGED. Каждое число — авторасчитанный ДЕФОЛТ, который
     *  пользователь может напрямую переопределить (по явному требованию: сложные раскладки
     *  инженер чертит в Vectorworks и считает сам, калькулятор — только отправная точка). */
    private double structureTowerHeightMm = 3000;
    private int structureTowerCount = 0;
    private int structureVerticalFramesPerTower = 0;
    /** Число сегментов ЗАДНЕГО ряда (row=1) — Round 5, баг-репорт с фото реальной башни: с
     *  фронта видна ОДНА полноразмерная лестничная рама (передний ряд, {@code
     *  structureVerticalFramesPerTower}), задний ряд — короткий (~1-2м), просто опора для
     *  перемычек и выноса, НЕ полноразмерная вторая башня. Независим от переднего ряда, номинальная
     *  граница сетки для {@code service.ScreenLogic#regenerateStructureCells}. */
    private int structureBackRowSegments = 0;
    /** Число уровней перемычек (передний↔задний ряд внутри одной башни) по высоте — номинальная
     *  граница сетки для {@code service.ScreenLogic#regenerateStructureCells}, шаг между уровнями
     *  не хранится тут (Phase 2.2 — коэффициент 1.5 от высоты рамы, см. {@code service
     *  .StructureCalc#peremychkaIntervalMm}), только предложенное количество. */
    private int structurePeremychkaLevels = 0;
    /** Число ДОПОЛНИТЕЛЬНЫХ секций выноса базовой рамы под балласт (сверх обязательной секции
     *  0 — ядра под объёмом башни), каждая — ширина ОБЫЧНОЙ рамы (Round 16 — "короткая рама"
     *  как отдельный тип упразднена, см. {@code service.StructureCalc} class-javadoc, {@code
     *  ui.Structure3DPanel#computeGeometry}: перемычка/база/усилительные рамы делаются из того
     *  же {@link #structureFrameTypeId}, что и вертикальные рамы башни; количество реальных
     *  коротких рам, если инженер решит их использовать на месте, в модель не входит — только
     *  per-cell override конкретной ячейки, см. {@code StructureFrameCell#getFrameTypeId()}). */
    private int structureExtendedBaseSections = 0;
    /** «Вынос базы под балласт», мм — насколько дополнительно (сверх обязательного ядра под
     *  объёмом самой башни) базовая рама выступает вперёд-назад, чтобы под ней уместился
     *  нужный балласт (площадь опоры = рычаг устойчивости). Раньше здесь была НОМИНАЛЬНАЯ
     *  эвристика по высоте/ширине экрана ({@code StructureCalc#suggestExtendedBaseSections},
     *  удалена 2026-08-19) — по прямому указанию пользователя: "аппаратно не получается его
     *  просчитывать эффективно, будем указывать руками", заменяет прежний параметр «Шаг
     *  башен» в той же секции формы (тот, как выяснилось, реально ни на что не влиял — см.
     *  STRUCTURE_CALC_NOTES.md). {@link #structureExtendedBaseSections} (число реальных
     *  секций) теперь ВЫВОДИТСЯ из этого мм-значения делением на глубину модуля рамы, см.
     *  {@code AppModel#updateScreenStructure}. */
    private double structureBaseExtensionMm = 500;
    /** Коэффициент отношения требуемого веса балласта к суммарному весу отгружаемого экрана
     *  (см. {@code StructureCalc#compute}) — по прямому указанию пользователя: раньше было
     *  жёстко зашито 1:1 (коэффициент 1.0), но "в реальности хорошо если 6:10" — теперь
     *  редактируемый коэффициент вместо константы, дефолт 0.6 (6:10). */
    private double structureBallastRatio = 0.6;
    private String structureFrameTypeId;
    private String structureCupTypeId;
    private String structureBallastTypeId;
    /** Высота нижнего края экрана над землёй, мм — 0 = экран на земле. Влияет только на
     *  отображение в 3D (виден зазор между истинной землёй, на которой стоит основание башни,
     *  и приподнятым экраном) — см. {@code ui.Structure3DPanel}. НЕ участвует в проверке
     *  «высота башни строго меньше высоты экрана» (см. {@code service.StructureCalc#compute}) —
     *  та сравнивается с СОБСТВЕННОЙ высотой экрана, не с его положением над землёй. */
    private double structureScreenElevationMm;
    private String structureNotes;
    /** Реально существующие вертикальные сегменты/перемычки/секции базовых рам конструктива
     *  (Phase 2 — интерактивный 3D-редактор, Phase 2.1 — объёмная башня, см.
     *  STRUCTURE_CALC_NOTES.md) — источник истины о том, что физически стоит,
     *  structureTowerCount/VerticalFramesPerTower/PeremychkaLevels/ExtendedBaseSections выше
     *  остаются НОМИНАЛЬНЫМИ границами сетки для «Рассчитать конструктив» (см.
     *  {@code service.ScreenLogic#regenerateStructureCells}), а не однородной формулой
     *  количества — тот же принцип, что {@link #cabinets} для формы экрана. Меняются через
     *  {@code AppModel#toggleStructureFrameCell}/{@code toggleStructurePeremychkaCell}/
     *  {@code toggleStructureBaseFrameSection}, не напрямую сеттером. */
    private List<StructureFrameCell> structureFrameCells = new ArrayList<>();
    private List<StructurePeremychkaCell> structurePeremychkaCells = new ArrayList<>();
    private List<StructureBaseFrameCell> structureBaseFrameCells = new ArrayList<>();

    /** Контроллеры, обслуживающие экран (может быть несколько). Если список не пуст,
     *  суммарное число их портов определяет доступные порты сигнала вместо signalPortCount. */
    private List<ControllerInstance> controllers = new ArrayList<>();

    private List<CabinetInstance> cabinets = new ArrayList<>();
    private List<PowerChain> powerChains = new ArrayList<>();
    private List<SignalChain> signalChains = new ArrayList<>();

    public Screen() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCabinetTypeId() {
        return cabinetTypeId;
    }

    public void setCabinetTypeId(String cabinetTypeId) {
        this.cabinetTypeId = cabinetTypeId;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public double getPosXMm() {
        return posXMm;
    }

    public void setPosXMm(double posXMm) {
        this.posXMm = posXMm;
    }

    public double getPosYMm() {
        return posYMm;
    }

    public void setPosYMm(double posYMm) {
        this.posYMm = posYMm;
    }

    public int getSignalPortCount() {
        return signalPortCount;
    }

    public void setSignalPortCount(int signalPortCount) {
        this.signalPortCount = signalPortCount;
    }

    public int getRefreshRateHz() {
        return refreshRateHz;
    }

    public void setRefreshRateHz(int refreshRateHz) {
        this.refreshRateHz = refreshRateHz;
    }

    public int getColorBitDepth() {
        return colorBitDepth;
    }

    public void setColorBitDepth(int colorBitDepth) {
        this.colorBitDepth = colorBitDepth;
    }

    public MaskColorPreset getBackground() {
        return background != null ? background : MaskColorPreset.NORMAL;
    }

    public void setBackground(MaskColorPreset background) {
        this.background = background != null ? background : MaskColorPreset.NORMAL;
    }

    public ScreenMountType getMountType() {
        return mountType;
    }

    public void setMountType(ScreenMountType mountType) {
        this.mountType = mountType;
    }

    public int getRiggingPointsCount() {
        return riggingPointsCount;
    }

    public void setRiggingPointsCount(int riggingPointsCount) {
        this.riggingPointsCount = riggingPointsCount;
    }

    public String getRiggingNotes() {
        return riggingNotes;
    }

    public void setRiggingNotes(String riggingNotes) {
        this.riggingNotes = riggingNotes;
    }

    public double getRiggingSafetyFactorMin() {
        return riggingSafetyFactorMin > 0 ? riggingSafetyFactorMin : 5.0;
    }

    public void setRiggingSafetyFactorMin(double riggingSafetyFactorMin) {
        this.riggingSafetyFactorMin = riggingSafetyFactorMin > 0 ? riggingSafetyFactorMin : 5.0;
    }

    public Double getRiggingHoistCapacityKg() {
        return riggingHoistCapacityKg;
    }

    public void setRiggingHoistCapacityKg(Double riggingHoistCapacityKg) {
        this.riggingHoistCapacityKg = riggingHoistCapacityKg;
    }

    public String getRiggingHoistTypeId() {
        return riggingHoistTypeId;
    }

    public void setRiggingHoistTypeId(String riggingHoistTypeId) {
        this.riggingHoistTypeId = riggingHoistTypeId;
    }

    public double getStructureTowerHeightMm() {
        return structureTowerHeightMm;
    }

    public void setStructureTowerHeightMm(double structureTowerHeightMm) {
        this.structureTowerHeightMm = structureTowerHeightMm;
    }

    public int getStructureTowerCount() {
        return structureTowerCount;
    }

    public void setStructureTowerCount(int structureTowerCount) {
        this.structureTowerCount = Math.max(0, structureTowerCount);
    }

    public int getStructureVerticalFramesPerTower() {
        return structureVerticalFramesPerTower;
    }

    public void setStructureVerticalFramesPerTower(int structureVerticalFramesPerTower) {
        this.structureVerticalFramesPerTower = Math.max(0, structureVerticalFramesPerTower);
    }

    public int getStructureBackRowSegments() {
        return structureBackRowSegments;
    }

    public void setStructureBackRowSegments(int structureBackRowSegments) {
        this.structureBackRowSegments = Math.max(0, structureBackRowSegments);
    }

    public int getStructurePeremychkaLevels() {
        return structurePeremychkaLevels;
    }

    public void setStructurePeremychkaLevels(int structurePeremychkaLevels) {
        this.structurePeremychkaLevels = Math.max(0, structurePeremychkaLevels);
    }

    public int getStructureExtendedBaseSections() {
        return structureExtendedBaseSections;
    }

    public void setStructureExtendedBaseSections(int structureExtendedBaseSections) {
        this.structureExtendedBaseSections = Math.max(0, structureExtendedBaseSections);
    }

    public double getStructureBaseExtensionMm() {
        return structureBaseExtensionMm;
    }

    public void setStructureBaseExtensionMm(double structureBaseExtensionMm) {
        this.structureBaseExtensionMm = Math.max(0, structureBaseExtensionMm);
    }

    public double getStructureBallastRatio() {
        return structureBallastRatio > 0 ? structureBallastRatio : 0.6;
    }

    public void setStructureBallastRatio(double structureBallastRatio) {
        this.structureBallastRatio = structureBallastRatio > 0 ? structureBallastRatio : 0.6;
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

    public double getStructureScreenElevationMm() {
        return structureScreenElevationMm;
    }

    public void setStructureScreenElevationMm(double structureScreenElevationMm) {
        this.structureScreenElevationMm = Math.max(0, structureScreenElevationMm);
    }

    public String getStructureNotes() {
        return structureNotes;
    }

    public void setStructureNotes(String structureNotes) {
        this.structureNotes = structureNotes;
    }

    public List<StructureFrameCell> getStructureFrameCells() {
        return structureFrameCells;
    }

    public void setStructureFrameCells(List<StructureFrameCell> structureFrameCells) {
        this.structureFrameCells = structureFrameCells != null ? structureFrameCells : new ArrayList<>();
    }

    public List<StructurePeremychkaCell> getStructurePeremychkaCells() {
        return structurePeremychkaCells;
    }

    public void setStructurePeremychkaCells(List<StructurePeremychkaCell> structurePeremychkaCells) {
        this.structurePeremychkaCells = structurePeremychkaCells != null ? structurePeremychkaCells : new ArrayList<>();
    }

    public List<StructureBaseFrameCell> getStructureBaseFrameCells() {
        return structureBaseFrameCells;
    }

    public void setStructureBaseFrameCells(List<StructureBaseFrameCell> structureBaseFrameCells) {
        this.structureBaseFrameCells = structureBaseFrameCells != null ? structureBaseFrameCells : new ArrayList<>();
    }

    public List<ControllerInstance> getControllers() {
        return controllers;
    }

    public void setControllers(List<ControllerInstance> controllers) {
        this.controllers = controllers;
    }

    public List<CabinetInstance> getCabinets() {
        return cabinets;
    }

    public void setCabinets(List<CabinetInstance> cabinets) {
        this.cabinets = cabinets;
    }

    public List<PowerChain> getPowerChains() {
        return powerChains;
    }

    public void setPowerChains(List<PowerChain> powerChains) {
        this.powerChains = powerChains;
    }

    public List<SignalChain> getSignalChains() {
        return signalChains;
    }

    public void setSignalChains(List<SignalChain> signalChains) {
        this.signalChains = signalChains;
    }

    // ---- вспомогательное ----

    @JsonIgnore
    public CabinetInstance cabinetById(String cabId) {
        for (CabinetInstance c : cabinets) {
            if (c.getId().equals(cabId)) {
                return c;
            }
        }
        return null;
    }

    @JsonIgnore
    public CabinetInstance cabinetAt(int row, int col) {
        for (CabinetInstance c : cabinets) {
            if (c.getRowIndex() == row && c.getColIndex() == col) {
                return c;
            }
        }
        return null;
    }

    public Screen copy() {
        Screen s = new Screen();
        s.id = id;
        s.name = name;
        s.cabinetTypeId = cabinetTypeId;
        s.rows = rows;
        s.cols = cols;
        s.posXMm = posXMm;
        s.posYMm = posYMm;
        s.signalPortCount = signalPortCount;
        s.refreshRateHz = refreshRateHz;
        s.colorBitDepth = colorBitDepth;
        s.background = background;
        s.mountType = mountType;
        s.riggingPointsCount = riggingPointsCount;
        s.riggingNotes = riggingNotes;
        s.riggingSafetyFactorMin = riggingSafetyFactorMin;
        s.riggingHoistCapacityKg = riggingHoistCapacityKg;
        s.riggingHoistTypeId = riggingHoistTypeId;
        s.structureTowerHeightMm = structureTowerHeightMm;
        s.structureTowerCount = structureTowerCount;
        s.structureVerticalFramesPerTower = structureVerticalFramesPerTower;
        s.structureBackRowSegments = structureBackRowSegments;
        s.structurePeremychkaLevels = structurePeremychkaLevels;
        s.structureExtendedBaseSections = structureExtendedBaseSections;
        s.structureBaseExtensionMm = structureBaseExtensionMm;
        s.structureBallastRatio = structureBallastRatio;
        s.structureFrameTypeId = structureFrameTypeId;
        s.structureCupTypeId = structureCupTypeId;
        s.structureBallastTypeId = structureBallastTypeId;
        s.structureScreenElevationMm = structureScreenElevationMm;
        s.structureNotes = structureNotes;
        s.structureFrameCells = new ArrayList<>();
        for (StructureFrameCell c : structureFrameCells) {
            // Баг-репорт (найден при переносе на объёмную башню): 2-арг конструктор не сохранял
            // hidden -- undo-снимок/черновая копия (см. calculateStructure()) молча "возвращали"
            // все убранные пользователем ячейки. Копируем через сеттер, чтобы hidden выжил.
            StructureFrameCell copy = new StructureFrameCell(c.getTowerIndex(), c.getRow(), c.getSegmentIndex());
            copy.setHidden(c.isHidden());
            copy.setFrameTypeId(c.getFrameTypeId());
            s.structureFrameCells.add(copy);
        }
        s.structurePeremychkaCells = new ArrayList<>();
        for (StructurePeremychkaCell c : structurePeremychkaCells) {
            StructurePeremychkaCell copy = new StructurePeremychkaCell(c.getTowerIndex(), c.getRow(), c.getLevelIndex());
            copy.setHidden(c.isHidden());
            s.structurePeremychkaCells.add(copy);
        }
        s.structureBaseFrameCells = new ArrayList<>();
        for (StructureBaseFrameCell c : structureBaseFrameCells) {
            StructureBaseFrameCell copy = new StructureBaseFrameCell(c.getTowerIndex(), c.getSectionIndex());
            copy.setHidden(c.isHidden());
            s.structureBaseFrameCells.add(copy);
        }
        s.controllers = new ArrayList<>();
        for (ControllerInstance c : controllers) {
            s.controllers.add(c.copy());
        }
        s.cabinets = new ArrayList<>();
        for (CabinetInstance c : cabinets) {
            s.cabinets.add(c.copy());
        }
        // powerChains/signalChains НЕ копируются здесь — цепочки хранятся на уровне
        // сцены, а не экрана (см. Task #78); эти поля на Screen остаются только для
        // чтения СТАРЫХ файлов проектов (миграция в WorkspaceStore переносит их на
        // сцену при загрузке), копия экрана (используется для undo-снимка) их больше
        // не касается — AppModel.undo() снимает/восстанавливает цепочки сцены отдельно.
        return s;
    }
}
