package com.pedropalazon.sales_manager_backend.sales;

import java.util.Collection;
import java.util.List;

// API pública de ventas para otros módulos (por ejemplo, cobros necesita el total de un pedido)
public interface SalesOrderApi {

    // Lanza ResourceNotFoundException si no existe
    SalesOrderSnapshot getOrder(Long orderId);

    // Igual que getOrder, pero bloquea el pedido hasta que termine la transacción de quien llama.
    // Sirve para que dos operaciones simultáneas sobre el mismo pedido (por ejemplo, dos cobros)
    // se ejecuten una detrás de otra. Exige una transacción en curso.
    SalesOrderSnapshot lockOrder(Long orderId);

    // Camisetas pendientes de pedir al fabricante (de pedidos abiertos), de la más antigua a la más reciente
    List<OrderLineSnapshot> findPendingLines();

    // Lanza ResourceNotFoundException si alguna no existe
    List<OrderLineSnapshot> getLines(Collection<Long> lineIds);

    // Marca las camisetas como pedidas al fabricante. Todas deben estar pendientes; si alguna no lo está,
    // no se marca ninguna (BusinessRuleException). Exige una transacción en curso.
    void markLinesOrdered(Collection<Long> lineIds);

    // Camisetas que han llegado del fabricante (deben estar pedidas). Todo o nada. Exige una transacción.
    void markLinesReceived(Collection<Long> lineIds);

    // Camisetas pedidas que no han llegado: vuelven a estar pendientes de pedir. Todo o nada. Exige una transacción.
    void returnLinesToPending(Collection<Long> lineIds);
}
