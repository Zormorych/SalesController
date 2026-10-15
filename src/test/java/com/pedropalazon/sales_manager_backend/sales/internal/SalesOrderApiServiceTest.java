package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
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
class SalesOrderApiServiceTest {

    @Autowired
    private SalesOrderApi salesOrderApi;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void createCatalogAndCustomer() {
        productId = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        customerId = customerService.create("Ana Pérez", "600333444", null).getId();
    }

    @Test
    void givenOpenOrder_whenRequested_thenReturnsNumberCustomerAndTotal() {
        SalesOrderView.Details order = createOrder();

        SalesOrderSnapshot snapshot = salesOrderApi.getOrder(order.id());

        assertThat(snapshot.id()).isEqualTo(order.id());
        assertThat(snapshot.orderNumber()).isEqualTo(order.orderNumber());
        assertThat(snapshot.customerId()).isEqualTo(customerId);
        assertThat(snapshot.cancelled()).isFalse();
        assertThat(snapshot.totalSale()).isEqualByComparingTo("15.00");
    }

    @Test
    void givenCancelledOrder_whenRequested_thenIsCancelledWithZeroTotal() {
        SalesOrderView.Details order = createOrder();
        salesOrderService.cancel(order.id());

        SalesOrderSnapshot snapshot = salesOrderApi.getOrder(order.id());

        assertThat(snapshot.cancelled()).isTrue();
        assertThat(snapshot.totalSale()).isEqualByComparingTo("0.00");
    }

    @Test
    void givenUnknownOrder_whenRequested_thenThrowsNotFound() {
        assertThatThrownBy(() -> salesOrderApi.getOrder(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existe el pedido con id 999999");
    }

    private SalesOrderView.Details createOrder() {
        OrderLineDraft line = new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null,
                List.of());
        return salesOrderService.create(customerId, LocalDate.of(2026, 10, 15), null, List.of(line));
    }
}
