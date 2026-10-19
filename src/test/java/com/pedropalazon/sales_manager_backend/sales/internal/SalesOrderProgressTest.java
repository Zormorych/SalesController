package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// El progreso del pedido se calcula a partir de sus camisetas no canceladas
class SalesOrderProgressTest {

    @Test
    void givenAllShirtsPending_whenCheckingProgress_thenIsPending() {
        SalesOrder order = orderWithLines(2);

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.PENDING);
        assertThat(order.lineCounts()).isEqualTo(new SalesOrderView.LineCounts(2, 0, 0, 0, 0));
    }

    @Test
    void givenSomeShirtsOrderedOrReceived_whenCheckingProgress_thenIsInProgress() {
        SalesOrder order = orderWithLines(3);
        order.getLines().get(0).markOrdered();
        order.getLines().get(1).markOrdered();
        order.getLines().get(1).markReceived();

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.IN_PROGRESS);
        assertThat(order.lineCounts()).isEqualTo(new SalesOrderView.LineCounts(1, 1, 1, 0, 0));
    }

    @Test
    void givenEveryShirtReceivedAndSomeDelivered_whenCheckingProgress_thenIsReadyToDeliver() {
        SalesOrder order = orderWithLines(2);
        order.getLines().forEach(line -> {
            line.markOrdered();
            line.markReceived();
        });
        order.getLines().get(0).markDelivered();

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.READY_TO_DELIVER);
    }

    @Test
    void givenEveryActiveShirtDelivered_whenCheckingProgress_thenIsCompletedIgnoringCancelledOnes() {
        SalesOrder order = orderWithLines(3);
        order.getLines().get(2).cancel();
        order.getLines().subList(0, 2).forEach(line -> {
            line.markOrdered();
            line.markReceived();
            line.markDelivered();
        });

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.COMPLETED);
        assertThat(order.lineCounts()).isEqualTo(new SalesOrderView.LineCounts(0, 0, 0, 2, 1));
    }

    @Test
    void givenCancelledOrder_whenCheckingProgress_thenIsCancelled() {
        SalesOrder order = orderWithLines(1);
        order.cancel();

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.CANCELLED);
    }

    @Test
    void givenShirtThatDidNotArrive_whenReturnedToPending_thenCountsAsPendingAgain() {
        SalesOrder order = orderWithLines(1);
        order.getLines().get(0).markOrdered();
        order.getLines().get(0).returnToPending();

        assertThat(order.progress()).isEqualTo(SalesOrderProgress.PENDING);
    }

    private static SalesOrder orderWithLines(int count) {
        SalesOrder order = new SalesOrder("SO-2026-000001", 1L, LocalDate.of(2026, 10, 16), null);
        for (int i = 0; i < count; i++) {
            order.addLine(new SalesOrderLine(1L, "Camiseta Fan", "Selección Española blanca", ClothingSize.M,
                    new BigDecimal("15.00"), new BigDecimal("6.50")));
        }
        return order;
    }
}
