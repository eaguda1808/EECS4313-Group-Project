package edu.testing.quickcart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Product constructor. Spec, Input constraints: "Product price must be nonnegative."
 * Characteristics: unitPrice (< 0 | = 0 | > 0), id/name (null | blank | non-blank).
 * Weak robust: one invalid field per test, the others valid.
 */
class ProductTest {

    // ----- unitPrice: boundary -0.01 / 0.00 / 0.01 -----

    @Test
    @DisplayName("Price just below zero (-0.01) is rejected")
    void negativePriceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", "Cable", new BigDecimal("-0.01")));
    }

    @ParameterizedTest(name = "price {0} is accepted")
    @ValueSource(strings = {"0.00", "0.01", "19.99"})
    @DisplayName("Zero and positive prices are accepted")
    void nonNegativePriceIsAccepted(String price) {
        Product p = new Product("P1", "Cable", new BigDecimal(price));
        assertEquals(0, new BigDecimal(price).compareTo(p.unitPrice()));
    }

    // ----- id / name blank: MC/DC for  id.isBlank() || name.isBlank()  -----
    // Row 1 (F,F) -> valid; Row 2 (T,F) -> invalid; Row 3 (F,T) -> invalid.
    // Rows 1+2 show the id clause alone flips the outcome; rows 1+3 show the same for name.

    @ParameterizedTest(name = "id=''{0}'', name=''{1}'' -> valid={2}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "P1  | Cable | true",   // MC/DC row 1: neither blank
            "''  | Cable | false",  // MC/DC row 2: id blank (empty)
            "' ' | Cable | false",  //             id blank (whitespace only)
            "P1  | ''    | false",  // MC/DC row 3: name blank (empty)
            "P1  | ' '   | false"   //             name blank (whitespace only)
    })
    @DisplayName("Blank id or blank name is rejected (MC/DC on the OR)")
    void blankIdOrNameIsRejected(String id, String name, boolean valid) {
        if (valid) {
            Product p = new Product(id, name, new BigDecimal("1.00"));
            assertEquals(id, p.id());
            assertEquals(name, p.name());
        } else {
            assertThrows(IllegalArgumentException.class, () -> new Product(id, name, new BigDecimal("1.00")));
        }
    }

    // ----- null fields -----

    @Test
    @DisplayName("Null id is rejected")
    void nullIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new Product(null, "Cable", BigDecimal.ONE));
    }

    @Test
    @DisplayName("Null name is rejected")
    void nullNameIsRejected() {
        assertThrows(NullPointerException.class, () -> new Product("P1", null, BigDecimal.ONE));
    }

    @Test
    @DisplayName("Null price is rejected")
    void nullPriceIsRejected() {
        assertThrows(NullPointerException.class, () -> new Product("P1", "Cable", null));
    }
}
