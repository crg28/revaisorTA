package com.writingassistant.api;

import com.writingassistant.domain.Tone;
import com.writingassistant.domain.WritingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RewriteRequest(
        @NotBlank(message = "The text cannot be empty.")
        @Size(max = MAX_TEXT_LENGTH, message = "The text cannot exceed " + MAX_TEXT_LENGTH + " characters.")
        String text,

        @NotNull(message = "You must choose a mode.")
        WritingMode mode,

        /** Only used (and required) by {@link WritingMode#TONE}. */
        Tone tone) {

    public static final int MAX_TEXT_LENGTH = 5000;
}
