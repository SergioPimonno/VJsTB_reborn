package com.vjstb.ledscheme.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Локальные сетевые интерфейсы ЭТОЙ машины — источник данных для блока
 * «Admin Laptop» (запрос пользователя: "в поле должен автоматически
 * формироваться блок Admin Laptop, представляющий текущее устройство", см.
 * {@code ui.NetworkCanvasPanel#addAdminLaptop}/{@code
 * #refreshAdminLaptopAddresses}).
 *
 * <p>Берётся ТОЛЬКО первый найденный активный IPv4-адрес ({@link
 * #primaryIp()}) — не пытается угадать, какой из нескольких сетевых
 * интерфейсов (Wi-Fi/провод/VPN) "правильный": однозначного способа решить
 * это нет, а у большинства пользователей на площадке активен ровно один.
 * Если нужно представить несколько интерфейсов машины отдельными
 * подключениями к разным сетям — это делается вручную той же общей формой
 * «Подключить к сети…», что и у любого другого многосетевого устройства (см.
 * {@code NetworkCanvasPanel#attachToNetwork}) — сознательный компромисс,
 * не пытаемся автоматически сопоставлять интерфейс конкретной сети (у {@code
 * model.Network} нет понятия подсети, сопоставлять было бы не по чему). */
public final class LocalNetworkInterfaces {

    private LocalNetworkInterfaces() {
    }

    public record LocalAddress(String interfaceName, String ip) {
    }

    /** Все активные (не loopback, не выключенные) интерфейсы с IPv4-адресом —
     *  в порядке, в котором их возвращает ОС; пустой список при ошибке
     *  перечисления (нет прав/платформенная особенность) — не бросает. */
    public static List<LocalAddress> active() {
        List<LocalAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        result.add(new LocalAddress(ni.getDisplayName(), a.getHostAddress()));
                    }
                }
            }
        } catch (SocketException ignored) {
            // Платформа отказала в перечислении интерфейсов -- блок Admin Laptop просто
            // останется без адреса, не критично для остальной работы менеджера.
        }
        return result;
    }

    /** Первый найденный активный IPv4-адрес — см. class-javadoc про то, почему
     *  не пытаемся выбирать "самый правильный" интерфейс. Пустая строка, если
     *  ни одного не найдено. */
    public static String primaryIp() {
        List<LocalAddress> all = active();
        return all.isEmpty() ? "" : all.get(0).ip();
    }

    /** Имя компьютера — стартовая подпись блока (пользователь может
     *  переименовать как любой другой блок, дальше это уже не отслеживается
     *  живьём, см. {@code model.NetworkDevicePlacement} javadoc про приоритет
     *  {@code customLabel}). */
    public static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "Мой компьютер";
        }
    }
}
