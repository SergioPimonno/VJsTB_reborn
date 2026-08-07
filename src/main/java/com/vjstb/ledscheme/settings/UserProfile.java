package com.vjstb.ledscheme.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Профиль пользовательских настроек: цвета интерфейса (переопределяют встроенную
 * палитру, null-поля = использовать встроенное значение) и запомненные позиции
 * перетаскиваемых разделителей раскладки этапов (доля [0..1], по ключу раздела).
 */
public class UserProfile {

    private String id = UUID.randomUUID().toString();
    private String name = "По умолчанию";

    private Integer phase1Color;
    private Integer phase2Color;
    private Integer phase3Color;
    private Integer phaseNoneColor;
    private Integer accentColor;
    private List<Integer> signalColors;

    private Map<String, Double> layout = new LinkedHashMap<>();

    /** Показывать ли всегда видимый мини-превью раскладки сцены в правом нижнем
     *  углу холста на этапах Питание/Сигнал (см. группу 2 плана правок). */
    private boolean previewWidgetEnabled = true;

    /** Учитывать ли центр канваса как цель прилипания при Shift-перетаскивании
     *  экрана в редакторе канваса (помимо краёв уже размещённых экранов и краёв
     *  самого канваса, которые прилипают всегда). */
    private boolean canvasSnapToCenter = false;

    /** Порог прилипания (px) — единый для всех трёх мест с Shift-перетаскиванием
     *  и прилипанием (канвас «Генерация масок», кабинеты внутри экрана, узлы общей
     *  схемы) — на каком расстоянии от цели начинает действовать притяжение. */
    private int snapThresholdPx = 10;

    /** Сила прилипания (0–100%) — насколько сильно притягивает курсор к найденной
     *  в пределах порога цели: 100% — курсор жёстко прилипает точно к цели (как
     *  было раньше, без этой настройки), меньше — курсор лишь частично «тянется»
     *  к цели, не прилипая намертво (см. SnapMath.blend). */
    private int snapStrengthPercent = 100;

    /** Коммутация через гнёзда разъёмов в общей схеме: если включено, соединение
     *  узлов в режиме «Соединение» цепляется за конкретный разъём (гнездо) карты
     *  оборудования, а не за узел целиком — так связь на схеме показывает, какой
     *  именно разъём с каким соединён. Выключено по умолчанию — обычное соединение
     *  «узел-узел» продолжает работать как раньше. */
    private boolean socketWiringEnabled = false;

    /** "Защита от дурака" в общей схеме: если включено, соединение через гнёзда
     *  разъёмов (см. socketWiringEnabled) запрещает связывать ВХОД со ВХОДОМ или
     *  ВЫХОД с ВЫХОДОМ (сравнение {@link com.vjstb.ledscheme.model.CardPort#getDirection()}
     *  на обоих концах) — такое соединение физически бессмысленно. Включено по
     *  умолчанию — типичная ошибка новичка, защита не мешает опытному инженеру,
     *  который всегда может выключить её здесь для нестандартного случая. */
    private boolean foolProofWiringEnabled = true;

    /** Как рисовать узел-ссылку на экран в общей схеме: false — обычный блок
     *  (имя + краткая статистика, компактно, годится для больших схем со многими
     *  экранами); true — уменьшенная схема расключения этого экрана (заливка
     *  ячеек по цепочкам, путь подключения, панель контроллеров) — см. Task #83/v1.4.
     *  Применяется и к живому редактору схемы, и к экспорту пакета документации. */
    private boolean schemaScreensAsWiringDiagram = false;

    /** Как рисовать разъёмы на блоке узла общей схемы — группой по типу (как было,
     *  по умолчанию) или каждый физический разъём отдельным гнездом. Независимая ось
     *  от {@link #socketWiringEnabled} — та решает, цепляется ли линия связи за
     *  конкретное гнездо; эта — как гнёзда рисуются, см. {@link ConnectorDisplayMode}. */
    private ConnectorDisplayMode connectorDisplayMode = ConnectorDisplayMode.GROUPED;

    /** Ориентация разъёмов на блоке узла общей схемы (Task #2/v1.6, часть 2): false
     *  (по умолчанию) — гнёзда у левого/правого края, строки сверху вниз, как раньше;
     *  true — гнёзда у верхнего/нижнего края, строки колонками слева направо. Тоже
     *  независимая ось — сочетается с любым {@link #connectorDisplayMode}. */
    private boolean connectorsVertical = false;

    /** Контроль электрической/сигнальной нагрузки (Task #80/#81/#86/#87): сравнение
     *  тока цепочки/суммарной нагрузки силового узла схемы с ёмкостью разъёма/автомата,
     *  предупреждения в списке цепочек и на схеме, блокировка экспорта при
     *  неподтверждённой перегрузке. Включено по умолчанию; инженер выключает здесь
     *  целиком для нестандартного случая, который расчёт не покрывает (см. GuideDialog),
     *  и дальше считает нагрузку сам. */
    private boolean loadTrackingEnabled = true;

    /** Переназначенные горячие клавиши, по id {@link HotkeyAction}. Действие, для
     *  которого здесь нет записи, использует {@link HotkeyAction#getDefaultCombo()}. */
    private Map<String, KeyCombo> keyBindings = new LinkedHashMap<>();

    /** Путь к файлу логотипа для маски «Генерация масок» (см.
     *  PixelGridRenderer.GridRenderOptions) — индивидуален для пользователя (этого
     *  профиля), настраивается один раз в «Предпочтениях» и дальше автоматически
     *  применяется на любом гриде с включённым чекбоксом «Лого», в любом проекте —
     *  не нужно выбирать файл заново на каждом канвасе. null — логотип не задан. */
    private String maskLogoImagePath;

    public UserProfile() {
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

    public Integer getPhase1Color() {
        return phase1Color;
    }

    public void setPhase1Color(Integer phase1Color) {
        this.phase1Color = phase1Color;
    }

    public Integer getPhase2Color() {
        return phase2Color;
    }

    public void setPhase2Color(Integer phase2Color) {
        this.phase2Color = phase2Color;
    }

    public Integer getPhase3Color() {
        return phase3Color;
    }

    public void setPhase3Color(Integer phase3Color) {
        this.phase3Color = phase3Color;
    }

    public Integer getPhaseNoneColor() {
        return phaseNoneColor;
    }

    public void setPhaseNoneColor(Integer phaseNoneColor) {
        this.phaseNoneColor = phaseNoneColor;
    }

    public Integer getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(Integer accentColor) {
        this.accentColor = accentColor;
    }

    public List<Integer> getSignalColors() {
        return signalColors;
    }

    public void setSignalColors(List<Integer> signalColors) {
        this.signalColors = signalColors;
    }

    public Map<String, Double> getLayout() {
        return layout;
    }

    public void setLayout(Map<String, Double> layout) {
        this.layout = layout;
    }

    public boolean isPreviewWidgetEnabled() {
        return previewWidgetEnabled;
    }

    public void setPreviewWidgetEnabled(boolean previewWidgetEnabled) {
        this.previewWidgetEnabled = previewWidgetEnabled;
    }

    public boolean isCanvasSnapToCenter() {
        return canvasSnapToCenter;
    }

    public void setCanvasSnapToCenter(boolean canvasSnapToCenter) {
        this.canvasSnapToCenter = canvasSnapToCenter;
    }

    public int getSnapThresholdPx() {
        return snapThresholdPx;
    }

    public void setSnapThresholdPx(int snapThresholdPx) {
        this.snapThresholdPx = snapThresholdPx;
    }

    public int getSnapStrengthPercent() {
        return snapStrengthPercent;
    }

    public void setSnapStrengthPercent(int snapStrengthPercent) {
        this.snapStrengthPercent = snapStrengthPercent;
    }

    public boolean isSocketWiringEnabled() {
        return socketWiringEnabled;
    }

    public void setSocketWiringEnabled(boolean socketWiringEnabled) {
        this.socketWiringEnabled = socketWiringEnabled;
    }

    public boolean isFoolProofWiringEnabled() {
        return foolProofWiringEnabled;
    }

    public void setFoolProofWiringEnabled(boolean foolProofWiringEnabled) {
        this.foolProofWiringEnabled = foolProofWiringEnabled;
    }

    public boolean isSchemaScreensAsWiringDiagram() {
        return schemaScreensAsWiringDiagram;
    }

    public void setSchemaScreensAsWiringDiagram(boolean schemaScreensAsWiringDiagram) {
        this.schemaScreensAsWiringDiagram = schemaScreensAsWiringDiagram;
    }

    public ConnectorDisplayMode getConnectorDisplayMode() {
        return connectorDisplayMode != null ? connectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    public void setConnectorDisplayMode(ConnectorDisplayMode connectorDisplayMode) {
        this.connectorDisplayMode = connectorDisplayMode != null ? connectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    public boolean isConnectorsVertical() {
        return connectorsVertical;
    }

    public void setConnectorsVertical(boolean connectorsVertical) {
        this.connectorsVertical = connectorsVertical;
    }

    public boolean isLoadTrackingEnabled() {
        return loadTrackingEnabled;
    }

    public void setLoadTrackingEnabled(boolean loadTrackingEnabled) {
        this.loadTrackingEnabled = loadTrackingEnabled;
    }

    public String getMaskLogoImagePath() {
        return maskLogoImagePath;
    }

    public void setMaskLogoImagePath(String maskLogoImagePath) {
        this.maskLogoImagePath = maskLogoImagePath;
    }

    public Map<String, KeyCombo> getKeyBindings() {
        return keyBindings;
    }

    public void setKeyBindings(Map<String, KeyCombo> keyBindings) {
        this.keyBindings = keyBindings != null ? keyBindings : new LinkedHashMap<>();
    }

    /** Действующая комбинация для действия — переназначенная пользователем, или
     *  встроенная по умолчанию, если пользователь её не менял. */
    public KeyCombo bindingFor(HotkeyAction action) {
        KeyCombo custom = keyBindings.get(action.getId());
        return custom != null ? custom : action.getDefaultCombo();
    }

    public UserProfile copy() {
        UserProfile p = new UserProfile();
        p.id = id;
        p.name = name;
        p.phase1Color = phase1Color;
        p.phase2Color = phase2Color;
        p.phase3Color = phase3Color;
        p.phaseNoneColor = phaseNoneColor;
        p.accentColor = accentColor;
        p.signalColors = signalColors != null ? new ArrayList<>(signalColors) : null;
        p.layout = new LinkedHashMap<>(layout);
        p.previewWidgetEnabled = previewWidgetEnabled;
        p.canvasSnapToCenter = canvasSnapToCenter;
        p.snapThresholdPx = snapThresholdPx;
        p.snapStrengthPercent = snapStrengthPercent;
        p.socketWiringEnabled = socketWiringEnabled;
        p.foolProofWiringEnabled = foolProofWiringEnabled;
        p.schemaScreensAsWiringDiagram = schemaScreensAsWiringDiagram;
        p.connectorDisplayMode = connectorDisplayMode;
        p.connectorsVertical = connectorsVertical;
        p.loadTrackingEnabled = loadTrackingEnabled;
        p.maskLogoImagePath = maskLogoImagePath;
        p.keyBindings = new LinkedHashMap<>();
        for (Map.Entry<String, KeyCombo> en : keyBindings.entrySet()) {
            p.keyBindings.put(en.getKey(), en.getValue().copy());
        }
        return p;
    }
}
