package edu.testing.quickcart.service;

import edu.testing.quickcart.model.*;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 7 - Property-based testing with jqwik.
 *
 * Instead of hand-picking a few carts, jqwik generates 1000 random valid orders
 * (1-5 items, prices $0-$200, quantities 1-25, any tier, any province,
 * with or without a coupon, any date in November 2026) and checks that a rule
 * from the spec holds for every one of them.
 */
class PricingPropertyTest {

    private final PricingService pricing = new PricingService();


    /* Property 1: the receipt always adds up.
       Spec: total = merchandise + shipping + tax, and merchandise is never negative.
       This must hold for ANY valid order, so it runs through every pricing rule at once
       (bulk, GOLD, coupon + clamp, shipping threshold, tax rounding). */

    @Property(tries = 1000)
    @Label("For any valid order: total = merchandise + shipping + tax, and no part is negative")
    void receiptAlwaysAddsUp(@ForAll("carts") List<CartItem> cart,
                             @ForAll CustomerTier tier,
                             @ForAll("coupons") Coupon coupon,
                             @ForAll("provinces") String province,
                             @ForAll("novemberDates") LocalDate orderDate) {

        OrderQuote q = pricing.quote(cart, tier, coupon, province, orderDate);

        BigDecimal sum = q.merchandise().add(q.shipping()).add(q.tax());
        assertEquals(0, q.total().compareTo(sum),
                "total " + q.total() + " != merchandise + shipping + tax = " + sum);
        assertTrue(q.merchandise().signum() >= 0, "merchandise negative: " + q.merchandise());
        assertTrue(q.shipping().signum() >= 0, "shipping negative: " + q.shipping());
        assertTrue(q.tax().signum() >= 0, "tax negative: " + q.tax());
    }


    /* Property 2 (metamorphic): GOLD never pays more for merchandise than STANDARD.
       Run the SAME random cart twice, once per tier, and compare the two results.
       No coupon here: with a coupon, GOLD's 10% can drop merchandise below the
       $50.00 coupon minimum, so GOLD can legitimately end up paying more. */

    @Property(tries = 1000)
    @Label("For any cart without a coupon: GOLD merchandise <= STANDARD merchandise")
    void goldNeverPaysMoreThanStandard(@ForAll("carts") List<CartItem> cart,
                                       @ForAll("provinces") String province,
                                       @ForAll("novemberDates") LocalDate orderDate) {

        OrderQuote standard = pricing.quote(cart, CustomerTier.STANDARD, null, province, orderDate);
        OrderQuote gold     = pricing.quote(cart, CustomerTier.GOLD,     null, province, orderDate);

        assertTrue(gold.merchandise().compareTo(standard.merchandise()) <= 0,
                "GOLD " + gold.merchandise() + " > STANDARD " + standard.merchandise());
    }

    // ===================== Generators (input space) =====================

    @Provide
    Arbitrary<List<CartItem>> carts() {
        Arbitrary<BigDecimal> prices = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("200.00"))
                .ofScale(2);
        Arbitrary<Integer> quantities = Arbitraries.integers().between(1, 25); // crosses the bulk boundary at 10

        Arbitrary<CartItem> items = Combinators.combine(prices, quantities)
                .as((price, qty) -> new CartItem(new Product("P1", "Item", price), qty));

        return items.list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Coupon> coupons() {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("150.00"))
                .ofScale(2);
        return amounts
                .map(amt -> new Coupon("SAVE", amt, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20)))
                .injectNull(0.3); // about 30% of orders have no coupon
    }

    @Provide
    Arbitrary<String> provinces() {
        return Arbitraries.of("ON", "BC", "AB");
    }

    @Provide
    Arbitrary<LocalDate> novemberDates() {
        return Arbitraries.integers().between(1, 30).map(day -> LocalDate.of(2026, 11, day));
    }
}