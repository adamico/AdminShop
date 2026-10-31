package com.ammonium.adminshop.money;

import com.ammonium.adminshop.setup.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;

/**
 * Formats money (a Balance or an Offer's price) for display on the client. Amounts are whole numbers
 * in the smallest unit. The language key {@code gui.money_decimal_offset} sets how many of their digits
 * are decimals, and {@code gui.money_format} adds the currency.
 */
public final class MoneyFormat {
    public static final double FORMAT_START = 1000000;

    public enum FormatType {
        FULL,
        SHORT,
        RAW
    }

    private MoneyFormat() {
    }

    /** Formats in the configured style, or as raw digits while Shift is held. */
    public static String cfgformat(long value) {
        boolean displayFormat = (Config.displayFormat.get() != null) ? Config.displayFormat.get() : true;
        FormatType formattype = displayFormat ? FormatType.SHORT : FormatType.FULL;
        return format(value, formattype, FormatType.RAW);
    }

    /** Formats in {@code forced} whether or not Shift is held. */
    public static String forcedFormat(long value, FormatType forced) {
        return format(value, forced, forced);
    }

    /** Formats in {@code noShift}, or as raw digits while Shift is held. */
    public static String format(long value, FormatType noShift) {
        return format(value, noShift, FormatType.RAW);
    }

    public static String format(long value, FormatType noShift, FormatType onShift) {
        int decimalOffset = Integer.parseInt(I18n.get("gui.money_decimal_offset"));
        FormatType type = Minecraft.getInstance().hasShiftDown() ? onShift : noShift;
        String amount = formatAmount(value, type, decimalOffset, Config.ignoreDecimalOffset.get(),
                Locale.getDefault(Locale.Category.FORMAT));
        return I18n.get("gui.money_format", amount);
    }

    /**
     * Formats the number part of an amount, without the currency. Amounts below a million (after the
     * decimal offset) always show in full. Larger ones use {@code type}. A new NumberFormat is made on
     * each call because the HUD and the screens format from different threads.
     *
     * @param decimalOffset       how many of the amount's digits are decimals
     * @param ignoreDecimalOffset whether to show the amount undivided. It still shows the offset's number
     *                            of decimals.
     */
    public static String formatAmount(long value, FormatType type, int decimalOffset, boolean ignoreDecimalOffset,
                                      Locale locale) {
        double realValue = value;
        if (decimalOffset > 0 && !ignoreDecimalOffset) {
            realValue /= Math.pow(10, decimalOffset);

            double scale = Math.pow(10, decimalOffset);
            realValue = Math.round(realValue * scale) / scale;
        }

        NumberFormat decimalFormat = NumberFormat.getNumberInstance(locale);
        decimalFormat.setMinimumFractionDigits(decimalOffset);
        decimalFormat.setMaximumFractionDigits(decimalOffset);

        NumberName name = NumberName.findName(realValue);
        if (name == null || Math.abs(realValue) < FORMAT_START) {
            return decimalFormat.format(realValue);
        }
        return switch (type) {
            case SHORT -> getShort(realValue, decimalFormat, decimalOffset) + name.getName(true);
            case FULL -> getShort(realValue, decimalFormat, decimalOffset) + " " + name.getName(false);
            case RAW -> decimalFormat.format(realValue);
        };
    }

    /** The leading thousands group and the next two digits after a point, such as 1.23 for 1234567. */
    private static String getShort(long value) {
        boolean isNegative = value < 0;
        String str = String.valueOf(Math.abs(value));
        int len = str.length();

        if (len < 4) {
            return isNegative ? "-" + str : str;
        }

        String sig = str.substring(0, len % 3 == 0 ? 3 : len % 3);
        String dec = str.substring(sig.length(), Math.min(sig.length() + 2, len));

        String result = String.format("%s.%s", sig, dec);
        return isNegative ? "-" + result : result;
    }

    private static String getShort(double value, NumberFormat decimalFormat, int decimalOffset) {
        boolean isNegative = value < 0;
        String str = decimalFormat.format(Math.abs(value));
        int len = str.length();

        if (len <= Math.max(5, 2 + decimalOffset)) {
            return isNegative ? "-" + str : str;
        }

        return getShort((long) value);
    }

    public enum NumberName {
        MILLION(1e6, "M"),
        BILLION(1e9, "B"),
        TRILLION(1e12, "T"),
        QUADRILLION(1e15, "Qa"),
        QUINTILLION(1e18, "Qi"),
        SEXTILLION(1e21, "Sx"),
        SEPTILLION(1e24, "Sp"),
        OCTILLION(1e27, "O"),
        NONILLION(1e30, "N"),
        DECILLION(1e33, "D"),
        UNDECILLION(1e36, "U"),
        DUODECILLION(1e39, "Du"),
        TREDECILLION(1e42, "Tr"),
        QUATTUORDECILLION(1e45, "Qt"),
        QUINDECILLION(1e48, "Qd"),
        SEXDECILLION(1e51, "Sd"),
        SEPTENDECILLION(1e54, "St"),
        OCTODECILLION(1e57, "Oc"),
        NOVEMDECILLION(1e60, "No");

        public static final NumberName[] VALUES = values();

        private final double value;
        private final String shortName;

        NumberName(double val, String shortName) {
            this.value = val;
            this.shortName = shortName;
        }

        public String getName() {
            return getName(false);
        }

        public String getName(boolean getShort) {
            return getShort ? shortName : (name().charAt(0) + name().toLowerCase(Locale.US).substring(1)).trim();
        }

        public double getValue() {
            return value;
        }

        static @Nullable NumberName findName(double value) {
            return Arrays.stream(VALUES).filter(v -> Math.abs(value) >= v.getValue()).reduce((first, second) -> second).orElse(null);
        }
    }
}
