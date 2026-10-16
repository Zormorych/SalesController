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
import java.util.HashSet;
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

    // Llega el pedido del fabricante. Puede faltar alguna camiseta sin que avise: esas vuelven a estar
    // pendientes de pedir (para reclamarlas o meterlas en el siguiente lote) y el lote recuerda que faltaron
    @Transactional
    public FactoryBatchView.Details receive(Long id, Collection<Long> missingLineIds) {
        FactoryBatch batch = lock(id);
        if (batch.getStatus() == FactoryBatchStatus.OPEN) {
            throw new BusinessRuleException("El lote %s todavía no se ha enviado".formatted(batch.getBatchNumber()));
        }
        if (batch.getStatus() == FactoryBatchStatus.RECEIVED) {
            throw new BusinessRuleException("El lote %s ya se ha recibido".formatted(batch.getBatchNumber()));
        }
        if (batch.getStatus() == FactoryBatchStatus.CANCELLED) {
            throw new BusinessRuleException("El lote %s está cancelado".formatted(batch.getBatchNumber()));
        }
        Set<Long> missing = Set.copyOf(missingLineIds);
        missing.stream()
                .filter(lineId -> !batch.getLineIds().contains(lineId))
                .findFirst()
                .ifPresent(lineId -> {
                    throw new ResourceNotFoundException("La línea %d no está en el lote %s"
                            .formatted(lineId, batch.getBatchNumber()));
                });
        Set<Long> received = new HashSet<>(batch.getLineIds());
        received.removeAll(missing);
        // Un lote "recibido" sin ninguna camiseta dejaría su envío sin asignar a nadie: no se acepta
        if (received.isEmpty()) {
            throw new BusinessRuleException("En el lote %s no ha llegado ninguna camiseta: no se puede dar por recibido"
                    .formatted(batch.getBatchNumber()));
        }

        salesOrderApi.receiveLines(received, missing);
        batch.markReceived(missing, LocalDateTime.now());
        return detailsOf(batch);
    }

    // La camiseta que no llegó con el lote la manda el fabricante más tarde (por ejemplo, tras reclamarla):
    // vuelve a su lote como recibida y el envío se reparte también con ella
    @Transactional
    public FactoryBatchView.Details markMissingLineArrived(Long id, Long lineId) {
        FactoryBatch batch = lock(id);
        if (batch.getStatus() != FactoryBatchStatus.RECEIVED) {
            throw new BusinessRuleException("El lote %s todavía no se ha recibido".formatted(batch.getBatchNumber()));
        }
        if (!batch.getMissingLineIds().contains(lineId)) {
            throw new ResourceNotFoundException("La línea %d no figura como no llegada en el lote %s"
                    .formatted(lineId, batch.getBatchNumber()));
        }
        if (repository.findAssignedLineIds().contains(lineId)) {
            throw new BusinessRuleException(
                    "La línea %d ya está en otro lote: márcala como recibida al recibir ese lote".formatted(lineId));
        }
        salesOrderApi.markLateArrivalReceived(lineId);
        batch.markArrived(lineId);
        return detailsOf(batch);
    }

    // Se puede generar con el lote abierto (para revisarlo antes de enviarlo), enviado o recibido.
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
        FactoryBatch batch = lock(id);
        switch (batch.getStatus()) {
            case SENT -> throw new BusinessRuleException(
                    "El lote %s ya se ha enviado y no se puede modificar".formatted(batch.getBatchNumber()));
            case RECEIVED -> throw new BusinessRuleException(
                    "El lote %s ya se ha recibido y no se puede modificar".formatted(batch.getBatchNumber()));
            case CANCELLED -> throw new BusinessRuleException(
                    "El lote %s está cancelado".formatted(batch.getBatchNumber()));
            default -> {
                return batch;
            }
        }
    }

    private FactoryBatch lock(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el lote con id %d".formatted(id)));
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
        Set<Long> all = new HashSet<>(batch.getLineIds());
        all.addAll(batch.getMissingLineIds());
        List<OrderLineSnapshot> lines = all.isEmpty() ? List.of() : salesOrderApi.getLines(all);
        return FactoryBatchView.details(batch,
                lines.stream().filter(line -> batch.getLineIds().contains(line.lineId())).toList(),
                lines.stream().filter(line -> batch.getMissingLineIds().contains(line.lineId())).toList());
    }
}
