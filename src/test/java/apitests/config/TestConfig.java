package apitests.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class loads the global test settings from the test.properties file,
 * which is located in config/test.properties
 */
public class TestConfig {
    private final Properties properties = new Properties();

    /**
     * This constructor will load the shared test settings
     *
     * @throws IllegalStateException if the resource cannot be loaded or a required setting is blank
     */
    public TestConfig() {
        try (InputStream input = TestConfig.class.getResourceAsStream("/config/test.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing configuration resource: /config/test.properties.");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load config/test.properties", exception);
        }

        requireProperty("app.base.url");
        requireProperty("app.timeout.seconds");
        requireProperty("api.key");
        requireProperty("wiremock.port");
    }

    /**
     * This method will return the URL of the system we are testing
     *
     * @return the configured application base URL
     */
    public String getAppBaseUrl() {
        return properties.getProperty("app.base.url");
    }

    /**
     * This method will return the API Key of the system we are testing
     *
     * @return the configured API key
     */
    public String getApiKey() {
        return properties.getProperty("api.key");
    }

    /**
     * This method will return the wiremock port of the system we are testing
     *
     * @return the configured WireMock port as text
     */
    public String getAppWireMockPort() {
        return properties.getProperty("wiremock.port");
    }

    /**
     * Checks that a required setting exists without including its value in errors.
     *
     * @param key the property name to check
     * @throws IllegalStateException if the property is missing, empty, or whitespace-only
     */
    private void requireProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing or blank required setting: " + key + " in config/test.properties");
        }
    }

    /**
     * This method will get the specified timeout for requests to the system we are testing
     *
     * @return the configured timeout in seconds
     * @throws IllegalStateException if the timeout is zero or negative
     * @throws NumberFormatException if the timeout cannot be parsed as an integer
     */
    public int getAppTimeoutSeconds() {
        int seconds = Integer.parseInt(properties.getProperty("app.timeout.seconds"));
        if (seconds <= 0) {
            throw new IllegalStateException("app.timeout.seconds must be greater than zero");
        }
        return seconds;
    }
}
