package com.pedropalazon.sales_manager_backend.sales.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

// Personalización aplicada a una camiseta. Nombre y precios son una copia del catálogo en el momento
// de añadirla (o el precio que se puso a mano), para que cambiar el catálogo no altere pedidos antiguos.
@Entity
@Table(name = "line_customizations")
public class LineCustomization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_line_id")
    private SalesOrderLine line;

    @Column(nullable = false)
    private Long customizationId;

    @Column(nullable = false, length = 120)
    private String customizationName;

    @Column(length = 120)
    private String customText;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    protected LineCustomization() {
    }

    public LineCustomization(Long customizationId, String customizationName, String customText,
            BigDecimal salePrice, BigDecimal purchaseCost) {
        this.customizationId = customizationId;
        this.customizationName = customizationName;
        this.customText = customText == null || customText.isBlank() ? null : customText.trim();
        this.salePrice = salePrice.setScale(2, RoundingMode.HALF_UP);
        this.purchaseCost = purchaseCost.setScale(2, RoundingMode.HALF_UP);
    }

    void attachTo(SalesOrderLine line) {
        this.line = line;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomizationId() {
        return customizationId;
    }

    public String getCustomizationName() {
        return customizationName;
    }

    public String getCustomText() {
        return customText;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }
}
