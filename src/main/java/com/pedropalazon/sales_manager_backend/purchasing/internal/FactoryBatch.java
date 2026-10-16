package com.pedropalazon.sales_manager_backend.purchasing.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

// Pedido al fabricante: agrupa camisetas de varios pedidos de cliente. Las camisetas pertenecen
// al módulo de ventas, así que el lote solo guarda sus ids.
@Entity
@Table(name = "factory_batches")
public class FactoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FactoryBatchStatus status = FactoryBatchStatus.OPEN;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(length = 500)
    private String notes;

    private LocalDateTime sentAt;

    private LocalDateTime receivedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "factory_batch_lines", joinColumns = @JoinColumn(name = "factory_batch_id"))
    @Column(name = "sales_order_line_id", nullable = false)
    private Set<Long> lineIds = new LinkedHashSet<>();

    // Camisetas que se pidieron en este lote pero no llegaron (ya no forman parte de él)
    @ElementCollection
    @CollectionTable(name = "factory_batch_missing_lines", joinColumns = @JoinColumn(name = "factory_batch_id"))
    @Column(name = "sales_order_line_id", nullable = false)
    private Set<Long> missingLineIds = new LinkedHashSet<>();

    protected FactoryBatch() {
    }

    public FactoryBatch(String batchNumber, BigDecimal shippingCost, String notes) {
        this.batchNumber = batchNumber;
        updateDetails(shippingCost, notes);
    }

    public static String formatBatchNumber(int year, long sequence) {
        return "FB-%d-%04d".formatted(year, sequence);
    }

    public void addLines(Collection<Long> newLineIds) {
        lineIds.addAll(newLineIds);
    }

    public boolean removeLine(Long lineId) {
        return lineIds.remove(lineId);
    }

    public void updateDetails(BigDecimal shippingCost, String notes) {
        this.shippingCost = shippingCost.setScale(2, RoundingMode.HALF_UP);
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void markSent(LocalDateTime when) {
        this.status = FactoryBatchStatus.SENT;
        this.sentAt = when;
    }

    // Las que no llegaron salen del lote (quedan libres para pedirlas en otro) y se apuntan como no llegadas
    public void markReceived(Collection<Long> missing, LocalDateTime when) {
        lineIds.removeAll(missing);
        missingLineIds.addAll(missing);
        this.status = FactoryBatchStatus.RECEIVED;
        this.receivedAt = when;
    }

    // Una camiseta que no llegó con el lote llega después: vuelve a formar parte de él
    public void markArrived(Long lineId) {
        missingLineIds.remove(lineId);
        lineIds.add(lineId);
    }

    // Un lote descartado deja libres sus camisetas para poder pedirlas en otro
    public void cancel() {
        this.status = FactoryBatchStatus.CANCELLED;
        lineIds.clear();
    }

    public boolean isOpen() {
        return status == FactoryBatchStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public FactoryBatchStatus getStatus() {
        return status;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public Set<Long> getMissingLineIds() {
        return Set.copyOf(missingLineIds);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<Long> getLineIds() {
        return Set.copyOf(lineIds);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
