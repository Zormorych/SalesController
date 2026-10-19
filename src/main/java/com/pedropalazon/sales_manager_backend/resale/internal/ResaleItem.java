package com.pedropalazon.sales_manager_backend.resale.internal;

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

// Prenda en stock para revender. Si viene de un pedido se guarda el id de la camiseta: pertenece a otro módulo.
@Entity
@Table(name = "resale_items")
public class ResaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "item_size", length = 40)
    private String size;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ResaleItemOrigin origin;

    @Column(updatable = false)
    private Long salesOrderLineId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal askingPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResaleItemStatus status = ResaleItemStatus.AVAILABLE;

    @Column(precision = 10, scale = 2)
    private BigDecimal salePrice;

    private LocalDate saleDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ResaleItem() {
    }

    public ResaleItem(ResaleItemDraft draft) {
        this.origin = ResaleItemOrigin.MANUAL;
        update(draft);
    }

    // Camiseta de un pedido que el cliente ya no quiso: entra hoy en stock con el coste que se le asigne
    public static ResaleItem fromOrderLine(Long salesOrderLineId, String name, String size, BigDecimal purchaseCost,
            BigDecimal askingPrice) {
        ResaleItem item = new ResaleItem(new ResaleItemDraft(name, size, null, purchaseCost, null, askingPrice));
        item.origin = ResaleItemOrigin.ORDER_LINE;
        item.salesOrderLineId = salesOrderLineId;
        return item;
    }

    public void update(ResaleItemDraft draft) {
        this.name = draft.name().trim();
        this.size = blankToNull(draft.size());
        this.notes = blankToNull(draft.notes());
        this.purchaseCost = money(draft.purchaseCost());
        this.purchaseDate = draft.purchaseDate() == null ? LocalDate.now() : draft.purchaseDate();
        this.askingPrice = draft.askingPrice() == null ? null : money(draft.askingPrice());
    }

    public void sell(BigDecimal price, LocalDate date) {
        this.status = ResaleItemStatus.SOLD;
        this.salePrice = money(price);
        this.saleDate = date == null ? LocalDate.now() : date;
    }

    public void unsell() {
        this.status = ResaleItemStatus.AVAILABLE;
        this.salePrice = null;
        this.saleDate = null;
    }

    public boolean isSold() {
        return status == ResaleItemStatus.SOLD;
    }

    // Solo existe si está vendida
    public BigDecimal profit() {
        return isSold() ? salePrice.subtract(purchaseCost) : null;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSize() {
        return size;
    }

    public String getNotes() {
        return notes;
    }

    public ResaleItemOrigin getOrigin() {
        return origin;
    }

    public Long getSalesOrderLineId() {
        return salesOrderLineId;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getAskingPrice() {
        return askingPrice;
    }

    public ResaleItemStatus getStatus() {
        return status;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public LocalDate getSaleDate() {
        return saleDate;
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
