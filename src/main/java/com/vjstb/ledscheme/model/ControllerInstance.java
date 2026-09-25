package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экземпляр контроллера, назначенный экрану. Экран может обслуживаться несколькими
 * контроллерами — их суммарное число портов даёт общее число портов расключения сигнала.
 *
 * <p>С 2026-09-23 (слияние библиотеки контроллеров в {@link EquipmentPreset}, category
 * == CONTROLLER) экземпляр владеет СВОЕЙ ЗАМОРОЖЕННОЙ копией карт/плоского числа
 * портов ({@link #cards}/{@link #portCount}/{@link #portBandwidthMbps}/{@link
 * #inputPortCount}) — как и любой другой узел, скопированный из пресета (см.
 * {@code SchemaNode#getCards()}), а не живой ссылкой на библиотечный тип, которая
 * раньше резолвилась заново при каждом расчёте нумерации портов. Причина: у одного
 * пресета-контроллера может быть НЕСКОЛЬКО шаблонов карт (как у {@code
 * EquipmentPreset} для остального оборудования) — реальная комплектация КОНКРЕТНОГО
 * физического юнита собирается один раз при добавлении на экран (см. {@code
 * ui.AssembleCardsDialog}, {@code AppModel#addControllerToScreen}) и с тех пор не
 * меняется сама по себе, даже если библиотечный пресет потом отредактируют — то же
 * поведение, что и у остальных узлов схемы, скопированных из пресетов. {@link
 * #controllerTypeId} — по-прежнему id пресета-источника (имя поля сохранено ради
 * JSON-совместимости), нужен только для повторной сборки/предложения правки, НЕ для
 * нумерации портов.
 */
public class ControllerInstance {

    private String id = UUID.randomUUID().toString();
    private String controllerTypeId;
    private String label = "";
    /** id ДРУГОГО контроллера этого же экрана, который целиком подхватывает сигнал,
     *  если этот (основной) откажет — резерв на уровне контроллера, а не отдельного
     *  порта: второй контроллер полностью дублирует все порты первого. null — резерв
     *  не назначен. Задаётся на ОСНОВНОМ контроллере (см. {@link #getBackupControllerId()}),
     *  как и {@code SignalChain.backupPortNumber} для отдельного порта. */
    private String backupControllerId;
    /** Резерв на уровне ОТДЕЛЬНОЙ КАРТЫ (пула Ethernet-портов) этого контроллера —
     *  в отличие от {@link #backupControllerId} (весь контроллер целиком дублирует
     *  другой), здесь конкретная карта резервируется конкретной картой (возможно —
     *  другого) контроллера сцены: контроллеры с несколькими выходными картами
     *  (например, Novastar H2 — 2 карты) резервируют их зачастую по отдельности, а
     *  не весь контроллер разом (запрос: "сделать так же для контроллеров с картами,
     *  клик по заголовкам карт в окне портов"). Ключ — 0-based индекс ПУЛА ЭТОГО
     *  контроллера (см. {@link #ethernetPoolCount()}); null/отсутствие
     *  записи — у карты резерва нет. */
    private java.util.Map<Integer, CardBackupLink> cardBackupLinks = new java.util.LinkedHashMap<>();

    /** Доля полезной нагрузки от номинальной пропускной способности порта (служебные
     *  данные протокола) — та же эмпирическая константа NovaStar, что была на
     *  бывшем ControllerType, используется в {@link #referencePixelsPerPort()}. */
    private static final double USAGE_RATE = 0.936;

    /** Замороженная на момент добавления на экран комплектация карт (копии со
     *  свежими id, как у {@code SchemaNode#getCards()}) — пусто, если контроллер
     *  плоский (без карт, см. {@link #portCount}). */
    private List<SchemaCard> cards = new ArrayList<>();
    /** Плоское число выходных портов — используется, только если {@link #cards}
     *  пуст (простой контроллер без модульных карт). */
    private int portCount;
    /** Пропускная способность ОДНОГО порта вывода, Мбит/с. */
    private double portBandwidthMbps = 1000;
    /** Плоское число входных портов — используется, только если {@link #cards} пуст. */
    private int inputPortCount;

    /** Куда резервируется одна карта {@link #cardBackupLinks} — id контроллера сцены
     *  (может быть этот же или другой) и 0-based индекс ЕГО пула. */
    public static class CardBackupLink {
        private String controllerId;
        private int poolIndex;

        public CardBackupLink() {
        }

        public CardBackupLink(String controllerId, int poolIndex) {
            this.controllerId = controllerId;
            this.poolIndex = poolIndex;
        }

        public String getControllerId() {
            return controllerId;
        }

        public void setControllerId(String controllerId) {
            this.controllerId = controllerId;
        }

        public int getPoolIndex() {
            return poolIndex;
        }

        public void setPoolIndex(int poolIndex) {
            this.poolIndex = poolIndex;
        }

        public CardBackupLink copy() {
            return new CardBackupLink(controllerId, poolIndex);
        }
    }

    public ControllerInstance() {
    }

    public ControllerInstance(String controllerTypeId, String label) {
        this.controllerTypeId = controllerTypeId;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getControllerTypeId() {
        return controllerTypeId;
    }

    public void setControllerTypeId(String controllerTypeId) {
        this.controllerTypeId = controllerTypeId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBackupControllerId() {
        return backupControllerId;
    }

    public void setBackupControllerId(String backupControllerId) {
        this.backupControllerId = backupControllerId;
    }

    public java.util.Map<Integer, CardBackupLink> getCardBackupLinks() {
        return cardBackupLinks;
    }

    public void setCardBackupLinks(java.util.Map<Integer, CardBackupLink> cardBackupLinks) {
        this.cardBackupLinks = cardBackupLinks != null ? cardBackupLinks : new java.util.LinkedHashMap<>();
    }

    public List<SchemaCard> getCards() {
        return cards;
    }

    public void setCards(List<SchemaCard> cards) {
        this.cards = cards != null ? cards : new ArrayList<>();
    }

    public int getPortCount() {
        return portCount;
    }

    public void setPortCount(int portCount) {
        this.portCount = portCount;
    }

    public double getPortBandwidthMbps() {
        return portBandwidthMbps;
    }

    public void setPortBandwidthMbps(double portBandwidthMbps) {
        this.portBandwidthMbps = portBandwidthMbps;
    }

    public int getInputPortCount() {
        return inputPortCount;
    }

    public void setInputPortCount(int inputPortCount) {
        this.inputPortCount = inputPortCount;
    }

    // ---- нумерация портов — перенесено из бывшего ControllerType (см. class-javadoc),
    //      логика не менялась, только источник данных (карты/portCount этого экземпляра
    //      вместо резолва библиотечного типа по id) ----

    /** Число выходных портов, фактически используемое для расчёта ёмкости сигнала:
     *  по картам, если хоть одна задана (модульный контроллер вроде Novastar H-серии),
     *  иначе — ручной {@link #getPortCount()} (простой контроллер с фиксированным числом портов). */
    public int effectivePortCount() {
        if (cards.isEmpty()) {
            return portCount;
        }
        int total = 0;
        for (SchemaCard c : cards) {
            total += c.totalOutputs();
        }
        return total;
    }

    /** true — локальный выходной порт (1..{@link #effectivePortCount()}) — Ethernet,
     *  т.е. годится для расключения сигнальной цепочки на экран (см. javadoc бывшего
     *  ControllerType.isEffectivePortEthernet — та же семантика). */
    public boolean isEffectivePortEthernet(int localPort1Based) {
        if (cards.isEmpty()) {
            return true;
        }
        int seen = 0;
        for (SchemaCard c : cards) {
            for (CardPort p : c.getPorts()) {
                if (p.getDirection() == PortDirection.IN) {
                    continue;
                }
                int groupStart = seen + 1;
                int groupEnd = seen + p.getCount();
                if (localPort1Based >= groupStart && localPort1Based <= groupEnd) {
                    return isEthernetConnectorType(p.getConnectorType());
                }
                seen = groupEnd;
            }
        }
        return false;
    }

    /** Сколько из {@link #effectivePortCount()} реально пригодны для расключения
     *  экрана (Ethernet). */
    public int effectiveEthernetPortCount() {
        int total = effectivePortCount();
        if (cards.isEmpty()) {
            return total;
        }
        int usable = 0;
        for (int i = 1; i <= total; i++) {
            if (isEffectivePortEthernet(i)) {
                usable++;
            }
        }
        return usable;
    }

    private static boolean hasEthernetOutput(SchemaCard card) {
        for (CardPort p : card.getPorts()) {
            if (p.getDirection() == PortDirection.IN) {
                continue;
            }
            if (isEthernetConnectorType(p.getConnectorType())) {
                return true;
            }
        }
        return false;
    }

    /** true — тип разъёма физически годится для расключения экрана по Ethernet
     *  (реальная NovaLCT-нумерация Sending Card/Port, см. class-javadoc) — не
     *  только буквально "Ethernet" (как в {@link
     *  com.vjstb.ledscheme.service.AppModel}'s встроенном виде интерфейса
     *  "Ethernet" с версиями Cat5e/Cat6/Cat6a/Cat7/Cat8), но и распространённые
     *  реальные названия того же разъёма в общей библиотеке (например, у одного
     *  из общих пресетов контроллеров — буквально "Cat6/RJ45", без слова
     *  "Ethernet" вообще) — баг-репорт: "для МКТРЛ выбрана карта по умолчанию,
     *  порты не показываются" (0 Ethernet из 19, хотя 16 портов физически
     *  Cat6/RJ45) после слияния библиотеки контроллеров в пресеты, 2026-09-24 —
     *  раньше карты общих CONTROLLER-пресетов никогда не проходили через ЭТУ
     *  проверку (использовались только декоративно на схеме), несовпадение
     *  названия было незаметно. Специально НЕ ищем сырую подстроку "cat" (даёт
     *  ложные срабатывания на несвязанных названиях) — только "catN"/"catNx" с
     *  цифрой сразу после, как в реальных названиях категорий кабеля. */
    private static boolean isEthernetConnectorType(String type) {
        if (type == null) {
            return false;
        }
        String lower = type.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("ethernet") || lower.contains("rj45") || lower.contains("rj-45")) {
            return true;
        }
        int catIdx = lower.indexOf("cat");
        return catIdx >= 0 && catIdx + 3 < lower.length() && Character.isDigit(lower.charAt(catIdx + 3));
    }

    /** Число независимых пулов нумерации ВЫХОДНЫХ Ethernet-портов — см. javadoc
     *  бывшего ControllerType.ethernetPoolCount, семантика не менялась. */
    public int ethernetPoolCount() {
        if (cards.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (SchemaCard c : cards) {
            if (hasEthernetOutput(c)) {
                count++;
            }
        }
        return count;
    }

    /** Сколько Ethernet-портов в пуле нумерации КОНКРЕТНОЙ отдающей карты. */
    public int ethernetPortCountInPool(int poolIndex) {
        if (cards.isEmpty()) {
            return poolIndex == 0 ? portCount : 0;
        }
        SchemaCard card = sendingCardAt(poolIndex);
        if (card == null) {
            return 0;
        }
        int count = 0;
        for (CardPort p : card.getPorts()) {
            if (p.getDirection() == PortDirection.IN) {
                continue;
            }
            if (isEthernetConnectorType(p.getConnectorType())) {
                count += p.getCount();
            }
        }
        return count;
    }

    /** poolIndex-я (0-based) карта СРЕДИ карт с {@link #hasEthernetOutput} — null,
     *  если такого индекса нет. */
    public SchemaCard sendingCardAt(int poolIndex) {
        if (poolIndex < 0) {
            return null;
        }
        int idx = 0;
        for (SchemaCard c : cards) {
            if (hasEthernetOutput(c)) {
                if (idx == poolIndex) {
                    return c;
                }
                idx++;
            }
        }
        return null;
    }

    /** Разбирает сквозной номер выходного порта КОНТРОЛЛЕРА (1..{@link
     *  #effectivePortCount()}) на пару (индекс пула, номер в пуле) — см. javadoc
     *  бывшего ControllerType.ethernetPoolLocalPort, семантика не менялась. */
    public int[] ethernetPoolLocalPort(int controllerLocalPort1Based) {
        if (cards.isEmpty()) {
            if (controllerLocalPort1Based < 1 || controllerLocalPort1Based > portCount) {
                return null;
            }
            return new int[]{0, controllerLocalPort1Based};
        }
        int seenGlobal = 0;
        int sendingCardIndex = 0;
        for (SchemaCard card : cards) {
            boolean sending = hasEthernetOutput(card);
            int localEthernetSeen = 0;
            for (CardPort p : card.getPorts()) {
                if (p.getDirection() == PortDirection.IN) {
                    continue;
                }
                int groupStart = seenGlobal + 1;
                int groupEnd = seenGlobal + p.getCount();
                boolean isEth = isEthernetConnectorType(p.getConnectorType());
                if (controllerLocalPort1Based >= groupStart && controllerLocalPort1Based <= groupEnd) {
                    if (!isEth) {
                        return null;
                    }
                    return new int[]{sendingCardIndex, localEthernetSeen + (controllerLocalPort1Based - groupStart) + 1};
                }
                if (isEth) {
                    localEthernetSeen += groupEnd - groupStart + 1;
                }
                seenGlobal = groupEnd;
            }
            if (sending) {
                sendingCardIndex++;
            }
        }
        return null;
    }

    /** Обратное преобразование к {@link #ethernetPoolLocalPort} — см. javadoc
     *  бывшего ControllerType.globalPortFor, семантика не менялась. */
    public int globalPortFor(int poolIndex, int poolLocalPort1Based) {
        if (cards.isEmpty()) {
            return poolIndex == 0 && poolLocalPort1Based >= 1 && poolLocalPort1Based <= portCount
                    ? poolLocalPort1Based : -1;
        }
        int seenGlobal = 0;
        int sendingCardIndex = 0;
        for (SchemaCard card : cards) {
            boolean sending = hasEthernetOutput(card);
            int localEthernetSeen = 0;
            for (CardPort p : card.getPorts()) {
                if (p.getDirection() == PortDirection.IN) {
                    continue;
                }
                boolean isEth = isEthernetConnectorType(p.getConnectorType());
                if (sending && sendingCardIndex == poolIndex && isEth) {
                    int groupLocalStart = localEthernetSeen + 1;
                    int groupLocalEnd = localEthernetSeen + p.getCount();
                    if (poolLocalPort1Based >= groupLocalStart && poolLocalPort1Based <= groupLocalEnd) {
                        return seenGlobal + (poolLocalPort1Based - groupLocalStart) + 1;
                    }
                    localEthernetSeen = groupLocalEnd;
                }
                seenGlobal += p.getCount();
            }
            if (sending) {
                if (sendingCardIndex == poolIndex) {
                    return -1;
                }
                sendingCardIndex++;
            }
        }
        return -1;
    }

    /** Число входных портов: по картам (сумма IN-направления), если хоть одна
     *  карта задана, иначе — ручной {@link #getInputPortCount()}. */
    public int effectiveInputPortCount() {
        if (cards.isEmpty()) {
            return inputPortCount;
        }
        int total = 0;
        for (SchemaCard c : cards) {
            total += c.totalInputs();
        }
        return total;
    }

    /** Типы входных разъёмов из карт (например «2×HDMI, 1×DVI») — пусто, если
     *  карты не заданы. */
    public String inputPortTypesSummary() {
        if (cards.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SchemaCard c : cards) {
            for (CardPort p : c.getPorts()) {
                if (p.getDirection() == PortDirection.OUT) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(p.getCount()).append('×').append(p.getConnectorType());
            }
        }
        return sb.toString();
    }

    /** Опорные условия для взаимного пересчёта «пиксели ⟷ Мбит/с» (как в даташитах
     *  производителей) — см. {@link #maxPixelsFor}/{@link #referencePixelsPerPort()}. */
    public static final int REFERENCE_HZ = 60;
    public static final int REFERENCE_BIT_DEPTH = 8;

    /** Пикселей на порт при опорных условиях {@link #REFERENCE_HZ}/{@link
     *  #REFERENCE_BIT_DEPTH} — то же число, что обычно указано в даташите
     *  производителя для этой пропускной способности. */
    public int referencePixelsPerPort() {
        return maxPixelsFor(portBandwidthMbps, REFERENCE_HZ, REFERENCE_BIT_DEPTH);
    }

    /** Максимум пикселей, которые способен нести порт данной пропускной способности
     *  при заданных герцовке и глубине цвета КОНТЕНТА (формула NovaStar) — перенесено
     *  из бывшего {@code ControllerType.maxPixelsFor}, семантика не менялась. */
    public static int maxPixelsFor(double bandwidthMbps, int hz, int bitDepth) {
        double bps = bandwidthMbps * 1_000_000.0;
        if (hz <= 0 || bitDepth <= 0) {
            return 0;
        }
        return (int) Math.floor(bps * USAGE_RATE / (hz * 3.0 * bitDepth));
    }

    /** Пропускная способность (Мбит/с), нужная для переноса заданного числа пикселей
     *  при опорных условиях — обратный пересчёт для поля «пикселей на порт». */
    public static double bandwidthForPixels(int pixels, int hz, int bitDepth) {
        double bps = (double) pixels * hz * 3.0 * bitDepth / USAGE_RATE;
        return bps / 1_000_000.0;
    }

    public ControllerInstance copy() {
        ControllerInstance c = new ControllerInstance();
        c.id = id;
        c.controllerTypeId = controllerTypeId;
        c.label = label;
        c.backupControllerId = backupControllerId;
        for (java.util.Map.Entry<Integer, CardBackupLink> e : cardBackupLinks.entrySet()) {
            c.cardBackupLinks.put(e.getKey(), e.getValue().copy());
        }
        c.cards = new ArrayList<>();
        for (SchemaCard card : cards) {
            c.cards.add(card.copy());
        }
        c.portCount = portCount;
        c.portBandwidthMbps = portBandwidthMbps;
        c.inputPortCount = inputPortCount;
        return c;
    }
}
