package com.pedropalazon.sales_manager_backend.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Sin @Transactional a propósito: cada cobro tiene que hacer commit de verdad para que compitan entre sí.
// Por eso el test borra al final lo que ha creado.
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    private static final int SIMULTANEOUS_PAYMENTS = 5;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long orderId;

    @BeforeEach
    void createOrderOfFifteenEuros() {
        Long productId = productService.create("Camiseta Concurrencia", new BigDecimal("15.00"),
                new BigDecimal("6.50")).getId();
        Long customerId = customerService.create("Cliente Concurrencia", null, null).getId();
        OrderLineDraft line = new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null,
                List.of());
        orderId = salesOrderService.create(customerId, LocalDate.of(2026, 10, 15), null, List.of(line)).id();
    }

    @AfterEach
    void deleteCreatedRows() {
        jdbcTemplate.update("delete from payments where sales_order_id = ?", orderId);
        jdbcTemplate.update("delete from sales_order_lines where sales_order_id = ?", orderId);
        jdbcTemplate.update("delete from sales_orders where id = ?", orderId);
        jdbcTemplate.update("delete from customers where name = 'Cliente Concurrencia'");
        jdbcTemplate.update("delete from products where name = 'Camiseta Concurrencia'");
    }

    @Test
    void givenSimultaneousFullPayments_whenRegistered_thenOnlyOneIsAccepted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_PAYMENTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < SIMULTANEOUS_PAYMENTS; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    paymentService.register(orderId, new BigDecimal("15.00"), null, null);
                    return true;
                } catch (BusinessRuleException alreadyPaid) {
                    return false;
                }
            }));
        }
        start.countDown();

        int accepted = 0;
        for (Future<Boolean> result : results) {
            accepted += result.get() ? 1 : 0;
        }
        executor.shutdown();

        assertThat(accepted).isEqualTo(1);
        assertThat(paymentService.summary(orderId).paidAmount()).isEqualByComparingTo("15.00");
    }
}
