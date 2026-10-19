package com.pedropalazon.sales_manager_backend.reports.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.payments.internal.PaymentService;
import com.pedropalazon.sales_manager_backend.payments.internal.PaymentSummary;
import com.pedropalazon.sales_manager_backend.purchasing.internal.FactoryBatchService;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private FactoryBatchService batchService;

    @Autowired
    private PaymentService paymentService;

    private Long batchId;

    // A (05/10): Player 17,00/7,70 + Fan 15,00/6,50 · B (20/10): Fan · C (28/09): Fan, cobrado el 01/10
    // D (10/10): Fan, cobrado 10 € y cancelado · lote con A y B (4,50 € de envío) enviado
    // Cobros: 20 € de A el 06/10 y 5 € anulados de B
    @BeforeEach
    void createScenario() {
        Long player = productService.create("Camiseta Player", new BigDecimal("17.00"), new BigDecimal("7.70")).getId();
        Long fan = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        Long customerId = customerService.create("Ana Pérez", null, null).getId();

        SalesOrderView.Details orderA = order(customerId, LocalDate.of(2026, 10, 5), line(player), line(fan));
        SalesOrderView.Details orderB = order(customerId, LocalDate.of(2026, 10, 20), line(fan));
        SalesOrderView.Details orderC = order(customerId, LocalDate.of(2026, 9, 28), line(fan));
        SalesOrderView.Details orderD = order(customerId, LocalDate.of(2026, 10, 10), line(fan));

        List<Long> batched = new ArrayList<>(lineIds(orderA));
        batched.addAll(lineIds(orderB));
        batchId = batchService.create(batched, new BigDecimal("4.50"), null).id();
        batchService.send(batchId);

        paymentService.register(orderA.id(), new BigDecimal("20.00"), LocalDate.of(2026, 10, 6), null);
        PaymentSummary voided = paymentService.register(orderB.id(), new BigDecimal("5.00"), LocalDate.of(2026, 10, 21), null);
        paymentService.voidPayment(orderB.id(), voided.payments().get(0).id());
        paymentService.register(orderC.id(), new BigDecimal("15.00"), LocalDate.of(2026, 10, 1), null);
        paymentService.register(orderD.id(), new BigDecimal("10.00"), LocalDate.of(2026, 10, 11), null);
        salesOrderService.cancel(orderD.id());
    }

    @Test
    void givenOctober_whenGettingSummary_thenCountsOrdersOfOctoberAndPaymentsCollectedInOctober() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.orders").value(2))
                .andExpect(jsonPath("$.shirts").value(3))
                .andExpect(jsonPath("$.sale").value(47.00))
                .andExpect(jsonPath("$.cost").value(20.70))
                .andExpect(jsonPath("$.shipping").value(4.50))
                .andExpect(jsonPath("$.profit").value(21.80))
                .andExpect(jsonPath("$.collected").value(45.00));
    }

    @Test
    void givenSeptember_whenGettingSummary_thenShirtWithoutBatchHasNoShipping() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(1))
                .andExpect(jsonPath("$.shirts").value(1))
                .andExpect(jsonPath("$.sale").value(15.00))
                .andExpect(jsonPath("$.shipping").value(0.00))
                .andExpect(jsonPath("$.profit").value(8.50))
                .andExpect(jsonPath("$.collected").value(0.00));
    }

    @Test
    void givenNoRange_whenGettingSummary_thenUsesCurrentMonth() throws Exception {
        YearMonth month = YearMonth.now();
        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.to").value(month.atEndOfMonth().toString()));
    }

    @Test
    void givenOnlyOneDate_whenGettingSummary_thenTheOtherIsTakenFromThatSameMonth() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-30"))
                .andExpect(jsonPath("$.shirts").value(1));

        mockMvc.perform(get("/api/reports/summary").param("from", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-15"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.shirts").value(1));
    }

    @Test
    void givenInvertedRange_whenGettingSummary_thenReturnsBadRequestInSpanish() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("from", "2026-10-31").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value(containsString("31/10/2026")));
    }

    @Test
    void givenBatches_whenGettingBatchReport_thenShowsSentBatchWithItsProfit() throws Exception {
        mockMvc.perform(get("/api/reports/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].batchId").value(batchId))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].shirts").value(3))
                .andExpect(jsonPath("$[0].shipping").value(4.50))
                .andExpect(jsonPath("$[0].profit").value(21.80));
    }

    @Test
    void givenScenario_whenGettingPendingWork_thenCountsShirtsMoneyToCollectAndToRefund() throws Exception {
        mockMvc.perform(get("/api/reports/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shirtsToOrder").value(1))
                .andExpect(jsonPath("$.shirtsToReceive").value(3))
                .andExpect(jsonPath("$.shirtsToDeliver").value(0))
                .andExpect(jsonPath("$.ordersToCollect").value(2))
                .andExpect(jsonPath("$.amountToCollect").value(27.00))
                .andExpect(jsonPath("$.amountToRefund").value(10.00));
    }

    private SalesOrderView.Details order(Long customerId, LocalDate date, OrderLineDraft... lines) {
        return salesOrderService.create(customerId, date, null, List.of(lines));
    }

    private static OrderLineDraft line(Long productId) {
        return new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null, List.of());
    }

    private static List<Long> lineIds(SalesOrderView.Details order) {
        return order.lines().stream().map(SalesOrderView.Line::id).toList();
    }
}
