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
 * {@link HttpClient} для запросов к своему серверу (см. {@link LibrarySyncClient
 * #DEFAULT_BASE_URL}) — сервер сейчас за самоподписанным сертификатом (см.
 * CLAUDE.md, секция 2: Caddy на dxv, доверенного домена/CA пока нет). Обычный
 * {@code HttpClient.newBuilder().build()} такой сертификат бы отверг —
 * настраиваем доверие ИМЕННО к этому одному сертификату (pinning), НЕ отключаем
 * проверку целиком ("trust all") — это была бы дыра, а не решение задачи "сервер
 * за открытым портом без шифрования". Сертификат — src/main/resources/certs/
 * dxv-server.crt, публичный, коммитить можно (это не секрет, приватный ключ
 * остаётся только на сервере).
 *
 * <p>Если/когда у сервера появится домен и настоящий сертификат от публичного CA
 * (см. обсуждение в этой же сессии) — этот класс и ресурс можно будет убрать,
 * вернувшись к обычному {@code HttpClient.newBuilder().build()}.</p>
 */
public final class TrustedHttp {

    private static final String CERT_RESOURCE = "/certs/dxv-server.crt";
    private static volatile SSLContext cachedContext;

    private TrustedHttp() {
    }

    public static HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(sslContext())
                .build();
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
