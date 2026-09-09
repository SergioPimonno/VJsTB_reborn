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

    /** Коммутация через гнёзда разъёмов в общей схеме СИГНАЛА: если включено,
     *  соединение узлов в режиме «Соединение» цепляется за конкретный разъём
     *  (гнездо) карты оборудования, а не за узел целиком — так связь на схеме
     *  показывает, какой именно разъём с каким соединён. Выключено по умолчанию —
     *  обычное соединение «узел-узел» продолжает работать как раньше. Отдельная
     *  настройка от {@link #powerSocketWiringEnabled} — режимы схем устроены
     *  по-разному, инженеру может быть нужен режим гнёзд только для одного из них. */
    private boolean signalSocketWiringEnabled = false;

    /** То же самое для схемы ПИТАНИЯ — см. {@link #signalSocketWiringEnabled}
     *  (та же идея, отдельная настройка). */
    private boolean powerSocketWiringEnabled = false;

    /** Использовать вводные кабинеты цепочек СИГНАЛА как гнёзда подключения на общей
     *  схеме — независимо от {@link #signalSocketWiringEnabled} (та решает,
     *  цепляется ли связь за конкретное гнездо ВООБЩЕ; эта — какие гнёзда доступны). Вводной
     *  кабинет основной цепочки, и ДОПОЛНИТЕЛЬНО последний кабинет той же цепочки,
     *  если для порта задан резерв (см. {@code AppModel.chainEndpointSocketCabinetIds}).
     *  Гнёзда видны только поверх миниатюры расключения экрана (см.
     *  {@link #schemaScreensAsWiringDiagram}) — не отдельная визуализация. Отдельная
     *  настройка от {@link #powerChainEndpointSocketsEnabled} — режимы схем
     *  устроены по-разному (см. {@link #signalSchemaAutoPopulateEnabled}), инженеру
     *  может быть нужен режим гнёзд только для одного из них. Выключено по умолчанию. */
    private boolean signalChainEndpointSocketsEnabled = false;

    /** То же самое для схемы ПИТАНИЯ — см. {@link #signalChainEndpointSocketsEnabled}
     *  (та же идея, отдельная настройка). Вводной (первый в {@code cabinetInstanceIds})
     *  кабинет каждой {@code PowerChain}. */
    private boolean powerChainEndpointSocketsEnabled = false;

    /** Автозаполнение общей схемы СИГНАЛА: при переходе с расключения экрана на
     *  общую схему автоматически добавляет узлы уже расключенных экранов и
     *  использованных контроллеров сцены, которых там ещё нет — без ручного
     *  добавления каждого узла из панели «Добавить узел», зеркаля РЕАЛЬНУЮ
     *  комплектацию карт контроллера. Если ВДОБАВОК включено
     *  {@link #signalChainEndpointSocketsEnabled} — также проводит связи от гнёзд
     *  вводных/резервных кабинетов к соответствующей группе портов узла-контроллера,
     *  которому эта цепочка прописана (см. AppModel#autoPopulateSchema). Отдельная
     *  настройка от {@link #powerSchemaAutoPopulateEnabled} — расключение сигнала и
     *  питания устроено принципиально по-разному (контроллеры с реальными портами
     *  vs произвольные «проходные» щиты), инженеру может быть нужно только одно из
     *  двух. Доступно только когда включён {@link #signalSocketWiringEnabled} (см.
     *  PreferencesDialog — без него общая схема не различает конкретные гнёзда/порты
     *  вообще). Выключено по умолчанию. */
    private boolean signalSchemaAutoPopulateEnabled = false;

    /** Автозаполнение общей схемы ПИТАНИЯ — см. {@link #signalSchemaAutoPopulateEnabled}
     *  (та же идея, отдельная настройка). Добавляет только узлы расключенных экранов —
     *  у питания нет понятия контроллера (см. {@code PowerChain}). Если ВДОБАВОК
     *  включено {@link #powerChainEndpointSocketsEnabled} — распределяет вводные
     *  кабинеты цепочек по СВОБОДНЫМ разъёмам уже существующих на схеме узлов типа
     *  «Распределение» (щиты/проходные), максимально заполняя каждый по очереди,
     *  прежде чем переходить к следующему — новые узлы автоматически не создаются
     *  (см. AppModel#autoPopulateSchema). Доступно только когда включён
     *  {@link #powerSocketWiringEnabled}. Выключено по умолчанию. */
    private boolean powerSchemaAutoPopulateEnabled = false;

    /** "Защита от дурака" в общей схеме (обе схемы, общая настройка): если включено,
     *  соединение через гнёзда разъёмов (см. signal/powerSocketWiringEnabled)
     *  запрещает связывать ВХОД со ВХОДОМ или
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

    /** Легаси-флаг «рисовать ли мостики» — оставлен только для чтения профилей,
     *  сохранённых до появления {@link #schemaWireHopStyle}, и для обратной
     *  совместимости при откате на старый клиент (тот читает лишь этот boolean).
     *  Актуальное значение — {@link #getSchemaWireHopStyle()}; сеттеры держат оба
     *  поля синхронно. */
    private boolean schemaWireHops = false;

    /** Форма «мостиков» — обходов в местах пересечения соединительных линий общей
     *  схемы, как принято в ГОСТ (не рисовать / полукруглая дуга / усечённая дуга
     *  с плоской вершиной). «Сверху» (рисует дугу) считается линия, чей сегмент в
     *  точке пересечения длиннее, вторая проходит насквозь. Чисто визуально, на
     *  обе схемы (сигнал/питание) сразу — отдельной копии на этап нет. {@code null}
     *  в поле = профиль сохранён до этой настройки: тогда режим выводится из
     *  легаси-{@link #schemaWireHops} (см. {@link #getSchemaWireHopStyle()}).
     *  Для новых профилей по умолчанию {@link WireHopStyle#NONE}: на плотной
     *  схеме десятки дуг скорее мешают, инженер включает по желанию. */
    private WireHopStyle schemaWireHopStyle = null;

    /** Раскладка окна «Персонализация — предпочтения»: false (по умолчанию) —
     *  список, сгруппированный по этапу; true — таблица-матрица «настройка ×
     *  этап» (колонки Общие/Сигнал/Питание), где зеркальные пары сигнал/питание
     *  стоят в одной строке и «—» отмечает неприменимые этапы. Переключается
     *  дропдауном в окне «цвета и профили» ({@code ui.PersonalizationDialog}),
     *  само окно предпочтений своего переключателя не имеет. */
    private boolean preferencesMatrixView = false;

    /** Как рисовать разъёмы на блоке узла общей схемы СИГНАЛА — группой по типу (как
     *  было, по умолчанию) или каждый физический разъём отдельным гнездом. Отдельная
     *  настройка от {@link #powerConnectorDisplayMode} — на практике у сигнала
     *  отдельные разъёмы карты используют довольно часто (расключение по конкретным
     *  Ethernet-портам), у питания — редко (обычно достаточно группы «N×разъём»).
     *  Независимая ось от {@link #signalSocketWiringEnabled} — та решает, цепляется ли
     *  линия связи за конкретное гнездо; эта — как гнёзда рисуются, см.
     *  {@link ConnectorDisplayMode}. */
    private ConnectorDisplayMode signalConnectorDisplayMode = ConnectorDisplayMode.GROUPED;

    /** Как рисовать разъёмы на блоке узла общей схемы ПИТАНИЯ — см.
     *  {@link #signalConnectorDisplayMode} (та же идея, отдельная настройка). */
    private ConnectorDisplayMode powerConnectorDisplayMode = ConnectorDisplayMode.GROUPED;

    /** Ориентация разъёмов на блоке узла общей схемы СИГНАЛА (Task #2/v1.6, часть 2;
     *  разделено на сигнал/питание позже, по явному запросу — раньше была общая
     *  {@code connectorsVertical} на обе схемы): false (по умолчанию) — гнёзда у
     *  левого/правого края, строки сверху вниз, как раньше; true — гнёзда у
     *  верхнего/нижнего края, строки колонками слева направо. Независимая ось —
     *  сочетается с любым режимом отображения разъёмов ({@link #signalConnectorDisplayMode}). */
    private boolean signalConnectorsVertical = false;

    /** То же самое для схемы ПИТАНИЯ — см. {@link #signalConnectorsVertical}
     *  (та же идея, отдельная настройка). */
    private boolean powerConnectorsVertical = false;

    /** УСТАРЕВШЕЕ поле — единая настройка ориентации разъёмов на обе схемы,
     *  существовавшая до разделения на {@link #signalConnectorsVertical}/
     *  {@link #powerConnectorsVertical}. Не читается и не пишется напрямую нигде,
     *  кроме {@link #setLegacyConnectorsVertical} — тот принимает старое имя поля
     *  из уже сохранённого JSON (Jackson, FAIL_ON_UNKNOWN_PROPERTIES выключен, иначе
     *  значение молча терялось бы) и переносит его на оба новых поля, чтобы у ранее
     *  сохранённых профилей поведение не изменилось молча после обновления. */
    @com.fasterxml.jackson.annotation.JsonSetter("connectorsVertical")
    private void setLegacyConnectorsVertical(boolean vertical) {
        this.signalConnectorsVertical = vertical;
        this.powerConnectorsVertical = vertical;
    }

    /** Контроль электрической/сигнальной нагрузки (Task #80/#81/#86/#87): сравнение
     *  тока цепочки/суммарной нагрузки силового узла схемы с ёмкостью разъёма/автомата,
     *  предупреждения в списке цепочек и на схеме, блокировка экспорта при
     *  неподтверждённой перегрузке. Включено по умолчанию; инженер выключает здесь
     *  целиком для нестандартного случая, который расчёт не покрывает (см. GuideDialog),
     *  и дальше считает нагрузку сам. */
    private boolean loadTrackingEnabled = true;

    /** Единицы отображения мощности/нагрузки везде в UI (карточки узлов, статистика
     *  этапов Питание/Сигнал, экспортные документы) — false (по умолчанию) = ватты,
     *  true = киловатты. Влияет ТОЛЬКО на отображение — внутренний расчёт и хранение
     *  (CabinetType.powerConsumptionW и производные) остаются в ваттах, см.
     *  {@code ui.UiKit#fmtPower}. */
    private boolean powerUnitKw = false;

    /** Переназначенные горячие клавиши, по id {@link HotkeyAction}. Действие, для
     *  которого здесь нет записи, использует {@link HotkeyAction#getDefaultCombo()}. */
    private Map<String, KeyCombo> keyBindings = new LinkedHashMap<>();

    /** Путь к файлу логотипа для маски «Генерация масок» (см.
     *  PixelGridRenderer.GridRenderOptions) — индивидуален для пользователя (этого
     *  профиля), настраивается один раз в «Предпочтениях» и дальше автоматически
     *  применяется на любом гриде с включённым чекбоксом «Лого», в любом проекте —
     *  не нужно выбирать файл заново на каждом канвасе. null — логотип не задан. */
    private String maskLogoImagePath;

    /** Корневая папка по умолчанию для экспортов (маски/пресеты/пакет документации,
     *  см. {@code ui.OutputPaths#defaultFolder}) — {@code null}/пусто = встроенный
     *  дефолт {@code ~/Documents/Video} (запрос пользователя: "добавить путь по
     *  умолчанию для экспорта схем... в предпочтениях" — раньше это было жёстко
     *  зашитое значение без возможности сменить один раз на весь профиль). Явный
     *  выбор папки кнопкой «Папка…» на конкретном этапе (Вывод/Генерация масок)
     *  по-прежнему приоритетнее — эта настройка влияет только на АВТОПОДСТАВЛЯЕМОЕ
     *  значение, когда пользователь ещё ничего не выбрал в текущей сессии. */
    private String exportRootFolder;

    /** «Форма экрана» (см. {@code ui.ShapeEditorPanel}) открывается ОТДЕЛЬНЫМ
     *  всплывающим окном вместо встроенной секции в «Сетапе» — запрос
     *  пользователя, тот же паттерн, что уже применялся к 3D-превью конструктива
     *  (см. {@code ui.Structure3DDialog}: "вынеси в отдельное окно, как
     *  калькулятор видеотаймингов"). Окно живое — показывает ТЕКУЩИЙ выбранный
     *  экран приложения и обновляется при смене выбора, а не фиксированный
     *  снимок на момент открытия (см. {@code ui.ShapeEditorDialog}). По
     *  умолчанию выключено — прежнее встроенное поведение (кнопка «Изменить
     *  форму экрана» показывает/прячет секцию на месте) не меняется, пока не
     *  включат явно в «Предпочтения → Форма экрана». */
    private boolean shapeEditorFloating;

    /** Показывать ли блок «Статистика сцены» (под «Статистика экрана») на этапах
     *  Питание/Сигнал — раздельно, т.к. пользователю может быть нужна сводка по
     *  сцене только в одном из режимов (запрос: «сделай статистику сцены
     *  переключаемой через галочку в предпочтениях, раздельно для силы и
     *  сигнала»). По умолчанию включено — блок появился как всегда видимый
     *  (см. {@code PowerStagePanel}/{@code SignalStagePanel}), выключение —
     *  осознанный шаг пользователя, не смена поведения по умолчанию. */
    private boolean powerSceneStatsEnabled = true;
    private boolean signalSceneStatsEnabled = true;

    /** Id варианта отрисовки FlatLaf (см. {@code ui.LafStyle}) — свободная строка,
     *  а не FK на enum UI-слоя (та же конвенция, что и у остальных моделей, см.
     *  {@code ProjectorInstance#ambientLight}). "flatdark" по умолчанию — раньше
     *  переключатель в MainMenuBar ничего не сохранял (при каждом запуске сбрасывался
     *  на тёмную, независимо от последнего выбора). */
    private String lafStyle = "flatdark";

    /** Название семейства шрифта для {@code FlatLaf.setPreferredFontFamily} —
     *  null/пусто = использовать встроенный шрифт FlatLaf, ничего не переопределять. */
    private String fontFamily;

    /** Плотность пикселей (DPI) для JPEG-схем пакета документации (питание/сигнал
     *  экранов, блок-схема площадки, обзор сцены — см. {@code OutputStagePanel}/
     *  {@code SchemeRenderer}), 72 по умолчанию — прежнее поведение (без явных
     *  метаданных DPI, большинство просмотрщиков в этом случае показывают 72).
     *  НЕ применяется к маскам (см. {@code PixelGridRenderer}) — их пиксельный
     *  размер жёстко привязан к реальному разрешению LED-панели, увеличивать его
     *  "под печать" физически бессмысленно (растянуло бы контент с панели). */
    private int docExportDpi = 72;

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

    public boolean isSignalSocketWiringEnabled() {
        return signalSocketWiringEnabled;
    }

    public void setSignalSocketWiringEnabled(boolean signalSocketWiringEnabled) {
        this.signalSocketWiringEnabled = signalSocketWiringEnabled;
    }

    public boolean isPowerSocketWiringEnabled() {
        return powerSocketWiringEnabled;
    }

    public void setPowerSocketWiringEnabled(boolean powerSocketWiringEnabled) {
        this.powerSocketWiringEnabled = powerSocketWiringEnabled;
    }

    /** Режим коммутации через гнёзда для {@code mode} — удобный маршрутизатор, см.
     *  {@link #getConnectorDisplayMode(com.vjstb.ledscheme.model.SchemaMode)}. */
    public boolean isSocketWiringEnabled(com.vjstb.ledscheme.model.SchemaMode mode) {
        return mode == com.vjstb.ledscheme.model.SchemaMode.POWER
                ? isPowerSocketWiringEnabled() : isSignalSocketWiringEnabled();
    }

    public boolean isSignalChainEndpointSocketsEnabled() {
        return signalChainEndpointSocketsEnabled;
    }

    public void setSignalChainEndpointSocketsEnabled(boolean signalChainEndpointSocketsEnabled) {
        this.signalChainEndpointSocketsEnabled = signalChainEndpointSocketsEnabled;
    }

    public boolean isPowerChainEndpointSocketsEnabled() {
        return powerChainEndpointSocketsEnabled;
    }

    public void setPowerChainEndpointSocketsEnabled(boolean powerChainEndpointSocketsEnabled) {
        this.powerChainEndpointSocketsEnabled = powerChainEndpointSocketsEnabled;
    }

    /** Режим гнёзд подключения для {@code mode} — удобный маршрутизатор, см.
     *  {@link #getConnectorDisplayMode(com.vjstb.ledscheme.model.SchemaMode)}. */
    public boolean isChainEndpointSocketsEnabled(com.vjstb.ledscheme.model.SchemaMode mode) {
        return mode == com.vjstb.ledscheme.model.SchemaMode.POWER
                ? isPowerChainEndpointSocketsEnabled() : isSignalChainEndpointSocketsEnabled();
    }

    public boolean isSignalSchemaAutoPopulateEnabled() {
        return signalSchemaAutoPopulateEnabled;
    }

    public void setSignalSchemaAutoPopulateEnabled(boolean signalSchemaAutoPopulateEnabled) {
        this.signalSchemaAutoPopulateEnabled = signalSchemaAutoPopulateEnabled;
    }

    public boolean isPowerSchemaAutoPopulateEnabled() {
        return powerSchemaAutoPopulateEnabled;
    }

    public void setPowerSchemaAutoPopulateEnabled(boolean powerSchemaAutoPopulateEnabled) {
        this.powerSchemaAutoPopulateEnabled = powerSchemaAutoPopulateEnabled;
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

    /** {@code true}, если обходы рисуются в любой форme — тонкий обёрточный вопрос
     *  над {@link #getSchemaWireHopStyle()} для кода, которому важен лишь факт
     *  «мостики включены». */
    public boolean isSchemaWireHops() {
        return getSchemaWireHopStyle() != WireHopStyle.NONE;
    }

    /** Легаси-сеттер: {@code true} → {@link WireHopStyle#ARC}, {@code false} →
     *  {@link WireHopStyle#NONE}. Новый код зовёт {@link #setSchemaWireHopStyle}. */
    public void setSchemaWireHops(boolean schemaWireHops) {
        setSchemaWireHopStyle(schemaWireHops ? WireHopStyle.ARC : WireHopStyle.NONE);
    }

    /** Актуальная форма мостиков. Профиль без явного значения (сохранён до этой
     *  настройки) отдаёт режим по легаси-флагу: {@code schemaWireHops ? ARC : NONE}.
     *  Никогда не {@code null}. */
    public WireHopStyle getSchemaWireHopStyle() {
        if (schemaWireHopStyle != null) {
            return schemaWireHopStyle;
        }
        return schemaWireHops ? WireHopStyle.ARC : WireHopStyle.NONE;
    }

    /** Ставит форму мостиков и держит легаси-{@link #schemaWireHops} в синхроне
     *  (чтобы откат на старый клиент сохранил хотя бы факт вкл/выкл). {@code null}
     *  трактуется как {@link WireHopStyle#NONE}. */
    public void setSchemaWireHopStyle(WireHopStyle style) {
        this.schemaWireHopStyle = style == null ? WireHopStyle.NONE : style;
        this.schemaWireHops = this.schemaWireHopStyle != WireHopStyle.NONE;
    }

    public boolean isPreferencesMatrixView() {
        return preferencesMatrixView;
    }

    public void setPreferencesMatrixView(boolean preferencesMatrixView) {
        this.preferencesMatrixView = preferencesMatrixView;
    }

    public ConnectorDisplayMode getSignalConnectorDisplayMode() {
        return signalConnectorDisplayMode != null ? signalConnectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    public void setSignalConnectorDisplayMode(ConnectorDisplayMode signalConnectorDisplayMode) {
        this.signalConnectorDisplayMode = signalConnectorDisplayMode != null
                ? signalConnectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    public ConnectorDisplayMode getPowerConnectorDisplayMode() {
        return powerConnectorDisplayMode != null ? powerConnectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    public void setPowerConnectorDisplayMode(ConnectorDisplayMode powerConnectorDisplayMode) {
        this.powerConnectorDisplayMode = powerConnectorDisplayMode != null
                ? powerConnectorDisplayMode : ConnectorDisplayMode.GROUPED;
    }

    /** Режим отображения разъёмов для {@code mode} — удобный маршрутизатор для
     *  UI-кода вроде SchemaCanvasPanel, у которого уже есть конкретный SchemaMode
     *  и не хочется дублировать if/else на каждом месте вызова. */
    public ConnectorDisplayMode getConnectorDisplayMode(com.vjstb.ledscheme.model.SchemaMode mode) {
        return mode == com.vjstb.ledscheme.model.SchemaMode.POWER
                ? getPowerConnectorDisplayMode() : getSignalConnectorDisplayMode();
    }

    public boolean isSignalConnectorsVertical() {
        return signalConnectorsVertical;
    }

    public void setSignalConnectorsVertical(boolean signalConnectorsVertical) {
        this.signalConnectorsVertical = signalConnectorsVertical;
    }

    public boolean isPowerConnectorsVertical() {
        return powerConnectorsVertical;
    }

    public void setPowerConnectorsVertical(boolean powerConnectorsVertical) {
        this.powerConnectorsVertical = powerConnectorsVertical;
    }

    /** Ориентация разъёмов для {@code mode} — маршрутизатор, см.
     *  {@link #getConnectorDisplayMode(com.vjstb.ledscheme.model.SchemaMode)}
     *  (тот же приём для соседней настройки). */
    public boolean isConnectorsVertical(com.vjstb.ledscheme.model.SchemaMode mode) {
        return mode == com.vjstb.ledscheme.model.SchemaMode.POWER
                ? isPowerConnectorsVertical() : isSignalConnectorsVertical();
    }

    public boolean isLoadTrackingEnabled() {
        return loadTrackingEnabled;
    }

    public void setLoadTrackingEnabled(boolean loadTrackingEnabled) {
        this.loadTrackingEnabled = loadTrackingEnabled;
    }

    public boolean isPowerUnitKw() {
        return powerUnitKw;
    }

    public void setPowerUnitKw(boolean powerUnitKw) {
        this.powerUnitKw = powerUnitKw;
    }

    public String getMaskLogoImagePath() {
        return maskLogoImagePath;
    }

    public void setMaskLogoImagePath(String maskLogoImagePath) {
        this.maskLogoImagePath = maskLogoImagePath;
    }

    public String getExportRootFolder() {
        return exportRootFolder;
    }

    public void setExportRootFolder(String exportRootFolder) {
        this.exportRootFolder = exportRootFolder;
    }

    public boolean isShapeEditorFloating() {
        return shapeEditorFloating;
    }

    public void setShapeEditorFloating(boolean shapeEditorFloating) {
        this.shapeEditorFloating = shapeEditorFloating;
    }

    public boolean isPowerSceneStatsEnabled() {
        return powerSceneStatsEnabled;
    }

    public void setPowerSceneStatsEnabled(boolean powerSceneStatsEnabled) {
        this.powerSceneStatsEnabled = powerSceneStatsEnabled;
    }

    public boolean isSignalSceneStatsEnabled() {
        return signalSceneStatsEnabled;
    }

    public void setSignalSceneStatsEnabled(boolean signalSceneStatsEnabled) {
        this.signalSceneStatsEnabled = signalSceneStatsEnabled;
    }

    public String getLafStyle() {
        return lafStyle != null && !lafStyle.isBlank() ? lafStyle : "flatdark";
    }

    public void setLafStyle(String lafStyle) {
        this.lafStyle = lafStyle == null || lafStyle.isBlank() ? "flatdark" : lafStyle;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily == null || fontFamily.isBlank() ? null : fontFamily;
    }

    public int getDocExportDpi() {
        return docExportDpi > 0 ? docExportDpi : 72;
    }

    public void setDocExportDpi(int docExportDpi) {
        this.docExportDpi = docExportDpi > 0 ? docExportDpi : 72;
    }

    /** true — тёмный бакет цветов Palette (см. Palette#applyTheme); ПРОИЗВОДНОЕ от
     *  {@link #lafStyle}, не отдельное состояние (Darcula считается тёмным,
     *  IntelliJ — светлым, см. {@code ui.LafStyle#isDark}). */
    public boolean isDarkTheme() {
        return !"flatlight".equals(getLafStyle()) && !"intellij".equals(getLafStyle());
    }

    /** Быстрый переключатель (см. MainMenuBar) — сбрасывает на "стандартный"
     *  вариант выбранной темы (flatdark/flatlight), теряя выбор Darcula/IntelliJ,
     *  если он был. Полный выбор из 4 вариантов — в PersonalizationDialog. */
    public void setDarkTheme(boolean darkTheme) {
        this.lafStyle = darkTheme ? "flatdark" : "flatlight";
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
        p.signalSocketWiringEnabled = signalSocketWiringEnabled;
        p.powerSocketWiringEnabled = powerSocketWiringEnabled;
        p.signalChainEndpointSocketsEnabled = signalChainEndpointSocketsEnabled;
        p.powerChainEndpointSocketsEnabled = powerChainEndpointSocketsEnabled;
        p.signalSchemaAutoPopulateEnabled = signalSchemaAutoPopulateEnabled;
        p.powerSchemaAutoPopulateEnabled = powerSchemaAutoPopulateEnabled;
        p.foolProofWiringEnabled = foolProofWiringEnabled;
        p.schemaScreensAsWiringDiagram = schemaScreensAsWiringDiagram;
        p.schemaWireHops = schemaWireHops;
        p.schemaWireHopStyle = schemaWireHopStyle;
        p.preferencesMatrixView = preferencesMatrixView;
        p.signalConnectorDisplayMode = signalConnectorDisplayMode;
        p.powerConnectorDisplayMode = powerConnectorDisplayMode;
        p.signalConnectorsVertical = signalConnectorsVertical;
        p.powerConnectorsVertical = powerConnectorsVertical;
        p.loadTrackingEnabled = loadTrackingEnabled;
        p.powerUnitKw = powerUnitKw;
        p.maskLogoImagePath = maskLogoImagePath;
        p.exportRootFolder = exportRootFolder;
        p.shapeEditorFloating = shapeEditorFloating;
        p.powerSceneStatsEnabled = powerSceneStatsEnabled;
        p.signalSceneStatsEnabled = signalSceneStatsEnabled;
        p.lafStyle = lafStyle;
        p.fontFamily = fontFamily;
        p.docExportDpi = docExportDpi;
        p.keyBindings = new LinkedHashMap<>();
        for (Map.Entry<String, KeyCombo> en : keyBindings.entrySet()) {
            p.keyBindings.put(en.getKey(), en.getValue().copy());
        }
        return p;
    }
}
