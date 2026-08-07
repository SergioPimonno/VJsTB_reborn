package com.vjstb.ledscheme.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Разбор ответа {@code GET /api/library/changes} без реальной сети — зеркалит
 *  ровно тот JSON, что отдаёт {@code LibraryController} на сервере. */
class LibrarySyncClientTest {

    @Test
    void parsesEmptyChanges() throws Exception {
        LibrarySyncClient.ChangesResult result = LibrarySyncClient.parse(
                "{\"items\":[],\"latestGlobalSeq\":0}");
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.latestGlobalSeq());
    }

    @Test
    void parsesItemsWithAllFields() throws Exception {
        String json = """
                {"items":[
                    {"id":"srv-1","kind":"CABINET","name":"Тест","payloadJson":"{\\"name\\":\\"Тест\\"}",
                     "globalSeq":5,"deleted":false},
                    {"id":"srv-2","kind":"CABLE","name":"Старый кабель","payloadJson":"{}",
                     "globalSeq":6,"deleted":true}
                ],"latestGlobalSeq":6}
                """;
        LibrarySyncClient.ChangesResult result = LibrarySyncClient.parse(json);
        assertEquals(2, result.items().size());
        assertEquals(6, result.latestGlobalSeq());

        LibrarySyncClient.LibraryItemDto first = result.items().get(0);
        assertEquals("srv-1", first.id());
        assertEquals("CABINET", first.kind());
        assertEquals("Тест", first.name());
        assertEquals(5, first.globalSeq());
        assertTrue(!first.deleted());

        assertTrue(result.items().get(1).deleted());
    }
}
