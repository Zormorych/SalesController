package com.pedropalazon.sales_manager_backend.payments.internal;

import com.pedropalazon.sales_manager_backend.payments.PaymentApi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentApiService implements PaymentApi {

    private final PaymentRepository repository;

    PaymentApiService(PaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> paidAmountsByOrder() {
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : repository.sumByOrderExcludingStatus(PaymentStatus.VOIDED)) {
            paid.put((Long) row[0], money((BigDecimal) row[1]));
        }
        return paid;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal collectedBetween(LocalDate from, LocalDate to) {
        return money(repository.sumBetweenExcludingStatus(PaymentStatus.VOIDED, from, to));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
