package com.radiomod.signal;

public final class PtyNames {
    private static final String[] NAMES = {
            "None", "News", "Current Affairs", "Information", "Sport", "Education", "Drama",
            "Culture", "Science", "Varied", "Pop Music", "Rock Music", "Easy Listening",
            "Light Classics", "Serious Classics", "Other Music", "Weather", "Finance", "Children",
            "Social Affairs", "Religion", "Phone In", "Travel", "Leisure", "Jazz Music",
            "Country Music", "National Music", "Oldies Music", "Folk Music", "Documentary",
            "Alarm Test", "Alarm"
    };

    private PtyNames() {
    }

    public static String name(int code) {
        return code >= 0 && code < NAMES.length ? NAMES[code] : "None";
    }
}
