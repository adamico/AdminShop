package com.ammonium.adminshop.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ammonium.adminshop.money.MoneyFormat.FormatType;
import com.ammonium.adminshop.money.MoneyFormat.NumberName;
import org.junit.jupiter.api.Test;

/**
 * Tests how a Balance or price of a million or more is formatted. The shipped language file stores money in cents
 * (decimal offset 2). Below a million the full amount shows, and the currency symbol comes from the language file:
 * both need the client's language, so these tests start from the amount after the decimal offset.
 */
class MoneyFormatTest {

    private static String cents(long value, FormatType type) {
        double amount = value / 100.0;
        return MoneyFormat.doFormat(amount, NumberName.findName(amount), type, 2);
    }

    private static String whole(long value, FormatType type) {
        double amount = value;
        return MoneyFormat.doFormat(amount, NumberName.findName(amount), type, 0);
    }

    @Test
    void amountsBelowAMillionHaveNoNumberName() {
        assertNull(NumberName.findName(999_999.99));
        assertNull(NumberName.findName(-999_999.99));
        assertEquals(NumberName.MILLION, NumberName.findName(1_000_000.0));
        assertEquals(NumberName.BILLION, NumberName.findName(-1_500_000_000.0));
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
        assertEquals("-12.34M", cents(-1234567890, FormatType.SHORT));
        assertEquals("-1.50 Billion", whole(-1500000000, FormatType.FULL));
    }
}
