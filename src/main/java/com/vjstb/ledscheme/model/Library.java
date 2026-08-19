package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Общие справочники (библиотека), не привязанные к конкретному проекту —
 * персистятся отдельно от {@link Workspace} (см. {@code store.LibraryStore}),
 * чтобы их можно было синхронизировать с сервером (v2.0) независимо от
 * проектов/сцен пользователя.
 */
public class Library {

    private List<CabinetType> cabinetTypes = new ArrayList<>();
    /** Библиотека типов контроллеров (аналог SmartLCT: модель, число портов, лимит на порт). */
    private List<ControllerType> controllerTypes = new ArrayList<>();
    /** Библиотека пресетов оборудования (медиасерверы/видеопроцессоры/конвертеры и т.д.)
     *  для быстрой вставки узлов общей схемы. */
    private List<EquipmentPreset> equipmentPresets = new ArrayList<>();
    /** Библиотека пользовательских кабелей/переходников (например «CEE 16A →
     *  TrueCON») — дополняет встроенные пресеты в WireLabelDialog/PowerConnectorsConfigDialog. */
    private List<CableType> cableTypes = new ArrayList<>();
    /** Справочник видов интерфейса (HDMI/DisplayPort/SDI/...) и их версий — общий
     *  для карт оборудования, кабелей и подписи связи схемы (см. InterfaceType). */
    private List<InterfaceType> interfaceTypes = new ArrayList<>();
    /** Каталоги доступных длин катушек по типам провода (см. CableLengthProfile) —
     *  для автоокругления в спецификации коммутации (service.CableSpecCalc). */
    private List<CableLengthProfile> cableLengthProfiles = new ArrayList<>();
    /** Библиотека моделей подъёмного оборудования (лебёдки/тали) с паспортной
     *  грузоподъёмностью (WLL) — см. HoistType, RIGGING_CALC_NOTES.md и
     *  {@code Screen#riggingHoistTypeId}. */
    private List<HoistType> hoistTypes = new ArrayList<>();
    /** Библиотека элементов наземного конструктива (рамы/короткие рамы/стаканы/
     *  контейнеры балласта) — см. StructureFrameType, STRUCTURE_CALC_NOTES.md и
     *  {@code Screen#structureFrameTypeId} и соседние FK-поля. */
    private List<StructureFrameType> structureFrameTypes = new ArrayList<>();
    /** Библиотека типов транспортировочных кофров (габариты/вес/лимит штабелирования) —
     *  см. CaseType и VEHICLE_CALC_NOTES.md. В отличие от hoistTypes/structureFrameTypes
     *  на эти id нет персистентных FK нигде в проектных данных. */
    private List<CaseType> caseTypes = new ArrayList<>();
    /** Библиотека типов машин (габариты кузова, грузоподъёмность) — см. VehicleType
     *  и VEHICLE_CALC_NOTES.md. */
    private List<VehicleType> vehicleTypes = new ArrayList<>();
    /** Подкатегории оборудования под "Прочее оборудование" — общая справочная
     *  данные (Task #135/v2.0), редактируется только через отдельную админ-консоль
     *  (ledscheme-admin), синхронизируется с сервера (вид EQUIPMENT_CUSTOM_CATEGORY,
     *  ключ карты — id записи на сервере, значение — текущее название) — см.
     *  EquipmentPreset.customCategoryLabel (которое хранит само название, не id;
     *  карта здесь нужна ТОЛЬКО чтобы переименование по id на сервере корректно
     *  подменяло старое название при повторном синке, а не плодило дубликат).
     *  Заменяет прежнее локальное customEquipmentCategories (Task #106/v1.6) —
     *  категория оборудования — это общий справочник, а не настройка воркспейса. */
    private Map<String, String> serverCustomEquipmentCategoriesById = new LinkedHashMap<>();
    /** Переопределение ПОДПИСИ встроенной категории оборудования (см. SchemaNodeType),
     *  ключ — имя константы enum (SOURCE, DISTRO, ...), значение — новый текст,
     *  который видит пользователь (см. AppModel.categoryLabel). Общая справочная
     *  данные, синхронизируется с сервера (синглтон-вид EQUIPMENT_CATEGORY_LABELS) —
     *  заменяет прежнее локальное equipmentCategoryLabelOverrides (Task #106/v1.6). */
    private Map<String, String> serverEquipmentCategoryLabels = new LinkedHashMap<>();
    /** Текст «Руководства»/«Приветствия» — общая справочная данные, синхронизируется
     *  с сервера (синглтон-виды GUIDE_TEXT/ONBOARDING_TEXT), редактируется только
     *  через ledscheme-admin (Task #135/v2.0). Пусто, пока ничего не синхронизировано —
     *  тогда GuideDialog/OnboardingDialog показывают свои встроенные DEFAULT_SECTIONS. */
    private List<ContentSection> guideSections = new ArrayList<>();
    private List<ContentSection> onboardingSections = new ArrayList<>();
    /** Пошаговые интерактивные сценарии (см. {@link Scenario}) — та же общая
     *  справочная данные, что и guideSections/onboardingSections, синглтон-вид
     *  INTERACTIVE_SCENARIOS, редактируется только через ledscheme-admin. */
    private List<Scenario> interactiveScenarios = new ArrayList<>();

    /** Общая библиотека компании ("GTO") — кэш последней синхронизации с сервером
     *  (Task #135/v2.0: "личная библиотека у каждого нулевая"), read-only в клиенте
     *  (редактируется только через ledscheme-admin либо через "Предложить…" →
     *  одобрение). {@code cabinetTypes}/{@code controllerTypes}/... выше — ЛИЧНАЯ
     *  библиотека, свободно редактируется, стартует пустой у каждого пользователя;
     *  когда личная запись "продвигается" (одобрено предложение/то же имя пришло
     *  с сервера), она переезжает сюда и пропадает из личного списка — см.
     *  AppModel.applyOne. Объединённый вид для UI/пикеров — AppModel.getCabinetTypes()
     *  и аналоги (личное ++ общее). */
    private List<CabinetType> sharedCabinetTypes = new ArrayList<>();
    private List<ControllerType> sharedControllerTypes = new ArrayList<>();
    private List<EquipmentPreset> sharedEquipmentPresets = new ArrayList<>();
    private List<CableType> sharedCableTypes = new ArrayList<>();
    private List<InterfaceType> sharedInterfaceTypes = new ArrayList<>();
    private List<CableLengthProfile> sharedCableLengthProfiles = new ArrayList<>();
    private List<HoistType> sharedHoistTypes = new ArrayList<>();
    private List<StructureFrameType> sharedStructureFrameTypes = new ArrayList<>();
    private List<CaseType> sharedCaseTypes = new ArrayList<>();
    private List<VehicleType> sharedVehicleTypes = new ArrayList<>();

    public List<CabinetType> getCabinetTypes() {
        return cabinetTypes;
    }

    public void setCabinetTypes(List<CabinetType> cabinetTypes) {
        this.cabinetTypes = cabinetTypes;
    }

    public List<ControllerType> getControllerTypes() {
        return controllerTypes;
    }

    public void setControllerTypes(List<ControllerType> controllerTypes) {
        this.controllerTypes = controllerTypes;
    }

    public List<EquipmentPreset> getEquipmentPresets() {
        return equipmentPresets;
    }

    public void setEquipmentPresets(List<EquipmentPreset> equipmentPresets) {
        this.equipmentPresets = equipmentPresets;
    }

    public List<CableType> getCableTypes() {
        return cableTypes;
    }

    public void setCableTypes(List<CableType> cableTypes) {
        this.cableTypes = cableTypes;
    }

    public List<InterfaceType> getInterfaceTypes() {
        return interfaceTypes;
    }

    public void setInterfaceTypes(List<InterfaceType> interfaceTypes) {
        this.interfaceTypes = interfaceTypes;
    }

    public List<CableLengthProfile> getCableLengthProfiles() {
        return cableLengthProfiles;
    }

    public void setCableLengthProfiles(List<CableLengthProfile> cableLengthProfiles) {
        this.cableLengthProfiles = cableLengthProfiles;
    }

    public List<HoistType> getHoistTypes() {
        return hoistTypes;
    }

    public void setHoistTypes(List<HoistType> hoistTypes) {
        this.hoistTypes = hoistTypes;
    }

    public List<StructureFrameType> getStructureFrameTypes() {
        return structureFrameTypes;
    }

    public void setStructureFrameTypes(List<StructureFrameType> structureFrameTypes) {
        this.structureFrameTypes = structureFrameTypes;
    }

    public List<CaseType> getCaseTypes() {
        return caseTypes;
    }

    public void setCaseTypes(List<CaseType> caseTypes) {
        this.caseTypes = caseTypes;
    }

    public List<VehicleType> getVehicleTypes() {
        return vehicleTypes;
    }

    public void setVehicleTypes(List<VehicleType> vehicleTypes) {
        this.vehicleTypes = vehicleTypes;
    }

    public Map<String, String> getServerCustomEquipmentCategoriesById() {
        return serverCustomEquipmentCategoriesById;
    }

    public void setServerCustomEquipmentCategoriesById(Map<String, String> serverCustomEquipmentCategoriesById) {
        this.serverCustomEquipmentCategoriesById = serverCustomEquipmentCategoriesById;
    }

    public Map<String, String> getServerEquipmentCategoryLabels() {
        return serverEquipmentCategoryLabels;
    }

    public void setServerEquipmentCategoryLabels(Map<String, String> serverEquipmentCategoryLabels) {
        this.serverEquipmentCategoryLabels = serverEquipmentCategoryLabels;
    }

    public List<ContentSection> getGuideSections() {
        return guideSections;
    }

    public void setGuideSections(List<ContentSection> guideSections) {
        this.guideSections = guideSections;
    }

    public List<ContentSection> getOnboardingSections() {
        return onboardingSections;
    }

    public void setOnboardingSections(List<ContentSection> onboardingSections) {
        this.onboardingSections = onboardingSections;
    }

    public List<Scenario> getInteractiveScenarios() {
        return interactiveScenarios;
    }

    public void setInteractiveScenarios(List<Scenario> interactiveScenarios) {
        this.interactiveScenarios = interactiveScenarios;
    }

    public List<CabinetType> getSharedCabinetTypes() {
        return sharedCabinetTypes;
    }

    public void setSharedCabinetTypes(List<CabinetType> sharedCabinetTypes) {
        this.sharedCabinetTypes = sharedCabinetTypes;
    }

    public List<ControllerType> getSharedControllerTypes() {
        return sharedControllerTypes;
    }

    public void setSharedControllerTypes(List<ControllerType> sharedControllerTypes) {
        this.sharedControllerTypes = sharedControllerTypes;
    }

    public List<EquipmentPreset> getSharedEquipmentPresets() {
        return sharedEquipmentPresets;
    }

    public void setSharedEquipmentPresets(List<EquipmentPreset> sharedEquipmentPresets) {
        this.sharedEquipmentPresets = sharedEquipmentPresets;
    }

    public List<CableType> getSharedCableTypes() {
        return sharedCableTypes;
    }

    public void setSharedCableTypes(List<CableType> sharedCableTypes) {
        this.sharedCableTypes = sharedCableTypes;
    }

    public List<InterfaceType> getSharedInterfaceTypes() {
        return sharedInterfaceTypes;
    }

    public void setSharedInterfaceTypes(List<InterfaceType> sharedInterfaceTypes) {
        this.sharedInterfaceTypes = sharedInterfaceTypes;
    }

    public List<CableLengthProfile> getSharedCableLengthProfiles() {
        return sharedCableLengthProfiles;
    }

    public void setSharedCableLengthProfiles(List<CableLengthProfile> sharedCableLengthProfiles) {
        this.sharedCableLengthProfiles = sharedCableLengthProfiles;
    }

    public List<HoistType> getSharedHoistTypes() {
        return sharedHoistTypes;
    }

    public void setSharedHoistTypes(List<HoistType> sharedHoistTypes) {
        this.sharedHoistTypes = sharedHoistTypes;
    }

    public List<StructureFrameType> getSharedStructureFrameTypes() {
        return sharedStructureFrameTypes;
    }

    public void setSharedStructureFrameTypes(List<StructureFrameType> sharedStructureFrameTypes) {
        this.sharedStructureFrameTypes = sharedStructureFrameTypes;
    }

    public List<CaseType> getSharedCaseTypes() {
        return sharedCaseTypes;
    }

    public void setSharedCaseTypes(List<CaseType> sharedCaseTypes) {
        this.sharedCaseTypes = sharedCaseTypes;
    }

    public List<VehicleType> getSharedVehicleTypes() {
        return sharedVehicleTypes;
    }

    public void setSharedVehicleTypes(List<VehicleType> sharedVehicleTypes) {
        this.sharedVehicleTypes = sharedVehicleTypes;
    }

    /** Общее, затем личное — см. class-javadoc про общую/личную библиотеку. */
    public CabinetType cabinetTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (CabinetType ct : sharedCabinetTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        for (CabinetType ct : cabinetTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        return null;
    }

    public ControllerType controllerTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (ControllerType ct : sharedControllerTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        for (ControllerType ct : controllerTypes) {
            if (ct.getId().equals(id)) {
                return ct;
            }
        }
        return null;
    }

    /** Общее, затем личное — см. class-javadoc про общую/личную библиотеку. */
    public HoistType hoistTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (HoistType h : sharedHoistTypes) {
            if (h.getId().equals(id)) {
                return h;
            }
        }
        for (HoistType h : hoistTypes) {
            if (h.getId().equals(id)) {
                return h;
            }
        }
        return null;
    }

    /** Общее, затем личное — см. class-javadoc про общую/личную библиотеку. */
    public StructureFrameType structureFrameTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (StructureFrameType t : sharedStructureFrameTypes) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        for (StructureFrameType t : structureFrameTypes) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Общее, затем личное — см. class-javadoc про общую/личную библиотеку. */
    public CaseType caseTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (CaseType c : sharedCaseTypes) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        for (CaseType c : caseTypes) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Общее, затем личное — см. class-javadoc про общую/личную библиотеку. */
    public VehicleType vehicleTypeById(String id) {
        if (id == null) {
            return null;
        }
        for (VehicleType v : sharedVehicleTypes) {
            if (v.getId().equals(id)) {
                return v;
            }
        }
        for (VehicleType v : vehicleTypes) {
            if (v.getId().equals(id)) {
                return v;
            }
        }
        return null;
    }
}
