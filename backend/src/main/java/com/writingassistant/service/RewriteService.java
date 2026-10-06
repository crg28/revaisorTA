package com.writingassistant.service;

import com.writingassistant.domain.Tone;
import com.writingassistant.domain.WritingMode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RewriteService {

    private final PromptBuilder promptBuilder;
    private final AiClient client;

    public RewriteService(PromptBuilder promptBuilder, AiClient client) {
        this.promptBuilder = promptBuilder;
        this.client = client;
    }

    /** Streams the transformed text. {@code tone} is required when the mode needs one. */
    public Flux<String> rewrite(WritingMode mode, Tone tone, String text) {
        if (mode.requiresTone() && tone == null) {
            return Flux.error(new IllegalArgumentException("The '" + mode.label() + "' mode requires choosing a tone."));
        }
        return client.stream(promptBuilder.systemPrompt(), promptBuilder.userPrompt(mode, tone, text));
    }
}
