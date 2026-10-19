package com.pedropalazon.sales_manager_backend.reports.internal.web;

import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchTotals;
import com.pedropalazon.sales_manager_backend.reports.internal.PendingWork;
import com.pedropalazon.sales_manager_backend.reports.internal.ReportService;
import com.pedropalazon.sales_manager_backend.reports.internal.SalesSummary;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Informes", description = "Venta, coste, envío y beneficio; beneficio por lote; y lo que queda pendiente")
class ReportController {

    private static final DateTimeFormatter SPANISH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    // Sin fechas: el mes actual. Con una sola: la otra se completa con el mismo mes de la que viene
    @GetMapping("/summary")
    SalesSummary summary(
            @Parameter(description = "Primer día (incluido). Por defecto, el día 1 del mes de `to` (o del mes actual)")
            @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Último día (incluido). Por defecto, el último día del mes de `from` (o del mes actual)")
            @RequestParam(required = false) LocalDate to) {
        LocalDate start = from != null ? from : (to != null ? YearMonth.from(to) : YearMonth.now()).atDay(1);
        LocalDate end = to != null ? to : YearMonth.from(start).atEndOfMonth();
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio (%s) es posterior a la de fin (%s)."
                    .formatted(start.format(SPANISH_DATE), end.format(SPANISH_DATE)));
        }
        return service.summary(start, end);
    }

    @GetMapping("/batches")
    List<FactoryBatchTotals> batches() {
        return service.batches();
    }

    @GetMapping("/pending")
    PendingWork pending() {
        return service.pending();
    }
}
