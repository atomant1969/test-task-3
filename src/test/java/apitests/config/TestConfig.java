package apitests.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Загружает общие настройки тестов из ресурса config/test.properties.
 * <p>
 * Loads shared test settings from the config/test.properties resource.
 */
public class TestConfig {
    private final Properties properties = new Properties();

    /**
     * Загружает настройки и проверяет наличие обязательных значений.
     * <p>
     * Loads settings and checks that required values are present.
     *
     * @throws IllegalStateException
     *                                   если ресурс недоступен или обязательная
     *                                   настройка не задана /
     *                                   if the resource cannot be loaded or a
     *                                   required setting is blank
     */
    public TestConfig() {
        try (InputStream input = TestConfig.class.getResourceAsStream("/config/test.properties")) {
            if (input == null) {
                throw new IllegalStateException("Отсутствует ресурс настроек: /config/test.properties.");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось загрузить config/test.properties", exception);
        }

        requireProperty("app.base.url");
        requireProperty("app.timeout.seconds");
        requireProperty("api.key");
        requireProperty("wiremock.port");
    }

    /**
     * Возвращает базовый URL тестируемого приложения.
     * <p>
     * Returns the base URL of the application under test.
     *
     * @return базовый URL из настроек / the configured application base URL
     */
    public String getAppBaseUrl() {
        return properties.getProperty("app.base.url");
    }

    /**
     * Возвращает ключ доступа к API тестируемого приложения.
     * <p>
     * Returns the application's API key.
     *
     * @return ключ API из настроек / the configured API key
     */
    public String getApiKey() {
        return properties.getProperty("api.key");
    }

    /**
     * Возвращает порт WireMock для подмены внешнего сервиса.
     * <p>
     * Returns the WireMock port used to replace the external service.
     *
     * @return порт WireMock в виде строки / the configured WireMock port as text
     */
    public String getAppWireMockPort() {
        return properties.getProperty("wiremock.port");
    }

    /**
     * Проверяет обязательную настройку, не раскрывая её значение в сообщении об
     * ошибке.
     * <p>
     * Checks a required setting without exposing its value in error messages.
     *
     * @param key
     *                имя проверяемой настройки / the property name to check
     * @throws IllegalStateException
     *                                   если настройка отсутствует, пуста или
     *                                   содержит только пробельные символы /
     *                                   if the property is missing, empty, or
     *                                   whitespace-only
     */
    private void requireProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Отсутствует или пуста обязательная настройка: " + key + " в config/test.properties");
        }
    }

    /**
     * Возвращает тайм-аут HTTP-запросов к приложению и проверяет, что он
     * положительный.
     * <p>
     * Returns the HTTP request timeout and checks that it is positive.
     *
     * @return тайм-аут в секундах / the configured timeout in seconds
     * @throws IllegalStateException
     *                                   если тайм-аут равен нулю или отрицателен /
     *                                   if the timeout is zero or negative
     * @throws NumberFormatException
     *                                   если значение нельзя преобразовать в целое
     *                                   число /
     *                                   if the timeout cannot be parsed as an
     *                                   integer
     */
    public int getAppTimeoutSeconds() {
        int seconds = Integer.parseInt(properties.getProperty("app.timeout.seconds"));
        if (seconds <= 0) {
            throw new IllegalStateException("Значение app.timeout.seconds должно быть больше нуля");
        }
        return seconds;
    }
}
