package com.pedropalazon.sales_manager_backend.sales;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Una camiseta de un pedido tal como la necesitan otros módulos (por ejemplo, los lotes al fabricante)
public record OrderLineSnapshot(Long lineId, Long orderId, String orderNumber, LocalDate orderDate, Long customerId,
        String customerName, Long productId, String productName, String kitName, ClothingSize size, LineStatus status,
        List<Customization> customizations, BigDecimal totalSale, BigDecimal totalCost) {

    public record Customization(String name, String customText) {
    }
}
