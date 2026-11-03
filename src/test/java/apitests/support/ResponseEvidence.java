package apitests.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

/**
 * Добавляет ответы в Allure и скрывает ключ API и токен.
 * <p>
 * Attaches responses to Allure, masking the API key and token.
 */
public final class ResponseEvidence {
    /**
     * Запрещает создание экземпляров служебного класса. / Prevents utility class
     * instantiation.
     */
    private ResponseEvidence() {
    }

    /**
     * Прикладывает статус и поля result и message. Ключ и токен скрываются;
     * тело ответа, не соответствующее формату JSON-объекта, не прикладывается.
     * <p>
     * Attaches the status, result and message, masking the key and token.
     * Bodies that aren't JSON objects are omitted.
     *
     * @param response
     *                     ответ приложения / the application's response
     * @param apiKey
     *                     ключ API, который нужно скрыть, если он попал в ответ /
     *                     the API key to redact if echoed
     * @param token
     *                     токен сессии, который нужно скрыть, если он попал в ответ
     *                     / the session token to redact if echoed
     */
    public static void attach(HttpResponse<String> response, String apiKey, String token) {
        Allure.addAttachment("HTTP-статус", "text/plain",
                Integer.toString(response.statusCode()), ".txt");
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode body = mapper.readTree(response.body());
            if (body == null || !body.isObject()) {
                Allure.addAttachment("Примечание к ответу", "Ожидался объект JSON; исходное тело ответа не приложено.");
                return;
            }
            ObjectNode evidence = mapper.createObjectNode();
            for (String field : new String[] { "result", "message" }) {
                if (body.path(field).isTextual()) {
                    String value = redact(body.path(field).asText(), apiKey, token);
                    evidence.put(field, value);
                }
            }
            Allure.addAttachment("Ответ приложения", "application/json",
                    evidence.toPrettyString(), ".json");
        } catch (JsonProcessingException exception) {
            Allure.addAttachment("Примечание к ответу", "Некорректный JSON; исходное тело ответа не приложено.");
        }
    }

    /**
     * Скрывает ключ API и токен в диагностическом тексте без учёта регистра.
     * <p>
     * Redacts the API key and token from diagnostic text, ignoring case.
     *
     * @param value
     *                   текст диагностического поля / the diagnostic field's text
     * @param apiKey
     *                   ключ API, который нужно скрыть / the API key to redact
     * @param token
     *                   токен сессии, который нужно скрыть / the session token to
     *                   redact
     * @return текст с маркером скрытого значения вместо ключа и токена / text with
     *         known credentials redacted
     */
    private static String redact(String value, String apiKey, String token) {
        String redacted = value;
        for (String secret : new String[] { apiKey, token }) {
            if (secret != null && !secret.isEmpty()) {
                redacted = Pattern.compile(Pattern.quote(secret), Pattern.CASE_INSENSITIVE)
                        .matcher(redacted).replaceAll("[скрыто]");
            }
        }
        return redacted;
    }
}
