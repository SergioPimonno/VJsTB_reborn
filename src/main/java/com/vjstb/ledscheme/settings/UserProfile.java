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

    /** Коммутация через гнёзда разъёмов в общей схеме: если включено, соединение
     *  узлов в режиме «Соединение» цепляется за конкретный разъём (гнездо) карты
     *  оборудования, а не за узел целиком — так связь на схеме показывает, какой
     *  именно разъём с каким соединён. Выключено по умолчанию — обычное соединение
     *  «узел-узел» продолжает работать как раньше. */
    private boolean socketWiringEnabled = false;

    /** Переназначенные горячие клавиши, по id {@link HotkeyAction}. Действие, для
     *  которого здесь нет записи, использует {@link HotkeyAction#getDefaultCombo()}. */
    private Map<String, KeyCombo> keyBindings = new LinkedHashMap<>();

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

    public boolean isSocketWiringEnabled() {
        return socketWiringEnabled;
    }

    public void setSocketWiringEnabled(boolean socketWiringEnabled) {
        this.socketWiringEnabled = socketWiringEnabled;
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
        p.socketWiringEnabled = socketWiringEnabled;
        p.keyBindings = new LinkedHashMap<>();
        for (Map.Entry<String, KeyCombo> en : keyBindings.entrySet()) {
            p.keyBindings.put(en.getKey(), en.getValue().copy());
        }
        return p;
    }
}
