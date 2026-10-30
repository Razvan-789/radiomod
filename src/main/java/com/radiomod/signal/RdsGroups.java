package com.radiomod.signal;

public final class RdsGroups {
    private static final String[] GROUP_CODES = new String[]{
            "0A", "0B", "1A", "1B", "2A", "2B", "3A", "3B",
            "4A", "4B", "5A", "5B", "6A", "6B", "7A", "7B",
            "8A", "8B", "9A", "9B", "10A", "10B", "11A", "11B",
            "12A", "12B", "13A", "13B", "14A", "14B", "15A", "15B"
    };

    private RdsGroups() {
    }

    public static int size() {
        return GROUP_CODES.length;
    }

    public static String codeByIndex(int index) {
        int clamped = Math.max(0, Math.min(GROUP_CODES.length - 1, index));
        return GROUP_CODES[clamped];
    }

    public static int indexByCode(String code) {
        for (int i = 0; i < GROUP_CODES.length; i++) {
            if (GROUP_CODES[i].equalsIgnoreCase(code)) {
                return i;
            }
        }
        return 0;
    }
}
