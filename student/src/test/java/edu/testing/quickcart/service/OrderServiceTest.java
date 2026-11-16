package edu.testing.quickcart.service;

import edu.testing.quickcart.infra.InMemoryInventory;
import edu.testing.quickcart.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Part C (1): unit tests for OrderService.placeOrder using a TEST DOUBLE.
 *
 * What is substituted and why (Lecture 7, slide 15):
 *  - PaymentGateway -> Mockito MOCK. It is an external system with no real implementation in the project,
 *    and the spec states rules about the CALL itself, which a return value cannot show:
 *      Ordering 2: "If any item is out of stock, payment must not be attempted"
 *      Ordering 3: "payment authorization is attempted for the quoted total"
 *    So the interaction (called / not called, with which amount) is part of the oracle.
 *  - InventoryService -> InMemoryInventory, the project's own FAKE. Its stock level can be read afterwards,
 *    a stronger oracle than verifying reserve() calls (state verification first, Lecture 7, slide 8).
 *  - PricingService -> REAL. Cheap and deterministic; a stub would only add an unrealistic world.
 *
 * Structure: a decision table over "stock sufficient?" x "payment approved?" (Lecture 4, slides 11-13; Lecture 5, slide 10),
 * plus the exact-stock boundary.
 *
 * Inputs use free shipping (merchandise >= $100, AB 5%) so the quote total is unaffected by the pricing
 * defects already covered in Part B (R1, R5, R6). This keeps each failure here pointing at an ordering defect.
 *
 * Tests marked "EXPECTED TO FAIL" are defect evidence (R9, R10). Do not change their expected values.
 */
class OrderServiceTest {

    private static final String CUSTOMER = "C1";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Product WIDGET = new Product("P1", "Widget", new BigDecimal("60.00"));
    private static final int STOCK = 5;

    private InMemoryInventory inventory;
    private PaymentGateway payment;
    private OrderService orders;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory(new HashMap<>(Map.of("P1", STOCK)));
        payment = mock(PaymentGateway.class);           // test double for the external gateway
        orders = new OrderService(new PricingService(), inventory, payment);
    }

    private OrderResult order(int quantity) {
        return orders.placeOrder(CUSTOMER, List.of(new CartItem(WIDGET, quantity)),
                CustomerTier.STANDARD, null, "AB", TODAY);
    }

    /** Matches a BigDecimal by value, so 126.0 and 126.00 are equal (BigDecimal.equals checks scale). */
    private static BigDecimal amountOf(String expected) {
        return argThat(actual -> actual != null && actual.compareTo(new BigDecimal(expected)) == 0);
    }

    // ---------- Decision table ----------
    // Rule | stock sufficient? | payment approved? | status            | payment attempted? | stock after
    // D1   | N                 | -                 | OUT_OF_STOCK      | never              | unchanged
    // D2   | Y                 | Y                 | ACCEPTED          | once, quoted total | reduced
    // D3   | Y                 | N                 | PAYMENT_DECLINED  | once, quoted total | unchanged

    @Test
    @DisplayName("D1: out of stock -> OUT_OF_STOCK, payment never attempted, stock unchanged (Ordering 2)")
    void outOfStockNeverAttemptsPayment() {
        OrderResult result = order(STOCK + 1);   // 6 requested, 5 available

        assertEquals(OrderResult.Status.OUT_OF_STOCK, result.status());
        verify(payment, never()).authorize(anyString(), any());   // interaction oracle: no charge attempt
        assertEquals(STOCK, inventory.available("P1"), "stock must not change");
    }

    @Test
    @DisplayName("D2: stock and payment OK -> ACCEPTED, charged once for exactly the quoted total, stock reduced")
    void approvedPaymentChargesQuotedTotalOnce() {
        when(payment.authorize(anyString(), any())).thenReturn(true);

        OrderResult result = order(2);   // 2 x 60.00 = 120.00, free shipping, AB 5% tax 6.00 -> total 126.00

        assertAll(
                () -> assertEquals(OrderResult.Status.ACCEPTED, result.status()),
                () -> assertEquals(0, new BigDecimal("126.00").compareTo(result.quote().total()),
                        "quoted total: expected 126.00 but was " + result.quote().total()),
                () -> assertEquals(STOCK - 2, inventory.available("P1"), "stock reduced by the quantity ordered"));
        // Ordering 3: attempted once, for this customer, for the quoted total (exact value, not any())
        verify(payment, times(1)).authorize(eq(CUSTOMER), amountOf("126.00"));
    }

    // EXPECTED TO FAIL (R10): code reserves stock BEFORE asking for payment, so a declined payment
    // still uses up stock. Spec, Ordering 4-5: reserve "only after payment authorization succeeds";
    // "If payment is declined, inventory must remain unchanged."
    @Test
    @DisplayName("D3: payment declined -> PAYMENT_DECLINED and stock unchanged (R10)")
    void declinedPaymentLeavesStockUnchanged() {
        when(payment.authorize(anyString(), any())).thenReturn(false);

        OrderResult result = order(2);

        assertAll(
                () -> assertEquals(OrderResult.Status.PAYMENT_DECLINED, result.status()),
                () -> assertEquals(STOCK, inventory.available("P1"),
                        "stock after declined payment: expected " + STOCK + " but was " + inventory.available("P1")));
        verify(payment, times(1)).authorize(eq(CUSTOMER), amountOf("126.00"));
    }

    // ---------- Boundary on the stock check ----------

    // EXPECTED TO FAIL (R9): spec, Ordering 1: "An order requesting exactly the available quantity is valid."
    // Code uses available <= quantity, so it rejects the exact amount.
    @Test
    @DisplayName("Boundary: ordering exactly the available stock is accepted (R9)")
    void orderingExactlyAvailableStockIsAccepted() {
        when(payment.authorize(anyString(), any())).thenReturn(true);

        OrderResult result = order(STOCK);   // 5 x 60.00 = 300.00, AB 5% tax 15.00 -> total 315.00

        assertAll(
                () -> assertEquals(OrderResult.Status.ACCEPTED, result.status()),
                () -> assertEquals(0, inventory.available("P1"), "all stock reserved"));
        verify(payment, times(1)).authorize(eq(CUSTOMER), amountOf("315.00"));
    }

    @Test
    @DisplayName("Boundary: one less than available stock is accepted")
    void orderingOneLessThanStockIsAccepted() {
        when(payment.authorize(anyString(), any())).thenReturn(true);

        OrderResult result = order(STOCK - 1);   // 4 x 60.00 = 240.00, AB 5% tax 12.00 -> total 252.00

        assertEquals(OrderResult.Status.ACCEPTED, result.status());
        assertEquals(1, inventory.available("P1"));
        verify(payment, times(1)).authorize(eq(CUSTOMER), amountOf("252.00"));
    }

    @Test
    @DisplayName("Unknown product (0 in stock) -> OUT_OF_STOCK, payment never attempted")
    void unknownProductIsOutOfStock() {
        Product ghost = new Product("NOPE", "Ghost", new BigDecimal("150.00"));

        OrderResult result = orders.placeOrder(CUSTOMER, List.of(new CartItem(ghost, 1)),
                CustomerTier.STANDARD, null, "AB", TODAY);

        assertEquals(OrderResult.Status.OUT_OF_STOCK, result.status());
        verifyNoInteractions(payment);
    }
}
