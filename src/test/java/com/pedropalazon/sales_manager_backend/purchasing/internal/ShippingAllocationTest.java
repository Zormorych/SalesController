package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ShippingAllocationTest {

    @Test
    void givenAmountThatDoesNotDivideEvenly_whenSplit_thenExtraCentsGoToFirstPartsAndSumIsExact() {
        assertThat(ShippingAllocation.split(new BigDecimal("4.00"), 3))
                .containsExactly(new BigDecimal("1.34"), new BigDecimal("1.33"), new BigDecimal("1.33"));
        assertThat(ShippingAllocation.split(new BigDecimal("0.01"), 3))
                .containsExactly(new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    void givenAmountThatDividesEvenly_whenSplit_thenAllPartsAreEqual() {
        assertThat(ShippingAllocation.split(new BigDecimal("4"), 4))
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00"),
                        new BigDecimal("1.00"));
        assertThat(ShippingAllocation.split(new BigDecimal("0.00"), 2))
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    void givenNoParts_whenSplit_thenReturnsEmptyList() {
        assertThat(ShippingAllocation.split(new BigDecimal("4.00"), 0)).isEmpty();
    }
}
