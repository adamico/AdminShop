package com.ammonium.adminshop.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ammonium.adminshop.money.MoneyFormat.FormatType;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * A Balance or price shows as on 1.21.1. The shipped language file stores money in cents (decimal
 * offset 2); the currency symbol comes from the language file and is not part of these strings.
 */
class MoneyFormatTest {

    private static String cents(long value, FormatType type) {
        return MoneyFormat.formatAmount(value, type, 2, false, Locale.US);
    }

    private static String whole(long value, FormatType type) {
        return MoneyFormat.formatAmount(value, type, 0, false, Locale.US);
    }

    @Test
    void amountsBelowAMillionShowInFullWhateverTheType() {
        assertEquals("0.00", cents(0, FormatType.SHORT));
        assertEquals("123.45", cents(12345, FormatType.SHORT));
        assertEquals("999,999.99", cents(99999999, FormatType.SHORT));
        assertEquals("999,999.99", cents(99999999, FormatType.FULL));
        assertEquals("1,234", whole(1234, FormatType.SHORT));
    }

    @Test
    void shortNamesAMillionOrMoreWithTwoDigitsAfterThePoint() {
        assertEquals("1.00M", cents(100000000, FormatType.SHORT));
        assertEquals("12.34M", cents(1234567890, FormatType.SHORT));
        assertEquals("1.50B", whole(1500000000, FormatType.SHORT));
        assertEquals("12.34T", whole(12345678901234L, FormatType.SHORT));
        assertEquals("9.22Qi", whole(Long.MAX_VALUE, FormatType.SHORT));
    }

    @Test
    void fullSpellsTheNumberNameOut() {
        assertEquals("12.34 Million", cents(1234567890, FormatType.FULL));
        assertEquals("123.45 Billion", whole(123456789012L, FormatType.FULL));
    }

    @Test
    void rawShowsEveryDigit() {
        assertEquals("12,345,678.90", cents(1234567890, FormatType.RAW));
        assertEquals("1,500,000,000", whole(1500000000, FormatType.RAW));
    }

    @Test
    void negativeAmountsKeepTheirSign() {
        assertEquals("-123.45", cents(-12345, FormatType.SHORT));
        assertEquals("-12.34M", cents(-1234567890, FormatType.SHORT));
    }

    @Test
    void ignoringTheDecimalOffsetShowsTheAmountUndivided() {
        assertEquals("12,345.00", MoneyFormat.formatAmount(12345, FormatType.SHORT, 2, true, Locale.US));
        assertEquals("12.34M", MoneyFormat.formatAmount(12345678, FormatType.SHORT, 2, true, Locale.US));
    }

    @Test
    void separatorsFollowTheLocale() {
        assertEquals("1.234,56", MoneyFormat.formatAmount(123456, FormatType.SHORT, 2, false, Locale.GERMANY));
    }
}
