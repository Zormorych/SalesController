package com.pedropalazon.sales_manager_backend.resale.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.resale.ResaleApi;
import com.pedropalazon.sales_manager_backend.resale.ResaleSales;
import com.pedropalazon.sales_manager_backend.resale.StockValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResaleApiServiceTest {

    @Autowired
    private ResaleApi resaleApi;

    @Autowired
    private ResaleItemService service;

    @Test
    void givenNoItems_whenGettingFigures_thenEverythingIsZero() {
        assertThat(resaleApi.soldBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .isEqualTo(new ResaleSales(0, new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(resaleApi.stock()).isEqualTo(new StockValue(0, new BigDecimal("0.00")));
    }

    @Test
    void givenItemSoldOnLastDayOfOctober_whenGettingSales_thenCountsInOctoberOnly() {
        Long sold = item("Chaqueta Nike vintage", "11.70");
        service.sell(sold, new BigDecimal("17.70"), LocalDate.of(2026, 10, 31));
        item("Sudadera Adidas", "8.00");
        item("Gorra New Era", "4.50");

        assertThat(resaleApi.soldBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .isEqualTo(new ResaleSales(1, new BigDecimal("17.70"), new BigDecimal("11.70"), new BigDecimal("6.00")));
        assertThat(resaleApi.soldBetween(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).items()).isZero();
        assertThat(resaleApi.stock()).isEqualTo(new StockValue(2, new BigDecimal("12.50")));
    }

    private Long item(String name, String cost) {
        return service.create(new ResaleItemDraft(name, null, null, new BigDecimal(cost), null, null)).id();
    }
}
