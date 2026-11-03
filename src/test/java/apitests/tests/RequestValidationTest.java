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
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Проверки ключа API и полей token и action.
 * <p>
 * API key, token and action validation tests.
 */
@DisplayName("Проверка входных данных и доступа")
public class RequestValidationTest {
    private final TestConfig config = new TestConfig();
    private final EndpointClient client = new EndpointClient(config);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token = UUID.randomUUID().toString()
            .replace("-", "").toUpperCase(Locale.ROOT);
    private String cleanupToken = token;

    @RegisterExtension
    final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig()
                    .port(Integer.parseInt(config.getAppWireMockPort())))
            .build();

    /**
     * Настраивает HTTP 200 от внешних сервисов, чтобы проверять только входные
     * данные.
     * <p>
     * Stubs HTTP 200 from external services to isolate input validation.
     */
    @BeforeEach
    void prepareExternalServices() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/auth"))
                .willReturn(WireMock.aResponse().withStatus(200)));
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/doAction"))
                .willReturn(WireMock.aResponse().withStatus(200)));
    }

    /**
     * Проверяет каждое действие с неверными токенами, в том числе без поля token.
     * <p>
     * Tests every documented action with malformed tokens, including an omitted
     * token field.
     *
     * @param action
     *                      действие из задания / the documented action to request
     * @param tokenCase
     *                      категория неверного токена / the malformed token
     *                      category
     */
    @ParameterizedTest(name = "— {0}: {1}")
    @MethodSource("invalidTokenCases")
    @DisplayName("Неверные токены отклоняются без вызова внешних сервисов")
    void rejectsInvalidToken(String action, String tokenCase) {
        String invalidToken = switch (tokenCase) {
            case "поле отсутствует" -> null;
            case "пустая строка" -> "";
            case "31 символ" -> token.substring(1);
            case "33 символа" -> token + "A";
            case "строчная буква" -> "a" + token.substring(1);
            case "пробел" -> token.substring(1) + " ";
            case "табуляция" -> token.substring(1) + "\t";
            case "перенос строки" -> token.substring(1) + "\n";
            case "перенос строки после 32 символов" -> token + "\n";
            case "символ &" -> token.substring(1) + "&";
            case "символ +" -> token.substring(1) + "+";
            case "символ =" -> token.substring(1) + "=";
            case "буква вне ASCII" -> token.substring(1) + "\u0416";
            default -> throw new IllegalArgumentException("Неизвестная категория токена: " + tokenCase);
        };
        cleanupToken = invalidToken;
        Allure.step("Отправить " + action + " с токеном: " + tokenCase, () -> {
            assertRejected(client.sendAction(invalidToken, action), invalidToken);
        });
        Allure.step("Проверить, что неверный токен не вызвал внешних запросов", () -> {
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
    }

    /**
     * Проверяет обязательность ключа API для каждого действия, в том числе при
     * действующей сессии.
     * <p>
     * Tests required API credentials on every action, even for an already
     * authenticated token.
     *
     * @param action
     *                    действие из задания / the documented action to request
     * @param keyCase
     *                    описание ошибки ключа, без самого значения / key error
     *                    description, without the credential value
     */
    @ParameterizedTest(name = "— {0}: {1}")
    @MethodSource("invalidKeyCases")
    @DisplayName("Для каждого действия требуется правильный ключ API")
    void rejectsInvalidApiKey(String action, String keyCase) {
        login();
        wireMock.resetRequests();
        String invalidKey = switch (keyCase) {
            case "заголовок отсутствует" -> null;
            case "пустая строка" -> "";
            case "неверное значение" -> config.getApiKey() + "-invalid";
            case "неверный регистр" -> config.getApiKey().toUpperCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Неизвестная категория ключа API: " + keyCase);
        };
        Allure.step("Отправить " + action + " с ключом API: " + keyCase, () -> {
            assertRejected(client.sendAction(token, action, invalidKey), token);
        });
        Allure.step("Проверить отсутствие внешних запросов при неверном ключе API", () -> {
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
        Allure.step("Проверить, что действующая сессия сохранилась", () -> {
            HttpResponse<String> response = client.sendAction(token, "ACTION");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Запрос с неверным ключом API не должен удалять ранее созданную сессию");
        });
    }

    /**
     * Проверяет отказ при отсутствующем или неподдерживаемом действии после
     * успешного входа.
     * <p>
     * Checks rejection of missing and unsupported action names after establishing a
     * valid session.
     *
     * @param label
     *                   описание неверного действия / a readable description of the
     *                   invalid action
     * @param action
     *                   значение поля или null, чтобы не передавать его / the form
     *                   value, or null to omit it
     */
    @ParameterizedTest(name = "— {0}")
    @MethodSource("invalidActionCases")
    @DisplayName("Отсутствующие и неподдерживаемые действия отклоняются")
    void rejectsInvalidAction(String label, String action) {
        login();
        wireMock.resetRequests();
        Allure.step("Отправить недопустимое действие: " + label, () -> {
            assertRejected(client.sendAction(token, action), token);
        });
        Allure.step("Проверить отсутствие внешних запросов при недопустимом действии", () -> {
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
    }

    /**
     * Проверяет точное совпадение названий действий; правила обработки пробелов
     * требуют уточнения.
     * <p>
     * Checks exact action names; the handling of surrounding whitespace needs
     * confirmation.
     *
     * @param label
     *                   описание пробельных символов вокруг действия / a
     *                   description of the surrounding whitespace
     * @param action
     *                   действие с пробельными символами / the padded action value
     */
    @ParameterizedTest(name = "— {0}")
    @MethodSource("paddedActionCases")
    @Tag("требует-уточнения")
    @DisplayName("Пробелы в названии действия — требуется уточнение")
    @Description("Можно ли передавать пробелы и переносы строк вокруг LOGIN, ACTION и LOGOUT? "
            + "В задании это не указано. Здесь ожидается отказ, но падение проверки пока не считаем "
            + "дефектом: сначала нужно уточнить правило.")
    void checksExactActionNames(String label, String action) {
        login();
        wireMock.resetRequests();
        Allure.step("Проверить отклонение действия: " + label, () -> {
            HttpResponse<String> response = client.sendAction(token, action);
            Allure.addAttachment("Число внешних запросов при выполнении действия",
                    Integer.toString(wireMock.getAllServeEvents().size()));
            assertRejected(response, token,
                    "Требует уточнения: пробельные символы вокруг действия — здесь ожидается отказ, "
                            + "но в задании это правило не указано");
        });
        Allure.step("Проверить отсутствие внешних запросов для действия с пробельными символами", () -> {
            wireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        });
    }

    /**
     * Проверяет допустимые категории символов токена, включая токены только из
     * цифр.
     * <p>
     * Exercises the allowed token character categories, including digit-only
     * tokens.
     *
     * @param tokenCase
     *                      категория допустимого токена / the category of valid
     *                      token to generate
     */
    @ParameterizedTest(name = "— {0}")
    @ValueSource(strings = { "заглавные буквы", "цифры", "буквы и цифры" })
    @DisplayName("Вход с допустимыми символами токена")
    @Description("По заданию токен состоит из 32 символов A-Z и 0-9. Проверяем буквы, цифры и их сочетание. "
            + "Токены с буквами содержат Z: приложение ошибочно отклоняет их, разрешая только A-F. "
            + "Из-за этого пользователь с допустимым токеном не может войти. Два падения показывают один баг.")
    void acceptsValidTokenCharacters(String tokenCase) {
        String validToken = switch (tokenCase) {
            case "заглавные буквы" -> "Z" + token.substring(1).replaceAll("[0-9]", "Z");
            case "цифры" -> String.format(Locale.ROOT, "%032d",
                    ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
            case "буквы и цифры" -> "AZ09" + token.substring(4);
            default -> throw new IllegalArgumentException("Неизвестная категория токена: " + tokenCase);
        };
        cleanupToken = validToken;
        Allure.step("Войти с токеном длиной 32 символа: " + tokenCase, () -> {
            HttpResponse<String> response = client.sendAction(validToken, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), validToken);
            Allure.addAttachment("Число внешних запросов аутентификации",
                    Integer.toString(wireMock.getAllServeEvents().size()));
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Допустимый токен должен приниматься: 32 символа A-Z и 0-9 разрешены заданием");
            wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/auth"))
                    .withFormParam("token", WireMock.equalTo(validToken)));
        });
    }

    /**
     * Выполняет вход, чтобы проверять валидацию независимо от авторизации.
     * <p>
     * Logs in to test validation independently of authorization.
     */
    private void login() {
        Allure.step("Войти перед проверкой входных данных", () -> {
            HttpResponse<String> response = client.sendAction(token, "LOGIN");
            ResponseEvidence.attach(response, config.getApiKey(), token);
            assertEquals("OK", mapper.readTree(response.body()).path("result").asText(),
                    "Не удалось войти с корректными данными перед проверкой валидации");
        });
    }

    /**
     * Проверяет формат ошибки из задания, не задавая неоговорённые HTTP-статус и
     * текст сообщения.
     * <p>
     * Checks the documented error response without assuming an unspecified HTTP
     * status or wording.
     *
     * @param response
     *                         ответ приложения на неверный запрос / the
     *                         application's response to an invalid request
     * @param requestToken
     *                         токен, который нужно скрыть в отчёте / the token to
     *                         redact from report evidence, if present
     * @throws IOException
     *                         если JSON ответа не удалось разобрать / if response
     *                         JSON cannot be parsed
     */
    private void assertRejected(HttpResponse<String> response, String requestToken) throws IOException {
        assertRejected(response, requestToken, "Неверный запрос должен возвращать JSON с ошибкой и её причиной");
    }

    /**
     * Проверяет ответ с ошибкой и добавляет в отчёт пояснение конкретного сценария.
     * <p>
     * Checks the error response with a scenario-specific explanation for the
     * report.
     *
     * @param response
     *                         ответ приложения на неверный запрос / the
     *                         application's response to an invalid request
     * @param requestToken
     *                         токен, который нужно скрыть в отчёте / the token to
     *                         redact from report evidence, if present
     * @param explanation
     *                         заголовок ошибки для выделения неуточнённых
     *                         требований /
     *                         the failure heading used to distinguish unresolved
     *                         requirements
     * @throws IOException
     *                         если JSON ответа не удалось разобрать / if response
     *                         JSON cannot be parsed
     */
    private void assertRejected(HttpResponse<String> response, String requestToken, String explanation)
            throws IOException {
        ResponseEvidence.attach(response, config.getApiKey(), requestToken);
        JsonNode body = mapper.readTree(response.body());
        assertAll(explanation,
                () -> assertEquals("ERROR", body.path("result").asText(),
                        "Приложение должно отклонить неверный запрос"),
                () -> assertTrue(body.path("message").isTextual()
                        && !body.path("message").asText().isBlank(),
                        "Ответ с ошибкой должен объяснять причину отклонения запроса"),
                () -> assertTrue(response.headers().firstValue("Content-Type").orElse("")
                        .toLowerCase(Locale.ROOT).startsWith("application/json"),
                        "Ответы с ошибкой должны использовать описанный в задании формат JSON"));
    }

    /**
     * Завершает сессию, если приложение приняло проверяемый токен.
     * <p>
     * Closes the session if the application accepted the tested token.
     */
    @AfterEach
    void removeSession() {
        if (cleanupToken == null) {
            return;
        }
        Allure.step("Завершить сессию, если она была создана", () -> {
            HttpResponse<String> response = client.sendAction(cleanupToken, "LOGOUT");
            ResponseEvidence.attach(response, config.getApiKey(), cleanupToken);
        });
    }

    /**
     * Сочетает каждую категорию неверных токенов со всеми действиями из задания.
     * <p>
     * Pairs each malformed token category with every documented action.
     *
     * @return пары действия и категории токена / action and token-category
     *         arguments
     */
    private static Stream<Arguments> invalidTokenCases() {
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action -> Stream.of(
                "поле отсутствует", "пустая строка", "31 символ", "33 символа", "строчная буква",
                "пробел", "табуляция", "перенос строки", "перенос строки после 32 символов",
                "символ &", "символ +", "символ =", "буква вне ASCII")
                .map(tokenCase -> Arguments.of(action, tokenCase)));
    }

    /**
     * Сочетает каждую категорию неверных ключей API со всеми действиями из задания.
     * <p>
     * Pairs each invalid API-key category with every documented action.
     *
     * @return пары действия и категории ключа без значений ключей / action and
     *         key-category arguments without key values
     */
    private static Stream<Arguments> invalidKeyCases() {
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action -> Stream.of(
                "заголовок отсутствует", "пустая строка", "неверное значение", "неверный регистр")
                .map(keyCase -> Arguments.of(action, keyCase)));
    }

    /**
     * Возвращает отсутствующие, пустые, неизвестные действия и действия с неверным
     * регистром.
     * <p>
     * Provides omitted, empty, unknown, and incorrectly cased action values.
     *
     * @return описания и неверные значения поля / readable labels and invalid form
     *         values
     */
    private static Stream<Arguments> invalidActionCases() {
        return Stream.of(
                Arguments.of("поле отсутствует", null),
                Arguments.of("пустая строка", ""),
                Arguments.of("неизвестное название", "DELETE"),
                Arguments.of("login в нижнем регистре", "login"),
                Arguments.of("action в нижнем регистре", "action"),
                Arguments.of("logout в нижнем регистре", "logout"),
                Arguments.of("только пробел", " "));
    }

    /**
     * Возвращает действия с пробельными символами, обработка которых не определена
     * в задании.
     * <p>
     * Provides surrounding whitespace whose handling is not specified in the task.
     *
     * @return описания и действия с пробельными символами / readable labels and
     *         padded action values
     */
    private static Stream<Arguments> paddedActionCases() {
        return Stream.of(
                Arguments.of("пробел перед названием", " LOGIN"),
                Arguments.of("пробел после названия", "ACTION "),
                Arguments.of("перенос строки после названия", "LOGOUT\n"));
    }
}
