package com.writingassistant.api;

import com.writingassistant.domain.Tone;
import com.writingassistant.domain.WritingMode;
import java.util.Arrays;
import java.util.List;

/** Catalog of modes and tones, so the frontend never hardcodes them. */
public record ModesResponse(int maxTextLength, List<ModeDto> modes, List<ToneDto> tones) {

    public record ModeDto(String id, String label, String description, boolean requiresTone) {}

    public record ToneDto(String id, String label) {}

    public static ModesResponse create() {
        return new ModesResponse(
                RewriteRequest.MAX_TEXT_LENGTH,
                Arrays.stream(WritingMode.values())
                        .map(m -> new ModeDto(m.name(), m.label(), m.description(), m.requiresTone()))
                        .toList(),
                Arrays.stream(Tone.values()).map(t -> new ToneDto(t.name(), t.label())).toList());
    }
}
