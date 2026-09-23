package com.vjstb.ledscheme.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Разбор ответов login/register без реальной сети — зеркалит ровно то, что
 *  отдаёт {@code AuthController} на сервере (после Части 1 шага 2, ошибки
 *  содержат человеко-читаемое поле "message"). */
class AuthClientTest {

    @Test
    void parsesSuccessfulLoginResponse() throws Exception {
        AuthClient.AuthResult result = AuthClient.parseSuccess("{\"token\":\"abc.def.ghi\",\"role\":\"USER\"}");
        assertEquals("abc.def.ghi", result.token());
        assertEquals("USER", result.role());
        assertEquals(null, result.teamName());
    }

    @Test
    void parsesLoginResponseWithTeamName() throws Exception {
        AuthClient.AuthResult result = AuthClient.parseSuccess(
                "{\"token\":\"abc.def.ghi\",\"role\":\"USER\",\"teamName\":\"Монтаж\"}");
        assertEquals("Монтаж", result.teamName());
    }

    @Test
    void extractsMessageFromErrorBody() {
        String message = AuthClient.extractMessage(
                "{\"timestamp\":\"...\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Имя пользователя уже занято\"}");
        assertEquals("Имя пользователя уже занято", message);
    }

    @Test
    void fallsBackToErrorFieldWhenMessageMissing() {
        String message = AuthClient.extractMessage(
                "{\"timestamp\":\"...\",\"status\":409,\"error\":\"Conflict\"}");
        assertEquals("Conflict", message);
    }

    @Test
    void fallsBackToGenericTextForMalformedBody() {
        String message = AuthClient.extractMessage("не json вообще");
        assertEquals("неизвестная ошибка", message);
    }
}
