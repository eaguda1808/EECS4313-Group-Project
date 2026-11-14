package edu.testing.quickcart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CartItem constructor. Spec, Input constraints: "Item quantity must be positive."
 * Characteristic: quantity, blocks <= 0 (invalid) | >= 1 (valid); boundary 0 / 1.
 * Weak robust: exactly one invalid input per test.
 */
class CartItemTest {

    private static final Product CABLE = new Product("P1", "Cable", new BigDecimal("10.00"));

    @ParameterizedTest(name = "quantity {0} is rejected")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("Non-positive quantity is rejected (R13)")
    void nonPositiveQuantityIsRejected(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> new CartItem(CABLE, quantity));
    }

    @ParameterizedTest(name = "quantity {0} is accepted")
    @ValueSource(ints = {1, 2, 10})
    @DisplayName("Positive quantity is accepted, starting at the boundary value 1")
    void positiveQuantityIsAccepted(int quantity) {
        CartItem item = new CartItem(CABLE, quantity);
        assertEquals(quantity, item.quantity());
        assertEquals(CABLE, item.product());
    }

    @Test
    @DisplayName("Null product is rejected")
    void nullProductIsRejected() {
        assertThrows(NullPointerException.class, () -> new CartItem(null, 1));
    }
}