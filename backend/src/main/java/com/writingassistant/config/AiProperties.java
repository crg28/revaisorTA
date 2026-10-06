package com.writingassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for an OpenAI-compatible chat completions API (Google Gemini by default). The API key comes from the environment. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String apiKey, String model, String baseUrl, int maxTokens) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
