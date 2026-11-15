package edu.comp5100.election.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A small real-HTTP client for tests: one instance is one browser-like session with its own
 * cookies. It never follows redirects by itself and remembers the CSRF token of the last page,
 * exactly as a browser form would send it back.
 */
public class HttpTestClient {

    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private final String baseUrl;
    private final HttpClient client;
    private String csrfToken;

    public HttpTestClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    /** Posts a form with the CSRF token of the last page that contained one. */
    public HttpResponse<String> postForm(String path, Map<String, String> fields) {
        Map<String, String> withToken = new LinkedHashMap<>(fields);
        if (csrfToken != null) {
            withToken.putIfAbsent("_csrf", csrfToken);
        }
        return postFormWithoutCsrf(path, withToken);
    }

    /** Posts exactly the given fields. */
    public HttpResponse<String> postFormWithoutCsrf(String path, Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Requests the Location of a redirect response; returns other responses unchanged. */
    public HttpResponse<String> follow(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 3) {
            return response;
        }
        String location = response.headers().firstValue("Location").orElseThrow();
        return get(location.startsWith("http") ? location.substring(baseUrl.length()) : location);
    }

    /** Signs in through the real login form and returns the page the user lands on. */
    public HttpResponse<String> login(String username, String password) {
        get("/login");
        HttpResponse<String> response = postForm("/login", Map.of("username", username, "password", password));
        while (response.statusCode() / 100 == 3) {
            response = follow(response);
        }
        return response;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            // Ask for HTML like a browser does; without this header Spring Boot answers errors as JSON.
            HttpResponse<String> response = client.send(request.timeout(Duration.ofSeconds(30))
                            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8").build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Matcher matcher = CSRF_FIELD.matcher(response.body());
            if (matcher.find()) {
                csrfToken = matcher.group(1);
            }
            return response;
        } catch (IOException failure) {
            throw new IllegalStateException("HTTP request failed", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", interrupted);
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
