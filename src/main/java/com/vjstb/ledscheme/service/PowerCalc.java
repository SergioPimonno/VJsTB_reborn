package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.PowerConnectorType;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.util.Locale;

/**
 * Общее ядро электрического расчёта нагрузки (Task #80): переводит Вт цепочки/блока
 * в ток и сравнивает его с допустимой ёмкостью разъёма/автомата. Напряжение и cosφ
 * зафиксированы по решению инженера проекта: расчёт всегда однофазный на уровне
 * экрана, cosφ=1 — площадка не работает с реактивной нагрузкой, которую стоило бы
 * учитывать отдельно (Task #135/v2.0: cosφ намеренно НЕ вводится как множитель —
 * только сами числовые значения ниже стали runtime-настраиваемыми).
 */
public final class PowerCalc {

    /** Значения по умолчанию для формул ниже — изначально те же зашитые числа, что
     *  были константами до Task #135/v2.0; становятся runtime-настраиваемыми через
     *  синк синглтон-вида CALC_DEFAULTS (см. AppModel.applyLibrarySyncItems), который
     *  правится только через отдельную админ-консоль (ledscheme-admin). Пока с
     *  сервера ничего не пришло — поведение 100% как раньше. {@code volatile}:
     *  читается из UI/расчётного потока, пишется из потока синхронизации. */
    public static final class Defaults {
        private static volatile double voltageV = 220.0;
        private static volatile double cabinetConnectorDefaultAmps = 16.0;
        private static volatile double defaultDeratingPercent = 75.0 / 81.0 * 100.0;
        private static volatile double distroDefaultDeratingPercent = 100.0;

        private Defaults() {
        }

        public static void apply(double voltageVValue, double cabinetConnectorDefaultAmpsValue,
                                  double defaultDeratingPercentValue, double distroDefaultDeratingPercentValue) {
            voltageV = voltageVValue;
            cabinetConnectorDefaultAmps = cabinetConnectorDefaultAmpsValue;
            defaultDeratingPercent = defaultDeratingPercentValue;
            distroDefaultDeratingPercent = distroDefaultDeratingPercentValue;
        }

        /** Возвращает к зашитым исходным значениям — используется тестами, чтобы
         *  не зависеть от того, что успел применить другой тест через {@link #apply}. */
        public static void reset() {
            voltageV = 220.0;
            cabinetConnectorDefaultAmps = 16.0;
            defaultDeratingPercent = 75.0 / 81.0 * 100.0;
            distroDefaultDeratingPercent = 100.0;
        }
    }

    private PowerCalc() {
    }

    /** Напряжение на уровне экрана — кабинеты запитаны однофазно. */
    public static double voltageV() {
        return Defaults.voltageV;
    }

    /** Номинал разъёма кабинета (PowerCon/TRUEcon) для расчёта цепочки — сам разъём
     *  физически держит больше, но вводной кабель за ним обычно тоньше, поэтому
     *  цепочку через него не нагружают выше этого значения (см.
     *  CabinetType.powerConnectorType). */
    public static double cabinetConnectorDefaultAmps() {
        return Defaults.cabinetConnectorDefaultAmps;
    }

    /** Запас (%) по умолчанию ПРОХОДНЫХ блоков (тип узла DISTRO, «Распределение») —
     *  используется и через {@link #defaultDeratingPercentFor}, и напрямую там, где
     *  нужен именно этот тип независимо от узла (см. AppModel.java). */
    public static double distroDefaultDeratingPercent() {
        return Defaults.distroDefaultDeratingPercent;
    }

    /** Запас (%) по умолчанию для НЕ-DISTRO узлов (щиты/источники и остальные) —
     *  используется и через {@link #defaultDeratingPercentFor}, и напрямую там, где
     *  нужно именно это значение без привязки к конкретному узлу схемы (см.
     *  ui.PowerConnectorsConfigDialog). */
    public static double defaultDeratingPercent() {
        return Defaults.defaultDeratingPercent;
    }

    /** Запас (%) по умолчанию для узла данного типа — без учёта ручного
     *  переопределения (см. SchemaNode.getLoadDeratingPercent, которое всегда в
     *  приоритете там, где задано). Инженерное правило по умолчанию для SOURCE:
     *  CEE125A физически держит ~81кВт (220В×125А×3ф), реально нагружают не больше
     *  ~75кВт; DISTRO по умолчанию грузится на 100% номинала. */
    public static double defaultDeratingPercentFor(SchemaNodeType type) {
        return type == SchemaNodeType.DISTRO ? Defaults.distroDefaultDeratingPercent : Defaults.defaultDeratingPercent;
    }

    /** Ток (А), который тянет нагрузка watts по однофазной линии, cosφ=1. */
    public static double amps(double watts) {
        return watts / voltageV();
    }

    /** Номинал разъёма (А) конкретного типа кабинета — по умолчанию для PowerCon/
     *  TRUEcon (ограничение вводного кабеля, не самого разъёма), собственное
     *  значение для "Другой" ({@link CabinetType#getCustomConnectorAmpRating()});
     *  0, если для "Другой" номинал не указан (проверка нагрузки для таких
     *  цепочек невозможна). */
    public static double cabinetConnectorAmps(CabinetType type) {
        if (type == null) {
            return 0;
        }
        if (type.getPowerConnectorType() == PowerConnectorType.OTHER) {
            Double custom = type.getCustomConnectorAmpRating();
            return custom != null ? custom : 0;
        }
        return cabinetConnectorDefaultAmps();
    }

    /** Допустимая мощность (Вт) при данном номинале (А) и запасе (%, 100 = без запаса). */
    public static double capacityWatts(double ampRating, double deratingPercent) {
        return ampRating * voltageV() * (deratingPercent / 100.0);
    }

    /** Номинал (А) по строковому обозначению разъёма схемы (CEE.../Schuko/PowerCon.../
     *  TRUEcon...), как их вводит инженер в WireLabelDialog/PowerConnectorsConfigDialog —
     *  null, если тип не распознан: тогда проверка нагрузки для этого разъёма не
     *  выполняется (нестандартный случай — см. UserProfile.isLoadTrackingEnabled()
     *  для полного отключения контроля целиком). PowerCon/TRUEcon — тот же вводной
     *  кабель, что и на кабинете (см. {@link #cabinetConnectorDefaultAmps()}). */
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
            return cabinetConnectorDefaultAmps();
        }
        return null;
    }
}
