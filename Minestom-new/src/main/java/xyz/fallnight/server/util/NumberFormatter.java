package xyz.fallnight.server.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberFormatter {
    private static final String[] SUFFIXES = {
        "", "K", "M", "B", "T", "Qa", "Qt", "Sx", "Sp", "Oc", "No", "De"
    };

    private NumberFormatter() {
    }

    public static String shortNumber(long number) {
        return shortNumberInternal(number, false);
    }

    public static String shortNumberRounded(double number) {
        return shortNumberInternal(number, true);
    }

    public static String currency(double amount) {
        return "$" + shortNumberRounded(amount);
    }

    private static String shortNumberInternal(double number, boolean rounded) {
        double abs = Math.abs(number);
        int suffixIndex = 0;
        while (abs >= 1000d && suffixIndex < SUFFIXES.length - 1) {
            abs /= 1000d;
            suffixIndex++;
        }

        String sign = number < 0 ? "-" : "";
        if (suffixIndex == 0) {
            return sign + (rounded ? trim(toTwoDecimals(abs)) : Long.toString((long) abs));
        }
        if (!rounded) {
            return sign + Double.toString(abs) + SUFFIXES[suffixIndex];
        }
        return sign + trim(truncateTwoDecimals(abs)) + SUFFIXES[suffixIndex];
    }

    private static String toTwoDecimals(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    private static String truncateTwoDecimals(double value) {
        double truncated = ((int) (value * 100d)) / 100d;
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(truncated);
    }

    private static String trim(String value) {
        if (!value.contains(".")) {
            return value;
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(0, end);
    }
}
