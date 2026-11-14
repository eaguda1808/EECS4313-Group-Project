package edu.testing.quickcart.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryInventory.reserve. Characteristic: quantity vs current stock,
 * blocks <= 0 (invalid) | 1..stock (valid) | > stock (invalid).
 * Oracle is the stock level afterwards, not just "no exception".
 */
class InMemoryInventoryTest {

    private static final int STOCK = 5;
    private InMemoryInventory inventory;

    @BeforeEach
    void freshInventory() {
        inventory = new InMemoryInventory(Map.of("P1", STOCK));
    }

    // MC/DC for  quantity <= 0 || current < quantity  (stock = 5):
    //   (F,F) quantity 1 -> reserved   (F,F) quantity 5 -> reserved (upper boundary)
    //   (T,F) quantity 0 -> rejected   (F,T) quantity 6 -> rejected
    @ParameterizedTest(name = "reserve {0} of 5 -> stock left {1}")
    @CsvSource({
            "1, 4",   // lower valid boundary
            "4, 1",   // stock - 1
            "5, 0"    // exactly all stock (upper valid boundary)
    })
    @DisplayName("Valid reservations reduce stock by exactly the quantity")
    void validReservationReducesStock(int quantity, int expectedLeft) {
        inventory.reserve("P1", quantity);
        assertEquals(expectedLeft, inventory.available("P1"));
    }

    @ParameterizedTest(name = "reserve {0} is rejected, stock stays 5")
    @CsvSource({
            "0",    // quantity <= 0 is true on its own
            "-1",
            "6"     // current < quantity is true on its own (stock + 1)
    })
    @DisplayName("Invalid reservations are rejected and leave stock unchanged")
    void invalidReservationIsRejected(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> inventory.reserve("P1", quantity));
        assertEquals(STOCK, inventory.available("P1"));
    }

    @Test
    @DisplayName("Unknown product has 0 available and cannot be reserved")
    void unknownProductCannotBeReserved() {
        assertEquals(0, inventory.available("NOPE"));
        assertThrows(IllegalArgumentException.class, () -> inventory.reserve("NOPE", 1));
    }

    @Test
    @DisplayName("Two reservations add up")
    void reservationsAccumulate() {
        inventory.reserve("P1", 2);
        inventory.reserve("P1", 3);
        assertEquals(0, inventory.available("P1"));
    }
}
