package com.radiomod.signal;

public record RdsFrameData(String groupCode, String pi, String ps, boolean tp, int pty, String rt) {
}
