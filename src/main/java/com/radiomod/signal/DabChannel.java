package com.radiomod.signal;

public record DabChannel(String label, int frequencyKhz) {
    private static final DabChannel[] CHANNELS = new DabChannel[]{
            new DabChannel("5A", 174_928),
            new DabChannel("5B", 176_640),
            new DabChannel("5C", 178_352),
            new DabChannel("5D", 180_064),
            new DabChannel("6A", 181_936),
            new DabChannel("6B", 183_648),
            new DabChannel("6C", 185_360),
            new DabChannel("6D", 187_072),
            new DabChannel("7A", 188_928),
            new DabChannel("7B", 190_640),
            new DabChannel("7C", 192_352),
            new DabChannel("7D", 194_064),
            new DabChannel("8A", 195_936),
            new DabChannel("8B", 197_648),
            new DabChannel("8C", 199_360),
            new DabChannel("8D", 201_072),
            new DabChannel("9A", 202_928),
            new DabChannel("9B", 204_640),
            new DabChannel("9C", 206_352),
            new DabChannel("9D", 208_064),
            new DabChannel("10A", 209_936),
            new DabChannel("10B", 211_648),
            new DabChannel("10C", 213_360),
            new DabChannel("10D", 215_072),
            new DabChannel("11A", 216_928),
            new DabChannel("11B", 218_640),
            new DabChannel("11C", 220_352),
            new DabChannel("11D", 222_064),
            new DabChannel("12A", 223_936),
            new DabChannel("12B", 225_648),
            new DabChannel("12C", 227_360),
            new DabChannel("12D", 229_072),
            new DabChannel("13A", 230_784),
            new DabChannel("13B", 232_496),
            new DabChannel("13C", 234_208),
            new DabChannel("13D", 235_776),
            new DabChannel("13E", 237_488),
            new DabChannel("13F", 239_200)
    };

    public static DabChannel byIndex(int index) {
        int clamped = Math.max(0, Math.min(CHANNELS.length - 1, index));
        return CHANNELS[clamped];
    }

    public static int clampIndex(int index) {
        return Math.max(0, Math.min(CHANNELS.length - 1, index));
    }

    public static int size() {
        return CHANNELS.length;
    }

    public static int nearestIndexForFrequencyKhz(int frequencyKhz) {
        int bestIndex = 0;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < CHANNELS.length; i++) {
            int delta = Math.abs(CHANNELS[i].frequencyKhz() - frequencyKhz);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
