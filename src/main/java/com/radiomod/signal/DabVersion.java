package com.radiomod.signal;

public enum DabVersion {
    DAB("DAB", "MP3", "mp3"),
    DAB_PLUS("DAB+", "MP3 (Opus rate)", "mp3");

    private final String label;
    private final String codecLabel;
    private final String codecToken;

    DabVersion(String label, String codecLabel, String codecToken) {
        this.label = label;
        this.codecLabel = codecLabel;
        this.codecToken = codecToken;
    }

    public String getLabel() {
        return label;
    }

    public String getCodecLabel() {
        return codecLabel;
    }

    public String getCodecToken() {
        return codecToken;
    }

    public DabVersion next() {
        DabVersion[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
