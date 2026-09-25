package com.vjstb.ledscheme.settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Шифрование запомненного пароля («Запомнить логин и пароль» в {@code ui.AccountDialog}).
 * AES-GCM, случайный ключ лежит отдельным файлом {@code credential.key} рядом с
 * settings.json — пароль не лежит в настройках открытым текстом и не утекает вместе с
 * одним лишь settings.json (например, если его прислали для отладки). От того, у кого
 * есть доступ ко всему каталогу {@code ~/.led-scheme/}, это НЕ защищает — полноценного
 * хранилища ОС (DPAPI/Keychain) без нативных зависимостей в Java нет.
 */
final class CredentialCipher {

    private static final int KEY_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final File keyFile;
    private final SecureRandom random = new SecureRandom();

    CredentialCipher(File keyFile) {
        this.keyFile = keyFile;
    }

    String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось зашифровать пароль: " + e.getMessage(), e);
        }
    }

    /** {@code null}, если расшифровать не удалось (ключ удалён/подменён, данные испорчены) —
     *  тогда форма входа просто остаётся с пустым паролем. */
    String decrypt(String encoded) {
        try {
            byte[] key = key(false);
            if (key == null) {
                return null;
            }
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] key(boolean create) throws IOException {
        if (keyFile.exists()) {
            byte[] k = Base64.getDecoder().decode(Files.readString(keyFile.toPath()).trim());
            if (k.length == KEY_BYTES) {
                return k;
            }
        }
        if (!create) {
            return null;
        }
        byte[] k = new byte[KEY_BYTES];
        random.nextBytes(k);
        File dir = keyFile.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        Files.writeString(keyFile.toPath(), Base64.getEncoder().encodeToString(k));
        return k;
    }
}
