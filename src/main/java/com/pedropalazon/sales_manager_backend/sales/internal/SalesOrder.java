package com.pedropalazon.sales_manager_backend.sales.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

// Pedido de un cliente. El cliente se guarda por id: pertenece a otro módulo.
@Entity
@Table(name = "sales_orders")
public class SalesOrder {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String orderNumber;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderStatus status = SalesOrderStatus.OPEN;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SalesOrderLine> lines = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(String orderNumber, Long customerId, LocalDate orderDate, String notes) {
        this.orderNumber = orderNumber;
        updateHeader(customerId, orderDate, notes);
    }

    public static String formatOrderNumber(int year, long sequence) {
        return "SO-%d-%06d".formatted(year, sequence);
    }

    public void addLine(SalesOrderLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void updateHeader(Long customerId, LocalDate orderDate, String notes) {
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void cancel() {
        this.status = SalesOrderStatus.CANCELLED;
        lines.forEach(SalesOrderLine::cancel);
    }

    public boolean isCancelled() {
        return status == SalesOrderStatus.CANCELLED;
    }

    public Optional<SalesOrderLine> findLine(Long lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst();
    }

    public long activeLineCount() {
        return activeLines().count();
    }

    public BigDecimal totalSale() {
        return sumOfActiveLines(SalesOrderLine::totalSale);
    }

    public BigDecimal totalCost() {
        return sumOfActiveLines(SalesOrderLine::totalCost);
    }

    public BigDecimal totalProfit() {
        return sumOfActiveLines(SalesOrderLine::profit);
    }

    private Stream<SalesOrderLine> activeLines() {
        return lines.stream().filter(line -> !line.isCancelled());
    }

    private BigDecimal sumOfActiveLines(Function<SalesOrderLine, BigDecimal> amount) {
        return activeLines().map(amount).reduce(ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<SalesOrderLine> getLines() {
        return List.copyOf(lines);
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
