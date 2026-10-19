package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Una camiseta recibida que el cliente ya no quiere pasa a stock: o la paga igualmente o se le devuelve el dinero
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesOrderStockApiTest {

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
    void givenReceivedLine_whenMovedToStockWithCustomerPaying_thenLineIsStockedPaidAndOrderTotalIsUnchanged() {
        SalesOrderView.Details order = createReceivedOrder(LocalDate.of(2026, 10, 5), 2);
        Long lineId = order.lines().get(0).id();

        OrderLineSnapshot moved = salesOrderApi.moveLineToStock(lineId, true);

        assertThat(moved.status()).isEqualTo(LineStatus.STOCKED_PAID);
        assertThat(moved.totalCost()).isEqualByComparingTo("6.50");
        SalesOrderView.Details reloaded = salesOrderService.get(order.id());
        assertThat(reloaded.status()).isEqualTo(SalesOrderStatus.OPEN);
        assertThat(reloaded.totals().sale()).isEqualByComparingTo("30.00");
    }

    @Test
    void givenReceivedLine_whenMovedToStockWithRefund_thenOrderTotalDropsByItsSale() {
        SalesOrderView.Details order = createReceivedOrder(LocalDate.of(2026, 10, 5), 2);

        OrderLineSnapshot moved = salesOrderApi.moveLineToStock(order.lines().get(0).id(), false);

        assertThat(moved.status()).isEqualTo(LineStatus.STOCKED_REFUNDED);
        SalesOrderView.Details reloaded = salesOrderService.get(order.id());
        assertThat(reloaded.status()).isEqualTo(SalesOrderStatus.OPEN);
        assertThat(reloaded.totals().sale()).isEqualByComparingTo("15.00");
    }

    @Test
    void givenOnlyActiveLine_whenMovedToStockWithRefund_thenOrderIsCancelledAndLineStaysStockedRefunded() {
        SalesOrderView.Details order = createReceivedOrder(LocalDate.of(2026, 10, 5), 1);

        salesOrderApi.moveLineToStock(order.lines().get(0).id(), false);

        SalesOrderView.Details reloaded = salesOrderService.get(order.id());
        assertThat(reloaded.status()).isEqualTo(SalesOrderStatus.CANCELLED);
        assertThat(reloaded.lines()).singleElement()
                .satisfies(line -> assertThat(line.status()).isEqualTo(LineStatus.STOCKED_REFUNDED));
    }

    @Test
    void givenPendingOrOrderedLine_whenMovedToStock_thenConflictAndNothingChanges() {
        SalesOrderView.Details order = createOrder(LocalDate.of(2026, 10, 5), 2);
        Long pending = order.lines().get(0).id();
        Long ordered = order.lines().get(1).id();
        salesOrderApi.markLinesOrdered(List.of(ordered));

        for (Long lineId : List.of(pending, ordered)) {
            assertThatThrownBy(() -> salesOrderApi.moveLineToStock(lineId, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no está recibida");
        }
        assertThat(salesOrderApi.getLines(List.of(pending, ordered)))
                .extracting(OrderLineSnapshot::status)
                .containsExactly(LineStatus.PENDING, LineStatus.ORDERED);
    }

    @Test
    void givenStockedRefundedLine_whenSearchingReportLines_thenItIsLeftOutButStockedPaidIsIncluded() {
        SalesOrderView.Details order = createReceivedOrder(LocalDate.of(2026, 10, 5), 3);
        Long paid = order.lines().get(0).id();
        Long refunded = order.lines().get(1).id();
        salesOrderApi.moveLineToStock(paid, true);
        salesOrderApi.moveLineToStock(refunded, false);

        assertThat(salesOrderApi.findActiveLinesOrderedBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .extracting(OrderLineSnapshot::lineId)
                .contains(paid, order.lines().get(2).id())
                .doesNotContain(refunded);
        assertThat(salesOrderApi.countActiveLinesByStatus())
                .doesNotContainKey(LineStatus.STOCKED_REFUNDED)
                .containsEntry(LineStatus.STOCKED_PAID, 1L);
    }

    private SalesOrderView.Details createReceivedOrder(LocalDate orderDate, int lineCount) {
        SalesOrderView.Details order = createOrder(orderDate, lineCount);
        List<Long> lineIds = order.lines().stream().map(SalesOrderView.Line::id).toList();
        salesOrderApi.markLinesOrdered(lineIds);
        salesOrderApi.receiveLines(lineIds, List.of());
        return order;
    }

    private SalesOrderView.Details createOrder(LocalDate orderDate, int lineCount) {
        List<OrderLineDraft> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null, List.of()));
        }
        return salesOrderService.create(customerId, orderDate, null, lines);
    }
}
