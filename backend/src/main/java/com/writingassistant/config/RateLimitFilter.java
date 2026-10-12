package com.writingassistant.config;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Fixed-window, per-IP rate limit on the AI endpoint. Protects the API key
 * (and its billing) from abuse. In-memory: fine for a single instance.
 */
@Component
@Order(0)
public class RateLimitFilter implements WebFilter {

    private static final PathPattern REWRITE_PATH = PathPatternParser.defaultInstance.parse("/api/rewrite");
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public RateLimitFilter(@Value("${app.rate-limit-per-minute}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        // Same matcher the router uses, so ";x=1" and "%72" tricks can't dodge the limit.
        boolean limited = request.getMethod() == HttpMethod.POST
                && REWRITE_PATH.matches(request.getPath().pathWithinApplication());
        if (!limited || tryAcquire(clientKey(exchange), System.currentTimeMillis())) {
            return chain.filter(exchange);
        }

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(WINDOW_MS / 1000));
        byte[] body = "{\"message\":\"Too many requests. Please try again in a minute.\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    boolean tryAcquire(String key, long now) {
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MS);
        }
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= WINDOW_MS ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        return w.count() <= limitPerMinute;
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }
}
