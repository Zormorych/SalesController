package com.pedropalazon.sales_manager_backend.resale.internal;

import com.pedropalazon.sales_manager_backend.resale.ResaleApi;
import com.pedropalazon.sales_manager_backend.resale.ResaleSales;
import com.pedropalazon.sales_manager_backend.resale.StockValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ResaleApiService implements ResaleApi {

    private final ResaleItemRepository repository;

    ResaleApiService(ResaleItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResaleSales soldBetween(LocalDate from, LocalDate to) {
        Object[] row = repository.sumSoldBetween(ResaleItemStatus.SOLD, from, to).get(0);
        BigDecimal sale = money((BigDecimal) row[1]);
        BigDecimal cost = money((BigDecimal) row[2]);
        return new ResaleSales((Long) row[0], sale, cost, sale.subtract(cost));
    }

    @Override
    @Transactional(readOnly = true)
    public StockValue stock() {
        Object[] row = repository.sumWithStatus(ResaleItemStatus.AVAILABLE).get(0);
        return new StockValue((Long) row[0], money((BigDecimal) row[1]));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
