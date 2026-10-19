package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesOrderReportApiTest {

    @Autowired
    private SalesOrderApi salesOrderApi;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    private Long productId;
    private Long customerId;

    @BeforeEach
    void createProductAndCustomer() {
        productId = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        customerId = customerService.create("Ana Pérez", null, null).getId();
    }

    @Test
    void givenOrdersInAndOutOfRange_whenFindingActiveLines_thenOnlyNonCancelledLinesOfNonCancelledOrdersInRange() {
        SalesOrderView.Details october = createOrder(LocalDate.of(2026, 10, 5), 2);
        salesOrderService.cancelLine(october.id(), october.lines().get(1).id());
        SalesOrderView.Details lastDayOfOctober = createOrder(LocalDate.of(2026, 10, 31), 1);
        createOrder(LocalDate.of(2026, 9, 30), 1);
        SalesOrderView.Details cancelled = createOrder(LocalDate.of(2026, 10, 10), 1);
        salesOrderService.cancel(cancelled.id());

        List<OrderLineSnapshot> lines = salesOrderApi.findActiveLinesOrderedBetween(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(lines).extracting(OrderLineSnapshot::lineId)
                .containsExactly(october.lines().get(0).id(), lastDayOfOctober.lines().get(0).id());
        assertThat(lines.get(0).totalSale()).isEqualByComparingTo("15.00");
    }

    @Test
    void givenLinesInSeveralStates_whenCounting_thenCountsOnlyLinesOfNonCancelledOrders() {
        SalesOrderView.Details order = createOrder(LocalDate.of(2026, 10, 5), 3);
        List<Long> lines = order.lines().stream().map(SalesOrderView.Line::id).toList();
        salesOrderApi.markLinesOrdered(lines.subList(0, 2));
        salesOrderApi.receiveLines(lines.subList(0, 1), List.of());
        SalesOrderView.Details cancelled = createOrder(LocalDate.of(2026, 10, 6), 1);
        salesOrderService.cancel(cancelled.id());

        assertThat(salesOrderApi.countActiveLinesByStatus())
                .containsEntry(LineStatus.PENDING, 1L)
                .containsEntry(LineStatus.ORDERED, 1L)
                .containsEntry(LineStatus.RECEIVED, 1L)
                .doesNotContainKey(LineStatus.CANCELLED);
    }

    @Test
    void givenOpenAndCancelledOrders_whenFindingAll_thenReturnsEveryOrderWithItsTotal() {
        SalesOrderView.Details open = createOrder(LocalDate.of(2026, 10, 5), 2);
        SalesOrderView.Details cancelled = createOrder(LocalDate.of(2026, 10, 6), 1);
        salesOrderService.cancel(cancelled.id());

        assertThat(salesOrderApi.findAllOrders())
                .extracting(SalesOrderSnapshot::id, SalesOrderSnapshot::cancelled, order -> order.totalSale().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple(open.id(), false, "30.00"),
                        tuple(cancelled.id(), true, "0.00"));
    }

    private SalesOrderView.Details createOrder(LocalDate date, int lineCount) {
        List<OrderLineDraft> lines = IntStream.range(0, lineCount)
                .mapToObj(i -> new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null,
                        List.of()))
                .toList();
        return salesOrderService.create(customerId, date, null, lines);
    }
}
