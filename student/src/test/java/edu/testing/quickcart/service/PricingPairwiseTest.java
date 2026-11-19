package edu.testing.quickcart.service;

import edu.testing.quickcart.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pair-wise combinatorial testing of PricingService.quote.
 *
 * Characteristics (2 x 4 x 3 x 2 = 48 combinations):
 *   tier         STANDARD | GOLD
 *   coupon state NONE | OUTSIDE_DATES | UNDER_MINIMUM (active, merchandise < $50) | APPLIED
 *   province     ON | BC | AB
 *   shipping     PAID (< $100) | FREE (>= $100)
 * Constraint: UNDER_MINIMUM cannot occur with FREE shipping (merchandise < 50 is never >= 100),
 * so those 6 combinations are infeasible, not missing coverage.
 * These 12 rows cover every feasible pair of values.
 *
 * Concrete inputs: one item; price 80.00 (PAID), 150.00 (FREE) or 40.00 (UNDER_MINIMUM);
 * $10 coupon valid 2026-11-10..2026-11-20; order date 2026-11-15, or 2026-11-25 for OUTSIDE_DATES.
 * Expected values computed by hand from the spec (Pricing 1-8).
 *
 * EXPECTED TO FAIL: every PAID row, because the code leaves shipping out of the tax base (R6).
 * The FREE rows pass, which shows the rest of the pricing logic agrees with the spec.
 */
class PricingPairwiseTest {

    private static final Coupon SAVE10 = new Coupon("SAVE10", new BigDecimal("10.00"),
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20));

    @ParameterizedTest(name = "{index}: {0}, {1}, {2}, {3} -> total {10}")
    @Tag("defect")
    @CsvSource({
            // tier,   coupon,        prov, ship, price,  coupon?, date,       merch,  ship, tax,   total
            "STANDARD, NONE,          ON,   PAID, 80.00,  false,   2026-11-15, 80.00,  8.99, 11.57, 100.56",
            "STANDARD, OUTSIDE_DATES, BC,   FREE, 150.00, true,    2026-11-25, 150.00, 0.00, 18.00, 168.00",
            "GOLD,     NONE,          AB,   FREE, 150.00, false,   2026-11-15, 135.00, 0.00, 6.75,  141.75",
            "GOLD,     UNDER_MINIMUM, BC,   PAID, 40.00,  true,    2026-11-15, 36.00,  8.99, 5.40,  50.39",
            "STANDARD, APPLIED,       AB,   PAID, 80.00,  true,    2026-11-15, 70.00,  8.99, 3.95,  82.94",
            "GOLD,     APPLIED,       ON,   FREE, 150.00, true,    2026-11-15, 125.00, 0.00, 16.25, 141.25",
            "GOLD,     OUTSIDE_DATES, ON,   PAID, 80.00,  true,    2026-11-25, 72.00,  8.99, 10.53, 91.52",
            "STANDARD, UNDER_MINIMUM, ON,   PAID, 40.00,  true,    2026-11-15, 40.00,  8.99, 6.37,  55.36",
            "STANDARD, NONE,          BC,   PAID, 80.00,  false,   2026-11-15, 80.00,  8.99, 10.68, 99.67",
            "STANDARD, OUTSIDE_DATES, AB,   PAID, 80.00,  true,    2026-11-25, 80.00,  8.99, 4.45,  93.44",
            "STANDARD, UNDER_MINIMUM, AB,   PAID, 40.00,  true,    2026-11-15, 40.00,  8.99, 2.45,  51.44",
            "STANDARD, APPLIED,       BC,   PAID, 80.00,  true,    2026-11-15, 70.00,  8.99, 9.48,  88.47"
    })
    @DisplayName("Pair-wise: tier x coupon state x province x shipping band")
    void pairwiseQuote(CustomerTier tier, String couponState, String province, String shippingBand,
                       String price, boolean useCoupon, LocalDate orderDate,
                       String merchandise, String shipping, String tax, String total) {
        List<CartItem> items = List.of(new CartItem(new Product("P1", "Item", new BigDecimal(price)), 1));
        OrderQuote q = new PricingService().quote(items, tier, useCoupon ? SAVE10 : null, province, orderDate);

        assertAll(
                () -> assertEquals(0, new BigDecimal(merchandise).compareTo(q.merchandise()), "merchandise was " + q.merchandise()),
                () -> assertEquals(0, new BigDecimal(shipping).compareTo(q.shipping()), "shipping was " + q.shipping()),
                () -> assertEquals(0, new BigDecimal(tax).compareTo(q.tax()), "tax was " + q.tax()),
                () -> assertEquals(0, new BigDecimal(total).compareTo(q.total()), "total was " + q.total()));
    }
}
