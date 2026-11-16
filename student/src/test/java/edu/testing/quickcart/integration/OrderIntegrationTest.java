package edu.testing.quickcart.integration;

import edu.testing.quickcart.infra.InMemoryInventory;
import edu.testing.quickcart.model.*;
import edu.testing.quickcart.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part C (2): INTEGRATION tests with multiple real components:
 *   real OrderService + real PricingService + real InMemoryInventory.
 * Only the external PaymentGateway is substituted, by a small hand-written recording stub (no Mockito),
 * because it has no real implementation.
 *
 * Why integration and not just unit tests: the faults below only appear when real state passes between
 * components - the quote produced by PricingService must be the amount charged, and the stock changes made
 * by the real inventory must be consistent with the order outcome. A mocked inventory would hide R10 and R11.
 *
 * Tests marked "EXPECTED TO FAIL" are defect evidence (R10, R11). Do not change their expected values.
 */
class OrderIntegrationTest {

    /** Hand-written stub: answers approve/decline and records every charge attempt. */
    static class RecordingGateway implements PaymentGateway {
        private final boolean approve;
        final List<String> customers = new ArrayList<>();
        final List<BigDecimal> amounts = new ArrayList<>();

        RecordingGateway(boolean approve) { this.approve = approve; }

        @Override
        public boolean authorize(String customerId, BigDecimal amount) {
            customers.add(customerId);
            amounts.add(amount);
            return approve;
        }
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Product MUG = new Product("P1", "Mug", new BigDecimal("30.00"));
    private static final Product BOWL = new Product("P2", "Bowl", new BigDecimal("25.00"));

    private InMemoryInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory(new HashMap<>(Map.of("P1", 5, "P2", 5)));
    }

    private OrderService serviceWith(PaymentGateway gateway) {
        return new OrderService(new PricingService(), inventory, gateway);
    }

    // Cart: 2 mugs (60.00) + 2 bowls (50.00) = 110.00 -> free shipping; ON 13% tax 14.30 -> total 124.30
    private static List<CartItem> twoProductCart() {
        return List.of(new CartItem(MUG, 2), new CartItem(BOWL, 2));
    }

    @Test
    @DisplayName("Happy path: priced quote is what gets charged, and every product's stock is reduced")
    void acceptedOrderFlowsThroughAllComponents() {
        RecordingGateway gateway = new RecordingGateway(true);

        OrderResult result = serviceWith(gateway)
                .placeOrder("C1", twoProductCart(), CustomerTier.STANDARD, null, "ON", TODAY);

        assertAll(
                () -> assertEquals(OrderResult.Status.ACCEPTED, result.status()),
                // PricingService -> OrderResult: the quote is computed by the real pricing rules
                () -> assertEquals(0, new BigDecimal("124.30").compareTo(result.quote().total()),
                        "quote total: expected 124.30 but was " + result.quote().total()),
                // OrderService -> PaymentGateway: charged once, for that customer, for exactly that quote
                () -> assertEquals(List.of("C1"), gateway.customers, "one charge attempt for C1"),
                () -> assertEquals(0, result.quote().total().compareTo(gateway.amounts.get(0)),
                        "amount charged must equal the quoted total"),
                // OrderService -> InventoryService: real stock reduced for each line
                () -> assertEquals(3, inventory.available("P1"), "mug stock"),
                () -> assertEquals(3, inventory.available("P2"), "bowl stock"));
    }

    // EXPECTED TO FAIL (R10): stock is reserved before payment, so after a decline the REAL inventory
    // is left reduced. Spec, Ordering 4-5.
    @Test
    @DisplayName("Declined payment leaves the real inventory unchanged (R10)")
    void declinedPaymentDoesNotChangeRealInventory() {
        RecordingGateway gateway = new RecordingGateway(false);

        OrderResult result = serviceWith(gateway)
                .placeOrder("C1", twoProductCart(), CustomerTier.STANDARD, null, "ON", TODAY);

        assertAll(
                () -> assertEquals(OrderResult.Status.PAYMENT_DECLINED, result.status()),
                () -> assertEquals(1, gateway.amounts.size(), "payment attempted once"),
                () -> assertEquals(5, inventory.available("P1"),
                        "mug stock after decline: expected 5 but was " + inventory.available("P1")),
                () -> assertEquals(5, inventory.available("P2"),
                        "bowl stock after decline: expected 5 but was " + inventory.available("P2")));
    }

    // EXPECTED TO FAIL (R11, not marked as a seeded defect in the code):
    // Two lines of the same product, 6 + 6 = 12 against stock 10. Each line passes the stock check on its own;
    // the first reserve() takes 6, the second throws. Spec, Ordering 1-2 (assumption A3: "sufficient inventory"
    // means the total quantity per product): the order is out of stock, payment is not attempted,
    // inventory must not change.
    @Test
    @DisplayName("Same product on two lines exceeding stock -> OUT_OF_STOCK, stock unchanged (R11)")
    void sameProductOnTwoLinesIsCheckedAsATotal() {
        inventory = new InMemoryInventory(new HashMap<>(Map.of("P1", 10)));
        RecordingGateway gateway = new RecordingGateway(true);
        Product cable = new Product("P1", "Cable", new BigDecimal("10.00"));
        List<CartItem> items = List.of(new CartItem(cable, 6), new CartItem(cable, 6));

        // Capture the outcome instead of letting an exception end the test early,
        // so the report shows every broken fact (exception, status, stock, payment).
        OrderResult result = null;
        RuntimeException thrown = null;
        try {
            result = serviceWith(gateway).placeOrder("C1", items, CustomerTier.STANDARD, null, "ON", TODAY);
        } catch (RuntimeException e) {
            thrown = e;
        }
        final OrderResult r = result;
        final RuntimeException ex = thrown;

        assertAll(
                () -> assertNull(ex, () -> "no exception should escape placeOrder, but got: " + ex),
                () -> assertEquals(OrderResult.Status.OUT_OF_STOCK, r == null ? null : r.status(), "status"),
                () -> assertEquals(10, inventory.available("P1"),
                        "stock: expected 10 but was " + inventory.available("P1")),
                () -> assertTrue(gateway.amounts.isEmpty(), "payment must not be attempted"));
    }
}
