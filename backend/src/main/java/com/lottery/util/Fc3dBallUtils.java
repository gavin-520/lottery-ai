package com.lottery.util;

public final class Fc3dBallUtils {

    private Fc3dBallUtils() {}

    public static int sum(int d1, int d2, int d3) {
        return d1 + d2 + d3;
    }

    public static int span(int d1, int d2, int d3) {
        int max = Math.max(d1, Math.max(d2, d3));
        int min = Math.min(d1, Math.min(d2, d3));
        return max - min;
    }

    public static String oddEvenPattern(int d1, int d2, int d3) {
        return oe(d1) + oe(d2) + oe(d3);
    }

    private static String oe(int digit) {
        return digit % 2 == 0 ? "E" : "O";
    }
}
