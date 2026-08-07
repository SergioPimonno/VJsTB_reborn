package com.vjstb.ledscheme.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Разбор ответа {@code POST /api/proposals} без реальной сети. */
class ProposalClientTest {

    @Test
    void parsesSubmittedProposal() throws Exception {
        String json = """
                {"id":"p1","authorUserId":"u1","kind":"NEW_ITEM","targetItemId":null,
                 "libraryItemKind":"CABINET","draftJson":"{\\"name\\":\\"Тест\\"}",
                 "justification":"обоснование","status":"PENDING","moderatorNote":null}
                """;
        ProposalClient.ProposalDto dto = ProposalClient.parseSuccess(json);
        assertEquals("p1", dto.id());
        assertEquals("NEW_ITEM", dto.kind());
        assertEquals("CABINET", dto.libraryItemKind());
        assertEquals("PENDING", dto.status());
    }

    @Test
    void parsesPendingList() throws Exception {
        String json = """
                [{"id":"p1","authorUserId":"u1","kind":"NEW_ITEM","targetItemId":null,
                  "libraryItemKind":"CABINET","draftJson":"{}","justification":"j1",
                  "status":"PENDING","moderatorNote":null},
                 {"id":"p2","authorUserId":"u2","kind":"NEW_ITEM","targetItemId":null,
                  "libraryItemKind":"CABLE","draftJson":"{}","justification":"j2",
                  "status":"PENDING","moderatorNote":null}]
                """;
        List<ProposalClient.ProposalDto> items = ProposalClient.parsePendingList(json);
        assertEquals(2, items.size());
        assertEquals("p1", items.get(0).id());
        assertEquals("CABLE", items.get(1).libraryItemKind());
    }

    @Test
    void parsesEmptyPendingList() throws Exception {
        assertEquals(0, ProposalClient.parsePendingList("[]").size());
    }

    @Test
    void extractsMessageFromErrorBody() {
        String message = ProposalClient.extractMessage(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Недостаточно прав\"}");
        assertEquals("Недостаточно прав", message);
    }
}
