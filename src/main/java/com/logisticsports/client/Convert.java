package com.logisticsports.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Convert {
    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        FORMAT = new DecimalFormat("#.##", symbols);
    }

    public static String ShowAmountString(int amount, boolean convert, boolean fluid) {
        if (amount >= 1000 && convert) {
            double value = amount / 1000.0;
            String formatted = FORMAT.format(value);
            return fluid ? (formatted + " B") : ("x" + formatted + "K");
        }

        return (fluid ? "" : "x") + amount + (fluid ? " mB" : "");
    }

    public static String ShowAmountString(int amount, boolean convert) {
        return ShowAmountString(amount, convert, false);
    }
}
