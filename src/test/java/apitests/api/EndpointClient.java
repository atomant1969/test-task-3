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

/**
 * This class sends requests to the apps endpoint and receives the responses
 */
public class EndpointClient {
    private final TestConfig config;
    private final HttpClient httpClient;

    /**
     * Creates an endpoint client instance.
     *
     * @param config the shared test configuration
     */
    public EndpointClient(TestConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();

    }

    /**
     * Sends an action to /endpoint and returns the application's response.
     *
     * @param token the session token to send
     * @param action the action to request
     * @return the application's HTTP response
     * @throws IOException if the HTTP request fails
     * @throws InterruptedException if the request is interrupted
     */
    public HttpResponse<String> sendAction(String token, String action) throws IOException, InterruptedException {
        String body = "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
            + "&action=" + URLEncoder.encode(action, StandardCharsets.UTF_8);
        URI endpoint = URI.create(config.getAppBaseUrl()).resolve("/endpoint");

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .header("X-Api-Key", config.getApiKey())
            .timeout(Duration.ofSeconds(config.getAppTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
