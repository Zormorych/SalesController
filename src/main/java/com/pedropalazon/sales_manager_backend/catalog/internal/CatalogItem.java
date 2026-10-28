package com.pedropalazon.sales_manager_backend.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// Campos comunes a todo lo que se vende con precio y coste: productos y personalizaciones.
@MappedSuperclass
public abstract class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    // Texto libre opcional: por ejemplo, la URL del producto en la web del fabricante
    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CatalogItem() {
    }

    protected CatalogItem(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        update(name, salePrice, purchaseCost, description);
    }

    public void update(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.salePrice = salePrice.setScale(2, RoundingMode.HALF_UP);
        this.purchaseCost = purchaseCost.setScale(2, RoundingMode.HALF_UP);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
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
