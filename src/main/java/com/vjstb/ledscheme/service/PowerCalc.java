package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import java.util.Locale;

/**
 * Общее ядро электрического расчёта нагрузки (Task #80): переводит Вт цепочки/блока
 * в ток и сравнивает его с допустимой ёмкостью разъёма/автомата. Напряжение и cosφ
 * зафиксированы по решению инженера проекта: расчёт всегда однофазный на уровне
 * экрана (220В), cosφ=1 — площадка не работает с реактивной нагрузкой, которую
 * стоило бы учитывать отдельно.
 */
public final class PowerCalc {

    /** Напряжение на уровне экрана — кабинеты запитаны однофазно 220В. */
    public static final double VOLTAGE_V = 220.0;

    /** Номинал разъёма кабинета (PowerCon/TRUEcon) для расчёта цепочки — сам разъём
     *  физически держит больше, но вводной кабель за ним всегда 16А, поэтому цепочку
     *  через него не нагружают выше этого значения (см. CabinetType.powerConnectorType). */
    public static final double CABINET_CONNECTOR_DEFAULT_AMPS = 16.0;

    /** Запас по умолчанию для силовых узлов схемы (щиты/дистрибьюторы), % от номинала.
     *  Инженерное правило: CEE125A физически держит ~81кВт (220В×125А×3ф), реально
     *  нагружают не больше ~75кВт. Проходные блоки (например, 32A → 6×16A) настраиваются
     *  на 100% индивидуально — см. SchemaNode.getLoadDeratingPercent(). */
    public static final double DEFAULT_DERATING_PERCENT = 75.0 / 81.0 * 100.0;

    private PowerCalc() {
    }

    /** Ток (А), который тянет нагрузка watts по однофазной линии 220В, cosφ=1. */
    public static double amps(double watts) {
        return watts / VOLTAGE_V;
    }

    /** Номинал разъёма (А) конкретного типа кабинета — 16А для PowerCon/TRUEcon
     *  (ограничение вводного кабеля, не самого разъёма), собственное значение для
     *  "Другой" ({@link CabinetType#getCustomConnectorAmpRating()}); 0, если для
     *  "Другой" номинал не указан (проверка нагрузки для таких цепочек невозможна). */
    public static double cabinetConnectorAmps(CabinetType type) {
        if (type == null) {
            return 0;
        }
        if (type.getPowerConnectorType() == PowerConnectorType.OTHER) {
            Double custom = type.getCustomConnectorAmpRating();
            return custom != null ? custom : 0;
        }
        return CABINET_CONNECTOR_DEFAULT_AMPS;
    }

    /** Допустимая мощность (Вт) при данном номинале (А) и запасе (%, 100 = без запаса). */
    public static double capacityWatts(double ampRating, double deratingPercent) {
        return ampRating * VOLTAGE_V * (deratingPercent / 100.0);
    }

    /** Номинал (А) по строковому обозначению разъёма схемы (CEE.../Schuko/PowerCon.../
     *  TRUEcon...), как их вводит инженер в WireLabelDialog/PowerConnectorsConfigDialog —
     *  null, если тип не распознан: тогда проверка нагрузки для этого разъёма не
     *  выполняется (нестандартный случай — см. UserProfile.isLoadTrackingEnabled()
     *  для полного отключения контроля целиком). PowerCon/TRUEcon всегда 16А — тот же
     *  вводной кабель, что и на кабинете (см. CABINET_CONNECTOR_DEFAULT_AMPS). */
    public static Double connectorLabelAmps(String label) {
        if (label == null) {
            return null;
        }
        String s = label.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        if (s.contains("cee")) {
            if (s.contains("125")) {
                return 125.0;
            }
            if (s.contains("63")) {
                return 63.0;
            }
            if (s.contains("32")) {
                return 32.0;
            }
            if (s.contains("16")) {
                return 16.0;
            }
        }
        if (s.contains("schuko") || s.contains("shuko") || s.contains("шуко")) {
            return 16.0;
        }
        if (s.contains("powercon") || s.contains("паверкон")
                || s.contains("truecon") || s.contains("трукон")) {
            return CABINET_CONNECTOR_DEFAULT_AMPS;
        }
        return null;
    }
}
