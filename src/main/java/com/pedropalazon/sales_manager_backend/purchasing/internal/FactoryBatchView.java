package com.pedropalazon.sales_manager_backend.purchasing.internal;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Modelos de lectura de los lotes. El envío se reparte solo entre las camisetas no canceladas,
// y lo cancelado no suma en los totales (igual que en los pedidos).
public final class FactoryBatchView {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private FactoryBatchView() {
    }

    public record Summary(Long id, String batchNumber, FactoryBatchStatus status, LocalDateTime createdAt,
            LocalDateTime sentAt, LocalDateTime receivedAt, int lineCount, int missingCount, BigDecimal shippingCost) {
    }

    public record Details(Long id, String batchNumber, FactoryBatchStatus status, String notes,
            LocalDateTime createdAt, LocalDateTime sentAt, LocalDateTime receivedAt, BigDecimal shippingCost,
            List<Line> lines, Totals totals, List<MissingLine> missingLines) {
    }

    // Camiseta que se pidió en este lote pero no llegó; status es su estado actual (pendiente o ya pedida en otro lote)
    public record MissingLine(Long lineId, Long orderId, String orderNumber, String customerName, String productName,
            String kitName, ClothingSize size, String sizeLabel, LineStatus status,
            List<OrderLineSnapshot.Customization> customizations) {
    }

    public record Line(Long lineId, Long orderId, String orderNumber, LocalDate orderDate, String customerName,
            String productName, String kitName, ClothingSize size, String sizeLabel, LineStatus status,
            List<OrderLineSnapshot.Customization> customizations, BigDecimal totalSale, BigDecimal totalCost,
            BigDecimal shippingShare, BigDecimal profit) {
    }

    public record Totals(long activeLines, BigDecimal sale, BigDecimal cost, BigDecimal shipping, BigDecimal profit) {
    }

    static Summary summary(FactoryBatch batch) {
        return new Summary(batch.getId(), batch.getBatchNumber(), batch.getStatus(), batch.getCreatedAt(),
                batch.getSentAt(), batch.getReceivedAt(), batch.getLineIds().size(), batch.getMissingLineIds().size(),
                batch.getShippingCost());
    }

    static Details details(FactoryBatch batch, List<OrderLineSnapshot> orderLines, List<OrderLineSnapshot> missing) {
        long activeCount = orderLines.stream().filter(line -> line.status() != LineStatus.CANCELLED).count();
        List<BigDecimal> shares = ShippingAllocation.split(batch.getShippingCost(), (int) activeCount);
        List<Line> lines = new ArrayList<>();
        BigDecimal sale = ZERO;
        BigDecimal cost = ZERO;
        BigDecimal shipping = ZERO;
        int activeIndex = 0;
        for (OrderLineSnapshot orderLine : orderLines) {
            boolean cancelled = orderLine.status() == LineStatus.CANCELLED;
            BigDecimal share = cancelled ? ZERO : shares.get(activeIndex++);
            lines.add(new Line(orderLine.lineId(), orderLine.orderId(), orderLine.orderNumber(), orderLine.orderDate(),
                    orderLine.customerName(), orderLine.productName(), orderLine.kitName(), orderLine.size(),
                    orderLine.size().getLabel(), orderLine.status(), orderLine.customizations(),
                    orderLine.totalSale(), orderLine.totalCost(), share,
                    orderLine.totalSale().subtract(orderLine.totalCost()).subtract(share)));
            if (!cancelled) {
                sale = sale.add(orderLine.totalSale());
                cost = cost.add(orderLine.totalCost());
                shipping = shipping.add(share);
            }
        }
        Totals totals = new Totals(activeCount, sale, cost, shipping, sale.subtract(cost).subtract(shipping));
        return new Details(batch.getId(), batch.getBatchNumber(), batch.getStatus(), batch.getNotes(),
                batch.getCreatedAt(), batch.getSentAt(), batch.getReceivedAt(), batch.getShippingCost(), lines, totals,
                missing.stream().map(FactoryBatchView::missingLine).toList());
    }

    private static MissingLine missingLine(OrderLineSnapshot line) {
        return new MissingLine(line.lineId(), line.orderId(), line.orderNumber(), line.customerName(), line.productName(),
                line.kitName(), line.size(), line.size().getLabel(), line.status(), line.customizations());
    }
}
