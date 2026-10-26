package com.vjstb.ledscheme.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Синхронизация библиотеки «глазами пользователя» (запрос 2026-10-27): запоминается scope ответа, при его смене
 *  клиент берёт всё с нуля, а надгробия недоступных записей убирают их из копии. Сеть заменена {@link LibrarySync.Fetcher}. */
class LibrarySyncTest {

    private record Call(long since, String token) {
    }

    private static LibrarySyncClient.ChangesResult result(String scope, long latest,
                                                          LibrarySyncClient.LibraryItemDto... items) {
        return new LibrarySyncClient.ChangesResult(List.of(items), latest, scope);
    }

    private static LibrarySyncClient.LibraryItemDto item(String id, boolean deleted) {
        return new LibrarySyncClient.LibraryItemDto(id, "CABINET", deleted ? "" : "N", deleted ? "" : "{\"name\":\"N\"}", 1, deleted);
    }

    @Test
    void sameScopeIsOneDeltaRequestWithTheToken() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySyncClient.ChangesResult r = LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return result("team:a", 12);
        }, 10, "team:a", "tok");
        assertEquals(List.of(new Call(10, "tok")), calls);
        assertEquals(12, r.latestGlobalSeq());
    }

    @Test
    void changedScopeRefetchesEverythingFromZero() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySyncClient.ChangesResult r = LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return since == 0 ? result("team:a", 40, item("x", false)) : result("team:a", 10);
        }, 10, "anon", "tok");
        assertEquals(List.of(new Call(10, "tok"), new Call(0, "tok")), calls);
        assertEquals(40, r.latestGlobalSeq());
    }

    @Test
    void loggingOutIsAlsoAScopeChange() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return result("anon", 7);
        }, 7, "team:a", null);
        assertEquals(List.of(new Call(7, null), new Call(0, null)), calls);
    }

    @Test
    void settingsFromBeforeTheUpdateHaveNoScopeSoTheFirstSyncIsFull() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return result("anon", 7);
        }, 7, null, null);
        assertEquals(List.of(new Call(7, null), new Call(0, null)), calls, "старая копия могла содержать чужие записи");
    }

    @Test
    void anOldServerWithoutScopeKeepsTheOldBehaviour() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return result(null, 9);
        }, 7, null, null);
        assertEquals(1, calls.size());
    }

    @Test
    void firstEverSyncIsASingleRequest() throws Exception {
        List<Call> calls = new ArrayList<>();
        LibrarySync.fetch((since, token) -> {
            calls.add(new Call(since, token));
            return result("anon", 3);
        }, 0, null, null);
        assertEquals(1, calls.size());
    }

    @Test
    void rememberStoresCursorAndScopeEvenWhenTheCursorWentBackwards(@TempDir Path dir) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        LibrarySync.remember(settings, result("team:a", 50));
        assertEquals(50, settings.getSettings().getLibrarySyncGlobalSeq());
        assertEquals("team:a", settings.getSettings().getLibrarySyncScope());

        LibrarySync.remember(settings, result("team:a", 40));
        assertEquals(50, settings.getSettings().getLibrarySyncGlobalSeq(), "тот же scope — курсор назад не идёт");

        LibrarySync.remember(settings, result("anon", 30));
        assertEquals(30, settings.getSettings().getLibrarySyncGlobalSeq(), "другой scope — курсор заново");
        assertEquals("anon", settings.getSettings().getLibrarySyncScope());

        LibrarySync.remember(settings, result(null, 99));
        assertEquals("anon", settings.getSettings().getLibrarySyncScope(), "старый сервер scope не затирает");
    }

    @Test
    void aTombstoneOfAnotherTeamsItemRemovesItFromTheLocalCopy(@TempDir Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        CabinetType ct = new CabinetType();
        ct.setId("srv-team-b");
        ct.setName("Чужой кабинет");
        model.getWorkspace().getSharedCabinetTypes().add(ct);

        AppModel.LibrarySyncSummary summary = model.applyLibrarySyncItems(List.of(item("srv-team-b", true)));

        assertEquals(1, summary.deleted());
        assertNull(model.getWorkspace().getSharedCabinetTypes().stream()
                .filter(c -> "srv-team-b".equals(c.getId())).findFirst().orElse(null));
    }
}
