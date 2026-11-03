package apitests.api;

import apitests.config.TestConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

/**
 * Отправляет HTTP-запросы к /endpoint и возвращает ответы приложения.
 * <p>
 * Sends HTTP requests to /endpoint and returns the application's responses.
 */
public class EndpointClient {
    private final TestConfig config;
    private final HttpClient httpClient;

    /**
     * Создаёт HTTP-клиент с общими настройками тестов.
     * <p>
     * Creates an HTTP client using the shared test configuration.
     *
     * @param config
     *                   общие настройки тестов / the shared test configuration
     */
    public EndpointClient(TestConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();

    }

    /**
     * Отправляет действие в /endpoint с ключом API из настроек.
     * <p>
     * Sends an action to /endpoint using the configured API key.
     *
     * @param token
     *                   токен сессии / the session token to send
     * @param action
     *                   запрашиваемое действие / the requested action
     * @return HTTP-ответ приложения / the application's HTTP response
     * @throws IOException
     *                                  если не удалось выполнить HTTP-запрос / if
     *                                  the HTTP request fails
     * @throws InterruptedException
     *                                  если выполнение запроса прервано / if the
     *                                  request is interrupted
     */
    public HttpResponse<String> sendAction(String token, String action) throws IOException, InterruptedException {
        return sendAction(token, action, config.getApiKey());
    }

    /**
     * Отправляет запрос с явно заданным ключом API для проверок валидации.
     * Значение null исключает поле или заголовок; пустая строка передаётся как
     * есть.
     * <p>
     * Sends a request with an explicit API key for validation tests.
     * Null omits a field or header; an empty string is sent as-is.
     *
     * @param token
     *                   токен сессии или null, чтобы не передавать поле / the
     *                   token, or null to omit the field
     * @param action
     *                   действие или null, чтобы не передавать поле / the action,
     *                   or null to omit the field
     * @param apiKey
     *                   ключ API или null, чтобы не передавать заголовок / the API
     *                   key, or null to omit the header
     * @return HTTP-ответ приложения / the application's HTTP response
     * @throws IOException
     *                                  если не удалось выполнить HTTP-запрос / if
     *                                  the HTTP request fails
     * @throws InterruptedException
     *                                  если выполнение запроса прервано / if the
     *                                  request is interrupted
     */
    public HttpResponse<String> sendAction(String token, String action, String apiKey)
            throws IOException, InterruptedException {
        StringJoiner body = new StringJoiner("&");
        if (token != null) {
            body.add("token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
        }
        if (action != null) {
            body.add("action=" + URLEncoder.encode(action, StandardCharsets.UTF_8));
        }
        URI endpoint = URI.create(config.getAppBaseUrl()).resolve("/endpoint");

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(config.getAppTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null) {
            builder.header("X-Api-Key", apiKey);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
