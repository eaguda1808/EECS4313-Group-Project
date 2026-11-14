package edu.testing.quickcart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coupon constructor. These checks are not in the spec, but they are the constructor's
 * own contract, so we test both sides of each boundary.
 * Characteristics: amountOff (< 0 | = 0 | > 0), endDate vs startDate (before | same day | after).
 * Weak robust: one invalid field per test.
 */
class CouponTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    private static final LocalDate END = LocalDate.of(2026, 11, 20);

    // ----- amountOff: boundary -0.01 / 0 / 0.01 -----

    @Test
    @DisplayName("Amount off just below zero (-0.01) is rejected")
    void negativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Coupon("SAVE", new BigDecimal("-0.01"), START, END));
    }

    @ParameterizedTest(name = "amountOff {0} is accepted")
    @ValueSource(strings = {"0", "0.01", "10.00"})
    @DisplayName("Zero and positive amounts are accepted")
    void nonNegativeAmountIsAccepted(String amount) {
        Coupon c = new Coupon("SAVE", new BigDecimal(amount), START, END);
        assertEquals(0, new BigDecimal(amount).compareTo(c.amountOff()));
    }

    // ----- endDate vs startDate: boundary start-1 / start / start+1 -----

    @Test
    @DisplayName("End date one day before start is rejected")
    void endBeforeStartIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Coupon("SAVE", BigDecimal.TEN, START, START.minusDays(1)));
    }

    @Test
    @DisplayName("End date equal to start (single-day coupon) is accepted")
    void endEqualToStartIsAccepted() {
        Coupon c = new Coupon("SAVE", BigDecimal.TEN, START, START);
        assertEquals(c.startDate(), c.endDate());
    }

    @Test
    @DisplayName("End date one day after start is accepted")
    void endAfterStartIsAccepted() {
        Coupon c = new Coupon("SAVE", BigDecimal.TEN, START, START.plusDays(1));
        assertEquals(START.plusDays(1), c.endDate());
    }

    // ----- code -----

    @ParameterizedTest(name = "code ''{0}'' is rejected")
    @ValueSource(strings = {"", " "})
    @DisplayName("Blank code is rejected")
    void blankCodeIsRejected(String code) {
        assertThrows(IllegalArgumentException.class, () -> new Coupon(code, BigDecimal.TEN, START, END));
    }

    @Test
    @DisplayName("Null fields are rejected")
    void nullFieldsAreRejected() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Coupon(null, BigDecimal.TEN, START, END)),
                () -> assertThrows(NullPointerException.class, () -> new Coupon("SAVE", null, START, END)),
                () -> assertThrows(NullPointerException.class, () -> new Coupon("SAVE", BigDecimal.TEN, null, END)),
                () -> assertThrows(NullPointerException.class, () -> new Coupon("SAVE", BigDecimal.TEN, START, null))
        );
    }
}
