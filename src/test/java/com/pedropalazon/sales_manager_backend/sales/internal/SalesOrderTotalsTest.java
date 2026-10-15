package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// Cálculos puros de importes: no necesitan Spring ni base de datos
class SalesOrderTotalsTest {

    @Test
    void givenLineWithCustomization_whenComputingTotals_thenAddsCustomizationToLine() {
        SalesOrderLine line = fanShirt();
        line.addCustomization(nameAndNumber("2.00", "1.20"));

        assertThat(line.totalSale()).isEqualTo(new BigDecimal("17.00"));
        assertThat(line.totalCost()).isEqualTo(new BigDecimal("7.70"));
        assertThat(line.profit()).isEqualTo(new BigDecimal("9.30"));
    }

    @Test
    void givenOrderWithTwoLines_whenComputingTotals_thenSumsBoth() {
        SalesOrder order = newOrder();
        SalesOrderLine first = fanShirt();
        first.addCustomization(nameAndNumber("2.00", "1.20"));
        order.addLine(first);
        order.addLine(fanShirt());

        assertThat(order.totalSale()).isEqualTo(new BigDecimal("32.00"));
        assertThat(order.totalCost()).isEqualTo(new BigDecimal("14.20"));
        assertThat(order.totalProfit()).isEqualTo(new BigDecimal("17.80"));
        assertThat(order.activeLineCount()).isEqualTo(2);
    }

    @Test
    void givenCancelledLine_whenComputingOrderTotals_thenItIsExcluded() {
        SalesOrder order = newOrder();
        order.addLine(fanShirt());
        SalesOrderLine cancelled = fanShirt();
        order.addLine(cancelled);

        cancelled.cancel();

        assertThat(order.totalSale()).isEqualTo(new BigDecimal("15.00"));
        assertThat(order.activeLineCount()).isEqualTo(1);
        assertThat(order.getLines()).hasSize(2);
    }

    @Test
    void givenCancelledOrder_whenComputingTotals_thenAllAreZeroAndLinesAreCancelled() {
        SalesOrder order = newOrder();
        order.addLine(fanShirt());

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
        assertThat(order.totalSale()).isEqualTo(new BigDecimal("0.00"));
        assertThat(order.totalProfit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(order.getLines()).allMatch(SalesOrderLine::isCancelled);
    }

    @Test
    void givenYearAndSequence_whenFormattingOrderNumber_thenPadsToSixDigits() {
        assertThat(SalesOrder.formatOrderNumber(2026, 1)).isEqualTo("SO-2026-000001");
        assertThat(SalesOrder.formatOrderNumber(2027, 1234567)).isEqualTo("SO-2027-1234567");
    }

    private static SalesOrder newOrder() {
        return new SalesOrder("SO-2026-000001", 1L, LocalDate.of(2026, 10, 15), null);
    }

    private static SalesOrderLine fanShirt() {
        return new SalesOrderLine(1L, "Camiseta Fan", "Selección Española blanca 2 estrellas", ClothingSize.M,
                new BigDecimal("15.00"), new BigDecimal("6.50"));
    }

    private static LineCustomization nameAndNumber(String salePrice, String purchaseCost) {
        return new LineCustomization(1L, "Nombre + número", "WILLIAMS JR 17", new BigDecimal(salePrice),
                new BigDecimal(purchaseCost));
    }
}
