package com.pedropalazon.sales_manager_backend.payments.internal;

import static com.pedropalazon.sales_manager_backend.shared.MoneyFormat.euros;

import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final SalesOrderApi salesOrderApi;

    public PaymentService(PaymentRepository repository, SalesOrderApi salesOrderApi) {
        this.repository = repository;
        this.salesOrderApi = salesOrderApi;
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(Long orderId) {
        return summaryOf(salesOrderApi.getOrder(orderId));
    }

    // El pedido queda bloqueado hasta el commit: dos cobros simultáneos no pueden ver los dos el mismo pendiente
    @Transactional
    public PaymentSummary register(Long orderId, BigDecimal amount, LocalDate paymentDate, String notes) {
        SalesOrderSnapshot order = salesOrderApi.lockOrder(orderId);
        if (order.cancelled()) {
            throw new BusinessRuleException(
                    "El pedido %s está cancelado y no admite cobros".formatted(order.orderNumber()));
        }
        BigDecimal pending = order.totalSale().subtract(PaymentSummary.paidAmount(paymentsOf(orderId)));
        if (pending.signum() <= 0) {
            throw new BusinessRuleException("El pedido %s ya está pagado".formatted(order.orderNumber()));
        }
        if (amount.compareTo(pending) > 0) {
            throw new BusinessRuleException("El pago de %s supera lo pendiente del pedido %s (%s)"
                    .formatted(euros(amount), order.orderNumber(), euros(pending)));
        }
        repository.save(new Payment(orderId, amount, paymentDate != null ? paymentDate : LocalDate.now(), notes));
        return summaryOf(order);
    }

    // Se puede anular aunque el pedido esté cancelado: es justo el caso de devolver un cobro
    @Transactional
    public PaymentSummary voidPayment(Long orderId, Long paymentId) {
        SalesOrderSnapshot order = salesOrderApi.lockOrder(orderId);
        Payment payment = repository.findById(paymentId)
                .filter(found -> found.getSalesOrderId().equals(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pago %d en el pedido %s"
                        .formatted(paymentId, order.orderNumber())));
        if (payment.isVoided()) {
            throw new BusinessRuleException("El pago %d ya está anulado".formatted(paymentId));
        }
        payment.voidPayment();
        return summaryOf(order);
    }

    private PaymentSummary summaryOf(SalesOrderSnapshot order) {
        return PaymentSummary.of(order, paymentsOf(order.id()));
    }

    private List<Payment> paymentsOf(Long orderId) {
        return repository.findBySalesOrderIdOrderByPaymentDateAscIdAsc(orderId);
    }
}
