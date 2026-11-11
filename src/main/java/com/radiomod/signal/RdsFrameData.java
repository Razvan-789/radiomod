package com.radiomod.signal;

public record RdsFrameData(String groupCode, String pi, String ps, boolean tp, int pty, String rt,
                           String title, String artist) {
    public RdsFrameData(String groupCode, String pi, String ps, boolean tp, int pty, String rt) {
        this(groupCode, pi, ps, tp, pty, rt, "", "");
    }
}
