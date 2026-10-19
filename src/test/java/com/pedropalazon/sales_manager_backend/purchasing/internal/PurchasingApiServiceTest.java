package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchStatus;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchTotals;
import com.pedropalazon.sales_manager_backend.purchasing.PurchasingApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private SalesOrderApi salesOrderApi;

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

    // 4,00 € entre 3 no sale exacto: el céntimo que sobra debe caer en la misma camiseta que en el detalle del lote,
    // aunque las camisetas sean de pedidos de fechas distintas
    @Test
    void givenUnevenShipping_whenGettingShippingShares_thenEachShirtGetsTheSameShareAsInTheBatchDetail() {
        Long productId = productService.create("Camiseta Player", new BigDecimal("17.00"), new BigDecimal("7.70")).getId();
        Long customerId = customerService.create("Luis Gómez", null, null).getId();
        OrderLineDraft line = new OrderLineDraft(productId, "Real Madrid local", ClothingSize.L, null, null, List.of());
        List<Long> lines = new ArrayList<>();
        lines.addAll(lineIds(salesOrderService.create(customerId, LocalDate.of(2026, 10, 20), null, List.of(line))));
        lines.addAll(lineIds(salesOrderService.create(customerId, LocalDate.of(2026, 9, 25), null, List.of(line, line))));
        Long unevenBatch = batchService.create(lines, new BigDecimal("4.00"), null).id();

        Map<Long, BigDecimal> shares = purchasingApi.shippingSharesByLine();

        assertThat(batchService.get(unevenBatch).lines()).allSatisfy(batchLine ->
                assertThat(shares.get(batchLine.lineId())).isEqualTo(batchLine.shippingShare()));
        assertThat(lines.stream().map(shares::get).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("4.00");
    }

    // Una camiseta que no llegó sale del reparto de su lote; si se vuelve a pedir, paga envío solo en el nuevo
    @Test
    void givenMissingShirtOrderedAgain_whenGettingFigures_thenItsShippingComesOnlyFromTheNewBatch() {
        Long missing = batchedLines.get(2);
        batchService.receive(batchId, List.of(missing));
        Long newBatch = batchService.create(List.of(missing), new BigDecimal("4.00"), null).id();

        assertThat(purchasingApi.shippingSharesByLine())
                .containsOnlyKeys(batchedLines)
                .hasEntrySatisfying(batchedLines.get(0), share -> assertThat(share).isEqualByComparingTo("2.25"))
                .hasEntrySatisfying(batchedLines.get(1), share -> assertThat(share).isEqualByComparingTo("2.25"))
                .hasEntrySatisfying(missing, share -> assertThat(share).isEqualByComparingTo("4.00"));
        assertThat(purchasingApi.findBatchTotals())
                .extracting(FactoryBatchTotals::batchId, FactoryBatchTotals::shirts)
                .containsExactly(tuple(newBatch, 1L), tuple(batchId, 2L));
    }

    // Una camiseta cancelada dentro de un lote abierto no cuenta ni se lleva envío
    @Test
    void givenCancelledShirtInOpenBatch_whenGettingFigures_thenItIsLeftOut() {
        Long productId = productService.create("Camiseta Kids", new BigDecimal("12.00"), new BigDecimal("5.00")).getId();
        Long customerId = customerService.create("Marta Ruiz", null, null).getId();
        OrderLineDraft line = new OrderLineDraft(productId, "Betis local", ClothingSize.S, null, null, List.of());
        SalesOrderView.Details order = salesOrderService.create(customerId, LocalDate.of(2026, 10, 2), null, List.of(line, line));
        List<Long> lines = lineIds(order);
        Long openBatch = batchService.create(lines, new BigDecimal("4.00"), null).id();
        salesOrderService.cancelLine(order.id(), lines.get(1));

        assertThat(purchasingApi.shippingSharesByLine())
                .doesNotContainKey(lines.get(1))
                .hasEntrySatisfying(lines.get(0), share -> assertThat(share).isEqualByComparingTo("4.00"));
        assertThat(purchasingApi.findBatchTotals()).filteredOn(totals -> totals.batchId().equals(openBatch))
                .singleElement().satisfies(totals -> {
                    assertThat(totals.status()).isEqualTo(FactoryBatchStatus.OPEN);
                    assertThat(totals.shirts()).isEqualTo(1);
                    assertThat(totals.sale()).isEqualByComparingTo("12.00");
                    assertThat(totals.profit()).isEqualByComparingTo("3.00");
                });
    }

    // Una camiseta que pasa a stock viajó en el lote: sigue contando para repartir el envío, así que las demás no pagan más
    @Test
    void givenBatchOfThreeWithOneMovedToStock_whenGettingShares_thenSplitIsUnchanged() {
        batchService.receive(batchId, List.of());
        salesOrderApi.moveLineToStock(batchedLines.get(0), false);
        salesOrderApi.moveLineToStock(batchedLines.get(1), true);

        assertThat(purchasingApi.shippingSharesByLine())
                .containsOnlyKeys(batchedLines)
                .allSatisfy((lineId, share) -> assertThat(share).isEqualByComparingTo("1.50"));
        assertThat(purchasingApi.shippingShareOf(batchedLines.get(0))).isEqualTo(new BigDecimal("1.50"));
    }

    // Si se le devuelve el dinero al cliente, la camiseta y su envío pasan al stock: dejan de contar en el lote
    @Test
    void givenStockedRefundedLine_whenGettingBatchTotals_thenItsSaleCostAndShippingAreLeftOut() {
        batchService.receive(batchId, List.of());
        salesOrderApi.moveLineToStock(batchedLines.get(0), false);
        salesOrderApi.moveLineToStock(batchedLines.get(1), true);

        assertThat(purchasingApi.findBatchTotals()).singleElement().satisfies(totals -> {
            assertThat(totals.shirts()).isEqualTo(2);
            assertThat(totals.sale()).isEqualByComparingTo("30.00");
            assertThat(totals.cost()).isEqualByComparingTo("13.00");
            assertThat(totals.shipping()).isEqualByComparingTo("3.00");
            assertThat(totals.profit()).isEqualByComparingTo("14.00");
        });
    }

    @Test
    void givenLineWithoutBatch_whenGettingShareOf_thenZero() {
        assertThat(purchasingApi.shippingShareOf(discardedLine)).isEqualTo(new BigDecimal("0.00"));
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

    private static List<Long> lineIds(SalesOrderView.Details order) {
        return order.lines().stream().map(SalesOrderView.Line::id).toList();
    }
}
