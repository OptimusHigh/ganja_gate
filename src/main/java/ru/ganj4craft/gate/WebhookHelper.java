package ru.ganj4craft.gate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WebhookHelper {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public static void postJsonAsync(String url, String json, String token) {
        if (url == null || url.isEmpty() || json == null) return;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

            if (token != null && !token.isEmpty()) {
                builder.header("x-api-token", token);
            }

            HttpRequest request = builder.build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        System.err.println("[GanjaGate/Webhook] Request error: " + err.getMessage());
                    } else if (resp != null) {
                        int code = resp.statusCode();
                        if (code >= 200 && code < 300) {
                            System.out.println("[GanjaGate/Webhook] Delivered: HTTP " + code);
                        } else {
                            System.err.println("[GanjaGate/Webhook] Failed: HTTP " + code + " " + resp.body());
                        }
                    }
                });
        } catch (Throwable t) {
            System.err.println("[GanjaGate/Webhook] Send exception: " + t.getMessage());
        }
    }
}

