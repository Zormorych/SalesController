package com.pedropalazon.sales_manager_backend.payments.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Cobro de un pedido. El pedido pertenece a otro módulo, así que se guarda por id.
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salesOrderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.REGISTERED;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    public Payment(Long salesOrderId, BigDecimal amount, LocalDate paymentDate, String notes) {
        this.salesOrderId = salesOrderId;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.paymentDate = paymentDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void voidPayment() {
        this.status = PaymentStatus.VOIDED;
    }

    public boolean isVoided() {
        return status == PaymentStatus.VOIDED;
    }

    public Long getId() {
        return id;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
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
