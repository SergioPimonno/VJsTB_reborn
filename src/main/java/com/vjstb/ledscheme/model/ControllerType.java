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

    /** true — локальный выходной порт (1..{@link #effectivePortCount()}, тот же
     *  порядок обхода карт/групп, что и в effectivePortCount/totalOutputs) —
     *  Ethernet, т.е. годится для расключения сигнальной цепочки на экран.
     *  У модульных контроллеров (карты) на одной карте физически могут стоять и
     *  Ethernet-, и fiber-порты одновременно (пользователь сам заводит и то, и
     *  другое через тип разъёма CardPort) — но в схему расключения экрана
     *  подаётся только Ethernet, fiber/оптика — для магистральных соединений
     *  между устройствами, не для конечных LED-кабинетов. Без карт (плоский
     *  portCount, без информации о типе) — фильтровать нечем, порт считается
     *  доступным как раньше. */
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
                    String type = p.getConnectorType();
                    return type != null && type.toLowerCase(java.util.Locale.ROOT).contains("ethernet");
                }
                seen = groupEnd;
            }
        }
        return false;
    }

    /** Сколько из {@link #effectivePortCount()} реально пригодны для расключения
     *  экрана (Ethernet) — меньше общего числа, если на картах есть fiber-группы
     *  (см. isEffectivePortEthernet). У контроллеров без карт совпадает с
     *  effectivePortCount() (фильтровать нечем). Отдельный метод, а не изменение
     *  самого effectivePortCount() — тот считает ФИЗИЧЕСКИЙ итог для смещения
     *  номеров портов следующего контроллера сцены и для ёмкости, его нельзя
     *  тихо уменьшать. */
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

    /** Число независимых пулов нумерации ВЫХОДНЫХ Ethernet-портов — по картам
     *  (каждая карта — свой пул, нумеруется от 1 отдельно от других карт), если
     *  хоть одна карта задана, иначе один общий пул на весь плоский portCount
     *  (как было всегда — там нечем разбивать на карты). Нужно, чтобы UI показывал
     *  СТОЛЬКО ЖЕ отдельных областей нумерации портов, сколько реально card-групп
     *  задействовано в контроллере (баг-репорт: было видно одну сквозную нумерацию
     *  на весь контроллер, из-за чего fiber-порты "съедали" первые номера, и
     *  Ethernet-порты второй карты продолжали нумерацию первой, а не начинали
     *  заново с 1). */
    public int ethernetPoolCount() {
        return cards.isEmpty() ? 1 : cards.size();
    }

    /** Сколько Ethernet-портов в пуле нумерации КОНКРЕТНОЙ карты (poolIndex,
     *  0-based) — см. {@link #ethernetPoolCount()}. Для контроллера без карт
     *  единственный пул (poolIndex=0) — это весь portCount целиком (типов нет,
     *  фильтровать нечем, как и раньше в isEffectivePortEthernet). */
    public int ethernetPortCountInPool(int poolIndex) {
        if (cards.isEmpty()) {
            return poolIndex == 0 ? portCount : 0;
        }
        if (poolIndex < 0 || poolIndex >= cards.size()) {
            return 0;
        }
        int count = 0;
        for (CardPort p : cards.get(poolIndex).getPorts()) {
            if (p.getDirection() == PortDirection.IN) {
                continue;
            }
            String type = p.getConnectorType();
            if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("ethernet")) {
                count += p.getCount();
            }
        }
        return count;
    }

    /** Разбирает СКВОЗНОЙ номер выходного порта КОНТРОЛЛЕРА (1..{@link #effectivePortCount()},
     *  тот же локальный-в-пределах-контроллера номер, что использует
     *  {@link #isEffectivePortEthernet}, — НЕ полный сквозной номер по всей сцене,
     *  тот считается на уровень выше, в AppModel.portOffsetOf) на пару (индекс пула/
     *  карты 0-based, номер порта В ПРЕДЕЛАХ Ethernet-пула ЭТОЙ карты, 1-based,
     *  считая только Ethernet-порты карты подряд, без учёта fiber-пропусков) — то,
     *  что реально должен видеть пользователь в сетке портов вместо сквозного
     *  номера "с дырками" от fiber-групп. null — порт вне диапазона ИЛИ это НЕ
     *  Ethernet-порт (fiber ни в какой пул нумерации не входит — он не участвует в
     *  расключении экрана вовсе, см. {@link #isEffectivePortEthernet}). */
    public int[] ethernetPoolLocalPort(int controllerLocalPort1Based) {
        if (cards.isEmpty()) {
            if (controllerLocalPort1Based < 1 || controllerLocalPort1Based > portCount) {
                return null;
            }
            return new int[]{0, controllerLocalPort1Based};
        }
        int seenGlobal = 0;
        for (int ci = 0; ci < cards.size(); ci++) {
            int localEthernetSeen = 0;
            for (CardPort p : cards.get(ci).getPorts()) {
                if (p.getDirection() == PortDirection.IN) {
                    continue;
                }
                int groupStart = seenGlobal + 1;
                int groupEnd = seenGlobal + p.getCount();
                boolean isEth = p.getConnectorType() != null
                        && p.getConnectorType().toLowerCase(java.util.Locale.ROOT).contains("ethernet");
                if (controllerLocalPort1Based >= groupStart && controllerLocalPort1Based <= groupEnd) {
                    if (!isEth) {
                        return null;
                    }
                    return new int[]{ci, localEthernetSeen + (controllerLocalPort1Based - groupStart) + 1};
                }
                if (isEth) {
                    localEthernetSeen += groupEnd - groupStart + 1;
                }
                seenGlobal = groupEnd;
            }
        }
        return null;
    }

    /** Обратное преобразование к {@link #ethernetPoolLocalPort} — по индексу пула/
     *  карты и номеру Ethernet-порта В ПРЕДЕЛАХ этого пула (1-based) восстанавливает
     *  локальный-в-пределах-контроллера сквозной номер (тот же, что хранится/
     *  ожидается методами вроде AppModel.signalChainByPort). -1 — такого порта нет
     *  (индекс пула вне диапазона, либо локальный номер больше числа Ethernet-портов
     *  на этой карте). */
    public int globalPortFor(int poolIndex, int poolLocalPort1Based) {
        if (cards.isEmpty()) {
            return poolIndex == 0 && poolLocalPort1Based >= 1 && poolLocalPort1Based <= portCount
                    ? poolLocalPort1Based : -1;
        }
        if (poolIndex < 0 || poolIndex >= cards.size()) {
            return -1;
        }
        int seenGlobal = 0;
        for (int ci = 0; ci < cards.size(); ci++) {
            int localEthernetSeen = 0;
            for (CardPort p : cards.get(ci).getPorts()) {
                if (p.getDirection() == PortDirection.IN) {
                    continue;
                }
                boolean isEth = p.getConnectorType() != null
                        && p.getConnectorType().toLowerCase(java.util.Locale.ROOT).contains("ethernet");
                if (ci == poolIndex && isEth) {
                    int groupLocalStart = localEthernetSeen + 1;
                    int groupLocalEnd = localEthernetSeen + p.getCount();
                    if (poolLocalPort1Based >= groupLocalStart && poolLocalPort1Based <= groupLocalEnd) {
                        return seenGlobal + (poolLocalPort1Based - groupLocalStart) + 1;
                    }
                    localEthernetSeen = groupLocalEnd;
                }
                seenGlobal += p.getCount();
            }
            if (ci == poolIndex) {
                return -1;
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
