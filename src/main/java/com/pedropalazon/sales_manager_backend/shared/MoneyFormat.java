package com.pedropalazon.sales_manager_backend.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

// Importes en formato español para los mensajes de la API: 1234.5 → "1.234,50 €"
public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String euros(BigDecimal amount) {
        // Símbolos fijos: el formato de Java para "es" no agrupa miles por debajo de 10.000
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " €";
    }
}
