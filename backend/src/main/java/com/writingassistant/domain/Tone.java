package com.writingassistant.domain;

/** Target tones available for {@link WritingMode#TONE}. */
public enum Tone {
    FRIENDLY("Friendly", "friendly and warm"),
    PROFESSIONAL("Professional", "professional and polished"),
    PERSUASIVE("Persuasive", "persuasive and compelling"),
    EMPATHETIC("Empathetic", "empathetic and understanding"),
    CONFIDENT("Confident", "confident and assertive");

    private final String label;
    private final String promptDescription;

    Tone(String label, String promptDescription) {
        this.label = label;
        this.promptDescription = promptDescription;
    }

    public String label() {
        return label;
    }

    public String promptDescription() {
        return promptDescription;
    }
}
