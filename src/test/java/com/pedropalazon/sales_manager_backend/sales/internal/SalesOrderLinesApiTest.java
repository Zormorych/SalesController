package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.CustomizationService;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesOrderLinesApiTest {

    @Autowired
    private SalesOrderApi salesOrderApi;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomizationService customizationService;

    @Autowired
    private CustomerService customerService;

    private Long productId;
    private Long customizationId;
    private Long anaId;
    private Long juanId;

    @BeforeEach
    void createCatalogAndCustomers() {
        productId = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        customizationId = customizationService.create("Nombre + número", new BigDecimal("2.00"),
                new BigDecimal("1.20")).getId();
        anaId = customerService.create("Ana Pérez", null, null).getId();
        juanId = customerService.create("Juan García", null, null).getId();
    }

    @Test
    void givenOpenAndCancelledOrders_whenFindingPendingLines_thenReturnsOnlyPendingLinesOfOpenOrdersByDate() {
        SalesOrderView.Details later = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", "WILLIAMS JR 17"),
                line("L", null));
        salesOrderService.cancelLine(later.id(), later.lines().get(1).id());
        SalesOrderView.Details earlier = createOrder(juanId, LocalDate.of(2026, 10, 1), line("S", null));
        SalesOrderView.Details cancelled = createOrder(juanId, LocalDate.of(2026, 10, 5), line("XL", null));
        salesOrderService.cancel(cancelled.id());

        List<OrderLineSnapshot> pending = salesOrderApi.findPendingLines();

        assertThat(pending).extracting(OrderLineSnapshot::lineId)
                .containsExactly(earlier.lines().get(0).id(), later.lines().get(0).id());
        OrderLineSnapshot williams = pending.get(1);
        assertThat(williams.orderNumber()).isEqualTo(later.orderNumber());
        assertThat(williams.customerName()).isEqualTo("Ana Pérez");
        assertThat(williams.productName()).isEqualTo("Camiseta Fan");
        assertThat(williams.kitName()).isEqualTo("Selección Española blanca");
        assertThat(williams.size()).isEqualTo(ClothingSize.M);
        assertThat(williams.status()).isEqualTo(LineStatus.PENDING);
        assertThat(williams.customizations()).singleElement()
                .isEqualTo(new OrderLineSnapshot.Customization("Nombre + número", "WILLIAMS JR 17"));
        assertThat(williams.totalSale()).isEqualByComparingTo("17.00");
        assertThat(williams.totalCost()).isEqualByComparingTo("7.70");
    }

    @Test
    void givenLineIds_whenGettingLines_thenReturnsThemAndFailsIfAnyIsMissing() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null));
        Long lineId = order.lines().get(0).id();

        assertThat(salesOrderApi.getLines(List.of(lineId))).extracting(OrderLineSnapshot::lineId).containsExactly(lineId);
        assertThatThrownBy(() -> salesOrderApi.getLines(List.of(lineId, 999999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existen las líneas 999999");
    }

    @Test
    void givenPendingLines_whenMarkedOrdered_thenAreOrderedNotPendingAndNotEditable() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null), line("L", null));
        Long firstLineId = order.lines().get(0).id();

        salesOrderApi.markLinesOrdered(List.of(firstLineId));

        assertThat(salesOrderService.get(order.id()).lines().get(0).status()).isEqualTo(LineStatus.ORDERED);
        assertThat(salesOrderApi.findPendingLines()).extracting(OrderLineSnapshot::lineId)
                .containsExactly(order.lines().get(1).id());
        assertThatThrownBy(() -> salesOrderService.cancelLine(order.id(), firstLineId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void givenLineThatIsNotPending_whenMarkedOrdered_thenFailsWithoutChangingAnything() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null), line("L", null));
        Long firstLineId = order.lines().get(0).id();
        Long secondLineId = order.lines().get(1).id();
        salesOrderService.cancelLine(order.id(), secondLineId);

        assertThatThrownBy(() -> salesOrderApi.markLinesOrdered(List.of(firstLineId, secondLineId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La línea " + secondLineId + " no está pendiente de pedir");
        assertThat(salesOrderService.get(order.id()).lines().get(0).status()).isEqualTo(LineStatus.PENDING);
    }

    @Test
    void givenOrderedLines_whenMarkedReceived_thenAreReceivedAndTheOrderCanNoLongerBeCancelled() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null), line("L", null));
        List<Long> lineIds = order.lines().stream().map(SalesOrderView.Line::id).toList();
        salesOrderApi.markLinesOrdered(lineIds);

        salesOrderApi.receiveLines(lineIds, List.of());

        SalesOrderView.Details reloaded = salesOrderService.get(order.id());
        assertThat(reloaded.lines()).allSatisfy(line -> assertThat(line.status()).isEqualTo(LineStatus.RECEIVED));
        assertThat(reloaded.progress()).isEqualTo(SalesOrderProgress.READY_TO_DELIVER);
        assertThatThrownBy(() -> salesOrderService.cancel(order.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya pedidas al fabricante");
    }

    @Test
    void givenLineThatWasNotOrdered_whenMarkedReceived_thenFails() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null));
        Long lineId = order.lines().get(0).id();

        assertThatThrownBy(() -> salesOrderApi.receiveLines(List.of(lineId), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La línea " + lineId + " no está pedida al fabricante");
    }

    @Test
    void givenOrderedLineThatDidNotArrive_whenReturnedToPending_thenIsPendingAndEditableAgain() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null), line("L", null));
        Long lineId = order.lines().get(0).id();
        salesOrderApi.markLinesOrdered(List.of(lineId));

        salesOrderApi.receiveLines(List.of(), List.of(lineId));

        assertThat(salesOrderApi.findPendingLines()).extracting(OrderLineSnapshot::lineId).contains(lineId);
        salesOrderService.cancelLine(order.id(), lineId);
        assertThat(salesOrderService.get(order.id()).lines().get(0).status()).isEqualTo(LineStatus.CANCELLED);
    }

    @Test
    void givenReceivedLine_whenReturnedToPending_thenFails() {
        SalesOrderView.Details order = createOrder(anaId, LocalDate.of(2026, 10, 10), line("M", null));
        Long lineId = order.lines().get(0).id();
        salesOrderApi.markLinesOrdered(List.of(lineId));
        salesOrderApi.receiveLines(List.of(lineId), List.of());

        assertThatThrownBy(() -> salesOrderApi.receiveLines(List.of(), List.of(lineId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La línea " + lineId + " no está pedida al fabricante");
    }

    private SalesOrderView.Details createOrder(Long customerId, LocalDate date, OrderLineDraft... lines) {
        return salesOrderService.create(customerId, date, null, List.of(lines));
    }

    private OrderLineDraft line(String size, String customText) {
        List<OrderLineDraft.Customization> customizations = customText == null
                ? List.of()
                : List.of(new OrderLineDraft.Customization(customizationId, customText, null, null));
        return new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.valueOf(size), null, null,
                customizations);
    }
}
