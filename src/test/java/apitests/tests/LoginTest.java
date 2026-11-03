package apitests.tests;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Проверки входа через внешний сервис /auth.
 * <p>
 * Login tests using the external /auth service.
 */
@DisplayName("Вход пользователя")
public class LoginTest {
    private final TestConfig config = new TestConfig();
    private final String sessionToken = UUID.randomUUID().toString()
            .replace("-", "").toUpperCase(Locale.ROOT);

    @RegisterExtension
    final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig()
                    .port(Integer.parseInt(config.getAppWireMockPort())))
            .build();

    /**
     * Проверяет вход и передачу токена в /auth.
     * <p>
     * Checks login and token forwarding to /auth.
     */
    @Test
    @DisplayName("Успешная аутентификация разрешает вход")
    void logsInWhenAuthenticationSucceeds() {
        Allure.step("Настроить успешную аутентификацию", () -> {
            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/auth"))
                            .willReturn(WireMock.aResponse().withStatus(200)));
        });

        String token = sessionToken;
        EndpointClient client = new EndpointClient(config);
        HttpResponse<String> response = Allure.step("Отправить запрос на вход", () -> {
            HttpResponse<String> received = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(received, config.getApiKey(), token);
            return received;
        });

        Allure.step("Проверить успешный вход", () -> {
            String result = new ObjectMapper().readTree(response.body()).path("result").asText();
            assertEquals("OK", result,
                    "Ожидался успешный вход: сервис аутентификации вернул HTTP 200");
        });
        Allure.step("Проверить один вызов /auth с токеном пользователя", () -> {
            wireMock.verify(
                    1,
                    WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                            .withFormParam("token", WireMock.equalTo(token)));
        });
    }

    /**
     * Проверяет отказ входа и запрет действий после ошибки /auth.
     * <p>
     * Checks that an /auth failure blocks login and subsequent actions.
     *
     * @param status
     *                            неуспешный HTTP-статус внешнего сервиса / the
     *                            external service's failure status
     * @param bodyDescription
     *                            описание тела ответа внешнего сервиса / a readable
     *                            external response body description
     * @param externalBody
     *                            тело внешнего ответа / external response body
     */
    @ParameterizedTest(name = "— HTTP {0}; {1}")
    @CsvFileSource(resources = "/fixtures/unsuccessful-external-responses.csv", numLinesToSkip = 1)
    @DisplayName("Ошибка аутентификации запрещает вход и действия")
    @Description("Если сервис аутентификации вернул ошибку, вход и действия должны быть запрещены. "
            + "Проверяем это с разными телами ответа, в том числе с сообщением об успехе.")
    void rejectsLoginWhenAuthenticationFails(int status, String bodyDescription, String externalBody) {
        Allure.step("Настроить ответ сервиса аутентификации: HTTP " + status + "; " + bodyDescription, () -> {
            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/auth"))
                            .willReturn(WireMock.aResponse().withStatus(status).withBody(externalBody)));
            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/doAction"))
                            .willReturn(WireMock.aResponse().withStatus(200)));
        });

        String token = sessionToken;
        EndpointClient client = new EndpointClient(config);
        ObjectMapper mapper = new ObjectMapper();

        Allure.step("Проверить отказ входа и причину ошибки", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "LOGIN должен быть отклонён при отказе внешнего сервиса аутентификации");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на отклонённый LOGIN должен содержать причину ошибки");
            wireMock.verify(
                    1,
                    WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                            .withFormParam("token", WireMock.equalTo(token)));
        });

        Allure.step("Проверить, что действие после отказа входа недоступно", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "Токен, не прошедший LOGIN, не должен получать доступ к ACTION");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на отклонённый ACTION должен содержать причину ошибки");
            wireMock.verify(
                    0, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction")));
        });
    }

    /**
     * Проверяет вход при HTTP 200 с разными телами ответа /auth.
     * <p>
     * Checks login with different /auth response bodies at HTTP 200.
     *
     * @param bodyDescription
     *                            описание тела ответа / a readable response body
     *                            description
     * @param externalBody
     *                            тело внешнего ответа / external response body
     */
    @ParameterizedTest(name = "— {0}")
    @CsvFileSource(resources = "/fixtures/successful-external-responses.csv", numLinesToSkip = 1)
    @DisplayName("При HTTP 200 вход не зависит от тела ответа")
    void ignoresSuccessfulAuthenticationBody(String bodyDescription, String externalBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody(externalBody)));
        Allure.step("Проверить успешный вход при HTTP 200: " + bodyDescription, () -> {
            HttpResponse<String> response = new EndpointClient(config)
                    .sendAction(sessionToken, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), sessionToken);
            assertEquals("OK", new ObjectMapper().readTree(response.body()).path("result").asText(),
                    "При HTTP 200 от /auth ожидался успешный вход независимо от тела ответа");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                    .withHeader("Content-Type", WireMock.containing("application/x-www-form-urlencoded"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withFormParam("token", WireMock.equalTo(sessionToken)));
        });
    }

    /**
     * Удаляет сессию, даже если приложение ошибочно разрешило вход.
     * <p>
     * Clears the session even if the application allowed login incorrectly.
     */
    @AfterEach
    void removeSession() {
        Allure.step("Удалить тестовую сессию, если она была создана", () -> {
            HttpResponse<String> response = new EndpointClient(config).sendAction(sessionToken, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), sessionToken);
        });
    }
}
