package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Одна группа портов одного типа разъёма на карте (например «2×HDMI 2.1»). Карта
 * может содержать несколько таких групп разного типа/направления — комбинированные
 * карты вроде Barco E2 (2×HDMI2.1 + 2×DP1.2 на одной карте) требуют именно этого,
 * а не единой пары input/output-счётчиков на карту.
 */
public class CardPort {

    private String id = UUID.randomUUID().toString();
    private String connectorType = "";
    private PortDirection direction = PortDirection.IN;
    private int count = 1;
    /** Число фаз, реально разведённых на этой группе разъёмов (только силовая схема,
     *  см. PowerConnectorsConfigDialog) — например, разъём CEE 32A физически 3-фазный,
     *  но может быть заведён только одной фазой; ёмкость группы (Task #80/#86/#87)
     *  масштабируется на это число. 1 по умолчанию (в т.ч. для сигнальных карт, где
     *  поле не используется/не показывается). */
    private int phaseCount = 1;
    /** Номинал автомата (А), реально стоящего на этой группе разъёмов, если он ниже
     *  номинала самого разъёма (Task #86, «уточнить автоматы») — например, розетка
     *  CEE 63A, но автомат на 40А. null — автомат не указан, для расчёта ёмкости
     *  берётся номинал разъёма как есть (см. PowerCalc.connectorLabelAmps). */
    private Double breakerAmps;
    /** true — эта группа гнёзд заведена АВТОМАТИЧЕСКИ по факту существующих цепочек
     *  узла-экрана (см. AppModel.resyncScreenSockets) и пересчитывается заново при
     *  каждом изменении расключения — вручную добавленные гнёзда (false) при этом
     *  не трогаются. Различие нужно, чтобы пересчёт не терял то, что инженер
     *  добавил сам (например, свободный неиспользуемый вход про запас). */
    private boolean autoTracked;

    public CardPort() {
    }

    public CardPort(String connectorType, PortDirection direction, int count) {
        this.connectorType = connectorType;
        this.direction = direction;
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public PortDirection getDirection() {
        return direction;
    }

    public void setDirection(PortDirection direction) {
        this.direction = direction;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getPhaseCount() {
        return phaseCount;
    }

    public void setPhaseCount(int phaseCount) {
        this.phaseCount = phaseCount;
    }

    public Double getBreakerAmps() {
        return breakerAmps;
    }

    public void setBreakerAmps(Double breakerAmps) {
        this.breakerAmps = breakerAmps;
    }

    public boolean isAutoTracked() {
        return autoTracked;
    }

    public void setAutoTracked(boolean autoTracked) {
        this.autoTracked = autoTracked;
    }

    public CardPort copy() {
        CardPort p = new CardPort();
        p.id = id;
        p.connectorType = connectorType;
        p.direction = direction;
        p.count = count;
        p.phaseCount = phaseCount;
        p.breakerAmps = breakerAmps;
        p.autoTracked = autoTracked;
        return p;
    }
}
