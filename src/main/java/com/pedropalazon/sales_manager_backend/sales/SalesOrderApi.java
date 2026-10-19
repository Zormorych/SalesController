package com.pedropalazon.sales_manager_backend.sales;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    // Recepción de un lote: las recibidas pasan a RECEIVED y las que no llegaron vuelven a pendientes de pedir.
    // Todas deben estar pedidas al fabricante; si alguna no lo está no cambia ninguna. Exige una transacción.
    void receiveLines(Collection<Long> receivedLineIds, Collection<Long> missingLineIds);

    // Una camiseta que no llegó con su lote (y volvió a pendientes) llega después por su cuenta.
    // Debe estar pendiente. Exige una transacción.
    void markLateArrivalReceived(Long lineId);

    // --- Datos para informes ---

    // Camisetas no canceladas de pedidos no cancelados con fecha de pedido entre from y to (ambos incluidos)
    List<OrderLineSnapshot> findActiveLinesOrderedBetween(LocalDate from, LocalDate to);

    // Camisetas de pedidos no cancelados agrupadas por estado (cuántas faltan por pedir, recibir, entregar...)
    Map<LineStatus, Long> countActiveLinesByStatus();

    // Todos los pedidos, cancelados incluidos (con total 0)
    List<SalesOrderSnapshot> findAllOrders();
}
