package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchStatus;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchTotals;
import com.pedropalazon.sales_manager_backend.purchasing.PurchasingApi;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
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
class PurchasingApiServiceTest {

    @Autowired
    private PurchasingApi purchasingApi;

    @Autowired
    private FactoryBatchService batchService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    private List<Long> batchedLines;
    private Long discardedLine;
    private Long batchId;

    // Un lote enviado con 3 camisetas (15,00 / 6,50) y 4,50 € de envío, y otro lote descartado
    @BeforeEach
    void createBatches() {
        Long productId = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")).getId();
        Long customerId = customerService.create("Ana Pérez", null, null).getId();
        OrderLineDraft line = new OrderLineDraft(productId, "Selección Española blanca", ClothingSize.M, null, null, List.of());
        List<Long> lines = salesOrderService.create(customerId, LocalDate.of(2026, 10, 1), null, List.of(line, line, line, line))
                .lines().stream().map(SalesOrderView.Line::id).toList();
        batchedLines = lines.subList(0, 3);
        discardedLine = lines.get(3);

        batchId = batchService.create(batchedLines, new BigDecimal("4.50"), null).id();
        batchService.send(batchId);
        Long discarded = batchService.create(List.of(discardedLine), new BigDecimal("4.00"), null).id();
        batchService.cancel(discarded);
    }

    @Test
    void givenBatches_whenGettingShippingShares_thenEachBatchedShirtHasItsShareAndOthersAreAbsent() {
        assertThat(purchasingApi.shippingSharesByLine())
                .containsOnlyKeys(batchedLines)
                .allSatisfy((lineId, share) -> assertThat(share).isEqualByComparingTo("1.50"));
    }

    @Test
    void givenBatches_whenGettingTotals_thenOnlyNonDiscardedBatchesWithSaleCostShippingAndProfit() {
        assertThat(purchasingApi.findBatchTotals()).singleElement().satisfies(totals -> {
            assertThat(totals.batchId()).isEqualTo(batchId);
            assertThat(totals.status()).isEqualTo(FactoryBatchStatus.SENT);
            assertThat(totals.shirts()).isEqualTo(3);
            assertThat(totals.sale()).isEqualByComparingTo("45.00");
            assertThat(totals.cost()).isEqualByComparingTo("19.50");
            assertThat(totals.shipping()).isEqualByComparingTo("4.50");
            assertThat(totals.profit()).isEqualByComparingTo("21.00");
        });
    }
}
