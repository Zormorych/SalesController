package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Modelos de lectura de los pedidos. Se construyen dentro de la transacción del servicio
// (open-in-view está desactivado, así que fuera de ella no se pueden cargar las líneas).
public final class SalesOrderView {

    private SalesOrderView() {
    }

    public record Details(Long id, String orderNumber, LocalDate orderDate, SalesOrderStatus status,
            SalesOrderProgress progress, String notes, CustomerSummary customer, List<Line> lines, Totals totals,
            LineCounts lineCounts) {
    }

    public record Line(Long id, Long productId, String productName, String kitName, ClothingSize size,
            String sizeLabel, BigDecimal unitSalePrice, BigDecimal unitPurchaseCost, LineStatus status,
            List<Customization> customizations, BigDecimal totalSale, BigDecimal totalCost, BigDecimal profit) {
    }

    public record Customization(Long id, Long customizationId, String customizationName, String customText,
            BigDecimal salePrice, BigDecimal purchaseCost) {
    }

    public record Totals(BigDecimal sale, BigDecimal cost, BigDecimal profit, long activeLines) {
    }

    public record Summary(Long id, String orderNumber, LocalDate orderDate, SalesOrderStatus status,
            SalesOrderProgress progress, Long customerId, String customerName, long activeLines, BigDecimal totalSale,
            BigDecimal totalProfit, LineCounts lineCounts) {
    }

    // Camisetas del pedido en cada estado: lo que falta por pedir, recibir o entregar
    public record LineCounts(long pending, long ordered, long received, long delivered, long cancelled) {
    }

    static Details details(SalesOrder order, CustomerSummary customer) {
        List<Line> lines = order.getLines().stream().map(SalesOrderView::line).toList();
        Totals totals = new Totals(order.totalSale(), order.totalCost(), order.totalProfit(), order.activeLineCount());
        return new Details(order.getId(), order.getOrderNumber(), order.getOrderDate(), order.getStatus(),
                order.progress(), order.getNotes(), customer, lines, totals, order.lineCounts());
    }

    static Summary summary(SalesOrder order, String customerName) {
        return new Summary(order.getId(), order.getOrderNumber(), order.getOrderDate(), order.getStatus(),
                order.progress(), order.getCustomerId(), customerName, order.activeLineCount(), order.totalSale(),
                order.totalProfit(), order.lineCounts());
    }

    private static Line line(SalesOrderLine line) {
        List<Customization> customizations = line.getCustomizations().stream()
                .map(customization -> new Customization(customization.getId(), customization.getCustomizationId(),
                        customization.getCustomizationName(), customization.getCustomText(),
                        customization.getSalePrice(), customization.getPurchaseCost()))
                .toList();
        return new Line(line.getId(), line.getProductId(), line.getProductName(), line.getKitName(), line.getSize(),
                line.getSize().getLabel(), line.getUnitSalePrice(), line.getUnitPurchaseCost(), line.getStatus(),
                customizations, line.totalSale(), line.totalCost(), line.profit());
    }
}
