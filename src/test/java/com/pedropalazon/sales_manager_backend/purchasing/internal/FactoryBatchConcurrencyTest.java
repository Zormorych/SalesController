package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchStatus;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderStatus;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
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

// Sin @Transactional a propósito: cada operación hace commit de verdad para que compitan entre sí.
// Por eso el test borra al final lo que ha creado.
@SpringBootTest
@ActiveProfiles("test")
class FactoryBatchConcurrencyTest {

    @Autowired
    private FactoryBatchService batchService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long customerId;
    private final List<Long> orderIds = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void createProductAndCustomer() {
        productId = productService.create("Camiseta Concurrencia Lotes", new BigDecimal("15.00"),
                new BigDecimal("6.50")).getId();
        customerId = customerService.create("Cliente Concurrencia Lotes", null, null).getId();
    }

    @AfterEach
    void deleteCreatedRows() {
        for (Long orderId : orderIds) {
            jdbcTemplate.update("delete from factory_batch_lines where sales_order_line_id in "
                    + "(select id from sales_order_lines where sales_order_id = ?)", orderId);
        }
        jdbcTemplate.update("delete from factory_batches where notes = 'Concurrencia'");
        for (Long orderId : orderIds) {
            jdbcTemplate.update("delete from sales_order_lines where sales_order_id = ?", orderId);
            jdbcTemplate.update("delete from sales_orders where id = ?", orderId);
        }
        jdbcTemplate.update("delete from customers where name = 'Cliente Concurrencia Lotes'");
        jdbcTemplate.update("delete from products where name = 'Camiseta Concurrencia Lotes'");
    }

    @Test
    void givenBatchBeingSent_whenLinesAreAddedAtTheSameTime_thenEveryLineOfTheSentBatchIsOrdered() throws Exception {
        List<Long> lines = createOrder(6).lines().stream().map(SalesOrderView.Line::id).toList();
        Long batchId = batchService.create(lines.subList(0, 1), new BigDecimal("4.00"), "Concurrencia").id();

        List<Callable<Object>> operations = new ArrayList<>();
        operations.add(() -> batchService.send(batchId));
        for (Long lineId : lines.subList(1, 6)) {
            operations.add(() -> batchService.addLines(batchId, List.of(lineId)));
        }
        runAtTheSameTime(operations);

        FactoryBatchView.Details batch = batchService.get(batchId);
        assertThat(batch.status()).isEqualTo(FactoryBatchStatus.SENT);
        assertThat(batch.lines()).allSatisfy(line -> assertThat(line.status()).isEqualTo(LineStatus.ORDERED));
    }

    @Test
    void givenBatchBeingSent_whenItsOrderIsCancelledAtTheSameTime_thenOnlyOneOfThemWins() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            SalesOrderView.Details order = createOrder(2);
            List<Long> lines = order.lines().stream().map(SalesOrderView.Line::id).toList();
            Long batchId = batchService.create(lines, new BigDecimal("4.00"), "Concurrencia").id();

            runAtTheSameTime(List.of(() -> batchService.send(batchId), () -> salesOrderService.cancel(order.id())));

            FactoryBatchView.Details batch = batchService.get(batchId);
            SalesOrderView.Details reloaded = salesOrderService.get(order.id());
            if (batch.status() == FactoryBatchStatus.SENT) {
                assertThat(reloaded.status()).isEqualTo(SalesOrderStatus.OPEN);
                assertThat(reloaded.lines()).allSatisfy(line -> assertThat(line.status()).isEqualTo(LineStatus.ORDERED));
            } else {
                assertThat(batch.status()).isEqualTo(FactoryBatchStatus.OPEN);
                assertThat(reloaded.status()).isEqualTo(SalesOrderStatus.CANCELLED);
                assertThat(reloaded.lines()).allSatisfy(line -> assertThat(line.status()).isEqualTo(LineStatus.CANCELLED));
            }
        }
    }

    private SalesOrderView.Details createOrder(int lineCount) {
        List<OrderLineDraft> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null, List.of()));
        }
        SalesOrderView.Details order = salesOrderService.create(customerId, LocalDate.of(2026, 10, 15), null, lines);
        orderIds.add(order.id());
        return order;
    }

    // Lanza todas las operaciones a la vez; las que fallan por una regla de negocio (409) son resultados válidos
    private static void runAtTheSameTime(List<Callable<Object>> operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (Callable<Object> operation : operations) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    return operation.call();
                } catch (BusinessRuleException rejected) {
                    return rejected;
                }
            }));
        }
        start.countDown();
        for (Future<Object> result : results) {
            result.get();
        }
        executor.shutdown();
    }
}
