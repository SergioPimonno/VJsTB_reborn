package com.vjstb.ledscheme.sync;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * {@link HttpClient} для запросов к своему серверу. С 2026-09-11 адрес по
 * умолчанию ({@link LibrarySyncClient#DEFAULT_BASE_URL}) — домен
 * {@code ledschemedesigner.ru} с настоящим сертификатом от Let's Encrypt
 * (DNS-01 через Cloudflare API, DNS-запись переведена в режим "DNS only" —
 * Cloudflare-edge больше не участвует в передаче трафика). Для него подходит
 * обычный {@code HttpClient.newBuilder().build()} с системным доверием.
 *
 * <p>Pinning остался только ради обратной совместимости со старыми клиентами:
 * когда-то это был единственный способ довериться самоподписанному сертификату
 * сервера на голом IP. Тот self-signed сертификат сервер всё ещё раздаёт на
 * {@link LibrarySyncClient#LEGACY_PINNED_IP_URL} (старые установленные клиенты
 * собраны с этим URL как {@code DEFAULT_BASE_URL} и не могут быть пересобраны
 * задним числом) — сертификат в src/main/resources/certs/dxv-server.crt,
 * публичный, коммитить можно (это не секрет, приватный ключ остаётся только на
 * сервере). Новый код на IP-адрес полагаться не должен.</p>
 *
 * <p><b>Баг-репорт (2026-08-19, устарел после перехода на прямой домен)</b>:
 * "мост синхронизации" через Cloudflare ломал синхронизацию ошибкой
 * {@code unable to find valid certification path} при ручном переопределении
 * адреса сервера — TLS терминировался на Cloudflare, клиент видел СЕРТИФИКАТ
 * CLOUDFLARE, а не pinned self-signed для IP, потому что все sync-клиенты
 * жёстко использовали {@link #client()} (pinned) независимо от реально
 * резолвящегося адреса. {@link #clientFor(String)} тогда исправил это, сделав
 * pinned-клиент условным. Актуальный смысл условия изменился (см. ниже), сам
 * баг больше не воспроизводим — Cloudflare не в пути трафика по умолчанию. */
public final class TrustedHttp {

    private static final String CERT_RESOURCE = "/certs/dxv-server.crt";
    private static volatile SSLContext cachedContext;
    private static volatile HttpClient cachedSystemTrustClient;

    private TrustedHttp() {
    }

    /** Клиент с пиннингом на самоподписанный сертификат dxv — используй только для
     *  запросов, где адрес заведомо равен {@link LibrarySyncClient#LEGACY_PINNED_IP_URL}.
     *  Для любого другого адреса используй {@link #clientFor(String)}. */
    public static HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(sslContext())
                .build();
    }

    /** Выбирает клиент по фактическому адресу запроса: {@code baseUrl}, равный
     *  {@link LibrarySyncClient#LEGACY_PINNED_IP_URL} (старый self-signed
     *  IP-адрес) — pinned-клиент; любой другой адрес, включая пустой/null
     *  (= {@link LibrarySyncClient#DEFAULT_BASE_URL}, домен с настоящим CA-
     *  сертификатом) и любой override — обычный клиент с системным доверием. */
    public static HttpClient clientFor(String baseUrl) {
        if (baseUrl != null && baseUrl.equals(LibrarySyncClient.LEGACY_PINNED_IP_URL)) {
            return client();
        }
        HttpClient c = cachedSystemTrustClient;
        if (c == null) {
            synchronized (TrustedHttp.class) {
                if (cachedSystemTrustClient == null) {
                    cachedSystemTrustClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                }
                c = cachedSystemTrustClient;
            }
        }
        return c;
    }

    private static SSLContext sslContext() {
        SSLContext ctx = cachedContext;
        if (ctx != null) {
            return ctx;
        }
        synchronized (TrustedHttp.class) {
            if (cachedContext == null) {
                cachedContext = buildSslContext();
            }
            return cachedContext;
        }
    }

    private static SSLContext buildSslContext() {
        try (InputStream in = TrustedHttp.class.getResourceAsStream(CERT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Не найден встроенный сертификат сервера: " + CERT_RESOURCE);
            }
            Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("dxv-server", cert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Не удалось настроить доверие к сертификату сервера", ex);
        }
    }
}
