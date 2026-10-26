package com.vjstb.ledscheme.sync;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.AppSettings;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.io.IOException;
import javax.swing.SwingWorker;

/**
 * Синхронизация общей библиотеки с учётом того, кто вошёл (запрос 2026-10-27): без входа сервер отдаёт
 * записи без команды, с токеном — ещё и записи команды пользователя. Вместе с курсором {@code globalSeq}
 * клиент помнит {@code scope} — «чьими глазами» собрана его копия ({@code anon}, {@code team:<id>},
 * {@code admin}). Если ответ пришёл с другим scope (вход, выход, смена команды, первый запуск после обновления),
 * дельта с прежнего курсора бесполезна: чужие записи остались бы в копии. Тогда клиент запрашивает всё с нуля,
 * а сервер вместе с доступными записями присылает «надгробия» недоступных — их снимает обычный путь удаления
 * ({@code AppModel#applyLibrarySyncItems}), включая защиту записей, на которые ссылаются экраны.
 * Старый сервер {@code scope} не присылает — тогда работает прежняя логика с одним курсором.
 */
public final class LibrarySync {

    /** Один запрос дельты ({@code since}, {@code token}); вынесено ради теста без сети. */
    @FunctionalInterface
    public interface Fetcher {
        LibrarySyncClient.ChangesResult fetch(long since, String token) throws IOException, InterruptedException;
    }

    private LibrarySync() {
    }

    /** Запрашивает дельту и, если сервер собрал её для другого scope, повторяет запрос с нуля. */
    public static LibrarySyncClient.ChangesResult fetch(Fetcher fetcher, long since, String knownScope, String token)
            throws IOException, InterruptedException {
        LibrarySyncClient.ChangesResult result = fetcher.fetch(since, token);
        if (since != 0 && result.scope() != null && !result.scope().equals(knownScope)) {
            result = fetcher.fetch(0, token);
        }
        return result;
    }

    public static LibrarySyncClient.ChangesResult fetch(SettingsManager settings)
            throws IOException, InterruptedException {
        SessionKeeper.ensureFresh(settings);
        AppSettings s = settings.getSettings();
        LibrarySyncClient client = new LibrarySyncClient(LibrarySyncClient.resolveBaseUrl(settings));
        return fetch(client::fetchChanges, s.getLibrarySyncGlobalSeq(), s.getLibrarySyncScope(), s.getAuthToken());
    }

    /** Запоминает курсор и scope после успешного применения дельты. */
    public static void remember(SettingsManager settings, LibrarySyncClient.ChangesResult result) {
        AppSettings s = settings.getSettings();
        String scope = result.scope() != null ? result.scope() : s.getLibrarySyncScope();
        boolean scopeChanged = result.scope() != null && !result.scope().equals(s.getLibrarySyncScope());
        if (scopeChanged || result.latestGlobalSeq() > s.getLibrarySyncGlobalSeq()) {
            settings.setLibrarySyncState(result.latestGlobalSeq(), scope);
        }
    }

    /** Фоновая синхронизация без диалога: недоступный сервер и сетевые ошибки тихо игнорируются. */
    public static void runInBackground(AppModel model, SettingsManager settings, Runnable afterwards) {
        new SwingWorker<LibrarySyncClient.ChangesResult, Void>() {
            @Override
            protected LibrarySyncClient.ChangesResult doInBackground() throws Exception {
                return fetch(settings);
            }

            @Override
            protected void done() {
                try {
                    LibrarySyncClient.ChangesResult result = get();
                    model.applyLibrarySyncItems(result.items());
                    remember(settings, result);
                } catch (Exception ignored) {
                    // сервер недоступен/сеть -- не мешаем работе приложения
                }
                if (afterwards != null) {
                    afterwards.run();
                }
            }
        }.execute();
    }
}
