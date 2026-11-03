package apitests.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apitests.api.EndpointClient;
import apitests.config.TestConfig;
import apitests.support.ResponseEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Проверки завершения и изоляции сессий.
 * <p>
 * Session logout and isolation tests.
 */
@DisplayName("Выход пользователя")
public class LogoutTest {
    private final TestConfig config = new TestConfig();
    private final EndpointClient client = new EndpointClient(config);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token = UUID.randomUUID().toString()
            .replace("-", "").toUpperCase(Locale.ROOT);
    private final String otherToken = UUID.randomUUID().toString()
            .replace("-", "").toUpperCase(Locale.ROOT);

    @RegisterExtension
    final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig()
                    .port(Integer.parseInt(config.getAppWireMockPort())))
            .build();

    /**
     * Проверяет, что после выхода действие недоступно и /doAction не вызывается.
     * <p>
     * Checks that logout blocks actions without calling /doAction.
     */
    @Test
    @DisplayName("После выхода действие недоступно")
    void rejectsActionAfterLogout() {
        wireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/auth"))
                        .willReturn(WireMock.aResponse().withStatus(200)));
        wireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/doAction"))
                        .willReturn(WireMock.aResponse().withStatus(200)));

        Allure.step("Войти в приложение", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("OK", body.path("result").asText(),
                    "Перед проверкой выхода пользователь должен успешно войти");
            wireMock.verify(
                    1,
                    WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                            .withFormParam("token", WireMock.equalTo(token)));
        });

        wireMock.resetRequests();
        Allure.step("Выйти и проверить отсутствие внешних запросов", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("OK", body.path("result").asText(),
                    "Ожидался успешный выход из действующей сессии");
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });

        Allure.step("Проверить отказ действия после выхода", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "После выхода пользователь должен потерять доступ к действиям");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на отклонённое действие должен содержать причину ошибки");
            wireMock.verify(
                    0, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction")));
        });
    }

    /**
     * Проверяет восстановление доступа после выхода и нового входа.
     * <p>
     * Checks that logging in again after logout restores access.
     */
    @Test
    @DisplayName("Новый вход после выхода восстанавливает доступ")
    @Description("После выхода действие недоступно. Повторный вход с тем же токеном должен "
            + "вернуть доступ. При самом выходе внешние сервисы не вызываются.")
    void restoresAccessAfterLoggingInAgain() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                .willReturn(WireMock.aResponse().withStatus(200)));

        Allure.step("Войти и выполнить действие", () -> {
            HttpResponse<String> loginResponse = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(loginResponse, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(loginResponse.body()).path("result").asText(),
                    "Не удалось выполнить начальный вход");
            HttpResponse<String> actionResponse = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(actionResponse, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(actionResponse.body()).path("result").asText(),
                    "Изначально пользователь должен иметь доступ к действиям");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                    .withFormParam("token", WireMock.equalTo(token)));
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
        wireMock.resetRequests();
        Allure.step("Выйти без обращения к внешним сервисам", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Не удалось завершить сессию перед повторным входом");
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
        Allure.step("Проверить, что до нового входа действие недоступно", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "Действие не должно выполняться между выходом и новым входом");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на отклонённое действие должен содержать причину ошибки");
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
        Allure.step("Войти повторно с тем же токеном", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Допустимый токен должен позволять повторный вход после выхода");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
        Allure.step("Проверить доступ к действию после нового входа", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Новый успешный вход должен восстановить доступ для того же токена");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
    }

    /**
     * Проверяет, что выход одного пользователя не завершает чужую сессию.
     * <p>
     * Checks that one user's logout leaves the other user's session active.
     */
    @Test
    @DisplayName("Выход одного пользователя не завершает сессию другого")
    void keepsOtherSessionAfterLogout() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                .willReturn(WireMock.aResponse().withStatus(200)));

        Allure.step("Войти двумя пользователями", () -> {
            HttpResponse<String> first = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(first, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(first.body()).path("result").asText(),
                    "Не удалось войти первым пользователем");
            HttpResponse<String> second = client.sendAction(otherToken, "LOGIN");
            ResponseEvidence.attach(second, config.getApiKey(), otherToken);
            assertEquals("OK", mapper.readTree(second.body()).path("result").asText(),
                    "Не удалось войти вторым пользователем");
        });
        Allure.step("Выйти первым пользователем", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Не удалось завершить сессию первого пользователя");
        });
        wireMock.resetRequests();
        Allure.step("Проверить, что второму пользователю действие доступно", () -> {
            HttpResponse<String> response = client.sendAction(otherToken, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), otherToken);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Выход первого пользователя не должен завершать сессию второго");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                    .withFormParam("token", WireMock.equalTo(otherToken)));
        });
        Allure.step("Проверить, что первому пользователю действие недоступно", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("ERROR", mapper.readTree(response.body()).path("result").asText(),
                    "Токен первого пользователя не должен снова получить доступ");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction")));
        });
    }

    /**
     * Завершает обе сессии, даже если одна из операций очистки не удалась.
     * <p>
     * Cleans up both sessions even if one cleanup request fails.
     */
    @AfterEach
    void removeSessions() {
        assertAll("Завершить обе тестовые сессии",
                () -> removeSession(token),
                () -> removeSession(otherToken));
    }

    /**
     * Отправляет LOGOUT; отсутствие сессии при очистке допустимо.
     * <p>
     * Sends LOGOUT; an already absent session is acceptable during cleanup.
     *
     * @param sessionToken
     *                         тестовый токен для удаления / the synthetic token to
     *                         remove
     */
    private void removeSession(String sessionToken) {
        Allure.step("Удалить оставшуюся тестовую сессию", () -> {
            HttpResponse<String> response = client.sendAction(sessionToken, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), sessionToken);
        });
    }
}
