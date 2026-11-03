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
 * Проверки доступа к действиям и вызовов /doAction.
 * <p>
 * Action access and /doAction integration tests.
 */
@DisplayName("Действия пользователя")
public class ActionTest {
    private final TestConfig config = new TestConfig();
    private final EndpointClient client = new EndpointClient(config);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token = UUID.randomUUID().toString()
            .replace("-", "")
            .toUpperCase(Locale.ROOT);
    private boolean sessionCreated;

    @RegisterExtension
    final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig()
                    .port(Integer.parseInt(config.getAppWireMockPort())))
            .build();

    /**
     * Проверяет действие после входа и формат запроса к /doAction.
     * <p>
     * Checks an action after login and the request format sent to /doAction.
     */
    @Test
    @DisplayName("После входа действие доступно")
    void performsActionAfterLogin() {
        Allure.step("Настроить успешные ответы внешних сервисов", () -> {
            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/auth"))
                            .willReturn(WireMock.aResponse().withStatus(200)));
            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/doAction"))
                            .willReturn(WireMock.aResponse().withStatus(200)));
        });

        login();

        Allure.step("Выполнить действие после входа", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("OK", body.path("result").asText(),
                    "После входа ожидалось успешное действие: /doAction вернул HTTP 200");
        });

        Allure.step("Проверить один вызов /doAction, его заголовки и токен",
                () -> {
                    wireMock.verify(
                            1,
                            WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                                    .withHeader("Content-Type",
                                            WireMock.containing("application/x-www-form-urlencoded"))
                                    .withHeader("Accept", WireMock.equalTo("application/json"))
                                    .withFormParam("token", WireMock.equalTo(token)));
                });
    }

    /**
     * Проверяет отказ действия без входа и отсутствие внешних запросов.
     * <p>
     * Checks rejection before login and the absence of external requests.
     */
    @Test
    @DisplayName("Без входа действие недоступно")
    void rejectsActionBeforeLogin() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        Allure.step("Отправить действие без входа", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "ACTION должен быть отклонён для токена, не прошедшего вход");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на отклонённое действие должен содержать причину ошибки");
        });
        Allure.step("Проверить отсутствие внешних запросов", () -> {
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
    }

    /**
     * Проверяет ошибку действия при отказе /doAction.
     * <p>
     * Checks that a /doAction failure produces an action error.
     *
     * @param status
     *                            неуспешный HTTP-статус сервиса действий / the
     *                            external action service's failure status
     * @param bodyDescription
     *                            описание тела ответа внешнего сервиса / a readable
     *                            external response body description
     * @param externalBody
     *                            тело внешнего ответа / external response body
     */
    @ParameterizedTest(name = "— HTTP {0}; {1}")
    @CsvFileSource(resources = "/fixtures/unsuccessful-external-responses.csv", numLinesToSkip = 1)
    @DisplayName("Ошибка внешнего сервиса возвращается пользователю")
    @Description("Если внешний сервис не выполнил действие, пользователь должен получить ошибку с причиной. "
            + "Даже сообщение об успехе в теле внешнего ответа не должно менять результат.")
    void reportsExternalActionFailure(int status, String bodyDescription, String externalBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        Allure.step("Настроить ответ сервиса действий: HTTP " + status + "; " + bodyDescription, () -> {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                    .willReturn(WireMock.aResponse().withStatus(status).withBody(externalBody)));
        });
        login();
        Allure.step("Проверить ошибку действия и её причину", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("ERROR", body.path("result").asText(),
                    "Отказ внешнего сервиса действий не должен выдаваться за успех");
            assertTrue(body.path("message").isTextual()
                    && !body.path("message").asText().isBlank(),
                    "Ответ на неуспешное действие должен содержать причину ошибки");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
    }

    /**
     * Проверяет действие при HTTP 200 с разными телами ответа /doAction.
     * <p>
     * Checks an action with different /doAction response bodies at HTTP 200.
     *
     * @param bodyDescription
     *                            описание тела ответа / a readable response body
     *                            description
     * @param externalBody
     *                            тело внешнего ответа / external response body
     */
    @ParameterizedTest(name = "— {0}")
    @CsvFileSource(resources = "/fixtures/successful-external-responses.csv", numLinesToSkip = 1)
    @DisplayName("При HTTP 200 действие не зависит от тела ответа")
    void ignoresSuccessfulActionBody(String bodyDescription, String externalBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody(externalBody)));
        login();
        Allure.step("Проверить успешное действие при HTTP 200: " + bodyDescription, () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "При HTTP 200 от /doAction ожидалось успешное действие независимо от тела ответа");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/doAction"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
    }

    /**
     * Выполняет вход перед проверкой действия.
     * <p>
     * Logs in before testing an action.
     */
    private void login() {
        Allure.step("Войти перед выполнением действия", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            JsonNode body = mapper.readTree(response.body());
            assertEquals("OK", body.path("result").asText(),
                    "Перед выполнением действия пользователь должен успешно войти");
            sessionCreated = true;
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                    .withFormParam("token", WireMock.equalTo(token)));
        });
    }

    /**
     * Завершает созданную в тесте сессию, в том числе после падения проверки.
     * <p>
     * Closes the test session, including after an assertion failure.
     */
    @AfterEach
    void removeSession() {
        if (!sessionCreated) {
            return;
        }
        Allure.step("Удалить сессию тестового пользователя", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Тестовая сессия должна быть удалена при очистке");
        });
    }
}
