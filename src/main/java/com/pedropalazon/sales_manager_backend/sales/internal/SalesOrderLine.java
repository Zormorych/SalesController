package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// Una camiseta (o prenda) de un pedido. Nombre del producto y precios son una copia del catálogo.
@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 160)
    private String kitName;

    @Enumerated(EnumType.STRING)
    @Column(name = "clothing_size", nullable = false, length = 20)
    private ClothingSize size;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitSalePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPurchaseCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status = LineStatus.PENDING;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LineCustomization> customizations = new ArrayList<>();

    protected SalesOrderLine() {
    }

    public SalesOrderLine(Long productId, String productName, String kitName, ClothingSize size,
            BigDecimal unitSalePrice, BigDecimal unitPurchaseCost) {
        setFields(productId, productName, kitName, size, unitSalePrice, unitPurchaseCost);
    }

    public void addCustomization(LineCustomization customization) {
        customization.attachTo(this);
        customizations.add(customization);
    }

    // Sustituye producto, talla, precios y personalizaciones por los de otra línea aún no guardada
    public void replaceWith(SalesOrderLine draft) {
        setFields(draft.productId, draft.productName, draft.kitName, draft.size, draft.unitSalePrice,
                draft.unitPurchaseCost);
        customizations.clear();
        draft.customizations.forEach(this::addCustomization);
    }

    public void cancel() {
        this.status = LineStatus.CANCELLED;
    }

    public void markOrdered() {
        this.status = LineStatus.ORDERED;
    }

    public void markReceived() {
        this.status = LineStatus.RECEIVED;
    }

    // La camiseta no llegó del fabricante: vuelve a estar pendiente de pedir
    public void returnToPending() {
        this.status = LineStatus.PENDING;
    }

    public void markDelivered() {
        this.status = LineStatus.DELIVERED;
    }

    SalesOrder getOrder() {
        return order;
    }

    public boolean isCancelled() {
        return status == LineStatus.CANCELLED;
    }

    public boolean isPending() {
        return status == LineStatus.PENDING;
    }

    public BigDecimal totalSale() {
        return customizations.stream()
                .map(LineCustomization::getSalePrice)
                .reduce(unitSalePrice, BigDecimal::add);
    }

    public BigDecimal totalCost() {
        return customizations.stream()
                .map(LineCustomization::getPurchaseCost)
                .reduce(unitPurchaseCost, BigDecimal::add);
    }

    public BigDecimal profit() {
        return totalSale().subtract(totalCost());
    }

    void attachTo(SalesOrder order) {
        this.order = order;
    }

    private void setFields(Long productId, String productName, String kitName, ClothingSize size,
            BigDecimal unitSalePrice, BigDecimal unitPurchaseCost) {
        this.productId = productId;
        this.productName = productName;
        this.kitName = kitName.trim();
        this.size = size;
        this.unitSalePrice = unitSalePrice.setScale(2, RoundingMode.HALF_UP);
        this.unitPurchaseCost = unitPurchaseCost.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getKitName() {
        return kitName;
    }

    public ClothingSize getSize() {
        return size;
    }

    public BigDecimal getUnitSalePrice() {
        return unitSalePrice;
    }

    public BigDecimal getUnitPurchaseCost() {
        return unitPurchaseCost;
    }

    public LineStatus getStatus() {
        return status;
    }

    public List<LineCustomization> getCustomizations() {
        return List.copyOf(customizations);
    }
}
