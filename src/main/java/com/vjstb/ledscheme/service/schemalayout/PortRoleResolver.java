package com.vjstb.ledscheme.service.schemalayout;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.model.PortPlacement;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import java.util.List;
import java.util.Locale;

/**
 * Разрешает {@link InterfaceRole} одной группы разъёмов узла общей схемы (docs/
 * schema-ports-rework/PLAN.md, задача T1.3) — роль определяет, на какой стороне
 * рамки блока рисуется гнездо (см. {@link SideRules}) и каким цветом по умолчанию
 * рисуется связь. Без Swing и без {@code AppModel} — чистая функция от уже готовых
 * данных, вызывается и из отрисовки холста, и из хит-теста, и из проверок.
 *
 * <p><b>Питание.</b> Роль ВСЕГДА {@link InterfaceRole#POWER} — у силовой схемы нет
 * понятия "видео/аудио/синхро", деление там только на ввод/отходящие/транзит (см.
 * {@link ThruResolver}), поэтому ни переопределения на блоке, ни библиотека здесь не
 * смотрятся вовсе (PLAN.md §2.3: «В схеме питания роль всегда POWER»).
 *
 * <p><b>Сигнал</b> — порядок источников по старшинству:
 * <ol>
 *   <li>{@link PortPlacement#getRoleOverride()} — правка ИМЕННО В ЭТОМ ПРОЕКТЕ;</li>
 *   <li>{@link CardPort#getRole()} — роль конкретного гнезда в библиотеке (пресет);</li>
 *   <li>{@link InterfaceType#getDefaultRole()} вида интерфейса, чьё имя — самый
 *       длинный (самый точный) регистронезависимый префикс {@link
 *       CardPort#getConnectorType()} (тот же приём сопоставления типа с записью
 *       библиотеки, что и в {@code WireLabelDialog});</li>
 *   <li>эвристика по названию разъёма (см. {@link #heuristicRole}) — запасной
 *       вариант для оборудования без роли в библиотеке (старые пресеты, ручной ввод
 *       произвольного названия разъёма).</li>
 * </ol>
 */
public final class PortRoleResolver {

    private PortRoleResolver() {
    }

    /** @param nodeType тип узла-владельца гнезда — нужен эвристике: один и тот же
     *                  тип интерфейса (Ethernet/Fiber) означает LED-данные на
     *                  контроллере/конвертере/экране и обычную IP-сеть везде
     *                  остальном (PLAN.md §2.3). */
    public static InterfaceRole resolve(SchemaMode mode, SchemaNodeType nodeType, CardPort port,
                                         PortPlacement placement, List<InterfaceType> library) {
        if (mode == SchemaMode.POWER) {
            return InterfaceRole.POWER;
        }
        if (placement != null && placement.getRoleOverride() != null) {
            return placement.getRoleOverride();
        }
        if (port.getRole() != null) {
            return port.getRole();
        }
        InterfaceRole fromLibrary = libraryDefaultRole(port.getConnectorType(), library);
        if (fromLibrary != null) {
            return fromLibrary;
        }
        return heuristicRole(port.getConnectorType(), nodeType);
    }

    /** Разрешает роль ВНЕ контекста конкретного узла общей схемы — для клиентских
     *  редакторов библиотеки (docs/schema-ports-rework/PLAN.md, задача T5.1:
     *  {@code CardsConfigDialog} и т.п.), где ещё нет ни {@link PortPlacement}
     *  (правки конкретного блока в конкретном проекте просто не существует —
     *  редактируется карта-шаблон контроллера/пресета библиотеки, а не блок на
     *  схеме), ни гарантированно известного {@link SchemaNodeType} (при правке
     *  карты типа контроллера в библиотеке узел ещё не поставлен на схему —
     *  {@code nodeTypeOrNull} тогда передаётся {@code null}, и эвристика просто не
     *  сможет отличить LED-данные от обычной сети по Ethernet/Fiber, откатываясь на
     *  вариант "обычная сеть"/{@link InterfaceRole#VIDEO} — см. {@link
     *  #isLedFacingNode}). Питание сюда НЕ попадает — "роль всегда POWER" решается
     *  на уровне вызывающего UI (PowerConnectorsConfigDialog вообще не показывает
     *  колонку роли, см. class-javadoc), а не здесь. Используется, чтобы показать
     *  пользователю в таблице "угаданную" роль КУРСИВОМ, когда {@link
     *  CardPort#getRole()} ещё не проставлен явно. */
    public static InterfaceRole resolveForLibrary(CardPort port, SchemaNodeType nodeTypeOrNull,
                                                   List<InterfaceType> library) {
        if (port.getRole() != null) {
            return port.getRole();
        }
        InterfaceRole fromLibrary = libraryDefaultRole(port.getConnectorType(), library);
        if (fromLibrary != null) {
            return fromLibrary;
        }
        return heuristicRole(port.getConnectorType(), nodeTypeOrNull);
    }

    private static InterfaceRole libraryDefaultRole(String connectorType, List<InterfaceType> library) {
        if (library == null || connectorType == null || connectorType.isBlank()) {
            return null;
        }
        String lower = connectorType.toLowerCase(Locale.ROOT);
        InterfaceType best = null;
        for (InterfaceType t : library) {
            String name = t.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (lower.startsWith(name.toLowerCase(Locale.ROOT))
                    && (best == null || name.length() > best.getName().length())) {
                best = t;
            }
        }
        return best != null ? best.getDefaultRole() : null;
    }

    /** Эвристика по названию разъёма — см. таблицу PLAN.md §2.3. Шаблоны проверяются
     *  СТРОГО в этом порядке: например, "Genlock (SDI)" содержит подстроку "sdi",
     *  которая иначе увела бы её в {@link InterfaceRole#VIDEO}, но проверка синхро
     *  идёт первой и перехватывает её раньше. */
    private static InterfaceRole heuristicRole(String connectorType, SchemaNodeType nodeType) {
        if (connectorType == null || connectorType.isBlank()) {
            return InterfaceRole.OTHER;
        }
        String s = connectorType.toLowerCase(Locale.ROOT);
        if (matches(s, "genlock", "blackburst", "tri-?level", "\\bsync\\b", "ltc", "timecode", "word ?clock")) {
            return InterfaceRole.SYNC;
        }
        if (matches(s, "xlr", "aes", "madi", "dante", "\\btrs\\b", "jack", "speakon")) {
            return InterfaceRole.AUDIO;
        }
        if (matches(s, "ethernet", "rj-?45", "cat ?5", "cat ?6", "cat ?7", "\\blan\\b", "sfp")) {
            return isLedFacingNode(nodeType) ? InterfaceRole.LED_DATA : InterfaceRole.NETWORK;
        }
        if (matches(s, "fiber", "opticalcon", "оптик")) {
            return isLedFacingNode(nodeType) ? InterfaceRole.LED_DATA : InterfaceRole.VIDEO;
        }
        if (matches(s, "usb", "midi", "dmx", "rs-?232", "rs-?485", "gpio")) {
            return InterfaceRole.CONTROL;
        }
        if (matches(s, "hdmi", "displayport", "\\bdp\\b", "sdi", "dvi", "thunderbolt")) {
            return InterfaceRole.VIDEO;
        }
        return InterfaceRole.OTHER;
    }

    private static boolean matches(String lowerConnectorType, String... patterns) {
        for (String p : patterns) {
            if (lowerConnectorType.matches(".*" + p + ".*")) {
                return true;
            }
        }
        return false;
    }

    /** Оборудование, для которого Ethernet/Fiber физически несёт LED-данные (карта
     *  вывода/приёма пиксельных данных), а не служебную IP-сеть — контроллер, конвертер
     *  (в т.ч. приёмная карта экрана) и сам узел-экран. Медиасервер, распределитель и
     *  прочее оборудование сюда НЕ входят: у них тот же тип интерфейса — обычная сеть
     *  (см. control-кейс Disguise D3 в PLAN.md §2.3, тип {@code SERVER}). */
    private static boolean isLedFacingNode(SchemaNodeType nodeType) {
        return nodeType == SchemaNodeType.CONTROLLER || nodeType == SchemaNodeType.CONVERTER
                || nodeType == SchemaNodeType.SCREEN;
    }
}
