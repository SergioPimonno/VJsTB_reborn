package com.vjstb.ledscheme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Корневой контейнер данных приложения: проекты пользователя. Сериализуется в
 * локальный JSON-файл (см. store.WorkspaceStore) отдельно от общей библиотеки
 * (см. {@link Library}/store.LibraryStore) — разделение нужно для будущей
 * синхронизации библиотеки с сервером (v2.0) независимо от проектов.
 *
 * <p>Библиотечные геттеры/сеттеры ниже сохранены с прежними сигнатурами и просто
 * делегируют во внутренний {@link #library} — это позволяет всему остальному
 * коду (UI/рендеринг/экспорт), который годами обращался к
 * {@code workspace.getCabinetTypes()}/{@code cabinetTypeById()} напрямую,
 * продолжать работать без изменений после разделения хранения на два файла.
 * Jackson определяет сериализуемые свойства по ИМЕНАМ get/set-методов, а не по
 * полям — поэтому одного {@code @JsonIgnore} на поле {@link #library} мало:
 * делегирующие методы всё равно создают для Jackson свойства "cabinetTypes" и
 * т.д. Явно перечисляем их в {@link JsonIgnoreProperties}, иначе они снова
 * попадут в workspace.json прямо через геттеры, в обход поля.
 */
@JsonIgnoreProperties({"cabinetTypes", "controllerTypes", "equipmentPresets", "cableTypes", "interfaceTypes",
        "cableLengthProfiles", "hoistTypes", "structureFrameTypes",
        "sharedCabinetTypes", "sharedControllerTypes", "sharedEquipmentPresets", "sharedCableTypes",
        "sharedInterfaceTypes", "sharedCableLengthProfiles", "sharedHoistTypes", "sharedStructureFrameTypes",
        "customEquipmentCategories", "equipmentCategoryLabelOverrides"})
public class Workspace {

    @JsonIgnore
    private Library library = new Library();
    private List<Project> projects = new ArrayList<>();

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library == null ? new Library() : library;
    }

    public List<CabinetType> getCabinetTypes() {
        return library.getCabinetTypes();
    }

    public void setCabinetTypes(List<CabinetType> cabinetTypes) {
        library.setCabinetTypes(cabinetTypes);
    }

    public List<ControllerType> getControllerTypes() {
        return library.getControllerTypes();
    }

    public void setControllerTypes(List<ControllerType> controllerTypes) {
        library.setControllerTypes(controllerTypes);
    }

    public List<EquipmentPreset> getEquipmentPresets() {
        return library.getEquipmentPresets();
    }

    public void setEquipmentPresets(List<EquipmentPreset> equipmentPresets) {
        library.setEquipmentPresets(equipmentPresets);
    }

    public List<CableType> getCableTypes() {
        return library.getCableTypes();
    }

    public void setCableTypes(List<CableType> cableTypes) {
        library.setCableTypes(cableTypes);
    }

    public List<InterfaceType> getInterfaceTypes() {
        return library.getInterfaceTypes();
    }

    public void setInterfaceTypes(List<InterfaceType> interfaceTypes) {
        library.setInterfaceTypes(interfaceTypes);
    }

    public List<CableLengthProfile> getCableLengthProfiles() {
        return library.getCableLengthProfiles();
    }

    public void setCableLengthProfiles(List<CableLengthProfile> cableLengthProfiles) {
        library.setCableLengthProfiles(cableLengthProfiles);
    }

    public List<HoistType> getHoistTypes() {
        return library.getHoistTypes();
    }

    public void setHoistTypes(List<HoistType> hoistTypes) {
        library.setHoistTypes(hoistTypes);
    }

    public List<StructureFrameType> getStructureFrameTypes() {
        return library.getStructureFrameTypes();
    }

    public void setStructureFrameTypes(List<StructureFrameType> structureFrameTypes) {
        library.setStructureFrameTypes(structureFrameTypes);
    }

    public List<CabinetType> getSharedCabinetTypes() {
        return library.getSharedCabinetTypes();
    }

    public void setSharedCabinetTypes(List<CabinetType> sharedCabinetTypes) {
        library.setSharedCabinetTypes(sharedCabinetTypes);
    }

    public List<ControllerType> getSharedControllerTypes() {
        return library.getSharedControllerTypes();
    }

    public void setSharedControllerTypes(List<ControllerType> sharedControllerTypes) {
        library.setSharedControllerTypes(sharedControllerTypes);
    }

    public List<EquipmentPreset> getSharedEquipmentPresets() {
        return library.getSharedEquipmentPresets();
    }

    public void setSharedEquipmentPresets(List<EquipmentPreset> sharedEquipmentPresets) {
        library.setSharedEquipmentPresets(sharedEquipmentPresets);
    }

    public List<CableType> getSharedCableTypes() {
        return library.getSharedCableTypes();
    }

    public void setSharedCableTypes(List<CableType> sharedCableTypes) {
        library.setSharedCableTypes(sharedCableTypes);
    }

    public List<InterfaceType> getSharedInterfaceTypes() {
        return library.getSharedInterfaceTypes();
    }

    public void setSharedInterfaceTypes(List<InterfaceType> sharedInterfaceTypes) {
        library.setSharedInterfaceTypes(sharedInterfaceTypes);
    }

    public List<CableLengthProfile> getSharedCableLengthProfiles() {
        return library.getSharedCableLengthProfiles();
    }

    public void setSharedCableLengthProfiles(List<CableLengthProfile> sharedCableLengthProfiles) {
        library.setSharedCableLengthProfiles(sharedCableLengthProfiles);
    }

    public List<HoistType> getSharedHoistTypes() {
        return library.getSharedHoistTypes();
    }

    public void setSharedHoistTypes(List<HoistType> sharedHoistTypes) {
        library.setSharedHoistTypes(sharedHoistTypes);
    }

    public List<StructureFrameType> getSharedStructureFrameTypes() {
        return library.getSharedStructureFrameTypes();
    }

    public void setSharedStructureFrameTypes(List<StructureFrameType> sharedStructureFrameTypes) {
        library.setSharedStructureFrameTypes(sharedStructureFrameTypes);
    }

    public Map<String, String> getServerCustomEquipmentCategoriesById() {
        return library.getServerCustomEquipmentCategoriesById();
    }

    public void setServerCustomEquipmentCategoriesById(Map<String, String> serverCustomEquipmentCategoriesById) {
        library.setServerCustomEquipmentCategoriesById(serverCustomEquipmentCategoriesById);
    }

    public Map<String, String> getServerEquipmentCategoryLabels() {
        return library.getServerEquipmentCategoryLabels();
    }

    public void setServerEquipmentCategoryLabels(Map<String, String> serverEquipmentCategoryLabels) {
        library.setServerEquipmentCategoryLabels(serverEquipmentCategoryLabels);
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public CabinetType cabinetTypeById(String id) {
        return library.cabinetTypeById(id);
    }

    public ControllerType controllerTypeById(String id) {
        return library.controllerTypeById(id);
    }

    public HoistType hoistTypeById(String id) {
        return library.hoistTypeById(id);
    }

    public StructureFrameType structureFrameTypeById(String id) {
        return library.structureFrameTypeById(id);
    }
}
