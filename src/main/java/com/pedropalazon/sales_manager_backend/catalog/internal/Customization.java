package com.pedropalazon.sales_manager_backend.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "customizations")
public class Customization extends CatalogItem {

    protected Customization() {
    }

    public Customization(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        super(name, salePrice, purchaseCost);
    }
}
