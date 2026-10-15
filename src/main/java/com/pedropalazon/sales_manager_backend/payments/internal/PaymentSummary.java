package com.pedropalazon.sales_manager_backend.payments.internal;

import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Situación de cobro de un pedido. pendingAmount negativo = se ha cobrado de más (por ejemplo, pedido
// cancelado después de pagarlo) y hay que devolver esa cantidad.
public record PaymentSummary(Long orderId, String orderNumber, boolean orderCancelled, BigDecimal orderTotal,
        BigDecimal paidAmount, BigDecimal pendingAmount, List<Item> payments) {

    public record Item(Long id, BigDecimal amount, LocalDate paymentDate, PaymentStatus status, String notes) {
    }

    static PaymentSummary of(SalesOrderSnapshot order, List<Payment> payments) {
        BigDecimal paid = paidAmount(payments);
        List<Item> items = payments.stream()
                .map(payment -> new Item(payment.getId(), payment.getAmount(), payment.getPaymentDate(),
                        payment.getStatus(), payment.getNotes()))
                .toList();
        return new PaymentSummary(order.id(), order.orderNumber(), order.cancelled(), order.totalSale(), paid,
                order.totalSale().subtract(paid), items);
    }

    static BigDecimal paidAmount(List<Payment> payments) {
        return payments.stream()
                .filter(payment -> !payment.isVoided())
                .map(Payment::getAmount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }
}
