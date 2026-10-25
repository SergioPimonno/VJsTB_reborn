package com.vjstb.ledscheme.service.graphimport;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Семейства разъёмов и подсказки по подписи линии. В внешних схемах гнёзд нет, поэтому порт для связи подбирается по
 * СЕМЕЙСТВУ разъёма: у гнезда библиотеки ({@code "Cat6/RJ45"}, {@code "Fiber MMF LC"}, {@code "CEE 32A"}) и у подписи
 * линии из файла ({@code "fiber 1"}, {@code "CEE32A"}, {@code "6x16A->TRUECON"}) считается одинаковый короткий ключ.
 *
 * <p>Ключи: {@code fiber}, {@code ethernet} (Cat5e/Cat6/RJ45/Ethernet), {@code sdi}, {@code genlock}, {@code hdmi},
 * {@code dp}, {@code socapex}, {@code cee16}/{@code cee32}/… (по номиналу), {@code schuko}, {@code powercon},
 * {@code truecon}; остальное — буквы и цифры названия в нижнем регистре.
 */
public final class EdgeHints {

    private static final Pattern CEE = Pattern.compile("cee\\s*-?\\s*(\\d{2,3})");

    private EdgeHints() {
    }

    /** Семейство разъёма по названию гнезда библиотеки. */
    public static String familyOfConnector(String connectorType) {
        String f = detect(connectorType);
        if (f != null) {
            return f;
        }
        return connectorType == null ? "" : connectorType.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Семейство, которое называет подпись линии, или {@code null} — подпись про разъём ничего не говорит
     *  («1-4 main / 5-8 backup»). Если названо несколько семейств («4x16A->TRUECON»), берётся последнее — то, что
     *  стоит после стрелки, то есть у приёмника. */
    public static String familyOfLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        int arrow = lower.lastIndexOf("->");
        if (arrow >= 0) {
            String tail = detect(lower.substring(arrow + 2));
            if (tail != null) {
                return tail;
            }
        }
        return detect(lower);
    }

    private static String detect(String text) {
        if (text == null) {
            return null;
        }
        String s = text.toLowerCase(Locale.ROOT);
        if (s.contains("fiber") || s.contains("optic") || s.contains("оптик") || s.contains("mmf") || s.contains("smf")) {
            return "fiber";
        }
        if (s.contains("genlock")) {
            return "genlock";
        }
        if (s.contains("sdi")) {
            return "sdi";
        }
        if (s.contains("hdmi")) {
            return "hdmi";
        }
        if (s.contains("displayport") || Pattern.compile("\\bdp\\b").matcher(s).find()) {
            return "dp";
        }
        if (s.contains("cat5") || s.contains("cat6") || s.contains("cat7") || s.contains("rj45")
                || s.contains("ethernet") || s.contains("utp")) {
            return "ethernet";
        }
        if (s.contains("socapex")) {
            return "socapex";
        }
        Matcher m = CEE.matcher(s);
        if (m.find()) {
            return "cee" + m.group(1);
        }
        if (s.contains("truecon") || s.contains("true con")) {
            return "truecon";
        }
        if (s.contains("powercon")) {
            return "powercon";
        }
        if (s.contains("schuko") || s.contains("shuko") || s.contains("шуко")) {
            return "schuko";
        }
        return null;
    }
}
