package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Тип видеоконтроллера в библиотеке (аналог профилей устройств в SmartLCT/Novastar):
 * модель, число портов вывода и их пропускная способность. Библиотека общая для
 * всех проектов, как и {@link CabinetType}.
 *
 * Порт задаётся пропускной способностью (Мбит/с) — это физический параметр порта
 * (100/1000/2500/10000 Мбит/с и т.д.). Число пикселей, которое порт способен нести,
 * из неё ВЫЧИСЛЯЕТСЯ по формуле NovaStar (см. {@link #maxPixelsFor}): зависит ещё от
 * герцовки и глубины цвета контента — они настраиваются на самом экране, а не тут.
 * Здесь для удобства редактирования (когда удобнее задать не Мбит/с, а сразу число
 * пикселей) используется опорная герцовка/глубина 60 Гц / 8 бит — как в даташитах
 * производителей — и bandwidth пересчитывается обратно из указанных пикселей.
 */
public class ControllerType {

    /** Опорные условия для взаимного пересчёта «пиксели ⟷ Мбит/с» в библиотеке
     *  (как в даташитах производителей) — реальная ёмкость порта на конкретном
     *  экране зависит от ЕГО герцовки/глубины цвета, см. {@link #maxPixelsFor}. */
    public static final int REFERENCE_HZ = 60;
    public static final int REFERENCE_BIT_DEPTH = 8;
    /** Доля полезной нагрузки от номинальной пропускной способности порта (служебные
     *  данные протокола) — эмпирическая константа из документации NovaStar. */
    private static final double USAGE_RATE = 0.936;

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String vendor = "";
    /** Число портов вывода (Ethernet/оптика) на устройство. */
    private int portCount = 8;
    /** Пропускная способность ОДНОГО порта вывода, Мбит/с (100/1000/2500/10000...). */
    private double portBandwidthMbps = 1000;
    /** Число входных портов (видео/данные) без учёта типа — используется, только
     *  если карты (см. cards) не заданы, см. {@link #effectiveInputPortCount()}. */
    private int inputPortCount = 0;
    /** Есть ли Loop-порт (например, HDMI loop у Novastar VX1000) — позволяет
     *  прокинуть входной сигнал дальше на другой контроллер/дисплей без разветвителя. */
    private boolean loopPort;
    /** Комплектация карт (и вывода, и ввода — направление у каждой группы разъёмов
     *  своё, см. {@link CardPort#getDirection()}): например, Novastar H-серии тоже
     *  модульная, как медиасерверы/видеопроцессоры — если карты заданы, эффективное
     *  число портов вывода/ввода и их ТИПЫ считаются по картам, а не по ручным
     *  portCount/inputPortCount, см. {@link #effectivePortCount()}/{@link #effectiveInputPortCount()}. */
    private List<SchemaCard> cards = new ArrayList<>();

    public ControllerType() {
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

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
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

    public boolean isLoopPort() {
        return loopPort;
    }

    public void setLoopPort(boolean loopPort) {
        this.loopPort = loopPort;
    }

    public List<SchemaCard> getCards() {
        return cards;
    }

    public void setCards(List<SchemaCard> cards) {
        this.cards = cards;
    }

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
     *  карты не заданы (тогда известно только число входов, без типа). */
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

    /** Пикселей на порт при опорных условиях (60 Гц, 8 бит) — то же число, что
     *  обычно указано в даташите производителя для этой пропускной способности. */
    public int referencePixelsPerPort() {
        return maxPixelsFor(portBandwidthMbps, REFERENCE_HZ, REFERENCE_BIT_DEPTH);
    }

    /** Пропускная способность (Мбит/с), нужная для переноса заданного числа пикселей
     *  при опорных условиях — обратный пересчёт для поля «пикселей на порт». */
    public static double bandwidthForPixels(int pixels, int hz, int bitDepth) {
        double bps = (double) pixels * hz * 3.0 * bitDepth / USAGE_RATE;
        return bps / 1_000_000.0;
    }

    /** Максимум пикселей, которые способен нести порт данной пропускной способности
     *  при заданных герцовке и глубине цвета КОНТЕНТА (формула NovaStar: пропускная
     *  способность × КПД / (Гц × 3 канала × бит на канал)). */
    public static int maxPixelsFor(double bandwidthMbps, int hz, int bitDepth) {
        double bps = bandwidthMbps * 1_000_000.0;
        if (hz <= 0 || bitDepth <= 0) {
            return 0;
        }
        return (int) Math.floor(bps * USAGE_RATE / (hz * 3.0 * bitDepth));
    }

    public ControllerType copy() {
        ControllerType c = new ControllerType();
        c.id = id;
        c.applyEditedValues(this);
        return c;
    }

    /** Копирует ВСЕ редактируемые поля (кроме id) из other — единственная точка,
     *  которую нужно обновлять при появлении нового поля; используется и в
     *  {@link #copy()}, и в AppModel.updateControllerType для переноса изменений из
     *  отдельной (detached) копии, которую строит ControllerTypeDialog, обратно в
     *  реально хранимый в библиотеке объект. Раньше AppModel копировал поля вручную
     *  по отдельному списку, забывшему про loopPort — флажок "Есть Loop-порт" тихо
     *  терялся при редактировании уже созданного контроллера (тот же класс бага,
     *  что и с CabinetType.powerConnectorType — см. Task #94/v1.5). */
    public void applyEditedValues(ControllerType other) {
        this.name = other.name;
        this.vendor = other.vendor;
        this.portCount = other.portCount;
        this.portBandwidthMbps = other.portBandwidthMbps;
        this.inputPortCount = other.inputPortCount;
        this.loopPort = other.loopPort;
        this.cards = new ArrayList<>();
        for (SchemaCard card : other.cards) {
            this.cards.add(card.copy());
        }
    }
}
