package com.pedropalazon.sales_manager_backend.reports.internal;

import com.pedropalazon.sales_manager_backend.payments.PaymentApi;
import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchTotals;
import com.pedropalazon.sales_manager_backend.purchasing.PurchasingApi;
import com.pedropalazon.sales_manager_backend.resale.ResaleApi;
import com.pedropalazon.sales_manager_backend.resale.ResaleSales;
import com.pedropalazon.sales_manager_backend.resale.StockValue;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Informes de solo lectura: cruza ventas, compras y cobros a través de sus APIs públicas
@Service
public class ReportService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final SalesOrderApi salesOrderApi;
    private final PurchasingApi purchasingApi;
    private final PaymentApi paymentApi;
    private final ResaleApi resaleApi;

    public ReportService(SalesOrderApi salesOrderApi, PurchasingApi purchasingApi, PaymentApi paymentApi,
            ResaleApi resaleApi) {
        this.salesOrderApi = salesOrderApi;
        this.purchasingApi = purchasingApi;
        this.paymentApi = paymentApi;
        this.resaleApi = resaleApi;
    }

    // Las camisetas que aún no están en un lote no tienen envío asignado: cuentan 0
    @Transactional(readOnly = true)
    public SalesSummary summary(LocalDate from, LocalDate to) {
        List<OrderLineSnapshot> lines = salesOrderApi.findActiveLinesOrderedBetween(from, to);
        Map<Long, BigDecimal> shippingShares = purchasingApi.shippingSharesByLine();
        BigDecimal sale = ZERO;
        BigDecimal cost = ZERO;
        BigDecimal shipping = ZERO;
        for (OrderLineSnapshot line : lines) {
            sale = sale.add(line.totalSale());
            cost = cost.add(line.totalCost());
            shipping = shipping.add(shippingShares.getOrDefault(line.lineId(), ZERO));
        }
        long orders = lines.stream().map(OrderLineSnapshot::orderId).distinct().count();
        BigDecimal profit = sale.subtract(cost).subtract(shipping);
        ResaleSales resale = resaleApi.soldBetween(from, to);
        return new SalesSummary(from, to, orders, lines.size(), sale, cost, shipping, profit,
                paymentApi.collectedBetween(from, to), resale, profit.add(resale.profit()));
    }

    @Transactional(readOnly = true)
    public List<FactoryBatchTotals> batches() {
        return purchasingApi.findBatchTotals();
    }

    // Pendiente de cada pedido = total - cobrado. Un pedido cancelado tiene total 0, así que lo que se
    // cobró de él sale como dinero por devolver
    @Transactional(readOnly = true)
    public PendingWork pending() {
        Map<LineStatus, Long> shirts = salesOrderApi.countActiveLinesByStatus();
        Map<Long, BigDecimal> paid = paymentApi.paidAmountsByOrder();
        long ordersToCollect = 0;
        BigDecimal toCollect = ZERO;
        BigDecimal toRefund = ZERO;
        for (SalesOrderSnapshot order : salesOrderApi.findAllOrders()) {
            BigDecimal pending = order.totalSale().subtract(paid.getOrDefault(order.id(), ZERO));
            if (pending.signum() > 0 && !order.cancelled()) {
                ordersToCollect++;
                toCollect = toCollect.add(pending);
            } else if (pending.signum() < 0) {
                toRefund = toRefund.add(pending.negate());
            }
        }
        StockValue stock = resaleApi.stock();
        return new PendingWork(shirts.getOrDefault(LineStatus.PENDING, 0L),
                shirts.getOrDefault(LineStatus.ORDERED, 0L), shirts.getOrDefault(LineStatus.RECEIVED, 0L),
                ordersToCollect, toCollect, toRefund, stock.items(), stock.cost());
    }
}
