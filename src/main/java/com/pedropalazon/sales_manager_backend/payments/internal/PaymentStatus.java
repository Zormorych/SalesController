package com.pedropalazon.sales_manager_backend.payments.internal;

// Un pago apuntado por error no se borra: se anula y deja de contar
public enum PaymentStatus {
    REGISTERED,
    VOIDED
}
