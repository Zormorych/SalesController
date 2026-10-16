package com.pedropalazon.sales_manager_backend.purchasing.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Reparte un importe en partes iguales por céntimos. Los céntimos que sobran van a las primeras partes,
// así la suma es siempre exacta (4,00 € entre 3 → 1,34 + 1,33 + 1,33, no 3 × 1,33 = 3,99).
final class ShippingAllocation {

    private ShippingAllocation() {
    }

    static List<BigDecimal> split(BigDecimal total, int parts) {
        List<BigDecimal> shares = new ArrayList<>();
        if (parts <= 0) {
            return shares;
        }
        long cents = total.movePointRight(2).longValueExact();
        long base = cents / parts;
        long remainder = cents % parts;
        for (int i = 0; i < parts; i++) {
            shares.add(BigDecimal.valueOf(i < remainder ? base + 1 : base, 2));
        }
        return shares;
    }
}
