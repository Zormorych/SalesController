package com.pedropalazon.sales_manager_backend.purchasing.internal;

import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactoryBatchService {

    private final FactoryBatchRepository repository;
    private final SalesOrderApi salesOrderApi;

    public FactoryBatchService(FactoryBatchRepository repository, SalesOrderApi salesOrderApi) {
        this.repository = repository;
        this.salesOrderApi = salesOrderApi;
    }

    // Camisetas pendientes de pedir que todavía no están en ningún lote
    @Transactional(readOnly = true)
    public List<OrderLineSnapshot> pendingLines() {
        Set<Long> assigned = repository.findAssignedLineIds();
        return salesOrderApi.findPendingLines().stream()
                .filter(line -> !assigned.contains(line.lineId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FactoryBatchView.Summary> list() {
        return repository.findAllByOrderByIdDesc().stream().map(FactoryBatchView::summary).toList();
    }

    @Transactional(readOnly = true)
    public FactoryBatchView.Details get(Long id) {
        return detailsOf(find(id));
    }

    @Transactional
    public FactoryBatchView.Details create(Collection<Long> lineIds, BigDecimal shippingCost, String notes) {
        ensureLinesCanBeBatched(lineIds);
        String batchNumber = FactoryBatch.formatBatchNumber(LocalDate.now().getYear(), repository.nextBatchSequence());
        FactoryBatch batch = new FactoryBatch(batchNumber, shippingCost, notes);
        batch.addLines(lineIds);
        repository.save(batch);
        return detailsOf(batch);
    }

    // El coste de envío se puede corregir también con el lote ya enviado: la factura real suele llegar después
    @Transactional
    public FactoryBatchView.Details updateDetails(Long id, BigDecimal shippingCost, String notes) {
        FactoryBatch batch = find(id);
        if (batch.getStatus() == FactoryBatchStatus.CANCELLED) {
            throw new BusinessRuleException("El lote %s está cancelado".formatted(batch.getBatchNumber()));
        }
        batch.updateDetails(shippingCost, notes);
        return detailsOf(batch);
    }

    @Transactional
    public FactoryBatchView.Details addLines(Long id, Collection<Long> lineIds) {
        FactoryBatch batch = findOpen(id);
        ensureLinesCanBeBatched(lineIds);
        batch.addLines(lineIds);
        return detailsOf(batch);
    }

    @Transactional
    public FactoryBatchView.Details removeLine(Long id, Long lineId) {
        FactoryBatch batch = findOpen(id);
        if (!batch.removeLine(lineId)) {
            throw new ResourceNotFoundException("La línea %d no está en el lote %s"
                    .formatted(lineId, batch.getBatchNumber()));
        }
        return detailsOf(batch);
    }

    // Enviar el lote marca sus camisetas como pedidas al fabricante (ya no se pueden editar en el pedido)
    @Transactional
    public FactoryBatchView.Details send(Long id) {
        FactoryBatch batch = findOpen(id);
        List<OrderLineSnapshot> lines = batch.getLineIds().isEmpty()
                ? List.of()
                : salesOrderApi.getLines(batch.getLineIds());
        if (lines.isEmpty()) {
            throw new BusinessRuleException("El lote %s no tiene camisetas".formatted(batch.getBatchNumber()));
        }
        lines.stream()
                .filter(line -> line.status() == LineStatus.CANCELLED)
                .findFirst()
                .ifPresent(line -> {
                    throw new BusinessRuleException("La línea %d del lote está cancelada: quítala antes de enviar"
                            .formatted(line.lineId()));
                });
        salesOrderApi.markLinesOrdered(batch.getLineIds());
        batch.markSent(LocalDateTime.now());
        return detailsOf(batch);
    }

    @Transactional
    public FactoryBatchView.Details cancel(Long id) {
        FactoryBatch batch = findOpen(id);
        batch.cancel();
        return detailsOf(batch);
    }

    // Se puede generar con el lote abierto (para revisarlo antes de enviarlo) o ya enviado.
    // Las camisetas canceladas no se piden, así que no salen.
    @Transactional(readOnly = true)
    public ExcelFile excel(Long id) {
        FactoryBatch batch = find(id);
        if (batch.getStatus() == FactoryBatchStatus.CANCELLED) {
            throw new BusinessRuleException("El lote %s está cancelado".formatted(batch.getBatchNumber()));
        }
        List<OrderLineSnapshot> shirts = batch.getLineIds().isEmpty()
                ? List.of()
                : salesOrderApi.getLines(batch.getLineIds()).stream()
                        .filter(line -> line.status() != LineStatus.CANCELLED)
                        .toList();
        if (shirts.isEmpty()) {
            throw new BusinessRuleException("El lote %s no tiene camisetas".formatted(batch.getBatchNumber()));
        }
        byte[] content = FactoryBatchExcel.generate(batch.getBatchNumber(), LocalDate.now(), shirts);
        return new ExcelFile(batch.getBatchNumber() + ".xlsx", content);
    }

    // Bloquea el lote hasta el commit: así nadie añade camisetas a un lote que se está enviando
    private FactoryBatch findOpen(Long id) {
        FactoryBatch batch = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el lote con id %d".formatted(id)));
        if (batch.getStatus() == FactoryBatchStatus.SENT) {
            throw new BusinessRuleException(
                    "El lote %s ya se ha enviado y no se puede modificar".formatted(batch.getBatchNumber()));
        }
        if (batch.getStatus() == FactoryBatchStatus.CANCELLED) {
            throw new BusinessRuleException("El lote %s está cancelado".formatted(batch.getBatchNumber()));
        }
        return batch;
    }

    FactoryBatch find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el lote con id %d".formatted(id)));
    }

    void ensureLinesCanBeBatched(Collection<Long> lineIds) {
        List<OrderLineSnapshot> lines = salesOrderApi.getLines(lineIds);
        Set<Long> assigned = repository.findAssignedLineIds();
        for (OrderLineSnapshot line : lines) {
            if (line.status() != LineStatus.PENDING) {
                throw new BusinessRuleException("La línea %d no está pendiente de pedir".formatted(line.lineId()));
            }
            if (assigned.contains(line.lineId())) {
                throw new BusinessRuleException("La línea %d ya está en otro lote".formatted(line.lineId()));
            }
        }
    }

    FactoryBatchView.Details detailsOf(FactoryBatch batch) {
        List<OrderLineSnapshot> lines = batch.getLineIds().isEmpty()
                ? List.of()
                : salesOrderApi.getLines(batch.getLineIds());
        return FactoryBatchView.details(batch, lines);
    }
}
