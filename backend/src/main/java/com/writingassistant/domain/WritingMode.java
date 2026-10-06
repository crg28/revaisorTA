package com.writingassistant.domain;

/** The presets the assistant offers. Each one carries the instruction sent to the model. */
public enum WritingMode {
    CORRECT("Fix", "Fixes spelling, grammar and punctuation.",
            "Correct spelling, grammar and punctuation. Keep the original wording, meaning and tone as much as possible.",
            false),
    SUMMARIZE("Summarize", "Condenses the text to its key ideas.",
            "Summarize the text, keeping only the key ideas. Be concise and faithful to the original.",
            false),
    FORMAL("More formal", "Rewrites in a formal, professional register.",
            "Rewrite the text in a formal, professional register. Keep the meaning and all the information.",
            false),
    SHORTEN("Shorter", "Says the same with fewer words.",
            "Rewrite the text to be significantly shorter without losing its meaning or key details.",
            false),
    TONE("Change tone", "Adapts the text to the tone you choose.",
            "Rewrite the text with a %s tone. Keep the meaning and all the information.",
            true);

    private final String label;
    private final String description;
    private final String instruction;
    private final boolean requiresTone;

    WritingMode(String label, String description, String instruction, boolean requiresTone) {
        this.label = label;
        this.description = description;
        this.instruction = instruction;
        this.requiresTone = requiresTone;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean requiresTone() {
        return requiresTone;
    }

    /** The instruction for this mode; for {@link #TONE} the tone is interpolated. */
    public String instruction(Tone tone) {
        return requiresTone ? instruction.formatted(tone.promptDescription()) : instruction;
    }
}
