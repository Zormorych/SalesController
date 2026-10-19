package com.pedropalazon.sales_manager_backend.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.payments.PaymentApi;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
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
class PaymentApiServiceTest {

    @Autowired
    private PaymentApi paymentApi;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    private Long firstOrder;
    private Long secondOrder;

    // Dos pedidos de 30,00 €: el primero con pagos de 10 (01/10) y 5 (anulado), el segundo con 20 (15/11)
    @BeforeEach
    void createOrdersWithPayments() {
        Long productId = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        Long customerId = customerService.create("Ana Pérez", null, null).getId();
        OrderLineDraft line = new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null, List.of());
        firstOrder = salesOrderService.create(customerId, LocalDate.of(2026, 10, 1), null, List.of(line, line)).id();
        secondOrder = salesOrderService.create(customerId, LocalDate.of(2026, 10, 1), null, List.of(line, line)).id();

        paymentService.register(firstOrder, new BigDecimal("10.00"), LocalDate.of(2026, 10, 1), null);
        PaymentSummary withVoided = paymentService.register(firstOrder, new BigDecimal("5.00"), LocalDate.of(2026, 10, 2), null);
        paymentService.voidPayment(firstOrder, withVoided.payments().get(1).id());
        paymentService.register(secondOrder, new BigDecimal("20.00"), LocalDate.of(2026, 11, 15), null);
    }

    @Test
    void givenPayments_whenSummingByOrder_thenVoidedOnesDoNotCount() {
        assertThat(paymentApi.paidAmountsByOrder())
                .containsOnlyKeys(firstOrder, secondOrder)
                .hasEntrySatisfying(firstOrder, paid -> assertThat(paid).isEqualByComparingTo("10.00"))
                .hasEntrySatisfying(secondOrder, paid -> assertThat(paid).isEqualByComparingTo("20.00"));
    }

    @Test
    void givenPayments_whenSummingByPaymentDate_thenOnlyThoseInRangeCount() {
        assertThat(paymentApi.collectedBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .isEqualTo(new BigDecimal("10.00"));
        assertThat(paymentApi.collectedBetween(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
                .isEqualTo(new BigDecimal("0.00"));
    }
}
