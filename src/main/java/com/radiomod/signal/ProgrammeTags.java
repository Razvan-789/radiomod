package com.radiomod.signal;

/** Title and artist extracted from a decoded RadioText or DLS message. */
public record ProgrammeTags(String title, String artist) {
    public static final ProgrammeTags EMPTY = new ProgrammeTags("", "");

    public static ProgrammeTags fromText(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        // Prefer the spaced separator so a hyphen inside a title or name is preserved.
        int separator = text.indexOf(" - ");
        if (separator < 0) separator = text.indexOf('-');
        if (separator < 0) return EMPTY;
        String artist = text.substring(0, separator).strip();
        String title = text.substring(separator + (text.startsWith(" - ", separator) ? 3 : 1)).strip();
        return title.isEmpty() || artist.isEmpty() ? EMPTY : new ProgrammeTags(title, artist);
    }

    public boolean present() { return !title.isEmpty() && !artist.isEmpty(); }
}
