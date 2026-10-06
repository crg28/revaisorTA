package com.writingassistant.api;

import com.writingassistant.service.AiProviderException;
import com.writingassistant.service.RewriteService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class RewriteController {

    private final RewriteService rewriteService;

    public RewriteController(RewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    @GetMapping("/modes")
    public ModesResponse modes() {
        return ModesResponse.create();
    }

    /**
     * Streams the result as Server-Sent Events:
     * {@code token} (data: {"text": "..."}), then {@code done}, or {@code error} (data: {"message": "..."}).
     */
    @PostMapping(value = "/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, String>>> rewrite(@Valid @RequestBody RewriteRequest request) {
        return rewriteService.rewrite(request.mode(), request.tone(), request.text())
                .map(token -> event("token", Map.of("text", token)))
                .concatWith(Flux.just(event("done", Map.of())))
                // Headers are already sent once streaming starts, so errors travel as an event.
                .onErrorResume(e -> Flux.just(event("error", Map.of("message", userMessage(e)))));
    }

    private static String userMessage(Throwable e) {
        return e instanceof AiProviderException || e instanceof IllegalArgumentException
                ? e.getMessage()
                : "An unexpected error occurred.";
    }

    private static ServerSentEvent<Map<String, String>> event(String name, Map<String, String> data) {
        return ServerSentEvent.<Map<String, String>>builder().event(name).data(data).build();
    }
}
