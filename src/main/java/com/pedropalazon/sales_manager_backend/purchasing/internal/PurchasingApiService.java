package com.pedropalazon.sales_manager_backend.purchasing.internal;

import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchStatus;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchTotals;
import com.pedropalazon.sales_manager_backend.purchasing.PurchasingApi;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurchasingApiService implements PurchasingApi {

    private final FactoryBatchRepository repository;
    private final SalesOrderApi salesOrderApi;

    PurchasingApiService(FactoryBatchRepository repository, SalesOrderApi salesOrderApi) {
        this.repository = repository;
        this.salesOrderApi = salesOrderApi;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> shippingSharesByLine() {
        Map<Long, BigDecimal> shares = new HashMap<>();
        for (FactoryBatchView.Details batch : activeBatches()) {
            batch.lines().stream()
                    .filter(line -> line.status() != LineStatus.CANCELLED)
                    .forEach(line -> shares.put(line.lineId(), line.shippingShare()));
        }
        return shares;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FactoryBatchTotals> findBatchTotals() {
        return activeBatches().stream()
                .map(batch -> new FactoryBatchTotals(batch.id(), batch.batchNumber(), batch.status(), batch.sentAt(),
                        batch.receivedAt(), batch.totals().activeLines(), batch.totals().sale(), batch.totals().cost(),
                        batch.totals().shipping(), batch.totals().profit()))
                .toList();
    }

    // Detalle de todos los lotes no descartados, leyendo sus camisetas de una sola vez
    private List<FactoryBatchView.Details> activeBatches() {
        List<FactoryBatch> batches = repository.findByStatusNotOrderByIdDesc(FactoryBatchStatus.CANCELLED);
        Set<Long> lineIds = new HashSet<>();
        batches.forEach(batch -> {
            lineIds.addAll(batch.getLineIds());
            lineIds.addAll(batch.getMissingLineIds());
        });
        // Mismo orden que el detalle del lote, para que los céntimos sobrantes del envío caigan en las mismas camisetas
        List<OrderLineSnapshot> lines = lineIds.isEmpty() ? List.of() : salesOrderApi.getLines(lineIds);
        return batches.stream()
                .map(batch -> FactoryBatchView.details(batch,
                        linesIn(lines, batch.getLineIds()),
                        linesIn(lines, batch.getMissingLineIds())))
                .toList();
    }

    private static List<OrderLineSnapshot> linesIn(List<OrderLineSnapshot> lines, Collection<Long> ids) {
        return lines.stream().filter(line -> ids.contains(line.lineId())).toList();
    }
}
