package com.pedropalazon.sales_manager_backend.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyFormatTest {

    @Test
    void givenAmounts_whenFormattingAsEuros_thenUsesSpanishFormatWithTwoDecimals() {
        assertThat(MoneyFormat.euros(new BigDecimal("17"))).isEqualTo("17,00 €");
        assertThat(MoneyFormat.euros(new BigDecimal("0.5"))).isEqualTo("0,50 €");
        assertThat(MoneyFormat.euros(new BigDecimal("1234.5"))).isEqualTo("1.234,50 €");
        assertThat(MoneyFormat.euros(new BigDecimal("-10"))).isEqualTo("-10,00 €");
    }
}
