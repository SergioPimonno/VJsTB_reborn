package com.vjstb.ledscheme.sync;

import com.vjstb.ledscheme.AppInfo;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.SwingWorker;

/**
 * Тихо (фоново, без UI) отправляет копии {@code workspace.json}/{@code
 * library.json} на сервер (см. {@link ClientBackupClient}/{@code
 * ClientBackupController}) — страховка поверх атомарной записи в {@code
 * WorkspaceStore#save} на случай, что пойдёт не так и на самом диске клиента
 * (антивирус, сбой питания, битый сектор). По запросу пользователя вызывается из
 * ДВУХ мест — везде, где уже происходит синхронизация библиотеки: {@code
 * App#syncLibraryInBackground} (при каждом запуске) и {@code
 * LibrarySyncDialog#runSync} (ручной пункт меню) — не отдельный таймер/повод,
 * пиггибэком на уже существующие точки.
 *
 * <p>Требует логин (см. class-javadoc {@code ClientBackupClient}) — без токена
 * молча ничего не делает (та же тактика тихого пропуска, что и у сетевых ошибок
 * ниже: это необязательная доп. страховка, а не критичная для работы функция,
 * не должна ничем беспокоить пользователя, который не входил в аккаунт).
 */
public final class ClientBackupSync {

    private ClientBackupSync() {
    }

    public static void runInBackground(AppModel model, SettingsManager settings) {
        String token = settings.getSettings().getAuthToken();
        if (token == null || token.isBlank()) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                ClientBackupClient client = new ClientBackupClient(LibrarySyncClient.resolveBaseUrl(settings));
                uploadIfPresent(client, token, "workspace", model.getStore().getWorkspaceFile());
                uploadIfPresent(client, token, "library", model.getLibraryStore().getLibraryFile());
                return null;
            }
        }.execute();
    }

    private static void uploadIfPresent(ClientBackupClient client, String token, String fileKind,
            java.io.File file) {
        try {
            if (file == null || !file.exists() || file.length() == 0) {
                return; // нечего бэкапить -- в т.ч. только что описанный баг-репорт (пустой файл)
            }
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            client.upload(token, fileKind, content, AppInfo.VERSION);
        } catch (Exception ignored) {
            // сервер недоступен/сеть/токен истёк -- не мешаем работе приложения, это
            // необязательная доп. страховка (см. class-javadoc)
        }
    }
}
