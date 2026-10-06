package com.writingassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.writingassistant.config.AiProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Thin client for any OpenAI-compatible chat completions API (Gemini, OpenRouter, Groq, OpenAI...)
 * with streaming enabled. Emits the text fragments as the model produces them.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {};
    private static final String GENERIC_ERROR = "The AI service is unavailable right now. Please try again.";
    private static final String RATE_LIMIT_ERROR = "The AI provider's request limit was reached. Please wait a minute and try again.";
    private static final String DONE_MARKER = "[DONE]";
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    /** Transient provider failure (429 / 5xx) that is safe to retry before any token was streamed. */
    private static class RetryableProviderException extends AiProviderException {
        RetryableProviderException(String message) {
            super(message);
        }
    }

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final WebClient webClient;

    public AiClient(AiProperties props, ObjectMapper mapper, WebClient.Builder builder) {
        this.props = props;
        this.mapper = mapper;
        this.webClient = builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Flux<String> stream(String systemPrompt, String userPrompt) {
        if (!props.hasApiKey()) {
            log.error("AI_API_KEY is not set");
            return Flux.error(new AiProviderException("The server has no AI API key configured."));
        }

        Map<String, Object> body = Map.of(
                "model", props.model(),
                "max_tokens", props.maxTokens(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(errorBody -> {
                            // Provider details go to the log only, never to the client.
                            log.warn("AI API error {}: {}", response.statusCode(), errorBody);
                            int status = response.statusCode().value();
                            if (status == 429) {
                                return Mono.error(new RetryableProviderException(RATE_LIMIT_ERROR));
                            }
                            return Mono.error(response.statusCode().is5xxServerError()
                                    ? new RetryableProviderException(GENERIC_ERROR)
                                    : new AiProviderException(GENERIC_ERROR));
                        }))
                .bodyToFlux(SSE_TYPE)
                .concatMap(this::toText)
                // Only connection-time status errors are retryable, so no tokens are ever duplicated.
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(RetryableProviderException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorMap(e -> !(e instanceof AiProviderException), e -> {
                    log.error("AI stream failed", e);
                    return new AiProviderException(GENERIC_ERROR, e);
                });
    }

    /** Extracts {@code choices[0].delta.content} from a chunk; ignores the final [DONE] marker. */
    private Mono<String> toText(ServerSentEvent<String> event) {
        String data = event.data();
        if (data == null || DONE_MARKER.equals(data.trim())) {
            return Mono.empty();
        }
        try {
            JsonNode content = mapper.readTree(data).path("choices").path(0).path("delta").path("content");
            return content.isTextual() && !content.asText().isEmpty() ? Mono.just(content.asText()) : Mono.empty();
        } catch (Exception e) {
            return Mono.error(new AiProviderException(GENERIC_ERROR, e));
        }
    }
}
